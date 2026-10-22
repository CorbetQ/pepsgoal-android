package fr.pepsgoal.core

import fr.pepsgoal.core.model.Checkin
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.GoalGroup
import fr.pepsgoal.core.model.Notif
import fr.pepsgoal.core.model.Post
import fr.pepsgoal.core.model.Story
import fr.pepsgoal.core.model.User
import fr.pepsgoal.core.model.*
import java.time.LocalDate

/** Ce que la logique demande à l'application (interface, notifications système, serveur). */
interface Hooks {
    fun toast(text: String, icon: String = "check") {}
    /** Étincelles et « +N » à l'endroit touché. */
    fun sparks(pts: Int) {}
    fun celebrate(c: Celebration) {}
    /** Notification pour ce compte : affichée par le système si l'application est en arrière-plan. */
    fun localNotification(n: Notif) {}
    /** Le minuteur ou une validation a changé : rafraîchir les activités en cours (Live Updates, widget). */
    fun liveChanged() {}
    /** [next] est enchaîné après [prev], qui vient d'être validé : proposer de le faire maintenant. */
    fun chained(prev: Goal, next: Goal) {}
}

/** Fonctions de la couche connectée appelées par la logique (cloud.js : notify, autoPost, gels…). */
interface CloudBridge {
    fun notifyOther(to: String, text: String, type: String, goalId: String?)
    fun autoPost(g: Goal, text: String)
    fun confirmCheck(checkinId: String, ok: Boolean)
    fun freezesUsed(count: Int)
    fun welcomeBonus()
    val pulledBefore: Boolean
}

sealed class Celebration {
    class Perfect(val eyebrow: String, val title: String, val text: String, val icons: List<String>, val stats: List<Triple<Int, String, String>>, val share: String, val shareLabel: String = "ma réussite") : Celebration()
    class Badge(val family: BadgeFamily, val tier: Int, val more: Int) : Celebration()
}

class Stat(val v: String, val l: String)
class DueItem(val g: Goal, val done: Boolean, val meta: String? = null)
class Plan(val due: List<DueItem>, val ongoing: List<Goal>)
class Level(val idx: Int, val name: String, val pts: Int, val next: Int?, val nextName: String?, val pct: Double)
class Streak(val n: Int, val lit: Boolean)
class Window(val open: Boolean, val txt: String, val live: Boolean = false)
class ProgressInfo(val title: String, val info: String, val label: String, val quick: List<Double>, val text: Boolean = false, val value: String = "1", val quickAbsolute: Boolean = false)

/**
 * Logique de l'application PepsGoal (séries, flammes, gels, plan du jour, types d'objectifs, validations),
 * portée de apps/web-legacy/pepsgoal.html sans changement de règles.
 */
class Pg(val store: Store, var hooks: Hooks = object : Hooks {}) {
    var cloud: CloudBridge? = null
    val db get() = store.db
    /** Objectif validé à l'instant (animation de la case). */
    var justDone: String? = null

    fun me(): User? = db.users.firstOrNull { it.id == db.session }
    fun meId(): String = db.session ?: ""
    fun userById(id: String?): User? = if (id == null) null else db.users.firstOrNull { it.id == id }
    fun goalById(id: String?): Goal? = if (id == null) null else db.goals.firstOrNull { it.id == id }

    /* ---------------------------------------------------------------- index des validations */
    private class Ckx(val arr: List<Checkin>, val len: Int, val last: Checkin?, val keys: Set<String>, val byUser: Map<String, List<Checkin>>, val days: Map<String, Set<String>>)
    private var ckx: Ckx? = null
    private fun ckIndex(): Ckx {
        val a = db.checkins; val last = a.lastOrNull()
        ckx?.let { if (it.arr === a && it.len == a.size && it.last === last) return it }
        val keys = HashSet<String>(); val byUser = HashMap<String, MutableList<Checkin>>(); val days = HashMap<String, MutableSet<String>>()
        for (c in a) {
            keys.add(c.goalId + "|" + c.date + "|" + c.kind)
            byUser.getOrPut(c.userId) { mutableListOf() }.add(c)
            if (c.kind != "relapse" && c.kind != "use") days.getOrPut(c.userId) { HashSet() }.add(c.date)
        }
        return Ckx(a, a.size, last, keys, byUser, days).also { ckx = it }
    }
    fun userCheckins(id: String): List<Checkin> = ckIndex().byUser[id] ?: emptyList()
    fun hasCheck(gid: String, date: String, kind: String = "done") = ckIndex().keys.contains("$gid|$date|$kind")
    fun isWin(c: Checkin) = c.kind != "relapse" && c.kind != "use"
    fun isMember(g: Goal, id: String) = g.userId == id || (g.members?.contains(id) ?: false)
    fun myGoals(id: String = meId()) = db.goals.filter { isMember(it, id) }
    fun checkinsOf(g: Goal) = db.checkins.filter { it.goalId == g.id }
    fun sumKind(g: Goal, kind: String, who: String? = null, pred: (Checkin) -> Boolean = { true }): Double =
        db.checkins.filter { it.goalId == g.id && it.kind == kind && (who == null || it.userId == who) && pred(it) }.sumOf { it.value }

    /* ---------------------------------------------------------------- relations */
    fun friendship(a: String, b: String) = db.friendships.firstOrNull { (it.from == a && it.to == b) || (it.from == b && it.to == a) }
    fun areFriends(a: String, b: String) = friendship(a, b)?.status == "accepted"
    fun friendsOf(id: String): List<String> {
        val own = db.friendships.filter { it.status == "accepted" && (it.from == id || it.to == id) }.map { if (it.from == id) it.to else it.from }
        val u = if (store.acct && id != meId()) userById(id) else null
        val fc = u?.friendCount
        return if (fc != null && own.size < fc) own + List(fc - own.size) { "" } else own
    }
    fun blockedBy(a: String, b: String) = db.blocks.any { it.by == a && it.who == b }
    fun isBlockedPair(a: String, b: String) = blockedBy(a, b) || blockedBy(b, a)
    fun canSeeGoal(g: Goal, v: String) = (g.members?.contains(v) ?: false) || g.buddy == v || g.userId == v || g.visibility == "public" || (g.visibility == "friends" && areFriends(g.userId, v))
    fun canSeePost(p: Post, v: String) = p.userId == v || (!isBlockedPair(p.userId, v) && (p.visibility == "public" || (p.visibility == "friends" && areFriends(p.userId, v))))
    fun isAdmin(u: User?) = u?.role == "admin"
    fun isOfficial(u: User?) = u?.role == "official"
    fun isGuest(u: User? = me()) = u?.guest == true
    fun isBanned(u: User?): Boolean = u?.ban?.let { it.until == null || it.until > nowMs() } ?: false
    fun banText(u: User?): String {
        if (!isBanned(u)) return ""
        val until = u!!.ban!!.until ?: return "définitivement"
        val t = localDateTime(until)
        return "jusqu'au ${t.dayOfMonth} ${t.month.getDisplayName(java.time.format.TextStyle.FULL, FR)} à ${pad(t.hour)}:${pad(t.minute)}"
    }
    fun canComment(u: User, p: Post) = !isBanned(u) && !u.noComments && !p.commentsLocked
    fun postVisible(p: Post, v: String) = isAdmin(userById(v)) || p.userId == v || (p.status != "pending" && !isBanned(userById(p.userId)) && !isBlockedPair(p.userId, v))

    /** Fonctions sociales : compte requis, réglage activé et connexion active. */
    fun socialOn(u: User? = me()) = u != null && !u.guest && u.settings.social && store.online && !db.sandbox

    fun maintNow() = db.maintenance?.takeIf { it.start <= nowMs() && (it.until == null || it.until > nowMs()) }
    fun maintNext() = db.maintenance?.takeIf { it.start > nowMs() }
    fun maintBlocks(u: User?) = maintNow() != null && !isAdmin(u)
    /** Action sociale impossible (maintenance, bannissement) : message et vrai. */
    fun socialBlocked(action: String): Boolean {
        val u = me()
        if (maintBlocks(u)) { hooks.toast("Maintenance en cours : impossible $action pour le moment.", "gauge"); return true }
        if (!isBanned(u)) return false
        hooks.toast("Ton accès à la plateforme sociale est suspendu ${banText(u)} : impossible $action.", "ban")
        return true
    }

    /* ---------------------------------------------------------------- flammes, séries, niveaux */
    fun points(id: String): Int {
        val u = if (store.acct && id != meId()) userById(id) else null
        u?.pts?.let { return it }
        return userCheckins(id).sumOf { it.pts }
    }
    fun level(pts: Int): Level {
        var i = 0
        while (i < LEVELS.size - 1 && pts >= LEVELS[i + 1].first) i++
        val cur = LEVELS[i]; val nxt = LEVELS.getOrNull(i + 1)
        return Level(i + 1, cur.second, pts, nxt?.first, nxt?.second, if (nxt != null) (pts - cur.first).toDouble() / (nxt.first - cur.first) else 1.0)
    }
    fun activeDays(id: String): MutableSet<String> {
        val u = if (store.acct && id != meId()) userById(id) else null
        u?.days?.let { return it.toMutableSet() }
        return (ckIndex().days[id] ?: emptySet()).toMutableSet()
    }
    /** Jours qui comptent pour la série : jours actifs + jours protégés par un gel + maintenances complètes. */
    fun streakDays(id: String): MutableSet<String> {
        val s = activeDays(id)
        userById(id)?.frozenDays?.let { s.addAll(it) }
        for (h in db.maintLog.filter { it.mode == "full" }) {
            val end = minOf(h.endedAt ?: h.until ?: nowMs(), nowMs())
            if (end < h.start) continue
            var d = localDate(h.start); val last = localDate(end)
            while (!d.isAfter(last)) { s.add(ymd(d)); d = d.plusDays(1) }
        }
        return s
    }
    fun globalStreak(id: String): Streak {
        val days = streakDays(id); val rest = restSet(id); var d = todayDate(); var n = 0
        val lit = activeDays(id).contains(ymd(d))
        if (!days.contains(ymd(d))) d = d.minusDays(1)
        // Un jour de repos ne compte pas mais ne casse pas la série
        for (i in 0 until 4000) { val k = ymd(d); if (days.contains(k)) n++ else if (k !in rest) break; d = d.minusDays(1) }
        return Streak(n, lit)
    }

