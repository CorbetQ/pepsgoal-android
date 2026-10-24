package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Comptes à rebours */

fun Pg.countdowns(): List<Pair<Countdown, Int>> {
    val t = todayDate()
    return (me()?.countdowns ?: emptyList()).map { it to daysBetween(t, parseYmd(it.date)).toInt() }.filter { it.second >= 0 }.sortedBy { it.second }
}
fun Pg.addCountdown(title: String, emoji: String, date: String) { val u = me() ?: return; u.countdowns.add(Countdown(uid(), title.trim().take(40), emoji, date)); store.commit() }
fun Pg.removeCountdown(id: String) { val u = me() ?: return; u.countdowns.removeAll { it.id == id }; store.commit() }
/** « Aujourd'hui », « Demain », « Dans 12 jours ». */
fun countdownLabel(days: Int) = when (days) { 0 -> "Aujourd’hui"; 1 -> "Demain"; else -> "Dans $days jours" }

/* ---------------------------------------------------------------- Citations personnelles */

fun Pg.addQuote(text: String, author: String) { val u = me() ?: return; u.myQuotes.add(MyQuote(uid(), text.trim().take(220), author.trim().take(40))); store.commit() }
fun Pg.removeQuote(id: String) { val u = me() ?: return; u.myQuotes.removeAll { it.id == id }; store.commit() }
/** Citation du jour, en piochant aussi dans les citations ajoutées par l'utilisateur. */
fun Pg.quoteToday(d: java.time.LocalDate = todayDate()): Pair<String, String> {
    val mine = me()?.myQuotes?.map { it.text to it.author.ifBlank { "Ma citation" } } ?: emptyList()
    val all = QUOTES + mine
    return all[(d.dayOfYear + d.year * 7) % all.size]
}

/* ---------------------------------------------------------------- Objectif du mois */

fun monthKey(d: java.time.LocalDate = todayDate()) = "%04d-%02d".format(d.year, d.monthValue)
fun Pg.monthFocusGoal(): Goal? = me()?.monthFocus?.get(monthKey())?.let { goalById(it) }?.takeIf { !it.archived }
fun Pg.setMonthFocus(g: Goal?) { val u = me() ?: return; if (g == null) u.monthFocus.remove(monthKey()) else u.monthFocus[monthKey()] = g.id; store.commit() }
/** Jours validés ce mois-ci pour l'objectif, et jours prévus jusqu'à la fin du mois. */
fun Pg.monthFocusProgress(g: Goal): Pair<Int, Int> {
    val t = todayDate(); val first = t.withDayOfMonth(1); val last = t.withDayOfMonth(t.lengthOfMonth())
    var done = 0; var planned = 0; var d = first
    while (d <= last) { val k = ymd(d); if (scheduled(g, d)) planned++; if (hasCheck(g.id, k)) done++; d = d.plusDays(1) }
    return done to maxOf(planned, done)
}

/* ---------------------------------------------------------------- Objectif discret */

/** Objectif discret : son titre est masqué sur l'accueil, les widgets et les notifications. */
var Goal.discreet: Boolean get() = bool("discreet"); set(v) = set("discreet", v)
fun Goal.shownTitle(): String = if (discreet) "Objectif discret" else title
fun Goal.shownIcon(): String = if (discreet) "🔒" else icon

/* ---------------------------------------------------------------- Mobilité douce */

/** Mode de déplacement doux (« velo », « marche », « tram », « bus », « covoit ») ou vide. */
var Goal.mobility: String get() = str("mobility"); set(v) = set("mobility", v)
/** Distance d'un trajet (km), pour les objectifs validés sans valeur chiffrée. */
var Goal.tripKm: Double get() = num("tripKm", 0.0); set(v) = set("tripKm", v)
val MOBILITY = linkedMapOf("velo" to ("🚲" to "Vélo"), "marche" to ("🚶" to "Marche"), "tram" to ("🚋" to "Tram ou métro"), "bus" to ("🚌" to "Bus"), "covoit" to ("🚗" to "Covoiturage"))
/** Émissions évitées par km par rapport à une voiture seule (ADEME, ordres de grandeur, kg CO₂e/km). */
private val CO2_SAVED = mapOf("velo" to .218, "marche" to .218, "tram" to .214, "bus" to .105, "covoit" to .109)

