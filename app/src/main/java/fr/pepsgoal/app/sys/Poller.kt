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
import android.net.ConnectivityManager
import fr.pepsgoal.app.PepsApp
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.NetSource
import fr.pepsgoal.core.PollContext
import fr.pepsgoal.core.PollGate
import fr.pepsgoal.core.PollPolicy
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Relève des notifications du serveur application fermée : le serveur PepsGoal ne parle que Web Push, l'application
 * native interroge donc elle-même les messages, publications, demandes d'amis, encouragements… La cadence suit
 * [PollPolicy] (rapide après un événement utile, plus lente au calme et la nuit, selon le réseau et la batterie) ;
 * une porte globale ([PollGate]) évite que service, alarme et worker relèvent chacun de leur côté.
 */
object Poller {
    const val EVERY_MS = 60_000L
    @Volatile private var lastRun = 0L

    /** Contexte de décision : dernier événement utile, heure, réseau facturé, économiseur d'énergie, batterie, niveau choisi. */
    fun context(app: PepsApp): PollContext {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        return PollContext(System.currentTimeMillis(), app.prefs.lastEventAt, java.time.LocalTime.now().hour,
            metered = try { cm.isActiveNetworkMetered } catch (e: Exception) { false }, powerSave = pm.isPowerSaveMode,
            batteryLow = EnergyState.batteryLow(app), level = app.prefs.pollLevel)
    }

    /** Intervalle de relève courant (voir [PollPolicy.intervalMs]). */
    fun interval(app: PepsApp): Long = PollPolicy.intervalMs(context(app))

    /** Délai avant la prochaine relève : l'intervalle, espacé après des échecs consécutifs. */
    fun nextDelay(app: PepsApp): Long = PollPolicy.backoffMs(interval(app), app.cloud.pollFailures)

