package com.afsh.messages.activities

import org.fossify.commons.activities.BaseSimpleActivity
import com.afsh.messages.R
import com.afsh.messages.helpers.cobaltTheme

open class SimpleActivity : BaseSimpleActivity() {
    open fun refreshAfterDeletion() {}

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
