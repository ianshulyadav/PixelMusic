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
 * Smooth 8% vertical glide + 0.96 scale-in + fast fade-in in 260ms without spring drag.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetY = { (it * 0.08f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.96f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = 180, easing = EmphasizedDecelerateEasing)
)

/**
 * Calm background screen transition when navigating forward.
 * Quick fade and subtle scale-out to 0.98 so rendering resources are released cleanly.
 */
fun exitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
) + scaleOut(
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.98f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
)

/**
 * Background screen re-entry when popping back from detail screen.
 * Restores smoothly from 0.98 scale with fast fade.
 */
fun popEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing)
) + scaleIn(
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.98f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
)

/**
 * Fluid, calm dismiss for detail sheets (Playlists, Albums, Artists).
 * Snappy 240ms exit gliding down 8% with 0.96 scale-out and fade.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = tween(durationMillis = 240, easing = EmphasizedAccelerateEasing),
    targetOffsetY = { (it * 0.08f).toInt() }
) + scaleOut(
    animationSpec = tween(durationMillis = 240, easing = EmphasizedAccelerateEasing),
    targetScale = 0.96f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = 180, easing = EmphasizedAccelerateEasing)
)

// Fast responsive settings dismiss transitions
private const val SETTINGS_DISMISS_TOTAL_MS = 280
private const val SETTINGS_DISMISS_FADE_MS = 220

fun settingsPopExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.80f).toInt() }
) + scaleOut(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetScale = 0.97f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedAccelerateEasing)
)

fun settingsPopEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.12f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.98f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedDecelerateEasing)
)



