package fr.pepsgoal.core

import kotlinx.serialization.Serializable

@Serializable
class WxCurrent(val time: String? = null, val temp: Double? = null, val feels: Double? = null, val humidity: Double? = null, val isDay: Boolean = true,
                val precip: Double? = null, val code: Int = 0, val wind: Double? = null, val clouds: Double? = null)

@Serializable
class WxDay(val day: String, val code: Int? = null, val max: Double? = null, val min: Double? = null, val pop: Double? = null, val sunrise: String? = null, val sunset: String? = null)

@Serializable
class Forecast(val tz: String = "", val current: WxCurrent = WxCurrent(), val daily: List<WxDay> = emptyList(), val fetchedAt: String? = null)

@Serializable
class WxCache(val key: String, val data: Forecast, val at: Long)

class Wmo(val cond: String, val label: String, val emoji: String)

/** Météo de la ville choisie (Open-Meteo, licence CC BY 4.0) : codes WMO, conseil du jour et ciel du thème Météo. */
object Weather {
    private val WMO = listOf(
        listOf(0) to arrayOf("clear", "Ciel dégagé", "☀️", "🌙"), listOf(1) to arrayOf("clear", "Plutôt dégagé", "🌤️", "🌙"),
        listOf(2) to arrayOf("clouds", "Partiellement nuageux", "⛅", "☁️"), listOf(3) to arrayOf("clouds", "Couvert", "☁️", "☁️"),
        listOf(45, 48) to arrayOf("fog", "Brouillard", "🌫️", "🌫️"), listOf(51, 53, 55) to arrayOf("rain", "Bruine", "🌦️", "🌧️"),
        listOf(56, 57) to arrayOf("rain", "Bruine verglaçante", "🌧️", "🌧️"), listOf(61) to arrayOf("rain", "Pluie faible", "🌦️", "🌧️"),
        listOf(63) to arrayOf("rain", "Pluie", "🌧️", "🌧️"), listOf(65) to arrayOf("rain", "Forte pluie", "🌧️", "🌧️"),
        listOf(66, 67) to arrayOf("rain", "Pluie verglaçante", "🌧️", "🌧️"), listOf(71, 73, 75, 77) to arrayOf("snow", "Neige", "🌨️", "🌨️"),
        listOf(80, 81) to arrayOf("rain", "Averses", "🌦️", "🌧️"), listOf(82) to arrayOf("rain", "Fortes averses", "⛈️", "⛈️"),
        listOf(85, 86) to arrayOf("snow", "Averses de neige", "🌨️", "🌨️"), listOf(95) to arrayOf("storm", "Orage", "⛈️", "⛈️"),
        listOf(96, 99) to arrayOf("storm", "Orage et grêle", "⛈️", "⛈️")
    )

    fun wmo(code: Int?, day: Boolean = true): Wmo {
        val r = WMO.firstOrNull { it.first.contains(code ?: -1) }?.second ?: WMO[2].second
        return Wmo(r[0], r[1], if (day) r[2] else r[3])
    }

    fun temp(v: Double?) = if (v == null) "—" else "${Math.round(v)}°"

    fun tip(w: Forecast): String {
        val c = w.current; val k = wmo(c.code, c.isDay).cond; val t = c.temp ?: 15.0
        return when {
            k == "storm" -> "Orage : reste à l’abri, ta flamme aussi 🔥"
            k == "snow" -> "Neige : couvre-toi bien, ou garde ta série au chaud ❄️"
            k == "rain" -> "Pluie au programme : idéal pour tes objectifs à l’intérieur 📚"
            k == "fog" -> "Brouillard : prudence si tu sors courir 🌫️"
            (w.daily.firstOrNull()?.pop ?: 0.0) >= 60 -> "Pluie probable aujourd’hui : prévois un plan B à l’intérieur ☔"
            t >= 30 -> "Grosse chaleur : hydrate-toi et profite de la fraîcheur du matin 💧"
            t <= 3 -> "Il fait froid : un échauffement de plus avant de sortir 🧣"
            k == "clear" && c.isDay -> "Grand beau : parfait pour une routine en plein air ☀️"
            else -> "Temps calme, motivation au beau fixe 💪"
        }
    }

    class Sky(val phase: String, val cond: String, val prog: Float, val clouds: Float, val code: Int)

    /** Phase du ciel (nuit, aube, jour, crépuscule) selon l'heure, le lever et le coucher du soleil de la ville. */
    fun sky(w: Forecast?): Sky {
        var nowStr = "${today()}T${nowHM()}"; var rise = "${today()}T07:15"; var set = "${today()}T19:30"; var cond = "clear"
        if (w != null) { nowStr = w.current.time ?: nowStr; rise = w.daily.firstOrNull()?.sunrise ?: rise; set = w.daily.firstOrNull()?.sunset ?: set; cond = wmo(w.current.code).cond }
        val m = { s: String -> hmMinutes(s.drop(11).take(5)) }
        val t = m(nowStr); val r = m(rise); val s = m(set)
        val phase = if (t < r - 40 || t > s + 40) "night" else if (Math.abs(t - r) <= 40) "dawn" else if (Math.abs(t - s) <= 40) "dusk" else "day"
        val prog = ((t - r).toFloat() / maxOf(1, s - r)).coerceIn(0f, 1f)
        return Sky(phase, cond, prog, (w?.current?.clouds ?: 10.0).toFloat(), w?.current?.code ?: 0)
    }
    fun skyDark(w: Forecast?) = sky(w).phase in setOf("night", "dusk")
}
