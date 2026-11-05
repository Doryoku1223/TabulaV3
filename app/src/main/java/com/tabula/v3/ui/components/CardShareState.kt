package com.tabula.v3.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tabula.v3.data.model.ImageFile

/** The stack supplies a live resolver so a click also checks the latest gesture state. */
class CardShareState {
    var isAvailable by mutableStateOf(false)
        private set
    private var resolve: () -> ImageFile? = { null }

    fun update(resolver: () -> ImageFile?) = update(resolver, resolver() != null)

    fun update(resolver: () -> ImageFile?, available: Boolean) {
        resolve = resolver
        isAvailable = available
    }

    fun snapshot(): ImageFile? = resolve()

    fun clear() {
        resolve = { null }
        isAvailable = false
    }
}
