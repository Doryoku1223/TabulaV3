package com.tabula.v3.data.repository

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.function.Supplier

/**
 * C1/T03-MOVECOPY-IDENTITY：图集移动/复制按完整媒体身份（卷|ID）消费选择集。
 * 不同卷的同数值 ID 是不同照片——只选 B 不得移动/复制 A；同时确认 A、B 必须
 * 两张都处理；所选行已失效时跳过，不得回退到同数字 ID 的另一卷照片。
 * 复制断言核对目标实际字节（读到的源内容），不只断言调用次数。
 *
 * 夹具：受控 ContentProvider（真实 AlbumManager/SystemAlbumSyncManager 拷贝与
 * 移动链路），聚合查询返回两卷同 ID 行（VOLUME_NAME 列区分），行 URI 为卷限定
 * item URI（与真实仓库 imageItemUri 同形）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlbumIdentityMoveCopyTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app
    private lateinit var provider: VolumeMoveCopyProvider
    private lateinit var manager: AlbumManager

    private val primaryUri = Uri.parse("content://media/external_primary/images/media/10")
    private val secondaryUri = Uri.parse("content://media/external_secondary/images/media/10")

    private val primaryKey = "external_primary|10"
    private val secondaryKey = "external_secondary|10"

    @Before
    fun setUp() {
        provider = VolumeMoveCopyProvider(context)
        provider.seedRow(volume = "external_primary", id = 10L, content = "content-A")
        provider.seedRow(volume = "external_secondary", id = 10L, content = "content-B")
        provider.seedRow(volume = "external_primary", id = 11L, content = "content-C")
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        manager = AlbumManager(context)
    }

    // ========== 移动 ==========

    @Test
    fun moveOnlySecondaryRowWhenOnlyItIsConfirmed() = runBlocking {
        val result = manager.moveImagesByIdentity(listOf(secondaryKey), "source", "target")

        assertEquals(1, result.movedCount)
        assertEquals(0, result.retainedCount)
        assertEquals(
            "只确认第二卷行时必须只更新它的卷限定 URI",
            listOf(secondaryUri), provider.updateUris
        )
    }

    @Test
    fun moveMovesBothRowsWhenBothConfirmed() = runBlocking {
        val result = manager.moveImagesByIdentity(listOf(primaryKey, secondaryKey), "source", "target")

        assertEquals("两行都被确认时必须都移动", 2, result.movedCount)
        assertEquals(
            "移动目标必须是两行各自的卷限定 URI",
            setOf(primaryUri, secondaryUri), provider.updateUris.toSet()
        )
    }

    @Test
    fun moveSingleVolumeRowsStillWorks() = runBlocking {
        // 普通单卷操作不受身份迁移影响（控制组）。
        val result = manager.moveImagesByIdentity(listOf("external_primary|11"), "source", "target")

        assertEquals(1, result.movedCount)
        assertEquals(listOf(Uri.parse("content://media/external_primary/images/media/11")), provider.updateUris)
    }

    @Test
    fun moveStaleSelectionSkipsWithoutSameIdFallback() = runBlocking {
        // 确认后行已失效（第二卷行消失）：跳过该选择，不得回退到同数值 ID 的主卷行。
        provider.removeRow("external_secondary", 10L)

        val result = manager.moveImagesByIdentity(listOf(secondaryKey), "source", "target")

        assertEquals(0, result.movedCount)
        assertEquals(1, result.retainedCount)
        assertTrue(
            "失效选择不得回退移动同 ID 的另一卷照片",
            provider.updateUris.isEmpty()
        )
    }

    // ========== 复制（核对目标实际字节） ==========

    @Test
    fun copyOnlySecondaryRowBytesWhenOnlyItIsConfirmed() = runBlocking {
        manager.copyImagesByIdentity(listOf(secondaryKey), "target")

        assertEquals("只确认第二卷行时必须只产生一次复制插入", 1, provider.insertedTargets.size)
        assertEquals(
            "复制目标字节必须来自确认的第二卷行（而不是同 ID 的主卷行）",
            "content-B", provider.insertedTargets.single().readText()
        )
    }

    @Test
    fun copyCopiesBothRowsWhenBothConfirmed() = runBlocking {
        manager.copyImagesByIdentity(listOf(primaryKey, secondaryKey), "target")

        assertEquals(2, provider.insertedTargets.size)
        assertEquals(
            "两行都被确认时必须各自复制",
            setOf("content-A", "content-B"),
            provider.insertedTargets.map { it.readText() }.toSet()
        )
    }

    @Test
    fun copySingleVolumeRowsStillWorks() = runBlocking {
        manager.copyImagesByIdentity(listOf("external_primary|11"), "target")

        assertEquals(1, provider.insertedTargets.size)
        assertEquals("content-C", provider.insertedTargets.single().readText())
    }

    @Test
    fun copyStaleSelectionSkipsWithoutSameIdFallback() = runBlocking {
        provider.removeRow("external_secondary", 10L)

        manager.copyImagesByIdentity(listOf(secondaryKey), "target")

        assertTrue("失效选择不得回退复制同 ID 的另一卷照片", provider.insertedTargets.isEmpty())
    }

    @Test
    fun legacyLongApiRemainsSourceCompatible() = runBlocking {
        // 旧 Long 接口保留兼容：普通单卷行为不变（AlbumMoveResultTest 继续覆盖故障面）。
        val result = manager.moveImagesToAlbum(listOf(11L), "source", "target")
        assertEquals(1, result.movedCount)
    }
}

/**
 * 跨卷受控 Provider：聚合查询返回带 VOLUME_NAME 的多行；卷限定 item URI
 * （content://media/{volume}/images/media/{id}）解析回对应行（含插入的复制
 * 目标行）；update 可配置结果；insert 产出带真实 backing 文件的目标行
 * （openFile 读写，供复制字节核对）；记录全部调用。
 */
