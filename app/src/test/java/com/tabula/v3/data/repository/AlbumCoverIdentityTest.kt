package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C1/T03-COVER：图集封面完整媒体身份（卷|ID）——设置、持久化、刷新恢复与
 * 解析。
 *
 * 既有缺陷（非本轮引入）：
 * 1. AlbumMetadata 不持久化封面（updateAlbum 丢弃 coverImageId）——用户
 *    "设置封面"后 refreshAlbumsFromSystem 立即用桶默认封面重建，设置从不
 *    生效，重启即丢。
 * 2. Album.coverImageId 为裸数值 ID、无卷——跨卷同数值 ID 时展示解析
 *    （imageMap[id] / 聚合 URI fallback）会串到另一卷的同 ID 照片。
 *
 * 夹具：真实 AlbumManager + 真实 metadata 文件（albums_metadata.json）；
 * 桶列表经 preloadedBuckets 注入（覆盖两卷同名桶，封面为各自卷内照片）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlbumCoverIdentityTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app

    private val primaryBucket = LocalImageRepository.SystemBucket(
        name = "Camera", imageCount = 2, coverImageId = 10L,
        relativePath = "DCIM/Camera/", volumeName = "external_primary"
    )
    private val secondaryBucket = LocalImageRepository.SystemBucket(
        name = "Camera", imageCount = 2, coverImageId = 10L,
        relativePath = "DCIM/Camera/", volumeName = "external_secondary"
    )

    /** 用户设置封面 = 第二卷的同数值 ID 行（跨卷串图反例的关键行）。 */
    private fun albumWithCoverSet() = Album(
        id = "Camera",
        name = "Camera",
        coverImageId = 10L,
        coverVolumeName = "external_secondary",
        imageCount = 4
    )

    private val primaryCoverRow = ImageFile(
        id = 10L,
        uri = Uri.parse("content://media/external_primary/images/media/10"),
        displayName = "cover-primary.jpg",
        dateModified = 1_000L,
        size = 100L,
        width = 64,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = "external_primary"
    )

    private val secondaryCoverRow = ImageFile(
        id = 10L,
        uri = Uri.parse("content://media/external_secondary/images/media/10"),
        displayName = "cover-secondary.jpg",
        dateModified = 1_000L,
        size = 100L,
        width = 64,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = "external_secondary"
    )

    @Before
    fun setUp() {
        // metadata 文件为真实磁盘状态，逐用例清理。
        context.getSharedPreferences("tabula_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        java.io.File(context.filesDir, "albums_metadata.json").delete()
    }

    @Test
    fun setCoverPersistsAcrossRestartAndRefresh() = runBlocking {
        // 用户设置封面（第二卷行）→ 持久化 → 重启（新实例）+ 刷新（桶封面
        // 默认为主卷行）→ 用户封面必须以完整身份恢复，不得被桶默认覆盖。
        val manager = AlbumManager(context)
        // 显式设置封面走 setAlbumCover（updateAlbum 属性编辑不写封面字段）。
        manager.setAlbumCover("Camera", secondaryCoverRow)

        val restarted = AlbumManager(context)
        restarted.refreshAlbumsFromSystem(
            preloadedBuckets = listOf(primaryBucket, secondaryBucket)
        )
        val album = restarted.albums.value.first { it.name == "Camera" }
        assertEquals(
            "用户设置的封面必须以完整媒体身份持久恢复",
            "external_secondary", album.coverVolumeName
        )
        assertEquals(10L, album.coverImageId)
    }

    @Test
    fun coverReSetToDifferentVolumeReplacesIdentity() = runBlocking {
        // 跨卷换封面：先设第二卷封面，再改设主卷同数值 ID 行——持久化身份
        // 必须跟随新选择整体替换（不得保留旧卷致串图）。
        val manager = AlbumManager(context)
        manager.setAlbumCover("Camera", secondaryCoverRow)
        manager.setAlbumCover("Camera", primaryCoverRow)

        val restarted = AlbumManager(context)
        restarted.refreshAlbumsFromSystem(
            preloadedBuckets = listOf(primaryBucket, secondaryBucket)
        )
        val album = restarted.albums.value.first { it.name == "Camera" }
        assertEquals(
            "换封面后身份必须跟随新选择（卷替换）",
            "external_primary", album.coverVolumeName
        )
        assertEquals(10L, album.coverImageId)
    }

    @Test
    fun legacyMetadataWithoutCoverFallsBackToBucketDefault() = runBlocking {
        // 旧格式（metadata 无封面字段）：行为与升级前一致——桶默认封面，
        // 且默认封面必须携带其所属卷（跨卷同名桶不串）。
        val manager = AlbumManager(context)
        manager.updateAlbum(Album(id = "Camera", name = "Camera", imageCount = 4))

        val restarted = AlbumManager(context)
        restarted.refreshAlbumsFromSystem(
            preloadedBuckets = listOf(primaryBucket, secondaryBucket)
        )
        val album = restarted.albums.value.first { it.name == "Camera" }
        assertEquals(
            "无用户封面的图集回退桶默认封面（行为与升级前一致）",
            10L, album.coverImageId
        )
        assertEquals(
            "桶默认封面必须携带其所属卷（mergeBucketsByName 日期序首个桶）",
            "external_primary", album.coverVolumeName
        )
    }

    @Test
    fun coverResolutionDistinguishesCrossVolumeSameId() {
        // 展示解析：两卷同数值 ID 行、封面指向第二卷 → 解析必须命中第二卷
        // 行（旧口径 imageMap[裸 id] 会折叠到先出现的行）。
        fun row(volume: String, id: Long) = ImageFile(
            id = id,
            uri = Uri.parse("content://media/$volume/images/media/$id"),
            displayName = "img-$volume-$id.jpg",
            dateModified = 1_000L,
            size = 100L,
            width = 64,
            height = 64,
            bucketDisplayName = "Camera",
            volumeName = volume
        )
        val images = listOf(
            row("external_primary", 10L),
            row("external_secondary", 10L)
        )
        val album = albumWithCoverSet()
        val resolved = resolveAlbumCoverImage(album, images)
        assertEquals(
            "封面解析必须命中封面卷的那一行",
            "external_secondary", resolved!!.volumeName
        )
        assertEquals(
            "解析行必须携带卷限定 URI（供展示直接使用）",
            Uri.parse("content://media/external_secondary/images/media/10"),
            resolved.uri
        )
    }

    @Test
    fun legacyCoverWithoutVolumeResolvesOnlyUniqueId() {
        // 旧口径（卷未知）：库内该 ID 唯一 → 解析（兼容）；两卷各有同 ID
        // → 不猜选，返回 null（展示回退占位，指针保留）。
        fun row(volume: String, id: Long) = ImageFile(
            id = id,
            uri = Uri.parse("content://media/$volume/images/media/$id"),
            displayName = "img.jpg",
            dateModified = 1_000L,
            size = 100L,
            width = 64,
            height = 64,
            bucketDisplayName = "Camera",
            volumeName = volume
        )
        val legacyAlbum = Album(
            id = "Camera", name = "Camera",
            coverImageId = 10L, coverVolumeName = null
        )
        val single = listOf(row("external_primary", 10L))
        assertEquals(
            "旧口径封面在库内唯一时必须解析（兼容）",
            "external_primary", resolveAlbumCoverImage(legacyAlbum, single)!!.volumeName
        )
        val ambiguous = listOf(row("external_primary", 10L), row("external_secondary", 10L))
        assertNull(
            "旧口径封面跨卷歧义时不得猜选另一卷",
            resolveAlbumCoverImage(legacyAlbum, ambiguous)
        )
    }

    @Test
    fun coverResolutionReturnsNullWhenCoverMissingOrUnreadable() {
        // 封面照片已被删除/暂时不可读：解析 null（展示回退占位），由调用方
        // 保留指针——不抛异常、不猜选别的照片。
        fun row(volume: String, id: Long) = ImageFile(
            id = id,
            uri = Uri.parse("content://media/$volume/images/media/$id"),
            displayName = "img.jpg",
            dateModified = 1_000L,
            size = 100L,
            width = 64,
            height = 64,
            bucketDisplayName = "Camera",
            volumeName = volume
        )
        val album = Album(
            id = "Camera", name = "Camera",
            coverImageId = 99L, coverVolumeName = "external_primary"
        )
        val library = listOf(row("external_primary", 10L))
        assertNull(
            "封面行缺失/不可读时解析为 null（占位展示，指针保留）",
            resolveAlbumCoverImage(album, library)
        )
    }

    // ========== 复验轮四：用户封面与自动封面的区分 ==========

    @Test
    fun propertyEditDoesNotFreezeBucketDefaultCover() = runBlocking {
        // 审计复现场景：updateAlbum 只改名称/颜色等属性时不得把显示态的
        // 桶默认封面持久化为用户封面——否则自动封面被冻结（新照片入桶后
        // 默认封面本应跟随最新，却被旧 ID 固定）。
        val manager = AlbumManager(context)
        manager.refreshAlbumsFromSystem(preloadedBuckets = listOf(primaryBucket, secondaryBucket))
        val displayAlbum = manager.albums.value.first { it.name == "Camera" }
        assertEquals(10L, displayAlbum.coverImageId)

        // 属性编辑（排序）——携带显示态封面的 Album 对象走 updateAlbum。
        manager.updateAlbum(displayAlbum.copy(order = 5))

        // 桶内容变化：新照片 id=11 成为该桶最新（默认封面应跟随）。
        val newBuckets = listOf(
            primaryBucket.copy(coverImageId = 11L),
            secondaryBucket
        )
        val restarted = AlbumManager(context)
        restarted.refreshAlbumsFromSystem(preloadedBuckets = newBuckets)
        val album = restarted.albums.value.first { it.name == "Camera" }
        assertEquals(
            "属性编辑不得冻结自动封面（桶默认封面必须跟随最新照片）",
            11L, album.coverImageId
        )
    }

}
