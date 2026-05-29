package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,

    isPlaying: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f, // Slower wave as requested

    waveAmplitudeWhenPlaying: Dp = 4.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, SmartImageEntryPoint::class.java)
    }
    val userPreferencesRepository = entryPoint.userPreferencesRepository()
    val performanceModeEnabled by userPreferencesRepository.performanceModeEnabledFlow.collectAsState(initial = false)

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
        else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValue = remember(normalizedValue, safeSemanticsStep) {
        ((normalizedValue / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
    }
    val semanticSliderValue = remember(semanticNormalizedValue, valueRange) {
        valueRange.start + semanticNormalizedValue * (valueRange.endInclusive - valueRange.start)
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    // Keep visual progress interpolation out of composition:
    // update this state on frame clock, then consume it only inside draw lambdas.
    // This preserves smooth visuals while avoiding high-frequency recompositions.
    val renderedNormalizedProgress = remember { mutableFloatStateOf(normalizedValue) }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(normalizedValue, isInteracting, enabled) {
        val target = normalizedValue.coerceIn(0f, 1f)
        if (!enabled || isInteracting) {
            renderedNormalizedProgress.floatValue = target
            lastProgressUpdateNanos = System.nanoTime()
            return@LaunchedEffect
        }

        val start = renderedNormalizedProgress.floatValue
        if (abs(start - target) > 0.1f) {
            renderedNormalizedProgress.floatValue = target
            lastProgressUpdateNanos = System.nanoTime()
            return@LaunchedEffect
        }

        val nowNanos = System.nanoTime()
        val intervalMs = if (lastProgressUpdateNanos == 0L) {
            180L
        } else {
            ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
        }
        lastProgressUpdateNanos = nowNanos

        if (abs(start - target) <= 0.0001f) {
            renderedNormalizedProgress.floatValue = target
            return@LaunchedEffect
        }

        val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
        var startFrameNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (startFrameNanos == 0L) startFrameNanos = frameNanos
            val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
            val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
            renderedNormalizedProgress.floatValue = start + (target - start) * fraction
            if (fraction >= 1f) break
        }
        renderedNormalizedProgress.floatValue = target
    }

    val containerHeight = max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValue,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit?.invoke(coerced)
                            ?: latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            progress = { renderedNormalizedProgress.floatValue },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                // Decorative layer: avoid duplicate semantics updates from the visual track.
                .clearAndSetSemantics { },
            color = activeTrackColor,
            trackColor = inactiveTrackColor,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = thumbRadius + 4.dp,
            stopSize = 3.dp,
            amplitude = { progress -> if (performanceModeEnabled) 0f else if (progress > 0f) animatedAmplitude else 0f },
            wavelength = wavelength,
            waveSpeed = if (performanceModeEnabled) 0.dp else waveSpeed
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2
            val renderedProgress = renderedNormalizedProgress.floatValue

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)
            
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, trackEdgePaddingPx) {
                    if (!enabled) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return valueRange.start +
                            normalized * (valueRange.endInclusive - valueRange.start)
                    }

                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPointerSeeking = true
                            down.consume()
                            var latestGestureValue = valueForX(down.position.x)
                            latestOnValueChange(latestGestureValue)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                if (change.position != change.previousPosition) {
                                    change.consume()
                                    latestGestureValue = valueForX(change.position.x)
                                    latestOnValueChange(latestGestureValue)
                                }
                            }

                            latestOnValueCommit?.invoke(latestGestureValue)
                                ?: latestOnValueChangeFinished?.invoke()
                        } finally {
                            isPointerSeeking = false
                        }
                    }
                }
        )
    }
}
