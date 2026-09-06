package com.unshoo.pixelmusic.data.jellyfin

import com.google.common.truth.Truth.assertThat
import com.unshoo.pixelmusic.data.jellyfin.model.JellyfinLibrary
import com.unshoo.pixelmusic.data.network.jellyfin.JellyfinResponseParser
import org.json.JSONObject
import org.junit.jupiter.api.Test

class JellyfinRepositoryTest {

    @Test
    fun `selected libraries defaults to all available libraries when saved selection is empty`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = emptySet()
        )

        assertThat(selectedIds).containsExactly("lossless", "lossy")
    }

    @Test
    fun `selected libraries keeps valid saved subset`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = setOf("lossless")
        )

        assertThat(selectedIds).containsExactly("lossless")
    }

    @Test
    fun `selected libraries falls back to all when saved ids are stale`() {
        val libraries = listOf(
            JellyfinLibrary(id = "lossless", name = "Lossless", collectionType = "music"),
            JellyfinLibrary(id = "lossy", name = "Lossy", collectionType = "music")
        )

        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = libraries,
            savedLibraryIds = setOf("deleted-library")
        )

        assertThat(selectedIds).containsExactly("lossless", "lossy")
    }

    @Test
    fun `selected libraries returns empty when server exposes no libraries`() {
        val selectedIds = selectedJellyfinLibraryIds(
            availableLibraries = emptyList(),
            savedLibraryIds = setOf("lossless")
        )

        assertThat(selectedIds).isEmpty()
    }

    @Test
    fun `only music collection type counts as music library`() {
        val music = JellyfinLibrary(id = "1", name = "Music", collectionType = "music")
        val movies = JellyfinLibrary(id = "2", name = "Movies", collectionType = "movies")
        val mixed = JellyfinLibrary(id = "3", name = "Mixed", collectionType = null)

        assertThat(music.isMusic).isTrue()
        assertThat(movies.isMusic).isFalse()
        assertThat(mixed.isMusic).isFalse()
    }

    @Test
    fun `parseLibraries maps views response items`() {
        val items = listOf(
            JSONObject(
                """{"Id": "abc", "Name": "Music", "CollectionType": "music"}"""
            ),
            JSONObject(
                """{"Id": "def", "Name": "Movies", "CollectionType": "movies"}"""
            ),
            JSONObject(
                """{"Id": "ghi", "Name": "Mixed"}"""
            )
        )

        val libraries = JellyfinResponseParser.parseLibraries(items)

        assertThat(libraries).containsExactly(
            JellyfinLibrary(id = "abc", name = "Music", collectionType = "music"),
            JellyfinLibrary(id = "def", name = "Movies", collectionType = "movies"),
            JellyfinLibrary(id = "ghi", name = "Mixed", collectionType = null)
        ).inOrder()
    }
}
