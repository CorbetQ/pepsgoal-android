package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/* ---------------------------------------------------------------- Rappel par objectif */

/** Heure de rappel propre à l'objectif (« HH:MM »), ou null. */
var Goal.remindAt: String? get() = strOrNull("remindAt")?.ifEmpty { null }; set(v) = set("remindAt", v ?: "")

/* ---------------------------------------------------------------- « Je n'ai pas pu » */

val MISS_REASONS = listOf("fatigue" to "😴 Fatigue", "temps" to "⏰ Pas le temps", "oubli" to "🤦 Oubli", "imprevu" to "⚡ Imprévu", "motivation" to "😶 Pas envie", "sante" to "🤒 Santé", "meteo" to "🌧️ Météo", "autre" to "💬 Autre")

/** Raisons notées pour les jours manqués (jour → raison), gardées sur l'objectif. */
fun Goal.missWhy(): Map<String, String> = (data["missWhy"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }?.toMap() ?: emptyMap()
fun Pg.setMissReason(g: Goal, date: String, reason: String?) {
    val m = g.missWhy().toMutableMap(); if (reason == null) m.remove(date) else m[date] = reason
    // On garde les 120 dernières
    g.data["missWhy"] = JsonObject(m.entries.sortedByDescending { it.key }.take(120).associate { it.key to JsonPrimitive(it.value) })
    store.commit()
}
/** Raisons les plus fréquentes (toutes ou d'un objectif), de la plus à la moins citée. */
fun Pg.missStats(goals: List<Goal> = myGoals()): List<Pair<String, Int>> =
    goals.flatMap { it.missWhy().values }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }

/* ---------------------------------------------------------------- Météo */

