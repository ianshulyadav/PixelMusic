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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Material 3 Expressive In-View / Scroll-Triggered Revealing Modifier (IntersectionObserver).
 *
 * Precisely as elements cross the viewport threshold during scroll:
 * - Fades up smoothly (alpha: 0f -> 1f, no overshoot as per MD3 effects spec)
 * - Slides in vertically (translationY: initialOffsetY -> 0f, subtle spatial spring)
 * - Scales / stretches into view (scale: initialScale -> 1.0f, with expressive spring overshoot)
 *
 * Runs exclusively in the Draw/RenderNode phase via [graphicsLayer], ensuring 120Hz fluid
 * scrolling with ZERO recompositions and zero remeasurements.
 */
fun Modifier.scrollReveal(
    staggerIndex: Int = 0,
    initialOffsetY: Dp = 24.dp,
    initialScale: Float = 0.94f,
    key: Any? = null,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    var hasEntered by rememberSaveable(key) { mutableStateOf(false) }
    var isAnimationTriggered by remember(key) { mutableStateOf(hasEntered) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val slideDistancePx = remember(initialOffsetY, density) {
        with(density) { initialOffsetY.toPx() }
    }

    // Trigger animation with subtle stagger when entering viewport
    LaunchedEffect(hasEntered, staggerIndex) {
        if (hasEntered && !isAnimationTriggered) {
            val delayMillis = (staggerIndex.coerceIn(0, 6) * 28).toLong()
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            isAnimationTriggered = true
        }
    }

    // Failsafe: if the node is placed on screen, guarantee visibility after 450ms
    LaunchedEffect(Unit) {
        if (!hasEntered) {
            delay(450)
            hasEntered = true
            isAnimationTriggered = true
        }
    }

    // Material 3 Expressive spring physics
    // Effects (opacity): no overshoot, smooth fade
    val alpha by animateFloatAsState(
        targetValue = if (isAnimationTriggered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scroll_reveal_alpha"
    )

    // Spatial (translationY & scale): calm, fluid glide into place
    val translationY by animateFloatAsState(
        targetValue = if (isAnimationTriggered) 0f else slideDistancePx,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessLow
        ),
        label = "scroll_reveal_translationY"
    )

    val scale by animateFloatAsState(
        targetValue = if (isAnimationTriggered) 1f else initialScale,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessLow
        ),
        label = "scroll_reveal_scale"
    )

    this
        .onGloballyPositioned { coordinates ->
            if (!hasEntered && coordinates.isAttached) {
                val bounds = coordinates.boundsInWindow()
                val isInVerticalView = bounds.bottom > 0f && bounds.top < screenHeightPx * 0.98f
                val isInHorizontalView = bounds.right > 0f && bounds.left < screenWidthPx * 1.02f
                if (isInVerticalView && isInHorizontalView) {
                    hasEntered = true
                }
            }
        }
        .graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
            this.scaleX = scale
            this.scaleY = scale
        }
}
