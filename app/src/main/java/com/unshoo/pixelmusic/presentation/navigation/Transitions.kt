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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

// Expressive motion physics matching Mood & Genre preview page
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val TRANSITION_DURATION = 260

/**
 * Smooth and calm fluid entry matching Mood & Genre preview opening.
 * Slides up gently from it / 6 with smooth, calm spring physics and fade, without aggressive scaling.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    ),
    initialOffsetY = { it / 6 }
) + fadeIn(
    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
)

/**
 * Calm background screen transition when navigating forward.
 * Stays stable with subtle gentle fade so it does not jerk or shrink.
 */
fun exitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
)

/**
 * Background screen re-entry when popping back from detail screen.
 */
fun popEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
)

/**
 * Fluid, calm dismiss matching Mood & Genre preview closing.
 * Drops down smoothly to it / 5 with linear fade and no jarring shrink.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    ),
    targetOffsetY = { it / 5 }
) + fadeOut(
    animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing)
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



