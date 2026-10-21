package fr.pepsgoal.app.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import fr.pepsgoal.app.peps
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Relève des notifications du serveur toutes les minutes, application fermée : le serveur PepsGoal ne parle que
 * Web Push, l'application native interroge donc elle-même les messages, publications, demandes d'amis,
 * encouragements… Une alarme « chaînée » d'une minute (exacte si Android l'autorise) réveille l'application ;
 * en mode Économie d'énergie / Doze, Android espace de lui-même les réveils (WorkManager reste le filet de sécurité).
 */
object Poller {
    const val EVERY_MS = 60_000L
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, PollReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, delayMs: Long = EVERY_MS) {
        val app = ctx.peps
        val a = ctx.getSystemService(AlarmManager::class.java); val p = pi(ctx)
        if (!app.store.acct) { a.cancel(p); return }
        val t = SystemClock.elapsedRealtime() + delayMs
        try {
            if (Reminders.canExact(ctx)) a.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p)
            else a.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p)
        } catch (e: Exception) { a.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, t, p) }
    }

    fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))

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
        val done = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(25_000) {
                    app.cloud.navOnline = app.networkUp()
                    if (app.cloud.poll()) WidgetUpdater.update(ctx)
                    if (app.store.online) app.prefs.lastPoll = System.currentTimeMillis()
                }
            } catch (e: Exception) {
            } finally { Poller.schedule(ctx); done.finish() }
        }
    }
}
