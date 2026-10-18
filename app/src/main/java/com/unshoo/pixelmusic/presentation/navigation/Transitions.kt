package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings

// Material 3 Expressive easing curves from material-components-android / M3 motion spec
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * M3 Expressive Shared-Axis Y entry for detail sheets (Playlists, Albums, Artists).
 * Calm organic glide with M3 Expressive spatial & effects spring physics.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = spring(
        dampingRatio = 0.84f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    initialOffsetY = { (it * 0.08f).toInt() }
) + fadeIn(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultEffectsStiffness
    )
)

/**
 * Calm background screen transition when navigating forward to detail screen.
 * Soft fade in lockstep with entry to avoid brightness flashes.
 */
fun exitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 180, easing = EmphasizedAccelerateEasing)
)

/**
 * Background screen re-entry when popping back from detail screen.
 * Restores smoothly with M3 Expressive effects spring.
 */
fun popEnterTransition(): EnterTransition = fadeIn(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultEffectsStiffness
    )
)

/**
 * Fluid, calm dismiss for detail sheets (Playlists, Albums, Artists).
 * Glides down in lockstep with background restore.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    targetOffsetY = { (it * 0.08f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = 180, easing = EmphasizedAccelerateEasing)
)

// Responsive settings transitions with M3 Expressive easing curves
private const val SETTINGS_TOTAL_MS = 240
private const val SETTINGS_FADE_MS = 180

fun settingsPopExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.16f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = SETTINGS_FADE_MS, easing = EmphasizedAccelerateEasing)
)

fun settingsPopEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.08f).toInt() }
) + fadeIn(
    animationSpec = tween(durationMillis = SETTINGS_FADE_MS, easing = EmphasizedDecelerateEasing)
)




