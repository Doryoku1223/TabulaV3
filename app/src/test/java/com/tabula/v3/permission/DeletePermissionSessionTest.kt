package com.tabula.v3.permission

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DeletePermissionSessionTest {
    @Test fun restoredSessionWaitsForSystemAndTriggersJournalRecovery() {
        val old = DeletePermissionSession(null)
        var oldCallback = false
        old.launch({ oldCallback = true }, {})
        val state = Bundle()
        old.save(state)
        val restored = DeletePermissionSession(state)
        assertTrue(restored.inFlight)
        var secondGranted = true
        var secondLaunches = 0
        restored.launch({ secondGranted = it }, { secondLaunches++ })
        assertFalse(secondGranted)
        assertEquals(0, secondLaunches)
        restored.complete(true)
        assertFalse(restored.inFlight)
        assertEquals(1, restored.recoveryRevision)
        assertFalse(oldCallback)
    }

    @Test fun ordinaryResultClearsSlotBeforeCallbackAndDoesNotTriggerRecovery() {
        val session = DeletePermissionSession(null)
        var called = 0
        session.launch({ assertFalse(session.inFlight); called++ }, {})
        session.complete(false)
        assertEquals(1, called)
        assertEquals(0, session.recoveryRevision)
        session.launch({}, {})
        assertTrue(session.inFlight)
    }

    @Test fun failedLauncherDoesNotLeaveBusyState() {
        val session = DeletePermissionSession(null)
        try { session.launch({}, { throw IllegalStateException("No launcher") }); fail("Expected error") }
        catch (expected: IllegalStateException) { }
        assertFalse(session.inFlight)
        session.launch({}, {})
        assertTrue(session.inFlight)
    }
}
