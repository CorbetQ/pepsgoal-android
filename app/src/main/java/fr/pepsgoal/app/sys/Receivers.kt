package fr.pepsgoal.app.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.cloud.openChat
import fr.pepsgoal.core.cloud.comment
import fr.pepsgoal.core.cloud.toggleLike
import fr.pepsgoal.core.cloud.openGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** Actions des notifications, des activités en cours et du widget, sans ouvrir l'application. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val VALIDATE = "fr.pepsgoal.app.VALIDATE"; const val CONFIRM = "fr.pepsgoal.app.CONFIRM"; const val REPLY = "fr.pepsgoal.app.REPLY"
        const val CHEER = "fr.pepsgoal.app.CHEER"
        const val READ = "fr.pepsgoal.app.READ"; const val TIMER = "fr.pepsgoal.app.TIMER"; const val DISMISS_LIVE = "fr.pepsgoal.app.DISMISS_LIVE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.peps; val pg = app.pg
        val nid = intent.getIntExtra("nid", 0)
        when (intent.action) {
            VALIDATE -> {
                val id = intent.getStringExtra("goal") ?: return
                when (val r = pg.validate(id)) {
                    is Pg.VResult.AskProgress, is Pg.VResult.OpenGoal -> ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(if (r is Pg.VResult.AskProgress) MainActivity.EXTRA_PROGRESS else MainActivity.EXTRA_GOAL, id))
                    else -> {}
                }
                if (nid != 0 && !intent.getBooleanExtra("keep", false)) Notifier.cancel(ctx, nid)
                // Notification du matin : elle se met à jour (case cochée)
                if (intent.getBooleanExtra("morning", false)) Notifier.morning(ctx.peps)
                LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx)
            }
            CHEER -> {
                val post = app.pg.db.posts.firstOrNull { it.id == intent.getStringExtra("post") } ?: return
                val text = intent.getStringExtra("text") ?: return
                val done = goAsync()
                app.scope.launch {
                    try { if (!post.likes.contains(pg.meId())) app.cloud.toggleLike(post); app.cloud.comment(post, text); if (nid != 0) Notifier.cancel(ctx, nid) }
                    catch (e: Exception) {}
                    finally { done.finish() }
                }
            }
            CONFIRM -> { app.cloud.confirmCheck(intent.getStringExtra("checkin") ?: return, intent.getBooleanExtra("ok", true)); if (nid != 0) Notifier.cancel(ctx, nid) }
            TIMER -> {
                val g = pg.goalById(intent.getStringExtra("goal")) ?: pg.runningTimer()?.let { pg.goalById(it.goalId) } ?: return
                pg.toggleTimer(g); LiveUpdates.refresh(ctx); Reminders.reschedule(ctx); WidgetUpdater.update(ctx)
            }
            DISMISS_LIVE -> { app.prefs.liveDismissed = intent.getStringExtra("which") + "|" + intent.getStringExtra("token"); LiveUpdates.refresh(ctx) }
            REPLY -> {
                val key = intent.getStringExtra("key") ?: return
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return
                val done = goAsync()
                app.scope.launch {
                    try {
                        val chat = key.removePrefix("g:").takeIf { key.startsWith("g:") }; val to = key.removePrefix("u:").takeIf { key.startsWith("u:") }
                        app.cloud.e2e.deliver(chat, to, JsonPrimitive(text))
                        // Les messages affichés sont considérés comme lus : on les marque côté serveur en rechargeant la conversation
                        if (chat != null) app.cloud.openGroup(chat) else if (to != null) app.cloud.openChat(to)
                        Notifier.conversation(app, key, sent = text)
                    } catch (e: Exception) { Notifier.conversation(app, key, sent = "⚠️ Envoi impossible : $text") }
                    finally { done.finish() }
                }
            }
            READ -> {
                val key = intent.getStringExtra("key") ?: return
                Notifier.clearChat(ctx, key)
                val done = goAsync()
                app.scope.launch {
                    try { if (key.startsWith("g:")) app.cloud.openGroup(key.drop(2)) else app.cloud.openChat(key.drop(2)) } catch (e: Exception) {}
                    finally { done.finish() }
                }
            }
        }
    }
}

/** Alarmes des rappels (heure exacte) : vérifie les rappels, met à jour les activités en cours, reprogramme. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.peps
        app.pg.timerTick()
        app.pg.checkReminders()
        SmartReminders.check(ctx)
        LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx); Reminders.reschedule(ctx)
    }
}

/** Redémarrage du téléphone, mise à jour de l'application, changement d'heure : les alarmes sont reprogrammées. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.reschedule(ctx); SyncWorker.schedule(ctx); Poller.schedule(ctx); Poller.startLink(ctx); LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx)
        GeoReminders.register(ctx)
    }
}
