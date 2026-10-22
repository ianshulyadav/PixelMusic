package com.unshoo.pixelmusic.data.storage

import com.unshoo.pixelmusic.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class StorageSenseLogicTest {

    @Test
    fun testDuplicateSongDetection() {
        val song1 = Song.emptySong().copy(
            id = "1",
            title = "Shape of You",
            artist = "Ed Sheeran",
            path = "/storage/emulated/0/Music/track1.mp3",
            contentUriString = "content://media/external/audio/media/1"
        )
        val song2 = Song.emptySong().copy(
            id = "2",
            title = "Shape of You",
            artist = "Ed Sheeran",
            path = "/storage/emulated/0/Music/track2.mp3",
            contentUriString = "content://media/external/audio/media/2"
        )
        val song3 = Song.emptySong().copy(
            id = "3",
            title = "Blinding Lights",
            artist = "The Weeknd",
            path = "/storage/emulated/0/Music/track3.mp3",
            contentUriString = "content://media/external/audio/media/3"
        )

        val allSongs = listOf(song1, song2, song3)
        val localSongs = allSongs.filter { it.isLocal }

        val grouped = localSongs.groupBy { song ->
            val cleanTitle = song.title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
            val cleanArtist = song.artist.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
            "$cleanTitle|$cleanArtist"
        }.filter { it.value.size > 1 }

        assertEquals(1, grouped.size)
        val duplicates = grouped.values.first()
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.any { it.id == "1" })
        assertTrue(duplicates.any { it.id == "2" })
    }

    @Test
    fun testInvalidSongDetection() {
        val validSong = Song.emptySong().copy(
            id = "1",
            title = "Valid Song",
            artist = "Artist",
            path = System.getProperty("java.io.tmpdir") ?: "",
            contentUriString = "content://media/external/audio/media/1"
        )
        val missingSong = Song.emptySong().copy(
            id = "2",
            title = "Ghost Song",
            artist = "Artist",
            path = "/non_existent_path_xyz_123.mp3",
            contentUriString = "content://media/external/audio/media/2"
        )

        val songs = listOf(validSong, missingSong)
        val invalid = songs.filter { it.isLocal && (it.path.isBlank() || !java.io.File(it.path).exists()) }

        assertEquals(1, invalid.size)
        assertEquals("2", invalid.first().id)
    }

    @Test
    fun testStreamCacheTtlCutoffCalculation() {
        val now = 100_000_000_000L
        val ttlDays = 14
        val cutoff = now - TimeUnit.DAYS.toMillis(ttlDays.toLong())

        val spanRecent = now - TimeUnit.DAYS.toMillis(3)
        val spanExpired = now - TimeUnit.DAYS.toMillis(20)

        assertTrue("Recent span should not be expired", spanRecent >= cutoff)
        assertTrue("Older span should be expired", spanExpired < cutoff)
    }

    @Test
    fun testForgottenSongSafetyNetExcludesFavorites() {
        val songFavorite = Song.emptySong().copy(
            id = "1",
            title = "Fav",
            artist = "Artist",
            isFavorite = true,
            path = "/path/fav.mp3",
            contentUriString = "content://media/external/audio/media/1"
        )
        val songNormal = Song.emptySong().copy(
            id = "2",
            title = "Normal",
            artist = "Artist",
            isFavorite = false,
            path = "/path/normal.mp3",
            contentUriString = "content://media/external/audio/media/2"
        )

        val neverSuggestFavorites = true
        val candidates = listOf(songFavorite, songNormal).filterNot {
            neverSuggestFavorites && it.isFavorite
        }

        assertEquals(1, candidates.size)
        assertEquals("2", candidates.first().id)
    }
}
