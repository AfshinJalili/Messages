package org.fossify.messages

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.messages.activities.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/** Launches top-level screens and captures them as QA evidence. */
@RunWith(AndroidJUnit4::class)
class ScreenTourChecks {
    @Test
    fun inboxLaunchesAndIsCaptured() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { check(!it.isFinishing) { "Inbox closed on launch" } }
            check(Screenshots.capture("inbox").length() > 0)
        }
    }
}
