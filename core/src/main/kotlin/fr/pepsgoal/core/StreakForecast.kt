package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDate

/* ---------------------------------------------------------------- Prévisions de série */

/**
 * Un jour des 7 prochains : [risk] de casser la série (0 à 1), [level] « rest » (repos ou vacances : série protégée),
 * « low », « medium » ou « high », les raisons repérées, l'objectif le plus fragile ce jour-là et un plan B proposé à
 * l'avance (version minimale, plan B météo, autre heure).
 */
class RiskDay(val date: String, val risk: Double, val level: String, val reasons: List<String>, val due: Int,
              val goal: Goal? = null, val idea: String? = null)

/** Mauvais temps d'un jour de la prévision (orage, neige, pluie probable, forte chaleur), ou null. */
fun dayWeatherIssue(d: WxDay?): String? {
    d ?: return null
    val k = Weather.wmo(d.code).cond
    return when {
        k == "storm" -> "Orage annoncé"
        k == "snow" -> "Neige annoncée"
        (k == "rain" && (d.pop ?: 100.0) >= 50) || (d.pop ?: 0.0) >= 70 -> "Pluie annoncée"
        (d.max ?: 0.0) >= 32 -> "Forte chaleur (${Math.round(d.max!!)}°)"
        else -> null
    }
}

/** Objectifs prévus le jour [d] (y compris les routines « X fois par semaine », faisables n'importe quel jour). */
fun Pg.dueOn(d: LocalDate, id: String = meId()): List<Goal> {
    val k = ymd(d)
    return myGoals(id).filter { g -> !isClosedGoal(g) && !isPaused(g, k) && k >= ymd(g.createdAt) && (scheduled(g, d) || (g.type == "routine" && g.mode == "count")) }
}

/** Part des [n] mêmes jours de la semaine passés (avant [d]) où l'objectif prévu a été fait, ou null avec moins de 3 jours. */
fun Pg.weekdayRate(g: Goal, d: LocalDate, n: Int = 8): Double? {
    val rest = goalRest(g); val created = ymd(g.createdAt); var due = 0; var ok = 0
    for (i in 1..n) {
        val x = d.minusWeeks(i.toLong()); val k = ymd(x)
        if (k < created) break
        if (!x.isBefore(todayDate())) continue
        if (k in rest || !(scheduled(g, x) || g.type == "routine" && g.mode == "count")) continue
        due++; if (db.checkins.any { it.goalId == g.id && it.date == k && it.userId == meId() && isWin(it) }) ok++
    }
    return if (due >= 3) ok.toDouble() / due else null
}

/** Part des mêmes jours de la semaine passés (8 semaines) où au moins une validation a allumé la flamme. */
fun Pg.activeWeekdayRate(d: LocalDate): Double? {
    val id = meId(); val act = activeDays(id); val rest = restSet(id)
    val first = act.minOrNull() ?: return null
    var n = 0; var ok = 0
    for (i in 1..8) { val k = ymd(d.minusWeeks(i.toLong())); if (k < first) break; if (k >= today() || k in rest) continue; n++; if (k in act) ok++ }
    return if (n >= 3) ok.toDouble() / n else null
}

/**
 * Prévisions de la série sur [days] jours à partir de [from] : historique par jour de la semaine, objectifs souvent
 * manqués ce jour-là, météo (objectifs d'extérieur), jours de repos et vacances. [wx] : prévision de la ville.
 */
