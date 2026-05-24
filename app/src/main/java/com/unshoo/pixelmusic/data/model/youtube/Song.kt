package com.unshoo.pixelmusic.data.model.youtube

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Immutable
@Entity(tableName = Constants.Database.SONGS_TABLE)
data class Song(
    @PrimaryKey
    val youtubeId: String,
    val title: String = "",
    val artist: String = "",
    val duration: String = "",
    val thumbnailHref: String = "",
    val thumbnailPath: String? = null,
    val streamUrl: String? = null,
    val audioFilePath: String? = null,
    val uid: String = UUID.randomUUID().toString(),
    /** If true, this song was explicitly downloaded by the user and must NOT be auto-deleted. */
    val isPermanentlyDownloaded: Boolean = false,
    /** Epoch millis when the audio file was downloaded. 0 = never downloaded. */
    val downloadTimestamp: Long = 0L,
    /** Genre fetched from YouTube Music API. Null = not yet fetched. */
    val genre: String? = null,
) {
    val mediaItem: MediaItem
        get() {
            val extras = Bundle()
            extras.putString(Constants.ExoPlayer.SongMetadata.DURATION, duration)
            extras.putString(Constants.ExoPlayer.SongMetadata.UID, UUID.randomUUID().toString())

            return MediaItem.Builder()
                .setUri(youtubeUrl)
                .setMediaId(youtubeId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setArtworkUri(upgradeThumbnailUrlToHighQuality(thumbnailPath ?: thumbnailHref).orEmpty().toUri())
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

    val youtubeUrl: String
        get() = "${Constants.YoutubeApi.YOUTUBE_URL_PREFIX}${youtubeId}"
    val downloaded: Boolean
        get() = audioFilePath != null && thumbnailPath != null

    override fun equals(other: Any?): Boolean {
        if (other !is Song) return false
        return this.hashCode() == other.hashCode()
    }

    override fun hashCode(): Int {
        return youtubeId.hashCode() + 31 * uid.hashCode()
    }

    fun isSameYoutubeSong(other: Song): Boolean {
        return this.youtubeId == other.youtubeId
    }

    companion object {
        fun createFromYoutubeUrl(url: String): Song {
            return Song(youtubeId = url.removePrefix(Constants.YoutubeApi.YOUTUBE_URL_PREFIX))
        }
    }
}
