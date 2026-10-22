package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.days
import fr.pepsgoal.core.model.mode
import fr.pepsgoal.core.model.str
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.today
import fr.pepsgoal.core.uid
import kotlinx.serialization.json.*

/** Défi de la communauté (serveur : /v1/challenges). */
class CommunityChallenge(
    val id: String, val title: String, val emoji: String, val description: String, val startsOn: String, val endsOn: String,
    val participants: Int, val joined: Boolean, val myDays: List<String>, val totalDays: Int = 0, val board: List<BoardRow> = emptyList()
) {
    val running get() = today() in startsOn..endsOn
    val upcoming get() = today() < startsOn
    val ended get() = today() > endsOn
}
class BoardRow(val rank: Int, val userId: String, val name: String, val username: String, val avatar: JsonObject, val days: Int)

private fun JsonElement.toChallenge(): CommunityChallenge {
    val o = jsonObject
    return CommunityChallenge(o.str("id"), o.str("title"), o.str("emoji", "🏁"), o.str("description"), o.str("startsOn"), o.str("endsOn"),
        o["participants"]?.jsonPrimitive?.intOrNull ?: 0, o["joined"]?.jsonPrimitive?.booleanOrNull ?: false, o.strs("myDays"),
        o["totalDays"]?.jsonPrimitive?.intOrNull ?: 0,
        (o["board"] as? JsonArray)?.map { r -> val ro = r.jsonObject; val u = ro.obj("user")
            BoardRow(ro["rank"]?.jsonPrimitive?.intOrNull ?: 0, u.str("id"), u.str("displayName"), u.str("username"), u?.get("avatar") as? JsonObject ?: JsonObject(emptyMap()), ro["days"]?.jsonPrimitive?.intOrNull ?: 0)
        } ?: emptyList())
}

suspend fun Cloud.challenges(): List<CommunityChallenge> = (api.get("/v1/challenges") as? JsonArray)?.map { it.toChallenge() } ?: emptyList()
suspend fun Cloud.challenge(id: String): CommunityChallenge = api.get("/v1/challenges/$id").toChallenge()
suspend fun Cloud.joinChallenge(id: String) { api.post("/v1/challenges/$id/join") }
suspend fun Cloud.leaveChallenge(id: String) { api.delete("/v1/challenges/$id/join") }
suspend fun Cloud.challengeDay(id: String, day: String, on: Boolean) { if (on) api.put("/v1/challenges/$id/days/$day", JsonObject(emptyMap())) else api.delete("/v1/challenges/$id/days/$day") }
suspend fun Cloud.createChallenge(title: String, emoji: String, description: String, startsOn: String, endsOn: String): CommunityChallenge =
    api.post("/v1/admin/challenges", buildJsonObject { put("title", title); put("emoji", emoji); put("description", description); put("startsOn", startsOn); put("endsOn", endsOn) }).toChallenge()
suspend fun Cloud.deleteChallenge(id: String) { api.delete("/v1/admin/challenges/$id") }

/** Objectif local relié à un défi de la communauté : une routine quotidienne marquée par l'identifiant du défi. */
val Goal.communityId: String? get() = str("communityId").ifEmpty { null }

/**
 * Rejoint un défi : inscription sur le serveur et routine quotidienne locale (validée comme les autres objectifs) ;
 * chaque validation pendant la période du défi est ensuite reportée au classement par [pushChallengeDays].
 */
suspend fun Cloud.joinAsGoal(c: CommunityChallenge): Goal {
    joinChallenge(c.id)
    db.goals.firstOrNull { it.communityId == c.id && it.userId == meId() }?.let { it.archived = false; store.commit(); return it }
    val g = Goal(uid(), meId(), "routine", c.title.take(80), c.emoji.ifBlank { "🏁" }, "Défi de la communauté, jusqu’au ${fr.pepsgoal.core.fmtDate(c.endsOn)}.", "friends", nowMs())
    g.mode = "days"; g.days = (0..6).toList()
    g.data["communityId"] = JsonPrimitive(c.id); g.data["communityEnd"] = JsonPrimitive(c.endsOn); g.data["communityStart"] = JsonPrimitive(c.startsOn)
    db.goals.add(g); store.commit()
    return g
}

/** Reporte au serveur les jours validés (et retirés) des objectifs reliés à un défi, puis archive ceux qui sont terminés. */
suspend fun Cloud.pushChallengeDays(list: List<CommunityChallenge>? = null): Int {
    val goals = db.goals.filter { it.userId == meId() && it.communityId != null }
    if (goals.isEmpty()) return 0
    val all = list ?: challenges(); var n = 0
    for (g in goals) {
        val c = all.firstOrNull { it.id == g.communityId } ?: continue
        if (!c.joined) continue
        val mine = db.checkins.filter { it.goalId == g.id && it.kind == "done" && it.date in c.startsOn..c.endsOn }.map { it.date }.toSet()
        for (d in mine - c.myDays.toSet()) { try { challengeDay(c.id, d, true); n++ } catch (e: Exception) {} }
        for (d in c.myDays.toSet() - mine) { try { challengeDay(c.id, d, false); n++ } catch (e: Exception) {} }
        if (c.ended && !g.archived) { g.archived = true; store.commit() }
    }
    return n
}
