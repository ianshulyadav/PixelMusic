package com.theveloper.pixelplay.data.database.youtube

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theveloper.pixelplay.data.model.youtube.Song

@Dao
interface LocalSongDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(song: Song)

    @Query(
        """
    SELECT * 
    FROM songs 
    WHERE audioFilePath IS NOT NULL 
      AND thumbnailPath IS NOT NULL
      ORDER BY  
        songs.title COLLATE NOCASE ASC,
        songs.artist COLLATE NOCASE ASC
"""
    )
    suspend fun getDownloadedSongs(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE youtubeId = :songId")
    suspend fun getSong(songId: String): Song?

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    suspend fun setStreamUrl(songId: String, streamUrl: String) {
        val existing = getSong(songId) ?: Song(
            youtubeId = songId,
            streamUrl = streamUrl
        )

        val updated = existing.copy(streamUrl = streamUrl)
        create(updated)
    }

    @Query("DELETE FROM songs WHERE youtubeId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<String>)

    @Delete
    suspend fun delete(song: Song)

    /** Get all song IDs that exist in the local DB. Used for delta sync. */
    @Query("SELECT youtubeId FROM songs WHERE youtubeId IN (:ids)")
    suspend fun getExistingSongIds(ids: List<String>): List<String>

    /** Mark a song as permanently downloaded by the user (won't be auto-deleted). */
    @Query("UPDATE songs SET isPermanentlyDownloaded = 1, downloadTimestamp = :timestamp WHERE youtubeId = :songId")
    suspend fun markAsPermanentlyDownloaded(songId: String, timestamp: Long = System.currentTimeMillis())

    /** Update the genre for a song. */
    @Query("UPDATE songs SET genre = :genre WHERE youtubeId = :songId")
    suspend fun updateGenre(songId: String, genre: String)
}
