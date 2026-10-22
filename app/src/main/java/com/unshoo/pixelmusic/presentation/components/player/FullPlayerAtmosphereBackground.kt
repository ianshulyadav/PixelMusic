package com.unshoo.pixelmusic.presentation.components.player

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme

// Smooth 850ms easing for fluid, liquid-like atmospheric color glide on song transition
private val AtmosphereEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

/**
 * Dynamic album-art-based atmospheric blur background for the player sheet (mini and full).
 *
 * Light Theme:
 * - Anchored 100% to the bottom sheet color (albumColorScheme.primaryContainer)
 *   with vibrant hardware Gaussian blur of the artwork, with no white gradient wash or dimming.
 *
 * Dark Theme:
 * - Vibrant hardware Gaussian blur of the artwork over the solid tinted base,
 *   with no muddy dark overlay. Clean contrast scrim only at the bottom controls.
 *
 * Transitions:
 * - Liquid-smooth 850ms crossfade with zero alpha dip (retains outgoing art until incoming completes).
 * - Upcoming song artwork is proactively cached by PlayerViewModel for instant cache hits.
 *
 * Mini player:
 * - Preserved untouched in both themes.
 */
@Composable
fun FullPlayerAtmosphereBackground(
    currentSong: Song?,
    albumColorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = LocalPixelMusicDarkTheme.current,
    expansionFractionProvider: (() -> Float)? = null,
    showTopVignette: Boolean = true
) {
    val context = LocalContext.current
    val albumArtUri = currentSong?.albumArtUriString

    // Zero-dip smooth crossfade state: retains outgoing art underneath at 100% opacity
    // while incoming art fades in on top over 850ms, eliminating any background color flash/dip
    var displayedArtUri by remember { mutableStateOf(albumArtUri) }
    var previousArtUri by remember { mutableStateOf<String?>(null) }
    val crossfadeProgress = remember { Animatable(1f) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri != displayedArtUri) {
            previousArtUri = displayedArtUri
            displayedArtUri = albumArtUri
            crossfadeProgress.snapTo(0f)
            crossfadeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = AtmosphereEasing)
            )
            previousArtUri = null
        }
    }

    // Smooth crossfade on song / theme change matching blur animation
    val animSpec = remember { tween<Color>(durationMillis = 850, easing = AtmosphereEasing) }
    val animatedBaseColor by animateColorAsState(albumColorScheme.primaryContainer, animSpec, label = "atmo_base_color")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBaseColor)
    ) {

        // 2. REAL VIBRANT GAUSSIAN BLURRED ALBUM ARTWORK LAYER WITH LIQUID CROSSFADE
        // Perf (high refresh rate): the artwork below is blurred by 52-60dp,
        // which destroys all fine detail, so it is decoded at 256px instead of
        // 512px. That is ~4x fewer pixels for the GPU to scale/blur on every
        // frame of the sheet expansion animation — invisible visually, but
        // the difference between hitting and missing the 8.3ms frame budget
        // at 120Hz. The sharp (unblurred) artwork elsewhere is unaffected.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val expansionFraction = expansionFractionProvider?.invoke() ?: 1f
                    scaleX = 1.20f
                    scaleY = 1.20f
                    alpha = if (isDarkTheme) {
                        lerpFloat(0.45f, 0.65f, expansionFraction)
                    } else {
                        lerpFloat(0.18f, 0.45f, expansionFraction)
                    }
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(if (isDarkTheme) 52.dp else 60.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            // Previous artwork held at 1.0f alpha underneath: eliminates Porter-Duff crossfade alpha dip
            if (!previousArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previousArtUri)
                        .size(256, 256)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Incoming artwork dissolving in on top from 0f -> 1f
            if (!displayedArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayedArtUri)
                        .size(256, 256)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (previousArtUri != null) crossfadeProgress.value else 1f
                        }
                )
            }
        }

        // 3. CONTRAST & HARMONIZATION LAYERS
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val expansionFraction = expansionFractionProvider?.invoke() ?: 1f
                    val fullPlayerAlpha = ((expansionFraction - 0.20f) / 0.80f).coerceIn(0f, 1f)
                    val scrimAlpha = ((expansionFraction - 0.30f) / 0.70f).coerceIn(0f, 1f)

                    if (!isDarkTheme) {
                        // Light theme: gentle harmonization wash of the player sheet solid color (primaryContainer)
                        // over the blur so dark artwork details are harmonized without muting the blur.
                        // In mini player (fullPlayerAlpha == 0), this is completely inactive.
                        if (fullPlayerAlpha > 0f) {
                            drawRect(
                                color = albumColorScheme.primaryContainer.copy(alpha = 0.20f * fullPlayerAlpha)
                            )
                        }
                    } else {
                        // Dark theme: deep atmosphere tint restoring rich dark depth in full player
                        // In mini player (fullPlayerAlpha == 0), this is completely inactive.
                        if (fullPlayerAlpha > 0f) {
                            drawRect(
                                color = Color(0xFF090D14).copy(alpha = 0.28f * fullPlayerAlpha)
                            )

                            // Top subtle vignette for header contrast in full player only
                            if (showTopVignette) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.18f * fullPlayerAlpha),
                                        0.35f to Color.Transparent,
                                        1.0f to Color.Transparent
                                    )
                                )
                            }
                        }

                        if (scrimAlpha > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.65f to Color.Transparent,
                                    0.82f to Color.Black.copy(alpha = 0.42f * scrimAlpha),
                                    0.92f to Color.Black.copy(alpha = 0.68f * scrimAlpha),
                                    1.0f to Color(0xFF090D13).copy(alpha = 0.82f * scrimAlpha)
                                )
                            )
                        }
                    }
                }
        )
    }
}
