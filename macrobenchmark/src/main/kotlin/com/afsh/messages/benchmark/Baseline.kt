package com.afsh.messages.benchmark

/**
 * Cold-start / scroll baselines recorded on the local API-35 Pixel-7 AVD with the synthetic
 * seeded inbox (~2k threads / ~25k messages) and Room already warm (2026-11-18).
 *
 * Gate = baseline × [SLACK]. Update from `benchmarkData.json` when intentionally changing
 * startup or the seeder shape.
 *
 * Scroll gate uses gfxinfo P90 frame time ([SCROLL_FRAME_P90_MS]) — the emulator-safe
 * stand-in for Perfetto frameOverrunMs (RenderThread slices are missing on swiftshader).
 *
 * Measured: TTID median 985.3 ms; gfxFrameTime90thPercentileMs median 63.0 ms
 * (gfxFrameTotalCount median 164; jank% median ~57.5).
 */
object Baseline {
    const val SLACK = 1.20

    /** Median timeToInitialDisplayMs for StartupMode.COLD. */
    const val TTID_MEDIAN_MS = 985.3

    /** Median gfxFrameTime90thPercentileMs across firstInboxScroll iterations. */
    const val SCROLL_FRAME_P90_MS = 63.0

    fun ttidBudgetMs(): Double = TTID_MEDIAN_MS * SLACK
    fun scrollFrameBudgetMs(): Double = SCROLL_FRAME_P90_MS * SLACK
}
