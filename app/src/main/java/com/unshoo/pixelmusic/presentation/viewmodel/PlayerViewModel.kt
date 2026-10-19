package com.unshoo.pixelmusic.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Trace
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import com.unshoo.pixelmusic.data.remote.youtube.SongDownloadWorker
import com.unshoo.pixelmusic.data.remote.youtube.PlaylistDownloadWorker
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.serializeArtistRefs
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.toSong
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import com.unshoo.pixelmusic.data.model.PlaybackQueueSnapshot
import com.unshoo.pixelmusic.data.model.PlaybackQueueItemSnapshot
import android.media.MediaMetadataRetriever
import kotlin.math.absoluteValue
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.unshoo.pixelmusic.data.model.LibraryTabId
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.EotStateHolder
import com.unshoo.pixelmusic.data.ai.SongMetadata
import com.unshoo.pixelmusic.data.database.AlbumArtThemeDao
import com.unshoo.pixelmusic.data.media.CoverArtUpdate
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.FolderSource
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Lyrics
import com.unshoo.pixelmusic.data.model.LyricsSourcePreference
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.toLibraryTabIdOrNull
import com.unshoo.pixelmusic.data.provider.SharedArtworkContentProvider
import com.unshoo.pixelmusic.data.preferences.CarouselStyle
import com.unshoo.pixelmusic.data.preferences.LibraryNavigationMode
import com.unshoo.pixelmusic.data.preferences.NavBarStyle
import com.unshoo.pixelmusic.data.preferences.FullPlayerLoadingTweaks
import com.unshoo.pixelmusic.data.preferences.AiPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.AlbumArtPaletteStyle
import com.unshoo.pixelmusic.data.preferences.ThemePreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.AlbumArtQuality
import com.unshoo.pixelmusic.data.preferences.ThemePreference
import com.unshoo.pixelmusic.data.preferences.PlayerBackgroundStyle
import com.unshoo.pixelmusic.data.repository.LyricsSearchResult
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.service.MusicNotificationProvider
import com.unshoo.pixelmusic.data.service.MusicService
import com.unshoo.pixelmusic.data.service.player.CastPlayer
import com.unshoo.pixelmusic.data.service.http.MediaFileHttpServerService
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import com.unshoo.pixelmusic.data.service.player.ActiveDecoderInfo
import com.unshoo.pixelmusic.data.worker.SyncManager
import com.unshoo.pixelmusic.data.worker.YouTubeLibrarySyncManager
import com.unshoo.pixelmusic.utils.AppReadinessSignal
import com.unshoo.pixelmusic.utils.AppShortcutManager
import com.unshoo.pixelmusic.utils.ValidatedLyricsImport
import com.unshoo.pixelmusic.utils.QueueUtils
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.utils.LocalArtworkUri
import com.unshoo.pixelmusic.utils.LyricsUtils
import com.unshoo.pixelmusic.utils.StorageType
import com.unshoo.pixelmusic.utils.StorageUtils
import com.unshoo.pixelmusic.utils.ZipShareHelper
import com.unshoo.pixelmusic.utils.normalizeMetadataText
import com.unshoo.pixelmusic.utils.ContentFilterUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import com.unshoo.pixelmusic.data.lastfm.LastFM
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import dagger.Lazy

private const val CAST_LOG_TAG = "PlayerCastTransfer"
private const val ENABLE_FOLDERS_SOURCE_SWITCHING = true
private const val MAX_ALBUM_BATCH_SELECTION = 6
private const val SONG_ID_QUERY_CHUNK_SIZE = 900
private const val HOME_MIX_PREVIEW_LIMIT = 48
private const val OPTIMISTIC_RESOLVE_BUDGET_MS = 1_500L

private fun List<Song>.toPlaybackQueue(): ImmutableList<Song> = when (this) {
    is PersistentList<Song> -> this
    is ImmutableList<Song> -> this
    else -> this.toPersistentList()
}

private fun ImmutableList<Song>.asPersistentPlaybackQueue(): PersistentList<Song> =
    this as? PersistentList<Song> ?: this.toPersistentList()

private fun ImmutableList<Song>.replaceSong(updatedSong: Song): ImmutableList<Song> {
    val index = indexOfFirst { it.id == updatedSong.id }
    if (index == -1) return this
    return asPersistentPlaybackQueue().replacingAt(index, updatedSong)
}

private fun ImmutableList<Song>.removeSongById(songId: String): ImmutableList<Song> {
    val index = indexOfFirst { it.id == songId }
    if (index == -1) return this
    return asPersistentPlaybackQueue().removingAt(index)
}

private fun ImmutableList<Song>.moveSong(fromIndex: Int, toIndex: Int): ImmutableList<Song> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val movedSong = this[fromIndex]
    return asPersistentPlaybackQueue()
        .removingAt(fromIndex)
        .addingAt(toIndex, movedSong)
}

private fun moveQueueIndex(index: Int, fromIndex: Int, toIndex: Int): Int {
    if (index == C.INDEX_UNSET || fromIndex == toIndex) return index
    return when {
        index == fromIndex -> toIndex
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> index - 1
        toIndex < fromIndex && index in toIndex until fromIndex -> index + 1
        else -> index
    }
}

private data class QueueTimelineSignature(
    val count: Int,
    val orderHash: Long,
    val firstMediaId: String?,
    val lastMediaId: String?
)

private data class SortOptionsSnapshot(
    val songSort: SortOption,
    val albumSort: SortOption,
    val artistSort: SortOption,
    val folderSort: SortOption,
    val favoriteSort: SortOption,
)

private data class AiUiSnapshot(
    val showAiPlaylistSheet: Boolean,
    val isGeneratingAiPlaylist: Boolean,
    val aiStatus: String?,
    val aiError: String?,
    val isGeneratingAiMetadata: Boolean,
)

private data class PreparedPlaybackQueue(
    val mediaItems: List<MediaItem>,
    val startIndex: Int
)

private data class PendingMetadataEdit(
    val song: Song,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val composer: String,
    val genre: String,
    val lyrics: String,
    val trackNumber: Int,
    val discNumber: Int?,
    val replayGainTrackGainDb: String?,
    val replayGainAlbumGainDb: String?,
    val coverArtUpdate: CoverArtUpdate?
)

private data class PendingLyricsSave(
    val song: Song,
    val lyrics: Lyrics,
    val preferSynced: Boolean
)

private data class ResolvedAlbumSelection(
    val albums: List<Album>,
    val songs: List<Song>,
    val wasTrimmed: Boolean
)

