package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.text.Normalizer

/* ---------------------------------------------------------------- Carte des heures */

/** Validations par jour de la semaine (0 = lundi) et par heure, sur les [days] derniers jours : 7 lignes de 24 cases. */
fun Pg.hourHeat(uid: String = meId(), days: Int = 90): List<IntArray> {
    val from = startOfDayMs(todayDate().minusDays(days.toLong()))
    val grid = List(7) { IntArray(24) }
    for (c in db.checkins) {
        if (c.userId != uid || c.at < from || !isWin(c) || c.kind == "skip") continue
        val t = localDateTime(c.at)
        grid[t.dayOfWeek.value - 1][t.hour]++
    }
    return grid
}

/** Créneau le plus fréquent (jour, heure, nombre), ou null s'il y a trop peu de validations. */
fun hourPeak(grid: List<IntArray>): Triple<Int, Int, Int>? {
    var best: Triple<Int, Int, Int>? = null
    for (d in 0..6) for (h in 0..23) { val n = grid[d][h]; if (n > (best?.third ?: 0)) best = Triple(d, h, n) }
    return best?.takeIf { grid.sumOf { it.sum() } >= 5 }
}

/* ---------------------------------------------------------------- Défi de N jours */

/** Durée du défi en jours (0 : pas de défi) et date de début. */
var Goal.trialDays: Int get() = num("trialDays", 0.0).toInt(); set(v) = set("trialDays", v)
var Goal.trialStart: String? get() = strOrNull("trialStart")?.ifEmpty { null }; set(v) = set("trialStart", v ?: "")
val TRIAL_LENGTHS = listOf(7, 21, 30, 66, 100)

class TrialState(val days: List<Pair<String, Boolean>>, val idx: Int, val done: Int, val finished: Boolean) {
    val total get() = days.size
    /** Réussi : au moins 80 % des jours validés. */
    val success get() = done * 100 >= total * 80
}

fun Pg.trialState(g: Goal): TrialState? {
    val n = g.trialDays; val start = g.trialStart ?: return null
    if (n <= 0) return null
    val d0 = parseYmd(start)
    val wins = db.checkins.filter { it.goalId == g.id && it.userId == g.userId && isWin(it) }.map { it.date }.toSet()
    val days = (0 until n).map { i -> ymd(addDays(d0, i.toLong())).let { it to (it in wins) } }
    val idx = daysBetween(d0, todayDate()).toInt()
    return TrialState(days, idx, days.count { it.second }, idx >= n)
}

/** Démarre (ou arrête avec 0) un défi de [n] jours à partir d'aujourd'hui. */
fun Pg.startTrial(g: Goal, n: Int) {
    g.trialDays = n; g.trialStart = if (n > 0) today() else null; g.set("trialDone", false); store.commit()
}

/** Appelé après une validation : défi terminé → célébration une seule fois. */
fun Pg.checkTrial(g: Goal) {
    val st = trialState(g) ?: return
    if (g.bool("trialDone")) return
    val lastDay = st.days.last().first
    if (st.idx < st.total - 1 && today() < lastDay) return
    if (st.idx == st.total - 1 && !st.days.last().second) return
    g.set("trialDone", true)
    val u = me() ?: return
    if (st.success) {
        hooks.celebrate(Celebration.Perfect("Défi de ${g.trialDays} jours", "Défi réussi !", "« ${g.title} » : ${st.done} jours sur ${st.total}. Une vraie habitude est née.",
            listOf(g.icon), listOf(Triple(st.done, "jours validés", ""), Triple(st.total, "jours de défi", "")), "J’ai réussi mon défi de ${g.trialDays} jours « ${g.title} » sur PepsGoal 🔥", "mon défi"))
        notify(u.id, "Défi de ${g.trialDays} jours réussi : « ${g.title} » (${st.done}/${st.total}).", "badge", g.id)
    } else notify(u.id, "Défi de ${g.trialDays} jours terminé : ${st.done}/${st.total} pour « ${g.title} ». Relance-le pour faire mieux !", "info", g.id)
    store.commit()
}

/* ---------------------------------------------------------------- Rappels intelligents */

/** Rappel calé sur l'heure habituelle de validation. */
var Goal.smartRemind: Boolean get() = bool("smartRemind"); set(v) = set("smartRemind", v)

/** Heure habituelle de validation (« HH:MM », médiane des 30 dernières validations), ou null avant 5 validations. */
fun Pg.usualTime(g: Goal): String? {
    val mins = db.checkins.filter { it.goalId == g.id && it.userId == meId() && isWin(it) && it.at > 0 }
        .sortedByDescending { it.at }.take(30).map { val t = localDateTime(it.at); t.hour * 60 + t.minute }.sorted()
    if (mins.size < 5) return null
    val m = mins[mins.size / 2]
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Heure du rappel réellement utilisée : 15 minutes avant l'heure habituelle si le rappel intelligent est actif. */
fun Pg.effectiveRemind(g: Goal): String? {
    if (!g.smartRemind) return g.remindAt
    val u = usualTime(g) ?: return g.remindAt
    val m = (u.substring(0, 2).toInt() * 60 + u.substring(3, 5).toInt() - 15).coerceAtLeast(0)
    return "%02d:%02d".format(m / 60, m % 60)
}

/* ---------------------------------------------------------------- Classement entre amis */

private fun normTitle(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    .replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 2 }.toSet()

/** Deux titres désignent le même objectif (« Courir 30 minutes » ~ « courir 30 min ») : mots en commun. */
fun sameGoalTitle(a: String, b: String): Boolean {
    val x = normTitle(a); val y = normTitle(b)
    if (x.isEmpty() || y.isEmpty()) return false
    val inter = x.intersect(y).size
    return inter * 10 >= maxOf(x.size, y.size) * 6
}

class Rank(val user: User, val goal: Goal, val streak: Int, val rate30: Int)

/** Toi et tes amis qui suivent le même objectif (titre proche, même type), classés par série puis régularité. */
fun Pg.friendsRanking(g: Goal): List<Rank> {
    val me = meId()
    val friends = friendsOf(me).toSet()
    val others = db.goals.filter { o -> o.userId in friends && o.type == g.type && !isClosedGoal(o) && canSeeGoal(o, me) && sameGoalTitle(o.title, g.title) }
        .groupBy { it.userId }.mapNotNull { (_, l) -> l.maxByOrNull { goalStreak(it) } }
    if (others.isEmpty()) return emptyList()
    return (others + g).mapNotNull { o -> userById(o.userId)?.let { Rank(it, o, goalStreak(o), rate30(o)) } }
        .sortedWith(compareByDescending<Rank> { it.streak }.thenByDescending { it.rate30 })
}

/** Part des 30 derniers jours validés (en %). */
fun Pg.rate30(g: Goal): Int {
    val from = ymd(todayDate().minusDays(29))
    val n = db.checkins.filter { it.goalId == g.id && it.userId == g.userId && isWin(it) && it.date >= from }.map { it.date }.toSet().size
    return n * 100 / 30
}

/* ---------------------------------------------------------------- Réactions du feed */

val REACTIONS = listOf("fire" to "🔥", "muscle" to "💪", "clap" to "👏", "party" to "🎉")
fun reactionEmoji(k: String?) = REACTIONS.firstOrNull { it.first == k }?.second ?: "🔥"

/** Réactions d'une publication, de la plus fréquente à la plus rare : (emoji, nombre). */
fun reactionSummary(p: Post): List<Pair<String, Int>> =
    p.likes.groupingBy { p.reactions[it] ?: "fire" }.eachCount().entries.sortedByDescending { it.value }.map { reactionEmoji(it.key) to it.value }
