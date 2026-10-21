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
}

class PollReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.peps
        // Au premier plan, la boucle de l'application s'en charge déjà
        if (app.foreground) { Poller.schedule(ctx); return }
        val done = goAsync()
        app.scope.launch {
            try {
                withTimeoutOrNull(25_000) { if (app.cloud.poll()) { WidgetUpdater.update(ctx) } }
            } catch (e: Exception) {
            } finally { Poller.schedule(ctx); done.finish() }
        }
    }
}
