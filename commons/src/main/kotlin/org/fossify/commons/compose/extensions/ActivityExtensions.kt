package org.fossify.commons.compose.extensions

import androidx.activity.ComponentActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.isAppInstalledOnSDCard

fun ComponentActivity.appOnSdCardCheckCompose(
    showConfirmationDialog: () -> Unit
) {
    if (!baseConfig.wasAppOnSDShown && isAppInstalledOnSDCard()) {
        baseConfig.wasAppOnSDShown = true
        showConfirmationDialog()
    }
}
