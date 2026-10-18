package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

// Expressive motion physics matching Material 3 Expressive guidelines
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * M3 Expressive Shared-Axis Y entry for detail sheets (Playlists, Albums, Artists).
 * Calm faded spring glide (5% vertical offset) without scaling jank or frame drops.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow
    ),
    initialOffsetY = { (it * 0.05f).toInt() }
) + fadeIn(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
)

/**
 * Calm background screen transition when navigating forward.
 * Soft fade so rendering resources are released cleanly without scale overhead.
 */
fun exitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
)

/**
 * Background screen re-entry when popping back from detail screen.
 * Restores smoothly with calm faded spring.
 */
fun popEnterTransition(): EnterTransition = fadeIn(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
)

/**
 * Fluid, calm dismiss for detail sheets (Playlists, Albums, Artists).
 * Glides down 5% with calm faded spring.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),
    targetOffsetY = { (it * 0.05f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
)

// Fast responsive settings dismiss transitions
private const val SETTINGS_DISMISS_TOTAL_MS = 260
private const val SETTINGS_DISMISS_FADE_MS = 200

fun settingsPopExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.20f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedAccelerateEasing)
)

fun settingsPopEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.08f).toInt() }
) + fadeIn(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedDecelerateEasing)
)



