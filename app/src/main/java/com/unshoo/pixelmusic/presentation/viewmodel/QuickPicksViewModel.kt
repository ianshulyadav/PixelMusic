package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject

val QUICK_PICKS_CATEGORIES = listOf(
    "All", "Romance", "Love", "Pump", "Punjabi", "Bollywood",
    "Chill", "Party", "Sad", "Dance", "Hip Hop", "Pop", "Indie", "Rock"
)

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks: StateFlow<List<Song>> = _quickPicks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    init {
        loadQuickPicks("All")
    }

    fun setCategory(category: String) {
        if (_selectedCategory.value == category && !_isLoading.value) {
            return
        }
        _selectedCategory.value = category
        loadQuickPicks(category)
    }

    fun refresh() {
        loadQuickPicks(_selectedCategory.value)
    }

    private fun loadQuickPicks(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _quickPicks.value = emptyList()
            try {
                val songs = withContext(Dispatchers.IO) {
                    fetchYoutubeSongs(category)
                }
                _quickPicks.value = songs
                Timber.tag("QuickPicks").d("Loaded ${songs.size} songs for category: $category")
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error fetching quick picks for category: $category")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchYoutubeSongs(category: String): List<Song> {
        val uniqueAccountSongs = mutableListOf<SongItem>()
        var defaultHome: HomePage? = null
        
        try {
            defaultHome = YouTube.home().getOrNull()
        } catch (e: Exception) {
            Timber.tag("QuickPicks").e(e, "Error loading default home page")
        }

        var targetHome = defaultHome
        var usingFilteredHome = false

        if (category != "All" && defaultHome?.chips != null) {
            val mappedTitles = when (category) {
                "Chill" -> listOf("Relax", "Focus", "Chill")
                "Pump" -> listOf("Workout", "Energize")
                "Party" -> listOf("Energize", "Party")
                "Romance", "Love" -> listOf("Romance", "Feel Good")
                else -> listOf(category)
            }
            val matchingChip = defaultHome.chips.firstOrNull { chip ->
                mappedTitles.any { title -> chip.title.contains(title, ignoreCase = true) }
            }
            if (matchingChip != null && matchingChip.endpoint?.params != null) {
                try {
                    val filteredHome = YouTube.home(params = matchingChip.endpoint.params).getOrNull()
                    if (filteredHome != null) {
                        targetHome = filteredHome
                        usingFilteredHome = true
                    }
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").e(e, "Error loading filtered home page for chip: ${matchingChip.title}")
                }
            }
        }

        val accountSongsPool = mutableListOf<SongItem>()
        if (targetHome != null) {
            val quickPicksSection = targetHome.sections.firstOrNull {
                it.title.contains("quick picks", ignoreCase = true) ||
                it.title.contains("quick", ignoreCase = true)
            }
            if (quickPicksSection != null) {
                accountSongsPool.addAll(quickPicksSection.items.filterIsInstance<SongItem>())
            }

            val otherSectionsSongs = targetHome.sections
                .filter { it != quickPicksSection }
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
            accountSongsPool.addAll(otherSectionsSongs)

            val continuation = targetHome.continuation
            if (continuation != null) {
                try {
                    val continuationHome = YouTube.home(continuation = continuation).getOrNull()
                    if (continuationHome != null) {
                        val continuationSongs = continuationHome.sections
                            .flatMap { it.items }
                            .filterIsInstance<SongItem>()
                        accountSongsPool.addAll(continuationSongs)
                    }
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").e(e, "Error loading home continuation")
                }
            }
        }

        uniqueAccountSongs.addAll(accountSongsPool.distinctBy { it.id })

        val fallbackSongs = mutableListOf<SongItem>()
        if (uniqueAccountSongs.size < 50 || (!usingFilteredHome && category != "All")) {
            val mixQuery = when (category) {
                "All" -> "My Mix"
                "Romance" -> "Romance Mix"
                "Love" -> "Romance Mix"
                "Pump" -> "Workout Mix"
                "Chill" -> "Chill Mix"
                "Party" -> "Energy Mix"
                "Sad" -> "Sad Mix"
                "Dance" -> "Dance Mix"
                "Hip Hop" -> "Hip Hop Mix"
                "Pop" -> "Pop Mix"
                "Indie" -> "Indie Mix"
                "Rock" -> "Rock Mix"
                "Bollywood" -> "Bollywood Mix"
                "Punjabi" -> "Punjabi Mix"
                else -> "$category Mix"
            }
            
            // Try to find personalized Mix playlists first
            try {
                val playlistSearchResult = YouTube.search(mixQuery, SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                    ?: YouTube.search(mixQuery, SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                
                val mixPlaylist = playlistSearchResult?.items?.filterIsInstance<PlaylistItem>()?.firstOrNull {
                    it.title.contains("Mix", ignoreCase = true) || it.title.contains(category, ignoreCase = true)
                }
                
                if (mixPlaylist != null) {
                    val playlistPage = YouTube.playlist(mixPlaylist.id).getOrNull()
                    if (playlistPage != null && playlistPage.songs.isNotEmpty()) {
                        fallbackSongs.addAll(playlistPage.songs)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error loading personalized Mix playlist for: $mixQuery")
            }
            
            // If Mix playlist fetch did not yield songs, do a personalized song search
            if (fallbackSongs.isEmpty()) {
                val songSearchQuery = when (category) {
                    "All" -> "songs"
                    "Romance", "Love" -> "romantic songs"
                    "Pump" -> "workout music"
                    "Chill" -> "chill music"
                    "Party" -> "party music"
                    "Sad" -> "sad songs"
                    else -> "$category songs"
                }
                try {
                    val searchResult = YouTube.search(songSearchQuery, SearchFilter.FILTER_SONG).getOrNull()
                    if (searchResult != null) {
                        fallbackSongs.addAll(searchResult.items.filterIsInstance<SongItem>())
                        val searchContinuationToken = searchResult.continuation
                        if (fallbackSongs.distinctBy { it.id }.size < 50 && searchContinuationToken != null) {
                            val nextSearch = YouTube.searchContinuation(searchContinuationToken).getOrNull()
                            if (nextSearch != null) {
                                fallbackSongs.addAll(nextSearch.items.filterIsInstance<SongItem>())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").e(e, "Error loading fallback song search for: $songSearchQuery")
                }
            }
        }

        val combinedPool = (uniqueAccountSongs + fallbackSongs).distinctBy { it.id }.toMutableList()
        combinedPool.shuffle()
        return combinedPool.take(50).map { it.toNativeSong() }
    }
}
