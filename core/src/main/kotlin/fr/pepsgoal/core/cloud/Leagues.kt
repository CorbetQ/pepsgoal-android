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

/**
 * Saisons de ligue : chaque mois est une saison. Le classement de chaque semaine du mois (parmi tes amis) donne la
 * moyenne de la saison : premier tiers promu dans la division supérieure, dernier tiers relégué. La division et les
 * résultats sont gardés dans le compte (« season:AAAA-MM:division:mouvement:rang »).
 */
object LeagueSeason {
    val DIVISIONS = listOf(Triple("Bronze", "🥉", "#C9824A"), Triple("Argent", "🥈", "#B8C2CF"), Triple("Or", "🥇", "#FFC14D"), Triple("Diamant", "💎", "#7FE7FF"), Triple("Légende", "👑", "#B48CFF"))

    class Result(val season: String, val division: Int, val move: Int, val avgRank: Double)

    /** Saisons déjà closes (les plus récentes en dernier). */
    fun history(claimed: List<String>): List<Result> = claimed.filter { it.startsWith("season:") }.mapNotNull { s ->
        val p = s.split(":"); if (p.size < 5) null else Result(p[1], p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toDoubleOrNull() ?: 0.0)
    }.sortedBy { it.season }

    /** Division actuelle (Bronze au départ). */
    fun division(claimed: List<String>): Int = history(claimed).lastOrNull()?.let { (it.division + it.move).coerceIn(0, DIVISIONS.lastIndex) } ?: 0

    /** Rang relatif d'une semaine (0 : premier, 1 : dernier) ; seul dans la ligue : selon les flammes de la semaine. */
    fun weekScore(rows: List<LeagueRow>, me: String): Double? {
        val i = rows.indexOfFirst { it.userId == me }; if (i < 0) return null
        if (rows[i].points <= 0) return 1.0
        if (rows.size <= 1) return when { rows[i].points >= 350 -> 0.0; rows[i].points >= 150 -> .5; else -> 1.0 }
        return i.toDouble() / (rows.size - 1)
    }

    /** Mouvement de fin de saison selon la moyenne des semaines : +1 promu, -1 relégué, 0 maintenu. */
    fun move(scores: List<Double>, division: Int): Int {
        if (scores.isEmpty()) return if (division > 0) -1 else 0
        val avg = scores.average()
        return when { avg <= .34 && division < DIVISIONS.lastIndex -> 1; avg >= .67 && division > 0 -> -1; else -> 0 }
    }

    fun mondaysOf(season: String): List<java.time.LocalDate> {
        val first = java.time.LocalDate.parse("$season-01"); var d = fr.pepsgoal.core.weekStart(first); if (d.isBefore(first)) d = d.plusWeeks(1)
        val out = mutableListOf<java.time.LocalDate>(); while (d.monthValue == first.monthValue && d.year == first.year) { out.add(d); d = d.plusWeeks(1) }
        return out
    }
}

/**
 * Clôt la saison précédente si ce n'est pas encore fait : lit le classement de chacune de ses semaines, calcule le
 * mouvement et l'enregistre. Renvoie le résultat nouvellement enregistré (null : rien à faire ou hors ligne).
 */
suspend fun Cloud.closeLeagueSeason(): LeagueSeason.Result? {
    val u = pg.me() ?: return null; if (!store.acct || !store.online) return null
    val prev = fr.pepsgoal.core.todayDate().minusMonths(1); val key = "%04d-%02d".format(prev.year, prev.monthValue)
    if (u.shopClaimed.any { it.startsWith("season:$key:") }) return null
    val div = LeagueSeason.division(u.shopClaimed); val me = u.id
    val scores = LeagueSeason.mondaysOf(key).mapNotNull { m -> try { LeagueSeason.weekScore(leagueWeek(fr.pepsgoal.core.ymd(m)), me) } catch (e: Exception) { null } }
    val mv = LeagueSeason.move(scores, div); val avg = if (scores.isEmpty()) 1.0 else scores.average()
    u.shopClaimed = (u.shopClaimed + "season:$key:$div:$mv:${"%.2f".format(java.util.Locale.ROOT, avg)}").takeLast(400).toMutableList()
    val (dn, de, _) = LeagueSeason.DIVISIONS[(div + mv).coerceIn(0, LeagueSeason.DIVISIONS.lastIndex)]
    pg.notify(me, when (mv) { 1 -> "Saison terminée : promotion en division $dn $de !"; -1 -> "Saison terminée : retour en division $dn $de. La prochaine sera la bonne."; else -> "Saison terminée : maintien en division $dn $de." }, "badge")
    store.commit()
    return LeagueSeason.Result(key, div, mv, avg)
}
