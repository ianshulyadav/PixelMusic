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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.SearchSource
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.coroutines.flow.first

@Singleton
class SearchStateHolder @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) {
    companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val SEARCH_CACHE_SIZE = 150
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

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    private var lastContinuationToken: String? = null
    private var activeSearchQuery: String? = null
    private var activeFilterType: SearchFilterType = SearchFilterType.ALL
    private var isLoadingMore = false

    private fun cacheKey(query: String, filter: SearchFilterType, source: SearchSource): String =
        "${source.name}:${filter.name}:${query.lowercase().trim()}"

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
                    val currentFilter = _selectedSearchFilter.value
                    if (query.isBlank()) {
                        _searchResults.value = persistentListOf()
                        _isSearching.value = false
                        return@collectLatest
                    }

                    val isLocalMode = userPreferencesRepository.localModeEnabledFlow.first()
                    val source = if (isLocalMode) SearchSource.LOCAL else userPreferencesRepository.searchSourceFlow.first()
                    val key = cacheKey(query, currentFilter, source)
                    val cached = searchResultCache.get(key)
                    if (cached != null) {
                        _searchResults.value = cached
                        _isSearching.value = false
                    } else {
                        _searchResults.value = persistentListOf()
                        _isSearching.value = true
                    }
                    if (source == SearchSource.LOCAL) {
                        try {
                            val results = musicRepository.searchAllOnce(query, currentFilter)
                            if (request.requestId == latestSearchRequestId.get()) {
                                val immutable = results.toImmutableList()
                                _searchResults.value = immutable
                                _isSearching.value = false
                                searchResultCache.put(key, immutable)
                            }
                        } catch (_: CancellationException) {
                        } catch (e: Exception) {
                            if (request.requestId == latestSearchRequestId.get()) {
                                Timber.e(e, "Local search error: $query")
                                _isSearching.value = false
                            }
                        }
                        return@collectLatest
                    }

