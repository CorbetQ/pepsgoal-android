package fr.pepsgoal.app.ui

import android.content.Context
import fr.pepsgoal.app.AppState
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.Msg
import fr.pepsgoal.core.cloud.forecast
import fr.pepsgoal.core.cloud.readAllNotifications
import fr.pepsgoal.core.cloud.str
import fr.pepsgoal.core.cloud.strs
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.Place
import fr.pepsgoal.core.model.Post
import kotlinx.serialization.json.JsonObject

/** Météo de la ville choisie : cache de 15 minutes sur l'appareil. */
object Wx {
    private var mem: WxCache? = null
    var error: String? = null
    private var failAt = 0L
    private var loading = false

    fun cached(ctx: Context, p: Place?): WxCache? {
        if (p == null) return null
        val key = "${p.lat},${p.lon}"
        mem?.let { if (it.key == key) return it }
        val raw = ctx.getSharedPreferences("pepsgoal.device", Context.MODE_PRIVATE).getString("wx", null) ?: return null
        return try { JSON.decodeFromString(WxCache.serializer(), raw).takeIf { it.key == key }?.also { mem = it } } catch (e: Exception) { null }
    }

    suspend fun ensure(ctx: Context, ui: AppState, force: Boolean) {
        val p = ui.pg.me()?.settings?.weather ?: return
        if (loading) return
        val c = cached(ctx, p)
        if (!force && c != null && nowMs() - c.at < 15 * 60_000) return
        if (!ui.pg.store.online || (!force && nowMs() - failAt < 60_000)) return
        loading = true
        try {
            val d = ui.cloud.forecast(p.lat, p.lon)
            val w = WxCache("${p.lat},${p.lon}", d, nowMs()); mem = w; error = null; failAt = 0
            ctx.getSharedPreferences("pepsgoal.device", Context.MODE_PRIVATE).edit().putString("wx", JSON.encodeToString(WxCache.serializer(), w)).apply()
        } catch (e: Exception) { error = e.message ?: "Météo indisponible"; failAt = nowMs() }
        finally { loading = false; ui.bump() }
    }

    fun clear(ctx: Context) { mem = null; ctx.getSharedPreferences("pepsgoal.device", Context.MODE_PRIVATE).edit().remove("wx").apply() }
}

/** Messagerie : conversations, non-lus, aperçus (fonctions de la version web). */
object Social {
    fun body(ui: AppState, m: Message): JsonObject? = if (m.e2e == null) Msg.body(m.text) else Msg.body(ui.cloud.e2e.cache[m.id])
    fun pending(ui: AppState, m: Message) = m.e2e != null && !ui.cloud.e2e.cache.containsKey(m.id)
    fun preview(ui: AppState, m: Message) = Msg.preview(m, ui.cloud.e2e.cache[m.id], pending(ui, m))
    private fun kind(ui: AppState, m: Message) = body(ui, m)?.str("k")
    fun hiddenFrom(ui: AppState, m: Message) = m.from != ui.pg.meId() && ui.pg.blockedBy(ui.pg.meId(), m.from)
    fun convo(ui: AppState, a: String, b: String) = ui.pg.db.messages.filter { it.chat == null && ((it.from == a && it.to == b) || (it.from == b && it.to == a)) }.sortedBy { it.at }
    fun groupConvo(ui: AppState, gid: String) = ui.pg.db.messages.filter { it.chat == gid && !hiddenFrom(ui, it) }.sortedBy { it.at }
    fun isUnread(ui: AppState, m: Message): Boolean {
        val me = ui.pg.meId()
        if (kind(ui, m) in Msg.SILENT) return false
        return if (m.chat != null) m.from != me && !m.readBy.contains(me) && !hiddenFrom(ui, m) && ui.pg.db.chats.any { it.id == m.chat && it.members.contains(me) } else m.to == me && !m.read
    }
    fun unreadFrom(ui: AppState, other: String) = ui.pg.db.messages.count { it.chat == null && it.to == ui.pg.meId() && it.from == other && !it.read }
    fun unreadGroup(ui: AppState, gid: String) = ui.pg.db.messages.count { it.chat == gid && isUnread(ui, it) }
    fun unreadMsgs(ui: AppState) = ui.pg.db.messages.count { isUnread(ui, it) && (it.chat != null || (ui.pg.userById(it.from) != null && !ui.pg.blockedBy(ui.pg.meId(), it.from))) }
    fun readNotifs(ui: AppState) { if (ui.pg.store.acct) ui.cloud.readAllNotifications() else { ui.pg.db.notifs.forEach { it.read = true }; ui.pg.store.commit() } }

    /** Réactions d'une conversation : la dernière réaction de chaque personne pour chaque emoji fait foi. */
    fun reactions(ui: AppState, list: List<Message>): Map<String, Map<String, Set<String>>> {
        val map = LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>>()
        for (m in list) {
            val b = body(ui, m) ?: continue
            if (b.str("k") != "react" || b.str("p").isEmpty() || b.str("e").isEmpty()) continue
            val per = map.getOrPut(b.str("p")) { LinkedHashMap() }; val set = per.getOrPut(b.str("e")) { LinkedHashSet() }
            if ((b["on"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "false") set.remove(m.from) else set.add(m.from)
        }
        return map
    }

    /** Votes d'un sondage de la messagerie : dernier vote de chaque participant. */
    fun pollVotes(ui: AppState, poll: Message, list: List<Message>): Map<String, List<String>> {
        val by = LinkedHashMap<String, List<String>>()
        for (x in list) { if (x.at < poll.at) continue; val b = body(ui, x) ?: continue; if (b.str("k") == "vote" && b.str("p") == poll.id) by[x.from] = b.strs("o") }
        return by
    }
}

/** Modération : éléments en attente. */
object Mod {
    fun pending(ui: AppState) = ui.pg.db.posts.filter { it.status == "pending" }.sortedBy { it.at }
    fun openAppeals(ui: AppState) = ui.pg.db.appeals.filter { it.status == "pending" && ui.pg.userById(it.userId) != null && it.id != "appeal-${ui.pg.meId()}" }
    fun openReports(ui: AppState): List<Pair<Post, List<fr.pepsgoal.core.model.Report>>> =
        ui.pg.db.reports.filter { !it.closed }.groupBy { it.postId }.mapNotNull { (pid, rs) -> ui.pg.db.posts.firstOrNull { it.id == pid }?.let { it to rs } }.sortedByDescending { it.second.size }
    fun count(ui: AppState) = pending(ui).size + openAppeals(ui).size + openReports(ui).size
}
