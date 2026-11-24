package com.afsh.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxRepositoryTest {

    private var dirtyChecks = 0

    private fun pass(force: Boolean = false, requested: Boolean = false, dirty: Boolean = false) =
        providerReconcilePass(force = force, requested = requested) { dirtyChecks++; dirty }

    @Test
    fun `a clean Room on resume syncs only the recent window`() {
        assertEquals(ProviderReconcilePass.RECENT_WINDOW, pass())
        assertEquals(1, dirtyChecks)
    }

    @Test
    fun `a Room that drifted from Telephony gets the full reconcile`() {
        assertEquals(ProviderReconcilePass.FULL, pass(dirty = true))
    }

    @Test
    fun `a forced or requested run gets the full reconcile without the dirty-check`() {
        assertEquals(ProviderReconcilePass.FULL, pass(force = true))
        assertEquals(ProviderReconcilePass.FULL, pass(requested = true))
        assertEquals(0, dirtyChecks)
    }
}
