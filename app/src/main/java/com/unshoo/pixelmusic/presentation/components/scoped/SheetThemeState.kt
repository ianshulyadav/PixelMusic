package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.ThemePreference
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair

/**
 * Theme state for the player sheet.
 *
 * Expansion-dependent values ([miniAlpha], elevation) are **no longer** included here.
 * They are computed inline in the consuming composable's `graphicsLayer` / `derivedStateOf`,
 * reading directly from the [Animatable] expansion fraction during the draw phase.
 * This eliminates per-frame recomposition that the old [Transition]-based approach caused.
 */
internal data class SheetThemeState(
    val albumColorScheme: ColorScheme,
    val miniPlayerScheme: ColorScheme,
    val isPreparingPlayback: Boolean,
    val miniReadyAlpha: Float,
    val miniAppearScale: Float,
    val playerAreaBackground: Color
)

internal fun resolvePlayerSheetTargetScheme(
    isAlbumArtTheme: Boolean,
    hasAlbumArt: Boolean,
    currentSongActiveScheme: ColorScheme?,
    lastAlbumScheme: ColorScheme?,
    systemColorScheme: ColorScheme
): ColorScheme {
    return when {
        !isAlbumArtTheme || !hasAlbumArt -> systemColorScheme
        currentSongActiveScheme != null -> currentSongActiveScheme
        lastAlbumScheme != null -> lastAlbumScheme
        else -> systemColorScheme
    }
}

@Composable
internal fun rememberSheetThemeState(
    activePlayerSchemePair: ColorSchemePair?,
    isDarkTheme: Boolean,
    playerThemePreference: String,
    currentSong: Song?,
    themedAlbumArtUri: String?,
    preparingSongId: String?,
    systemColorScheme: ColorScheme
): SheetThemeState {
    val isAlbumArtTheme = playerThemePreference == ThemePreference.ALBUM_ART
    val hasAlbumArt = !currentSong?.albumArtUriString.isNullOrBlank()

    val activePlayerScheme = remember(activePlayerSchemePair, isDarkTheme) {
        activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }
    }
    val currentSongActiveScheme = remember(
        activePlayerScheme,
        currentSong?.albumArtUriString,
        themedAlbumArtUri
    ) {
        if (
            activePlayerScheme != null &&
            hasAlbumArt &&
            currentSong.albumArtUriString == themedAlbumArtUri
        ) {
            activePlayerScheme
        } else {
            null
        }
    }

    var lastAlbumScheme by remember { mutableStateOf<ColorScheme?>(null) }
    var lastAlbumSchemeSongId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong?.id != lastAlbumSchemeSongId) {
            lastAlbumSchemeSongId = currentSong?.id
        }
    }
    LaunchedEffect(currentSongActiveScheme, currentSong?.id) {
        val currentSongId = currentSong?.id
        if (currentSongId != null && currentSongActiveScheme != null) {
            lastAlbumScheme = currentSongActiveScheme
            lastAlbumSchemeSongId = currentSongId
        }
    }

    val isPreparingPlayback = remember(preparingSongId, currentSong?.id) {
        preparingSongId != null && preparingSongId == currentSong?.id
    }

    val lastAlbumSchemeSnapshot = lastAlbumScheme

    val rawAlbumColorScheme = resolvePlayerSheetTargetScheme(
        isAlbumArtTheme = isAlbumArtTheme,
        hasAlbumArt = hasAlbumArt,
        currentSongActiveScheme = currentSongActiveScheme,
        lastAlbumScheme = lastAlbumSchemeSnapshot,
        systemColorScheme = systemColorScheme
    )

    val rawMiniPlayerScheme = resolvePlayerSheetTargetScheme(
        isAlbumArtTheme = isAlbumArtTheme,
        hasAlbumArt = hasAlbumArt,
        currentSongActiveScheme = currentSongActiveScheme,
        lastAlbumScheme = lastAlbumSchemeSnapshot,
        systemColorScheme = systemColorScheme
    )

    val albumColorScheme = rememberBatchAnimatedColorScheme(rawAlbumColorScheme)
    val miniPlayerScheme = rememberBatchAnimatedColorScheme(rawMiniPlayerScheme)

    val miniAppearProgress = remember { Animatable(0f) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong == null) {
            miniAppearProgress.snapTo(0f)
        } else if (miniAppearProgress.value < 1f) {
            miniAppearProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    val miniReadyAlpha = miniAppearProgress.value
    val miniAppearScale = lerp(0.985f, 1f, miniAppearProgress.value)
    val playerAreaBackground = miniPlayerScheme.primaryContainer

    return SheetThemeState(
        albumColorScheme = albumColorScheme,
        miniPlayerScheme = miniPlayerScheme,
        isPreparingPlayback = isPreparingPlayback,
        miniReadyAlpha = miniReadyAlpha,
        miniAppearScale = miniAppearScale,
        playerAreaBackground = playerAreaBackground
    )
}

