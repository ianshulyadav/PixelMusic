package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.feed.FeedInnerTubeApi
import com.unshoo.pixelmusic.data.feed.FeedRepository
import com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack
import com.unshoo.pixelmusic.data.feed.YouTubePlaylistResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.utils.ContentFilterUtils
import kotlinx.coroutines.flow.first

sealed interface FeedPlaylistDetailUiState {
    data object Loading : FeedPlaylistDetailUiState

    @Immutable
    data class Success(
        val playlist: YouTubePlaylistResult,
        val isSaving: Boolean = false,
        val savedToLibrary: Boolean = false,
        val saveError: String? = null,
        val isLoadingMore: Boolean = false,
        val loadError: String? = null,
    ) : FeedPlaylistDetailUiState

    data class Error(val message: String) : FeedPlaylistDetailUiState
}

@HiltViewModel
class FeedPlaylistDetailViewModel @Inject constructor(
    private val innerTube: FeedInnerTubeApi,
    private val feedRepository: FeedRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FeedPlaylistDetailUiState>(FeedPlaylistDetailUiState.Loading)
    val uiState: StateFlow<FeedPlaylistDetailUiState> = _uiState.asStateFlow()

    private var currentPlaylistId: String? = null
    private var loadJob: Job? = null

    fun blockRelease(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.blockRelease(id, true)
            val state = _uiState.value as? FeedPlaylistDetailUiState.Success ?: return@launch
            val remainingTracks = state.playlist.tracks.filter { it.videoId != id && it.album != id }
            _uiState.value = state.copy(
                playlist = state.playlist.copy(
                    tracks = remainingTracks,
                    trackCount = remainingTracks.size
                )
            )
        }
    }

    fun load(playlistId: String, force: Boolean = false) {
        if (playlistId.isBlank()) {
            _uiState.value = FeedPlaylistDetailUiState.Error("This playlist is unavailable.")
            return
        }
        if (!force && playlistId == currentPlaylistId && _uiState.value !is FeedPlaylistDetailUiState.Error) return
        loadJob?.cancel()
        val previous = if (playlistId == currentPlaylistId) _uiState.value as? FeedPlaylistDetailUiState.Success else null
        currentPlaylistId = playlistId
        _uiState.value = previous?.copy(isLoadingMore = true, loadError = null) ?: FeedPlaylistDetailUiState.Loading
        loadJob = viewModelScope.launch {
            val liked = playlistId == "yt_liked"
            val recent = playlistId == "yt_recent"
            val isNewReleases = playlistId == "new_releases"
            val title = when {
                liked -> "Liked on YouTube"
                recent -> "Recently played"
                isNewReleases -> "New Releases"
                else -> "Playlist"
            }
            val author = when {
                liked -> "Your favorites"
                recent -> "Recently played tracks"
                isNewReleases -> "Latest releases"
                else -> "YouTube Music"
            }

            val pureYtMusic = runCatching { userPreferencesRepository.pureYtMusicOnlyFlow.first() }.getOrDefault(false)
            val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
            val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
            val blockedReleaseIds = runCatching { userPreferencesRepository.blockedReleaseIdsFlow.first() }.getOrDefault(emptySet())
            fun filterTrack(t: YouTubeMusicTrack): Boolean =
                (!pureYtMusic || !t.isVideo) &&
                (!filterCoverAndLofi || !ContentFilterUtils.isCoverOrLofi(t, filterKeywords)) &&
                (t.videoId !in blockedReleaseIds)

            fun showTracks(tracks: List<YouTubeMusicTrack>, customArt: String? = null) {
                coroutineContext.ensureActive()
                val filtered = tracks.filter(::filterTrack)
                if (filtered.isEmpty()) return
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                if (state != null && state.playlist.tracks.size > filtered.size) return
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    YouTubePlaylistResult(
                        id = playlistId,
                        title = title,
                        author = author,
                        artworkUrl = customArt ?: filtered.firstOrNull()?.artworkUrl,
                        trackCount = filtered.size,
                        tracks = filtered
                    ),
                    isLoadingMore = true,
                )
            }

            if (liked) showTracks(feedRepository.getCachedFeed()?.ytLikedSongs.orEmpty())
            if (recent) showTracks(feedRepository.getCachedFeed()?.ytRecentSongs.orEmpty())

            try {
                val result = when {
                    recent -> {
                        val taste = innerTube.fetchTasteSignals(recentLimit = 50, likedLimit = 0, feedLimit = 0)
                        taste.recentTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
                            val filtered = tracks.filter(::filterTrack)
                            YouTubePlaylistResult(
                                id = playlistId,
                                title = title,
                                author = author,
                                artworkUrl = filtered.firstOrNull()?.artworkUrl,
                                trackCount = filtered.size,
                                tracks = filtered
                            )
                        }
                    }
                    isNewReleases -> {
                        val accumulatedTracks = mutableListOf<YouTubeMusicTrack>()
                        var coverArt: String? = null

                        // 1. Fetch user account based new releases from user's YouTube Home & YouTube Music new releases
                        val homeReleases = withContext(Dispatchers.IO) {
                            runCatching {
                                val home = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.home().getOrNull()
                                val sections = home?.sections.orEmpty()
                                val releaseSections = sections.filter { section ->
                                    val t = section.title.lowercase()
                                    !t.contains("video") && !t.contains("videos") && (
                                        t.contains("released") ||
                                        t.contains("new release") ||
                                        t.contains("new album") ||
                                        t.contains("latest release") ||
                                        t.contains("new music") ||
                                        t.contains("recent release") ||
                                        t.contains("release radar") ||
                                        t.contains("new for you") ||
                                        t.contains("fresh drops") ||
                                        section.label?.lowercase()?.contains("release") == true
                                    )
                                }
                                releaseSections.flatMap { it.items }
                            }.getOrDefault(emptyList())
                        }

                        // 2. Direct song releases from home sections (user account based)
                        val directSongs = homeReleases.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()
                            .map { item ->
                                YouTubeMusicTrack(
                                    videoId = item.id,
                                    title = item.title,
                                    artist = item.artists.firstOrNull()?.name ?: "Unknown artist",
                                    album = item.album?.name,
                                    artworkUrl = item.thumbnail
                                )
                            }.filter(::filterTrack)

                        if (directSongs.isNotEmpty()) {
                            coverArt = directSongs.firstOrNull()?.artworkUrl
                            accumulatedTracks.addAll(directSongs)
                            showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                        }

                        // 3. User account / YouTube Music popular new release albums (capped at max 12 albums, 1-2 tracks each)
                        val albumCandidates = mutableListOf<com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary>()

                        // Add album items from user home
                        homeReleases.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem>().forEach { album ->
                            albumCandidates.add(
                                com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary(
                                    id = album.browseId,
                                    title = album.title,
                                    author = album.artists?.firstOrNull()?.name ?: "Album",
                                    artworkUrl = album.thumbnail
                                )
                            )
                        }

                        // Also include cached user new releases if available
                        if (albumCandidates.size < 8) {
                            val cached = feedRepository.getCachedFeed()?.newReleases.orEmpty()
                                .filter { it.id.startsWith("MPRE") || it.id.startsWith("FEmusic_album") }
                            albumCandidates.addAll(cached)
                        }

                        // Fallback to official YouTube Music new release albums
                        if (albumCandidates.size < 8) {
                            val ytNewReleases = withContext(Dispatchers.IO) {
                                runCatching {
                                    unshoo.ianshulyadav.pixelmusic.innertube.YouTube.newReleaseAlbums().getOrNull()?.map { album ->
                                        com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary(
                                            id = album.browseId,
                                            title = album.title,
                                            author = album.artists?.firstOrNull()?.name ?: "Album",
                                            artworkUrl = album.thumbnail
                                        )
                                    }.orEmpty()
                                }.getOrDefault(emptyList())
                            }
                            albumCandidates.addAll(ytNewReleases)
                        }

                        val targetAlbums = albumCandidates.distinctBy { it.id }.filter { it.id !in blockedReleaseIds }.take(12)
                        if (coverArt == null) {
                            coverArt = targetAlbums.firstOrNull()?.artworkUrl
                        }

                        // Fetch top 1-2 lead tracks per album in parallel (CPU and network friendly)
                        if (targetAlbums.isNotEmpty()) {
                            kotlinx.coroutines.coroutineScope {
                                val albumJobs = targetAlbums.map { rel ->
                                    async(Dispatchers.IO) {
                                        val page = runCatching { innerTube.fetchAlbumPage(rel.id) }.getOrNull()
                                        val fallbackArtist = rel.author ?: page?.artist ?: "Artist"
                                        val fallbackThumb = rel.artworkUrl?.takeIf { it.isNotBlank() } ?: page?.artworkUrl
                                        // Take only the top 1-2 lead tracks from each album to prevent flooding
                                        page?.songs.orEmpty()
                                            .take(2)
                                            .map { s ->
                                                s.copy(
                                                    artist = if (s.artist.isBlank() || s.artist == "Unknown artist") fallbackArtist else s.artist,
                                                    album = s.album ?: rel.title,
                                                    artworkUrl = s.artworkUrl ?: fallbackThumb
                                                )
                                            }.filter(::filterTrack)
                                    }
                                }

                                for (job in albumJobs) {
                                    coroutineContext.ensureActive()
                                    val tracks = job.await()
                                    if (tracks.isNotEmpty()) {
                                        accumulatedTracks.addAll(tracks)
                                        showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                                    }
                                }
                            }
                        }

                        val distinctTracks = accumulatedTracks.distinctBy { it.videoId }.take(30)
                        if (distinctTracks.isEmpty()) {
                            throw java.io.IOException("No new release tracks found.")
                        }

                        YouTubePlaylistResult(
                            id = playlistId,
                            title = title,
                            author = author,
                            artworkUrl = coverArt ?: distinctTracks.firstOrNull()?.artworkUrl,
                            trackCount = distinctTracks.size,
                            tracks = distinctTracks
                        )
                    }
                    else -> {
                        val pl = innerTube.fetchPlaylist(if (liked) "LM" else playlistId, progressive = true, onPageLoaded = ::showTracks)
                        if (pl != null) {
                            val filteredTracks = pl.tracks.filter(::filterTrack)
                            pl.copy(tracks = filteredTracks, trackCount = filteredTracks.size)
                        } else {
                            // Fallback to fetchAlbumPage for album/single browse IDs
                            innerTube.fetchAlbumPage(playlistId)?.let { album ->
                                val songs = album.songs.filter(::filterTrack)
                                YouTubePlaylistResult(
                                    id = playlistId,
                                    title = album.title,
                                    author = album.artist,
                                    artworkUrl = album.artworkUrl,
                                    trackCount = songs.size,
                                    tracks = songs
                                )
                            }
                        }
                    }
                } ?: throw java.io.IOException("Couldn't finish loading this playlist. Tap Retry.")

                coroutineContext.ensureActive()
                val finalTracks = result.tracks.filter(::filterTrack)
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    result.copy(
                        id = playlistId,
                        title = if (liked || recent || isNewReleases) title else result.title.ifBlank { title },
                        author = result.author?.takeIf(String::isNotBlank) ?: author,
                        tracks = finalTracks,
                        trackCount = finalTracks.size
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                val message = error.message ?: "Couldn't finish loading. Tap Retry."
                _uiState.value = state?.copy(isLoadingMore = false, loadError = message)
                    ?: FeedPlaylistDetailUiState.Error(message)
            }
        }
    }

    fun retry() {
        currentPlaylistId?.let { load(it, force = true) }
    }
}
