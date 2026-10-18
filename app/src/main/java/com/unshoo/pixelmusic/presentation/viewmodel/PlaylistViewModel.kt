package com.unshoo.pixelmusic.presentation.viewmodel

import android.net.Uri
import android.util.Log
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SmartPlaylistRule
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.getDeduplicationKey
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.playlist.M3uManager
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import com.unshoo.pixelmusic.data.playlist.SpotifyPlaylistParser
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.remote.youtube.toYoutubeSong
import com.unshoo.pixelmusic.data.remote.youtube.YouTubeItemFilter
import com.unshoo.pixelmusic.data.repository.YouTubeLibraryPersistenceManager
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.OutputStreamWriter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import coil.imageLoader
import com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode
import com.unshoo.pixelmusic.data.ai.AiPlaylistGenerator
import com.unshoo.pixelmusic.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.unshoo.pixelmusic.data.database.MusicDao
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.unshoo.pixelmusic.data.remote.youtube.DownloadHelper
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.serializeArtistRefs
import com.unshoo.pixelmusic.data.model.ArtistRef
import kotlin.math.absoluteValue

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val showTelegramCloudPlaylists: Boolean = true,
    val showSmartMixPlaylists: Boolean = true,
    val telegramTopicDisplayMode: TelegramTopicDisplayMode = TelegramTopicDisplayMode.CHANNELS_AND_TOPICS,
    val currentPlaylistSongs: List<Song> = emptyList(),
    val currentPlaylistDetails: Playlist? = null,
    val isLoading: Boolean = false,
    val playlistNotFound: Boolean = false,

    //Sort option
    val currentPlaylistSortOption: SortOption = SortOption.PlaylistNameAZ,
    val currentPlaylistSongsSortOption: SortOption = SortOption.SongTitleAZ,
    val playlistSongsOrderMode: PlaylistSongsOrderMode = PlaylistSongsOrderMode.Sorted(SortOption.SongTitleAZ),
    val playlistOrderModes: Map<String, PlaylistSongsOrderMode> = emptyMap(),

    // AI Generation State
    val isAiGenerating: Boolean = false,
    val aiGenerationError: String? = null
)

sealed class PlaylistSongsOrderMode {
    object Manual : PlaylistSongsOrderMode()
    data class Sorted(val option: SortOption) : PlaylistSongsOrderMode()
}

