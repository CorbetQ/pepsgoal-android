package fr.pepsgoal.app.sys

import android.content.Context
import androidx.work.*
import fr.pepsgoal.app.peps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Tâche périodique (toutes les 15 minutes environ, gérée par Android) : synchronise le compte, récupère les
 * notifications et messages du serveur, vérifie les rappels et met à jour le widget et les activités en cours.
 * Le serveur PepsGoal envoie ses notifications push en Web Push uniquement : l'application native les relève ici.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.peps
        try {
            withContext(Dispatchers.Main) {
                // Le social n'est rechargé que si la relève a vu des nouveautés (poll s'en charge)
                if (app.store.acct) { if (app.cloud.poll()) Poller.lastNews = System.currentTimeMillis(); if (app.store.online) app.cloud.syncNow() }
                Poller.schedule(app)
                app.pg.timerTick(); app.pg.checkReminders()
                LiveUpdates.refresh(app); WidgetUpdater.update(app); Reminders.reschedule(app)
            }
        } catch (e: Exception) { return Result.retry() }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("pepsgoal-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
