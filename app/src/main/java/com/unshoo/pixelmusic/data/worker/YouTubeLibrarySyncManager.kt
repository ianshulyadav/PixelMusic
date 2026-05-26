package com.unshoo.pixelmusic.data.worker

import android.content.Context
import android.util.Log
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Syncs YouTube library data (subscribed artists, liked songs, user albums) into the local
 * Room database so the Library paging flows can display them in the UI.
 *
 * Call [syncNow] once per app session; it is a no-op if the user is not logged in to YouTube.
 */
@Singleton
class YouTubeLibrarySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val favoritesDao: FavoritesDao,
    private val musicRepository: MusicRepository,
) {

    companion object {
        private const val TAG = "YTLibSync"
        private const val LIKED_SONGS_PLAYLIST = "LM"
        private const val BROWSE_SUBSCRIPTIONS = "FEmusic_library_subscriptions"
    }

    /**
     * Performs a full YouTube library sync (subscribed artists + liked songs).
     * Runs on IO dispatcher; safe to call from any coroutine.
     */
    suspend fun syncNow() = withContext(Dispatchers.IO) {
        try {
            syncSubscribedArtists()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to sync subscribed artists")
        }
        try {
            syncLikedSongs()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to sync liked songs")
        }
    }

    // ---------------------------------------------------------------------------
    // Subscribed artists
    // ---------------------------------------------------------------------------

    private suspend fun syncSubscribedArtists() {
        Timber.tag(TAG).d("Syncing subscribed artists from YouTube…")
        val allArtistItems = mutableListOf<ArtistItem>()

        // Fetch first page
        val firstPage = YouTube.library(BROWSE_SUBSCRIPTIONS).getOrNull()
        if (firstPage == null) {
            Timber.tag(TAG).d("No subscriptions page returned (user may not be logged in)")
            return
        }

        allArtistItems += firstPage.items.filterIsInstance<ArtistItem>()

        // Fetch continuation pages (YouTube returns them in batches)
        var continuation = firstPage.continuation
        while (continuation != null) {
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            allArtistItems += next.items.filterIsInstance<ArtistItem>()
            continuation = next.continuation
        }

        if (allArtistItems.isEmpty()) {
            Timber.tag(TAG).d("No subscribed artists found")
            return
        }

        // Convert to ArtistEntity and insert (IGNORE conflict = keep existing track_count)
        val entities = allArtistItems.mapNotNull { item ->
            val id = ytArtistId(item.title)
            ArtistEntity(
                id = id,
                name = item.title,
                trackCount = 0,
                imageUrl = item.thumbnail,
                channelId = item.id // browse id like UC…
            )
        }

        musicDao.insertArtistsIgnoreConflicts(entities)
        Timber.tag(TAG).d("Synced ${entities.size} subscribed artists to DB")
    }

    // ---------------------------------------------------------------------------
    // Liked songs
    // ---------------------------------------------------------------------------

    suspend fun syncLikedSongs() = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("Syncing liked songs from YouTube playlist LM…")
        val allSongItems = mutableListOf<SongItem>()

        val firstPage = YouTube.playlist(LIKED_SONGS_PLAYLIST).getOrNull()
        if (firstPage == null) {
            Timber.tag(TAG).d("Could not load liked songs playlist (not logged in?)")
            return@withContext
        }

        allSongItems += firstPage.songs

        // Fetch all continuation pages (each has ~100 songs)
        var continuation = firstPage.songsContinuation
        while (continuation != null) {
            val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            allSongItems += next.songs
            continuation = next.continuation
        }

        if (allSongItems.isEmpty()) {
            Timber.tag(TAG).d("No liked songs found")
            return@withContext
        }

        Timber.tag(TAG).d("Found ${allSongItems.size} liked songs, syncing to DB…")

        // For each liked song: insert the song skeleton into the songs table,
        // then mark it as favourite in the favorites table.
        val songs = allSongItems.map { it.toNativeSong() }
        musicRepository.insertYoutubeSongs(songs)

        val favoriteEntities = songs.mapNotNull { song ->
            val songIdStr = song.youtubeId ?: return@mapNotNull null
            val numericId = ytSongId(songIdStr)
            FavoritesEntity(songId = numericId, isFavorite = true)
        }

        if (favoriteEntities.isNotEmpty()) {
            favoritesDao.insertAll(favoriteEntities)
            Timber.tag(TAG).d("Marked ${favoriteEntities.size} songs as liked in DB")
        }
    }

    // ---------------------------------------------------------------------------
    // Stable ID helpers (must match MusicRepositoryImpl)
    // ---------------------------------------------------------------------------

    private fun ytSongId(youtubeId: String): Long =
        -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)

    private fun ytArtistId(name: String): Long =
        -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)
}
