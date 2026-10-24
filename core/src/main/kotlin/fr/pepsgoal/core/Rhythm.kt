package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDate

/* ---------------------------------------------------------------- Pause d'un objectif */

/**
 * Pause (blessure, voyage…) : de [from] à [until] inclus, l'objectif disparaît du jour et ne casse pas sa série.
 * Les pauses passées restent dans l'historique (« pauses » : « début..fin ») pour que les séries restent justes.
 */
var Goal.pausedUntil: String? get() = strOrNull("pausedUntil")?.ifEmpty { null }; set(v) = set("pausedUntil", v ?: "")
var Goal.pauseReason: String get() = str("pauseReason"); set(v) = set("pauseReason", v)

fun Pg.isPaused(g: Goal, date: String = today()): Boolean =
    g.strs("pauses").any { val (a, b) = it.split("..").let { p -> p[0] to p.getOrElse(1) { p[0] } }; date in a..b }

/** Jours de pause de l'objectif (passés et à venir). */
fun Pg.pausedDays(g: Goal): Set<String> {
    val out = HashSet<String>()
    for (r in g.strs("pauses")) { val p = r.split(".."); var d = parseYmd(p[0]); val e = parseYmd(p.getOrElse(1) { p[0] }); var guard = 0
        while (!d.isAfter(e) && guard++ < 400) { out.add(ymd(d)); d = d.plusDays(1) } }
    return out
}

/** Met l'objectif en pause d'aujourd'hui jusqu'à [until] (inclus, 90 jours au plus). Renvoie un message d'erreur. */
fun Pg.pauseGoal(g: Goal, until: String, reason: String = ""): String? {
    val t = today()
    if (until < t) return "La date de reprise doit être aujourd’hui ou plus tard."
    if (daysBetween(todayDate(), parseYmd(until)) > 90) return "Une pause dure 90 jours au plus."
    val keep = g.strs("pauses").filter { !it.startsWith("$t..") && it.split("..").getOrElse(1) { "" } < t }
    g.setStrs("pauses", keep + "$t..$until"); g.pausedUntil = until; g.pauseReason = reason.take(60)
    store.commit(); hooks.liveChanged(); return null
}

/** Reprise immédiate : la pause en cours s'arrête hier. */
fun Pg.resumeGoal(g: Goal) {
    val t = today(); val y = yday()
    g.setStrs("pauses", g.strs("pauses").mapNotNull { r -> val p = r.split(".."); val a = p[0]; val b = p.getOrElse(1) { a }
        if (t in a..b) (if (a <= y) "$a..$y" else null) else r })
    g.pausedUntil = null; g.pauseReason = ""; store.commit(); hooks.liveChanged()
}

/** Pauses qui se terminent : notification « c'est reparti » le jour de la reprise (appelé à l'ouverture et chaque jour). */
fun Pg.checkResumes() {
    val u = me() ?: return; val t = today()
    for (g in myGoals(u.id).filter { it.pausedUntil != null }) {
        val until = g.pausedUntil!!
        if (until < t) {
            g.pausedUntil = null; g.pauseReason = ""
            notify(u.id, "« ${g.shownTitle()} » reprend aujourd’hui après ta pause. On repart en douceur !", "reminder", g.id)
            store.commit()
        }
    }
}

/* ---------------------------------------------------------------- Objectif par paliers */

/** Types dont la quantité quotidienne peut monter par paliers. */
val LADDER_TYPES = setOf("duration", "cap")
var Goal.ladder: Boolean get() = bool("ladder"); set(v) = set("ladder", v)
var Goal.ladderStep: Double get() = num("ladderStep", 5.0); set(v) = set("ladderStep", v)
var Goal.ladderMax: Double get() = num("ladderMax", 60.0); set(v) = set("ladderMax", v)
var Goal.ladderMin: Double get() = num("ladderMin", 5.0); set(v) = set("ladderMin", v)
var Goal.ladderAt: String get() = str("ladderAt"); set(v) = set("ladderAt", v)

/** Quantité quotidienne actuelle d'un objectif à paliers. */
fun ladderValue(g: Goal) = if (g.type == "duration") g.minutes else g.target
private fun setLadderValue(g: Goal, v: Double) { if (g.type == "duration") g.minutes = v else g.target = v }

