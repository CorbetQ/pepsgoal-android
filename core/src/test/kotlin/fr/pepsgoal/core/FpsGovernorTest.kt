package fr.pepsgoal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FpsGovernorTest {
    /** Envoie une mesure par seconde de [from] à [to] (ms, inclus) et renvoie le dernier palier. */
    private fun FpsGovernor.run(from: Long, to: Long, ratio: Float): Int { var t = from; var l = level; while (t <= to) { l = feed(t, ratio); t += 1000 }; return l }

    @Test fun stableStaysNormal() {
        val g = FpsGovernor()
        assertEquals(0, g.run(0, 60_000, 1f))
        assertEquals(0, g.run(61_000, 120_000, .9f)) // zone d'hystérésis
    }

    @Test fun shortDipIsIgnored() {
        val g = FpsGovernor()
        g.run(0, 5_000, 1f)
        assertEquals(0, g.run(6_000, 8_000, .5f)) // 2 s seulement sous 85 %
        assertEquals(0, g.run(9_000, 12_000, 1f))
        assertEquals(0, g.run(13_000, 15_000, .6f))
    }

    @Test fun sustainedDropLowersOneStepAtATime() {
        val g = FpsGovernor()
        assertEquals(0, g.feed(0, .5f)); assertEquals(0, g.feed(1000, .5f)); assertEquals(0, g.feed(2000, .5f))
        assertEquals(1, g.feed(3000, .5f)) // 3 s de chute soutenue
        assertEquals(1, g.feed(4000, .5f)); assertEquals(1, g.feed(5000, .5f))
        assertEquals(2, g.feed(6000, .5f)) // 3 s de plus au palier 1
        assertEquals(QualitySteps.MAX, g.run(7000, 60_000, .2f))
        assertEquals(QualitySteps.MAX, g.run(61_000, 80_000, .2f)) // jamais au-delà du dernier palier
    }

    @Test fun recoversStepByStepAfterStability() {
        val g = FpsGovernor()
        g.run(0, 6_000, .5f)
        assertEquals(2, g.level)
        assertEquals(2, g.run(7_000, 16_000, 1f)) // 9 s stables : pas encore
        assertEquals(1, g.feed(17_000, 1f)) // 10 s stables
        assertEquals(1, g.run(18_000, 26_000, 1f))
        assertEquals(0, g.feed(27_000, 1f))
    }

    @Test fun hysteresisBandHoldsLevel() {
        val g = FpsGovernor()
        g.run(0, 3_000, .5f)
        assertEquals(1, g.level)
        assertEquals(1, g.run(4_000, 120_000, .9f)) // entre 85 et 95 % : ni baisse ni remontée
    }

    @Test fun instabilityAfterRaiseDoublesRecoveryDelay() {
        val g = FpsGovernor()
        g.run(0, 3_000, .5f) // palier 1
        g.run(4_000, 14_000, 1f) // remonte à 0 à 14 s
        assertEquals(0, g.level); assertEquals(10_000, g.recoverMs)
        g.run(15_000, 18_000, .5f) // rechute aussitôt
        assertEquals(1, g.level); assertEquals(20_000, g.recoverMs)
        assertEquals(1, g.run(19_000, 38_000, 1f)) // 19 s stables : pas encore
        assertEquals(0, g.feed(39_000, 1f))
        // Longue stabilité : le délai revient à sa valeur de départ
        g.run(40_000, 200_000, 1f)
        assertEquals(10_000, g.recoverMs)
    }

    @Test fun pausedMeasuresRestartCounters() {
        val g = FpsGovernor()
        g.feed(0, .5f); g.feed(1000, .5f); g.feed(2000, .5f)
        // Application en arrière-plan 10 s : la chute ne se cumule pas avec la suivante
        assertEquals(0, g.feed(12_000, .5f)); assertEquals(0, g.feed(13_000, .5f)); assertEquals(0, g.feed(14_000, .5f))
        assertEquals(1, g.feed(15_000, .5f))
    }

    @Test fun resetGoesBackToNormal() {
        val g = FpsGovernor()
        g.run(0, 10_000, .3f)
        assertTrue(g.level > 0)
        g.reset()
        assertEquals(0, g.level)
    }

    @Test fun ratioIsBounded() {
        assertEquals(1f, FpsGovernor.ratio(130f, 120f))
        assertEquals(.5f, FpsGovernor.ratio(30f, 60f))
        assertEquals(1f, FpsGovernor.ratio(30f, 0f))
    }

    @Test fun stepsNeverExceedCeiling() {
        val ceilings = listOf(
            Quality(), Quality(bgFps = 8, particles = .6f),
            Quality(bgAnim = false, bgFps = 6, fx = false, particles = .3f, rich = false),
            Quality(bgFps = 12, particles = .1f, sceneScale = .3f),
        )
        for (c in ceilings) {
            var prev = c
            assertEquals(c, QualitySteps.apply(c, 0))
            for (l in 1..QualitySteps.MAX + 2) {
                val q = QualitySteps.apply(c, l)
                assertTrue(QualitySteps.within(q, c), "palier $l au-dessus du plafond $c : $q")
                assertTrue(QualitySteps.within(q, prev), "palier $l plus riche que le précédent")
                prev = q
            }
        }
    }

    @Test fun stepsReduceInOrder() {
        val c = Quality()
        assertEquals(.6f, QualitySteps.apply(c, 1).particles); assertEquals(9, QualitySteps.apply(c, 1).bgFps)
        assertEquals(.4f, QualitySteps.apply(c, 2).sceneScale); assertEquals(6, QualitySteps.apply(c, 2).bgFps)
        assertFalse(QualitySteps.apply(c, 3).fx); assertTrue(QualitySteps.apply(c, 3).bgAnim)
        assertFalse(QualitySteps.apply(c, 4).bgAnim); assertFalse(QualitySteps.apply(c, 4).rich)
        assertEquals(QualitySteps.MAX + 1, QualitySteps.LABELS.size)
    }
}
