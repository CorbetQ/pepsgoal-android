package fr.pepsgoal.app.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.pepsgoal.app.PepsApp
import fr.pepsgoal.app.peps
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Relève des notifications du serveur toutes les minutes, application fermée : le serveur PepsGoal ne parle que
 * Web Push, l'application native interroge donc elle-même les messages, publications, demandes d'amis,
 * encouragements… Une alarme « chaînée » d'une minute (exacte si Android l'autorise) réveille l'application ;
 * en mode Économie d'énergie / Doze, Android espace de lui-même les réveils (WorkManager reste le filet de sécurité).
 */
object Poller {
    const val EVERY_MS = 60_000L
    /** Activité sociale récente (nouveautés relevées) : la relève reste à la minute pendant un quart d'heure. */
    @Volatile var lastNews = 0L
    @Volatile private var lastRun = 0L
    private var lastHealth = 0L

    /**
     * Intervalle de relève adapté : à la minute quand ça bouge (nouveautés récentes), toutes les 3 minutes au calme,
     * toutes les 15 minutes la nuit (00 h – 7 h). Les réveils du processeur coûtent cher en batterie.
     */
    fun interval(app: PepsApp): Long {
        val now = System.currentTimeMillis()
        if (now - lastNews < 15 * 60_000L) return EVERY_MS
        val h = java.time.LocalTime.now().hour
        return if (h < 7) 15 * 60_000L else 3 * 60_000L
    }
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, PollReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, delayMs: Long? = null) {
        val app = ctx.peps
        val a = ctx.getSystemService(AlarmManager::class.java); val p = pi(ctx)
        if (!app.store.acct) { a.cancel(p); return }
        // Avec la connexion en direct, le service relève lui-même : l'alarme n'est plus qu'un filet de sécurité
        val d = delayMs ?: if (app.prefs.pollTest > 0) 30_000L else if (app.prefs.instantNotifs) 15 * 60_000L else interval(app)
        val t = SystemClock.elapsedRealtime() + d
        // Alarme inexacte : Android la regroupe avec les autres réveils (bien moins coûteux qu'une alarme exacte chaque minute)
        try { a.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p) } catch (e: Exception) { a.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p) }
    }

    fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))

    private val lock = Mutex()

    /** Une relève : réseau relu, serveur interrogé, nouveautés notifiées ; le processeur reste éveillé le temps de la relève. */
    suspend fun run(app: PepsApp, src: String) {
        if (!lock.tryLock()) return
        // Relève toute récente (service et alarme qui se croisent) : inutile de recommencer
        if (System.currentTimeMillis() - lastRun < 40_000L && app.prefs.pollTest == 0L) { lock.unlock(); return }
        val wl = app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pepsgoal:poll")
        try {
            wl.acquire(40_000)
            val ok = withTimeoutOrNull(30_000) {
                app.cloud.navOnline = app.networkUp()
                if (app.cloud.poll()) { lastNews = System.currentTimeMillis(); WidgetUpdater.update(app) }
                try { SmartReminders.check(app) } catch (e: Exception) {}
                // Santé Connect : relu au plus tous les quarts d'heure
                if (System.currentTimeMillis() - lastHealth > 15 * 60_000L) { lastHealth = System.currentTimeMillis(); try { Health.sync(app) } catch (e: Exception) {} }
                true
            }
            if (ok == null) app.cloud.pollStatus = "délai dépassé"
            val now = System.currentTimeMillis(); lastRun = now
            if (app.store.online) app.prefs.lastPoll = now
            val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(now))
            app.prefs.pollLog = (listOf("$hm · $src · ${app.cloud.pollStatus}") + app.prefs.pollLog).take(6)
            if (app.prefs.pollTest > 0 && now - app.prefs.pollTest > 20_000) {
                app.prefs.pollTest = 0
                Notifier.test(app, "✓ Relève en arrière-plan reçue ($src, ${app.cloud.pollStatus}). Les messages t’arriveront aussi application fermée.")
            }
        } catch (e: Exception) {
            app.cloud.pollStatus = "erreur : ${e::class.simpleName}"
        } finally { if (wl.isHeld) wl.release(); lock.unlock() }
    }

    /** (Re)lance le service de connexion en direct si le compte et le réglage le permettent (sinon l'arrête). */
    fun startLink(ctx: Context) {
        val app = ctx.peps
        val i = Intent(ctx, LinkService::class.java)
        if (!app.store.acct || !app.prefs.instantNotifs) { ctx.stopService(i); return }
        try { ContextCompat.startForegroundService(ctx, i) } catch (e: Exception) { /* démarrage refusé hors premier plan : l'alarme prend le relais */ }
    }

    /** L'application est-elle exemptée de l'optimisation de batterie (réveils plus réguliers en veille) ? */
    fun unrestricted(ctx: Context) = ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun askUnrestricted(ctx: Context) {
        val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

class PollReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.peps
        // Au premier plan, la boucle de l'application s'en charge déjà
        if (app.foreground) { Poller.schedule(ctx); return }
        // Une alarme exacte autorise à (re)lancer le service de connexion, s'il a été arrêté par le système
        Poller.startLink(ctx)
        val done = goAsync()
        app.scope.launch {
            try { Poller.run(app, "alarme") } finally { Poller.schedule(ctx); done.finish() }
        }
    }
}

/**
 * Service « connexion en direct » : garde PepsGoal éveillé en arrière-plan (petite notification discrète) pour relever
 * messages, publications et notifications du serveur chaque minute, même application fermée. Sans lui, Android gèle
 * ou ferme l'application et certains fabricants suppriment ses alarmes quand on la retire des applications récentes.
 */
class LinkService : Service() {
    private var job: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = peps
        try {
            ServiceCompat.startForeground(this, Notifier.LINK_ID, Notifier.link(this), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!app.store.acct || !app.prefs.instantNotifs) { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive != true) job = app.scope.launch {
            while (isActive) {
                delay(Poller.interval(app))
                if (!app.foreground) Poller.run(app, "service")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }
}
