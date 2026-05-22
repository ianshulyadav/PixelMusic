package com.theveloper.pixelplay.data.model.youtube

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.theveloper.pixelplay.data.remote.youtube.Constants
import kotlinx.serialization.Serializable

@Immutable
data class Playlist(
    @Embedded val info: PlaylistInfo,
    @Relation(
        parentColumn = "id",              // Playlist.id
        entityColumn = "youtubeId",       // Song.youtubeId
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",  // column in junction pointing to Playlist
            entityColumn = "songId"       // column in junction pointing to Song
        )
    )
    val songs: List<Song> = listOf()
) {
    val mediaItems: List<MediaItem>
        get() = songs.map { song ->
            song.mediaItem
        }

    val downloaded: Boolean
        get() = songs.all { song -> song.downloaded }
}

@Serializable
@Immutable
@Entity(tableName = Constants.Database.PLAYLISTS_TABLE)
data class PlaylistInfo(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val coverHref: String = "",
    val coverPath: String? = null,
    /** Number of songs at last successful sync. Used for delta detection. */
    val lastSyncSongCount: Int = 0,
    /** Epoch millis of last successful sync. */
    val lastSyncTimestamp: Long = 0L,
) {
    val isDownloadedPlaylist: Boolean
        get() = id == Constants.Downloads.DOWNLOADED_PLAYLIST_ID
}

@Entity(
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String
)
