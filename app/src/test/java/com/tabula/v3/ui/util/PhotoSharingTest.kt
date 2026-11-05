package com.tabula.v3.ui.util

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.ui.components.CardShareState
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMimeTypeMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PhotoSharingTest {
    @Before fun registerAndroidPhotoFormats() {
        // Robolectric's map starts empty instead of loading Android's MIME registry.
        val mimeTypes = Shadow.extract<ShadowMimeTypeMap>(MimeTypeMap.getSingleton())
        mimeTypes.addExtensionMimeTypeMapping("png", "image/png")
        mimeTypes.addExtensionMimeTypeMapping("jpg", "image/jpeg")
    }

    private fun photo(id: Long, name: String = "photo.jpg") = ImageFile(
        id, Uri.parse("content://media/external/images/media/$id"), name,
        0L, 100L, 100, 100, null
    )

    @Test fun sharesTheOriginalPhotoWithMatchingClipDataAndReadAccess() {
        val image = photo(7)
        val intent = buildPhotoShareIntent(image, "image/jpeg")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(image.uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(1, intent.clipData!!.itemCount)
        assertEquals(image.uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(intent.`package`)
    }

    @Test fun preservesReportedPhotoFormat() {
        assertEquals("image/heic", photoMimeType("image/heic", "photo.jpg"))
        assertEquals("image/gif", photoMimeType("image/gif", "photo.jpg"))
    }

    @Test fun infersFormatWhenProviderTypeIsMissingOrGeneric() {
        assertEquals("image/png", photoMimeType(null, "PHOTO.PNG"))
        assertEquals("image/jpeg", photoMimeType("application/octet-stream", "photo.jpg"))
        assertEquals("image/*", photoMimeType(null, "photo.unknown"))
    }

    @Test fun doesNotShareWhileGestureStartsBeforeButtonRecomposes() {
        val image = photo(1)
        var busy = false
        val state = CardShareState()
        val resolve = { if (busy) null else image }
        state.update(resolve)
        assertTrue(state.isAvailable)
        busy = true
        assertNull(state.snapshot())
        state.update(resolve)
        assertFalse(state.isAvailable)
        busy = false
        state.update(resolve)
        assertTrue(state.isAvailable)
        assertEquals(image, state.snapshot())
    }

    @Test fun snapshotKeepsClickedPhotoWhileStackMovesToNextPhoto() {
        val first = photo(1)
        val second = photo(2)
        var current = first
        val state = CardShareState()
        state.update { current }
        val snapshot = state.snapshot()!!
        current = second
        val intent = buildPhotoShareIntent(snapshot, "image/jpeg")
        assertEquals(first.uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(second, state.snapshot())
    }

    @Test fun leavingTheStackClearsItsShareTarget() {
        val state = CardShareState()
        state.update { photo(1) }
        state.clear()
        assertFalse(state.isAvailable)
        assertNull(state.snapshot())
    }
}
