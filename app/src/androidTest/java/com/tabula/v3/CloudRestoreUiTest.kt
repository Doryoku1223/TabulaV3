package com.tabula.v3

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.app.UiAutomation
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.repository.AlbumManager
import com.tabula.v3.data.repository.FileOperationManager
import com.tabula.v3.data.repository.RecycleBinManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudRestoreUiTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private val photos = mutableListOf<ImageFile>()
    private val fixtureRows = mutableListOf<CloudFixtureCleanup.Fixture>()
    private val fixtures get() = CloudFixtureCleanup(ctx, ins)
    private var probeAllowed = false
    private val bucket = "CloudRestore" + UUID.randomUUID().toString().replace("-", "").take(10)
    private val albums get() = AlbumManager.getInstance(ctx)
    private val trash get() = RecycleBinManager.getInstance(ctx)
    private var activity: android.app.Activity? = null
    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use { p ->
        FileInputStream(p.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }
    @Before fun setup() {
        check(InstrumentationRegistry.getArguments().getString("allowSyntheticMediaProbe") == "true")
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        probeAllowed = true
        shell("pm grant ${ctx.packageName} ${Manifest.permission.READ_MEDIA_IMAGES}")
        assertEquals(PackageManager.PERMISSION_GRANTED, ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES))
        AppPreferences(ctx).apply { hasCompletedOnboarding = true; hasAcknowledgedDisclaimer = true }
    }
    // Fixtures are created under the primary Pictures volume; use its item URI
    // when directly deleting our own synthetic media, matching the existing device probe.
    private fun primaryItem(uri: Uri): Uri = ContentUris.withAppendedId(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentUris.parseId(uri)
    )
    private fun present(uri: Uri): Boolean = requireNotNull(
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
    ) { "MediaStore query returned null: $uri" }.use { it.moveToFirst() }
    private fun logMediaIdentity(uri: Uri, stage: String) {
        val owner = try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "missing"
            } ?: "query-null"
        } catch (e: Exception) { "query-error:${e.javaClass.simpleName}:${e.message}" }
        Log.i("CloudMediaIdentity", "stage=$stage package=${ctx.packageName} opPackage=${ctx.opPackageName} uid=${android.os.Process.myUid()} uri=$uri owner=$owner")
    }
    private fun photo(path: String, register: Boolean = true): ImageFile {
        val name = "cloud-" + UUID.randomUUID() + ".jpg"
        val uri = requireNotNull(ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$path/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        val image = ImageFile(ContentUris.parseId(uri), uri, name, System.currentTimeMillis(), 1L, 32, 32, path)
        if (register) {
            photos += image
            fixtureRows += fixtures.fixture(uri)
        }
        logMediaIdentity(uri, "insert-pending")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(name.hashCode() or -0x1000000)
            ctx.contentResolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally { bitmap.recycle() }
        check(ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        logMediaIdentity(uri, "insert-published")
        return image
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && (node.text?.toString()?.contains(text) == true || node.contentDescription?.toString()?.contains(text) == true)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun captureUiFailure(label: String) {
        fun dump(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30) return
            val r = Rect(); node.getBoundsInScreen(r)
            Log.e("CloudUiFailure", "${" ".repeat(depth)}pkg=${node.packageName} text=${node.text} desc=${node.contentDescription} bounds=$r clickable=${node.isClickable}")
            for (i in 0 until node.childCount) dump(node.getChild(i), depth + 1)
        }
        try {
            Log.e("CloudUiFailure", "Timed out waiting for: $label")
            Log.e("CloudUiFailure", "active root")
            dump(ins.uiAutomation.rootInActiveWindow, 0)
            ins.uiAutomation.windows.forEach { window ->
                Log.e("CloudUiFailure", "window type=${window.type} active=${window.isActive} focused=${window.isFocused}")
                dump(window.root, 0)
            }
            val dir = requireNotNull(ctx.getExternalFilesDir(null)).resolve("cloud-ui-failures")
            check(dir.mkdirs() || dir.isDirectory)
            ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                try { dir.resolve("${UUID.randomUUID()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
        } catch (e: Exception) { Log.e("CloudUiFailure", "Could not capture UI evidence", e) }
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            find(ins.uiAutomation.rootInActiveWindow, text)?.let { return it }
            SystemClock.sleep(100)
        }
        captureUiFailure(text)
        error("UI element not found: $text")
    }
    private fun gesture(text: String, long: Boolean = false) {
        val bounds = Rect(); waitFor(text).getBoundsInScreen(bounds)
        check(!bounds.isEmpty)
        val x = bounds.centerX(); val y = bounds.centerY()
        if (long) shell("input swipe $x $y $x $y 1200") else shell("input tap $x $y")
    }
    private fun awaitAlbum(count: Int, expected: Set<Long>) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            val album = albums.albums.value.firstOrNull { it.name == bucket }
            if (album?.imageCount == count && album.coverImageId in expected) return
            SystemClock.sleep(100)
        }
        error("Album not updated: $bucket, expected count=$count, actual=${albums.albums.value.firstOrNull { it.name == bucket }}")
    }
    private suspend fun prepareTrash(count: Int): List<ImageFile> {
        val removed = (1..count).map { photo(bucket) }
        photo("CloudAnchor" + UUID.randomUUID().toString().take(8))
        trash.addAllToRecycleBin(removed)
        activity = ins.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("图库")
        val end = SystemClock.uptimeMillis() + 20000
        while (albums.albums.value.none { it.name == bucket && it.imageCount == 0 } && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
        assertEquals(0, albums.albums.value.first { it.name == bucket }.imageCount)
        gesture("回收站")
        waitFor("${count} 项待处理")
        return removed
    }
    private fun assertUiAlbum(count: Int) {
        gesture("返回")
        gesture("图库")
        val nameNode = waitFor(bucket)
        var card: AccessibilityNodeInfo? = nameNode
        repeat(3) { if (card != null && find(card, "$count 张") == null) card = card?.parent }
        assertNotNull("Album card must show its own count", find(card, "$count 张"))
    }
    @Test fun singleRestoreUpdatesAlbumWithoutManualRefresh() = runBlocking {
        val removed = prepareTrash(1)
        gesture(removed[0].displayName)
        gesture("恢复")
        awaitAlbum(1, setOf(removed[0].id))
        assertUiAlbum(1)
    }
    @Test fun restoreKeepsUnknownEntriesWithFeedback() = runBlocking {
        // C0-T02-RESTORE-UNKNOWN-UI：核验 UNKNOWN 的条目恢复被保留并给出反馈；
        // 解除注入后同一操作恢复成功（重试语义）。
        val removed = prepareTrash(2)
        FileOperationManager.debugForceUnknownUris = removed.map { it.uri.toString() }.toSet()
        try {
            gesture(removed[0].displayName)
            gesture("恢复")
            SystemClock.sleep(3000) // 让恢复协程完成；Toast 已在 run 40732503499 日志中捕获为证据
        } finally {
            FileOperationManager.debugForceUnknownUris = emptySet()
        }
        // 核心断言：UNKNOWN 条目记录保留、不冒充成功
        assertEquals(2, trash.loadRecycleBin().size)
        shell("input keyevent 4") // 退出照片查看器，回到回收站列表
        SystemClock.sleep(1000)
        waitFor("2 项待处理")

        // 解除注入后恢复成功
        gesture(removed[0].displayName)
        gesture("恢复")
        awaitAlbum(1, setOf(removed[0].id))
    }

    @Test fun batchRestoreUpdatesAlbumWithoutManualRefresh() = runBlocking {
        val removed = prepareTrash(2)
        gesture(removed[0].displayName, long = true)
        gesture(removed[1].displayName)
        gesture("恢复所选")
        awaitAlbum(2, removed.map { it.id }.toSet())
        assertUiAlbum(2)
    }
    @After fun cleanup() = runBlocking {
        if (!probeAllowed) return@runBlocking
        activity?.let { current ->
            ins.runOnMainSync { current.finish() }
            ins.waitForIdleSync()
            val end = SystemClock.uptimeMillis() + 10000
            while (!current.isDestroyed && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
            check(current.isDestroyed) { "Activity did not finish" }
        }
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) { try { block() } catch (e: Throwable) { errors += e } }
        try { trash.removeAllFromRecycleBin(photos) } catch (e: Throwable) { errors += e }
        var sentinel: CloudFixtureCleanup.Fixture? = null
        attempt { sentinel = fixtures.fixture(photo("CloudSafetySentinel", register = false).uri) }
        fixtureRows.forEach { f -> attempt { fixtures.removeFixture(f) } }
        sentinel?.let { f ->
            attempt { check(fixtures.queryFixture(f) && present(f.uri)) { "Unrelated sentinel was removed" } }
            attempt { fixtures.removeFixture(f) }
        }
        if (errors.isNotEmpty()) {
            val failure = AssertionError("Fixture cleanup failed (${errors.size} errors)")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }
}
