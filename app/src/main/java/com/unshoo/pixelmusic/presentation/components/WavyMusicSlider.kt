package com.unshoo.pixelmusic.presentation.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.util.lerp

/**
 * A custom slider with a wave effect that moves along the progress track.
 * The wave flattens when no music is playing or when the user interacts with the slider.
 * The thumb morphs from a circle into a vertical line when the user interacts with it.
 *
 * @param value The current slider value (between 0f and 1f)
 * @param onValueChange Callback invoked when the value changes
 * @param modifier Modifier to apply to this composable
 * @param enabled Whether the slider is enabled or not
 * @param valueRange Range of allowed values
 * @param onValueChangeFinished Callback invoked when interaction with the slider ends
 * @param interactionSource Interaction source for this slider
 * @param trackHeight Height of the slider track
 * @param thumbRadius Radius of the thumb
 * @param activeTrackColor Color of the active part of the track
 * @param inactiveTrackColor Color of the inactive part of the track
 * @param thumbColor Color of the thumb
 * @param waveAmplitude Wave amplitude
 * @param waveLength Wave length expressed in Dp. Controls the distance between the wave peaks
 *                   along the track.
 * @param animationDuration Duration of the wave animation in milliseconds
 * @param hideInactiveTrack Whether to hide the inactive part of the track that has already been traversed
 * @param isPlaying Whether the associated content is currently playing
 * @param thumbLineHeight Height of the thumb's vertical line when in the interaction state
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 80.dp,
    waveAnimationDuration: Int = 2000,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    isWaveEligible: Boolean = true,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    val phaseShiftAnim = remember { Animatable(0f) }
    val phaseShift = phaseShiftAnim.value

    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            while (shouldShowWave) {
                val start = (phaseShiftAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
                phaseShiftAnim.snapTo(start)
                phaseShiftAnim.animateTo(
                    targetValue = start + fullRotation,
                    animationSpec = tween(durationMillis = waveAnimationDuration, easing = LinearEasing)
                )
            }
        }
    }

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) {
        ((2 * PI) / waveLengthPx).toFloat()
    } else {
        0f
    }
    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }

    val hapticFeedback = LocalHapticFeedback.current
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val normalizedValue = if (valueRange.endInclusive == valueRange.start) {
        0f
    } else {
        ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }
    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValue = remember(normalizedValue, safeSemanticsStep) {
        ((normalizedValue / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
    }
    val semanticSliderValue = remember(semanticNormalizedValue, valueRange) {
        valueRange.start + semanticNormalizedValue * (valueRange.endInclusive - valueRange.start)
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val normalizedNew = if (valueRange.endInclusive == valueRange.start) {
                    0f
                } else {
                    ((newValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                }
                val currentStep = (normalizedNew * 50f).roundToInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
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
                            onValueChange(coerced)
                            onValueChangeFinished?.invoke()
                            true
                        }
                    }
                },
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)

                    val normalizedValue = clampedValue.let { v ->
                        if (valueRange.endInclusive == valueRange.start) 0f
                        else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(
                            0f,
                            1f
                        )
                    }
                    onDrawWithContent {
                        val currentProgressPxEndVisual =
                            localTrackStart + localTrackWidth * normalizedValue
                        if (hideInactiveTrackPortion) {
                            if (currentProgressPxEndVisual < localTrackEnd) {
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = Offset(currentProgressPxEndVisual, localCenterY),
                                    end = Offset(localTrackEnd, localCenterY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(localTrackStart, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        if (normalizedValue > 0f) {
                            val activeTrackVisualEnd =
                                currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                            if (waveAmplitudePxInternal > 0.01f && waveFrequency > 0f) {
                                wavePath.reset()
                                val waveStartDrawX = localTrackStart
                                val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
                                if (waveEndDrawX > waveStartDrawX) {
                                    val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                    val samplesPerCycle = 20f
                                    val waveStep = (periodPx / samplesPerCycle)
                                        .coerceAtLeast(1.2f)
                                        .coerceAtMost(trackHeightPx)

                                    fun yAt(x: Float): Float {
                                        val s = sin(waveFrequency * x + phaseShift)
                                        return (localCenterY + waveAmplitudePxInternal * s)
                                            .coerceIn(
                                                localCenterY - waveAmplitudePxInternal - trackHeightPx / 2f,
                                                localCenterY + waveAmplitudePxInternal + trackHeightPx / 2f
                                            )
                                    }

                                    var prevX = waveStartDrawX
                                    var prevY = yAt(prevX)
                                    wavePath.moveTo(prevX, prevY)

                                    var x = prevX + waveStep
                                    while (x < waveEndDrawX) {
                                        val y = yAt(x)
                                        val midX = (prevX + x) * 0.5f
                                        val midY = (prevY + y) * 0.5f
                                        wavePath.quadraticTo(prevX, prevY, midX, midY)
                                        prevX = x
                                        prevY = y
                                        x += waveStep
                                    }
                                    val endY = yAt(waveEndDrawX)
                                    wavePath.quadraticTo(prevX, prevY, waveEndDrawX, endY)

                                    drawPath(
                                        path = wavePath,
                                        color = activeTrackColor,
                                        style = Stroke(
                                            width = trackHeightPx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            miter = 1f
                                        )
                                    )
                                }
                            } else {
                                if (activeTrackVisualEnd > localTrackStart) {
                                    drawLine(
                                        color = activeTrackColor,
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeTrackVisualEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }


                        val currentThumbCenterX =
                            localTrackStart + localTrackWidth * normalizedValue
                        val thumbCurrentWidthPx =
                            lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbCurrentHeightPx = lerp(
                            thumbRadiusPx * 2f,
                            thumbLineHeightPxInternal,
                            thumbInteractionFraction
                        )

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(
                                currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                localCenterY - thumbCurrentHeightPx / 2f
                            ),
                            size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                            cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
                        )
                    }
                }
        )
    }
}
