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
