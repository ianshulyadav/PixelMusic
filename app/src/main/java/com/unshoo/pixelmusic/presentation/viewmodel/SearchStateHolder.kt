package com.unshoo.pixelmusic.presentation.viewmodel

import android.util.LruCache
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Pure YouTube Music search — all local library search removed.
 * Queries go directly to YouTube Music API; results are cached in LRU.
 */
@Singleton
class SearchStateHolder @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val SEARCH_CACHE_SIZE = 30
        val albumIdMap = java.util.concurrent.ConcurrentHashMap<Long, String>()
    }

    private val searchResultCache = LruCache<String, ImmutableList<SearchResultItem>>(SEARCH_CACHE_SIZE)

    private data class SearchRequest(val query: String, val requestId: Long)

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
                    val query = request.query
                    if (query.isBlank()) {
                        _searchResults.value = persistentListOf()
                        return@collectLatest
                    }

                    // Instantly show cache if available
                    searchResultCache.get(query)?.let { cached ->
                        _searchResults.value = cached
                    }

                    try {
                        val results = withContext(Dispatchers.IO) {
                            searchYouTube(query, _selectedSearchFilter.value)
                        }
                        if (request.requestId == latestSearchRequestId.get()) {
                            val immutable = results.toImmutableList()
                            _searchResults.value = immutable
                            searchResultCache.put(query, immutable)

                            // Pre-fetch top song stream URL
                            scope?.launch(Dispatchers.IO) {
                                try {
                                    val topSong = immutable.filterIsInstance<SearchResultItem.SongItem>().firstOrNull()
                                    if (topSong?.song?.youtubeId != null) {
                                        val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                                            youtubeId = topSong.song.youtubeId,
                                            title = topSong.song.title,
                                            artist = topSong.song.artist,
                                            thumbnailHref = topSong.song.albumArtUriString ?: ""
                                        )
                                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.getSongPlayerUrl(
                                            context = appContext, song = ytSong, allowLocal = false
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "YouTube search error: $query")
                        }
                    }
                }
        }
    }

    private suspend fun searchYouTube(query: String, filter: SearchFilterType): List<SearchResultItem> {
        val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()
        val items = mutableListOf<SearchResultItem>()
        when (filter) {
            SearchFilterType.ALL -> {
                val result = YouTube.searchSummary(query).getOrNull()
                result?.summaries?.forEach { summary ->
                    summary.items.forEach { item ->
                        when (item) {
                            is SongItem -> {
                                val musicVideoType = item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                                val isMusicVideo = musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || musicVideoType == "MUSIC_VIDEO_TYPE_UGC"
                                if (!pureYtMusicOnly || !isMusicVideo) {
                                    items.add(SearchResultItem.SongItem(item.toNativeSong()))
                                }
                            }
                            is ArtistItem -> items.add(SearchResultItem.ArtistItem(
                                Artist(id = ytArtistId(item.title), name = item.title, songCount = 0, imageUrl = item.thumbnail, channelId = item.id)
                            ))
                            is AlbumItem -> {
                                val longId = ytAlbumId(item.title)
                                albumIdMap[longId] = item.browseId
                                items.add(SearchResultItem.AlbumItem(
                                    Album(id = longId, title = item.title,
                                        artist = item.artists?.joinToString { it.name }.orEmpty(),
                                        year = item.year ?: 0, dateAdded = System.currentTimeMillis(),
                                        albumArtUriString = item.thumbnail, songCount = 0)
                                ))
                            }
                            is PlaylistItem -> items.add(SearchResultItem.PlaylistItem(
                                Playlist(id = item.id, name = item.title, songIds = emptyList(), coverImageUri = item.thumbnail, source = "YOUTUBE")
                            ))
                            else -> {}
                        }
                    }
                }
            }
            SearchFilterType.SONGS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                result?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly)?.forEach { items.add(SearchResultItem.SongItem(it.toNativeSong())) }
            }
            SearchFilterType.ARTISTS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                result?.items?.filterIsInstance<ArtistItem>()?.forEach { a ->
                    items.add(SearchResultItem.ArtistItem(Artist(id = ytArtistId(a.title), name = a.title, songCount = 0, imageUrl = a.thumbnail, channelId = a.id)))
                }
            }
            SearchFilterType.ALBUMS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                result?.items?.filterIsInstance<AlbumItem>()?.forEach { a ->
                    val longId = ytAlbumId(a.title)
                    albumIdMap[longId] = a.browseId
                    items.add(SearchResultItem.AlbumItem(Album(id = longId, title = a.title,
                        artist = a.artists?.joinToString { it.name }.orEmpty(), year = a.year ?: 0,
                        dateAdded = System.currentTimeMillis(), albumArtUriString = a.thumbnail, songCount = 0)))
                }
            }
            SearchFilterType.PLAYLISTS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                result?.items?.filterIsInstance<PlaylistItem>()?.forEach { p ->
                    items.add(SearchResultItem.PlaylistItem(Playlist(id = p.id, name = p.title, songIds = emptyList(), coverImageUri = p.thumbnail, source = "YOUTUBE")))
                }
            }
            SearchFilterType.VIDEOS -> {
                if (!pureYtMusicOnly) {
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                    result?.items?.filterIsInstance<SongItem>()?.forEach { items.add(SearchResultItem.SongItem(it.toNativeSong())) }
                }
            }
        }
        return items
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun performSearch(query: String) {
        val requestId = latestSearchRequestId.incrementAndGet()
        if (query.trim().isBlank()) {
            _searchResults.value = persistentListOf()
        }
        searchRequests.tryEmit(SearchRequest(query.trim(), requestId))
    }

    fun loadSearchHistory(limit: Int = 15) {
        // History stub – no local DB
    }

    fun onSearchQuerySubmitted(query: String) {
        // No-op — history not persisted locally
    }

    fun deleteSearchHistoryItem(query: String) {}
    fun clearSearchHistory() { _searchHistory.value = persistentListOf() }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }

    private fun ytArtistId(name: String): Long =
        -(17_000_000_000_000L + kotlin.math.abs(name.lowercase().hashCode().toLong()))

    private fun ytAlbumId(name: String): Long =
        -(16_000_000_000_000L + kotlin.math.abs(name.lowercase().hashCode().toLong()))
}
