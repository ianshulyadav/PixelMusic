package com.unshoo.pixelmusic.data.worker

import android.content.Context
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.remote.youtube.toYoutubeSong
import com.unshoo.pixelmusic.data.remote.youtube.YouTubeItemFilter
import com.unshoo.pixelmusic.data.repository.YouTubeLibraryPersistenceManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import com.unshoo.pixelmusic.utils.YouTubeIdUtils

import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.LibraryMembershipEntity

@Singleton
class YouTubeLibrarySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val favoritesDao: FavoritesDao,
    private val engagementDao: EngagementDao,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val persistenceManager: YouTubeLibraryPersistenceManager
) {

    companion object {
        private const val LIKED_SONGS_PLAYLIST = "LM"
        private const val BROWSE_SUBSCRIPTIONS = "FEmusic_library_corpus_artists"
        private const val BROWSE_ALBUMS = "FEmusic_library_corpus_albums"
        private const val MIN_SYNC_INTERVAL_MS = 10 * 60 * 1000L
        // Maximum continuation pages per library category.
        // 50 pages × ~25 items/page ≈ 1250 items — covers virtually all libraries.
        private const val MAX_CONTINUATION_PAGES = 50
    }

    private val syncMutex = Mutex()
    @Volatile private var lastSuccessfulSyncAtMs: Long = 0L

    suspend fun syncNow(force: Boolean = false) = withContext(Dispatchers.IO) {
        val lastTimestamp = userPreferencesRepository.getLastSyncTimestamp()
        val now = System.currentTimeMillis()
        if (!force && now - lastTimestamp < 6L * 60L * 60L * 1000L) {
            return@withContext
        }

        syncMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - userPreferencesRepository.getLastSyncTimestamp() < 6L * 60L * 60L * 1000L) {
                return@withLock
            }
            if (!YouTube.hasLoginCookie()) {
                return@withLock
            }
            val syncPlaylistsAndLikes = userPreferencesRepository.youtubeSyncPlaylistsAndLikesFlow.first()
            if (!syncPlaylistsAndLikes && !force) {
                return@withLock
            }
            // Bug 7 fix: reduced from 2500ms to 150ms. The long delay was causing
            // unnecessary latency on every automatic sync when the app is in the foreground.
            if (!force) {
                delay(150L)
            }
            try {
                syncSubscribedArtists()
            } catch (_: Exception) {
            }
            try {
                syncLikedSongs()
            } catch (_: Exception) {
            }
            try {
                syncLikedAlbums()
            } catch (_: Exception) {
            }
            try {
                syncLikedPlaylists()
            } catch (_: Exception) {
            }
            try {
                syncListeningHistory()
            } catch (_: Exception) {
            }
            userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())
        }
    }

    suspend fun syncSubscribedArtists(forceFull: Boolean = false) {
        val allArtistItems = mutableListOf<ArtistItem>()

        val firstPage = YouTube.library(BROWSE_SUBSCRIPTIONS).getOrNull() ?: return
        allArtistItems += firstPage.items.filterIsInstance<ArtistItem>()

        val existingSubscribedIds = userPreferencesRepository.subscribedArtistIdsFlow.first()
        val firstPageIds = allArtistItems.map { ytArtistIdFromChannelId(it.id) }
        val existingInDb = musicDao.getArtistsByIds(firstPageIds).associateBy { it.id }
        val allFirstPageKnown = !forceFull && existingSubscribedIds.isNotEmpty() &&
            allArtistItems.isNotEmpty() &&
            allArtistItems.all { (it.id in existingSubscribedIds) || (ytArtistIdFromChannelId(it.id).toString() in existingSubscribedIds) } &&
            firstPageIds.all { it in existingInDb }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.continuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                val newItems = next.items.filterIsInstance<ArtistItem>()
                allArtistItems += newItems
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allArtistItems.isEmpty()) return

        val memberships = mutableListOf<LibraryMembershipEntity>()
        val entities = allArtistItems.mapNotNull { item ->
            val id = ytArtistIdFromChannelId(item.id)
            memberships.add(LibraryMembershipEntity(songKey = "artist_$id", firstPlayedTimestamp = System.currentTimeMillis()))
            ArtistEntity(
                id = id,
                name = item.title,
                trackCount = 0,
                imageUrl = item.thumbnail,
                channelId = item.id
            )
        }
        musicDao.insertArtists(entities)
        engagementDao.insertLibraryMemberships(memberships)
        val subscribedIds = existingSubscribedIds + entities.mapNotNull { it.channelId }.toSet() + entities.map { it.id.toString() }
        userPreferencesRepository.setSubscribedArtistIds(subscribedIds)
    }

    suspend fun syncLikedAlbums(forceFull: Boolean = false) {
        val allAlbumItems = mutableListOf<AlbumItem>()

        val firstPage = YouTube.library(BROWSE_ALBUMS).getOrNull() ?: return
        allAlbumItems += firstPage.items.filterIsInstance<AlbumItem>()

        val existingLikedAlbumIds = userPreferencesRepository.likedAlbumIdsFlow.first()
        val allFirstPageKnown = !forceFull && existingLikedAlbumIds.isNotEmpty() &&
            allAlbumItems.isNotEmpty() &&
            allAlbumItems.all { it.browseId in existingLikedAlbumIds }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.continuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                allAlbumItems += next.items.filterIsInstance<AlbumItem>()
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allAlbumItems.isEmpty()) return

        val memberships = mutableListOf<LibraryMembershipEntity>()
        val entities = allAlbumItems.mapNotNull { item ->
            val id = YouTubeIdUtils.toUnifiedYoutubeAlbumId(item.title)
            com.unshoo.pixelmusic.presentation.viewmodel.AlbumIdMapper.putMapping(context, id, item.browseId)
            memberships.add(LibraryMembershipEntity(songKey = "album_$id", firstPlayedTimestamp = System.currentTimeMillis()))
            val primaryArtistName = item.artists?.firstOrNull()?.name ?: "Unknown Artist"
            val primaryArtistId = ytArtistId(primaryArtistName)
            AlbumEntity(
                id = id,
                title = item.title,
                artistName = primaryArtistName,
                artistId = primaryArtistId,
                songCount = 0,
                dateAdded = System.currentTimeMillis(),
                year = item.year ?: 0,
                albumArtUriString = item.thumbnail
            )
        }
        musicDao.insertAlbums(entities)
        engagementDao.insertLibraryMemberships(memberships)
        val browseIds = existingLikedAlbumIds + allAlbumItems.map { it.browseId }.toSet()
        userPreferencesRepository.setLikedAlbumIds(browseIds)
    }

    suspend fun syncLikedSongs(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val firstPage = YouTube.playlist(LIKED_SONGS_PLAYLIST).getOrNull() ?: return@withContext
        val allSongItems = mutableListOf<SongItem>()
        allSongItems += firstPage.songs

        val existingFavorites = favoritesDao.getFavoriteSongIdsOnce().toSet()
        val firstPageIds = firstPage.songs.mapNotNull { it.id?.let(::ytSongId) }

        // Parse total count from header if present (e.g. "125 songs")
        val totalCount = firstPage.playlist.songCountText
            ?.split(" ")?.firstOrNull()
            ?.filter { it.isDigit() }?.toIntOrNull()

        // Unlikes check: if YouTube reported fewer liked songs than we have locally,
        // songs were removed/unliked -> full sync required to reconcile local DB.
        val unlikesDetected = totalCount != null && totalCount < existingFavorites.size
        val shouldDoFull = forceFull || unlikesDetected

        if (!shouldDoFull && existingFavorites.isNotEmpty() && firstPageIds.isNotEmpty()) {
            // If top items are already known and count didn't drop, nothing was added or unliked!
            if (firstPageIds.first() in existingFavorites && firstPageIds.take(5).all { it in existingFavorites }) {
                Timber.d("YouTubeLibrarySyncManager: Liked songs top boundary unchanged. Skipping sync.")
                return@withContext
            }

            // New items added: page until we hit the known boundary of existing favorite IDs
            var pages = 0
            var continuation = firstPage.songsContinuation
            var consecutiveHits = 0
            val boundaryThreshold = 3

            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                allSongItems += next.songs
                continuation = next.continuation
                pages++

                val nextIds = next.songs.mapNotNull { it.id?.let(::ytSongId) }
                for (id in nextIds) {
                    if (id in existingFavorites) {
                        consecutiveHits++
                        if (consecutiveHits >= boundaryThreshold) {
                            continuation = null // Stop pagination
                            break
                        }
                    } else {
                        consecutiveHits = 0
                    }
                }
                delay(30L)
            }
        } else {
            // Full sync: fetch all pages
            var pages = 0
            var continuation = firstPage.songsContinuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                allSongItems += next.songs
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allSongItems.isEmpty()) return@withContext
        val nativeSongs = allSongItems.map { it.toNativeSong() }
        persistenceManager.persistLikes(nativeSongs, isFullSync = shouldDoFull)
    }

    private suspend fun syncListeningHistory() {
        val historyPage = YouTube.musicHistory().getOrNull() ?: return
        val allSongs = mutableListOf<SongItem>()
        historyPage.sections?.forEach { section ->
            allSongs += section.songs
        }
        if (allSongs.isEmpty()) return

        val nativeSongs = allSongs.map { it.toNativeSong() }
        musicRepository.insertYoutubeSongs(nativeSongs)
    }

    suspend fun syncLikedPlaylists(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<PlaylistItem>()

        val firstPage = YouTube.library("FEmusic_liked_playlists").getOrNull() ?: return@withContext
        allPlaylists += firstPage.items.filterIsInstance<PlaylistItem>().filter {
            YouTubeItemFilter.isMusicPlaylist(it.title, it.id)
        }

        val appDatabase = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
        val playlistRepo = appDatabase.playlistRepository()

        var pages = 0
        var continuation = firstPage.continuation
        while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
            yield()
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            allPlaylists += next.items.filterIsInstance<PlaylistItem>().filter {
                YouTubeItemFilter.isMusicPlaylist(it.title, it.id)
            }
            continuation = next.continuation
            pages++
            delay(30L)
        }

        if (allPlaylists.isEmpty()) return@withContext

        // Remote IDs set for detecting deleted playlists
        val remoteIds = allPlaylists.map { it.id }.toSet()
        val existingPlaylists = playlistRepo.getAll()

        // Clean up any deleted YouTube playlists locally
        existingPlaylists.forEach { existing ->
            val cleanId = existing.info.id.removePrefix("VL")
            if (existing.info.id !in remoteIds && cleanId !in remoteIds && !existing.info.id.startsWith("LM")) {
                Timber.i("YouTubeLibrarySyncManager: Removing deleted remote playlist '%s' (%s)", existing.info.title, existing.info.id)
                persistenceManager.deletePlaylist(existing.info.id)
            }
        }

        for (item in allPlaylists) {
            yield()
            val reportedCount = item.songCountText
                ?.split(" ")?.firstOrNull()
                ?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            val existing = playlistRepo.getPlaylistById(item.id)
                ?: playlistRepo.getPlaylistById("VL${item.id}")

            val existingSongs = existing?.songs ?: emptyList()
            val countMatches = existing != null && existingSongs.size == reportedCount && reportedCount > 0

            // Fetch page 1 to check signature and grab latest metadata
            val page1Result = YouTube.playlist(item.id).getOrNull()
            if (page1Result == null) {
                // If playlist fetch failed, keep existing cache
                continue
            }

            val page1Songs = page1Result.songs
            val page1Matches = countMatches && page1Songs.isNotEmpty() &&
                page1Songs.indices.all { i ->
                    i < existingSongs.size && page1Songs[i].id == existingSongs[i].youtubeId
                }

            val highQualityCover = com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality(
                item.thumbnail
            ) ?: item.thumbnail ?: ""

            val info = PlaylistInfo(
                id = item.id,
                title = item.title,
                coverHref = highQualityCover,
                lastSyncSongCount = reportedCount.takeIf { it > 0 } ?: page1Songs.size,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            if (!forceFull && page1Matches) {
                Timber.d("YouTubeLibrarySyncManager: Playlist '%s' unchanged (signature & count match). Skipping.", item.title)
                playlistRepo.insertPlaylist(info)
                continue
            }

            // Songs changed, added, removed, or reordered: page all songs
            val fullSongItems = mutableListOf<SongItem>()
            fullSongItems += page1Songs

            var songPages = 0
            var songContinuation = page1Result.songsContinuation ?: page1Result.continuation
            while (songContinuation != null && songPages < MAX_CONTINUATION_PAGES) {
                yield()
                val nextSongs = YouTube.playlistContinuation(songContinuation).getOrNull() ?: break
                fullSongItems += nextSongs.songs
                songContinuation = nextSongs.continuation
                songPages++
                delay(30L)
            }

            val ytSongs = fullSongItems.map { it.toYoutubeSong() }
            persistenceManager.persistPlaylist(info, ytSongs)
            delay(50L)
        }
    }

    private fun ytSongId(youtubeId: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeSongId(youtubeId)

    private fun ytAlbumId(name: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeAlbumId(name)

    private fun ytArtistId(name: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeArtistId(name)

    /**
     * Bug 1+8 fix: derives a stable Long ID from the YouTube channel ID string
     * rather than from the artist's display name. Prevents duplicate rows when a
     * channel is renamed and avoids collisions between differently-named artists
     * that happen to share the same hash.
     *
     * Falls back to the name-based hash only if the channelId is blank (should
     * not happen in practice but keeps the function total).
     */
    private fun ytArtistIdFromChannelId(channelId: String): Long {
        if (channelId.isBlank()) return ytArtistId(channelId)
        return -(17_000_000_000_000L + channelId.hashCode().toLong().absoluteValue)
    }
}
