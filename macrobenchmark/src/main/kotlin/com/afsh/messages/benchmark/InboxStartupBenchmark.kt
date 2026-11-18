package com.afsh.messages.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start (TTID) and first-scroll frame timing on a Telephony-seeded inbox.
 * The workflow seeds via the debug androidTest seeder, then installs the benchmark
 * app and warms Room once before these iterations.
 *
 * Scroll uses [FrameTimingGfxInfoMetric] (dumpsys gfxinfo) because Perfetto
 * [androidx.benchmark.macro.FrameTimingMetric] needs RenderThread slices that the
 * CI swiftshader emulator does not emit. gfxinfo P90 is the emulator-safe stand-in
 * for the plan's frameOverrunMs gate.
 *
 * TTFD is not asserted: the inbox does not yet call reportFullyDrawn, so TTID alone
 * is the honest signal (avoids touching MainActivity from open PR #286).
 */
@RunWith(AndroidJUnit4::class)
class InboxStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            killProcess()
        },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MS)
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun firstInboxScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MS)
            checkNotNull(waitForInboxList()) {
                "Inbox list not scrollable for $TARGET_PACKAGE — is Room warm after seeding?"
            }
            // Return to the top so the measured swipe always animates new frames.
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.3).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.8).toInt(),
                    30,
                )
                device.waitForIdle()
            }
        },
    ) {
        repeat(4) {
            val list = waitForInboxList()
            if (list != null) {
                try {
                    list.setGestureMargin(device.displayWidth / 5)
                    list.swipe(Direction.DOWN, 0.9f, 800)
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    device.swipe(
                        device.displayWidth / 2,
                        (device.displayHeight * 0.75).toInt(),
                        device.displayWidth / 2,
                        (device.displayHeight * 0.25).toInt(),
                        40,
                    )
                }
            } else {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.75).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.25).toInt(),
                    40,
                )
            }
            device.waitForIdle()
        }
    }

    /** Compose LazyColumn is scrollable; prefer the app's own node over the launcher workspace. */
    private fun MacrobenchmarkScope.waitForInboxList(): UiObject2? {
        val deadline = System.currentTimeMillis() + UI_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val match = device.findObjects(By.scrollable(true)).firstOrNull { node ->
                try {
                    node.applicationPackage == TARGET_PACKAGE
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    false
                }
            }
            if (match != null) return match
            // Fallback: drag the center of the screen if accessibility is flaky mid-frame.
            device.waitForIdle()
        }
        return null
    }

    companion object {
        const val TARGET_PACKAGE = "com.afsh.messages"
        const val ITERATIONS = 8
        const val UI_TIMEOUT_MS = 30_000L
    }
}
