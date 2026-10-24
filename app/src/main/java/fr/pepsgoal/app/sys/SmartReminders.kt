package fr.pepsgoal.app.sys

import android.content.Context
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal

/**
 * Rappels intelligents : PepsGoal apprend à quelle heure tu valides vraiment chaque objectif (30 derniers jours) et
 * te le rappelle juste avant cette heure s'il n'est pas encore fait ; et si ta flamme n'est pas allumée le soir, un
 * rappel « dernière chance » arrive avant ton heure habituelle de fin de journée.
 */
object SmartReminders {
    /** Minute de la journée où l'objectif est habituellement validé (médiane), ou null si pas assez d'historique. */
    fun usualMinute(ctx: Context, g: Goal): Int? {
        val pg = ctx.peps.pg; val since = nowMs() - 30L * 86_400_000
        val mins = pg.userCheckins(pg.meId()).filter { it.goalId == g.id && pg.isWin(it) && it.at > since }.map { localDateTime(it.at).let { t -> t.hour * 60 + t.minute } }.sorted()
        return if (mins.size < 4) null else mins[mins.size / 2]
    }

    /** Heure du rappel (ms aujourd'hui) pour chaque objectif restant : 20 minutes avant l'heure habituelle. */
    fun plan(ctx: Context): List<Pair<Goal, Long>> {
        val app = ctx.peps; val pg = app.pg; val u = pg.me() ?: return emptyList()
        if (!app.prefs.smartReminders) return emptyList()
        val day0 = startOfDayMs(todayDate())
        return pg.todayPlan(u.id).due.filter { !it.done }.mapNotNull { d -> usualMinute(ctx, d.g)?.let { m -> d.g to day0 + (maxOf(6 * 60, m - 20)) * 60_000L } }
    }

    /** « Dernière chance » : avant l'heure à laquelle tu valides habituellement en dernier (entre 20 h et 22 h 45). */
    fun lastChance(ctx: Context): Long? {
        val app = ctx.peps; val pg = app.pg; val u = pg.me() ?: return null
        if (!app.prefs.smartReminders || pg.isRest()) return null
        val st = pg.globalStreak(u.id); if (st.lit || st.n == 0) return null
        val since = nowMs() - 21L * 86_400_000
        val last = pg.userCheckins(u.id).filter { pg.isWin(it) && it.at > since }.groupBy { it.date }.values.map { l -> l.maxOf { localDateTime(it.at).let { t -> t.hour * 60 + t.minute } } }.sorted()
        val m = if (last.size >= 5) last[last.size / 2] - 30 else 21 * 60 + 30
        return startOfDayMs(todayDate()) + m.coerceIn(20 * 60, 22 * 60 + 45) * 60_000L
    }

    /** Prochain instant utile (pour l'alarme des rappels). */
    fun nextAt(ctx: Context): Long? { val now = nowMs(); return (plan(ctx).map { it.second } + listOfNotNull(lastChance(ctx))).filter { it > now + 5_000 }.minOrNull() }

    /** Envoie les rappels dont l'heure vient de passer (une seule fois par jour et par objectif). */
    fun check(ctx: Context) {
        val app = ctx.peps; if (app.foreground || !app.prefs.smartReminders || app.pg.isRest()) return
        val now = nowMs(); val t = today(); val sent = app.prefs.notifiedIds
        for ((g, at) in plan(ctx)) {
            val key = "smart|$t|${g.id}"
            if (now in at..(at + 25 * 60_000) && key !in sent) {
                app.prefs.notifiedIds = app.prefs.notifiedIds + key
                Notifier.simple(ctx, key.hashCode(), Notifier.CH_REMIND, "${g.shownIcon()} C’est ton moment habituel", "Tu valides souvent « ${g.shownTitle()} » vers cette heure-ci. On s’y met ?", goalId = g.id)
            }
        }
        lastChance(ctx)?.let { at ->
            val key = "last|$t"
            if (now in at..(at + 40 * 60_000) && key !in app.prefs.notifiedIds) {
                app.prefs.notifiedIds = app.prefs.notifiedIds + key
                val n = app.pg.globalStreak(app.pg.meId()).n
                Notifier.simple(ctx, key.hashCode(), Notifier.CH_REMIND, "🔥 Dernière chance pour ta série", "Ta série de ${pl(n, "jour", "jours")} t’attend : une seule validation avant minuit suffit.")
            }
        }
    }
}
