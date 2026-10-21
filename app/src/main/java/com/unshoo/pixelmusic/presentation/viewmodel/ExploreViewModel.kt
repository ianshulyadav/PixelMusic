package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import com.unshoo.pixelmusic.presentation.model.ExploreChipUiModel
import com.unshoo.pixelmusic.presentation.model.ExploreItemUiModel
import com.unshoo.pixelmusic.presentation.model.ExploreSectionUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.feed.FeedArtist
import com.unshoo.pixelmusic.data.feed.ImageDto
import com.unshoo.pixelmusic.data.feed.RecentTrack
import com.unshoo.pixelmusic.data.feed.RecentTrackArtistRef
import javax.inject.Inject

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContinuationLoading: Boolean = false,
    val isChartsLoading: Boolean = false,
    val homePageSections: List<HomePage.Section> = emptyList(),
    val homePageContinuation: String? = null,
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val albumsForYou: List<AlbumItem> = emptyList(),
    val chartsPage: unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage? = null,
    val error: String? = null,
    val selectedFilter: String = "All",
    val recentMixes: List<Playlist> = emptyList(),
    val libraryPlaylists: List<Playlist> = emptyList(),
    val moodChips: List<HomePage.Chip> = emptyList(),
    val explorePageSections: List<HomePage.Section> = emptyList(),
    val activeMoodChip: HomePage.Chip? = null,
    val localSongs: Map<String, Song> = emptyMap(),
    val localTopArtists: List<FeedArtist> = emptyList(),
    val localHighlyRotatoryTracks: List<RecentTrack> = emptyList(),
    val localRecentlyAddedSongs: List<Song> = emptyList(),
    val isAdvancedExploreEnabled: Boolean = true,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val datastoreRepository: DatastoreRepository,
    private val connectivityStateHolder: ConnectivityStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // --- Fine-grained decoupled StateFlows for high reactivity ---
    private val _sectionsState = MutableStateFlow<List<ExploreSectionUiModel>>(emptyList())
    val sectionsState: StateFlow<List<ExploreSectionUiModel>> = _sectionsState.asStateFlow()

    private val _moodChipsState = MutableStateFlow<List<ExploreChipUiModel>>(emptyList())
    val moodChipsState: StateFlow<List<ExploreChipUiModel>> = _moodChipsState.asStateFlow()

    private val _activeChipState = MutableStateFlow<ExploreChipUiModel?>(null)
    val activeChipState: StateFlow<ExploreChipUiModel?> = _activeChipState.asStateFlow()

    private val _isLoadingState = MutableStateFlow(true)
    val isLoadingState: StateFlow<Boolean> = _isLoadingState.asStateFlow()

    private val _isRefreshingState = MutableStateFlow(false)
    val isRefreshingState: StateFlow<Boolean> = _isRefreshingState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    // Backward-compatible monolithic state
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var stage2Job: Job? = null
    @Volatile
    private var hasFetchedFromNetwork = false
    @Volatile
    private var lastFetchTimestamp: Long = 0L
    private var initialHomeContinuation: String? = null

    companion object {
        private const val EXPLORE_CACHE_EXPIRATION_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(YTItem::class.java, YTItemTypeAdapter())
            .create()
    }

    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "explore_cache.json")
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                restoreFromCache()
            }
            val isCacheFresh = (System.currentTimeMillis() - lastFetchTimestamp) < EXPLORE_CACHE_EXPIRATION_MS
            if (_sectionsState.value.isEmpty() || !isCacheFresh) {
                loadDataInternal(forceRefresh = false)
            }
        }
        viewModelScope.launch {
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val mixes = playlists.filter {
                    (it.source == "LASTFM_MIX" || it.source == "AI" || it.isAiGenerated) &&
                            it.songIds.isNotEmpty() &&
                            !it.name.contains("deleted", ignoreCase = true)
                }.sortedByDescending { it.lastModified }
                val libPlaylists = playlists.filter { !it.isQueueGenerated && it.id != "_downloaded_" && it.source != "LASTFM_MIX" && it.source != "AI" && !it.isAiGenerated }
                    .sortedByDescending { it.lastModified }
                _uiState.update { it.copy(recentMixes = mixes, libraryPlaylists = libPlaylists) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.advancedExplorePageFlow
                .distinctUntilChanged()
                .collectIndexed { index, enabled ->
                    _uiState.update { it.copy(isAdvancedExploreEnabled = enabled) }
                    if (index > 0) {
                        loadData(forceRefresh = true)
                    }
                }
        }
        viewModelScope.launch {
            YouTube.personalizedExploreEnabled = runCatching {
                userPreferencesRepository.youtubePersonalizedExploreFlow.first()
            }.getOrDefault(true)

            userPreferencesRepository.youtubePersonalizedExploreFlow
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    YouTube.personalizedExploreEnabled = enabled
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    loadData(forceRefresh = true)
                }
        }
        viewModelScope.launch {
            userPreferencesRepository.youtubePersonalizedQueueFlow
                .distinctUntilChanged()
                .collect { enabled ->
                    YouTube.personalizedQueueEnabled = enabled
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadedSongs: List<Song> = runCatching {
                    com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                        .songRepository().getDownloadedSongs().map { it.toNativeSong() }
                }.getOrDefault(emptyList())

                musicDao.getHomeMixPreviewSongs(
                    limit = 30,
                    allowedParentDirs = emptyList(),
                    applyDirectoryFilter = false
                ).collect { localEntities ->
                    val localSongsList = localEntities.map { it.toSong() }
                    val combinedRecent = (downloadedSongs + localSongsList)
                        .distinctBy { it.id }
                        .sortedByDescending { it.dateAdded }
                        .take(20)

                    val localMap = combinedRecent.associateBy { it.id }

                    _uiState.update { current ->
                        current.copy(
                            localRecentlyAddedSongs = combinedRecent,
                            localSongs = current.localSongs + localMap
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe recently added songs")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = playbackStatsRepository.loadPlaybackHistory(limit = 60)
                if (history.isNotEmpty()) {
                    val rotatoryFrequency = history.groupingBy { it.songId }.eachCount()
                    val highlyRotatoryEntries = history
                        .distinctBy { it.songId }
                        .sortedByDescending { rotatoryFrequency[it.songId] ?: 1 }
                        .take(15)

                    val rotatoryTracks = highlyRotatoryEntries.map { entry ->
                        RecentTrack(
                            name = entry.title ?: "Unknown",
                            artist = RecentTrackArtistRef(name = entry.artist ?: "Unknown artist"),
                            album = RecentTrackArtistRef(name = ""),
                            image = entry.thumbnail?.let { listOf(ImageDto(it, "extralarge")) }.orEmpty(),
                            url = entry.songId
                        )
                    }

                    val artistFrequency = history
                        .mapNotNull { it.artist?.takeIf(String::isNotBlank) }
                        .map { it.replace(Regex("""(?i)\s*-\s*topic$"""), "").trim() }
                        .filter {
                            val lower = it.lowercase(java.util.Locale.ROOT)
                            lower !in setOf("unknown", "unknown artist", "<unknown>", "various artists", "various")
                        }
                        .groupingBy { it.lowercase(java.util.Locale.ROOT) }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(10)

                    val topLocalArtists = artistFrequency.map { entry ->
                        val matchingEntry = history.firstOrNull {
                            it.artist?.replace(Regex("""(?i)\s*-\s*topic$"""), "")?.trim()?.equals(entry.key, ignoreCase = true) == true
                        }
                        val displayName = matchingEntry?.artist?.replace(Regex("""(?i)\s*-\s*topic$"""), "")?.trim()
                            ?: entry.key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                        FeedArtist(
                            name = displayName,
                            browseId = null,
                            artworkUrl = matchingEntry?.thumbnail
                        )
                    }

                    _uiState.update { current ->
                        current.copy(
                            localHighlyRotatoryTracks = rotatoryTracks,
                            localTopArtists = topLocalArtists
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load local rotatory history and top artists")
            }
        }
    }

    private suspend fun restoreFromCache() {
        try {
            if (cacheFile.exists()) {
                if (userPreferencesRepository.localModeEnabledFlow.first() || !connectivityStateHolder.isOnline.value) {
                    loadOfflineExploreData()
                    return
                }
                if (System.currentTimeMillis() - cacheFile.lastModified() > 24 * 60 * 60 * 1000L) {
                    cacheFile.delete()
                    return
                }
                val json = cacheFile.readText()
                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                if (cache != null && cache.sections.isNotEmpty()) {
                    val age = System.currentTimeMillis() - cache.timestamp
                    if (age < EXPLORE_CACHE_EXPIRATION_MS) {
                        val uiSections = cache.sections.map { it.toUiModel() }
                        val uiChips = cache.moodChips.map { ExploreChipUiModel(it.title, it.endpoint?.browseId, it.endpoint?.params) }
                        _sectionsState.value = uiSections
                        _moodChipsState.value = uiChips
                        _isLoadingState.value = false
                        lastFetchTimestamp = cache.timestamp
                        hasFetchedFromNetwork = true
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                homePageSections = cache.sections,
                                moodChips = cache.moodChips,
                                newReleaseAlbums = cache.newReleaseAlbums,
                                albumsForYou = cache.albumsForYou
                            )
                        }
                    } else {
                        Timber.d("Explore cache expired (age: ${age / 60000} min > 30 min). Holding calm loading indicator until fresh content arrives.")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore explore data from cache")
            try { cacheFile.delete() } catch (_: Exception) {}
        }
    }

    private fun persistToCache(
        sections: List<HomePage.Section>,
        moodChips: List<HomePage.Chip> = emptyList(),
        newReleases: List<AlbumItem> = emptyList(),
        albums: List<AlbumItem> = emptyList()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cache = ExploreCacheModel(
                    sections = sections.take(20),
                    moodChips = moodChips,
                    newReleaseAlbums = newReleases.take(20),
                    albumsForYou = albums.take(20),
                    albums = albums.take(20),
                    charts = null,
                    continuation = null,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(cache)
                if (json.length < 500 * 1024) {
                    cacheFile.writeText(json)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist explore data to cache")
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadDataInternal(forceRefresh)
        }
    }

    private suspend fun loadDataInternal(forceRefresh: Boolean) {
        val isLocalMode = userPreferencesRepository.localModeEnabledFlow.first()
        if (isLocalMode || !connectivityStateHolder.isOnline.value) {
            loadOfflineExploreData()
            return
        }
        val isCacheFresh = (System.currentTimeMillis() - lastFetchTimestamp) < EXPLORE_CACHE_EXPIRATION_MS
        if (!forceRefresh && isCacheFresh && _sectionsState.value.isNotEmpty()) {
            return
        }
        stage2Job?.cancel()

        if (forceRefresh) {
            _isRefreshingState.value = true
            _errorState.value = null
            _uiState.update { it.copy(isRefreshing = true, error = null) }
        } else {
            val hasData = _sectionsState.value.isNotEmpty()
            _isLoadingState.value = !hasData
            _errorState.value = null
            _uiState.update { it.copy(isLoading = !hasData, error = null) }
        }

        try {
            val isAdvancedExploreEnabled = userPreferencesRepository.advancedExplorePageFlow.first()
            _uiState.update { it.copy(isAdvancedExploreEnabled = isAdvancedExploreEnabled) }

            // Stage 1: Fast concurrent initial fetch for YouTube Home & New Release Albums
            val (initialHome, ytNewReleases) = withContext(Dispatchers.IO) {
                val homeDeferred = async { runCatching { YouTube.home().getOrNull() }.getOrNull() }
                val newReleasesDeferred = async { runCatching { YouTube.newReleaseAlbums().getOrNull().orEmpty() }.getOrDefault(emptyList()) }
                Pair(homeDeferred.await(), newReleasesDeferred.await())
            }

            if (initialHome != null) {
                val combinedSections = ArrayList<HomePage.Section>()
                combinedSections.addAll(initialHome.sections)
                val currentContinuation = initialHome.continuation

                val cookies = runCatching { datastoreRepository.cookies.first() }.getOrNull()
                val isYtConnected = cookies?.toRawCookie()?.let {
                    it.contains("SAPISID=") || it.contains("__Secure-3PAPISID=")
                } == true

                // Preserve video sections (e.g. Music videos for you) while keeping pure audio for other shelves
                val rawSections = combinedSections.map { section ->
                    val isVideoSection = section.title.contains("video", ignoreCase = true)
                    if (isVideoSection) section else section.copy(items = section.items.filterVideo(true))
                }.filter { section ->
                    val title = section.title.lowercase()
                    !title.contains("trending") &&
                    !title.contains("long listens") &&
                    !title.contains("local") &&
                    section.items.isNotEmpty()
                }.distinctBy { it.title }

                // Extract personalized new releases directly from user's YouTube Home feed (or fallback to authenticated new releases)
                val personalizedNewReleases = rawSections.filter { section ->
                    val t = section.title.lowercase()
                    !t.contains("video") && !t.contains("videos") && (
                        t.contains("released") ||
                        t.contains("new release") || t.contains("new releases") ||
                        t.contains("new album") || t.contains("latest release") ||
                        t.contains("new music") || t.contains("recent release") ||
                        t.contains("novedades") || t.contains("nouveautés") ||
                        t.contains("veröffentlichungen") || t.contains("release radar") ||
                        t.contains("new for you") ||
                        section.label?.lowercase()?.contains("release") == true
                    )
                }.flatMap { it.items }.mapNotNull { item ->
                    when (item) {
                        is AlbumItem -> item
                        is SongItem -> AlbumItem(
                            browseId = item.id,
                            playlistId = "",
                            title = item.title,
                            artists = item.artists,
                            year = null,
                            thumbnail = item.thumbnail,
                            explicit = item.explicit
                        )
                        else -> null
                    }
                }.distinctBy { it.browseId }

                // Combine personalized home releases with official YouTube Music new release albums
                val resolvedNewReleases = (personalizedNewReleases + ytNewReleases).distinctBy { it.browseId }

                // Extract personalized albums directly from user's authenticated YouTube Home feed
                val personalizedAlbums = rawSections.filter { section ->
                    val t = section.title.lowercase()
                    !t.contains("video") && !t.contains("videos") && (
                        t.contains("album") || section.label?.lowercase()?.contains("album") == true ||
                        (t.contains("for you") && section.items.any { it is AlbumItem })
                    )
                }.flatMap { it.items }.mapNotNull { item ->
                    when (item) {
                        is AlbumItem -> item
                        is PlaylistItem -> if (item.title.contains("album", ignoreCase = true) || item.id.startsWith("MPRE")) {
                            AlbumItem(
                                browseId = item.id,
                                playlistId = item.id,
                                title = item.title,
                                artists = listOfNotNull(item.author),
                                year = null,
                                thumbnail = item.thumbnail ?: "",
                                explicit = false
                            )
                        } else null
                        else -> null
                    }
                }.distinctBy { it.browseId }

                // Progressive streaming: map to domain UI models once
                val uiSections = rawSections.map { it.toUiModel() }
                val rawChips = initialHome.chips ?: emptyList()
                val uiChips = rawChips.map { ExploreChipUiModel(it.title, it.endpoint?.browseId, it.endpoint?.params) }

                _sectionsState.value = uiSections
                _moodChipsState.value = uiChips
                _isLoadingState.value = false
                _isRefreshingState.value = false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isContinuationLoading = false,
                        homePageSections = rawSections,
                        homePageContinuation = currentContinuation,
                        newReleaseAlbums = resolvedNewReleases,
                        albumsForYou = personalizedAlbums,
                        moodChips = rawChips,
                        isAdvancedExploreEnabled = isAdvancedExploreEnabled
                    )
                }

                initialHomeContinuation = currentContinuation
                lastFetchTimestamp = System.currentTimeMillis()
                persistToCache(
                    sections = rawSections,
                    moodChips = rawChips,
                    newReleases = resolvedNewReleases,
                    albums = personalizedAlbums
                )
                hasFetchedFromNetwork = true
            } else if (_sectionsState.value.isEmpty()) {
                // Graceful fallback to cached data if network failed and sections are empty
                val fallbackCache = runCatching {
                    if (cacheFile.exists()) {
                        val json = cacheFile.readText()
                        gson.fromJson(json, ExploreCacheModel::class.java)
                    } else null
                }.getOrNull()

                if (fallbackCache != null && fallbackCache.sections.isNotEmpty()) {
                    val uiSections = fallbackCache.sections.map { it.toUiModel() }
                    _sectionsState.value = uiSections
                    _isLoadingState.value = false
                    _isRefreshingState.value = false
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            homePageSections = fallbackCache.sections
                        )
                    }
                } else {
                    val msg = "Failed to fetch explore data. Check connection."
                    _errorState.value = msg
                    _isLoadingState.value = false
                    _isRefreshingState.value = false
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = msg) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading Explore screen data")
            val msg = e.localizedMessage ?: "Unknown error occurred"
            _errorState.value = msg
            _isLoadingState.value = false
            _isRefreshingState.value = false
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = msg) }
        }
    }


    fun loadChartsIfNeeded(forceRefresh: Boolean = false) {
        if (!forceRefresh && (_uiState.value.chartsPage != null || _uiState.value.isChartsLoading)) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChartsLoading = true) }
            val charts = runCatching { YouTube.getChartsPage().getOrNull() }.getOrNull()
            _uiState.update { it.copy(isChartsLoading = false, chartsPage = charts) }
        }
    }

    fun loadMore() {
        val continuation = _uiState.value.homePageContinuation ?: return
        if (_uiState.value.isContinuationLoading || _uiState.value.isLoading || _uiState.value.isRefreshing) return
        if (!_uiState.value.isAdvancedExploreEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isContinuationLoading = true) }
            val continuationHome = withContext(Dispatchers.IO) {
                runCatching { YouTube.home(continuation = continuation).getOrNull() }.getOrNull()
            }

            if (continuationHome != null && continuationHome.sections.isNotEmpty()) {
                val newRawSections = continuationHome.sections.map { section ->
                    val isVideoSection = section.title.contains("video", ignoreCase = true)
                    if (isVideoSection) section else section.copy(items = section.items.filterVideo(true))
                }.filter { section ->
                    val title = section.title.lowercase()
                    !title.contains("trending") &&
                    !title.contains("long listens") &&
                    !title.contains("local") &&
                    section.items.isNotEmpty()
                }

                val currentRaw = _uiState.value.homePageSections
                val mergedRaw = (currentRaw + newRawSections).distinctBy { it.title }
                val uiSections = mergedRaw.map { it.toUiModel() }

                _sectionsState.value = uiSections
                _uiState.update {
                    it.copy(
                        isContinuationLoading = false,
                        homePageSections = mergedRaw,
                        homePageContinuation = continuationHome.continuation
                    )
                }
                persistToCache(
                    sections = mergedRaw,
                    moodChips = _uiState.value.moodChips,
                    newReleases = _uiState.value.newReleaseAlbums,
                    albums = _uiState.value.albumsForYou
                )
            } else {
                _uiState.update { it.copy(isContinuationLoading = false, homePageContinuation = null) }
            }
        }
    }

    fun setSelectedFilter(filter: String) {
        if (filter.equals("All", ignoreCase = true)) {
            selectMoodChip(null)
        } else if (filter.equals("Local", ignoreCase = true)) {
            _activeChipState.value = null
            _uiState.update {
                it.copy(
                    selectedFilter = "Local",
                    activeMoodChip = null,
                    explorePageSections = emptyList(),
                    isLoading = false,
                    error = null
                )
            }
        } else {
            _uiState.update { it.copy(selectedFilter = filter) }
            val matchingChip = _uiState.value.moodChips.firstOrNull {
                it.title.equals(filter, ignoreCase = true) ||
                it.title.replace(" ", "").equals(filter.replace(" ", ""), ignoreCase = true)
            }
            selectMoodChipByName(filter, matchingChip)
        }
    }

    fun selectMoodChip(chip: HomePage.Chip?) {
        if (chip == null) {
            _activeChipState.value = null
            _uiState.update {
                it.copy(
                    activeMoodChip = null,
                    explorePageSections = emptyList(),
                    selectedFilter = "All",
                    isLoading = false,
                    error = null,
                    homePageContinuation = initialHomeContinuation
                )
            }
            _sectionsState.value = _uiState.value.homePageSections.map { it.toUiModel() }
            if (_uiState.value.homePageSections.isEmpty()) {
                loadData(false)
            }
        } else {
            selectMoodChipByName(chip.title, chip)
        }
    }

    fun selectMoodChipByName(name: String, chip: HomePage.Chip? = null) {
        viewModelScope.launch {
            val effectiveChip = chip ?: _uiState.value.moodChips.firstOrNull {
                it.title.equals(name, ignoreCase = true) ||
                it.title.replace(" ", "").equals(name.replace(" ", ""), ignoreCase = true)
            } ?: HomePage.Chip(
                title = name,
                endpoint = null,
                deselectEndPoint = null
            )
            _activeChipState.value = ExploreChipUiModel(effectiveChip.title, effectiveChip.endpoint?.browseId, effectiveChip.endpoint?.params)
            _uiState.update { it.copy(activeMoodChip = effectiveChip, selectedFilter = effectiveChip.title, isLoading = true, error = null) }
            _isLoadingState.value = true

            withContext(Dispatchers.IO) {
                val params = effectiveChip.endpoint?.params
                if (params != null) {
                    val result = runCatching {
                        YouTube.home(params = params).getOrThrow()
                    }
                    if (result.isSuccess && result.getOrNull()?.sections?.isNotEmpty() == true) {
                        val homeResult = result.getOrThrow()
                        val rawSections = homeResult.sections
                        val uiSections = rawSections.map { it.toUiModel() }
                        _sectionsState.value = uiSections
                        _isLoadingState.value = false
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                explorePageSections = rawSections,
                                homePageContinuation = homeResult.continuation
                            )
                        }
                        return@withContext
                    }
                }
                val endpoint = effectiveChip.endpoint
                if (endpoint != null && endpoint.browseId.isNotBlank()) {
                    val result = runCatching {
                        YouTube.explore(browseId = endpoint.browseId, params = endpoint.params).getOrThrow()
                    }
                    if (result.isSuccess && result.getOrNull()?.sections?.isNotEmpty() == true) {
                        val exp = result.getOrThrow()
                        val rawSections = exp.sections
                        val uiSections = rawSections.map { it.toUiModel() }
                        _sectionsState.value = uiSections
                        _isLoadingState.value = false
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                explorePageSections = rawSections,
                                homePageContinuation = null
                            )
                        }
                        return@withContext
                    }
                }
                fetchMoodFallbackSections(name)
            }
        }
    }

    private suspend fun fetchMoodFallbackSections(moodName: String) {
        val playlistsResult = runCatching {
            YouTube.search(moodName, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                ?: YouTube.search(moodName, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
        }.getOrNull()

        val songsResult = runCatching {
            YouTube.search(moodName, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()

        val sections = mutableListOf<HomePage.Section>()
        val playlistItems = playlistsResult?.items?.filterIsInstance<PlaylistItem>().orEmpty()
        if (playlistItems.isNotEmpty()) {
            sections.add(
                HomePage.Section(
                    title = "$moodName Mixes & Playlists",
                    label = "Playlists",
                    thumbnail = playlistItems.firstOrNull()?.thumbnail,
                    endpoint = null,
                    items = playlistItems.take(20)
                )
            )
        }
        val songItems = songsResult?.items?.filterIsInstance<SongItem>()?.filterVideo(true).orEmpty()
        if (songItems.isNotEmpty()) {
            sections.add(
                HomePage.Section(
                    title = "Popular in $moodName",
                    label = "Songs",
                    thumbnail = songItems.firstOrNull()?.thumbnail,
                    endpoint = null,
                    items = songItems.take(20)
                )
            )
        }

        val uiSections = sections.map { it.toUiModel() }
        _sectionsState.value = uiSections
        _isLoadingState.value = false
        _uiState.update {
            it.copy(
                isLoading = false,
                explorePageSections = sections
            )
        }
    }

    private suspend fun loadOfflineExploreData() = withContext(Dispatchers.IO) {
        _isLoadingState.value = false
        _isRefreshingState.value = false
        _errorState.value = null

        val downloadedSongs = runCatching {
            com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                .songRepository().getDownloadedSongs().map { it.toNativeSong() }
        }.getOrDefault(emptyList()).filter {
            com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it)
        }

        val localEntities = musicDao.getSongsBySourceType(0) // SourceType.LOCAL
        val localSongs = localEntities.map { it.toSong() }.filter {
            com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it)
        }

        val allOfflineSongs = com.unshoo.pixelmusic.utils.OfflineAudioResolver.deduplicateQueue(downloadedSongs + localSongs).first
        val localMap = allOfflineSongs.associateBy { it.id }

        val offlineSongItems = allOfflineSongs.take(30).map { song ->
            SongItem(
                id = song.id,
                title = song.title,
                artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(name = song.artist, id = null)),
                album = song.album.takeIf { it.isNotBlank() }?.let { unshoo.ianshulyadav.pixelmusic.innertube.models.Album(name = it, id = null) },
                duration = (song.duration / 1000).toInt(),
                thumbnail = song.albumArtUriString.orEmpty(),
                explicit = false,
                endpoint = null
            )
        }

        val offlineSection = if (offlineSongItems.isNotEmpty()) {
            HomePage.Section(
                title = "Offline Tracks",
                label = "Downloaded & Local Music",
                thumbnail = null,
                endpoint = null,
                items = offlineSongItems
            )
        } else null

        val rawSections = listOfNotNull(offlineSection)
        val uiSections = rawSections.map { it.toUiModel() }

        _sectionsState.value = uiSections
        _moodChipsState.value = emptyList()
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                homePageSections = rawSections,
                explorePageSections = emptyList(),
                newReleaseAlbums = emptyList(),
                albumsForYou = emptyList(),
                moodChips = emptyList(),
                localSongs = localMap,
                localRecentlyAddedSongs = allOfflineSongs.take(20)
            )
        }
    }

    private fun HomePage.Section.toUiModel(): ExploreSectionUiModel {
        val uiItems = items.mapNotNull { item ->
            when (item) {
                is SongItem -> ExploreItemUiModel.SongModel(item.toNativeSong(), item)
                is AlbumItem -> ExploreItemUiModel.AlbumModel(item)
                is PlaylistItem -> ExploreItemUiModel.PlaylistModel(item)
                is ArtistItem -> ExploreItemUiModel.ArtistModel(item)
                else -> null
            }
        }
        return ExploreSectionUiModel(
            id = title,
            title = title,
            label = label,
            items = uiItems
        )
    }
}

