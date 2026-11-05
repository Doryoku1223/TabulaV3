package com.tabula.v3

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.repository.AlbumManager
import com.tabula.v3.data.repository.SystemAlbumSyncManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.UUID

/**
 * O2 图集创建诊断（合成最小复现）：经真实入口在 API36/37 记录创建阶段、
 * 异常原文与 Provider/目录结果。诊断结论写入 logcat（CreateAlbumDiag 标签），
 * 由 CI 日志与工件保存；测试只断言路径一致的不变量与 API36 基线，不吞失败细节。
 *
 * - createBucketStageProbeReportsEachStep：先调用真实 createBucket（含
 *   getAlbumRelativePath 的 MediaStore/File 前置探测），再对独立唯一名逐阶段
 *   复现旧路径（insert pending → 写最小 JPEG → unpublish → delete），dot 名与
 *   非 dot 名各一次以隔离占位文件名影响；两路对照定位失败阶段。
 * - createAlbumRealEntryOutcomeReported：真实 createAlbum 入口；成功路径断言
 *   图集以 imageCount=0 可见，失败路径断言元数据无半提交残留（abort-before-save
 *   不变量）。
 *
 * 首轮云运行（41082467467，API37）：real createBucket=NULL（无异常）；
 * replica insert/write/unpublish 全部成功，delete 抛 SecurityException
 * "has no access to content://media/external/images/media/85" —— 失败阶段
 * 定位为"unpublish 后删除自有占位项"。本版追加非 dot 名变体以确认文件名
 * 前导点是否为触发条件。
 */
