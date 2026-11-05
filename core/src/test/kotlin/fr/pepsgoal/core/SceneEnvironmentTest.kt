package fr.pepsgoal.core

import fr.pepsgoal.core.model.Place
import java.time.*
import org.junit.Test
import kotlin.test.*

class SceneEnvironmentTest {
    private val paris = Place("Paris", lat = 48.86, lon = 2.35, tz = "Europe/Paris")
    private fun forecast(date: String = "2026-11-03", time: String = "2026-11-03T12:00") = Forecast("Europe/Paris",
        WxCurrent(time = time, code = 63, clouds = 90.0, wind = 30.0),
        listOf(WxDay("2026-11-02", sunrise = "2026-11-02T04:00", sunset = "2026-11-02T23:00"),
            WxDay(date, sunrise = "${date}T07:40", sunset = "${date}T17:30")))
    @Test fun observationDoesNotFreezeClock() {
        val w = forecast()
        val noon = SceneAtmosphere.resolve(Instant.parse("2026-11-03T11:00:00Z"), paris, w)
        val night = SceneAtmosphere.resolve(Instant.parse("2026-11-03T22:00:00Z"), paris, w)
        assertEquals("day", noon.phase); assertEquals("night", night.phase)
        assertEquals("clear", night.condition)
    }
    @Test fun cityTimezoneAndMatchingDate() {
        val x = SceneAtmosphere.resolve(Instant.parse("2026-11-03T06:40:00Z"), paris, forecast())
        assertEquals(7, x.localTime.hour); assertEquals("dawn", x.phase)
        val tokyo = Place("Tokyo", lat = 35.7, lon = 139.7, tz = "Asia/Tokyo")
        assertEquals(LocalDate.parse("2026-11-04"), SceneAtmosphere.resolve(Instant.parse("2026-11-03T16:00:00Z"), tokyo).localTime.toLocalDate())
    }
    @Test fun daylightSavingAndInvalidTimezone() {
        val a = SceneAtmosphere.resolve(Instant.parse("2026-03-29T00:30:00Z"), paris)
        val b = SceneAtmosphere.resolve(Instant.parse("2026-03-29T01:30:00Z"), paris)
        assertEquals(1, a.localTime.hour); assertEquals(3, b.localTime.hour)
        val invalid = Place("X", lat = 0.0, lon = 0.0, tz = "invalid")
        assertEquals(ZoneId.of("Europe/Paris"), SceneAtmosphere.zone(invalid, forecast(), ZoneOffset.UTC))
        assertEquals(ZoneOffset.UTC, SceneAtmosphere.zone(invalid, null, ZoneOffset.UTC))
    }
    @Test fun freshnessFadesBeforeTwoHoursAndRejectsOldObservation() {
        val now = Instant.parse("2026-11-03T12:52:30Z") // 112.5 minutes after observation
        val x = SceneAtmosphere.resolve(now, paris, forecast(), now.toEpochMilli())
        assertEquals(.5f, x.freshness, .001f); assertEquals(45f, x.clouds, .001f)
        assertEquals("clear", SceneAtmosphere.resolve(now.plusSeconds(451), paris, forecast(), now.toEpochMilli()).condition)
    }
    @Test fun cacheAgeAlsoBoundsFreshness() {
        val now = Instant.parse("2026-11-03T11:00:00Z")
        assertEquals(0f, SceneAtmosphere.resolve(now, paris, forecast(), now.minusSeconds(7201).toEpochMilli()).freshness)
    }
    @Test fun missingAndMalformedDataStayFinite() {
        val now = Instant.parse("2026-11-03T06:00:00Z")
        val x = SceneAtmosphere.resolve(now, paris, Forecast("bad", WxCurrent(time="broken"), listOf(WxDay("2026-11-03", sunrise="broken"))))
        assertTrue(x.sun.isFinite()); assertEquals(1f, x.day+x.night+x.dawn+x.dusk, .001f)
        assertEquals("clear", x.condition)
    }
    @Test fun polarDayAndNight() {
        val p = Place("Tromsø", lat = 69.65, lon = 18.96, tz = "Europe/Oslo")
        assertEquals(1f, SceneAtmosphere.resolve(Instant.parse("2026-06-21T00:00:00Z"), p).day)
        assertEquals(1f, SceneAtmosphere.resolve(Instant.parse("2026-12-21T12:00:00Z"), p).night)
    }
    @Test fun themeSwitchesAtSunriseWhileDawnRemainsVisible() {
        val e = SceneAtmosphere.resolve(Instant.parse("2026-11-03T06:20:00Z"), paris, forecast())
        assertTrue(e.sunDown); assertTrue(e.dawn > 0f)
        assertFalse(SceneAtmosphere.resolve(Instant.parse("2026-11-03T07:00:00Z"), paris, forecast()).sunDown)
    }
    @Test fun allCatalogEntriesHaveCapabilities() {
        assertTrue(ALL_BGS.all { it.key in SCENE_CAPABILITIES })
        assertEquals(SceneCapability.ARTISTIC, sceneCapability("s:deepsea"))
        assertEquals(SceneCapability.WINDOW, sceneCapability("rainwindow"))
        assertEquals(SceneCapability.OUTDOOR, sceneCapability("s:loire"))
    }
    @Test fun weightsAreContinuousAroundSunriseAndMidnight() {
        for (i in 0..1440) {
            val now = Instant.parse("2026-11-02T23:00:00Z").plusSeconds(i*60L)
            val e = SceneAtmosphere.resolve(now, paris, forecast())
            assertTrue(listOf(e.night,e.day,e.dawn,e.dusk,e.sun).all { it in 0f..1f })
            assertEquals(1f, e.night+e.day+e.dawn+e.dusk, .0001f)
        }
    }
}
