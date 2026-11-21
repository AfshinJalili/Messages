package com.afsh.messages

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario

/** Reads [block] from the activity on the main thread. */
fun <A : Activity, T> ActivityScenario<A>.read(block: (A) -> T): T {
    var result: Result<T>? = null
    onActivity { result = runCatching { block(it) } }
    return checkNotNull(result).getOrThrow()
}

/** Polls [condition] on the activity until it holds, failing with [message] after [timeoutMs]. */
fun <A : Activity> ActivityScenario<A>.awaitUntil(message: String, timeoutMs: Long = 5_000, condition: (A) -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (!read(condition)) {
        check(SystemClock.elapsedRealtime() < deadline) { message }
        SystemClock.sleep(POLL_MS)
    }
}

/** Keeps a test-only activity on screen over the lock screen, so text is laid out and drawn. */
fun Activity.keepOnScreen() = window.addFlags(
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
)

private const val POLL_MS = 50L
