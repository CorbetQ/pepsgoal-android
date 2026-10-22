package fr.pepsgoal.app.sys

import fr.pepsgoal.app.PepsApp
import fr.pepsgoal.app.ui.Social
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.str
import fr.pepsgoal.core.cloud.strs
import fr.pepsgoal.core.cloud.obj
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * Objectif en duo : chacun garde sa copie de l'objectif, et les jours validés s'échangent par messages chiffrés de bout en bout
 * (invisibles dans la conversation). La flamme commune ne brûle que les jours prévus validés par les deux.
 */
object Duo {
    const val INVITE = "duo"; const val JOIN = "duoj"; const val DAYS = "duod"; const val LEAVE = "duox"
    /** Fenêtre des jours envoyés à chaque mise à jour. */
    private const val WINDOW = 60L
    /** Clés propres à une copie (historique, état du jour) : jamais recopiées chez l'ami. */
    private val PRIVATE_KEYS = setOf("duo", "duoPeer", "duoPeerDays", "milestones", "monthsDone", "weeksDone", "stepsDay", "stepsDone", "after", "targetHit", "winner", "failDay", "communityId", "communityEnd", "communityStart")

    enum class State { PENDING, JOINED, LEFT }

    fun goals(app: PepsApp) = app.pg.myGoals().filter { it.userId == app.pg.meId() && it.duoId != null && it.duoPeer != null && !it.archived }

    /** Messages de service reçus de l'ami pour ce duo, du plus ancien au plus récent. */
    private fun fromPeer(app: PepsApp, g: Goal): List<Pair<Message, JsonObject>> {
        val me = app.pg.meId(); val peer = g.duoPeer ?: return emptyList(); val id = g.duoId ?: return emptyList()
        return app.pg.db.messages.filter { it.chat == null && it.from == peer && it.to == me }.sortedBy { it.at }
            .mapNotNull { m -> Social.body(app.ui, m)?.takeIf { it.str("id") == id && it.str("k") in setOf(JOIN, DAYS, LEAVE) }?.let { m to it } }
    }

    fun state(app: PepsApp, g: Goal): State {
        val l = fromPeer(app, g)
        if (l.lastOrNull()?.second?.str("k") == LEAVE) return State.LEFT
        return if (l.isNotEmpty() || g.strs("duoPeerDays").isNotEmpty()) State.JOINED else State.PENDING
    }

    /** Jours validés par l'ami : ceux déjà gardés sur l'objectif, mis à jour par chaque instantané reçu. */
    fun peerDays(app: PepsApp, g: Goal): Set<String> {
        val out = g.strs("duoPeerDays").toMutableSet()
        for ((_, b) in fromPeer(app, g)) if (b.str("k") == DAYS) {
            val w0 = b.str("w0"); val w1 = b.str("w1")
            out.removeAll { it >= w0 && it <= w1 }; out.addAll(b.strs("d"))
        }
        return out
    }

    fun streak(app: PepsApp, g: Goal): Int = app.pg.duoStreak(g, peerDays(app, g))

    /** Invite un ami sur un objectif existant. */
    suspend fun invite(app: PepsApp, g: Goal, friend: String): Boolean {
        val id = g.duoId?.takeIf { g.duoPeer == friend } ?: uid()
        val body = buildJsonObject {
            put("k", INVITE); put("id", id)
            putJsonObject("g") { put("title", g.title); put("icon", g.icon); put("type", g.type); put("data", JsonObject(g.data.filterKeys { it !in PRIVATE_KEYS })) }
        }
        app.cloud.e2e.deliver(null, friend, body) ?: return false
        g.set("duo", id); g.set("duoPeer", friend); app.pg.store.commit()
        app.prefs.raw.edit().remove("duoSent:$id").apply()
        sync(app)
        return true
    }

    /** Accepte une invitation : une copie de l'objectif est créée, reliée au duo. */
    suspend fun join(app: PepsApp, from: String, b: JsonObject): Goal? {
        val pg = app.pg; val id = b.str("id"); if (id.isEmpty()) return null
        pg.db.goals.firstOrNull { it.duoId == id && it.userId == pg.meId() }?.let { return it }
        val sg = b.obj("g") ?: return null
        val type = sg.str("type").takeIf { it in DUO_TYPES } ?: return null
        val name = pg.userById(from)?.name ?: "ton ami"
        val g = Goal(uid(), pg.meId(), type, sg.str("title").take(80).ifBlank { "Objectif en duo" }, sg.str("icon").ifBlank { "🤝" }, "En duo avec ${first(name)}.", pg.me()?.settings?.defaultVis ?: "friends", nowMs())
        (sg["data"] as? JsonObject)?.forEach { (k, v) -> if (k !in PRIVATE_KEYS) g.data[k] = v }
        if (type == "abstinence") g.set("since", today())
        g.set("duo", id); g.set("duoPeer", from)
        pg.db.goals.add(g); pg.store.commit()
        try { app.cloud.e2e.deliver(null, from, buildJsonObject { put("k", JOIN); put("id", id) }) } catch (e: Exception) {}
        sync(app)
        return g
    }

    /** Quitte le duo : l'objectif reste, sans la flamme commune. */
    suspend fun leave(app: PepsApp, g: Goal) {
        val id = g.duoId ?: return; val peer = g.duoPeer
        if (peer != null) try { app.cloud.e2e.deliver(null, peer, buildJsonObject { put("k", LEAVE); put("id", id) }) } catch (e: Exception) {}
        listOf("duo", "duoPeer", "duoPeerDays").forEach { g.data.remove(it) }; app.pg.store.commit()
    }

    private var job: Job? = null
    /** Après une validation ou de nouveaux messages : garde les jours de l'ami et envoie les miens s'ils ont changé. */
    fun syncSoon(app: PepsApp) {
        if (goals(app).isEmpty()) return
        job?.cancel(); job = app.ui.scope.launch { delay(2500); try { sync(app) } catch (e: Exception) {} }
    }

    suspend fun sync(app: PepsApp) {
        val pg = app.pg; if (!pg.store.acct || !pg.socialOn()) return
        var changed = false
        for (g in goals(app)) {
            val peer = g.duoPeer ?: continue; val id = g.duoId ?: continue
            // Jours de l'ami : conservés sur l'objectif (les vieux messages finissent par disparaître)
            val pd = peerDays(app, g).filter { it >= ymd(todayDate().minusDays(400)) }.sorted()
            if (pd != g.strs("duoPeerDays").sorted()) { g.setStrs("duoPeerDays", pd); changed = true }
            if (state(app, g) == State.LEFT || !pg.areFriends(pg.meId(), peer)) continue
            val w0 = ymd(todayDate().minusDays(WINDOW)); val w1 = today()
            val mine = pg.db.checkins.filter { it.goalId == g.id && it.kind == "done" && it.userId == pg.meId() && it.date in w0..w1 }.map { it.date }.distinct().sorted()
            val sig = "$w1|${mine.joinToString(",")}"
            if (app.prefs.raw.getString("duoSent:$id", null) == sig) continue
            val m = app.cloud.e2e.deliver(null, peer, buildJsonObject { put("k", DAYS); put("id", id); put("w0", w0); put("w1", w1); putJsonArray("d") { mine.forEach { add(it) } } })
            if (m != null) app.prefs.raw.edit().putString("duoSent:$id", sig).apply()
        }
        if (changed) pg.store.commit()
    }
}
