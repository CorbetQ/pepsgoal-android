package fr.pepsgoal.app.sys

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect (Santé d'Android) : lecture des données de santé du téléphone et de la montre (pas, distances,
 * sport, sommeil et horaires de coucher/réveil, poids, calories, étages, hydratation). Un objectif peut être relié à
 * une mesure et se valide tout seul quand le seuil du jour est atteint ; les valeurs alimentent aussi les
 * statistiques et le coach IA.
 */
object Health {
    /**
     * Une mesure : [op] = sens par défaut du seuil (">=" atteindre au moins, "<=" ne pas dépasser) ; [kind] = "time"
     * pour une heure (valeur en heures décimales, au-delà de 24 h pour l'après-minuit).
     */
    class Metric(val key: String, val label: String, val unit: String, val emoji: String, val op: String, val def: String, val kind: String = "num")

    val METRICS = listOf(
        Metric("steps", "Pas", "pas", "👟", ">=", "8000"),
        Metric("distance", "Distance parcourue", "km", "🗺️", ">=", "5"),
        Metric("run", "Distance courue", "km", "🏃", ">=", "5"),
        Metric("bike", "Distance à vélo", "km", "🚴", ">=", "10"),
        Metric("exercise", "Sport", "min", "💪", ">=", "30"),
        Metric("calories", "Calories actives", "kcal", "🔥", ">=", "400"),
        Metric("floors", "Étages montés", "étages", "🪜", ">=", "10"),
        Metric("sleep", "Sommeil", "h", "😴", ">=", "7"),
        Metric("bedtime", "Heure de coucher", "", "🛏️", "<=", "23:00", "time"),
        Metric("wakeup", "Heure de réveil", "", "⏰", "<=", "07:30", "time"),
        Metric("weight", "Poids", "kg", "⚖️", "<=", "70"),
        Metric("water", "Eau", "L", "💧", ">=", "1.5"),
    )
    fun metric(k: String) = METRICS.firstOrNull { it.key == k }

    val PERMS = setOf(StepsRecord::class, DistanceRecord::class, ExerciseSessionRecord::class, ActiveCaloriesBurnedRecord::class, FloorsClimbedRecord::class,
        SleepSessionRecord::class, WeightRecord::class, HydrationRecord::class).map { HealthPermission.getReadPermission(it) }.toSet()

    class Link(val goalId: String, val metric: String, val threshold: Double, val op: String)

    fun available(ctx: Context) = try { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE } catch (e: Exception) { false }
    fun needsInstall(ctx: Context) = try { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED } catch (e: Exception) { false }
    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Set<String> = if (!available(ctx)) emptySet() else try { client(ctx).permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }

    fun links(ctx: Context): List<Link> = ctx.peps.prefs.healthLinks.lines().mapNotNull { l ->
        val p = l.split('|'); if (p.size < 3) return@mapNotNull null
        Link(p[0], p[1], p[2].toDoubleOrNull() ?: return@mapNotNull null, p.getOrNull(3) ?: metric(p[1])?.op ?: ">=")
    }
    fun saveLinks(ctx: Context, l: List<Link>) { ctx.peps.prefs.healthLinks = l.joinToString("\n") { "${it.goalId}|${it.metric}|${it.threshold}|${it.op}" } }

    /* ------------------------------------------------------------------ format */

    /** Heure « 23:30 » ou « 0:45 » → heures décimales (après minuit au-delà de 24 pour le coucher). */
    fun parseTime(s: String, key: String): Double? {
        val p = s.trim().replace('h', ':').split(':')
        val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: return null; val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        val v = h + m / 60.0
        return if (key == "bedtime" && v < 12) v + 24 else v
    }

    fun format(key: String, v: Double?): String {
        if (v == null) return "—"
        val m = metric(key) ?: return "%.1f".format(v)
        return when {
            m.kind == "time" -> { val t = ((v % 24) * 60).toInt(); "%d:%02d".format(t / 60, t % 60) }
            key == "steps" || key == "calories" || key == "floors" || key == "exercise" -> fmtNum(Math.round(v).toInt()) + " " + m.unit
            else -> "%.1f".format(v).replace('.', ',') + " " + m.unit
        }
    }

    /* ------------------------------------------------------------------ lecture */

    private suspend fun <T : Any> sum(ctx: Context, from: Instant, to: Instant, metric: androidx.health.connect.client.aggregate.AggregateMetric<T>): T? =
        client(ctx).aggregate(AggregateRequest(setOf(metric), TimeRangeFilter.between(from, to)))[metric]

