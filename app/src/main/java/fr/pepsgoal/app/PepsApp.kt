package fr.pepsgoal.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import fr.pepsgoal.app.data.DevicePrefs
import fr.pepsgoal.app.data.FileDb
import fr.pepsgoal.app.data.KeystoreSecrets
import fr.pepsgoal.app.sys.LiveUpdates
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.sys.Poller
import fr.pepsgoal.app.sys.Reminders
import fr.pepsgoal.app.sys.SyncWorker
import fr.pepsgoal.app.sys.WidgetUpdater
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.ScreenPollPolicy
import fr.pepsgoal.core.Store
import fr.pepsgoal.core.cloud.Api
import fr.pepsgoal.core.cloud.Cloud
import fr.pepsgoal.core.cloud.CloudEvents
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.Notif
import fr.pepsgoal.core.model.Post
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Point d'entrée de l'application : base locale, logique, couche connectée et état de l'interface,
 * partagés par l'activité, les notifications, les alarmes, le widget et les tâches d'arrière-plan.
 */
class PepsApp : Application() {
    lateinit var prefs: DevicePrefs
    lateinit var store: Store
    lateinit var pg: Pg
    lateinit var cloud: Cloud
    lateinit var ui: AppState
    lateinit var secrets: KeystoreSecrets
    val scope = MainScope()
    /** L'application est-elle visible à l'écran ? (sinon : notifications système) */
    var foreground = false
    /** Dernière interaction tactile (horloge de démarrage), pour la boucle d'écran adaptative. */
    @Volatile var lastTouch = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = DevicePrefs(this)
        // Langue de l'interface (Paramètres › Apparence) : français, anglais, ou celle du téléphone
        fr.pepsgoal.app.ui.I18n.apply(this, prefs.raw.getString("lang", "auto") ?: "auto")
        fr.pepsgoal.app.sys.Perf.load(this)
        fr.pepsgoal.app.sys.BgStats.load(this)
        fr.pepsgoal.app.sys.EnergyState.start(this)
        // Prix des icônes, pour rendre les flammes après la baisse des prix de la boutique
        fr.pepsgoal.core.shopExtraPrices = fr.pepsgoal.app.sys.AppIcons.ALL.associate { "icon:${it.key}" to it.price }
        fr.pepsgoal.core.shopExtraNames = fr.pepsgoal.app.sys.AppIcons.ALL.associate { "icon:${it.key}" to it.label }
        fr.pepsgoal.app.ui.FluentEmoji.load(this)
        secrets = KeystoreSecrets(this)
        store = Store(FileDb(this), scope)
        pg = Pg(store)
        cloud = Cloud(store, pg, Api(BuildConfig.API_URL, secrets, "PepsGoal-Android/${BuildConfig.VERSION_NAME}"), secrets, scope)
        ui = AppState(this)
        pg.hooks = ui
        cloud.events = object : CloudEvents {
            override fun onlineChanged(online: Boolean) {
                ui.onlineChanged(online)
                scope.launch { Poller.schedule(this@PepsApp); Poller.startLink(this@PepsApp); SyncWorker.schedule(this@PepsApp) }
            }
            override fun sessionChanged() { ui.sessionChanged(); Poller.schedule(this@PepsApp); Poller.startLink(this@PepsApp); SyncWorker.schedule(this@PepsApp) }
            override fun serverNotifications(fresh: List<Notif>) {
                // Événement utile : la relève repasse en cadence rapide (persisté)
                Poller.onEvent(this@PepsApp)
                if (foreground) return
                // Encouragements sur une même publication : une seule notification « Léa et 3 autres t'encouragent »
                val (likes, rest) = fresh.partition { it.type == "like" && it.postId != null }
                rest.forEach { Notifier.server(this@PepsApp, it) }
                Notifier.likes(this@PepsApp, likes)
            }
            override fun incomingMessages(fresh: List<Message>) { Poller.onEvent(this@PepsApp); Notifier.messages(this@PepsApp, fresh); fr.pepsgoal.app.sys.Duo.syncSoon(this@PepsApp) }
            override fun newPosts(fresh: List<Post>) { Poller.onEvent(this@PepsApp); if (!foreground) Notifier.posts(this@PepsApp, fresh) }
            override fun synced(pushed: Int) { if (ui.wasOffline) { ui.wasOffline = false; ui.syncedN = pushed; ui.syncedAt = System.currentTimeMillis() } }
        }
        store.onChange = { ui.bump() }
        store.onSaved = { cloud.scheduleSync() }
        Notifier.channels(this)
        cloud.setForeground(false)
        cloud.boot()
        watchNetwork()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; onForeground() }
            override fun onStop(owner: LifecycleOwner) { foreground = false; onBackground() }
        })
        Poller.schedule(this)
        // Tâches de fond (WorkManager, géorepérage des services Google) : planifiées juste après le démarrage, hors du
        // chemin critique de la première image (leur initialisation coûte plusieurs dizaines de millisecondes)
        scope.launch {
            delay(1500)
            try { SyncWorker.schedule(this@PepsApp) } catch (e: Exception) {}
            try { fr.pepsgoal.app.sys.GeoReminders.register(this@PepsApp) } catch (e: Exception) {}
            try { fr.pepsgoal.app.sys.AutoBackup.schedule(this@PepsApp) } catch (e: Exception) {}
        }
        // These loops exist only while the process is visible; alarms/workers own background work.
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Feed et commentaires : 20 s tant que des nouveautés arrivent, puis ×1,5 par cycle sans changement
                    // jusqu'à 60 s ; une interaction ramène à 20 s. Ailleurs : 60 s.
                    var wait = ScreenPollPolicy.FAST; var at = android.os.SystemClock.elapsedRealtime()
                    while (true) {
                        val social = ui.tab == "feed" || ui.screens.lastOrNull() is Screen.Comments
                        val target = if (social) wait else Poller.EVERY_MS
                        var slept = 0L
                        while (slept < target) {
                            delay(minOf(5_000L, target - slept)); slept += 5_000L
                            if (social && lastTouch > at && slept >= ScreenPollPolicy.FAST) break
                        }
                        val touched = lastTouch > at
                        at = android.os.SystemClock.elapsedRealtime()
                        Poller.run(this@PepsApp, "écran")
                        wait = if (touched) ScreenPollPolicy.FAST else ScreenPollPolicy.next(wait, cloud.pollStatus == "nouveautés relevées")
                    }
                }
                launch { while (true) { delay(1000); pg.timerTick(); if (pg.badgeCheckPending) pg.checkBadges() } }
                launch { while (true) { delay(30_000); pg.checkReminders(); LiveUpdates.refresh(this@PepsApp) } }
            }
        }
    }

    private fun onForeground() {
        cloud.setForeground(true)
        cloud.pingSoon()
        // Synchronisation forcée ; rechargement social complet si une relève allégée a eu lieu, sinon selon sa validité (60 s)
        cloud.foregroundRefresh()
        pg.checkReminders()
        Notifier.clearReminders(this)
        Poller.startLink(this)
        fr.pepsgoal.app.sys.Updater.installedCheck(this)
        scope.launch { fr.pepsgoal.app.sys.Updater.autoCheck(this@PepsApp) }
        scope.launch { try { fr.pepsgoal.app.sys.Health.sync(this@PepsApp) } catch (e: Exception) {} }
        scope.launch { try { fr.pepsgoal.app.sys.Agenda.prune(this@PepsApp) } catch (e: Exception) {} }
    }

    private fun onBackground() {
        cloud.setForeground(false)
        fr.pepsgoal.app.ai.LocalAi.stopBackgroundGeneration()
        scope.launch { try { store.flush() } catch (e: Exception) { ui.toast("L’enregistrement local a échoué. Vérifie l’espace disponible.", "alert") } }
        Reminders.reschedule(this)
        fr.pepsgoal.app.sys.AssistantShortcuts.sync(this)
        LiveUpdates.refresh(this)
        WidgetUpdater.update(this)
        Poller.schedule(this)
        fr.pepsgoal.app.sys.BgStats.flush()
    }

    /** Connexion réseau réelle de l'appareil (relue à chaque réveil : l'état gardé en mémoire peut dater). */
    fun networkUp(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
    }

    private fun watchNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val online = { networkUp() }
        cloud.navOnline = online()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { scope.launch { cloud.navOnline = true; cloud.pingSoon() } }
            override fun onLost(network: Network) { scope.launch { cloud.navOnline = online() } }
        })
    }

    companion object {
        lateinit var instance: PepsApp
            private set
    }
}

val Context.peps: PepsApp get() = applicationContext as PepsApp
