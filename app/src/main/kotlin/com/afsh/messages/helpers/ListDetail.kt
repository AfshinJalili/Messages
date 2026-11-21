package com.afsh.messages.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import androidx.window.WindowSdkExtensions
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitRule
import com.afsh.messages.activities.MainActivity
import com.afsh.messages.activities.ThreadActivity

/**
 * 85 · Wide / list-detail (D09): from 840 dp the inbox (96 dp rail + 360 dp list) stays beside
 * the open conversation. Activity embedding puts the two activities side by side, so neither has
 * to host the other. Below 840 dp nothing changes.
 */
object ListDetail {
    internal const val MIN_WIDTH_DP = 840
    internal const val LIST_PANE_DP = 96 + 360
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
        // A fixed-width list pane: the ratio follows the window. Older window extensions keep the
        // fallback ratio, and their minimum width still refuses the split on a phone.
        if (WindowSdkExtensions.getInstance().extensionVersion >= 2) {
            // Lint does not see the version check above.
            @SuppressLint("RequiresWindowSdk")
            SplitController.getInstance(context).setSplitAttributesCalculator { params ->
                // defaultSplitAttributes stays the 0.36 fallback even when the 840 dp
                // minimum failed, and the calculator result replaces that minimum.
                // Returning it put a phone-width window into a crushed split.
                val widthDp = params.parentConfiguration.screenWidthDp
                val mode = listDetailMode(widthDp, params.areDefaultConstraintsSatisfied)
                val type = if (mode == ListDetailMode.Expand) {
                    SplitAttributes.SplitType.SPLIT_TYPE_EXPAND
                } else {
                    SplitAttributes.SplitType.ratio(listPaneRatio(widthDp))
                }
                SplitAttributes.Builder().setSplitType(type).build()
            }
        }
    }

    /** Whether [activity] is one pane of the split, so the inbox shows its rail even at 456 dp. */
    fun isPane(activity: Activity): Boolean = ActivityEmbeddingController.getInstance(activity).isActivityEmbedded(activity)
}

internal enum class ListDetailMode { Expand, Split }

/**
 * A window under [ListDetail.MIN_WIDTH_DP], or one the embedding rule already rejected,
 * stays a single full-screen activity. The calculator must say so itself: its result
 * replaces the rule's minimum width.
 */
internal fun listDetailMode(widthDp: Int, constraintsSatisfied: Boolean): ListDetailMode {
    if (!constraintsSatisfied || widthDp < ListDetail.MIN_WIDTH_DP) return ListDetailMode.Expand
    return ListDetailMode.Split
}

/** List pane of 96 dp rail + 360 dp list, as a fraction of [widthDp], clamped to a legal split. */
internal fun listPaneRatio(widthDp: Int): Float {
    if (widthDp <= 0) return 0.9f
    return (ListDetail.LIST_PANE_DP.toFloat() / widthDp).coerceIn(0.1f, 0.9f)
}
