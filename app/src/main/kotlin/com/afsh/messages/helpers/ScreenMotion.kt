package com.afsh.messages.helpers

import android.app.Activity
import com.afsh.messages.R
import org.fossify.commons.helpers.isUpsideDownCakePlus

/**
 * The one screen motion: opening a screen slides it in from the end with a small parallax on the
 * screen underneath; Back reverses that. Activities get it here; Compose stacks inside a screen
 * get it from ScreenStack. Timing and parallax live in res/values/motion.xml. On Android 14+ the
 * close pair also drives the predictive-back gesture.
 */
object ScreenMotion {
    /** From Android 14, sets the motion once, in onCreate, for both opening and closing. */
    fun applyTo(activity: Activity) {
        if (!isUpsideDownCakePlus()) return
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.screen_open_enter, R.anim.screen_open_exit)
        activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.screen_close_enter, R.anim.screen_close_exit)
    }

    /** Before Android 14, the motion is set right after each start. */
    @Suppress("DEPRECATION")
    fun afterStart(activity: Activity) {
        if (!isUpsideDownCakePlus()) activity.overridePendingTransition(R.anim.screen_open_enter, R.anim.screen_open_exit)
    }

    /** Before Android 14, the motion is set right after each finish. */
    @Suppress("DEPRECATION")
    fun afterFinish(activity: Activity) {
        if (!isUpsideDownCakePlus()) activity.overridePendingTransition(R.anim.screen_close_enter, R.anim.screen_close_exit)
    }
}