    /* ---------------------------------------------------------------- jours de repos */
    fun restSet(id: String = meId()): Set<String> { val u = userById(id) ?: return emptySet(); return u.restDays.toSet() + vacationDays(u) }
    fun isRest(date: String = today(), id: String = meId()) = date in restSet(id)
    /** Nombre maximal de jours de repos par mois. */
    val REST_PER_MONTH = 6
    /** Planifie (ou retire) un jour de repos : aujourd'hui ou plus tard, jamais dans le passé. Renvoie un message d'erreur. */
    fun toggleRest(date: String): String? {
        val u = me() ?: return null
        if (date < today()) return "Un jour de repos se planifie à l’avance, pas après coup."
        if (u.restDays.contains(date)) { u.restDays.remove(date); store.commit(); hooks.liveChanged(); return null }
        val month = date.substring(0, 7)
        if (u.restDays.count { it.startsWith(month) } >= REST_PER_MONTH) return "Tu as déjà $REST_PER_MONTH jours de repos ce mois-ci."
        u.restDays.add(date); u.restDays.sort(); store.commit(); hooks.liveChanged(); return null
    }
    fun bestStreak(id: String): Int {
        val ds = streakDays(id).sorted(); var best = 0; var run = 0; var prev: String? = null
        for (d in ds) { run = if (prev != null && ymd(parseYmd(prev).plusDays(1)) == d) run + 1 else 1; best = maxOf(best, run); prev = d }
        return best
    }
    /** Consomme automatiquement des gels pour combler les jours manqués avant aujourd'hui. */
    fun applyFreezes(u: User?) {
        if (u == null || u.freezes <= 0) return
        if (store.acct && cloud?.pulledBefore == false) return
        val days = streakDays(u.id); val rest = restSet(u.id); val gap = mutableListOf<String>()
        var d = todayDate().minusDays(1)
        while (gap.size < 30 && (!days.contains(ymd(d)) || ymd(d) in rest)) { if (ymd(d) !in rest) gap.add(ymd(d)); d = d.minusDays(1); if (ymd(d) < ymd(todayDate().minusDays(400))) break }
        if (gap.isEmpty() || !days.contains(ymd(d)) || gap.size > u.freezes) return
        u.freezes -= gap.size; u.frozenDays.addAll(gap)
        val n = globalStreak(u.id).n
        notify(u.id, "${if (gap.size > 1) "${gap.size} gels utilisés" else "Gel de série utilisé"} : ta série de ${pl(n, "jour", "jours")} est sauvée. Il te reste ${pl(u.freezes, "gel", "gels")}.", "freeze")
        if (store.acct && u.id == meId()) cloud?.freezesUsed(gap.size)
        store.commit()
    }

    fun weekCount(g: Goal, ws: LocalDate): Int {
        val a = ymd(ws); val b = ymd(ws.plusDays(6))
        return db.checkins.count { it.goalId == g.id && it.kind == "done" && it.date >= a && it.date <= b }
    }
    fun cleanStart(g: Goal): String {
        val rel = db.checkins.filter { it.goalId == g.id && it.kind == "relapse" }.map { it.date }.maxOrNull()
        return if (rel != null && rel > g.since) rel else g.since
    }
    fun objProgress(g: Goal) = db.checkins.filter { it.goalId == g.id && it.kind == "progress" }.sumOf { it.value }
    fun curMonth() = today().substring(0, 7)
    fun monthProgress(g: Goal, key: String = curMonth()) = db.checkins.filter { it.goalId == g.id && it.kind == "progress" && it.date.startsWith(key) }.sumOf { it.value }
    fun daysLeftInMonth(): Int { val n = todayDate(); return n.lengthOfMonth() - n.dayOfMonth + 1 }
    fun monthKeys(g: Goal): List<String> {
        val out = mutableListOf<String>(); val start = localDate(g.createdAt).withDayOfMonth(1); val end = todayDate()
        var d = start
        while (!d.isAfter(end)) { out.add("${d.year}-${pad(d.monthValue)}"); d = d.plusMonths(1) }
        return out
    }

    /** Jours consécutifs réussis ; le jour « en attente » ne casse pas la série. */
    fun dayStreak(g: Goal, isOk: (String) -> Boolean, isSched: (LocalDate) -> Boolean = { true }, pendKey: String = today(), from: LocalDate = todayDate()): Int {
        val start = ymd(g.createdAt); var d = from; var n = 0; val rest = goalRest(g)
        for (i in 0 until 800) {
            val k = ymd(d); if (k < start) break
            if (isSched(d)) { if (isOk(k)) n++ else if (k != pendKey && k !in rest) break }
            d = d.minusDays(1)
        }
        return n
    }

    fun goalStreak(g: Goal): Int = when (g.type) {
        "routine" -> if (g.mode == "days") {
            val start = ymd(g.createdAt); val t = today(); var d = todayDate(); var n = 0; val rs = goalRest(g)
            for (i in 0 until 800) {
                val k = ymd(d); if (k < start) break
                if (g.days.contains(wdIdx(d))) { if (hasCheck(g.id, k)) n++ else if (k != t && k !in rs) break }
                d = d.minusDays(1)
            }
            n
        } else {
            var ws = weekStart(todayDate()); var n = 0; val first = weekStart(localDate(g.createdAt))
            if (weekCount(g, ws) >= g.perWeek) n++
            ws = ws.minusDays(7)
            while (!ws.isBefore(first) && weekCount(g, ws) >= g.perWeek) { n++; ws = ws.minusDays(7) }
            n
        }
        "abstinence" -> maxOf(0L, -daysUntil(cleanStart(g))).toInt()
        "monthly" -> {
            val ms = monthKeys(g).reversed(); var n = 0
            for ((i, k) in ms.withIndex()) { if (monthProgress(g, k) >= g.target) n++ else if (i > 0) break }
            n
        }
        "limit", "duration", "cap" -> dayStreak(g, { hasCheck(g.id, it) })
        "timed" -> { val na = g.mode == "notafter"; dayStreak(g, { hasCheck(g.id, it) }, { g.days.contains(wdIdx(it)) }, if (na) yday() else today(), if (na) todayDate().minusDays(1) else todayDate()) }
        "challenge" -> challengeDone(g)
        "program" -> g.weeksDone.size
        "period" -> dayStreak(g, { hasCheck(g.id, it) }, { periodOn(g, it) })
        else -> 0
    }

    fun scheduled(g: Goal, d: LocalDate): Boolean = when (g.type) {
        "routine" -> g.mode == "days" && g.days.contains(wdIdx(d))
        "abstinence", "limit", "duration", "cap" -> true
        "timed" -> g.days.contains(wdIdx(d))
        "challenge" -> { val k = ymd(d); k >= g.start && k < ymd(parseYmd(g.start).plusDays(g.length.toLong())) }
        "period" -> periodOn(g, d)
        else -> false
    }
    /** Le type affiche un calendrier des 5 dernières semaines. */
    fun hasCalendar(type: String) = type in setOf("routine", "abstinence", "objective", "monthly", "limit", "duration", "cap", "timed", "period")
    fun daily(type: String) = type in setOf("limit", "duration", "cap", "timed", "challenge", "period")

    fun dueText(due: String): String {
        val n = daysUntil(due)
        return when {
            n < 0 -> "En retard de ${pl(-n, "jour", "jours")}"
            n == 0L -> "À rendre aujourd'hui"
            n == 1L -> "À rendre demain"
            else -> "Dans $n jours · ${fmtDate(due)}"
        }
    }
    fun daysLabel(days: List<Int>): String = when {
        days.size == 7 -> "tous les jours"
        days.sorted() == listOf(0, 1, 2, 3, 4) -> "en semaine"
        days.sorted() == listOf(5, 6) -> "le week-end"
        else -> days.sorted().joinToString(", ") { WD_SHORT[it] }
    }
    fun endText(d: String): String { val n = daysUntil(d); return if (n < 0) "terminé" else if (n == 0L) "se termine aujourd'hui" else "encore ${pl(n, "jour", "jours")}" }
    fun unitSfx(g: Goal) = if (g.unit.isNotEmpty()) " " + g.unit else ""

