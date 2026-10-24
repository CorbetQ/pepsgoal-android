package fr.pepsgoal.app.sys

import fr.pepsgoal.core.shownTitle
import fr.pepsgoal.core.shownIcon
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.autoPlace
import fr.pepsgoal.core.autoMin
import fr.pepsgoal.core.steps
import fr.pepsgoal.core.stepDate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Rappels par lieu : une notification en arrivant (ou en partant) d'un endroit, si l'objectif n'est pas encore validé.
 * Les zones sont surveillées par Android (géorepérage) ; les positions restent sur le téléphone.
 */
object GeoReminders {
    class Place(val goalId: String, val lat: Double, val lon: Double, val radius: Int, val exit: Boolean, val label: String)

    fun all(ctx: Context): List<Place> = ctx.peps.prefs.geoPlaces.lines().mapNotNull { l ->
        val p = l.split('|'); if (p.size < 6) null else Place(p[0], p[1].toDoubleOrNull() ?: return@mapNotNull null, p[2].toDoubleOrNull() ?: return@mapNotNull null, p[3].toIntOrNull() ?: 200, p[4] == "1", p.drop(5).joinToString("|"))
    }
    fun of(ctx: Context, goalId: String) = all(ctx).firstOrNull { it.goalId == goalId }
    private fun save(ctx: Context, l: List<Place>) { ctx.peps.prefs.geoPlaces = l.joinToString("\n") { "${it.goalId}|${it.lat}|${it.lon}|${it.radius}|${if (it.exit) "1" else "0"}|${it.label}" } }

    fun hasFine(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackground(ctx: Context) = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Position actuelle (précise), ou null si indisponible. */
    @SuppressLint("MissingPermission")
    suspend fun here(ctx: Context): android.location.Location? {
        if (!hasFine(ctx)) return null
        return suspendCancellableCoroutine { c ->
            LocationServices.getFusedLocationProviderClient(ctx).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) }
        }
    }

    fun set(ctx: Context, p: Place) { save(ctx, all(ctx).filter { it.goalId != p.goalId } + p); register(ctx) }
    fun remove(ctx: Context, goalId: String) {
        save(ctx, all(ctx).filter { it.goalId != goalId })
        try { LocationServices.getGeofencingClient(ctx).removeGeofences(listOf("geo:$goalId")) } catch (e: Exception) {}
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(ctx, 7711, Intent(ctx, GeoReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))

    /** (Ré)inscrit toutes les zones auprès d'Android : au démarrage, après un redémarrage et à chaque modification. */
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        val pg = ctx.peps.pg
        val list = all(ctx).filter { pg.goalById(it.goalId) != null }
        // Validation automatique : rester un moment à un emplacement enregistré (géorepérage « séjour »)
        val places = pg.me()?.places.orEmpty()
        val autos = pg.myGoals().filter { it.userId == pg.meId() && !pg.isClosedGoal(it) && it.autoPlace != null }.mapNotNull { g -> places.firstOrNull { it.id == g.autoPlace }?.let { g to it } }
        try { LocationServices.getGeofencingClient(ctx).removeGeofences(pending(ctx)) } catch (e: Exception) {}
        if ((list.isEmpty() && autos.isEmpty()) || !hasFine(ctx) || !hasBackground(ctx)) return
        val fences = list.take(60).map {
            Geofence.Builder().setRequestId("geo:${it.goalId}").setCircularRegion(it.lat, it.lon, it.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(if (it.exit) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(60_000).build()
        } + autos.take(30).map { (g, pl) ->
            Geofence.Builder().setRequestId("auto:${g.id}").setCircularRegion(pl.lat, pl.lon, pl.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(g.autoMin * 60_000).build()
        }
        val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        try { LocationServices.getGeofencingClient(ctx).addGeofences(req, pending(ctx)) } catch (e: Exception) {}
    }
}

/** Entrée ou sortie d'une zone : rappel de l'objectif s'il reste à faire aujourd'hui. */
class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val app = ctx.peps; val pg = app.pg; if (pg.me() == null || pg.isRest()) return
        val due = pg.todayPlan().due.associate { it.g.id to it.done }
        for (f in ev.triggeringGeofences ?: emptyList()) {
            // Séjour à un emplacement : validation automatique (une fois par jour), annulable depuis la notification
            if (f.requestId.startsWith("auto:")) {
                val gid = f.requestId.removePrefix("auto:"); val g = pg.goalById(gid) ?: continue
                if (due[gid] != false || g.steps.isNotEmpty()) continue
                val key = "auto|${fr.pepsgoal.core.today()}|$gid"; if (key in app.prefs.notifiedIds) continue
                app.prefs.notifiedIds = app.prefs.notifiedIds + key
                pg.validate(gid)
                if (pg.hasCheck(gid, pg.stepDate(g))) {
                    val pl = pg.me()?.places?.firstOrNull { it.id == g.autoPlace }
                    Notifier.autoValidated(ctx, gid, "${g.shownIcon()} ${g.shownTitle()} : validé ✓", "Tu es resté ${g.autoMin} min ${pl?.let { "à ${it.name}" } ?: "sur place"} : c’est noté. Pas pour ça ? Touche « Annuler ».")
                    LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx)
                }
                continue
            }
            val id = f.requestId.removePrefix("geo:"); val g = pg.goalById(id) ?: continue
            val pl = GeoReminders.of(ctx, id) ?: continue
            if (due[id] == true || (due[id] == null && g.type !in setOf("measure", "objective", "monthly", "project", "program", "group", "duel"))) continue
            val key = "geo|${fr.pepsgoal.core.today()}|$id"
            if (key in app.prefs.notifiedIds) continue
            app.prefs.notifiedIds = app.prefs.notifiedIds + key
            Notifier.simple(ctx, key.hashCode(), Notifier.CH_REMIND, "${g.shownIcon()} ${g.shownTitle()}",
                "${if (pl.exit) "Tu quittes" else "Te voilà à"} ${pl.label.ifBlank { "l’endroit choisi" }} : c’est le moment !", goalId = id.takeIf { g.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit") })
        }
    }
}
