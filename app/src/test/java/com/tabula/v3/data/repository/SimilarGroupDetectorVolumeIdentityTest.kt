package com.tabula.v3.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.cache.toHashKey
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.function.Supplier

/**
 * C1/T03-HASHVOLUME：相似组检测的哈希计算/缓存按完整媒体身份（卷|ID）隔离。
 * 不同卷的同数值 ID 行各自计算、各自缓存；旧行为按裸数值 ID 折叠——第二卷行
 * 复用第一卷的哈希（缓存级基线反例见 ImageHashCacheVersionTest，本用例钉死
 * 迁移后检测器映射与缓存条目的身份独立性）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SimilarGroupDetectorVolumeIdentityTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app
    private lateinit var uriPrimary: Uri
    private lateinit var uriSecondary: Uri
    private lateinit var filePrimary: File
    private lateinit var fileSecondary: File

    @Before
    fun setUp() {
        filePrimary = File(context.cacheDir, "hash-vol-primary.jpg")
        fileSecondary = File(context.cacheDir, "hash-vol-secondary.jpg")
        uriPrimary = Uri.parse("content://media/external_primary/images/media/10")
        uriSecondary = Uri.parse("content://media/external_secondary/images/media/10")
        writeGradientJpeg(filePrimary, leftToRight = true)
        writeGradientJpeg(fileSecondary, leftToRight = false)
        val streamResolver = shadowOf(context.contentResolver)
        streamResolver.registerInputStreamSupplier(uriPrimary, Supplier { FileInputStream(filePrimary) })
        streamResolver.registerInputStreamSupplier(uriSecondary, Supplier { FileInputStream(fileSecondary) })
        ImageHashCache.getInstance(context).clearAll()
    }

    /** 灰度横向渐变；leftToRight=false 时方向反转，dHash 与正向近似全位不同。 */
    private fun writeGradientJpeg(target: File, leftToRight: Boolean) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                val v = (if (leftToRight) x else 63 - x) * 4
                bitmap.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "JPEG compress failed" } }
        bitmap.recycle()
    }

    private fun row(volume: String, uri: Uri, dateModified: Long) = ImageFile(
        id = 10L,
        uri = uri,
        displayName = "img-$volume.jpg",
        dateModified = dateModified,
        size = 4096L,
        width = 64,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = volume
    )

    @Test
    fun sameIdRowsOnDifferentVolumesComputeAndCacheIndependently() = runBlocking {
        val hashCache = ImageHashCache(context)
        val detector = SimilarGroupDetector(context, hashCache)
        val primary = row("external_primary", uriPrimary, dateModified = 1000L)
        val secondary = row("external_secondary", uriSecondary, dateModified = 2000L)

        val hashes = detector.ensureHashesComputed(listOf(primary, secondary))

        assertEquals(
            "两卷同数值 ID 行必须各自持有身份键（不得折叠为一条）",
            2, hashes.size
        )
        val primaryHash = hashes[primary.toHashKey()]
        val secondaryHash = hashes[secondary.toHashKey()]
        assertTrue("主卷行必须计算成功", primaryHash != null)
        assertTrue("第二卷行必须计算成功", secondaryHash != null)
        assertNotEquals(
            "不同内容（正反渐变）的跨卷同 ID 行哈希必须不同",
            primaryHash, secondaryHash
        )

        // 缓存按身份独立可读，且内容版本各自生效（版本键不互踢）。
        assertEquals(primaryHash, hashCache.getHash(primary.toHashKey(), contentVersion = 1000L)!!.hash)
        assertEquals(secondaryHash, hashCache.getHash(secondary.toHashKey(), contentVersion = 2000L)!!.hash)

        // 清理按身份判定：有效集合只含主卷时，第二卷条目被清、主卷保留。
        hashCache.cleanupStaleHashes(validKeys = setOf(primary.toHashKey()))
        assertTrue(hashCache.getHash(primary.toHashKey(), contentVersion = 1000L) != null)
        assertNull(hashCache.getHash(secondary.toHashKey(), contentVersion = 2000L))
    }
}
