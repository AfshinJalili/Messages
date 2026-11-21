package com.afsh.commons.activities

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.core.view.size
import com.afsh.commons.R
import com.afsh.commons.extensions.adjustAlpha
import com.afsh.commons.extensions.applyColorFilter
import com.afsh.commons.extensions.applyFontToTextView
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.extensions.getAppIconColors
import com.afsh.commons.extensions.getColoredDrawableWithColor
import com.afsh.commons.extensions.getContrastColor
import com.afsh.commons.extensions.getPermissionString
import com.afsh.commons.extensions.getProperBackgroundColor
import com.afsh.commons.extensions.getProperStatusBarColor
import com.afsh.commons.extensions.getThemeId
import com.afsh.commons.extensions.hasAllPermissions
import com.afsh.commons.extensions.hasPermission
import com.afsh.commons.extensions.hideKeyboard
import com.afsh.commons.extensions.openDeviceSettings
import com.afsh.commons.helpers.MEDIUM_ALPHA
import com.afsh.commons.helpers.MyContextWrapper
import com.afsh.commons.helpers.NavigationIcon
import com.afsh.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import com.afsh.commons.helpers.PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
import com.afsh.commons.helpers.isQPlus
import com.afsh.commons.helpers.isTiramisuPlus
import com.afsh.commons.helpers.isUpsideDownCakePlus
import com.afsh.commons.views.MyAppBarLayout

abstract class BaseSimpleActivity : EdgeToEdgeActivity() {
    var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false
    var useDynamicTheme = true

    private lateinit var backCallback: OnBackPressedCallback

    companion object {
        private const val GENERIC_PERM_HANDLER = 100
    }

    abstract fun getAppIconIDs(): ArrayList<Int>

    abstract fun getAppLauncherName(): String

    abstract fun getRepositoryName(): String?

    /** Return true if the back press was consumed. */
    protected open fun onBackPressedCompat(): Boolean = false

    /** Use when a screen needs to temporarily ignore back (e.g., during animations). */
    protected fun setBackHandlingEnabled(enabled: Boolean) {
        backCallback.isEnabled = enabled
    }

    /** If a subclass wants to explicitly trigger the default behaviour. */
    protected fun performDefaultBack() {
        backCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        backCallback.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (useDynamicTheme) {
            setTheme(getThemeId(showTransparentTop = true))
        }

        installFontInflaterFactory()
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        registerBackPressedCallback()
    }

    private fun installFontInflaterFactory() {
        val inflater = layoutInflater
        if (inflater.factory2 != null) return

        val appCompatDelegate = delegate
        LayoutInflaterCompat.setFactory2(inflater, object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                val view = appCompatDelegate.createView(parent, name, context, attrs)
                val textView = view as? TextView ?: return view
                applyFontToTextView(textView)
                return view
            }

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
                return onCreateView(null, name, context, attrs)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (useDynamicTheme) {
            setTheme(getThemeId(showTransparentTop = true))
            updateBackgroundColor(getProperBackgroundColor())
        }

        updateRecentsAppIcon()
        // App lock is handled by the app module's ActivityLifecycleCallbacks
    }

