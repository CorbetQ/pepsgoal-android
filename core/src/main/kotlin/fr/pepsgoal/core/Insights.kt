package fr.pepsgoal.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Corrélations simples entre la réussite du jour et le reste de la vie (sommeil, pas, sport, humeur, jour de la semaine) :
 * moyennes comparées, affichées seulement quand l'écart est net et que l'échantillon suffit.
 */
object Insights {
    class Finding(val icon: String, val text: String, val diff: Double)

    /** Facteur mesuré par jour : seuil et formulation (« Les jours où tu dors au moins 7 h »). */
    class Factor(val key: String, val icon: String, val threshold: Double, val hi: String)

    val FACTORS = listOf(
        Factor("sleep", "moon", 7.0, "Les jours où tu as dormi au moins 7 h"),
        Factor("steps", "compass", 8000.0, "Les jours où tu dépasses 8 000 pas"),
        Factor("exercise", "dumbbell", 20.0, "Les jours avec au moins 20 min de sport"),
        Factor("mood", "smile", 4.0, "Les jours où ton humeur est bonne")
    )
    const val MIN_SAMPLES = 4
    const val MIN_DIFF = .08

    /** Taux de réussite de chaque jour passé (validations / objectifs prévus), hors jours de repos et de vacances. */
    fun dayRates(pg: Pg, days: Int = 60): Map<String, Double> {
        val me = pg.meId(); val rest = pg.restSet(me); val out = LinkedHashMap<String, Double>()
        val goals = pg.myGoals(me).filter { it.userId == me && it.type in setOf("routine", "abstinence", "limit", "duration", "cap", "timed", "period", "challenge") }
        val lastCheck = pg.userCheckins(me).groupBy { it.goalId }.mapValues { e -> e.value.maxOf { it.date } }
        for (i in days downTo 1) {
            val d = todayDate().minusDays(i.toLong()); val k = ymd(d); if (k in rest) continue
            var due = 0; var done = 0
            for (g in goals) {
                if (ymd(g.createdAt) > k || !pg.scheduled(g, d)) continue
                if (g.archived && (lastCheck[g.id] ?: "") < k) continue
                due++; if (pg.hasCheck(g.id, k)) done++
            }
            if (due > 0) out[k] = done.toDouble() / due
        }
        return out
    }

    private fun pct(v: Double) = "${(v * 100).roundToInt()} %"

    /** Constats triés du plus marquant au moins marquant ; [values] : facteur → (jour → valeur). */
    fun findings(rates: Map<String, Double>, values: Map<String, Map<String, Double>>): List<Finding> {
        val out = mutableListOf<Finding>()
        for (f in FACTORS) {
            val v = values[f.key] ?: continue
            val hi = rates.filterKeys { k -> v[k]?.let { it >= f.threshold } == true }.values
            val lo = rates.filterKeys { k -> v[k]?.let { it < f.threshold } == true }.values
            if (hi.size < MIN_SAMPLES || lo.size < MIN_SAMPLES) continue
            val a = hi.average(); val b = lo.average(); if (abs(a - b) < MIN_DIFF) continue
            out += Finding(f.icon, "${f.hi}, tu réussis ${pct(a)} de tes objectifs, contre ${pct(b)} les autres jours.", a - b)
        }
        // Jour de la semaine : meilleur et plus difficile
        val byWd = rates.entries.groupBy { wdIdx(parseYmd(it.key)) }.filterValues { it.size >= 3 }.mapValues { e -> e.value.map { it.value }.average() }
        if (byWd.size >= 3) {
            val best = byWd.maxBy { it.value }; val worst = byWd.minBy { it.value }
            if (best.value - worst.value >= .12)
                out += Finding("cal", "Le ${WD_LONG[best.key]} est ton meilleur jour (${pct(best.value)}) ; le ${WD_LONG[worst.key]}, le plus difficile (${pct(worst.value)}).", best.value - worst.value)
        }
        return out.sortedByDescending { abs(it.diff) }
    }
}