fun Pg.streakForecast(wx: Forecast? = null, days: Int = 7, from: LocalDate = todayDate()): List<RiskDay> {
    val u = me() ?: return emptyList(); val rest = restSet(u.id); val vac = vacationDays(u)
    // Activité générale des 4 dernières semaines (repli quand un jour de la semaine a trop peu d'historique)
    val act = activeDays(u.id)
    val recent = (1..28).map { ymd(todayDate().minusDays(it.toLong())) }.filter { it !in rest && it >= (act.minOrNull() ?: "9999") }
    val overall = if (recent.size >= 7) recent.count { it in act }.toDouble() / recent.size else .75
    return (0 until days).map { i ->
        val d = from.plusDays(i.toLong()); val k = ymd(d); val wd = WD_LONG[wdIdx(d)]
        if (k in rest) return@map RiskDay(k, 0.0, "rest", listOf(if (k in vac) "Vacances : série en pause" else "Jour de repos : série protégée"), 0)
        val due = dueOn(d)
        val reasons = mutableListOf<String>(); var risk = 0.0
        val rate = activeWeekdayRate(d) ?: overall
        risk += 1 - rate
        if (rate < .7 && activeWeekdayRate(d) != null) reasons += "Le $wd, ta flamme s’allume ${Math.round(rate * 100)} % du temps"
        // Objectifs souvent manqués ce jour-là
        val rates = due.associateWith { weekdayRate(it, d) }
        val weak = rates.entries.filter { (it.value ?: 1.0) < .5 }.minByOrNull { it.value ?: 1.0 }?.key
        if (weak != null) { reasons += "« ${weak.title} » souvent manqué le $wd"; risk += .1 }
        // Météo : objectifs d'extérieur un jour de mauvais temps
        val issue = dayWeatherIssue(wx?.daily?.firstOrNull { it.day == k })
        val outdoor = if (issue != null) due.filter { it.outdoor || it.planB.isNotBlank() } else emptyList()
        if (outdoor.isNotEmpty()) { reasons += "$issue pour « ${outdoor.first().title} »"; risk += .15 }
        if (due.isEmpty()) reasons += "Rien de prévu : une petite validation suffit à garder la série"
        risk = risk.coerceIn(0.0, 1.0)
        val level = when { risk >= .5 -> "high"; risk >= .3 -> "medium"; else -> "low" }
        val g = outdoor.firstOrNull() ?: weak ?: due.minByOrNull { rates[it] ?: 1.0 }
        val idea = if (level == "low" || g == null) null else planBIdea(g, outdoor.isNotEmpty())
        RiskDay(k, risk, level, reasons, due.size, g, idea)
    }
}

/** Plan B proposé à l'avance pour [g] : plan B météo, version mini, autre heure ou version minimale. */
fun Pg.planBIdea(g: Goal, badWeather: Boolean): String {
    if (badWeather && g.planB.isNotBlank()) return "Plan B : « ${g.planB} » à la place de « ${g.title} »"
    g.miniLabel?.let { return "Version mini de « ${g.title} » : $it" }
    usualTime(g)?.let { u ->
        val m = hmMinutes(u); val earlier = maxOf(7 * 60, m - 120)
        if (m - earlier >= 60) return "Avance « ${g.title} » : vise ${pad(earlier / 60)}:${pad(earlier % 60)} au lieu de $u"
    }
    return "Prévois une version minimale de « ${g.title} » (5 minutes suffisent)"
}

/**
 * Réponse du coach (« prévisions », « semaine prochaine ») : les jours à risque et leur plan B, ou une semaine calme.
 * Morceaux séparés par « · » (traduits un à un dans l'interface anglaise).
 */
fun forecastText(list: List<RiskDay>): String {
    val risky = list.filter { it.level == "high" || it.level == "medium" }.sortedByDescending { it.risk }.take(3).sortedBy { it.date }
    val rest = list.count { it.level == "rest" }
    val restLine = if (rest > 0) "${pl(rest, "jour de repos", "jours de repos")} protège ta série" else null
    if (risky.isEmpty()) return listOfNotNull("Semaine à venir au calme : aucun jour à risque repéré sur les 7 prochains jours", restLine, "Garde ton rythme !").joinToString(" · ")
    return (listOf("Jours à risque sur les 7 prochains jours") + risky.map { r ->
        val why = (r.reasons.firstOrNull() ?: "historique encore court").replaceFirstChar { c -> c.lowercase() }
        "**${weekdayLongDate(parseYmd(r.date))}** : risque ${if (r.level == "high") "élevé" else "moyen"} ($why). ${r.idea ?: "Une petite validation suffit"}."
    } + listOfNotNull(restLine)).joinToString(" · ")
}
