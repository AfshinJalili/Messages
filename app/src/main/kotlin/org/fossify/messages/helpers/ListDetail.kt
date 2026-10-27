package org.fossify.messages.helpers

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitRule
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.activities.ThreadActivity

/**
 * 85 · Wide / list-detail (D09): from 840 dp the inbox (96 dp rail + 360 dp list) stays beside
 * the open conversation. Activity embedding puts the two activities side by side, so neither has
 * to host the other. Below 840 dp nothing changes.
 */
object ListDetail {
    private const val MIN_WIDTH_DP = 840
    private const val LIST_PANE_DP = 96 + 360
    // Only for windows the calculator cannot size; 456 of 1280 dp.
    private const val FALLBACK_RATIO = 0.36f

    fun install(context: Context) {
        val filter = SplitPairFilter(
            ComponentName(context, MainActivity::class.java),
            ComponentName(context, ThreadActivity::class.java),
            null,
        )
        val rule = SplitPairRule.Builder(setOf(filter))
            .setMinWidthDp(MIN_WIDTH_DP)
            .setMinSmallestWidthDp(0)
            // Opening another conversation replaces the one beside the list instead of stacking.
            .setClearTop(true)
            .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
            .setDefaultSplitAttributes(SplitAttributes.Builder().setSplitType(SplitAttributes.SplitType.ratio(FALLBACK_RATIO)).build())
            .build()
        RuleController.getInstance(context).addRule(rule)
        val density = context.resources.displayMetrics.density
        try {
            // A fixed-width list pane: the ratio follows the window.
            SplitController.getInstance(context).setSplitAttributesCalculator { params ->
                if (!params.areDefaultConstraintsSatisfied) return@setSplitAttributesCalculator params.defaultSplitAttributes
                val widthDp = params.parentWindowMetrics.bounds.width() / density
                SplitAttributes.Builder()
                    .setSplitType(SplitAttributes.SplitType.ratio((LIST_PANE_DP / widthDp).coerceIn(0.1f, 0.9f)))
                    .build()
            }
        } catch (_: UnsupportedOperationException) {
            // Older window extensions: the fallback ratio applies.
        }
    }

    /** Whether [activity] is one pane of the split, so the inbox shows its rail even at 456 dp. */
    fun isPane(activity: Activity): Boolean = ActivityEmbeddingController.getInstance(activity).isActivityEmbedded(activity)
}
