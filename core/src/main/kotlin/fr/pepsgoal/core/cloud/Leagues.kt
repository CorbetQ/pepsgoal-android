package fr.pepsgoal.core.cloud

import kotlinx.serialization.json.*

/** Ligne de la ligue de la semaine (serveur : /v1/leagues/week). */
class LeagueRow(val userId: String, val name: String, val avatar: JsonObject, val points: Int, val days: Int)

/** Paliers de ligue selon les flammes gagnées dans la semaine. */
object League {
    val TIERS = listOf(Triple("Bronze", "🥉", 0), Triple("Argent", "🥈", 150), Triple("Or", "🥇", 350), Triple("Diamant", "💎", 700))
    fun tier(points: Int) = TIERS.last { points >= it.third }
    fun next(points: Int) = TIERS.firstOrNull { points < it.third }
}

suspend fun Cloud.leagueWeek(from: String): List<LeagueRow> = (api.get("/v1/leagues/week?from=$from") as? JsonArray)?.map { r ->
    val o = r.jsonObject; val u = o.obj("user")
    LeagueRow(u.str("id"), u.str("displayName"), u?.get("avatar") as? JsonObject ?: JsonObject(emptyMap()), o["points"]?.jsonPrimitive?.intOrNull ?: 0, o["days"]?.jsonPrimitive?.intOrNull ?: 0)
} ?: emptyList()
