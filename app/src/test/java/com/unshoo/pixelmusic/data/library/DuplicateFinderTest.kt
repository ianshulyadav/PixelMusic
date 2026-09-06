package com.unshoo.pixelmusic.data.library

import com.unshoo.pixelmusic.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateFinderTest {

    private fun song(id: String, title: String, artist: String, duration: Long) = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = "Album",
        albumId = 0L,
        path = "/music/$id.mp3",
        contentUriString = "content://$id",
        albumArtUriString = null,
        duration = duration,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )

    @Test
    fun `no duplicates returns empty`() {
        val songs = listOf(
            song("1", "Alpha", "A", 100_000),
            song("2", "Beta", "B", 100_000),
        )
        assertTrue(DuplicateFinder.findDuplicates(songs).isEmpty())
    }

    @Test
    fun `same title and artist within tolerance are grouped ignoring case and whitespace`() {
        val songs = listOf(
            song("1", "Bohemian Rhapsody", "Queen", 354_000),
            song("2", "bohemian  rhapsody", "queen", 355_000),
            song("3", "Something Else", "Queen", 200_000),
        )
        val groups = DuplicateFinder.findDuplicates(songs)
        assertEquals(1, groups.size)
        assertEquals(setOf("1", "2"), groups.first().songs.map { it.id }.toSet())
    }

    @Test
    fun `same name but durations beyond tolerance are not merged`() {
        val songs = listOf(
            song("studio", "Live Wire", "AC/DC", 300_000),
            song("live", "Live Wire", "AC/DC", 360_000),
        )
        assertTrue(DuplicateFinder.findDuplicates(songs).isEmpty())
    }

    @Test
    fun `groups are ordered by size descending`() {
        val songs = listOf(
            song("a1", "Pair", "X", 100_000),
            song("a2", "Pair", "X", 100_000),
            song("b1", "Trio", "Y", 200_000),
            song("b2", "Trio", "Y", 200_000),
            song("b3", "Trio", "Y", 201_000),
        )
        val groups = DuplicateFinder.findDuplicates(songs)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].songs.size)
        assertEquals(2, groups[1].songs.size)
    }
}
