package fr.pepsgoal.core

import fr.pepsgoal.core.model.Place
import java.time.*
import kotlin.math.*

/** Capabilities are explicit: a terrestrial shower must never fall inside a nebula or an aquarium. */
enum class SceneCapability(val light: Boolean, val weather: Boolean) {
    OUTDOOR(true, true), WINDOW(true, true), ARTISTIC(false, false)
}

val SCENE_CAPABILITIES: Map<String, SceneCapability> = buildMap {
    listOf("mountains", "tram", "mistforest", "dunes", "sakura", "lagoon", "loire", "lighthouse",
        "vineyard", "balloons", "bamboo", "rooftops", "canyon", "savanna", "icefield", "angers",
        "nantes", "angevine", "snowpeak", "eggmeadow", "flowerfield", "summerbeach", "autumnwood",
        "loiresummer", "pop", "sky").forEach { put(it, SceneCapability.OUTDOOR) }
    put("rainwindow", SceneCapability.WINDOW)
    listOf("nebula", "deepsea", "citynight", "magma", "coderain", "borealis", "planet", "jellyfish",
        "nighttrain", "lanterns", "tunnel", "haunted", "snowvillage", "fireworks", "heartsky", "xmastree",
        "valentine", "kopa", "ecran", "grid", "stars", "waves", "aurora", "mesh", "blob", "plain", "custom")
        .forEach { put(it, SceneCapability.ARTISTIC) }
}
fun sceneCapability(key: String) = SCENE_CAPABILITIES[key.removePrefix("s:")] ?: SceneCapability.ARTISTIC

/** Pure snapshot; only this value crosses from Compose to the background renderer. */
data class SceneEnvironment(
    val localTime: ZonedDateTime,
    val night: Float, val dawn: Float, val day: Float, val dusk: Float,
    val sun: Float, val condition: String, val clouds: Float,
    val wind: Float, val precipitation: Float, val code: Int, val freshness: Float, val sunDown: Boolean
) {
    val phase: String get() = when { night >= .5f -> "night"; dawn >= dusk && dawn > day -> "dawn"; dusk > day -> "dusk"; else -> "day" }
    fun sky() = Weather.Sky(phase, condition, sun, clouds, code)
}

object SceneAtmosphere {
    fun zone(place: Place?, forecast: Forecast?, fallback: ZoneId = ZoneId.systemDefault()): ZoneId =
        listOfNotNull(place?.tz, forecast?.tz).firstNotNullOfOrNull { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback

    fun resolve(now: Instant, place: Place? = null, forecast: Forecast? = null, cachedAt: Long? = null,
                fallback: ZoneId = ZoneId.systemDefault()): SceneEnvironment {
        val local = now.atZone(zone(place, forecast, fallback)); val date = local.toLocalDate()
        val daily = forecast?.daily?.firstOrNull { it.day == date.toString() }
        fun minute(s: String?): Double? = runCatching { LocalDateTime.parse(s).takeIf { it.toLocalDate().isEqual(date) }
            ?.let { it.hour * 60.0 + it.minute + it.second / 60.0 } }.getOrNull()
        val predicted = solar(date, place, local.offset)
        val r = minute(daily?.sunrise); val s = minute(daily?.sunset)
        val (rise, set) = if (r != null && s != null && r < s) r to s else predicted
        val t = local.hour * 60.0 + local.minute + local.second / 60.0
        fun smooth(a: Double, b: Double, v: Double): Double { val x = ((v - a) / (b - a)).coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
        val polar = rise == set
        val daylight = when { polar -> if (rise == 0.0) 1.0 else 0.0; else -> smooth(rise - 40, rise + 40, t) * (1 - smooth(set - 40, set + 40, t)) }
        val dawn = if (polar) 0.0 else (1 - abs(t - rise) / 80).coerceIn(0.0, 1.0)
        val dusk = if (polar) 0.0 else (1 - abs(t - set) / 80).coerceIn(0.0, 1.0)
        val day = daylight * (1 - max(dawn, dusk)); val night = (1 - daylight) * (1 - max(dawn, dusk))
        val sum = day + night + dawn + dusk
        // Never interpret a cached observation's time as the live clock. Fade old weather over the last 15 minutes.
        val observation = forecast?.current?.time?.let { runCatching { LocalDateTime.parse(it).atZone(local.zone).toInstant() }.getOrNull() }
        val age = listOfNotNull(cachedAt?.let { Duration.between(Instant.ofEpochMilli(it), now).seconds },
            observation?.let { Duration.between(it, now).seconds }).maxOrNull()
        val freshness = if (forecast == null || age == null || age < -300) 0f else ((7200 - age) / 900f).coerceIn(0f, 1f)
        val c = forecast?.current
        val condition = if (freshness > 0f) Weather.wmo(c?.code).cond else "clear"
        return SceneEnvironment(local, (night / sum).toFloat(), (dawn / sum).toFloat(), (day / sum).toFloat(),
            (dusk / sum).toFloat(), if (polar) .5f else ((t - rise) / (set - rise)).toFloat().coerceIn(0f, 1f),
            condition, ((c?.clouds ?: 10.0).toFloat().coerceIn(0f, 100f) * freshness),
            (c?.wind ?: 0.0).toFloat().coerceIn(0f, 150f) * freshness,
            (c?.precip ?: 0.0).toFloat().coerceIn(0f, 100f) * freshness, if (freshness > 0) c?.code ?: 0 else 0, freshness,
            if (polar) rise != 0.0 else t < rise || t > set)
    }

    /** NOAA fractional-year approximation, local legal minutes; identical pairs mark polar day/night. */
    private fun solar(date: LocalDate, place: Place?, offset: ZoneOffset): Pair<Double, Double> {
        if (place == null || !place.lat.isFinite() || !place.lon.isFinite() || abs(place.lat) > 90 || abs(place.lon) > 180) {
            val season = cos((date.dayOfYear - 172) / date.lengthOfYear().toDouble() * 2 * PI)
            return (7.25 - 1.25 * season) * 60 to (19.75 + 2.1 * season) * 60
        }
        val g = 2 * PI / date.lengthOfYear() * (date.dayOfYear - 1)
        val eq = 229.18 * (.000075 + .001868 * cos(g) - .032077 * sin(g) - .014615 * cos(2*g) - .040849 * sin(2*g))
        val dec = .006918 - .399912 * cos(g) + .070257 * sin(g) - .006758 * cos(2*g) + .000907 * sin(2*g) - .002697 * cos(3*g) + .00148 * sin(3*g)
        val lat = Math.toRadians(place.lat.coerceIn(-89.999, 89.999))
        val cosH = (cos(Math.toRadians(90.833)) / (cos(lat) * cos(dec)) - tan(lat) * tan(dec))
        if (cosH < -1) return 0.0 to 0.0
        if (cosH > 1) return 1440.0 to 1440.0
        val noon = 720 - 4 * place.lon - eq + offset.totalSeconds / 60.0
        val span = Math.toDegrees(acos(cosH)) * 4
        return noon - span to noon + span
    }
}
