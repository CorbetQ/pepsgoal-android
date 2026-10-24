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
        val o = JSONObject().put("at", nowMs())
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

    /** Publie la journée pour la montre (sans effet si aucune montre n'est jumelée). */
    fun push(ctx: Context) {
        try {
            val req = PutDataMapRequest.create(PATH_TODAY).apply { dataMap.putString("json", snapshot(ctx)); dataMap.putLong("at", nowMs()) }
            Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
        } catch (e: Exception) {}
    }
}

/** Messages de la montre : valider un objectif, ajouter au cap du jour, demander la journée. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(e: MessageEvent) {
        val app = applicationContext.peps; val pg = app.pg
        val id = String(e.data, Charsets.UTF_8)
        // La logique tourne sur le fil principal, comme dans l'application
        app.scope.launch {
            when (e.path) {
                WearSync.MSG_VALIDATE -> pg.goalById(id)?.let { if (it.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit")) pg.validate(it.id) }
                WearSync.MSG_CAP -> pg.goalById(id)?.takeIf { it.type == "cap" }?.let { pg.capAdd(it, pg.capQuick(it).first(), silent = true) }
            }
            LiveUpdates.refresh(app); WidgetUpdater.update(app)
            WearSync.push(app)
        }
    }
}
