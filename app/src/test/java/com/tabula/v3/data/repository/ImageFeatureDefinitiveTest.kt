package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ImageFeatureDefinitiveTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external_primary/images/media/10")
    private lateinit var provider: EvidenceTestProvider

    @Before
    fun setUp() {
        val sourceFile = File(context.cacheDir, "feature-source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        provider = EvidenceTestProvider(
            context,
            sourceFile,
            Uri.parse("content://media/external/images/media/10"),
            Uri.parse("content://media/external_primary/images/media/10?alias=source"),
            Uri.parse("content://media/external_primary/files/10")
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        ImageFeatureDetector.setFastMode(false)
    }

    @Test
    fun fastModeNegativeIsInconclusive() = runBlocking {
        ImageFeatureDetector.setFastMode(true)

        val features = ImageFeatureDetector.detect(context, sourceUri, checkHdr = true, checkMotion = true)

        assertFalse(features.isHdr)
        assertFalse(features.definitive)
        ImageFeatureDetector.setFastMode(false)
    }

    @Test
    fun motionOnlyAbsenceWithoutFailureIsConclusive() = runBlocking {
        // 1 字节合成文件：queryContentLength 命中但小于阈值 → 无动态片段，属可信阴性。
        val features = ImageFeatureDetector.detect(context, sourceUri, checkHdr = false, checkMotion = true)

        assertEquals(null, features.motionPhotoInfo)
        assertTrue(features.definitive)
    }
}
