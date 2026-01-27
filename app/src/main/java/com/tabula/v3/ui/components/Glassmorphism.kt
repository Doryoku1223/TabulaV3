package com.tabula.v3.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun FrostedGlass(
    modifier: Modifier = Modifier,
    shape: Shape,
    blurRadius: Dp,
    tint: Color,
    noiseAlpha: Float = 0.06f,
    borderBrush: Brush? = null,
    borderWidth: Dp = 0.dp,
    highlightBrush: Brush? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val noiseBrush = rememberNoiseBrush()

    Layout(
        content = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .background(tint)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(noiseBrush, alpha = noiseAlpha)
                        }
                    }
            )

            if (highlightBrush != null) {
                Box(
                    modifier = Modifier
                        .background(highlightBrush)
                )
            }

            Box(
                contentAlignment = contentAlignment
            ) {
                content()
            }
        },
        modifier = modifier
            .clip(shape)
            .then(
                if (borderBrush != null && borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderBrush, shape)
                } else {
                    Modifier
                }
            )
    ) { measurables, constraints ->
        val contentMeasurable = measurables.last()
        val contentPlaceable = contentMeasurable.measure(constraints)
        val width = contentPlaceable.width
        val height = contentPlaceable.height
        val fixed = Constraints.fixed(width, height)

        val backgroundPlaceable = measurables[0].measure(fixed)
        val highlightPlaceable = if (measurables.size == 3) {
            measurables[1].measure(fixed)
        } else {
            null
        }

        layout(width, height) {
            backgroundPlaceable.place(0, 0)
            highlightPlaceable?.place(0, 0)
            contentPlaceable.place(0, 0)
        }
    }
}

@Composable
private fun rememberNoiseBrush(): Brush {
    val noise = remember { createNoiseBitmap(64) }
    return remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }
}

private fun createNoiseBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val random = Random(0)

    for (i in pixels.indices) {
        val alpha = random.nextInt(0, 38)
        pixels[i] = AndroidColor.argb(alpha, 255, 255, 255)
    }

    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