private class VolumeMoveCopyProvider(private val context: Context) : ContentProvider() {
    class Row(
        val volume: String,
        val id: Long,
        val file: File,
        val displayName: String,
        val bucket: String,
        val relativePath: String,
        val dateModifiedSeconds: Long,
        var pending: Int = 0
    )

    private val seededRows = mutableListOf<Row>()
    private val insertedRows = mutableListOf<Pair<Uri, Row>>()
    private var nextInsertedId = 200L
    var updateResults = mutableMapOf<Long, Int>()
    val updateUris = mutableListOf<Uri>()
    val insertedTargets: List<File> get() = insertedRows.map { it.second.file }
    var deleteCalls = 0

    fun seedRow(volume: String, id: Long, content: String) {
        val file = File(context.cacheDir, "ident-$volume-$id.jpg").apply {
            parentFile?.mkdirs()
            writeText(content)
        }
        seededRows.add(
            Row(
                volume = volume,
                id = id,
                file = file,
                displayName = file.name,
                bucket = "source",
                relativePath = "Pictures/source/",
                dateModifiedSeconds = 1_700_000_000L + id
            )
        )
        // 复制链路经 ShadowContentResolver 的流 supplier 读源字节（与真实
        // ContentResolver.openInputStream 委托一致），必须在种子时注册。
        val sourceUri = Uri.parse("content://media/$volume/images/media/$id")
        shadowOf(context.contentResolver).registerInputStreamSupplier(
            sourceUri,
            Supplier { FileInputStream(file) }
        )
    }

    fun removeRow(volume: String, id: Long) {
        seededRows.removeAll { it.volume == volume && it.id == id }
    }

    override fun onCreate() = true

    private fun allRows(): List<Row> = seededRows + insertedRows.map { it.second }

