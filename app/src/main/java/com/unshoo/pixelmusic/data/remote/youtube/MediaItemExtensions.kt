package com.unshoo.pixelmusic.data.remote.youtube

import androidx.media3.common.MediaItem
import com.unshoo.pixelmusic.data.model.youtube.Song

/**
 * Converts a nullable [MediaItem] back into a [Song] model.
 * Reads metadata and extras that were packed in via [Song.mediaItem].
 */
fun MediaItem?.toSong(): Song {
    val extras = this?.mediaMetadata?.extras
    return Song(
        uid = extras?.getString(Constants.ExoPlayer.SongMetadata.UID) ?: "",
        youtubeId = this?.mediaId ?: "",
        title = this?.mediaMetadata?.title?.toString() ?: "",
        artist = this?.mediaMetadata?.artist?.toString() ?: "",
        thumbnailHref = upgradeThumbnailUrlToHighQuality(this?.mediaMetadata?.artworkUri?.toString()).orEmpty(),
        duration = extras?.getString(Constants.ExoPlayer.SongMetadata.DURATION) ?: ""
    )
}
