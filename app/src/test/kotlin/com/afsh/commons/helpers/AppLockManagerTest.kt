package com.afsh.commons.helpers

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.afsh.messages.receivers.PreferencesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** App Lock's state machine; App Presence reads it on receiver threads through [AppLockManager.isLockedNow]. */
class AppLockManagerTest {
    private var now = 1_000_000L
    private val config = BaseConfig(PreferencesContext()).apply {
        isAppPasswordProtectionOn = true
        unlockTimeoutDurationMs = TIMEOUT
    }
    private val manager = AppLockManager(Application(), config, clock = { now })
    private val process = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
    }

    private fun unlockedInForeground() {
        manager.onResume(process)
        manager.unlock()
    }

    @Test
    fun `reading past the timeout after a quick return does not lock the app`() {
        unlockedInForeground()
        manager.onStop(process)
        now += 5_000
        manager.onResume(process)

        now += TIMEOUT + 10_000
        assertFalse(manager.isLockedNow())
        assertFalse(manager.isLocked())
    }

    @Test
    fun `returning after the timeout locks the app`() {
        unlockedInForeground()
        manager.onStop(process)
        now += TIMEOUT + 1

        assertTrue(manager.isLockedNow())
        manager.onResume(process)
        assertTrue(manager.isLocked())
    }

    @Test
    fun `reading the lock state for App Presence changes nothing`() {
        unlockedInForeground()
        val unlockedAt = config.lastUnlockTimestampMs
        now += 10_000

        assertFalse(manager.isLockedNow())
        assertEquals(unlockedAt, config.lastUnlockTimestampMs)
    }

    @Test
    fun `the app is never locked with App Lock off`() {
        config.isAppPasswordProtectionOn = false
        manager.onStop(process)
        now += TIMEOUT * 10

        assertFalse(manager.isLockedNow())
        assertFalse(manager.isLocked())
    }

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
