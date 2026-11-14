package org.fossify.commons

import android.app.Activity
import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.checkUseEnglish

open class FossifyApp : Application() {

    open val isAppLockFeatureAvailable = false

    /** The app's own theme for Commons screens such as app lock, or null for Commons' palette themes. */
    open fun themeId(activity: Activity): Int? = null

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        setupAppLockManager()
    }

    private fun setupAppLockManager() {
        if (isAppLockFeatureAvailable) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        }
    }
}
