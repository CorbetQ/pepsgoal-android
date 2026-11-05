package fr.pepsgoal.core

import kotlin.random.Random
import kotlin.test.*

class PollPolicyTest {
    private val MIN = 60_000L
    private val NOW = 1_000_000_000_000L

    private fun ctx(sinceEventMin: Long?, hour: Int = 14, level: Int = PollPolicy.BALANCED, metered: Boolean = false, powerSave: Boolean = false, batteryLow: Boolean = false) =
        PollContext(NOW, sinceEventMin?.let { NOW - it * MIN } ?: 0L, hour, metered, powerSave, batteryLow, level)

    @Test fun balancedCadenceFollowsUsefulEvents() {
        assertEquals(2 * MIN, PollPolicy.intervalMs(ctx(1)), "Événement récent : 2 min")
        assertEquals(5 * MIN, PollPolicy.intervalMs(ctx(6)), "6 min après : 5 min")
        assertEquals(5 * MIN, PollPolicy.intervalMs(ctx(16)), "16 min après : calme de jour")
        assertEquals(5 * MIN, PollPolicy.intervalMs(ctx(null)))
        assertEquals(30 * MIN, PollPolicy.intervalMs(ctx(null, hour = 23)), "Nuit dès 23 h")
        assertEquals(30 * MIN, PollPolicy.intervalMs(ctx(60, hour = 3)))
        assertEquals(2 * MIN, PollPolicy.intervalMs(ctx(1, hour = 3)), "Une conversation active la nuit reste réactive")
    }

    @Test fun multipliersAndBounds() {
        assertEquals((7.5 * MIN).toLong(), PollPolicy.intervalMs(ctx(null, metered = true)))
        assertEquals(10 * MIN, PollPolicy.intervalMs(ctx(null, powerSave = true)))
        assertEquals(15 * MIN, PollPolicy.intervalMs(ctx(null, batteryLow = true)))
        assertEquals(60 * MIN, PollPolicy.intervalMs(ctx(null, hour = 2, metered = true, powerSave = true, batteryLow = true)), "Plafond 60 min")
        // Table de cas : jamais sous 2 min en Équilibré, jamais au-delà de 60 min
        for (since in listOf(null, 0L, 3L, 6L, 14L, 16L, 600L)) for (h in 0..23) for (m in listOf(false, true)) for (ps in listOf(false, true)) for (b in listOf(false, true)) for (level in 0..2) {
            val v = PollPolicy.intervalMs(ctx(since, h, level, m, ps, b))
            assertTrue(v <= 60 * MIN, "≤ 60 min")
            if (level == PollPolicy.BALANCED) assertTrue(v >= 2 * MIN, "≥ 2 min en Équilibré")
            if (level == PollPolicy.FAST) assertTrue(v >= MIN)
        }
    }

    @Test fun fastLevelKeepsHistoricBehaviourAndSaverIsSlower() {
        assertEquals(MIN, PollPolicy.intervalMs(ctx(10, level = PollPolicy.FAST)))
        assertEquals(3 * MIN, PollPolicy.intervalMs(ctx(null, level = PollPolicy.FAST)))
        assertEquals(15 * MIN, PollPolicy.intervalMs(ctx(null, hour = 5, level = PollPolicy.FAST)))
        assertEquals(3 * MIN, PollPolicy.intervalMs(ctx(null, hour = 23, level = PollPolicy.FAST)), "Rapide : nuit de 0 h à 7 h comme avant")
        assertEquals(3 * MIN, PollPolicy.intervalMs(ctx(null, level = PollPolicy.FAST, metered = true, batteryLow = true)), "Rapide : sans multiplicateur")
        assertEquals(3 * MIN, PollPolicy.intervalMs(ctx(1, level = PollPolicy.SAVER)))
        assertEquals(15 * MIN, PollPolicy.intervalMs(ctx(null, level = PollPolicy.SAVER)))
        assertEquals(60 * MIN, PollPolicy.intervalMs(ctx(null, hour = 1, level = PollPolicy.SAVER)))
    }

    private class MemoryPollState(val backing: MutableMap<String, Long>) : PollState {
        override var lastEventAt: Long get() = backing["event"] ?: 0L; set(v) { backing["event"] = v }
        override var lastRunWall: Long get() = backing["run"] ?: 0L; set(v) { backing["run"] = v }
    }

    @Test fun lastEventIsPersistedAcrossRestart() {
        val disk = mutableMapOf<String, Long>()
        PollPolicy.recordEvent(MemoryPollState(disk), NOW - 60_000)
        // « Redémarrage » : nouvel objet d'état relu depuis le même stockage
        val restarted = MemoryPollState(disk)
        assertEquals(NOW - 60_000, restarted.lastEventAt)
        assertEquals(2 * MIN, PollPolicy.intervalMs(restarted, ctx(null)))
    }

    @Test fun backoffDoublesWithCapAndResets() {
        val r = Random(1)
        assertEquals(5 * MIN, PollPolicy.backoffMs(5 * MIN, 0, r))
        val one = PollPolicy.backoffMs(5 * MIN, 1, r); val two = PollPolicy.backoffMs(5 * MIN, 2, r); val three = PollPolicy.backoffMs(5 * MIN, 3, r)
        assertTrue(one in 8 * MIN..12 * MIN, "≈ 10 min ±20 %")
        assertTrue(two in 16 * MIN..24 * MIN)
        assertTrue(three in 32 * MIN..48 * MIN)
        assertEquals(60 * MIN, PollPolicy.backoffMs(5 * MIN, 10, r), "Plafond 60 min")
    }