                    try {
                        val results = withContext(Dispatchers.IO) {
                            val remote = searchYouTube(query, currentFilter)
                            if (remote.isEmpty()) {
                                musicRepository.searchAllOnce(query, currentFilter)
                            } else {
                                remote
                            }
                        }
                        if (request.requestId == latestSearchRequestId.get()) {
                            val immutable = results.toImmutableList()
                            _searchResults.value = immutable
                            _isSearching.value = false
                            searchResultCache.put(key, immutable)

                            scope?.launch(Dispatchers.IO) {
                                try {
                                    kotlinx.coroutines.delay(800L)
                                    if (request.requestId != latestSearchRequestId.get()) return@launch
                                    val topSongs = immutable.filterIsInstance<SearchResultItem.SongItem>().take(2)
                                    topSongs.forEach { topItem ->
                                        if (request.requestId != latestSearchRequestId.get()) return@forEach
                                        val song = topItem.song
                                        val yId = song.youtubeId
                                            ?: song.id.removePrefix("youtube_").takeIf { it != song.id }
                                            ?: song.contentUriString.removePrefix("youtube://").takeIf { it != song.contentUriString }
                                        if (!yId.isNullOrBlank()) {
                                            val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                                                youtubeId = yId,
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                duration = if (song.duration > 0) (song.duration / 1000).toString() else "",
                                                thumbnailHref = song.albumArtUriString.orEmpty()
                                            )
                                            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.getSongPlayerUrl(
                                                context = appContext, song = ytSong, allowLocal = false
                                            )
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "YouTube search error: $query")
                            _isSearching.value = false
                        }
                    }
                }
        }
    }

    /**
     * BUGFIX (search): shared mapping from an InnerTube YTItem to PixelMusic's UI-facing
     * SearchResultItem. Extracted from loadMoreSearch()'s inline when-block so the same,
     * already-correct logic can also drive the "All" tab below (previously that tab hand-rolled
     * its own 3-way parallel-search-and-interleave instead of using YouTube's own ranked
     * summary, and had no shared mapper).
     */
    private fun YTItem.toSearchResultItem(pureYtMusicOnly: Boolean): SearchResultItem? =
        when (this) {
            is SongItem -> {
                val musicVideoType = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                val isMusicVideo = musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || musicVideoType == "MUSIC_VIDEO_TYPE_UGC"
                if (!pureYtMusicOnly || !isMusicVideo) {
                    SearchResultItem.SongItem(toNativeSong())
                } else {
                    null
                }
            }
            is ArtistItem -> SearchResultItem.ArtistItem(
                Artist(id = ytArtistId(title), name = title, songCount = 0, imageUrl = thumbnail, channelId = id)
            )
            is AlbumItem -> {
                val longId = browseId.hashCode().toLong()
                albumIdMap[longId] = browseId
                AlbumIdMapper.putMapping(appContext, longId, browseId)
                SearchResultItem.AlbumItem(
                    Album(id = longId, title = title,
                        artist = artists?.joinToString { it.name }.orEmpty(),
                        year = year ?: 0, dateAdded = System.currentTimeMillis(),
                        albumArtUriString = thumbnail, songCount = 0)
                )
            }
            is PlaylistItem -> SearchResultItem.PlaylistItem(
                Playlist(id = id, name = title, songIds = emptyList(), coverImageUri = thumbnail, source = "YOUTUBE")
            )
            else -> null
        }

    fun loadMoreSearch() {
        val token = lastContinuationToken
        val query = activeSearchQuery
        val filter = activeFilterType
        if (token == null || query == null || isLoadingMore) return

        isLoadingMore = true
        scope?.launch(Dispatchers.IO) {
            try {
                if (userPreferencesRepository.localModeEnabledFlow.first()) return@launch
                val result = YouTube.searchContinuation(token).getOrNull()
                if (result != null) {
                    val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()
                    val newItems = result.items.mapNotNull { it.toSearchResultItem(pureYtMusicOnly) }
                    if (newItems.isNotEmpty()) {
                        val currentList = _searchResults.value
                        val updatedList = (currentList + newItems).toImmutableList()
                        _searchResults.value = updatedList
                        lastContinuationToken = result.continuation
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more search results")
            } finally {
                isLoadingMore = false
            }
        }
    }

    private suspend fun searchYouTube(query: String, filter: SearchFilterType): List<SearchResultItem> {
        val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()
        val items = mutableListOf<SearchResultItem>()
        
        activeSearchQuery = query
        activeFilterType = filter
        lastContinuationToken = null

        when (filter) {
            SearchFilterType.ALL -> coroutineScope {
                val songsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() }
                val artistsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull() }
                val albumsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull() }
                val playlistsDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull() }

                val songsResult = songsDeferred.await()
                val artistsResult = artistsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val playlistsResult = playlistsDeferred.await()

                lastContinuationToken = songsResult?.continuation

                val topSongs = mutableListOf<SearchResultItem>()
                val mixedItems = mutableListOf<SearchResultItem>()

                val songsList = songsResult?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly).orEmpty()
                val artistsList = artistsResult?.items?.filterIsInstance<ArtistItem>().orEmpty()
                val albumsList = albumsResult?.items?.filterIsInstance<AlbumItem>().orEmpty()
                val playlistsList = playlistsResult?.items?.filterIsInstance<PlaylistItem>().orEmpty()

                // Optimization: Pre-calculate search items off-main
                withContext(Dispatchers.Default) {
                    // Top 10 Songs
                    val (top10Songs, remainingSongs) = if (songsList.size > 10) {
                        songsList.take(10) to songsList.drop(10)
                    } else {
                        songsList to emptyList()
                    }

                    top10Songs.forEach { topSongs.add(SearchResultItem.SongItem(it.toNativeSong())) }

                    // Interleave Artists, Albums, Playlists, and remaining Songs (1 of each per iteration)
                    val artistIterator = artistsList.iterator()
                    val albumIterator = albumsList.iterator()
                    val playlistIterator = playlistsList.iterator()
                    val songIterator = remainingSongs.iterator()

                    while (artistIterator.hasNext() || albumIterator.hasNext() || playlistIterator.hasNext() || songIterator.hasNext()) {
                        if (artistIterator.hasNext()) {
                            val artist = artistIterator.next()
                            mixedItems.add(SearchResultItem.ArtistItem(
                                Artist(id = ytArtistId(artist.title), name = artist.title, songCount = 0, imageUrl = artist.thumbnail, channelId = artist.id)
                            ))
                        }
                        if (albumIterator.hasNext()) {
                            val album = albumIterator.next()
                            val longId = album.browseId.hashCode().toLong()
                            albumIdMap[longId] = album.browseId
                            AlbumIdMapper.putMapping(appContext, longId, album.browseId)
                            mixedItems.add(SearchResultItem.AlbumItem(
                                Album(id = longId, title = album.title,
                                    artist = album.artists?.joinToString { it.name }.orEmpty(),
                                    year = album.year ?: 0, dateAdded = System.currentTimeMillis(),
                                    albumArtUriString = album.thumbnail, songCount = 0)
                            ))
                        }
                        if (playlistIterator.hasNext()) {
                            val playlist = playlistIterator.next()
                            mixedItems.add(SearchResultItem.PlaylistItem(
                                Playlist(id = playlist.id, name = playlist.title, songIds = emptyList(), coverImageUri = playlist.thumbnail, source = "YOUTUBE")
                            ))
                        }
                        if (songIterator.hasNext()) {
                            val song = songIterator.next()
                            mixedItems.add(SearchResultItem.SongItem(song.toNativeSong()))
                        }
                    }

                    items.addAll(topSongs)
                    items.addAll(mixedItems)
                }
            }
            SearchFilterType.SONGS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                lastContinuationToken = result?.continuation
                result?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly)?.forEach { items.add(SearchResultItem.SongItem(it.toNativeSong())) }
            }
            SearchFilterType.ARTISTS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                lastContinuationToken = result?.continuation
                result?.items?.filterIsInstance<ArtistItem>()?.forEach { a ->
                    items.add(SearchResultItem.ArtistItem(Artist(id = ytArtistId(a.title), name = a.title, songCount = 0, imageUrl = a.thumbnail, channelId = a.id)))
                }
            }
            SearchFilterType.ALBUMS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                lastContinuationToken = result?.continuation
                result?.items?.filterIsInstance<AlbumItem>()?.forEach { a ->
                    val longId = a.browseId.hashCode().toLong()
                    albumIdMap[longId] = a.browseId
                    AlbumIdMapper.putMapping(appContext, longId, a.browseId)
                    items.add(SearchResultItem.AlbumItem(Album(id = longId, title = a.title,
                        artist = a.artists?.joinToString { it.name }.orEmpty(), year = a.year ?: 0,
                        dateAdded = System.currentTimeMillis(), albumArtUriString = a.thumbnail, songCount = 0)))
                }
            }
            SearchFilterType.PLAYLISTS -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                lastContinuationToken = result?.continuation
                result?.items?.filterIsInstance<PlaylistItem>()?.forEach { p ->
                    items.add(SearchResultItem.PlaylistItem(Playlist(id = p.id, name = p.title, songIds = emptyList(), coverImageUri = p.thumbnail, source = "YOUTUBE")))
                }
            }
            SearchFilterType.VIDEOS -> {
                if (!pureYtMusicOnly) {
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                    lastContinuationToken = result?.continuation
                    result?.items?.filterIsInstance<SongItem>()?.forEach { items.add(SearchResultItem.SongItem(it.toNativeSong())) }
                }
            }
        }
        return items
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
        activeSearchQuery?.let { query ->
            if (query.isNotBlank()) {
                performSearch(query)
            }
        }
    }

    fun performSearch(query: String) {
        activeSearchQuery = query.trim()
        val requestId = latestSearchRequestId.incrementAndGet()
        if (query.trim().isBlank()) {
            _searchResults.value = persistentListOf()
        }
        searchRequests.tryEmit(SearchRequest(query.trim(), requestId))
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            val history = musicRepository.getRecentSearchHistory(limit)
            _searchHistory.value = history.toImmutableList()
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                musicRepository.addSearchHistoryItem(query)
                loadSearchHistory()
            }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            musicRepository.deleteSearchHistoryItemByQuery(query)
            loadSearchHistory()
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            musicRepository.clearSearchHistory()
            _searchHistory.value = persistentListOf()
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }

    private fun ytArtistId(name: String): Long =
        -(17_000_000_000_000L + kotlin.math.abs(name.lowercase().hashCode().toLong()))

    private fun ytAlbumId(name: String): Long =
        -(16_000_000_000_000L + kotlin.math.abs(name.lowercase().hashCode().toLong()))
}
