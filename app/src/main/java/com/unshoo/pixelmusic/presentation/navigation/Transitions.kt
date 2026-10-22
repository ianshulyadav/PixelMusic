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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings

// Material 3 Expressive easing curves from material-components-android / M3 motion spec
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * M3 Expressive Shared-Axis / Container Transform entry for detail sheets (Playlists, Albums, Artists).
 * Calm zoom-in slide transition with very calm fade after the zoom-in initiates.
 */
fun enterTransition(): EnterTransition = slideInVertically(
    animationSpec = spring(
        dampingRatio = 0.84f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    initialOffsetY = { (it * 0.08f).toInt() }
) + scaleIn(
    animationSpec = spring(
        dampingRatio = 0.84f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin.Center
) + fadeIn(
    animationSpec = tween(
        durationMillis = 300,
        delayMillis = 85,
        easing = EmphasizedDecelerateEasing
    )
)

/**
 * Calm background screen transition when navigating forward to detail screen.
 * Zoom-out receding scale with calm fade out in lockstep.
 */
fun exitTransition(): ExitTransition = scaleOut(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin.Center
) + fadeOut(
    animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
)

/**
 * Background screen re-entry when popping back from detail screen.
 * Restores smoothly with zoom-in and very calm fade after spatial start.
 */
fun popEnterTransition(): EnterTransition = scaleIn(
    animationSpec = spring(
        dampingRatio = 0.84f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin.Center
) + fadeIn(
    animationSpec = tween(
        durationMillis = 300,
        delayMillis = 85,
        easing = EmphasizedDecelerateEasing
    )
)

/**
 * Fluid, calm dismiss for detail sheets (Playlists, Albums, Artists).
 * Glides down in lockstep with zoom-out and fade-out.
 */
fun popExitTransition(): ExitTransition = slideOutVertically(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    targetOffsetY = { (it * 0.08f).toInt() }
) + scaleOut(
    animationSpec = spring(
        dampingRatio = 1f,
        stiffness = ExpressiveSprings.DefaultSpatialStiffness
    ),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin.Center
) + fadeOut(
    animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
)

// Responsive settings transitions match Storage Sense's M3 Expressive container transform
fun settingsPopExitTransition(): ExitTransition = popExitTransition()

fun settingsPopEnterTransition(): EnterTransition = popEnterTransition()





