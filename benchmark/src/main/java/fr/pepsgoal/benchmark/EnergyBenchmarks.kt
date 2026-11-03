package fr.pepsgoal.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Physical power rails only. Explicit opt-in avoids silently presenting emulator estimates as energy. */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class EnergyBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun todayJourneyPower() {
        assumeTrue("Pass -Pandroid.testInstrumentationRunnerArguments.pepsgoal.power=true on a supported physical device",
            InstrumentationRegistry.getArguments().getString("pepsgoal.power") == "true")
        assumeTrue("ODPM rails unavailable", PowerMetric.deviceSupportsHighPrecisionTracking())
        rule.measureRepeated(PACKAGE, listOf(FrameTimingMetric(), PowerMetric(PowerMetric.Energy())),
            CompilationMode.Partial(BaselineProfileMode.Require), iterations = 5, startupMode = StartupMode.WARM,
            setupBlock = { pressHome(); startDemo() }) {
            scrollList("list", 5)
        }
    }
}

/** Compare actual frame/startup traces with and without compilation; no hardcoded battery claim. */
@RunWith(AndroidJUnit4::class)
class UncompiledStartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Test fun coldStartupWithoutProfile() = rule.measureRepeated(PACKAGE, listOf(StartupTimingMetric()),
        CompilationMode.None(), iterations = 10, startupMode = StartupMode.COLD) {
        pressHome(); startDemo()
    }
}
