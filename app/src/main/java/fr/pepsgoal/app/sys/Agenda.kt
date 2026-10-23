package fr.pepsgoal.app.sys

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import java.time.LocalDate
import java.time.ZoneId

/**
 * Créneaux dans l'agenda du téléphone : un événement récurrent qui suit le rythme de l'objectif (jours prévus, période…).
 * Les identifiants d'événements sont propres à l'appareil : ils restent dans ses préférences, pas sur le serveur.
 */
object Agenda {
    class Cal(val id: Long, val name: String, val account: String, val primary: Boolean)
    class Slot(val goalId: String, val eventId: Long, val calId: Long, val time: String, val minutes: Int)

    val PERMS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    fun granted(ctx: Context) = PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** Types dont le rythme se traduit en récurrence d'agenda. */
    fun supported(g: Goal) = when (g.type) {
        "routine" -> g.mode == "days" && g.days.isNotEmpty()
        "abstinence", "limit", "duration", "cap", "timed", "period", "challenge" -> true
        else -> false
    }

    /** Heure et durée proposées : celles de l'objectif quand il en a. */
    fun defaults(g: Goal): Pair<String, Int> = when (g.type) {
        "timed" -> g.time to 30
        "period" -> g.start to maxOf(15, hmMinutes(g.end) - hmMinutes(g.start))
        "duration" -> "18:00" to g.minutes.toInt().coerceIn(5, 600)
        else -> "08:00" to 30
    }

    fun calendars(ctx: Context): List<Cal> {
        if (!granted(ctx)) return emptyList()
        val out = mutableListOf<Cal>()
        try {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE),
                null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(4) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || c.getInt(5) == 0) continue
                    out += Cal(c.getLong(0), c.getString(1) ?: "Agenda", c.getString(2) ?: "", c.getInt(3) == 1)
                }
            }
        } catch (e: Exception) {}
        return out.sortedByDescending { it.primary }
    }

    private fun key(id: String) = "agenda:$id"
    fun slot(ctx: Context, goalId: String): Slot? = ctx.peps.prefs.raw.getString(key(goalId), null)?.split('|')?.takeIf { it.size == 4 }?.let {
        Slot(goalId, it[0].toLongOrNull() ?: return null, it[1].toLongOrNull() ?: return null, it[2], it[3].toIntOrNull() ?: 30)
    }

    private val BYDAY = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private fun until(d: String) = d.replace("-", "") + "T235959Z"

    /** Règle de récurrence (RFC 5545) et premier jour du créneau. */
    fun rule(g: Goal): Pair<String, LocalDate>? {
        val t = todayDate()
        fun firstOn(ok: (LocalDate) -> Boolean): LocalDate { var d = t; repeat(40) { if (ok(d)) return d; d = d.plusDays(1) }; return t }
        return when (g.type) {
            "routine", "timed" -> if (g.days.isEmpty()) null else "FREQ=WEEKLY;BYDAY=${g.days.sorted().joinToString(",") { BYDAY[it] }}" to firstOn { wdIdx(it) in g.days }
            "period" -> if (g.freq == "month") "FREQ=MONTHLY;BYMONTHDAY=${g.mdays.sorted().joinToString(",")}" to firstOn { it.dayOfMonth in g.mdays }
                else "FREQ=WEEKLY;BYDAY=${g.days.sorted().joinToString(",") { BYDAY[it] }}" to firstOn { wdIdx(it) in g.days }
            "challenge" -> { val s0 = parseYmd(g.start); val s = if (s0 > t) s0 else t; val end = parseYmd(g.start).plusDays(g.length - 1L); if (end < t) null else "FREQ=DAILY;UNTIL=${until(ymd(end))}" to s }
            "cap", "limit", "duration", "abstinence" -> "FREQ=DAILY" to t
            else -> null
        }
    }

    /** Crée (ou remplace) le créneau. Renvoie un message d'erreur, ou null. */
    fun set(ctx: Context, g: Goal, calId: Long, time: String, minutes: Int): String? {
        if (!granted(ctx)) return "Autorise l’accès à l’agenda pour bloquer un créneau."
        val (rr, first) = rule(g) ?: return "Le rythme de cet objectif ne se traduit pas en créneau d’agenda."
        remove(ctx, g.id)
        val z = ZoneId.systemDefault()
        val start = first.atStartOfDay(z).toInstant().toEpochMilli() + hmMinutes(time) * 60_000L
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, "${g.icon} ${g.title}")
            put(CalendarContract.Events.DESCRIPTION, "Créneau réservé pour ton objectif PepsGoal. Valide-le dans l’application.")
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DURATION, "PT${minutes.coerceIn(5, 720)}M")
            put(CalendarContract.Events.RRULE, rr)
            put(CalendarContract.Events.EVENT_TIMEZONE, z.id)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        return try {
            val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return "L’agenda a refusé le créneau."
            ctx.peps.prefs.raw.edit().putString(key(g.id), "${ContentUris.parseId(uri)}|$calId|$time|$minutes").apply()
            null
        } catch (e: Exception) { e.message ?: "L’agenda a refusé le créneau." }
    }

    fun remove(ctx: Context, goalId: String) {
        val s = slot(ctx, goalId) ?: return
        if (granted(ctx)) try { ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, s.eventId), null, null) } catch (e: Exception) {}
        ctx.peps.prefs.raw.edit().remove(key(goalId)).apply()
    }

    /**
     * Créneaux du jour cochés : quand l'objectif est validé, l'occurrence du jour devient « ✅ … » dans l'agenda
     * (exception de l'événement récurrent, une seule fois par jour).
     */
    fun syncDone(ctx: Context) {
        if (!granted(ctx)) return
        val pg = ctx.peps.pg; val t = today(); val raw = ctx.peps.prefs.raw
        for (k in raw.all.keys.filter { it.startsWith("agenda:") }) {
            val id = k.removePrefix("agenda:"); val s = slot(ctx, id) ?: continue; val g = pg.goalById(id) ?: continue
            val dk = "agendaDone:$id"
            if (raw.getString(dk, "") == t || !pg.hasCheck(g.id, t)) continue
            val z = ZoneId.systemDefault()
            val inst = todayDate().atStartOfDay(z).toInstant().toEpochMilli() + hmMinutes(s.time) * 60_000L
            val v = ContentValues().apply {
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, inst)
                put(CalendarContract.Events.TITLE, "✅ ${g.icon} ${g.title}")
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            }
            try { ctx.contentResolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, s.eventId), v); raw.edit().putString(dk, t).apply() } catch (e: Exception) {}
        }
    }

    /** Objectif supprimé, archivé ou terminé : son créneau disparaît de l'agenda. */
    fun prune(ctx: Context) {
        val pg = ctx.peps.pg
        val ids = ctx.peps.prefs.raw.all.keys.filter { it.startsWith("agenda:") }.map { it.removePrefix("agenda:") }
        for (id in ids) { val g = pg.goalById(id); if (g == null || pg.isClosedGoal(g)) remove(ctx, id) }
    }
}