    private fun rowForUri(uri: Uri): Row? {
        val segments = uri.pathSegments
        if (segments.size < 4) return null
        val volume = segments[0]
        val id = segments[3].toLongOrNull() ?: return null
        return allRows().find { it.volume == volume && it.id == id }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val columns = projection ?: arrayOf(MediaStore.Images.Media._ID)
        val cursor = MatrixCursor(columns)

        rowForUri(uri)?.let { row ->
            cursor.addRow(columns.map { column -> rowValue(column, row) }.toTypedArray())
            return cursor
        }

        if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
            var candidateRows = allRows()
            if (selection?.contains(MediaStore.Images.Media.BUCKET_DISPLAY_NAME) == true) {
                val bucket = selectionArgs?.firstOrNull()
                candidateRows = candidateRows.filter { it.bucket == bucket }
            } else if (selection?.contains(MediaStore.Images.Media.RELATIVE_PATH) == true) {
                // 复制链路的既有候选查询（目标路径 + 文件名）。
                val path = selectionArgs?.getOrNull(0)
                val name = selectionArgs?.getOrNull(1)
                candidateRows = candidateRows.filter { row ->
                    row.relativePath == path && (name == null || row.displayName == name)
                }
            }
            for (row in candidateRows) {
                cursor.addRow(columns.map { column -> rowValue(column, row) }.toTypedArray())
            }
        }
        return cursor
    }

    private fun rowValue(column: String, row: Row): Any? = when (column) {
        MediaStore.Images.Media._ID -> row.id
        MediaStore.Images.Media.DISPLAY_NAME -> row.displayName
        MediaStore.Images.Media.DATE_MODIFIED -> row.dateModifiedSeconds
        MediaStore.Images.Media.DATE_ADDED -> row.dateModifiedSeconds
        MediaStore.Images.Media.DATE_TAKEN -> row.dateModifiedSeconds * 1000
        MediaStore.Images.Media.SIZE -> row.file.length()
        MediaStore.Images.Media.WIDTH -> 640
        MediaStore.Images.Media.HEIGHT -> 480
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> row.bucket
        MediaStore.Images.Media.ORIENTATION -> 0
        MediaStore.Images.Media.DATA -> row.file.absolutePath
        MediaStore.Images.Media.RELATIVE_PATH -> row.relativePath
        MediaStore.MediaColumns.VOLUME_NAME -> row.volume
        MediaStore.MediaColumns.IS_PENDING -> row.pending
        else -> null
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextInsertedId++
        val newUri = Uri.parse("content://media/external_primary/images/media/$id")
        val targetFile = File(context.cacheDir, "ident-copy-target-$id.jpg")
        val relativePath = values?.getAsString(MediaStore.Images.Media.RELATIVE_PATH) ?: "Pictures/target/"
        insertedRows.add(
            newUri to Row(
                volume = "external_primary",
                id = id,
                file = targetFile,
                displayName = values?.getAsString(MediaStore.Images.Media.DISPLAY_NAME) ?: targetFile.name,
                bucket = relativePath.trimEnd('/').substringAfterLast('/'),
                relativePath = relativePath,
                dateModifiedSeconds = 1_700_000_000L,
                pending = 1
            )
        )
        // 目标写入与回读流（复制链路在 insert 之后才打开流，需此刻注册）。
        val streamResolver = shadowOf(context.contentResolver)
        streamResolver.registerOutputStreamSupplier(
            newUri,
            Supplier { FileOutputStream(targetFile) }
        )
        streamResolver.registerInputStreamSupplier(
            newUri,
            Supplier { if (targetFile.exists()) FileInputStream(targetFile) else null }
        )
        return newUri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleteCalls++
        return 1
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        updateUris += uri
        val inserted = insertedRows.find { it.first == uri }
        if (inserted != null) {
            // 目标行 publish（IS_PENDING=0）：真实 MediaStore 会更新该行。
            if (values?.containsKey(MediaStore.MediaColumns.IS_PENDING) == true) {
                inserted.second.pending = values.getAsInteger(MediaStore.MediaColumns.IS_PENDING) ?: 0
            }
            return 1
        }
        return uri.lastPathSegment?.toLongOrNull()?.let { updateResults[it] } ?: 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rowForUri(uri) ?: throw IllegalArgumentException("Unknown uri: $uri")
        val flags = if (mode.startsWith("r")) {
            ParcelFileDescriptor.MODE_READ_ONLY
        } else {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        }
        return ParcelFileDescriptor.open(row.file, flags)
    }
}
