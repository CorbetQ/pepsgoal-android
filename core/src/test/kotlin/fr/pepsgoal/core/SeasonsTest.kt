package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeasonsTest {
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
        Clock.zone = { zone }; at(2026, 10, 30)
        val store = Store(files)
        store.use(Db(session = "me", users = mutableListOf(User("me", "camille", "Camille Roux"))), false)
        pg = Pg(store)
    }

    @Test fun calendar() {
        val s1 = themeSeasonAt(LocalDate.of(2026, 1, 5))
        assertEquals(1, s1.n); assertEquals(LocalDate.of(2026, 2, 15), s1.end)
        assertEquals(2, themeSeasonAt(LocalDate.of(2026, 2, 16)).n)
        // Avant le début du calendrier : numéros négatifs, mais thèmes et dates cohérents
        val s0 = themeSeasonAt(LocalDate.of(2026, 1, 4))
        assertEquals(0, s0.n); assertEquals(LocalDate.of(2025, 11, 24), s0.start)
        val now = themeSeasonAt()
        assertEquals(8, now.n); assertEquals("braise", now.theme.key)
        assertEquals(DayOfWeekCheck.MONDAY, now.start.dayOfWeek); assertEquals(41, daysBetween(now.start, now.end))
        assertEquals(LocalDate.of(2026, 10, 26), now.start); assertEquals(1, now.week()); assertEquals("givre", themeSeasonAt(now.end.plusDays(1)).theme.key)
    }

    @Test fun passAndQuests() {
        val s = themeSeasonAt()
        val g = Goal(uid(), "me", "routine", "Lire").also { pg.db.goals.add(it) }
        // 5 validations à 12 flammes cette saison, une la veille de la saison (non comptée)
        for (i in 0 until 5) pg.addCheck(g, "done", 1.0, 12, ymd(todayDate().minusDays(i.toLong())))
        pg.addCheck(g, "done", 1.0, 12, ymd(s.start.minusDays(1)))
        assertEquals(60, pg.seasonFlames(s))
        assertEquals(listOf(0), pg.tiersToClaim().map { it.i })
        assertNotNull(pg.claimTier(1))
        val wallet = pg.wallet()
        assertNull(pg.claimTier(0)); assertEquals(wallet + 20, pg.wallet())
        assertNotNull(pg.claimTier(0))
        assertTrue(pg.tiersToClaim().isEmpty())
        // Quêtes : 3 par semaine, stables, sans doublon
        val q = weekQuestDefs(todayDate())
        assertEquals(3, q.map { it.key }.toSet().size)
        assertEquals(q.map { it.key }, weekQuestDefs(weekStart(todayDate())).map { it.key })
        val wq = pg.weekQuests()
        assertEquals(3, wq.size)
        wq.firstOrNull { it.done }?.let { done ->
            assertNull(pg.claimWeekQuest(done.def.key)); assertEquals(60 + WEEK_QUEST_BONUS, pg.seasonFlames(s))
            assertNotNull(pg.claimWeekQuest(done.def.key))
        }
        // Palier d'article : rangé dans les achats
        repeat(14) { pg.addCheck(g, "done", 1.0, 10, today()) }
        assertNull(pg.claimTier(1)); assertTrue(pg.ownsItem("sticker:${s.theme.stickers[0]}"))
    }
}

private object DayOfWeekCheck { val MONDAY = java.time.DayOfWeek.MONDAY }

class PetTest {
    @Test fun stagesAndMood() {
        assertEquals("egg", petStageOf(0, 1).key)
        assertEquals("baby", petStageOf(3, 1).key); assertEquals("baby", petStageOf(0, 3).key)
        assertEquals("young", petStageOf(14, 2).key); assertEquals("adult", petStageOf(2, 15).key)
        assertEquals("legend", petStageOf(120, 1).key)
        // Avancement : la meilleure des deux voies (série 8 sur 3 → 14, ou niveau 3 sur 3 → 8)
        assertEquals(5.0 / 11, petProgress(8, 3), 1e-9)
        assertEquals(1.0, petProgress(200, 30), 1e-9)
        assertEquals("proud", petMood(true, true, false, 1, true, 10))
        assertEquals("tired", petMood(true, false, false, 2, false, 10))
        assertEquals("tired", petMood(false, false, false, null, true, 10))
        assertEquals("happy", petMood(true, false, false, null, false, 22))
        assertEquals("happy", petMood(false, false, false, 5, false, 10))
        assertEquals("tired", petMood(false, false, false, null, false, 22))
        assertEquals("calm", petMood(false, false, false, 3, false, 10))
    }
}
