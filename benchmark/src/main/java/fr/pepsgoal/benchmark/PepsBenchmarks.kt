package fr.pepsgoal.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/*
 * Parcours de mesure Braise Pop 8.1 (spec §8). Seuils visés sur un appareil modeste (Galaxy A15 / A35) :
 * P90 ≤ 16 ms, P99 ≤ 24 ms, aucune image > 32 ms pendant les validations, démarrage à froid ≤ 1,2 s ;
 * écart de P90 entre fond « Partout » et « Nulle part » ≤ 1 ms.
 */

private val PARTIAL = CompilationMode.Partial(BaselineProfileMode.Require)

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    /** Démarrage à froid, 10 itérations, avec le profil de référence. */
    @Test fun coldStartup() = rule.measureRepeated(PACKAGE, listOf(StartupTimingMetric()), PARTIAL, iterations = 10, startupMode = StartupMode.COLD) {
        pressHome(); startDemo()
    }
}

/** Défilement d'Aujourd'hui, fond « Partout » puis « Nulle part » (le coût du fond se lit dans l'écart). */
@RunWith(Parameterized::class)
class TodayScrollBenchmark(private val bgWhere: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "fond={0}") fun params() = listOf("all", "off") }
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun todayScroll() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startDemo(bgWhere) }) { scrollList("list") }
}

@RunWith(AndroidJUnit4::class)
class InteractionBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    /** Valider 3 objectifs : pop de la case, roulement du compteur, glissade de la ligne. */
    @Test fun validate() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.COLD,
        setupBlock = { pressHome(); startDemo() }) {
        device.findObjects(By.res("check")).take(3).forEach { it.click(); device.waitForIdle() }
    }

    /** Cinq appuis sur la case d'un cap quotidien (« appui = +1 pas »). */
    @Test fun capPlusOne() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startDemo() }) {
        val cap = device.findObject(By.res("cap_check")) ?: return@measureRepeated
        repeat(5) { cap.click(); Thread.sleep(120) }
        device.waitForIdle()
    }

    /** Ouverture rebondissante de la fiche d'un objectif, puis défilement de la fiche. */
    @Test fun openGoalSheet() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startDemo() }) {
        val list = device.findObject(By.res("list")) ?: return@measureRepeated
        list.children.getOrNull(2)?.click(); device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN); device.waitForIdle()
        device.pressBack(); device.waitForIdle()
    }

    /** Statistiques : héros et parallaxe du fond, défilement. */
    @Test fun statsPage() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startDemo(open = "stats") }) { scrollList("page_list") }

    /** Conversation de démonstration : défilement des messages. */
    @Test fun chatScroll() = rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric()), PARTIAL, iterations = 5, startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startDemo(open = "chat") }) { scrollList("chat_list", 4) }
}
