package com.afsh.messages.benchmark

/**
 * Cold-start / scroll baselines recorded on the local API-35 Pixel-7 AVD with the synthetic
 * seeded inbox (~2k threads / ~25k messages) and Room already warm, after the cold-start
 * merge on redesign-groundwork (97aad79) — Room-first paint, dirty-only reconcile,
 * denormalized previews, single-activity splash, baseline profile (2026-11-18).
 *
 * Gate = baseline × [SLACK]. Update from `benchmarkData.json` when intentionally changing
 * startup or the seeder shape.
 *
 * Scroll gate uses gfxinfo P90 frame time ([SCROLL_FRAME_P90_MS]) — the emulator-safe
 * stand-in for Perfetto frameOverrunMs (RenderThread slices are missing on swiftshader).
 *
 * Before (pre-#286, same AVD/method/iterations): TTID median 985.3 ms;
 * gfxFrameTime90thPercentileMs median 63.0 ms; jank% median ~57.5;
 * gfxFrameTotalCount median 164.
 *
 * After (this baseline): TTID median 650.2 ms (runs min 576.9 / max 740.9);
 * gfxFrameTime50thPercentileMs median 37.0 ms; gfxFrameTime90thPercentileMs median 53.0 ms
 * (runs 53–57); jank% median 40.2; gfxFrameTotalCount median 168.5.
 * TTFD is not reported (no reportFullyDrawn yet).
 */
object Baseline {
    const val SLACK = 1.20

    /** Median timeToInitialDisplayMs for StartupMode.COLD. */
    const val TTID_MEDIAN_MS = 650.2

    /** Median gfxFrameTime90thPercentileMs across firstInboxScroll iterations. */
    const val SCROLL_FRAME_P90_MS = 53.0

    fun ttidBudgetMs(): Double = TTID_MEDIAN_MS * SLACK
    fun scrollFrameBudgetMs(): Double = SCROLL_FRAME_P90_MS * SLACK
}
