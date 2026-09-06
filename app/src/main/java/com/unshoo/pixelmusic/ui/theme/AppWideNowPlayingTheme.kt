package com.unshoo.pixelmusic.ui.theme

import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair

/**
 * Selects the optional application-wide now-playing palette.
 *
 * A missing palette falls back to the normal app theme while playing. When playback is paused,
 * the last palette produced for that same song is retained so pausing does not recolor the UI.
 */
internal fun resolveAppWideNowPlayingColorSchemePair(
    enabled: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    currentSongScheme: ColorSchemePair?,
    lastValidSongId: String?,
    lastValidScheme: ColorSchemePair?
): ColorSchemePair? {
    if (!enabled || currentSongId == null) return null
    if (currentSongScheme != null) return currentSongScheme
    if (!isPlaying && currentSongId == lastValidSongId) return lastValidScheme
    return null
}
