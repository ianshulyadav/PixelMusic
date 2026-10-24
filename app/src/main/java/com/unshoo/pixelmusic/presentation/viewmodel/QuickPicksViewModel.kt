package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.QuickPicks
import com.unshoo.pixelmusic.data.repository.MusicRepository
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import com.unshoo.pixelmusic.utils.AppReadinessSignal
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem

private const val PREFS_NAME = "quick_picks_cache"
private const val KEY_SONGS = "songs_json"
private const val KEY_CATEGORIES = "categories_json"
private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
// Cache valid for 4 hours (shorter than before so new releases appear faster)
private const val CACHE_MAX_AGE_MS = 4 * 60 * 60 * 1000L

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao,
    private val playbackStatsRepository: PlaybackStatsRepository
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val quickPicks: StateFlow<ImmutableList<Song>> = _quickPicks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categories = MutableStateFlow<ImmutableList<String>>(persistentListOf("All", "Local"))
    val categories: StateFlow<ImmutableList<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    init {
        // Immediately populate from cache so the UI shows something on relaunch.
        loadFromCache()
        viewModelScope.launch {
            loadQuickPicks(_selectedCategory.value, forceRefresh = isCacheExpired())
            userPreferencesRepository.discoverFlow.collect { _ ->
                if (_selectedCategory.value == "All") {
                    loadQuickPicks("All", forceRefresh = isCacheExpired())
                }
            }
        }
    }

    fun setCategory(category: String) {
        if (_selectedCategory.value == category && !_isLoading.value) return
        _selectedCategory.value = category
        loadQuickPicks(category)
    }

    fun refresh(force: Boolean = false) {
        clearCache()
        loadQuickPicks(_selectedCategory.value, forceRefresh = true)
    }

    /**
     * Lifecycle-aware auto-refresh: silently reloads quickpicks in the background
     * when the cache TTL (4 hours) has lapsed. Unlike refresh(), the old cached data
     * stays visible while the new data loads — no visible loading state unless cache
     * was already empty.
     */
    fun refreshIfStale() {
        if (isCacheExpired() && !_isLoading.value) {
            loadQuickPicks(_selectedCategory.value, forceRefresh = true)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isCacheExpired(): Boolean {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        if (timestamp == 0L) return true
        return (System.currentTimeMillis() - timestamp) > CACHE_MAX_AGE_MS
    }

    private fun clearCache() {
        prefs.edit().clear().apply()
        _quickPicks.value = persistentListOf()
    }

    private fun loadFromCache() {
        try {
            if (isCacheExpired()) {
                clearCache()
                return
            }
            val songsJson = prefs.getString(KEY_SONGS, null) ?: return
            val categoriesJson = prefs.getString(KEY_CATEGORIES, null)

            val songsArray = JSONArray(songsJson)
            val songs = mutableListOf<Song>()
            for (i in 0 until songsArray.length()) {
                songs.add(songFromJson(songsArray.getJSONObject(i)))
            }
            if (songs.isNotEmpty()) _quickPicks.value = songs.toImmutableList()

            if (categoriesJson != null) {
                val catArray = JSONArray(categoriesJson)
                val cats = mutableListOf<String>()
                for (i in 0 until catArray.length()) cats.add(catArray.getString(i))
                if (cats.isNotEmpty()) _categories.value = cats.toImmutableList()
            }
        } catch (e: Exception) {
            Timber.tag("QuickPicks").w(e, "Failed to load cache")
        }
    }

    private fun saveToCache(songs: List<Song>, categories: List<String>) {
        try {
            val songsArray = JSONArray()
            songs.forEach { songsArray.put(songToJson(it)) }
            val catArray = JSONArray()
            categories.forEach { catArray.put(it) }
            prefs.edit()
                .putString(KEY_SONGS, songsArray.toString())
                .putString(KEY_CATEGORIES, catArray.toString())
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Timber.tag("QuickPicks").w(e, "Failed to save cache")
        }
    }

    private fun songToJson(song: Song): JSONObject = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("artistId", song.artistId)
        put("album", song.album)
        put("albumId", song.albumId)
        put("albumArtist", song.albumArtist ?: "")
        put("path", song.path)
        put("contentUriString", song.contentUriString)
        put("albumArtUriString", song.albumArtUriString ?: "")
        put("duration", song.duration)
        put("genre", song.genre ?: "")
        put("mimeType", song.mimeType ?: "")
        put("bitrate", song.bitrate ?: 0)
        put("sampleRate", song.sampleRate ?: 0)
        put("youtubeId", song.youtubeId ?: "")
        put("albumBrowseId", song.albumBrowseId ?: "")
    }

    private fun songFromJson(obj: JSONObject): Song = Song(
        id = obj.optString("id"),
        title = obj.optString("title"),
        artist = obj.optString("artist", ""),
        artistId = obj.optLong("artistId", 0L),
        album = obj.optString("album", ""),
        albumId = obj.optLong("albumId", 0L),
        albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() },
        path = obj.optString("path", ""),
        contentUriString = obj.optString("contentUriString", ""),
        albumArtUriString = obj.optString("albumArtUriString").takeIf { it.isNotBlank() },
        duration = obj.optLong("duration", 0L),
        genre = obj.optString("genre").takeIf { it.isNotBlank() },
        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
        bitrate = obj.optInt("bitrate", 0),
        sampleRate = obj.optInt("sampleRate", 0),
        youtubeId = obj.optString("youtubeId").takeIf { it.isNotBlank() },
        albumBrowseId = obj.optString("albumBrowseId").takeIf { it.isNotBlank() }
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Main load entry point
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadQuickPicks(category: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cacheExpired = isCacheExpired()
            val shouldRefresh = forceRefresh || cacheExpired
            if (category == "All" && !shouldRefresh && _quickPicks.value.isNotEmpty()) {
                _isLoading.value = false
                return@launch
            }
            if (cacheExpired) {
                clearCache()
            }
            if (_quickPicks.value.isEmpty()) {
                _isLoading.value = true
            }
            try {
                if (category == "All") {
                    val discover = userPreferencesRepository.discoverFlow.first()
                    when (discover) {
                        QuickPicks.DONT_SHOW -> {
                            _quickPicks.value = persistentListOf()
                        }
                        QuickPicks.QUICK_PICKS -> {
                            // Try enhanced 5-bucket algorithm first
                            val songs = loadEnhancedQuickPicks()
                            if (songs.isNotEmpty()) {
                                _quickPicks.value = songs.toImmutableList()
                                saveToCache(songs, _categories.value)
                            }
                        }
                        QuickPicks.LAST_LISTEN -> {
                            val lastPlayed = withContext(Dispatchers.IO) {
                                musicRepository.getLastPlayedSong()
                            }
                            val related = if (lastPlayed != null) {
                                withContext(Dispatchers.IO) {
                                    val id = lastPlayed.id.toLongOrNull()
                                    if (id != null) musicRepository.getRelatedSongs(id, 50) else emptyList()
                                }
                            } else emptyList()

                            if (related.isNotEmpty()) {
                                val songs = related.shuffled().take(20)
                                _quickPicks.value = songs.toImmutableList()
                                saveToCache(songs, _categories.value)
                            } else {
                                val songs = loadEnhancedQuickPicks()
                                if (songs.isNotEmpty()) {
                                    _quickPicks.value = songs.toImmutableList()
                                    saveToCache(songs, _categories.value)
                                }
                            }
                        }
                    }
                } else {
                    loadCategoryQuickPicks(category)
                }
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error loading quick picks")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5-bucket parallel algorithm — all buckets fetched concurrently
    // Final output: flat shuffled list of ~20 songs
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadEnhancedQuickPicks(): List<Song> = coroutineScope {
        val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()

        // 1. Gather historical seeds from user's local/online history and favorites
        val localHistoryList = try {
            playbackStatsRepository.loadPlaybackHistory(limit = 40)
        } catch (e: Exception) {
            emptyList()
        }

        val localFavoritesList = try {
            musicRepository.getFavoriteSongsOnce(com.unshoo.pixelmusic.data.model.StorageFilter.ALL)
        } catch (e: Exception) {
            emptyList()
        }

        val ytHistoryList = try {
            YouTube.musicHistory().getOrNull()?.sections?.flatMap { it.songs } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val subscribedIds = userPreferencesRepository.subscribedArtistIdsFlow.first().toList()

        // Extract seed songs
        val seedSongs = mutableListOf<Pair<String, String>>()
        
        // Add from online YT Music history
        ytHistoryList.forEach { songItem ->
            if (!songItem.id.isNullOrBlank()) {
                seedSongs.add(songItem.id to (songItem.artists.firstOrNull()?.name ?: ""))
            }
        }
        
        // Add from local playback history
        localHistoryList.forEach { historyEntry ->
            if (!historyEntry.songId.isNullOrBlank() && (historyEntry.songId.startsWith("youtube:") || historyEntry.songId.length == 11)) {
                val cleanId = historyEntry.songId.removePrefix("youtube:")
                if (cleanId.isNotBlank()) {
                    seedSongs.add(cleanId to (historyEntry.artist ?: ""))
                }
            }
        }
        
        // Add from local favorites
        localFavoritesList.forEach { favoriteSong ->
            val yId = favoriteSong.youtubeId
            if (!yId.isNullOrBlank()) {
                seedSongs.add(yId to favoriteSong.artist)
            }
        }

        // Deduplicate seeds and select randomly for freshness on every load/refresh
        val uniqueSeedSongs = seedSongs.distinctBy { it.first }.shuffled()

        // Extract seed artists
        val seedArtistNames = mutableListOf<String>()
        val seedArtistIds = mutableListOf<String>()

        subscribedIds.forEach { seedArtistIds.add(it) }
        localHistoryList.mapNotNull { it.artist }.filter { it.isNotBlank() }.forEach { seedArtistNames.add(it) }
        localFavoritesList.map { it.artist }.filter { it.isNotBlank() }.forEach { seedArtistNames.add(it) }
        ytHistoryList.flatMap { it.artists }.forEach {
            if (!it.id.isNullOrBlank()) seedArtistIds.add(it.id)
            if (it.name.isNotBlank()) seedArtistNames.add(it.name)
        }

        val uniqueSeedArtistNames = seedArtistNames.distinct().shuffled()
        val uniqueSeedArtistIds = seedArtistIds.distinct().shuffled()

        // 2. Query different recommendation endpoints concurrently
        // Bucket A: Song Mix Radios (fetch related radio mix for up to 3 distinct seed songs)
        val songMixesDeferred = uniqueSeedSongs.take(3).map { (videoId, _) ->
            async(Dispatchers.IO) {
                try {
                    val radioResult = YouTube.next(
                        WatchEndpoint(playlistId = "RDAMVM$videoId", videoId = videoId)
                    ).getOrNull()
                    radioResult?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly) ?: emptyList()
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").w(e, "Song mix radio fetch failed for videoId: $videoId")
                    emptyList()
                }
            }
        }

        // Bucket B: Similar Artist Radios (fetch similar artist radio for up to 2 subscribed/frequent artists)
        val artistRadiosDeferred = uniqueSeedArtistIds.take(2).map { artistId ->
            async(Dispatchers.IO) {
                try {
                    val artistPage = YouTube.artist(artistId).getOrNull()
                    val radioEndpoint = artistPage?.artist?.radioEndpoint
                    if (radioEndpoint != null) {
                        YouTube.next(radioEndpoint).getOrNull()?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly) ?: emptyList()
                    } else {
                        // Fallback to top song's mix radio
                        val songsSection = artistPage?.sections?.find {
                            it.title.contains("songs", ignoreCase = true) ||
                            it.title.contains("popular", ignoreCase = true)
                        }
                        val firstSong = songsSection?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        if (firstSong != null) {
                            YouTube.next(
                                WatchEndpoint(playlistId = "RDAMVM${firstSong.id}", videoId = firstSong.id)
                            ).getOrNull()?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly) ?: emptyList()
                        } else emptyList()
                    }
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").w(e, "Artist radio fetch failed for artistId: $artistId")
                    emptyList()
                }
            }
        }

        // If artist IDs are not available but we have artist names, search and fetch their radio
        val artistNameRadiosDeferred = if (uniqueSeedArtistIds.isEmpty() && uniqueSeedArtistNames.isNotEmpty()) {
            uniqueSeedArtistNames.take(2).map { artistName ->
                async(Dispatchers.IO) {
                    try {
                        val searchResult = YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                        val artistItem = searchResult?.items?.find { it is ArtistItem } as? ArtistItem
                        val artistId = artistItem?.id
                        if (artistId != null) {
                            val artistPage = YouTube.artist(artistId).getOrNull()
                            val radioEndpoint = artistPage?.artist?.radioEndpoint
                            if (radioEndpoint != null) {
                                YouTube.next(radioEndpoint).getOrNull()?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly) ?: emptyList()
                            } else {
                                val songsSection = artistPage?.sections?.find {
                                    it.title.contains("songs", ignoreCase = true) ||
                                    it.title.contains("popular", ignoreCase = true)
                                }
                                val firstSong = songsSection?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                                if (firstSong != null) {
                                    YouTube.next(
                                        WatchEndpoint(playlistId = "RDAMVM${firstSong.id}", videoId = firstSong.id)
                                    ).getOrNull()?.items?.filterIsInstance<SongItem>()?.filterVideo(pureYtMusicOnly) ?: emptyList()
                                } else emptyList()
                            }
                        } else emptyList()
                    } catch (e: Exception) {
                        Timber.tag("QuickPicks").w(e, "Artist name search/radio failed for: $artistName")
                        emptyList()
                    }
                }
            }
        } else emptyList()

        // Bucket C: YouTube Music personalized Home page sections + Trending
        val ytHomeRecommendationsDeferred = async(Dispatchers.IO) {
            try {
                val homePage = YouTube.home().getOrNull() ?: return@async emptyList<SongItem>()
                val chipTitles = homePage.chips?.map { it.title } ?: emptyList()
                val updatedCategories = (listOf("All", "Local") + chipTitles).distinct()
                _categories.value = updatedCategories.toImmutableList()

                val items = mutableListOf<SongItem>()
                val quickPicksShelf = homePage.sections.firstOrNull {
                    it.title.contains("quick", ignoreCase = true)
                }
                if (quickPicksShelf != null) {
                    items.addAll(quickPicksShelf.items.filterIsInstance<SongItem>())
                }
                // Extract items from remaining home sections
                homePage.sections.forEach { section ->
                    if (section != quickPicksShelf) {
                        items.addAll(section.items.filterIsInstance<SongItem>())
                    }
                }
                items.filterVideo(pureYtMusicOnly)
            } catch (e: Exception) {
                Timber.tag("QuickPicks").w(e, "Personalized home section fetch failed")
                emptyList()
            }
        }

        // Bucket E: Local Songs (sampled sparingly & occasionally)
        val localPopularDeferred = async(Dispatchers.IO) {
            // Include local songs only 35% of the time, and capped at 3 tracks max
            if ((1..100).random() > 35) return@async emptyList<Song>()
            try {
                val topEngagements = engagementDao.getTopPlayedSongs(10)
                val songs = if (topEngagements.isNotEmpty()) {
                    val songIds = topEngagements.map { it.songId }
                    musicRepository.getSongsByIdsOnce(songIds)
                } else {
                    musicRepository.getRandomSongs(10)
                }
                songs.shuffled().take(3)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Await all async requests
        val songMixSongs = songMixesDeferred.flatMap { it.await() }
        val artistRadioSongs = artistRadiosDeferred.flatMap { it.await() }
        val artistNameRadioSongs = artistNameRadiosDeferred.flatMap { it.await() }
        val homeRecSongs = ytHomeRecommendationsDeferred.await()
        val localPopularSongs = localPopularDeferred.await()

        val onlineSongs = (songMixSongs + artistRadioSongs + artistNameRadioSongs + homeRecSongs).map { it.toNativeSong() }

        // OFFLINE FALLBACK: 0 internet -> load all local songs + fully downloaded playlist songs
        if (onlineSongs.isEmpty()) {
            return@coroutineScope loadAllOfflineAndDownloadedSongs()
        }

        // 3. Blend online recommendations with sparse local sampling
        val combinedCandidates = mutableListOf<Song>()
        combinedCandidates.addAll(onlineSongs)
        combinedCandidates.addAll(localPopularSongs)

        val deduplicated = combinedCandidates.distinctBy { song ->
            song.youtubeId?.takeIf { it.isNotBlank() } ?: "${song.title.lowercase()}|${song.artist.lowercase()}"
        }

        val dislikedYtIds = runCatching { musicRepository.getDislikedYoutubeIds() }.getOrNull() ?: emptySet()
        val notInterestedIds = runCatching { userPreferencesRepository.notInterestedSongIdsFlow.first() }.getOrNull() ?: emptySet()
        val notDisliked = deduplicated.filter { song ->
            val yId = song.youtubeId ?: if (song.contentUriString.startsWith("youtube://")) {
                song.contentUriString.removePrefix("youtube://")
            } else if (song.id.startsWith("youtube_")) {
                song.id.removePrefix("youtube_")
            } else null
            (yId == null || !dislikedYtIds.contains(yId)) &&
            song.id !in notInterestedIds &&
            (yId == null || !notInterestedIds.contains(yId))
        }

        // Shuffle completely to make it dynamic on every view/refresh
        notDisliked.shuffled().take(20)
    }

    private suspend fun loadAllOfflineAndDownloadedSongs(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val localSongs = musicRepository.getAllSongsOnce()
            val downloadedYtSongs = com.unshoo.pixelmusic.data.database.youtube.AppDatabase
                .getInstance(context)
                .songRepository()
                .getDownloadedSongs()
                .map { it.toNativeSong() }

            (localSongs + downloadedYtSongs)
                .distinctBy { song ->
                    song.youtubeId?.takeIf { it.isNotBlank() } ?: song.id
                }
                .shuffled()
        } catch (e: Exception) {
            Timber.tag("QuickPicks").e(e, "Failed to load offline/downloaded songs")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Category-specific fetch (genre chips from YouTube home)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadCategoryQuickPicks(category: String) {
        if (category.equals("Local", ignoreCase = true)) {
            val localSongs = withContext(Dispatchers.IO) {
                try {
                    val topEngagements = engagementDao.getTopPlayedSongs(30)
                    val historySongs = playbackStatsRepository.loadPlaybackHistory(limit = 50)
                        .mapNotNull { historyEntry ->
                            historyEntry.songId.takeIf { !it.startsWith("youtube:") && it.length != 11 }
                        }
                    
                    val preferredLocalIds = (topEngagements.map { it.songId } + historySongs).distinct()
                    
                    val preferredLocalSongs = if (preferredLocalIds.isNotEmpty()) {
                        musicRepository.getSongsByIdsOnce(preferredLocalIds)
                    } else emptyList()

                    val (favoriteLocal, otherLocal) = musicRepository.getAllSongsOnce()
                        .partition { it.isFavorite }

                    val pool = (preferredLocalSongs + favoriteLocal.shuffled() + otherLocal.shuffled()).distinctBy { it.id }
                    pool.shuffled().take(25)
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").e(e, "Local category fetch failed")
                    emptyList()
                }
            }
            if (localSongs.isNotEmpty()) {
                _quickPicks.value = localSongs.toImmutableList()
            } else {
                _quickPicks.value = loadAllOfflineAndDownloadedSongs().take(25).toImmutableList()
            }
            return
        }

        val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()
        val songs = withContext(Dispatchers.IO) {
            try {
                val defaultHome = YouTube.home().getOrNull() ?: return@withContext emptyList<Song>()
                val matchingChip = defaultHome.chips?.firstOrNull {
                    it.title.equals(category, ignoreCase = true)
                }
                val targetHome = if (matchingChip?.endpoint?.params != null) {
                    YouTube.home(params = matchingChip.endpoint.params).getOrNull() ?: defaultHome
                } else defaultHome

                targetHome.sections
                    .filter {
                        !it.title.contains("listen again", ignoreCase = true) &&
                        !it.title.contains("recently played", ignoreCase = true)
                    }
                    .flatMap { it.items.filterIsInstance<SongItem>() }
                    .filterVideo(pureYtMusicOnly)
                    .distinctBy { it.id }
                    .take(25)
                    .map { it.toNativeSong() }
                    .shuffled()
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Category fetch failed: $category")
                emptyList()
            }
        }
        if (songs.isNotEmpty()) {
            val dislikedYtIds = runCatching { musicRepository.getDislikedYoutubeIds() }.getOrNull() ?: emptySet()
            val notInterestedIds = runCatching { userPreferencesRepository.notInterestedSongIdsFlow.first() }.getOrNull() ?: emptySet()
            val filtered = songs.filter { song ->
                val yId = song.youtubeId ?: if (song.contentUriString.startsWith("youtube://")) {
                    song.contentUriString.removePrefix("youtube://")
                } else if (song.id.startsWith("youtube_")) {
                    song.id.removePrefix("youtube_")
                } else null
                (yId == null || !dislikedYtIds.contains(yId)) &&
                song.id !in notInterestedIds &&
                (yId == null || !notInterestedIds.contains(yId))
            }
            _quickPicks.value = filtered.toImmutableList()
        } else {
            // Offline fallback for categories if net is unreachable
            _quickPicks.value = loadAllOfflineAndDownloadedSongs().take(25).toImmutableList()
        }
    }
}
