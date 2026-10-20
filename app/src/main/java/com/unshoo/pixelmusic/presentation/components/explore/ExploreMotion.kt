package com.unshoo.pixelmusic.presentation.components.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * ============================================================================
 *  ExploreMotion — zero-recomposition motion toolkit
 * ============================================================================
 *
 *  Every effect in this file is applied through [graphicsLayer] and every
 *  animated value is read **inside** the layer lambda. Reading snapshot state
 *  inside `graphicsLayer { }` is a *deferred* read: it invalidates the render
 *  layer only, never triggers recomposition and never re-runs measure/layout.
 *
 *  That is the whole point. Naive scroll effects (`animateFloatAsState` read in
 *  composition) recompose every visible row on every scroll frame; at ~10
 *  visible rows that is ~600 recompositions/second for something that only
 *  needed to move pixels. This file moves pixels.
 *
 *  Rules for callers:
 *   1. Never read `state.x` in composition — pass the [Stable] holder down and
 *      read it inside a `graphicsLayer`/`drawWithContent` block.
 *   2. Lambdas (`() -> Float`) are fine to read in composition only when they
 *      are wrapped in `derivedStateOf` — still prefer the layer.
 * ============================================================================
 */

// ---------------------------------------------------------------------------
// 1. Scroll velocity / rubber-band stretch
// ---------------------------------------------------------------------------

/**
 * Holds the current scroll velocity derived from [rememberScrollStretch].
 * Mutated off the composition thread path, read only inside `graphicsLayer`.
 */
@Stable
class ScrollStretch {
    /** -1f (fast up) .. 1f (fast down). */
    var velocity by mutableFloatStateOf(0f)

    /** Horizontal scale applied while flinging. */
    var scaleX by mutableFloatStateOf(1f)

    /** Vertical scale applied while flinging. */
    var scaleY by mutableFloatStateOf(1f)
}

/**
 * Turns [listState] scroll deltas into a smoothed, normalised velocity and a
 * rubber-band stretch. When the fling settles the values ease back to rest.
 *
 * [maxStretch] is intentionally tiny (default 1.2%). Real physics-based lists
 * stretch imperceptibly; anything above ~2% reads as a bug.
 */
@Composable
fun rememberScrollStretch(
    listState: LazyListState,
    maxStretch: Float = 0.012f
): ScrollStretch {
    val state = remember { ScrollStretch() }

    LaunchedEffect(listState, maxStretch) {
        var lastPos = Long.MIN_VALUE
        var lastNanos = 0L
        var smoothed = 0f

        snapshotFlow {
            listState.firstVisibleItemIndex.toLong() * 1_000_000L +
                listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { pos ->
                // System.nanoTime(), not withFrameNanos: this collector must not
                // park waiting for a frame, otherwise a scroll that starts while
                // the app is otherwise idle is never measured.
                val nanos = System.nanoTime()
                if (lastPos != Long.MIN_VALUE && nanos > lastNanos) {
                    val dtMs = ((nanos - lastNanos) / 1_000_000f).coerceAtLeast(1f)
                    val pxPerMs = (pos - lastPos).toFloat() / dtMs
                    // ~3 px/ms on a 3x display is a hard fling; clamp there.
                    val target = (pxPerMs / 3f).coerceIn(-1f, 1f)
                    smoothed = smoothed * 0.55f + target * 0.45f
                }
                lastPos = pos
                lastNanos = nanos

                val magnitude = abs(smoothed)
                state.velocity = smoothed
                state.scaleY = 1f + magnitude * maxStretch
                state.scaleX = 1f - magnitude * maxStretch * 0.6f
            }
    }

    // Settle back to rest when the user stops.
    LaunchedEffect(listState, maxStretch) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) return@collect
                val startScaleX = state.scaleX
                val startScaleY = state.scaleY
                val startVelocity = state.velocity
                if (
                    abs(startScaleX - 1f) < 0.0005f &&
                    abs(startScaleY - 1f) < 0.0005f &&
                    abs(startVelocity) < 0.005f
                ) {
                    return@collect
                }
                val startNanos = withFrameNanos { it }
                while (true) {
                    val elapsed = (withFrameNanos { it } - startNanos) / 1_000_000f
                    val t = (elapsed / 220f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - t) * (1f - t)
                    state.scaleX = lerp(startScaleX, 1f, eased)
                    state.scaleY = lerp(startScaleY, 1f, eased)
                    state.velocity = lerp(startVelocity, 0f, eased)
                    if (t >= 1f) break
                }
            }
    }

    return state
}

/**
 * Applies the rubber-band stretch. Put this on the scroll container itself,
 * **not** on individual items — a container-level layer is one GPU surface.
 */
fun Modifier.scrollStretch(state: ScrollStretch): Modifier =
    this.graphicsLayer {
        scaleX = state.scaleX
        scaleY = state.scaleY
    }

