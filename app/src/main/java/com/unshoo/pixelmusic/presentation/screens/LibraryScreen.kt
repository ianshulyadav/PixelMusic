@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.unshoo.pixelmusic.presentation.screens

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely

import android.os.Trace
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material.icons.rounded.ViewModule
import com.unshoo.pixelmusic.presentation.components.ToggleSegmentButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.ShimmerBox
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.MusicFolder
import com.unshoo.pixelmusic.data.model.FolderSource
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.StorageFilter
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageListTargetSize
import com.unshoo.pixelmusic.presentation.components.resolveMainScreenBottomGradientHeight
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.presentation.components.PlaylistArtCollage
import com.unshoo.pixelmusic.presentation.components.ReorderTabsSheet
import com.unshoo.pixelmusic.presentation.components.SongInfoBottomSheet
import com.unshoo.pixelmusic.presentation.components.subcomps.LibraryActionRow
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.components.MultiSelectionBottomSheet
import com.unshoo.pixelmusic.presentation.components.AlbumMultiSelectionOptionSheet
import androidx.compose.material3.HorizontalDivider
import com.unshoo.pixelmusic.presentation.components.PlaylistMultiSelectionBottomSheet
import com.unshoo.pixelmusic.presentation.components.QuickImportCard
import com.unshoo.pixelmusic.presentation.components.scrollReveal
import com.unshoo.pixelmusic.presentation.components.PlaylistCreationTypeDialog
import com.unshoo.pixelmusic.presentation.components.CreateAiPlaylistDialog
import com.unshoo.pixelmusic.presentation.components.subcomps.SelectionActionRow
import com.unshoo.pixelmusic.presentation.components.subcomps.SelectionCountPill
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerUiState
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.StablePlayerState
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistUiState
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.unshoo.pixelmusic.data.model.LibraryTabId
import com.unshoo.pixelmusic.data.model.getDisplayTitle
import com.unshoo.pixelmusic.data.model.toLibraryTabIdOrNull
import com.unshoo.pixelmusic.data.preferences.LibraryNavigationMode
import com.unshoo.pixelmusic.data.worker.SyncProgress
import com.unshoo.pixelmusic.presentation.screens.search.components.GenreTypography
import com.unshoo.pixelmusic.presentation.components.SyncProgressBar
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.utils.formatSongCount
import androidx.paging.compose.collectAsLazyPagingItems
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.TextButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unshoo.pixelmusic.presentation.components.AutoScrollingTextOnDemand
import com.unshoo.pixelmusic.presentation.screens.CreatePlaylistDialog
import com.unshoo.pixelmusic.presentation.components.PlaylistBottomSheet
import com.unshoo.pixelmusic.presentation.components.PlaylistContainer
import com.unshoo.pixelmusic.presentation.components.subcomps.PlayingEqIcon
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.focus.focusModifier
import com.unshoo.pixelmusic.data.model.PlaylistShapeType
import kotlinx.coroutines.flow.first
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.LoadState
import com.unshoo.pixelmusic.presentation.components.ExpressiveScrollBar
import com.unshoo.pixelmusic.presentation.components.LibrarySortBottomSheet
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.utils.itemsUnique
import java.io.File
import kotlin.math.abs

val ListExtraBottomGap = 30.dp
val PlayerSheetCollapsedCornerRadius = 32.dp
private const val MAX_ALBUM_MULTI_SELECTION = 6
private const val ENABLE_FOLDERS_SOURCE_TOGGLE = true
private const val ENABLE_FOLDERS_STORAGE_FILTER = false
private const val FOLDER_NAVIGATION_ROOT_KEY = "__folder_root__"
private const val FOLDER_NAVIGATION_FORWARD = 1
private const val FOLDER_NAVIGATION_BACKWARD = -1
private const val PULL_REFRESH_MIN_VISIBLE_MS = 900L
private const val PULL_REFRESH_MAX_VISIBLE_MS = 1_500L
private const val INLINE_SYNC_MIN_VISIBLE_MS = 600L


private data class LibraryScreenPlayerProjection(
    val currentFolder: MusicFolder? = null,
    val folderSourceRootPath: String = "",
    val folderSource: FolderSource = FolderSource.INTERNAL,
    val isFoldersPlaylistView: Boolean = false,
    val currentStorageFilter: StorageFilter = StorageFilter.ALL,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongDateLiked,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val isAlbumsListView: Boolean = false,
    val isSdCardAvailable: Boolean = false,
    val musicFolders: ImmutableList<MusicFolder> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = false,
    val isGeneratingAiMetadata: Boolean = false,
    val isSyncingLibrary: Boolean = false,
    val isLoadingInitialSongs: Boolean = false,
    val hideLocalMedia: Boolean = false
)

private fun PlayerUiState.toLibraryScreenProjection(): LibraryScreenPlayerProjection =
    LibraryScreenPlayerProjection(
        currentFolder = currentFolder,
        folderSourceRootPath = folderSourceRootPath,
        folderSource = folderSource,
        isFoldersPlaylistView = isFoldersPlaylistView,
        currentStorageFilter = currentStorageFilter,
        currentSongSortOption = currentSongSortOption,
        currentAlbumSortOption = currentAlbumSortOption,
        currentArtistSortOption = currentArtistSortOption,
        currentFavoriteSortOption = currentFavoriteSortOption,
        currentFolderSortOption = currentFolderSortOption,
        isAlbumsListView = isAlbumsListView,
        isSdCardAvailable = isSdCardAvailable,
        musicFolders = musicFolders,
        isLoadingLibraryCategories = isLoadingLibraryCategories,
        isGeneratingAiMetadata = isGeneratingAiMetadata,
        isSyncingLibrary = isSyncingLibrary,
        isLoadingInitialSongs = isLoadingInitialSongs,
        hideLocalMedia = hideLocalMedia
    )

