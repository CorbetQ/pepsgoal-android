package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDate

/* ---------------------------------------------------------------- Tableau de bord « Mon mois » */

/**
 * Tendance d'un objectif sur le mois : validations, jours prévus (null pour les types sans calendrier), réussite et
 * celle du mois précédent ; [trend] : +1 en hausse, -1 en baisse, 0 stable (écart de moins de 5 points).
 */
class GoalTrend(val goal: Goal, val done: Int, val planned: Int?, val rate: Double?, val prevDone: Int, val prevRate: Double?) {
    val trend: Int get() = trendOf(rate ?: done.toDouble(), prevRate ?: prevDone.toDouble(), if (rate != null && prevRate != null) .05 else .5)
}

/** Bilan d'un mois (« AAAA-MM ») comparé au mois précédent. Les jours après aujourd'hui ne comptent pas. */
class MonthReport(
    val key: String, val first: LocalDate, val last: LocalDate,
    /** Validations par jour (« AAAA-MM-JJ » → nombre). */
    val perDay: Map<String, Int>,
    val n: Int, val prevN: Int, val active: Int, val prevActive: Int, val pts: Int, val prevPts: Int, val perfect: Int, val prevPerfect: Int,
    val star: GoalTrend?, val goals: List<GoalTrend>,
    /** Meilleur jour (date, validations), jour de la semaine le plus actif (0 = lundi), heure moyenne de validation. */
    val bestDay: Pair<String, Int>?, val bestWd: Int?, val avgTime: String?, val prevAvgTime: String?,
)

/** +1, -1 ou 0 selon l'écart entre [a] et [b] (au-delà de [eps]). */
fun trendOf(a: Double, b: Double, eps: Double = 0.0): Int = when { a - b > eps -> 1; b - a > eps -> -1; else -> 0 }
/** Flèche de comparaison : ↑, ↓ ou =. */
fun arrowOf(t: Int) = when (t) { 1 -> "↑"; -1 -> "↓"; else -> "=" }
/** Écart en pourcentage (« +12 % », « −30 % ») ; null sans base de comparaison. */
fun pctDelta(a: Int, b: Int): String? = if (b == 0) null else Math.round((a - b) * 100.0 / b).let { if (it >= 0) "+$it %" else "−${-it} %" }

/** Mois disponibles (« AAAA-MM ») : de la première validation (ou de la création du compte) au mois en cours. */
fun Pg.reportMonths(): List<String> {
    val me = meId(); val firstCk = userCheckins(me).minOfOrNull { it.date }
    val created = me()?.createdAt?.takeIf { it > 0 }?.let { ymd(it) }
    var d = parseYmd(listOfNotNull(firstCk, created, today()).min()).withDayOfMonth(1); val end = todayDate().withDayOfMonth(1)
    val out = mutableListOf<String>()
    while (!d.isAfter(end) && out.size < 120) { out += ymd(d).substring(0, 7); d = d.plusMonths(1) }
    return out
}

/** Bilan du mois [key] (« AAAA-MM »). */
fun Pg.monthReport(key: String): MonthReport {
    val me = meId(); val first = parseYmd("$key-01"); val last0 = first.withDayOfMonth(first.lengthOfMonth())
    val last = if (last0.isAfter(todayDate())) todayDate() else last0
    val pFirst = first.minusMonths(1); val pLast = pFirst.withDayOfMonth(pFirst.lengthOfMonth())
    val wins = userCheckins(me).filter { isWin(it) }
    fun inRange(c: Checkin, a: LocalDate, b: LocalDate) = c.date >= ymd(a) && c.date <= ymd(b)
    val cur = wins.filter { inRange(it, first, last) }; val prev = wins.filter { inRange(it, pFirst, pLast) }
    val perDay = cur.groupingBy { it.date }.eachCount()
    val u = me()
    fun perfectIn(a: LocalDate, b: LocalDate) = u?.perfectDays?.count { it >= ymd(a) && it <= ymd(b) } ?: 0
    fun avgTime(l: List<Checkin>): String? {
        val mins = l.filter { it.at > 0 }.map { val t = localDateTime(it.at); t.hour * 60 + t.minute }
        if (mins.size < 3) return null
        val m = mins.average().toInt(); return "${pad(m / 60)}:${pad(m % 60)}"
    }
    // Tendances : réussite (jours prévus) ou nombre de validations pour les types sans calendrier
    val goals = myGoals(me).filter { g -> g.userId == me && ymd(g.createdAt) <= ymd(last) && (!isClosedGoal(g) || cur.any { it.goalId == g.id }) }.map { g ->
        val rest = goalRest(g)
        fun planned(a: LocalDate, b: LocalDate): Int? {
            if (!hasCalendar(g.type) && g.type != "challenge") return null
            var n = 0; var d = maxOf(a, parseYmd(ymd(g.createdAt)))
            while (!d.isAfter(b)) { if (ymd(d) !in rest && scheduled(g, d)) n++; d = d.plusDays(1) }
            return n
        }
        val done = cur.filter { it.goalId == g.id }.map { it.date }.toSet().size
        val pDone = prev.filter { it.goalId == g.id }.map { it.date }.toSet().size
        val pl = planned(first, last); val ppl = planned(pFirst, pLast)
        GoalTrend(g, done, pl, pl?.takeIf { it > 0 }?.let { minOf(1.0, done.toDouble() / it) }, pDone, ppl?.takeIf { it > 0 }?.let { minOf(1.0, pDone.toDouble() / it) })
    }.sortedWith(compareByDescending<GoalTrend> { it.rate ?: -1.0 }.thenByDescending { it.done })
    // Objectif star : le plus validé du mois (à égalité, la meilleure réussite)
    val star = goals.filter { it.done > 0 }.maxWithOrNull(compareBy<GoalTrend> { it.done }.thenBy { it.rate ?: 0.0 })
    val bestDay = perDay.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.let { it.key to it.value }
    val byWd = IntArray(7); cur.forEach { byWd[wdIdx(parseYmd(it.date))]++ }
    return MonthReport(key, first, last, perDay,
        cur.size, prev.size, perDay.size, prev.map { it.date }.toSet().size, cur.sumOf { it.pts }, prev.sumOf { it.pts },
        perfectIn(first, last), perfectIn(pFirst, pLast), star, goals,
        bestDay, byWd.indices.maxByOrNull { byWd[it] }?.takeIf { byWd[it] > 0 }, avgTime(cur), avgTime(prev))
}

/** Niveau de chaleur (0 à 4) d'un jour à [n] validations, [max] étant le maximum du mois. */
fun heatLevel(n: Int, max: Int): Int = if (n <= 0) 0 else (1 + 3 * n / maxOf(1, max)).coerceIn(1, 4)
