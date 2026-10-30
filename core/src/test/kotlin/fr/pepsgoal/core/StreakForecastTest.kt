package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreakForecastTest {
    private val files = object : DbFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
    }
    private lateinit var pg: Pg
    private val zone = ZoneId.of("Europe/Paris")

    @BeforeTest fun setUp() {
        // Vendredi 30 octobre 2026, midi
        Clock.zone = { zone }; Clock.now = { LocalDateTime.of(2026, 10, 30, 12, 0).atZone(zone).toInstant().toEpochMilli() }
        val store = Store(files)
        store.use(Db(session = "me", users = mutableListOf(User("me", "camille", "Camille Roux", createdAt = nowMs() - 90 * DAY_MS))), false)
        pg = Pg(store)
    }

    @Test fun riskyWeekdayAndPlanB() {
        val run = Goal(uid(), "me", "routine", "Courir", createdAt = nowMs() - 80 * DAY_MS).also { it.mode = "days"; it.days = (0..6).toList(); it.planB = "Vélo d’appartement"; pg.db.goals.add(it) }
        // 9 semaines : validé chaque jour sauf le mardi
        for (i in 1..63) { val d = todayDate().minusDays(i.toLong()); if (wdIdx(d) != 1) pg.addCheck(run, "done", 1.0, 10, ymd(d)) }
        val wx = Forecast(daily = listOf(WxDay(ymd(todayDate().plusDays(1)), code = 63, pop = 90.0)))
        val f = pg.streakForecast(wx)
        assertEquals(7, f.size)
        val tue = f.first { wdIdx(parseYmd(it.date)) == 1 }
        assertEquals("high", tue.level)
        assertTrue(tue.reasons.any { it.contains("mardi") })
        assertNotNull(tue.idea)
        // Samedi : pluie annoncée, objectif d'extérieur → plan B météo
        val sat = f.first { it.date == ymd(todayDate().plusDays(1)) }
        assertTrue(sat.reasons.any { it.startsWith("Pluie annoncée") })
        assertEquals("low", f.first { wdIdx(parseYmd(it.date)) == 3 }.level)
        assertTrue(forecastText(f).contains("mardi"))
    }

    @Test fun restDaysAndCalmWeek() {
        val g = Goal(uid(), "me", "routine", "Lire", createdAt = nowMs() - 80 * DAY_MS).also { it.mode = "days"; it.days = (0..6).toList(); pg.db.goals.add(it) }
        for (i in 1..63) pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong())))
        pg.me()!!.restDays.add(ymd(todayDate().plusDays(2)))
        val f = pg.streakForecast()
        assertEquals("rest", f[2].level)
        assertTrue(f.none { it.level == "high" || it.level == "medium" })
        assertTrue(forecastText(f).startsWith("Semaine à venir au calme"))
        assertEquals("Plan B : « Vélo » à la place de « Lire »", pg.planBIdea(g.also { it.planB = "Vélo" }, true))
        g.miniLabel = "2 pages"; assertEquals("Version mini de « Lire » : 2 pages", pg.planBIdea(g, false))
    }
}
