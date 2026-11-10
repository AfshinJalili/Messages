package com.afsh.messages.helpers

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle
import androidx.annotation.AnimRes
import com.afsh.messages.R
import org.fossify.commons.helpers.isUpsideDownCakePlus

/**
 * How a screen comes and goes (#176). Every screen pushed on top rises from the bottom and Back
 * reverses it; Inbox, Library and Settings cross-fade into each other. Compose stacks inside one
 * screen use the same motion through ScreenStack and TabSwitch.
 */
enum class ScreenMotion(
    @AnimRes private val openEnter: Int,
    @AnimRes private val openExit: Int,
    @AnimRes private val closeEnter: Int,
    @AnimRes private val closeExit: Int,
) {
    PUSH(R.anim.screen_push_enter, R.anim.screen_hold, R.anim.screen_hold, R.anim.screen_pop_exit),
    TAB(R.anim.screen_fade_in, R.anim.screen_fade_out, R.anim.screen_fade_in, R.anim.screen_fade_out);

    /** Options for a startActivity that must use this motion, whatever the target screen's own. */
    fun options(context: Context): Bundle = ActivityOptions.makeCustomAnimation(context, openEnter, openExit).toBundle()

    /** From Android 14, sets the motion once, in onCreate, for both opening and closing. */
    fun applyTo(activity: Activity) {
        if (!isUpsideDownCakePlus()) return
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, openEnter, openExit)
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, closeEnter, closeExit)
    }

    /** Before Android 14, the motion is set right after each start. */
    @Suppress("DEPRECATION")
    fun afterStart(activity: Activity) {
        if (!isUpsideDownCakePlus()) activity.overridePendingTransition(openEnter, openExit)
    }

    /** Before Android 14, the motion is set right after each finish. */
    @Suppress("DEPRECATION")
    fun afterFinish(activity: Activity) {
        if (!isUpsideDownCakePlus()) activity.overridePendingTransition(closeEnter, closeExit)
    }
}