data class ImportProgressState(
    val isImporting: Boolean = false,
    val playlistName: String = "",
    val totalTracks: Int = 0,
    val currentTrackIndex: Int = 0,
    val currentTrackName: String = "",
    val currentTrackArtist: String = "",
    val importType: String = ""
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyMixManager: DailyMixManager,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    val m3uManager: M3uManager,
    private val musicDao: MusicDao,
    private val datastoreRepository: DatastoreRepository,
    private val persistenceManager: YouTubeLibraryPersistenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgressState())
    val importProgress: StateFlow<ImportProgressState> = _importProgress.asStateFlow()

    private val _syncingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val syncingPlaylists: StateFlow<Set<String>> = _syncingPlaylists.asStateFlow()

    private val _playlistSyncProgress = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val playlistSyncProgress: StateFlow<Map<String, Pair<Int, Int>>> = _playlistSyncProgress.asStateFlow()

    private var currentPlaylistSetVideoIds: List<String> = emptyList()
    private var loadPlaylistJob: kotlinx.coroutines.Job? = null

    val youtubeLoggedInFlow = datastoreRepository.cookies
        .map { it.toRawCookie().isNotEmpty() }
        .distinctUntilChanged()

    private val _playlistCreationEvent = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val playlistCreationEvent: SharedFlow<Boolean> = _playlistCreationEvent.asSharedFlow()

    companion object {
        const val FOLDER_PLAYLIST_PREFIX = "folder_playlist:"
        private const val MANUAL_ORDER_MODE = "manual"
        private const val SMART_PLAYLIST_MAX_ITEMS = 100
    }

    // Helper function to resolve stored playlist sort keys
    private fun resolvePlaylistSortOption(optionKey: String?): SortOption {
        return SortOption.fromStorageKey(
            optionKey,
            SortOption.PLAYLISTS,
            SortOption.PlaylistNameAZ
        )
    }

    init {
        loadPlaylistsAndInitialSortOption()
        observeTelegramCloudPlaylistVisibility()
        observeSmartMixPlaylistVisibility()
        observeTelegramTopicDisplayMode()
        observePlaylistOrderModes()
    }

    private fun observePlaylistOrderModes() {
        viewModelScope.launch {
            playlistPreferencesRepository.playlistSongOrderModesFlow.collect { storedModes ->
                val resolvedModes = storedModes.mapValues { (_, value) ->
                    decodeOrderMode(value)
                }
                _uiState.update { it.copy(playlistOrderModes = resolvedModes) }
            }
        }
    }

    private fun loadPlaylistsAndInitialSortOption() {
        viewModelScope.launch {
            // First, get the initial sort option
            val initialSortOptionName = playlistPreferencesRepository.playlistsSortOptionFlow.first()
            val initialSortOption = resolvePlaylistSortOption(initialSortOptionName)
            _uiState.update { it.copy(currentPlaylistSortOption = initialSortOption) }

            // Then, collect playlists and apply the sort option
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val currentSortOption =
                    _uiState.value.currentPlaylistSortOption // Use the most up-to-date sort option
                val sortedPlaylists = sortPlaylistsList(
                    filterSmartMixPlaylists(playlists, _uiState.value.showSmartMixPlaylists),
                    currentSortOption
                )
                _uiState.update { it.copy(playlists = sortedPlaylists) }
            }
        }
        // Collect subsequent changes to sort option from preferences
        viewModelScope.launch {
            playlistPreferencesRepository.playlistsSortOptionFlow.collect { optionName ->
                val newSortOption = resolvePlaylistSortOption(optionName)
                if (_uiState.value.currentPlaylistSortOption != newSortOption) {
                    // If the option from preferences is different, re-sort the current list
                    sortPlaylists(newSortOption)
                }
            }
        }
    }

    private fun observeTelegramCloudPlaylistVisibility() {
        viewModelScope.launch {
            playlistPreferencesRepository.showTelegramCloudPlaylistsFlow.collect { show ->
                _uiState.update { it.copy(showTelegramCloudPlaylists = show) }
            }
        }
    }

    private fun observeSmartMixPlaylistVisibility() {
        viewModelScope.launch {
            userPreferencesRepository.showSmartMixPlaylistsFlow.collect { show ->
                val currentSort = _uiState.value.currentPlaylistSortOption
                val all = playlistPreferencesRepository.userPlaylistsFlow.first()
                _uiState.update {
                    it.copy(
                        showSmartMixPlaylists = show,
                        playlists = sortPlaylistsList(filterSmartMixPlaylists(all, show), currentSort)
                    )
                }
            }
        }
    }

    private fun filterSmartMixPlaylists(playlists: List<Playlist>, show: Boolean): List<Playlist> {
        if (show) return playlists
        return playlists.filterNot { playlist ->
            playlist.source == "LASTFM_MIX" || playlist.source == "AI" || playlist.isAiGenerated
        }
    }

    private fun observeTelegramTopicDisplayMode() {
        viewModelScope.launch {
            playlistPreferencesRepository.telegramTopicDisplayModeFlow.collect { mode ->
                _uiState.update { it.copy(telegramTopicDisplayMode = mode) }
            }
        }
    }

    fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) { // Simplified
        _uiState.update { it.copy(telegramTopicDisplayMode = mode) }
        viewModelScope.launch {
            playlistPreferencesRepository.setTelegramTopicDisplayMode(mode)
        }
    }

    fun loadPlaylistDetails(playlistId: String) {
        loadPlaylistJob?.cancel()
        loadPlaylistJob = viewModelScope.launch(Dispatchers.IO) {
            val shouldKeepExisting = _uiState.value.currentPlaylistDetails?.id == playlistId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    playlistNotFound = false,
                    currentPlaylistDetails = if (shouldKeepExisting) it.currentPlaylistDetails else null,
                    currentPlaylistSongs = if (shouldKeepExisting) it.currentPlaylistSongs else emptyList()
                )
            } // Reset details and songs
            try {
                if (isFolderPlaylistId(playlistId)) {
                    val folderPath = Uri.decode(playlistId.removePrefix(FOLDER_PLAYLIST_PREFIX))
                    val folders = musicRepository.getMusicFolders().first()
                    val folder = findFolder(folderPath, folders)

                    if (folder != null) {
                        val songsList = withContext(Dispatchers.IO) {
                            val rawSongs = folder.collectAllSongs()
                            if (rawSongs.any { it.contentUriString.isBlank() }) {
                                musicRepository.getSongsByIdsOnce(rawSongs.map { it.id })
                            } else {
                                rawSongs
                            }
                        }
                        val pseudoPlaylist = Playlist(
                            id = playlistId,
                            name = folder.name,
                            songIds = songsList.map { it.id }
                        )

                        // PERF FIX: same Main-thread sort issue as the two spots above - a
                        // folder opened as a "playlist" can contain just as many songs as a
                        // real playlist, so this pays the same cost. Sort on Default first,
                        // then publish.
                        val sortedFolderSongs = withContext(Dispatchers.Default) {
                            applySortToSongs(songsList, _uiState.value.currentPlaylistSongsSortOption)
                        }

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = pseudoPlaylist,
                                currentPlaylistSongs = sortedFolderSongs,
                                playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(it.currentPlaylistSongsSortOption),
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                    } else {
                        Log.w("PlaylistVM", "Folder playlist with path $folderPath not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        }
                    }
                } else {
                    if (playlistId == "mixed_for_you_all") {
                        val cacheFile = File(context.cacheDir, "explore_cache.json")
                        val songsList = mutableListOf<Song>()
                        var playlistName = "Mixed For You"
                        var coverUri: String? = null
                        
                        if (cacheFile.exists()) {
                            try {
                                val json = cacheFile.readText()
                                val gson = com.google.gson.GsonBuilder()
                                    .registerTypeAdapter(unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem::class.java, PlaylistYTItemTypeAdapter())
                                    .create()
                                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                                val sections = cache.sections.filter { section ->
                                    val title = section.title.lowercase()
                                    val isSug = title.contains("mix") || title.contains("listen again") || 
                                                title.contains("favorites") || title.contains("suggest") || 
                                                title.contains("recommend") || title.contains("radio") || 
                                                title.contains("similar") || title.contains("played")
                                    val hasSongs = section.items.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>().isNotEmpty()
                                    isSug && hasSongs
                                }
                                sections.forEach { section ->
                                    val sectionSongs = section.items.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>().map { it.toNativeSong() }
                                    songsList.addAll(sectionSongs)
                                    if (coverUri == null) {
                                        coverUri = section.thumbnail
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PlaylistVM", "Failed to parse explore cache for Mixed For You", e)
                            }
                        }

                        val pseudoPlaylist = Playlist(
                            id = playlistId,
                            name = playlistName,
                            songIds = songsList.map { it.id },
                            coverImageUri = coverUri,
                            source = "YOUTUBE"
                        )

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = pseudoPlaylist,
                                currentPlaylistSongs = songsList.distinctBy { it.id },
                                playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                        return@launch
                    }

                    val cleanPlaylistId = playlistId.removePrefix("VL")
                    val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                        .find { it.id == playlistId || it.id == cleanPlaylistId || "VL${it.id}" == playlistId }

                    if (playlist != null) {
                        val orderMode = _uiState.value.playlistOrderModes[playlistId]
                            ?: PlaylistSongsOrderMode.Manual

                        val isYt = playlist.source.equals("YOUTUBE", ignoreCase = true)
                        val effectivePlaylist = if (isYt && playlist.songIds.isEmpty()) {
                            withContext(Dispatchers.IO) {
                                val ytDb = com.unshoo.pixelmusic.data.database.youtube.AppDatabase
                                    .getInstance(context)
                                    .playlistRepository()
                                val ytPlaylist = ytDb.getPlaylistById(cleanPlaylistId)
                                    ?: ytDb.getPlaylistById("VL$cleanPlaylistId")
                                ytPlaylist?.takeIf { it.songs.isNotEmpty() }?.let { hydrated ->
                                    playlist.copy(
                                        source = "YOUTUBE",
                                        songIds = hydrated.songs.map { "youtube_${it.youtubeId}" },
                                        displaySongCount = hydrated.info.lastSyncSongCount.takeIf { count -> count > 0 }
                                            ?: hydrated.songs.size
                                    )
                                }
                            } ?: playlist.copy(source = "YOUTUBE")
                        } else {
                            if (isYt) playlist.copy(source = "YOUTUBE") else playlist
                        }

                        val songsList: List<Song> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            if (playlistId == com.unshoo.pixelmusic.data.remote.youtube.Constants.Downloads.DOWNLOADED_PLAYLIST_ID) {
                                val downloaded = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).songRepository().getDownloadedSongs()
                                downloaded.map { ySong ->
                                    val primaryArtistId = toUnifiedYoutubeArtistId(ySong.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist")
                                    val songAlbum = ySong.album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
                                    Song(
                                        id = "youtube_${ySong.youtubeId}",
                                        title = ySong.title,
                                        artist = ySong.artist,
                                        artistId = primaryArtistId,
                                        artists = listOf(
                                            com.unshoo.pixelmusic.data.model.ArtistRef(
                                                id = primaryArtistId,
                                                name = ySong.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                                                isPrimary = true
                                            )
                                        ),
                                        album = songAlbum,
                                        albumId = toUnifiedYoutubeAlbumId(songAlbum),
                                        albumArtist = null,
                                        path = ySong.audioFilePath ?: "",
                                        contentUriString = "youtube://${ySong.youtubeId}",
                                        albumArtUriString = ySong.thumbnailPath ?: ySong.thumbnailHref,
                                        duration = parseDurationMillis(ySong.duration),
                                        genre = ySong.genre ?: "YouTube Music",
                                        lyrics = null,
                                        isFavorite = false,
                                        trackNumber = 0,
                                        discNumber = null,
                                        year = 0,
                                        dateAdded = ySong.downloadTimestamp,
                                        dateModified = 0,
                                        mimeType = "audio/opus",
                                        bitrate = null,
                                        sampleRate = null,
                                        youtubeId = ySong.youtubeId,
                                        albumBrowseId = ySong.albumBrowseId
                                    )
                                }.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it) }
                            } else {
                                var songs = musicRepository.getSongsByIdsOnce(effectivePlaylist.songIds)
                                    .distinctBy { it.getDeduplicationKey() }

                                // BUG FIX: If this is a YouTube playlist and songs count is less than expected,
                                // load cached songs from AppDatabase's playlist repository so songs are never wiped out!
                                if (effectivePlaylist.source.equals("YOUTUBE", ignoreCase = true) && (songs.isEmpty() || songs.size < effectivePlaylist.songIds.size)) {
                                    val cleanId = playlistId.removePrefix("VL")
                                    val cachedYtPlaylist = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                                        .playlistRepository()
                                        .getPlaylistById(cleanId)
                                        ?: com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                                            .playlistRepository()
                                            .getPlaylistById(playlistId)

                                    if (cachedYtPlaylist != null && cachedYtPlaylist.songs.isNotEmpty() && cachedYtPlaylist.songs.size >= songs.size) {
                                        val nativeYtSongs = cachedYtPlaylist.songs.map { it.toNativeSong() }
                                        musicRepository.insertYoutubeSongs(nativeYtSongs)
                                        songs = nativeYtSongs
                                    }
                                }
                                songs
                            }
                        }

                        // PERF FIX: applySortToSongs was running on the Main dispatcher here
                        // (the withContext(Dispatchers.IO) above only covered the DB fetch) -
                        // for a playlist with thousands of songs, sorting on Main is exactly the
                        // kind of jank a first open of a big playlist would show. Same fix as
                        // sortPlaylistSongs(): do the comparator sort on Dispatchers.Default.
                        val orderedSongs = when (orderMode) {
                            is PlaylistSongsOrderMode.Sorted -> withContext(Dispatchers.Default) {
                                applySortToSongs(songsList, orderMode.option)
                            }
                            PlaylistSongsOrderMode.Manual -> songsList
                        }

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = effectivePlaylist,
                                currentPlaylistSongs = orderedSongs,
                                currentPlaylistSongsSortOption = (orderMode as? PlaylistSongsOrderMode.Sorted)?.option
                                    ?: it.currentPlaylistSongsSortOption,
                                playlistSongsOrderMode = orderMode,
                                playlistOrderModes = it.playlistOrderModes + (playlistId to orderMode),
                                isLoading = songsList.isEmpty() && playlist.source == "YOUTUBE",
                                playlistNotFound = false
                            )
                        }

                        if (playlistId == com.unshoo.pixelmusic.data.remote.youtube.Constants.Downloads.DOWNLOADED_PLAYLIST_ID) {
                            viewModelScope.launch(Dispatchers.IO) {
                                if (isNetworkAvailable(context)) {
                                    val ytSongRepo = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).songRepository()
                                    var anyDownloaded = false
                                    songsList.forEach { song ->
                                        val youtubeId = song.youtubeId ?: song.id.removePrefix("youtube_")
                                        if (song.id.startsWith("youtube_") || song.youtubeId != null) {
                                            val ytSong = ytSongRepo.getSong(youtubeId)
                                            if (ytSong != null && (ytSong.thumbnailPath.isNullOrBlank() || ytSong.thumbnailPath.startsWith("http"))) {
                                                val downloadedImage = DownloadHelper.downloadImage(context, ytSong.thumbnailHref, ytSong.youtubeId)
                                                if (downloadedImage != null) {
                                                    anyDownloaded = true
                                                    val updatedYtSong = ytSong.copy(thumbnailPath = downloadedImage.path)
                                                    ytSongRepo.create(updatedYtSong)
                                                    song.id.toLongOrNull()?.let { numericId ->
                                                        musicDao.updateSongArtwork(numericId, downloadedImage.path)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (anyDownloaded) {
                                        loadPlaylistDetails(playlistId)
                                    }
                                }
                            }
                        }

                        // Background fetch & sync for synced YouTube playlists
                        if (playlist.source.equals("YOUTUBE", ignoreCase = true)) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val syncEnabled = userPreferencesRepository.youtubeSyncPlaylistsAndLikesFlow.first()
                                if (!syncEnabled) {
                                    withContext(Dispatchers.Main) {
                                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                            _uiState.update { it.copy(isLoading = false) }
                                        }
                                    }
                                    return@launch
                                }
                                if (YouTubeItemFilter.isPodcastOrEpisode(playlist.name, playlistId)) {
                                    withContext(Dispatchers.Main) {
                                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                            _uiState.update { it.copy(isLoading = false) }
                                        }
                                    }
                                    return@launch
                                }

                                // Retry up to 3 times for mobile data reliability
                                var ytPlaylistResult = YouTube.playlist(playlistId)
                                var fetchAttempt = 0
                                while (ytPlaylistResult.isFailure && fetchAttempt < 2) {
                                    fetchAttempt++
                                    kotlinx.coroutines.delay(1000L * fetchAttempt)
                                    ytPlaylistResult = YouTube.playlist(playlistId)
                                }

                                if (ytPlaylistResult.isSuccess) {
                                    val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                                    val ytPlaylist = ytPlaylistPage.playlist

                                    val reportedCount = ytPlaylist.songCountText
                                        ?.split(" ")?.firstOrNull()
                                        ?.filter { it.isDigit() }?.toIntOrNull()
                                        ?: ytPlaylistPage.songs.size

                                    val countMatches = songsList.isNotEmpty() && reportedCount > 0 && reportedCount == songsList.size
                                    val page1Songs = ytPlaylistPage.songs
                                    val page1Matches = countMatches && page1Songs.isNotEmpty() &&
                                        page1Songs.indices.all { i ->
                                            i < songsList.size && (
                                                page1Songs[i].id == songsList[i].youtubeId ||
                                                "youtube_${page1Songs[i].id}" == songsList[i].id
                                            )
                                        }

                                    if (page1Matches) {
                                        Log.d("PlaylistViewModel", "YouTube playlist '$playlistId' unchanged (signature & count match). Skipping paging.")
                                        withContext(Dispatchers.Main) {
                                            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                                _uiState.update { it.copy(isLoading = false) }
                                            }
                                        }
                                        return@launch
                                    }

                                    val allYtSongs = ytPlaylistPage.songs.toMutableList()

                                    var continuation = ytPlaylistPage.songsContinuation ?: ytPlaylistPage.continuation
                                    var pages = 0
                                    while (continuation != null && pages < 20) {
                                        var contResult = YouTube.playlistContinuation(continuation)
                                        if (contResult.isFailure) {
                                            kotlinx.coroutines.delay(1000L)
                                            contResult = YouTube.playlistContinuation(continuation)
                                        }
                                        if (contResult.isSuccess) {
                                            val contPage = contResult.getOrThrow()
                                            allYtSongs.addAll(contPage.songs)
                                            continuation = contPage.continuation
                                            pages++
                                        } else {
                                            break
                                        }
                                    }

                                    // Atomic update via centralized persistence manager
                                    val highQualityCover = com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality(
                                        ytPlaylist.thumbnail
                                    ) ?: ytPlaylist.thumbnail ?: ""

                                    val info = PlaylistInfo(
                                        id = playlistId,
                                        title = ytPlaylist.title,
                                        coverHref = highQualityCover,
                                        lastSyncSongCount = allYtSongs.size,
                                        lastSyncTimestamp = System.currentTimeMillis()
                                    )

                                    persistenceManager.persistPlaylist(info, allYtSongs.map { it.toYoutubeSong() })

                                    val allNativeSongs = allYtSongs.map { it.toNativeSong() }
                                    val allSongIds = allNativeSongs.map { it.id }
                                    currentPlaylistSetVideoIds = allYtSongs.mapNotNull { it.setVideoId }

                                    // Single UI update with the complete songs list
                                    withContext(Dispatchers.Main) {
                                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                            _uiState.update { state ->
                                                val songsChanged = state.currentPlaylistSongs != allNativeSongs
                                                state.copy(
                                                    currentPlaylistDetails = state.currentPlaylistDetails?.copy(
                                                        name = ytPlaylist.title,
                                                        songIds = allSongIds,
                                                        coverImageUri = highQualityCover
                                                    ),
                                                    currentPlaylistSongs = if (songsChanged) allNativeSongs else state.currentPlaylistSongs,
                                                    isLoading = false
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                            // Keep cached songs visible, just stop the loading indicator
                                            _uiState.update { it.copy(isLoading = false) }
                                        }
                                    }
                                    Log.e("PlaylistVM", "YouTube fetch failed for $playlistId after retries: ${ytPlaylistResult.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    } else if (playlistId.startsWith("PL") || playlistId.startsWith("VL") || playlistId.toLongOrNull() == null) {
                        val cleanId = playlistId.removePrefix("VL")
                        val ytDb = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).playlistRepository()
                        val cachedYtPlaylist = ytDb.getPlaylistById(cleanId)
                            ?: ytDb.getPlaylistById("VL$cleanId")

                        var initialDisplaySongs: List<Song> = emptyList()
                        if (cachedYtPlaylist != null && cachedYtPlaylist.songs.isNotEmpty()) {
                            val nativeCachedSongs = cachedYtPlaylist.songs.map { it.toNativeSong() }
                            initialDisplaySongs = nativeCachedSongs
                            musicRepository.insertYoutubeSongs(nativeCachedSongs)
                            val cachedModel = Playlist(
                                id = playlistId,
                                name = cachedYtPlaylist.info.title,
                                songIds = nativeCachedSongs.map { it.id },
                                coverImageUri = cachedYtPlaylist.info.coverHref,
                                source = "YOUTUBE",
                                displaySongCount = cachedYtPlaylist.info.lastSyncSongCount.takeIf { it > 0 } ?: nativeCachedSongs.size
                            )
                            _uiState.update {
                                it.copy(
                                    currentPlaylistDetails = cachedModel,
                                    currentPlaylistSongs = nativeCachedSongs,
                                    playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                                    isLoading = false,
                                    playlistNotFound = false
                                )
                            }
                        }

                        val ytPlaylistResult = withContext(Dispatchers.IO) {
                            YouTube.playlist(playlistId)
                        }
                        if (ytPlaylistResult.isSuccess) {
                            val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                            val ytPlaylist = ytPlaylistPage.playlist
                            
                            val firstPageSongs = ytPlaylistPage.songs.map { it.toNativeSong() }
                            
                            // Cache first page online playlist songs in Room DB
                            musicRepository.insertYoutubeSongs(firstPageSongs)

                            val existing = playlistPreferencesRepository.userPlaylistsFlow.first().find {
                                it.id.removePrefix("VL") == cleanId
                            }
                            if (existing != null) {
                                val updatedSongIds = if (existing.songIds.size > firstPageSongs.size) {
                                    existing.songIds
                                } else {
                                    firstPageSongs.map { it.id }
                                }
                                playlistPreferencesRepository.updatePlaylist(
                                    existing.copy(
                                        name = ytPlaylist.title,
                                        songIds = updatedSongIds,
                                        coverImageUri = ytPlaylist.thumbnail
                                    )
                                )
                            }

                            currentPlaylistSetVideoIds = ytPlaylistPage.songs.mapNotNull { it.setVideoId }

                            val cachedSongs = if (existing != null && existing.songIds.size > firstPageSongs.size) {
                                musicRepository.getSongsByIdsOnce(existing.songIds)
                            } else initialDisplaySongs

                            val displaySongs = if (cachedSongs.isNotEmpty()) {
                                cachedSongs
                            } else {
                                firstPageSongs
                            }

                            val playlistModel = Playlist(
                                id = playlistId,
                                name = ytPlaylist.title,
                                songIds = displaySongs.map { s -> s.id },
                                coverImageUri = ytPlaylist.thumbnail,
                                source = "YOUTUBE"
                            )

                            _uiState.update {
                                it.copy(
                                    currentPlaylistDetails = playlistModel,
                                    currentPlaylistSongs = displaySongs,
                                    playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                                    isLoading = false,
                                    playlistNotFound = false
                                )
                            }

                            // Fetch the remaining pages progressively in the background coroutine
                            val allYtSongs = ytPlaylistPage.songs.toMutableList()
                            var continuation = ytPlaylistPage.songsContinuation ?: ytPlaylistPage.continuation
                            var pages = 0
                            while (continuation != null && pages < 10) {
                                val contResult = YouTube.playlistContinuation(continuation)
                                if (contResult.isSuccess) {
                                    val contPage = contResult.getOrThrow()
                                    allYtSongs.addAll(contPage.songs)
                                    continuation = contPage.continuation
                                    pages++

                                    val currentNativeSongs = allYtSongs.map { it.toNativeSong() }
                                    musicRepository.insertYoutubeSongs(contPage.songs.map { it.toNativeSong() })
                                    
                                    // Update video ids
                                    currentPlaylistSetVideoIds = allYtSongs.mapNotNull { it.setVideoId }

                                    // Only update preferences if the playlist exists locally
                                    val currentExisting = playlistPreferencesRepository.userPlaylistsFlow.first().find {
                                        it.id.removePrefix("VL") == cleanId
                                    }
                                    if (currentExisting != null) {
                                        playlistPreferencesRepository.updatePlaylist(
                                            currentExisting.copy(
                                                songIds = currentNativeSongs.map { it.id }
                                            )
                                        )
                                    }

                                    val updatedPlaylistModel = Playlist(
                                        id = playlistId,
                                        name = ytPlaylist.title,
                                        songIds = currentNativeSongs.map { it.id },
                                        coverImageUri = ytPlaylist.thumbnail,
                                        source = "YOUTUBE"
                                    )

                                    withContext(Dispatchers.Main) {
                                        _uiState.update { state ->
                                            if (state.currentPlaylistDetails?.id == playlistId) {
                                                state.copy(
                                                    currentPlaylistDetails = updatedPlaylistModel,
                                                    currentPlaylistSongs = currentNativeSongs
                                                )
                                            } else state
                                        }
                                    }
                                } else {
                                    break
                                }
                            }

                            // Songs are already inserted to MusicRepository above (line 688/749).
                            // Do NOT persist to Room's PlaylistInfo table here — that table feeds
                            // userPlaylistsFlow and would cause the playlist to appear "liked".
                        } else {
                            if (initialDisplaySongs.isEmpty()) {
                                _uiState.update { it.copy(isLoading = false, playlistNotFound = true) }
                            } else {
                                _uiState.update { it.copy(isLoading = false) }
                            }
                        }
                    } else {
                        Log.w("PlaylistVM", "Playlist with id $playlistId not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        } // Mantener isLoading en false
                        // Opcional: podrías establecer un error o un estado específico de "no encontrado"
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Error loading playlist details for id $playlistId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlistNotFound = true,
                        currentPlaylistDetails = null,
                        currentPlaylistSongs = emptyList()
                    )
                }
            }
        }
    }

    fun importFromSpotifyUrl(url: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val parseResult = SpotifyPlaylistParser.parse(url)
                if (parseResult.isFailure) {
                    onComplete(Result.failure(parseResult.exceptionOrNull() ?: Exception("Failed to parse Spotify playlist")))
                    return@launch
                }
                val parsed = parseResult.getOrThrow()
                if (parsed.tracks.isEmpty()) {
                    onComplete(Result.failure(Exception("No tracks found in playlist")))
                    return@launch
                }

                // 1. Immediately create local playlist
                val customId = "spotify_${parsed.id}"
                val newPlaylist = playlistPreferencesRepository.createPlaylist(
                    name = parsed.title,
                    songIds = emptyList(),
                    coverImageUri = parsed.coverUrl,
                    customId = customId,
                    source = "SPOTIFY"
                )

                val totalTracks = parsed.tracks.size
                _playlistSyncProgress.update { it + (newPlaylist.id to Pair(0, totalTracks)) }
                _syncingPlaylists.update { it + newPlaylist.id }

                onComplete(Result.success(newPlaylist.id))

                // 2. Map Spotify tracks to YouTube Music IDs sequentially in background
                viewModelScope.launch(Dispatchers.IO) {
                    var currentCoverPath: String? = parsed.coverUrl

                    // Download Spotify playlist cover art to internal storage
                    if (!parsed.coverUrl.isNullOrBlank()) {
                        try {
                            val localCover = saveCoverImageToInternalStorage(
                                uri = Uri.parse(parsed.coverUrl),
                                uniqueId = newPlaylist.id,
                                cropScale = 1f,
                                cropPanX = 0f,
                                cropPanY = 0f
                            )
                            if (!localCover.isNullOrBlank()) {
                                currentCoverPath = localCover
                                playlistPreferencesRepository.updatePlaylist(
                                    newPlaylist.copy(coverImageUri = localCover)
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to download Spotify playlist cover to local storage")
                        }
                    }

                    val resolvedSongIds = mutableListOf<String>()

                    parsed.tracks.forEachIndexed { index, track ->
                        try {
                            val query = "${track.title} ${track.artist}"
                            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                            val matchedItem = searchResult.getOrNull()?.items
                                ?.filterIsInstance<SongItem>()
                                ?.minByOrNull { item ->
                                    val durDiff = item.duration?.let { durSec ->
                                        kotlin.math.abs((durSec * 1000L) - track.durationMs)
                                    } ?: Long.MAX_VALUE
                                    durDiff
                                } ?: searchResult.getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()

                            val songToSave: Song = if (matchedItem != null) {
                                matchedItem.toNativeSong().copy(
                                    path = track.spotifyUrl.ifBlank { matchedItem.toNativeSong().path }
                                )
                            } else {
                                Song(
                                    id = "spotify_${parsed.id}_$index",
                                    title = track.title,
                                    artist = track.artist,
                                    artistId = 0L,
                                    artists = emptyList(),
                                    album = track.album.ifBlank { parsed.title },
                                    albumId = 0L,
                                    albumArtist = track.artist,
                                    path = track.spotifyUrl,
                                    contentUriString = track.spotifyUrl,
                                    albumArtUriString = track.coverUrl ?: currentCoverPath,
                                    duration = track.durationMs,
                                    genre = "Spotify",
                                    lyrics = null,
                                    isFavorite = false,
                                    trackNumber = index + 1,
                                    discNumber = null,
                                    year = 0,
                                    dateAdded = System.currentTimeMillis(),
                                    dateModified = System.currentTimeMillis(),
                                    mimeType = "audio/opus",
                                    bitrate = 128000,
                                    sampleRate = 44100,
                                    youtubeId = null,
                                    albumBrowseId = null
                                )
                            }

                            if (songToSave.youtubeId != null) {
                                musicRepository.insertYoutubeSongs(listOf(songToSave))
                            }
                            resolvedSongIds.add(songToSave.id)

                            // Fallback: If playlist still lacks a cover, use first matched song's cover
                            if (currentCoverPath.isNullOrBlank() && !songToSave.albumArtUriString.isNullOrBlank()) {
                                currentCoverPath = songToSave.albumArtUriString
                            }

                            // Periodically update playlist entry in Room DB every 5 tracks or on last track
                            if ((index + 1) % 5 == 0 || index == parsed.tracks.lastIndex) {
                                playlistPreferencesRepository.updatePlaylist(
                                    newPlaylist.copy(
                                        coverImageUri = currentCoverPath,
                                        songIds = resolvedSongIds.toList()
                                    )
                                )
                            }

                            _playlistSyncProgress.update { it + (newPlaylist.id to Pair(index + 1, totalTracks)) }

                            // Smooth 150ms delay between search requests
                            kotlinx.coroutines.delay(150L)
                        } catch (e: Exception) {
                            Timber.w(e, "Error resolving Spotify track: ${track.title}")
                        }
                    }

                    // Final update
                    playlistPreferencesRepository.updatePlaylist(
                        newPlaylist.copy(
                            coverImageUri = currentCoverPath,
                            songIds = resolvedSongIds.toList()
                        )
                    )
                    _playlistSyncProgress.update { it - newPlaylist.id }
                    _syncingPlaylists.update { it - newPlaylist.id }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importing Spotify playlist")
                onComplete(Result.failure(e))
            }
        }
    }

    fun importFromYouTubeUrl(url: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanId = when {
                    url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
                    url.contains("playlist?list=") -> url.substringAfter("playlist?list=").substringBefore("&")
                    else -> url.trim().removePrefix("VL")
                }

                if (cleanId.isBlank()) {
                    onComplete(Result.failure(IllegalArgumentException("Invalid YouTube playlist URL or ID")))
                    return@launch
                }

                val ytResult = withContext(Dispatchers.IO) {
                    YouTube.playlist(cleanId)
                }

                if (ytResult.isFailure) {
                    onComplete(Result.failure(ytResult.exceptionOrNull() ?: Exception("Failed to fetch YouTube playlist")))
                    return@launch
                }

                val page = ytResult.getOrThrow()
                val ytPlaylist = page.playlist
                val initialSongs = page.songs.toMutableList()
                var continuation = page.songsContinuation ?: page.continuation

                val nativeInitial = initialSongs.map { it.toNativeSong() }
                withContext(Dispatchers.IO) {
                    musicRepository.insertYoutubeSongs(nativeInitial)
                }

                val customId = "yt_import_$cleanId"
                val newPlaylist = playlistPreferencesRepository.createPlaylist(
                    name = ytPlaylist.title,
                    songIds = nativeInitial.map { it.id },
                    coverImageUri = ytPlaylist.thumbnail,
                    customId = customId,
                    source = "YOUTUBE"
                )

                val totalCount = ytPlaylist.songCountText
                    ?.split(" ")?.firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: initialSongs.size

                _playlistSyncProgress.update { it + (newPlaylist.id to Pair(nativeInitial.size, totalCount)) }
                _syncingPlaylists.update { it + newPlaylist.id }
                onComplete(Result.success(newPlaylist.id))

                if (continuation != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val allSongsList = nativeInitial.toMutableList()
                        var currentContinuation: String? = continuation
                        var pages = 0
                        while (currentContinuation != null && pages < 25) {
                            val contResult = YouTube.playlistContinuation(currentContinuation)
                            if (contResult.isSuccess) {
                                val contPage = contResult.getOrThrow()
                                val newBatch = contPage.songs.map { it.toNativeSong() }
                                musicRepository.insertYoutubeSongs(newBatch)
                                allSongsList.addAll(newBatch)
                                currentContinuation = contPage.continuation
                                pages++
                                _playlistSyncProgress.update { it + (newPlaylist.id to Pair(allSongsList.size, totalCount)) }
                            } else {
                                break
                            }
                        }
                        playlistPreferencesRepository.updatePlaylist(
                            newPlaylist.copy(songIds = allSongsList.map { it.id })
                        )
                        _playlistSyncProgress.update { it - newPlaylist.id }
                        _syncingPlaylists.update { it - newPlaylist.id }
                    }
                } else {
                    _playlistSyncProgress.update { it - newPlaylist.id }
                    _syncingPlaylists.update { it - newPlaylist.id }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importing YouTube playlist")
                onComplete(Result.failure(e))
            }
        }
    }

    fun createPlaylist(
        name: String,
        coverImageUri: String? = null,
        coverColor: Int? = null,
        coverIcon: String? = null,
        songIds: List<String> = emptyList(), // Added songIds parameter
        songs: List<Song> = emptyList(), // Added songs parameter
        privacyStatus: String = "LOCAL", // Added privacyStatus parameter ("LOCAL", "PRIVATE", "UNLISTED", "PUBLIC")
        cropScale: Float = 1f,
        cropPanX: Float = 0f,
        cropPanY: Float = 0f,
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        source: String = "LOCAL", // Mark source
        smartRuleKey: String? = null
    ) {
        viewModelScope.launch {
            var savedCoverPath: String? = null

            if (coverImageUri != null) {
                // Generate a unique ID for the image file since we don't have the playlist ID yet
                val imageId = UUID.randomUUID().toString()
                savedCoverPath = saveCoverImageToInternalStorage(
                    Uri.parse(coverImageUri),
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
            }

            val resolvedSmartRule = SmartPlaylistRule.fromStorageKey(smartRuleKey)
            val resolvedSongIds = if (songs.isNotEmpty()) {
                ensureSongsPersisted(songs).distinct()
            } else if (resolvedSmartRule != null) {
                buildSmartPlaylistSongIds(
                    rule = resolvedSmartRule,
                    limit = SMART_PLAYLIST_MAX_ITEMS
                )
            } else {
                songIds.map { songId ->
                    if (songId.startsWith("youtube_")) {
                        toUnifiedYoutubeSongId(songId.removePrefix("youtube_")).toString()
                    } else {
                        songId
                    }
                }.distinct()
            }
            
            var finalSource = when {
                resolvedSmartRule != null && source == "LOCAL" -> "SMART"
                else -> source
            }
            var remotePlaylistId: String? = null

            if (privacyStatus != "LOCAL") {
                val settings = datastoreRepository.settings.first()
                if (!settings.cookies.isEmpty()) {
                    val youtubeVideoIds = if (songs.isNotEmpty()) {
                        songs.mapNotNull { it.youtubeId ?: if (it.id.startsWith("youtube_")) it.id.removePrefix("youtube_") else null }
                    } else {
                        val loadedSongs = musicRepository.getSongsByIdsOnce(songIds)
                        loadedSongs.mapNotNull { it.youtubeId ?: if (it.id.startsWith("youtube_")) it.id.removePrefix("youtube_") else null }
                    }
                    try {
                        val result = withContext(Dispatchers.IO) {
                            YouTube.createPlaylist(name)
                        }
                        if (result.isSuccess) {
                            val playlistId = result.getOrThrow()
                            remotePlaylistId = playlistId
                            finalSource = "YOUTUBE"
                            youtubeVideoIds.forEach { videoId ->
                                withContext(Dispatchers.IO) {
                                    YouTube.addToPlaylist(playlistId, videoId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaylistViewModel", "Failed to create remote YouTube playlist", e)
                    }
                }
            }

            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = resolvedSongIds,
                isAiGenerated = isAiGenerated,
                isQueueGenerated = isQueueGenerated,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4,
                customId = remotePlaylistId,
                source = finalSource
            )
            _playlistCreationEvent.emit(true)
        }
    }

    private suspend fun buildSmartPlaylistSongIds(
        rule: SmartPlaylistRule,
        limit: Int
    ): List<String> {
        val allSongs = musicRepository.getAllSongsOnce()
        if (allSongs.isEmpty()) return emptyList()

        val engagements = dailyMixManager.getAllEngagementStats()
        val now = System.currentTimeMillis()
        val songById = allSongs.associateBy { it.id }
        val favoriteIds = musicRepository.getFavoriteSongIdsOnce()
        val safeLimit = limit.coerceAtLeast(1).coerceAtMost(allSongs.size)

        val pickedSongs = when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> {
                engagements.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, DailyMixManager.SongEngagementStats>> { it.value.playCount }
                            .thenByDescending { it.value.totalPlayDurationMs }
                            .thenByDescending { it.value.lastPlayedTimestamp }
                    )
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }

            SmartPlaylistRule.RECENTLY_PLAYED -> {
                engagements.entries
                    .filter { it.value.lastPlayedTimestamp > 0L }
                    .sortedByDescending { it.value.lastPlayedTimestamp }
                    .mapNotNull { (songId, _) -> songById[songId] }
                    .take(safeLimit)
            }

            SmartPlaylistRule.FORGOTTEN_FAVORITES -> {
                val staleThreshold = now - TimeUnit.DAYS.toMillis(30)
                allSongs
                    .asSequence()
                    .filter { favoriteIds.contains(it.id) }
                    .sortedWith(
                        compareBy<Song> { engagements[it.id]?.lastPlayedTimestamp ?: 0L }
                            .thenBy { it.title.lowercase() }
                    )
                    .filter { song ->
                        (engagements[song.id]?.lastPlayedTimestamp ?: 0L) < staleThreshold
                    }
                    .take(safeLimit)
                    .toList()
            }

            SmartPlaylistRule.NEW_GEMS -> {
                allSongs
                    .asSequence()
                    .sortedWith(
                        compareByDescending<Song> { it.dateAdded }
                            .thenBy { engagements[it.id]?.playCount ?: 0 }
                    )
                    .filter { song -> (engagements[song.id]?.playCount ?: 0) <= 2 }
                    .take(safeLimit)
                    .toList()
            }
        }

        if (pickedSongs.isNotEmpty()) {
            return pickedSongs.map { it.id }.distinct()
        }

        return allSongs
            .sortedByDescending { it.dateAdded }
            .take(safeLimit)
            .map { it.id }
    }


    suspend fun saveCoverImageToInternalStorage(
        uri: Uri,
        uniqueId: String,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val originalBitmap: android.graphics.Bitmap? = when {
                    uri.scheme == "http" || uri.scheme == "https" -> {
                        val loader = context.imageLoader
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(request)
                        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }
                    uri.scheme == "file" || uri.scheme.isNullOrEmpty() -> {
                        val path = uri.path ?: uri.toString()
                        android.graphics.BitmapFactory.decodeFile(path)
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }
                if (originalBitmap == null) return@withContext null

                // Target dimensions (Square)
                val targetSize = 1024

                // create target bitmap
                val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(targetBitmap)

                // Calculate base dimensions (fitting smallest dimension to target)
                // Logic must match ImageCropView
                val bitmapWidth = originalBitmap.width.toFloat()
                val bitmapHeight = originalBitmap.height.toFloat()
                val bitmapRatio = bitmapWidth / bitmapHeight

                val (baseWidth, baseHeight) = if (bitmapRatio > 1f) {
                    // Wide: Height matches target
                    targetSize * bitmapRatio to targetSize.toFloat()
                } else {
                    // Tall: Width matches target
                    targetSize.toFloat() to targetSize / bitmapRatio
                }

                // Calculate transformations
                // Scaled Dimensions
                val scaledWidth = baseWidth * cropScale
                val scaledHeight = baseHeight * cropScale

                // Center + Pan
                // Center of target is targetSize/2
                // We want to center the Scaled Image at (Center + Pan)
                // TopLeft = CenterX - ScaledW/2 + PanX

                // Pan is normalized relative to Viewport (TargetSize)
                val panPxX = cropPanX * targetSize
                val panPxY = cropPanY * targetSize

                val dx = (targetSize - scaledWidth) / 2f + panPxX
                val dy = (targetSize - scaledHeight) / 2f + panPxY

                // Draw
                // We draw the original bitmap scaled to (scaledWidth, scaledHeight) at (dx, dy)
                val matrix = android.graphics.Matrix()
                matrix.postScale(scaledWidth / bitmapWidth, scaledHeight / bitmapHeight)
                matrix.postTranslate(dx, dy)

                canvas.drawBitmap(originalBitmap, matrix, null)

                // Save
                val fileName = "playlist_cover_$uniqueId.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                // Recycle
                if (originalBitmap != targetBitmap) originalBitmap.recycle()
                // Target bitmap is not recycled here, let GC handle?
                // Or recycle explicitly if immediate memory pressure concern.

                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun togglePinPlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistPreferencesRepository.togglePinPlaylist(playlistId)
        }
    }

    fun togglePlaylistLike(playlist: Playlist, songs: List<Song> = emptyList()) {
        viewModelScope.launch {
            val cleanId = playlist.id.removePrefix("VL")
            val isCurrentlyLiked = playlistPreferencesRepository.userPlaylistsFlow.first().any {
                it.id.removePrefix("VL") == cleanId
            }
            val newLikedState = !isCurrentlyLiked

            if (newLikedState) {
                val info = PlaylistInfo(
                    id = cleanId,
                    title = playlist.name,
                    coverHref = playlist.coverImageUri.orEmpty(),
                    coverPath = null,
                    lastSyncSongCount = songs.size,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                val ytSongs = songs.map { song ->
                    val yId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else song.id
                    val durationStr = if (song.duration > 0) com.unshoo.pixelmusic.utils.formatDuration(song.duration) else ""
                    com.unshoo.pixelmusic.data.model.youtube.Song(
                        youtubeId = yId,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        albumBrowseId = song.albumBrowseId,
                        duration = durationStr,
                        thumbnailHref = song.albumArtUriString ?: ""
                    )
                }
                persistenceManager.persistPlaylist(info, ytSongs)
                if (songs.isEmpty()) {
                    loadPlaylistDetails(playlist.id)
                }
            } else {
                persistenceManager.deletePlaylist(cleanId)
                persistenceManager.deletePlaylist(playlist.id)
            }

            if (YouTube.hasLoginCookie()) {
                withContext(Dispatchers.IO) {
                    YouTube.likePlaylist(if (playlist.id.startsWith("VL")) playlist.id else "VL$cleanId", newLikedState)
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            playlistPreferencesRepository.deletePlaylist(playlistId)
            if (playlist != null && playlist.source == "YOUTUBE") {
                try {
                    withContext(Dispatchers.IO) {
                        YouTube.deletePlaylist(playlist.id)
                    }
                } catch (e: Exception) {
                    Log.e("PlaylistViewModel", "Failed to delete remote YouTube playlist", e)
                }
            }
        }
    }

    fun setImportingState(isImporting: Boolean, name: String = "", isCsv: Boolean = false) {
        _importProgress.update {
            ImportProgressState(
                isImporting = isImporting,
                playlistName = name,
                importType = if (isCsv) "csv" else "m3u"
            )
        }
    }

    fun updateImportProgress(name: String, current: Int, total: Int, title: String, artist: String) {
        _importProgress.update {
            it.copy(
                playlistName = name,
                totalTracks = total,
                currentTrackIndex = current,
                currentTrackName = title,
                currentTrackArtist = artist
            )
        }
    }

    fun findPlaylistByName(name: String): Playlist? {
        return _uiState.value.playlists.find { it.name.equals(name, ignoreCase = true) }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            var initialName = "Importing Playlist..."
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        initialName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error resolving M3U display name", e)
            }
            _importProgress.update {
                ImportProgressState(isImporting = true, playlistName = initialName, importType = "m3u")
            }
            try {
                val (name, songIds) = m3uManager.parseM3u(uri) { current, total, title, artist ->
                    _importProgress.update {
                        it.copy(
                            playlistName = initialName,
                            totalTracks = total,
                            currentTrackIndex = current,
                            currentTrackName = title,
                            currentTrackArtist = artist
                        )
                    }
                }
                if (songIds.isNotEmpty()) {
                    playlistPreferencesRepository.createPlaylist(name, songIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error importing M3U", e)
            } finally {
                _importProgress.update { ImportProgressState(isImporting = false) }
            }
        }
    }

    fun exportM3u(playlist: Playlist, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIdsOnce(playlist.songIds)
                val m3uContent = m3uManager.generateM3u(playlist, songs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(m3uContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting M3U", e)
            }
        }
    }

    fun exportCsv(playlist: Playlist, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIdsOnce(playlist.songIds)
                val csvContent = m3uManager.generateCsv(songs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(csvContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting CSV", e)
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            var initialName = "Importing Playlist..."
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        initialName = cursor.getString(nameIndex).removeSuffix(".csv")
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error resolving CSV display name", e)
            }
            _importProgress.update {
                ImportProgressState(isImporting = true, playlistName = initialName, importType = "csv")
            }
            try {
                val (name, songIds) = m3uManager.parseCsv(uri) { current, total, title, artist ->
                    _importProgress.update {
                        it.copy(
                            playlistName = initialName,
                            totalTracks = total,
                            currentTrackIndex = current,
                            currentTrackName = title,
                            currentTrackArtist = artist
                        )
                    }
                }
                if (songIds.isNotEmpty()) {
                    playlistPreferencesRepository.createPlaylist(name, songIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error importing CSV", e)
            } finally {
                _importProgress.update { ImportProgressState(isImporting = false) }
            }
        }
    }

    /**
     * Shares a playlist as M3U or CSV via the Android share sheet.
     * The content is written to the app cache directory first, then shared.
     */
    fun sharePlaylist(playlist: Playlist, asCsv: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIdsOnce(playlist.songIds)
                val (fileContent, mimeType, extension) = if (asCsv) {
                    Triple(m3uManager.generateCsv(songs), "text/csv", "csv")
                } else {
                    Triple(m3uManager.generateM3u(playlist, songs), "audio/x-mpegurl", "m3u")
                }
                val safePlaylistName = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = File(context.cacheDir, "${safePlaylistName}.${extension}")
                file.writeText(fileContent)
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, playlist.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, playlist.name).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error sharing playlist", e)
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            playlistPreferencesRepository.renamePlaylist(playlistId, newName)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = it.currentPlaylistDetails?.copy(
                            name = newName
                        )
                    )
                }
            }
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            if (playlist != null && playlist.source == "YOUTUBE") {
                try {
                    withContext(Dispatchers.IO) {
                        YouTube.renamePlaylist(playlist.id, newName)
                    }
                } catch (e: Exception) {
                    Log.e("PlaylistViewModel", "Failed to rename remote YouTube playlist", e)
                }
            }
        }
    }

    fun updatePlaylistParameters(
        playlistId: String,
        name: String,
        coverImageUri: String?,
        coverColor: Int?,
        coverIcon: String?,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float,
        coverShapeType: String?,
        coverShapeDetail1: Float?,
        coverShapeDetail2: Float?,
        coverShapeDetail3: Float?,
        coverShapeDetail4: Float?
    ) {
        if (isFolderPlaylistId(playlistId)) return
        val currentPlaylist = _uiState.value.currentPlaylistDetails ?: return
        if (currentPlaylist.id != playlistId) return

        viewModelScope.launch {
            var savedCoverPath: String? = currentPlaylist.coverImageUri

            // If a new URI is provided and it's different from the existing one (and not null)
            // Or if we need to re-save because crop params changed?
            // For simplicity, if coverImageUri is passed and it's a content URI, we save it.
            // If it's the same string as savedCoverPath, we assume it's unchanged unless we want to force re-crop.
            // The UI will pass the Uri string. If it's a local file path, it's likely already saved.
            // But if the user selected a new image, it will be a content content:// uri.

            if (coverImageUri != null && coverImageUri != currentPlaylist.coverImageUri) {
                val imageId = UUID.randomUUID().toString()
                val parsedUri = Uri.parse(coverImageUri)
                val newPath = saveCoverImageToInternalStorage(
                    parsedUri,
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
                if (newPath != null) {
                    savedCoverPath = newPath
                }
            } else if (coverImageUri == null) {
                savedCoverPath = null
            } else {
                savedCoverPath = currentPlaylist.coverImageUri
            }


            val updatedPlaylist = currentPlaylist.copy(
                name = name,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )

            // Optimistic update
            _uiState.update {
                it.copy(currentPlaylistDetails = updatedPlaylist)
            }

            playlistPreferencesRepository.updatePlaylist(updatedPlaylist)

            if (currentPlaylist.source == "YOUTUBE" && currentPlaylist.name != name) {
                try {
                    withContext(Dispatchers.IO) {
                        YouTube.renamePlaylist(currentPlaylist.id, name)
                    }
                } catch (e: Exception) {
                    Log.e("PlaylistViewModel", "Failed to rename remote YouTube playlist in updatePlaylistParameters", e)
                }
            }
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            playlistPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId)
            }
            val cleanId = playlistId.removePrefix("VL")
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find {
                it.id == playlistId || it.id == cleanId || "VL${it.id}" == playlistId
            }
            if (playlist != null && playlist.source.equals("YOUTUBE", ignoreCase = true)) {
                val settings = datastoreRepository.settings.first()
                if (!settings.cookies.isEmpty()) {
                    val songs = musicRepository.getSongsByIdsOnce(songIdsToAdd)
                    val videoIds = songs.mapNotNull { it.youtubeId ?: if (it.id.startsWith("youtube_")) it.id.removePrefix("youtube_") else null }
                    if (videoIds.isNotEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                videoIds.forEach { videoId ->
                                    YouTube.addToPlaylist(cleanId, videoId)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PlaylistViewModel", "Failed to sync added songs to YouTube playlist", e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun ensureSongsPersisted(songs: List<Song>): List<String> {
        val mappedIds = mutableListOf<String>()
        val songsToInsert = mutableListOf<SongEntity>()
        val albumsToInsert = mutableListOf<AlbumEntity>()
        val artistsToInsert = mutableListOf<ArtistEntity>()
        val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()

        songs.forEach { song ->
            if (song.id.startsWith("youtube_") || song.youtubeId != null) {
                val yId = song.youtubeId ?: song.id.removePrefix("youtube_")
                val songId = toUnifiedYoutubeSongId(yId)
                mappedIds.add(songId.toString())

                // Check if already in DB
                val existing = musicDao.getSongByIdOnce(songId)
                if (existing != null) {
                    val newAlbumName = song.album.takeIf { it.isNotBlank() && it != "YouTube Music" }
                    if (newAlbumName != null && existing.albumName == "YouTube Music") {
                        val albumId = toUnifiedYoutubeAlbumId(newAlbumName)
                        val album = AlbumEntity(
                            id = albumId,
                            title = newAlbumName,
                            artistName = song.artist,
                            artistId = existing.artistId,
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtUriString = song.albumArtUriString ?: existing.albumArtUriString
                        )
                        musicDao.insertAlbumsIgnoreConflicts(listOf(album))
                        musicDao.updateSongAlbum(songId, newAlbumName, albumId, song.albumBrowseId)
                    }
                } else {
                    val albumName = song.album.takeIf { it.isNotBlank() } ?: "YouTube Music"
                    val albumId = toUnifiedYoutubeAlbumId(albumName)
                    val artistId = toUnifiedYoutubeArtistId(song.artist)

                    val artist = ArtistEntity(
                        id = artistId,
                        name = song.artist,
                        trackCount = 1,
                        imageUrl = null
                    )
                    artistsToInsert.add(artist)

                    val album = AlbumEntity(
                        id = albumId,
                        title = albumName,
                        artistName = song.artist,
                        artistId = artistId,
                        songCount = 1,
                        dateAdded = System.currentTimeMillis(),
                        year = 0,
                        albumArtUriString = song.albumArtUriString
                    )
                    albumsToInsert.add(album)

                    val youtubeArtistRefs = listOf(
                        ArtistRef(
                            id = artistId,
                            name = song.artist,
                            isPrimary = true
                        )
                    )

                    val entity = SongEntity(
                        id = songId,
                        title = song.title,
                        artistName = song.artist,
                        artistId = artistId,
                        albumArtist = null,
                        albumName = albumName,
                        albumId = albumId,
                        contentUriString = "youtube://$yId",
                        albumArtUriString = song.albumArtUriString,
                        duration = song.duration,
                        genre = "YouTube",
                        filePath = song.path,
                        parentDirectoryPath = "/Cloud/YouTube",
                        isFavorite = song.isFavorite,
                        lyrics = song.lyrics,
                        trackNumber = song.trackNumber,
                        discNumber = song.discNumber,
                        year = song.year,
                        dateAdded = System.currentTimeMillis(),
                        mimeType = song.mimeType ?: "audio/opus",
                        bitrate = song.bitrate,
                        sampleRate = song.sampleRate,
                        telegramChatId = null,
                        telegramFileId = null,
                        artistsJson = serializeArtistRefs(youtubeArtistRefs),
                        sourceType = SourceType.YOUTUBE,
                        albumBrowseId = song.albumBrowseId
                    )
                    songsToInsert.add(entity)
                    crossRefsToInsert.add(
                        SongArtistCrossRef(
                            songId = songId,
                            artistId = artistId,
                            isPrimary = true
                        )
                    )
                }
            } else {
                mappedIds.add(song.id)
            }
        }

        if (songsToInsert.isNotEmpty()) {
            musicDao.incrementalSyncMusicData(
                songs = songsToInsert,
                albums = albumsToInsert.distinctBy { it.id },
                artists = artistsToInsert.distinctBy { it.id },
                crossRefs = crossRefsToInsert,
                deletedSongIds = emptyList()
            )
        }
        return mappedIds.distinct()
    }

    /**
     * @param playlistIds Ids of playlists to add the song to
     * */
    fun addOrRemoveSongFromPlaylists(
        song: Song,
        playlistIds: List<String>,
        currentPlaylistId: String?
    ) {
        viewModelScope.launch {
            val mappedIds = ensureSongsPersisted(listOf(song))
            val mappedSongId = mappedIds.firstOrNull() ?: song.id
            
            val currentPlaylists = playlistPreferencesRepository.userPlaylistsFlow.first()
            val songIds = mutableSetOf(song.id, mappedSongId)
            song.youtubeId?.let { yid ->
                songIds.add(yid)
                songIds.add("youtube_$yid")
            }
            if (song.id.startsWith("youtube_")) {
                songIds.add(song.id.removePrefix("youtube_"))
            }
            val addedToPlaylists = playlistIds.filter { pid ->
                val pl = currentPlaylists.find { it.id == pid }
                pl != null && pl.songIds.none { it in songIds }
            }

            val removedFromPlaylists =
                playlistPreferencesRepository.addOrRemoveSongFromPlaylists(mappedSongId, playlistIds)
            if (currentPlaylistId != null && removedFromPlaylists.contains (currentPlaylistId)) {
                removeSongFromPlaylist(currentPlaylistId, mappedSongId)
            }

            // Sync all removals to remote YouTube playlists
            removedFromPlaylists.forEach { playlistId ->
                if (playlistId != currentPlaylistId) {
                    val playlist = currentPlaylists.find { it.id == playlistId }
                    if (playlist != null && playlist.source == "YOUTUBE") {
                        val videoId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else song.id
                        if (videoId.isNotBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val setVideoIds = YouTube.playlistEntrySetVideoIds(playlist.id, videoId).getOrNull()
                                    setVideoIds?.forEach { setVideoId ->
                                        YouTube.removeFromPlaylist(playlist.id, videoId, setVideoId)
                                    }
                                } catch (e: Exception) {
                                    Log.e("PlaylistViewModel", "Failed to sync song removal to YouTube playlist $playlistId", e)
                                }
                            }
                        }
                    }
                }
            }

            addedToPlaylists.forEach { playlistId ->
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                if (playlist != null && playlist.source == "YOUTUBE") {
                    val settings = datastoreRepository.settings.first()
                    if (!settings.cookies.isEmpty()) {
                        val videoId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
                        if (videoId != null) {
                            try {
                                withContext(Dispatchers.IO) {
                                    YouTube.addToPlaylist(playlist.id, videoId)
                                }
                            } catch (e: Exception) {
                                Log.e("PlaylistViewModel", "Failed to sync added song to YouTube playlist", e)
                            }
                        }
                    }
                }
            }
        }
    }

    fun addSongsToPlaylists(songs: List<Song>, playlistIds: List<String>) {
        viewModelScope.launch {
            val mappedIds = ensureSongsPersisted(songs)
            playlistIds.forEach { playlistId ->
                playlistPreferencesRepository.addSongsToPlaylist(playlistId, mappedIds)
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                if (playlist != null && playlist.source == "YOUTUBE") {
                    val settings = datastoreRepository.settings.first()
                    if (!settings.cookies.isEmpty()) {
                        val videoIds = songs.mapNotNull { it.youtubeId ?: if (it.id.startsWith("youtube_")) it.id.removePrefix("youtube_") else null }
                        if (videoIds.isNotEmpty()) {
                            try {
                                withContext(Dispatchers.IO) {
                                    videoIds.forEach { videoId ->
                                        YouTube.addToPlaylist(playlist.id, videoId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PlaylistViewModel", "Failed to sync added songs to YouTube playlist", e)
                            }
                        }
                    }
                }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            playlistPreferencesRepository.removeSongFromPlaylist(playlistId, songIdToRemove)
            
            val song = musicRepository.getSongsByIdsOnce(listOf(songIdToRemove)).firstOrNull()
            val videoId = song?.youtubeId ?: if (songIdToRemove.startsWith("youtube_")) songIdToRemove.removePrefix("youtube_") else songIdToRemove
            
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                val targets = mutableSetOf(songIdToRemove)
                if (videoId.isNotBlank() && videoId != songIdToRemove) {
                    targets.add(videoId)
                    targets.add("youtube_$videoId")
                }
                _uiState.update {
                    it.copy(currentPlaylistSongs = it.currentPlaylistSongs.filterNot { s -> 
                        s.id in targets || (s.youtubeId != null && s.youtubeId in targets)
                    })
                }
            }
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            if (playlist != null && playlist.source == "YOUTUBE") {
                if (videoId.isNotBlank()) {
                    try {
                        withContext(Dispatchers.IO) {
                            val setVideoIds = YouTube.playlistEntrySetVideoIds(playlist.id, videoId).getOrNull()
                            setVideoIds?.forEach { setVideoId ->
                                YouTube.removeFromPlaylist(playlist.id, videoId, setVideoId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaylistViewModel", "Failed to sync song removal to YouTube playlist", e)
                    }
                }
            }
        }
    }

    fun reorderSongsInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            val currentSongs = _uiState.value.currentPlaylistSongs.toMutableList()
            if (fromIndex in currentSongs.indices && toIndex in currentSongs.indices) {
                val item = currentSongs.removeAt(fromIndex)
                currentSongs.add(toIndex, item)
                val newSongOrderIds = currentSongs.map { it.id }
                playlistPreferencesRepository.reorderSongsInPlaylist(playlistId, newSongOrderIds)
                playlistPreferencesRepository.setPlaylistSongOrderMode(
                    playlistId,
                    MANUAL_ORDER_MODE
                )
                _uiState.update {
                    val updatedModes = it.playlistOrderModes + (playlistId to PlaylistSongsOrderMode.Manual)
                    it.copy(
                        currentPlaylistSongs = currentSongs,
                        playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                        playlistOrderModes = updatedModes
                    )
                }

                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                if (playlist != null && playlist.source == "YOUTUBE") {
                    val setVideoIds = currentPlaylistSetVideoIds.toMutableList()
                    if (fromIndex in setVideoIds.indices && toIndex in setVideoIds.indices) {
                        val setVideoId = setVideoIds.removeAt(fromIndex)
                        setVideoIds.add(toIndex, setVideoId)
                        val successorSetVideoId = if (toIndex + 1 < setVideoIds.size) setVideoIds[toIndex + 1] else null
                        currentPlaylistSetVideoIds = setVideoIds
                        withContext(Dispatchers.IO) {
                            YouTube.moveSongPlaylist(playlistId, setVideoId, successorSetVideoId)
                        }
                    }
                }
            }
        }
    }

    //Sort funs
    fun sortPlaylists(sortOption: SortOption) {
        if (_uiState.value.currentPlaylistSortOption.storageKey == sortOption.storageKey) {
            return
        }

        _uiState.update { it.copy(currentPlaylistSortOption = sortOption) }

        val currentPlaylists = _uiState.value.playlists
        val sortedPlaylists = sortPlaylistsList(currentPlaylists, sortOption)

        _uiState.update { it.copy(playlists = sortedPlaylists) }

        viewModelScope.launch {
            playlistPreferencesRepository.setPlaylistsSortOption(sortOption.storageKey)
        }
    }

    fun setShowTelegramCloudPlaylists(show: Boolean) {
        if (_uiState.value.showTelegramCloudPlaylists == show) return

        _uiState.update { it.copy(showTelegramCloudPlaylists = show) }
        viewModelScope.launch {
            playlistPreferencesRepository.setShowTelegramCloudPlaylists(show)
        }
    }

    fun sortPlaylistSongs(sortOption: SortOption) {
        val playlistId = _uiState.value.currentPlaylistDetails?.id

        // If SongDefaultOrder is selected, reload the playlist to get original order
        if (sortOption == SortOption.SongDefaultOrder) {
            if (playlistId != null) {
                viewModelScope.launch {
                    // Set order mode to Manual (which preserves original order)
                    playlistPreferencesRepository.setPlaylistSongOrderMode(
                        playlistId,
                        MANUAL_ORDER_MODE
                    )
                    // Reload the playlist to get original song order
                    loadPlaylistDetails(playlistId)
                }
            }
            return
        }

        val currentSongs = _uiState.value.currentPlaylistSongs

        // PERF FIX: sortSongsList runs a compareBy-chain sort over the whole playlist (each
        // comparison re-lowercases title/artist). sortPlaylistSongs() is called directly from
        // a Compose onClick, so this used to execute inline on whichever thread called it -
        // the UI thread - meaning every tap on a sort option blocked a frame for as long as the
        // sort took. On a playlist of a few thousand songs on a slower device that's a visible
        // stutter on every single sort change. Dispatchers.Default moves the CPU-bound sort off
        // the UI thread; StateFlow.update() is safe to call from any thread, so no hop back to
        // Main is needed before publishing the sorted result.
        viewModelScope.launch(Dispatchers.Default) {
            val sortedSongs = sortSongsList(currentSongs, sortOption)

            _uiState.update {
                val updatedModes = if (playlistId != null) {
                    it.playlistOrderModes + (playlistId to PlaylistSongsOrderMode.Sorted(sortOption))
                } else {
                    it.playlistOrderModes
                }
                it.copy(
                    currentPlaylistSongs = sortedSongs,
                    currentPlaylistSongsSortOption = sortOption,
                    playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(sortOption),
                    playlistOrderModes = updatedModes
                )
            }
        }

        if (playlistId != null) {
            viewModelScope.launch {
                playlistPreferencesRepository.setPlaylistSongOrderMode(
                    playlistId,
                    sortOption.storageKey
                )
            }
        }

        // Persist local sort preference if needed (optional, not requested but good UX)
        // For now, we keep it in memory as per request focus.
    }

    private fun isFolderPlaylistId(playlistId: String): Boolean =
        playlistId.startsWith(FOLDER_PLAYLIST_PREFIX)

    private fun findFolder(
        targetPath: String,
        folders: List<com.unshoo.pixelmusic.data.model.MusicFolder>
    ): com.unshoo.pixelmusic.data.model.MusicFolder? {
        val queue: ArrayDeque<com.unshoo.pixelmusic.data.model.MusicFolder> = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (folder.path == targetPath) {
                return folder
            }
            folder.subFolders.forEach { queue.addLast(it) }
        }
        return null
    }

    private fun com.unshoo.pixelmusic.data.model.MusicFolder.collectAllSongs(): List<Song> {
        return songs + subFolders.flatMap { it.collectAllSongs() }
    }

    private fun applySortToSongs(songs: List<Song>, sortOption: SortOption): List<Song> {
        return sortSongsList(songs, sortOption)
    }

    private fun sortPlaylistsList(
        playlists: List<com.unshoo.pixelmusic.data.model.Playlist>,
        sortOption: SortOption
    ): List<com.unshoo.pixelmusic.data.model.Playlist> {
        val pinned = playlists.filter { it.isPinned }
        val unpinned = playlists.filter { !it.isPinned }
        val sortedPinned = sortPlaylistsListRaw(pinned, sortOption)
        val sortedUnpinned = sortPlaylistsListRaw(unpinned, sortOption)
        return sortedPinned + sortedUnpinned
    }

    private fun sortPlaylistsListRaw(
        playlists: List<com.unshoo.pixelmusic.data.model.Playlist>,
        sortOption: SortOption
    ): List<com.unshoo.pixelmusic.data.model.Playlist> {
        return when (sortOption) {
            SortOption.PlaylistNameAZ -> playlists.sortedWith(
                compareBy<com.unshoo.pixelmusic.data.model.Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
            SortOption.PlaylistNameZA -> playlists.sortedWith(
                compareByDescending<com.unshoo.pixelmusic.data.model.Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
            SortOption.PlaylistDateCreated -> playlists.sortedWith(
                compareByDescending<com.unshoo.pixelmusic.data.model.Playlist> { it.lastModified }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.PlaylistDateCreatedAsc -> playlists.sortedWith(
                compareBy<com.unshoo.pixelmusic.data.model.Playlist> { it.lastModified }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            else -> playlists.sortedWith(
                compareBy<com.unshoo.pixelmusic.data.model.Playlist> { it.name.lowercase() }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.id }
            )
        }
    }

    private fun sortSongsList(
        songs: List<Song>,
        sortOption: SortOption
    ): List<Song> {
        return when (sortOption) {
            SortOption.SongTitleAZ -> songs.sortedWith(
                compareBy<Song> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongTitleZA -> songs.sortedWith(
                compareByDescending<Song> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongArtist -> songs.sortedWith(
                compareBy<Song> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongArtistDesc -> songs.sortedWith(
                compareByDescending<Song> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongAlbum -> songs.sortedWith(
                compareBy<Song> { it.album.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongAlbumDesc -> songs.sortedWith(
                compareByDescending<Song> { it.album.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDuration -> songs.sortedWith(
                compareByDescending<Song> { it.duration }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDurationAsc -> songs.sortedWith(
                compareBy<Song> { it.duration }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDateAdded -> songs.sortedWith(
                compareByDescending<Song> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDateAddedAsc -> songs.sortedWith(
                compareBy<Song> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            else -> songs
        }
    }

    private fun decodeOrderMode(value: String): PlaylistSongsOrderMode {
        return if (value == MANUAL_ORDER_MODE) {
            PlaylistSongsOrderMode.Manual
        } else {
            val option = SortOption.fromStorageKey(value, SortOption.SONGS, SortOption.SongTitleAZ)
            PlaylistSongsOrderMode.Sorted(option)
        }
    }

    fun generateAiPlaylist(prompt: String, minLength: Int = 10, maxLength: Int = 50) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiGenerating = true, aiGenerationError = null) }

            try {
                val allSongs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }

                // Call AiPlaylistGenerator
                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength
                )

                result.onSuccess { selectedSongs ->
                    // Create Playlist
                    val playlistName = "AI: $prompt".take(50)

                    playlistPreferencesRepository.createPlaylist(
                        name = playlistName,
                        songIds = selectedSongs.map { it.id },
                        isAiGenerated = true,
                        source = "AI" // Mark as AI source
                    )

                    _uiState.update { it.copy(isAiGenerating = false) }
                    _playlistCreationEvent.emit(true)
                }.onFailure { e ->
                    val errorMessage = if (e.message?.contains("API Key") == true) {
                        context.getString(R.string.ai_playlist_gemini_key_required)
                    } else {
                        e.message ?: context.getString(R.string.error_unknown)
                    }
                    _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = errorMessage) }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = e.message) }
            }
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(aiGenerationError = null) }
    }

    /**
     * Delete multiple playlists in batch
     */
    fun deletePlaylistsInBatch(playlistIds: List<String>) {
        viewModelScope.launch {
            val playlists = playlistPreferencesRepository.userPlaylistsFlow.first()
            playlistIds.forEach { playlistId ->
                if (!isFolderPlaylistId(playlistId)) {
                    val playlist = playlists.find { it.id == playlistId }
                    playlistPreferencesRepository.deletePlaylist(playlistId)
                    if (playlist != null && playlist.source == "YOUTUBE") {
                        try {
                            withContext(Dispatchers.IO) {
                                YouTube.deletePlaylist(playlist.id)
                            }
                        } catch (e: Exception) {
                            Log.e("PlaylistViewModel", "Failed to delete remote YouTube playlist $playlistId", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Merge selected playlists into a new playlist
     * Collects all songs from all selected playlists (removing duplicates)
     */
    fun mergeSelectedPlaylists(playlistIds: List<String>, newPlaylistName: String) {
        if (newPlaylistName.isBlank()) return

        viewModelScope.launch {
            try {
                // Get all songs from selected playlists
                val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
                val mergedSongIds = selectedPlaylists
                    .flatMap { it.songIds }
                    .distinct() // Remove duplicates
                    .toList()

                if (mergedSongIds.isNotEmpty()) {
                    // Create new playlist with merged songs
                    playlistPreferencesRepository.createPlaylist(newPlaylistName, mergedSongIds)
                    _playlistCreationEvent.emit(true)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error merging playlists", e)
            }
        }
    }

    /**
     * Get all playlists with their song data for bulk operations
     */
    suspend fun getPlaylistsWithSongs(playlistIds: List<String>): List<Pair<Playlist, List<Song>>> {
        return try {
            val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
            selectedPlaylists.map { playlist ->
                val songs = musicRepository.getSongsByIdsOnce(playlist.songIds)
                playlist to songs
            }
        } catch (e: Exception) {
            Log.e("PlaylistViewModel", "Error getting playlists with songs", e)
            emptyList()
        }
    }

    /**
     * Share all selected playlists as M3U files in a ZIP
     */
    fun shareSelectedPlaylistsAsZip(playlistIds: List<String>, activity: android.app.Activity?) {
        if (activity == null) {
            Log.w("PlaylistViewModel", "Activity is null, cannot share")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("PlaylistViewModel", "Starting share of ${playlistIds.size} playlists")
                // Get all selected playlists with their songs
                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)

                if (playlistsWithSongs.isEmpty()) {
                    Log.w("PlaylistViewModel", "No playlists found to share")
                    Toast.makeText(context, context.getString(R.string.playlist_none_to_share), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val shareFile: File
                val shareFileName: String
                val shareMimeType: String

                if (playlistsWithSongs.size == 1) {
                    // Single playlist: share M3U file directly
                    val (playlist, songs) = playlistsWithSongs.first()
                    val m3uContent = m3uManager.generateM3u(playlist, songs)
                    shareFileName = "${playlist.name}.m3u"
                    shareFile = File(context.cacheDir, shareFileName)
                    shareFile.writeText(m3uContent)
                    shareMimeType = "audio/mpegurl"
                    Log.d("PlaylistViewModel", "Created M3U file: ${shareFile.absolutePath}, size: ${shareFile.length()} bytes")
                } else {
                    // Multiple playlists: create ZIP file
                    val zipFileName = "Playlists_${playlistsWithSongs.first().first.name}_and_${playlistsWithSongs.size - 1}_more.zip"
                    shareFile = File(context.cacheDir, zipFileName)
                    val outputStream = FileOutputStream(shareFile)

                    java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                        playlistsWithSongs.forEach { (playlist, songs) ->
                            val m3uContent = m3uManager.generateM3u(playlist, songs)
                            val entry = java.util.zip.ZipEntry("${playlist.name}.m3u")
                            zipOut.putNextEntry(entry)
                            zipOut.write(m3uContent.toByteArray())
                            zipOut.closeEntry()
                        }
                    }

                    shareFileName = zipFileName
                    shareMimeType = "application/zip"
                    Log.d("PlaylistViewModel", "Created ZIP file: ${shareFile.absolutePath}, size: ${shareFile.length()} bytes")
                }

                // Share the file
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    shareFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = shareMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                Log.d("PlaylistViewModel", "Launching share intent for: $shareFileName")
                activity.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.playlist_share_chooser_title)))
                val n = playlistsWithSongs.size
                val sharingMsg = context.resources.getQuantityString(R.plurals.sharing_playlists_message, n, n)
                Toast.makeText(context, sharingMsg, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error sharing playlists", e)
                Toast.makeText(context, context.getString(R.string.playlist_share_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Merge multiple playlists into one new playlist
     * @param playlistIds List of playlist IDs to merge
     * @param newPlaylistName Name for the merged playlist
     */
    fun mergePlaylistsIntoOne(playlistIds: List<String>, newPlaylistName: String) {
        if (playlistIds.isEmpty() || newPlaylistName.isEmpty()) return

        viewModelScope.launch {
            try {
                // Get all playlists first
                val currentPlaylists = _uiState.value.playlists

                // Get all songs from selected playlists
                val allSongs = mutableSetOf<String>()
                playlistIds.forEach { playlistId ->
                    val playlist = currentPlaylists.find { it.id == playlistId }
                    if (playlist != null) {
                        allSongs.addAll(playlist.songIds)
                    }
                }

                // Create new playlist with merged songs
                val newPlaylist = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = newPlaylistName,
                    songIds = allSongs.toList(),
                    createdAt = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis(),
                    isAiGenerated = false,
                    isQueueGenerated = false
                )

                playlistPreferencesRepository.createPlaylist(
                    name = newPlaylistName,
                    songIds = allSongs.toList(),
                    isAiGenerated = false,
                    isQueueGenerated = false
                )

                Log.d("PlaylistViewModel", "Successfully merged ${playlistIds.size} playlists into '$newPlaylistName' with ${allSongs.size} total unique songs")

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error merging playlists", e)
            }
        }
    }

    /**
     * Export selected playlists as M3U files to device storage
     */
    fun exportPlaylistsAsM3u(playlistIds: List<String>) {
        if (playlistIds.isEmpty()) return

        viewModelScope.launch {
            try {
                Log.d("PlaylistViewModel", "Starting export of ${playlistIds.size} playlists")
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }

                val exportDir = File(musicDir, "PixelMusic Exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)
                if (playlistsWithSongs.isEmpty()) {
                    Log.w("PlaylistViewModel", "No playlists found to export")
                    Toast.makeText(context, context.getString(R.string.playlist_none_to_export), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                playlistsWithSongs.forEach { (playlist, songs) ->
                    val m3uContent = m3uManager.generateM3u(playlist, songs)
                    val file = File(exportDir, "${playlist.name}.m3u")
                    file.writeText(m3uContent)
                    Log.d("PlaylistViewModel", "Exported playlist '${playlist.name}' to ${file.absolutePath}")
                }

                Log.d("PlaylistViewModel", "Successfully exported ${playlistIds.size} playlists to $exportDir")
                val count = playlistsWithSongs.size
                val folderLabel = context.getString(R.string.playlist_export_folder_display)
                val exportedMsg = context.resources.getQuantityString(R.plurals.exported_playlists_message, count, count, folderLabel)
                Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting playlists", e)
                Toast.makeText(context, context.getString(R.string.playlist_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Re-fetches the currently displayed YouTube playlist from the remote API.
     * Call this after toggling a song's like/favorite status so the playlist UI
     * immediately reflects any server-side changes (e.g., a newly liked song
     * appearing in the user's Liked Music playlist).
     */
    fun refreshCurrentPlaylist() {
        val currentId = _uiState.value.currentPlaylistDetails?.id ?: return
        val source = _uiState.value.currentPlaylistDetails?.source
        if (source == "YOUTUBE") {
            loadPlaylistDetails(currentId)
        }
    }

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeSongId(youtubeId)

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumName)

    private fun toUnifiedYoutubeArtistId(artistName: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeArtistId(artistName)

    private fun parseYoutubeArtistNames(artistStr: String): List<String> {
        if (artistStr.isBlank()) return listOf("Unknown Artist")
        // Split on common separators including natural-language " and " connector
        val parsed = artistStr
            .split(Regex("\\s*[,/&;+、•]\\s*|\\s+(?:feat\\.|ft\\.|vs)\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    fun syncYouTubePlaylist(playlistId: String) {
        if (_syncingPlaylists.value.contains(playlistId)) return
        _syncingPlaylists.update { it + playlistId }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                if (playlist != null && playlist.source == "YOUTUBE") {
                    // Retry up to 3 times for mobile data reliability
                    var ytPlaylistResult = YouTube.playlist(playlistId)
                    var fetchAttempt = 0
                    while (ytPlaylistResult.isFailure && fetchAttempt < 2) {
                        fetchAttempt++
                        kotlinx.coroutines.delay(1500L * fetchAttempt)
                        ytPlaylistResult = YouTube.playlist(playlistId)
                    }

                    if (ytPlaylistResult.isSuccess) {
                        val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                        val ytPlaylist = ytPlaylistPage.playlist
                        val allYtSongs = ytPlaylistPage.songs.toMutableList()
                        var continuation = ytPlaylistPage.songsContinuation ?: ytPlaylistPage.continuation
                        var pages = 0
                        while (continuation != null && pages < 20) {
                            var contResult = YouTube.playlistContinuation(continuation)
                            // Retry once on failure
                            if (contResult.isFailure) {
                                kotlinx.coroutines.delay(1000L)
                                contResult = YouTube.playlistContinuation(continuation)
                            }
                            if (contResult.isSuccess) {
                                val contPage = contResult.getOrThrow()
                                allYtSongs.addAll(contPage.songs)
                                continuation = contPage.continuation
                                pages++
                            } else {
                                break
                            }
                        }
                        val allNativeSongs = allYtSongs.map { it.toNativeSong() }
                        
                        // Insert standard Room entity fields
                        val songsToInsert = allNativeSongs.map { song ->
                            val parsedArtists = parseYoutubeArtistNames(song.artist)
                            val primaryArtistName = parsedArtists.firstOrNull() ?: "Unknown Artist"
                            val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)
                            
                            val artistsJson = try {
                                val arr = org.json.JSONArray()
                                parsedArtists.forEachIndexed { idx, name ->
                                    val obj = org.json.JSONObject()
                                    obj.put("id", toUnifiedYoutubeArtistId(name))
                                    obj.put("name", name)
                                    obj.put("primary", idx == 0)
                                    arr.put(obj)
                                }
                                arr.toString()
                            } catch (e: Exception) {
                                null
                            }
                            
                            val rawYtId = song.youtubeId ?: song.id.removePrefix("youtube_")
                            val songId = toUnifiedYoutubeSongId(rawYtId)
                            val songAlbum = song.album.takeIf { it.isNotBlank() } ?: "YouTube Music"
                            val albumId = toUnifiedYoutubeAlbumId(songAlbum)
                            SongEntity(
                                    id = songId,
                                    title = song.title,
                                    artistName = song.artist,
                                    artistId = primaryArtistId,
                                    albumArtist = null,
                                    albumName = songAlbum,
                                    albumId = albumId,
                                    contentUriString = "youtube://$rawYtId",
                                    albumArtUriString = song.albumArtUriString,
                                    duration = song.duration,
                                    genre = "YouTube Music",
                                    filePath = "",
                                    parentDirectoryPath = "youtube://",
                                    isFavorite = false,
                                    lyrics = null,
                                    trackNumber = 0,
                                    year = 0,
                                    dateAdded = System.currentTimeMillis(),
                                    mimeType = "audio/opus",
                                    bitrate = null,
                                    sampleRate = null,
                                    telegramChatId = null,
                                    telegramFileId = null,
                                    artistsJson = artistsJson,
                                    sourceType = SourceType.YOUTUBE,
                                    albumBrowseId = song.albumBrowseId
                                )
                            }
                        
                        // We also need to map the unique albums and artists to insert them to avoid foreign key violations
                        val uniqueArtists = allNativeSongs.flatMap { parseYoutubeArtistNames(it.artist) }.distinct().map { name ->
                            ArtistEntity(
                                id = toUnifiedYoutubeArtistId(name),
                                name = name,
                                trackCount = 0,
                                imageUrl = null
                            )
                        }
                        
                        val crossRefs = allNativeSongs.flatMap { song ->
                            val parsedArtists = parseYoutubeArtistNames(song.artist)
                            parsedArtists.mapIndexed { index, name ->
                                SongArtistCrossRef(
                                    songId = toUnifiedYoutubeSongId(song.youtubeId ?: song.id.removePrefix("youtube_")),
                                    artistId = toUnifiedYoutubeArtistId(name),
                                    isPrimary = index == 0
                                )
                            }
                        }
                        
                        val albumsToInsert = allNativeSongs.map { song ->
                            val songAlbum = song.album.takeIf { it.isNotBlank() } ?: "YouTube Music"
                            val albumId = toUnifiedYoutubeAlbumId(songAlbum)
                            val primaryArtist = parseYoutubeArtistNames(song.artist).firstOrNull() ?: "YouTube Music"
                            AlbumEntity(
                                id = albumId,
                                title = songAlbum,
                                artistName = primaryArtist,
                                artistId = toUnifiedYoutubeArtistId(primaryArtist),
                                songCount = 0,
                                dateAdded = System.currentTimeMillis(),
                                year = 0,
                                albumArtUriString = song.albumArtUriString ?: ytPlaylist.thumbnail
                            )
                        }.distinctBy { it.id }
                        
                        musicDao.incrementalSyncMusicData(
                            songs = songsToInsert,
                            albums = albumsToInsert,
                            artists = uniqueArtists,
                            crossRefs = crossRefs,
                            deletedSongIds = emptyList()
                        )
                        
                        val updatedIds = allNativeSongs.map { it.id }
                        playlistPreferencesRepository.updatePlaylist(
                            playlist.copy(
                                name = ytPlaylist.title,
                                songIds = updatedIds,
                                coverImageUri = ytPlaylist.thumbnail
                            )
                        )
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Playlist sync successful!", Toast.LENGTH_SHORT).show()
                        }
                        
                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                            loadPlaylistDetails(playlistId)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to sync playlist from YouTube", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Failed to sync YouTube playlist: $playlistId", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _syncingPlaylists.update { it - playlistId }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun parseDurationMillis(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val parts = durationStr.split(":")
        var seconds = 0L
        try {
            when (parts.size) {
                1 -> {
                    val raw = parts[0].toLongOrNull() ?: 0L
                    seconds = if (raw >= 1000L) raw / 1000L else raw
                }
                2 -> seconds = (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
                3 -> seconds = (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
            }
        } catch (_: Exception) {}
        return seconds * 1000
    }
}

private class PlaylistYTItemTypeAdapter : com.google.gson.JsonSerializer<unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem>, com.google.gson.JsonDeserializer<unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem> {
    override fun serialize(src: unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val obj = context.serialize(src).asJsonObject
        obj.addProperty("type", src::class.java.simpleName)
        return obj
    }

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val clazz = when (type) {
            "SongItem" -> unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem::class.java
            "AlbumItem" -> unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem::class.java
            "PlaylistItem" -> unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem::class.java
            "ArtistItem" -> unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem::class.java
            else -> throw com.google.gson.JsonParseException("Unknown type: $type")
        }
        return context.deserialize(obj, clazz)
    }
}