    fun metaLine(g: Goal): String = when (g.type) {
        "routine" -> if (g.mode == "days") (if (g.days.size == 7) "Tous les jours" else g.days.sorted().joinToString(", ") { WD_SHORT[it] }) else "${g.perWeek}× par semaine"
        "abstinence" -> "Depuis le ${fmtDate(cleanStart(g))}"
        "objective" -> "${fmtNum(objProgress(g))} / ${fmtNum(g.target)} ${g.unit}".trim()
        "monthly" -> "${fmtNum(monthProgress(g))} / ${fmtNum(g.target)} ${g.unit} en ${monthName(curMonth())}".replace(Regex("\\s+"), " ")
        "deadline" -> if (g.completedAt != null) "Terminé le ${fmtDate(ymd(g.completedAt!!))}" else dueText(g.due)
        "limit" -> "${fmtNum(g.max)} ${g.unit} max par ${if (g.period == "week") "semaine" else "jour"}"
        "duration" -> "${fmtNum(g.minutes)} min par jour"
        "cap" -> "${fmtNum(g.target)}${unitSfx(g)} par jour"
        "measure" -> { val b = measureBest(g); "${if (g.dir == "down") "Plus bas = mieux" else "Plus haut = mieux"}${g.targetOrNull?.let { " · cible ${measureFmt(g, it)}${if (g.timeFmt) "" else " " + g.unit}" } ?: ""}${b?.let { " · record ${measureFmt(g, it)}" } ?: ""}" }
        "timed" -> "${TIMED_LBL[g.mode]} ${g.time} · ${daysLabel(g.days)}"
        "project" -> { val (d, n) = projectCounts(g); "$d/$n étapes${if (g.due.isNotEmpty() && g.completedAt == null) " · " + dueText(g.due) else ""}" }
        "challenge" -> when {
            g.completedAt != null -> "Réussi : ${g.length} jours sur ${g.length}"
            g.failedAt != null -> "Interrompu au jour ${g.failDay ?: 0}"
            else -> { val k = challengeDay(g); if (k < 1) "Commence le ${fmtDate(g.start)}" else "Jour ${minOf(k, g.length)} sur ${g.length}" }
        }
        "program" -> if (g.completedAt != null) "Programme terminé : ${g.weeksDone.size}/${g.weeks} semaines réussies" else { val w = programWeek(g); if (w < 1) "Commence le ${fmtDate(g.start)}" else "Semaine $w/${g.weeks} · cible ${fmtNum(programTarget(g, w))} ${g.unit}" }
        "group" -> "${fmtNum(round1(groupTotal(g)))} / ${fmtNum(g.target)} ${g.unit} · ${pl(g.members?.size ?: 1, "membre", "membres")}${if (g.endDate.isNotEmpty() && g.completedAt == null) " · " + endText(g.endDate) else ""}"
        "duel" -> { val o = userById(duelOpp(g)); "Contre ${o?.let { first(it.name) } ?: "?"} · ${if (g.completedAt != null) (g.winner?.let { "victoire de ${if (it == meId()) "toi" else first(userById(it)?.name)}" } ?: "égalité") else endText(g.endDate)}" }
        "period" -> "${cap(periodDays(g))} · ${g.start}–${g.end}"
        else -> ""
    }

