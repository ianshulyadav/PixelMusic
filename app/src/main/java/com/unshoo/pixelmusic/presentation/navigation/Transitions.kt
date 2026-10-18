package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
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

const val TRANSITION_DURATION = 260

/**
 * M3 Expressive Shared-Axis Y entry for detail sheets (Playlists, Albums, Artists).
 * Smooth 6% vertical glide + fast fade-in without scaling jank or frame drops.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetY = { (it * 0.06f).toInt() }
) + fadeIn(
    animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing)
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
 * Restores smoothly with soft fade.
 */
fun popEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 200, delayMillis = 30, easing = EmphasizedDecelerateEasing)
)

/**
 * Fluid, calm dismiss for detail sheets (Playlists, Albums, Artists).
 * Glides down 6% with soothing fade.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = tween(durationMillis = 220, easing = EmphasizedAccelerateEasing),
    targetOffsetY = { (it * 0.06f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = 170, easing = EmphasizedAccelerateEasing)
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