// ---------------------------------------------------------------------------
// 2. Per-item depth transform while scrolling
// ---------------------------------------------------------------------------

/**
 * Scales, fades and (optionally) tilts a list item based on its distance from
 * the viewport centre. Reads [LazyListState.layoutInfo] inside `graphicsLayer`,
 * so it costs one layer evaluation per frame per item and **zero**
 * recompositions.
 *
 * [itemKey] must match the `key` passed to the lazy `item { }`. Matching on the
 * key (not the index) means the effect survives sections being added, removed or
 * conditionally rendered — an index-based version silently animates the wrong
 * row the first time a section is empty. If the key is not currently visible the
 * item is left untouched.
 */
@Composable
fun Modifier.itemScrollMotion(
    listState: LazyListState,
    itemKey: Any,
    shrink: Float = 0.05f,
    fade: Float = 0.22f,
    tiltDeg: Float = 2.5f,
    parallax: Float = 0.03f
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    return this.graphicsLayer {
        cameraDistance = 28f * density
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.key == itemKey }
            ?: return@graphicsLayer
        val viewportStart = layout.viewportStartOffset
        val viewportHeight =
            (layout.viewportEndOffset - viewportStart).coerceAtLeast(1)
        // `info.offset` is measured from the viewport start, which is negative
        // whenever the list has a top contentPadding. Normalise before comparing.
        val itemCentre = (info.offset - viewportStart) + info.size * 0.5f
        val viewportCentre = viewportHeight * 0.5f
        // -1 at top edge, 0 dead centre, +1 at bottom edge
        val distance =
            ((itemCentre - viewportCentre) / (viewportHeight * 0.5f)).coerceIn(-1.4f, 1.4f)
        val magnitude = abs(distance).coerceAtMost(1f)

        scaleX = 1f - magnitude * shrink
        scaleY = 1f - magnitude * shrink
        alpha = 1f - magnitude * fade
        rotationX = -distance * tiltDeg
        translationY = -distance * parallax * info.size
    }
}

// ---------------------------------------------------------------------------
// 3. Press feedback
// ---------------------------------------------------------------------------

/**
 * Spring-damped press scale. Respects Material's "respond on touch-down" rule:
 * the card shrinks while the finger is down, before onClick fires, so the tap
 * feels registered.
 *
 * The animation state is read inside `graphicsLayer`, so pressing/releasing
 * never recomposes the card — only its render layer.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.955f,
    bouncy: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale: State<Float> = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (bouncy) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            spring(stiffness = Spring.StiffnessMedium)
        },
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Depth-tilts an element toward the viewer while pressed. Used on genre chips
 * and the hero cards so a press reads as "pressing into" the surface.
 */
@Composable
fun Modifier.tiltOnPress(
    interactionSource: MutableInteractionSource,
    maxDegrees: Float = 5f,
    squash: Float = 0.018f
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val pressed by interactionSource.collectIsPressedAsState()
    val progress: State<Float> = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tiltProgress"
    )
    return this.graphicsLayer {
        cameraDistance = 22f * density
        val p = progress.value
        rotationX = -p * maxDegrees
        scaleX = 1f + p * squash
        scaleY = 1f - p * squash
    }
}

/**
 * Fires a single light haptic on touch-down. `LocalHapticFeedback` is already
 * swapped for a no-op when the user disables haptics in Settings (see
 * `MainActivity.MainUI`), so this respects that preference for free.
 */
@Composable
fun Modifier.hapticPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Modifier {
    val haptics = LocalHapticFeedback.current
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    return this
}

// ---------------------------------------------------------------------------
// 4. One-shot entrance reveal
// ---------------------------------------------------------------------------

/**
 * Fades + rises an item into place once, staggered by [index]. Runs on first
 * composition only (`LaunchedEffect(Unit)`), so it never re-fires when the user
 * scrolls back up — which is exactly the bug the naive `animateFloatAsState`
 * version has.
 */
@Composable
fun Modifier.revealOnEnter(
    index: Int,
    staggerMillis: Int = 45,
    travelDp: Float = 18f,
    durationMillis: Int = 320
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 10) * staggerMillis.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * travelDp * density
        val s = 0.975f + 0.025f * p
        scaleX = s
        scaleY = s
    }
}

// ---------------------------------------------------------------------------
// 5. Skeleton shimmer
// ---------------------------------------------------------------------------

/**
 * A single shimmer surface. The sweep is driven by an [androidx.compose.animation.core.InfiniteTransition]
 * whose value is read inside `graphicsLayer` — no recomposition per frame.
 *
 * Use this instead of a spinner for first paint: an instant layout that
 * *implies* content feels far faster than a centred `CircularProgressIndicator`,
 * because the layout never jumps when the data arrives.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep: State<Float> = transition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = sweep.value * size.width
                }
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to highlightColor.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
    }
}