    /** Distance parcourue pendant les séances de certains types (course, vélo…). */
    private suspend fun sessionDistance(ctx: Context, from: Instant, to: Instant, types: Set<Int>): Double {
        val sessions = client(ctx).readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))).records.filter { it.exerciseType in types }
        var km = 0.0
        for (s in sessions) km += (sum(ctx, s.startTime, s.endTime, DistanceRecord.DISTANCE_TOTAL) as? androidx.health.connect.client.units.Length)?.inKilometers ?: 0.0
        return km
    }

    /** Nuit principale se terminant le matin du jour [d] (entre la veille 18 h et le jour même 14 h). */
    private suspend fun night(ctx: Context, d: LocalDate): SleepSessionRecord? {
        val z = ZoneId.systemDefault()
        return client(ctx).readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(d.minusDays(1).atTime(18, 0).atZone(z).toInstant(), d.atTime(14, 0).atZone(z).toInstant())))
            .records.maxByOrNull { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private fun hours(i: Instant, bed: Boolean): Double {
        val t = i.atZone(ZoneId.systemDefault()).toLocalTime(); val v = t.hour + t.minute / 60.0
        return if (bed && v < 12) v + 24 else v
    }

    /** Valeur d'une mesure pour le jour [d] (aujourd'hui par défaut ; null si indisponible). */
    suspend fun value(ctx: Context, key: String, d: LocalDate = LocalDate.now()): Double? {
        if (!available(ctx)) return null
        val z = ZoneId.systemDefault()
        val from = d.atStartOfDay(z).toInstant(); val to = minOf(d.plusDays(1).atStartOfDay(z).toInstant(), Instant.now())
        if (to <= from) return null
        return try {
            when (key) {
                "steps" -> (sum(ctx, from, to, StepsRecord.COUNT_TOTAL) as? Long)?.toDouble()
                "distance" -> (sum(ctx, from, to, DistanceRecord.DISTANCE_TOTAL) as? androidx.health.connect.client.units.Length)?.inKilometers
                "run" -> sessionDistance(ctx, from, to, setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL))
                "bike" -> sessionDistance(ctx, from, to, setOf(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY))
                "exercise" -> (sum(ctx, from, to, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL) as? java.time.Duration)?.toMinutes()?.toDouble()
                "calories" -> (sum(ctx, from, to, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) as? androidx.health.connect.client.units.Energy)?.inKilocalories
                "floors" -> sum(ctx, from, to, FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL) as? Double
                "water" -> (sum(ctx, from, to, HydrationRecord.VOLUME_TOTAL) as? androidx.health.connect.client.units.Volume)?.inLiters
                "sleep" -> night(ctx, d)?.let { java.time.Duration.between(it.startTime, it.endTime).toMinutes() / 60.0 }
                "bedtime" -> night(ctx, d)?.let { hours(it.startTime, true) }
                "wakeup" -> night(ctx, d)?.let { hours(it.endTime, false) }
                "weight" -> client(ctx).readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(d.minusDays(30).atStartOfDay(z).toInstant(), to)))
                    .records.maxByOrNull { it.time }?.weight?.inKilograms
                else -> null
            }?.takeIf { key !in setOf("steps", "distance", "run", "bike", "exercise", "calories", "floors", "water") || it > 0 || d == LocalDate.now() }
        } catch (e: Exception) { null }
    }

    /** Valeurs des [days] derniers jours (le plus ancien en premier). */
    suspend fun series(ctx: Context, key: String, days: Int = 7): List<Double?> = (days - 1 downTo 0).map { value(ctx, key, LocalDate.now().minusDays(it.toLong())) }

    /** Résumé santé du jour pour le coach IA (vide si Health Connect n'est pas connecté). */
    suspend fun summary(ctx: Context): String {
        if (!available(ctx) || !granted(ctx).containsAll(PERMS)) return ""
        val parts = listOf("steps", "distance", "run", "exercise", "sleep", "bedtime", "wakeup", "weight").mapNotNull { k -> value(ctx, k)?.let { "${metric(k)!!.label.lowercase()} ${format(k, it)}" } }
        return if (parts.isEmpty()) "" else "Santé aujourd’hui : " + parts.joinToString(", ") + "."
    }

    /* ------------------------------------------------------------------ validation automatique */

    fun reached(l: Link, v: Double) = if (l.op == "<=") v <= l.threshold else v >= l.threshold

    /** Vérifie les liens (au plus toutes les 10 minutes) et valide les objectifs dont le seuil est atteint. */
    suspend fun sync(ctx: Context, force: Boolean = false): Int {
        val app = ctx.peps; val pg = app.pg; val u = pg.me() ?: return 0
        val links = links(ctx); if (links.isEmpty() || !available(ctx)) return 0
        val stamp = app.prefs.healthLast.toLongOrNull() ?: 0L
        if (!force && nowMs() - stamp < 10 * 60_000) return 0
        app.prefs.healthLast = nowMs().toString()
        val due = pg.todayPlan(u.id).due.associate { it.g.id to it.done }
        var n = 0
        for (l in links) {
            // Cap quotidien : la mesure du jour devient la quantité du jour (sans jamais faire baisser une saisie à la main)
            val cg = pg.goalById(l.goalId)
            if (cg?.type == "cap") {
                val v = value(ctx, l.metric) ?: continue
                val was = due[cg.id] == true
                if (v > pg.capDay(cg) + 1e-6) pg.capSet(cg, v)
                if (!was && pg.hasCheck(cg.id, today())) {
                    n++
                    if (!app.foreground) Notifier.simple(ctx, "health|${cg.id}".hashCode(), Notifier.CH_GOALS, "✓ ${cg.icon} ${cg.title}", "Cap atteint grâce à Santé : ${format(l.metric, v)}.", tab = "today")
                }
                continue
            }
            if (due[l.goalId] != false) continue // pas prévu aujourd'hui, ou déjà validé
            val v = value(ctx, l.metric) ?: continue
            // Une valeur nulle ne valide jamais un seuil « au plus » (absence de données)
            if (l.op == "<=" && v <= 0) continue
            if (!reached(l, v)) continue
            val g = pg.goalById(l.goalId) ?: continue
            if (pg.validate(g.id) is Pg.VResult.Done) {
                n++
                val m = metric(l.metric)
                val txt = "Validé automatiquement grâce à Santé : ${m?.label?.lowercase() ?: l.metric} ${format(l.metric, v)}."
                if (!app.foreground) Notifier.simple(ctx, "health|${g.id}".hashCode(), Notifier.CH_GOALS, "✓ ${g.icon} ${g.title}", txt, tab = "today")
                else app.ui.toast("« ${g.title} » validé grâce à Santé (${format(l.metric, v)}).", "check")
            }
        }
        if (n > 0) { LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx) }
        return n
    }
}