    override fun onDestroy() {
        super.onDestroy()
        actionOnPermission = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                hideKeyboard()
                finish()
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        if (newBase.baseConfig.useEnglish && !isTiramisuPlus()) {
            super.attachBaseContext(MyContextWrapper(newBase).wrap(newBase, "en"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    fun registerBackPressedCallback() {
        backCallback = onBackPressedDispatcher.addCallback(this) {
            if (onBackPressedCompat()) return@addCallback
            // fallback to system
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    fun updateBackgroundColor(color: Int = baseConfig.backgroundColor) {
        window.decorView.setBackgroundColor(color)
    }

    fun setupTopAppBar(
        topAppBar: MyAppBarLayout,
        navigationIcon: NavigationIcon = NavigationIcon.None,
        topBarColor: Int = getRequiredTopBarColor(),
        searchMenuItem: MenuItem? = null,
    ) {
        val contrastColor = topBarColor.getContrastColor()
        if (navigationIcon != NavigationIcon.None) {
            val drawableId = if (navigationIcon == NavigationIcon.Cross) {
                R.drawable.ic_msym_close
            } else {
                R.drawable.ic_msym_arrow_back
            }

            topAppBar.toolbar?.navigationIcon =
                resources.getColoredDrawableWithColor(drawableId, contrastColor)
            topAppBar.toolbar?.setNavigationContentDescription(navigationIcon.accessibilityResId)
        }

        topAppBar.toolbar?.setNavigationOnClickListener {
            hideKeyboard()
            finish()
        }

        updateTopBarColors(topAppBar, topBarColor)

        if (!isSearchBarEnabled) {
            searchMenuItem?.actionView
                ?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                ?.apply {
                    applyColorFilter(contrastColor)
                }

            searchMenuItem?.actionView
                ?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                ?.apply {
                    setTextColor(contrastColor)
                    setHintTextColor(contrastColor.adjustAlpha(MEDIUM_ALPHA))
                    hint = "${getString(R.string.search)}…"

                    if (isQPlus()) {
                        textCursorDrawable = null
                    }
                }

            // search underline
            searchMenuItem?.actionView
                ?.findViewById<View>(androidx.appcompat.R.id.search_plate)
                ?.apply {
                    background.setColorFilter(contrastColor, PorterDuff.Mode.MULTIPLY)
                }
        }
    }

    fun updateRecentsAppIcon() {
        if (baseConfig.isUsingModifiedAppIcon) {
            val appIconIDs = getAppIconIDs()
            val currentAppIconColorIndex = getCurrentAppIconColorIndex()
            if (appIconIDs.size - 1 < currentAppIconColorIndex) {
                return
            }

            val recentsIcon =
                BitmapFactory.decodeResource(resources, appIconIDs[currentAppIconColorIndex])
            val title = getAppLauncherName()
            val color = baseConfig.primaryColor

            val description = ActivityManager.TaskDescription(title, recentsIcon, color)
            setTaskDescription(description)
        }
    }

    fun updateMenuItemColors(
        menu: Menu?,
        baseColor: Int = getProperStatusBarColor(),
        forceWhiteIcons: Boolean = false
    ) {
        if (menu == null) {
            return
        }

        var color = baseColor.getContrastColor()
        if (forceWhiteIcons) {
            color = Color.WHITE
        }

        for (i in 0 until menu.size) {
            try {
                menu[i].icon?.setTint(color)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun getCurrentAppIconColorIndex(): Int {
        val appIconColor = baseConfig.appIconColor
        getAppIconColors().forEachIndexed { index, color ->
            if (color == appIconColor) {
                return index
            }
        }
        return 0
    }

    fun launchCustomizeNotificationsIntent() {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun launchChangeAppLanguageIntent() {
        try {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                startActivity(this)
            }
        } catch (e: Exception) {
            openDeviceSettings()
        }
    }

    fun handlePermission(
        permissionId: Int,
        callback: (granted: Boolean) -> Unit
    ) {
        actionOnPermission = null
        if (hasPermission(permissionId)) {
            callback(true)
        } else {
            isAskingPermissions = true
            actionOnPermission = callback
            ActivityCompat.requestPermissions(
                this,
                arrayOf(getPermissionString(permissionId)),
                GENERIC_PERM_HANDLER
            )
        }
    }

    fun handlePartialMediaPermissions(
        permissionIds: Collection<Int>,
        force: Boolean = false,
        callback: (granted: Boolean) -> Unit
    ) {
        actionOnPermission = null
        if (isUpsideDownCakePlus()) {
            if (hasPermission(PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED) && !force) {
                callback(true)
            } else {
                isAskingPermissions = true
                actionOnPermission = callback
                ActivityCompat.requestPermissions(
                    this,
                    permissionIds.map { getPermissionString(it) }.toTypedArray(),
                    GENERIC_PERM_HANDLER
                )
            }
        } else {
            if (hasAllPermissions(permissionIds)) {
                callback(true)
            } else {
                isAskingPermissions = true
                actionOnPermission = callback
                ActivityCompat.requestPermissions(
                    this,
                    permissionIds.map { getPermissionString(it) }.toTypedArray(),
                    GENERIC_PERM_HANDLER
                )
            }
        }
    }

    fun handleNotificationPermission(callback: (granted: Boolean) -> Unit) {
        if (!isTiramisuPlus()) {
            callback(true)
        } else {
            handlePermission(PERMISSION_POST_NOTIFICATIONS) { granted ->
                callback(granted)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false
        if (requestCode == GENERIC_PERM_HANDLER && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }
}
