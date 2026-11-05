package com.tabula.v3.ui.util

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class OwnedBitmapsTest {
    @Test
    fun scaledDownReturnsNewInstanceWithTargetSize() {
        val source = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)

        val owned = ownedScaledCopyOrNull(source, maxSize = 250)

        assertNotNull(owned)
        assertFalse(owned === source)
        assertEquals(250, owned!!.width)
        assertEquals(200, owned.height)
    }

    @Test
    fun sameSizeScaleReturnsIndependentCopy() {
        // F15：目标尺寸与源一致时 createScaledBitmap 可能返回源本身；
        // 本函数必须保证返回值与源不是同一实例，调用方才能安全 recycle。
        val source = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)

        val owned = ownedScaledCopyOrNull(source, maxSize = 250)

        assertNotNull(owned)
        assertFalse(owned === source)
        assertEquals(250, owned!!.width)
        assertEquals(250, owned.height)
    }

    @Test
    fun upscaledSourceIsCappedAtOriginalSize() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)

        val owned = ownedScaledCopyOrNull(source, maxSize = 250)

        assertNotNull(owned)
        assertEquals(100, owned!!.width)
        assertEquals(80, owned.height)
    }

    @Test
    fun recycledSourceReturnsNull() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        source.recycle()

        assertNull(ownedScaledCopyOrNull(source, maxSize = 250))
    }

    @Test
    fun copiedInstanceSurvivesSourceRecycle() {
        // 所有权契约：返回值独立于源，源被回收后返回值仍可用（像素级拷贝）。
        val source = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)

        val owned = ownedScaledCopyOrNull(source, maxSize = 250)!!
        source.recycle()

        assertTrue(!owned.isRecycled)
        assertTrue(owned.width == 250 && owned.height == 250)
    }
}
