package com.tabula.v3

import android.app.Instrumentation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.FileInputStream

/** Exact synthetic-media cleanup for the two opt-in emulator UI tests. */
internal class CloudFixtureCleanup(private val ctx: Context, private val ins: Instrumentation) {
    data class Fixture(val uri: Uri, val volume: String, val id: Long, val name: String, val path: String)
    private fun present(uri: Uri): Boolean = requireNotNull(
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
    ) { "MediaStore query returned null: $uri" }.use { it.moveToFirst() }
    private fun shellScript(script: String): String {
        val encoded = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val command = "/system/bin/sh -c printf\${IFS}%s\${IFS}$encoded|base64\${IFS}-d|/system/bin/sh"
        return ins.uiAutomation.executeShellCommand(command).use { p ->
            FileInputStream(p.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }
    fun fixture(uri: Uri): Fixture {
        val columns = arrayOf(MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        return requireNotNull(ctx.contentResolver.query(uri, columns, null, null, null)) { "Null fixture metadata: $uri" }.use { cursor ->
            check(cursor.count == 1 && cursor.moveToFirst()) { "Expected one fixture metadata row: $uri" }
            Fixture(uri, requireNotNull(cursor.getString(0)), cursor.getLong(1),
                requireNotNull(cursor.getString(2)), requireNotNull(cursor.getString(3)))
        }.also {
            check(it.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY && it.id == ContentUris.parseId(uri))
            Log.i("CloudFixtureCleanup", "registered volume=${it.volume} id=${it.id} name=${it.name} path=${it.path}")
        }
    }
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    private fun content(command: String): String {
        val output = shellScript("$command 2>&1; code=\$?; printf '\\n__TABULA_EXIT=%s\\n' \$code")
        val match = Regex("(?s)^(.*)\\n__TABULA_EXIT=(\\d+)\\s*$").matchEntire(output)
            ?: error("Missing shell exit status: $output")
        val result = match.groupValues[1].trim()
        check(match.groupValues[2] == "0" && !Regex("(?i)exception|\\berror\\b|permission denial").containsMatchIn(result)) {
            "Shell content failed: $output"
        }
        return result
    }
    fun queryFixture(f: Fixture): Boolean {
        val item = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(f.volume), f.id)
        val result = content("content query --uri ${quote(item.toString())} --projection _id:_display_name:relative_path:volume_name")
        if (result == "No result found.") return false
        val expected = "Row: 0 _id=${f.id}, _display_name=${f.name}, relative_path=${f.path}, volume_name=${f.volume}"
        check(result == expected) { "Unexpected fixture query for $item: $result" }
        return true
    }
    fun removeFixture(f: Fixture) {
        val item = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(f.volume), f.id)
        val before = queryFixture(f)
        Log.i("CloudFixtureCleanup", "before=$before uri=$item name=${f.name} path=${f.path}")
        if (before) {
            val where = "_id=${f.id} AND _display_name='${f.name.replace("'", "''")}' AND relative_path='${f.path.replace("'", "''")}' AND volume_name='${f.volume.replace("'", "''")}'"
            content("content delete --uri ${quote(item.toString())} --where ${quote(where)}")
        }
        check(!queryFixture(f)) { "Fixture remains in shell query: $item" }
        check(!present(f.uri)) { "Fixture remains in app query: ${f.uri}" }
        Log.i("CloudFixtureCleanup", "after=false uri=$item")
    }
}
