package com.afsh.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import com.afsh.messages.R
import com.afsh.messages.helpers.ScreenMotion
import com.afsh.messages.helpers.cobaltTheme

open class SimpleActivity : BaseSimpleActivity() {
    /** Whether this screen opens and closes with [ScreenMotion]; the inbox, the task root, keeps the system's. */
    protected open val usesScreenMotion = true

    open fun refreshAfterDeletion() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (usesScreenMotion) ScreenMotion.applyTo(this)
    }

    // Before Android 14 a screen cannot set its own motion, so the screen that opens it does.
    // Starts that pass options chose their own; other apps keep the system's.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        if (options == null && intent.component?.packageName == packageName) ScreenMotion.afterStart(this)
    }

    override fun finish() {
        super.finish()
        if (usesScreenMotion) ScreenMotion.afterFinish(this)
    }

    override fun onPostResume() {
        super.onPostResume()
        com.afsh.messages.helpers.UndoDeletion.attach(this)
    }

    override fun setTheme(resId: Int) {
        super.setTheme(cobaltTheme() ?: resId)
    }

    // One launcher icon: the colour variants were a Fossify customisation the app no longer offers.
    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"
}
