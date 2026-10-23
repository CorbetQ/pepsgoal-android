package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/* ---------------------------------------------------------------- Statut du jour */

/** Statuts proposés : un emoji et quelques mots, visibles par les amis pendant 24 h. */
val STATUS_PRESETS = listOf(
    "😊" to "En forme", "💪" to "Motivé", "🔥" to "En feu", "🧘" to "Zen", "📚" to "Concentré",
    "😴" to "Fatigué", "🤒" to "Malade", "🏖️" to "En vacances", "🎉" to "Jour de fête", "🌧️" to "Jour sans"
)

class DayStatus(val e: String, val l: String, val at: Long, val storyId: String)

/** Statut du jour d'une personne (le plus récent de moins de 24 h), ou null. */
fun Pg.statusOf(id: String?): DayStatus? {
    if (id == null) return null
    val s = db.stories.filter { it.kind == "status" && it.userId == id && nowMs() - it.at < STORY_TTL }.maxByOrNull { it.at } ?: return null
    val e = (s.data?.get("e") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val l = (s.data?.get("l") as? JsonPrimitive)?.contentOrNull ?: ""
    return DayStatus(e, l, s.at, s.id)
}

/* ---------------------------------------------------------------- Séries des amis à fêter */

/** Paliers de série qui méritent un bravo. */
val FRIEND_MILESTONES = listOf(7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 400, 500, 600, 730, 1000)

class FriendMilestone(val u: User, val n: Int)

/** Amis qui viennent d'atteindre un palier de série (série active aujourd'hui ou hier), du plus grand au plus petit. */
fun Pg.friendMilestones(): List<FriendMilestone> {
    val me = meId(); if (me.isEmpty()) return emptyList()
    return friendsOf(me).mapNotNull { id ->
        val u = userById(id) ?: return@mapNotNull null
        if (isBlockedPair(me, id)) return@mapNotNull null
        val st = globalStreak(id)
        if (st.n in FRIEND_MILESTONES) FriendMilestone(u, st.n) else null
    }.sortedByDescending { it.n }
}

/** Message de bravo proposé pour un palier. */
fun milestoneCheer(n: Int) = when {
    n >= 365 -> "🏆 $n jours de série, c’est légendaire ! Respect total."
    n >= 100 -> "🔥 $n jours de série, quelle régularité ! Bravo à toi."
    n >= 30 -> "💪 $n jours de série, tu tiens le cap ! Bravo."
    else -> "👏 Déjà $n jours de série, continue comme ça !"
}

/* ---------------------------------------------------------------- Plan B météo */

/** Alternative de l'objectif quand il pleut (« vélo d'appartement à la place de la course »). */
var Goal.planB: String get() = str("planB"); set(v) = set("planB", v)

/** Temps humide aujourd'hui : pluie, neige ou orage en cours, ou forte probabilité de pluie dans la journée. */
fun Weather.isWet(f: Forecast?): Boolean {
    if (f == null) return false
    val k = wmo(f.current.code, f.current.isDay).cond
    if (k == "rain" || k == "snow" || k == "storm") return true
    val d = f.daily.firstOrNull { it.day == today() } ?: f.daily.firstOrNull()
    return (d?.pop ?: 0.0) >= 55 || wmo(d?.code).cond in setOf("rain", "snow", "storm")
}

/** Objectifs du jour encore à faire qui ont un plan B, quand il pleut. */
fun Pg.planBToday(f: Forecast?): List<Goal> =
    if (weatherIssue(f) == null && !Weather.isWet(f)) emptyList() else todayPlan().due.filter { !it.done && it.g.planB.isNotBlank() }.map { it.g }

/* ---------------------------------------------------------------- Ton meilleur moment */

class Moments(
    /** Taux de réussite moyen de chaque jour de la semaine (lundi → dimanche), null sans assez de données. */
    val wd: List<Double?>,
    /** Validations par heure de la journée (0 → 23). */
    val hours: IntArray,
    val bestWd: Int?, val hardWd: Int?,
    /** Créneau le plus actif (clé, nom) et sa part des validations. */
    val slot: Triple<String, String, Double>?,
    val total: Int,
    /** Heure habituelle de chaque objectif suivi (au moins 3 validations horodatées). */
    val goals: List<Pair<Goal, Float>>
)

val DAY_SLOTS = listOf(Triple("morning", "le matin", 5..11), Triple("noon", "à midi", 12..13), Triple("afternoon", "l’après-midi", 14..17), Triple("evening", "le soir", 18..21), Triple("night", "la nuit", 22..28))

fun Pg.moments(days: Int = 90): Moments {
    val me = meId(); val from = ymd(todayDate().minusDays(days.toLong()))
    val rates = Insights.dayRates(this, days)
    val byWd = rates.entries.groupBy { wdIdx(parseYmd(it.key)) }.mapValues { e -> if (e.value.size >= 2) e.value.map { it.value }.average() else null }
    val wd = (0..6).map { byWd[it] }
    val known = wd.withIndex().filter { it.value != null }
    val best = known.maxByOrNull { it.value!! }; val hard = known.minByOrNull { it.value!! }
    val spread = if (best != null && hard != null) best.value!! - hard.value!! else 0.0
    val hours = IntArray(24)
    val cks = userCheckins(me).filter { it.at > 0 && it.date >= from && isWin(it) && goalById(it.goalId)?.userId == me }
    cks.forEach { hours[localDateTime(it.at).hour]++ }
    val total = cks.size
    val slot = if (total < 5) null else DAY_SLOTS.map { (k, n, r) -> Triple(k, n, r.sumOf { h -> hours[h % 24] }.toDouble() / total) }.maxByOrNull { it.third }
    val goals = myGoals(me).filter { g -> g.userId == me && !g.archived && cks.count { it.goalId == g.id } >= 3 }
        .map { it to usualHour(it) }.sortedBy { it.second }.take(6)
    return Moments(wd, hours, best?.index?.takeIf { known.size >= 3 }, hard?.index?.takeIf { known.size >= 3 && spread >= .05 }, slot, total, goals)
}

/** Heure lisible : 7.5 → « 7 h 30 ». */
fun hourLabel(h: Float): String { val m = Math.round(h * 60) % (24 * 60); return if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')}" }

/* ---------------------------------------------------------------- Briefing du matin */

/**
 * Briefing lu à voix haute : salut, date, météo, objectifs du jour (avec le plan B s'il pleut) et série.
 * Renvoyé phrase par phrase pour que l'application traduise chacune.
 */
fun Pg.briefing(place: String?, wx: Forecast?): List<String> {
    val u = me() ?: return emptyList(); val out = mutableListOf<String>()
    val h = localDateTime(nowMs()).hour
    val name = first(u.name)
    out += when { name.isEmpty() -> if (h < 18) "Bonjour !" else "Bonsoir !"; h < 18 -> "Bonjour $name !"; else -> "Bonsoir $name !" }
    val d = todayDate()
    out += "Nous sommes ${if (Lang.en) d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ENGLISH)) else weekdayLongDate(d)}."
    if (wx != null) {
        val w = Weather.wmo(wx.current.code, wx.current.isDay); val t = wx.current.temp?.let { Math.round(it) }
        if (t != null) { out += if (!place.isNullOrBlank()) "À $place, $t degrés." else "Il fait $t degrés."; out += w.label }
    }
    val plan = todayPlan(u.id); val left = plan.due.filter { !it.done }
    when {
        plan.due.isEmpty() -> out += "Rien n’est prévu aujourd’hui : profite pour souffler."
        left.isEmpty() -> out += "Tout est déjà validé aujourd’hui, bravo !"
        else -> {
            val names = left.take(5).map { it.g.title }
            out += if (names.size == 1) "Au programme : ${names[0]}." else "Au programme : ${names.dropLast(1).joinToString(", ")} et ${names.last()}."
            if (left.size > 5) out += "Et ${pl(left.size - 5, "autre objectif", "autres objectifs")}."
        }
    }
    planBToday(wx).take(2).forEach { g -> out += "Il pleut : pour ${g.title}, passe au plan B, ${g.planB}." }
    val st = globalStreak(u.id)
    out += when {
        st.n == 0 -> "Allume ta flamme dès aujourd’hui !"
        st.lit -> "Ta série est de ${pl(st.n, "jour", "jours")}, et ta flamme brille déjà."
        else -> "Ta série est de ${pl(st.n, "jour", "jours")} : une validation suffit pour la garder."
    }
    out += "Belle journée !"
    return out
}
