package com.afsh.messages

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.afsh.messages.activities.AppLockActivity
import com.afsh.messages.activities.MainActivity
import org.fossify.commons.R as CommonsR
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.PROTECTION_PIN
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.MessageDigest

/**
 * App lock checks: verify that the Compose lock screen appears when the app is locked and that
 * correct credentials unlock it. Tests PIN SHA-1 compatibility with Fossify.
 */
@RunWith(AndroidJUnit4::class)
class AppLockChecks {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<App>()
    private val config = context.baseConfig
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var lockScenario: ActivityScenario<AppLockActivity>? = null

    @Before
    fun setUp() {
        device.waitForIdle()
        config.isAppPasswordProtectionOn = false
        config.appPasswordHash = ""
        config.appProtectionType = PROTECTION_PIN
        config.passwordRetryCount = 0
        config.passwordCountdownStartMs = 0
        context.appLockManager.unlock()
    }

    @After
    fun tearDown() {
        lockScenario?.close()
        lockScenario = null
        config.isAppPasswordProtectionOn = false
        config.appPasswordHash = ""
        config.passwordRetryCount = 0
        config.passwordCountdownStartMs = 0
        context.appLockManager.unlock()
    }

    @Test
    fun lockedAppShowsLockScreen() {
        enableLock("1234")

        // Launching MainActivity while locked should surface AppLockActivity via App lifecycle.
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()

        val enterPin = context.getString(CommonsR.string.enter_pin)
        check(device.wait(Until.hasObject(By.text(enterPin)), 10_000)) {
            "Lock screen did not appear after launching MainActivity while locked"
        }
    }

    @Test
    fun correctPinUnlocksApp() {
        val pin = "5678"
        enableLock(pin)

        // Launch the lock activity only after lock is armed so it does not finish() immediately.
        lockScenario = ActivityScenario.launch(AppLockActivity::class.java)
        composeTestRule.waitForIdle()

        val enterPin = context.getString(CommonsR.string.enter_pin)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeTestRule.onNodeWithText(enterPin).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithText(enterPin).assertIsDisplayed()

        pin.forEach { digit ->
            composeTestRule.onNodeWithText(digit.toString()).performClick()
        }
        composeTestRule.onNodeWithContentDescription(context.getString(android.R.string.ok)).performClick()
        composeTestRule.waitForIdle()
        device.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            !context.appLockManager.isLocked()
        }
        assert(!context.appLockManager.isLocked()) { "App should be unlocked after correct PIN" }
    }

    private fun enableLock(pin: String) {
        config.isAppPasswordProtectionOn = true
        config.appPasswordHash = hashPin(pin)
        config.appProtectionType = PROTECTION_PIN
        context.appLockManager.lock()
    }

    /** SHA-1 hash compatible with Fossify PinTab. Hex lowercase, matching stored hashes. */
    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(pin.toByteArray())
        val digest = md.digest()
        val bigInt = BigInteger(1, digest)
        return String.format("%0${digest.size * 2}x", bigInt).lowercase()
    }
}
