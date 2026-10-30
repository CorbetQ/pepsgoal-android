package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthReportTest {
    private val files = object : DbFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
    }
    private lateinit var pg: Pg
    private val zone = ZoneId.of("Europe/Paris")
    private fun ms(d: LocalDate, h: Int) = LocalDateTime.of(d.year, d.month, d.dayOfMonth, h, 0).atZone(zone).toInstant().toEpochMilli()

    @BeforeTest fun setUp() {
        Clock.zone = { zone }; Clock.now = { LocalDateTime.of(2026, 10, 20, 12, 0).atZone(zone).toInstant().toEpochMilli() }
        val store = Store(files)
        store.use(Db(session = "me", users = mutableListOf(User("me", "camille", "Camille Roux", createdAt = ms(LocalDate.of(2026, 8, 1), 9)))), false)
        pg = Pg(store)
    }

    @Test fun reportAndComparison() {
        val run = Goal(uid(), "me", "routine", "Courir", createdAt = ms(LocalDate.of(2026, 8, 1), 9)).also { it.mode = "days"; it.days = (0..6).toList(); pg.db.goals.add(it) }
        val read = Goal(uid(), "me", "routine", "Lire", createdAt = ms(LocalDate.of(2026, 8, 1), 9)).also { it.mode = "days"; it.days = (0..6).toList(); pg.db.goals.add(it) }
        // Septembre : Courir 10 jours à 8 h ; octobre (1er → 20) : Courir 15 jours à 8 h, Lire 5 jours à 20 h
        for (d in 1..10) pg.addCheck(run, "done", 1.0, 10, ymd(LocalDate.of(2026, 9, d))).at = ms(LocalDate.of(2026, 9, d), 8)
        for (d in 1..15) pg.addCheck(run, "done", 1.0, 10, ymd(LocalDate.of(2026, 10, d))).at = ms(LocalDate.of(2026, 10, d), 8)
        for (d in 1..5) pg.addCheck(read, "done", 1.0, 10, ymd(LocalDate.of(2026, 10, d))).at = ms(LocalDate.of(2026, 10, d), 20)
        pg.me()!!.perfectDays.addAll(listOf("2026-10-02", "2026-09-03"))
        val r = pg.monthReport("2026-10")
        assertEquals(20, r.n); assertEquals(10, r.prevN); assertEquals(15, r.active)
        assertEquals("+100 %", pctDelta(r.n, r.prevN)); assertEquals("↑", arrowOf(trendOf(r.n.toDouble(), r.prevN.toDouble())))
        assertEquals(1, r.perfect); assertEquals(1, r.prevPerfect)
        assertEquals(run.id, r.star!!.goal.id)
        val tr = r.goals.first { it.goal.id == run.id }
        assertEquals(20, tr.planned); assertEquals(.75, tr.rate!!, 1e-9); assertEquals(1, tr.trend)
        assertEquals("ok", if (r.bestDay!!.first == "2026-10-05" && r.bestDay!!.second == 2) "ok" else r.bestDay.toString())
        assertEquals("11:00", r.avgTime); assertEquals("08:00", r.prevAvgTime)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), pg.reportMonths())
        assertNull(pctDelta(3, 0)); assertEquals("−50 %", pctDelta(5, 10))
        assertEquals(0, heatLevel(0, 3)); assertEquals(4, heatLevel(3, 3)); assertEquals(2, heatLevel(1, 3))
    }
}

class SeasonalScenesTest {
    @Test fun freeInSeason() {
        assertEquals("xmastree", seasonalScene(LocalDate.of(2026, 12, 10))?.key)
        assertEquals("valentine", seasonalScene(LocalDate.of(2027, 2, 14))?.key)
        assertEquals("loiresummer", seasonalScene(LocalDate.of(2027, 7, 14))?.key)
        assertNull(seasonalScene(LocalDate.of(2026, 10, 30)))
        val b = SHOP_BGS.first { it.key == "xmastree" }
        kotlin.test.assertTrue(b.freeNow(LocalDate.of(2026, 12, 31))); kotlin.test.assertTrue(!b.freeNow(LocalDate.of(2027, 1, 2)))
        // Pas une édition limitée : achetable toute l'année
        assertNull(b.season)
    }
}
