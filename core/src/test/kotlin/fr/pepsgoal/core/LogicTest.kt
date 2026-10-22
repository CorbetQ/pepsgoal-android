package fr.pepsgoal.core

import fr.pepsgoal.core.cloud.Mapping
import fr.pepsgoal.core.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicTest {
    private val files = object : DbFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
    }
    private lateinit var pg: Pg
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12) { Clock.now = { LocalDateTime.of(y, mo, d, h, 0).atZone(zone).toInstant().toEpochMilli() } }

    @BeforeTest fun setUp() {
        Clock.zone = { zone }; at(2026, 10, 21)
        val store = Store(files)
        val u = User("me", "camille", "Camille Roux", createdAt = nowMs() - 40 * DAY_MS)
        store.use(Db(session = "me", users = mutableListOf(u)), false)
        pg = Pg(store)
    }

    private fun goal(type: String, ago: Int = 30, f: Goal.() -> Unit = {}) = Goal(uid(), "me", type, "Test $type", createdAt = nowMs() - ago * DAY_MS).also { it.f(); pg.db.goals.add(it) }

    @Test fun routineStreakAndToday() {
        val g = goal("routine") { mode = "days"; days = listOf(0, 1, 2, 3, 4, 5, 6) }
        for (i in 1..5) pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong())))
        assertEquals(5, pg.goalStreak(g))
        assertEquals(Streak(5, false).n, pg.globalStreak("me").n)
        assertEquals(1, pg.todayPlan().due.size)
        pg.validate(g.id)
        assertEquals(6, pg.globalStreak("me").n)
        assertTrue(pg.globalStreak("me").lit)
        assertEquals(60, pg.points("me"))
        pg.validate(g.id) // annulation
        assertEquals(5, pg.globalStreak("me").n)
    }

    @Test fun freezesFillGap() {
        val g = goal("routine") { mode = "days"; days = (0..6).toList() }
        pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(3)))
        pg.me()!!.freezes = 3
        pg.applyFreezes(pg.me())
        assertEquals(1, pg.me()!!.freezes)
        assertEquals(3, pg.globalStreak("me").n)
    }

    @Test fun dailyCap() {
        val g = goal("cap") { target = 10000.0; unit = "pas" }
        pg.addCheck(g, "done", 1.0, 12, ymd(todayDate().minusDays(1)))
        assertEquals(1, pg.todayPlan().due.size)
        assertTrue(pg.validate(g.id) is Pg.VResult.AskProgress)
        pg.addProgress(g.id, 6000.0)
        assertEquals(false, pg.hasCheck(g.id, today()))
        pg.addProgress(g.id, 4500.0)
        assertTrue(pg.hasCheck(g.id, today()))
        assertEquals(2, pg.goalStreak(g))
        pg.capSet(g, 8000.0) // nouvelle lecture Santé plus basse : la validation du jour tombe
        assertEquals(false, pg.hasCheck(g.id, today()))
        assertEquals(8000.0, pg.capDay(g))
        // Le serveur le voit comme un objectif chiffré marqué « cap », relu comme un cap quotidien
        val sg = Mapping.toServerGoal(g)
        assertEquals("objective", sg["type"].toString().trim('"'))
        assertEquals("cap", Mapping.fromServerGoal(sg).type)
    }

    @Test fun objectiveCompletes() {
        val g = goal("objective") { target = 3.0; unit = "livres" }
        pg.addProgress(g.id, 2.0); pg.addProgress(g.id, 1.0)
        assertTrue(g.completedAt != null)
        assertEquals("100%", pg.statBig(g).v)
    }

    @Test fun periodWindow() {
        at(2026, 10, 21, 18) // mercredi
        val g = goal("period") { freq = "week"; days = listOf(2); start = "17:30"; end = "19:00" }
        assertTrue(pg.periodWindow(g).open)
        assertEquals(1, pg.todayPlan().due.size)
        at(2026, 10, 21, 21)
        assertTrue(!pg.periodWindow(g).open)
    }

    @Test fun mappingRoundTrip() {
        val g = goal("measure") { unit = "kg"; dir = "down"; timeFmt = false; set("target", 70.0) }
        val back = Mapping.fromServerGoal(Mapping.toServerGoal(g))
        assertEquals(Mapping.toServerGoal(back).toString(), Mapping.toServerGoal(Mapping.fromServerGoal(Mapping.toServerGoal(back))).toString())
        assertEquals("down", back.dir); assertEquals(70.0, back.targetOrNull)
        val u = pg.me()!!; u.favGoals.add(g.id); u.settings.accent = 6
        val st = Mapping.toServerSettings(u)
        val u2 = User("x", "x", "x"); Mapping.applyServerSettings(u2, st)
        assertEquals(listOf(g.id), u2.favGoals); assertEquals(6, u2.settings.accent)
    }

    @Test fun seededMatchesJs() {
        val r = Recaps.seeded("abc|week|2026-10-19é")
        assertEquals(0.6216483721509576, r(), 1e-12)
        assertEquals(0.42934317351318896, r(), 1e-12)
        assertEquals(0.48474208847619593, r(), 1e-12)
    }

    @Test fun recapAndBadges() {
        val g = goal("routine") { mode = "days"; days = (0..6).toList() }
        for (i in 0..8) pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong())))
        val (slides, st) = Recaps.slides(pg, "me", "month")
        assertTrue(slides.size > 5); assertTrue(st.n >= 9)
        val state = Badges.state(pg, "me")
        assertTrue(state.first { it.f.k == "streak" }.tier >= 1)
    }
}
