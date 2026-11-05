package com.tabula.v3.data.repository

import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SimilarGroupsCacheTest {
    private val cache = SimilarGroupsCache(validityMs = 60_000L)
    private val now = 1_000_000L

    @Before
    fun setUp() {
        cache.invalidate()
    }

    private fun image(id: Long, dateModified: Long = 1000L, size: Long = 500L, volumeName: String? = null) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/${volumeName ?: "external"}/images/media/$id"),
        displayName = "img$id.jpg",
        dateModified = dateModified,
        size = size,
        width = 64,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = volumeName
    )

    private fun group(vararg ids: Long) = SimilarGroup(
        images = ids.map { image(it) },
        startTime = 1000L,
        endTime = 2000L
    )

    @Test
    fun sameInputWithinTtlReturnsSameInstance() {
        val images = listOf(image(1), image(2))
        val stored = listOf(group(1, 2))
        cache.store(stored, images, now)
        assertSame(
            "unchanged input within TTL must reuse the stored group list instance",
            stored, cache.lookup(images, now + 1_000L)
        )
    }

    @Test
    fun fingerprintIsOrderInsensitive() {
        val images = listOf(image(1), image(2), image(3))
        val stored = listOf(group(1, 2, 3))
        cache.store(stored, images, now)
        assertSame(
            "same members in different order must still hit the cache",
            stored, cache.lookup(images.shuffled(), now + 1_000L)
        )
    }

    @Test
    fun contentEditWithinTtlMisses() {
        // TC58：同媒体 ID 改内容（DATE_MODIFIED 提升）在 TTL 内也必须失效旧组。
        val images = listOf(image(1), image(2, dateModified = 2000L))
        cache.store(listOf(group(1, 2)), images, now)
        val edited = images.map { if (it.id == 2L) it.copy(dateModified = 9_999L) else it }
        assertNull("content edit must invalidate the cached groups", cache.lookup(edited, now + 1_000L))
    }

    @Test
    fun sizeChangeWithinTtlMisses() {
        val images = listOf(image(1), image(2))
        cache.store(listOf(group(1, 2)), images, now)
        val edited = images.map { if (it.id == 2L) it.copy(size = 999L) else it }
        assertNull("content size change must invalidate the cached groups", cache.lookup(edited, now + 1_000L))
    }

    @Test
    fun simultaneousDateAndSizeChangesDoNotCollide() {
        val images = listOf(image(10, dateModified = 1000L, size = 500L))
        val stored = listOf(group(10))
        cache.store(stored, images, now)
        assertSame(stored, cache.lookup(images, now + 1_000L))
        val edited = listOf(image(10, dateModified = 1001L, size = 469L))
        assertNull("different version fields must miss even when old hash collides", cache.lookup(edited, now + 1_000L))
    }

    @Test
    fun membershipChangeWithinTtlMisses() {
        val images = listOf(image(1), image(2))
        cache.store(listOf(group(1, 2)), images, now)
        assertNull("added member must invalidate the cache", cache.lookup(images + image(3), now + 1_000L))
        assertNull("removed member must invalidate the cache", cache.lookup(listOf(image(1)), now + 1_000L))
    }

    @Test
    fun ttlExpiryMissesButJustBeforeExpiryHits() {
        val images = listOf(image(1), image(2))
        val stored = listOf(group(1, 2))
        cache.store(stored, images, now)
        assertSame(stored, cache.lookup(images, now + 59_999L))
        assertNull("expired cache must trigger recompute", cache.lookup(images, now + 60_000L))
    }

    @Test
    fun invalidateForcesMissEvenForMatchingInput() {
        val images = listOf(image(1), image(2))
        cache.store(listOf(group(1, 2)), images, now)
        cache.invalidate()
        assertNull("explicit invalidation must force recompute", cache.lookup(images, now + 1_000L))
        assertNotNull(cache.fingerprintOf(images))
    }

    @Test
    fun volumeSwapWithSameMetadataMisses() {
        // C1/T03-GROUPCACHE-VOLUME：成员身份含卷——A 卷照片被 B 卷同
        // (id, dateModified, size) 的照片替换时，指纹必须 miss，不得继续
        // 返回包含 A 卷旧 URI 的缓存组（绕过已按卷消歧的检测链）。
        val volumeA = listOf(image(10, volumeName = "external_primary"))
        cache.store(listOf(group(10)), volumeA, now)
        assertSame("未变化输入仍命中", cache.lookup(volumeA, now + 1_000L), cache.lookup(volumeA, now + 1_000L))

        val volumeB = listOf(image(10, volumeName = "external_secondary"))
        assertNull(
            "同元数据换卷必须失效缓存组",
            cache.lookup(volumeB, now + 1_000L)
        )
    }

    @Test
    fun versionSizeOwnershipSwapBetweenSameIdRowsMisses() {
        // 两卷同数值 ID 行的版本/大小归属交换（集合层面的多重集不变）也必须
        // miss：条目保留数相同但成员所属卷不同，旧指纹无法区分。
        val original = listOf(
            image(10, dateModified = 1000L, size = 500L, volumeName = "external_primary"),
            image(10, dateModified = 2000L, size = 900L, volumeName = "external_secondary")
        )
        cache.store(listOf(group(10)), original, now)
        assertSame(cache.lookup(original, now + 1_000L), cache.lookup(original, now + 1_000L))

        val swapped = listOf(
            image(10, dateModified = 2000L, size = 900L, volumeName = "external_primary"),
            image(10, dateModified = 1000L, size = 500L, volumeName = "external_secondary")
        )
        assertNull(
            "版本/大小归属跨卷交换必须失效缓存组",
            cache.lookup(swapped, now + 1_000L)
        )
    }

    @Test
    fun reorderWithVolumeIdentityStillHits() {
        // 顺序无关语义保留：带卷成员重排后仍命中。
        val images = listOf(
            image(10, volumeName = "external_primary"),
            image(11, dateModified = 2000L, volumeName = "external_secondary")
        )
        val stored = listOf(group(10, 11))
        cache.store(stored, images, now)
        assertSame(
            "带卷身份的同成员重排必须仍命中",
            stored,
            cache.lookup(images.reversed(), now + 1_000L)
        )
    }
}