@androidx.annotation.Keep
data class ExploreCacheModel(
    val sections: List<HomePage.Section> = emptyList(),
    val moodChips: List<HomePage.Chip> = emptyList(),
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val albumsForYou: List<AlbumItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val charts: ChartsPage? = null,
    val continuation: String? = null,
    val timestamp: Long = 0L
)

private class YTItemTypeAdapter : com.google.gson.JsonSerializer<YTItem>, com.google.gson.JsonDeserializer<YTItem> {
    override fun serialize(src: YTItem, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val obj = com.google.gson.JsonObject()
        when (src) {
            is SongItem -> {
                obj.addProperty("type", "song")
                obj.add("data", context.serialize(src, SongItem::class.java))
            }
            is AlbumItem -> {
                obj.addProperty("type", "album")
                obj.add("data", context.serialize(src, AlbumItem::class.java))
            }
            is PlaylistItem -> {
                obj.addProperty("type", "playlist")
                obj.add("data", context.serialize(src, PlaylistItem::class.java))
            }
            is ArtistItem -> {
                obj.addProperty("type", "artist")
                obj.add("data", context.serialize(src, ArtistItem::class.java))
            }
        }
        return obj
    }

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): YTItem? {
        return try {
            val obj = json.asJsonObject
            val type = obj.get("type")?.asString ?: return null
            val targetClass = when (type) {
                "song", "SongItem" -> SongItem::class.java
                "album", "AlbumItem" -> AlbumItem::class.java
                "playlist", "PlaylistItem" -> PlaylistItem::class.java
                "artist", "ArtistItem" -> ArtistItem::class.java
                else -> null
            } ?: return null
            val data = obj.get("data") ?: obj
            context.deserialize(data, targetClass)
        } catch (_: Exception) {
            null
        }
    }
}
