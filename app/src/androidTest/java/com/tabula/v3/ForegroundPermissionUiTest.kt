package com.tabula.v3

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tabula.v3.data.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.UUID

/**
 * C4/T07：应用回前台后按快照契约刷新可见列表——真实 UI 与真实 MediaStore。
 *
 * 覆盖（真实系统场景）：后台期间照片被外部删除，回前台后列表按快照契约
 * 更新（被删照片的卡片离树，其余照片保留）——前台重载 + deck 批次身份
 * 剪枝的全链设备证据。
 *
 * 已知边界（如实登记）：
 * - PARTIAL（READ_MEDIA_VISUAL_USER_SELECTED）的真实"用户选中集合"无法经
 *   pm 确定性夹具化（选择集由 MediaProvider 用户会话持有）——部分授权的
 *   设备级切换未在本套件执行；级别映射由单元测试钉住，刷新机制与级别
 *   无关（任何带权限回前台都触发）。
 * - 撤销权限（pm revoke/系统设置）路径不可经 in-process instrumentation
 *   执行：Android 对存储权限撤销会强停应用进程（CI 41706417934 双 API
 *   实证：revoke 后 instrumentation 随宿主进程死亡，Process crashed），
 *   强停后应用侧行为 = 用户重开 → onCreate 级别检查 → 权限门控（既有
 *   路径 + 单测级别映射）。真实撤销手工验收属既有真实设备 blocked 口径。
 */
@RunWith(AndroidJUnit4::class)
class ForegroundPermissionUiTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private var activity: android.app.Activity? = null
    private val photos = mutableListOf<Uri>()

    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use { p ->
        FileInputStream(p.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @Before fun setup() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("allowSyntheticMediaProbe") == "true")
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        // 探针每套件清应用数据：权限与引导态都从头来，先恢复到"已授权可进主界面"。
        shell("pm grant ${ctx.packageName} ${Manifest.permission.READ_MEDIA_IMAGES}")
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
        )
        AppPreferences(ctx).apply {
            hasCompletedOnboarding = true
            hasAcknowledgedDisclaimer = true
        }
        Unit
    }

    @After fun cleanup() {
        activity?.let { current ->
            ins.runOnMainSync { current.finish() }
            ins.waitForIdleSync()
        }
        photos.forEach { uri ->
            try {
                ctx.contentResolver.delete(primaryItem(uri), null, null)
            } catch (_: SecurityException) {
                // 权限已被测试撤销的收尾：经 shell 以自身身份补删。
                shell("content delete --uri $uri")
            }
        }
        photos.clear()
    }

    private fun primaryItem(uri: Uri): Uri = ContentUris.withAppendedId(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentUris.parseId(uri)
    )

    private fun photo(): Uri {
        val name = "fgperm-" + UUID.randomUUID() + ".jpg"
        val item = requireNotNull(ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ForegroundPermFixtures/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        photos += item
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(name.hashCode() or -0x1000000)
            ctx.contentResolver.openOutputStream(item)!!.use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally { bitmap.recycle() }
        check(ctx.contentResolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        return item
    }

    private fun displayNameOf(uri: Uri): String = requireNotNull(
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
    ) { "MediaStore query returned null: $uri" }.use {
        check(it.moveToFirst()) { "No row for $uri" }
        it.getString(0)
    }

    private fun launchToForeground() {
        val current = activity
        if (current != null && !current.isDestroyed) {
            // 回前台路径（C4/T07 设备证据修正，CI 41703684508 用例 2）：
            // startActivitySync 要求主线程进入空闲，回前台重组/卡片动画持续
            // 出帧时会 45s 超时——改用 am start 带回任务（已有任务回前台或
            // 进程被杀后冷启动），就绪判定交给 waitFor/waitUntilGone 轮询。
            shell("am start -n ${ctx.packageName}/com.tabula.v3.MainActivity")
            SystemClock.sleep(800)
        } else {
            activity = ins.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun background() {
        shell("input keyevent KEYCODE_HOME")
        SystemClock.sleep(1000)
    }

    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && (node.text?.toString()?.contains(label) == true || node.contentDescription?.toString()?.contains(label) == true)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }

    private fun captureUiFailure(label: String) {
        val root = ins.uiAutomation.rootInActiveWindow
        android.util.Log.e("FgPermUiFailure", "Timed out waiting for: $label root=${root?.packageName}")
    }

    private fun waitFor(label: String, timeout: Long = 20000): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            find(ins.uiAutomation.rootInActiveWindow, label)?.let { return it }
            SystemClock.sleep(100)
        }
        captureUiFailure(label)
        error("UI element not found: $label")
    }

    private fun waitUntilGone(label: String, timeout: Long = 20000) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (find(ins.uiAutomation.rootInActiveWindow, label) == null) return
            SystemClock.sleep(100)
        }
        captureUiFailure("still visible: $label")
        error("UI element still present: $label")
    }

    @Test
    fun externalDeleteWhileBackgroundedRefreshesListOnReturn() {
        val removed = photo()
        val kept = photo()
        val removedName = displayNameOf(removed)
        val keptName = displayNameOf(kept)

        launchToForeground()
        waitFor(removedName)
        waitFor(keptName)

        background()
        // 后台期间外部删除（自有合成媒体，经主卷 item URI 精确删除）
        assertEquals(1, ctx.contentResolver.delete(primaryItem(removed), null, null))
        photos.remove(removed)

        launchToForeground()
        // 回前台按快照契约刷新：被删照片消失，其余保留
        waitFor(keptName)
        waitUntilGone(removedName)
    }
    @Test
    fun externalEditWhileBackgroundedKeepsPhotoVisibleAfterReturn() {
        // C4/T07 任务 F：后台外部编辑（重写字节，DATE_MODIFIED 由 MediaStore
        // 派生）→ 回前台按快照契约刷新——身份存活行以最新对象替换（卡片
        // 缓存键含 DATE_MODIFIED）。可观察面：编辑确实落库（DATE_MODIFIED
        // 变化）、回前台后照片仍可见、应用可用不崩溃；卡片像素级刷新不属
        // UI dump 可验证面（如实登记，键语义由 ViewerHdRequestTest/
        // DeckViewerThumbnailKeyTest 钉住）。
        val photo = photo()
        val name = displayNameOf(photo)

        launchToForeground()
        waitFor(name)

        background()
        val before = dateModifiedOf(photo)
        // 已发布条的裸流写不保证 DATE_MODIFIED 立即派生（首跑 CI 41878788776
        // 实证）：按 photo() 同款 pending 发布舞蹈触发媒体扫描
        ctx.contentResolver.update(primaryItem(photo), ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }, null, null)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(-0x10000) // 与初始底色不同
            ctx.contentResolver.openOutputStream(primaryItem(photo), "w")!!.use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
        } finally {
            bitmap.recycle()
        }
        check(ctx.contentResolver.update(primaryItem(photo), ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) == 1)
        val after = dateModifiedOf(photo)
        assertTrue("编辑必须改变内容版本（DATE_MODIFIED 派生）", after > before)

        launchToForeground()
        // 回前台：全链重载+同步（存活行对象替换）后照片仍可见、应用可用
        waitFor(name)
        assertTrue(ctx.contentResolver.query(primaryItem(photo), null, null, null, null)!!.use { it.moveToFirst() })
    }

    private fun dateModifiedOf(uri: Uri): Long = requireNotNull(
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)
    ) { "MediaStore query returned null: $uri" }.use {
        check(it.moveToFirst()) { "No row for $uri" }
        it.getLong(0)
    }
}
