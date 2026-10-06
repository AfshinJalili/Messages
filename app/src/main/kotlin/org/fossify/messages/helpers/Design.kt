package org.fossify.messages.helpers

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.messages.R

/** Float resources work on API 26 too, unlike Resources.getFloat(). */
fun Resources.designFloat(@DimenRes token: Int): Float =
    TypedValue().also { getValue(token, it, true) }.float

fun Context.cobaltColorFor(background: Int): Int = ContextCompat.getColor(
    this,
    if (ColorUtils.calculateLuminance(background) > 0.5) R.color.brand_cobalt else R.color.brand_cobalt_dark
)

fun Context.tonalSurfaceColor(): Int = ColorUtils.compositeColors(
    getProperTextColor().adjustAlpha(resources.designFloat(R.dimen.opacity_surface_tint)),
    getProperBackgroundColor()
)

/** Commons' fixed palette falls back to green for custom colors. */
fun Context.cobaltTheme(): Int? {
    if (isDynamicTheme()) return null
    val primary = getProperPrimaryColor()
    if (primary != ContextCompat.getColor(this, R.color.brand_cobalt) &&
        primary != ContextCompat.getColor(this, R.color.brand_cobalt_dark)
    ) return null
    return if (ColorUtils.calculateLuminance(getProperBackgroundColor()) > 0.5) {
        R.style.AppTheme_Cobalt_Light
    } else {
        R.style.AppTheme_Cobalt_Dark
    }
}
