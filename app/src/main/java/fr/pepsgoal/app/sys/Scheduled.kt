package fr.pepsgoal.app.sys

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.work.*
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.uid
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.TimeUnit

/** Message programmé : écrit maintenant, envoyé (chiffré) à l'heure choisie, même application fermée. */
@Serializable
class SchedMsg(val id: String, val key: String, val body: String, val at: Long, val preview: String)

object Scheduled {
    /** Change à chaque ajout, envoi ou annulation : les écrans se redessinent. */
    var rev by mutableIntStateOf(0)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("pepsgoal.device", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<SchedMsg> = try { JSON.decodeFromString(ListSerializer(SchedMsg.serializer()), prefs(ctx).getString("scheduled", "[]") ?: "[]") } catch (e: Exception) { emptyList() }
    private fun save(ctx: Context, l: List<SchedMsg>) { prefs(ctx).edit().putString("scheduled", JSON.encodeToString(ListSerializer(SchedMsg.serializer()), l)).apply(); rev++ }

    fun of(ctx: Context, key: String) = all(ctx).filter { it.key == key }.sortedBy { it.at }

    fun add(ctx: Context, key: String, body: JsonElement, at: Long, preview: String) {
        val m = SchedMsg(uid(), key, body.toString(), at, preview.take(90))
        save(ctx, all(ctx) + m); enqueue(ctx, m)
    }

    fun cancel(ctx: Context, id: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork("sched:$id")
        save(ctx, all(ctx).filter { it.id != id })
    }

    /** Envoi immédiat d'un message programmé. */
    fun sendNow(ctx: Context, id: String) { all(ctx).firstOrNull { it.id == id }?.let { m -> save(ctx, all(ctx).filter { it.id != id } + SchedMsg(m.id, m.key, m.body, nowMs(), m.preview)); enqueue(ctx, m, now = true) } }

    private fun enqueue(ctx: Context, m: SchedMsg, now: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<SchedWorker>()
            .setInitialDelay(if (now) 0 else (m.at - nowMs()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("id" to m.id)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("sched:${m.id}", ExistingWorkPolicy.REPLACE, req)
    }

    /** Envoie le message (appelé par le travail planifié) ; true si c'est fait ou s'il n'existe plus. */
    suspend fun deliver(ctx: Context, id: String): Boolean {
        val m = all(ctx).firstOrNull { it.id == id } ?: return true
        val app = ctx.peps
        val chat = m.key.removePrefix("g:").takeIf { m.key.startsWith("g:") }; val to = m.key.removePrefix("u:").takeIf { m.key.startsWith("u:") }
        app.cloud.e2e.deliver(chat, to, JSON.parseToJsonElement(m.body)) ?: return false
        save(ctx, all(ctx).filter { it.id != id })
        return true
    }
}

class SchedWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        return try { if (Scheduled.deliver(applicationContext, id)) Result.success() else Result.retry() } catch (e: Exception) { if (runAttemptCount < 8) Result.retry() else Result.failure() }
    }
}