/** Kilomètres parcourus et CO₂ évité (kg) depuis le début de l'objectif. */
fun Pg.mobilityImpact(g: Goal): Pair<Double, Double> {
    if (g.mobility.isEmpty()) return 0.0 to 0.0
    val cks = db.checkins.filter { it.goalId == g.id && it.userId == g.userId && isWin(it) }
    val km = if (g.unit.lowercase().startsWith("km")) cks.sumOf { it.value } else cks.size * g.tripKm
    return km to km * (CO2_SAVED[g.mobility] ?: 0.0)
}

/* ---------------------------------------------------------------- Import (Loop Habit Tracker, CSV) */

class ImportResult(val goals: Int, val checks: Int)

/**
 * Importe un historique CSV : première colonne la date (AAAA-MM-JJ), puis une colonne par habitude
 * (export « Checkmarks.csv » de Loop Habit Tracker, ou tableau maison avec 1/x/oui pour un jour validé).
 * Chaque habitude devient un objectif quotidien ; les validations importées ne rapportent pas de flammes.
 */
fun Pg.importCsv(csv: String): ImportResult {
    val u = me() ?: return ImportResult(0, 0)
    val lines = csv.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return ImportResult(0, 0)
    val sep = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
    fun cells(l: String) = l.split(sep).map { it.trim().trim('"') }
    val head = cells(lines[0])
    val rows = lines.drop(1).map(::cells).filter { it.isNotEmpty() && Regex("\\d{4}-\\d{2}-\\d{2}").matches(it[0]) }
    if (head.size < 2 || rows.isEmpty()) return ImportResult(0, 0)
    // Loop : 2 = coché à la main (1 = déduit de la fréquence, ignoré) ; sinon 1, x, oui, true, ✓
    val loop = rows.any { r -> r.drop(1).any { it == "2" } }
    fun yes(v: String) = if (loop) v == "2" else v.lowercase() in setOf("1", "x", "oui", "yes", "true", "✓", "✔", "2")
    var goals = 0; var checks = 0
    for (col in 1 until head.size) {
        val name = head[col].ifBlank { "Habitude $col" }.take(60)
        val days = rows.filter { it.getOrNull(col)?.let(::yes) == true }.map { it[0] }.filter { it <= today() }.distinct().sorted()
        if (days.isEmpty()) continue
        val g = myGoals(u.id).firstOrNull { it.userId == u.id && it.title.equals(name, true) && it.type == "routine" }
            ?: Goal(uid(), u.id, "routine", name, createdAt = msOf("${days.first()}T00:00:00Z") ?: nowMs()).also { it.icon = "✅"; it.mode = "days"; it.days = (0..6).toList(); db.goals.add(it); goals++ }
        for (d in days) if (!hasCheck(g.id, d)) { addCheck(g, "done", 1.0, 0, d); checks++ }
    }
    store.commit()
    return ImportResult(goals, checks)
}

/* ---------------------------------------------------------------- Bilan du mois */

/** Le 1er du mois (jusqu'au 3), une notification résume le mois écoulé (une seule fois, tous appareils). */
fun Pg.checkMonthRecap() {
    val u = me() ?: return; val t = todayDate(); if (t.dayOfMonth > 3) return
    val prev = t.minusMonths(1); val mark = "mrecap:${monthKey(prev)}"
    if (mark in u.shopClaimed) return
    val s = ymd(prev.withDayOfMonth(1)); val e = ymd(prev.withDayOfMonth(prev.lengthOfMonth()))
    val n = userCheckins(u.id).count { isWin(it) && it.date in s..e }
    u.shopClaimed.add(mark); store.commit()
    if (n == 0) return
    val m = Recaps.MONTHS_LONG[prev.monthValue - 1]
    notify(u.id, "🗓️ Le bilan de ton mois est prêt ($m) : ${pl(n, "validation", "validations")}. Découvre ton récap !", "recap")
}

/* ---------------------------------------------------------------- Porte-monnaie détaillé */

class WalletBreakdown(val earned: Int, val bonus: Int, val received: Int, val spent: Int, val given: Int, val wallet: Int)

/** D'où viennent les flammes du porte-monnaie et où elles sont parties. */
fun Pg.walletBreakdown(): WalletBreakdown {
    val u = me() ?: return WalletBreakdown(0, 0, 0, 0, 0, 0)
    val given = giftsOut()
    val received = u.shopClaimed.filter { it.startsWith("giftin:") }.sumOf { it.substringAfterLast(':').toIntOrNull() ?: 0 }
    return WalletBreakdown(points(u.id), u.shopBonus - received, received, u.shopSpent - given, given, wallet(u))
}
