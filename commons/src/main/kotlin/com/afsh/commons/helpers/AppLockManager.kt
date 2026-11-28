package com.afsh.commons.helpers

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.afsh.commons.extensions.baseConfig

class AppLockManager(
    private val context: Application,
    private val config: BaseConfig = context.baseConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {

    @Volatile
    private var isLocked: Boolean = config.isAppPasswordProtectionOn

    @Volatile
    private var shouldUpdateLockState: Boolean = true

    fun lock() {
        isLocked = true
        resetUnlockTimestamp(0L)
    }

    fun unlock() {
        isLocked = false
        resetUnlockTimestamp()
    }

    /** Decides whether the app is locked now, and records that decision: call it where the lock screen may open. */
    fun isLocked(): Boolean {
        updateLockState()
        return isLocked
    }

    /**
     * What [isLocked] would answer, without recording anything. For readers that only observe, such as
     * App Presence on a receiver thread: they must not lock the app while the user is in it.
     */
    fun isLockedNow(): Boolean {
        if (!config.isAppPasswordProtectionOn) return false
        return isLocked || shouldUpdateLockState && isUnlockExpired(clock())
    }

    override fun onResume(owner: LifecycleOwner) = updateLockState()

    override fun onStop(owner: LifecycleOwner) {
        shouldUpdateLockState = true
        if (!isLocked) {
            resetUnlockTimestamp()
        }
    }

    private fun updateLockState() {
        if (config.isAppPasswordProtectionOn) {
            val now = clock()
            if (isUnlockExpired(now) && shouldUpdateLockState) {
                isLocked = true
            } else {
                resetUnlockTimestamp(now)
            }
            // Back within the timeout, or locked: either way this return is decided. Only the next
            // stop may lock the app, not time spent reading in it.
            shouldUpdateLockState = false
        } else {
            isLocked = false
        }
    }

    private fun isUnlockExpired(now: Long) = now - config.lastUnlockTimestampMs > config.unlockTimeoutDurationMs

    private fun resetUnlockTimestamp(timestamp: Long = clock()) {
        config.lastUnlockTimestampMs = timestamp
    }

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun getInstance(appContext: Application): AppLockManager {
            return instance ?: synchronized(this) {
                instance ?: AppLockManager(appContext).also { instance = it }
            }
        }
    }
}

