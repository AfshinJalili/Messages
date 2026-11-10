package com.afsh.messages.helpers

import android.app.Activity
import com.afsh.messages.R
import org.fossify.commons.helpers.isUpsideDownCakePlus

/**
 * The one screen motion (#176): opening a screen zooms it in, Back shrinks it away. Activities
 * get it here; Compose stacks inside a screen get it from ScreenStack. Timing and scale live in
 * res/values/motion.xml.
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
