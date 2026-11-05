package com.tabula.v3.permission

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The callback is transient; a restored request's result triggers journal reconciliation instead. */
internal class DeletePermissionSession(savedState: Bundle?) {
    var inFlight by mutableStateOf(savedState?.getBoolean(KEY, false) ?: false)
        private set
    var recoveryRevision by mutableIntStateOf(0)
        private set
    private var callback: ((Boolean) -> Unit)? = null

    fun launch(result: (Boolean) -> Unit, launchSystem: () -> Unit) {
        if (inFlight) {
            result(false)
            return
        }
        inFlight = true
        callback = result
        try {
            launchSystem()
        } catch (e: Exception) {
            callback = null
            inFlight = false
            throw e
        }
    }

    fun complete(granted: Boolean) {
        val target = callback
        callback = null
        inFlight = false
        if (target == null) recoveryRevision++
        target?.invoke(granted)
    }

    fun save(out: Bundle) { out.putBoolean(KEY, inFlight) }

    companion object { private const val KEY = "tabula.deletePermissionInFlight" }
}
