package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Minimal In-View Card Revealing Modifier.
 *
 * Runs exclusively in the RenderNode/Draw phase via [graphicsLayer] using a single
 * lightweight spring progress float.
 * - Minimal, polished elevation glide (clamped to at most 8dp translationY)
 * - Subtle, calm opacity transition (0.75f -> 1.0f, never blank or popping)
 * - Minimal scale (0.985f -> 1.0f, no distortion)
 * - Zero onGloballyPositioned overhead, zero Rect allocations, zero CPU during steady scrolling.
 */
fun Modifier.scrollReveal(
    staggerIndex: Int = 0,
    initialOffsetY: Dp = 8.dp,
    initialScale: Float = 0.985f,
    key: Any? = null,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    var hasAnimated by rememberSaveable(key) { mutableStateOf(false) }
    if (hasAnimated) {
        return@composed this
    }

    var isTriggered by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        isTriggered = true
    }

    val density = LocalDensity.current
    val calmOffsetDp = initialOffsetY.coerceAtMost(8.dp)
    val slideDistancePx = remember(calmOffsetDp, density) {
        with(density) { calmOffsetDp.toPx() }
    }
    val safeScale = initialScale.coerceIn(0.98f, 1f)

    val progress by animateFloatAsState(
        targetValue = if (isTriggered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMediumLow
        ),
        finishedListener = {
            hasAnimated = true
        },
        label = "scroll_reveal_progress"
    )

    this.graphicsLayer {
        if (progress >= 0.999f) {
            this.alpha = 1f
            this.translationY = 0f
            this.scaleX = 1f
            this.scaleY = 1f
        } else {
            this.alpha = 0.75f + (0.25f * progress)
            this.translationY = slideDistancePx * (1f - progress)
            val s = safeScale + ((1f - safeScale) * progress)
            this.scaleX = s
            this.scaleY = s
        }
    }
}
