package com.theveloper.pixelplay.data.database.youtube

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.theveloper.pixelplay.data.model.youtube.Playlist
import com.theveloper.pixelplay.data.model.youtube.PlaylistInfo
import com.theveloper.pixelplay.data.model.youtube.PlaylistSongCrossRef
import com.theveloper.pixelplay.data.model.youtube.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDataSource {
    @Transaction
    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<Playlist>

    @Transaction
    @Query("SELECT * FROM playlists")
    fun observeAll(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Query(
        """
    SELECT DISTINCT songId
    FROM PlaylistSongCrossRef
    WHERE songId IN (:songIds)
"""
    )
    suspend fun getSongIdsWithPlaylist(songIds: List<String>): List<String>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistById(playlistId: String): Flow<Playlist?>

    @Query("SELECT EXISTS(SELECT 1 FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId)")
    fun observeIsSongInPlaylist(playlistId: String, songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteCrossRef(playlistId: String, songId: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlistInfo: PlaylistInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistSongCrossRef>)

    @Transaction
    suspend fun insertPlaylistWithSongs(
        playlist: Playlist,
    ) {
        insertPlaylist(playlist.info)
        val songs = playlist.songs
        insertSongs(songs)
        val refs = songs.map { song -> PlaylistSongCrossRef(playlist.info.id, song.youtubeId) }
        insertCrossRefs(refs)
    }

    /**
     * Insert playlist and only NEW songs (not already in DB).
     * Preserves existing songs' download state and metadata.
     * Updates the playlist info's sync tracking fields.
     */
    @Transaction
    suspend fun insertPlaylistWithSongsPreserving(
        playlist: Playlist,
        songDataSource: LocalSongDataSource,
    ) {
        // Update playlist info with sync tracking
        val updatedInfo = playlist.info.copy(
            lastSyncSongCount = playlist.songs.size,
            lastSyncTimestamp = System.currentTimeMillis()
        )
        insertPlaylist(updatedInfo)

        val allSongIds = playlist.songs.map { it.youtubeId }
        val existingIds = songDataSource.getExistingSongIds(allSongIds).toSet()
        val newSongs = playlist.songs.filter { it.youtubeId !in existingIds }

        if (newSongs.isNotEmpty()) {
            insertSongs(newSongs)
        }

        // Always update cross refs to reflect current playlist composition
        val refs = playlist.songs.map { song -> PlaylistSongCrossRef(playlist.info.id, song.youtubeId) }
        insertCrossRefs(refs)
    }

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsByPlaylistId(playlistId: String)

    @Transaction
    suspend fun deleteFullPlaylist(playlistId: String) {
        deleteCrossRefsByPlaylistId(playlistId)
        deletePlaylistById(playlistId)
    }
}
