package com.unshoo.pixelmusic.presentation.components.subcomps

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.sin

/**
 * Composable that draws a horizontal line with a sine-wave ripple.
 *
 * @param modifier Modifier for the Composable.
 * @param color Line color.
 * @param alpha Opacity (0f..1f).
 * @param strokeWidth Line thickness (Dp).
 * @param amplitude Wave amplitude (Dp) — the maximum height from the center.
 * @param waves Number of complete waves across the width (e.g. 1f = one wave).
 * @param phase Static phase shift (radians). Used only if animate = false.
 * @param animate If true, enables an infinite scrolling animation.
 * @param animationDurationMillis Duration in milliseconds of one full animation cycle.
 * @param samples Number of points used to draw the curve (more = smoother).
 * @param cap Line cap type (Round, Butt, Square).
 */
@Composable
fun SineWaveLine(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 1f,
    strokeWidth: Dp = 2.dp,
    amplitude: Dp = 8.dp,
    waves: Float = 2f,
    phase: Float = 0f,
    animate: Boolean? = false,
    animationDurationMillis: Int = 2000,
    samples: Int = 400,
    cap: StrokeCap = StrokeCap.Round
) {
    val density = LocalDensity.current

    val currentPhase = if (animate == true) {
        val infiniteTransition = rememberInfiniteTransition(label = "SineWaveAnimation")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = animationDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phaseAnimation"
        )
        animatedPhase
    } else {
        phase
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val strokePx = with(density) { strokeWidth.toPx() }
        val ampPx = with(density) { amplitude.toPx() }

        if (w <= 0f || samples < 2) return@Canvas

        val path = Path().apply {
            val step = w / (samples - 1)
            moveTo(0f, centerY + (ampPx * sin(currentPhase)))
            for (i in 1 until samples) {
                val x = i * step
                val theta = (x / w) * (2f * PI.toFloat() * waves) + currentPhase
                val y = centerY + ampPx * sin(theta)
                lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = cap,
                join = StrokeJoin.Round
            ),
            alpha = alpha
        )
    }
}

/**
 * Static usage example:
 *
 * SineWaveLine(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .height(28.dp),
 *     color = Color(0xFF00AEEF),
 *     alpha = 0.95f,
 *     strokeWidth = 3.dp,
 *     amplitude = 10.dp,
 *     waves = 1.6f
 * )
 */
