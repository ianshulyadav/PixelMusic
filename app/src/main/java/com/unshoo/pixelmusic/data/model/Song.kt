package com.unshoo.pixelmusic.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class Song(
    val id: String,
    val title: String,
    /**
     * Legacy artist display string.
     * - With multi-artist parsing enabled by default, this typically contains only the primary artist for backward compatibility.
     * For accurate display of all artists, use the [artists] list and [displayArtist] property.
     */
    val artist: String,
    val artistId: Long, // Primary artist ID for backward compatibility
    val artists: List<ArtistRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val discNumber: Int? = null,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val telegramFileId: Int? = null, // ID of the file in Telegram
    val telegramChatId: Long? = null, // ID of the chat where the file is located
    val neteaseId: Long? = null, // Netease Cloud Music song ID
    val gdriveFileId: String? = null, // Google Drive file ID
    val qqMusicMid: String? = null, // QQ Music song MID
    val youtubeId: String? = null, // YouTube video ID for dynamic streaming
    val albumBrowseId: String? = null, // YouTube album browse ID
    val isDisliked: Boolean = false,
) : Parcelable {
    /**
     * Returns the display string for artists.
     * If multiple artists exist (populated during sync), joins them with ", ".
     * Falls back to the raw artist field (splitting is done at sync time, not display time).
     */
    val displayArtist: String
        get() {
            if (artists.isNotEmpty()) {
                return artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            return artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: ArtistRef
        get() = artists.find { it.isPrimary }
            ?: artists.firstOrNull()
            ?: ArtistRef(id = artistId, name = artist, isPrimary = true)

    val isLocal: Boolean
        get() = (path.isNotBlank() || contentUriString.startsWith("content://") || contentUriString.startsWith("file://")) &&
                !contentUriString.startsWith("telegram://") &&
                !contentUriString.startsWith("netease://") &&
                !contentUriString.startsWith("gdrive://") &&
                !contentUriString.startsWith("qqmusic://") &&
                !contentUriString.startsWith("youtube://") &&
                telegramFileId == null && neteaseId == null && gdriveFileId == null &&
                qqMusicMid == null && youtubeId == null

    val isTelegram: Boolean
        get() = contentUriString.startsWith("telegram://") || telegramFileId != null

    val isYouTube: Boolean
        get() = contentUriString.startsWith("youtube://") || youtubeId != null

    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                artists = emptyList(),
                album = "",
                albumId = -1L,
                albumArtist = null,
                path = "",
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = 0,
                dateModified = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0,
                telegramFileId = null,
                telegramChatId = null,
                neteaseId = null,
                gdriveFileId = null,
                qqMusicMid = null,
                youtubeId = null,
                albumBrowseId = null,
                isDisliked = false
            )
        }
    }
}

fun Song.getDeduplicationKey(): String {
    val yId = youtubeId
        ?: if (id.startsWith("youtube_")) id.removePrefix("youtube_")
        else if (contentUriString.startsWith("youtube://")) contentUriString.removePrefix("youtube://")
        else null
    if (!yId.isNullOrBlank()) {
        return "yt:$yId"
    }
    if (telegramChatId != null && telegramFileId != null) {
        return "tg:${telegramChatId}_$telegramFileId"
    }
    if (contentUriString.startsWith("telegram://")) {
        return "tg:${contentUriString.removePrefix("telegram://")}"
    }
    if (path.isNotBlank()) {
        return "path:$path"
    }
    return "id:$id"
}

fun Song.getCanonicalId(): String {
    val yId = youtubeId
        ?: if (id.startsWith("youtube_")) id.removePrefix("youtube_")
        else if (contentUriString.startsWith("youtube://")) contentUriString.removePrefix("youtube://")
        else null
    if (!yId.isNullOrBlank()) {
        return YouTubeIdUtils.toUnifiedYoutubeSongId(yId).toString()
    }
    return id
}

