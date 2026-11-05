package fr.pepsgoal.app.sys

import android.content.Context
import androidx.work.*
import fr.pepsgoal.app.peps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** Local deadlines run offline; transfers have their own network-constrained work. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        withContext(Dispatchers.Main) {
            val app = applicationContext.peps
            app.pg.timerTick(); app.pg.checkReminders()
            Health.sync(app)
            LiveUpdates.refresh(app); WidgetUpdater.update(app); Reminders.reschedule(app)
            app.store.flush()
        }
        Result.success()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.retry() }

    companion object {
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val local = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("pepsgoal-sync", ExistingPeriodicWorkPolicy.UPDATE, local)
            if (!ctx.peps.store.acct || ctx.peps.cloud.forceOffline) {
                wm.cancelUniqueWork("pepsgoal-network-sync")
                return
            }
            val network = PeriodicWorkRequestBuilder<NetworkSyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("pepsgoal-network-sync", ExistingPeriodicWorkPolicy.UPDATE, network)
        }
    }
}

class NetworkSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val ok = withContext(Dispatchers.Main) {
            val app = applicationContext.peps
            if (app.store.acct && !app.cloud.forceOffline) {
                withTimeout(60_000) {
                    Poller.run(app, "worker")
                    if (app.store.online) app.cloud.syncNow()
                    app.store.flush()
                }
                Poller.schedule(app)
                app.store.online && app.cloud.syncError.isEmpty()
            } else true
        }
        if (ok) Result.success() else Result.retry()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.retry() }
}