@UnstableApi
@SuppressLint("LogNotTimber")
@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    val userPreferencesRepository: UserPreferencesRepository,
    private val youtubeDatastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager
    private val musicDao: MusicDao,
    private val youTubeLibrarySyncManager: YouTubeLibrarySyncManager,

    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val telegramCacheManagerProvider: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramRepository>,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val queueUndoStateHolder: QueueUndoStateHolder,
    private val playlistDismissUndoStateHolder: PlaylistDismissUndoStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val libraryTabsStateHolder: LibraryTabsStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val songRemovalStateHolder: SongRemovalStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    val themeStateHolder: ThemeStateHolder,
    val multiSelectionStateHolder: MultiSelectionStateHolder,
    val playlistSelectionStateHolder: PlaylistSelectionStateHolder,
    private val sessionToken: SessionToken,
    private val mediaControllerFactory: com.unshoo.pixelmusic.data.media.MediaControllerFactory
) : ViewModel() {

    private inline fun runOnMainImmediate(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            viewModelScope.launch(Dispatchers.Main.immediate) { block() }
        }
    }

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    val isOnline: StateFlow<Boolean> = connectivityStateHolder.isOnline

    // Dedicated queue flow so the player sheet's MiniPlayer branch does not
    // recompose whenever the queue changes. Consumers that actually need the
    // queue (FullPlayer carousel, queue sheet) collect this narrower flow
    // directly, keeping the unrelated subtree stable.
    val queueFlow: StateFlow<ImmutableList<Song>> = _playerUiState
        .map { it.currentPlaybackQueue }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val artistLibraryFilter: StateFlow<String> = userPreferencesRepository.artistLibraryFilterFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "SUBSCRIBED"
    )

    val searchSource: StateFlow<com.unshoo.pixelmusic.data.preferences.SearchSource> = userPreferencesRepository.searchSourceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.unshoo.pixelmusic.data.preferences.SearchSource.ONLINE
    )

    val quickPicksDisplayMode: StateFlow<com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode> = userPreferencesRepository.quickPicksDisplayModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode.UNCONTAINED
    )

    fun toggleSearchSource() {
        viewModelScope.launch {
            val current = searchSource.value
            val next = if (current == com.unshoo.pixelmusic.data.preferences.SearchSource.ONLINE) {
                com.unshoo.pixelmusic.data.preferences.SearchSource.LOCAL
            } else {
                com.unshoo.pixelmusic.data.preferences.SearchSource.ONLINE
            }
            userPreferencesRepository.setSearchSource(next)
            if (searchQuery.isNotBlank()) {
                performSearch(searchQuery)
            }
        }
    }

    private val _showNoInternetDialog = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val showNoInternetDialog: SharedFlow<Unit> = _showNoInternetDialog.asSharedFlow()

    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    val albumArtPaletteStyle: StateFlow<AlbumArtPaletteStyle> = themePreferencesRepository
        .albumArtPaletteStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumArtPaletteStyle.default
        )
    /**
     * High-frequency playback position should not force global UI recomposition.
     * Keep a dedicated position flow for real-time UI elements (seek bars, lyrics timing).
     */
    val currentPlaybackPosition: StateFlow<Long> = playbackStateHolder.currentPosition
    val playbackHistory = listeningStatsTracker.playbackHistory

    // Removed: _masterAllSongs was a duplicate of libraryStateHolder.allSongs
    // All reads now delegate to libraryStateHolder.allSongs

    // Lyrics load callback for LyricsStateHolder
    private val lyricsLoadCallback = object : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = false, lyrics = lyrics)
            }
        }
    }



    private val _playlistPickerStorageFilter = MutableStateFlow(com.unshoo.pixelmusic.data.model.StorageFilter.LOCAL)
    val playlistPickerStorageFilter: StateFlow<com.unshoo.pixelmusic.data.model.StorageFilter> = _playlistPickerStorageFilter.asStateFlow()

    /**
     * Paginated songs for efficient display in LibraryScreen.
     * Uses Paging 3 for memory-efficient loading of large libraries.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val paginatedSongs: Flow<PagingData<Song>> = libraryStateHolder.songsPagingFlow
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistPickerFavoriteSongs: Flow<PagingData<Song>> = combine(
        libraryStateHolder.currentSongSortOption,
        _playlistPickerStorageFilter
    ) { sortOption, storageFilter ->
        sortOption to storageFilter
    }
        .distinctUntilChanged()
        .flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedFavoriteSongs(
                sortOption = sortOption,
                storageFilter = storageFilter
            )
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistPickerSongs: Flow<PagingData<Song>> = combine(
        libraryStateHolder.currentSongSortOption,
        _playlistPickerStorageFilter
    ) { sortOption, storageFilter ->
        sortOption to storageFilter
    }
        .distinctUntilChanged()
        .flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedSongs(
                sortOption = sortOption,
                storageFilter = storageFilter
            )
        }
        .cachedIn(viewModelScope)

    private val offlinePlaybackObserverJob = viewModelScope.launch {
        connectivityStateHolder.offlinePlaybackBlocked.collect {
            Timber.w("Received offline blocked event. Showing dialog.")
            _showNoInternetDialog.emit(Unit)
        }
    }

    private var telegramPlaybackObserversStarted = false

    private fun ensureTelegramPlaybackObserversStarted() {
        if (telegramPlaybackObserversStarted) return
        telegramPlaybackObserversStarted = true

        val telegramCacheManager = telegramCacheManagerProvider.get()
        val telegramRepository = musicRepository.telegramRepository

        viewModelScope.launch {
            launch {
                telegramCacheManager.embeddedArtUpdated.collect { updatedArtUri ->
                    refreshArtwork(updatedArtUri)
                }
            }

            launch {
                telegramRepository.artworkUpdated.collect { updatedArtUri ->
                    Timber.d("PlayerViewModel: Telegram high-quality artwork ready for $updatedArtUri")
                    refreshArtwork(updatedArtUri)
                }
            }

            launch {
                telegramRepository.downloadCompleted.collect {
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    if (currentSong != null && currentSong.contentUriString.startsWith("telegram:")) {
                        val uri = Uri.parse(currentSong.contentUriString)
                        val chatId = uri.host?.toLongOrNull()
                        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()

                        if (chatId != null && messageId != null) {
                            refreshArtwork("telegram_art://$chatId/$messageId")
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshArtwork(updatedArtUri: String) {
        val currentState = playbackStateHolder.stablePlayerState.value
        val currentSong = currentState.currentSong
        // Check if it matches, ignoring query params for comparison
        val currentUriClean = currentSong?.albumArtUriString?.substringBefore('?')
        val updatedUriClean = updatedArtUri.substringBefore('?')
        
        if (currentUriClean == updatedUriClean) {
            Timber.d("PlayerViewModel: Embedded art updated for current song, forcing refresh")
            
            // 1. Invalidate Coil cache for the BASE uri (without params)
            // This ensures next time we load it without params, it's fresh too.
            val baseUri = currentUriClean
            
            // Remove from Memory Cache
            context.imageLoader.memoryCache?.keys?.forEach { key ->
                if (key.toString().contains(baseUri)) {
                    context.imageLoader.memoryCache?.remove(key)
                }
            }
            // Remove from Disk Cache
            context.imageLoader.diskCache?.remove(baseUri)

            // 2. Extract Colors (using base URI)
            themeStateHolder.extractAndGenerateColorScheme(updatedArtUri.toUri(), updatedArtUri, isPreload = false)
            
            // 3. FORCE UI REFRESH by updating the URI with a version timestamp
            // This forces SmartImage to see a "new" model and reload.
            // We keep the quality param if it exists, or add a version param.
            val newUri = if (updatedArtUri.contains("?")) {
                "$updatedArtUri&v=${System.currentTimeMillis()}"
            } else {
                "$updatedArtUri?v=${System.currentTimeMillis()}"
            }
            
            val updatedSong = currentSong.copy(albumArtUriString = newUri)
            
            // Update State
            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(currentSong = updatedSong)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSongArtists: StateFlow<List<Artist>> = stablePlayerState
        .map { it.currentSong }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { currentSong ->
            if (currentSong == null) {
                flowOf(emptyList())
            } else {
                val idLong = currentSong.id.toLongOrNull()
                if (idLong == null) {
                    val artistsFlows = currentSong.artists.map { ref ->
                        flow {
                            val initialArtist = Artist(
                                id = ref.id,
                                name = ref.name,
                                songCount = 0,
                                imageUrl = null,
                                customImageUri = null,
                                channelId = ref.channelId
                            )
                            emit(initialArtist)
                            val resolvedUrl = artistImageRepository.getArtistImageUrl(ref.name, ref.id)
                            if (!resolvedUrl.isNullOrBlank()) {
                                emit(initialArtist.copy(imageUrl = resolvedUrl))
                            }
                        }
                    }
                    if (artistsFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(artistsFlows) { it.toList() }
                    }
                } else {
                    musicRepository.getArtistsForSong(idLong)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible: StateFlow<Boolean> = _isSheetVisible.asStateFlow()
    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()
    private val _predictiveBackSwipeEdge = MutableStateFlow<Int?>(null)
    val predictiveBackSwipeEdge: StateFlow<Int?> = _predictiveBackSwipeEdge.asStateFlow()
    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()
    private val _isCastSheetVisible = MutableStateFlow(false)
    val isCastSheetVisible: StateFlow<Boolean> = _isCastSheetVisible.asStateFlow()

    val playerContentExpansionFraction = Animatable(0f)

    private val _isMiniPlayerDismissing = MutableStateFlow(false)
    val isMiniPlayerDismissing: StateFlow<Boolean> = _isMiniPlayerDismissing.asStateFlow()

    fun setMiniPlayerDismissing(dismissing: Boolean) {
        _isMiniPlayerDismissing.value = dismissing
    }

    // AI Ecosystem: States delegated to AiStateHolder for centralized management
    val showAiPlaylistSheet: StateFlow<Boolean> = aiStateHolder.showAiPlaylistSheet
    val isGeneratingAiPlaylist: StateFlow<Boolean> = aiStateHolder.isGeneratingAiPlaylist
    val aiSuccess: StateFlow<Boolean> = aiStateHolder.aiSuccess
    val aiStatus: StateFlow<String?> = aiStateHolder.aiStatus
    val aiError: StateFlow<String?> = aiStateHolder.aiError

    // AI Metadata Generation States
    val isGeneratingAiMetadata: StateFlow<Boolean> = aiStateHolder.isGeneratingMetadata
    val aiMetadataSuccess: StateFlow<Boolean> = aiStateHolder.aiMetadataSuccess

    private val _selectedSongForInfo = MutableStateFlow<Song?>(null)
    val selectedSongForInfo: StateFlow<Song?> = _selectedSongForInfo.asStateFlow()

    // Theme & Colors - delegated to ThemeStateHolder
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentAlbumArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair
    val currentThemedAlbumArtUri: StateFlow<String?> = themeStateHolder.currentAlbumArtUri

    val playerThemePreference: StateFlow<String> = themePreferencesRepository.playerThemePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.ALBUM_ART
        )

    val playerBackgroundStyle: StateFlow<String> = themePreferencesRepository.playerBackgroundStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerBackgroundStyle.GRADIENT
        )

    val useBlurEffects: StateFlow<Boolean> = userPreferencesRepository.useBlurEffectsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val nowPlayingBlurEnabled: StateFlow<Boolean> = userPreferencesRepository.nowPlayingBlurEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val navBarBlurEnabled: StateFlow<Boolean> = userPreferencesRepository.navBarBlurEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val playerExpansionBlurEnabled: StateFlow<Boolean> = userPreferencesRepository.playerExpansionBlurEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val colorPalette: StateFlow<String> = themePreferencesRepository.colorPalettePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "SAGE"
        )



    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NavBarStyle.DEFAULT
        )

    val navBarHeightOffset: StateFlow<Int> = userPreferencesRepository.navBarHeightOffsetFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val navBarCompactMode: StateFlow<Boolean> = userPreferencesRepository.navBarCompactModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val hasActiveAiProviderApiKey: StateFlow<Boolean> = combine(
        aiPreferencesRepository.aiProvider,
        aiPreferencesRepository.geminiApiKey,
        aiPreferencesRepository.deepseekApiKey,
        aiPreferencesRepository.groqApiKey,
        aiPreferencesRepository.mistralApiKey,
        aiPreferencesRepository.nvidiaApiKey,
        aiPreferencesRepository.kimiApiKey,
        aiPreferencesRepository.glmApiKey,
        aiPreferencesRepository.openaiApiKey
    ) { values ->
        val provider = values[0]
        val gemini = values[1]
        val deepseek = values[2]
        val groq = values[3]
        val mistral = values[4]
        val nvidia = values[5]
        val kimi = values[6]
        val glm = values[7]
        val openai = values[8]
        when (provider) {
            "DEEPSEEK" -> deepseek.isNotBlank()
            "GROQ" -> groq.isNotBlank()
            "MISTRAL" -> mistral.isNotBlank()
            "NVIDIA" -> nvidia.isNotBlank()
            "KIMI" -> kimi.isNotBlank()
            "GLM" -> glm.isNotBlank()
            "OPENAI" -> openai.isNotBlank()
            else -> gemini.isNotBlank()
        }
    }.distinctUntilChanged()
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val hasGeminiApiKey: StateFlow<Boolean> = aiPreferencesRepository.geminiApiKey
        .map { it.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    val showPlayerFileInfo: StateFlow<Boolean> = userPreferencesRepository.showPlayerFileInfoFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hapticsEnabled: StateFlow<Boolean> = userPreferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Lyrics sync offset - now managed by LyricsStateHolder
    val currentSongLyricsSyncOffset: StateFlow<Int> = lyricsStateHolder.currentSongSyncOffset

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = userPreferencesRepository.lyricsSourcePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsSourcePreference.EMBEDDED_FIRST
        )

    val immersiveLyricsEnabled: StateFlow<Boolean> = userPreferencesRepository.immersiveLyricsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val immersiveLyricsTimeout: StateFlow<Long> = userPreferencesRepository.immersiveLyricsTimeoutFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4000L
        )

    private val _isImmersiveTemporarilyDisabled = MutableStateFlow(false)
    val isImmersiveTemporarilyDisabled: StateFlow<Boolean> = _isImmersiveTemporarilyDisabled.asStateFlow()

    fun setImmersiveTemporarilyDisabled(disabled: Boolean) {
        _isImmersiveTemporarilyDisabled.value = disabled
    }

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.ORIGINAL)

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        lyricsStateHolder.setSyncOffset(songId, offsetMs)
    }

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )



    private val _isInitialThemePreloadComplete = MutableStateFlow(false)

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val activeTimerDurationMinutes: StateFlow<Int?> = sleepTimerStateHolder.activeTimerDurationMinutes
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState

    private var bufferingDebounceJob: Job? = null



    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val toastEvents = _toastEvents.asSharedFlow()

    // MediaStore write-permission request (needed for metadata editing without MANAGE_EXTERNAL_STORAGE)
    private val _writePermissionRequest = MutableSharedFlow<android.content.IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val writePermissionRequest: SharedFlow<android.content.IntentSender> = _writePermissionRequest.asSharedFlow()

    // MediaStore delete-permission request (for deletion without MANAGE_EXTERNAL_STORAGE)
    private val _deletePermissionRequest = MutableSharedFlow<android.content.IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deletePermissionRequest: SharedFlow<android.content.IntentSender> = _deletePermissionRequest.asSharedFlow()

    private var pendingMetadataEdit: PendingMetadataEdit? = null
    private var pendingLyricsSave: PendingLyricsSave? = null
    private var pendingDeleteSong: Song? = null
    private var pendingDeleteCallback: ((Boolean) -> Unit)? = null
    private var lastRegisteredVideoId: String? = null
    private var pendingYoutubeHistoryVideoId: String? = null
    private var youtubePlaybackHistoryJob: Job? = null

    private val _albumNavigationRequests = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val albumNavigationRequests = _albumNavigationRequests.asSharedFlow()
    private val _artistNavigationRequests = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()
    private val _searchNavDoubleTapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchNavDoubleTapEvents = _searchNavDoubleTapEvents.asSharedFlow()
    
    // New event for scrolling to a specific index in the songs list
    private val _scrollToIndexEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndexEvent = _scrollToIndexEvent.asSharedFlow()
    
    private var albumNavigationJob: Job? = null
    private var artistNavigationJob: Job? = null
    private var fullQueuePlaybackJob: Job? = null
    private var fullQueuePlaybackToken: Long = 0L
    private var directPlaybackJob: Job? = null
    private var directPlaybackToken: Long = 0L
    private var pendingQueueSegmentsJob: Job? = null
    // BUGFIX (stale-resolution race): tracks the inner "resolve stream URL -> setMediaItems"
    // coroutine started from internalPlaySongs(). Before this field existed, that coroutine was
    // launched as an untracked, independent viewModelScope.launch - cancelling directPlaybackJob
    // (e.g. on the next tap) did NOT cancel it, so a slow tap A could resolve after a faster tap B
    // and overwrite B's queue/player state with A's.
    private var directPlaybackApplyJob: Job? = null
    private var directPlaybackRecommendationsJob: Job? = null

    fun requestLocateCurrentSong() {
        val currentSong = stablePlayerState.value.currentSong ?: return

        viewModelScope.launch {
            try {
                val sortOption = playerUiState.value.currentSongSortOption
                
                // Logic must match effectiveStorageFilter in LibraryStateHolder
                val baseFilter = playerUiState.value.currentStorageFilter
                val hideLocal = playerUiState.value.hideLocalMedia
                val isOnline = connectivityStateHolder.isOnline.value
                val isLocalMode = userPreferencesRepository.localModeEnabledFlow.first()
                val storageFilter = if (isLocalMode || !isOnline) {
                    com.unshoo.pixelmusic.data.model.StorageFilter.LOCAL
                } else if (hideLocal) {
                    com.unshoo.pixelmusic.data.model.StorageFilter.ONLINE
                } else {
                    baseFilter
                }

                // A 20k-song library means both loading the ordered IDs and finding the target
                // are linear work. Keep that work off the UI dispatcher so tapping Locate never
                // competes with Compose layout or player callbacks.
                val index = withContext(Dispatchers.Default) {
                    val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)
                    
                    val youtubeVideoId = currentSong.youtubeId
                        ?: currentSong.id.removePrefix("youtube_").takeIf { currentSong.id.startsWith("youtube_") }
                        ?: currentSong.contentUriString.removePrefix("youtube://").takeIf { currentSong.contentUriString.startsWith("youtube://") }

                    val hashedYoutubeId = youtubeVideoId?.let { vid ->
                        -(15_000_000_000_000L + vid.hashCode().toLong().let { if (it < 0L) -it else it })
                    }

                    val unifiedId = currentSong.id.toLongOrNull()
                        ?: hashedYoutubeId
                        ?: currentSong.contentUriString.takeIf { it.isNotBlank() }?.let { musicRepository.getSongIdByContentUri(it) }

                    if (unifiedId != null) {
                        val foundIndex = sortedIds.indexOf(unifiedId)
                        if (foundIndex != -1) return@withContext foundIndex
                    }

                    // Metadata fallback if ID mapping differed (e.g. cloud streams)
                    if (currentSong.title.isNotBlank()) {
                        val cachedSongs = libraryStateHolder.allSongs.value
                        val matched = cachedSongs.indexOfFirst {
                            it.id == currentSong.id ||
                            (it.title.equals(currentSong.title, ignoreCase = true) &&
                             it.artist.equals(currentSong.artist, ignoreCase = true))
                        }
                        if (matched != -1) {
                            val matchedSong = cachedSongs[matched]
                            val matchedUnifiedId = matchedSong.id.toLongOrNull()
                                ?: matchedSong.youtubeId?.let { -(15_000_000_000_000L + kotlin.math.abs(it.hashCode().toLong())) }
                            if (matchedUnifiedId != null) {
                                val idx = sortedIds.indexOf(matchedUnifiedId)
                                if (idx != -1) return@withContext idx
                            }
                            return@withContext matched
                        }
                    }

                    -1
                }

                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    sendToast(context.getString(R.string.player_song_not_found_in_list))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate current song")
                sendToast(context.getString(R.string.player_could_not_locate_song))
            }
        }
    }

    fun requestLocateCurrentFavoriteSong() {
        val currentSong = stablePlayerState.value.currentSong ?: return

        viewModelScope.launch {
            try {
                val sortOption = playerUiState.value.currentFavoriteSortOption
                val baseFilter = playerUiState.value.currentStorageFilter
                val hideLocal = playerUiState.value.hideLocalMedia
                val storageFilter = if (hideLocal) {
                    com.unshoo.pixelmusic.data.model.StorageFilter.ONLINE
                } else {
                    baseFilter
                }

                val index = withContext(Dispatchers.Default) {
                    val sortedIds = musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)
                    
                    val youtubeVideoId = currentSong.youtubeId
                        ?: currentSong.id.removePrefix("youtube_").takeIf { currentSong.id.startsWith("youtube_") }
                        ?: currentSong.contentUriString.removePrefix("youtube://").takeIf { currentSong.contentUriString.startsWith("youtube://") }

                    val hashedYoutubeId = youtubeVideoId?.let { vid ->
                        -(15_000_000_000_000L + kotlin.math.abs(vid.hashCode().toLong()))
                    }

                    val unifiedId = currentSong.id.toLongOrNull()
                        ?: hashedYoutubeId
                        ?: currentSong.contentUriString.takeIf { it.isNotBlank() }?.let { musicRepository.getSongIdByContentUri(it) }

                    if (unifiedId != null) {
                        val foundIndex = sortedIds.indexOf(unifiedId)
                        if (foundIndex != -1) return@withContext foundIndex
                    }

                    // Metadata fallback
                    if (currentSong.title.isNotBlank()) {
                        val cachedSongs = libraryStateHolder.allSongs.value
                        val matched = cachedSongs.indexOfFirst {
                            it.id == currentSong.id ||
                            (it.title.equals(currentSong.title, ignoreCase = true) &&
                             it.artist.equals(currentSong.artist, ignoreCase = true))
                        }
                        if (matched != -1) {
                            val matchedSong = cachedSongs[matched]
                            val matchedUnifiedId = matchedSong.id.toLongOrNull()
                                ?: matchedSong.youtubeId?.let { -(15_000_000_000_000L + kotlin.math.abs(it.hashCode().toLong())) }
                            if (matchedUnifiedId != null) {
                                val idx = sortedIds.indexOf(matchedUnifiedId)
                                if (idx != -1) return@withContext idx
                            }
                        }
                    }

                    -1
                }

                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    sendToast(context.getString(R.string.player_song_not_found_in_list))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate current favorite song")
                sendToast(context.getString(R.string.player_could_not_locate_song))
            }
        }
    }

    fun showAndPlaySongFromLibrary(
        song: Song,
        queueName: String = "Library",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build full library queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentSongSortOption
            val storageFilter = playerUiState.value.currentStorageFilter
            musicRepository.getSongIdsSorted(sortOption, storageFilter)
        }
    }

    fun showAndPlaySongFromFavorites(
        song: Song,
        queueName: String = "Liked Songs",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build favorites queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentFavoriteSortOption
            val storageFilter = playerUiState.value.currentStorageFilter
            musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)
        }
    }

    suspend fun getSongsForCurrentLibrarySelection(): List<Song> = withContext(Dispatchers.IO) {
        val sortOption = playerUiState.value.currentSongSortOption
        val storageFilter = playerUiState.value.currentStorageFilter
        val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)
        resolvePlaybackQueueFromSortedIds(sortedIds)
    }

    suspend fun getFavoriteSongsForCurrentSelection(): List<Song> = withContext(Dispatchers.IO) {
        val sortOption = playerUiState.value.currentFavoriteSortOption
        val storageFilter = playerUiState.value.currentStorageFilter
        val sortedIds = musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)
        resolvePlaybackQueueFromSortedIds(sortedIds)
    }


    private fun launchLatestFullQueuePlayback(
        song: Song,
        queueName: String,
        isVoluntaryPlay: Boolean,
        failureMessage: String,
        sortedIdsProvider: suspend () -> List<Long>
    ) {
        cancelPendingFullQueuePlayback()
        val requestToken = fullQueuePlaybackToken

        fullQueuePlaybackJob = viewModelScope.launch {
            try {
                // Keep ID mapping and queue materialisation off main. This path is exercised for
                // every tap in the Songs tab, and processing 5k-20k IDs on main causes visible
                // input and animation stalls before the player is even prepared.
                val fullQueue = withContext(Dispatchers.Default) {
                    val sortedIds = sortedIdsProvider()
                    throwIfFullQueuePlaybackRequestIsStale(requestToken)
                    resolvePlaybackQueueFromSortedIds(sortedIds)
                }
                throwIfFullQueuePlaybackRequestIsStale(requestToken)

                showAndPlaySong(
                    song = song,
                    contextSongs = fullQueue.ifEmpty { listOf(song) },
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestToken != fullQueuePlaybackToken) {
                    return@launch
                }

                Timber.e(error, failureMessage, song.id)
                val fallbackQueue = libraryStateHolder.allSongs.value.takeIf { songs ->
                    songs.isNotEmpty() && songs.any { it.id == song.id }
                } ?: listOf(song)
                showAndPlaySong(
                    song = song,
                    contextSongs = fallbackQueue,
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            }
        }
    }

    private fun cancelPendingFullQueuePlayback() {
        fullQueuePlaybackToken += 1L
        fullQueuePlaybackJob?.cancel()
        fullQueuePlaybackJob = null
        cancelPendingDirectPlayback()
    }

    private fun throwIfFullQueuePlaybackRequestIsStale(requestToken: Long) {
        if (requestToken != fullQueuePlaybackToken) {
            throw CancellationException("Stale full-queue playback request")
        }
    }

    private fun beginDirectPlaybackRequest(): Long {
        directPlaybackToken += 1L
        directPlaybackJob?.cancel()
        directPlaybackJob = null
        directPlaybackApplyJob?.cancel()
        directPlaybackApplyJob = null
        directPlaybackRecommendationsJob?.cancel()
        directPlaybackRecommendationsJob = null
        pendingQueueSegmentsJob?.cancel()
        pendingQueueSegmentsJob = null
        return directPlaybackToken
    }

    private fun cancelPendingDirectPlayback() {
        cancelPendingDirectPlaybackBuild()
        pendingQueueSegmentsJob?.cancel()
        pendingQueueSegmentsJob = null
    }

    private fun cancelPendingDirectPlaybackBuild() {
        directPlaybackToken += 1L
        directPlaybackJob?.cancel()
        directPlaybackJob = null
        directPlaybackApplyJob?.cancel()
        directPlaybackApplyJob = null
        directPlaybackRecommendationsJob?.cancel()
        directPlaybackRecommendationsJob = null
    }

    private fun throwIfDirectPlaybackRequestIsStale(requestToken: Long) {
        if (requestToken != directPlaybackToken) {
            throw CancellationException("Stale direct playback request")
        }
    }

    // BUGFIX: non-throwing variant safe to call from Dispatchers.Main.immediate right before
    // touching the player, inside the previously-untracked resolve/apply coroutine.
    private fun isDirectPlaybackRequestStale(requestToken: Long): Boolean =
        requestToken != directPlaybackToken

    private suspend fun resolvePlaybackQueueFromSortedIds(sortedIds: List<Long>): List<Song> {
        if (sortedIds.isEmpty()) return emptyList()

        val cachedSongsById = libraryStateHolder.allSongsById.value
        val (orderedIds, missingIds, cachedQueue) = withContext(Dispatchers.Default) {
            val orderedIds = ArrayList<String>(sortedIds.size)
            val missingIds = ArrayList<String>()
            val cachedQueue = ArrayList<Song>(sortedIds.size)

            sortedIds.forEach { songId ->
                val id = songId.toString()
                orderedIds.add(id)
                val cachedSong = cachedSongsById[id]
                if (cachedSong != null) {
                    cachedQueue.add(cachedSong)
                } else {
                    missingIds.add(id)
                }
            }
            Triple(orderedIds, missingIds, cachedQueue)
        }

        if (missingIds.isEmpty()) {
            return cachedQueue
        }

        val missingSongsById = getSongsByIdsChunked(missingIds).associateBy { it.id }
        return withContext(Dispatchers.Default) {
            val finalQueue = ArrayList<Song>(orderedIds.size)
            orderedIds.forEach { songId ->
                val cachedSong = cachedSongsById[songId]
                if (cachedSong != null) {
                    finalQueue.add(cachedSong)
                } else {
                    missingSongsById[songId]?.let(finalQueue::add)
                }
            }
            finalQueue
        }
    }

    private suspend fun getSongsByIdsChunked(songIds: List<String>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        if (songIds.size <= SONG_ID_QUERY_CHUNK_SIZE) {
            return musicRepository.getSongsByIdsOnce(songIds)
        }

        return withContext(Dispatchers.IO) {
            buildList(songIds.size) {
                songIds.chunked(SONG_ID_QUERY_CHUNK_SIZE).forEach { chunk ->
                    addAll(musicRepository.getSongsByIdsOnce(chunk))
                }
            }
        }
    }

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    /** Pre-mapped so UI composables don't create a new Flow on every recomposition. */
    val selectedRouteName: StateFlow<String?> = castStateHolder.selectedRoute
        .map { it?.name }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    fun startCastDiscovery() {
        castStateHolder.startDiscovery()
    }

    fun stopCastDiscovery() {
        castStateHolder.stopDiscovery()
    }

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = connectivityStateHolder.bluetoothAudioDeviceStates
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices



    // Connectivity is now managed by ConnectivityStateHolder

    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager? get() = castStateHolder.sessionManager

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()


    @Inject
    lateinit var mediaMapper: com.unshoo.pixelmusic.data.media.MediaMapper

    @Inject
    lateinit var imageCacheManager: com.unshoo.pixelmusic.data.media.ImageCacheManager

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, lyricsLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(viewModelScope)
        themeStateHolder.initialize(viewModelScope)

        // BUGFIX (lag — first-frame jank): the cold-start work below used to
        // run as soon as the ViewModel was constructed (which happens the
        // moment `MainActivity.setContent { … by viewModels<PlayerViewModel>() }`
        // runs), i.e. before the first frame is on screen. The DataStore
        // read, the cloud-URI pre-fetch, and the palette quantization all
        // competed with the first frame; the runtime log shows a 51-frame
        // Choreographer skip during the very first interaction. We now
        // suspend on AppReadinessSignal so the work runs after the first
        // frame is committed (the signal is raised by MainActivity at
        // line ~296 right after the contentVisible transition starts).
        viewModelScope.launch(Dispatchers.IO) {
            AppReadinessSignal.awaitReady()
            // On cold start, the MediaController connects asynchronously, leaving stablePlayerState.currentSong
            // and currentPlaybackQueue empty until that happens. Pre-load the snapshot from DataStore immediately
            // so the UI and miniplayer populate with the restored queue on the very first frame.
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            if (snapshot.items.isNotEmpty()) {
                val allSongsById = libraryStateHolder.allSongsById.value
                val preloadedQueue = snapshot.items.mapNotNull { itemSnapshot ->
                    allSongsById[itemSnapshot.mediaId] ?: Song(
                        id = itemSnapshot.mediaId,
                        title = itemSnapshot.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_song_title),
                        artist = itemSnapshot.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist),
                        artistId = -1L,
                        album = itemSnapshot.albumTitle?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_album),
                        albumId = -1L,
                        path = "",
                        contentUriString = itemSnapshot.uri,
                        albumArtUriString = itemSnapshot.artworkUri,
                        duration = itemSnapshot.durationMs ?: 0L,
                        dateAdded = System.currentTimeMillis(),
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null,
                        youtubeId = if (itemSnapshot.mediaId.startsWith("youtube_")) {
                            itemSnapshot.mediaId.removePrefix("youtube_")
                        } else if (itemSnapshot.uri.startsWith("youtube://")) {
                            itemSnapshot.uri.removePrefix("youtube://")
                        } else if (itemSnapshot.mediaId.length == 11 && !itemSnapshot.mediaId.startsWith("external:")) {
                            itemSnapshot.mediaId
                        } else {
                            null
                        }
                    )
                }
                if (preloadedQueue.isNotEmpty()) {
                    _playerUiState.update { currentState ->
                        if (currentState.currentPlaybackQueue.isEmpty()) {
                            currentState.copy(currentPlaybackQueue = preloadedQueue.toPlaybackQueue())
                        } else {
                            currentState
                        }
                    }
                    _isSheetVisible.value = true
                }
            }

            val currentItem = if (snapshot.currentMediaId != null) {
                snapshot.items.find { it.mediaId == snapshot.currentMediaId }
            } else {
                snapshot.items.getOrNull(snapshot.currentIndex)
            } ?: return@launch

            if (playbackStateHolder.stablePlayerState.value.currentSong == null) {
                val currentSongResolved = _playerUiState.value.currentPlaybackQueue.find { it.id == currentItem.mediaId }
                    ?: Song(
                        id = currentItem.mediaId,
                        title = currentItem.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_song_title),
                        artist = currentItem.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist),
                        artistId = -1L,
                        album = currentItem.albumTitle?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_album),
                        albumId = -1L,
                        path = "",
                        contentUriString = currentItem.uri,
                        albumArtUriString = currentItem.artworkUri,
                        duration = currentItem.durationMs ?: 0L,
                        dateAdded = System.currentTimeMillis(),
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null,
                        youtubeId = if (currentItem.mediaId.startsWith("youtube_")) {
                            currentItem.mediaId.removePrefix("youtube_")
                        } else if (currentItem.uri.startsWith("youtube://")) {
                            currentItem.uri.removePrefix("youtube://")
                        } else if (currentItem.mediaId.length == 11 && !currentItem.mediaId.startsWith("external:")) {
                            currentItem.mediaId
                        } else {
                            null
                        }
                    )
                playbackStateHolder.updateStablePlayerState { state ->
                    if (state.currentSong == null) {
                        state.copy(
                            currentSong = currentSongResolved,
                            totalDuration = currentItem.durationMs ?: 0L
                        )
                    } else {
                        state
                    }
                }
                playbackStateHolder.setCurrentPosition(snapshot.currentPositionMs.coerceAtLeast(0L))
            }

            launch {
                AppReadinessSignal.awaitReady()
                val uriStr = currentItem.uri
                if (uriStr.isNotBlank() && (
                    uriStr.startsWith("youtube://") ||
                    uriStr.startsWith("telegram:") ||
                    uriStr.startsWith("gdrive:")
                )) {
                    runCatching { dualPlayerEngine.resolveCloudUri(uriStr.toUri()) }
                }

                val artworkUri = currentItem.artworkUri?.takeIf { it.isNotBlank() }
                if (artworkUri != null) {
                    themeStateHolder.extractAndGenerateColorScheme(
                        albumArtUriAsUri = artworkUri.toUri(),
                        currentSongUriString = artworkUri,
                        isPreload = false
                    )
                }
            }
        }

        viewModelScope.launch {
            libraryStateHolder.allSongsById
                .collect { songsMap: Map<String, Song> ->
                    if (songsMap.isNotEmpty() && _playerUiState.value.currentPlaybackQueue.isNotEmpty()) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val hasUnresolved = currentQueue.any { it.artistId == -1L && songsMap.containsKey(it.id) }
                        if (hasUnresolved) {
                            val updatedQueue: List<Song> = currentQueue.map { song ->
                                songsMap[song.id] ?: song
                            }
                            _playerUiState.update { it.copy(currentPlaybackQueue = updatedQueue.toPlaybackQueue()) }
                        }
                    }
                }
        }

        stablePlayerState
            .map { it.currentSong?.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
            .distinctUntilChanged()
            .onEach { artworkUri ->
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = artworkUri?.toUri(),
                    currentSongUriString = artworkUri,
                    isPreload = false
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            lyricsStateHolder.songUpdates.collect { update: Pair<com.unshoo.pixelmusic.data.model.Song, com.unshoo.pixelmusic.data.model.Lyrics?> ->
                val song = update.first
                val lyrics = update.second
                // Check if this update is relevant to the currently playing song OR the selected song
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    // MERGE FIX: if song comes back empty (e.g. from reset), preserve current metadata
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    val safeSong = if (song.title.isEmpty() && currentSong != null) {
                        currentSong.copy(lyrics = "")
                    } else {
                        song
                    }
                    updateSongInStates(safeSong, lyrics, isLoadingLyrics = false)
                }
                if (_selectedSongForInfo.value?.id == song.id) {
                    val currentSelected = _selectedSongForInfo.value
                    if (song.title.isEmpty() && currentSelected != null) {
                        _selectedSongForInfo.value = currentSelected.copy(lyrics = "")
                    } else {
                        _selectedSongForInfo.value = song
                    }
                }
            }
        }

        lyricsStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId.isNullOrBlank()) flowOf(null)
                    else musicRepository.getSong(songId)
                }
                .collect { repositorySong ->
                    val currentState = playbackStateHolder.stablePlayerState.value
                    val currentSong = currentState.currentSong ?: return@collect
                    if (repositorySong == null || repositorySong.id != currentSong.id) {
                        return@collect
                    }

                    val hydratedSong = currentSong.withRepositoryHydration(repositorySong)
                    val persistedLyrics = parsePersistedLyrics(hydratedSong.lyrics)
                    val shouldApplyPersistedLyrics = currentState.lyrics == null && persistedLyrics != null
                    val shouldRefreshSong = hydratedSong != currentSong
                    val shouldReloadLyrics =
                        !shouldApplyPersistedLyrics &&
                            currentState.lyrics == null &&
                            hydratedSong.improvesLyricsLookupComparedTo(currentSong)

                    if (shouldApplyPersistedLyrics || shouldReloadLyrics) {
                        lyricsStateHolder.cancelLoading()
                    }

                    if (shouldRefreshSong || shouldApplyPersistedLyrics) {
                        updateSongInStates(
                            updatedSong = hydratedSong,
                            newLyrics = if (shouldApplyPersistedLyrics) persistedLyrics else null,
                            isLoadingLyrics = if (shouldApplyPersistedLyrics) false else null
                        )

                        if (_selectedSongForInfo.value?.id == hydratedSong.id) {
                            _selectedSongForInfo.value = hydratedSong
                        }
                    }

                    if (shouldReloadLyrics) {
                        lyricsStateHolder.loadLyricsForSong(hydratedSong, lyricsSourcePreference.value)
                    }
                }
        }

        viewModelScope.launch {
            combine(
                playerUiState.map { it.currentPlaybackQueue }.distinctUntilChanged(),
                stablePlayerState.map { it.currentMediaItemIndex }.distinctUntilChanged(),
                lyricsSourcePreference
            ) { queue, index, sourcePref ->
                Triple(queue, index, sourcePref)
            }
            .collect { (queue, index, sourcePref) ->
                if (index in queue.indices) {
                    val nextSongs = mutableListOf<Song>()
                    val qSize = queue.size
                    if (qSize > 1) {
                        val nextIndex1 = (index + 1) % qSize
                        if (nextIndex1 != index) {
                            nextSongs.add(queue[nextIndex1])
                        }
                        if (qSize > 2) {
                            val nextIndex2 = (index + 2) % qSize
                            if (nextIndex2 != index && nextIndex2 != nextIndex1) {
                                nextSongs.add(queue[nextIndex2])
                            }
                        }
                    }
                    if (nextSongs.isNotEmpty()) {
                        preCacheLyricsForSongs(nextSongs, sourcePref)
                        nextSongs.forEach { song ->
                            val artUri = song.albumArtUriString
                            if (!artUri.isNullOrBlank()) {
                                val req = ImageRequest.Builder(context)
                                    .data(artUri)
                                    .size(512, 512)
                                    .build()
                                context.imageLoader.enqueue(req)
                            }
                        }
                    }
                }
            }
        }
    }

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

    fun sendToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }

    fun onSearchNavIconDoubleTapped() {
        _searchNavDoubleTapEvents.tryEmit(Unit)
    }


    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
                }
            } else {
                listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED"))

    private val _loadedTabs = MutableStateFlow(emptySet<String>())
    private var lastBlockedDirectories: Set<String>? = null

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> SortOption.SONGS
                    LibraryTabId.ALBUMS -> SortOption.ALBUMS
                    LibraryTabId.ARTISTS -> SortOption.ARTISTS
                    LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                    LibraryTabId.FOLDERS -> SortOption.FOLDERS
                    LibraryTabId.LIKED -> SortOption.LIKED
                }
            } finally {
                Trace.endSection()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.SONGS
        )

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

    // Public read-only access to all songs (using _masterAllSongs declared at class level)
    // Library State - delegated to LibraryStateHolder
    val allSongsFlow: StateFlow<ImmutableList<Song>> = libraryStateHolder.allSongs

    fun findSongById(id: String): Song? {
        val map = libraryStateHolder.allSongsById.value
        map[id]?.let { return it }
        val cleanId = id.removePrefix("youtube_")
        map[cleanId]?.let { return it }
        map["youtube_$id"]?.let { return it }
        return null
    }

    // Genres StateFlow - delegated to LibraryStateHolder
    val genres: StateFlow<ImmutableList<Genre>> = libraryStateHolder.genres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val paletteRegenerationTargets: StateFlow<List<Song>> = musicRepository.getDistinctAlbumArtSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = musicRepository.getHomeMixPreviewSongs(
        limit = HOME_MIX_PREVIEW_LIMIT
    ).map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val songCountFlow: StateFlow<Int> = musicRepository.getSongCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val hasCloudSongsFlow: StateFlow<Boolean?> = musicRepository.getCloudSongCountFlow()
        .map<Int, Boolean?> { it > 0 }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val albumsFlow: StateFlow<ImmutableList<Album>> = libraryStateHolder.albums
    val artistsFlow: StateFlow<ImmutableList<Artist>> = libraryStateHolder.artists

    var searchQuery by mutableStateOf("")
        private set

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    private var mediaController: MediaController? = null
    private var mediaControllerPlaybackListener: Player.Listener? = null
    private val _isMediaControllerReady = MutableStateFlow(false)
    val isMediaControllerReady: StateFlow<Boolean> = _isMediaControllerReady.asStateFlow()
    // SessionToken injected via constructor
    private val mediaControllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                viewModelScope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

        /**
         * Called when the MediaController is disconnected from the MediaSession (e.g. the
         * MusicService was killed by the OS after a long uptime). Without this handler the
         * ViewModel would keep a stale, dead controller reference and silently drop every
         * playback command — making skip buttons and gestures appear frozen.
         */
        override fun onDisconnected(controller: MediaController) {
            runOnMainImmediate {
                // A controller that we intentionally replaced may deliver onDisconnected after a
                // newer controller is already active. Never let that stale callback tear down the
                // new connection or start a reconnect loop.
                if (controller !== mediaController) {
                    Log.d("PlayerViewModel", "Ignoring disconnect from superseded MediaController")
                    return@runOnMainImmediate
                }
                Log.w("PlayerViewModel", "Active MediaController disconnected; reconnecting")
                mediaControllerPlaybackListener?.let { listener ->
                    runCatching { controller.removeListener(listener) }
                }
                mediaController = null
                playbackStateHolder.clearMediaController(controller)
                _isMediaControllerReady.value = false
                scheduleMediaControllerReconnect()
            }
        }
    }
    private var activeMediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaControllerConnectionGeneration = 0L
    private var mediaControllerReconnectJob: Job? = null
    private var mediaControllerHealthJob: Job? = null
    private val pendingControllerCommands = ArrayDeque<() -> Unit>()
    private val maxPendingControllerCommands = 8
    private val mediaControllerHealthIntervalMs = 2_000L
    private var pendingRepeatMode: Int? = null

    private var pendingPlaybackAction: (() -> Unit)? = null
    private var metadataProbeJob: Job? = null
    private var metadataProbeMediaId: String? = null

    private val _playbackAudioMetadata = MutableStateFlow(PlaybackAudioMetadata())
    val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata> = _playbackAudioMetadata.asStateFlow()
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = dualPlayerEngine.activeDecoderInfo
    val hiFiModeEnabled: StateFlow<Boolean> = dualPlayerEngine.hiFiModeEnabledState

    init {
        // The resolver publishes (and later back-fills the measured bitrate of) streams it OFFERS.
        // Re-reconcile when that concerns the current track so the enrichment lands as soon as it
        // exists. Reconciliation only enriches a format the player has confirmed, so an offer that
        // arrives before the player switches streams changes nothing the user sees.
        viewModelScope.launch {
            com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.lastResolved.collect { offered ->
                if (offered == null || isRemoteSessionControllingPlayback()) return@collect
                val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                val controller = mediaController
                val currentId = controller?.currentMediaItem?.mediaId ?: currentSong?.id
                val cleanCurrentId = currentId?.removePrefix("youtube_")
                val offeredCleanId = offered.mediaId.removePrefix("youtube_")
                if (cleanCurrentId != null && (cleanCurrentId == offeredCleanId || currentId == offered.mediaId)) {
                    if (offered.source == com.unshoo.pixelmusic.data.lossless.constants.AudioSourceType.MONOCHROME) {
                        _playbackAudioMetadata.value = monochromeMetadata(currentId, offered.bitrate, offered.sampleRate, offered.bitDepth)
                    } else {
                        val tag = when {
                            offered.isHiRes -> "HI-RES LOSSLESS"
                            offered.isLossless -> "LOSSLESS"
                            else -> null
                        }
                        _playbackAudioMetadata.value = PlaybackAudioMetadata(
                            mediaId = currentId,
                            mimeType = offered.mimeType,
                            bitrate = offered.bitrate.takeIf { it > 0 } ?: 1411_200,
                            sampleRate = offered.sampleRate ?: 44100,
                            bitDepth = offered.bitDepth ?: 16,
                            formatTag = tag,
                            sourceName = offered.source.name,
                            isConfirmed = true
                        )
                    }
                    if (controller != null && controller.isConnected) {
                        refreshPlaybackAudioMetadata(controller)
                    }
                }
            }
        }
    }

    val localModeEnabled: StateFlow<Boolean> = userPreferencesRepository.localModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val favoriteSongIds: StateFlow<Set<String>> = combine(
        musicRepository.getFavoriteSongIdsFlow(),
        userPreferencesRepository.localModeEnabledFlow
    ) { ids, localMode ->
        if (localMode) {
            withContext(Dispatchers.IO) {
                val longIds = ids.mapNotNull { it.toLongOrNull() }
                if (longIds.isEmpty()) return@withContext emptySet<String>()
                val songs = musicDao.getSongsByIdsListSimple(longIds)
                val validIds = HashSet<String>()
                songs.forEach { entity ->
                    val s = entity.toSong()
                    if (s.isLocal || com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, s)) {
                        validIds.add(s.id)
                        val ytId = s.youtubeId
                        if (!ytId.isNullOrBlank()) {
                            validIds.add(ytId)
                            validIds.add("youtube_$ytId")
                        }
                    }
                }
                validIds
            }
        } else {
            ids
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoQueueEnabled: StateFlow<Boolean> = youtubeDatastoreRepository.settings
        .map { it.autoQueueEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged(),
        favoriteSongIds
    ) { songId, ids ->
        songId?.let { ids.contains(it) } ?: false
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---------------------------------------------------------------------------
    // FullPlayerSlice — consolidates 11 independent flows into ONE subscription.
    // Previously FullPlayerContent had ~13 separate collectAsStateWithLifecycle()
    // calls. Each emission from any of them caused a recompose of the entire 2k-line
    // composable. Now a single collect + distinctUntilChanged batches all settings.
    // ---------------------------------------------------------------------------
    data class FullPlayerSlice(
        val currentSongArtists: List<Artist> = emptyList(),
        val lyricsSyncOffset: Int = 0,
        val albumArtQuality: AlbumArtQuality = AlbumArtQuality.ORIGINAL,
        val audioMetadata: PlaybackAudioMetadata = PlaybackAudioMetadata(),
        val showPlayerFileInfo: Boolean = true,
        val immersiveLyricsEnabled: Boolean = false,
        val immersiveLyricsTimeout: Long = 4000L,
        val isImmersiveTemporarilyDisabled: Boolean = false,
        val isRemotePlaybackActive: Boolean = false,
        val selectedRouteName: String? = null,
        val isBluetoothEnabled: Boolean = false,
        val bluetoothName: String? = null
    )

    // Intermediate combine #1: 5 settings flows
    private val fullPlayerSlicePart1 = combine(
        currentSongArtists,
        currentSongLyricsSyncOffset,
        albumArtQuality,
        playbackAudioMetadata,
        showPlayerFileInfo
    ) { artists: List<Artist>, syncOffset: Int, artQuality: AlbumArtQuality,
        audioMeta: PlaybackAudioMetadata, showFileInfo: Boolean ->
        FullPlayerSlicePart1(artists, syncOffset, artQuality, audioMeta, showFileInfo)
    }

    private data class BluetoothSlice(val enabled: Boolean, val name: String?)

    private val bluetoothSlice = combine(isBluetoothEnabled, bluetoothName) { bt, btName ->
        BluetoothSlice(bt, btName)
    }

    // Intermediate combine #2: remaining flows (≤5 for Kotlin type inference)
    private val fullPlayerSlicePart2 = combine(
        immersiveLyricsEnabled,
        immersiveLyricsTimeout,
        isImmersiveTemporarilyDisabled,
        isRemotePlaybackActive,
        combine(selectedRouteName, bluetoothSlice) { route, bt -> route to bt }
    ) { immersive: Boolean, immersiveTimeout: Long, immersiveDisabled: Boolean,
        remotePb: Boolean, routeAndBt: Pair<String?, BluetoothSlice> ->
        val (routeName, bt) = routeAndBt
        FullPlayerSlicePart2(immersive, immersiveTimeout, immersiveDisabled, remotePb, routeName, bt.enabled, bt.name)
    }

    private data class FullPlayerSlicePart1(
        val currentSongArtists: List<Artist>,
        val lyricsSyncOffset: Int,
        val albumArtQuality: AlbumArtQuality,
        val audioMetadata: PlaybackAudioMetadata,
        val showPlayerFileInfo: Boolean
    )

    private data class FullPlayerSlicePart2(
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val isImmersiveTemporarilyDisabled: Boolean,
        val isRemotePlaybackActive: Boolean,
        val selectedRouteName: String?,
        val isBluetoothEnabled: Boolean,
        val bluetoothName: String?
    )

    val fullPlayerSlice: StateFlow<FullPlayerSlice> = combine(
        fullPlayerSlicePart1,
        fullPlayerSlicePart2
    ) { p1, p2 ->
        FullPlayerSlice(
            currentSongArtists = p1.currentSongArtists,
            lyricsSyncOffset = p1.lyricsSyncOffset,
            albumArtQuality = p1.albumArtQuality,
            audioMetadata = p1.audioMetadata,
            showPlayerFileInfo = p1.showPlayerFileInfo,
            immersiveLyricsEnabled = p2.immersiveLyricsEnabled,
            immersiveLyricsTimeout = p2.immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = p2.isImmersiveTemporarilyDisabled,
            isRemotePlaybackActive = p2.isRemotePlaybackActive,
            selectedRouteName = p2.selectedRouteName,
            isBluetoothEnabled = p2.isBluetoothEnabled,
            bluetoothName = p2.bluetoothName
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FullPlayerSlice())

    // ---------------------------------------------------------------------------
    // PlayerConfigSlice — consolidates 7 infrequently-changing preference flows
    // into ONE subscription. Previously the player sheet had 7 separate
    // collectAsStateWithLifecycle() calls for config values, each causing a full
    // sheet recomposition when any preference changed.
    // ---------------------------------------------------------------------------
    data class PlayerConfigSlice(
        val navBarCornerRadius: Int = 32,
        val navBarStyle: String = NavBarStyle.DEFAULT,
        val carouselStyle: String = CarouselStyle.NO_PEEK,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
        val tapBackgroundClosesPlayer: Boolean = false,
        val useSmoothCorners: Boolean = true,
        val playerThemePreference: String = ThemePreference.ALBUM_ART,
        val playerBackgroundStyle: String = PlayerBackgroundStyle.GRADIENT,
        val useBlurEffects: Boolean = true,
        val nowPlayingBlurEnabled: Boolean = true
    )

    private val playerConfigSlicePart1 = combine(
        navBarCornerRadius,
        navBarStyle,
        carouselStyle,
        fullPlayerLoadingTweaks,
        tapBackgroundClosesPlayer
    ) { radius, style, carousel, tweaks, tapClose ->
        PlayerConfigSlicePart1(radius, style, carousel, tweaks, tapClose)
    }

    private data class PlayerConfigSlicePart1(
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val carouselStyle: String,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
        val tapBackgroundClosesPlayer: Boolean
    )

    val playerConfigSlice: StateFlow<PlayerConfigSlice> = combine(
        playerConfigSlicePart1,
        useSmoothCorners,
        playerThemePreference,
        combine(playerBackgroundStyle, useBlurEffects, nowPlayingBlurEnabled) { bg, blur, npBlur -> Triple(bg, blur, npBlur) }
    ) { p1, smoothCorners, themePref, blurConfig ->
        PlayerConfigSlice(
            navBarCornerRadius = p1.navBarCornerRadius,
            navBarStyle = p1.navBarStyle,
            carouselStyle = p1.carouselStyle,
            fullPlayerLoadingTweaks = p1.fullPlayerLoadingTweaks,
            tapBackgroundClosesPlayer = p1.tapBackgroundClosesPlayer,
            useSmoothCorners = smoothCorners,
            playerThemePreference = themePref,
            playerBackgroundStyle = blurConfig.first,
            useBlurEffects = blurConfig.second,
            nowPlayingBlurEnabled = blurConfig.third
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerConfigSlice())

    // Library State - delegated to LibraryStateHolder
    // Favorites now use paginated flow from LibraryStateHolder (DB-level sort & filter)
    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.dailyMixSongs
    val yourMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.yourMixSongs

    fun removeFromDailyMix(songId: String) {
        dailyMixStateHolder.removeFromDailyMix(songId)
    }

    /**
     * Observes a song by ID from Room DB, combined with the latest favorite status.
     * Uses direct Room query instead of scanning the full in-memory list.
     */
    fun observeSong(songId: String?): Flow<Song?> {
        if (songId == null) return flowOf(null)
        return combine(
            musicRepository.getSong(songId),
            favoriteSongIds
        ) { song, favorites ->
            song?.copy(isFavorite = favorites.contains(songId))
        }.distinctUntilChanged()
    }



    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    fun shuffleAllSongs(queueName: String = "All Songs (Shuffled)") {
        
        // Load random songs from DB instead of materializing the entire library
        viewModelScope.launch {
            val randomSongs = musicRepository.getRandomSongs(limit = 500)
            if (randomSongs.isNotEmpty()) {
                playSongsShuffled(randomSongs, queueName, startAtZero = true)
            }
        }
    }

    /**
     * Called from Quick Settings tile. Unlike shuffleAllSongs(), this always starts
     * fresh playback regardless of current state, and correctly handles the case
     * where the MediaController isn't ready yet (cold start from tile).
     *
     * Queries a bounded random sample directly from the repository so the tile does
     * not depend on the eager in-memory song cache being populated first.
     */
    fun triggerShuffleAllFromTile() {
        Timber.d("[TileDebug] triggerShuffleAllFromTile called. mediaController=${mediaController != null}")
        val action: () -> Unit = {
            Timber.d("[TileDebug] action() invoked")
            viewModelScope.launch {
                var songs = musicRepository.getRandomSongs(limit = 500)
                Timber.d("[TileDebug] Repository returned ${songs.size} random songs immediately")

                if (songs.isEmpty()) {
                    // Cold start or stale DB state: trigger a sync and retry the bounded query.
                    Timber.d("[TileDebug] No songs available yet, triggering sync and retrying repository sample")
                    syncManager.sync()
                    songs = withTimeoutOrNull(30_000L) {
                        var refreshedSongs = emptyList<Song>()
                        while (refreshedSongs.isEmpty()) {
                            refreshedSongs = musicRepository.getRandomSongs(limit = 500)
                            if (refreshedSongs.isEmpty()) {
                                delay(500L)
                            }
                        }
                        refreshedSongs
                    }
                        ?: emptyList()
                    Timber.d("[TileDebug] After retry, repository returned ${songs.size} songs")
                }

                if (songs.isNotEmpty()) {
                    Timber.d("[TileDebug] Calling playSongsShuffled with ${songs.size} songs")
                    playSongsShuffled(songs, "All Songs (Shuffled)", startAtZero = true)
                } else {
                    Timber.w("[TileDebug] No songs found even after sync - library may be empty")
                    sendToast(context.getString(R.string.player_no_songs_in_library_toast))
                }
            }
        }

        if (mediaController == null) {
            Timber.d("[TileDebug] mediaController null, queuing as pendingPlaybackAction")
            pendingPlaybackAction = action
        } else {
            Timber.d("[TileDebug] mediaController ready, calling action immediately")
            action()
        }
    }

    fun playRandomSong() {
        viewModelScope.launch {
            val randomSongs = musicRepository.getRandomSongs(limit = 500)
            if (randomSongs.isNotEmpty()) {
                playSongsShuffled(randomSongs, "All Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleFavoriteSongs() {

        // Load favorite songs from DB on-demand instead of holding them in memory
        viewModelScope.launch {
            val favSongs = musicRepository.getFavoriteSongsOnce(playerUiState.value.currentStorageFilter)
            if (favSongs.isNotEmpty()) {
                playSongsShuffled(favSongs, "Liked Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleRandomAlbum() {
        viewModelScope.launch {
            val allAlbums = libraryStateHolder.albums.value
            if (allAlbums.isNotEmpty()) {
                val randomAlbum = allAlbums.random()
                val albumSongs = musicRepository.getSongsForAlbum(randomAlbum.id).first()
                if (albumSongs.isNotEmpty()) {
                    playSongsShuffled(albumSongs, randomAlbum.title, startAtZero = true)
                }
            }
        }
    }

    fun shuffleRandomArtist() {
        viewModelScope.launch {
            val allArtists = libraryStateHolder.artists.value
            if (allArtists.isNotEmpty()) {
                val randomArtist = allArtists.random()
                val artistSongs = musicRepository.getSongsForArtist(randomArtist.id).first()
                if (artistSongs.isNotEmpty()) {
                    playSongsShuffled(artistSongs, randomArtist.name, startAtZero = true)
                }
            }
        }
    }


    private fun loadPersistedDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.loadPersistedDailyMix()
    }

    fun forceUpdateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.forceUpdate(
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private var transitionSchedulerJob: Job? = null
    private var remoteQueueLoadJob: Job? = null
    private var castSongUiSyncJob: Job? = null
    private var lastCastSongUiSyncedId: String? = null

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    // MIN_SESSION_LISTEN_MS, currentSession, and ListeningStatsTracker class
    // have been moved to ListeningStatsTracker.kt for better modularity


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun updatePredictiveBackSwipeEdge(edge: Int?) {
        _predictiveBackSwipeEdge.value = edge
    }

    fun resetPredictiveBackState() {
        _predictiveBackCollapseFraction.value = 0f
        _predictiveBackSwipeEdge.value = null
    }

    fun updateQueueSheetVisibility(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun updateCastSheetVisibility(visible: Boolean) {
        _isCastSheetVisible.value = visible
    }

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private data class FolderSourceState(
        val source: FolderSource,
        val rootPath: String,
        val isSdCardAvailable: Boolean
    )

    private fun resolveFolderSourceState(preferredSource: FolderSource): FolderSourceState {
        val storages = StorageUtils.getAvailableStorages(context)
        val internalPath = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: android.os.Environment.getExternalStorageDirectory().path
        val sdPath = StorageUtils.getSdCardStorage(context)
            ?.path
            ?.path

        val effectiveSource = if (!ENABLE_FOLDERS_SOURCE_SWITCHING) {
            FolderSource.INTERNAL
        } else if (preferredSource == FolderSource.SD_CARD && sdPath == null) {
            FolderSource.INTERNAL
        } else {
            preferredSource
        }

        val resolvedRootPath = if (effectiveSource == FolderSource.SD_CARD) sdPath!! else internalPath
        return FolderSourceState(
            source = effectiveSource,
            rootPath = resolvedRootPath,
            isSdCardAvailable = sdPath != null
        )
    }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        connectivityStateHolder.refreshLocalConnectionInfo(refreshBluetoothDevices)
    }

    /**
     * Suspends until the UI has committed its first frame AND initial onboarding/setup
     * is completed. This prevents non-critical background jobs (sort calculations, tab
     * migrations, Cast discovery) from competing with first-install setup screens.
     */
    private suspend fun awaitMainAppReady() {
        AppReadinessSignal.awaitReady()
        if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
            userPreferencesRepository.initialSetupDoneFlow.filter { it }.first()
        }
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager?.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession, context.contentResolver))
            castStateHolder.setRemotePlaybackActive(true)
        }

        // Consolidated one-time migrations: run sequentially off the main thread
        // only after the main app is ready, avoiding multiple concurrent coroutine launches.
        viewModelScope.launch(Dispatchers.IO) {
            awaitMainAppReady()
            userPreferencesRepository.migrateTabOrder()
            userPreferencesRepository.ensureLibrarySortDefaults()
            val legacyFavoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
            if (legacyFavoriteIds.isNotEmpty()) {
                val roomFavoriteIds = musicRepository.getFavoriteSongIdsOnce()
                if (roomFavoriteIds.isEmpty()) {
                    legacyFavoriteIds.forEach { songId ->
                        musicRepository.setFavoriteStatus(songId, true)
                    }
                }
                userPreferencesRepository.clearFavoriteSongIds()
            }
        }

        viewModelScope.launch {
            awaitMainAppReady()
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                folderNavigationStateHolder.setFoldersPlaylistViewState(
                    isPlaylistView = isPlaylistView,
                    updateUiState = { mutation -> _playerUiState.update(mutation) }
                )
            }
        }

        // PERF: deferred — foldersSourceFlow reads DataStore; not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            userPreferencesRepository.foldersSourceFlow.collect { preferredSource ->
                val resolved = resolveFolderSourceState(preferredSource)
                if (resolved.source != preferredSource) {
                    userPreferencesRepository.setFoldersSource(resolved.source)
                }

                _playerUiState.update { currentState ->
                    val sourceChanged = currentState.folderSource != resolved.source ||
                            currentState.folderSourceRootPath != resolved.rootPath
                    currentState.copy(
                        folderSource = resolved.source,
                        folderSourceRootPath = resolved.rootPath,
                        isSdCardAvailable = resolved.isSdCardAvailable,
                        currentFolderPath = if (sourceChanged) null else currentState.currentFolderPath,
                        currentFolder = if (sourceChanged) null else currentState.currentFolder
                    )
                }
            }
        }

        // PERF: deferred — both flows read DataStore; not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            combine(
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.isAlbumsListViewFlow,
            ) { gestureNav, albumsList ->
                Pair(gestureNav, albumsList)
            }.collect { (gestureNav, albumsList) ->
                _playerUiState.update {
                    it.copy(
                        folderBackGestureNavigationEnabled = gestureNav,
                        isAlbumsListView = albumsList,
                    )
                }
            }
        }

        // PERF: deferred — blockedDirectoriesFlow reads DataStore; not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }

                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        // PERF: deferred — both flows ultimately read DataStore; not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // PERF: deferred — 5 DataStore .first() reads plus sort re-application;
        // not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            val initialSongSort = resolveSortOption(
                userPreferencesRepository.songsSortOptionFlow.first(),
                SortOption.SONGS,
                SortOption.SongTitleAZ
            )
            val initialAlbumSort = resolveSortOption(
                userPreferencesRepository.albumsSortOptionFlow.first(),
                SortOption.ALBUMS,
                SortOption.AlbumTitleAZ
            )
            val initialArtistSort = resolveSortOption(
                userPreferencesRepository.artistsSortOptionFlow.first(),
                SortOption.ARTISTS,
                SortOption.ArtistNameAZ
            )
            val initialFolderSort = resolveSortOption(
                userPreferencesRepository.foldersSortOptionFlow.first(),
                SortOption.FOLDERS,
                SortOption.FolderNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedSongsSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedSongDateLiked
            )

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFolderSortOption = initialFolderSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }

            sortSongs(initialSongSort, persist = false)
            sortAlbums(initialAlbumSort, persist = false)
            sortArtists(initialArtistSort, persist = false)
            sortFolders(initialFolderSort, persist = false)
            sortFavoriteSongs(initialLikedSort, persist = false)
        }

        // PERF: deferred — 2 DataStore .first() reads for shuffle state;
        viewModelScope.launch {
            awaitMainAppReady()
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

        loadPersistedDailyMix()
        loadSearchHistory()

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                if (oldSyncingLibraryState && !isSyncing) {
                    Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }


        connectMediaController()
        startMediaControllerHealthMonitor()

        // Observe selection for HTTP server management
        viewModelScope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                    castTransferStateHolder.primeHttpServerStart()
                } else if (route?.isDefault == true) {
                    val hasActiveRemoteSession = castStateHolder.castSession.value?.remoteMediaClient != null ||
                            castStateHolder.isRemotePlaybackActive.value ||
                            castStateHolder.isCastConnecting.value
                    if (hasActiveRemoteSession) {
                        return@collect
                    }
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { mediaController },
            currentSongIdProvider = { stablePlayerState.map { it.currentSong?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            songTitleResolver = { songId -> libraryStateHolder.allSongsById.value[songId]?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder
        searchStateHolder.initialize(viewModelScope)

        // Collect SearchStateHolder flows
        viewModelScope.launch {
            combine(
                searchStateHolder.searchResults,
                searchStateHolder.selectedSearchFilter,
                searchStateHolder.searchHistory,
                searchStateHolder.isSearching,
            ) { results, filter, history, isSearching ->
                arrayOf(results, filter, history, isSearching)
            }.collect { array ->
                @Suppress("UNCHECKED_CAST")
                _playerUiState.update {
                    it.copy(
                        searchResults = array[0] as kotlinx.collections.immutable.ImmutableList<com.unshoo.pixelmusic.data.model.SearchResultItem>,
                        selectedSearchFilter = array[1] as com.unshoo.pixelmusic.data.model.SearchFilterType,
                        searchHistory = array[2] as kotlinx.collections.immutable.ImmutableList<com.unshoo.pixelmusic.data.model.SearchHistoryItem>,
                        isSearching = array[3] as Boolean
                    )
                }
            }
        }

        // Initialize AiStateHolder
        aiStateHolder.initialize(
            scope = viewModelScope,
            allSongsProvider = { musicRepository.getAllSongsOnce() },
            favoriteSongIdsProvider = { favoriteSongIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playSongsCallback = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
            openPlayerSheetCallback = { _isSheetVisible.value = true }
        )

        // Collect AiStateHolder flows
        viewModelScope.launch {
            combine(
                aiStateHolder.showAiPlaylistSheet,
                aiStateHolder.isGeneratingAiPlaylist,
                aiStateHolder.aiStatus,
                aiStateHolder.aiError,
                aiStateHolder.isGeneratingMetadata,
            ) { show, generating, status, error, generatingMetadata ->
                AiUiSnapshot(
                    showAiPlaylistSheet = show,
                    isGeneratingAiPlaylist = generating,
                    aiStatus = status,
                    aiError = error,
                    isGeneratingAiMetadata = generatingMetadata
                )
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(isGeneratingAiMetadata = snapshot.isGeneratingAiMetadata)
                }
            }
        }

        // Initialize LibraryStateHolder
        libraryStateHolder.initialize(viewModelScope)

        // Sync library folders and loading states
        viewModelScope.launch {
            combine(
                libraryStateHolder.musicFolders,
                libraryStateHolder.isLoadingLibrary,
                libraryStateHolder.isLoadingCategories,
            ) { folders, loadingLibrary, loadingCategories ->
                Triple(folders, loadingLibrary, loadingCategories)
            }.collect { (folders, loadingLibrary, loadingCategories) ->
                _playerUiState.update {
                    it.copy(
                        musicFolders = folders,
                        isLoadingInitialSongs = loadingLibrary,
                        isLoadingLibraryCategories = loadingCategories,
                    )
                }
            }
        }

        // Sync sort options and storage filter
        viewModelScope.launch {
            combine(
                libraryStateHolder.currentSongSortOption,
                libraryStateHolder.currentAlbumSortOption,
                libraryStateHolder.currentArtistSortOption,
                libraryStateHolder.currentFolderSortOption,
                libraryStateHolder.currentFavoriteSortOption,
            ) { songSort, albumSort, artistSort, folderSort, favoriteSort ->
                SortOptionsSnapshot(songSort, albumSort, artistSort, folderSort, favoriteSort)
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(
                        currentSongSortOption = snapshot.songSort,
                        currentAlbumSortOption = snapshot.albumSort,
                        currentArtistSortOption = snapshot.artistSort,
                        currentFolderSortOption = snapshot.folderSort,
                        currentFavoriteSortOption = snapshot.favoriteSort,
                    )
                }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentStorageFilter.collect { filter ->
                _playerUiState.update { it.copy(currentStorageFilter = filter) }
            }
        }
        // PERF: deferred — DataStore read; not needed for first frame or during onboarding.
        viewModelScope.launch {
            awaitMainAppReady()
            userPreferencesRepository.hideLocalMediaFlow.collect { hide ->
                _playerUiState.update { it.copy(hideLocalMedia = hide) }
            }
        }


        castTransferStateHolder.initialize(
            scope = viewModelScope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue ->
                _playerUiState.update {
                    it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue())
                }
            },
            getSongsByIdMap = { libraryStateHolder.allSongsById.value },
            onTransferBackComplete = { startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { disconnect() },
            onCastError = { message ->
                viewModelScope.launch { _toastEvents.emit(message) }
            },
            onSongChanged = { uriString ->
                castSongUiSyncJob?.cancel()
                castSongUiSyncJob = viewModelScope.launch {
                    delay(220)
                    val currentSongId = stablePlayerState.value.currentSong?.id
                    if (currentSongId != null && currentSongId == lastCastSongUiSyncedId) {
                        return@launch
                    }
                    loadLyricsForCurrentSong()
                    uriString?.toUri()?.let { uri ->
                        themeStateHolder.extractAndGenerateColorScheme(uri, uriString)
                    }
                    if (currentSongId != null) {
                        lastCastSongUiSyncedId = currentSongId
                    }
                }
            }
        )



        viewModelScope.launch {
            // Repeat preference is only a startup restore value.
            // Keeping a live collector here creates a feedback path:
            // player -> DataStore -> collector -> player, which can cause
            // repeat mode oscillation if a transient player state is persisted.
            val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
            applyPreferredRepeatMode(savedRepeatMode)
        }

        viewModelScope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }

        // Auto-hide undo bar when a new song starts playing
        playlistDismissUndoStateHolder.observeUndoStateAgainstPlayback(
            scope = viewModelScope,
            currentSongIdFlow = stablePlayerState.map { it.currentSong?.id },
            getUiState = { _playerUiState.value },
            onHideDismissUndoBar = { hideDismissUndoBar() }
        )

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        try {
            checkAndUpdateDailyMixIfNeeded()
            checkAndReconnectMediaController()
            preWarmCurrentSongStream()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Silently pre-resolves the current snapshot song's cloud stream URL in the background
     * so that when the user taps play, the stream is already cached and playback starts
     * instantly. This replaces the missing PlaybackSessionWarmer functionality.
     */
    private fun preWarmCurrentSongStream() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        val uri = com.unshoo.pixelmusic.utils.MediaItemBuilder.playbackUri(currentSong)
        val scheme = uri.scheme
        if (scheme == "youtube" || scheme == "telegram" || scheme == "gdrive") {
            viewModelScope.launch(Dispatchers.IO) {
                AppReadinessSignal.awaitReady()
                runCatching {
                    dualPlayerEngine.ensureFreshStreamForResume()
                }.onFailure { e ->
                    timber.log.Timber.w(e, "Pre-warm stream resolve failed for %s (non-fatal)", currentSong.id)
                }
            }
        }
    }

    private fun connectMediaController() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            viewModelScope.launch(Dispatchers.Main.immediate) { connectMediaController() }
            return
        }

        val connectedController = mediaController
        if (connectedController?.isConnected == true) {
            _isMediaControllerReady.value = true
            return
        }

        // Do not cancel/recreate an in-flight future. The old implementation released every
        // future each time reconnect was requested. Its resulting onDisconnected callback then
        // requested another connection, creating a controller churn loop in which UI commands
        // were sent to released binders while MusicService kept playing normally.
        val inFlight = activeMediaControllerFuture
        if (inFlight != null && !inFlight.isDone) return
        if (inFlight != null) {
            runCatching { MediaController.releaseFuture(inFlight) }
            if (activeMediaControllerFuture === inFlight) activeMediaControllerFuture = null
        }

        mediaControllerReconnectJob?.cancel()
        mediaControllerReconnectJob = null
        _isMediaControllerReady.value = false
        val generation = ++mediaControllerConnectionGeneration
        val future = mediaControllerFactory.create(context, sessionToken, mediaControllerListener)
        activeMediaControllerFuture = future

        future.addListener({
            if (future !== activeMediaControllerFuture || generation != mediaControllerConnectionGeneration) {
                runCatching { MediaController.releaseFuture(future) }
                return@addListener
            }
            try {
                val controller = future.get()
                if (!controller.isConnected) {
                    MediaController.releaseFuture(future)
                    activeMediaControllerFuture = null
                    scheduleMediaControllerReconnect()
                    return@addListener
                }

                val previousController = mediaController
                val previousListener = mediaControllerPlaybackListener

                // Publish the new reference before releasing the old controller. This makes a
                // late disconnect callback from the old instance harmless (identity check above).
                mediaController = controller
                playbackStateHolder.setMediaController(controller)
                _isMediaControllerReady.value = true

                if (previousController != null && previousController !== controller) {
                    previousListener?.let { listener ->
                        runCatching { previousController.removeListener(listener) }
                    }
                    runCatching { previousController.release() }
                }

                setupMediaControllerListeners()
                syncCurrentPlayerState(controller)
                updateCurrentPlaybackQueueFromPlayer(controller)
                flushPendingRepeatMode()
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
                flushPendingControllerCommands()
            } catch (e: Exception) {
                if (future === activeMediaControllerFuture) {
                    activeMediaControllerFuture = null
                }
                _isMediaControllerReady.value = false
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
                scheduleMediaControllerReconnect()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun scheduleMediaControllerReconnect(delayMs: Long = 300L) {
        if (mediaControllerReconnectJob?.isActive == true) return
        mediaControllerReconnectJob = viewModelScope.launch(Dispatchers.Main) {
            delay(delayMs)
            mediaControllerReconnectJob = null
            connectMediaController()
        }
    }

    private fun runWhenMediaControllerReady(command: () -> Unit) {
        runOnMainImmediate {
            val controller = mediaController
            if (controller?.isConnected == true) {
                runCatching(command).onFailure { error ->
                    Timber.tag("PlayerControllerHealth").w(error, "Playback command failed; reconnecting")
                    enqueuePendingControllerCommand(command)
                    if (mediaController === controller) {
                        mediaController = null
                        playbackStateHolder.clearMediaController(controller)
                        _isMediaControllerReady.value = false
                        runCatching { controller.release() }
                    }
                    scheduleMediaControllerReconnect(delayMs = 0L)
                }
                return@runOnMainImmediate
            }
            enqueuePendingControllerCommand(command)
            connectMediaController()
        }
    }

    private fun enqueuePendingControllerCommand(command: () -> Unit) {
        // Keep the queue bounded against accidental rapid tapping while disconnected.
        if (pendingControllerCommands.size >= maxPendingControllerCommands) {
            pendingControllerCommands.removeFirst()
        }
        pendingControllerCommands.addLast(command)
    }

    private fun flushPendingControllerCommands() {
        if (mediaController?.isConnected != true) return
        while (pendingControllerCommands.isNotEmpty()) {
            val command = pendingControllerCommands.removeFirst()
            val succeeded = runCatching(command)
                .onFailure { Timber.tag("PlayerControllerHealth").w(it, "Queued playback command failed") }
                .isSuccess
            if (!succeeded) {
                pendingControllerCommands.addFirst(command)
                scheduleMediaControllerReconnect(delayMs = 0L)
                return
            }
        }
    }

    private fun startMediaControllerHealthMonitor() {
        if (mediaControllerHealthJob?.isActive == true) return
        mediaControllerHealthJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(mediaControllerHealthIntervalMs)
                if (castStateHolder.castSession.value?.remoteMediaClient != null) continue

                val controller = mediaController
                if (controller == null || !controller.isConnected) {
                    _isMediaControllerReady.value = false
                    scheduleMediaControllerReconnect(delayMs = 0L)
                    continue
                }

                // MediaSession playback lives in the service and can remain healthy even if a UI
                // listener update is missed under heavy Explore/Search navigation. Reconcile from
                // the live controller instead of requiring a process restart to unfreeze the UI.
                val stable = playbackStateHolder.stablePlayerState.value
                val controllerMediaId = controller.currentMediaItem?.mediaId
                val stateIsStale = controllerMediaId != stable.currentSong?.id ||
                    controller.isPlaying != stable.isPlaying ||
                    controller.playWhenReady != stable.playWhenReady
                if (stateIsStale) {
                    Timber.tag("PlayerControllerHealth").w(
                        "Repairing stale player UI (controller=%s ui=%s connected=%s)",
                        controllerMediaId,
                        stable.currentSong?.id,
                        controller.isConnected
                    )
                    setupMediaControllerListeners()
                    syncCurrentPlayerState(controller)
                }
            }
        }
    }

    fun checkAndReconnectMediaController() {
        val currentController = mediaController
        if (currentController == null || !currentController.isConnected) {
            Log.i("PlayerViewModel", "MediaController is null or disconnected. Triggering reconnect.")
            connectMediaController()
            return
        }

        // BUGFIX (Full Player freezes after long background idle, notification stays fine):
        // MediaController.isConnected staying true does NOT guarantee the ViewModel-side UI
        // state (_stablePlayerState, current position, queue) is still in sync with it. The
        // notification is built server-side directly from MusicService's own player/session, so
        // it's unaffected by anything on this side - but the Full Player only reflects whatever
        // was last pushed into these StateFlows. If a Player.Listener callback was ever missed,
        // or a downstream distinctUntilChanged() suppressed an update while the screen wasn't
        // being actively collected, the Full Player is left showing a stale snapshot with no
        // trigger to correct itself, even though the controller is technically fine. Rather than
        // relying purely on passive listener callbacks to eventually catch up,
        // setupMediaControllerListeners() re-derives every piece of Full Player state directly
        // from the live controller and is safe to call again on an already-connected controller
        // (it removes/re-adds its own listener and every value it sets is freshly read here, not
        // incrementally patched) - so forcing it on every foreground return guarantees the Full
        // Player always reflects the actual MediaSession the moment the user looks at it again.
        Log.d("PlayerViewModel", "MediaController still connected; forcing a full state resync for Full Player")
        setupMediaControllerListeners()
        syncCurrentPlayerState(currentController)
    }

    private fun syncCurrentPlayerState(playerCtrl: MediaController) {
        val mediaItem = playerCtrl.currentMediaItem
        if (mediaItem != null) {
            val song = resolveSongFromMediaItem(mediaItem)
            val readyPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
            val songDurationHint = song?.duration?.coerceAtLeast(0L) ?: 0L
            val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                reportedDurationMs = playerCtrl.duration,
                songDurationHintMs = songDurationHint,
                currentPositionMs = readyPosition
            )
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = song,
                    currentMediaItemIndex = playerCtrl.currentMediaItemIndex,
                    totalDuration = resolvedDuration,
                    isPlaying = playerCtrl.isPlaying,
                    playWhenReady = playerCtrl.playWhenReady,
                    repeatMode = playerCtrl.repeatMode
                )
            }
            syncPlaybackPositionFromPlayer(mediaItem.mediaId, readyPosition)
            if (playerCtrl.isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        } else {
            val hasPreloadedState = playbackStateHolder.stablePlayerState.value.currentSong != null ||
                _playerUiState.value.currentPlaybackQueue.isNotEmpty()
            if (_playerUiState.value.preparingSongId == null && !hasPreloadedState) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        totalDuration = 0L
                    )
                }
                playbackStateHolder.clearCurrentPositionHints()
                playbackStateHolder.setCurrentPosition(0L)
                stopProgressUpdates()
            }
        }
        updateCurrentPlaybackQueueFromPlayer(playerCtrl)
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder after UI is interactive
        viewModelScope.launch {
            AppReadinessSignal.awaitReady()
            delay(1_500L)
            dailyMixStateHolder.checkAndUpdateIfNeeded(
                favoriteSongIdsFlow = favoriteSongIds
            )
        }
    }

    private fun preloadThemesAndInitialData() {
        _isInitialThemePreloadComplete.value = true
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        try {
            Log.d("PlayerViewModel", "resetAndLoadInitialData called by $caller")
            if (libraryStateHolder.allSongs.value.isNotEmpty()) {
                loadInitialLibraryDataParallel()
            }
            dailyMixStateHolder.checkAndUpdateIfNeeded(favoriteSongIds)
        } finally {
            Trace.endSection()
        }
    }

    fun loadSongsIfNeeded() = libraryStateHolder.loadSongsIfNeeded()
    fun loadAlbumsIfNeeded() = libraryStateHolder.loadAlbumsIfNeeded()
    fun loadArtistsIfNeeded() = libraryStateHolder.loadArtistsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun setStorageFilter(filter: com.unshoo.pixelmusic.data.model.StorageFilter) {
        libraryStateHolder.setStorageFilter(filter)
    }

    fun setPlaylistPickerStorageFilter(filter: com.unshoo.pixelmusic.data.model.StorageFilter) {
        _playlistPickerStorageFilter.value = filter
    }

    fun setHideLocalMedia(hide: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideLocalMedia(hide)
        }
    }

    fun toggleStorageFilter() {
        val current = _playerUiState.value.currentStorageFilter
        val isTelegramReady = try {
            telegramRepositoryProvider.get().isReady()
        } catch (e: Exception) {
            false
        }
        val next = when (current) {
            com.unshoo.pixelmusic.data.model.StorageFilter.ALL -> com.unshoo.pixelmusic.data.model.StorageFilter.LOCAL
            com.unshoo.pixelmusic.data.model.StorageFilter.LOCAL -> {
                if (isTelegramReady) {
                    com.unshoo.pixelmusic.data.model.StorageFilter.TELEGRAM
                } else {
                    com.unshoo.pixelmusic.data.model.StorageFilter.YOUTUBE
                }
            }
            com.unshoo.pixelmusic.data.model.StorageFilter.TELEGRAM -> com.unshoo.pixelmusic.data.model.StorageFilter.YOUTUBE
            com.unshoo.pixelmusic.data.model.StorageFilter.YOUTUBE -> com.unshoo.pixelmusic.data.model.StorageFilter.ALL
            com.unshoo.pixelmusic.data.model.StorageFilter.ONLINE -> com.unshoo.pixelmusic.data.model.StorageFilter.ALL
        }
        setStorageFilter(next)
    }

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true,
        cancelPendingQueueBuild: Boolean = true,
        playlistId: String? = null
    ) {
        if (cancelPendingQueueBuild) {
            cancelPendingFullQueuePlayback()
        }
        val playbackContext =
            if (contextSongs.any { it.id == song.id }) contextSongs else listOf(song)
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val desiredQueue = playbackContext
            val lastRemoteQueue = castTransferStateHolder.lastRemoteQueue
            val contextMatchesRemoteSnapshot = lastRemoteQueue.matchesSongOrder(desiredQueue)
            val targetIndexInDesiredQueue = desiredQueue.indexOfFirst { it.id == song.id }

            val currentRemoteId = mediaStatus
                ?.let { status ->
                    status.getQueueItemById(status.getCurrentItemId())
                        ?.customData?.optString("songId")
                        ?.takeIf { it.isNotBlank() }
                } ?: castTransferStateHolder.lastRemoteSongId

            val itemIdFromStatus = mediaStatus
                ?.queueItems
                ?.firstOrNull { it.customData?.optString("songId") == song.id }
                ?.itemId

            val targetItemId = itemIdFromStatus?.takeIf { it > 0 }
            val canJumpInCurrentRemoteQueue = contextMatchesRemoteSnapshot && targetIndexInDesiredQueue >= 0 && targetItemId != null

            when {
                canJumpInCurrentRemoteQueue -> {
                    // Same queue context: jump directly for immediate, deterministic song changes.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                    val itemId = requireNotNull(targetItemId)
                    castStateHolder.castPlayer?.jumpToItem(itemId, 0L)
                }
                contextMatchesRemoteSnapshot && currentRemoteId == song.id -> {
                    // Already on target.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                }
                else -> {
                    // Queue context changed: perform a single remote queue load.
                    remoteQueueLoadJob?.cancel()
                    remoteQueueLoadJob = viewModelScope.launch {
                        val hydratedQueue = hydrateSongsIfNeeded(desiredQueue)
                        if (hydratedQueue.isEmpty()) return@launch
                        val hydratedStartSong =
                            hydratedQueue.firstOrNull { it.id == song.id } ?: hydratedQueue.first()
                        val loaded = castTransferStateHolder.playRemoteQueue(
                            songsToPlay = hydratedQueue,
                            startSong = hydratedStartSong,
                            isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
                        )
                        if (!loaded) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Failed to load requested remote queue (songId=%s size=%d).",
                                song.id,
                                desiredQueue.size
                            )
                        }
                    }
                }
            }

            if (isVoluntaryPlay) {
                incrementSongScore(song)
                if (playlistId != null && queueName != "None") {
                    appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
                }
            }
            return
        }

        // Local playback logic
        if (playbackContext.size <= 1) {
            if (isVoluntaryPlay) incrementSongScore(song)
            playWithArchiveTuneQueueBuilder(song, queueName, playlistId)
        } else {
            val controller = mediaController
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val songIndexInQueue = currentQueue.indexOfFirst { it.id == song.id }
            val queueMatchesContext = currentQueue.matchesSongOrder(playbackContext)
            val reusableTargetIndex = if (
                controller != null &&
                controller.isConnected &&
                !dualPlayerEngine.isTransitionRunning() &&
                songIndexInQueue != -1 &&
                queueMatchesContext
            ) {
                controller.resolveReusablePlaybackTargetIndex(songIndexInQueue, song.id)
            } else {
                null
            }

            if (controller != null && reusableTargetIndex != null) {
                cancelPendingDirectPlaybackBuild()
                directPlaybackApplyJob = playLoadedControllerItem(controller, reusableTargetIndex, song)
                if (isVoluntaryPlay) {
                    incrementSongScore(song)
                    if (playlistId != null && queueName != "None") {
                        appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
                    }
                }
            } else {
                if (isVoluntaryPlay) incrementSongScore(song)
                playSongs(playbackContext, song, queueName, playlistId)
                if (queueName == "Quick Picks") {
                    // Quick Picks lists are short (< 20 songs) by design. Start playback
                    // instantly on the tapped song, then top the queue back up to the
                    // auto-queue target from its radio after a 1500ms defer.
                    scheduleQuickPicksQueueFill(song)
                }
            }
        }
        resetPredictiveBackState()
    }

    fun showAndPlaySong(song: Song) {
        val castSession = castStateHolder.castSession.value
        val contextSongs = if (castSession != null && castSession.remoteMediaClient != null) {
            libraryStateHolder.allSongs.value.takeIf { songs ->
                songs.isNotEmpty() && songs.any { it.id == song.id }
            } ?: listOf(song)
        } else {
            listOf(song)
        }
        showAndPlaySong(song, contextSongs, "Library")
    }

    private fun parseLastFmTracksForMix(res: String): List<LastFmTrack> {
        return try {
            val json = Json { isLenient = true; ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(res).jsonObject
            val trackArray = when {
                "similartracks" in root -> root["similartracks"]?.jsonObject?.get("track")
                else -> null
            } ?: return emptyList()

            val elements = if (trackArray is JsonArray) {
                trackArray
            } else {
                buildJsonArray { add(trackArray) }
            }

            elements.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val artistElement = obj["artist"]
                val artist = when (artistElement) {
                    is JsonPrimitive -> artistElement.content
                    is JsonObject -> artistElement["name"]?.jsonPrimitive?.content
                        ?: artistElement["#text"]?.jsonPrimitive?.content
                        ?: ""
                    else -> ""
                }
                LastFmTrack(name, artist)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveTrackToYoutubeSongForMix(track: LastFmTrack): Song? {
        return try {
            val query = "${track.name} ${track.artist}"
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val songsList = result?.items?.filterIsInstance<SongItem>()?.filterVideo(true).orEmpty()

            // Prefer ATV (official audio tracks) that actually match the requested artist.
            // Normalise both sides for a loose containment check so we reject unrelated
            // videos that YouTube search surfaces (covers, remixes from wrong artists, etc.).
            val trackArtistNorm = track.artist.trim().lowercase()
            val trackTitleNorm  = track.name.trim().lowercase()

            fun SongItem.isRelevant(): Boolean {
                val native = this.toNativeSong()
                val ytArtistNorm = native.artist.trim().lowercase()
                val ytTitleNorm  = native.title.trim().lowercase()
                // Accept if either artist contains the other, or title contains track name
                val artistMatch = ytArtistNorm.contains(trackArtistNorm) ||
                    trackArtistNorm.contains(ytArtistNorm)
                val titleMatch  = ytTitleNorm.contains(trackTitleNorm) ||
                    trackTitleNorm.contains(ytTitleNorm)
                return artistMatch || titleMatch
            }

            // 1st preference: ATV + relevant
            val songItem = songsList.firstOrNull { item ->
                val musicVideoType = item.endpoint
                    ?.watchEndpointMusicSupportedConfigs
                    ?.watchEndpointMusicConfig?.musicVideoType
                musicVideoType == "MUSIC_VIDEO_TYPE_ATV" && item.isRelevant()
            }
                // 2nd preference: any relevant result
                ?: songsList.firstOrNull { it.isRelevant() }
                // Reject entirely if no result matches — don't return random videos
                ?: return null

            // Keep YouTube's native metadata (title, artist, artwork, videoId) as-is.
            // Overwriting with Last.fm strings was the original bug: it hid wrong videos
            // behind the correct-looking name while the audio was completely different.
            val native = songItem.toNativeSong()
            native.copy(
                album = native.album.takeIf { it.isNotBlank() && it != "YouTube Music" } ?: "YouTube Music"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanForLastFm(text: String): String {
        return text
            .replace(Regex("\\s*[(\\[](Official|Music|Video|Audio|Lyric|Lyrics|Live|Remastered|Remaster|HD|HQ).*?[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(Official|Video|Audio|Lyric|Lyrics|Live|Remastered|Remaster|HD|HQ).*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*Topic", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private var lastFmMixJob: Job? = null
    private var lastFmMixToken: Long = 0L

    fun startMixFromSong(song: Song) {
        lastFmMixJob?.cancel()
        lastFmMixToken += 1L
        val requestToken = lastFmMixToken

        val currentMediaId = dualPlayerEngine.masterPlayer.currentMediaItem?.mediaId
        val isAlreadyPlaying = (currentMediaId != null && (
            currentMediaId == song.id ||
            (song.youtubeId != null && currentMediaId == song.youtubeId) ||
            currentMediaId.removePrefix("youtube_") == song.id.removePrefix("youtube_")
        ))

        lastFmMixJob = viewModelScope.launch {
            val isSmartMixEnabled = runCatching { userPreferencesRepository.lastfmSmartMixEnabledFlow.first() }.getOrDefault(true)
            if (!isSmartMixEnabled) {
                sendToast("Last.fm Smart Mix disabled. Starting YouTube Music mix...")
                playWithArchiveTuneQueueBuilder(song, "Mix: ${song.title}")
                return@launch
            }

            if (!LastFM.isInitialized()) {
                sendToast("Last.fm not configured. Starting YouTube Music mix...")
                playWithArchiveTuneQueueBuilder(song, "Mix: ${song.title}")
                return@launch
            }

            sendToast("Generating Last.fm mix for '${song.title}'...")

            var lastfmFailed = false
            var lastfmFailReason = ""

            val songs = withContext(Dispatchers.IO) {
                try {
                    val cleanTitle = cleanForLastFm(song.title)
                    val cleanArtist = cleanForLastFm(song.artist)
                    val res = LastFM.get(
                        "track.getsimilar",
                        mapOf("track" to cleanTitle, "artist" to cleanArtist, "limit" to "100")
                    ).getOrNull()

                    if (requestToken != lastFmMixToken) return@withContext emptyList<Song>()

                    if (res.isNullOrBlank()) {
                        lastfmFailed = true
                        lastfmFailReason = "Empty response"
                        return@withContext emptyList<Song>()
                    }

                    // Check if response is actually a Last.fm error JSON
                    if (res.contains("\"error\"") && res.contains("\"message\"")) {
                        lastfmFailed = true
                        try {
                            val json = Json { isLenient = true; ignoreUnknownKeys = true }
                            val root = json.parseToJsonElement(res).jsonObject
                            val errorMsg = root["message"]?.jsonPrimitive?.content ?: "Track not found"
                            lastfmFailReason = errorMsg
                        } catch (e: Exception) {
                            lastfmFailReason = "Track not found"
                        }
                        return@withContext emptyList<Song>()
                    }

                    val rawTracks = parseLastFmTracksForMix(res)
                    if (rawTracks.isEmpty()) {
                        lastfmFailed = true
                        lastfmFailReason = "No similar tracks found"
                        return@withContext emptyList<Song>()
                    }

                    val filteredTracks = mutableListOf<LastFmTrack>()
                    val artistCounts = mutableMapOf<String, Int>()

                    val seedArtistNorm = song.artist.trim().lowercase()
                    val seedTitleNorm = song.title.trim().lowercase()

                    artistCounts[seedArtistNorm] = 1

                    for (track in rawTracks) {
                        val tArtistNorm = track.artist.trim().lowercase()
                        val tTitleNorm = track.name.trim().lowercase()

                        if (tArtistNorm == seedArtistNorm && tTitleNorm == seedTitleNorm) continue

                        val count = artistCounts[tArtistNorm] ?: 0
                        if (count >= 3) continue

                        filteredTracks.add(track)
                        artistCounts[tArtistNorm] = count + 1
                    }

                    val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
                    val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
                    if (filterCoverAndLofi) {
                        filteredTracks.removeAll { ContentFilterUtils.isCoverOrLofi(it.name, it.artist, filterKeywords = filterKeywords) }
                    }

                    val candidates = filteredTracks.shuffled().take(35)

                    val resolvedSongs = java.util.Collections.synchronizedList(mutableListOf<Song>())
                    val semaphore = Semaphore(3)
                    val deferreds = candidates.map { track ->
                        async {
                            semaphore.withPermit {
                                if (requestToken != lastFmMixToken) return@withPermit
                                val resolvedSong = resolveTrackToYoutubeSongForMix(track)
                                if (resolvedSong != null) {
                                    resolvedSongs.add(resolvedSong)
                                }
                            }
                        }
                    }
                    deferreds.awaitAll()

                    if (requestToken != lastFmMixToken) return@withContext emptyList<Song>()

                    if (resolvedSongs.isNotEmpty()) {
                        musicRepository.insertYoutubeSongs(resolvedSongs)
                    } else {
                        lastfmFailed = true
                        lastfmFailReason = "Could not resolve tracks on YouTube"
                    }

                    resolvedSongs.toList()
                } catch (e: Exception) {
                    Timber.e(e, "Error generating similar mix")
                    lastfmFailed = true
                    lastfmFailReason = e.localizedMessage ?: "Unknown error"
                    emptyList<Song>()
                }
            }

            if (requestToken != lastFmMixToken) return@launch

            if (songs.isNotEmpty()) {
                val player = dualPlayerEngine.masterPlayer
                withContext(Dispatchers.Main.immediate) {
                    if (requestToken != lastFmMixToken) return@withContext
                    val activeIndex = player.currentMediaItemIndex
                    val activeItem = player.currentMediaItem
                    val currentSongForQueue: Song = if (activeItem != null) {
                        _playerUiState.value.currentPlaybackQueue
                            .firstOrNull { it.id == activeItem.mediaId } ?: song
                    } else {
                        song
                    }

                    val totalCount = player.mediaItemCount
                    if (activeIndex >= 0 && activeIndex < totalCount) {
                        if (totalCount > activeIndex + 1) {
                            player.removeMediaItems(activeIndex + 1, totalCount)
                        }
                        if (activeIndex > 0) {
                            player.removeMediaItems(0, activeIndex)
                        }
                    } else if (totalCount > 1) {
                        player.removeMediaItems(1, totalCount)
                    }

                    val mediaItems = songs.map { MediaItemBuilder.build(it) }
                    player.addMediaItems(mediaItems)
                    _playerUiState.update {
                        it.copy(
                            currentPlaybackQueue = (listOf(currentSongForQueue) + songs).toPlaybackQueue(),
                            currentQueueSourceName = "Mix: ${song.title}"
                        )
                    }
                }
                sendToast("Last.fm mix ready — ${songs.size} tracks queued")
            } else {
                if (requestToken == lastFmMixToken) {
                    if (lastfmFailed) {
                        sendToast("Last.fm mix failed ($lastfmFailReason). Starting YouTube Music mix...")
                    } else {
                        sendToast("Last.fm mix failed. Starting YouTube Music mix...")
                    }
                    playWithArchiveTuneQueueBuilder(song, "Mix: ${song.title}")
                }
            }
        }
    }

    fun playRadio(
        endpoint: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint,
        title: String,
        shuffle: Boolean = false,
        artistName: String? = null,
        artistSongs: List<Song> = emptyList()
    ) {
        viewModelScope.launch {
            _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            val result = withContext(Dispatchers.IO) {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.next(endpoint)
            }
            result.onSuccess { nextResult ->
                var songs = nextResult.items.map { it.toNativeSong() }
                if (songs.isNotEmpty()) {
                    if (artistName != null && artistName.isNotBlank()) {
                        val nameLower = artistName.lowercase().trim()
                        val radioArtistSongs = songs.filter {
                            it.artist.lowercase().contains(nameLower) ||
                            it.artists.any { ref -> ref.name.lowercase().contains(nameLower) }
                        }
                        val radioRelatedSongs = songs.filter {
                            !it.artist.lowercase().contains(nameLower) &&
                            it.artists.none { ref -> ref.name.lowercase().contains(nameLower) }
                        }

                        val allArtistSongs = (artistSongs + radioArtistSongs)
                            .distinctBy { it.youtubeId ?: it.id }

                        val allRelatedSongs = radioRelatedSongs
                            .distinctBy { it.youtubeId ?: it.id }

                        if (allArtistSongs.isNotEmpty()) {
                            val targetSize = songs.size.coerceAtLeast(25)
                            val targetArtistCount = ((targetSize * 60) / 100).coerceAtMost(allArtistSongs.size)
                            val targetRelatedCount = (targetSize - targetArtistCount).coerceAtMost(allRelatedSongs.size)

                            val selectedArtist = allArtistSongs.shuffled().take(targetArtistCount)
                            val selectedRelated = allRelatedSongs.shuffled().take(targetRelatedCount)

                            var combined = (selectedArtist + selectedRelated)
                            if (shuffle) {
                                combined = combined.shuffled()
                            }
                            songs = combined
                        }
                    } else {
                        if (shuffle) {
                            songs = songs.shuffled()
                        }
                    }

                    if (songs.isNotEmpty()) {
                        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.reset()

                        // CRITICAL FIX: Save songs to DB BEFORE starting playback.
                        // This ensures artist/album rows exist when the player/history
                        // system references them, preventing NOT NULL / FK constraint crashes.
                        withContext(Dispatchers.IO) {
                            saveYoutubeSongsToDb(songs)
                        }

                        val startSong = songs.first()
                        playSongs(songs, startSong, title)
                        
                        val videoId = startSong.youtubeId ?: startSong.id.substringAfter("youtube_")
                        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                            endpoint = nextResult.endpoint ?: endpoint,
                            continuation = nextResult.continuation,
                            videoId = videoId
                        )
                    }
                } else {
                    Timber.w("playRadio: YouTube.next() returned empty items for endpoint")
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }.onFailure { e ->
                Timber.e(e, "Failed to start radio mix")
                sendToast("Failed to start radio mix: ${e.localizedMessage ?: "Unknown error"}")
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }
        }
    }

    fun playArtistSongsShuffledWithRelated(
        artistName: String,
        initialArtistSongs: List<Song>,
        songsMoreEndpoint: unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint? = null,
        isOnline: Boolean = true
    ) {
        viewModelScope.launch {
            _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            try {
                val allArtistSongs = initialArtistSongs.toMutableList()

                if (isOnline && songsMoreEndpoint != null) {
                    val fetchResult = withContext(Dispatchers.IO) {
                        YouTube.artistItems(songsMoreEndpoint)
                    }
                    fetchResult.onSuccess { page ->
                        val fetchedSongs = page.items.mapNotNull { item ->
                            (item as? unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem)?.toNativeSong()
                        }
                        if (fetchedSongs.isNotEmpty()) {
                            allArtistSongs.addAll(fetchedSongs)
                        }
                    }
                }

                val uniqueArtistSongs = allArtistSongs.distinctBy { it.youtubeId ?: it.id }

                var seedVideoId = uniqueArtistSongs.firstOrNull { !it.youtubeId.isNullOrBlank() }?.youtubeId
                    ?: uniqueArtistSongs.firstOrNull { it.id.startsWith("youtube_") }?.id?.removePrefix("youtube_")

                if (seedVideoId == null && isOnline) {
                    val searchResult = withContext(Dispatchers.IO) {
                        YouTube.search(artistName, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    }
                    val songItem = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()?.firstOrNull()
                    seedVideoId = songItem?.id
                }

                var relatedSongs = emptyList<Song>()

                if (isOnline && seedVideoId != null) {
                    val radioEndpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                        playlistId = "RDAMVM$seedVideoId",
                        videoId = seedVideoId
                    )
                    val nextResult = withContext(Dispatchers.IO) {
                        YouTube.next(radioEndpoint)
                    }
                    nextResult.onSuccess { nextData ->
                        val radioSongs = nextData.items.map { it.toNativeSong() }
                        val nameLower = artistName.lowercase().trim()
                        relatedSongs = radioSongs.filter {
                            !it.artist.lowercase().contains(nameLower) &&
                            it.artists.none { ref -> ref.name.lowercase().contains(nameLower) }
                        }.distinctBy { it.youtubeId ?: it.id }
                    }
                }

                val shuffledArtistSongs = uniqueArtistSongs.shuffled().toMutableList()
                val selectedRelated = relatedSongs.shuffled().take(5)

                if (selectedRelated.isNotEmpty() && shuffledArtistSongs.isNotEmpty()) {
                    val interval = (shuffledArtistSongs.size / (selectedRelated.size + 1)).coerceAtLeast(3)
                    selectedRelated.forEachIndexed { index, song ->
                        val insertIndex = ((index + 1) * interval).coerceAtMost(shuffledArtistSongs.size)
                        shuffledArtistSongs.add(insertIndex, song)
                    }
                } else if (shuffledArtistSongs.isEmpty() && selectedRelated.isNotEmpty()) {
                    shuffledArtistSongs.addAll(selectedRelated)
                }

                if (shuffledArtistSongs.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        saveYoutubeSongsToDb(shuffledArtistSongs)
                    }
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.reset()
                    val startSong = shuffledArtistSongs.first()
                    playSongs(shuffledArtistSongs, startSong, artistName)
                } else {
                    sendToast("No songs found for this artist")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing artist songs with related releases")
                sendToast("Failed to start artist playback: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }
        }
    }

    fun startArtistMixRadio(
        artistName: String,
        popularSongs: List<Song>,
        songsMoreEndpoint: unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint? = null
    ) {
        viewModelScope.launch {
            _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            sendToast("Tuning Mix Radio for $artistName...")
            
            try {
                val mixTracks = mutableListOf<Song>()
                val cleanArtistName = artistName.trim()
                
                // 1. Gather local songs by the artist
                val localSongs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByArtistName(cleanArtistName, limit = 50)
                }
                mixTracks.addAll(localSongs)
                
                // 2. Try Last.fm if initialized
                var lastFmSuccess = false
                if (LastFM.isInitialized()) {
                    val similarRes = withContext(Dispatchers.IO) {
                        LastFM.get("artist.getsimilar", mapOf("artist" to cleanArtistName, "limit" to "12"))
                            .getOrNull()
                    }
                    if (!similarRes.isNullOrBlank() && !similarRes.contains("\"error\"")) {
                        val similarArtists = parseLastFmSimilarArtists(similarRes)
                        if (similarArtists.isNotEmpty()) {
                            lastFmSuccess = true
                            
                            // Fetch top tracks in parallel
                            val deferredTracks = coroutineScope {
                                val mainArtistTracksDeferred = async(Dispatchers.IO) {
                                    fetchLastFmArtistTopTracks(cleanArtistName, limit = 15)
                                }
                                val similarArtistsTracksDeferred = similarArtists.take(8).map { similarArtist ->
                                    async(Dispatchers.IO) {
                                        fetchLastFmArtistTopTracks(similarArtist, limit = 3)
                                    }
                                }
                                mainArtistTracksDeferred to similarArtistsTracksDeferred
                            }
                            
                            val rawTracksList = mutableListOf<LastFmTrack>()
                            rawTracksList.addAll(deferredTracks.first.await())
                            deferredTracks.second.forEach {
                                rawTracksList.addAll(it.await())
                            }
                            
                            val shuffledRaw = rawTracksList.distinctBy { "${it.name.lowercase()} - ${it.artist.lowercase()}" }.shuffled()
                            
                            // Resolve top 25 tracks to YouTube songs in parallel
                            val resolvedSongs = withContext(Dispatchers.IO) {
                                shuffledRaw.take(25).map { track ->
                                    async {
                                        resolveTrackToYoutubeSongForMix(track)
                                    }
                                }.awaitAll().filterNotNull()
                            }
                            
                            mixTracks.addAll(resolvedSongs)
                        }
                    }
                }
                
                // 3. Fallback/Enrichment using YouTube Music recommendations
                val hasEnoughSongs = mixTracks.distinctBy { it.youtubeId ?: it.id }.size >= 15
                if (!hasEnoughSongs) {
                    val allArtistSongs = (popularSongs + localSongs).distinctBy { it.youtubeId ?: it.id }
                    var seedVideoId = allArtistSongs.firstOrNull { !it.youtubeId.isNullOrBlank() }?.youtubeId
                        ?: allArtistSongs.firstOrNull { it.id.startsWith("youtube_") }?.id?.removePrefix("youtube_")
                    
                    if (seedVideoId == null) {
                        val searchResult = withContext(Dispatchers.IO) {
                            YouTube.search(cleanArtistName, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        }
                        val songItem = searchResult?.items?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()?.firstOrNull()
                        seedVideoId = songItem?.id
                    }
                    
                    if (seedVideoId != null) {
                        val radioEndpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                            playlistId = "RDAMVM$seedVideoId",
                            videoId = seedVideoId
                        )
                        val nextResult = withContext(Dispatchers.IO) {
                            YouTube.next(radioEndpoint)
                        }
                        nextResult.onSuccess { nextData ->
                            val radioSongs = nextData.items.map { it.toNativeSong() }
                            mixTracks.addAll(radioSongs)
                        }
                    }
                }
                
                val finalMixList = mixTracks.distinctBy { it.youtubeId ?: it.id }
                
                if (finalMixList.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        saveYoutubeSongsToDb(finalMixList)
                    }
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.reset()
                    
                    // DIVERSITY FIX: enforce 40% artist / 60% related so the mix
                    // doesn't become a single-artist playlist. Cap main artist at 40%.
                    val targetTotal = finalMixList.size.coerceAtMost(40)
                    val maxMainArtist = (targetTotal * 0.40f).toInt().coerceAtLeast(1)

                    val mainArtistSongs = finalMixList
                        .filter { it.artist.contains(cleanArtistName, ignoreCase = true) }
                        .shuffled()
                        .take(maxMainArtist)

                    val diverseSongs = finalMixList
                        .filterNot { it.artist.contains(cleanArtistName, ignoreCase = true) }
                        .shuffled()
                        .toMutableList()

                    // Interleave 1 main-artist : 2 diverse for a rich, varied experience
                    val shuffledMix = mutableListOf<Song>()
                    if (mainArtistSongs.isNotEmpty()) {
                        shuffledMix.add(mainArtistSongs.first()) // seed song first for instant play
                        val mainTail = mainArtistSongs.drop(1).toMutableList()
                        while (mainTail.isNotEmpty() || diverseSongs.isNotEmpty()) {
                            if (mainTail.isNotEmpty()) shuffledMix.add(mainTail.removeFirst())
                            if (diverseSongs.isNotEmpty()) shuffledMix.add(diverseSongs.removeFirst())
                            if (diverseSongs.isNotEmpty()) shuffledMix.add(diverseSongs.removeFirst())
                        }
                    } else {
                        shuffledMix.addAll(finalMixList.shuffled())
                    }

                    playSongs(shuffledMix, shuffledMix.first(), "$artistName Mix Radio")

                } else {
                    sendToast("No songs found to build Mix Radio")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error launching artist Mix Radio")
                sendToast("Failed to tune Mix Radio: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }
        }
    }

    private fun parseLastFmSimilarArtists(res: String): List<String> {
        return try {
            val json = Json { isLenient = true; ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(res).jsonObject
            val artistArray = root["similarartists"]?.jsonObject?.get("artist") ?: return emptyList()
            
            val elements = if (artistArray is JsonArray) {
                artistArray
            } else {
                buildJsonArray { add(artistArray) }
            }
            
            elements.mapNotNull { element ->
                element.jsonObject["name"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchLastFmArtistTopTracks(artistName: String, limit: Int): List<LastFmTrack> {
        return try {
            val res = LastFM.get("artist.gettoptracks", mapOf("artist" to artistName, "limit" to limit.toString())).getOrNull()
            if (res.isNullOrBlank() || res.contains("\"error\"")) return emptyList()
            
            val json = Json { isLenient = true; ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(res).jsonObject
            val trackArray = root["toptracks"]?.jsonObject?.get("track") ?: return emptyList()
            
            val elements = if (trackArray is JsonArray) {
                trackArray
            } else {
                buildJsonArray { add(trackArray) }
            }
            
            elements.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val artistElement = obj["artist"]
                val artist = when (artistElement) {
                    is JsonPrimitive -> artistElement.content
                    is JsonObject -> artistElement["name"]?.jsonPrimitive?.content ?: artistElement["#text"]?.jsonPrimitive?.content ?: ""
                    else -> ""
                }
                LastFmTrack(name, if (artist.isNotBlank()) artist else artistName)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveYoutubeSongsToDb(songs: List<Song>) {
        val youtubeSongs = songs.filter { it.id.startsWith("youtube_") || it.youtubeId != null }
        if (youtubeSongs.isEmpty()) return
        
        withContext(Dispatchers.IO) {
            val songsToInsert = mutableListOf<SongEntity>()
            val albumsToInsert = mutableListOf<AlbumEntity>()
            val artistsToInsert = mutableListOf<ArtistEntity>()
            val crossRefsToInsert = mutableListOf<SongArtistCrossRef>()
            
            youtubeSongs.forEach { song ->
                val yId = song.youtubeId ?: song.id.removePrefix("youtube_")
                val songId = -(15_000_000_000_000L + yId.hashCode().toLong().let { if (it < 0) -it else it })
                
                val existing = musicDao.getSongByIdOnce(songId)
                if (existing != null) {
                    val newAlbumName = song.album.takeIf { it.isNotBlank() && it != "YouTube Music" }
                    if (newAlbumName != null && existing.albumName == "YouTube Music") {
                        val albumId = -(16_000_000_000_000L + newAlbumName.lowercase().hashCode().toLong().let { if (it < 0) -it else it })
                        val albumEntity = AlbumEntity(
                            id = albumId,
                            title = newAlbumName,
                            artistName = song.artist,
                            artistId = existing.artistId,
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtUriString = song.albumArtUriString ?: existing.albumArtUriString
                        )
                        musicDao.insertAlbumsIgnoreConflicts(listOf(albumEntity))
                        musicDao.updateSongAlbum(songId, newAlbumName, albumId, song.albumBrowseId)
                    }
                } else {
                    val albumName = song.album.ifBlank { "YouTube Music" }
                    val albumId = -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().let { if (it < 0) -it else it })
                    val artistId = -(17_000_000_000_000L + song.artist.lowercase().hashCode().toLong().let { if (it < 0) -it else it })
                    
                    val artistEntity = ArtistEntity(
                        id = artistId,
                        name = song.artist,
                        trackCount = 1,
                        imageUrl = null
                    )
                    artistsToInsert.add(artistEntity)
                    
                    val albumEntity = AlbumEntity(
                        id = albumId,
                        title = albumName,
                        artistName = song.artist,
                        artistId = artistId,
                        songCount = 1,
                        dateAdded = System.currentTimeMillis(),
                        year = 0,
                        albumArtUriString = song.albumArtUriString
                    )
                    albumsToInsert.add(albumEntity)
                    
                    val artistRefs = song.artists.ifEmpty {
                        listOf(
                            com.unshoo.pixelmusic.data.model.ArtistRef(
                                id = artistId,
                                name = song.artist,
                                isPrimary = true
                            )
                        )
                    }
                    
                    val entity = SongEntity(
                        id = songId,
                        title = song.title,
                        artistName = song.artist,
                        artistId = artistId,
                        albumArtist = song.albumArtist,
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
                        artistsJson = serializeArtistRefs(artistRefs),
                        sourceType = SourceType.YOUTUBE,
                        albumBrowseId = song.albumBrowseId
                    )
                    songsToInsert.add(entity)
                    
                    artistRefs.forEach { ref ->
                        val refArtistId = if (ref.id > 0) -(17_000_000_000_000L + ref.name.lowercase().hashCode().toLong().let { if (it < 0) -it else it }) else ref.id
                        val refArtistEntity = ArtistEntity(
                            id = refArtistId,
                            name = ref.name,
                            trackCount = 1,
                            imageUrl = null
                        )
                        artistsToInsert.add(refArtistEntity)
                        crossRefsToInsert.add(
                            SongArtistCrossRef(
                                songId = songId,
                                artistId = refArtistId,
                                isPrimary = ref.isPrimary
                            )
                        )
                    }
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
        }
    }
    private fun isSameSongId(id1: String?, id2: String?): Boolean {
        if (id1 == null || id2 == null) return false
        if (id1 == id2) return true
        val clean1 = id1.removePrefix("youtube_").removePrefix("youtube://")
        val clean2 = id2.removePrefix("youtube_").removePrefix("youtube://")
        return clean1 == clean2
    }

    private suspend fun resolveQuickPicksVideoId(first: com.unshoo.pixelmusic.data.model.Song): String? {
        var videoId: String? = when {
            !first.youtubeId.isNullOrBlank() && !first.youtubeId.startsWith("-15") -> first.youtubeId
            first.id.startsWith("youtube_") -> first.id.substringAfter("youtube_")
            first.contentUriString.startsWith("youtube://") -> first.contentUriString.removePrefix("youtube://")
            first.id.length == 11 && !first.id.contains(" ") && !first.id.startsWith("external:") &&
                first.id.all { it.isLetterOrDigit() || it == '-' || it == '_' } -> first.id
            else -> null
        }
        if (videoId.isNullOrBlank() || videoId.startsWith("-15")) {
            val longId = first.id.toLongOrNull()
            if (longId != null) {
                val dbSong = withContext(Dispatchers.IO) {
                    runCatching { musicDao.getSongByIdOnce(longId) }.getOrNull()
                }
                if (dbSong?.contentUriString?.startsWith("youtube://") == true) {
                    videoId = dbSong.contentUriString.removePrefix("youtube://")
                }
            }
        }
        if (videoId.isNullOrBlank() || videoId.startsWith("-15")) {
            videoId = withContext(Dispatchers.IO) {
                try {
                    val query = "${first.title} ${first.artist}"
                    val searchResult = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.search(query, unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val songItem = searchResult?.items?.firstOrNull { it is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem } as? unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
                    songItem?.id
                } catch (e: Exception) {
                    null
                }
            }
        }
        return videoId?.takeIf { it.isNotBlank() && !it.startsWith("-15") }
    }
    /**
     * Quick Picks lists are intentionally short (<= 20 songs). When the user taps one,
     * playback must start instantly on the small list, and the queue is then topped
     * back up to the auto-queue target (45) from the tapped song's radio.
     *
     * Sequence:
     *  1. Wait (up to 5s) until the tapped song is actually the current one — the
     *     pending playSongs() build may still be hydrating/saving to DB, and
     *     internalPlaySongs() calls AutoQueueManager.reset() before playback starts,
     *     which would wipe a seed planted too early.
     *  2. Resolve a YouTube radio seed (search fallback covers local songs too).
     *  3. Defer 1500ms so tap-to-play keeps full priority (same defer convention the
     *     auto-queue uses elsewhere), then force a refill that appends radio songs
     *     until the upcoming queue reaches the target.
     */
    fun scheduleQuickPicksQueueFill(seedSong: Song) {
        viewModelScope.launch {
            var retries = 0
            while (!isSameSongId(stablePlayerState.value.currentSong?.id, seedSong.id) && retries < 50) {
                delay(100)
                retries++
            }
            if (!isSameSongId(stablePlayerState.value.currentSong?.id, seedSong.id)) {
                // User moved on before the tapped song started — don't fight the new context.
                return@launch
            }

            val videoId = resolveQuickPicksVideoId(seedSong)
            if (!videoId.isNullOrBlank()) {
                val endpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = "RDAMVM$videoId"
                )
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                    endpoint = endpoint,
                    continuation = null,
                    videoId = videoId
                )
            } else {
                // Local song with no resolvable video: skip seeding; the refill's
                // local-related path still fills from the device library.
                Timber.w("Quick Picks fill: no videoId resolved for '${seedSong.title}', using local fill")
            }

            com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleRefill(
                delayMs = 600L,
                forceRefresh = true
            )
        }
    }

    fun playQuickPicksRadio(quickPicks: List<Song>) {
        if (quickPicks.isEmpty()) return
        val first = quickPicks.first()
        // CRITICAL FIX: If seed is a YouTube song, save it to DB first so that
        // the player/history system doesn't crash on missing artist_id FK.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                saveYoutubeSongsToDb(listOf(first))
            }
            playSongs(listOf(first), first, "Quick Picks Radio")
        }
        
        viewModelScope.launch {
            val videoId = resolveQuickPicksVideoId(first)
            if (videoId.isNullOrBlank()) {
                Timber.w("Could not resolve videoId for Quick Picks Radio seed song")
                return@launch
            }
            val endpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                videoId = videoId,
                playlistId = "RDAMVM$videoId"
            )
            
            _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            val result = withContext(Dispatchers.IO) {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.next(endpoint)
            }
            result.onSuccess { nextResult ->
                val relatedSongs = nextResult.items.map { it.toNativeSong() }
                if (relatedSongs.isNotEmpty()) {
                    val filteredRelated = relatedSongs.filter { it.youtubeId != videoId && it.id != first.id }
                    val fullQueue = (listOf(first) + filteredRelated).take(50)
                    
                    saveYoutubeSongsToDb(fullQueue)
                    
                    // Delay/wait until the player starts playing the first song to avoid race conditions
                    var retries = 0
                    while (stablePlayerState.value.currentSong?.id != first.id && retries < 50) {
                        delay(100)
                        retries++
                    }
                    
                    if (stablePlayerState.value.currentSong?.id == first.id) {
                        try {
                            val newSongs = fullQueue.drop(1)
                            val mediaItems = newSongs.map { MediaItemBuilder.build(it) }.toMutableList()
                            if (mediaItems.isNotEmpty()) {
                                runCatching {
                                    mediaItems[0] = dualPlayerEngine.preResolveForPlayback(mediaItems[0])
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val player = dualPlayerEngine.masterPlayer
                                val totalCount = player.mediaItemCount
                                if (totalCount > 1) {
                                    player.removeMediaItems(1, totalCount)
                                }
                                player.addMediaItems(mediaItems)
                                _playerUiState.update {
                                    it.copy(
                                        currentPlaybackQueue = fullQueue.toPlaybackQueue(),
                                        currentQueueSourceName = "Quick Picks Radio"
                                    )
                                }
                            }
                            if (mediaItems.size > 1) {
                                launch(Dispatchers.IO) {
                                    mediaItems.drop(1).take(3).forEach { item ->
                                        runCatching { dualPlayerEngine.preResolveForPlayback(item) }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error dynamically updating ExoPlayer queue for quick picks radio")
                        }
                    }
                    
                    val lastSong = fullQueue.last()
                    // Only seed from a valid YouTube video ID; fall back to the original
                    // videoId when the last song in the queue is a local-only track.
                    val lastVideoId = lastSong.youtubeId
                        ?: if (lastSong.id.startsWith("youtube_")) lastSong.id.substringAfter("youtube_") else videoId
                    if (!lastVideoId.isNullOrBlank()) {
                        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                            endpoint = nextResult.endpoint ?: endpoint,
                            continuation = nextResult.continuation,
                            videoId = lastVideoId
                        )
                    }
                } else {
                    // Radio page returned no usable items — hand over to AutoQueueManager
                    // so the queue still builds up from the seed song instead of staying
                    // at a single track.
                    Timber.w("Quick Picks Radio: empty radio page, falling back to AutoQueueManager")
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleAdaptiveRefill(forceRefresh = false)
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }.onFailure { e ->
                Timber.e(e, "Failed to build quick picks radio")
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
                // Initial radio build failed (network / rate limit) — fall back to the
                // AutoQueueManager so the queue is still built around the seed song.
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleAdaptiveRefill(forceRefresh = false)
            }
        }
    }

    fun playWithArchiveTuneQueueBuilder(
        song: Song,
        queueName: String = "Current Context",
        playlistId: String? = null
    ) {
        Log.i("ArchiveTuneBuilder", "playWithArchiveTuneQueueBuilder: starting for song '${song.title}' (${song.id})")
        val requestToken = beginDirectPlaybackRequest()

        val currentMediaId = dualPlayerEngine.masterPlayer.currentMediaItem?.mediaId
        val isSameTrack = (currentMediaId != null && (
            currentMediaId == song.id ||
            (song.youtubeId != null && currentMediaId == song.youtubeId) ||
            currentMediaId.removePrefix("youtube_") == song.id.removePrefix("youtube_")
        ))
        val isAlreadyPlaying = isSameTrack &&
            (dualPlayerEngine.masterPlayer.isPlaying || dualPlayerEngine.masterPlayer.playWhenReady) &&
            dualPlayerEngine.masterPlayer.mediaItemCount > 1

        if (!isAlreadyPlaying) {
            setPreparingSong(song.id)
        }

        // 1. Play the seed song immediately so there is zero delay!
        if (!isAlreadyPlaying) {
            directPlaybackJob = viewModelScope.launch {
                if (isDirectPlaybackRequestStale(requestToken)) return@launch
                withContext(Dispatchers.IO) {
                    saveYoutubeSongsToDb(listOf(song))
                }
                if (isDirectPlaybackRequestStale(requestToken)) return@launch
                internalPlaySongs(
                    songsToPlay = listOf(song),
                    startSong = song,
                    queueName = queueName,
                    playlistId = playlistId,
                    requestToken = requestToken,
                    skipAutoQueueSchedule = true
                )
                if (requestToken == directPlaybackToken) {
                    directPlaybackJob = null
                }
            }
        }

        // 2. Fetch related recommendations in the background and update the player's queue.
        directPlaybackRecommendationsJob = viewModelScope.launch {
            val videoId = resolveQuickPicksVideoId(song)
            if (videoId.isNullOrBlank()) {
                Timber.w("ArchiveTune Queue Builder: Could not resolve videoId for seed song '${song.title}'")
                return@launch
            }
            if (isDirectPlaybackRequestStale(requestToken)) return@launch

            val endpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                videoId = videoId,
                playlistId = "RDAMVM$videoId"
            )

            _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            val result = withContext(Dispatchers.IO) {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.next(endpoint)
            }
            if (isDirectPlaybackRequestStale(requestToken)) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
                return@launch
            }

            result.onSuccess { nextResult ->
                var relatedSongs = nextResult.items.map { it.toNativeSong() }
                val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
                val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
                if (filterCoverAndLofi) {
                    relatedSongs = relatedSongs.filterNot { ContentFilterUtils.isCoverOrLofi(it, filterKeywords) }
                }
                if (relatedSongs.isNotEmpty()) {
                    val fullQueue = withContext(Dispatchers.IO) {
                        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.buildMixQueue(song, relatedSongs)
                    }

                    launch(Dispatchers.IO) {
                        try {
                            saveYoutubeSongsToDb(fullQueue)
                        } catch (e: Exception) {
                            Timber.e(e, "ArchiveTune Queue Builder: Failed to save queue songs to DB in background")
                        }
                    }

                    if (isDirectPlaybackRequestStale(requestToken)) return@launch

                    try {
                        val newSongs = fullQueue.drop(1)
                        val mediaItems = newSongs.map { MediaItemBuilder.build(it) }.toMutableList()
                        if (mediaItems.isNotEmpty()) {
                            runCatching {
                                mediaItems[0] = dualPlayerEngine.preResolveForPlayback(mediaItems[0])
                            }
                        }

                        // Await seed song playback applying into player before appending recommendations
                        directPlaybackJob?.join()
                        directPlaybackApplyJob?.join()

                        if (isDirectPlaybackRequestStale(requestToken)) return@launch

                        withContext(Dispatchers.Main.immediate) {
                            if (isDirectPlaybackRequestStale(requestToken)) return@withContext
                            val player = dualPlayerEngine.masterPlayer
                            val totalCount = player.mediaItemCount
                            val activeIndex = player.currentMediaItemIndex
                            if (activeIndex in 0 until totalCount) {
                                if (totalCount > activeIndex + 1) {
                                    player.removeMediaItems(activeIndex + 1, totalCount)
                                }
                                if (activeIndex > 0) {
                                    player.removeMediaItems(0, activeIndex)
                                }
                            } else if (totalCount > 1) {
                                player.removeMediaItems(1, totalCount)
                            }
                            player.addMediaItems(mediaItems)
                            _playerUiState.update {
                                it.copy(
                                    currentPlaybackQueue = fullQueue.toPlaybackQueue(),
                                    currentQueueSourceName = queueName
                                )
                            }
                            persistPlaybackSnapshotFromViewModel(
                                queue = fullQueue,
                                currentSong = song,
                                playWhenReady = true,
                                currentIndex = 0,
                                currentPositionMs = player.currentPosition
                            )
                        }
                        if (mediaItems.size > 1) {
                            launch(Dispatchers.IO) {
                                mediaItems.drop(1).take(3).forEach { item ->
                                    runCatching { dualPlayerEngine.preResolveForPlayback(item) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "ArchiveTune Queue Builder: Error dynamically updating ExoPlayer queue")
                    }

                    val lastSong = fullQueue.last()
                    val lastVideoId = lastSong.youtubeId
                        ?: if (lastSong.id.startsWith("youtube_")) lastSong.id.substringAfter("youtube_") else videoId
                    if (!lastVideoId.isNullOrBlank()) {
                        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                            endpoint = nextResult.endpoint ?: endpoint,
                            continuation = nextResult.continuation,
                            videoId = lastVideoId
                        )
                    }
                } else {
                    // Empty related tracks: hand over to AutoQueueManager to refill
                    Timber.w("ArchiveTune Queue Builder: Empty related songs from radio, falling back to AutoQueueManager")
                    val fallbackEndpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                        videoId = videoId,
                        playlistId = "RDAMVM$videoId"
                    )
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                        endpoint = fallbackEndpoint,
                        continuation = null,
                        videoId = videoId
                    )
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleAdaptiveRefill(delayMs = 200L, forceRefresh = true)
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }.onFailure { e ->
                Timber.e(e, "ArchiveTune Queue Builder: Failed to fetch related queue, falling back to AutoQueueManager")
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
                val fallbackEndpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = "RDAMVM$videoId"
                )
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.seed(
                    endpoint = fallbackEndpoint,
                    continuation = null,
                    videoId = videoId
                )
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleAdaptiveRefill(delayMs = 200L, forceRefresh = true)
            }
        }
    }


    private fun List<Song>.matchesSongOrder(contextSongs: List<Song>): Boolean {
        if (size != contextSongs.size) return false
        return indices.all { this[it].id == contextSongs[it].id }
    }

    private fun MediaController.resolveReusablePlaybackTargetIndex(
        songIndexInQueue: Int,
        songId: String
    ): Int? {
        currentMediaItem?.takeIf { it.mediaId == songId }?.let {
            return currentMediaItemIndex.takeIf { index -> index != C.INDEX_UNSET } ?: 0
        }

        if (songIndexInQueue in 0 until mediaItemCount) {
            val mediaIdAtTarget = runCatching { getMediaItemAt(songIndexInQueue).mediaId }.getOrNull()
            if (mediaIdAtTarget == songId) return songIndexInQueue
        }

        // Search the timeline if display index differs from raw player order (e.g. shuffled queue)
        for (i in 0 until mediaItemCount) {
            val itemMediaId = runCatching { getMediaItemAt(i).mediaId }.getOrNull()
            if (itemMediaId == songId) return i
        }

        return null
    }

    private fun playLoadedControllerItem(controller: MediaController, targetIndex: Int, targetSong: Song? = null): Job {
        // No optimistic UI — the miniplayer reflects real player state only (Saurav behavior).
        // Keep the preparing-song guard (race fix): prevents the null-transition/STATE_IDLE
        // handler from clearing currentSong while the seek is still in flight.
        runOnMainImmediate {
            targetSong?.let { song -> setPreparingSong(song.id) }
        }

        // FREEZE FIX: queue reuse used to seekTo() a still-unresolved youtube:// URI.
        // ResolvingDataSource no longer runBlocks, so that left the player stuck until
        // force-stop. Resolve the target item (LOW stream) before seek/prepare/play.
        return viewModelScope.launch {
            try {
                val targetItem = withContext(Dispatchers.Main.immediate) {
                    runCatching {
                        if (targetIndex in 0 until controller.mediaItemCount) {
                            controller.getMediaItemAt(targetIndex)
                        } else null
                    }.getOrNull()
                }

                val scheme = targetItem?.localConfiguration?.uri?.scheme
                val needsResolve = scheme == "youtube" || scheme == "telegram" ||
                    scheme == "gdrive" || scheme == "netease" || scheme == "qqmusic" ||
                    scheme == "navidrome" || scheme == "jellyfin"

                val resolvedItem = if (targetItem != null && needsResolve) {
                    withContext(Dispatchers.IO) {
                        // 3s budget for queue-skip path: the URL should already be in
                        // resolvedUriCache from the initial queue build, so this is only
                        // needed on a cache miss. 3s is generous without blocking navigation.
                        kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                            dualPlayerEngine.preResolveForPlayback(targetItem)
                        } ?: targetItem
                    }
                } else {
                    targetItem
                }

                withContext(Dispatchers.Main.immediate) {
                    if (resolvedItem != null && needsResolve &&
                        targetIndex in 0 until controller.mediaItemCount
                    ) {
                        // Prefer engine replace so master player sees the http(s) URI.
                        runCatching {
                            val master = dualPlayerEngine.masterPlayer
                            if (targetIndex in 0 until master.mediaItemCount) {
                                master.replaceMediaItem(targetIndex, resolvedItem)
                            }
                        }
                        runCatching {
                            if (targetIndex in 0 until controller.mediaItemCount) {
                                // MediaController may not expose replaceMediaItem on all versions;
                                // engine replace above is authoritative.
                            }
                        }
                    }

                    val shouldSeekToStart =
                        controller.currentMediaItemIndex != targetIndex ||
                            controller.playbackState == Player.STATE_ENDED

                    if (shouldSeekToStart) {
                        controller.seekTo(targetIndex, 0L)
                    }
                    if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                        controller.prepare()
                    }
                    controller.playWhenReady = true
                    controller.play()
                    controller.currentMediaItem?.let { registerYoutubePlaybackHistoryIfNeeded(it) }
                }

                // Stuck recovery: poll every 500ms so the coroutine exits immediately
                // once playback starts (typical case on good networks: 1-3s).
                // Only trigger the expensive re-resolve if still stuck after 8s.
                // PERF: blind delay(8_000L) was holding the coroutine alive for the full
                // 8s even on songs that started playing fine after 1.5s.
                var stuckAfterPolling = false
                run {
                    var elapsed = 0L
                    while (elapsed < 8_000L) {
                        delay(500L)
                        elapsed += 500L
                        // Exit early the moment playback has begun — no recovery needed.
                        if (controller.isPlaying || controller.currentPosition > 500L) return@run
                    }
                    // Only reach here if still stuck after 8s — mark for recovery below
                    withContext(Dispatchers.Main.immediate) {
                        stuckAfterPolling = controller.currentMediaItemIndex == targetIndex &&
                            controller.playWhenReady &&
                            !controller.isPlaying &&
                            controller.currentPosition < 500L &&
                            controller.playbackState != Player.STATE_READY &&
                            !dualPlayerEngine.isTransitionRunning()
                        if (stuckAfterPolling) {
                            Timber.w("playLoadedControllerItem genuinely stuck after 8s — re-resolving LOW stream")
                            val stuckItem = runCatching { controller.getMediaItemAt(targetIndex) }.getOrNull()
                            val uri = stuckItem?.localConfiguration?.uri?.toString()
                            if (!uri.isNullOrBlank()) {
                                dualPlayerEngine.invalidateResolvedUri(uri)
                            }
                        }
                    }
                }
                if (!stuckAfterPolling) return@launch
                val item = withContext(Dispatchers.Main.immediate) {
                    runCatching { controller.getMediaItemAt(targetIndex) }.getOrNull()
                } ?: return@launch
                val retried = withContext(Dispatchers.IO) {
                    runCatching { dualPlayerEngine.preResolveForPlayback(item) }.getOrNull()
                }
                withContext(Dispatchers.Main.immediate) {
                    if (retried != null && targetIndex in 0 until dualPlayerEngine.masterPlayer.mediaItemCount) {
                        dualPlayerEngine.masterPlayer.replaceMediaItem(targetIndex, retried)
                    }
                    controller.prepare()
                    controller.seekTo(targetIndex, 0L)
                    controller.playWhenReady = true
                    controller.play()
                }
            } catch (e: Exception) {
                Timber.e(e, "playLoadedControllerItem failed")
                runOnMainImmediate {
                    clearPreparingSongIfMatching(targetSong?.id)
                    runCatching {
                        controller.seekTo(targetIndex, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }
            } finally {
                if (coroutineContext[Job]?.isCancelled == true) {
                    runOnMainImmediate {
                        clearPreparingSongIfMatching(targetSong?.id)
                    }
                }
            }
        }
    }

    private fun Song.requiresHydration(): Boolean {
        return contentUriString.isBlank()
    }

    private suspend fun hydrateSongsIfNeeded(songs: List<Song>): List<Song> {
        if (songs.isEmpty() || songs.none { it.requiresHydration() }) return songs
        val hydratedSongs = getSongsByIdsChunked(songs.map { it.id })
        if (hydratedSongs.isEmpty()) return songs
        val hydratedById = hydratedSongs.associateBy { it.id }
        return songs.mapNotNull { original ->
            hydratedById[original.id] ?: original.takeIf { !original.requiresHydration() }
        }
    }

    suspend fun insertYoutubeSongs(songs: List<Song>) {
        musicRepository.insertYoutubeSongs(songs)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            try {
                val mappedBrowseId = SearchStateHolder.albumIdMap[album.id]
                val songsList: List<Song> = if (mappedBrowseId != null) {
                    withContext(Dispatchers.IO) {
                        val result = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.album(mappedBrowseId)
                        if (result.isSuccess) {
                            val albumPage = result.getOrThrow()
                            val mappedSongs = albumPage.songs.map { it.toNativeSong() }
                            if (mappedSongs.isNotEmpty()) {
                                musicRepository.insertYoutubeSongs(mappedSongs)
                            }
                            mappedSongs
                        } else {
                            emptyList()
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        musicRepository.getSongsForAlbum(album.id).first()
                    }
                }

                if (songsList.isNotEmpty()) {
                    val sortedSongs = if (mappedBrowseId != null) {
                        songsList
                    } else {
                        songsList.sortedWith(
                            compareBy<Song> { it.discNumber ?: 1 }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                    }

                    playSongs(sortedSongs, sortedSongs.first(), album.title, null)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    Log.w("PlayerViewModel", "Album '${album.title}' has no playable songs.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing album ${album.title}", e)
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForArtist(artist.id).first()
                }

                if (songsList.isNotEmpty()) {
                    playSongs(songsList, songsList.first(), artist.name, null)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Artist '${artist.name}' has no playable songs.")
                    // podrías emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing artist ${artist.name}", e)
            }
        }
    }

    fun removeSongFromQueue(songId: String) {
        queueUndoStateHolder.removeSongFromQueue(
            scope = viewModelScope,
            mediaController = mediaController,
            songId = songId,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun undoRemoveSongFromQueue() {
        queueUndoStateHolder.undoRemoveSongFromQueue(
            mediaController = mediaController,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun hideQueueItemUndoBar() {
        queueUndoStateHolder.hideQueueItemUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        runOnMainImmediate {
            val controller = mediaController ?: return@runOnMainImmediate
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {
                val currentIndexBeforeMove = controller.currentMediaItemIndex
                    .takeIf { it != C.INDEX_UNSET }
                    ?: playbackStateHolder.stablePlayerState.value.currentMediaItemIndex
                val updatedCurrentIndex = moveQueueIndex(currentIndexBeforeMove, fromIndex, toIndex)

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

                // Optimistically mirror the committed move in UI state. The drag preview stays
                // local while dragging, so this single state update does not add per-frame work.
                _playerUiState.update { state ->
                    val updatedQueue = state.currentPlaybackQueue.moveSong(fromIndex, toIndex)
                    if (updatedQueue === state.currentPlaybackQueue) {
                        state
                    } else {
                        state.copy(currentPlaybackQueue = updatedQueue)
                    }
                }

                playbackStateHolder.updateStablePlayerState { state ->
                    if (updatedCurrentIndex == C.INDEX_UNSET ||
                        state.currentMediaItemIndex == updatedCurrentIndex
                    ) {
                        state
                    } else {
                        state.copy(currentMediaItemIndex = updatedCurrentIndex)
                    }
                }
            }
        }
    }

    fun togglePlayerSheetState(resetPredictiveState: Boolean = true) {
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun expandPlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.EXPANDED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun collapsePlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.COLLAPSED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun triggerAlbumNavigationFromPlayer(albumId: Long) {
        triggerAlbumNavigationFromPlayer(albumId.toString())
    }

    fun triggerAlbumNavigationFromPlayer(albumIdStr: String) {
        if (albumIdStr.isBlank() || albumIdStr == "-1" || albumIdStr == "0") {
            return
        }

        val existingJob = albumNavigationJob
        if (existingJob != null && existingJob.isActive) {
            return
        }

        albumNavigationJob?.cancel()
        albumNavigationJob = viewModelScope.launch {
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _albumNavigationRequests.emit(albumIdStr)
        }
    }

    fun triggerArtistNavigationFromPlayer(artistId: Long) {
        triggerArtistNavigationFromPlayer(artistId.toString())
    }

    fun triggerArtistNavigationFromPlayer(artistIdStr: String) {
        if (artistIdStr.isBlank() || artistIdStr == "0" || artistIdStr == "-1") {
            return
        }

        val existingJob = artistNavigationJob
        if (existingJob != null && existingJob.isActive) {
            return
        }

        artistNavigationJob?.cancel()
        artistNavigationJob = viewModelScope.launch {
            var resolvedId = artistIdStr
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            
            if (resolvedId == "-1" && currentSong != null) {
                val idFromName = musicRepository.getArtistIdByName(currentSong.artist)
                if (idFromName != null) {
                    resolvedId = idFromName.toString()
                }
            }

            if (resolvedId == "0" || resolvedId == "-1") {
                return@launch
            }

            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _artistNavigationRequests.emit(resolvedId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    private fun resolveSongFromMediaItem(
        mediaItem: MediaItem,
        allSongsById: Map<String, Song>? = null
    ): Song? {
        val resolvedSong =
            allSongsById?.get(mediaItem.mediaId)
                ?: libraryStateHolder.allSongsById.value[mediaItem.mediaId]
                ?: _playerUiState.value.currentPlaybackQueue.find { it.id == mediaItem.mediaId }
                ?: mediaMapper.resolveSongFromMediaItem(mediaItem)
                ?: playbackStateHolder.stablePlayerState.value.currentSong?.takeIf { it.id == mediaItem.mediaId }

        return resolvedSong?.let { normalizeArtworkForResolvedSong(it, mediaItem) }
    }

    private fun normalizeArtworkForResolvedSong(song: Song, mediaItem: MediaItem): Song {
        val metadataArtwork =
            mediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }
                ?: mediaItem.mediaMetadata.extras
                    ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
                    ?.takeIf { it.isNotBlank() }

        return when {
            metadataArtwork == null && song.albumArtUriString != null -> song.copy(albumArtUriString = null)
            metadataArtwork != null && song.albumArtUriString != metadataArtwork ->
                song.copy(albumArtUriString = metadataArtwork)
            else -> song
        }
    }

    private var lastQueueUpdateRequestId = 0L
    private var lastQueueSignature: QueueTimelineSignature? = null
    private var lastQueueUpdateJob: Job? = null

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController: androidx.media3.common.Player = playerCtrl ?: mediaController ?: dualPlayerEngine.masterPlayer
        val requestId = ++lastQueueUpdateRequestId
        lastQueueUpdateJob?.cancel()
        lastQueueUpdateJob = viewModelScope.launch {
            // Debounce slightly to handle rapid-fire timeline events
            delay(100)

            // Do not overwrite the queue while shuffle/unshuffle transition is mutating the timeline
            if (playbackStateHolder.stablePlayerState.value.isShuffleTransitionInProgress) {
                return@launch
            }
            
            val timeline = currentMediaController.currentTimeline
            val count = timeline.windowCount
            if (count == 0) {
                if (requestId != lastQueueUpdateRequestId) return@launch
                val emptySignature = QueueTimelineSignature(
                    count = 0,
                    orderHash = 0L,
                    firstMediaId = null,
                    lastMediaId = null
                )
                lastQueueSignature = emptySignature
                return@launch
            }

            val mediaItems = ArrayList<MediaItem>(count)
            val window = Timeline.Window()
            var orderHash = 1125899906842597L
            var firstMediaId: String? = null
            var lastMediaId: String? = null

            for (i in 0 until count) {
                val mediaItem = timeline.getWindow(i, window).mediaItem
                mediaItems.add(mediaItem)
                val mediaId = mediaItem.mediaId
                if (i == 0) firstMediaId = mediaId
                if (i == count - 1) lastMediaId = mediaId
                orderHash = (orderHash * 31) + mediaId.hashCode()
                if (i % 500 == 0) kotlinx.coroutines.yield()
            }

            val signature = QueueTimelineSignature(
                count = count,
                orderHash = orderHash,
                firstMediaId = firstMediaId,
                lastMediaId = lastMediaId
            )
            if (requestId != lastQueueUpdateRequestId) return@launch
            if (signature == lastQueueSignature) return@launch

            val allSongsById = libraryStateHolder.allSongsById.value
            
            val queue = withContext(Dispatchers.Default) {
                mediaItems.mapNotNull { mediaItem ->
                    resolveSongFromMediaItem(mediaItem, allSongsById)
                }
            }

            if (requestId != lastQueueUpdateRequestId) return@launch

            lastQueueSignature = signature
            if (queue.isNotEmpty() || count == 0) {
                _playerUiState.update { it.copy(currentPlaybackQueue = queue.toPlaybackQueue()) }
            }
        }
    }

    fun persistPlaybackSnapshotFromViewModel(
        queue: List<Song>,
        currentSong: Song?,
        playWhenReady: Boolean = false,
        currentIndex: Int? = null,
        currentPositionMs: Long? = null
    ) {
        if (queue.isEmpty()) return
        val effectiveCurrentSong = currentSong ?: queue.firstOrNull() ?: return
        val resolvedCurrentIndex = currentIndex ?: queue.indexOfFirst { it.id == effectiveCurrentSong.id }.coerceAtLeast(0)
        val position = currentPositionMs ?: playbackStateHolder.currentPosition.value
        val isShuffle = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
        val repeat = playbackStateHolder.stablePlayerState.value.repeatMode

        val snapshotItems = queue.map { song ->
            val uri = when {
                song.contentUriString.isNotBlank() -> song.contentUriString
                song.id.startsWith("youtube_") -> "youtube://${song.id.removePrefix("youtube_")}"
                song.youtubeId != null -> "youtube://${song.youtubeId}"
                else -> song.path
            }
            PlaybackQueueItemSnapshot(
                mediaId = song.id,
                uri = uri,
                title = song.title,
                artist = song.artist,
                albumTitle = song.album,
                artworkUri = song.albumArtUriString,
                durationMs = song.duration.takeIf { it > 0L }
            )
        }

        val snapshot = PlaybackQueueSnapshot(
            items = snapshotItems,
            currentMediaId = effectiveCurrentSong.id,
            currentIndex = resolvedCurrentIndex,
            currentPositionMs = position,
            playWhenReady = playWhenReady,
            repeatMode = repeat,
            shuffleEnabled = isShuffle
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
            }.onFailure {
                Timber.w(it, "Failed to persist playback snapshot from PlayerViewModel")
            }
        }
    }

    private fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        runOnMainImmediate {
            val controller = mediaController
            if (controller == null) {
                pendingRepeatMode = mode
                return@runOnMainImmediate
            }

            if (controller.repeatMode != mode) {
                controller.repeatMode = mode
            }
            pendingRepeatMode = null
        }
    }

    private fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    private var dynamicMetadataRefreshJob: Job? = null

    private fun scheduleDynamicPlaybackMetadataRefresh(player: Player, targetMediaId: String?) {
        if (targetMediaId == null) return
        dynamicMetadataRefreshJob?.cancel()
        dynamicMetadataRefreshJob = viewModelScope.launch(Dispatchers.Main) {
            val delays = listOf(400L, 1000L, 2000L, 3500L, 5000L, 7000L)
            for (probeDelay in delays) {
                delay(probeDelay)
                if (player.currentMediaItem?.mediaId != targetMediaId) break
                refreshPlaybackAudioMetadata(player)
            }
        }
    }

    private fun resetPlaybackAudioMetadata() {
        dynamicMetadataRefreshJob?.cancel()
        dynamicMetadataRefreshJob = null
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        _playbackAudioMetadata.value = PlaybackAudioMetadata()
    }

    private fun monochromeMetadata(mediaId: String, bitrate: Int? = null, sampleRate: Int? = null, bitDepth: Int? = null) = PlaybackAudioMetadata(
        mediaId = mediaId,
        mimeType = "audio/flac",
        bitrate = bitrate?.takeIf { it > 0 } ?: 1411_200,
        sampleRate = sampleRate ?: 44100,
        bitDepth = bitDepth ?: 16,
        formatTag = "LOSSLESS",
        sourceName = "Monochrome (FLAC)",
        isConfirmed = true
    )

    private fun preparePlaybackAudioMetadataForMedia(mediaId: String?) {
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        if (mediaId == null) {
            _playbackAudioMetadata.value = PlaybackAudioMetadata()
            return
        }

        // 1. Check if LosslessStreamResolver has a cached resolution for this track
        val cleanMediaId = mediaId.removePrefix("youtube_")
        val losslessResult = com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver
            .resultFor(cleanMediaId)
            ?: com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.resultFor(mediaId)
        if (losslessResult != null) {
            val tag = when {
                losslessResult.isHiRes -> "HI-RES LOSSLESS"
                losslessResult.isLossless -> "LOSSLESS"
                else -> null
            }
            val srcName = if (losslessResult.source == com.unshoo.pixelmusic.data.lossless.constants.AudioSourceType.MONOCHROME) {
                "Monochrome (FLAC)"
            } else {
                losslessResult.source.name
            }
            _playbackAudioMetadata.value = PlaybackAudioMetadata(
                mediaId = mediaId,
                mimeType = losslessResult.mimeType,
                bitrate = losslessResult.bitrate.takeIf { it > 0 } ?: 1411_200,
                sampleRate = losslessResult.sampleRate ?: 44100,
                bitDepth = losslessResult.bitDepth ?: 16,
                formatTag = tag,
                sourceName = srcName,
                isConfirmed = true
            )
            return
        }

        // 1b. Check if song or mediaId represents Monochrome FLAC
        val song = _playerUiState.value.currentPlaybackQueue.find { it.id == mediaId }
            ?: libraryStateHolder.allSongsById.value[mediaId]
            ?: runCatching { mediaController?.currentMediaItem?.let(::resolveSongFromMediaItem) }.getOrNull()

        if (mediaId.startsWith("monochrome_") || song?.contentUriString?.startsWith("monochrome://") == true || song?.contentUriString?.contains("monochrome.st/track/") == true) {
            _playbackAudioMetadata.value = monochromeMetadata(mediaId)
            return
        }

        if (song != null && song.isLocal && song.path.isNotBlank()) {
            val path = song.path.lowercase()
            val isFlac = song.mimeType?.contains("flac", true) == true || path.endsWith(".flac")
            val isWav = song.mimeType?.contains("wav", true) == true || path.endsWith(".wav")
            val isAlac = song.mimeType?.contains("alac", true) == true || path.endsWith(".alac") || path.endsWith(".caf")
            val isHiRes = isFlac && ((song.sampleRate ?: 0) > 48000 || (song.bitrate ?: 0) > 1_500_000)

            val mime = when {
                isFlac -> "audio/flac"
                isWav -> "audio/wav"
                isAlac -> "audio/alac"
                path.endsWith(".m4a") || path.endsWith(".mp4") -> "audio/mp4"
                path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".ogg") -> "audio/ogg"
                path.endsWith(".opus") -> "audio/opus"
                path.endsWith(".webm") -> "audio/webm; codecs=\"opus\""
                else -> song.mimeType
            }
            val formatTag = when {
                isHiRes -> "HI-RES LOSSLESS"
                isFlac || isWav || isAlac -> "LOSSLESS"
                else -> null
            }
            val defaultSampleRate = when {
                isHiRes -> 96000
                mime?.contains("opus") == true -> 48000
                else -> 44100
            }
            _playbackAudioMetadata.value = PlaybackAudioMetadata(
                mediaId = mediaId,
                mimeType = mime,
                bitrate = song.bitrate,
                sampleRate = song.sampleRate ?: defaultSampleRate,
                bitDepth = if (isHiRes) 24 else if (isFlac || isWav || isAlac) 16 else null,
                formatTag = formatTag,
                sourceName = "Local Storage",
                isConfirmed = true
            )
            return
        }

        // 3. Online/remote streams (YouTube, JioSaavn):
        val isSaavn = song?.contentUriString?.contains("saavn") == true
        val videoId = song?.youtubeId ?: cleanMediaId
        val ytHelper = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
        val mime = ytHelper.streamMimeTypeLruCache.get("${videoId}_high")
            ?: ytHelper.streamMimeTypeLruCache.get("${videoId}_low")
            ?: if (isSaavn) "audio/mp4; codecs=\"mp4a.40.2\"" else "audio/webm; codecs=\"opus\""
        val bitrate = ytHelper.streamBitrateLruCache.get("${videoId}_high")?.takeIf { it > 65_000 }
            ?: ytHelper.streamBitrateLruCache.get("${videoId}_low")
            ?: if (isSaavn) 320_000 else 160_000
        val sampleRate = if (isSaavn || !mime.contains("opus", true)) 44100 else 48000

        _playbackAudioMetadata.value = PlaybackAudioMetadata(
            mediaId = mediaId,
            mimeType = mime,
            bitrate = bitrate,
            sampleRate = sampleRate,
            sourceName = if (isSaavn) "JioSaavn" else "YouTube",
            isConfirmed = false
        )
    }

    private fun com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.Result.toOfferedStream() =
        OfferedStream(
            // Include the codec token so e.g. audio/mp4 + alac is compared as ALAC, not AAC.
            mimeType = if (codecs.isBlank()) mimeType else "$mimeType; codecs=\"$codecs\"",
            bitrate = bitrate.takeIf { it > 0 },
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            sourceName = source.name,
            labelSaysHiRes = isHiRes,
        )

    private fun extractBitDepthFromPcmEncoding(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    private fun Player.selectedAudioFormat(): Format? =
        currentTracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).asSequence().filter(g::isTrackSelected).map(g::getTrackFormat) }
            .firstOrNull()
            ?: currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }?.getTrackFormat(0)

    private fun resolvePlayingUri(player: Player, mediaId: String?): Uri? {
        val rawUri = player.currentMediaItem?.localConfiguration?.uri
            ?: dualPlayerEngine.masterPlayer.currentMediaItem?.localConfiguration?.uri
            ?: player.currentMediaItem?.mediaMetadata?.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)?.let(Uri::parse)
            ?: _playerUiState.value.currentPlaybackQueue.find { it.id == mediaId }?.contentUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: libraryStateHolder.allSongsById.value[mediaId]?.contentUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return rawUri?.let { dualPlayerEngine.getCachedResolvedUri(it) } ?: rawUri
    }

    private fun parseSaavnBitrate(uri: String): Int = when {
        uri.contains("_320") -> 320_000
        uri.contains("_160") -> 160_000
        uri.contains("_96") -> 96_000
        else -> 320_000
    }

    private fun parseYoutubeItag(itag: String): Pair<Int, String> = when (itag) {
        "140" -> 128_000 to "audio/mp4; codecs=\"mp4a.40.2\""
        "171" -> 128_000 to "audio/webm; codecs=\"vorbis\""
        "250" -> 70_000 to "audio/webm; codecs=\"opus\""
        "249" -> 50_000 to "audio/webm; codecs=\"opus\""
        else -> 160_000 to "audio/webm; codecs=\"opus\""
    }

    private fun refreshPlaybackAudioMetadata(player: Player, tracks: Tracks = player.currentTracks) {
        runCatching {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId == null) {
                resetPlaybackAudioMetadata()
                return@runCatching
            }

            val cleanMediaId = mediaId.removePrefix("youtube_")
            val offered = com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.resultFor(cleanMediaId)
                ?: com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.resultFor(mediaId)

            val playingUri = resolvePlayingUri(player, mediaId)
            val playingUriString = playingUri?.toString().orEmpty()
            if (playingUriString.contains("monochrome.st/track/") || playingUriString.startsWith("monochrome://") || mediaId.startsWith("monochrome_") || offered?.source == com.unshoo.pixelmusic.data.lossless.constants.AudioSourceType.MONOCHROME) {
                _playbackAudioMetadata.value = monochromeMetadata(mediaId, offered?.bitrate, offered?.sampleRate, offered?.bitDepth)
                return@runCatching
            }
            if (playingUriString.contains("saavncdn.com") || playingUriString.contains("jiosaavn.com")) {
                _playbackAudioMetadata.value = PlaybackAudioMetadata(
                    mediaId = mediaId,
                    mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                    bitrate = parseSaavnBitrate(playingUriString),
                    sampleRate = 44100,
                    sourceName = "JioSaavn",
                    isConfirmed = true
                )
                return@runCatching
            }

            val selectedAudioFormat = player.selectedAudioFormat() ?: dualPlayerEngine.masterPlayer.selectedAudioFormat()
            val previous = _playbackAudioMetadata.value.takeIf {
                it.mediaId == mediaId || it.mediaId?.removePrefix("youtube_") == cleanMediaId
            }

            if (selectedAudioFormat == null) {
                if (playingUriString.startsWith("http") || playingUriString.startsWith("youtube://") || playingUri?.scheme == "youtube") {
                    maybeProbeMissingPlaybackAudioMetadata(player, _playbackAudioMetadata.value)
                } else if (previous == null) {
                    preparePlaybackAudioMetadataForMedia(mediaId)
                }
                return@runCatching
            }

            val metadata = PlaybackStreamReconciler.reconcile(
                mediaId = mediaId,
                confirmed = ConfirmedAudioFormat(
                    sampleMimeType = selectedAudioFormat.sampleMimeType,
                    containerMimeType = selectedAudioFormat.containerMimeType,
                    bitrate = selectedAudioFormat.bitrate.takeIf { it > 0 },
                    sampleRate = selectedAudioFormat.sampleRate.takeIf { it > 0 },
                    channelCount = selectedAudioFormat.channelCount.takeIf { it > 0 },
                    bitDepth = extractBitDepthFromPcmEncoding(selectedAudioFormat.pcmEncoding),
                ),
                offered = offered?.toOfferedStream(),
                previous = previous,
            )

            _playbackAudioMetadata.value = metadata
            maybeProbeMissingPlaybackAudioMetadata(player, metadata)
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to refresh playback audio metadata")
        }
    }

    private fun maybeProbeMissingPlaybackAudioMetadata(
        player: Player,
        metadata: PlaybackAudioMetadata
    ) {
        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val uri = resolvePlayingUri(player, mediaId) ?: return
        val playingUriString = uri.toString()

        val cleanMediaId = mediaId.removePrefix("youtube_")
        if (com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.resultFor(cleanMediaId) != null ||
            com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.resultFor(mediaId) != null) return

        if (metadata.isConfirmed && (metadata.bitrate ?: 0) >= 65_000 && !metadata.mimeType.isNullOrBlank()) return

        val isYoutube = (mediaId.startsWith("youtube_") || !mediaId.contains("/")) &&
                (playingUriString.contains("googlevideo.com") || playingUriString.contains("youtube.com") ||
                 playingUriString.startsWith("youtube://") || uri.scheme == "youtube")
        if (isYoutube) {
            val videoId = mediaId.removePrefix("youtube_")
            val ytHelper = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
            val cacheKey = ytHelper.streamUrlLruCache.snapshot().entries.find { it.value == playingUriString }?.key
            var cachedBitrate = cacheKey?.let { ytHelper.streamBitrateLruCache.get(it) }?.takeIf { it > 65_000 }
                ?: ytHelper.streamBitrateLruCache.get("${videoId}_high")
                ?: ytHelper.streamBitrateLruCache.get("${videoId}_low")
            var cachedMime = cacheKey?.let { ytHelper.streamMimeTypeLruCache.get(it) }
                ?: ytHelper.streamMimeTypeLruCache.get("${videoId}_high")
                ?: ytHelper.streamMimeTypeLruCache.get("${videoId}_low")

            if (cachedBitrate == null || cachedMime == null) {
                if (playingUriString.contains("saavncdn.com") || playingUriString.contains("jiosaavn.com")) {
                    cachedMime = "audio/mp4; codecs=\"mp4a.40.2\""
                    cachedBitrate = parseSaavnBitrate(playingUriString)
                } else {
                    val itag = playingUriString.substringAfter("itag=", "").substringBefore("&")
                    val (itagBitrate, itagMime) = parseYoutubeItag(itag)
                    if (cachedBitrate == null) cachedBitrate = itagBitrate
                    if (cachedMime == null) cachedMime = itagMime
                }
            }
            
            _playbackAudioMetadata.update { current ->
                if (current.mediaId != mediaId && current.mediaId?.removePrefix("youtube_") != cleanMediaId) return@update current
                if (current.isConfirmed && (current.bitrate ?: 0) > 65_000 && !current.mimeType.isNullOrBlank()) return@update current
                val resolvedMime = cachedMime ?: "audio/webm; codecs=\"opus\""
                val resolvedBitrate = cachedBitrate ?: 160_000
                val resolvedSampleRate = current.sampleRate ?: if (resolvedMime.contains("opus")) 48000 else 44100
                current.copy(
                    bitrate = resolvedBitrate,
                    mimeType = resolvedMime,
                    sampleRate = resolvedSampleRate,
                    sourceName = if (playingUriString.contains("saavn")) "JioSaavn" else "YouTube",
                    isConfirmed = true
                )
            }

            return
        }

        if (metadataProbeMediaId == mediaId && metadataProbeJob?.isActive == true) return

        metadataProbeJob?.cancel()
        metadataProbeMediaId = mediaId
        metadataProbeJob = viewModelScope.launch(Dispatchers.IO) {
            val probedMetadata = runCatching {
                var sourceUri = uri
                var expectedSize: Long? = null
                val uriStr = uri.toString()

                if (uriStr.contains("127.0.0.1") && uriStr.contains("/stream/")) {
                    val fileId = uriStr.substringAfter("/stream/").substringBefore("?").substringBefore("/").toIntOrNull()
                    if (fileId != null) {
                        val fileInfo = runCatching { musicRepository.telegramRepository.getFile(fileId) }.getOrNull()
                        if (fileInfo != null) {
                            expectedSize = fileInfo.expectedSize.takeIf { it > 0 }
                            if (!fileInfo.local.path.isNullOrEmpty()) {
                                val file = java.io.File(fileInfo.local.path)
                                if (file.exists() && file.canRead()) {
                                    sourceUri = Uri.fromFile(file)
                                }
                            }
                        }
                    }
                } else if (uriStr.startsWith("telegram://")) {
                    val fileId = runCatching { musicRepository.telegramRepository.resolveTelegramUri(uriStr)?.first }.getOrNull()
                    if (fileId != null) {
                        val fileInfo = runCatching { musicRepository.telegramRepository.getFile(fileId) }.getOrNull()
                        if (fileInfo != null) {
                            expectedSize = fileInfo.expectedSize.takeIf { it > 0 }
                            if (!fileInfo.local.path.isNullOrEmpty()) {
                                val file = java.io.File(fileInfo.local.path)
                                if (file.exists() && file.canRead()) {
                                    sourceUri = Uri.fromFile(file)
                                }
                            }
                        }
                    }
                }

                com.unshoo.pixelmusic.utils.MediaMetadataRetrieverPool.withRetriever { retriever ->
                    if (sourceUri.scheme == "file") {
                        retriever.setDataSource(sourceUri.path)
                    } else {
                        retriever.setDataSource(context, sourceUri)
                    }
                    val mimeType = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.isNotBlank() }
                        ?: context.contentResolver.getType(sourceUri)
                    val bitrate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    val sampleRate = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    } else null
                    
                    var finalBitrate = bitrate
                    if (finalBitrate == null || finalBitrate <= 0) {
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        if (durationMs != null && durationMs > 0) {
                            val sizeInBytes = expectedSize ?: (if (sourceUri.scheme == "file") sourceUri.path?.let { java.io.File(it).length() } else null)
                            if (sizeInBytes != null && sizeInBytes > 0) {
                                finalBitrate = ((sizeInBytes * 8) / (durationMs / 1000.0)).toInt()
                            }
                        }
                    }
                    
                    PlaybackAudioMetadata(
                        mediaId = mediaId,
                        mimeType = mimeType,
                        bitrate = finalBitrate,
                        sampleRate = sampleRate
                    )
                }
            }.getOrNull() ?: return@launch

            val localTag = when {
                (probedMetadata.sampleRate ?: 0) > 48000 || (probedMetadata.bitDepth ?: 0) >= 24 -> "HI-RES LOSSLESS"
                probedMetadata.mimeType?.contains("flac", true) == true ||
                    probedMetadata.mimeType?.contains("alac", true) == true ||
                    probedMetadata.mimeType?.contains("wav", true) == true -> "LOSSLESS"
                else -> null
            }
            _playbackAudioMetadata.update { current ->
                val isSameMediaItem = current.mediaId == mediaId
                if (!isSameMediaItem) return@update current
                current.copy(
                    mimeType = current.mimeType ?: probedMetadata.mimeType,
                    bitrate = current.bitrate ?: probedMetadata.bitrate,
                    sampleRate = current.sampleRate ?: probedMetadata.sampleRate,
                    formatTag = current.formatTag ?: localTag.takeIf { !current.isConfirmed }
                )
            }
        }
    }

    private fun isRemoteSessionControllingPlayback(): Boolean {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
        return remoteClient != null &&
                (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value)
    }

    private fun syncPlaybackPositionFromPlayer(
        mediaId: String?,
        reportedPositionMs: Long
    ): Long {
        playbackStateHolder.syncCurrentPositionFromPlayer(mediaId, reportedPositionMs)
        return playbackStateHolder.currentPosition.value
    }

    private fun syncDisplayedMediaItemIfChanged(player: Player) {
        if (isRemoteSessionControllingPlayback()) return

        val mediaItem = player.currentMediaItem ?: return
        val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
        if (currentSongId == mediaItem.mediaId) return

        // MINIPLAYER GLITCH FIX: while a direct tap-request for another song is in
        // flight (preparingSongId is set), the controller can still report the OLD
        // current item until the seek/transition lands. Ignore stale reports so the
        // miniplayer does NOT flash back to the previous song before the tapped song
        // starts (it would otherwise show: tapped song -> old song -> tapped song).
        val preparingSongId = _playerUiState.value.preparingSongId
        if (preparingSongId != null && mediaItem.mediaId != preparingSongId) return

        playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem.mediaId)
        preparePlaybackAudioMetadataForMedia(mediaItem.mediaId)
        scheduleDynamicPlaybackMetadataRefresh(player, mediaItem.mediaId)
        transitionSchedulerJob?.cancel()
        lyricsStateHolder.cancelLoading()
        resetLyricsSearchState()

        val song = resolveSongFromMediaItem(mediaItem)
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val resolvedDuration = if (song != null) {
            playbackStateHolder.resolveDurationForPlaybackState(
                reportedDurationMs = player.duration,
                songDurationHintMs = song.duration.coerceAtLeast(0L),
                currentPositionMs = currentPosition
            )
        } else {
            0L
        }

        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                currentMediaItemIndex = player.currentMediaItemIndex,
                totalDuration = resolvedDuration,
                lyrics = null,
                isLoadingLyrics = song != null,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady
            )
        }
        syncPlaybackPositionFromPlayer(mediaItem.mediaId, currentPosition)

        song?.let { currentSongValue ->
            viewModelScope.launch {
                val uri = currentSongValue.albumArtUriString?.toUri()
                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
            }
            loadLyricsForCurrentSong()
        }
    }

    private fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _trackVolume.value = playerCtrl.volume
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying,
                playWhenReady = playerCtrl.playWhenReady
            )
        }
        preparePlaybackAudioMetadataForMedia(playerCtrl.currentMediaItem?.mediaId)
        refreshPlaybackAudioMetadata(playerCtrl)
        scheduleDynamicPlaybackMetadataRefresh(playerCtrl, playerCtrl.currentMediaItem?.mediaId)

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            playbackStateHolder.ensureCurrentPlaybackOccurrence(mediaItem.mediaId)
            val song = resolveSongFromMediaItem(mediaItem)

            if (song != null) {
                val initialPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                    reportedDurationMs = playerCtrl.duration,
                    songDurationHintMs = song.duration.coerceAtLeast(0L),
                    currentPositionMs = initialPosition
                )
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        totalDuration = resolvedDuration
                    )
                }
                syncPlaybackPositionFromPlayer(mediaItem.mediaId, initialPosition)
                viewModelScope.launch {
                    val uri = song.albumArtUriString?.toUri()
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                }
                loadLyricsForCurrentSong()
                if (playerCtrl.isPlaying) {
                    _isSheetVisible.value = true
                    startProgressUpdates()
                }
            } else {
                // FIX (miniplayer flash): If resolveSongFromMediaItem is transiently null during
                // buffering (before song is fully cached in allSongsById), do NOT overwrite currentSong
                // with null if a media item is actively loaded in the player. Keep last-known song.
                if (_playerUiState.value.preparingSongId == null && playerCtrl.currentMediaItem == null) {
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = null,
                            isPlaying = false,
                            playWhenReady = false
                        )
                    }
                    playbackStateHolder.clearCurrentPositionHints()
                    playbackStateHolder.setCurrentPosition(0L)
                    resetPlaybackAudioMetadata()
                }
            }
        }

        mediaControllerPlaybackListener?.let(playerCtrl::removeListener)
        mediaControllerPlaybackListener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                if (dualPlayerEngine.isTransitionRunning()) return
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = isPlaying,
                        playWhenReady = playerCtrl.playWhenReady,
                        // BUGFIX: clear isBuffering when playback starts. Without this,
                        // the spinner could persist if STATE_READY was missed or if
                        // playPause set isBuffering before ensureFreshStreamForResume.
                        isBuffering = if (isPlaying) false else it.isBuffering
                    )
                }
                if (isPlaying) {
                    _isSheetVisible.value = true
                    clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    startProgressUpdates()

                    playerCtrl.currentMediaItem?.let { currentItem ->
                        registerYoutubePlaybackHistoryIfNeeded(currentItem)
                        if (!_playbackAudioMetadata.value.isConfirmed) {
                            refreshPlaybackAudioMetadata(playerCtrl)
                            scheduleDynamicPlaybackMetadataRefresh(playerCtrl, currentItem.mediaId)
                        }
                    }
                } else {
                    stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, pausedPosition)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                val currentId = mediaItem?.mediaId
                playbackStateHolder.onPlaybackOccurrenceTransition(currentId)
                preparePlaybackAudioMetadataForMedia(currentId)
                if (currentId != null) {
                    scheduleDynamicPlaybackMetadataRefresh(playerCtrl, currentId)
                }
                transitionSchedulerJob?.cancel()
                lyricsStateHolder.cancelLoading()
                lastRegisteredVideoId = null
                pendingYoutubeHistoryVideoId = null
                if (playerCtrl.isPlaying) {
                    mediaItem?.let { registerYoutubePlaybackHistoryIfNeeded(it) }
                }
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = libraryStateHolder.allSongsById.value[previousSongId]?.title
                                ?: context.getString(R.string.player_default_track_title)

                            viewModelScope.launch {
                                _toastEvents.emit(
                                    context.getString(R.string.player_playback_stopped_eot, finishedSongTitle),
                                )
                            }
                            cancelSleepTimer(suppressDefaultToast = true)
                        }
                    }

                    mediaItem?.let { transitionedItem ->
                        val song = resolveSongFromMediaItem(transitionedItem)
                        if (song != null && (song.id.startsWith("youtube_") || song.youtubeId != null)) {
                            viewModelScope.launch(Dispatchers.IO) {
                                saveYoutubeSongsToDb(listOf(song))
                            }
                        }
                        
                        // Offline check for Telegram songs
                        if (song?.contentUriString?.startsWith("telegram:") == true) {
                            ensureTelegramPlaybackObserversStarted()
                            val isOnline = connectivityStateHolder.isOnline.value
                            if (!isOnline) {
                                val fileId = song.telegramFileId
                                if (fileId != null) {
                                    val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                                    if (!isCached) {
                                        playerCtrl.pause()
                                        _showNoInternetDialog.emit(Unit)
                                    }
                                }
                            }
                        }

                        val resolvedDuration = if (song != null) {
                            playbackStateHolder.resolveDurationForPlaybackState(
                                reportedDurationMs = playerCtrl.duration,
                                songDurationHintMs = song.duration.coerceAtLeast(0L),
                                currentPositionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                            )
                        } else {
                            0L
                        }
                        resetLyricsSearchState()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = song,
                                currentMediaItemIndex = playerCtrl.currentMediaItemIndex,
                                totalDuration = resolvedDuration,
                                lyrics = null,
                                isLoadingLyrics = song != null,
                                playWhenReady = playerCtrl.playWhenReady
                            )
                        }
                        val transitionPosition = syncPlaybackPositionFromPlayer(
                            transitionedItem.mediaId,
                            playerCtrl.currentPosition.coerceAtLeast(0L)
                        )

                        song?.let { currentSongValue ->
                            viewModelScope.launch {
                                val uri = currentSongValue.albumArtUriString?.toUri()
                                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                            }
                            loadLyricsForCurrentSong()

                            val youtubeId = currentSongValue.youtubeId
                            if (youtubeId != null && currentSongValue.path.isBlank()) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                                            youtubeId = youtubeId,
                                            title = currentSongValue.title,
                                            artist = currentSongValue.artist,
                                            album = currentSongValue.album,
                                            duration = if (currentSongValue.duration > 0) (currentSongValue.duration / 1000).toString() else "",
                                            thumbnailHref = currentSongValue.albumArtUriString ?: ""
                                        )
                                        val url = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.getSongPlayerUrl(context, ytSong)
                                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.streamUrlLruCache.put("${youtubeId}_high", url)

                                        val cachingEnabled = userPreferencesRepository.cacheMostPlayedSongsOfflineFlow.first() || userPreferencesRepository.cacheLikedSongsOfflineFlow.first()
                                        if (cachingEnabled && connectivityStateHolder.isMeteredNetwork.value == false) {
                                            val audioPath = com.unshoo.pixelmusic.data.remote.youtube.DownloadHelper.downloadAudio(context, ytSong)
                                            if (audioPath != null) {
                                                val ytDb = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                                                ytDb.songRepository().updateAudioPath(youtubeId, audioPath)

                                                val mainId = -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)
                                                musicRepository.updateSongFilePath(mainId, audioPath)
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    } ?: run {
                        val isPreparingCurrent = _playerUiState.value.preparingSongId != null
                        if (!isCastConnecting.value && !isRemotePlaybackActive.value && !isPreparingCurrent) {
                            lyricsStateHolder.cancelLoading()
                            playbackStateHolder.updateStablePlayerState {
                                it.copy(
                                    currentSong = null,
                                    isPlaying = false,
                                    playWhenReady = false,
                                    lyrics = null,
                                    isLoadingLyrics = false,
                                    totalDuration = 0L
                                )
                            }
                            playbackStateHolder.clearCurrentPositionHints()
                            resetPlaybackAudioMetadata()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl)
                syncDisplayedMediaItemIfChanged(playerCtrl)

                // Debounce buffering state to avoid flickering
                bufferingDebounceJob?.cancel()
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingDebounceJob = viewModelScope.launch {
                        delay(150) // Wait 150ms before showing buffering indicator
                        playbackStateHolder.updateStablePlayerState { state ->
                            state.copy(isBuffering = true)
                        }
                    }
                } else {
                    // Immediately hide buffering when not buffering
                    playbackStateHolder.updateStablePlayerState { state ->
                        state.copy(isBuffering = false)
                    }
                }

                if (playbackState == Player.STATE_READY) {
                    val currentId = playerCtrl.currentMediaItem?.mediaId
                    if (currentId != null) {
                        refreshPlaybackAudioMetadata(playerCtrl)
                        scheduleDynamicPlaybackMetadataRefresh(playerCtrl, currentId)
                    }
                    clearPreparingSongIfMatching(currentId)
                    val readyPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    val songDurationHint = playbackStateHolder.stablePlayerState.value.currentSong?.duration ?: 0L
                    val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                        reportedDurationMs = playerCtrl.duration,
                        songDurationHintMs = songDurationHint,
                        currentPositionMs = readyPosition
                    )
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, readyPosition)
                    playbackStateHolder.updateStablePlayerState { it.copy(totalDuration = resolvedDuration) }
                    startProgressUpdates()
                    updateCurrentPlaybackQueueFromPlayer(playerCtrl)
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    val hasPreloadedQueue = _playerUiState.value.currentPlaybackQueue.isNotEmpty()
                    if (!isCastConnecting.value && !isRemotePlaybackActive.value && _playerUiState.value.preparingSongId == null && !hasPreloadedQueue) {
                        lyricsStateHolder.cancelLoading()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = null,
                                isPlaying = false,
                                playWhenReady = false,
                                lyrics = null,
                                isLoadingLyrics = false,
                                totalDuration = 0L
                            )
                        }
                        playbackStateHolder.clearCurrentPositionHints()
                        playbackStateHolder.setCurrentPosition(0L)
                        resetPlaybackAudioMetadata()
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl, tracks)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncDisplayedMediaItemIfChanged(playerCtrl)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (isRemoteSessionControllingPlayback()) return
                if (playbackStateHolder.stablePlayerState.value.isShuffleEnabled != shuffleModeEnabled) {
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleTransitionInProgress) {
                        toggleShuffle()
                    }
                }
                updateCurrentPlaybackQueueFromPlayer(playerCtrl)
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                viewModelScope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                syncDisplayedMediaItemIfChanged(playerCtrl)
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(playerCtrl)
            }
        }
        playerCtrl.addListener(checkNotNull(mediaControllerPlaybackListener))
        Trace.endSection()
    }


    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        cancelPendingFullQueuePlayback()
        // FIX (miniplayer vanish on tap): Set preparingSongId IMMEDIATELY so the miniplayer
        // becomes visible before any async hydration work starts. Without this, the miniplayer
        // is invisible during the hydrateSongsIfNeeded() suspend call below, and the
        // STATE_IDLE protection has nothing to guard against.
        setPreparingSong(startSong.id)
        val requestToken = beginDirectPlaybackRequest()
        directPlaybackJob = viewModelScope.launch {
            transitionSchedulerJob?.cancel()

            val validSongs = hydrateSongsIfNeeded(songsToPlay)
            throwIfDirectPlaybackRequestIsStale(requestToken)

            val isOnline = connectivityStateHolder.isOnline.value
            val availableSongs = if (!isOnline) {
                validSongs.filter { song ->
                    com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, song) ||
                    (song.telegramFileId != null && musicRepository.telegramRepository.isFileCached(song.telegramFileId))
                }
            } else {
                validSongs
            }

            if (availableSongs.isEmpty()) {
                clearPreparingSongIfMatching()
                if (!isOnline) {
                    _showNoInternetDialog.tryEmit(Unit)
                } else {
                    _toastEvents.emit(context.getString(R.string.no_valid_songs))
                }
                return@launch
            }

            // Adjust startSong if it was filtered out
            val validStartSong =
                availableSongs.firstOrNull { it.id == startSong.id } ?: availableSongs.first()

            // Offline check for the starting song
            if (!isOnline) {
                val canPlayStartSong = com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, validStartSong) ||
                    (validStartSong.telegramFileId != null && musicRepository.telegramRepository.isFileCached(validStartSong.telegramFileId))
                if (!canPlayStartSong) {
                    clearPreparingSongIfMatching()
                    _showNoInternetDialog.tryEmit(Unit)
                    return@launch
                }
            }

            if (validStartSong.contentUriString.startsWith("telegram:")) {
                ensureTelegramPlaybackObserversStarted()
            }

            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(availableSongs)

            // Check if the user wants shuffle to be persistent across different albums
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            throwIfDirectPlaybackRequestIsStale(requestToken)
            // Check if shuffle is currently active in the player
            val isShuffleOn = playbackStateHolder.stablePlayerState.value.isShuffleEnabled

            // If Persistent Shuffle is OFF, we reset shuffle to "false" every time a new album starts
            if (!isPersistent || !isShuffleOn) {
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }
                syncShuffleStateWithSession(false)
            }

            // If shuffle is persistent and currently ON, we shuffle the new songs immediately
            val finalSongsToPlay = if (isPersistent && isShuffleOn) {
                // Shuffle the list but ensure the chosen song starts first at index 0 and all other songs follow
                withContext(Dispatchers.Default) {
                    QueueUtils.buildAnchoredShuffleQueueSuspending(
                        validSongs,
                        validSongs.indexOfFirst { it.id == validStartSong.id }.coerceAtLeast(0),
                        startAtZero = true
                    )
                }
            } else {
                // Otherwise, just use the normal sequential order
                validSongs
            }
            throwIfDirectPlaybackRequestIsStale(requestToken)

            // Send the final list (shuffled or not) to the player engine
            internalPlaySongs(finalSongsToPlay, validStartSong, queueName, playlistId, requestToken)
            if (requestToken == directPlaybackToken) {
                directPlaybackJob = null
            }
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(
        songsToPlay: List<Song>, 
        queueName: String = "None", 
        playlistId: String? = null,
        startAtZero: Boolean = true
    ) {
        cancelPendingFullQueuePlayback()
        val requestToken = beginDirectPlaybackRequest()
        directPlaybackJob = viewModelScope.launch {
            try {
                val isOnline = connectivityStateHolder.isOnline.value
                val offlineFilteredSongs = if (!isOnline) {
                    songsToPlay.filter { song ->
                        com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, song) ||
                        (song.telegramFileId != null && musicRepository.telegramRepository.isFileCached(song.telegramFileId))
                    }
                } else {
                    songsToPlay
                }

                if (offlineFilteredSongs.isEmpty()) {
                    if (!isOnline) {
                        _showNoInternetDialog.tryEmit(Unit)
                    } else {
                        sendToast(context.getString(R.string.player_no_songs_to_shuffle))
                    }
                    return@launch
                }

                val cappedSongs = if (offlineFilteredSongs.size > 500) {
                    offlineFilteredSongs.shuffled().take(500)
                } else {
                    offlineFilteredSongs
                }
                val result = queueStateHolder.prepareShuffledQueueSuspending(cappedSongs, startAtZero)
                throwIfDirectPlaybackRequestIsStale(requestToken)
                if (result == null) {
                    sendToast(context.getString(R.string.player_no_songs_to_shuffle))
                    return@launch
                }

                val (shuffledQueue, startSong) = result
                transitionSchedulerJob?.cancel()

                // Optimistically update shuffle state
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }
                launch { userPreferencesRepository.setShuffleOn(true) }
                syncShuffleStateWithSession(true)

                internalPlaySongs(shuffledQueue, startSong, queueName, playlistId, requestToken)
                if (requestToken == directPlaybackToken) {
                    directPlaybackJob = null
                }
            } catch (e: Exception) {
                if (requestToken == directPlaybackToken) {
                    Timber.e(e, "Error during playSongsShuffled")
                    sendToast("Failed to play shuffled songs")
                    directPlaybackJob = null
                }
            }
        }
    }

    fun openYouTubeMusicLink(uri: Uri) {
        viewModelScope.launch {
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()
            val videoId = when {
                host == "youtu.be" -> uri.pathSegments.firstOrNull()
                else -> uri.getQueryParameter("v")
            }?.takeIf { it.isNotBlank() }
            val playlistId = uri.getQueryParameter("list")
                ?: path.removePrefix("/playlist/").takeIf { path.startsWith("/playlist/") && it.isNotBlank() }
            val browseId = path.removePrefix("/browse/").takeIf { path.startsWith("/browse/") && it.isNotBlank() }

            try {
                when {
                    videoId != null -> {
                        val ytSong = withContext(Dispatchers.IO) {
                            runCatching { com.unshoo.pixelmusic.data.remote.youtube.SongDataSource().getSongInfo(videoId) }
                                .getOrElse {
                                    com.unshoo.pixelmusic.data.model.youtube.Song(
                                        youtubeId = videoId,
                                        title = videoId,
                                        artist = "YouTube Music",
                                        thumbnailHref = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                    )
                                }
                        }
                        val song = ytSong.toNativeSong()
                        val queue = if (!playlistId.isNullOrBlank()) {
                            val page = withContext(Dispatchers.IO) {
                                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.playlist(playlistId).getOrNull()
                            }
                            val songs = page?.songs?.map { it.toNativeSong() }.orEmpty()
                            (listOf(song) + songs.filterNot { it.youtubeId == videoId || it.id == song.id })
                                .ifEmpty { listOf(song) }
                        } else {
                            listOf(song)
                        }
                        playSongs(queue, song, "YouTube Music", playlistId)
                    }

                    !playlistId.isNullOrBlank() -> {
                        val page = withContext(Dispatchers.IO) {
                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.playlist(playlistId).getOrNull()
                        }
                        val songs = page?.songs?.map { it.toNativeSong() }.orEmpty()
                        if (songs.isNotEmpty()) {
                            playSongs(songs, songs.first(), page?.playlist?.title ?: "YouTube Music Playlist", playlistId)
                        } else {
                            playRadio(
                                unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(playlistId = playlistId),
                                "YouTube Music Playlist"
                            )
                        }
                    }

                    !browseId.isNullOrBlank() -> {
                        val songs = withContext(Dispatchers.IO) {
                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.albumSongs(browseId).getOrNull()
                                ?.map { it.toNativeSong() }
                                .orEmpty()
                        }
                        if (songs.isNotEmpty()) {
                            playSongs(songs, songs.first(), "YouTube Music Album", browseId)
                        } else {
                            sendToast("Could not open YouTube Music link")
                        }
                    }

                    else -> sendToast("Unsupported YouTube Music link")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to open YouTube Music link: %s", uri)
                sendToast("Failed to open YouTube Music link")
            }
        }
    }

    fun playExternalUri(uri: Uri) {
        viewModelScope.launch {
            val externalResult = externalMediaStateHolder.buildExternalSongFromUri(uri)
            if (externalResult == null) {
                sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueSongs = externalMediaStateHolder.buildExternalQueue(externalResult, uri)
            val immutableQueue = queueSongs.toPlaybackQueue()

            _playerUiState.update { state ->
                state.copy(
                    currentPlaybackQueue = immutableQueue,
                    currentQueueSourceName = context.getString(R.string.external_queue_label),
                    showDismissUndoBar = false,
                    dismissedSong = null,
                    dismissedQueue = persistentListOf(),
                    dismissedQueueName = "",
                    dismissedPosition = 0L
                )
            }
            playbackStateHolder.setCurrentPosition(0L)

            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(
                    currentSong = externalResult.song,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = externalResult.song.duration,
                    lyrics = null,
                    isLoadingLyrics = false
                )
            }

            _sheetState.value = PlayerSheetState.COLLAPSED
            _isSheetVisible.value = true

            internalPlaySongs(queueSongs, externalResult.song, context.getString(R.string.external_queue_label), null)
            showPlayer()
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }

    private fun setPreparingSong(songId: String?) {
        _playerUiState.update { state ->
            if (state.preparingSongId == songId) state else state.copy(preparingSongId = songId)
        }
    }

    private fun beginPreparingSong(song: Song) {
        setPreparingSong(song.id)
        viewModelScope.launch(Dispatchers.IO) {
            val albumArtUri = song.albumArtUriString
            if (albumArtUri.isNullOrBlank()) {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = null,
                    currentSongUriString = null,
                    isPreload = false
                )
            } else {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = albumArtUri.toUri(),
                    currentSongUriString = albumArtUri,
                    isPreload = false
                )
            }
        }
    }

    private fun clearPreparingSongIfMatching(mediaId: String? = null) {
        val preparingSongId = _playerUiState.value.preparingSongId ?: return
        if (mediaId == null || preparingSongId == mediaId) {
            setPreparingSong(null)
        }
    }

    private suspend fun preparePlaybackQueue(
        songsToPlay: List<Song>,
        startSongId: String,
        playlistId: String?
    ): PreparedPlaybackQueue = withContext(Dispatchers.IO) {
        val preferTelegram = userPreferencesRepository.preferTelegramAlternativeFlow.first()
        val telegramByKey = if (preferTelegram && songsToPlay.any { it.contentUriString.startsWith("youtube://") || it.youtubeId != null }) {
            musicRepository.getTelegramSongsOnce()
                .asSequence()
                .filter { it.contentUriString.startsWith("telegram://") }
                .mapNotNull { telegramSong ->
                    val key = telegramAlternativeKey(telegramSong.title, telegramSong.artist)
                    key?.let { it to telegramSong }
                }
                .toMap()
        } else {
            emptyMap()
        }

        if (telegramByKey.isNotEmpty()) {
            ensureTelegramPlaybackObserversStarted()
        }

        val mediaItems = ArrayList<MediaItem>(songsToPlay.size)
        var startIndex = 0
        var foundStartIndex = false

        songsToPlay.forEachIndexed { index, song ->
            if (!foundStartIndex && song.id == startSongId) {
                startIndex = index
                foundStartIndex = true
            }

            val baseItem = buildPlaybackMediaItem(song, playlistId)
            val telegramAlternative = telegramAlternativeKey(song.title, song.artist)?.let(telegramByKey::get)
            mediaItems += if (telegramAlternative != null) {
                // Stream audio from Telegram, but keep the YouTube MediaMetadata/artwork/title so
                // the queue/full-player visuals remain the online YouTube Music version.
                baseItem.buildUpon()
                    .setUri(Uri.parse(telegramAlternative.contentUriString))
                    .build()
            } else {
                baseItem
            }
        }

        PreparedPlaybackQueue(
            mediaItems = mediaItems,
            startIndex = startIndex
        )
    }

    private fun telegramAlternativeKey(title: String, artist: String): String? {
        val normalizedTitle = title.normalizeMetadataText()
        val normalizedArtist = artist.normalizeMetadataText()
        if (normalizedTitle.isNullOrBlank() || normalizedArtist.isNullOrBlank()) return null
        return "${normalizedTitle.lowercase(Locale.ROOT)}|${normalizedArtist.lowercase(Locale.ROOT)}"
    }



    private suspend fun internalPlaySongs(
        songsToPlay: List<Song>,
        startSong: Song,
        queueName: String = "None",
        playlistId: String? = null,
        // BUGFIX: identifies which tap/request this call belongs to, so the async resolve+apply
        // work below can detect if it has been superseded by a newer request before it mutates
        // the player. Callers that don't already have a token mint one via beginDirectPlaybackRequest()
        // so every entry point into this function is covered, not just playSongs()/playSongsShuffled().
        requestToken: Long = beginDirectPlaybackRequest(),
        skipAutoQueueSchedule: Boolean = false
    ) {
        if (songsToPlay.isEmpty()) {
            clearPreparingSongIfMatching()
            return
        }
        val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
        val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
        val songsToPlay = if (filterCoverAndLofi && songsToPlay.size > 1) {
            songsToPlay.filter { it.id == startSong.id || !ContentFilterUtils.isCoverOrLofi(it, filterKeywords) }
        } else {
            songsToPlay
        }
        val effectiveStartSong = songsToPlay.firstOrNull { it.id == startSong.id } ?: songsToPlay.first()
        val isOnline = connectivityStateHolder.isOnline.value
        if (!isOnline) {
            val hasOffline = com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, effectiveStartSong) ||
                (effectiveStartSong.telegramFileId != null && musicRepository.telegramRepository.isFileCached(effectiveStartSong.telegramFileId))
            if (!hasOffline) {
                clearPreparingSongIfMatching()
                _showNoInternetDialog.tryEmit(Unit)
                return
            }
        }
        saveYoutubeSongsToDb(listOf(effectiveStartSong))
        if (songsToPlay.size > 1) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    saveYoutubeSongsToDb(songsToPlay)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save YouTube songs to DB in background")
                }
            }
        }
        com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.reset()

        // Update dynamic shortcut for last played playlist
        if (playlistId != null && queueName != "None") {
            appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
        }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            clearPreparingSongIfMatching()
            val remoteLoaded = castTransferStateHolder.playRemoteQueue(
                songsToPlay = songsToPlay,
                startSong = effectiveStartSong,
                isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
            )

            if (!remoteLoaded) {
                Timber.tag(CAST_LOG_TAG).w(
                    "Remote queue load failed in internalPlaySongs (songId=%s queueSize=%d).",
                    effectiveStartSong.id,
                    songsToPlay.size
                )
                castSession.remoteMediaClient?.requestStatus()
                return
            }

            _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toPlaybackQueue(), currentQueueSourceName = queueName) }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
        } else {
            beginPreparingSong(effectiveStartSong)
            _playerUiState.update {
                it.copy(
                    currentPlaybackQueue = songsToPlay.toPlaybackQueue(),
                    currentQueueSourceName = queueName
                )
            }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = false,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
            _isSheetVisible.value = true

            // Pre-resolve the starting song's cloud URI before ExoPlayer touches it.
            // This populates the resolvedUriCache so resolveDataSpec finds it instantly.
            val startingUri = MediaItemBuilder.playbackUri(effectiveStartSong)
            val scheme = startingUri.scheme
            if (
                scheme == "telegram" ||
                scheme == "netease" ||
                scheme == "qqmusic" ||
                scheme == "navidrome" ||
                scheme == "jellyfin" ||
                scheme == "gdrive" ||
                scheme == "youtube" ||
                scheme == "monochrome"
            ) {
                if (scheme == "telegram") {
                    ensureTelegramPlaybackObserversStarted()
                }
                if (scheme == "youtube") {
                    val videoId = startingUri.toString().substringAfter("youtube://")
                    val ytSong = try {
                        com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).songRepository().getSong(videoId)
                    } catch (e: Exception) {
                        null
                    }
                    if (ytSong?.audioFilePath != null && java.io.File(ytSong.audioFilePath).exists()) {
                        dualPlayerEngine.registerLocalPath(startingUri.toString(), ytSong.audioFilePath)
                    }
                }
                // Pre-resolve asynchronously on Dispatchers.IO to not block player preparation
                viewModelScope.launch(Dispatchers.IO) {
                    dualPlayerEngine.resolveCloudUri(startingUri)
                }
            }

            val preparedPlaybackQueue = preparePlaybackQueue(
                songsToPlay = songsToPlay,
                startSongId = effectiveStartSong.id,
                playlistId = playlistId
            )

            val playSongsAction: suspend () -> Unit = {
                // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                dualPlayerEngine.cancelNext()
                val enginePlayer = dualPlayerEngine.masterPlayer

                if (preparedPlaybackQueue.mediaItems.isNotEmpty()) {
                    // FREEZE FIX: pre-resolve youtube/telegram/gdrive URIs off the ExoPlayer
                    // load thread BEFORE prepare/play. Without this, ResolvingDataSource used
                    // to runBlocking network work and froze the miniplayer/UI on song taps.
                    val applyJob = viewModelScope.launch {
                        // INSTANT PLAY on low connectivity (SpatialFlow pattern):
                        // Resolve ONLY the tapped track to a real http(s)/file URL first
                        // (always LOW bitrate for fastest first-byte), then inject into ExoPlayer.
                        // Never prepare unresolved youtube:// URIs — the non-blocking resolver
                        // will error and stall playback.
                        val items = preparedPlaybackQueue.mediaItems.toMutableList()
                        val startIndex = preparedPlaybackQueue.startIndex
                        val startItem = items.getOrNull(startIndex)

                        val resolvedStart = if (startItem != null) {
                            try {
                                dualPlayerEngine.getCachedResolvedMediaItem(startItem) ?: run {
                                    withContext(Dispatchers.IO) {
                                        kotlinx.coroutines.withTimeoutOrNull(OPTIMISTIC_RESOLVE_BUDGET_MS) {
                                            dualPlayerEngine.preResolveForPlayback(startItem)
                                        }
                                    } ?: run {
                                        startItem.localConfiguration?.uri?.let { dualPlayerEngine.kickBackgroundResolve(it) }
                                        startItem
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "instant start resolve failed")
                                startItem
                            }
                        } else {
                            null
                        }

                        if (resolvedStart != null) {
                            items[startIndex] = resolvedStart
                        }

                        // BUGFIX (stale-resolution race): if a newer tap has superseded this
                        // request while we were resolving the stream URL above, stop here.
                        // Previously this coroutine was untracked and always ran to completion,
                        // so a slow tap A could finish resolving after a faster tap B and
                        // silently overwrite B's already-playing queue/player state with A's -
                        // this is the root cause of "wrong song plays" under rapid tapping or
                        // weak network. beginDirectPlaybackRequest() bumps directPlaybackToken
                        // for every new tap, so a mismatch here means we've been superseded.
                        if (isDirectPlaybackRequestStale(requestToken)) {
                            Timber.d(
                                "Discarding stale direct-playback apply (requestToken=%d, current=%d, songId=%s)",
                                requestToken,
                                directPlaybackToken,
                                effectiveStartSong.id
                            )
                            return@launch
                        }

                        withContext(Dispatchers.Main.immediate) {
                            dualPlayerEngine.cancelNext()
                            val player = dualPlayerEngine.masterPlayer
                            player.setMediaItems(items, startIndex, 0L)
                            val isShuffle = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
                            player.shuffleModeEnabled = isShuffle
                            if (isShuffle) {
                                (player as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                                    androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(IntArray(items.size) { it }, System.currentTimeMillis())
                                )
                            }
                            player.prepare()
                            player.playWhenReady = true
                            player.play()
                            // Instant YT Music history sync for the song just launched.
                            player.currentMediaItem?.let { registerYoutubePlaybackHistoryIfNeeded(it) }
                            _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
                            if (!skipAutoQueueSchedule && songsToPlay.size <= 1) {
                                // Auto Queue automatically refills related songs with an adaptive debounce delay so tap-to-play gets 100% priority
                                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.scheduleAdaptiveRefill(forceRefresh = true)
                            }
                        }

                        // Warm next/prev + first-chunk cache OFF the critical path.
                        launch(Dispatchers.IO) {
                            runCatching {
                                dualPlayerEngine.preResolveForPlayback(
                                    preparedPlaybackQueue.mediaItems,
                                    startIndex
                                )
                            }
                        }
                    }
                    directPlaybackApplyJob = applyJob
                    applyJob.join()
                } else {
                    clearPreparingSongIfMatching(effectiveStartSong.id)
                    _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
                }
            }

            // Persist snapshot immediately from PlayerViewModel to prevent process death losing queue state
            persistPlaybackSnapshotFromViewModel(songsToPlay, effectiveStartSong, playWhenReady = true)

            // Ensure MediaController connection is ready before executing playback action
            if (mediaController == null || !_isMediaControllerReady.value) {
                Timber.w("MediaController not ready. Awaiting connection.")
                withTimeoutOrNull(2000L) {
                    _isMediaControllerReady.filter { it }.first()
                }
            }
            playSongsAction()
        }
    }

    private suspend fun buildResolvedPlaybackMediaItem(song: Song): MediaItem {
        val mediaItem = MediaItemBuilder.build(song)
        val originalUri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = originalUri.scheme
        if (
            scheme != "telegram" &&
            scheme != "netease" &&
            scheme != "qqmusic" &&
            scheme != "navidrome" &&
            scheme != "jellyfin" &&
            scheme != "gdrive" &&
            scheme != "youtube"
        ) {
            return mediaItem
        }

        if (scheme == "telegram") {
            ensureTelegramPlaybackObserversStarted()
        }

        var finalUri = originalUri
        if (scheme == "youtube") {
            // First check if preferTelegramAlternative is enabled
            val preferTelegram = userPreferencesRepository.preferTelegramAlternativeFlow.first()
            if (preferTelegram) {
                val normalizedTitle = song.title.normalizeMetadataText()
                val normalizedArtist = song.artist.normalizeMetadataText()
                if (!normalizedTitle.isNullOrBlank() && !normalizedArtist.isNullOrBlank()) {
                    val telegramSongs = musicRepository.getTelegramSongsOnce()
                    val matchingTelegram = telegramSongs.firstOrNull {
                        val tTitle = it.title.normalizeMetadataText()
                        val tArtist = it.artist.normalizeMetadataText()
                        !tTitle.isNullOrBlank() && !tArtist.isNullOrBlank() &&
                                tTitle.equals(normalizedTitle, ignoreCase = true) &&
                                tArtist.equals(normalizedArtist, ignoreCase = true)
                    }
                    if (matchingTelegram != null && matchingTelegram.contentUriString.startsWith("telegram://")) {
                        Log.i("PlayerViewModel", "Substituting YouTube song with matching Telegram alternative: ${matchingTelegram.title} by ${matchingTelegram.artist}")
                        ensureTelegramPlaybackObserversStarted()
                        finalUri = Uri.parse(matchingTelegram.contentUriString)
                    }
                }
            }

            if (finalUri.scheme == "youtube") {
                val videoId = finalUri.toString().substringAfter("youtube://")
                val ytSong = try {
                    com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).songRepository().getSong(videoId)
                } catch (e: Exception) {
                    null
                }
                if (ytSong?.audioFilePath != null && java.io.File(ytSong.audioFilePath).exists()) {
                    dualPlayerEngine.registerLocalPath(finalUri.toString(), ytSong.audioFilePath)
                    return mediaItem.buildUpon().setUri(Uri.fromFile(java.io.File(ytSong.audioFilePath))).build()
                }
            }
        }

        if (finalUri.scheme == "telegram") {
            ensureTelegramPlaybackObserversStarted()
        }

        val resolvedUri = if (finalUri.scheme == "youtube") {
            dualPlayerEngine.getCachedResolvedUri(finalUri) ?: run {
                val optimistic = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(OPTIMISTIC_RESOLVE_BUDGET_MS) {
                        dualPlayerEngine.resolveCloudUri(finalUri)
                    }
                }
                if (optimistic != null && optimistic != finalUri) {
                    optimistic
                } else {
                    dualPlayerEngine.kickBackgroundResolve(finalUri)
                    originalUri
                }
            }
        } else {
            dualPlayerEngine.resolveCloudUri(finalUri)
        }

        return if (resolvedUri == originalUri) {
            mediaItem
        } else {
            mediaItem.buildUpon().setUri(resolvedUri).build()
        }
    }


    private fun loadAndPlaySong(song: Song) {
        cancelPendingFullQueuePlayback()
        beginPreparingSong(song)
        _playerUiState.update { state ->
            if (state.currentPlaybackQueue.none { it.id == song.id }) {
                state.copy(currentPlaybackQueue = persistentListOf(song))
            } else {
                state
            }
        }
        if (!queueStateHolder.hasOriginalQueue()) {
            queueStateHolder.setOriginalQueueOrder(listOf(song))
        }
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                isPlaying = true,
                playWhenReady = true
            )
        }
        _isSheetVisible.value = true

        val controller = mediaController
        if (controller == null) {
            pendingPlaybackAction = {
                loadAndPlaySong(song)
            }
            return
        }

        viewModelScope.launch {
            val mediaItem = buildResolvedPlaybackMediaItem(song)
            if (controller.currentMediaItem?.mediaId == song.id) {
                if (!controller.isPlaying) controller.play()
            } else {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
            registerYoutubePlaybackHistoryIfNeeded(mediaItem)
        }
    }