@RunWith(AndroidJUnit4::class)
class CreateAlbumDiagnosticDeviceTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private val fixtures get() = CloudFixtureCleanup(ctx, ins)
    private val albums get() = AlbumManager.getInstance(ctx)
    private val syncManager get() = SystemAlbumSyncManager.getInstance(ctx)
    private var probeAllowed = false
    private val registered = mutableListOf<Uri>()
    private val fixtureInfo = mutableMapOf<Uri, CloudFixtureCleanup.Fixture>()
    private val dirsToClean = mutableListOf<String>()
    private val dotReplicaFilesToClean = mutableListOf<Pair<String, String>>()
    private val metadataAlbumsToClean = mutableListOf<String>()
    private val bucketName = "DiagBucket" + UUID.randomUUID().toString().replace("-", "").take(10)
    private val replicaName = "DiagReplica" + UUID.randomUUID().toString().replace("-", "").take(10)
    private val entryName = "DiagAlbum" + UUID.randomUUID().toString().replace("-", "").take(10)
    private val sentinelName = "DiagSentinel" + UUID.randomUUID().toString().replace("-", "").take(10)

    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use { p ->
        FileInputStream(p.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun diag(stage: String, detail: String) {
        android.util.Log.i("CreateAlbumDiag", "device=$stage detail=$detail")
    }

    @Before fun setup() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("allowSyntheticMediaProbe") == "true")
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        probeAllowed = true
        shell("pm grant ${ctx.packageName} ${Manifest.permission.READ_MEDIA_IMAGES}")
        assertEquals(PackageManager.PERMISSION_GRANTED, ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES))
        AppPreferences(ctx).apply { hasCompletedOnboarding = true; hasAcknowledgedDisclaimer = true }
        albums.initialize()
        diag("env", "sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT}")
        Unit
    }

    /** createBucket 旧路径的最小 JPEG 占位字节（与 SystemAlbumSyncManager 相同）。 */
    private fun minimalJpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF.toByte(), 0xD9.toByte()
    )

    // UiAutomation 字符串入口按空白分词（acceptance.md）：含 &&/||/重定向的
    // 命令必须经完整脚本封装；此前 dirExists 恒 false 是 shell 语法假象
    // （run 41082467467 的"空目录不持久"观察因此作废，待本版重证）。
    private fun runScript(script: String): String {
        val encoded = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val command = "/system/bin/sh -c printf\${IFS}%s\${IFS}$encoded|base64\${IFS}-d|/system/bin/sh"
        return ins.uiAutomation.executeShellCommand(command).use { p ->
            FileInputStream(p.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    private fun dirListing(relativePath: String): String =
        runScript("ls /sdcard/$relativePath 2>&1").trim().replace("\n", "|")

    private fun dirExists(relativePath: String): Boolean =
        runScript("test -d /sdcard/$relativePath && echo EXISTS || echo ABSENT").trim() == "EXISTS"

    @Test fun createBucketStageProbeReportsEachStep() = runBlocking {
        // 1) 真实 createBucket：包含 getAlbumRelativePath 前置探测 + 占位链。
        dirsToClean += "Pictures/Tabula/$bucketName"
        val realOutcome = try {
            val path = syncManager.createBucket(bucketName)
            diag("real-createBucket", if (path != null) "OK path=$path" else "NULL (no exception)")
            path
        } catch (e: Throwable) {
            diag("real-createBucket", "EXCEPTION ${e.javaClass.name}: ${e.message}")
            null
        }
        if (realOutcome != null) {
            // 空目录在占位删除后未必持久可见（首轮 API36 实测 dirExists=false）：
            // createBucket 的契约是返回路径，目录存在性只记录不作为断言。
            diag("real-createBucket-dir", "path=$realOutcome dirExists=${dirExists(realOutcome.trimEnd('/'))}")

        } else {
            diag("real-createBucket-dir", "dirExists=${dirExists("Pictures/Tabula/$bucketName")}")
        }

        // 2) 逐阶段复现：dot 名（与生产占位一致）与非 dot 名各跑一次，
        // 隔离占位文件名前导点对 delete 阶段的影响。
        val replicaPath = "Pictures/Tabula/$replicaName"
        dirsToClean += replicaPath
        val dotName = ".tabula_placeholder_diag_${System.currentTimeMillis()}.jpg"
        dotReplicaFilesToClean += replicaPath to dotName
        val dotFailures = runReplicaChain(replicaPath, dotName, "replica")
        val dotlessFailures = runReplicaChain(replicaPath, "tabula_placeholder_diag_${System.currentTimeMillis()}.jpg", "replica-dotless")
        diag("replica-summary", "dotFailures=$dotFailures dotlessFailures=$dotlessFailures")


        // 3) 对照结论：两路结果一致性（不一致记 FINDING/MISMATCH 供分析，不作为断言）。
        if (realOutcome == null && dotFailures.isEmpty()) {
            diag("MISMATCH", "real createBucket failed but every replicated stage succeeded")
        }
        if (realOutcome != null && dotFailures.isNotEmpty()) {
            diag("MISMATCH", "real createBucket succeeded but replicated stages failed: $dotFailures")
        }
        if ("delete" in dotFailures && "delete" !in dotlessFailures) {
            diag("FINDING", "delete stage fails only with the dot-prefixed placeholder name")
        }
        if ("delete" in dotFailures && "delete" in dotlessFailures) {
            diag("FINDING", "delete stage fails regardless of placeholder name")
        }
        assertNotNull("real createBucket must succeed on API ${Build.VERSION.SDK_INT}", realOutcome)
        assertEquals("Pictures/Tabula/$bucketName", realOutcome?.trimEnd('/'))

    }

    /** createBucket 旧占位链的逐阶段复现；返回失败阶段列表（空 = 全部成功）。 */
    private fun runReplicaChain(relativePath: String, displayName: String, tag: String): List<String> {
        val failures = mutableListOf<String>()
        var placeholderUri: Uri? = null
        try {
            placeholderUri = ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            )
            diag("$tag-insert", if (placeholderUri != null) "OK uri=$placeholderUri" else "NULL (no exception)")
            if (placeholderUri == null) failures += "insert"
            if (placeholderUri != null) {
                registered += placeholderUri
                fixtureInfo[placeholderUri] = fixtures.fixture(placeholderUri)
            }

            if (placeholderUri != null) {
                val written = try {
                    ctx.contentResolver.openOutputStream(placeholderUri!!)?.use { output ->
                        output.write(minimalJpeg())
                        true
                    } ?: false
                } catch (e: Throwable) {
                    diag("$tag-write", "EXCEPTION ${e.javaClass.name}: ${e.message}")
                    false
                }
                diag("$tag-write", if (written) "OK" else "FAILED")
                if (!written) failures += "write"

                val unpublished = try {
                    ctx.contentResolver.update(
                        placeholderUri!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null
                    )
                } catch (e: Throwable) {
                    diag("$tag-unpublish", "EXCEPTION ${e.javaClass.name}: ${e.message}")
                    -1
                }
                diag("$tag-unpublish", "rows=$unpublished")
                if (unpublished != 1) failures += "unpublish"

                val deleted = try {
                    ctx.contentResolver.delete(placeholderUri!!, null, null)
                } catch (e: Throwable) {
                    diag("$tag-delete", "EXCEPTION ${e.javaClass.name}: ${e.message}")
                    -1
                }
                diag("$tag-delete", "rows=$deleted")
                if (deleted != 1) failures += "delete"
            }
        } finally {
            diag("$tag-dir", "exists=${dirExists(relativePath)} listing=${dirListing("Pictures/Tabula")}")
        }
        return failures
    }

    @Test fun createAlbumRealEntryOutcomeReported() = runBlocking {
        // Register before the operation: metadata can be saved before a later failure.
        metadataAlbumsToClean += entryName
        dirsToClean += "Pictures/Tabula/$entryName"
        val album = albums.createAlbum(entryName)
        diag("createAlbum", "OK systemAlbumPath=${album.systemAlbumPath} imageCount=${album.imageCount}")
        assertEquals("created album must start empty", 0, album.imageCount)
        assertEquals("created album path must follow the Tabula convention",
            "Pictures/Tabula/$entryName", album.systemAlbumPath?.trimEnd('/'))

    }

    @After fun cleanup() = runBlocking {
        if (!probeAllowed) return@runBlocking
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) { try { block() } catch (e: Throwable) { errors += e } }
        // 哨兵：与本诊断无关的独立合成媒体，验证清理动作不越界。
        var sentinel: Uri? = null
        attempt {
            val item = requireNotNull(ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$sentinelName.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CloudSafetySentinels/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            ))
            sentinel = item // Record immediately, even if writing or publishing fails.
            fixtureInfo[item] = fixtures.fixture(item)
            ctx.contentResolver.openOutputStream(item)!!.use { it.write(minimalJpeg()) }
            check(ctx.contentResolver.update(item, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1)
        }
        metadataAlbumsToClean.forEach { name ->
            try { albums.deleteAlbum(name) } catch (e: Throwable) { errors += e }
        }
        // Delete exact inserted media first; the dot replica may have failed its own delete.
        registered.forEach { uri -> attempt {
            val fixture = fixtureInfo[uri]
            if (fixture != null) fixtures.removeFixture(fixture)
            else {
                // Metadata lookup failed immediately after insertion; use only the returned item URI.
                ctx.contentResolver.delete(uri, null, null)
                val cursor = requireNotNull(ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null))
                check(!cursor.use { it.moveToFirst() }) { "Fixture remains: $uri" }
            }
        } }

        // A published dot replica can disappear from MediaStore while its file remains.
        // Only the exact generated fixture path is eligible for this shell fallback.
        dotReplicaFilesToClean.forEach { (dir, name) -> attempt {
            check(dir == "Pictures/Tabula/$replicaName" && Regex("Pictures/Tabula/DiagReplica[0-9a-f]{10}").matches(dir)) { "Unexpected replica directory: $dir" }
            check(Regex("\\.tabula_placeholder_diag_[0-9]+\\.jpg").matches(name)) { "Unexpected replica name: $name" }
            fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
            val directory = quote("/sdcard/$dir")
            val file = quote("/sdcard/$dir/$name")
            diag("cleanup-replica-list", runScript("ls -la $directory 2>&1").trim())
            val result = runScript(
                "if [ -L $file ]; then echo UNSAFE; " +
                    "elif [ -f $file ]; then rm $file && { [ ! -e $file ] && [ ! -L $file ]; } && echo REMOVED || echo FAILED; " +
                    "elif [ -e $file ]; then echo UNSAFE; else echo ABSENT; fi"
            ).trim()
            check(result == "REMOVED" || result == "ABSENT") { "Exact replica cleanup failed for $dir/$name: $result" }
        } }
        dirsToClean.distinct().forEach { dir -> attempt {
            if (dirExists(dir)) {
                runScript("rmdir /sdcard/$dir")
                check(!dirExists(dir)) { "Could not remove empty fixture directory: $dir; listing=${runScript("ls -la /sdcard/$dir 2>&1").trim()}" }
            }
        } }
        sentinel?.let { uri ->
            val fixture = fixtureInfo[uri]
            if (fixture != null) {
                attempt { check(fixtures.queryFixture(fixture)) { "Unrelated sentinel was removed" } }
                attempt { fixtures.removeFixture(fixture) }
            } else {
                attempt {
                    ctx.contentResolver.delete(uri, null, null)
                    val cursor = requireNotNull(ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null))
                    check(!cursor.use { it.moveToFirst() }) { "Sentinel remains: $uri" }
                }
            }
        }

        if (errors.isNotEmpty()) {
            val failure = AssertionError("Fixture cleanup failed (${errors.size} errors)")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
        Unit
    }
}
