package fr.pepsgoal.app.sys

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect (Santé d'Android) : un objectif peut être relié à une mesure (pas, sommeil, sport, eau) et se valide
 * tout seul quand le seuil du jour est atteint, à partir des données de la montre ou du téléphone.
 */
object Health {
    /** Mesures proposées : (clé, libellé, unité, emoji). */
    val METRICS = listOf(Triple("steps", "Pas", "pas"), Triple("sleep", "Sommeil", "h"), Triple("exercise", "Sport", "min"), Triple("water", "Eau", "L"))
    val PERMS = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class), HealthPermission.getReadPermission(HydrationRecord::class))

    class Link(val goalId: String, val metric: String, val threshold: Double)

    fun available(ctx: Context) = try { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE } catch (e: Exception) { false }
    fun needsInstall(ctx: Context) = try { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED } catch (e: Exception) { false }
    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Set<String> = if (!available(ctx)) emptySet() else try { client(ctx).permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }

    fun links(ctx: Context): List<Link> = ctx.peps.prefs.healthLinks.lines().mapNotNull { l -> l.split('|').takeIf { it.size == 3 }?.let { Link(it[0], it[1], it[2].toDoubleOrNull() ?: return@let null) } }
    fun saveLinks(ctx: Context, l: List<Link>) { ctx.peps.prefs.healthLinks = l.joinToString("\n") { "${it.goalId}|${it.metric}|${it.threshold}" } }

    /** Valeur du jour d'une mesure (le sommeil est celui de la nuit précédente). */
    suspend fun today(ctx: Context, metric: String): Double? {
        if (!available(ctx)) return null
        val z = ZoneId.systemDefault(); val d = LocalDate.now()
        val start = d.atStartOfDay(z).toInstant(); val now = Instant.now()
        return try {
            when (metric) {
                "steps" -> client(ctx).aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, now)))[StepsRecord.COUNT_TOTAL]?.toDouble()
                "sleep" -> client(ctx).aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(d.minusDays(1).atTime(18, 0).atZone(z).toInstant(), d.atTime(14, 0).atZone(z).toInstant())))[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.div(60.0)
                "exercise" -> client(ctx).aggregate(AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), TimeRangeFilter.between(start, now)))[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toDouble()
                "water" -> client(ctx).aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), TimeRangeFilter.between(start, now)))[HydrationRecord.VOLUME_TOTAL]?.inLiters
                else -> null
            }
        } catch (e: Exception) { null }
    }

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
            if (due[l.goalId] != false) continue // pas prévu aujourd'hui, ou déjà validé
            val v = today(ctx, l.metric) ?: continue
            if (v < l.threshold) continue
            val g = pg.goalById(l.goalId) ?: continue
            if (pg.validate(g.id) is Pg.VResult.Done) {
                n++
                val m = METRICS.first { it.first == l.metric }
                val shown = if (l.metric == "steps" || l.metric == "exercise") fmtNum(v.toInt()) else "%.1f".format(v)
                if (!app.foreground) Notifier.simple(ctx, "health|${g.id}".hashCode(), Notifier.CH_GOALS, "✓ ${g.icon} ${g.title}", "Validé automatiquement grâce à Santé : $shown ${m.third} aujourd’hui.", tab = "today")
                else app.ui.toast("« ${g.title} » validé grâce à Santé ($shown ${m.third}).", "check")
            }
        }
        if (n > 0) { LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx) }
        return n
    }
}