// buildMediaMetadataForSong moved to MediaItemBuilder

    /**
     * BUGFIX (duplicate/conflicting telemetry writers): this used to independently call
     * YouTube.registerPlayback(...) here, AND MusicService's YouTubeTelemetryManager did its own
     * competing registration, AND ListeningStatsTracker did a THIRD independent one - three
     * uncoordinated writers each firing their own CPN/session for the same video at slightly
     * different times, which is exactly the kind of out-of-order or conflicting write YouTube's
     * history backend is unreliable in the face of.
     *
     * MusicService.startTelemetryReporting() is now the single authoritative remote writer: it
     * observes the actual MediaSession player (the real source of truth for what's currently
     * playing) and uses a canonical, correctly-stripped video ID (see
     * MusicService.canonicalYoutubeVideoId - the old "youtube_<id>" mediaId was previously sent
     * to YouTube unstripped, which is why history only updated ~once per 100 songs). This
     * function is kept only to trigger a local merged-history refresh so the in-app "Recently
     * Played" UI reflects the new song promptly; it deliberately no longer performs any remote
     * registerPlayback/telemetry network calls itself, to avoid re-introducing the race.
     */
    private fun registerYoutubePlaybackHistoryIfNeeded(mediaItem: MediaItem) {
        val song = resolveSongFromMediaItem(mediaItem)
        val videoId = song?.youtubeId
            ?: mediaItem.mediaId.takeIf { it.startsWith("youtube_") }?.substringAfter("youtube_")
            ?: mediaItem.localConfiguration?.uri?.toString()
                ?.takeIf { it.startsWith("youtube://") }
                ?.substringAfter("youtube://")
            ?: return

        if (videoId.isBlank() || videoId == lastRegisteredVideoId) return
        lastRegisteredVideoId = videoId
        pendingYoutubeHistoryVideoId = null

        // Local-only: keep the merged/optimistic "Recently Played" UI fresh. The actual remote
        // YouTube Music history write is now owned exclusively by MusicService's telemetry
        // manager, which is driven by the real player rather than a UI-side click/transition.
        youtubePlaybackHistoryJob?.cancel()
        youtubePlaybackHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { listeningStatsTracker.refreshMergedYoutubeHistory() }
        }
    }

    private suspend fun registerYoutubePlaybackHistory(videoId: String, playlistId: String?): Boolean {
        suspend fun registerTracking(baseUrl: String): Boolean {
            return unshoo.ianshulyadav.pixelmusic.innertube.YouTube.registerPlayback(
                playlistId = playlistId,
                playbackTracking = baseUrl,
                videoId = videoId
            ).onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.w(throwable, "Failed to register YouTube playback history for %s", videoId)
            }.isSuccess
        }

        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[videoId]?.let { cachedUrl ->
            if (registerTracking(cachedUrl)) return true
            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache.remove(videoId, cachedUrl)
        }

        // ArchiveTune-style fallback: if stream URL resolution returned from cache or local file and
        // no tracking URL was captured, fetch a lightweight player response just for playbackTracking.
        val trackingUrl = runCatching {
            val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                .getSignatureTimestamp(videoId)
                .getOrNull()
            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_REMIX,
                signatureTimestamp = signatureTimestamp,
                setLogin = true
            ).getOrNull()
                ?.playbackTracking
                ?.videostatsPlaybackUrl
                ?.baseUrl
        }.getOrNull()

        if (!trackingUrl.isNullOrBlank()) {
            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[videoId] = trackingUrl
            if (registerTracking(trackingUrl)) return true
        }

        // Final fallback: resolve the stream with local disabled. This path also stores playbackTracking
        // inside YoutubeHelper when YouTube returns it.
        runCatching {
            val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                youtubeId = videoId,
                title = videoId,
                artist = "",
                thumbnailHref = ""
            )
            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.getSongPlayerUrl(
                context = context,
                song = ytSong,
                allowLocal = false
            )
        }
        val resolvedTracking = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[videoId]
        return !resolvedTracking.isNullOrBlank() && registerTracking(resolvedTracking)
    }

    private fun syncShuffleStateWithSession(enabled: Boolean) {
        runOnMainImmediate {
            val controller = mediaController ?: return@runOnMainImmediate
            val args = Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
            }
            controller.sendCustomCommand(
                SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle()),
                args
            )
        }
    }

    fun toggleShuffle(currentSongOverride: Song? = null) {
        cancelPendingFullQueuePlayback()
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentSong = currentSongOverride
            ?: playbackStateHolder.stablePlayerState.value.currentSong
            ?: mediaController?.currentMediaItem?.let { resolveSongFromMediaItem(it) }
            ?: currentQueue.firstOrNull()

        playbackStateHolder.toggleShuffle(
            currentSongs = currentQueue,
            currentSong = currentSong,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue()) }
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
            }
        )
    }

    fun cycleRepeatMode() {
        playbackStateHolder.cycleRepeatMode()
    }

    fun toggleAutoQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = autoQueueEnabled.value
            val target = !current
            youtubeDatastoreRepository.save(
                com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository.PreferenceKeys.AUTO_QUEUE_ENABLED,
                target
            )
            if (target) {
                // Do NOT trim the queue here. Reseeding appends fresh related songs on top
                // of whatever is already queued; trimming first raced the network fetch that
                // builds the new queue and left the user with nothing queued whenever that
                // fetch was slow or failed (e.g. on a weak connection).
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.resetAndReseedFromCurrentSong()
            } else {
                com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.disableAndTrimQueue()
            }
        }
    }

    private suspend fun setFavoriteStatusEverywhere(song: Song, isFavorite: Boolean, awaitRemoteSync: Boolean = false) {
        musicRepository.setFavoriteStatusWithMetadata(song, isFavorite, awaitRemoteSync)
    }

    fun toggleFavorite() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        viewModelScope.launch {
            toggleFavoriteSpecificSongSuspending(currentSong)
        }
    }

    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) {
        viewModelScope.launch {
            toggleFavoriteSpecificSongSuspending(song, removing)
        }
    }

    suspend fun toggleFavoriteSpecificSongSuspending(song: Song, removing: Boolean = false) {
        val currentlyFavorite = favoriteSongIds.value.contains(song.id)
        val targetFavoriteState = if (removing) false else !currentlyFavorite
        setFavoriteStatusEverywhere(song, targetFavoriteState, awaitRemoteSync = true)

        val videoId = song.youtubeId ?: if (song.contentUriString?.startsWith("youtube://") == true) {
            song.contentUriString.substringAfter("youtube://")
        } else if (song.id.startsWith("youtube_")) {
            song.id.substringAfter("youtube_")
        } else {
            null
        }

        if (videoId != null && targetFavoriteState) {
            // Optional manual behavior: only download liked songs if the user explicitly enabled it.
            // Do not mark songs as downloaded unless the audio file actually exists.
            val shouldCache = userPreferencesRepository.cacheLikedSongsOfflineFlow.first()
            if (shouldCache) {
                val workRequest = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                    .setInputData(workDataOf(SongDownloadWorker.SONG_KEY to videoId))
                    .setConstraints(Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork("dl_liked_$videoId", ExistingWorkPolicy.KEEP, workRequest)
            }
        }

        if (videoId != null && !userPreferencesRepository.localModeEnabledFlow.first() && unshoo.ianshulyadav.pixelmusic.innertube.YouTube.hasLoginCookie()) {
            try {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.likeVideo(videoId, targetFavoriteState)
                youTubeLibrarySyncManager.syncLikedSongs()
            } catch (e: Exception) {
                Timber.w(e, "Failed to instantly sync Liked Songs playlist")
            }
        }
    }

    fun downloadPlaylistSongs(playlistId: String, songIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadTag = playlistDownloadTag(playlistId)
            val ytDb = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
            val ytPlaylist = ytDb.playlistRepository().getPlaylistById(playlistId)
            if (ytPlaylist != null) {
                val workRequest = OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            PlaylistDownloadWorker.PLAYLIST_KEY to playlistId,
                            "user_initiated" to true
                        )
                    )
                    // Tagged so cancelPlaylistDownload() can stop this regardless of which of
                    // the two enqueue paths below was taken (single worker vs per-song
                    // fallback), without the caller needing to know which one ran.
                    .addTag(downloadTag)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresStorageNotLow(true)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "playlist_dl_$playlistId",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } else {
                val songs = musicRepository.getSongsByIds(songIds).first()
                songs.forEach { song ->
                    val videoId = song.youtubeId ?: if (song.contentUriString?.startsWith("youtube://") == true) {
                        song.contentUriString.substringAfter("youtube://")
                    } else if (song.id.startsWith("youtube_")) {
                        song.id.substringAfter("youtube_")
                    } else {
                        null
                    }
                    if (videoId != null) {
                        val workRequest = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                            .setInputData(workDataOf(SongDownloadWorker.SONG_KEY to videoId))
                            .addTag(downloadTag)
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .setRequiresStorageNotLow(true)
                                    .build()
                            )
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "dl_playlist_song_$videoId",
                            ExistingWorkPolicy.KEEP,
                            workRequest
                        )
                    }
                }
            }
        }
    }

    private fun playlistDownloadTag(playlistId: String) = "playlist_dl_$playlistId"

    /**
     * Cancels an in-progress playlist download started via [downloadPlaylistSongs]. Previously
     * there was no way to cancel a playlist download at all: the work was enqueued under unique
     * names that nothing in the app ever called `cancelUniqueWork` on. Cancelling here stops
     * whichever of the two enqueue strategies is actually running, since both are tagged with
     * the same [playlistDownloadTag].
     *
     * Songs that already finished downloading are left on disk (`DownloadHelper.downloadAudio`
     * skips files that already exist), so calling [downloadPlaylistSongs] again later resumes
     * from where this left off instead of starting over. That combination -- cancel now, resume
     * later by re-invoking the same download call -- is how pause/resume is implemented, since
     * WorkManager itself has no native "pause" primitive for running work.
     */
    fun cancelPlaylistDownload(playlistId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(playlistDownloadTag(playlistId))
        com.unshoo.pixelmusic.data.remote.youtube.PixelMusicNotificationManager.cancelPlaylistDownloadNotification(context, playlistId)
    }

    data class PlaylistDownloadStatus(
        val isActive: Boolean = false,
        val completed: Int = 0,
        val total: Int = 0,
    )

    /**
     * Live download state for a playlist, so the UI can show real progress and a working
     * cancel/pause action instead of a single "Download" button with no feedback once tapped.
     */
    fun observePlaylistDownloadStatus(playlistId: String): Flow<PlaylistDownloadStatus> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(playlistDownloadTag(playlistId))
            .map { infos -> toPlaylistDownloadStatus(infos) }
    }

    private fun toPlaylistDownloadStatus(infos: List<WorkInfo>): PlaylistDownloadStatus {
        if (infos.isEmpty()) return PlaylistDownloadStatus()

        val isActive = infos.any {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }

        // Single-worker path: one PlaylistDownloadWorker reports aggregate progress via
        // setProgress(). Detect it by the presence of the total-count key.
        val singleWorkerInfo = infos.firstOrNull { info ->
            info.progress.getInt(PlaylistDownloadWorker.PROGRESS_TOTAL, -1) >= 0
        }
        if (singleWorkerInfo != null) {
            val total = singleWorkerInfo.progress.getInt(PlaylistDownloadWorker.PROGRESS_TOTAL, 0)
            val current = singleWorkerInfo.progress.getInt(PlaylistDownloadWorker.PROGRESS_CURRENT, 0)
            return PlaylistDownloadStatus(isActive = isActive, completed = current, total = total)
        }

        // Per-song fallback path: one SongDownloadWorker WorkInfo per song, no shared progress.
        val total = infos.size
        val completed = infos.count { it.state == WorkInfo.State.SUCCEEDED }
        return PlaylistDownloadStatus(isActive = isActive, completed = completed, total = total)
    }

    fun addSongToQueue(song: Song) {
        if (song.id.startsWith("youtube_") || song.youtubeId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                saveYoutubeSongsToDb(listOf(song))
            }
        }
        viewModelScope.launch {
            var mediaItem = buildPlaybackMediaItem(song)
            val scheme = mediaItem.localConfiguration?.uri?.scheme
            if (scheme == "youtube" || scheme == "telegram" || scheme == "gdrive") {
                mediaItem = withContext(Dispatchers.IO) {
                    runCatching { dualPlayerEngine.preResolveForPlayback(mediaItem) }
                        .getOrDefault(mediaItem)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val player = mediaController ?: dualPlayerEngine.masterPlayer
                player.addMediaItem(mediaItem)
                if (player.mediaItemCount == 1) {
                    player.prepare()
                    player.play()
                }
                sendToast("Added to queue")
            }
        }
    }

    fun addSongNextToQueue(song: Song) {
        if (song.id.startsWith("youtube_") || song.youtubeId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                saveYoutubeSongsToDb(listOf(song))
            }
        }
        viewModelScope.launch {
            var mediaItem = buildPlaybackMediaItem(song)
            val scheme = mediaItem.localConfiguration?.uri?.scheme
            if (scheme == "youtube" || scheme == "telegram" || scheme == "gdrive") {
                mediaItem = withContext(Dispatchers.IO) {
                    runCatching { dualPlayerEngine.preResolveForPlayback(mediaItem) }
                        .getOrDefault(mediaItem)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val player = mediaController ?: dualPlayerEngine.masterPlayer
                val insertionIndex = if (player.currentMediaItemIndex != C.INDEX_UNSET) {
                    (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
                } else {
                    player.mediaItemCount
                }
                player.addMediaItem(insertionIndex, mediaItem)
                if (player.mediaItemCount == 1) {
                    player.prepare()
                    player.play()
                }
                sendToast("Playing next")
            }
        }
    }
    private fun buildPlaybackMediaItem(song: Song, playlistId: String? = null): MediaItem {
        val baseItem = MediaItemBuilder.build(song)
        if (playlistId == null) {
            return baseItem
        }

        val mergedExtras = Bundle(baseItem.mediaMetadata.extras ?: Bundle()).apply {
            putString("playlistId", playlistId)
        }

        return baseItem.buildUpon()
            .setMediaMetadata(
                baseItem.mediaMetadata.buildUpon()
                    .setExtras(mergedExtras)
                    .build()
            )
            .build()
    }

    // =====================================================
    // Multi-Selection Batch Operations
    // =====================================================

    /**
     * Plays all selected songs, preserving their selection order.
     * Clears selection after starting playback.
     */
    fun playSelectedSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.first(), "Selected Songs")
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds multiple songs to the end of the queue in a single transaction.
     */
    fun addSongsToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val youtubeSongs = songs.filter { it.id.startsWith("youtube_") || it.youtubeId != null }
        if (youtubeSongs.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                saveYoutubeSongsToDb(youtubeSongs)
            }
        }
        viewModelScope.launch {
            val mediaItems = songs.map { buildPlaybackMediaItem(it) }.toMutableList()
            withContext(Dispatchers.IO) {
                mediaItems.take(3).forEachIndexed { idx, item ->
                    val scheme = item.localConfiguration?.uri?.scheme
                    if (scheme == "youtube" || scheme == "telegram" || scheme == "gdrive") {
                        runCatching {
                            mediaItems[idx] = dualPlayerEngine.preResolveForPlayback(item)
                        }
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val controller = mediaController ?: return@withContext
                controller.addMediaItems(mediaItems)
            }
            val n = songs.size
            _toastEvents.emit(
                context.resources.getQuantityString(R.plurals.n_songs_added_to_queue, n, n),
            )
        }
    }

    /**
     * Adds all selected songs to the end of the queue.
     * Clears selection after adding.
     */
    fun addSelectedToQueue(songs: List<Song>) {
        addSongsToQueue(songs)
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds all selected songs to play next, preserving selection order.
     * Songs are inserted in reverse order so they play in the correct sequence.
     * Clears selection after adding.
     */
    fun addSelectedAsNext(songs: List<Song>) {
        songs.reversed().forEach { addSongNextToQueue(it) }
        viewModelScope.launch {
            val n = songs.size
            _toastEvents.emit(
                context.resources.getQuantityString(R.plurals.n_songs_will_play_next, n, n),
            )
        }
        multiSelectionStateHolder.clearSelection()
    }

    fun playSelectedAlbums(albums: List<Album>) {
        if (albums.isEmpty()) return
        viewModelScope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit(context.getString(R.string.player_no_playable_songs_in_albums))
                    return@launch
                }

                val queueName = if (resolvedSelection.albums.size == 1) {
                    resolvedSelection.albums.first().title
                } else {
                    context.getString(R.string.player_queue_name_selected_albums)
                }

                playSongs(resolvedSelection.songs, resolvedSelection.songs.first(), queueName, null)
                _isSheetVisible.value = true

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit(
                        context.getString(R.string.player_only_first_n_albums_queued, MAX_ALBUM_BATCH_SELECTION),
                    )
                } else {
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_albums_queued_format,
                            resolvedSelection.albums.size,
                            resolvedSelection.songs.size,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing selected albums", e)
                _toastEvents.emit(context.getString(R.string.player_could_not_queue_albums))
            }
        }
    }

    fun addSelectedAlbumsAsNext(albums: List<Album>) {
        if (albums.isEmpty()) return

        viewModelScope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit("No playable songs found in selected albums")
                    return@launch
                }

                resolvedSelection.songs
                    .asReversed()
                    .forEach(::addSongNextToQueue)

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit("Only the first $MAX_ALBUM_BATCH_SELECTION albums were added as next")
                } else {
                    _toastEvents.emit("${resolvedSelection.albums.size} albums will play next")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error adding selected albums as next", e)
                _toastEvents.emit("Could not add selected albums as next")
            }
        }
    }

    fun addSelectedAlbumsToQueue(albums: List<Album>) {
        if (albums.isEmpty()) return

        viewModelScope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    _toastEvents.emit("No playable songs found in selected albums")
                    return@launch
                }

                resolvedSelection.songs.forEach(::addSongToQueue)

                if (resolvedSelection.wasTrimmed) {
                    _toastEvents.emit("Only the first $MAX_ALBUM_BATCH_SELECTION albums were added to queue")
                } else {
                    _toastEvents.emit("${resolvedSelection.albums.size} albums added to queue")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error adding selected albums to queue", e)
                _toastEvents.emit("Could not add selected albums to queue")
            }
        }
    }

    fun queueAndPlaySelectedAlbums(albums: List<Album>) {
        playSelectedAlbums(albums)
    }

    fun likeSelectedSongs(songs: List<Song>) {
        viewModelScope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var likedCount = 0
            songs.forEach { song ->
                if (!favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song, true)
                    favIds.add(song.id)
                    likedCount++
                }
            }
            if (likedCount > 0) {
                _toastEvents.emit(
                    context.resources.getQuantityString(R.plurals.n_songs_added_to_favorites, likedCount, likedCount),
                )
            } else {
                _toastEvents.emit(context.getString(R.string.player_all_songs_already_in_favorites))
            }
            multiSelectionStateHolder.clearSelection()
        }
    }

    /**
     * Removes all selected songs from favorites.
     * Clears selection after unliking.
     */
    fun unlikeSelectedSongs(songs: List<Song>) {
        viewModelScope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var unlikedCount = 0
            songs.forEach { song ->
                if (favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song, false)
                    favIds.remove(song.id)
                    unlikedCount++
                }
            }
            if (unlikedCount > 0) {
                _toastEvents.emit(
                    context.resources.getQuantityString(
                        R.plurals.n_songs_removed_from_favorites,
                        unlikedCount,
                        unlikedCount,
                    ),
                )
            } else {
                _toastEvents.emit(context.getString(R.string.player_no_songs_were_in_favorites))
            }
            multiSelectionStateHolder.clearSelection()
        }
    }

    /**
     * Shares all selected songs as a ZIP file.
     * Clears selection after initiating share.
     */
    fun shareSelectedAsZip(songs: List<Song>) {
        viewModelScope.launch {
            _toastEvents.emit(context.getString(R.string.player_creating_zip))

            val result = ZipShareHelper.createAndShareZip(context, songs)

            result.onSuccess {
                multiSelectionStateHolder.clearSelection()
            }.onFailure { error ->
                _toastEvents.emit(
                    context.getString(R.string.player_share_zip_failed_format, error.localizedMessage ?: ""),
                )
            }
        }
    }

    private suspend fun resolveSelectedAlbumSongs(albums: List<Album>): ResolvedAlbumSelection {
        val albumsToProcess = albums.take(MAX_ALBUM_BATCH_SELECTION)
        val wasTrimmed = albums.size > albumsToProcess.size

        val songs = withContext(Dispatchers.IO) {
            buildList {
                albumsToProcess.forEach { album ->
                    val albumSongs = musicRepository.getSongsForAlbum(album.id).first()
                    if (albumSongs.isNotEmpty()) {
                        addAll(sortSongsForAlbumSelection(albumSongs))
                    }
                }
            }
        }

        return ResolvedAlbumSelection(
            albums = albumsToProcess,
            songs = songs,
            wasTrimmed = wasTrimmed
        )
    }

    private fun sortSongsForAlbumSelection(songs: List<Song>): List<Song> {
        return songs.sortedWith(
            compareBy<Song> { it.discNumber ?: 1 }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
    }

    /**
     * Deletes all selected songs from device with confirmation.
     * Shows a single confirmation dialog for all songs.
     */
    private var pendingBatchDeleteSongs: List<Song>? = null
    private var pendingBatchDeleteSkippedCount: Int = 0
    private var pendingBatchDeleteOnComplete: (() -> Unit)? = null

    fun deleteSelectedFromDevice(activity: Activity, songs: List<Song>, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Filter out currently playing song
            val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
            val deletableSongs = songs.filter { it.id != currentSongId }

            if (deletableSongs.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.player_cannot_delete_currently_playing))
                return@launch
            }

            val skippedCount = songs.size - deletableSongs.size

            // On Android 11+, use system batch delete dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = deletableSongs.mapNotNull { song ->
                    song.id.toLongOrNull()?.let { id ->
                        com.unshoo.pixelmusic.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.unshoo.pixelmusic.utils.MediaStorePermissionHelper
                        .createDeleteRequestIntentSender(activity, uris)
                    if (intentSender != null) {
                        pendingBatchDeleteSongs = deletableSongs
                        pendingBatchDeleteSkippedCount = skippedCount
                        pendingBatchDeleteOnComplete = onComplete
                        _deletePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            // Fallback for older Android or non-MediaStore songs
            val confirmed = showMultiDeleteConfirmation(activity, deletableSongs.size)
            if (!confirmed) {
                onComplete()
                return@launch
            }

            var successCount = 0
            deletableSongs.forEach { song ->
                val success = metadataEditStateHolder.deleteSong(song)
                if (success) {
                    removeFromMediaControllerQueue(song.id)
                    removeSong(song)
                    successCount++
                }
            }

            when {
                successCount == deletableSongs.size && skippedCount == 0 ->
                    _toastEvents.emit(
                        context.resources.getQuantityString(R.plurals.n_files_deleted, successCount, successCount),
                    )
                successCount == deletableSongs.size && skippedCount > 0 ->
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_batch_delete_files_deleted_skipped_format,
                            successCount,
                            skippedCount,
                        ),
                    )
                successCount > 0 ->
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_batch_delete_partial_format,
                            successCount,
                            deletableSongs.size,
                        ),
                    )
                else ->
                    _toastEvents.emit(context.getString(R.string.player_delete_files_failed))
            }

            multiSelectionStateHolder.clearSelection()
            onComplete()
        }
    }

    private suspend fun showMultiDeleteConfirmation(activity: Activity, count: Int): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(
                        context.resources.getQuantityString(
                            R.plurals.delete_songs_confirmation_title,
                            count,
                            count,
                        ),
                    )
                    .setMessage(context.getString(R.string.delete_songs_permanent_message))
                    .setPositiveButton(context.getString(R.string.delete_action)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(context.getString(R.string.cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}){
        viewModelScope.launch {
            // Failsafe: Prevent deleting the currently playing song
            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                _toastEvents.emit(context.getString(R.string.player_cannot_delete_currently_playing))
                onResult(false)
                return@launch
            }

            // On Android 11+, use the system delete confirmation dialog via MediaStore.createDeleteRequest()
            // which both confirms AND handles deletion in one step (no MANAGE_EXTERNAL_STORAGE needed).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val songLongId = song.id.toLongOrNull()
                val intentSender = if (songLongId != null && songLongId > 0) {
                    com.unshoo.pixelmusic.utils.MediaStorePermissionHelper
                        .createDeleteRequestForSong(activity, songLongId)
                } else null
                if (intentSender != null) {
                    pendingDeleteSong = song
                    pendingDeleteCallback = onResult
                    _deletePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            // For non-local songs (YouTube / cloud sources), there is no physical file to delete.
            // The song ID is non-numeric (e.g. a YouTube video ID), so the MediaStore path above is
            // skipped. We just confirm with the user and remove it from the library.
            val hasNoLocalFile = song.youtubeId != null || song.path.isBlank()
            if (hasNoLocalFile) {
                val userConfirmed = songRemovalStateHolder.showDeleteConfirmation(activity, song)
                if (!userConfirmed) {
                    onResult(false)
                    return@launch
                }
                _toastEvents.emit(context.getString(R.string.player_file_deleted))
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                onResult(true)
                return@launch
            }

            // Fallback for older Android or files not in MediaStore
            val userConfirmed = songRemovalStateHolder.showDeleteConfirmation(activity, song)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }

            val success = songRemovalStateHolder.deleteSongFile(song)
            if (success) {
                _toastEvents.emit(context.getString(R.string.player_file_deleted))
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                onResult(true)
            } else {
                _toastEvents.emit(context.getString(R.string.player_delete_file_not_found))
                onResult(false)
            }
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean) {
        // Handle batch delete
        val batchSongs = pendingBatchDeleteSongs
        if (batchSongs != null) {
            val skippedCount = pendingBatchDeleteSkippedCount
            val onComplete = pendingBatchDeleteOnComplete
            pendingBatchDeleteSongs = null
            pendingBatchDeleteSkippedCount = 0
            pendingBatchDeleteOnComplete = null
            viewModelScope.launch {
                if (granted) {
                    // System already deleted the files — clean up library
                    batchSongs.forEach { song ->
                        removeFromMediaControllerQueue(song.id)
                        removeSong(song)
                    }
                    val count = batchSongs.size
                    if (skippedCount > 0) {
                        _toastEvents.emit(
                            context.getString(
                                R.string.player_batch_delete_files_deleted_skipped_format,
                                count,
                                skippedCount,
                            ),
                        )
                    } else {
                        _toastEvents.emit(
                            context.resources.getQuantityString(R.plurals.n_files_deleted, count, count),
                        )
                    }
                } else {
                    _toastEvents.emit(context.getString(R.string.player_deletion_cancelled))
                }
                multiSelectionStateHolder.clearSelection()
                onComplete?.invoke()
            }
            return
        }

        // Handle single delete
        val song = pendingDeleteSong ?: return
        val callback = pendingDeleteCallback
        pendingDeleteSong = null
        pendingDeleteCallback = null
        viewModelScope.launch {
            if (granted) {
                // The system already deleted the file — just clean up the library
                _toastEvents.emit(context.getString(R.string.player_file_deleted))
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    suspend fun removeSong(song: Song) = withContext(Dispatchers.IO) {
        try {
            runCatching { musicRepository.setFavoriteStatusWithMetadata(song, false, awaitRemoteSync = false) }
            withContext(Dispatchers.Main.immediate) {
                playbackStateHolder.setCurrentPosition(0L)
                _playerUiState.update { currentState ->
                    currentState.copy(
                        currentPlaybackQueue = currentState.currentPlaybackQueue.removeSongById(song.id),
                        currentQueueSourceName = ""
                    )
                }
                _isSheetVisible.value = false
            }
            songRemovalStateHolder.removeSongFromLibrary(song)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error in removeSong for ${song.id}: ${e.message}", e)
        }
    }

    private fun removeFromMediaControllerQueue(songId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == songId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }

    private fun hasRemoteQueueItems(remoteMediaClient: RemoteMediaClient): Boolean {
        val mediaQueueCount = remoteMediaClient.mediaQueue.itemCount
        val statusQueueCount = remoteMediaClient.mediaStatus?.queueItems?.size ?: 0
        val snapshotQueueCount = castTransferStateHolder.lastRemoteQueue.size
        return mediaQueueCount > 0 || statusQueueCount > 0 || snapshotQueueCount > 0
    }

    private fun remoteQueueMatchesLocalQueue(
        remoteMediaClient: RemoteMediaClient,
        localQueue: List<Song>,
        localStartSong: Song?
    ): Boolean {
        if (localQueue.isEmpty()) return true

        val localQueueIds = localQueue.map { it.id }
        val status = remoteMediaClient.mediaStatus
        val remoteQueueIdsFromStatus = status
            ?.queueItems
            ?.mapNotNull { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        val remoteQueueIdsFromSnapshot = castTransferStateHolder.lastRemoteQueue.map { it.id }

        val queueMatches = when {
            remoteQueueIdsFromStatus.size == localQueueIds.size ->
                remoteQueueIdsFromStatus == localQueueIds
            remoteQueueIdsFromSnapshot.size == localQueueIds.size ->
                remoteQueueIdsFromSnapshot == localQueueIds
            remoteQueueIdsFromStatus.isNotEmpty() -> false
            remoteQueueIdsFromSnapshot.isNotEmpty() -> false
            else -> false
        }

        if (!queueMatches) return false

        val expectedSongId = localStartSong?.id ?: return true
        val remoteCurrentSongId = status
            ?.let { mediaStatus ->
                mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId())
                    ?.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            ?: castTransferStateHolder.lastRemoteSongId

        return remoteCurrentSongId == null || remoteCurrentSongId == expectedSongId
    }

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
            } else {
                val localQueue = _playerUiState.value.currentPlaybackQueue.toList()
                val startSong = playbackStateHolder.stablePlayerState.value.currentSong ?: localQueue.firstOrNull()
                val remoteHasQueue = hasRemoteQueueItems(remoteMediaClient)
                val remoteQueueAligned = remoteQueueMatchesLocalQueue(remoteMediaClient, localQueue, startSong)
                val shouldResumeRemoteQueue = remoteHasQueue && (localQueue.isEmpty() || remoteQueueAligned)

                if (shouldResumeRemoteQueue) {
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else if (localQueue.isNotEmpty() && startSong != null) {
                    Timber.tag(CAST_LOG_TAG).i(
                        "Remote queue out of sync. Reloading remote queue (local=%d status=%d snapshot=%d).",
                        localQueue.size,
                        remoteMediaClient.mediaStatus?.queueItems?.size ?: 0,
                        castTransferStateHolder.lastRemoteQueue.size
                    )
                    viewModelScope.launch {
                        internalPlaySongs(localQueue, startSong, _playerUiState.value.currentQueueSourceName)
                    }
                } else if (remoteHasQueue) {
                    // No local queue available to reconcile; fallback to resuming remote queue.
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else {
                    Timber.tag(CAST_LOG_TAG).w("Cannot resume Cast playback: both local and remote queues are empty.")
                }
            }
        } else {
            runWhenMediaControllerReady {
                val controller = mediaController ?: return@runWhenMediaControllerReady
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.currentMediaItem == null) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                        when {
                            currentQueue.isNotEmpty() && currentSong != null -> {
                                viewModelScope.launch {
                                    transitionSchedulerJob?.cancel()
                                    internalPlaySongs(
                                        currentQueue.toList(),
                                        currentSong,
                                        _playerUiState.value.currentQueueSourceName
                                    )
                                }
                            }
                            currentSong != null -> {
                                loadAndPlaySong(currentSong)
                            }
                            else -> {
                                viewModelScope.launch {
                                    val fallbackSong = musicRepository.getFirstPlayableSong()
                                    if (fallbackSong != null) {
                                        loadAndPlaySong(fallbackSong)
                                    } else {
                                        mediaController?.takeIf { it.isConnected }?.play()
                                    }
                                }
                            }
                        }
                    } else {
                        val currentMediaId = controller.currentMediaItem?.mediaId
                        if (!currentMediaId.isNullOrBlank()) {
                            setPreparingSong(currentMediaId)
                        }
                        playbackStateHolder.playPause()
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        if (castStateHolder.castSession.value?.remoteMediaClient != null) {
            playbackStateHolder.seekTo(position)
        } else {
            runWhenMediaControllerReady { playbackStateHolder.seekTo(position) }
        }
    }

    fun nextSong() {
        if (castStateHolder.castSession.value?.remoteMediaClient != null) {
            playbackStateHolder.nextSong()
        } else {
            runWhenMediaControllerReady { playbackStateHolder.nextSong() }
        }
    }

    fun previousSong() {
        val command = {
            // Pass the in-memory queue media IDs so PlaybackStateHolder can resolve the
            // previous-song index without issuing N Binder IPC calls to the MediaController.
            val queueMediaIds = _playerUiState.value.currentPlaybackQueue.map { it.id }
            playbackStateHolder.previousSong(queueMediaIds.ifEmpty { null })
        }
        if (castStateHolder.castSession.value?.remoteMediaClient != null) {
            command()
        } else {
            runWhenMediaControllerReady(command)
        }
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    fun observeSongs(songIds: List<String>): Flow<List<Song>> {
        return musicRepository.getSongsByIds(songIds)
    }

    fun searchSongs(query: String): Flow<List<Song>> {
        return musicRepository.searchSongs(query)
    }

    suspend fun getSongs(songIds: List<String>) : List<Song>{
        return musicRepository.getSongsByIdsOnce(songIds)
    }

    fun setCurrentLibraryTabId(tabId: LibraryTabId) {
        _currentLibraryTabId.value = tabId
    }

    //Sorting
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        val validOption = SortOption.fromStorageKey(sortOption.storageKey, SortOption.SONGS, SortOption.SongTitleAZ)
        libraryStateHolder.sortSongs(validOption, persist)
        _playerUiState.update { it.copy(currentSongSortOption = validOption) }
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setSongsSortOption(validOption.storageKey)
            }
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        val validOption = SortOption.fromStorageKey(sortOption.storageKey, SortOption.ALBUMS, SortOption.AlbumTitleAZ)
        libraryStateHolder.sortAlbums(validOption, persist)
        _playerUiState.update { it.copy(currentAlbumSortOption = validOption) }
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setAlbumsSortOption(validOption.storageKey)
            }
        }
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        val validOption = SortOption.fromStorageKey(sortOption.storageKey, SortOption.ARTISTS, SortOption.ArtistNameAZ)
        libraryStateHolder.sortArtists(validOption, persist)
        _playerUiState.update { it.copy(currentArtistSortOption = validOption) }
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setArtistsSortOption(validOption.storageKey)
            }
        }
    }

    fun setArtistLibraryFilter(filter: String) {
        viewModelScope.launch {
            userPreferencesRepository.setArtistLibraryFilter(filter)
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        val validOption = SortOption.fromStorageKey(sortOption.storageKey, SortOption.LIKED, SortOption.LikedSongDateLiked)
        libraryStateHolder.sortFavoriteSongs(validOption, persist)
        _playerUiState.update { it.copy(currentFavoriteSortOption = validOption) }
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setLikedSongsSortOption(validOption.storageKey)
            }
        }
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        val validOption = SortOption.fromStorageKey(sortOption.storageKey, SortOption.FOLDERS, SortOption.FolderNameAZ)
        libraryStateHolder.sortFolders(validOption, persist)
        _playerUiState.update { it.copy(currentFolderSortOption = validOption) }
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setFoldersSortOption(validOption.storageKey)
            }
        }
    }


    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
            folderNavigationStateHolder.setFoldersPlaylistViewState(
                isPlaylistView = isPlaylistView,
                updateUiState = { mutation -> _playerUiState.update(mutation) }
            )
        }
    }

    fun setFoldersSource(source: FolderSource) {
        if (!ENABLE_FOLDERS_SOURCE_SWITCHING) return
        viewModelScope.launch {
            userPreferencesRepository.setFoldersSource(source)
        }
    }

    fun navigateToFolder(path: String) {
        folderNavigationStateHolder.navigateToFolder(
            path = path,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun navigateBackFolder() {
        folderNavigationStateHolder.navigateBackFolder(
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun showAiPlaylistSheet() {
        aiStateHolder.showAiPlaylistSheet()
    }

    fun setAlbumsListView(isList: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumsListView(isList)
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        searchStateHolder.updateSearchFilter(filterType)
    }

    fun loadSearchHistory(limit: Int = 15) {
        searchStateHolder.loadSearchHistory(limit)
    }

    fun onSearchQuerySubmitted(query: String) {
        searchStateHolder.onSearchQuerySubmitted(query)
    }

    fun performSearch(query: String) {
        searchStateHolder.performSearch(query)
    }

    fun loadMoreSearch() {
        searchStateHolder.loadMoreSearch()
    }

    fun deleteSearchHistoryItem(query: String) {
        searchStateHolder.deleteSearchHistoryItem(query)
    }

    fun clearSearchHistory() {
        searchStateHolder.clearSearchHistory()
    }

    fun dismissAiPlaylistSheet() {
        aiStateHolder.dismissAiPlaylistSheet()
    }

    fun clearAiPlaylistError() {
        aiStateHolder.clearAiPlaylistError()
    }

    fun generateAiPlaylist(
        prompt: String,
        minLength: Int,
        maxLength: Int,
        saveAsPlaylist: Boolean = false,
        playlistName: String? = null
    ) {
        aiStateHolder.generateAiPlaylist(
            prompt = prompt,
            minLength = minLength,
            maxLength = maxLength,
            saveAsPlaylist = saveAsPlaylist,
            playlistName = playlistName
        )
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        aiStateHolder.regenerateDailyMixWithPrompt(prompt)
    }

    fun retryLastPlaylistGeneration() {
        aiStateHolder.retryLastPlaylistGeneration()
    }

    fun retryLastMetadataGeneration() {
        aiStateHolder.retryLastMetadataGeneration()
    }

    fun clearQueueExceptCurrent() {
        runOnMainImmediate {
            val controller = mediaController ?: return@runOnMainImmediate
            val currentSongIndex = controller.currentMediaItemIndex
            if (currentSongIndex == C.INDEX_UNSET) return@runOnMainImmediate
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentSongIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        castRouteStateHolder.selectRoute(route) { message ->
            viewModelScope.launch { _toastEvents.emit(message) }
        }
    }

    fun disconnect(resetConnecting: Boolean = true) {
        castRouteStateHolder.disconnect(resetConnecting = resetConnecting)
    }

    fun setRouteVolume(volume: Int) {
        castRouteStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes() {
        castRouteStateHolder.refreshCastRoutes(viewModelScope)
    }



    override fun onCleared() {
        mediaControllerHealthJob?.cancel()
        mediaControllerHealthJob = null
        mediaControllerReconnectJob?.cancel()
        mediaControllerReconnectJob = null
        pendingControllerCommands.clear()
        mediaControllerConnectionGeneration++
        mediaControllerPlaybackListener?.let { listener ->
            mediaController?.removeListener(listener)
            mediaControllerPlaybackListener = null
        }
        val controllerToRelease = mediaController
        mediaController = null
        controllerToRelease?.let { controller ->
            playbackStateHolder.clearMediaController(controller)
            controller.release()
        }
        val futureToRelease = activeMediaControllerFuture
        activeMediaControllerFuture = null
        futureToRelease?.let {
            androidx.media3.session.MediaController.releaseFuture(it)
        }
        super.onCleared()
        directPlaybackJob?.cancel()
        directPlaybackApplyJob?.cancel()
        remoteQueueLoadJob?.cancel()
        castSongUiSyncJob?.cancel()
        stopProgressUpdates()
        playbackStateHolder.onCleared()
        listeningStatsTracker.onCleared()
        dailyMixStateHolder.onCleared()
        lyricsStateHolder.onCleared()
        themeStateHolder.onCleared()
        castTransferStateHolder.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()
        queueUndoStateHolder.onCleared()
        playlistDismissUndoStateHolder.onCleared()
    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) {
        sleepTimerStateHolder.setSleepTimer(durationMinutes)
    }

    fun playCounted(count: Int) {
        sleepTimerStateHolder.playCounted(count)
    }

    fun cancelCountedPlay() {
        sleepTimerStateHolder.cancelCountedPlay()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentSongId = stablePlayerState.value.currentSong?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentSongId)
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissPlaylistAndShowUndo() {
        collapsePlayerSheet()
        setMiniPlayerDismissing(true)
        cancelPendingDirectPlaybackBuild()
        clearPreparingSongIfMatching()
        dualPlayerEngine.cancelNext()
        lastQueueSignature = null
        playlistDismissUndoStateHolder.dismissPlaylistAndShowUndo(
            scope = viewModelScope,
            currentSong = playbackStateHolder.stablePlayerState.value.currentSong,
            queue = _playerUiState.value.currentPlaybackQueue,
            queueName = _playerUiState.value.currentQueueSourceName,
            position = playbackStateHolder.currentPosition.value,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            disconnectRemoteIfNeeded = {
                val hasCastSession = castStateHolder.castSession.value != null
                val shouldDisconnectRemote = hasCastSession ||
                    castStateHolder.isRemotePlaybackActive.value ||
                    castStateHolder.isCastConnecting.value
                if (shouldDisconnectRemote) {
                    if (hasCastSession) {
                        castTransferStateHolder.skipNextTransferBack()
                    }
                    disconnect()
                }
            },
            clearPlayback = {
                runOnMainImmediate {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                    lastQueueSignature = null
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.reset()
                    mediaController?.sendCustomCommand(
                        SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                }
            },
            clearStablePlaybackState = {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        totalDuration = 0L
                    )
                }
            },
            setCurrentPosition = { playbackStateHolder.setCurrentPosition(it) },
            setSheetVisible = { _isSheetVisible.value = it },
            onDismissComplete = { setMiniPlayerDismissing(false) }
        )
    }

    fun hideDismissUndoBar() {
        setMiniPlayerDismissing(false)
        playlistDismissUndoStateHolder.hideDismissUndoBar(
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onDismissComplete = { setMiniPlayerDismissing(false) }
        )
    }

    fun undoDismissPlaylist() {
        setMiniPlayerDismissing(false)
        playlistDismissUndoStateHolder.undoDismissPlaylist(
            scope = viewModelScope,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            playSongs = { songs, startSong, queueName ->
                playSongs(songs, startSong, queueName)
            },
            seekTo = { position -> mediaController?.seekTo(position) },
            setSheetVisible = { _isSheetVisible.value = it },
            setSheetCollapsed = { _sheetState.value = PlayerSheetState.COLLAPSED },
            emitToast = { message -> _toastEvents.emit(message) }
        )
    }

    fun getSongUrisForGenre(genreId: String): Flow<List<String>> {
        return musicRepository.getMusicByGenre(genreId).map { songs ->
            songs.take(4).mapNotNull { it.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun showSortingSheet() {
        libraryTabsStateHolder.showSortingSheet(_isSortingSheetVisible)
    }

    fun hideSortingSheet() {
        libraryTabsStateHolder.hideSortingSheet(_isSortingSheetVisible)
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        libraryTabsStateHolder.onLibraryTabSelected(
            tabIndex = tabIndex,
            libraryTabs = libraryTabsFlow.value,
            loadedTabs = _loadedTabs,
            currentLibraryTabId = _currentLibraryTabId,
            saveLastTabIndex = { index -> userPreferencesRepository.saveLastLibraryTabIndex(index) },
            scope = viewModelScope,
            loadSongs = { loadSongsIfNeeded() },
            loadAlbums = { loadAlbumsIfNeeded() },
            loadArtists = { loadArtistsIfNeeded() },
            loadFolders = { loadFoldersFromRepository() }
        )
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        viewModelScope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    fun refreshLibraryTab(tabId: LibraryTabId) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tabId == LibraryTabId.FOLDERS) {
                // Bug 5+10 fix: use forceRefresh() (REPLACE policy, no throttle) so
                // pull-to-refresh on the Folders tab always runs regardless of when
                // the last sync happened. sync() has a 6h guard that silently skips.
                syncManager.forceRefresh()
                return@launch
            }
            if (userPreferencesRepository.localModeEnabledFlow.first() || !connectivityStateHolder.isOnline.value || !unshoo.ianshulyadav.pixelmusic.innertube.YouTube.hasLoginCookie()) {
                // Also use forceRefresh() here so local-only users get an immediate
                // incremental rescan instead of a potentially throttled no-op.
                syncManager.forceRefresh()
                return@launch
            }
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> {
                        youTubeLibrarySyncManager.syncLikedSongs()
                        syncManager.forceRefresh()
                    }
                    LibraryTabId.LIKED -> {
                        youTubeLibrarySyncManager.syncLikedSongs()
                    }
                    LibraryTabId.ALBUMS -> {
                        youTubeLibrarySyncManager.syncLikedAlbums()
                    }
                    LibraryTabId.ARTISTS -> {
                        youTubeLibrarySyncManager.syncSubscribedArtists(forceFull = true)
                    }
                    LibraryTabId.PLAYLISTS -> {
                        youTubeLibrarySyncManager.syncLikedPlaylists()
                        syncManager.forceRefresh(syncCloud = true)
                    }
                    else -> {
                        syncManager.forceRefresh()
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to sync YouTube tab: $tabId", e)
            }
        }
    }

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
        viewModelScope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                musicRepository.getSong(song.id).first()
            } ?: return@launch
            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = hydrated
            }
        }
    }

    fun clearSelectedSongForInfo() {
        _selectedSongForInfo.value = null
    }

    private var preCacheLyricsJob: Job? = null

    private fun preCacheLyricsForSongs(songs: List<Song>, sourcePref: LyricsSourcePreference) {
        preCacheLyricsJob?.cancel()
        preCacheLyricsJob = viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                try {
                    musicRepository.getLyrics(song = song, sourcePreference = sourcePref)
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Failed to pre-cache lyrics for song ${song.title}: ${e.message}")
                }
            }
        }
    }

    private fun loadLyricsForCurrentSong() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        // Delegate to LyricsStateHolder
        lyricsStateHolder.loadLyricsForSong(currentSong, lyricsSourcePreference.value)
    }

    fun editSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newAlbumArtist: String,
        newComposer: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        viewModelScope.launch {
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editSongMetadata via Holder")

            // On Android 11+, request MediaStore write permission for local songs
            val songId = song.id.toLongOrNull()
            if (songId != null && songId > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = com.unshoo.pixelmusic.utils.MediaStorePermissionHelper
                    .createWriteRequestForSong(context, songId)
                if (intentSender != null) {
                    // Store pending edit and request permission from the UI
                    pendingMetadataEdit = PendingMetadataEdit(
                        song = song,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        albumArtist = newAlbumArtist,
                        composer = newComposer,
                        genre = newGenre,
                        lyrics = newLyrics,
                        trackNumber = newTrackNumber,
                        discNumber = newDiscNumber,
                        replayGainTrackGainDb = newReplayGainTrackGainDb,
                        replayGainAlbumGainDb = newReplayGainAlbumGainDb,
                        coverArtUpdate = coverArtUpdate
                    )
                    _writePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            performMetadataEdit(song, newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics,
                newTrackNumber, newDiscNumber, newReplayGainTrackGainDb, newReplayGainAlbumGainDb, coverArtUpdate)
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore write permission. */
    fun onWritePermissionResult(granted: Boolean) {
        // Handle batch genre edit
        val batchGenre = pendingBatchGenreEdit
        if (batchGenre != null) {
            pendingBatchGenreEdit = null
            if (!granted) {
                viewModelScope.launch {
                    _toastEvents.emit(context.getString(R.string.player_permission_denied_edit_files))
                }
                return
            }
            viewModelScope.launch { performBatchEditGenre(batchGenre.first, batchGenre.second) }
            return
        }

        // Handle lyrics save retry
        val pendingLyrics = pendingLyricsSave
        if (pendingLyrics != null) {
            pendingLyricsSave = null
            if (!granted) {
                viewModelScope.launch {
                    _toastEvents.emit(context.getString(R.string.player_permission_denied_save_lyrics))
                }
                return
            }
            performLyricsSave(pendingLyrics.song, pendingLyrics.lyrics, pendingLyrics.preferSynced)
            return
        }

        // Handle single metadata edit
        val pending = pendingMetadataEdit ?: return
        pendingMetadataEdit = null
        if (!granted) {
            viewModelScope.launch {
                _toastEvents.emit(context.getString(R.string.player_permission_denied_edit_this_file))
            }
            return
        }
        viewModelScope.launch {
            performMetadataEdit(
                pending.song, pending.title, pending.artist, pending.album,
                pending.albumArtist, pending.composer, pending.genre, pending.lyrics,
                pending.trackNumber, pending.discNumber,
                pending.replayGainTrackGainDb, pending.replayGainAlbumGainDb, pending.coverArtUpdate
            )
        }
    }

    fun saveLyricsToFile(song: Song, lyrics: Lyrics, preferSynced: Boolean) {
        val lrcContent = LyricsUtils.toLrcString(lyrics, preferSynced)
        if (lrcContent.isEmpty()) {
            viewModelScope.launch { _toastEvents.emit(context.getString(R.string.no_lyrics_to_save)) }
            return
        }

        val songFile = java.io.File(song.path)
        val lrcFile = java.io.File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")

        // Android 11+ check: if file exists and we might not have permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && lrcFile.exists() && !lrcFile.canWrite()) {
            val uri = com.unshoo.pixelmusic.utils.MediaStorePermissionHelper.getMediaStoreUri(context, lrcFile.absolutePath)
            if (uri != null) {
                val intentSender = com.unshoo.pixelmusic.utils.MediaStorePermissionHelper.createWriteRequestIntentSender(context, listOf(uri))
                if (intentSender != null) {
                    pendingLyricsSave = PendingLyricsSave(song, lyrics, preferSynced)
                    viewModelScope.launch { _writePermissionRequest.emit(intentSender) }
                    return
                }
            }
        }

        performLyricsSave(song, lyrics, preferSynced)
    }

    private fun performLyricsSave(song: Song, lyrics: Lyrics, preferSynced: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songFile = java.io.File(song.path)
                val lrcFile = java.io.File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")
                val lrcContent = LyricsUtils.toLrcString(lyrics, preferSynced)

                lrcFile.writeText(lrcContent, Charsets.UTF_8)
                _toastEvents.emit(context.getString(R.string.lyrics_saved_successfully))
                
                // If it was the current song, we might want to refresh the lyrics in state if it migrated from remote to local
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    loadLyricsForCurrentSong()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lyrics to file")
                _toastEvents.emit(context.getString(R.string.lyrics_save_failed))
            }
        }
    }

    private suspend fun performMetadataEdit(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newAlbumArtist: String,
        newComposer: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String?,
        newReplayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        val previousAlbumArt = song.albumArtUriString

        val result = metadataEditStateHolder.saveMetadata(
            song = song,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newAlbumArtist = newAlbumArtist,
            newComposer = newComposer,
            newGenre = newGenre,
            newLyrics = newLyrics,
            newTrackNumber = newTrackNumber,
            newDiscNumber = newDiscNumber,
            newReplayGainTrackGainDb = newReplayGainTrackGainDb,
            newReplayGainAlbumGainDb = newReplayGainAlbumGainDb,
            coverArtUpdate = coverArtUpdate
        )

        Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result success=${result.success}")

        if (result.success && result.updatedSong != null) {
            val updatedSong = result.updatedSong
            val refreshedAlbumArtUri = result.updatedAlbumArtUri

            invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)

            _playerUiState.update { state ->
                val updatedQueue = state.currentPlaybackQueue.replaceSong(updatedSong)
                if (updatedQueue === state.currentPlaybackQueue) {
                    state
                } else {
                    state.copy(currentPlaybackQueue = updatedQueue)
                }
            }

            // libraryStateHolder.updateSong() below handles the SSOT update

            // Update the LibraryStateHolder which drives the UI
            libraryStateHolder.updateSong(updatedSong)

            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = updatedSong,
                        lyrics = result.parsedLyrics
                    )
                }

                // Update the player's current MediaItem to refresh notification artwork
                // This is efficient: only replaces metadata, not the media stream
                runOnMainImmediate {
                    val controller = playbackStateHolder.mediaController
                    if (controller != null) {
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                            val currentPosition = controller.currentPosition
                            val newMediaItem = MediaItemBuilder.build(updatedSong)
                            controller.replaceMediaItem(currentIndex, newMediaItem)
                            // Restore position since replaceMediaItem may reset it
                            controller.seekTo(currentIndex, currentPosition)
                        }
                    }
                }
            }

            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = updatedSong
            }

            if (coverArtUpdate != null) {
                purgeAlbumArtThemes(previousAlbumArt, updatedSong.albumArtUriString)
                val paletteTargetUri = updatedSong.albumArtUriString
                if (paletteTargetUri != null) {
                    themeStateHolder.getAlbumColorSchemeFlow(paletteTargetUri)
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(paletteTargetUri.toUri(), currentUri, isPreload = false)
                } else {
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(null, currentUri, isPreload = false)
                }
            }

            // No need for full library sync - file, MediaStore, and local DB are already updated
            // syncManager.sync() was removed to avoid unnecessary wait time
            _toastEvents.emit(context.getString(R.string.metadata_updated_successfully))
        } else {
            val errorMessage = result.getUserFriendlyErrorMessage()
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Failed - ${result.error}: $errorMessage")
            _toastEvents.emit(errorMessage)
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        imageCacheManager.invalidateCoverArtCaches(*uriStrings)
    }

    private suspend fun purgeAlbumArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            // Cache invalidation delegated to ThemeStateHolder (if implemented) or relied on re-generation
            // individualAlbumColorSchemes was removed.
        }
    }

    suspend fun forceRegenerateAlbumPaletteForSong(song: Song): Boolean {
        val albumArtUri = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            // Full reset: clear all cached variants for this URI and recreate every style from scratch.
            themeStateHolder.forceRegenerateColorScheme(
                uriString = albumArtUri,
                regenerateAllStyles = true
            )
            true
        }.getOrDefault(false)
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        return aiStateHolder.generateAiMetadata(song, fields)
    }

    private fun updateSongInStates(
        updatedSong: Song,
        newLyrics: Lyrics? = null,
        isLoadingLyrics: Boolean? = null
    ) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val updatedQueue = currentQueue.replaceSong(updatedSong)

        if (updatedQueue !== currentQueue) {
            _playerUiState.update { it.copy(currentPlaybackQueue = updatedQueue) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update lyrics if they are explicitly passed
            val finalLyrics = newLyrics ?: state.lyrics
            state.copy(
                currentSong = updatedSong,
                lyrics = if (state.currentSong?.id == updatedSong.id) finalLyrics else state.lyrics,
                isLoadingLyrics = isLoadingLyrics ?: state.isLoadingLyrics
            )
        }
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.fetchLyricsForSong(currentSong, forcePickResults, lyricsSourcePreference.value) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        lyricsStateHolder.searchLyricsManually(title, artist)
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.acceptLyricsSearchResult(result, currentSong)
    }

    fun resetLyricsForCurrentSong() {
        val songId = stablePlayerState.value.currentSong?.id?.toLongOrNull() ?: return
        lyricsStateHolder.resetLyrics(songId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    fun resetAllLyrics() {
        lyricsStateHolder.resetAllLyrics()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport) {
        val currentSong = stablePlayerState.value.currentSong
        lyricsStateHolder.importLyricsFromFile(songId, validatedImport, currentSong)
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }

    fun playSong(song: Song) {
        playSongs(listOf(song), song)
        _isSheetVisible.value = true
        _sheetState.value = PlayerSheetState.EXPANDED
    }

    fun prepareBenchmarkPlayerFromLibrary() {
        viewModelScope.launch {
            repeat(90) { attempt ->
                val controllerReady = mediaController != null
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }
                Log.i(
                    "PixelMusicBenchmark",
                    "prepare player attempt=$attempt controllerReady=$controllerReady songs=${songs.size}"
                )
                if (controllerReady && songs.isNotEmpty()) {
                    playSongs(songs, songs.first(), "Benchmark Player")
                    delay(700L)
                    collapsePlayerSheet()
                    Log.i("PixelMusicBenchmark", "Benchmark player prepared with ${songs.first().title}")
                    return@launch
                }
                delay(500L)
            }
            Log.w("PixelMusicBenchmark", "Unable to prepare benchmark player from library")
        }
    }

    private var pendingBatchGenreEdit: Pair<List<Song>, String>? = null

    fun batchEditGenre(songs: List<Song>, newGenre: String) {
        if (songs.isEmpty()) return

        viewModelScope.launch {
            // On Android 11+, request write permission for all local songs upfront
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = songs.mapNotNull { song ->
                    song.id.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
                        com.unshoo.pixelmusic.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.unshoo.pixelmusic.utils.MediaStorePermissionHelper
                        .createWriteRequestIntentSender(context, uris)
                    if (intentSender != null) {
                        pendingBatchGenreEdit = songs to newGenre
                        _writePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            performBatchEditGenre(songs, newGenre)
        }
    }

    private suspend fun performBatchEditGenre(songs: List<Song>, newGenre: String) {
            Log.d("PlayerViewModel", "Starting batch genre update for ${songs.size} songs to '$newGenre'")
            _toastEvents.emit(context.getString(R.string.player_updating_n_songs, songs.size))

            var successCount = 0
            var failCount = 0

            songs.forEach { song ->
                val sourceSong = if (song.lyrics != null) {
                    song
                } else {
                    withContext(Dispatchers.IO) {
                        musicRepository.getSong(song.id).first()
                    } ?: song
                }

                val result = metadataEditStateHolder.saveMetadata(
                    song = sourceSong,
                    newTitle = sourceSong.title,
                    newArtist = sourceSong.artist,
                    newAlbum = sourceSong.album,
                    newAlbumArtist = sourceSong.albumArtist ?: "",
                    newComposer = "",
                    newGenre = newGenre,
                    newLyrics = sourceSong.lyrics ?: "",
                    newTrackNumber = sourceSong.trackNumber,
                    newDiscNumber =  sourceSong.discNumber,
                    coverArtUpdate = null
                )

                if (result.success && result.updatedSong != null) {
                    successCount++
                    val updatedSong = result.updatedSong

                    // Optimistic update of UI flows
                    // libraryStateHolder.updateSong() below handles the SSOT update
                    libraryStateHolder.updateSong(updatedSong)

                    if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                        playbackStateHolder.updateStablePlayerState { it.copy(currentSong = updatedSong) }
                        runOnMainImmediate {
                            val controller = playbackStateHolder.mediaController
                            if (controller != null) {
                                val idx = controller.currentMediaItemIndex
                                if (idx != C.INDEX_UNSET) {
                                    controller.replaceMediaItem(idx, MediaItemBuilder.build(updatedSong))
                                }
                            }
                        }
                    }
                } else {
                    failCount++
                }
            }

            if (failCount == 0) {
                _toastEvents.emit(context.getString(R.string.player_batch_genre_updated_all, successCount))
            } else {
                _toastEvents.emit(
                    context.getString(R.string.player_batch_genre_updated_partial, successCount, failCount),
                )
            }
    }

    // Custom Genres Names
    val customGenres: StateFlow<Set<String>> = userPreferencesRepository.customGenresFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val customGenreIcons: StateFlow<Map<String, Int>> = userPreferencesRepository.customGenreIconsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val isGenreGridView: StateFlow<Boolean> = userPreferencesRepository.isGenreGridViewFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleGenreViewMode() {
        viewModelScope.launch {
            userPreferencesRepository.setGenreGridView(!isGenreGridView.value)
        }
    }

    fun addCustomGenre(genre: String, iconResId: Int? = null) {
        viewModelScope.launch {
            userPreferencesRepository.addCustomGenre(genre, iconResId)
        }
    }
}

internal fun Song.withRepositoryHydration(repositorySong: Song): Song {
    if (id != repositorySong.id) return this

    val hydratedArtworkUri = when {
        repositorySong.albumArtUriString.isNullOrBlank() -> albumArtUriString
        albumArtUriString.isNullOrBlank() -> repositorySong.albumArtUriString
        areEquivalentArtworkUrisForSong(id, albumArtUriString, repositorySong.albumArtUriString) ->
            albumArtUriString
        else -> repositorySong.albumArtUriString
    }

    return repositorySong.copy(
        contentUriString = repositorySong.contentUriString.ifBlank { contentUriString },
        albumArtUriString = hydratedArtworkUri,
        duration = repositorySong.duration.takeIf { it > 0L } ?: duration,
        lyrics = repositorySong.lyrics ?: lyrics
    )
}

internal fun areEquivalentArtworkUrisForSong(
    songId: String,
    firstUri: String?,
    secondUri: String?
): Boolean {
    if (firstUri == secondUri) return true
    if (firstUri.isNullOrBlank() || secondUri.isNullOrBlank()) return false

    val targetSongId = songId.toLongOrNull() ?: return false

    fun resolveUriSongId(uri: String): Long? {
        return LocalArtworkUri.parseSongId(uri)
            ?: SharedArtworkContentProvider.parseSongId(uri)
    }

    val firstSongId = resolveUriSongId(firstUri)
    val secondSongId = resolveUriSongId(secondUri)
    return firstSongId == targetSongId && secondSongId == targetSongId
}

internal fun Song.improvesLyricsLookupComparedTo(previousSong: Song): Boolean {
    return (previousSong.lyrics.isNullOrBlank() && !lyrics.isNullOrBlank()) ||
        (previousSong.path.isBlank() && path.isNotBlank()) ||
        (previousSong.contentUriString.isBlank() && contentUriString.isNotBlank())
}

internal fun parsePersistedLyrics(rawLyrics: String?): Lyrics? {
    val normalizedLyrics = rawLyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsedLyrics = LyricsUtils.parseLyrics(normalizedLyrics)
    return parsedLyrics.takeIf {
        !it.synced.isNullOrEmpty() || !it.plain.isNullOrEmpty()
    }
}
