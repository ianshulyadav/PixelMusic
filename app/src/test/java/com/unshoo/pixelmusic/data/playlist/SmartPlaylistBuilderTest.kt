package com.unshoo.pixelmusic.data.playlist

import com.unshoo.pixelmusic.data.DailyMixManager.SongEngagementStats
import com.unshoo.pixelmusic.data.model.SmartPlaylistRule
import com.unshoo.pixelmusic.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SmartPlaylistBuilderTest {

    private val now = 1_700_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    private fun song(id: String, title: String = "T-$id", dateAdded: Long = 0L) = Song(
        id = id,
        title = title,
        artist = "Artist",
        artistId = 0L,
        album = "Album",
        albumId = 0L,
        path = "/music/$id.mp3",
        contentUriString = "content://$id",
        albumArtUriString = null,
        duration = 1_000L,
        dateAdded = dateAdded,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )

    private fun stats(playCount: Int = 0, durationMs: Long = 0L, lastPlayed: Long = 0L) =
        SongEngagementStats(playCount = playCount, totalPlayDurationMs = durationMs, lastPlayedTimestamp = lastPlayed)

    @Test
    fun `empty library returns empty list`() {
        val result = SmartPlaylistBuilder.buildSongIds(
            rule = SmartPlaylistRule.TOP_PLAYED,
            allSongs = emptyList(),
            engagements = emptyMap(),
            favoriteIds = emptySet(),
            now = now,
            limit = 10,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TOP_PLAYED orders by play count then duration and drops unplayed`() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val engagements = mapOf(
            "a" to stats(playCount = 5),
            "b" to stats(playCount = 3, durationMs = 1_000),
            "c" to stats(playCount = 3, durationMs = 500),
            "d" to stats(playCount = 0, durationMs = 0),
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `limit is clamped between 1 and library size`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val engagements = mapOf(
            "a" to stats(playCount = 5),
            "b" to stats(playCount = 3),
            "c" to stats(playCount = 1),
        )
        assertEquals(
            listOf("a"),
            SmartPlaylistBuilder.buildSongIds(SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 0),
        )
        assertEquals(
            listOf("a", "b", "c"),
            SmartPlaylistBuilder.buildSongIds(SmartPlaylistRule.TOP_PLAYED, songs, engagements, emptySet(), now, limit = 99),
        )
    }

    @Test
    fun `RECENTLY_PLAYED keeps only played songs newest first`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val engagements = mapOf(
            "a" to stats(lastPlayed = 300),
            "b" to stats(lastPlayed = 100),
            "c" to stats(lastPlayed = 0),
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.RECENTLY_PLAYED, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `FORGOTTEN_FAVORITES uses a strict 30-day threshold and only favorites`() {
        val songs = listOf(
            song("older", title = "older"),
            song("exactly", title = "exactly"),
            song("recent", title = "recent"),
            song("never", title = "never"),
            song("nonfav", title = "nonfav"),
        )
        val engagements = mapOf(
            "older" to stats(lastPlayed = daysAgo(31)),
            "exactly" to stats(lastPlayed = daysAgo(30)),
            "recent" to stats(lastPlayed = daysAgo(29)),
            "nonfav" to stats(lastPlayed = daysAgo(100)),
        )
        val favorites = setOf("older", "exactly", "recent", "never")
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.FORGOTTEN_FAVORITES, songs, engagements, favorites, now, limit = 10,
        )
        assertEquals(listOf("never", "older"), result)
    }

    @Test
    fun `NEW_GEMS includes play count of exactly 2 but not 3, newest added first`() {
        val songs = listOf(
            song("z", dateAdded = 300),
            song("y", dateAdded = 200),
            song("x", dateAdded = 100),
            song("w", dateAdded = 50),
        )
        val engagements = mapOf(
            "z" to stats(playCount = 3),
            "y" to stats(playCount = 2),
            "x" to stats(playCount = 0),
        )
        val result = SmartPlaylistBuilder.buildSongIds(
            SmartPlaylistRule.NEW_GEMS, songs, engagements, emptySet(), now, limit = 10,
        )
        assertEquals(listOf("y", "x", "w"), result)
    }
}