    fun statBig(g: Goal): Stat {
        val s = goalStreak(g)
        return when (g.type) {
            "routine" -> if (g.mode == "days") Stat("$s", if (s > 1) "séances d'affilée" else "séance d'affilée") else Stat("$s", if (s > 1) "semaines réussies" else "semaine réussie")
            "abstinence" -> Stat("$s", if (s > 1) "jours sans" else "jour sans")
            "objective" -> Stat("${minOf(100, Math.round(objProgress(g) / g.target * 100).toInt())}%", "accompli")
            "monthly" -> Stat("${minOf(100, Math.round(monthProgress(g) / g.target * 100).toInt())}%", "ce mois-ci")
            "deadline" -> if (g.completedAt != null) Stat("Fait", "") else { val n = daysUntil(g.due); if (n < 0) Stat("${-n}", "jours de retard") else Stat("$n", if (n > 1) "jours restants" else "jour restant") }
            "limit" -> Stat("$s", if (s > 1) "jours sous la limite" else "jour sous la limite")
            "duration" -> Stat("$s", if (s > 1) "jours réussis" else "jour réussi")
            "cap" -> Stat("$s", if (s > 1) "jours au-dessus du cap" else "jour au-dessus du cap")
            "measure" -> measureStat(g)
            "timed" -> Stat("$s", if (s > 1) "jours dans les temps" else "jour dans les temps")
            "project" -> { val (d, n) = projectCounts(g); Stat("${if (n > 0) Math.round(d * 100.0 / n) else 0}%", "accompli") }
            "challenge" -> Stat("${challengeDone(g)}/${g.length}", "jours validés")
            "program" -> { val w = programWeek(g).coerceIn(1, g.weeks); Stat("${minOf(100, Math.round(programSum(g, w) / programTarget(g, w) * 100).toInt())}%", "de la semaine $w") }
            "group" -> Stat("${minOf(100, Math.round(groupTotal(g) / g.target * 100).toInt())}%", "du total commun")
            "duel" -> { val m = if (isMember(g, meId())) meId() else g.members?.firstOrNull() ?: g.userId; val d = duelScore(g, m) - duelScore(g, duelOpp(g, m)); if (d == 0.0) Stat("=", "égalité") else Stat((if (d > 0) "+" else "−") + fmtNum(round1(Math.abs(d))), "${g.unit} ${if (d > 0) "d'avance" else "de retard"}") }
            "period" -> Stat("$s", if (s > 1) "périodes tenues" else "période tenue")
            else -> Stat("$s", "")
        }
    }

    /** Barre de progression affichée sur la ligne ou la carte (null : aucune). */
    fun barPct(g: Goal): Double? = when (g.type) {
        "objective" -> objProgress(g) / g.target * 100
        "monthly" -> monthProgress(g) / g.target * 100
        "limit" -> limitUsed(g) / maxOf(g.max, 1.0) * 100
        "duration" -> durationLiveMins(g) / g.minutes * 100
        "cap" -> capDay(g) / g.target * 100
        "project" -> projectCounts(g).let { (d, n) -> if (n > 0) d * 100.0 / n else 0.0 }
        "challenge" -> challengeDone(g) * 100.0 / g.length
        "program" -> { val w = programWeek(g).coerceIn(1, g.weeks); programSum(g, w) / programTarget(g, w) * 100 }
        "group" -> groupTotal(g) / g.target * 100
        "duel" -> { val a = duelScore(g, meId()); val b = duelScore(g, duelOpp(g)); if (a + b > 0) a / (a + b) * 100 else 50.0 }
        else -> null
    }

    fun todayPlan(id: String = meId()): Plan {
        val t = today(); val wd = wdIdx(todayDate()); val due = mutableListOf<DueItem>(); val ongoing = mutableListOf<Goal>()
        for (g in myGoals(id).filter { !it.archived }) {
            when (g.type) {
                "routine" -> if (g.mode == "days") { if (g.days.contains(wd)) due.add(DueItem(g, hasCheck(g.id, t))) } else {
                    val c = weekCount(g, weekStart(todayDate())); val dn = hasCheck(g.id, t)
                    if (c < g.perWeek || dn) due.add(DueItem(g, dn, "$c/${g.perWeek} cette semaine"))
                }
                "abstinence" -> if (g.since <= t) due.add(DueItem(g, hasCheck(g.id, t)))
                "objective", "monthly", "deadline" -> if (g.completedAt == null) ongoing.add(g)
                "limit", "duration", "cap" -> due.add(DueItem(g, hasCheck(g.id, t)))
                "measure" -> ongoing.add(g)
                "timed" -> { val k = timedDate(g); if (g.days.contains(wdIdx(parseYmd(k))) && k >= ymd(g.createdAt)) due.add(DueItem(g, hasCheck(g.id, k))) }
                "project" -> if (g.completedAt == null) ongoing.add(g)
                "challenge" -> if (g.completedAt == null && g.failedAt == null) { val k = challengeDay(g); if (k >= 1 && k <= g.length) due.add(DueItem(g, hasCheck(g.id, t))) }
                "program" -> if (g.completedAt == null) { val w = programWeek(g); if (w >= 1 && w <= g.weeks) ongoing.add(g) }
                "group" -> if (socialOn() && g.completedAt == null && g.failedAt == null) ongoing.add(g)
                "duel" -> if (socialOn() && g.completedAt == null) ongoing.add(g)
                "period" -> if (periodOn(g, todayDate()) && t >= ymd(g.createdAt)) due.add(DueItem(g, hasCheck(g.id, t)))
            }
        }
        ongoing.sortBy { if (it.type == "deadline") daysUntil(it.due) else 999L }
        // Journée difficile : seuls les essentiels restent à faire (ceux déjà validés restent affichés)
        val u = userById(id)
        if (u != null && t in u.hardDays) return Plan(due.filter { it.g.id in u.essentials || it.done }, ongoing)
        return Plan(due, ongoing)
    }

    fun isClosedGoal(g: Goal) = g.archived || (g.type in CLOSABLE && (g.completedAt != null || g.failedAt != null))

    /* ---------------------------------------------------------------- notifications */
    fun notify(userId: String, text: String, type: String, goalId: String? = null, view: String? = null) {
        if (store.acct && userId != meId()) {
            if (type in setOf("mod", "like", "comment", "official")) return
            val t = if (type in setOf("badge", "verify", "friend")) type else "info"
            cloud?.notifyOther(userId, text.take(300), t, goalId)
            return
        }
        if (userId != meId()) return
        val n = Notif(uid(), userId, type, text, nowMs(), read = false, view = view, goalId = goalId)
        db.notifs.add(n)
        pruneNotifs()
        hooks.localNotification(n)
    }
    private fun pruneNotifs() {
        val mine = db.notifs.filter { !it.srv }.sortedByDescending { it.at }
        if (mine.size > 150) { val drop = mine.drop(150).toSet(); db.notifs.removeAll { it in drop } }
    }
    fun unread() = db.notifs.count { it.userId == meId() && !it.read }

    /* ---------------------------------------------------------------- validations */
    fun addCheck(g: Goal, kind: String, value: Double, pts: Int, date: String = today(), userId: String = meId().ifEmpty { g.userId }): Checkin {
        val c = Checkin(uid(), g.id, userId, date, kind, value, pts, at = nowMs())
        if (g.buddy != null && g.buddyVerify && userId == g.userId && kind in setOf("done", "progress", "measure")) { c.pending = true; c.ptsPending = pts; c.pts = 0 }
        db.checkins.add(c)
        return c
    }
    fun bumpPts(c: Checkin, pts: Int) { if (c.pending) c.ptsPending = pts else c.pts = pts }

    fun afterValidation(g: Goal, pts: Int, wasLit: Boolean) {
        hooks.sparks(pts)
        val u = me() ?: return; val st = globalStreak(u.id)
        if (!wasLit) hooks.toast(if (st.n > 1) "Flamme allumée : ${pl(st.n, "jour", "jours")} de série !" else "Flamme allumée ! Ta série commence.", "flame")
        val n = goalStreak(g)
        if (n in MILESTONES && !g.milestones.contains(n) && g.type in setOf("routine", "abstinence", "limit", "duration", "cap", "timed", "period")) {
            g.milestones = g.milestones + n
            val unit = statBig(g).l
            notify(u.id, "Palier débloqué : $n $unit pour « ${g.title} ».", "badge", g.id)
            autoPost(u, g, "$n $unit pour « ${g.title} ». La flamme tient bon.")
        }
        val key = "${st.n}@${today()}"
        if (st.n in listOf(3, 7, 14, 30, 60, 100, 365) && !u.milestones.contains(key)) {
            u.milestones.add(key)
            notify(u.id, "Série de ${st.n} jours ! Tu gagnes un nouveau palier de flamme.", "badge")
        }
        hooks.liveChanged()
        queueBadgeCheck()
        chainNext(g).firstOrNull()?.let { hooks.chained(g, it) }
    }

    fun autoPost(u: User, g: Goal, text: String) {
        if (!socialOn(u) || g.visibility == "private" || isBanned(u) || maintBlocks(u)) return
        if (store.acct) cloud?.autoPost(g, text)
    }

    private fun wasLit() = globalStreak(meId()).lit

    fun todayDone(g: Goal, date: String = today()) = db.checkins.firstOrNull { it.goalId == g.id && it.date == date && it.kind == "done" && it.userId == meId() }
    private fun undoDone(ex: Checkin) { db.checkins.remove(ex); db.checkins = db.checkins.toMutableList(); store.commit(); hooks.toast("Validation annulée.", "x"); hooks.liveChanged() }
    private fun winDone(g: Goal, pts: Int, date: String = today()) {
        val lit = wasLit()
        addCheck(g, "done", 1.0, pts, date); justDone = g.id
        store.commit(); afterValidation(g, pts, lit); store.commit(); checkPerfect()
    }

    /** Résultat d'une validation qui demande une saisie : la fenêtre de progrès doit s'ouvrir. */
    sealed class VResult { object Done : VResult(); class AskProgress(val goalId: String) : VResult(); class OpenGoal(val goalId: String) : VResult() }

    fun validate(id: String): VResult {
        val g = goalById(id); val u = me() ?: return VResult.Done
        if (g == null || !isMember(g, u.id)) return VResult.Done
        // Sous-tâches : la validation se fait en cochant chaque étape dans la fiche
        if (g.steps.isNotEmpty() && g.type in STEP_TYPES && !hasCheck(g.id, stepDate(g)) && !stepsComplete(g)) return VResult.OpenGoal(g.id)
        val t = today()
        when (g.type) {
            "routine", "abstinence" -> {
                val ex = db.checkins.firstOrNull { it.goalId == id && it.date == t && it.kind == "done" }
                if (ex != null) { undoDone(ex); return VResult.Done }
                if (g.type == "routine" && g.mode == "days" && !g.days.contains(wdIdx(todayDate()))) { hooks.toast("Cette routine n'est pas prévue aujourd'hui.", "cal"); return VResult.Done }
                winDone(g, typeInfo(g.type).pts)
            }
            "deadline" -> {
                if (g.completedAt != null) return VResult.Done
                val lit = wasLit()
                val pts = typeInfo("deadline").pts + if (t <= g.due) 20 else 0
                g.completedAt = nowMs(); addCheck(g, "done", 1.0, pts)
                autoPost(u, g, "Deadline bouclée : « ${g.title} »${if (t <= g.due) ", dans les temps" else ""}.")
                store.commit(); afterValidation(g, pts, lit); store.commit()
            }
            "limit" -> {
                todayDone(g)?.let { undoDone(it); return VResult.Done }
                if (limitUsed(g) > g.max) { hooks.toast("Limite dépassée ${if (g.period == "week") "cette semaine" else "aujourd'hui"} : pas de validation. Tu repars à zéro ${if (g.period == "week") "lundi" else "demain"}.", "x"); return VResult.Done }
                winDone(g, typeInfo("limit").pts)
            }
            "duration" -> toggleTimer(g)
            "timed" -> {
                val k = timedDate(g)
                todayDone(g, k)?.let { undoDone(it); return VResult.Done }
                if (!g.days.contains(wdIdx(parseYmd(k)))) { hooks.toast("Cet horaire n'est pas prévu aujourd'hui.", "cal"); return VResult.Done }
                if (!timedWindow(g).open) { hooks.toast(if (g.mode == "before") "Trop tard : c'était à faire avant ${g.time}. Demain sera le bon jour." else "Patience : ça compte à partir de ${g.time}.", "clock"); return VResult.Done }
                winDone(g, typeInfo("timed").pts, k)
            }
            "project" -> return VResult.OpenGoal(g.id)
            "challenge" -> {
                if (g.completedAt != null || g.failedAt != null) return VResult.Done
                todayDone(g)?.let { undoDone(it); return VResult.Done }
                val lit = wasLit()
                addCheck(g, "done", 1.0, typeInfo("challenge").pts); justDone = g.id
                var pts = typeInfo("challenge").pts
                if (challengeDone(g) >= g.length) {
                    g.completedAt = nowMs(); pts += 150
                    finishWith(g, 150, "Challenge réussi : ${g.length} jours pour « ${g.title} » ! +150 flammes.", "Challenge terminé : ${g.length} jours d'affilée pour « ${g.title} ».")
                    celebrateGoal(g, "Challenge réussi !", "${g.length} jours d'affilée, sans lâcher. Chapeau.")
                }
                store.commit(); afterValidation(g, pts, lit); store.commit(); checkPerfect()
            }
            "group", "duel" -> if (g.completedAt == null) return VResult.AskProgress(g.id)
            "period" -> {
                todayDone(g, t)?.let { undoDone(it); return VResult.Done }
                if (!periodOn(g, todayDate())) { hooks.toast("Cette période n’est pas prévue aujourd’hui.", "cal"); return VResult.Done }
                if (!periodWindow(g).open) { hooks.toast(if (nowHM() < g.start) "Patience : la période commence à ${g.start}." else "Trop tard : la période s’est terminée à ${g.end}. Rendez-vous à la prochaine.", "clock"); return VResult.Done }
                winDone(g, typeInfo("period").pts, t)
            }
            else -> return VResult.AskProgress(g.id)
        }
        return VResult.Done
    }

    fun progressInfo(g: Goal): ProgressInfo = when (g.type) {
        "cap" -> ProgressInfo("Ajouter", "${fmtNum(capDay(g))} / ${fmtNum(g.target)}${unitSfx(g)} aujourd'hui", "Quantité${if (g.unit.isNotEmpty()) " (${g.unit})" else ""}", capQuick(g))
        "duration" -> ProgressInfo("Ajouter des minutes", "${fmtClock(durationMins(g) * 60)} / ${fmtNum(g.minutes)} min aujourd'hui", "Minutes", listOf(5.0, 10.0, 15.0, 30.0), quickAbsolute = true)
        "measure" -> { val p = measurePts(g); val last = p.lastOrNull()?.value
            ProgressInfo("Nouvelle mesure", if (last != null) "dernière mesure ${measureFmt(g, last)}${unitSfx(g)}" else "première mesure",
                if (g.timeFmt) "Temps (mm:ss ou h:mm:ss)" else "Valeur${if (g.unit.isNotEmpty()) " (${g.unit})" else ""}", emptyList(), text = true, value = last?.let { measureFmt(g, it) } ?: "") }
        "program" -> { val w = programWeek(g); ProgressInfo("Ajouter un progrès", "semaine $w : ${fmtNum(programSum(g, w))}/${fmtNum(programTarget(g, w))} ${g.unit}", "Quantité (${g.unit})", listOf(1.0, 2.0, 5.0, 10.0)) }
        "group" -> ProgressInfo("Ajouter ma contribution", "${fmtNum(round1(groupTotal(g)))} / ${fmtNum(g.target)} ${g.unit} ensemble", "Ta part (${g.unit})", listOf(1.0, 2.0, 5.0, 10.0))
        "duel" -> { val o = userById(duelOpp(g)); ProgressInfo("Ajouter à mon score", "toi ${fmtNum(round1(duelScore(g, meId())))} – ${fmtNum(round1(duelScore(g, o?.id ?: "")))} ${first(o?.name)}", "Quantité (${g.unit})", listOf(1.0, 2.0, 5.0, 10.0)) }
        "monthly" -> ProgressInfo("Ajouter un progrès", "${fmtNum(monthProgress(g))} / ${fmtNum(g.target)} ${g.unit} en ${monthName(curMonth())}", "Quantité à ajouter${if (g.unit.isNotEmpty()) " (${g.unit})" else ""}", listOf(1.0, 2.0, 5.0, 10.0))
        else -> ProgressInfo("Ajouter un progrès", "${fmtNum(objProgress(g))} / ${fmtNum(g.target)} ${g.unit}", "Quantité à ajouter${if (g.unit.isNotEmpty()) " (${g.unit})" else ""}", listOf(1.0, 2.0, 5.0, 10.0))
    }

    /** Valeur saisie dans la fenêtre de progrès (temps « mm:ss » pour une mesure chronométrée). */
    fun parseProgress(g: Goal, raw: String): Double {
        val r = raw.trim().replace(',', '.')
        if (r.isEmpty()) return Double.NaN
        if (g.type == "measure" && g.timeFmt) {
            if (r.contains(':')) { val parts = r.split(':').map { it.toDoubleOrNull() }; if (parts.any { it == null }) return Double.NaN; return parts.fold(0.0) { s, x -> s * 60 + x!! } }
            return (r.toDoubleOrNull() ?: Double.NaN) * 60
        }
        return r.toDoubleOrNull() ?: Double.NaN
    }

    fun addProgress(id: String, value: Double) {
        val g = goalById(id) ?: return; val u = me() ?: return
        when (g.type) {
            "duration" -> { addTimeMinutes(g, value, false); return }
            "cap" -> { capAdd(g, value); return }
            "measure" -> { measureProgress(g, value); return }
            "program" -> { programProgress(g, value); return }
            "group" -> { groupProgress(g, value); return }
            "duel" -> { duelProgress(g, value); return }
        }
        val lit = wasLit()
        var pts = typeInfo(g.type).pts
        val c = addCheck(g, "progress", value, pts)
        if (g.type == "monthly") {
            val k = curMonth()
            if (!g.monthsDone.contains(k) && monthProgress(g, k) >= g.target) {
                g.monthsDone = g.monthsDone + k; pts += MONTH_BONUS
                bumpPts(c, pts)
                notify(u.id, "Objectif de ${monthName(k)} atteint : « ${g.title} » ! +$MONTH_BONUS flammes bonus.", "badge", g.id)
                autoPost(u, g, "Objectif de ${monthName(k)} atteint : ${fmtNum(g.target)} ${g.unit} pour « ${g.title} ».".replace(Regex("\\s+"), " "))
            }
        } else if (g.completedAt == null && objProgress(g) >= g.target) {
            g.completedAt = nowMs(); pts += 100
            bumpPts(c, pts)
            notify(u.id, "Objectif atteint : « ${g.title} » ! +100 flammes bonus.", "badge", g.id)
            autoPost(u, g, "Objectif atteint : ${fmtNum(g.target)} ${g.unit} pour « ${g.title} ».")
        }
        store.commit(); afterValidation(g, pts, lit); store.commit()
    }

    fun relapse(id: String) {
        val g = goalById(id) ?: return
        db.checkins = db.checkins.filter { !(it.goalId == id && it.date == today() && it.kind == "done") }.toMutableList()
        addCheck(g, "relapse", 0.0, 0)
        g.milestones = emptyList()
        store.commit()
        hooks.toast("Compteur remis à zéro. Un écart n'efface pas le chemin parcouru : tu repars dès demain.", "repeat")
    }

    private fun finishWith(g: Goal, pts: Int, msg: String, post: String?) {
        val last = db.checkins.lastOrNull()
        if (last != null && last.goalId == g.id) bumpPts(last, (if (last.pending) last.ptsPending ?: 0 else last.pts) + pts)
        notify(g.userId, msg, "badge", g.id)
        if (post != null) userById(g.userId)?.let { autoPost(it, g, post) }
        queueBadgeCheck()
    }
    private fun celebrateGoal(g: Goal, title: String, text: String) {
        if (!isMember(g, meId())) return
        hooks.celebrate(Celebration.Perfect(typeInfo(g.type).label, title, text, listOf(g.icon), emptyList(), "$title ${g.icon} « ${g.title} » sur PepsGoal."))
    }

    /* ---------------------------------------------------------------- Limite */
    fun limitUsed(g: Goal): Double {
        val t = today()
        return if (g.period == "week") sumKind(g, "use") { it.date >= ymd(weekStart(todayDate())) && it.date <= t } else sumKind(g, "use") { it.date == t }
    }
    fun limitAdd(id: String) {
        val g = goalById(id) ?: return
        addCheck(g, "use", 1.0, 0)
        val used = limitUsed(g); val ex = todayDone(g)
        if (used > g.max && ex != null) { db.checkins.remove(ex); db.checkins = db.checkins.toMutableList(); hooks.toast("Limite dépassée : la validation du jour est retirée.", "x") }
        else if (used > g.max) hooks.toast("Limite dépassée : ${fmtNum(used)}/${fmtNum(g.max)} ${g.unit}.", "x")
        else if (used == g.max) hooks.toast("Limite atteinte : ${fmtNum(g.max)} ${g.unit}. On s'arrête là !", "gauge")
        else hooks.toast("${fmtNum(used)}/${fmtNum(g.max)} ${g.unit}", "gauge")
        store.commit()
    }
    fun limitSub(id: String) {
        val list = db.checkins.filter { it.goalId == id && it.kind == "use" && it.date == today() }
        if (list.isEmpty()) { hooks.toast("Rien à retirer aujourd'hui.", "gauge"); return }
        db.checkins.remove(list.last()); db.checkins = db.checkins.toMutableList(); store.commit()
    }

    /* ---------------------------------------------------------------- Durée et minuteur */
    fun durationMins(g: Goal, k: String = today()) = sumKind(g, "progress", meId()) { it.date == k }
    fun timerOf(g: Goal) = db.timers[meId()]?.takeIf { it.goalId == g.id }
    fun runningTimer() = db.timers[meId()]
    fun durationLiveMins(g: Goal): Double { val t = timerOf(g); return durationMins(g) + if (t != null) (nowMs() - t.start) / 60000.0 else 0.0 }
    fun toggleTimer(g: Goal) {
        val u = me() ?: return
        val t = db.timers[u.id]
        if (t != null && t.goalId == g.id) { stopTimer(false); return }
        if (t != null) stopTimer(true)
        db.timers[u.id] = Timer(g.id, nowMs())
        store.commit(); hooks.toast("Minuteur lancé : ${g.title}", "clock"); hooks.liveChanged()
    }
    fun stopTimer(silent: Boolean) {
        val u = me() ?: return
        val t = db.timers.remove(u.id) ?: return
        val g = goalById(t.goalId); if (g == null) { store.commit(); hooks.liveChanged(); return }
        var mins = Math.round((nowMs() - t.start) / 6000.0) / 10.0
        if (mins > 240) { mins = 240.0; hooks.toast("Minuteur oublié ? La session a été plafonnée à 4 h.", "clock") }
        if (mins >= .1) addTimeMinutes(g, mins, silent) else store.commit()
        hooks.liveChanged()
    }
    fun addTimeMinutes(g: Goal, mins: Double, silent: Boolean) {
        val lit = wasLit()
        addCheck(g, "progress", mins, 0)
        if (!hasCheck(g.id, today()) && durationMins(g) >= g.minutes) {
            addCheck(g, "done", 1.0, typeInfo("duration").pts); justDone = g.id
            store.commit(); afterValidation(g, typeInfo("duration").pts, lit)
            hooks.toast("${fmtNum(g.minutes)} minutes atteintes pour « ${g.title} » !", "clock"); store.commit(); checkPerfect(); return
        }
        store.commit(); if (!silent) hooks.toast("+${fmtNum(mins)} min enregistrées.", "clock")
        hooks.liveChanged()
    }
    /** Appelé chaque seconde par l'application : arrête le minuteur quand le temps du jour est atteint. */
    fun timerTick() {
        val t = runningTimer() ?: return
        val g = goalById(t.goalId) ?: return
        if (!hasCheck(g.id, today()) && durationLiveMins(g) >= g.minutes) stopTimer(false)
    }

    /* ---------------------------------------------------------------- Cap quotidien */
    /** Quantité saisie aujourd'hui (ou le jour [k]) pour un cap quotidien. */
    fun capDay(g: Goal, k: String = today()) = sumKind(g, "progress", meId()) { it.date == k }
    /** Raccourcis de saisie proportionnés au cap (10 000 pas → 1 000, 2 500, 5 000…). */
    fun capQuick(g: Goal): List<Double> {
        val t = g.target
        val base = listOf(.1, .25, .5).map { x -> val v = t * x; val mag = Math.pow(10.0, Math.floor(Math.log10(maxOf(v, 1.0)))); Math.max(1.0, Math.round(v / mag) * mag) }
        return (base + t).distinct()
    }
    fun capAdd(g: Goal, value: Double, silent: Boolean = false) {
        val lit = wasLit()
        addCheck(g, "progress", value, 0)
        capCheck(g, lit, silent)
    }
    /** Fixe la quantité du jour à [value] (lecture Santé) : ajoute ou retire la différence. */
    fun capSet(g: Goal, value: Double) {
        val d = value - capDay(g)
        if (Math.abs(d) < 1e-6) return
        val lit = wasLit()
        addCheck(g, "progress", d, 0)
        capCheck(g, lit, true)
    }
    private fun capCheck(g: Goal, lit: Boolean, silent: Boolean) {
        val v = capDay(g); val ex = todayDone(g)
        if (ex == null && v >= g.target) {
            addCheck(g, "done", 1.0, typeInfo("cap").pts); justDone = g.id
            store.commit(); afterValidation(g, typeInfo("cap").pts, lit)
            hooks.toast("Cap atteint : ${fmtNum(g.target)}${unitSfx(g)} pour « ${g.title} » !", "bolt"); store.commit(); checkPerfect(); return
        }
        if (ex != null && v < g.target) { db.checkins.remove(ex); db.checkins = db.checkins.toMutableList() }
        store.commit(); if (!silent) hooks.toast("${fmtNum(v)} / ${fmtNum(g.target)}${unitSfx(g)} aujourd'hui", "bolt")
        hooks.liveChanged()
    }

    /* ---------------------------------------------------------------- Mesure */
    fun measurePts(g: Goal) = db.checkins.filter { it.goalId == g.id && it.kind == "measure" }.sortedWith(compareBy({ it.date }, { it.at }))
    fun measureFmt(g: Goal, v: Double) = if (g.timeFmt) fmtClock(v) else fmtNum(Math.round(v * 100) / 100.0)
    fun measureBetter(g: Goal, a: Double, b: Double) = if (g.dir == "down") a < b else a > b
    fun measureBest(g: Goal): Double? = measurePts(g).fold(null as Double?) { b, c -> if (b == null || measureBetter(g, c.value, b)) c.value else b }
    fun measureStat(g: Goal): Stat {
        val p = measurePts(g); if (p.isEmpty()) return Stat("—", "aucune mesure")
        val last = p.last().value; val d = last - p.first().value; val u = if (g.timeFmt) "" else " " + g.unit
        return Stat(measureFmt(g, last) + u, when {
            p.size < 2 -> "première mesure"
            d == 0.0 -> "stable depuis le début"
            else -> "${if (measureBetter(g, last, p.first().value)) "en progrès" else "en recul"} de ${measureFmt(g, Math.abs(d))}${if (g.timeFmt) "" else " " + g.unit} depuis le début"
        })
    }
    private fun measureProgress(g: Goal, v: Double) {
        val u = me() ?: return; val lit = wasLit(); val prevBest = measureBest(g)
        val pts = if (db.checkins.any { it.goalId == g.id && it.kind == "measure" && it.date == today() }) 0 else typeInfo("measure").pts
        addCheck(g, "measure", v, pts)
        var total = pts
        val us = if (g.timeFmt) "" else " " + g.unit
        if (prevBest != null && measureBetter(g, v, prevBest)) hooks.toast("Nouveau record : ${measureFmt(g, v)}$us !", "star")
        val tg = g.targetOrNull
        if (tg != null && g.targetHit == null && (v == tg || measureBetter(g, v, tg))) {
            g.targetHit = today(); total += 80
            finishWith(g, 80, "Cible atteinte pour « ${g.title} » : ${measureFmt(g, v)} ! +80 flammes.", "Cible atteinte : ${measureFmt(g, v)}$us pour « ${g.title} ».")
            celebrateGoal(g, "Cible atteinte !", "${measureFmt(g, v)}$us : tu as atteint ta cible.")
        }
        store.commit(); if (total > 0) afterValidation(g, total, lit); store.commit()
        if (u.id.isEmpty()) return
    }

    /* ---------------------------------------------------------------- Horaire */
    val TIMED_LBL = mapOf("before" to "Avant", "after" to "Après", "notafter" to "Rien après")
    fun timedDate(g: Goal) = if (g.mode == "notafter") yday() else today()
    fun timedWindow(g: Goal): Window {
        val h = nowHM()
        return when (g.mode) {
            "before" -> if (h < g.time) Window(true, "Encore ${untilTxt(g.time)} pour valider") else Window(false, "Créneau fermé à ${g.time}")
            "after" -> if (h >= g.time) Window(true, "Créneau ouvert depuis ${g.time}") else Window(false, "Ça compte à partir de ${g.time}")
            else -> Window(true, "Hier soir, rien après ${g.time} ?")
        }
    }

    /* ---------------------------------------------------------------- Projet */
    fun projectCounts(g: Goal): Pair<Int, Int> { val t = g.tasks; return t.count { it.done } to t.size }
    fun taskToggle(gid: String, tid: String) {
        val g = goalById(gid) ?: return; if (g.userId != meId()) return
        val tasks = g.tasks; val t = tasks.firstOrNull { it.id == tid } ?: return
        val lit = wasLit()
        t.done = !t.done; g.tasks = tasks
        if (t.done) {
            val c = addCheck(g, "progress", 1.0, typeInfo("project").pts); c.taskId = tid
            var pts = typeInfo("project").pts
            val (d, n) = projectCounts(g)
            if (d == n && g.completedAt == null) {
                g.completedAt = nowMs(); pts += 80
                finishWith(g, 80, "Projet bouclé : « ${g.title} » ! +80 flammes.", "Projet bouclé : « ${g.title} », $n étapes franchies.")
                celebrateGoal(g, "Projet bouclé !", "$n étapes franchies, une par une.")
            }
            store.commit(); afterValidation(g, pts, lit); store.commit()
        } else {
            db.checkins.firstOrNull { it.goalId == gid && it.taskId == tid }?.let { db.checkins.remove(it); db.checkins = db.checkins.toMutableList() }
            g.completedAt = null; store.commit()
        }
    }
    fun taskAdd(gid: String, text: String) {
        val g = goalById(gid) ?: return; val t = text.trim(); if (t.isEmpty()) return
        g.tasks = g.tasks + Task(uid(), t.take(120), false)
        if (g.completedAt != null) g.completedAt = null
        store.commit()
    }
    fun taskDel(gid: String, tid: String) {
        val g = goalById(gid) ?: return; if (g.userId != meId()) return
        g.tasks = g.tasks.filter { it.id != tid }
        db.checkins = db.checkins.filter { !(it.goalId == gid && it.taskId == tid) }.toMutableList()
        store.commit()
    }
    fun taskMove(gid: String, tid: String, to: Int): Boolean {
        val g = goalById(gid) ?: return false; if (g.userId != meId()) return false
        val list = g.tasks.toMutableList(); val i = list.indexOfFirst { it.id == tid }; if (i < 0) return false
        val j = to.coerceIn(0, list.size - 1); if (i == j) return false
        val t = list.removeAt(i); list.add(j, t); g.tasks = list
        store.commit(); return true
    }

    /* ---------------------------------------------------------------- Challenge */
    fun challengeDay(g: Goal) = (daysBetween(parseYmd(g.start), todayDate()) + 1).toInt()
    fun challengeDone(g: Goal): Int { var n = 0; var d = parseYmd(g.start); repeat(g.length) { if (hasCheck(g.id, ymd(d))) n++; d = d.plusDays(1) }; return n }
    private fun challengeEvaluate(g: Goal): Boolean {
        if (g.completedAt != null || g.failedAt != null) return false
        var d = parseYmd(g.start)
        for (i in 0 until g.length) {
            val k = ymd(d); if (k >= today()) break
            if (!hasCheck(g.id, k)) {
                g.failedAt = nowMs(); g.failDay = i + 1
                notify(g.userId, "Challenge « ${g.title} » interrompu au jour ${i + 1}. Tu peux le relancer quand tu veux depuis sa fiche.", "deadline", g.id)
                return true
            }
            d = d.plusDays(1)
        }
        return false
    }
    fun challengeRestart(id: String) {
        val g = goalById(id) ?: return
        g.start = today(); g.failedAt = null; g.data.remove("failDay"); g.completedAt = null
        store.commit(); hooks.toast("C'est reparti : jour 1 sur ${g.length}.", "trophy")
    }

    /* ---------------------------------------------------------------- Paliers */
    fun programWeek(g: Goal) = (daysBetween(weekStart(parseYmd(g.start)), weekStart(todayDate())) / 7 + 1).toInt()
    fun programTarget(g: Goal, w: Int) = g.base + g.step * (w - 1)
    fun programSum(g: Goal, w: Int): Double {
        val s = weekStart(parseYmd(g.start)).plusDays((w - 1) * 7L); val a = ymd(s); val b = ymd(s.plusDays(6))
        return sumKind(g, "progress") { it.date >= a && it.date <= b }
    }
    private fun programProgress(g: Goal, v: Double) {
        val lit = wasLit(); val w = programWeek(g)
        addCheck(g, "progress", v, typeInfo("program").pts); var pts = typeInfo("program").pts
        if (w >= 1 && w <= g.weeks && !g.weeksDone.contains(w) && programSum(g, w) >= programTarget(g, w)) {
            g.weeksDone = g.weeksDone + w; pts += 30
            if (w >= g.weeks) {
                g.completedAt = nowMs(); pts += 120
                finishWith(g, 150, "Programme « ${g.title} » bouclé : ${g.weeksDone.size}/${g.weeks} semaines réussies ! +150 flammes.", "Programme terminé : « ${g.title} », ${g.weeks} semaines de progression.")
                celebrateGoal(g, "Programme terminé !", "${g.weeks} semaines, une cible plus haute à chaque fois. Tu l'as fait.")
            } else {
                finishWith(g, 30, "Palier de la semaine $w validé pour « ${g.title} » ! Semaine ${w + 1} : ${fmtNum(programTarget(g, w + 1))} ${g.unit}.", null)
                hooks.toast("Palier $w validé ! Prochaine cible : ${fmtNum(programTarget(g, w + 1))} ${g.unit}.", "star")
            }
        }
        store.commit(); afterValidation(g, pts, lit); store.commit()
    }
    private fun programEvaluate(g: Goal): Boolean {
        if (g.completedAt == null && programWeek(g) > g.weeks) {
            g.completedAt = nowMs()
            notify(g.userId, "Programme « ${g.title} » terminé : ${g.weeksDone.size}/${g.weeks} semaines réussies.", "badge", g.id)
            return true
        }
        return false
    }

    /* ---------------------------------------------------------------- Défi de groupe */
    fun groupTotal(g: Goal) = sumKind(g, "progress")
    fun groupByUser(g: Goal) = (g.members ?: listOf(g.userId)).map { it to sumKind(g, "progress", it) }.sortedByDescending { it.second }
    private fun groupProgress(g: Goal, v: Double) {
        val u = me() ?: return; val lit = wasLit()
        addCheck(g, "progress", v, typeInfo("group").pts)
        (g.members ?: emptyList()).filter { it != u.id }.forEach { notify(it, "${u.name} a ajouté ${fmtNum(v)} ${g.unit} au défi « ${g.title} ».", "badge", g.id) }
        groupCheckDone(g)
        store.commit(); afterValidation(g, typeInfo("group").pts, lit); store.commit()
    }
    private fun groupCheckDone(g: Goal) {
        if (g.completedAt != null || groupTotal(g) < g.target) return
        g.completedAt = nowMs()
        val ms = g.members ?: listOf(g.userId)
        // Seule ma part est enregistrée ici : chaque membre reçoit la sienne sur son appareil
        if (ms.contains(meId())) db.checkins.add(Checkin(uid(), g.id, meId(), today(), "done", 1.0, 100, at = nowMs()))
        ms.forEach { notify(it, "Défi de groupe réussi : ${fmtNum(g.target)} ${g.unit} ensemble pour « ${g.title} » ! +100 flammes.", "badge", g.id) }
        userById(g.userId)?.let { autoPost(it, g, "Défi de groupe réussi : ${fmtNum(g.target)} ${g.unit} à ${ms.size} pour « ${g.title} ».") }
        celebrateGoal(g, "Défi relevé ensemble !", "${fmtNum(g.target)} ${g.unit} à ${ms.size}. L'union fait la flamme.")
    }
    private fun groupEvaluate(g: Goal): Boolean {
        if (g.completedAt == null && g.failedAt == null && g.endDate.isNotEmpty() && today() > g.endDate) {
            g.failedAt = nowMs()
            notify(meId(), "Le défi « ${g.title} » s'est terminé à ${fmtNum(round1(groupTotal(g)))}/${fmtNum(g.target)} ${g.unit}. Bel effort collectif !", "badge", g.id)
            return true
        }
        if (g.completedAt == null && groupTotal(g) >= g.target) { groupCheckDone(g); return true }
        return false
    }

    /* ---------------------------------------------------------------- Duel */
    fun duelOpp(g: Goal, id: String = meId()): String { val ms = g.members ?: emptyList(); return ms.firstOrNull { it != id } ?: ms.getOrNull(1) ?: "" }
    fun duelScore(g: Goal, id: String) = sumKind(g, "progress", id)
    private fun duelProgress(g: Goal, v: Double) {
        val u = me() ?: return; val lit = wasLit()
        addCheck(g, "progress", v, typeInfo("duel").pts)
        notify(duelOpp(g), "${u.name} a marqué ${fmtNum(v)} ${g.unit} dans votre duel « ${g.title} ».", "badge", g.id)
        store.commit(); afterValidation(g, typeInfo("duel").pts, lit); store.commit()
    }
    private fun duelEvaluate(g: Goal): Boolean {
        if (g.completedAt != null || g.endDate.isEmpty() || today() <= g.endDate) return false
        g.completedAt = nowMs()
        val ms = g.members ?: return true
        val a = ms.getOrNull(0) ?: return true; val b = ms.getOrNull(1) ?: return true
        val sa = duelScore(g, a); val sb = duelScore(g, b)
        g.winner = if (sa == sb) null else if (sa > sb) a else b
        if (g.winner == meId()) db.checkins.add(Checkin(uid(), g.id, meId(), today(), "done", 1.0, 60, at = nowMs()))
        val w = g.winner
        notify(meId(), when { w == null -> "Duel « ${g.title} » terminé sur une égalité : ${fmtNum(sa)} – ${fmtNum(sb)}."; w == meId() -> "Victoire dans le duel « ${g.title} » ! +60 flammes."; else -> "Duel « ${g.title} » terminé : ${userById(w)?.name ?: "ton adversaire"} l'emporte. Revanche ?" }, "badge", g.id)
        if (w == meId()) celebrateGoal(g, "Victoire !", "Tu remportes le duel « ${g.title} ».")
        return true
    }

    /* ---------------------------------------------------------------- Période */
    fun periodOn(g: Goal, d: LocalDate): Boolean =
        if (g.freq == "month") { val dom = d.dayOfMonth; val last = d.lengthOfMonth(); g.mdays.any { minOf(it, last) == dom } } else g.days.contains(wdIdx(d))
    fun periodDays(g: Goal): String {
        if (g.freq == "month") { val m = g.mdays.sorted(); val f = { x: Int -> if (x == 1) "1er" else "$x" }; return if (m.size == 1) "le ${f(m[0])} du mois" else "les ${m.joinToString(", ") { f(it) }} du mois" }
        return daysLabel(g.days)
    }
    fun nextPeriods(g: Goal, n: Int = 4): List<LocalDate> {
        val out = mutableListOf<LocalDate>(); val now = nowHM(); var d = todayDate()
        for (i in 0 until 400) {
            if (out.size >= n) break
            if (periodOn(g, d) && !(i == 0 && (hasCheck(g.id, today()) || now > g.end))) out.add(d)
            d = d.plusDays(1)
        }
        return out
    }
    fun periodWindow(g: Goal): Window {
        val h = nowHM(); val late = addHM(g.end, PERIOD_GRACE)
        return when {
            h < g.start -> Window(false, "${g.start}–${g.end} · commence dans ${untilTxt(g.start)}")
            h <= g.end -> Window(true, "En cours jusqu’à ${g.end}", live = true)
            h <= late -> Window(true, "Terminée : validation possible jusqu’à $late")
            else -> Window(false, "Période terminée à ${g.end}")
        }
    }

    /** Évalue les fins de challenge, de duel, de programme… et prévient le binôme des oublis. */
    fun evaluateGoals(u: User, once: (String, () -> Unit) -> Unit) {
        var changed = false
        for (g in myGoals(u.id).filter { !it.archived }) {
            val ev = when (g.type) { "challenge" -> challengeEvaluate(g); "program" -> programEvaluate(g); "group" -> groupEvaluate(g); "duel" -> duelEvaluate(g); else -> false }
            if (ev) changed = true
            val buddy = g.buddy
            if (g.userId == u.id && buddy != null && g.buddyMiss && !isClosedGoal(g)) {
                val yd = todayDate().minusDays(1); val k = ymd(yd)
                val sched = when { g.type == "timed" -> g.mode != "notafter" && g.days.contains(wdIdx(yd)); daily(g.type) -> scheduled(g, yd); else -> scheduled(g, yd) }
                if (sched && k >= ymd(g.createdAt) && db.checkins.none { it.goalId == g.id && it.date == k && it.kind == "done" && it.userId == u.id }) {
                    once("miss" + g.id) {
                        val b = userById(buddy) ?: return@once
                        notify(b.id, "${u.name} a raté « ${g.title} » hier. Un petit mot d'encouragement ?", "verify", g.id)
                        notify(u.id, "Ton binôme ${b.name} a reçu une alerte pour ton oubli d'hier sur « ${g.title} ».", "verify", g.id)
                    }
                }
            }
        }
        if (changed) store.commit()
    }

    /* ---------------------------------------------------------------- rappels */
    /**
     * Rappels et évaluations (toutes les 30 s quand l'application est ouverte, et à chaque alarme en arrière-plan).
     * Chaque rappel n'est envoyé qu'une fois par jour (reminderLog).
     */
    fun checkReminders() {
        val u = me() ?: return
        if (store.acct && cloud?.pulledBefore == false) return
        applyFreezes(u)
        val t = today(); val h = nowHM()
        val plan = todayPlan(u.id); val pending = plan.due.count { !it.done }; val st = globalStreak(u.id)
        var fired = false
        val once = { key: String, fn: () -> Unit -> val k = "${u.id}|$t|$key"; if (db.reminderLog[k] == null) { db.reminderLog[k] = 1; fn(); fired = true } }
        val s = u.settings
        val rest = isRest(t, u.id)
        val vac = vacationOf(u)?.takeIf { t in it.first..it.second }
        if (vac != null) once("vac" + vac.first) { notify(u.id, "Mode vacances jusqu’au ${fmtDate(vac.second)} : tes séries sont en pause et les rappels coupés. Bonnes vacances !", "reminder", view = "today") }
        else if (rest) once("rest") { notify(u.id, "Jour de repos : ta série est protégée aujourd’hui. Profite, sans culpabiliser.", "reminder", view = "today") }
        if (!rest && s.morning.on && h >= s.morning.time && plan.due.isNotEmpty()) once("morning") {
            notify(u.id, "Rappel du jour : ${pl(plan.due.size, "validation prévue", "validations prévues")}. ${if (st.n > 0) "Ta série de ${pl(st.n, "jour", "jours")} t'attend." else "Allume ta flamme !"}", "reminder", view = "today")
        }
        if (!rest && s.evening.on && h >= s.evening.time && pending > 0) once("evening") {
            notify(u.id, "Il te reste ${pl(pending, "validation", "validations")} avant minuit${if (st.n > 0 && !st.lit) " pour garder ta série de ${pl(st.n, "jour", "jours")}" else ""}.", "reminder", view = "today")
        }
        for ((k, lbl) in listOf("week" to "de la semaine passée", "month" to "du mois dernier", "year" to "de l’année dernière")) {
            val r = Recaps.range(k)
            if (r.past && h >= "08:00") once("recapPast$k${r.s}") { notify(u.id, "Ton récap $lbl est prêt : il reste consultable aujourd’hui sur l’accueil.", "badge", view = "today") }
        }
        if (wdIdx(todayDate()) == 6 && h >= "18:00") once("recap" + ymd(weekStart(todayDate()))) { notify(u.id, "Ton récap de la semaine est prêt : retrouve-le sur l’accueil.", "badge") }
        for (x in plan.due) if (x.g.type == "period" && !x.done && h >= x.g.start && h <= addHM(x.g.start, 15)) once("pd" + x.g.id) {
            notify(u.id, "C’est l’heure : « ${x.g.title} » (${x.g.start}–${x.g.end}).", "period", x.g.id, "today")
        }
        for (g in plan.ongoing) if (g.type == "deadline") {
            val n = daysUntil(g.due)
            if (n == 0L || n == 1L) once("dl" + g.id) { notify(u.id, "Deadline « ${g.title} » : ${if (n == 0L) "c'est aujourd'hui" else "c'est demain"}.", "deadline", g.id) }
        }
        evaluateGoals(u, once)
        aprilEgg(u)
        if (fired) store.commit()
        queueBadgeCheck()
    }
    private fun aprilEgg(u: User) { if (store.acct) return; val t = today(); if (!t.endsWith("-04-01") || u.aprilFool == t) return; u.aprilFool = t; notify(u.id, "PepsGoal Officiel : test cedric", "official") }

    /* ---------------------------------------------------------------- journée parfaite */
    fun checkPerfect() {
        val u = me() ?: return; val plan = todayPlan(u.id)
        if (plan.due.isEmpty() || plan.due.any { !it.done }) return
        val k = u.id + "|" + today(); if (db.perfectLog[k] != null) return
        db.perfectLog[k] = 1
        u.perfectDays = (u.perfectDays + today()).distinct().takeLast(1000).toMutableList(); store.commit()
        hooks.celebrate(perfectCelebration()); queueBadgeCheck()
    }
    fun perfectCelebration(): Celebration.Perfect {
        val id = meId()
        val cs = db.checkins.filter { it.userId == id && it.date == today() && isWin(it) }
        val n = cs.size; val pts = cs.sumOf { it.pts }; val icons = cs.mapNotNull { goalById(it.goalId)?.icon }.distinct(); val streak = globalStreak(id).n
        return Celebration.Perfect(cap(weekdayLongDate(todayDate())), "Journée parfaite !", "Tout ce qui était prévu aujourd'hui est validé. Ta flamme n'a jamais autant brillé.", icons,
            listOf(Triple(n, if (n > 1) "validations" else "validation", ""), Triple(pts, "flammes", "+"), Triple(streak, if (streak > 1) "jours de série" else "jour de série", "")),
            "Journée parfaite sur PepsGoal : ${pl(n, "validation", "validations")} ${icons.joinToString(" ")} · +$pts flammes · série de ${pl(streak, "jour", "jours")}.", "ma journée")
    }

    /* ---------------------------------------------------------------- badges */
    var badgeCheckPending = false
    /** L'application appelle `checkBadges()` un instant après (pour regrouper plusieurs validations). */
    fun queueBadgeCheck() { badgeCheckPending = true }
    fun checkBadges() {
        badgeCheckPending = false
        val u = me() ?: return
        if (store.acct && cloud?.pulledBefore == false) return
        val st = Badges.state(this, u.id); val keys = Badges.keys(st)
        val fams = BADGE_FAMILIES.map { it.k }
        if (u.badges != null && (u.badgeFams ?: BADGE_FAMS_V20).size < fams.size) {
            val known = (u.badgeFams ?: BADGE_FAMS_V20).toSet()
            u.badges = (u.badges!! + Badges.keys(st.filter { it.f.k !in known })).distinct().toMutableList()
            u.badgeFams = fams.toMutableList(); store.commit()
        }
        if (u.badges == null) { u.badges = keys.toMutableList(); u.badgeFams = fams.toMutableList(); store.commit(); return }
        val fresh = keys.filter { it !in u.badges!! }; if (fresh.isEmpty()) return
        u.badges = (u.badges!! + fresh).distinct().toMutableList(); store.commit()
        val items = fresh.map { k -> val (fk, ti) = k.split(":"); st.first { it.f.k == fk } to ti.toInt() }
        items.forEach { (b, tier) -> notify(u.id, "Badge débloqué : ${b.f.n} ${BADGE_TIERS[tier].first} (${b.f.d(b.f.t[tier])}).", "badge", view = "profile") }
        val best = LinkedHashMap<String, Pair<BadgeState, Int>>()
        items.forEach { if ((best[it.first.f.k]?.second ?: -1) < it.second) best[it.first.f.k] = it }
        val sorted = best.values.sortedByDescending { it.second }
        sorted.forEachIndexed { i, (b, tier) -> hooks.celebrate(Celebration.Badge(b.f, tier, sorted.size - 1 - i)) }
    }

    /* ---------------------------------------------------------------- favoris et groupes d'objectifs */
    fun isFav(g: Goal) = me()?.favGoals?.contains(g.id) ?: false
    fun toggleFav(id: String) {
        val u = me() ?: return; val g = goalById(id) ?: return
        val on = !isFav(g)
        u.favGoals = u.favGoals.filter { it != id && goalById(it) != null }.toMutableList()
        if (on) u.favGoals.add(0, id)
        store.commit(); hooks.toast(if (on) "« ${g.title} » ajouté à tes favoris." else "« ${g.title} » retiré de tes favoris.", "star")
    }
    fun groupOfGoal(u: User, gid: String): GoalGroup? = u.goalGroupOf[gid]?.let { id -> u.goalGroups.firstOrNull { it.id == id } }
    fun setGoalGroup(u: User, gid: String, grp: String?) { if (grp != null) u.goalGroupOf[gid] = grp else u.goalGroupOf.remove(gid) }

    /* ---------------------------------------------------------------- objectifs : création, suppression */
    fun deleteGoal(id: String, toast: Boolean = true) {
        db.goals = db.goals.filter { it.id != id }.toMutableList()
        db.checkins = db.checkins.filter { it.goalId != id }.toMutableList()
        db.posts.forEach { if (it.goalId == id) it.goalId = null }
        store.commit(); if (toast) hooks.toast("Objectif supprimé.", "trash")
    }
    /** Annule une suppression : l'objectif, ses validations et le lien de ses publications reviennent. */
    fun restoreGoal(g: Goal, checks: List<Checkin>, postIds: List<String>) {
        if (goalById(g.id) == null) db.goals = (db.goals + g).toMutableList()
        val have = db.checkins.map { it.id }.toHashSet()
        db.checkins = (db.checkins + checks.filter { it.id !in have }).toMutableList()
        db.posts.forEach { if (it.id in postIds) it.goalId = g.id }
        store.commit(); hooks.toast("Objectif restauré.", "check"); hooks.liveChanged()
    }
    fun archive(id: String, toast: Boolean = true) { val g = goalById(id) ?: return; g.archived = !g.archived; store.commit(); if (toast) hooks.toast(if (g.archived) "Objectif archivé." else "Objectif réactivé.", "archive") }
    fun reopen(id: String) { val g = goalById(id) ?: return; g.completedAt = null; db.checkins = db.checkins.filter { !(it.goalId == id && it.kind == "done") }.toMutableList(); store.commit() }

    /* ---------------------------------------------------------------- partage */
    fun progressSnap(g: Goal): Map<String, Any?> {
        val s = statBig(g)
        val pct: Double? = when (g.type) {
            "objective" -> objProgress(g) / g.target; "monthly" -> monthProgress(g) / g.target
            "project" -> projectCounts(g).let { (d, n) -> if (n > 0) d.toDouble() / n else 0.0 }
            "challenge" -> challengeDone(g).toDouble() / g.length; "group" -> groupTotal(g) / g.target
            else -> null
        }
        return mapOf("type" to g.type, "icon" to g.icon, "title" to g.title, "v" to s.v, "l" to s.l, "label" to typeInfo(g.type).label, "meta" to metaLine(g), "pct" to pct?.let { minOf(1.0, it) })
    }
    fun shareText(g: Goal): String { val s = statBig(g); return "${s.v} ${s.l} pour « ${g.title} » sur PepsGoal." }

    /* ---------------------------------------------------------------- stories */
    fun liveStories() = db.stories.filter { nowMs() - it.at < STORY_TTL && userById(it.userId) != null }
    fun canSeeStory(s: Story, v: String) = s.userId == v || (areFriends(s.userId, v) && !isBlockedPair(s.userId, v))
    class StoryGroup(val u: User, val list: List<Story>, val unseen: Boolean, val last: Long)
    fun storyUsers(v: String = meId()): List<StoryGroup> {
        val live = liveStories().filter { canSeeStory(it, v) && (it.userId == v || !isBanned(userById(it.userId))) }
        return live.map { it.userId }.distinct().mapNotNull { id ->
            val list = live.filter { it.userId == id }.sortedBy { it.at }
            val u = userById(id) ?: return@mapNotNull null
            StoryGroup(u, list, id != v && list.any { !it.seenBy.contains(v) }, list.last().at)
        }.sortedWith(compareByDescending<StoryGroup> { it.u.id == v }.thenByDescending { it.unseen }.thenByDescending { it.last })
    }
    fun myStoryArchive(id: String = meId()) = db.stories.filter { it.userId == id }.sortedByDescending { it.at }

    /* ---------------------------------------------------------------- easter egg */
    fun eggWord(): String {
        val s = me()?.settings ?: return "Goal"
        if (validAccent(s.accentCustom)) return "Goal"
        return when (s.accent) { ACC_SUN -> "Gold"; ACC_ICE -> "Cold"; else -> "Goal" }
    }
}

fun validAccent(a: List<String>?) = a != null && a.size == 3 && a.all { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }
