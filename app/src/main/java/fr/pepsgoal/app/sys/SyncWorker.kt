package fr.pepsgoal.app.sys

import android.content.Context
import androidx.work.*
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.LocalWorkPolicy
import fr.pepsgoal.core.NetSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** Local deadlines run offline; transfers have their own network-constrained work. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext.peps
        withContext(Dispatchers.Main) { BgStats.add("wake.sync"); app.pg.timerTick(); app.pg.checkReminders() }
        // Santé Connect hors du fil principal (seule la validation repasse dessus), et seulement s'il existe des liens
        if (Health.links(app).isNotEmpty()) Health.sync(app)
        withContext(Dispatchers.Main) {
            LiveUpdates.refresh(app); WidgetUpdater.update(app); Reminders.reschedule(app)
            app.store.flush()
        }
        // Nouvelle version publiée : vérifiée aussi application fermée (au plus toutes les 6 h, voir Updater.autoCheck)
        try { Updater.autoCheck(app) } catch (e: Exception) {}
        Result.success()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.retry() }

    companion object {
        /** Version de la configuration des travaux : à changer quand les contraintes changent (ré-enfilage UPDATE). */
        private const val CONFIG = "3"

        /**
         * Période du travail local : filet de sécurité des échéances (déjà portées par l'alarme des rappels), 30 min s'il
         * existe un lien Santé (validation automatique), 60 min sinon.
         */
        fun periodMinutes(hasHealthLinks: Boolean) = LocalWorkPolicy.periodMinutes(hasHealthLinks)

        /**
         * Appelé à chaque changement de connexion ou de session et au démarrage : une configuration identique n'est pas
         * ré-enfilée (KEEP) ; UPDATE seulement quand la période ou les contraintes changent.
         */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx); val app = ctx.peps
            val localMin = periodMinutes(Health.links(app).isNotEmpty())
            val net = app.store.acct && !app.cloud.forceOffline
            val sig = "$CONFIG|$localMin|$net"
            val policy = if (app.prefs.workConfig == sig) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE
            val local = PeriodicWorkRequestBuilder<SyncWorker>(localMin, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
            wm.enqueueUniquePeriodicWork("pepsgoal-sync", policy, local)
            if (!net) {
                wm.cancelUniqueWork("pepsgoal-network-sync")
                app.prefs.workConfig = sig
                return
            }
            // Filet de sécurité de la relève : 15 min (minimum de WorkManager), réseau disponible et batterie non faible
            val network = PeriodicWorkRequestBuilder<NetworkSyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("pepsgoal-network-sync", policy, network)
            app.prefs.workConfig = sig
        }
    }
}

class NetworkSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val ok = withContext(Dispatchers.Main) {
            val app = applicationContext.peps
            BgStats.add("wake.netsync")
            if (app.store.acct && !app.cloud.forceOffline) {
                withTimeout(60_000) {
                    // Relève soumise à la porte globale : rien si le service ou l'alarme vient de relever
                    Poller.run(app, "worker")
                    // Synchronisation seulement s'il y a des modifications locales ou si le dernier tirage date de plus de 30 min
                    if (app.store.online && app.cloud.syncDue()) withContext(NetSource("worker")) { app.cloud.syncNow() }
                    app.store.flush()
                }
                Poller.schedule(app)
                app.store.online && app.cloud.syncError.isEmpty()
            } else true
        }
        if (ok) Result.success() else Result.retry()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.retry() }
}
