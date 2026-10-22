package fr.pepsgoal.app.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.remindAt
import fr.pepsgoal.core.effectiveRemind
import fr.pepsgoal.core.model.minutes
import fr.pepsgoal.core.model.start
import fr.pepsgoal.core.model.end

/**
 * Rappels application fermée : une alarme (à l'heure exacte si Android l'autorise) au prochain moment utile —
 * rappel du matin ou du soir, début d'une période, fin de journée, fin du minuteur, minuit.
 */
object Reminders {
    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun openExactSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Prochain instant où quelque chose doit se passer (ms), ou null. */
    fun next(ctx: Context): Long? {
        val pg = ctx.peps.pg; val u = pg.me() ?: return null; val s = u.settings
        val now = nowMs(); val day0 = startOfDayMs(todayDate()); val day1 = startOfDayMs(todayDate().plusDays(1))
        val c = mutableListOf<Long>()
        fun at(hm: String, base: Long = day0) = base + hmMinutes(hm) * 60_000L
        for (base in listOf(day0, day1)) {
            if (s.morning.on) c += at(s.morning.time, base)
            if (s.evening.on) c += at(s.evening.time, base)
            c += at(if (s.evening.on) minOf(s.evening.time, "21:00") else "20:00", base)
            c += at("08:00", base); c += at("18:00", base)
        }
        // Débuts et fins des périodes (aujourd'hui et demain)
        for (g in pg.myGoals(u.id).filter { it.type == "period" && !pg.isClosedGoal(it) }) for (d in listOf(todayDate(), todayDate().plusDays(1)))
            if (pg.periodOn(g, d)) { c += at(g.start, startOfDayMs(d)); c += at(g.end, startOfDayMs(d)) }
        // Rappels propres à chaque objectif (aujourd'hui et demain)
        for (g in pg.myGoals(u.id).filter { !pg.isClosedGoal(it) }) { val hm = pg.effectiveRemind(g) ?: continue; c += at(hm, day0); c += at(hm, day1) }
        // Fin du minuteur : la journée se valide toute seule
        pg.runningTimer()?.let { t -> pg.goalById(t.goalId)?.let { g -> val left = (g.minutes - pg.durationLiveMins(g)) * 60_000; if (left > 0) c += now + left.toLong() } }
        c += day1 + 60_000
        // Rappels intelligents (heure habituelle de chaque objectif, « dernière chance » du soir)
        try { SmartReminders.nextAt(ctx)?.let { c += it } } catch (e: Exception) {}
        return c.filter { it > now + 5_000 }.minOrNull()
    }

    fun reschedule(ctx: Context) {
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val a = am(ctx); a.cancel(pi)
        val t = try { next(ctx) } catch (e: Exception) { null } ?: return
        if (canExact(ctx)) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }
}