private val OUTDOOR = Regex("cour|run|footing|jogg|vélo|velo|marche|balade|rando|promen|natation en mer|surf|plein air|extérieur|exterieur|jardin|trottinette|roller|foot|tennis", RegexOption.IGNORE_CASE)
/** Objectif d'extérieur : réglé à la main, sinon deviné d'après le titre. */
var Goal.outdoor: Boolean get() = (data["outdoor"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: OUTDOOR.containsMatchIn(title); set(v) = set("outdoor", v)

/** Mauvais temps du jour (pluie, orage, neige, forte chaleur), ou null. */
fun weatherIssue(fc: Forecast?): String? {
    val d = fc?.daily?.firstOrNull() ?: return null
    val k = Weather.wmo(d.code).cond
    return when {
        k == "storm" -> "Orage annoncé"
        k == "snow" -> "Neige annoncée"
        k == "rain" && (d.pop ?: 100.0) >= 50 -> "Pluie annoncée"
        (d.max ?: 0.0) >= 32 -> "Forte chaleur (${Math.round(d.max!!)}°)"
        else -> null
    }
}
/** Objectifs d'extérieur encore à faire aujourd'hui, quand la météo s'y prête mal. */
fun Pg.weatherAdaptable(fc: Forecast?): List<Goal> {
    if (weatherIssue(fc) == null) return emptyList()
    return todayPlan().due.filter { !it.done && (it.g.outdoor || it.g.planB.isNotBlank()) && today() !in it.g.strs("skipDays") }.map { it.g }
}
/** Décale l'objectif à demain : aujourd'hui ne compte pas pour sa série (une fois par semaine au plus). */
fun Pg.skipToday(g: Goal): String? {
    val wk = ymd(todayDate().minusDays(6))
    if (g.strs("skipDays").any { it >= wk }) return "Déjà décalé cette semaine."
    g.setStrs("skipDays", (g.strs("skipDays").filter { it >= ymd(todayDate().minusDays(400)) } + today()).distinct())
    store.commit(); hooks.liveChanged(); return null
}

/* ---------------------------------------------------------------- Validation automatique sur place */

/** Emplacement enregistré où rester valide l'objectif, et durée sur place (minutes). */
var Goal.autoPlace: String? get() = strOrNull("autoPlace")?.ifEmpty { null }; set(v) = set("autoPlace", v ?: "")
var Goal.autoMin: Int get() = num("autoMin", 30.0).toInt(); set(v) = set("autoMin", v)
val AUTO_TYPES = setOf("routine", "timed", "period", "challenge")

/* ---------------------------------------------------------------- Récompenses */

/** Progression vers une récompense : série de l'objectif choisi, sinon série globale. */
fun Pg.rewardProgress(r: Reward): Int = r.goalId?.let { goalById(it) }?.let { goalStreak(it) } ?: globalStreak(meId()).n
/** Récompenses atteintes à l'instant (à célébrer) : marquées pour ne l'être qu'une fois. */
fun Pg.checkRewards(): List<Reward> {
    val u = me() ?: return emptyList(); val out = mutableListOf<Reward>()
    for (r in u.rewards) if (r.reachedAt == null && r.claimedAt == null && rewardProgress(r) >= r.target) { r.reachedAt = nowMs(); out += r }
    if (out.isNotEmpty()) store.commit()
    return out
}

/* ---------------------------------------------------------------- Plan du dimanche */

class Suggestion(val goal: Goal, val lighten: Boolean, val text: String, val apply: () -> Unit)

/** Taux de réussite d'un objectif sur les [n] derniers jours prévus (null si trop peu de jours). */
fun Pg.rateOver(g: Goal, n: Int): Double? {
    val created = ymd(g.createdAt); val rest = goalRest(g); var due = 0; var ok = 0
    for (i in 1..n) { val d = todayDate().minusDays(i.toLong()); val k = ymd(d); if (k < created) break; if (k in rest || !scheduled(g, d)) continue; due++; if (hasCheck(g.id, k)) ok++ }
    return if (due >= 6) ok.toDouble() / due else null
}

/** 3 ajustements au plus : alléger ce qui coince (moins de 45 % sur 4 semaines), relever ce qui roule (95 % et plus). */
fun Pg.weeklySuggestions(): List<Suggestion> {
    val out = mutableListOf<Suggestion>()
    for (g in myGoals().filter { it.userId == meId() && !isClosedGoal(it) && it.duoId == null }) {
        val r = rateOver(g, 28) ?: continue
        val low = r < .45; val high = r >= .95
        if (!low && !high) continue
        val pc = "${Math.round(r * 100)} %"
        val s: Suggestion? = when (g.type) {
            "routine" -> if (g.mode == "days") {
                if (low && g.days.size > 2) {
                    // On retire le jour le moins réussi
                    val worst = g.days.minByOrNull { wd -> (1..28).count { i -> val d = todayDate().minusDays(i.toLong()); wdIdx(d) == wd && hasCheck(g.id, ymd(d)) } }!!
                    Suggestion(g, true, "« ${g.title} » : $pc de réussite. Le retirer le ${WD_LONG[worst]} ?") { g.days = g.days - worst; store.commit() }
                } else if (high && g.days.size < 7) Suggestion(g, false, "« ${g.title} » : $pc, tu es au top. L’ajouter un jour de plus ?") {
                    g.days = (g.days + (0..6).first { it !in g.days }).sorted(); store.commit() } else null
            } else {
                if (low && g.perWeek > 1) Suggestion(g, true, "« ${g.title} » : $pc. Passer à ${g.perWeek - 1} fois par semaine ?") { g.perWeek = g.perWeek - 1; store.commit() }
                else if (high && g.perWeek < 7) Suggestion(g, false, "« ${g.title} » : $pc. Passer à ${g.perWeek + 1} fois par semaine ?") { g.perWeek = g.perWeek + 1; store.commit() } else null
            }
            "cap" -> { val t = g.target; val nt = if (low) Math.round(t * .8 * 10) / 10.0 else Math.round(t * 1.1 * 10) / 10.0
                Suggestion(g, low, "« ${g.title} » : $pc. ${if (low) "Viser" else "Monter à"} ${fmtNum(nt)}${unitSfx(g)} par jour ?") { g.target = nt; store.commit() } }
            "duration" -> { val m = g.minutes; val nm = if (low) maxOf(5.0, Math.round(m * .75).toDouble()) else Math.round(m * 1.1).toDouble()
                Suggestion(g, low, "« ${g.title} » : $pc. Passer à ${fmtNum(nm)} min par jour ?") { g.minutes = nm; store.commit() } }
            "limit" -> if (low) Suggestion(g, true, "« ${g.title} » : $pc sous la limite. L’assouplir à ${fmtNum(g.max + 1)}${unitSfx(g)} ?") { g.max = g.max + 1; store.commit() }
                else if (g.max >= 1) Suggestion(g, false, "« ${g.title} » : $pc. La resserrer à ${fmtNum(g.max - 1)}${unitSfx(g)} ?") { g.max = g.max - 1; store.commit() } else null
            else -> null
        }
        if (s != null) out += s
    }
    // Les objectifs qui coincent d'abord
    return out.sortedByDescending { it.lighten }.take(3)
}

/** Lundi de la semaine en cours. */
fun weekKey(): String = ymd(weekStart(todayDate()))

/* ---------------------------------------------------------------- Version mini */

/** Version réduite de l'habitude (« 1 page » au lieu de 20) : garde la série, rapporte moitié moins de flammes. */
var Goal.miniLabel: String? get() = strOrNull("mini")?.ifEmpty { null }; set(v) = set("mini", v ?: "")
val MINI_TYPES = setOf("routine", "timed", "period", "challenge")
fun Pg.isMiniDone(g: Goal, date: String = stepDate(g)) = db.checkins.any { it.goalId == g.id && it.date == date && it.kind == "done" && it.userId == meId() && it.value < 1.0 }

/** Valide la version mini du jour. Renvoie un message d'erreur, ou null. */
fun Pg.validateMini(id: String): String? {
    val g = goalById(id) ?: return null; val k = stepDate(g)
    if (g.type !in MINI_TYPES || g.miniLabel == null) return null
    if (hasCheck(g.id, k)) return "Déjà validé aujourd’hui."
    if (todayPlan().due.none { it.g.id == g.id }) return "Cet objectif n’est pas prévu aujourd’hui."
    val lit = globalStreak(meId()).lit
    val pts = maxOf(1, typeInfo(g.type).pts / 2)
    addCheck(g, "done", 0.5, pts, k); justDone = g.id
    store.commit(); afterValidation(g, pts, lit); store.commit(); checkPerfect()
    return null
}
