package fr.pepsgoal.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/** Horloge de l'application (remplaçable dans les tests). Toutes les dates « jour » sont dans le fuseau de l'appareil. */
object Clock {
    var now: () -> Long = { System.currentTimeMillis() }
    var zone: () -> ZoneId = { ZoneId.systemDefault() }
}

const val DAY_MS = 86_400_000L
val FR: Locale = Locale.FRENCH

fun nowMs() = Clock.now()
fun nowDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs()), Clock.zone())
fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(Clock.zone()).toLocalDate()
fun localDateTime(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), Clock.zone())
fun todayDate(): LocalDate = localDate(nowMs())

/** AAAA-MM-JJ */
fun ymd(d: LocalDate): String = d.toString()
fun ymd(ms: Long): String = localDate(ms).toString()
fun today(): String = ymd(todayDate())
fun yday(): String = ymd(todayDate().minusDays(1))
fun parseYmd(s: String): LocalDate = try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.of(1970, 1, 1) }
fun addDays(d: LocalDate, n: Long): LocalDate = d.plusDays(n)
fun startOfDayMs(d: LocalDate): Long = d.atStartOfDay(Clock.zone()).toInstant().toEpochMilli()

/** 0 = lundi … 6 = dimanche */
fun wdIdx(d: LocalDate): Int = d.dayOfWeek.value - 1
fun weekStart(d: LocalDate): LocalDate = d.minusDays(wdIdx(d).toLong())
fun daysUntil(s: String): Long = ChronoUnit.DAYS.between(todayDate(), parseYmd(s))
fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

val WD = listOf("L", "M", "M", "J", "V", "S", "D")
val WD_SHORT = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
val WD_LONG = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

fun pad(n: Int) = n.toString().padStart(2, '0')
fun pl(n: Number, one: String, many: String): String {
    val v = n.toDouble()
    return "${fmtNum(v)} ${if (v > 1) many else one}"
}
fun cap(s: String) = if (s.isEmpty()) s else s.substring(0, 1).uppercase(FR) + s.substring(1)
fun first(n: String?) = (n ?: "").trim().split(Regex("\\s+")).firstOrNull() ?: ""

/** Nombre entier tel quel, sinon une décimale avec une virgule (comme la version web). */
fun fmtNum(n: Double): String {
    if (n.isNaN()) return "0"
    if (n == Math.floor(n) && !n.isInfinite() && Math.abs(n) < 1e15) return n.toLong().toString()
    return String.format(Locale.US, "%.1f", n).replace('.', ',')
}
fun fmtNum(n: Int) = n.toString()
fun round1(n: Double) = Math.round(n * 10) / 10.0

/** « 12 octobre » (année ajoutée si différente). */
fun fmtDate(s: String): String {
    val d = parseYmd(s)
    val base = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, FR)}"
    return if (d.year != todayDate().year) "$base ${d.year}" else base
}
fun fmtDateShort(s: String): String { val d = parseYmd(s); return "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, FR).removeSuffix(".")}." }
fun weekdayLongDate(d: LocalDate): String = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, FR)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, FR)}"
fun monthName(key: String): String {
    val (y, m) = key.split("-").map { it.toInt() }
    val name = java.time.Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, FR).lowercase(FR)
    return if (y != todayDate().year) "$name $y" else name
}
fun hm(ms: Long): String { val t = localDateTime(ms); return "${pad(t.hour)}:${pad(t.minute)}" }
fun nowHM(): String { val t = nowDateTime(); return "${pad(t.hour)}:${pad(t.minute)}" }
fun hm2(ms: Long): String { val t = localDateTime(ms); return "${t.hour} h ${pad(t.minute)}" }
fun fmtDT(ms: Long): String {
    val t = localDateTime(ms)
    return "${t.dayOfWeek.getDisplayName(TextStyle.SHORT, FR)} ${t.dayOfMonth} ${t.month.getDisplayName(TextStyle.FULL, FR)} à ${pad(t.hour)}:${pad(t.minute)}"
}

fun addHM(hm: String, n: Int): String {
    val (h, m) = hm.split(":").map { it.toIntOrNull() ?: 0 }
    val t = (h * 60 + m + n).coerceIn(0, 23 * 60 + 59)
    return "${pad(t / 60)}:${pad(t % 60)}"
}
fun hmMinutes(hm: String): Int { val p = hm.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }

/** « il y a 5 min », « il y a 3 h », « 12 oct. » */
fun ago(ts: Long): String {
    val s = (nowMs() - ts) / 1000.0
    if (s < 60) return "à l'instant"
    if (s < 3600) return "il y a ${(s / 60).toInt()} min"
    if (s < 86400) return "il y a ${(s / 3600).toInt()} h"
    val d = (s / 86400).toInt()
    if (d < 7) return "il y a $d j"
    val t = localDate(ts)
    return "${t.dayOfMonth} ${t.month.getDisplayName(TextStyle.SHORT, FR)}"
}

/** Temps restant avant une heure du jour : « 2 h 05 » ou « 12 min ». */
fun untilTxt(hm: String): String {
    val target = todayDate().atStartOfDay().plusMinutes(hmMinutes(hm).toLong())
    val mins = maxOf(0L, ChronoUnit.MINUTES.between(nowDateTime(), target) + 0)
    return if (mins >= 60) "${mins / 60} h ${pad((mins % 60).toInt())}" else "$mins min"
}

fun fmtClock(secIn: Double): String {
    val sec = maxOf(0L, secIn.roundToLong())
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "$h:${pad(m.toInt())}:${pad(s.toInt())}" else "$m:${pad(s.toInt())}"
}
fun fmtDur(s: Double): String = "${(s / 60).toInt()}:${pad(Math.round(s % 60).toInt().coerceAtMost(59))}"

fun fmtLeft(ms: Long): String = when {
    ms >= DAY_MS -> "dans ${pl(Math.round(ms.toDouble() / DAY_MS), "jour", "jours")}"
    ms >= 3_600_000 -> "dans ${Math.round(ms / 3_600_000.0)} h"
    else -> "dans ${maxOf(1, Math.round(ms / 60_000.0))} min"
}

fun isoOf(ms: Long?): String? = ms?.let { Instant.ofEpochMilli(it).toString().let { s -> if (s.length == 20) s.replace("Z", ".000Z") else s } }
fun msOf(iso: String?): Long? = try { iso?.let { Instant.parse(it).toEpochMilli() } } catch (e: Exception) { null }
fun nowIso(): String = isoOf(nowMs())!!
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
fun dayOfWeekFr(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, FR)
