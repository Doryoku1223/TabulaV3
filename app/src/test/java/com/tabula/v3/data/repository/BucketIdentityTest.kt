package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class BucketIdentityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: EvidenceTestProvider
    private lateinit var repository: LocalImageRepository

    @Before
    fun setUp() {
        provider = EvidenceTestProvider(
            context,
            File(context.cacheDir, "bucket-identity-seed.jpg").apply { writeBytes(byteArrayOf(1)) },
            Uri.parse("content://media/external_primary/images/media/10"),
            Uri.parse("content://media/external_primary/images/media/10?alias=source"),
            Uri.parse("content://media/external_primary/files/10")
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        repository = LocalImageRepository(context)
    }

    private fun row(id: Long, volume: String, bucket: String, path: String) {
        val file = File(context.cacheDir, "bucket-row-$id.jpg").apply { writeBytes(byteArrayOf(id.toByte())) }
        provider.rows[id] = EvidenceMediaRow(
            id = id,
            file = file,
            volumeName = volume,
            displayName = "row$id.jpg",
            bucketName = bucket,
            relativePath = path,
            dateModified = id
        )
    }

    @Test
    fun sameNameDifferentPathStaysSplitAtBucketLevel() = runBlocking {
        provider.rows.clear() // 移除夹具种子行，隔离本测试的行集合
        row(1L, "external_primary", "Camera", "DCIM/Camera/")
        row(2L, "external_primary", "Camera", "Pictures/Camera/")
        row(3L, "external_secondary", "Camera", "DCIM/Camera/")

        val buckets = repository.getAllBucketsWithInfo()

        assertEquals(3, buckets.size)
        assertEquals(
            setOf(
                Triple("external_primary", "DCIM/Camera", 1),
                Triple("external_primary", "Pictures/Camera", 1),
                Triple("external_secondary", "DCIM/Camera", 1)
            ),
            buckets.map { Triple(it.volumeName, it.relativePath, it.imageCount) }.toSet()
        )
        assertTrue(buckets.all { it.coverImageId != null })
    }

    @Test
    fun mergeBucketsByNameSumsCountsAndKeepsFirstCoverAndPath() {
        val merged = mergeBucketsByName(
            listOf(
                LocalImageRepository.SystemBucket("Camera", 2, 9L, "DCIM/Camera", "external_primary"),
                LocalImageRepository.SystemBucket("Camera", 3, 8L, "Pictures/Camera", "external_secondary"),
                LocalImageRepository.SystemBucket("Downloads", 1, 7L, "Download", "external_primary")
            )
        )

        assertEquals(listOf("Camera", "Downloads"), merged.map { it.name })
        assertEquals(5, merged.first { it.name == "Camera" }.imageCount)
        assertEquals(9L, merged.first { it.name == "Camera" }.coverImageId)
        assertEquals("DCIM/Camera", merged.first { it.name == "Camera" }.relativePath)
        assertEquals("external_primary", merged.first { it.name == "Camera" }.volumeName)
    }

    @Test
    fun mergeBucketsByNameHandlesEmptyAndSingle() {
        assertTrue(mergeBucketsByName(emptyList()).isEmpty())
        val single = listOf(LocalImageRepository.SystemBucket("A", 1, 1L, "a/", "external_primary"))
        assertEquals(single, mergeBucketsByName(single))
    }
}
