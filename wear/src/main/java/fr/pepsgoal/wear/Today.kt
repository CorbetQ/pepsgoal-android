package fr.pepsgoal.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Journée reçue du téléphone (série, objectifs du jour, caps). */
class Item(val id: String, val title: String, val icon: String, val type: String, val done: Boolean, val meta: String, val simple: Boolean,
           val v: Double, val target: Double, val unit: String, val step: Double)
class Day(val signedIn: Boolean, val streak: Int, val lit: Boolean, val rest: Boolean, val done: Int, val total: Int, val items: List<Item>, val at: Long)

object Today {
    const val PATH = "/pepsgoal/today"
    val state = MutableStateFlow<Day?>(null)

    fun parse(raw: String?): Day? = try {
        val o = JSONObject(raw ?: return null)
        val arr = o.optJSONArray("items")
        val items = (0 until (arr?.length() ?: 0)).map { i -> val x = arr!!.getJSONObject(i)
            Item(x.optString("id"), x.optString("title"), x.optString("icon", "🎯"), x.optString("type"), x.optBoolean("done"), x.optString("meta"), x.optBoolean("simple"),
                x.optDouble("v", 0.0), x.optDouble("target", 0.0), x.optString("unit"), x.optDouble("step", 1.0))
        }
        Day(o.optBoolean("signedIn"), o.optInt("streak"), o.optBoolean("lit"), o.optBoolean("rest"), o.optInt("done"), o.optInt("total"), items, o.optLong("at"))
    } catch (e: Exception) { null }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pepsgoal.wear", Context.MODE_PRIVATE)
    fun cached(ctx: Context): Day? = parse(prefs(ctx).getString("json", null))

    fun save(ctx: Context, raw: String) {
        prefs(ctx).edit().putString("json", raw).apply()
        state.value = parse(raw)
        ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, StreakComplication::class.java)).requestUpdateAll()
    }

    /** Dernière journée publiée par le téléphone (au lancement de l'application). */
    suspend fun load(ctx: Context) {
        state.value = cached(ctx)
        try {
            val items = Wearable.getDataClient(ctx).dataItems.await()
            items.forEach { if (it.uri.path == PATH) DataMapItem.fromDataItem(it).dataMap.getString("json")?.let { j -> save(ctx, j) } }
            items.release()
        } catch (e: Exception) {}
    }

    /** Envoie une action au téléphone (valider, ajouter au cap, demander la journée). Faux si aucun téléphone n'est joignable. */
    suspend fun send(ctx: Context, path: String, id: String = ""): Boolean = try {
        val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
        nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, path, id.toByteArray(Charsets.UTF_8)).await() }
        nodes.isNotEmpty()
    } catch (e: Exception) { false }
}

/** Nouvelle journée publiée par le téléphone. */
class WearData : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == Today.PATH)
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let { Today.save(applicationContext, it) }
        }
    }
}
