package org.fossify.messages

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.messages.ui.firstrun.FirstRunEvent
import org.fossify.messages.ui.firstrun.FirstRunScreen
import org.fossify.messages.ui.firstrun.FirstRunStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 32 and 33 (55 and 56 in Persian): each leads into Android's prompt, and captures of each variant. */
@RunWith(AndroidJUnit4::class)
class FirstRunChecks {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun eachStepAsksForWhatItExplains() {
        val events = mutableListOf<FirstRunEvent>()
        var step by mutableStateOf(FirstRunStep.DEFAULT_APP)
        compose.setContent { DesignVariantContent(DesignVariant.LIGHT) { FirstRunScreen(step, { events += it }) } }
        compose.onNodeWithText(text(R.string.set_as_default_sms_app)).performScrollTo().performClick()
        step = FirstRunStep.PERMISSIONS
        compose.onNodeWithText(text(R.string.continue_action)).performScrollTo().performClick()
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        check(events == listOf(FirstRunEvent.BecomeDefault, FirstRunEvent.Continue, FirstRunEvent.Back)) { "Events $events" }
    }

    @Test
    fun firstRunIsCapturedInEveryVariant() {
        var variant by mutableStateOf(DesignVariant.LIGHT)
        var step by mutableStateOf(FirstRunStep.DEFAULT_APP)
        compose.setContent { DesignVariantContent(variant) { FirstRunScreen(step, {}) } }
        listOf(FirstRunStep.DEFAULT_APP to "32-default-sms", FirstRunStep.PERMISSIONS to "33-permissions").forEach { (shown, name) ->
            step = shown
            DesignVariant.entries.forEach {
                variant = it
                compose.waitForIdle()
                Screenshots.capture(name + it.suffix)
            }
        }
    }
}
