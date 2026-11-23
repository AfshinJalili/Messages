package com.afsh.messages.activities

import com.afsh.commons.activities.BaseSimpleActivity
import com.afsh.messages.R
import com.afsh.messages.helpers.afshXmlTheme

open class SimpleActivity : BaseSimpleActivity() {
    /**
     * Task-root screens (the inbox) use [com.afsh.messages.R.style.AppTheme_Afsh_Root_*] so the
     * launcher open keeps the system motion; pushed screens use the slide window animations.
     */
    protected open val isTaskRootTheme = false

    open fun refreshAfterDeletion() {}

    override fun onPostResume() {
        super.onPostResume()
        com.afsh.messages.helpers.UndoDeletion.attach(this)
    }

    // Whatever theme Commons or the manifest asks for, XML widgets use the AFSH roles.
    override fun setTheme(resId: Int) {
        super.setTheme(afshXmlTheme(root = isTaskRootTheme))
    }

    // One launcher icon.
    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"
}
