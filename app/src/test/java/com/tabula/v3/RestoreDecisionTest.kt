package com.tabula.v3

import android.net.Uri
import com.tabula.v3.data.repository.DeletionVerification
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RestoreDecisionTest {
    private val uri: Uri = Uri.parse("content://media/external/images/media/10")

    @Test
    fun unknownVerificationKeepsRecordInRecycleBin() {
        val verification = DeletionVerification(deleted = emptySet(), existing = emptySet(), unknown = setOf(uri))

        assertEquals(RestoreDecision.KEEP_UNKNOWN, decideRestoreAction(uri, verification))
    }

    @Test
    fun missingVerificationDropsDeadRecordWithoutTouchingGallery() {
        val verification = DeletionVerification(deleted = setOf(uri), existing = emptySet(), unknown = emptySet())

        assertEquals(RestoreDecision.DROP_MISSING, decideRestoreAction(uri, verification))
    }

    @Test
    fun presentVerificationProceedsWithRestore() {
        val verification = DeletionVerification(deleted = emptySet(), existing = setOf(uri), unknown = emptySet())

        assertEquals(RestoreDecision.PROCEED_RESTORE, decideRestoreAction(uri, verification))
    }
}
