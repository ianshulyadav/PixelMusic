package com.unshoo.pixelmusic.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

@Composable
fun ImageCropView(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    pan: Offset = Offset.Zero,
    enabled: Boolean = true,
    onCrop: (Float, Offset) -> Unit
) {
    val density = LocalDensity.current
    
    val currentScaleState by rememberUpdatedState(scale)
    val currentPanState by rememberUpdatedState(pan)
    val currentOnCropState by rememberUpdatedState(onCrop)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        
        val bitmapW = imageBitmap.width.toFloat()
        val bitmapH = imageBitmap.height.toFloat()
        val bitmapRatio = if(bitmapH > 0) bitmapW / bitmapH else 1f
        val viewportRatio = if (viewportHeight > 0) viewportWidth / viewportHeight else 1f
        
        val (baseW, baseH) = if (bitmapRatio > viewportRatio) {
            val h = viewportHeight
            val w = h * bitmapRatio
            w to h
        } else {
            val w = viewportWidth
            val h = w / bitmapRatio
            w to h
        }
        
        val currentW = baseW * scale
        val currentH = baseH * scale
        
        val currentPanX = pan.x * viewportWidth
        val currentPanY = pan.y * viewportHeight
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(baseW, baseH, viewportWidth, viewportHeight) {
                            detectTransformGestures { _, panDelta, zoom, _ ->
                                val newScale = (currentScaleState * zoom).coerceAtLeast(1f).coerceAtMost(5f)
                                
                                val newW = baseW * newScale
                                val newH = baseH * newScale
                                val newMaxPanX = max(0f, (newW - viewportWidth) / 2f)
                                val newMaxPanY = max(0f, (newH - viewportHeight) / 2f)
                                
                                val panPxX = currentPanState.x * viewportWidth
                                val panPxY = currentPanState.y * viewportHeight

                                val newPanPxX = (panPxX + panDelta.x).coerceIn(-newMaxPanX, newMaxPanX)
                                val newPanPxY = (panPxY + panDelta.y).coerceIn(-newMaxPanY, newMaxPanY)
                                
                                val normX = if(viewportWidth > 0) newPanPxX / viewportWidth else 0f
                                val normY = if(viewportHeight > 0) newPanPxY / viewportHeight else 0f
                                
                                currentOnCropState(newScale, Offset(normX, normY))
                            }
                        }
                    } else Modifier
                )
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .requiredSize(
                        width = with(density) { baseW.toDp() },
                        height = with(density) { baseH.toDp() }
                    )
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = currentPanX
                        translationY = currentPanY
                    }
            )
        }
    }
}
