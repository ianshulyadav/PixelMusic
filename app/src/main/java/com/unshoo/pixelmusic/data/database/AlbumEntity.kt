package com.unshoo.pixelmusic.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.utils.LocalArtworkUri
import com.unshoo.pixelmusic.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["artist_id"], unique = false),
        Index(value = ["artist_name"], unique = false),
        Index(value = ["album_artist"], unique = false)
    ]
)
data class AlbumEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null
)

fun AlbumEntity.toAlbum(): Album {
    val effectiveAlbumArtUri = when {
        this.albumArtUriString.isNullOrBlank() -> null
        LocalArtworkUri.looksLikeVolatileArtworkUri(this.albumArtUriString) ->
            LocalArtworkUri.parseSongIdFromVolatileArtworkUri(this.albumArtUriString)
                ?.let { LocalArtworkUri.buildSongUri(it) }
        else -> this.albumArtUriString
    }

    return Album(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        albumArtist = this.albumArtist?.normalizeMetadataTextOrEmpty()?.takeIf { it.isNotBlank() },
        albumArtUriString = effectiveAlbumArtUri,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year
    )
}

fun List<AlbumEntity>.toAlbums(): List<Album> {
    return this.map { it.toAlbum() }
}

fun Album.toEntity(artistIdForAlbum: Long): AlbumEntity {
    return AlbumEntity(
        id = this.id,
        title = this.title,
        artistName = this.artist,
        artistId = artistIdForAlbum,
        albumArtUriString = this.albumArtUriString,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year,
        albumArtist = this.albumArtist
    )
}
