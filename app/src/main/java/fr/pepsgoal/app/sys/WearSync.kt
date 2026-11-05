package fr.pepsgoal.app.sys

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.target
import fr.pepsgoal.core.model.unit
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * Montre Wear OS : la journée (série, objectifs du jour, caps quotidiens) est publiée sur la couche de données
 * de Wear OS à chaque changement ; la montre renvoie ses validations par message.
 */
object WearSync {
    const val PATH_TODAY = "/pepsgoal/today"
    const val MSG_VALIDATE = "/pepsgoal/validate"
    const val MSG_CAP = "/pepsgoal/cap"
    const val MSG_REFRESH = "/pepsgoal/refresh"

    private fun snapshot(ctx: Context): String {
        val pg = ctx.peps.pg; val u = pg.me()
        val o = JSONObject().put("date", today())
        if (u == null) return o.put("signedIn", false).toString()
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id)
        val items = JSONArray()
        plan.due.sortedWith(compareBy({ it.done }, { !pg.isFav(it.g) })).take(20).forEach { d ->
            val g = d.g
            val it = JSONObject().put("id", g.id).put("title", g.shownTitle()).put("icon", g.shownIcon()).put("type", g.type).put("done", d.done).put("meta", d.meta ?: pg.metaLine(g))
            if (g.type == "cap") it.put("v", pg.capDay(g)).put("target", g.target).put("unit", g.unit).put("step", pg.capQuick(g).first())
            // Types qui demandent une saisie : ouverture sur le téléphone
            it.put("simple", g.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit"))
            items.put(it)
        }
        return o.put("signedIn", true).put("streak", st.n).put("lit", st.lit).put("rest", pg.isRest())
            .put("done", plan.due.count { it.done }).put("total", plan.due.size).put("items", items).toString()
    }

    private val lock = Mutex()
    private var lastContent: String? = null
    /** Stable payload: only an actual change or explicit watch refresh is sent. */
    suspend fun push(ctx: Context, urgent: Boolean = false, force: Boolean = false) = lock.withLock {
        try {
            val content = snapshot(ctx)
            if (!force && content == lastContent) { BgStats.add("wear.skip"); return@withLock }
            BgStats.add("wear.push")
            // Data Layer retains this item for reconnects; a disconnected watch must still receive the latest day.
            val json = JSONObject(content).put("at", nowMs()).toString()
            val req = PutDataMapRequest.create(PATH_TODAY).apply { dataMap.putString("json", json) }.asPutDataRequest()
            if (urgent) req.setUrgent()
            Wearable.getDataClient(ctx).putDataItem(req).awaitWear()
            lastContent = content // failed transfers remain eligible for retry
        } catch (e: CancellationException) { throw e } catch (e: Exception) {}
    }

}

/** Messages de la montre : valider un objectif, ajouter au cap du jour, demander la journée. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(e: MessageEvent) {
        val app = applicationContext.peps; val pg = app.pg
        val id = String(e.data, Charsets.UTF_8)
        // La logique tourne sur le fil principal, comme dans l'application
        app.scope.launch {
            if (e.path !in setOf(WearSync.MSG_VALIDATE, WearSync.MSG_CAP, WearSync.MSG_REFRESH)) return@launch
            when (e.path) {
                WearSync.MSG_VALIDATE -> pg.goalById(id)?.let { if (it.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit")) pg.validate(it.id) }
                WearSync.MSG_CAP -> pg.goalById(id)?.takeIf { it.type == "cap" }?.let { pg.capAdd(it, pg.capQuick(it).first(), silent = true) }
            }
            app.store.flush()
            LiveUpdates.refresh(app)
            WidgetUpdater.update(app, urgent = e.path != WearSync.MSG_REFRESH)
            if (e.path == WearSync.MSG_REFRESH) WearSync.push(app, force = true)
        }
    }
}

private suspend fun <T> Task<T>.awaitWear(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { if (c.isActive) c.resume(it) }
    addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
