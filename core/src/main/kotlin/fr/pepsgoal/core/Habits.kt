package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/* ---------------------------------------------------------------- Sous-tâches */

/** Types validés d'un geste (« fait / pas fait ») qui peuvent être découpés en sous-tâches. */
val STEP_TYPES = setOf("routine", "timed", "period", "challenge")

/** Sous-tâches d'un objectif (l'état « fait » du jour est rangé à part, voir [Pg.stepsDone]). */
var Goal.steps: List<Task>
    get() = (data["steps"] as? JsonArray)?.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        Task(o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["t"]?.jsonPrimitive?.content ?: "", false)
    } ?: emptyList()
    set(v) { data["steps"] = JsonArray(v.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "t" to JsonPrimitive(it.t.take(80)))) }) }

/** Jour auquel s'applique la validation (la veille pour « pas après » d'un horaire). */
fun Pg.stepDate(g: Goal) = if (g.type == "timed") timedDate(g) else today()

/** Étapes cochées pour le jour en cours ; toutes si l'objectif est déjà validé autrement (bouton, widget…). */
fun Pg.stepsDone(g: Goal): Set<String> {
    val k = stepDate(g)
    if (g.str("stepsDay") == k) return g.strs("stepsDone").toSet()
    return if (hasCheck(g.id, k)) g.steps.map { it.id }.toSet() else emptySet()
}
fun Pg.stepsComplete(g: Goal) = g.steps.isNotEmpty() && g.steps.all { it.id in stepsDone(g) }

/** Coche ou décoche une étape ; la dernière étape cochée valide l'objectif, et en décocher une annule la validation. */
fun Pg.stepToggle(gid: String, sid: String) {
    val g = goalById(gid) ?: return; if (g.userId != meId()) return
    val k = stepDate(g); val was = hasCheck(g.id, k)
    val cur = stepsDone(g).toMutableSet()
    if (!cur.add(sid)) cur.remove(sid)
    g.set("stepsDay", k); g.setStrs("stepsDone", g.steps.map { it.id }.filter { it in cur })
    store.commit()
    if (!was && stepsComplete(g)) validate(gid)
    else if (was && !stepsComplete(g)) validate(gid)
}
fun Pg.stepAdd(gid: String, text: String) {
    val g = goalById(gid) ?: return; val t = text.trim(); if (t.isEmpty() || g.steps.size >= 20) return
    g.steps = g.steps + Task(uid(), t, false); store.commit()
}
fun Pg.stepDel(gid: String, sid: String) {
    val g = goalById(gid) ?: return
    g.steps = g.steps.filter { it.id != sid }; g.setStrs("stepsDone", g.strs("stepsDone").filter { it != sid }); store.commit()
}
fun Pg.stepMove(gid: String, sid: String, to: Int) {
    val g = goalById(gid) ?: return; val l = g.steps.toMutableList(); val i = l.indexOfFirst { it.id == sid }; if (i < 0) return
    val t = l.removeAt(i); l.add(to.coerceIn(0, l.size), t); g.steps = l; store.commit()
}

/* ---------------------------------------------------------------- Enchaînement d'habitudes */

/** Objectif après lequel celui-ci s'enchaîne (« Après mon café → méditation »). */
var Goal.after: String? get() = strOrNull("after")?.ifEmpty { null }; set(v) = set("after", v ?: "")

/** Objectifs enchaînés après [g] encore à faire aujourd'hui. */
fun Pg.chainNext(g: Goal): List<Goal> {
    val plan = todayPlan()
    return plan.due.filter { !it.done && it.g.after == g.id && !it.g.archived }.map { it.g }
}
/** Objectifs qui peuvent précéder [g] sans créer de boucle. */
fun Pg.chainCandidates(g: Goal): List<Goal> = myGoals().filter { c ->
    if (c.id == g.id || isClosedGoal(c) || c.userId != meId()) return@filter false
    var cur: Goal? = c; var n = 0
    while (cur != null && n < 30) { if (cur.after == g.id) return@filter false; cur = goalById(cur.after); n++ }
    true
}

/* ---------------------------------------------------------------- Mode vacances */

