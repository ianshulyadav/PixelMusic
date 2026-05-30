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
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.playlist.M3uManager
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
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
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.serializeArtistRefs
import com.unshoo.pixelmusic.data.model.ArtistRef
import kotlin.math.absoluteValue

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val showTelegramCloudPlaylists: Boolean = true,
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

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val dailyMixManager: DailyMixManager,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val m3uManager: M3uManager,
    private val musicDao: MusicDao,
    private val datastoreRepository: DatastoreRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _syncingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val syncingPlaylists: StateFlow<Set<String>> = _syncingPlaylists.asStateFlow()

    private var currentPlaylistSetVideoIds: List<String> = emptyList()

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
                val sortedPlaylists = sortPlaylistsList(playlists, currentSortOption)
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
        viewModelScope.launch {
            val shouldKeepExisting = _uiState.value.currentPlaylistDetails?.id == playlistId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    playlistNotFound = false,
                    currentPlaylistDetails = if (shouldKeepExisting) it.currentPlaylistDetails else null,
                    currentPlaylistSongs = if (shouldKeepExisting) it.currentPlaylistSongs else emptyList()
                )
            } // Resetear detalles y canciones
            try {
                if (isFolderPlaylistId(playlistId)) {
                    val folderPath = Uri.decode(playlistId.removePrefix(FOLDER_PLAYLIST_PREFIX))
                    val folders = musicRepository.getMusicFolders().first()
                    val folder = findFolder(folderPath, folders)

                    if (folder != null) {
                        val songsList = withContext(Dispatchers.IO) {
                            val rawSongs = folder.collectAllSongs()
                            if (rawSongs.any { it.contentUriString.isBlank() }) {
                                musicRepository.getSongsByIds(rawSongs.map { it.id }).first()
                            } else {
                                rawSongs
                            }
                        }
                        val pseudoPlaylist = Playlist(
                            id = playlistId,
                            name = folder.name,
                            songIds = songsList.map { it.id }
                        )

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = pseudoPlaylist,
                                currentPlaylistSongs = applySortToSongs(songsList, it.currentPlaylistSongsSortOption),
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
                    // Obtener la playlist de las preferencias del usuario
                    val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                        .find { it.id == playlistId }

                    if (playlist != null) {
                        val orderMode = _uiState.value.playlistOrderModes[playlistId]
                            ?: PlaylistSongsOrderMode.Manual

                        // Colectar la lista de canciones del Flow devuelto por el repositorio en un hilo de IO
                        val songsList: List<Song> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            musicRepository.getSongsByIds(playlist.songIds).first()
                        }

                        val orderedSongs = when (orderMode) {
                            is PlaylistSongsOrderMode.Sorted -> applySortToSongs(songsList, orderMode.option)
                            PlaylistSongsOrderMode.Manual -> songsList
                        }

                        // La actualización del UI se hace en el hilo principal
                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = playlist,
                                currentPlaylistSongs = orderedSongs,
                                currentPlaylistSongsSortOption = (orderMode as? PlaylistSongsOrderMode.Sorted)?.option
                                    ?: it.currentPlaylistSongsSortOption,
                                playlistSongsOrderMode = orderMode,
                                playlistOrderModes = it.playlistOrderModes + (playlistId to orderMode),
                                isLoading = songsList.isEmpty() && playlist.source == "YOUTUBE",
                                playlistNotFound = false
                            )
                        }

                        // Background fetch & sync for synced YouTube playlists
                        if (playlist.source == "YOUTUBE") {
                            viewModelScope.launch(Dispatchers.IO) {
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

                                    // Accumulate ALL pages before touching the UI or preferences.
                                    // This prevents the "songs disappear" race where page-1 (25 songs)
                                    // immediately overwrites the cached full list from the DB.
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

                                    // Now we have the full list — do one atomic update
                                    val allNativeSongs = allYtSongs.map { it.toNativeSong() }
                                    val allSongIds = allNativeSongs.map { it.id }

                                    // Persist all songs to Room DB
                                    musicRepository.insertYoutubeSongs(allNativeSongs)

                                    // Persist the full songIds list to preferences
                                    val currentExisting = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                                    if (currentExisting != null) {
                                        playlistPreferencesRepository.updatePlaylist(
                                            currentExisting.copy(
                                                name = ytPlaylist.title,
                                                songIds = allSongIds,
                                                coverImageUri = ytPlaylist.thumbnail
                                            )
                                        )
                                    }

                                    currentPlaylistSetVideoIds = allYtSongs.mapNotNull { it.setVideoId }

                                    // Single UI update with the complete songs list
                                    withContext(Dispatchers.Main) {
                                        if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                                            _uiState.update { state ->
                                                state.copy(
                                                    currentPlaylistDetails = state.currentPlaylistDetails?.copy(
                                                        name = ytPlaylist.title,
                                                        songIds = allSongIds,
                                                        coverImageUri = ytPlaylist.thumbnail
                                                    ),
                                                    currentPlaylistSongs = allNativeSongs,
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
                        val ytPlaylistResult = withContext(Dispatchers.IO) {
                            YouTube.playlist(playlistId)
                        }
                        if (ytPlaylistResult.isSuccess) {
                            val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                            val ytPlaylist = ytPlaylistPage.playlist
                            
                            val firstPageSongs = ytPlaylistPage.songs.map { it.toNativeSong() }
                            
                            // Cache first page online playlist songs in Room DB
                            musicRepository.insertYoutubeSongs(firstPageSongs)

                            val playlistModel = Playlist(
                                id = playlistId,
                                name = ytPlaylist.title,
                                songIds = firstPageSongs.map { it.id },
                                coverImageUri = ytPlaylist.thumbnail,
                                source = "YOUTUBE"
                            )

                            // Only update preferences if the playlist already exists locally.
                            // Do NOT create a new local entry — the user is just browsing from Explore.
                            val existing = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
                            if (existing != null) {
                                playlistPreferencesRepository.updatePlaylist(
                                    existing.copy(
                                        name = ytPlaylist.title,
                                        songIds = firstPageSongs.map { it.id },
                                        coverImageUri = ytPlaylist.thumbnail
                                    )
                                )
                            }

                            currentPlaylistSetVideoIds = ytPlaylistPage.songs.mapNotNull { it.setVideoId }

                            _uiState.update {
                                it.copy(
                                    currentPlaylistDetails = playlistModel,
                                    currentPlaylistSongs = firstPageSongs,
                                    playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                                    isLoading = false,
                                    playlistNotFound = false
                                )
                            }

                            // Fetch the remaining pages progressively in the background coroutine
                            viewModelScope.launch(Dispatchers.IO) {
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
                                        val currentExisting = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
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
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, playlistNotFound = true) }
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
                ensureSongsPersisted(songs)
            } else if (resolvedSmartRule != null) {
                buildSmartPlaylistSongIds(
                    rule = resolvedSmartRule,
                    limit = SMART_PLAYLIST_MAX_ITEMS
                )
            } else {
                songIds
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
                        val loadedSongs = musicRepository.getSongsByIds(songIds).first()
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
                // Load original bitmap
                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        // Optimization: Mutable to support software rendering if needed
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        // Use HARWARE if possible but need to copy for Canvas?
                        // Software is safer for manual Canvas drawing.
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

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

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, songIds) = m3uManager.parseM3u(uri)
                if (songIds.isNotEmpty()) {
                    playlistPreferencesRepository.createPlaylist(name, songIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error importing M3U", e)
            }
        }
    }

    fun exportM3u(playlist: Playlist, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
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
                // Check if it is a content URI or a file path that is NOT the existing saved path
                if (coverImageUri.startsWith("content://") || (coverImageUri.startsWith("/") && coverImageUri != currentPlaylist.coverImageUri)) {
                    val imageId = UUID.randomUUID().toString()
                    val newPath = saveCoverImageToInternalStorage(
                        Uri.parse(coverImageUri),
                        imageId,
                        cropScale,
                        cropPanX,
                        cropPanY
                    )
                    if (newPath != null) {
                        savedCoverPath = newPath
                    }
                }
            } else if (coverImageUri == null) {
                // If passed null, it might mean remove cover? Or just no change?
                // For this implementation let's assume if the user cleared it, the UI passes null.
                // But we need to distinguish "no change" vs "remove".
                // In CreatePlaylist we have "selectedImageUri".
                // Let's assume the UI sends the desired final state.
                // NOTE: If the user didn't change the image, the UI might send the existing coverImageUri (which is a file path).
                // Or if they removed it, they send null.

                // However, we also have crop parameters. If image is unchanged but crop changed, we should re-save (re-crop)
                // if we have the original source. But we don't have the original source for the existing cover (we only have the cropped result).
                // So, we can only re-crop if we have a source URI.
                // This limitation implies: We can only update crop if we pick an image.
                // So if coverImageUri is the existing path, we ignore crop params.
                savedCoverPath = null // If explicit null passed, we remove it.
            }
            // Logic correction: 
            // If the UI passes the EXISTING file path, implies NO CHANGE to image.
            // If the UI passes a NEW content URI, implies NEW IMAGE (and we use crop params).
            // If the UI passes NULL, implies REMOVE IMAGE.
            if (coverImageUri == currentPlaylist.coverImageUri) {
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
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            if (playlist != null && playlist.source == "YOUTUBE") {
                val settings = datastoreRepository.settings.first()
                if (!settings.cookies.isEmpty()) {
                    val songs = musicRepository.getSongsByIds(songIdsToAdd).first()
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

    private suspend fun ensureSongsPersisted(songs: List<Song>): List<String> {
        val mappedIds = mutableListOf<String>()
        val songsToInsert = mutableListOf<SongEntity>()
        val albumsToInsert = mutableListOf<AlbumEntity>()
        val artistsToInsert = mutableListOf<ArtistEntity>()
        val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()

        songs.forEach { song ->
            if (song.id.startsWith("youtube_") || song.youtubeId != null) {
                val yId = song.youtubeId ?: song.id.removePrefix("youtube_")
                val songId = -(15_000_000_000_000L + yId.hashCode().toLong().absoluteValue)
                mappedIds.add(songId.toString())

                // Check if already in DB
                val existing = musicDao.getSongByIdOnce(songId)
                if (existing == null) {
                    val albumId = -(16_000_000_000_000L + "YouTube Music".lowercase().hashCode().toLong().absoluteValue)
                    val artistId = -(17_000_000_000_000L + song.artist.lowercase().hashCode().toLong().absoluteValue)

                    val artist = ArtistEntity(
                        id = artistId,
                        name = song.artist,
                        trackCount = 1,
                        imageUrl = null
                    )
                    artistsToInsert.add(artist)

                    val album = AlbumEntity(
                        id = albumId,
                        title = "YouTube Music",
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
                        albumName = "YouTube Music",
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
                        mimeType = song.mimeType ?: "audio/webm",
                        bitrate = song.bitrate,
                        sampleRate = song.sampleRate,
                        telegramChatId = null,
                        telegramFileId = null,
                        artistsJson = serializeArtistRefs(youtubeArtistRefs),
                        sourceType = SourceType.YOUTUBE
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
        return mappedIds
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
            val addedToPlaylists = playlistIds.filter { pid ->
                val pl = currentPlaylists.find { it.id == pid }
                pl != null && mappedSongId !in pl.songIds
            }

            val removedFromPlaylists =
                playlistPreferencesRepository.addOrRemoveSongFromPlaylists(mappedSongId, playlistIds)
            if (currentPlaylistId != null && removedFromPlaylists.contains (currentPlaylistId)) {
                removeSongFromPlaylist(currentPlaylistId, mappedSongId)
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
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(currentPlaylistSongs = it.currentPlaylistSongs.filterNot { s -> s.id == songIdToRemove })
                }
            }
            val playlist = playlistPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            if (playlist != null && playlist.source == "YOUTUBE") {
                val song = musicRepository.getSongsByIds(listOf(songIdToRemove)).first().firstOrNull()
                val videoId = song?.youtubeId ?: if (songIdToRemove.startsWith("youtube_")) songIdToRemove.removePrefix("youtube_") else songIdToRemove
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
            playlistIds.forEach { playlistId ->
                if (!isFolderPlaylistId(playlistId)) {
                    playlistPreferencesRepository.deletePlaylist(playlistId)
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
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
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

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long {
        return -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long {
        return -(15_000_000_000_000L + albumName.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeArtistId(artistName: String): Long {
        return -(15_000_000_000_000L + artistName.hashCode().toLong().absoluteValue)
    }

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
                        while (continuation != null && pages < 10) {
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
                            
                            val songId = toUnifiedYoutubeSongId(song.id)
                            SongEntity(
                                id = songId,
                                title = song.title,
                                artistName = song.artist,
                                artistId = primaryArtistId,
                                albumArtist = null,
                                albumName = "YouTube Music",
                                albumId = toUnifiedYoutubeAlbumId("YouTube Music"),
                                contentUriString = "youtube://${song.id}",
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
                                mimeType = "audio/webm",
                                bitrate = null,
                                sampleRate = null,
                                telegramChatId = null,
                                telegramFileId = null,
                                artistsJson = artistsJson,
                                sourceType = SourceType.YOUTUBE
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
                                    songId = toUnifiedYoutubeSongId(song.id),
                                    artistId = toUnifiedYoutubeArtistId(name),
                                    isPrimary = index == 0
                                )
                            }
                        }
                        
                        val albumToInsert = AlbumEntity(
                            id = toUnifiedYoutubeAlbumId("YouTube Music"),
                            title = "YouTube Music",
                            artistName = "YouTube Music",
                            artistId = toUnifiedYoutubeArtistId("YouTube Music"),
                            songCount = 0,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtUriString = ytPlaylist.thumbnail
                        )
                        
                        musicDao.incrementalSyncMusicData(
                            songs = songsToInsert,
                            albums = listOf(albumToInsert),
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
}
