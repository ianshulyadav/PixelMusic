package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.feed.FeedArtist
import com.unshoo.pixelmusic.data.feed.FeedData
import com.unshoo.pixelmusic.data.feed.FeedInnerTubeApi
import com.unshoo.pixelmusic.data.feed.FeedMix
import com.unshoo.pixelmusic.data.feed.FeedRepository
import com.unshoo.pixelmusic.data.feed.GeneratedTrack
import com.unshoo.pixelmusic.data.feed.RecentTrack
import com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack
import com.unshoo.pixelmusic.data.feed.toSong
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L

private data class FeedReloadKey(
    val isYt: Boolean,
    val username: String,
    val filterCoverLofi: Boolean,
    val keywords: List<String>
)

@Immutable
data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val launchingRadio: String? = null,
    val error: String? = null,
    val feedData: FeedData = FeedData(),
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val innerTube: FeedInnerTubeApi,
    private val datastoreRepository: DatastoreRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    val blockedReleaseIds = userPreferencesRepository.blockedReleaseIdsFlow
    val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow
    val filterKeywords = userPreferencesRepository.filterKeywordsFlow
    private var feedJob: Job? = null
    private var lastLoadedMillis: Long = 0L

    fun blockRelease(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.blockRelease(id, true)
            _uiState.update { current ->
                current.copy(
                    feedData = current.feedData.copy(
                        newReleases = current.feedData.newReleases.filter { it.id != id }
                    )
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                datastoreRepository.cookies,
                userPreferencesRepository.lastfmUsernameFlow,
                userPreferencesRepository.exploreLastfmEnabledFlow,
                userPreferencesRepository.filterCoverAndLofiFlow,
                userPreferencesRepository.filterKeywordsFlow,
            ) { cookies, username, exploreLastFm, filterCoverLofi, keywords ->
                val isYt = cookies.toRawCookie().let { it.contains("SAPISID=") || it.contains("__Secure-3PAPISID=") }
                val effectiveUsername = if (exploreLastFm) username else ""
                FeedReloadKey(isYt, effectiveUsername, filterCoverLofi, keywords)
            }.distinctUntilChanged().collect { key ->
                feedJob?.cancel()
                _uiState.value = FeedUiState(
                    feedData = FeedData(
                        isYtConnected = key.isYt,
                        userName = key.username.takeIf(String::isNotBlank),
                    )
                )
                loadFeed()
            }
        }
    }

    fun loadFeed() = fetchFeed(refreshing = false)

    fun refresh() = fetchFeed(refreshing = true)

    fun onVisible() {
        if (feedJob?.isActive == true) return
        val hasContent = _uiState.value.feedData.quickPicks.isNotEmpty() ||
            _uiState.value.feedData.topArtists.isNotEmpty() ||
            _uiState.value.feedData.newReleases.isNotEmpty()
        val stale = System.currentTimeMillis() - lastLoadedMillis > STALE_AFTER_MILLIS
        if (!hasContent || stale) refresh()
    }

    private fun fetchFeed(refreshing: Boolean) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refreshing || it.isLoading, isRefreshing = refreshing, error = null) }
            try {
                val exploreLastFm = userPreferencesRepository.exploreLastfmEnabledFlow.first()
                val username = if (exploreLastFm) userPreferencesRepository.lastfmUsernameFlow.first().takeIf(String::isNotBlank) else null
                val data = repository.loadFeed(username = username, forceRefresh = refreshing) { update ->
                    ensureActive()
                    _uiState.update { it.copy(feedData = update, isLoading = false) }
                }
                ensureActive()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        feedData = data,
                        error = null,
                    )
                }
                lastLoadedMillis = System.currentTimeMillis()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ensureActive()
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = "Couldn't update your feed. Please try again.")
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun playTrack(track: YouTubeMusicTrack, playerViewModel: PlayerViewModel, sourceLabel: String = "Feed") {
        val song = track.toSong()
        playerViewModel.showAndPlaySong(song, listOf(song), queueName = sourceLabel)
    }

    fun shuffleTracksQueue(tracks: List<YouTubeMusicTrack>, playerViewModel: PlayerViewModel, sourceLabel: String = "Feed") {
        if (tracks.isEmpty()) return
        val songs = tracks.map { it.toSong() }
        playerViewModel.playSongsShuffled(songs, queueName = "$sourceLabel Shuffle")
    }

    fun playTracksQueue(tracks: List<YouTubeMusicTrack>, startIndex: Int = 0, playerViewModel: PlayerViewModel, sourceLabel: String = "Feed") {
        if (tracks.isEmpty()) return
        val songs = tracks.map { it.toSong() }
        val startSong = songs.getOrElse(startIndex) { songs.first() }
        playerViewModel.playSongs(songs, startSong, queueName = sourceLabel)
    }

    fun playRecentQueue(tracks: List<RecentTrack>, startIndex: Int = 0, playerViewModel: PlayerViewModel) {
        if (tracks.isEmpty()) return
        val songs = tracks.map { it.toSong() }
        val startSong = songs.getOrElse(startIndex) { songs.first() }
        playerViewModel.playSongs(songs, startSong, queueName = "Jump Back In")
    }

    fun playGeneratedQueue(tracks: List<GeneratedTrack>, startIndex: Int = 0, playerViewModel: PlayerViewModel, sourceLabel: String = "Heavy Rotation") {
        if (tracks.isEmpty()) return
        val songs = tracks.map { it.toSong() }
        val startSong = songs.getOrElse(startIndex) { songs.first() }
        playerViewModel.playSongs(songs, startSong, queueName = sourceLabel)
    }

    fun playMix(mix: FeedMix, playerViewModel: PlayerViewModel) {
        playTrack(mix.seed, playerViewModel, mix.title)
    }

    fun playInfiniteRadio(playerViewModel: PlayerViewModel) {
        val feed = _uiState.value.feedData
        val candidate = (feed.quickPicks + feed.ytLikedSongs + feed.freshFinds).randomOrNull()
        if (candidate != null) {
            playTrack(candidate, playerViewModel, "Infinite Radio")
        }
    }

    fun playArtistRadio(artist: FeedArtist, playerViewModel: PlayerViewModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(launchingRadio = artist.name) }
            try {
                val seeds = innerTube.searchSongs("${artist.name} songs", limit = 5)
                val seed = seeds.firstOrNull { it.videoId.isNotBlank() }
                if (seed != null) {
                    playTrack(seed, playerViewModel, "${artist.name} Radio")
                } else {
                    playerViewModel.playArtistSongsShuffledWithRelated(artist.name, initialArtistSongs = emptyList())
                }
            } catch (_: Exception) {
                playerViewModel.playArtistSongsShuffledWithRelated(artist.name, initialArtistSongs = emptyList())
            } finally {
                _uiState.update { it.copy(launchingRadio = null) }
            }
        }
    }
}
