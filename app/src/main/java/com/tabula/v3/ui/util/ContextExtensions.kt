package com.tabula.v3.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
