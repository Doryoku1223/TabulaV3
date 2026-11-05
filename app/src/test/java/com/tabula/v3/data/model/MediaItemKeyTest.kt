package com.tabula.v3.data.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MediaItemKeyTest {
    @Test
    fun parsesStandardImagesMediaUri() {
        val key = mediaItemKeyFromUri(Uri.parse("content://media/external_primary/images/media/10"))

        assertEquals(MediaItemKey("external_primary", 10L), key)
    }

    @Test
    fun parsesVolumeSpecificFilesCollectionUri() {
        val key = mediaItemKeyFromUri(Uri.parse("content://media/external_primary/files/10"))

        assertEquals(MediaItemKey("external_primary", 10L), key)
    }

    @Test
    fun aggregateExternalVolumeIsNotAnIdentity() {
        assertNull(mediaItemKeyFromUri(Uri.parse("content://media/external/images/media/10")))
    }

    @Test
    fun unresolvableUrisReturnNullRatherThanGuessing() {
        assertNull(mediaItemKeyFromUri(Uri.parse("content://com.android.providers.media.documents/document/image:10")))
        assertNull(mediaItemKeyFromUri(Uri.parse("content://media/external_primary/images")))
        assertNull(mediaItemKeyFromUri(Uri.parse("file:///sdcard/DCIM/a.jpg")))
        assertNull(mediaItemKeyFromUri(Uri.parse("content://media/external_primary/images/media/abc")))
    }

    @Test
    fun sameVolumeAndIdAreEqualAcrossCollectionSpellings() {
        assertEquals(
            mediaItemKeyFromUri(Uri.parse("content://media/external_primary/images/media/10")),
            mediaItemKeyFromUri(Uri.parse("content://media/external_primary/files/10"))
        )
        assertEquals(
            MediaItemKey("external_primary", 10L).hashCode(),
            mediaItemKeyFromUri(Uri.parse("content://media/external_primary/images/media/10"))!!.hashCode()
        )
    }
}
