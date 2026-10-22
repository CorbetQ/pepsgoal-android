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

    @Test fun restDayKeepsStreak() {
        val g = goal("routine") { mode = "days"; days = (0..6).toList() }
        for (i in listOf(1, 2, 4, 5)) pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong())))
        assertEquals(2, pg.globalStreak("me").n)
        pg.me()!!.restDays.add(ymd(todayDate().minusDays(3)))
        assertEquals(4, pg.globalStreak("me").n)
        assertEquals(4, pg.goalStreak(g))
        assertTrue(pg.toggleRest(ymd(todayDate().minusDays(1))) != null) // pas dans le passé
        assertEquals(null, pg.toggleRest(today()))
        assertTrue(pg.isRest())
    }

    @Test fun backupRoundTripAndMerge() {
        val g = goal("routine") { mode = "days"; days = (0..6).toList() }
        pg.addCheck(g, "done", 1.0, 10, ymd(todayDate().minusDays(1)))
        val salt = Backup.newSalt(); val key = Backup.key("secret123", salt)
        val raw = Backup.seal(JSON.encodeToString(fr.pepsgoal.core.model.Db.serializer(), pg.db), key, salt)
        assertTrue(Backup.isBackup(raw))
        assertEquals(null, Backup.open(raw, "faux"))
        val back = JSON.decodeFromString(fr.pepsgoal.core.model.Db.serializer(), Backup.open(raw, "secret123")!!)
        // Nouveau téléphone : profil vierge, puis restauration par fusion
        pg.db.goals.clear(); pg.db.checkins.clear()
        assertEquals(1, Backup.merge(pg, back))
        assertEquals(1, pg.db.checkins.size)
        assertEquals(0, Backup.merge(pg, back)) // rien en double
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

    @Test fun mojibake() {
        assertEquals("Objectif validé, bravo !", fixMojibake("Objectif validÃ©, bravo !"))
        assertEquals("Déjà validé à 7 h – génial 🎉", fixMojibake("Déjà validÃ© Ã  7 h â€“ gÃ©nial ðŸŽ‰"))
        assertEquals("Âge, où, ça, ÉTÉ, « œuvre »", fixMojibake("Âge, où, ça, ÉTÉ, « œuvre »"))
        assertEquals("été", fixMojibake("ÃƒÂ©tÃƒÂ©"))
        assertEquals("plain text", fixMojibake("plain text"))
    }

    @Test fun stepsValidateWhenAllChecked() {
        val g = goal("routine") { mode = "days"; days = (0..6).toList() }
        pg.stepAdd(g.id, "Brossage"); pg.stepAdd(g.id, "Lecture")
        val (a, b) = g.steps.map { it.id }
        assertTrue(pg.validate(g.id) is Pg.VResult.OpenGoal)
        assertTrue(!pg.hasCheck(g.id, today()))
        pg.stepToggle(g.id, a); assertTrue(!pg.hasCheck(g.id, today()))
        pg.stepToggle(g.id, b); assertTrue(pg.hasCheck(g.id, today()))
        pg.stepToggle(g.id, b); assertTrue(!pg.hasCheck(g.id, today()))
        assertEquals(setOf(a), pg.stepsDone(g))
    }

    @Test fun chainAndVacation() {
        val a = goal("routine") { mode = "days"; days = (0..6).toList() }
        val b = goal("routine") { mode = "days"; days = (0..6).toList() }
        b.after = a.id
        assertEquals(listOf(b.id), pg.chainNext(a).map { it.id })
        assertTrue(pg.chainCandidates(a).none { it.id == b.id })
        var chained: String? = null
        pg.hooks = object : Hooks { override fun chained(prev: Goal, next: Goal) { chained = next.id } }
        pg.validate(a.id); assertEquals(b.id, chained)
        // Vacances : la série tient pendant la pause
        for (i in 1..3) pg.addCheck(a, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong())))
        assertEquals(null, pg.setVacation(today(), ymd(todayDate().plusDays(5))))
        assertTrue(pg.isRest(ymd(todayDate().plusDays(3))))
        at(2026, 10, 25)
        assertEquals(4, pg.goalStreak(a))
        pg.endVacation()
        assertEquals("2026-10-21|2026-10-24", pg.me()!!.vacations.single())
    }

    @Test fun insightsFindings() {
        val rates = (1..30).associate { ymd(todayDate().minusDays(it.toLong())) to if (it % 2 == 0) .9 else .4 }
        val sleep = (1..30).associate { ymd(todayDate().minusDays(it.toLong())) to if (it % 2 == 0) 8.0 else 6.0 }
        val f = Insights.findings(rates, mapOf("sleep" to sleep))
        assertTrue(f.first().text.contains("90 %") && f.first().text.contains("40 %"))
    }

    @Test fun hardDaySavingsStats() {
        val a = goal("routine") { mode = "days"; days = (0..6).toList() }
        val b = goal("routine") { mode = "days"; days = (0..6).toList() }
        for (i in 1..3) { pg.addCheck(a, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong()))); pg.addCheck(b, "done", 1.0, 10, ymd(todayDate().minusDays(i.toLong()))) }
        assertTrue(pg.toggleHardDay() != null) // pas d'essentiel choisi
        assertEquals(null, pg.toggleEssential(a.id))
        assertEquals(null, pg.toggleHardDay())
        assertEquals(listOf(a.id), pg.todayPlan().due.map { it.g.id })
        at(2026, 10, 22)
        // Hier (journée difficile) : b non validé mais sa série tient ; a, essentiel, la perd
        assertEquals(3, pg.goalStreak(b)); assertEquals(0, pg.goalStreak(a))
        val c = goal("abstinence", ago = 10) { since = ymd(todayDate().minusDays(10)); saveBase = 10.0; saveCost = .5 }
        val sv = pg.savings(c)!!
        assertEquals(100.0, sv.units); assertEquals(50.0, sv.money)
        assertEquals(3, pg.goalStats(b).longest)
    }
}
