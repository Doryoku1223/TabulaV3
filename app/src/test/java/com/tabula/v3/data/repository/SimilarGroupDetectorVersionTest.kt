package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
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
class SimilarGroupDetectorVersionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external/images/media/10")
    private lateinit var provider: EvidenceTestProvider
    private lateinit var hashCache: ImageHashCache
    private lateinit var detector: SimilarGroupDetector

    @Before
    fun setUp() {
        val sourceFile = File(context.cacheDir, "hash-version-source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        provider = EvidenceTestProvider(
            context,
            sourceFile,
            sourceUri,
            Uri.parse("content://media/external_primary/images/media/10?alias=source"),
            Uri.parse("content://media/external_primary/files/10")
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        hashCache = ImageHashCache(context)
        hashCache.clearAll()
        detector = SimilarGroupDetector(context, hashCache)
    }

    private fun image(dateModified: Long) = ImageFile(
        id = 10L,
        uri = sourceUri,
        displayName = "img.jpg",
        dateModified = dateModified,
        size = 3L,
        width = 1,
        height = 1,
        bucketDisplayName = "Camera"
    )

    @Test
    fun contentEditTriggersRecomputeForNewVersion() = runBlocking {
        // 旧格式成功条目（无版本键）对版本化读取视为过期 → 本轮重算并写入新版本。
        hashCache.saveHash(ImageHashCache.HashKey(null, 10L), PerceptualHash.HashResult.Success(7L))

        detector.ensureHashesComputed(listOf(image(dateModified = 2000L)))

        // 断言不依赖解码成败：只要重算发生，ver 键就会随结果写入。
        assertNotNull(hashCache.getHash(ImageHashCache.HashKey(null, 10L), contentVersion = 2000L))
    }

    @Test
    fun failedLegacyEntryIsRetriedAndVersionStamped() = runBlocking {
        // 旧行为：失败条目永不重试（"失败的不重试"），也不会获得版本键。
        hashCache.saveHash(ImageHashCache.HashKey(null, 10L), PerceptualHash.HashResult.Failed("legacy"))

        detector.ensureHashesComputed(listOf(image(dateModified = 1000L)))

        // 有界重试（每轮一次）：失败条目进入本轮计算并按当轮内容版本落盘。
        assertNotNull(hashCache.getHash(ImageHashCache.HashKey(null, 10L), contentVersion = 1000L))
    }
}
