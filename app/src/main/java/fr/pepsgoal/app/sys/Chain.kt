package fr.pepsgoal.app.sys

import android.content.Context
import androidx.work.*
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import java.util.concurrent.TimeUnit

/** Enchaînement d'habitudes : si l'objectif suivant n'est toujours pas fait un quart d'heure après, un rappel arrive. */
object Chain {
    private const val DELAY_MIN = 15L

    fun schedule(ctx: Context, prev: String, next: String) {
        val req = OneTimeWorkRequestBuilder<ChainWorker>().setInitialDelay(DELAY_MIN, TimeUnit.MINUTES)
            .setInputData(workDataOf("prev" to prev, "next" to next)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("chain:$next", ExistingWorkPolicy.REPLACE, req)
    }

    fun remind(ctx: Context, prev: String, next: String) {
        val pg = ctx.peps.pg; val u = pg.me() ?: return
        if (pg.isRest()) return
        val a = pg.goalById(prev) ?: return; val b = pg.goalById(next) ?: return
        if (b.after != a.id || pg.todayPlan().due.none { it.g.id == b.id && !it.done }) return
        val k = "${u.id}|${today()}|chain${b.id}"; if (pg.db.reminderLog[k] != null) return
        pg.db.reminderLog[k] = 1
        pg.notify(u.id, "Après ${a.icon} « ${a.title} », c’est le moment de ${b.icon} « ${b.title} ».", "reminder", b.id)
        pg.store.commit(false)
    }
}

class ChainWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Chain.remind(applicationContext, inputData.getString("prev") ?: return Result.success(), inputData.getString("next") ?: return Result.success())
        return Result.success()
    }
}
