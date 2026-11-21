package com.afsh.messages.ui.applock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import com.afsh.commons.extensions.baseConfig
import com.afsh.commons.helpers.DEFAULT_PASSWORD_COUNTDOWN
import com.afsh.commons.helpers.MAX_PASSWORD_RETRY_COUNT

/**
 * Lockout state for password attempts. Tracks retry count and countdown. Use [onCorrect] to reset,
 * [onIncorrect] to increment retries and start countdown when threshold is reached.
 */
class LockoutState(
    private val getRetryCount: () -> Int,
    private val setRetryCount: (Int) -> Unit,
    private val getCountdownStart: () -> Long,
    private val setCountdownStart: (Long) -> Unit,
) {
    var countdown by mutableIntStateOf(0)
        private set

    val isLockedOut: Boolean
        get() = countdown > 0

    fun onCorrect() {
        setRetryCount(0)
        setCountdownStart(0)
        countdown = 0
    }

    fun onIncorrect() {
        val count = getRetryCount() + 1
        setRetryCount(count)
        if (count >= MAX_PASSWORD_RETRY_COUNT) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        val currentMs = System.currentTimeMillis()
        val countdownStartMs = getCountdownStart()

        if (countdownStartMs == 0L) {
            setCountdownStart(currentMs)
            countdown = DEFAULT_PASSWORD_COUNTDOWN
        } else {
            val elapsedMs = currentMs - countdownStartMs
            val remainingMs = (DEFAULT_PASSWORD_COUNTDOWN * 1000L) - elapsedMs
            countdown = if (remainingMs > 0) (remainingMs / 1000).toInt() else 0
        }
    }

    fun tick() {
        if (countdown > 0) {
            countdown--
            if (countdown == 0) {
                setRetryCount(0)
                setCountdownStart(0)
            }
        }
    }
}

@Composable
fun rememberLockout(): LockoutState {
    val context = LocalContext.current
    val config = context.baseConfig

    val state = remember {
        LockoutState(
            getRetryCount = { config.passwordRetryCount },
            setRetryCount = { config.passwordRetryCount = it },
            getCountdownStart = { config.passwordCountdownStartMs },
            setCountdownStart = { config.passwordCountdownStartMs = it },
        )
    }

    // Initialize countdown on first render if already locked out
    LaunchedEffect(Unit) {
        val retryCount = config.passwordRetryCount
        if (retryCount >= MAX_PASSWORD_RETRY_COUNT) {
            state.onIncorrect() // This will compute and set the initial countdown
        }
    }

    // Countdown timer
    LaunchedEffect(state.countdown) {
        if (state.countdown > 0) {
            while (state.countdown > 0) {
                delay(1000)
                state.tick()
            }
        }
    }

    return state
}
