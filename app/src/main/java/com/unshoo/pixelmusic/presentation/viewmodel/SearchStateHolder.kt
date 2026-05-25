package com.unshoo.pixelmusic.presentation.viewmodel

import android.util.LruCache

import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val musicRepository: MusicRepository,
    private val youtubeSongRepository: com.unshoo.pixelmusic.data.remote.youtube.SongRepository,
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L // Reduced from 300ms for faster response
        const val SEARCH_CACHE_SIZE = 20
    }

    // In-memory LRU cache for recent search results — instant display for repeated queries
    private val searchResultCache = LruCache<String, ImmutableList<SearchResultItem>>(SEARCH_CACHE_SIZE)

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    // Show cached results immediately while fresh results load
                    val cachedResults = searchResultCache.get(normalizedQuery)
                    if (cachedResults != null) {
                        _searchResults.value = cachedResults
                    }

                    try {
                        val currentFilter = _selectedSearchFilter.value
                        val localSearchFlow = musicRepository.searchAll(normalizedQuery, currentFilter)
                        
                        val youtubeSearchFlow = flow {
                            val items = mutableListOf<SearchResultItem>()

                            try {
                                when (currentFilter) {
                                    SearchFilterType.ALL -> {
                                        val summaryResult = withContext(Dispatchers.IO) {
                                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.searchSummary(normalizedQuery).getOrNull()
                                        }
                                        summaryResult?.summaries?.forEach { summary ->
                                            summary.items.forEach { ytItem ->
                                                when (ytItem) {
                                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem -> {
                                                        items.add(SearchResultItem.SongItem(ytItem.toNativeSong()))
                                                    }
                                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem -> {
                                                        items.add(
                                                            SearchResultItem.ArtistItem(
                                                                com.unshoo.pixelmusic.data.model.Artist(
                                                                    id = toUnifiedYoutubeArtistId(ytItem.title),
                                                                    name = ytItem.title,
                                                                    songCount = 0,
                                                                    imageUrl = ytItem.thumbnail,
                                                                    channelId = ytItem.id
                                                                )
                                                            )
                                                        )
                                                    }
                                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem -> {
                                                        items.add(
                                                            SearchResultItem.AlbumItem(
                                                                com.unshoo.pixelmusic.data.model.Album(
                                                                    id = toUnifiedYoutubeAlbumId(ytItem.title),
                                                                    title = ytItem.title,
                                                                    artist = ytItem.artists?.joinToString { it.name }.orEmpty(),
                                                                    year = ytItem.year ?: 0,
                                                                    dateAdded = System.currentTimeMillis(),
                                                                    albumArtUriString = ytItem.thumbnail,
                                                                    songCount = 0
                                                                )
                                                            )
                                                        )
                                                    }
                                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem -> {
                                                        items.add(
                                                            SearchResultItem.PlaylistItem(
                                                                com.unshoo.pixelmusic.data.model.Playlist(
                                                                    id = ytItem.id,
                                                                    name = ytItem.title,
                                                                    songIds = emptyList(),
                                                                    coverImageUri = ytItem.thumbnail,
                                                                    source = "YOUTUBE"
                                                                )
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    SearchFilterType.SONGS -> {
                                        val searchResult = withContext(Dispatchers.IO) {
                                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.search(
                                                normalizedQuery,
                                                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter.FILTER_SONG
                                            ).getOrNull()
                                        }
                                        val apiSongs = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>() ?: emptyList()
                                        items.addAll(apiSongs.map { SearchResultItem.SongItem(it.toNativeSong()) })
                                    }
                                    SearchFilterType.ARTISTS -> {
                                        val searchResult = withContext(Dispatchers.IO) {
                                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.search(
                                                normalizedQuery,
                                                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter.FILTER_ARTIST
                                            ).getOrNull()
                                        }
                                        val apiArtists = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem>() ?: emptyList()
                                        items.addAll(apiArtists.map { apiArtist ->
                                            SearchResultItem.ArtistItem(
                                                com.unshoo.pixelmusic.data.model.Artist(
                                                    id = toUnifiedYoutubeArtistId(apiArtist.title),
                                                    name = apiArtist.title,
                                                    songCount = 0,
                                                    imageUrl = apiArtist.thumbnail,
                                                    channelId = apiArtist.id
                                                )
                                            )
                                        })
                                    }
                                    SearchFilterType.ALBUMS -> {
                                        val searchResult = withContext(Dispatchers.IO) {
                                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.search(
                                                normalizedQuery,
                                                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter.FILTER_ALBUM
                                            ).getOrNull()
                                        }
                                        val apiAlbums = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem>() ?: emptyList()
                                        items.addAll(apiAlbums.map { apiAlbum ->
                                            SearchResultItem.AlbumItem(
                                                com.unshoo.pixelmusic.data.model.Album(
                                                    id = toUnifiedYoutubeAlbumId(apiAlbum.title),
                                                    title = apiAlbum.title,
                                                    artist = apiAlbum.artists?.joinToString { it.name }.orEmpty(),
                                                    year = apiAlbum.year ?: 0,
                                                    dateAdded = System.currentTimeMillis(),
                                                    albumArtUriString = apiAlbum.thumbnail,
                                                    songCount = 0
                                                )
                                            )
                                        })
                                    }
                                    SearchFilterType.PLAYLISTS -> {
                                        val searchResult = withContext(Dispatchers.IO) {
                                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.search(
                                                normalizedQuery,
                                                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                                            ).getOrNull()
                                        }
                                        val apiPlaylists = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem>() ?: emptyList()
                                        items.addAll(apiPlaylists.map { apiPlaylist ->
                                            SearchResultItem.PlaylistItem(
                                                com.unshoo.pixelmusic.data.model.Playlist(
                                                    id = apiPlaylist.id,
                                                    name = apiPlaylist.title,
                                                    songIds = emptyList(),
                                                    coverImageUri = apiPlaylist.thumbnail,
                                                    source = "YOUTUBE"
                                                )
                                            )
                                        })
                                    }
                                }
                            } catch (e: java.lang.Exception) {
                                Timber.e(e, "Error executing YouTube search under filter $currentFilter")
                            }

                            emit(items)
                        }.flowOn(Dispatchers.IO)

                        combine(localSearchFlow, youtubeSearchFlow) { localResults, youtubeResults ->
                            val localSongsMap = localResults.filterIsInstance<SearchResultItem.SongItem>()
                                .map { it.song }
                                .filter { !it.youtubeId.isNullOrBlank() }
                                .associateBy { it.youtubeId!! }

                            val youtubeIdsSet = youtubeResults.filterIsInstance<SearchResultItem.SongItem>()
                                .mapNotNull { it.song.youtubeId }
                                .toSet()

                            val updatedYoutubeResults = youtubeResults.map { ytResult ->
                                if (ytResult is SearchResultItem.SongItem) {
                                    val ytId = ytResult.song.youtubeId
                                    val matchingLocalSong = localSongsMap[ytId]
                                    if (matchingLocalSong != null) {
                                        SearchResultItem.SongItem(
                                            ytResult.song.copy(
                                                id = matchingLocalSong.id,
                                                path = matchingLocalSong.path,
                                                isFavorite = matchingLocalSong.isFavorite
                                            )
                                        )
                                    } else {
                                        ytResult
                                    }
                                } else {
                                    ytResult
                                }
                            }

                            val filteredLocalResults = localResults.filter { localResult ->
                                if (localResult is SearchResultItem.SongItem) {
                                    val ytId = localResult.song.youtubeId
                                    ytId.isNullOrBlank() || !youtubeIdsSet.contains(ytId)
                                } else {
                                    true
                                }
                            }

                            val combined = updatedYoutubeResults + filteredLocalResults
                            combined.sortedWith(
                                compareBy { result ->
                                    when (result) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                    }
                                }
                            )
                        }.collect { resultsList ->
                            if (request.requestId != latestSearchRequestId.get()) {
                                return@collect
                            }

                            val immutableResults = resultsList.toImmutableList()
                            if (_searchResults.value != immutableResults) {
                                _searchResults.value = immutableResults
                                // Cache for future instant display
                                searchResultCache.put(normalizedQuery, immutableResults)

                                // Pre-cache/prefetch the stream URL of the top song result asynchronously
                                scope?.launch(Dispatchers.IO) {
                                    try {
                                        val topSongItem = immutableResults.firstOrNull { it is SearchResultItem.SongItem } as? SearchResultItem.SongItem
                                        if (topSongItem != null && topSongItem.song.youtubeId != null) {
                                            val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                                                youtubeId = topSongItem.song.youtubeId,
                                                title = topSongItem.song.title,
                                                artist = topSongItem.song.artist,
                                                thumbnailHref = topSongItem.song.albumArtUriString ?: ""
                                            )
                                            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.getSongPlayerUrl(
                                                context = appContext,
                                                song = ytSong,
                                                allowLocal = false
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "Error prefetching top search result stream URL")
                                    }
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            _searchResults.value = persistentListOf()
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    private fun toUnifiedYoutubeArtistId(artistName: String): Long {
        return -(17_000_000_000_000L + kotlin.math.abs(artistName.lowercase().hashCode().toLong()))
    }

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long {
        return -(16_000_000_000_000L + kotlin.math.abs(albumName.lowercase().hashCode().toLong()))
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
