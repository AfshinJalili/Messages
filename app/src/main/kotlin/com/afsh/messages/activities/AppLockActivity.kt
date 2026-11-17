package com.afsh.messages.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.afsh.messages.ui.Afsh
import com.afsh.messages.ui.AfshSystemBars
import com.afsh.messages.ui.AfshTheme
import com.afsh.messages.ui.afshDark
import com.afsh.messages.ui.applock.SecuritySetupDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.baseConfig

/**
 * Lock screen that blocks app access when app lock is enabled. Shows PIN/pattern/biometric verification.
 * Unlocks on success; exits app on back press. Extends [FragmentActivity] so BiometricPrompt can host.
 */
class AppLockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back press should lock and exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                appLockManager.lock()
                finishAffinity()
            }
        })

        setContent {
            AfshTheme(dark = afshDark()) {
                AfshSystemBars(window)
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val config = baseConfig

                    LaunchedEffect(Unit) {
                        if (!appLockManager.isLocked()) {
                            finish()
                        }
                    }

                    Box(Modifier.fillMaxSize().systemBarsPadding().padding(Afsh.spacing.xl), contentAlignment = Alignment.Center) {
                        SecuritySetupDialog(
                            requiredHash = config.appPasswordHash,
                            showTabIndex = config.appProtectionType,
                            onDismiss = {
                                appLockManager.lock()
                                finishAffinity()
                            },
                            onResult = { _, _, success ->
                                if (success) {
                                    appLockManager.unlock()
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!appLockManager.isLocked()) {
            finish()
        }
    }

    companion object {
        fun launch(activity: Activity, requestCode: Int) {
            if (activity.appLockManager.isLocked()) {
                Intent(activity, AppLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    activity.startActivityForResult(this, requestCode)
                }
            }
        }
    }
}
