package com.afsh.messages

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.afsh.messages.activities.MainActivity
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

    @Before
    fun setUp() {
        device.waitForIdle()
        // Start with lock disabled
        config.isAppPasswordProtectionOn = false
        config.appPasswordHash = ""
        config.appProtectionType = PROTECTION_PIN
        config.passwordRetryCount = 0
        config.passwordCountdownStartMs = 0
        context.appLockManager.unlock()
    }

    @After
    fun tearDown() {
        // Clean up: disable lock
        config.isAppPasswordProtectionOn = false
        config.appPasswordHash = ""
        config.passwordRetryCount = 0
        config.passwordCountdownStartMs = 0
        context.appLockManager.unlock()
    }

    @Test
    fun lockedAppShowsLockScreen() {
        // Given: app lock enabled with PIN "1234"
        val pin = "1234"
        val hash = hashPin(pin)
        config.isAppPasswordProtectionOn = true
        config.appPasswordHash = hash
        config.appProtectionType = PROTECTION_PIN
        context.appLockManager.lock()

        // When: launching MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()

        // Then: lock screen is shown (check for "Enter PIN" text)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.enter_pin)).assertIsDisplayed()
    }

    @Test
    fun correctPinUnlocksApp() {
        // Given: app lock enabled with PIN "5678"
        val pin = "5678"
        val hash = hashPin(pin)
        config.isAppPasswordProtectionOn = true
        config.appPasswordHash = hash
        config.appProtectionType = PROTECTION_PIN
        context.appLockManager.lock()

        // When: launching MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()

        // Then: lock screen appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText(context.getString(R.string.enter_pin)).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // When: entering correct PIN
        pin.forEach { digit ->
            composeTestRule.onNodeWithText(digit.toString()).performClick()
        }
        composeTestRule.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        device.waitForIdle()

        // Then: app is unlocked and MainActivity content is visible
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            !context.appLockManager.isLocked()
        }
        assert(!context.appLockManager.isLocked()) { "App should be unlocked after correct PIN" }
    }

    /**
     * SHA-1 hash compatible with Fossify PinTab. Hex format lowercase, matching existing stored hashes.
     */
    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(pin.toByteArray())
        val digest = md.digest()
        val bigInt = BigInteger(1, digest)
        return String.format("%0${digest.size * 2}x", bigInt).lowercase()
    }
}
