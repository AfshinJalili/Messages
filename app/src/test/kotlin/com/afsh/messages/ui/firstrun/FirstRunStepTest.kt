package com.afsh.messages.ui.firstrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstRunStepTest {

    @Test
    fun `an app that is not the default SMS app asks to become it`() {
        assertEquals(FirstRunStep.DEFAULT_APP, firstRunStep(isDefaultSmsApp = false, smsPermissionsHeld = false))
        assertEquals(FirstRunStep.DEFAULT_APP, firstRunStep(isDefaultSmsApp = false, smsPermissionsHeld = true))
    }

    @Test
    fun `the default SMS app without SMS access explains the permissions`() {
        assertEquals(FirstRunStep.PERMISSIONS, firstRunStep(isDefaultSmsApp = true, smsPermissionsHeld = false))
    }

    @Test
    fun `the default SMS app that holds SMS access skips the permissions screen`() {
        // Android 10+ grants SMS access with the SMS role, so only the system prompts remain.
        assertNull(firstRunStep(isDefaultSmsApp = true, smsPermissionsHeld = true))
    }
}
