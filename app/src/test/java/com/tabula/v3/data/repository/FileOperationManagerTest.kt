package com.tabula.v3.data.repository

import android.Manifest
import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FileOperationManagerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: QueryProvider
    private val uri = Uri.parse("content://media/external/images/media/1")

    class QueryProvider : ContentProvider() {
        var mode = "empty"
        var beforeReturn: () -> Unit = {}
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            beforeReturn()
            if (mode == "null") return null
            if (mode == "permission") throw SecurityException("Denied")
            if (mode == "service") throw IllegalStateException("Unavailable")
            return MatrixCursor(arrayOf("_id")).apply {
                if (mode == "present") addRow(arrayOf(1L))
            }
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Before fun setup() {
        provider = QueryProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    }

    private fun manager(available: Boolean = true) = FileOperationManager(context,
        createDeleteRequest = { _, _ ->
            PendingIntent.getBroadcast(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE).intentSender
        }, volumeAvailable = { available })

    @Test fun fullAccessAndAvailableVolumeCanConfirmMissing() = runBlocking {
        assertEquals(setOf(uri), manager().verifyDeletionState(listOf(uri)).deleted)
    }

    @Test fun selectedAccessEmptyQueryRemainsUnknownAfterAuthorization() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val result = manager().verifyDeletionState(listOf(uri))
        assertTrue(result.deleted.isEmpty())
        assertEquals(setOf(uri), result.unknown)
    }

    @Test fun unavailableVolumeCannotConfirmMissing() = runBlocking {
        assertEquals(setOf(uri), manager(false).verifyDeletionState(listOf(uri)).unknown)
    }

    @Test fun permissionRevokedDuringQueryCannotConfirmMissing() = runBlocking {
        provider.beforeReturn = { shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES) }
        assertEquals(setOf(uri), manager().verifyDeletionState(listOf(uri)).unknown)
    }

    @Test fun nullOrFailedQueriesRemainUnknown() = runBlocking {
        for (mode in listOf("null", "permission", "service")) {
            provider.mode = mode
            val result = manager().verifyDeletionState(listOf(uri))
            assertTrue(result.deleted.isEmpty())
            assertEquals(setOf(uri), result.unknown)
        }
    }

    @Test fun visiblePhotoRemainsPresentWithPartialAccess() = runBlocking {
        provider.mode = "present"
        shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        assertEquals(setOf(uri), manager().verifyDeletionState(listOf(uri)).existing)
    }

    @Test fun actualServiceErrorsMapToNonSplittableFailures() = runBlocking {
        provider.mode = "present"
        for (exception in listOf(SecurityException("Denied"), IllegalStateException("Unavailable"))) {
            val service = FileOperationManager(context, createDeleteRequest = { _, _ -> throw exception },
                volumeAvailable = { true })
            val error = service.deleteImages(listOf(uri)) as FileOperationManager.DeleteResult.Error
            assertFalse(error.asBatchFailure().canSplit)
            assertEquals(if (exception is SecurityException) DeleteFailureKind.PERMISSION else DeleteFailureKind.SERVICE, error.kind)
        }
    }

    @Test fun rejectedRequestCanBeSplitButCancellationPropagates() = runBlocking {
        provider.mode = "present"
        val invalid = FileOperationManager(context,
            createDeleteRequest = { _, _ -> throw IllegalArgumentException("Invalid URI") }, volumeAvailable = { true })
        assertTrue((invalid.deleteImages(listOf(uri)) as FileOperationManager.DeleteResult.Error).asBatchFailure().canSplit)
        val cancelled = FileOperationManager(context,
            createDeleteRequest = { _, _ -> throw CancellationException("Cancelled") }, volumeAvailable = { true })
        try { cancelled.deleteImages(listOf(uri)); fail("Must propagate cancellation") }
        catch (expected: CancellationException) { }
    }

    @Test fun mergedExternalVolumeDoesNotTreatEjectedVolumeAsMissing() {
        assertFalse(canConfirmMissingOnVolume("external", true, setOf("external_primary"), setOf("removed-sd")))
        assertTrue(canConfirmMissingOnVolume("external", true, setOf("external_primary"), emptySet()))
        assertFalse(canConfirmMissingOnVolume("external_primary", false, emptySet(), emptySet()))
        assertFalse(canConfirmMissingOnVolume("sd-volume", true, setOf("external_primary"), emptySet()))
    }

    private fun managerWithMountedRecentOverlap() = FileOperationManager(context,
        createDeleteRequest = { _, _ -> error("Reconciliation must never issue another deletion") },
        volumeAvailable = {
            canConfirmMissingOnVolume(it.pathSegments.firstOrNull(), true,
                setOf("external_primary"), setOf("external_primary"))
        })

    @Test fun emptyCursorWithMountedRecentOverlapIsConfirmedMissing() = runBlocking {
        val state = managerWithMountedRecentOverlap().verifyDeletionState(listOf(uri))
        assertEquals(setOf(uri), state.deleted)
        assertTrue(state.unknown.isEmpty())
    }

    @Test fun oldPendingDeletedEntryIsReconciledWhileNewEntryIsRetained() = runBlocking {
        val records = MemoryRecycleBinStorage()
        val journal = MemoryRecycleBinStorage()
        val manager = RecycleBinManager(records, journal)
        val old = fixturePhoto(1)
        val newlyAdded = fixturePhoto(2)
        manager.addAllToRecycleBin(listOf(old, newlyAdded))
        manager.beginDeletion(listOf(old.uri))
        manager.finishDeletion(emptySet(), setOf(old.uri)) // Previous version left this unknown.
        val verifier = managerWithMountedRecentOverlap()
        val recovered = reconcileRecycleBinDeletion(manager, verify = verifier::verifyDeletionState)
        assertEquals(setOf(old.uri), recovered.deleted)
        assertTrue(recovered.unknown.isEmpty())
        assertTrue(manager.pendingDeletionUris().isEmpty())
        assertEquals(listOf(newlyAdded.id), manager.loadRecycleBin().map { it.id })
        provider.mode = "present"
        assertEquals(setOf(newlyAdded.uri), verifier.verifyDeletionState(listOf(newlyAdded.uri)).existing)
        assertTrue(reconcileRecycleBinDeletion(manager, verify = verifier::verifyDeletionState).deleted.isEmpty())
    }
}