    /** Événement utile (notification serveur, message entrant, publication d'ami) : cadence rapide, persistée. */
    fun onEvent(app: PepsApp) = PollPolicy.recordEvent(app.prefs, System.currentTimeMillis())
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, PollReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, delayMs: Long? = null) {
        val app = ctx.peps
        val a = ctx.getSystemService(AlarmManager::class.java); val p = pi(ctx)
        if (!app.store.acct || app.cloud.forceOffline) { a.cancel(p); return }
        // Avec la connexion en direct, le service relève lui-même : l'alarme n'est plus qu'un filet de sécurité
        val d = delayMs ?: if (app.prefs.pollTest > 0) 30_000L else if (app.prefs.instantNotifs) {
            // Service arrêté pour la nuit : l'alarme réveille au plus tard à 7 h pour le relancer
            val base = PollPolicy.alarmDelayMs(nextDelay(app), LinkService.alive)
            if (!LinkService.alive) minOf(base, untilMorning() + 1_000L) else base
        } else nextDelay(app)
        val t = SystemClock.elapsedRealtime() + d
        // Alarme inexacte : Android la regroupe avec les autres réveils (bien moins coûteux qu'une alarme exacte chaque minute)
        try { a.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p) } catch (e: Exception) { a.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p) }
    }

    fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))

    private val lock = Mutex()

    /** Une relève : réseau relu, serveur interrogé, nouveautés notifiées ; le processeur reste éveillé le temps de la relève. */
    suspend fun run(app: PepsApp, src: String) {
        if (!app.store.acct || app.cloud.forceOffline) return
        if (!lock.tryLock()) return
        // Le verrou est toujours rendu, même si une lecture de l'état système échoue avant la relève
        try { runLocked(app, src) } finally { lock.unlock() }
    }

    private suspend fun runLocked(app: PepsApp, src: String) {
        // Relève toute récente (service et alarme qui se croisent) : inutile de recommencer
        if (lastRun > 0 && SystemClock.elapsedRealtime() - lastRun < (if (app.foreground) 15_000L else 40_000L) && app.prefs.pollTest == 0L) return
        // Porte globale : service, alarme et worker ne relèvent qu'une fois par intervalle (80 %), même après la mort du processus
        val wall = System.currentTimeMillis()
        if (app.prefs.pollTest == 0L && !PollGate.allow(src, wall, app.prefs.lastRunWall, interval(app))) { BgStats.add("poll.gated.$src"); return }
        app.prefs.lastRunWall = wall
        BgStats.add("poll.src.$src")
        val t0 = SystemClock.elapsedRealtime(); val rx0 = BgStats.rxPackets()
        val req0 = app.cloud.api.requests.get(); val bytes0 = app.cloud.api.receivedBytes.get()
        var wl: PowerManager.WakeLock? = null; var wlAt = 0L
        // Verrou de réveil libéré dès la fin des requêtes : l'écriture locale et le reste se font sans lui
        fun releaseWakeLock() {
            wl?.let { if (it.isHeld) { it.release(); val held = SystemClock.elapsedRealtime() - wlAt; BgStats.add("wl.ms", held); BgStats.max("wl.ms", held) } }
            wl = null
        }
        try {
            val pm = app.getSystemService(PowerManager::class.java)
            // Seulement quand rien d'autre ne tient le processeur : alarme, ou service écran éteint sur batterie (WorkManager a le sien)
            if (!app.foreground && PollPolicy.needsWakeLock(src, pm.isInteractive, EnergyState.charging(app))) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pepsgoal:poll").also { it.acquire(PollPolicy.WAKE_LOCK_MS) }
                wlAt = SystemClock.elapsedRealtime(); BgStats.add("wl.count")
            }
            val ok = withTimeoutOrNull(30_000) {
                app.cloud.navOnline = app.networkUp()
                // Niveau Rapide : le flux d'amis est relevé à chaque cycle (sinon palier lent en arrière-plan)
                app.cloud.feedEveryCycle = app.prefs.pollLevel == PollPolicy.FAST
                // La cadence n'est relancée que par un événement utile (CloudEvents), pas par un simple changement de JSON
                val fg = app.foreground
                withContext(NetSource(src)) { app.cloud.poll(if (fg) 15_000 else 40_000, foreground = fg) }
                // Widgets et montre ne dépendent que des objectifs : pas de mise à jour sur relève sociale (changements métier seulement).
                // Santé Connect et rappels intelligents ne passent plus par la relève réseau : travail local, alarmes et ouverture
                releaseWakeLock()
                app.store.flush()
                true
            }
            if (ok == null) app.cloud.pollStatus = "délai dépassé"
            val now = System.currentTimeMillis(); lastRun = SystemClock.elapsedRealtime()
            if (app.store.online) app.prefs.lastPoll = now
            val dur = lastRun - t0; val nReq = app.cloud.api.requests.get() - req0; val kb = (app.cloud.api.receivedBytes.get() - bytes0) / 1024
            BgStats.add("poll.ms", dur); BgStats.max("poll.ms", dur); BgStats.add("poll.rx.pkt", (BgStats.rxPackets() - rx0).coerceAtLeast(0))
            BgStats.add(when { ok == null -> "poll.timeout"; app.cloud.pollStatus.startsWith("erreur") || app.cloud.pollStatus == "serveur injoignable" -> "poll.fail"; app.cloud.pollStatus == "nouveautés relevées" -> "poll.news"; else -> "poll.ok" })
            val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(now))
            app.prefs.pollLog = (listOf("$hm · $src · ${app.cloud.pollStatus} · $nReq req · $kb ko · ${"%.1f".format(dur / 1000.0)} s") + app.prefs.pollLog).take(6)
            if (app.prefs.pollTest > 0 && now - app.prefs.pollTest > 20_000) {
                app.prefs.pollTest = 0
                Notifier.test(app, "✓ Relève en arrière-plan reçue ($src, ${app.cloud.pollStatus}). Les messages t’arriveront aussi application fermée.")
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            app.cloud.pollStatus = "erreur : ${e::class.simpleName}"
        } finally { releaseWakeLock() }
    }

    /** Le compte a-t-il une vie sociale (amis, conversations, groupes, ou notifications du serveur depuis 7 jours) ? */
    fun hasSocial(app: PepsApp): Boolean {
        val db = app.store.db; val since = System.currentTimeMillis() - 7 * 86_400_000L
        return db.friendships.isNotEmpty() || db.chats.isNotEmpty() || db.messages.isNotEmpty() || db.notifs.any { it.srv && it.at > since }
    }

    /** Le service de connexion en direct doit-il tourner maintenant ? (jour, cadence < 30 min, compte social, réglage actif) */
    fun shouldRunService(app: PepsApp): Boolean = app.store.acct && !app.cloud.forceOffline &&
        PollPolicy.shouldRunService(java.time.LocalTime.now().hour, interval(app), hasSocial(app), app.prefs.instantNotifs)

    /** (Re)lance le service de connexion en direct si le compte, le réglage et le moment le permettent (sinon l'arrête). */
    fun startLink(ctx: Context) {
        val app = ctx.peps
        val i = Intent(ctx, LinkService::class.java)
        // La nuit, au calme ou sans vie sociale : pas de service, l'alarme et le worker relèvent
        if (!shouldRunService(app)) { if (LinkService.alive) ctx.stopService(i); return }
        if (LinkService.alive) return
        try { ContextCompat.startForegroundService(ctx, i) } catch (e: Exception) { /* démarrage refusé hors premier plan : l'alarme prend le relais */ }
    }

    /** Millisecondes jusqu'à 7 h (relance du service de connexion en direct le matin). */
    private fun untilMorning(): Long {
        val now = java.time.ZonedDateTime.now()
        var t = now.withHour(7).withMinute(0).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return java.time.Duration.between(now, t).toMillis()
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
        BgStats.add("wake.poll")
        // Au premier plan, la boucle de l'application s'en charge déjà
        if (app.foreground) { Poller.schedule(ctx); return }
        // Le système peut autoriser à (re)lancer le service de connexion, s'il a été arrêté par le système (pas s'il tourne)
        if (!LinkService.alive) Poller.startLink(ctx)
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
    private var startedAt = 0L
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Le service relève-t-il en ce moment ? (l'alarme de secours s'espace alors, et ne le relance pas) */
        @Volatile var alive = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = peps
        try {
            ServiceCompat.startForeground(this, Notifier.LINK_ID, Notifier.link(this), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!Poller.shouldRunService(app)) { BgStats.add("fgs.policy"); stopSelf(); return START_NOT_STICKY }
        if (!alive) { alive = true; startedAt = SystemClock.elapsedRealtime(); BgStats.add("fgs.start") }
        if (job?.isActive != true) job = app.scope.launch {
            while (isActive) {
                // Prochain réveil calé sur la dernière relève (quel qu'en soit l'auteur), pas sur « maintenant + intervalle »
                val now = System.currentTimeMillis()
                val step = Poller.nextDelay(app)
                val due = maxOf(app.prefs.lastRunWall + step, now)
                // Borné à l'intervalle : une horloge reculée (dernière relève « dans le futur ») ne gèle pas la boucle
                delay((due - now).coerceIn(15_000L, maxOf(15_000L, step)))
                if (!app.foreground) Poller.run(app, "service")
                accountTime()
                // Nuit, cadence lente ou compte devenu calme : le service s'arrête, l'alarme de secours prend le relais
                if (!Poller.shouldRunService(app)) { stopSelf(); break }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        if (alive) { accountTime(); alive = false; BgStats.add("fgs.stop") }
        // L'alarme reprend à l'intervalle courant (ou à 7 h) maintenant que le service ne relève plus
        Poller.schedule(this)
        super.onDestroy()
    }

    /** Temps de service compté au fil de l'eau (un processus tué ne perd que la dernière tranche). */
    private fun accountTime() { val now = SystemClock.elapsedRealtime(); BgStats.add("fgs.ms", now - startedAt); startedAt = now }
}
