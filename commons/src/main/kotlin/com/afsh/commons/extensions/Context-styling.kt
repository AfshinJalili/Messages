package com.afsh.commons.extensions

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import com.afsh.commons.R
import com.afsh.commons.helpers.FONT_TYPE_CUSTOM
import com.afsh.commons.helpers.FONT_TYPE_SYSTEM_DEFAULT
import com.afsh.commons.helpers.FontHelper
import com.afsh.commons.helpers.appIconColorStrings
import com.afsh.commons.views.MyAppCompatCheckbox
import com.afsh.commons.views.MyAppCompatSpinner
import com.afsh.commons.views.MyAutoCompleteTextView
import com.afsh.commons.views.MyButton
import com.afsh.commons.views.MyCompatRadioButton
import com.afsh.commons.views.MyEditText
import com.afsh.commons.views.MyFloatingActionButton
import com.afsh.commons.views.MyMaterialSwitch
import com.afsh.commons.views.MySeekBar
import com.afsh.commons.views.MyTextInputLayout
import com.afsh.commons.views.MyTextView

/**
 * Wallpaper / Material You dynamic colors are not used. Light/dark follows stored colors
 * (and system night mode when Appearance → System is on); values come from the app GM3 tokens.
 */
fun Context.isDynamicTheme() = false

fun Context.isBlackAndWhiteTheme(): Boolean = false

fun Context.isWhiteTheme(): Boolean = false

fun Context.isSystemInDarkMode() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES != 0

fun Context.isAutoTheme() = false

fun Context.getProperTextColor() = baseConfig.textColor

fun Context.getProperBackgroundColor() = baseConfig.backgroundColor

fun Context.getProperPrimaryColor() = baseConfig.primaryColor

fun Context.getProperStatusBarColor() = getProperBackgroundColor()

fun Context.getColoredMaterialStatusBarColor(): Int = getProperPrimaryColor()

fun Context.updateTextColors(viewGroup: ViewGroup) {
    val textColor = baseConfig.textColor
    val backgroundColor = baseConfig.backgroundColor
    val accentColor = getProperPrimaryColor()

    val cnt = viewGroup.childCount
    (0 until cnt).map { viewGroup.getChildAt(it) }.forEach {
        when (it) {
            is MyTextView -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAppCompatSpinner -> it.setColors(textColor, accentColor, backgroundColor)
            is MyCompatRadioButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAppCompatCheckbox -> it.setColors(textColor, accentColor, backgroundColor)
            is MyMaterialSwitch -> it.setColors(textColor, accentColor, backgroundColor)
            is MyEditText -> it.setColors(textColor, accentColor, backgroundColor)
            is MyAutoCompleteTextView -> it.setColors(textColor, accentColor, backgroundColor)
            is MyFloatingActionButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MySeekBar -> it.setColors(textColor, accentColor, backgroundColor)
            is MyButton -> it.setColors(textColor, accentColor, backgroundColor)
            is MyTextInputLayout -> it.setColors(textColor, accentColor, backgroundColor)
            is ViewGroup -> updateTextColors(it)
        }
    }
}

fun Context.getTimePickerDialogTheme() =
    if (baseConfig.backgroundColor.isLightColor()) R.style.MyDialogTheme else R.style.MyDialogTheme_Dark

fun Context.getDatePickerDialogTheme() = getTimePickerDialogTheme()

fun Context.getPopupMenuTheme(): Int =
    if (baseConfig.backgroundColor.isLightColor()) {
        R.style.AppTheme_PopupMenuLightStyle
    } else {
        R.style.AppTheme_PopupMenuDarkStyle
    }

fun Context.syncGlobalConfig(callback: (() -> Unit)? = null) {
    baseConfig.showCheckmarksOnSwitches = false
    validateFontSettings()
    callback?.invoke()
}

private fun Context.validateFontSettings() {
    if (baseConfig.fontType == FONT_TYPE_CUSTOM) {
        val typeface = FontHelper.getTypeface(this)
        if (typeface == Typeface.DEFAULT) {
            baseConfig.fontType = FONT_TYPE_SYSTEM_DEFAULT
            baseConfig.fontName = ""
            FontHelper.clearCache()
        }
    }
}

fun Context.checkAppIconColor() {
    val appId = baseConfig.appId
    if (appId.isNotEmpty() && baseConfig.lastIconColor != baseConfig.appIconColor) {
        getAppIconColors().forEachIndexed { index, color ->
            toggleAppIconColor(appId, index, color, false)
        }

        getAppIconColors().forEachIndexed { index, color ->
            if (baseConfig.appIconColor == color) {
                toggleAppIconColor(appId, index, color, true)
            }
        }
    }
}

fun Context.toggleAppIconColor(appId: String, colorIndex: Int, color: Int, enable: Boolean) {
    val className = "${appId.removeSuffix(".debug")}.activities.SplashActivity${appIconColorStrings[colorIndex]}"
    val state = if (enable) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED
    try {
        packageManager.setComponentEnabledSetting(ComponentName(appId, className), state, PackageManager.DONT_KILL_APP)
        if (enable) {
            baseConfig.lastIconColor = color
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

/** Launcher icon accent swatches — resolved from the app GM3 primary (single accent). */
fun Context.getAppIconColors(): ArrayList<Int> {
    val id = resources.getIdentifier("m3_primary", "color", packageName)
    val primary = if (id != 0) getColor(id) else baseConfig.primaryColor
    return arrayListOf(primary)
}

@SuppressLint("NewApi")
fun Context.getBottomNavigationBackgroundColor(): Int = baseConfig.backgroundColor

fun Context.getDialogBackgroundColor(): Int = baseConfig.backgroundColor
