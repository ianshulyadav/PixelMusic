package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * High-performance RenderNode-backed bounce click and hover modifier.
 * Runs spring physics purely in the Draw/RenderNode phase via graphicsLayer,
 * guaranteeing zero recomposition of card content and zero layout passes.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    hoverScale: Float = 1.015f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val targetScale = when {
        isPressed -> scaleDown
        isHovered -> hoverScale
        else -> 1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce_click_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {
            onClick()
        }
}

/**
 * Subtle scale-and-fade entry transition for Tooltips, Popovers, and Dropdowns.
 */
fun subtleScaleFadeEnter(
    initialScale: Float = 0.92f,
    durationMs: Int = 180
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
) + scaleIn(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),
    initialScale = initialScale,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
)

/**
 * Subtle scale-and-fade exit transition for Tooltips, Popovers, and Dropdowns.
 */
fun subtleScaleFadeExit(
    targetScale: Float = 0.95f,
    durationMs: Int = 140
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing)
) + scaleOut(
    animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
    targetScale = targetScale,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
)
