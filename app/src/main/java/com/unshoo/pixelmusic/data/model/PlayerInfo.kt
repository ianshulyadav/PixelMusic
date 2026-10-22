package com.unshoo.pixelmusic.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueItem(
    val id: Long, // ID único de la canción
    val albumArtUri: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (id != other.id) return false
        if (albumArtUri != other.albumArtUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (albumArtUri?.hashCode() ?: 0)
        return result
    }
}

@Serializable
data class WidgetThemeColors(
    val lightSurfaceContainer: Int,
    val lightSurfaceContainerLowest: Int = 0,
    val lightSurfaceContainerLow: Int = 0,
    val lightSurfaceContainerHigh: Int = 0,
    val lightSurfaceContainerHighest: Int = 0,
    val lightTitle: Int,
    val lightArtist: Int,
    val lightPlayPauseBackground: Int,
    val lightPlayPauseIcon: Int,
    val lightPrevNextBackground: Int,
    val lightPrevNextIcon: Int,

    val darkSurfaceContainer: Int,
    val darkSurfaceContainerLowest: Int = 0,
    val darkSurfaceContainerLow: Int = 0,
    val darkSurfaceContainerHigh: Int = 0,
    val darkSurfaceContainerHighest: Int = 0,
    val darkTitle: Int,
    val darkArtist: Int,
    val darkPlayPauseBackground: Int,
    val darkPlayPauseIcon: Int,
    val darkPrevNextBackground: Int,
    val darkPrevNextIcon: Int
)

@Serializable
data class PlayerInfo(
    val songTitle: String = "",
    val artistName: String = "",
    val isPlaying: Boolean = false,
    val albumArtUri: String? = null,
    val albumArtBitmapData: ByteArray? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val themeColors: WidgetThemeColors? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
) {
    // equals y hashCode para ByteArray, ya que el por defecto no es comparando contenido
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerInfo

        if (songTitle != other.songTitle) return false
        if (artistName != other.artistName) return false
        if (isPlaying != other.isPlaying) return false
        if (albumArtUri != other.albumArtUri) return false
        if (!sameArtworkPayload(albumArtBitmapData, other.albumArtBitmapData)) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (totalDurationMs != other.totalDurationMs) return false
        if (isFavorite != other.isFavorite) return false
        if (queue != other.queue) return false
        if (lyrics != other.lyrics) return false
        if (isLoadingLyrics != other.isLoadingLyrics) return false
        if (themeColors != other.themeColors) return false
        if (isShuffleEnabled != other.isShuffleEnabled) return false
        if (repeatMode != other.repeatMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = songTitle.hashCode()
        result = 31 * result + artistName.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (albumArtUri?.hashCode() ?: 0)
        result = 31 * result + artworkPayloadHash(albumArtBitmapData)
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + totalDurationMs.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + queue.hashCode()
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + isLoadingLyrics.hashCode()
        result = 31 * result + (themeColors?.hashCode() ?: 0)
        result = 31 * result + isShuffleEnabled.hashCode()
        result = 31 * result + repeatMode
        return result
    }
}

private fun sameArtworkPayload(a: ByteArray?, b: ByteArray?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    // PlayerInfo equality is used for UI/update gating, not data integrity. Avoid a full
    // contentEquals scan of widget artwork on frequent playback/progress updates.
    if (a.size != b.size) return false
    if (a.isEmpty()) return true
    return a.first() == b.first() && a.last() == b.last()
}

private fun artworkPayloadHash(bytes: ByteArray?): Int {
    if (bytes == null) return 0
    if (bytes.isEmpty()) return 1
    // Avoid ByteArray.contentHashCode() on every hashCode call; artwork can be large and
    // hashCode may be invoked from Compose/Glance diffing.
    return 31 * bytes.size + 17 * bytes.first().toInt() + bytes.last().toInt()
}
