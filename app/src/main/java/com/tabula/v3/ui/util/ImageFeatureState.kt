package com.tabula.v3.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tabula.v3.data.model.ImageFeatures
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.ImageFeatureDetector

@Composable
fun rememberImageFeatures(
    image: ImageFile,
    enableHdr: Boolean,
    enableMotion: Boolean
): ImageFeatures? {
    val context = LocalContext.current
    var features by remember(image.id, enableHdr, enableMotion) {
        mutableStateOf<ImageFeatures?>(null)
    }

    LaunchedEffect(image.id, enableHdr, enableMotion) {
        if (!enableHdr && !enableMotion) {
            features = null
            return@LaunchedEffect
        }

        features = ImageFeatureDetector.detect(
            context = context,
            uri = image.uri,
            checkHdr = enableHdr,
            checkMotion = enableMotion
        )
    }

    return features
}
