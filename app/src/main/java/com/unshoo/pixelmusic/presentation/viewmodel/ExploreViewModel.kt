package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ExplorePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import javax.inject.Inject
import kotlinx.coroutines.async

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContinuationLoading: Boolean = false,
    val homePageSections: List<HomePage.Section> = emptyList(),
    val homePageContinuation: String? = null,
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val chartsPage: ChartsPage? = null,
    val error: String? = null,
    val selectedFilter: String = "All"
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playbackStatsRepository: com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        // Restore from in-process cache immediately so the UI doesn't flash empty
        restoreFromCache()
        loadData()
    }

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(YTItem::class.java, YTItemTypeAdapter())
            .create()
    }

    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "explore_cache.json")
    }

    private fun restoreFromCache() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                // Validity period: 24 hours
                if (System.currentTimeMillis() - cache.timestamp < 86400000L) {
                    _uiState.update {
                        it.copy(
                            isLoading = true, // still loading fresh data
                            homePageSections = cache.sections,
                            homePageContinuation = cache.continuation,
                            newReleaseAlbums = cache.albums,
                            chartsPage = cache.charts
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore explore data from cache")
        }
    }

    private fun persistToCache(state: ExploreUiState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cache = ExploreCacheModel(
                    sections = state.homePageSections,
                    albums = state.newReleaseAlbums,
                    charts = state.chartsPage,
                    continuation = state.homePageContinuation,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(cache)
                cacheFile.writeText(json)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist explore data to cache")
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                // Only show loading spinner if we have no cached data at all
                val hasCachedData = _uiState.value.homePageSections.isNotEmpty() ||
                        _uiState.value.newReleaseAlbums.isNotEmpty() ||
                        _uiState.value.chartsPage != null
                _uiState.update { it.copy(isLoading = !hasCachedData, error = null) }
            }
            try {
                // Fetch Explore sections parallelly
                val homeDeferred = async(Dispatchers.IO) { YouTube.home().getOrNull() }
                val exploreDeferred = async(Dispatchers.IO) { YouTube.explore().getOrNull() }
                val chartsDeferred = async(Dispatchers.IO) { YouTube.getChartsPage().getOrNull() }
                val historyDeferred = async(Dispatchers.IO) { playbackStatsRepository.loadPlaybackHistory(limit = 15) }
                val newReleasesDeferred = async(Dispatchers.IO) { YouTube.newReleaseAlbums().getOrNull() }

                val home = homeDeferred.await()
                val explore = exploreDeferred.await()
                val charts = chartsDeferred.await()
                val history = historyDeferred.await()
                val newReleasesResult = newReleasesDeferred.await()

                if (home == null && explore == null && charts == null) {
                    // Only show error if we also have no cached data
                    val hasCachedData = _uiState.value.homePageSections.isNotEmpty() ||
                            _uiState.value.newReleaseAlbums.isNotEmpty() ||
                            _uiState.value.chartsPage != null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (!hasCachedData) "Failed to fetch explore data from YouTube Music. Please check your connection." else null
                        )
                    }
                } else {
                    val userActivityQuery = if (history.isNotEmpty()) {
                        val artistCounts = history.mapNotNull { it.artist }.groupingBy { it }.eachCount()
                        artistCounts.maxByOrNull { it.value }?.key ?: "Bollywood"
                    } else {
                        "Bollywood"
                    }

                    // Load community playlists for user's favorite artist as guest fallback
                    val communityPlaylistsResult = withContext(Dispatchers.IO) {
                        YouTube.search(
                            query = "$userActivityQuery playlist",
                            filter = YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                        ).getOrNull()
                    }

                    val communityPlaylists = communityPlaylistsResult?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()

                    val rawSections = home?.sections ?: emptyList()
                    val updatedSections = rawSections.toMutableList()

                    // Check if logged in to fetch user account playlists
                    val personalPlaylists = if (YouTube.hasLoginCookie()) {
                        YouTube.library("FEmusic_liked_playlists").getOrNull()?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()
                    } else {
                        emptyList()
                    }

                    if (personalPlaylists.isNotEmpty()) {
                        // Logged in: Add "Your Playlists" section
                        // Also remove any generic "Trending community playlists" if present
                        updatedSections.removeAll { it.title.contains("trending", ignoreCase = true) }
                        
                        // Let's insert "Your Playlists" section
                        updatedSections.add(0, HomePage.Section(
                            title = "Your Playlists",
                            label = "From your YouTube Music Account",
                            thumbnail = null,
                            endpoint = null,
                            items = personalPlaylists
                        ))
                    } else {
                        // Guest mode / Fallback: Fetch search-based community playlists
                        if (communityPlaylists.isNotEmpty()) {
                            updatedSections.removeAll { it.title.contains("trending", ignoreCase = true) }
                            updatedSections.add(HomePage.Section(
                                title = "Community Playlists",
                                label = "Based on your activity for $userActivityQuery",
                                thumbnail = null,
                                endpoint = null,
                                items = communityPlaylists
                            ))
                        }
                    }

                    val finalNewReleases = if (!newReleasesResult.isNullOrEmpty()) newReleasesResult else explore?.newReleaseAlbums ?: emptyList()

                    val newState = ExploreUiState(
                        isLoading = false,
                        isRefreshing = false,
                        homePageSections = updatedSections,
                        homePageContinuation = home?.continuation,
                        newReleaseAlbums = finalNewReleases,
                        chartsPage = charts,
                        selectedFilter = _uiState.value.selectedFilter
                    )
                    _uiState.value = newState
                    persistToCache(newState)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Explore screen data")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.localizedMessage ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        val continuation = currentState.homePageContinuation
        if (currentState.isContinuationLoading || continuation == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isContinuationLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    YouTube.home(continuation = continuation).getOrNull()
                }
                if (result != null) {
                    _uiState.update {
                        val newState = it.copy(
                            isContinuationLoading = false,
                            homePageSections = it.homePageSections + result.sections,
                            homePageContinuation = result.continuation
                        )
                        persistToCache(newState)
                        newState
                    }
                } else {
                    _uiState.update { it.copy(isContinuationLoading = false) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more Explore screen sections")
                _uiState.update { it.copy(isContinuationLoading = false) }
            }
        }
    }

    fun setSelectedFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }
}

private data class ExploreCacheModel(
    val sections: List<HomePage.Section>,
    val albums: List<AlbumItem>,
    val charts: ChartsPage?,
    val continuation: String?,
    val timestamp: Long
)

private class YTItemTypeAdapter : com.google.gson.JsonSerializer<YTItem>, com.google.gson.JsonDeserializer<YTItem> {
    override fun serialize(src: YTItem, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val obj = context.serialize(src).asJsonObject
        obj.addProperty("type", src::class.java.simpleName)
        return obj
    }

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): YTItem {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val clazz = when (type) {
            "SongItem" -> SongItem::class.java
            "AlbumItem" -> AlbumItem::class.java
            "PlaylistItem" -> PlaylistItem::class.java
            "ArtistItem" -> ArtistItem::class.java
            else -> throw com.google.gson.JsonParseException("Unknown type: $type")
        }
        return context.deserialize(obj, clazz)
    }
}
