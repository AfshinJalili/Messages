package com.afsh.messages.helpers

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.adjustAlpha
import com.afsh.messages.R
import com.afsh.messages.ui.afshColorScheme
import com.afsh.messages.ui.afshDark

/** Float resources work on API 26 too, unlike Resources.getFloat(). */
fun Resources.designFloat(@DimenRes token: Int): Float =
    TypedValue().also { getValue(token, it, true) }.float

/** The theme's primary role, for what cannot read Compose: Commons' stored accent, notifications. */
fun Context.primaryRoleColor(dark: Boolean = afshDark()): Int = afshColorScheme(dark).primary.toArgb()

fun Context.tonalSurfaceColor(): Int = ColorUtils.compositeColors(
    getProperTextColor().adjustAlpha(resources.designFloat(R.dimen.opacity_surface_tint)),
    getProperBackgroundColor()
)

/** The XML theme for the current light or dark choice; it mirrors the Compose roles. */
fun Context.afshXmlTheme(): Int = if (afshDark()) R.style.AppTheme_Afsh_Dark else R.style.AppTheme_Afsh_Light