/** Période de vacances en cours ou à venir (début, fin). */
fun Pg.vacationOf(u: User? = me()): Pair<String, String>? = u?.vacations?.mapNotNull { v -> v.split('|').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
    ?.filter { it.second >= today() }?.minByOrNull { it.first }

/** Jours couverts par toutes les vacances de [u] (protègent les séries comme des jours de repos). */
fun vacationDays(u: User): Set<String> {
    if (u.vacations.isEmpty()) return emptySet()
    val out = HashSet<String>()
    for (v in u.vacations) {
        val p = v.split('|'); if (p.size != 2) continue
        var d = try { parseYmd(p[0]) } catch (e: Exception) { continue }; val end = p[1]; var n = 0
        while (ymd(d) <= end && n < VACATION_MAX) { out.add(ymd(d)); d = d.plusDays(1); n++ }
    }
    return out
}
const val VACATION_MAX = 60

/** Planifie les vacances (remplace celles à venir). Renvoie un message d'erreur, ou null. */
fun Pg.setVacation(from: String, to: String): String? {
    val u = me() ?: return null
    if (from < today()) return "Les vacances commencent aujourd’hui au plus tôt."
    if (to < from) return "La fin doit suivre le début."
    if (daysBetween(parseYmd(from), parseYmd(to)) + 1 > VACATION_MAX) return "$VACATION_MAX jours de vacances au maximum d’un coup."
    u.vacations = (u.vacations.filter { it.substringAfter('|') < today() } + "$from|$to").toMutableList()
    store.commit(); hooks.liveChanged(); return null
}
/** Termine les vacances en cours (elles s'arrêtent hier) ou annule celles à venir. */
fun Pg.endVacation() {
    val u = me() ?: return; val t = today(); val y = yday()
    u.vacations = u.vacations.mapNotNull { v -> val parts = v.split("|"); val a = parts[0]; val b = parts.getOrElse(1) { a }
        when { b < t -> v; a >= t -> null; else -> if (y >= a) "$a|$y" else null } }.toMutableList()
    store.commit(); hooks.liveChanged()
}

/* ---------------------------------------------------------------- Objectif en duo */

/** Identifiant du duo et ami associé : chacun a sa copie de l'objectif, les jours validés s'échangent en messages chiffrés. */
val Goal.duoId: String? get() = strOrNull("duo")?.ifEmpty { null }
val Goal.duoPeer: String? get() = strOrNull("duoPeer")?.ifEmpty { null }
/** Types qui se prêtent au duo : un « fait / pas fait » par jour prévu. */
val DUO_TYPES = setOf("routine", "abstinence", "limit", "duration", "cap", "timed", "period")
fun duoOk(g: Goal) = g.type in DUO_TYPES && !(g.type == "routine" && g.mode != "days")

/** Flamme commune : jours prévus consécutifs validés par les deux (aujourd'hui en attente ne casse rien). */
fun Pg.duoStreak(g: Goal, peerDays: Set<String>): Int = dayStreak(g, { hasCheck(g.id, it) && it in peerDays }, { scheduled(g, it) })

/* ---------------------------------------------------------------- Journée difficile */

/** Jours où un objectif ne compte pas : repos, vacances, et journées difficiles s'il n'est pas essentiel. */
fun Pg.goalRest(g: Goal): Set<String> {
    val u = userById(g.userId) ?: return emptySet()
    val base = restSet(g.userId)
    return if (u.hardDays.isEmpty() || g.id in u.essentials) base else base + u.hardDays
}
const val HARD_PER_MONTH = 4
const val ESSENTIALS_MAX = 3
fun Pg.isHardDay(date: String = today()) = me()?.hardDays?.contains(date) == true

/** Active (ou retire) la journée difficile d'aujourd'hui. Renvoie un message d'erreur, ou null. */
fun Pg.toggleHardDay(): String? {
    val u = me() ?: return null; val t = today()
    if (u.hardDays.remove(t)) { store.commit(); hooks.liveChanged(); return null }
    if (u.essentials.none { goalById(it)?.let { g -> !isClosedGoal(g) } == true }) return "Choisis d’abord tes objectifs essentiels."
    if (u.hardDays.count { it.startsWith(t.substring(0, 7)) } >= HARD_PER_MONTH) return "Déjà $HARD_PER_MONTH journées difficiles ce mois-ci."
    u.hardDays.add(t); store.commit(); hooks.liveChanged(); return null
}
fun Pg.toggleEssential(id: String): String? {
    val u = me() ?: return null
    if (u.essentials.remove(id)) { store.commit(); return null }
    u.essentials.removeAll { goalById(it)?.let { g -> isClosedGoal(g) } != false }
    if (u.essentials.size >= ESSENTIALS_MAX) return "$ESSENTIALS_MAX essentiels au plus."
    u.essentials.add(id); store.commit(); return null
}

/* ---------------------------------------------------------------- Compteur d'économies */

/** Coût d'une unité évitée (€) et temps (min) ; base : unités par jour avant l'objectif. */
var Goal.saveCost: Double get() = num("saveCost", 0.0); set(v) = set("saveCost", v)
var Goal.saveMin: Double get() = num("saveMin", 0.0); set(v) = set("saveMin", v)
var Goal.saveBase: Double get() = num("saveBase", 0.0); set(v) = set("saveBase", v)
val SAVING_TYPES = setOf("abstinence", "limit")

class Savings(val units: Double, val money: Double, val minutes: Double, val days: Int)

/** Unités évitées depuis le début : jours sans écart × base (abstinence), ou base − consommé chaque jour (limite). */
fun Pg.savings(g: Goal): Savings? {
    if (g.type !in SAVING_TYPES || g.saveBase <= 0) return null
    val start = parseYmd(if (g.type == "abstinence") g.since.ifEmpty { ymd(g.createdAt) } else ymd(g.createdAt))
    val days = maxOf(0, daysBetween(start, todayDate()).toInt())
    val units = when (g.type) {
        "abstinence" -> {
            // Les jours d'écart ne comptent pas comme évités
            val rel = db.checkins.count { it.goalId == g.id && it.kind == "relapse" && it.date >= ymd(start) }
            maxOf(0, days - rel) * g.saveBase
        }
        else -> {
            val used = db.checkins.filter { it.goalId == g.id && it.kind == "use" && it.date >= ymd(start) && it.date < today() }.sumOf { it.value }
            maxOf(0.0, days * g.saveBase - used)
        }
    }
    return Savings(units, units * g.saveCost, units * g.saveMin, days)
}

/* ---------------------------------------------------------------- Statistiques d'un objectif */

class GoalStats(val rate30: Double?, val rate90: Double?, val bestWd: Int?, val hour: Int?, val longest: Int, val total: Int)

/** Taux de réussite sur 30 et 90 jours (jours prévus), meilleur jour, heure habituelle, plus longue série. */
fun Pg.goalStats(g: Goal): GoalStats {
    val me = meId(); val done = db.checkins.filter { it.goalId == g.id && it.userId == me && isWin(it) }
    val doneDays = done.map { it.date }.toSet()
    val created = ymd(g.createdAt); val rest = goalRest(g)
    fun rate(n: Int): Double? {
        var due = 0; var ok = 0
        for (i in 1..n) { val d = todayDate().minusDays(i.toLong()); val k = ymd(d); if (k < created) break; if (k in rest || !scheduled(g, d)) continue; due++; if (k in doneDays) ok++ }
        return if (due >= 3) ok.toDouble() / due else null
    }
    val byWd = doneDays.groupingBy { wdIdx(parseYmd(it)) }.eachCount()
    val hours = done.filter { it.at > 0 }.map { localDateTime(it.at).hour }
    val hour = hours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    var longest = 0; var run = 0; var prev: String? = null
    for (d in doneDays.sorted()) { run = if (prev != null && ymd(parseYmd(prev).plusDays(1)) == d) run + 1 else 1; longest = maxOf(longest, run); prev = d }
    return GoalStats(rate(30), rate(90), byWd.maxByOrNull { it.value }?.key?.takeIf { doneDays.size >= 5 }, hour?.takeIf { hours.size >= 5 }, longest, doneDays.size)
}