/**
 * Animates a [ColorScheme] transition using a single [Animatable]<Float> progress value
 * instead of 34 independent [animateColorAsState] calls.
 *
 * When [target] changes, a spring from 0→1 is run once, and the interpolated scheme is
 * derived from it. This reduces the number of running Springs from 34 → 1 and eliminates
 * the 34 concurrent [State] reads that were triggering recomposition on every animation frame.
 */
@Composable
private fun rememberBatchAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val progress = remember { Animatable(1f) }
    var fromScheme by remember { mutableStateOf(target) }
    var toScheme by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (toScheme == target) return@LaunchedEffect
        fromScheme = lerpColorScheme(fromScheme, toScheme, progress.value)
        toScheme = target
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        )
    }

    val interpolated by remember {
        derivedStateOf { lerpColorScheme(fromScheme, toScheme, progress.value) }
    }
    return interpolated
}

/**
 * Manually interpolates every field of two [ColorScheme]s by [t] ∈ [0, 1].
 * Called once per animation frame (inside [derivedStateOf]) — O(29) lerp ops, negligible CPU.
 */
private fun lerpColorScheme(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme =
    to.copy(
        primary                = lerp(from.primary, to.primary, t),
        onPrimary              = lerp(from.onPrimary, to.onPrimary, t),
        primaryContainer       = lerp(from.primaryContainer, to.primaryContainer, t),
        onPrimaryContainer     = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
        secondary              = lerp(from.secondary, to.secondary, t),
        onSecondary            = lerp(from.onSecondary, to.onSecondary, t),
        secondaryContainer     = lerp(from.secondaryContainer, to.secondaryContainer, t),
        onSecondaryContainer   = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
        tertiary               = lerp(from.tertiary, to.tertiary, t),
        onTertiary             = lerp(from.onTertiary, to.onTertiary, t),
        tertiaryContainer      = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
        onTertiaryContainer    = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
        surface                = lerp(from.surface, to.surface, t),
        onSurface              = lerp(from.onSurface, to.onSurface, t),
        surfaceVariant         = lerp(from.surfaceVariant, to.surfaceVariant, t),
        onSurfaceVariant       = lerp(from.onSurfaceVariant, to.onSurfaceVariant, t),
        background             = lerp(from.background, to.background, t),
        onBackground           = lerp(from.onBackground, to.onBackground, t),
        inverseSurface         = lerp(from.inverseSurface, to.inverseSurface, t),
        inverseOnSurface       = lerp(from.inverseOnSurface, to.inverseOnSurface, t),
        inversePrimary         = lerp(from.inversePrimary, to.inversePrimary, t),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, t),
        surfaceContainerLow    = lerp(from.surfaceContainerLow, to.surfaceContainerLow, t),
        surfaceContainer       = lerp(from.surfaceContainer, to.surfaceContainer, t),
        surfaceContainerHigh   = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
        outline                = lerp(from.outline, to.outline, t),
        outlineVariant         = lerp(from.outlineVariant, to.outlineVariant, t),
        surfaceTint            = lerp(from.surfaceTint, to.surfaceTint, t),
        error                  = lerp(from.error, to.error, t),
        onError                = lerp(from.onError, to.onError, t),
        errorContainer         = lerp(from.errorContainer, to.errorContainer, t),
        onErrorContainer       = lerp(from.onErrorContainer, to.onErrorContainer, t),
        scrim                  = lerp(from.scrim, to.scrim, t),
        surfaceBright          = lerp(from.surfaceBright, to.surfaceBright, t),
        surfaceDim             = lerp(from.surfaceDim, to.surfaceDim, t),
    )