/** Réussite d'une semaine (7 jours finissant hier exclu [end]) : part des jours prévus validés. */
private fun Pg.weekRate(g: Goal, end: LocalDate): Double {
    val rest = goalRest(g); var due = 0; var ok = 0
    for (i in 1..7) { val d = end.minusDays(i.toLong()); val k = ymd(d); if (k < ymd(g.createdAt) || k in rest) continue; due++; if (hasCheck(g.id, k)) ok++ }
    return if (due == 0) -1.0 else ok.toDouble() / due
}

/**
 * Paliers : une fois par semaine au plus, l'objectif monte d'un cran après 7 jours réussis sur 7, et redescend d'un
 * cran après deux semaines sous 50 %. Renvoie le message affiché (null : pas de changement).
 */
fun Pg.checkLadder(g: Goal, ref: LocalDate = todayDate()): String? {
    if (!g.ladder || g.type !in LADDER_TYPES || g.archived) return null
    val last = g.ladderAt.ifEmpty { ymd(g.createdAt) }
    if (daysBetween(parseYmd(last), ref) < 7) return null
    val w1 = weekRate(g, ref); val w2 = weekRate(g, ref.minusDays(7)); val v = ladderValue(g); val unit = if (g.type == "duration") " min" else unitSfx(g)
    val msg = when {
        w1 >= 1.0 && v < g.ladderMax -> { val nv = minOf(g.ladderMax, v + g.ladderStep); setLadderValue(g, nv); "Palier franchi pour « ${g.title} » : une semaine parfaite, on passe à ${fmtNum(nv)}$unit par jour. 🪜" }
        w1 in 0.0..0.49 && w2 in 0.0..0.49 && v > g.ladderMin -> { val nv = maxOf(g.ladderMin, v - g.ladderStep); setLadderValue(g, nv); "« ${g.title} » redescend d’un cran (${fmtNum(nv)}$unit) : deux semaines difficiles, on reprend pied." }
        else -> null
    }
    g.ladderAt = ymd(ref); store.commit()
    if (msg != null) me()?.let { notify(it.id, msg, "badge", g.id) }
    return msg
}
fun Pg.checkLadders() { val u = me() ?: return; myGoals(u.id).forEach { checkLadder(it) } }

/* ---------------------------------------------------------------- Rattrapage de la veille */

/** Types qu'on peut rattraper d'une touche (validation simple, pas de quantité). */
private val CATCHUP_TYPES = setOf("routine", "timed", "period", "challenge")

/** Objectifs prévus hier mais pas validés (hors repos et pauses), proposés le matin jusqu'à 14 h. */
fun Pg.missedYesterday(): List<Goal> {
    val u = me() ?: return emptyList(); val y = yday(); val yd = parseYmd(y)
    if (y in restSet(u.id) || nowHM() >= "14:00") return emptyList()
    return myGoals(u.id).filter { g -> !g.archived && g.type in CATCHUP_TYPES && g.completedAt == null && g.failedAt == null && ymd(g.createdAt) <= y &&
        scheduled(g, yd) && !hasCheck(g.id, y) && y !in goalRest(g) }
}

/* ---------------------------------------------------------------- Heure habituelle (frise de la journée) */

/** Heure à laquelle l'objectif est d'habitude validé (médiane des 30 derniers jours), sinon son horaire prévu. */
fun Pg.usualHour(g: Goal): Float {
    val from = ymd(todayDate().minusDays(30))
    val hs = db.checkins.filter { it.goalId == g.id && it.at > 0 && it.date >= from && it.kind != "relapse" }.map { val t = localDateTime(it.at); t.hour + t.minute / 60f }.sorted()
    if (hs.size >= 2) return hs[hs.size / 2]
    return when (g.type) {
        "timed" -> g.time.split(":").let { it[0].toFloat() + it.getOrElse(1) { "0" }.toFloat() / 60f }
        "period" -> g.start.split(":").let { (it.getOrNull(0)?.toFloatOrNull() ?: 12f) + (it.getOrNull(1)?.toFloatOrNull() ?: 0f) / 60f }
        "abstinence" -> 21f
        else -> hs.firstOrNull() ?: 12f
    }
}