    @Test fun gateLetsOnlyOneBackgroundTriggerThrough() {
        val iv = 5 * MIN
        assertTrue(PollGate.allow("service", NOW, 0, iv))
        assertFalse(PollGate.allow("worker", NOW + 30_000, NOW, iv), "Service puis worker 30 s plus tard : un seul passe")
        assertFalse(PollGate.allow("alarme", NOW + 3 * MIN, NOW, iv))
        assertTrue(PollGate.allow("alarme", NOW + 4 * MIN, NOW, iv), "80 % de l'intervalle écoulé")
        assertTrue(PollGate.allow("écran", NOW + 1_000, NOW, iv), "L'écran passe toujours")
        assertTrue(PollGate.allow("test", NOW + 1_000, NOW, iv))
        assertTrue(PollGate.allow("service", NOW, NOW + 3_600_000, iv), "Horodatage futur ignoré")
    }

    @Test fun wakeLockOnlyWhenNothingElseKeepsTheCpuAwake() {
        assertTrue(PollPolicy.needsWakeLock("alarme", interactive = true, charging = true))
        assertFalse(PollPolicy.needsWakeLock("worker", interactive = false, charging = false))
        assertTrue(PollPolicy.needsWakeLock("service", interactive = false, charging = false))
        assertFalse(PollPolicy.needsWakeLock("service", interactive = true, charging = false))
        assertFalse(PollPolicy.needsWakeLock("service", interactive = false, charging = true))
        assertFalse(PollPolicy.needsWakeLock("écran", interactive = false, charging = false))
        assertTrue(PollPolicy.WAKE_LOCK_MS <= 15_000)
    }

    @Test fun serviceRunsOnlyByDayWithSocialLife() {
        assertTrue(PollPolicy.shouldRunService(12, 5 * MIN, hasSocial = true, instantNotifs = true))
        assertFalse(PollPolicy.shouldRunService(23, 5 * MIN, true, true), "Nuit")
        assertFalse(PollPolicy.shouldRunService(6, 5 * MIN, true, true))
        assertTrue(PollPolicy.shouldRunService(7, 5 * MIN, true, true))
        assertFalse(PollPolicy.shouldRunService(12, 30 * MIN, true, true), "Cadence ≥ 30 min : l'alarme suffit")
        assertFalse(PollPolicy.shouldRunService(12, 5 * MIN, false, true), "Compte sans vie sociale")
        assertFalse(PollPolicy.shouldRunService(12, 5 * MIN, true, false), "Réglage coupé")
    }

    @Test fun safetyAlarmIsSpacedWhileTheServiceRelieves() {
        assertEquals(15 * MIN, PollPolicy.alarmDelayMs(5 * MIN, serviceAlive = true))
        assertEquals(30 * MIN, PollPolicy.alarmDelayMs(30 * MIN, serviceAlive = true))
        assertEquals(5 * MIN, PollPolicy.alarmDelayMs(5 * MIN, serviceAlive = false))
    }

    @Test fun screenLoopSlowsDownWithoutNewsAndResets() {
        var d = ScreenPollPolicy.FAST
        val seen = mutableListOf(d)
        repeat(4) { d = ScreenPollPolicy.next(d, changed = false); seen.add(d) }
        assertEquals(listOf(20_000L, 30_000L, 45_000L, 60_000L, 60_000L), seen)
        assertEquals(20_000L, ScreenPollPolicy.next(d, changed = true), "Retour à 20 s sur interaction ou nouveauté")
    }

    @Test fun chatProbeSlowsDownWhenIdle() {
        assertEquals(6_000L, ChatPollPolicy.delayMs(30_000))
        assertEquals(10_000L, ChatPollPolicy.delayMs(3 * MIN))
        assertEquals(20_000L, ChatPollPolicy.delayMs(6 * MIN))
    }

    @Test fun localWorkPeriodDependsOnHealthLinks() {
        assertEquals(30L, LocalWorkPolicy.periodMinutes(true))
        assertEquals(60L, LocalWorkPolicy.periodMinutes(false))
    }

    @Test fun statsRingKeepsTwentyFourHoursAndSevenDays() {
        val h = StatsRing.HOUR; val t0 = 10 * StatsRing.DAY
        val r = StatsRing()
        r.add("req", 3, t0); r.add("req", 2, t0 + h); r.add("wl.max", 5, t0); r.add("wl.max", 2, t0 + 1)
        assertEquals(5L, r.last24h(t0 + h)["req"])
        assertEquals(5L, r.last24h(t0 + h)["wl.max"], "Les clés « .max » gardent le maximum")
        assertEquals(listOf(3L, 2L), r.perHour("req", t0 + h).takeLast(2))
        assertEquals(2L, r.last24h(t0 + 24 * h)["req"], "L'heure la plus ancienne sort de la fenêtre")
        val back = StatsRing.decode(r.encode())
        assertEquals(r.last24h(t0 + h), back.last24h(t0 + h))
        assertEquals(5L, back.week(t0 + 3 * StatsRing.DAY)["req"])
        assertNull(back.week(t0 + 8 * StatsRing.DAY)["req"], "Sept jours au plus")
        assertTrue(StatsRing.decode("n'importe quoi").last24h(t0).isEmpty())
    }

    @Test fun pathClassesForCounters() {
        assertEquals("statut", NetStats.classOf("/v1/status"))
        assertEquals("flux", NetStats.classOf("/v1/feed?tab=friends"))
        assertEquals("conversations", NetStats.classOf("/v1/conversations/abc"))
        assertEquals("admin", NetStats.classOf("/v1/admin/users"))
        assertEquals("sync", NetStats.classOf("/v1/sync/pull?cursor=3"))
        assertEquals("social", NetStats.classOf("/v1/friends/suggestions"))
    }
}
