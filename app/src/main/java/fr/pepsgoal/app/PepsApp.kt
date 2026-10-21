package fr.pepsgoal.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
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
    val scope = MainScope()
    /** L'application est-elle visible à l'écran ? (sinon : notifications système) */
    var foreground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = DevicePrefs(this)
        fr.pepsgoal.app.ui.FluentEmoji.load(this)
        val secrets = KeystoreSecrets(this)
        store = Store(FileDb(this))
        pg = Pg(store)
        cloud = Cloud(store, pg, Api(BuildConfig.API_URL, secrets, "PepsGoal-Android/${BuildConfig.VERSION_NAME}"), secrets, scope)
        ui = AppState(this)
        pg.hooks = ui
        cloud.events = object : CloudEvents {
            override fun onlineChanged(online: Boolean) = ui.onlineChanged(online)
            override fun sessionChanged() { ui.sessionChanged(); Poller.schedule(this@PepsApp); Poller.startLink(this@PepsApp) }
            override fun serverNotifications(fresh: List<Notif>) { if (!foreground) fresh.forEach { Notifier.server(this@PepsApp, it) } }
            override fun incomingMessages(fresh: List<Message>) { Notifier.messages(this@PepsApp, fresh) }
            override fun newPosts(fresh: List<Post>) { if (!foreground) Notifier.posts(this@PepsApp, fresh) }
        }
        store.onChange = { ui.bump() }
        store.onSaved = { cloud.scheduleSync() }
        Notifier.channels(this)
        cloud.boot()
        watchNetwork()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; onForeground() }
            override fun onStop(owner: LifecycleOwner) { foreground = false; onBackground() }
        })
        SyncWorker.schedule(this)
        Poller.schedule(this)
        // Notifications du serveur (messages, publications, amis…) relevées toutes les minutes à l'écran
        scope.launch { while (true) { delay(Poller.EVERY_MS); if (foreground) cloud.poll() } }
        // Rappels, évaluations et minuteur : vérifiés régulièrement pendant que l'application tourne
        scope.launch { while (true) { delay(1000); pg.timerTick(); if (pg.badgeCheckPending) pg.checkBadges() } }
        scope.launch { delay(1200); pg.checkReminders(); while (true) { delay(30_000); pg.checkReminders(); LiveUpdates.refresh(this@PepsApp) } }
    }

    private fun onForeground() {
        cloud.pingSoon()
        if (store.acct) { cloud.scheduleSync(300); cloud.scheduleSocial(600) }
        pg.checkReminders()
        Notifier.clearReminders(this)
        Poller.startLink(this)
        scope.launch { fr.pepsgoal.app.sys.Updater.autoCheck(this@PepsApp) }
    }

    private fun onBackground() {
        Reminders.reschedule(this)
        LiveUpdates.refresh(this)
        WidgetUpdater.update(this)
        Poller.schedule(this)
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