@Immutable
data class FolderPlayerProjection(
    val currentSongId: String? = null,
    val currentSongPath: String? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false
)

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    songInfoBottomSheetViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    // La recolecciÃ³n de estados de alto nivel se mantiene mÃ­nima.
    val context = LocalContext.current // Added context
    val haptic = LocalHapticFeedback.current
    val lastTabIndex by playerViewModel.lastLibraryTabIndexFlow.collectAsStateWithLifecycle()
    val folderArtworkPreference by playerViewModel.userPreferencesRepository.folderArtworkPreferenceFlow.collectAsStateWithLifecycle(initialValue = "recently_added")
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquÃ­
    val scope = rememberCoroutineScope() // Mantener si se usa para acciones de UI
    val syncManager = playerViewModel.syncManager
    var isRefreshing by remember { mutableStateOf(false) }
    // The pull-to-refresh spinner is reserved for user gestures. Automatic sync
    // and long-running refresh work move through the slim linear indicator under
    // LibraryActionRow so the list stays put.
    val isFetchingChanges by syncManager.isFetchingChanges
        .collectAsStateWithLifecycle(initialValue = false)
    val isSyncing by syncManager.isSyncing
        .collectAsStateWithLifecycle(initialValue = false)
    // NOTE: syncProgress is NOT collected here. It is collected inside LibrarySyncOverlay
    // to avoid triggering recomposition of the entire LibraryScreen on every progress tick.

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showSinglePlaylistOptionsSheet by remember { mutableStateOf(false) }
    var selectedPlaylistForOptions by remember { mutableStateOf<com.unshoo.pixelmusic.data.model.Playlist?>(null) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val tabTitles by playerViewModel.libraryTabsFlow.collectAsStateWithLifecycle()
    val currentTabId by playerViewModel.currentLibraryTabId.collectAsStateWithLifecycle()
    val libraryNavigationMode by playerViewModel.libraryNavigationMode.collectAsStateWithLifecycle()
    val isCompactNavigation = libraryNavigationMode == LibraryNavigationMode.COMPACT_PILL
    val tabCount = tabTitles.size.coerceAtLeast(1)
    val normalizedLastTabIndex = positiveMod(lastTabIndex, tabCount)
    val compactInitialPage = remember(tabCount, normalizedLastTabIndex) {
        infinitePagerInitialPage(tabCount, normalizedLastTabIndex)
    }
    val pagerState = if (isCompactNavigation) {
        rememberPagerState(initialPage = compactInitialPage) { Int.MAX_VALUE }
    } else {
        rememberPagerState(initialPage = normalizedLastTabIndex) { tabCount }
    }
    val currentTabIndex by remember(pagerState, tabTitles, isCompactNavigation) {
        derivedStateOf {
            resolveTabIndex(
                page = pagerState.currentPage,
                tabCount = tabTitles.size,
                compactMode = isCompactNavigation
            )
        }
    }
    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()
    val canNavigateBackInFolders by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState -> uiState.currentFolder != null && uiState.folderBackGestureNavigationEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val hasActiveAiProviderApiKey by playerViewModel.hasActiveAiProviderApiKey.collectAsStateWithLifecycle()
    val isGeneratingAiPlaylist by playerViewModel.isGeneratingAiPlaylist.collectAsStateWithLifecycle()
    val aiError by playerViewModel.aiError.collectAsStateWithLifecycle()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistCreationTypeDialog by remember { mutableStateOf(false) }
    var showCreateAiPlaylistDialog by remember { mutableStateOf(false) }
    var aiGenerationRequestedFromDialog by remember { mutableStateOf(false) }

    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportIsCsv by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Observe M3U files opened/shared from other apps via ACTION_VIEW or ACTION_SEND
    val activity = context as? com.unshoo.pixelmusic.MainActivity
    LaunchedEffect(activity) {
        activity?.pendingM3uImportUri?.collect { uri ->
            if (uri != null) {
                pendingImportUri = uri
                pendingImportIsCsv = false
                showImportDialog = true
                activity.pendingM3uImportUri.value = null // consume
            }
        }
    }

    // Use "*/*" so the picker shows M3U/M3U8 files regardless of their MIME type
    // (many apps tag them as text/plain or application/octet-stream)
    val m3uImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            pendingImportIsCsv = false
            showImportDialog = true
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            pendingImportIsCsv = true
            showImportDialog = true
        }
    }
    var showImportSheet by remember { mutableStateOf(false) }

    var showReorderTabsSheet by remember { mutableStateOf(false) }
    var showTabSwitcherSheet by remember { mutableStateOf(false) }

    // Multi-selection state
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var selectedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by remember { mutableStateOf(false) }

    var songsShowLocateButton by remember { mutableStateOf(false) }
    var likedShowLocateButton by remember { mutableStateOf(false) }
    var foldersShowLocateButton by remember { mutableStateOf(false) }
    var songsLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var likedLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var foldersLocateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingFoldersLocatePath by remember { mutableStateOf<String?>(null) }

    // Multi-selection callbacks
    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song) 
        }
    }

    val onSongSelectionToggle: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }

    // PERF: use rememberUpdatedState for selectedAlbums so the lambda captures
    // a stable reference that always reads the latest value, avoiding cascading
    // re-allocations of toggleAlbumSelection → onAlbumLongPress → onAlbumSelectionToggle
    // on every selection change. This was causing 4 new lambda objects per tap.
    val currentSelectedAlbums = rememberUpdatedState(selectedAlbums)

    val toggleAlbumSelection: (Album) -> Unit = remember(playerViewModel, context) {
        { album ->
            val albums = currentSelectedAlbums.value
            val existingIndex = albums.indexOfFirst { it.id == album.id }
            if (existingIndex >= 0) {
                selectedAlbums = albums.toMutableList().also { it.removeAt(existingIndex) }
            } else if (albums.size >= MAX_ALBUM_MULTI_SELECTION) {
                playerViewModel.sendToast(context.getString(R.string.presentation_batch_d_max_albums_selection, MAX_ALBUM_MULTI_SELECTION))
            } else {
                selectedAlbums = albums + album
            }
        }
    }

    val onAlbumLongPress: (Album) -> Unit = remember(toggleAlbumSelection, haptic) {
        { album -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            toggleAlbumSelection(album) 
        }
    }

    val onAlbumSelectionToggle: (Album) -> Unit = remember(toggleAlbumSelection) {
        { album -> toggleAlbumSelection(album) }
    }

    val getAlbumSelectionIndex: (Long) -> Int? = remember {
        { albumId ->
            val albums = currentSelectedAlbums.value
            val index = albums.indexOfFirst { it.id == albumId }
            if (index >= 0) index + 1 else null
        }
    }

    // Playlist multi-selection state and callbacks
    val playlistMultiSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistMultiSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistMultiSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistMultiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }
    var showMergePlaylistDialog by remember { mutableStateOf(false) }
    var pendingMergePlaylistIds by remember { mutableStateOf(emptyList<String>()) }

    val onPlaylistLongPress: (com.unshoo.pixelmusic.data.model.Playlist) -> Unit = remember(haptic) {
        { playlist ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            selectedPlaylistForOptions = playlist
            showSinglePlaylistOptionsSheet = true
        }
    }

    val onPlaylistSelectionToggle: (com.unshoo.pixelmusic.data.model.Playlist) -> Unit = remember(playlistMultiSelectionState) {
        { playlist -> playlistMultiSelectionState.toggleSelection(playlist) }
    }

    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    // Pull-to-refresh uses incremental sync for speed. The spinner gives manual
    // refreshes a short tactile confirmation, then longer work hands off to the
    // inline sync indicator.
    var isMinDelayActive by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }



    LaunchedEffect(isFetchingChanges) {
        if (!isFetchingChanges && !isMinDelayActive) {
            isRefreshing = false
        }
    }

    // Minimum-visible gate for the inline sync indicator. It covers automatic
    // startup syncs and manual refreshes once the pull spinner has handed off.
    // Fast no-op phases finish in tens of milliseconds, so the small linear bar is
    // held briefly to avoid single-frame flicker.
    var inlineSyncVisible by remember { mutableStateOf(false) }
    var inlineSyncShownAt by remember { mutableStateOf<Long?>(null) }
    // Bug 11 fix: split into two separate LaunchedEffects so that changes to
    // isRefreshing no longer race against the isSyncing hide path. Previously,
    // when isSyncing was still true but isRefreshing flipped false the combined
    // key restarted the effect and immediately hid the indicator.
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            if (!inlineSyncVisible) {
                inlineSyncShownAt = System.currentTimeMillis()
                inlineSyncVisible = true
            }
        } else if (inlineSyncVisible) {
            val shownAt = inlineSyncShownAt
            val elapsed = if (shownAt != null) {
                System.currentTimeMillis() - shownAt
            } else {
                INLINE_SYNC_MIN_VISIBLE_MS
            }
            val remaining = INLINE_SYNC_MIN_VISIBLE_MS - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            // If sync flipped back to visible during the delay this LaunchedEffect
            // is cancelled and re-runs, so reaching this line means we should hide.
            inlineSyncVisible = false
            inlineSyncShownAt = null
        }
    }
    // When a pull-to-refresh gesture starts, dismiss the inline indicator immediately
    // (the spinner takes over). Kept as its own effect to avoid restarting the
    // isSyncing effect above on every pull gesture.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            inlineSyncVisible = false
            inlineSyncShownAt = null
        }
    }

    // P1-1: derivedStateOf ensures BackHandler only recomposes when the boolean RESULT changes,
    // not every time any individual selection state emits.
    val hasSelectionInCurrentTab by remember {
        derivedStateOf {
            when (currentTabId) {
                LibraryTabId.PLAYLISTS -> isPlaylistSelectionMode
                LibraryTabId.ALBUMS -> isAlbumSelectionMode
                LibraryTabId.SONGS,
                LibraryTabId.LIKED,
                LibraryTabId.FOLDERS -> isSelectionMode
                LibraryTabId.ARTISTS -> false
            }
        }
    }
    val canHandleFolderBack by remember {
        derivedStateOf {
            currentTabId == LibraryTabId.FOLDERS &&
                    canNavigateBackInFolders &&
                    !isSortSheetVisible
        }
    }

    com.unshoo.pixelmusic.presentation.components.PlayerSheetAwareBackHandler(
        coordinator = playerViewModel.playerSheetBackCoordinator,
        enabled = true
    ) {
        when {
            hasSelectionInCurrentTab -> {
                when (currentTabId) {
                    LibraryTabId.PLAYLISTS -> {
                        playlistMultiSelectionState.clearSelection()
                        showPlaylistMultiSelectionSheet = false
                        showMergePlaylistDialog = false
                        pendingMergePlaylistIds = emptyList()
                    }

                    LibraryTabId.ALBUMS -> {
                        selectedAlbums = emptyList()
                        showAlbumMultiSelectionSheet = false
                    }

                    LibraryTabId.SONGS,
                    LibraryTabId.LIKED,
                    LibraryTabId.FOLDERS -> {
                        multiSelectionState.clearSelection()
                        showMultiSelectionSheet = false
                    }

                    LibraryTabId.ARTISTS -> Unit
                }
            }

            canHandleFolderBack -> {
                playerViewModel.navigateBackFolder()
            }

            else -> {
                navController.navigateToTopLevelSafely(Screen.Home.route)
            }
        }
    }

    // Feedback for Playlist Creation
    LaunchedEffect(Unit) {
        playlistViewModel.playlistCreationEvent.collect { success ->
            if (success) {
                showCreatePlaylistDialog = false
                Toast.makeText(context, context.getString(R.string.toast_playlist_created), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(
        showCreateAiPlaylistDialog,
        aiGenerationRequestedFromDialog,
        isGeneratingAiPlaylist,
        aiError
    ) {
        if (!showCreateAiPlaylistDialog || !aiGenerationRequestedFromDialog || isGeneratingAiPlaylist) {
            return@LaunchedEffect
        }

        if (aiError == null) {
            showCreateAiPlaylistDialog = false
            playerViewModel.clearAiPlaylistError()
        }
        aiGenerationRequestedFromDialog = false
    }

    LaunchedEffect(hasActiveAiProviderApiKey, showCreateAiPlaylistDialog) {
        if (!hasActiveAiProviderApiKey && showCreateAiPlaylistDialog) {
            showCreateAiPlaylistDialog = false
            aiGenerationRequestedFromDialog = false
            playerViewModel.clearAiPlaylistError()
        }
    }
    // La lÃ³gica de carga diferida (lazy loading) se mantiene.
    LaunchedEffect(Unit) {
        Trace.beginSection("LibraryScreen.InitialTabLoad")
        playerViewModel.onLibraryTabSelected(normalizedLastTabIndex)
        Trace.endSection()
    }

    LaunchedEffect(currentTabIndex) {
        Trace.beginSection("LibraryScreen.PageChangeTabLoad")
        playerViewModel.onLibraryTabSelected(currentTabIndex)
        Trace.endSection()

        // Clear selection when switching tabs
        multiSelectionState.clearSelection()
        playlistMultiSelectionState.clearSelection()
        selectedAlbums = emptyList()
        showMultiSelectionSheet = false
        showPlaylistMultiSelectionSheet = false
        showAlbumMultiSelectionSheet = false
    }

    val fabState by remember { derivedStateOf { currentTabIndex } } // UI sin cambios
    val transition = updateTransition(
        targetState = fabState,
        label = "Action Button Icon Transition"
    ) // UI sin cambios

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val navBarHeightOffsetRaw by playerViewModel.navBarHeightOffset.collectAsStateWithLifecycle()
    val navBarHeightOffset = navBarHeightOffsetRaw.dp
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(navBarStyle, systemNavBarInset, navBarCompactMode, navBarHeightOffset)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode, navBarHeightOffset)

    val dm = LocalPixelMusicDarkTheme.current

    val iconRotation by transition.animateFloat(
        label = "Action Button Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (tabTitles.getOrNull(page)?.toLibraryTabIdOrNull()) {
            LibraryTabId.PLAYLISTS -> 0f // Playlist icon (PlaylistAdd) usually doesn't rotate
            else -> 360f // Shuffle icon animates
        }
    }

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val currentTab = tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull() ?: currentTabId
    val currentTabTitle = currentTab.getDisplayTitle()

    val onRefresh: () -> Unit = remember(scope, playerViewModel, currentTab) {
        {
            // Bug 12 fix: capture the next generation value as a local val *before*
            // writing it to state. The previous code wrote refreshGeneration + 1 to
            // state and then compared against a captured copy that was still the
            // old value (0) because the lambda closed over the snapshot variable,
            // causing the spinner to never dismiss on subsequent pulls.
            val nextGeneration = refreshGeneration + 1
            refreshGeneration = nextGeneration
            isMinDelayActive = true
            isRefreshing = true
            playerViewModel.refreshLibraryTab(currentTab)
            scope.launch {
                kotlinx.coroutines.delay(PULL_REFRESH_MIN_VISIBLE_MS)
                if (nextGeneration != refreshGeneration) return@launch
                isMinDelayActive = false
                isRefreshing = false
            }
        }
    }

    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    Scaffold(
        modifier = Modifier.background(brush = gradientBrush),
        topBar = {
            Column(
                modifier = Modifier.background(headerContainerColor)
            ) {
                TopAppBar(
                    title = {
                        if (isCompactNavigation) {
                            LibraryNavigationPill(
                                modifier = Modifier,
                                title = currentTabTitle,
                                isExpanded = showTabSwitcherSheet,
                                showIcon = true,
                                iconRes = currentTab.iconRes(),
                                pageIndex = pagerState.currentPage,
                                compressForWatchTransfer = false,
                                onClick = {
                                    showTabSwitcherSheet = true
                                },
                                onArrowClick = { showTabSwitcherSheet = true }
                            )
                        } else {
                            val libraryTitle = stringResource(R.string.presentation_batch_d_library_title)
                            val titleFontSize = remember(libraryTitle) {
                                when {
                                    libraryTitle.length > 9 -> 28.sp
                                    libraryTitle.length > 7 -> 32.sp
                                    else -> 38.sp
                                }
                            }
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = libraryTitle,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = titleFontSize,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {

                        FilledIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            onClick = {
                                navController.navigateSafely(Screen.SmartMix.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Smart Mix Studio"
                            )
                        }
                        FilledIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            onClick = {
                                navController.navigateSafely(Screen.CloudDownloads.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = stringResource(R.string.cloud_downloads_title)
                            )
                        }
                        FilledIconButton(
                            modifier = Modifier.padding(end = 14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            onClick = {
                                navController.navigateSafely(Screen.Settings.route)
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_settings_24),
                                contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
                if (!isCompactNavigation) {
                    val showTabIndicator = false
                    PrimaryScrollableTabRow(
                        selectedTabIndex = currentTabIndex,
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        indicator = {
                            if (showTabIndicator) {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex = currentTabIndex),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, rawId ->
                            val tabId = rawId.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
                            TabAnimation(
                                index = index,
                                title = tabId.storageKey,
                                selectedIndex = currentTabIndex,
                                onClick = {
                                    scope.launch {
                                        val targetPage = targetPageForTabIndex(
                                            currentPage = pagerState.currentPage,
                                            targetTabIndex = index,
                                            tabCount = tabTitles.size,
                                            compactMode = isCompactNavigation
                                        )
                                        if (pagerState.currentPage != targetPage) {
                                            val diff = targetPage - pagerState.currentPage
                                            if (kotlin.math.abs(diff) > 1) {
                                                pagerState.scrollToPage(if (diff > 0) targetPage - 1 else targetPage + 1)
                                            }
                                            pagerState.animateScrollToPage(
                                                page = targetPage,
                                                animationSpec = tween(
                                                    durationMillis = 320,
                                                    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                                                )
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(tabId.titleRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (currentTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        TabAnimation(
                            index = -1,
                            title = stringResource(R.string.presentation_batch_d_edit_library_tabs_cd),
                            selectedIndex = currentTabIndex,
                            onClick = { showReorderTabsSheet = true }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.presentation_batch_d_reorder_tabs_cd),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else {
                    CompactLibraryPagerIndicator(
                        currentIndex = currentTabIndex,
                        pageCount = tabTitles.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                    )
                }
            }
        }
    ) { innerScaffoldPadding ->
        val playerUiState by remember(playerViewModel) {
            playerViewModel.playerUiState
                .map { uiState -> uiState.toLibraryScreenProjection() }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = LibraryScreenPlayerProjection())
        val isLibraryContentEmpty by remember(playerViewModel) {
            combine(
                playerViewModel.songCountFlow,
                playerViewModel.albumsFlow,
                playerViewModel.artistsFlow
            ) { songCount, albums, artists ->
                songCount == 0 && albums.isEmpty() && artists.isEmpty()
            }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = false)

        Box(
            modifier = Modifier
                .padding(top = innerScaffoldPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .background(color = headerContainerColor)
                    .fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp, vertical = 0.dp), // Removed horizontal padding for more space
                    color = MaterialTheme.colorScheme.surface,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 34.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 0.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 0.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusTR = 34.dp,
                        smoothnessAsPercentTL = 60
                    )
                ) {
                    Column(Modifier.fillMaxSize()) {
                        LaunchedEffect(currentTabId) {
                            playerViewModel.setCurrentLibraryTabId(currentTabId)
                        }
                        val sanitizedSortOptions = remember(currentTabId) {
                            when (currentTabId) {
                                LibraryTabId.SONGS -> SortOption.SONGS
                                LibraryTabId.ALBUMS -> SortOption.ALBUMS
                                LibraryTabId.ARTISTS -> SortOption.ARTISTS
                                LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                                LibraryTabId.FOLDERS -> SortOption.FOLDERS
                                LibraryTabId.LIKED -> SortOption.LIKED
                            }
                        }

                        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
                        val visiblePlaylists = remember(
                            playlistUiState.playlists,
                            playlistUiState.showTelegramCloudPlaylists,
                            playlistUiState.telegramTopicDisplayMode
                        ) {
                            val mode = playlistUiState.telegramTopicDisplayMode
                            val allPlaylists = playlistUiState.playlists

                            // When Telegram cloud is hidden, remove all Telegram playlists
                            if (!playlistUiState.showTelegramCloudPlaylists) {
                                return@remember allPlaylists.filterNot {
                                    it.source == "TELEGRAM" || it.source == "TELEGRAM_TOPIC"
                                }
                            }

                            allPlaylists.filter { playlist ->
                                when (playlist.source) {
                                    "TELEGRAM_TOPIC" -> when (mode) {
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY ->
                                            false
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY,
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS ->
                                            playlist.songIds.isNotEmpty()
                                    }
                                    "TELEGRAM" -> when (mode) {
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY ->
                                            true
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY -> {
                                            // Hide combined playlist only for forum channels
                                            // (those that have at least one topic playlist)
                                            val chatId = playlist.id
                                                .removePrefix("telegram_channel:")
                                                .toLongOrNull()
                                            if (chatId != null) {
                                                allPlaylists.none { p ->
                                                    p.source == "TELEGRAM_TOPIC" &&
                                                            p.id.startsWith("telegram_topic:${chatId}_")
                                                }
                                            } else true
                                        }
                                        com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS ->
                                            true
                                    }
                                    else -> true
                                }
                            }
                        }
                        val isLibraryLoading by libraryViewModel.isLoadingLibrary.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) {
                            com.unshoo.pixelmusic.utils.AppReadinessSignal.markInitialFetchFinished()
                        }
                        val hasCurrentSong by remember(playerViewModel) {
                            playerViewModel.stablePlayerState
                                .map { state -> state.currentSong != null && state.currentSong != Song.emptySong() }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val isShuffleEnabled by remember(playerViewModel) {
                            playerViewModel.stablePlayerState
                                .map { it.isShuffleEnabled }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)

                        LaunchedEffect(
                            playlistUiState.showTelegramCloudPlaylists,
                            selectedPlaylists
                        ) {
                            if (playlistUiState.showTelegramCloudPlaylists) return@LaunchedEffect

                            selectedPlaylists
                                .filter { it.source == "TELEGRAM" || it.source == "TELEGRAM_TOPIC" }
                                .forEach { playlist ->
                                    playlistMultiSelectionState.removeFromSelection(playlist.id)
                                }
                        }

                        val currentSelectedSortOption: SortOption? = when (currentTab) {
                            LibraryTabId.SONGS -> playerUiState.currentSongSortOption
                            LibraryTabId.ALBUMS -> playerUiState.currentAlbumSortOption
                            LibraryTabId.ARTISTS -> playerUiState.currentArtistSortOption
                            LibraryTabId.PLAYLISTS -> playlistUiState.currentPlaylistSortOption
                            LibraryTabId.LIKED -> playerUiState.currentFavoriteSortOption
                            LibraryTabId.FOLDERS -> playerUiState.currentFolderSortOption
                        }

                        val showLocateButton = when (currentTab) {
                            LibraryTabId.SONGS -> songsShowLocateButton
                            LibraryTabId.LIKED -> likedShowLocateButton
                            LibraryTabId.FOLDERS -> foldersShowLocateButton
                            else -> false
                        }
                        val locateAction = when (currentTab) {
                            LibraryTabId.SONGS -> songsLocateAction
                            LibraryTabId.LIKED -> likedLocateAction
                            LibraryTabId.FOLDERS -> foldersLocateAction
                            else -> null
                        }

                        val onSortOptionChanged: (SortOption) -> Unit = remember(playerViewModel, playlistViewModel, currentTabId) {
                            { option ->
                                when (currentTabId) {
                                    LibraryTabId.SONGS -> playerViewModel.sortSongs(option)
                                    LibraryTabId.ALBUMS -> playerViewModel.sortAlbums(option)
                                    LibraryTabId.ARTISTS -> playerViewModel.sortArtists(option)
                                    LibraryTabId.PLAYLISTS -> playlistViewModel.sortPlaylists(option)
                                    LibraryTabId.LIKED -> playerViewModel.sortFavoriteSongs(option)
                                    LibraryTabId.FOLDERS -> playerViewModel.sortFolders(option)
                                }
                            }
                        }

                        // Switch between normal action row and selection action row
                        AnimatedContent(
                            targetState = isSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode,
                            label = "ActionRowModeSwitch",
                            transitionSpec = {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            },
                            modifier = Modifier
                                .padding(
                                    top = 6.dp,
                                    start = 10.dp,
                                    end = 10.dp
                                )
                                .heightIn(min = 56.dp)
                        ) { inSelectionMode ->
                            if (inSelectionMode) {
                                // Playlist selection row
                                if (currentTab == LibraryTabId.PLAYLISTS && isPlaylistSelectionMode) {
                                    SelectionActionRow(
                                        selectedCount = selectedPlaylists.size,
                                        onSelectAll = {
                                            playerViewModel.playlistSelectionStateHolder.selectAll(visiblePlaylists)
                                        },
                                        onDeselect = { playerViewModel.playlistSelectionStateHolder.clearSelection() },
                                        onOptionsClick = { showPlaylistMultiSelectionSheet = true }
                                    )
                                } else if (currentTab == LibraryTabId.ALBUMS && isAlbumSelectionMode) {
                                    SelectionActionRow(
                                        selectedCount = selectedAlbums.size,
                                        onSelectAll = {
                                            val remaining = MAX_ALBUM_MULTI_SELECTION - selectedAlbums.size
                                            if (remaining <= 0) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.presentation_batch_d_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                val albumsToAppend = playerViewModel.albumsFlow.value
                                                    .filterNot { selectedAlbumIds.contains(it.id) }
                                                    .take(remaining)
                                                if (albumsToAppend.isNotEmpty()) {
                                                    selectedAlbums = selectedAlbums + albumsToAppend
                                                }
                                            }
                                        },
                                        onDeselect = { selectedAlbums = emptyList() },
                                        onOptionsClick = { showAlbumMultiSelectionSheet = true }
                                    )
                                } else {
                                    // Song selection row
                                    SelectionActionRow(
                                        selectedCount = selectedSongs.size,
                                        onSelectAll = {
                                            when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                                LibraryTabId.LIKED -> {
                                                    scope.launch {
                                                        val songsToSelect =
                                                            playerViewModel.getFavoriteSongsForCurrentSelection()
                                                        multiSelectionState.selectAll(songsToSelect)
                                                    }
                                                }
                                                LibraryTabId.FOLDERS -> {
                                                    val songsToSelect =
                                                        playerViewModel.playerUiState.value.currentFolder?.songs ?: emptyList()
                                                    multiSelectionState.selectAll(songsToSelect)
                                                }
                                                LibraryTabId.SONGS -> {
                                                    scope.launch {
                                                        val songsToSelect =
                                                            playerViewModel.getSongsForCurrentLibrarySelection()
                                                        multiSelectionState.selectAll(songsToSelect)
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        },
                                        onDeselect = { multiSelectionState.clearSelection() },
                                        onOptionsClick = { showMultiSelectionSheet = true }
                                    )
                                }
                            } else {
                                LibraryActionRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 4.dp),
                                    onMainActionClick = {
                                        when (tabTitles.getOrNull(currentTabIndex)?.toLibraryTabIdOrNull()) {
                                            LibraryTabId.PLAYLISTS -> showPlaylistCreationTypeDialog = true
                                            LibraryTabId.LIKED -> playerViewModel.shuffleFavoriteSongs()
                                            LibraryTabId.ALBUMS -> playerViewModel.shuffleRandomAlbum()
                                            LibraryTabId.ARTISTS -> playerViewModel.shuffleRandomArtist()
                                            else -> playerViewModel.shuffleAllSongs()
                                        }
                                    },
                                    iconRotation = iconRotation,
                                    showSortButton = sanitizedSortOptions.isNotEmpty(),
                                    showLocateButton = showLocateButton,
                                    onSortClick = { playerViewModel.showSortingSheet() },
                                    onLocateClick = { locateAction?.invoke() },
                                    isPlaylistTab = currentTab == LibraryTabId.PLAYLISTS,
                                    isFoldersTab = currentTab == LibraryTabId.FOLDERS && (!playerUiState.isFoldersPlaylistView || playerUiState.currentFolder != null),
                                    onImportM3uClick = { showImportSheet = true },
                                    currentFolder = playerUiState.currentFolder,
                                    folderRootPath = playerUiState.folderSourceRootPath.ifBlank {
                                        Environment.getExternalStorageDirectory().path
                                    },
                                    folderRootLabel = playerUiState.folderSource.displayName,
                                    onFolderClick = { playerViewModel.navigateToFolder(it) },
                                    onNavigateBack = { playerViewModel.navigateBackFolder() },
                                    isShuffleEnabled = isShuffleEnabled,
                                    showStorageFilterButton = currentTab == LibraryTabId.SONGS ||
                                            currentTab == LibraryTabId.ALBUMS ||
                                            currentTab == LibraryTabId.ARTISTS ||
                                            currentTab == LibraryTabId.LIKED ||
                                            (ENABLE_FOLDERS_STORAGE_FILTER && currentTab == LibraryTabId.FOLDERS),
                                    currentStorageFilter = playerUiState.currentStorageFilter,
                                    onStorageFilterClick = { playerViewModel.toggleStorageFilter() }
                                )
                            }
                        }

                        // Slim inline sync indicator. Automatic startup syncs use this
                        // instead of pulling the list down, and manual refreshes hand off
                        // to it when the worker takes longer than the pull gesture window.
                        LibraryInlineSyncIndicator(
                            visible = inlineSyncVisible && !isLibraryContentEmpty,
                            syncManager = syncManager
                        )

                        if (isSortSheetVisible && sanitizedSortOptions.isNotEmpty()) {
                            val currentSelectionKey = currentSelectedSortOption?.storageKey
                            val selectedOptionForSheet = sanitizedSortOptions.firstOrNull { option ->
                                option.storageKey == currentSelectionKey
                            }
                                ?: sanitizedSortOptions.firstOrNull { option ->
                                    option.storageKey == currentTabId.defaultSort.storageKey
                                }
                                ?: sanitizedSortOptions.first()


                            val isAlbumTab = currentTabId == LibraryTabId.ALBUMS
                            val isFoldersTab = currentTabId == LibraryTabId.FOLDERS
                            val isPlaylistsTab = currentTabId == LibraryTabId.PLAYLISTS

                            LibrarySortBottomSheet(
                                title = stringResource(R.string.presentation_batch_d_sort_by),
                                options = sanitizedSortOptions,
                                selectedOption = selectedOptionForSheet,
                                onDismiss = { playerViewModel.hideSortingSheet() },
                                onOptionSelected = { option ->
                                    onSortOptionChanged(option)
                                    playerViewModel.hideSortingSheet()
                                },
                                onDirectionToggle = { option ->
                                    onSortOptionChanged(option)
                                },
                                showViewToggle = isFoldersTab || isPlaylistsTab,
                                viewSectionTitle = if (isPlaylistsTab) {
                                    stringResource(R.string.presentation_batch_d_view_section_cloud)
                                } else {
                                    stringResource(R.string.presentation_batch_d_view_section_view)
                                },
                                viewToggleLabel = if (isPlaylistsTab) {
                                    stringResource(R.string.presentation_batch_d_telegram_cloud_channels)
                                } else {
                                    stringResource(R.string.presentation_batch_d_playlist_view)
                                },
                                viewToggleChecked = if (isPlaylistsTab) {
                                    playlistUiState.showTelegramCloudPlaylists
                                } else {
                                    playerUiState.isFoldersPlaylistView
                                },
                                onViewToggleChange = { isChecked ->
                                    if (isPlaylistsTab) {
                                        playlistViewModel.setShowTelegramCloudPlaylists(isChecked)
                                    } else {
                                        playerViewModel.setFoldersPlaylistView(isChecked)
                                    }
                                },
                                viewToggleContent = if (isAlbumTab) {
                                    {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isList = playerUiState.isAlbumsListView
                                            val primaryColor = MaterialTheme.colorScheme.tertiaryContainer
                                            val onPrimaryColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                                            val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

                                            // Grid Item
                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = !isList,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setAlbumsListView(false) },
                                                text = stringResource(R.string.presentation_batch_d_view_grid),
                                                imageVector = Icons.Rounded.ViewModule
                                            )

                                            // List Item
                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = isList,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setAlbumsListView(true) },
                                                text = stringResource(R.string.presentation_batch_d_view_list),
                                                imageVector = Icons.AutoMirrored.Rounded.ViewList
                                            )
                                        }
                                    }
                                } else null,
                                sourceToggleContent = if (isFoldersTab && ENABLE_FOLDERS_SOURCE_TOGGLE) {
                                    {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isSdAvailable = playerUiState.isSdCardAvailable
                                            ToggleSegmentButton(
                                                modifier = Modifier.weight(1f),
                                                active = playerUiState.folderSource == FolderSource.INTERNAL,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = { playerViewModel.setFoldersSource(FolderSource.INTERNAL) },
                                                text = stringResource(R.string.presentation_batch_d_storage_internal)
                                            )
                                            ToggleSegmentButton(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .alpha(if (isSdAvailable) 1f else 0.5f),
                                                active = playerUiState.folderSource == FolderSource.SD_CARD,
                                                activeColor = MaterialTheme.colorScheme.primary,
                                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                activeCornerRadius = 32.dp,
                                                onClick = {
                                                    if (isSdAvailable) {
                                                        playerViewModel.setFoldersSource(FolderSource.SD_CARD)
                                                    }
                                                },
                                                text = stringResource(R.string.presentation_batch_d_storage_sd_card)
                                            )
                                        }
                                        if (!playerUiState.isSdCardAvailable) {
                                            Text(
                                                text = stringResource(R.string.presentation_batch_d_sd_card_unavailable),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                                            )
                                        }
                                    }
                                } else null,
                                extraContent = {
                                    if (isPlaylistsTab && playlistUiState.showTelegramCloudPlaylists) {
                                        Text(
                                            text = stringResource(R.string.presentation_batch_d_topics_display),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontFamily = com.unshoo.pixelmusic.ui.theme.GoogleSansRounded,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY to stringResource(R.string.presentation_batch_d_topic_mode_channels),
                                                com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY to stringResource(R.string.presentation_batch_d_topic_mode_topics),
                                                com.unshoo.pixelmusic.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS to stringResource(R.string.presentation_batch_d_topic_mode_both)
                                            ).forEach { (mode, label) ->
                                                ToggleSegmentButton(
                                                    modifier = Modifier.weight(1f),
                                                    active = playlistUiState.telegramTopicDisplayMode == mode,
                                                    activeColor = MaterialTheme.colorScheme.primary,
                                                    inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    activeCornerRadius = 32.dp,
                                                    onClick = { playlistViewModel.setTelegramTopicDisplayMode(mode) },
                                                    text = label
                                                )
                                            }
                                        }
                                    }
                                    if (!isFoldersTab) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.presentation_batch_d_cloud_sources_heading),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontFamily = com.unshoo.pixelmusic.ui.theme.GoogleSansRounded,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                                        )
                                        com.unshoo.pixelmusic.presentation.components.LibrarySheetToggleCard(
                                            label = stringResource(R.string.presentation_batch_d_cloud_only),
                                            checked = playerUiState.hideLocalMedia,
                                            boxBackgroundColor = if (playerUiState.hideLocalMedia)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                            boxCornerRadius = if (playerUiState.hideLocalMedia) 18.dp else 50.dp,
                                            onCheckedChange = { playerViewModel.setHideLocalMedia(it) }
                                        )
                                    }
                                }
                            )
                        }

                        // Box wrapper to allow floating SelectionCountPill overlay
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                pageSpacing = 0.dp,
                                beyondViewportPageCount = 1, // Pre-compose adjacent tabs for 120 FPS high-performance swipe transitions
                                key = { it }
                            ) { page ->
                                val tabIndex = resolveTabIndex(
                                    page = page,
                                    tabCount = tabTitles.size,
                                    compactMode = isCompactNavigation
                                )
                                // Rendered immediately — the tab composables show in-place
                                // skeletons while loading, so page switches never wait on a
                                // spinner or latch.
                                when (tabTitles.getOrNull(tabIndex)?.toLibraryTabIdOrNull()) {
                                    LibraryTabId.SONGS -> {
                                        val allSongsLazyPagingItems = libraryViewModel.songsPagingFlow.collectAsLazyPagingItems()
                                        LibrarySongsTab(
                                            songs = allSongsLazyPagingItems,
                                            isLoading = isLibraryLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = {
                                                onRefresh()
                                                allSongsLazyPagingItems.refresh()
                                            },
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            onLocateCurrentSongVisibilityChanged = { songsShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { songsLocateAction = it },
                                            sortOption = playerUiState.currentSongSortOption,
                                            storageFilter = if (playerUiState.hideLocalMedia) StorageFilter.ONLINE else playerUiState.currentStorageFilter,
                                            hasCurrentSong = hasCurrentSong
                                        )
                                    }
                                    LibraryTabId.ALBUMS -> {
                                        val albumsLazyPagingItems = libraryViewModel.albumsPagingFlow.collectAsLazyPagingItems()
                                        val isLoading = playerUiState.isLoadingLibraryCategories && albumsLazyPagingItems.itemCount == 0

                                        val stableOnAlbumClick: (Long) -> Unit = remember(navController) {
                                            { albumId: Long ->
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.AlbumDetail.createRoute(albumId),
                                                    patternToPop = Screen.AlbumDetail.route
                                                )
                                            }
                                        }
                                        LibraryAlbumsTab(
                                            albums = albumsLazyPagingItems,
                                            isLoading = isLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            isListView = playerUiState.isAlbumsListView,
                                            currentAlbumSortOption = playerUiState.currentAlbumSortOption,
                                            onAlbumClick = stableOnAlbumClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isAlbumSelectionMode,
                                            selectedAlbumIds = selectedAlbumIds,
                                            onAlbumLongPress = onAlbumLongPress,
                                            onAlbumSelectionToggle = onAlbumSelectionToggle,
                                            getSelectionIndex = getAlbumSelectionIndex,
                                            storageFilter = if (playerUiState.hideLocalMedia) StorageFilter.ONLINE else playerUiState.currentStorageFilter
                                        )
                                    }

                                    LibraryTabId.ARTISTS -> {
                                        val artistsLazyPagingItems = libraryViewModel.artistsPagingFlow.collectAsLazyPagingItems()
                                        val isLoading = playerUiState.isLoadingLibraryCategories && artistsLazyPagingItems.itemCount == 0

                                        val stableOnArtistClick: (String) -> Unit = remember(navController) {
                                            { artistIdStr: String ->
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.ArtistDetail.createRoute(artistIdStr),
                                                    patternToPop = Screen.ArtistDetail.route
                                                )
                                            }
                                        }

                                        LibraryArtistsTab(
                                            artists = artistsLazyPagingItems,
                                            isLoading = isLoading,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            currentArtistSortOption = playerUiState.currentArtistSortOption,
                                            onArtistClick = stableOnArtistClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            storageFilter = if (playerUiState.hideLocalMedia) StorageFilter.ONLINE else playerUiState.currentStorageFilter
                                        )
                                    }

                                    LibraryTabId.PLAYLISTS -> {
                                        LibraryPlaylistsTab(
                                            playlistUiState = playlistUiState,
                                            filteredPlaylists = visiblePlaylists,
                                            navController = navController,
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            // Playlist multi-selection
                                            isSelectionMode = isPlaylistSelectionMode,
                                            selectedPlaylistIds = selectedPlaylistIds,
                                            onPlaylistLongPress = onPlaylistLongPress,
                                            onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                                            onPlaylistOptionsClick = { showPlaylistMultiSelectionSheet = true }
                                        )
                                    }

                                    LibraryTabId.LIKED -> {
                                        val favoritePagingItems = libraryViewModel.favoritesPagingFlow.collectAsLazyPagingItems()
                                        LibraryFavoritesTab(
                                            favoriteSongs = favoritePagingItems,
                                            isLoading = isLibraryLoading || (playerUiState.isLoadingLibraryCategories && favoritePagingItems.itemCount == 0),
                                            playerViewModel = playerViewModel,
                                            bottomBarHeight = bottomBarHeightDp,
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isRefreshing = isRefreshing,
                                            onRefresh = {
                                                onRefresh()
                                                favoritePagingItems.refresh()
                                            },
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            sortOption = playerUiState.currentFavoriteSortOption,
                                            onLocateCurrentSongVisibilityChanged = { likedShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { likedLocateAction = it },
                                            storageFilter = if (playerUiState.hideLocalMedia) StorageFilter.ONLINE else playerUiState.currentStorageFilter,
                                            hasCurrentSong = hasCurrentSong
                                        )
                                    }

                                    LibraryTabId.FOLDERS -> {
                                        val folders = playerUiState.musicFolders
                                        val currentFolder = playerUiState.currentFolder
                                        val isLoading = playerUiState.isLoadingLibraryCategories
                                        // PERF: FoldersTab only reads currentSong?.id, currentSong?.path,
                                        // and isPlaying. Narrow the collection to avoid recomposing the
                                        // entire tab on every player tick (position, queue change, etc.).
                                        val folderPlayerState by remember(playerViewModel) {
                                            playerViewModel.stablePlayerState
                                                .map { state ->
                                                    FolderPlayerProjection(
                                                        currentSongId = state.currentSong?.id,
                                                        currentSongPath = state.currentSong?.path,
                                                        currentSong = state.currentSong,
                                                        isPlaying = state.isPlaying
                                                    )
                                                }
                                                .distinctUntilChanged()
                                        }.collectAsStateWithLifecycle(initialValue = FolderPlayerProjection())
                                        val defaultFolderName = stringResource(R.string.presentation_batch_d_folder_name_fallback)

                                        LibraryFoldersTab(
                                            folders = folders,
                                            currentFolder = currentFolder,
                                            isLoading = isLoading,
                                            folderArtworkPreference = folderArtworkPreference,
                                            bottomBarHeight = bottomBarHeightDp,
                                            folderPlayerState = folderPlayerState,
                                            onNavigateBack = { playerViewModel.navigateBackFolder() },
                                            onFolderClick = { folderPath -> playerViewModel.navigateToFolder(folderPath) },
                                            onFolderAsPlaylistClick = { folder ->
                                                val encodedPath = Uri.encode(folder.path)
                                                navController.navigateSafelyReplacing(
                                                    route = Screen.PlaylistDetail.createRoute(
                                                        "${PlaylistViewModel.FOLDER_PLAYLIST_PREFIX}$encodedPath"
                                                    ),
                                                    patternToPop = Screen.PlaylistDetail.route
                                                )
                                            },
                                            onPlaySong = { song, queue ->
                                                playerViewModel.showAndPlaySong(
                                                    song,
                                                    queue,
                                                    currentFolder?.name ?: defaultFolderName
                                                )
                                            },
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isPlaylistView = playerUiState.isFoldersPlaylistView,
                                            currentSortOption = playerUiState.currentFolderSortOption,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh,
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                                            onLocateCurrentSongVisibilityChanged = { foldersShowLocateButton = it },
                                            onRegisterLocateCurrentSongAction = { foldersLocateAction = it },
                                            pendingLocatePath = pendingFoldersLocatePath,
                                            onClearPendingLocate = { pendingFoldersLocatePath = null },
                                            onRequestCrossFolderLocate = { folderPath ->
                                                pendingFoldersLocatePath = folderPath
                                                playerViewModel.navigateToFolder(folderPath)
                                            }
                                        )
                                    }

                                    null -> Unit
                                }
                        }

                            // Floating selection count pill overlay
                            val selectionCount = when {
                                currentTabId == LibraryTabId.PLAYLISTS && isPlaylistSelectionMode -> selectedPlaylists.size
                                currentTabId == LibraryTabId.ALBUMS && isAlbumSelectionMode -> selectedAlbums.size
                                else -> selectedSongs.size
                            }
                            SelectionCountPill(
                                selectedCount = selectionCount,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(1f)
                            )
                        }
                    }
                }
                if (playerUiState.isGeneratingAiMetadata) {
                    Surface( // Fondo semitransparente para el indicador
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator(modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.presentation_batch_d_generating_ai_metadata),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else if (
                    isLibraryContentEmpty &&
                    (
                            playerUiState.isSyncingLibrary ||
                                    playerUiState.isLoadingInitialSongs ||
                                    playerUiState.isLoadingLibraryCategories
                            )
                ) {
                    // The full-screen overlay is reserved for first-launch / empty library
                    // states. Once the user has content, in-place indicators (pull-to-refresh
                    // spinner + LibraryInlineSyncIndicator) handle sync feedback so the
                    // list stays visible.
                    LibrarySyncOverlay(syncManager = syncManager)
                }
            }
        }
    }



    PlaylistCreationTypeDialog(
        visible = showPlaylistCreationTypeDialog,
        onDismiss = { showPlaylistCreationTypeDialog = false },
        onManualSelected = {
            showPlaylistCreationTypeDialog = false
            showCreatePlaylistDialog = true
        },
        onAiSelected = {
            if (hasActiveAiProviderApiKey) {
                showPlaylistCreationTypeDialog = false
                playerViewModel.clearAiPlaylistError()
                showCreateAiPlaylistDialog = true
            } else {
                Toast.makeText(context, context.getString(R.string.toast_set_ai_provider_api_key_first), Toast.LENGTH_SHORT).show()
            }
        },
        isAiEnabled = hasActiveAiProviderApiKey,
        onSetupAiClick = {
            navController.navigateSafely(Screen.SettingsCategory.createRoute("ai"))
        }
    )

    CreatePlaylistDialog(
        playerViewModel = playerViewModel,
        visible = showCreatePlaylistDialog,
        onDismiss = { showCreatePlaylistDialog = false },
        onGenerateClick = {
            showCreatePlaylistDialog = false
            if (hasActiveAiProviderApiKey) {
                playerViewModel.clearAiPlaylistError()
                showCreateAiPlaylistDialog = true
            } else {
                Toast.makeText(context, context.getString(R.string.toast_set_gemini_api_key_first), Toast.LENGTH_SHORT).show()
            }
        },
        onCreate = { name, imageUri, color, icon, songIds, cropScale, cropPanX, cropPanY, shapeType, d1, d2, d3, d4, smartRuleKey, privacyStatus ->
            playlistViewModel.createPlaylist(
                name = name,
                coverImageUri = imageUri,
                coverColor = color,
                coverIcon = icon,
                songIds = songIds,
                cropScale = cropScale,
                cropPanX = cropPanX,
                cropPanY = cropPanY,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverShapeType = shapeType,
                coverShapeDetail1 = d1,
                coverShapeDetail2 = d2,
                coverShapeDetail3 = d3,
                coverShapeDetail4 = d4,
                smartRuleKey = smartRuleKey,
                privacyStatus = privacyStatus
            )
        }
    )

    CreateAiPlaylistDialog(
        visible = showCreateAiPlaylistDialog && hasActiveAiProviderApiKey,
        isGenerating = isGeneratingAiPlaylist,
        error = aiError,
        onDismiss = {
            showCreateAiPlaylistDialog = false
            aiGenerationRequestedFromDialog = false
            playerViewModel.clearAiPlaylistError()
        },
        onGenerate = { playlistName, prompt, minLength, maxLength ->
            aiGenerationRequestedFromDialog = true
            playerViewModel.generateAiPlaylist(
                prompt = prompt,
                minLength = minLength,
                maxLength = maxLength,
                saveAsPlaylist = true,
                playlistName = playlistName
            )
        }
    )


    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) { derivedStateOf { currentSong?.let {
            favoriteIds.contains(
                it.id)
        } } }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                playerViewModel = playerViewModel,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_added_to_queue))
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_playing_next))
                },
                onAddToPlayList = {
                    playlistSheetSongs = listOf(currentSong)
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(
                            currentSong.artists.firstOrNull()?.id?.takeIf { it != 0L && it != -1L }?.toString()
                                ?: currentSong.artists.firstOrNull()?.name
                                ?: currentSong.artist
                        ),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {},
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
                removeFromListTrigger = {},
                songInfoViewModel = songInfoBottomSheetViewModel
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = { showPlaylistBottomSheet = false },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    if (showSinglePlaylistOptionsSheet && selectedPlaylistForOptions != null) {
        val playlist = selectedPlaylistForOptions!!
        val isPinned = playlist.isPinned
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showSinglePlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_b_playlist_options_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            showSinglePlaylistOptionsSheet = false
                            playlistViewModel.togglePinPlaylist(playlist.id)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = if (isPinned) "Unpin Playlist" else "Pin Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            showSinglePlaylistOptionsSheet = false
                            scope.launch {
                                val songs = playerViewModel.getSongs(playlist.songIds)
                                playerViewModel.addSongsToQueue(songs)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.cd_add_all_to_queue),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.cd_add_all_to_queue),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!playlist.id.startsWith(PlaylistViewModel.FOLDER_PLAYLIST_PREFIX)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                showSinglePlaylistOptionsSheet = false
                                playlistViewModel.deletePlaylist(playlist.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_delete_24),
                                contentDescription = stringResource(R.string.presentation_batch_b_delete_playlist),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.presentation_batch_b_delete_playlist),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // Multi-Selection Bottom Sheet
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? android.app.Activity

        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                }
            }
        )
    }

    // Album Multi-Selection Option Sheet
    if (showAlbumMultiSelectionSheet && selectedAlbums.isNotEmpty()) {
        AlbumMultiSelectionOptionSheet(
            selectedAlbums = selectedAlbums,
            maxSelection = MAX_ALBUM_MULTI_SELECTION,
            onDismiss = { showAlbumMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedAlbums(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAlbumsAsNext(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedAlbumsToQueue(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            }
        )
    }

    // Playlist Multi-Selection Bottom Sheet
    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = {
                showPlaylistMultiSelectionSheet = false
            },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onMergeAll = {
                pendingMergePlaylistIds = selectedPlaylistIds.toList()
                showMergePlaylistDialog = true
                showPlaylistMultiSelectionSheet = false
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            }
        )
    }

    if (showTabSwitcherSheet) {
        LibraryTabSwitcherSheet(
            tabs = tabTitles,
            currentIndex = currentTabIndex,
            onTabSelected = { index ->
                scope.launch {
                    val targetPage = targetPageForTabIndex(
                        currentPage = pagerState.currentPage,
                        targetTabIndex = index,
                        tabCount = tabTitles.size,
                        compactMode = isCompactNavigation
                    )
                    if (pagerState.currentPage != targetPage) {
                        val diff = targetPage - pagerState.currentPage
                        if (kotlin.math.abs(diff) > 1) {
                            pagerState.scrollToPage(if (diff > 0) targetPage - 1 else targetPage + 1)
                        }
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = tween(
                                durationMillis = 320,
                                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                            )
                        )
                    }
                }
                showTabSwitcherSheet = false
            },
            onEditClick = {
                showTabSwitcherSheet = false
                showReorderTabsSheet = true
            },
            onDismiss = { showTabSwitcherSheet = false }
        )
    }

    if (showReorderTabsSheet) {
        ReorderTabsSheet(
            tabs = tabTitles,
            onReorder = { newOrder ->
                playerViewModel.saveLibraryTabsOrder(newOrder)
            },
            onReset = {
                playerViewModel.resetLibraryTabsOrder()
            },
            onDismiss = { showReorderTabsSheet = false }
        )
    }

    // Merge Playlists Dialog
    if (showMergePlaylistDialog && pendingMergePlaylistIds.isNotEmpty()) {
        var mergePlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showMergePlaylistDialog = false
                pendingMergePlaylistIds = emptyList()
                mergePlaylistName = ""
            },
            title = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.presentation_batch_d_merge_playlists_prompt))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mergePlaylistName,
                        onValueChange = { mergePlaylistName = it },
                        placeholder = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.presentation_batch_d_merge_playlists_body,
                            pendingMergePlaylistIds.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mergePlaylistName.isNotEmpty()) {
                            playlistViewModel.mergePlaylistsIntoOne(
                                pendingMergePlaylistIds,
                                mergePlaylistName
                            )
                            playlistMultiSelectionState.clearSelection()
                            showMergePlaylistDialog = false
                            pendingMergePlaylistIds = emptyList()
                            mergePlaylistName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMergePlaylistDialog = false
                    pendingMergePlaylistIds = emptyList()
                    mergePlaylistName = ""
                }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showImportSheet) {
        ImportPlaylistSheet(
            onDismiss = { showImportSheet = false },
            onImportM3u = {
                showImportSheet = false
                // Accept all file types so M3U/M3U8 tagged as text/plain show up too
                m3uImportLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "application/octet-stream", "*/*"))
            },
            onImportCsv = {
                showImportSheet = false
                csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
            },
            onImportSpotify = { url, onDone ->
                playlistViewModel.importFromSpotifyUrl(url, onDone)
            },
            onImportYouTube = { url, onDone ->
                playlistViewModel.importFromYouTubeUrl(url, onDone)
            }
        )
    }

    ImportPlaylistProgressOverlay(
        importProgressState = playlistViewModel.importProgress
    )

    if (showImportDialog && pendingImportUri != null) {
        ImportPlaylistFileDialog(
            isVisible = showImportDialog,
            uri = pendingImportUri!!,
            isCsv = pendingImportIsCsv,
            playlistViewModel = playlistViewModel,
            onDismiss = {
                showImportDialog = false
                pendingImportUri = null
            }
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ImportPlaylistProgressOverlay(
    importProgressState: StateFlow<com.unshoo.pixelmusic.presentation.viewmodel.ImportProgressState>
) {
    val importProgress by importProgressState.collectAsStateWithLifecycle()
    if (!importProgress.isImporting) return

    val infiniteTransition = rememberInfiniteTransition(label = "ImportMorph")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ImportCardRotation"
    )

    val radiusValue1 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Corner1"
    )
    val radiusValue2 by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Corner2"
    )
    val radiusValue3 by infiniteTransition.animateFloat(
        initialValue = 24f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Corner3"
    )
    val radiusValue4 by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Corner4"
    )

    val morphingShape = remember(radiusValue1, radiusValue2, radiusValue3, radiusValue4) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = radiusValue1.dp,
            cornerRadiusTR = radiusValue2.dp,
            cornerRadiusBR = radiusValue3.dp,
            cornerRadiusBL = radiusValue4.dp,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBR = 60,
            smoothnessAsPercentBL = 60
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                detectTapGestures { } // Consume taps to prevent interactions under the overlay
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                shape = morphingShape
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                    )
                                ),
                                shape = morphingShape
                            )
                    )
                    
                    AnimatedContent(
                        targetState = importProgress.importType,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "ImportIconTransition"
                    ) { type ->
                        val iconPainter = if (type == "csv") {
                            painterResource(R.drawable.rounded_attach_file_24)
                        } else {
                            null
                        }
                        
                        if (iconPainter != null) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }

                // Progress Texts
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = importProgress.playlistName.ifBlank { stringResource(R.string.presentation_batch_b_importing) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val progressText = if (importProgress.totalTracks > 0) {
                        stringResource(
                            R.string.presentation_batch_b_import_progress,
                            importProgress.currentTrackIndex,
                            importProgress.totalTracks
                        )
                    } else {
                        stringResource(R.string.presentation_batch_b_resolving)
                    }
                    
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Linear Wavy Progress Bar
                val animatedProgress by animateFloatAsState(
                    targetValue = if (importProgress.totalTracks > 0) {
                        importProgress.currentTrackIndex.toFloat() / importProgress.totalTracks
                    } else {
                        0f
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "ImportProgressAnim"
                )
                
                if (importProgress.totalTracks > 0) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                
                // Current Song Info details
                if (importProgress.currentTrackName.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = importProgress.currentTrackName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        if (importProgress.currentTrackArtist.isNotBlank()) {
                            Text(
                                text = importProgress.currentTrackArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPlaylistSheet(
    onDismiss: () -> Unit,
    onImportM3u: () -> Unit,
    onImportCsv: () -> Unit,
    onImportSpotify: (url: String, onDone: (Result<String>) -> Unit) -> Unit,
    onImportYouTube: (url: String, onDone: (Result<String>) -> Unit) -> Unit,
) {
    val importPlaylistTitle = stringResource(R.string.presentation_batch_b_import_playlist)
    val importM3uLabel = stringResource(R.string.presentation_batch_b_import_m3u)
    val importM3uDesc = stringResource(R.string.presentation_batch_b_export_as_m3u_desc)
    val importCsvLabel = stringResource(R.string.presentation_batch_b_import_csv)
    val importCsvDesc = stringResource(R.string.presentation_batch_b_export_as_csv_desc)
    val cancelLabel = stringResource(R.string.cancel)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = importPlaylistTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            QuickImportCard(
                onImportSpotify = onImportSpotify,
                onImportYouTube = onImportYouTube,
                onSuccess = { onDismiss() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // M3U import tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onImportM3u)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(importM3uLabel, style = MaterialTheme.typography.titleMedium, fontFamily = GoogleSansRounded)
                    Text(importM3uDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // CSV import tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onImportCsv)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_attach_file_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(importCsvLabel, style = MaterialTheme.typography.titleMedium, fontFamily = GoogleSansRounded)
                    Text(importCsvDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(cancelLabel)
            }
        }
    }
}

@Composable
private fun CompactLibraryPagerIndicator(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val safeIndex = positiveMod(currentIndex, pageCount)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == safeIndex
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 10.dp,
                label = "LibraryCompactPagerIndicatorWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                label = "LibraryCompactPagerIndicatorAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Slim, non-intrusive indicator for sync work that should not keep the list pulled
 * down: automatic startup syncs, background maintenance, and manual refreshes after
 * the short pull-to-refresh confirmation window. It sits just below
 * [LibraryActionRow] and collapses to zero height when not active.
 *
 * Distinct from [LibrarySyncOverlay], which is reserved for initial empty-library
 * loads. The parent screen also gates this indicator off while the pull spinner is
 * visible, so the two feedback channels do not compete.
 */
@Composable
private fun LibraryInlineSyncIndicator(
    visible: Boolean,
    syncManager: com.unshoo.pixelmusic.data.worker.SyncManager
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(180)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(160))
    ) {
        // Collected inside this subtree so progress ticks don't recompose the
        // parent screen â€” same pattern as LibrarySyncOverlay.
        val syncProgress by syncManager.syncProgress
            .collectAsStateWithLifecycle(initialValue = SyncProgress())

        val phaseLabel = when (syncProgress.phase) {
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                stringResource(R.string.sync_scanning)
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                stringResource(R.string.sync_processing)
            SyncProgress.SyncPhase.SCANNING_LRC ->
                stringResource(R.string.library_background_sync_lyrics)
            SyncProgress.SyncPhase.CLEANING_CACHE ->
                stringResource(R.string.library_background_sync_cache)
            SyncProgress.SyncPhase.SYNCING_CLOUD ->
                stringResource(R.string.library_background_sync_cloud)
            else ->
                stringResource(R.string.sync_in_progress)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * P1-1: Isolated sync/loading overlay composable.
 *
 * By collecting [SyncManager.syncProgress] HERE instead of in the parent [LibraryScreen],
 * only this small subtree recomposes on every progress tick (e.g., file count updates
 * during a library scan). The rest of [LibraryScreen] â€” including the Scaffold, pager,
 * and all tab content â€” remains unaffected during sync.
 */
@Composable
private fun LibrarySyncOverlay(syncManager: com.unshoo.pixelmusic.data.worker.SyncManager) {
    val syncProgress by syncManager.syncProgress
        .collectAsStateWithLifecycle(initialValue = SyncProgress())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                if (syncProgress.hasProgress && syncProgress.isRunning) {
                    // Show progress bar with file count when we have progress info
                    SyncProgressBar(
                        syncProgress = syncProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Show indeterminate loading indicator when scanning starts
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.syncing_library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryNavigationPill(
    modifier: Modifier = Modifier,
    title: String,
    isExpanded: Boolean,
    iconRes: Int,
    showIcon: Boolean = true,
    pageIndex: Int,
    compressForWatchTransfer: Boolean,
    onClick: () -> Unit,
    onArrowClick: () -> Unit
) {
    data class PillState(val pageIndex: Int, val iconRes: Int, val title: String)

    val pillRadius = 50.dp//26.dp
    val innerRadius = 4.dp
    val titleHorizontalPadding = 14.dp
    val titleVerticalPadding = 10.dp
    val titleIconSize = 22.dp
    val titleIconSpacing = 10.dp
    val pillHeight = 52.dp
    val arrowContentWidth = 36.dp
    val pillGap = 4.dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var availableWidthPx by remember { mutableStateOf(0) }

    val animatedArrowCorner by animateDpAsState(
        targetValue = if (isExpanded) pillRadius else innerRadius,
        label = "ArrowCornerAnimation"
    )
    val compressionProgress by animateFloatAsState(
        targetValue = if (compressForWatchTransfer) 1f else 0f,
        label = "LibraryPillCompression"
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ArrowRotation"
    )
    val targetArrowHorizontalPadding =
        LibraryNavigationPillArrowPaddingExpanded -
                (LibraryNavigationPillArrowPaddingExpanded - LibraryNavigationPillArrowPaddingCompressed) *
                compressionProgress
    val animatedArrowHorizontalPadding by animateDpAsState(
        targetValue = targetArrowHorizontalPadding,
        label = "LibraryPillArrowPadding"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
            .onSizeChanged { availableWidthPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val baseTitleStyle = rememberLibraryNavigationPillTitleStyle(
            widthAxis = LibraryNavigationPillTitleWidthMax
        )
        val idealTextWidth = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(title),
                style = baseTitleStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toDp()
        }
        val targetArrowWidth = arrowContentWidth + (targetArrowHorizontalPadding * 2)
        val availableWidth = if (availableWidthPx > 0) {
            with(density) { availableWidthPx.toDp() }
        } else {
            // High fallback value for initial composition
            1000.dp
        }
        val maxTitleWidth = (availableWidth - targetArrowWidth - pillGap - 4.dp).coerceAtLeast(0.dp)
        val idealTitleWidth = idealTextWidth +
                titleHorizontalPadding * 2 +
                (if (showIcon) (titleIconSize + titleIconSpacing) else 0.dp) +
                14.dp // Generous safety buffer for variable font glyph bounds
        val naturalTitleWidth = minOf(idealTitleWidth, maxTitleWidth)
        val minCompressedTitleWidth = (
                titleHorizontalPadding * 2 +
                        titleIconSize +
                        titleIconSpacing +
                        LibraryNavigationPillMinimumTextWidth
                ).coerceAtMost(maxTitleWidth)
        val forcedCompressionWidth = minOf(
            LibraryNavigationPillForcedCompressionWidth,
            (naturalTitleWidth - minCompressedTitleWidth).coerceAtLeast(0.dp),
        )
        val targetTitleWidth = (
                naturalTitleWidth - (forcedCompressionWidth * compressionProgress)
                ).coerceAtLeast(minCompressedTitleWidth)
        val widthCompressionRatio = if (idealTitleWidth.value > 0f) {
            (targetTitleWidth.value / idealTitleWidth.value).coerceIn(0f, 1f)
        } else {
            1f
        }
        val widthAxisBySpace = LibraryNavigationPillTitleWidthMin +
                (LibraryNavigationPillTitleWidthMax - LibraryNavigationPillTitleWidthMin) *
                widthCompressionRatio.coerceIn(0f, 1f)
        val forcedWidthAxis = LibraryNavigationPillTitleWidthMax -
                (LibraryNavigationPillTitleWidthMax - LibraryNavigationPillCompressedWidthAxis) *
                compressionProgress
        val targetWidthAxis = minOf(widthAxisBySpace, forcedWidthAxis)
        val animatedTitleWidth by animateDpAsState(
            targetValue = targetTitleWidth,
            label = "LibraryPillTitleWidth"
        )
        val animatedWidthAxis by animateFloatAsState(
            targetValue = targetWidthAxis,
            label = "LibraryPillTitleAxis"
        )
        val titleStyle = rememberLibraryNavigationPillTitleStyle(widthAxis = animatedWidthAxis)

        Row(
            modifier = Modifier.height(pillHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pillGap)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = pillRadius,
                    bottomStart = pillRadius,
                    topEnd = innerRadius,
                    bottomEnd = innerRadius
                ),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .width(animatedTitleWidth)
                    .height(pillHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = pillRadius,
                            bottomStart = pillRadius,
                            topEnd = innerRadius,
                            bottomEnd = innerRadius
                        )
                    )
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier.padding(start = titleHorizontalPadding, end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = PillState(pageIndex = pageIndex, iconRes = iconRes, title = title),
                        transitionSpec = {
                            // Calculate direction based on shortest path for potentially infinite/large page indices
                            val diff = targetState.pageIndex - initialState.pageIndex
                            val direction = when {
                                diff == 0 -> 0
                                // If the absolute difference is very large, it's likely a wrap-around or a direct jump
                                // We treat jumps as "forward" if positive, but we could also check a threshold
                                abs(diff) > 1 -> diff.coerceIn(-1, 1)
                                else -> diff
                            }

                            val slideIn = slideInHorizontally { fullWidth ->
                                if (direction >= 0) fullWidth else -fullWidth
                            } + fadeIn(animationSpec = tween(220))

                            val slideOut = slideOutHorizontally { fullWidth ->
                                if (direction >= 0) -fullWidth else fullWidth
                            } + fadeOut(animationSpec = tween(220))

                            slideIn.togetherWith(slideOut)
                        },
                        label = "LibraryPillTitle"
                    ) { targetState ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = titleVerticalPadding)
                                .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = showIcon,
                                enter = expandHorizontally(
                                    animationSpec = tween(durationMillis = 220),
                                    expandFrom = Alignment.Start
                                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                                exit = shrinkHorizontally(
                                    animationSpec = tween(durationMillis = 220),
                                    shrinkTowards = Alignment.Start
                                ) + fadeOut(animationSpec = tween(durationMillis = 160))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = targetState.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(titleIconSize),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(titleIconSpacing))
                                }
                            }
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = targetState.title,
                                style = titleStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible, // Change to Visible to prevent early ellipsis
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = animatedArrowCorner,
                    bottomStart = animatedArrowCorner,
                    topEnd = pillRadius,
                    bottomEnd = pillRadius
                ),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .height(pillHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = animatedArrowCorner,
                            bottomStart = animatedArrowCorner,
                            topEnd = pillRadius,
                            bottomEnd = pillRadius
                        )
                    )
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onArrowClick
                    )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = animatedArrowHorizontalPadding)
                        .width(arrowContentWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.rotate(arrowRotation),
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.presentation_batch_d_expand_tab_menu_cd),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private const val LibraryNavigationPillTitleWidthMin = 18f
private const val LibraryNavigationPillTitleWidthMax = 100f
private const val LibraryNavigationPillCompressedWidthAxis = 74f
private val LibraryNavigationPillForcedCompressionWidth = 12.dp
private val LibraryNavigationPillMinimumTextWidth = 56.dp
private val LibraryNavigationPillArrowPaddingExpanded = 10.dp
private val LibraryNavigationPillArrowPaddingCompressed = 4.dp

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberLibraryNavigationPillTitleStyle(widthAxis: Float): TextStyle {
    return remember(widthAxis) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(400),
                        FontVariation.width(widthAxis.coerceIn(
                            LibraryNavigationPillTitleWidthMin,
                            LibraryNavigationPillTitleWidthMax
                        )),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTabSwitcherSheet(
    tabs: List<String>,
    currentIndex: Int,
    onTabSelected: (Int) -> Unit,
    onEditClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_d_library_tabs_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded
            )
            Text(
                text = stringResource(R.string.presentation_batch_d_library_tabs_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                itemsIndexed(
                    items = tabs,
                    key = { index, tab -> "$tab-$index" },
                    contentType = { _, _ -> "library_tab_item" }
                ) { index, rawId ->
                    val tabId = rawId.toLibraryTabIdOrNull() ?: return@itemsIndexed
                    LibraryTabGridItem(
                        tabId = tabId,
                        isSelected = index == currentIndex,
                        onClick = { onTabSelected(index) }
                    )
                }

                item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "reorder_tabs_action"
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp, max = 60.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onEditClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.presentation_batch_d_reorder_tabs_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabGridItem(
    tabId: LibraryTabId,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconContainer = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = tabId.iconRes()),
                    contentDescription = tabId.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = tabId.getDisplayTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private fun positiveMod(value: Int, mod: Int): Int = if (mod <= 0) 0 else Math.floorMod(value, mod)

private fun infinitePagerInitialPage(tabCount: Int, selectedTabIndex: Int): Int {
    if (tabCount <= 0) return 0
    val midpoint = Int.MAX_VALUE / 2
    val aligned = midpoint - positiveMod(midpoint, tabCount)
    return aligned + positiveMod(selectedTabIndex, tabCount)
}

private fun resolveTabIndex(page: Int, tabCount: Int, compactMode: Boolean): Int {
    if (tabCount <= 0) return 0
    return if (compactMode) positiveMod(page, tabCount) else page.coerceIn(0, tabCount - 1)
}

private fun targetPageForTabIndex(
    currentPage: Int,
    targetTabIndex: Int,
    tabCount: Int,
    compactMode: Boolean
): Int {
    if (tabCount <= 0) return 0
    val safeTarget = positiveMod(targetTabIndex, tabCount)
    if (!compactMode) return safeTarget

    val currentBase = currentPage - positiveMod(currentPage, tabCount)
    val candidate = currentBase + safeTarget
    val prevCandidate = candidate - tabCount
    val nextCandidate = candidate + tabCount

    return listOf(prevCandidate, candidate, nextCandidate)
        .minByOrNull { abs(it - currentPage) }
        ?: candidate
}

private fun LibraryTabId.iconRes(): Int = when (this) {
    LibraryTabId.SONGS -> R.drawable.rounded_music_note_24
    LibraryTabId.ALBUMS -> R.drawable.rounded_album_24
    LibraryTabId.ARTISTS -> R.drawable.rounded_artist_24
    LibraryTabId.PLAYLISTS -> R.drawable.rounded_playlist_play_24
    LibraryTabId.FOLDERS -> R.drawable.rounded_folder_24
    LibraryTabId.LIKED -> R.drawable.round_favorite_24
}

internal fun resolveFolderNavigationDirection(initialPath: String?, targetPath: String?): Int =
    when {
        initialPath == targetPath -> FOLDER_NAVIGATION_FORWARD
        initialPath == null && targetPath != null -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath == null -> FOLDER_NAVIGATION_BACKWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(initialPath, targetPath) -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(targetPath, initialPath) -> FOLDER_NAVIGATION_BACKWARD
        else -> FOLDER_NAVIGATION_FORWARD
    }

private fun isDescendantFolderPath(ancestorPath: String, candidatePath: String): Boolean {
    val normalizedAncestor = ancestorPath.replace('\\', '/').trimEnd('/')
    val normalizedCandidate = candidatePath.replace('\\', '/').trimEnd('/')
    if (normalizedAncestor == normalizedCandidate) return false
    return normalizedCandidate.startsWith("$normalizedAncestor/")
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFoldersTab(
    folders: ImmutableList<MusicFolder>,
    currentFolder: MusicFolder?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderAsPlaylistClick: (MusicFolder) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    folderPlayerState: FolderPlayerProjection,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isPlaylistView: Boolean = false,
    currentSortOption: SortOption = SortOption.FolderNameAZ,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    pendingLocatePath: String? = null,
    onClearPendingLocate: () -> Unit = {},
    onRequestCrossFolderLocate: (String) -> Unit = {},
    folderArtworkPreference: String = "recently_added"
) {
    // List state moved inside AnimatedContent to prevent state sharing issues during transitions


    AnimatedContent(
        targetState = Pair(isPlaylistView, currentFolder?.path ?: FOLDER_NAVIGATION_ROOT_KEY),
        label = "FolderNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = resolveFolderNavigationDirection(
                initialPath = initialState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY },
                targetPath = targetState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY }
            )
            val slideIn = slideInHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) -width else width
            } + fadeOut()

            slideIn.togetherWith(slideOut)
        }
    ) { (playlistMode, targetPath) ->
        // Each navigation destination gets its own independant ListState
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
        val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
        var lastHandledFolderSortKey by remember { mutableStateOf(currentSortOption.storageKey) }
        var pendingFolderSortScrollReset by remember { mutableStateOf(false) }

        val flattenedFolders = remember(folders, currentSortOption) {
            sortMusicFoldersByOption(flattenFolders(folders), currentSortOption)
        }

        val isRoot = targetPath == FOLDER_NAVIGATION_ROOT_KEY
        val activeFolder = if (isRoot) null else currentFolder
        val showPlaylistCards = playlistMode && activeFolder == null
        val itemsToShow = remember(activeFolder, folders, flattenedFolders, currentSortOption) {
            when {
                showPlaylistCards -> flattenedFolders
                activeFolder != null -> sortMusicFoldersByOption(activeFolder.subFolders, currentSortOption)
                else -> sortMusicFoldersByOption(folders, currentSortOption)
            }
        }.toImmutableList()

        val songsToShow = remember(activeFolder, currentSortOption) {
            sortSongsForFolderView(activeFolder?.songs ?: emptyList(), currentSortOption)
        }.toImmutableList()

        val initialFolderSongBatch = 50
        var visibleFolderSongLimit by remember(songsToShow) {
            mutableIntStateOf(minOf(initialFolderSongBatch, songsToShow.size))
        }
        val shouldLoadMore = remember {
            derivedStateOf {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 4
            }
        }

        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && visibleFolderSongLimit < songsToShow.size) {
                visibleFolderSongLimit = minOf(visibleFolderSongLimit + 40, songsToShow.size)
            }
        }

        val songsToRender = remember(songsToShow, visibleFolderSongLimit) {
            if (songsToShow.size <= initialFolderSongBatch) songsToShow
            else songsToShow.take(visibleFolderSongLimit)
        }
        val currentSong = folderPlayerState.currentSong
        val currentSongId = currentSong?.id
        val currentSongIndexInSongs = remember(songsToShow, currentSongId) {
            currentSongId?.let { songId -> songsToShow.indexOfFirst { it.id == songId } } ?: -1
        }
        val currentSongListIndex = remember(itemsToShow.size, currentSongIndexInSongs) {
            if (currentSongIndexInSongs < 0) -1 else itemsToShow.size + currentSongIndexInSongs
        }
        val songInCurrentFolder = currentSongIndexInSongs >= 0
        val currentSongParentPath: String? = remember(currentSong?.path) {
            currentSong?.path
                ?.takeIf { it.startsWith("/") }
                ?.let { File(it).parentFile?.absolutePath }
        }
        val canCrossFolderLocate = remember(
            playlistMode,
            songInCurrentFolder,
            currentSongParentPath,
            currentFolder?.path
        ) {
            !playlistMode &&
                !songInCurrentFolder &&
                currentSongParentPath != null &&
                currentSongParentPath != currentFolder?.path
        }
        val locateCurrentSongAction: (() -> Unit)? = remember(
            songInCurrentFolder,
            canCrossFolderLocate,
            currentSongListIndex,
            listState,
            currentSongParentPath
        ) {
            when {
                songInCurrentFolder -> {
                    {
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentSongListIndex)
                        }
                    }
                }
                canCrossFolderLocate && currentSongParentPath != null -> {
                    { onRequestCrossFolderLocate(currentSongParentPath) }
                }
                else -> null
            }
        }

        LaunchedEffect(locateCurrentSongAction) {
            registerActionCallback(locateCurrentSongAction)
        }

        LaunchedEffect(currentSortOption) {
            val currentSortKey = currentSortOption.storageKey
            if (currentSortKey == lastHandledFolderSortKey) return@LaunchedEffect
            lastHandledFolderSortKey = currentSortKey
            pendingFolderSortScrollReset = true
            listState.scrollToItem(0)
        }

        LaunchedEffect(itemsToShow, songsToShow, pendingFolderSortScrollReset) {
            if (!pendingFolderSortScrollReset) return@LaunchedEffect
            listState.scrollToItem(0)
            pendingFolderSortScrollReset = false
        }

        LaunchedEffect(
            currentFolder?.path,
            pendingLocatePath,
            currentSongListIndex,
            songsToShow
        ) {
            val pending = pendingLocatePath ?: return@LaunchedEffect
            if (currentFolder?.path != pending) return@LaunchedEffect
            if (currentSongListIndex < 0) return@LaunchedEffect
            listState.animateScrollToItem(currentSongListIndex)
            onClearPendingLocate()
        }

        LaunchedEffect(currentSongListIndex, itemsToShow, songsToShow, listState, canCrossFolderLocate) {
            if (canCrossFolderLocate) {
                visibilityCallback(true)
                return@LaunchedEffect
            }
            if (currentSongListIndex < 0 || songsToShow.isEmpty()) {
                visibilityCallback(false)
                return@LaunchedEffect
            }

            snapshotFlow { listState.firstVisibleItemIndex }
                .map {
                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty()) {
                        false
                    } else {
                        currentSongListIndex in visibleItems.first().index..visibleItems.last().index
                    }
                }
                .distinctUntilChanged()
                .collect { isVisible ->
                    visibilityCallback(!isVisible)
                }
        }

        DisposableEffect(Unit) {
            onDispose {
                visibilityCallback(false)
                registerActionCallback(null)
            }
        }

        val shouldShowLoading = isLoading && itemsToShow.isEmpty() && songsToShow.isEmpty() && isRoot

        Column(modifier = Modifier.fillMaxSize()) {
            when {
                shouldShowLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                itemsToShow.isEmpty() && songsToShow.isEmpty() -> {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.FOLDERS,
                        storageFilter = StorageFilter.LOCAL,
                        bottomBarHeight = bottomBarHeight
                    )
                }

                else -> {
                    val foldersPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = foldersPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = foldersPullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp)
                                    .fillMaxSize()
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 26.dp,
                                            topEnd = 26.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap,
                                    top = 0.dp                            )
                            ) {
                                if (showPlaylistCards) {
                                    itemsUnique(itemsToShow, key = { it.path }, contentType = { "folder_card" }) { folder ->
                                        FolderPlaylistItem(
                                            folder = folder,
                                            onClick = { onFolderAsPlaylistClick(folder) }
                                        )
                                    }
                                } else {
                                    itemsUnique(itemsToShow, key = { it.path }, contentType = { "folder_list" }) { folder ->
                                        FolderListItem(
                                            folder = folder,
                                            folderArtworkPreference = folderArtworkPreference,
                                            onClick = { onFolderClick(folder.path) }
                                        )
                                    }
                                }

                                itemsUnique(songsToRender, key = { it.id }, contentType = { "song" }) { song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = folderPlayerState.currentSongId == song.id && folderPlayerState.isPlaying,
                                        isCurrentSong = folderPlayerState.currentSongId == song.id,
                                        onMoreOptionsClick = { onMoreOptionsClick(song) },
                                        isSelected = selectedSongIds.contains(song.id),
                                        selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                        isSelectionMode = isSelectionMode,
                                        onLongPress = { onSongLongPress(song) },
                                        onClick = {
                                            if (isSelectionMode) {
                                                onSongSelectionToggle(song)
                                            } else {
                                                onPlaySong(song, songsToShow)
                                            }
                                        }
                                    )
                                }

                                if (visibleFolderSongLimit < songsToShow.size) {
                                    item(key = "folders_load_more_loader") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // ScrollBar Overlay
                            val bottomPadding = if (folderPlayerState.currentSongId != null)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                listState = listState
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun FolderPlaylistItem(folder: MusicFolder, onClick: () -> Unit) {
    val previewSongs = remember(folder) { folder.collectAllSongs().take(9) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistArtCollage(
                songs = previewSongs,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatSongCount(folder.totalSongCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FolderListItem(
    folder: MusicFolder,
    folderArtworkPreference: String = "recently_added",
    onClick: () -> Unit
) {
    val recentlyAddedSong = remember(folder, folderArtworkPreference) {
        if (folderArtworkPreference == "recently_added") {
            folder.collectAllSongs().maxByOrNull { it.dateAdded }
        } else {
            null
        }
    }
    val albumArt = recentlyAddedSong?.albumArtUriString

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (albumArt != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                ) {
                    SmartImage(
                        model = albumArt,
                        contentDescription = stringResource(R.string.presentation_batch_d_cd_folder),
                        contentScale = ContentScale.Crop,
                        targetSize = Size(256, 256),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = stringResource(R.string.presentation_batch_d_cd_folder),
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatSongCount(folder.totalSongCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun flattenFolders(folders: List<MusicFolder>): List<MusicFolder> {
    return folders.flatMap { folder ->
        val current = if (folder.songs.isNotEmpty()) listOf(folder) else emptyList()
        current + flattenFolders(folder.subFolders)
    }
}

private fun sortMusicFoldersByOption(folders: List<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
    return when (sortOption) {
        SortOption.FolderNameAZ -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderNameZA -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        else -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
    }
}

private fun sortSongsForFolderView(songs: List<Song>, sortOption: SortOption): List<Song> {
    return when (sortOption) {
        SortOption.FolderNameZA -> songs.sortedWith(
            compareByDescending<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        else -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
    }
}

private fun MusicFolder.collectAllSongs(): List<Song> {
    return songs + subFolders.flatMap { it.collectAllSongs() }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelMusicDarkTheme.current

    // 1. ObtÃ©n el colorScheme del tema actual aquÃ­, en el scope Composable.
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        // 2. Ahora, currentMaterialColorScheme es una variable estable que puedes usar.
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme // Usa la variable capturada
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 20.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.985f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = cardShape
                )
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scrollReveal(initialOffsetY = 8.dp, initialScale = 0.985f)
                .scale(selectionScale)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = cardShape,
            //elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box {
                Column(
                    modifier = Modifier.background(
                        color = gradientBaseColor,
                        shape = cardShape
                    )
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            // Reducido el tamaÃ±o para mejorar el rendimiento del scroll, como se sugiere en el informe.
                            // ContentScale.Crop se encargarÃ¡ de ajustar la imagen al aspect ratio.
                            targetSize = Size(256, 256),
                            isThumbnail = true,
                            crossfadeDurationMillis = 0,
                            modifier = Modifier
                                .aspectRatio(3f / 2f)
                                .fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(
                                modifier = Modifier
                                    .aspectRatio(3f / 2f)
                                    .fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(3f / 2f)
                                .background(
                                    remember(gradientBaseColor) { // Recordar el Brush
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent, gradientBaseColor
                                            )
                                        )
                                    })
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onGradientColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSongCount(album.songCount), style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "âœ“",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ArtistListItem(artist: Artist, onClick: () -> Unit, isLoading: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scrollReveal(initialOffsetY = 8.dp, initialScale = 0.985f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                // Skeleton loading state
                ShimmerBox(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artist.effectiveImageUrl.isNullOrEmpty()) {
                        SmartImage(
                            model = artist.effectiveImageUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            shape = CircleShape,
                            targetSize = SmartImageListTargetSize,
                            errorResId = R.drawable.rounded_artist_24,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.rounded_artist_24),
                            contentDescription = stringResource(R.string.presentation_batch_d_cd_artist),
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(formatSongCount(artist.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumListItem(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalPixelMusicDarkTheme.current
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 16.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.99f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .scrollReveal(initialOffsetY = 8.dp, initialScale = 0.985f)
                .scale(selectionScale)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // LEFT: Album Art
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                    ) {
                        var isLoadingImage by remember { mutableStateOf(true) }
                        SmartImage(
                            model = album.albumArtUriString,
                            contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                            contentScale = ContentScale.Crop,
                            targetSize = SmartImageListTargetSize,
                            isThumbnail = true,
                            crossfadeDurationMillis = 0,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                isLoadingImage = state is AsyncImagePainter.State.Loading
                            }
                        )
                        if (isLoadingImage) {
                            ShimmerBox(modifier = Modifier.fillMaxSize())
                        }

                        // Gradient Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            gradientBaseColor
                                        )
                                    )
                                )
                        )
                    }

                    // MIDDLE: Solid Background
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(gradientBaseColor)
                    ) {
                        // Text on top of the gradient/solid background
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            val variableTextStyle = remember(album.id, album.title) {
                                GenreTypography.getGenreStyle(album.id.toString(), album.title)
                            }

                            Text(
                                album.title,
                                style = variableTextStyle.copy(fontSize = 22.sp),
                                color = onGradientColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatSongCount(album.songCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "âœ“",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPlaylistFileDialog(
    isVisible: Boolean,
    uri: android.net.Uri,
    isCsv: Boolean,
    playlistViewModel: PlaylistViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var initialName by androidx.compose.runtime.remember(uri) {
        var resolvedName = "Imported Playlist"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    resolvedName = rawName
                        .removeSuffix(".m3u8")
                        .removeSuffix(".m3u")
                        .removeSuffix(".csv")
                        .removeSuffix(".txt")
                }
            }
        } catch (e: Exception) {
            resolvedName = "Imported Playlist"
        }
        androidx.compose.runtime.mutableStateOf(resolvedName)
    }

    // Auto-detect format from the actual file extension (overrides the isCsv hint from which launcher was used)
    val effectiveIsCsv = androidx.compose.runtime.remember(uri) {
        var detectedCsv = isCsv
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex).lowercase()
                    detectedCsv = rawName.endsWith(".csv")
                }
            }
        } catch (_: Exception) {}
        detectedCsv
    }

    var playlistNameInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialName) }
    var isImporting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDuplicateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var resolvedSongIds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>?>(null) }
    var existingPlaylist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.unshoo.pixelmusic.data.model.Playlist?>(null) }
    var isProcessingDuplicate by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val importProgress by playlistViewModel.importProgress.collectAsStateWithLifecycle()

    fun resetState() {
        isImporting = false
        showDuplicateDialog = false
        resolvedSongIds = null
        existingPlaylist = null
        isProcessingDuplicate = false
    }

    if (isVisible && !showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    resetState()
                    onDismiss()
                }
            },
            title = {
                Text(
                    text = if (effectiveIsCsv) "Import CSV Playlist" else "Import M3U Playlist",
                    fontFamily = GoogleSansRounded,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isImporting) {
                        Text(
                            text = "Importing tracks: ${importProgress.currentTrackIndex}/${importProgress.totalTracks}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (importProgress.currentTrackName.isNotBlank()) {
                            Text(
                                text = "${importProgress.currentTrackName} - ${importProgress.currentTrackArtist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularWavyProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Text(
                            text = "Confirm playlist name:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting && playlistNameInput.isNotBlank(),
                    onClick = {
                        isImporting = true
                        playlistViewModel.setImportingState(true, playlistNameInput, effectiveIsCsv)
                        coroutineScope.launch {
                            try {
                                val result = if (effectiveIsCsv) {
                                    playlistViewModel.m3uManager.parseCsv(uri) { current, total, title, artist ->
                                        playlistViewModel.updateImportProgress(playlistNameInput, current, total, title, artist)
                                    }
                                } else {
                                    playlistViewModel.m3uManager.parseM3u(uri) { current, total, title, artist ->
                                        playlistViewModel.updateImportProgress(playlistNameInput, current, total, title, artist)
                                    }
                                }
                                val songIds = result.second
                                resolvedSongIds = songIds

                                if (songIds.isEmpty()) {
                                    android.widget.Toast.makeText(context, "No playable songs found to import", android.widget.Toast.LENGTH_SHORT).show()
                                    resetState()
                                    onDismiss()
                                    return@launch
                                }

                                val existing = playlistViewModel.findPlaylistByName(playlistNameInput)
                                if (existing != null) {
                                    existingPlaylist = existing
                                    isImporting = false
                                    showDuplicateDialog = true
                                } else {
                                    playlistViewModel.playlistPreferencesRepository.createPlaylist(playlistNameInput, songIds)
                                    android.widget.Toast.makeText(context, "Playlist imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    resetState()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Import failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                resetState()
                                onDismiss()
                            } finally {
                                playlistViewModel.setImportingState(false)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.playlist_action_import))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        resetState()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDuplicateDialog && existingPlaylist != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessingDuplicate) {
                    resetState()
                    onDismiss()
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.playlist_already_exists_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.playlist_already_exists_body, existingPlaylist!!.name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isProcessingDuplicate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularWavyProgressIndicator()
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Update option (Merge)
                    TextButton(
                        enabled = !isProcessingDuplicate,
                        onClick = {
                            isProcessingDuplicate = true
                            coroutineScope.launch {
                                try {
                                    val songIds = resolvedSongIds ?: emptyList()
                                    val existingSongIds = existingPlaylist!!.songIds.toSet()
                                    val newSongIds = songIds.filterNot { it in existingSongIds }

                                    if (newSongIds.isEmpty()) {
                                        android.widget.Toast.makeText(context, "Playlist is already up to date", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val mergedSongIds = existingPlaylist!!.songIds + newSongIds
                                        playlistViewModel.playlistPreferencesRepository.updatePlaylist(
                                            existingPlaylist!!.copy(songIds = mergedSongIds)
                                        )
                                        android.widget.Toast.makeText(context, "Playlist updated with ${newSongIds.size} new songs", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    resetState()
                                    onDismiss()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to update playlist: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.playlist_update_existing))
                    }

                    // Import as new option
                    TextButton(
                        enabled = !isProcessingDuplicate,
                        onClick = {
                            isProcessingDuplicate = true
                            coroutineScope.launch {
                                try {
                                    val songIds = resolvedSongIds ?: emptyList()
                                    playlistViewModel.playlistPreferencesRepository.createPlaylist(playlistNameInput, songIds)
                                    android.widget.Toast.makeText(context, "Playlist imported as a new copy", android.widget.Toast.LENGTH_SHORT).show()
                                    resetState()
                                    onDismiss()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to import playlist: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.playlist_create_new))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        resetState()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

