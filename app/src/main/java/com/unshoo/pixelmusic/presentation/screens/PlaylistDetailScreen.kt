package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.center

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import android.widget.Toast

import androidx.activity.compose.BackHandler
import android.os.Build
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.PlaylistArtCollage
import com.unshoo.pixelmusic.presentation.components.PlaylistBottomSheet
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import com.unshoo.pixelmusic.presentation.components.QueuePlaylistSongItem
import com.unshoo.pixelmusic.presentation.components.SongPickerBottomSheet
import com.unshoo.pixelmusic.presentation.components.ExpressiveScrollBar
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SongInfoBottomSheet
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.unshoo.pixelmusic.presentation.utils.LocalAppHapticsConfig
import com.unshoo.pixelmusic.presentation.utils.performAppCompatHapticFeedback
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistSongsOrderMode
import com.unshoo.pixelmusic.utils.formatSongCount
import com.unshoo.pixelmusic.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.unshoo.pixelmusic.presentation.components.LibrarySortBottomSheet
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.PlaylistShapeType
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImagePainter
import com.unshoo.pixelmusic.presentation.components.CollapsibleCommonTopBar
import com.unshoo.pixelmusic.ui.theme.PixelMusicStatusBarStyle
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Surface
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.statusBars
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.shortcut_playlist_short)
    val sortSongsLabel = stringResource(R.string.presentation_batch_b_sort_songs)
    val moreOptionsLabel = stringResource(R.string.presentation_batch_b_more_options)
    val playItLabel = stringResource(R.string.presentation_batch_b_play_it)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val addSongsCd = stringResource(R.string.presentation_batch_b_add_songs)
    val addLabel = stringResource(R.string.presentation_batch_b_add)
    val removeLabel = stringResource(R.string.cd_remove)
    val removeSongsCd = stringResource(R.string.presentation_batch_b_remove_songs)
    val reorderLabel = stringResource(R.string.presentation_batch_b_reorder)
    val reorderSongsCd = stringResource(R.string.presentation_batch_b_reorder_songs)
    val reorderSongCd = stringResource(R.string.presentation_batch_b_reorder_song)
    val playlistEmptyTitle = stringResource(R.string.presentation_batch_b_playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.presentation_batch_b_playlist_empty_folder_body)
    val playlistEmptyAddHint = stringResource(R.string.presentation_batch_b_playlist_empty_add_hint)
    val playlistOptionsTitle = stringResource(R.string.presentation_batch_b_playlist_options_title)
    val editPlaylistLabel = stringResource(R.string.presentation_batch_b_edit_playlist)
    val deletePlaylistLabel = stringResource(R.string.presentation_batch_b_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.presentation_batch_b_set_default_transition)
    val exportPlaylistLabel = stringResource(R.string.presentation_batch_b_export_playlist)
    val downloadPlaylistLabel = stringResource(R.string.presentation_batch_b_download_playlist)
    val deletePlaylistConfirmTitle = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_title)
    val deletePlaylistConfirmBody = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_body)
    val sortSheetTitle = stringResource(R.string.presentation_batch_b_sort_songs)
    val toastAddedToQueue = stringResource(R.string.toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs
    val playlistDisplaySongCount = currentPlaylist?.displaySongCount ?: songsInPlaylist.size
    val isYoutubePlaylist = remember(currentPlaylist, playlistId, uiState.playlists, songsInPlaylist) {
        val cleanId = playlistId.removePrefix("VL")
        val curCleanId = (currentPlaylist?.id ?: "").removePrefix("VL")
        val ytPrefixes = setOf("VL", "PL", "OLAK", "RD", "LM", "FL", "_downloaded_")
        currentPlaylist?.source.equals("YOUTUBE", ignoreCase = true) ||
        currentPlaylist?.source.equals("YOUTUBE_MUSIC", ignoreCase = true) ||
        ytPrefixes.any { playlistId.startsWith(it) || (currentPlaylist?.id ?: "").startsWith(it) } ||
        uiState.playlists.any { it.source.equals("YOUTUBE", ignoreCase = true) && it.id.removePrefix("VL") in setOf(cleanId, curCleanId) } ||
        (songsInPlaylist.isNotEmpty() && songsInPlaylist.all { it.isYouTube || it.id.startsWith("youtube_") || it.contentUriString.startsWith("youtube://") || it.youtubeId != null })
    }
    val isPlaylistLiked = remember(uiState.playlists, currentPlaylist, playlistId) {
        val targetIds = setOf(playlistId.removePrefix("VL"), (currentPlaylist?.id ?: "").removePrefix("VL"))
        uiState.playlists.any { it.id.removePrefix("VL") in targetIds }
    }
    val isYoutubePlaylistHydrating = isYoutubePlaylist && currentPlaylist?.songIds.isNullOrEmpty() && songsInPlaylist.isEmpty()
    val isPlaylistFullyDownloaded by remember(songsInPlaylist) {
        derivedStateOf {
            songsInPlaylist.isNotEmpty() && songsInPlaylist.all { it.path.isNotBlank() }
        }
    }
    // remember() the Flow itself (keyed on playlistId), not just collectAsState, because
    // observePlaylistDownloadStatus() builds a fresh WorkManager Flow on every call -- without
    // remember() a recomposition would resubscribe to WorkManager's Flow repeatedly instead of
    // observing one stable subscription.
    val playlistDownloadStatus by remember(playlistId) {
        playerViewModel.observePlaylistDownloadStatus(playlistId)
    }.collectAsStateWithLifecycle(initialValue = PlayerViewModel.PlaylistDownloadStatus())
    val isPlaylistDownloading = playlistDownloadStatus.isActive

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }

    val canHandlePlaylistBack = isReorderModeEnabled || isRemoveModeEnabled || showAddSongsSheet
    BackHandler(enabled = canHandlePlaylistBack) {
        when {
            showAddSongsSheet -> {
                showAddSongsSheet = false
            }
            isReorderModeEnabled -> {
                isReorderModeEnabled = false
            }
            isRemoveModeEnabled -> {
                isRemoveModeEnabled = false
            }
        }
    }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportCsv(playlist, it, context)
            }
        }
    }
    var showExportSheet by remember { mutableStateOf(false) }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var reorderedSongs by remember { mutableStateOf<List<Song>?>(null) }
    var reorderedKeys by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(isReorderModeEnabled) {
        if (isReorderModeEnabled) {
            reorderedSongs = songsInPlaylist
            reorderedKeys = songsInPlaylist.indices.map { "${songsInPlaylist[it].id}_$it" }
        } else {
            reorderedSongs = null
            reorderedKeys = null
        }
    }

    val displayedSongs = if (isReorderModeEnabled && reorderedSongs != null) {
        reorderedSongs!!
    } else {
        songsInPlaylist
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val keys = reorderedKeys ?: return@rememberReorderableLazyListState
            val songs = reorderedSongs ?: return@rememberReorderableLazyListState
            val fromPos = keys.indexOf(from.key).takeIf { it >= 0 } ?: from.index
            val toPos = keys.indexOf(to.key).takeIf { it >= 0 } ?: to.index

            if (fromPos != toPos && fromPos in songs.indices && toPos in songs.indices) {
                reorderedSongs = songs.toMutableList().apply {
                    add(toPos, removeAt(fromPos))
                }
                reorderedKeys = keys.toMutableList().apply {
                    add(toPos, removeAt(fromPos))
                }
                if (lastMovedFrom == null) {
                    lastMovedFrom = fromPos
                }
                lastMovedTo = toPos
            }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isFolderPlaylist) {
        if (!isFolderPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isFolderPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val isDarkTheme = LocalPixelMusicDarkTheme.current
    val baseColorScheme = MaterialTheme.colorScheme

    val fallbackSongs = remember(songsInPlaylist) {
        val result = ArrayList<Song>(4)
        for (s in songsInPlaylist) {
            if (!s.albumArtUriString.isNullOrBlank()) {
                result.add(s)
                if (result.size == 4) break
            }
        }
        result
    }
    val playlistArtUri = remember(currentPlaylist?.coverImageUri, fallbackSongs) {
        currentPlaylist?.coverImageUri?.takeIf { it.isNotBlank() }
            ?: fallbackSongs.firstOrNull()?.albumArtUriString
    }

    val playlistColorSchemeFlow = remember(playlistArtUri) {
        playlistArtUri?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val playlistColorSchemePair = playlistColorSchemeFlow?.collectAsStateWithLifecycle()?.value
    val playlistColorScheme = remember(playlistColorSchemePair, isDarkTheme, baseColorScheme) {
        playlistColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
            ?: baseColorScheme
    }

    var headerArtworkLoaded by remember(playlistArtUri) { mutableStateOf(playlistArtUri == null) }
    var themeRequestIssued by remember(playlistArtUri) { mutableStateOf(playlistArtUri == null) }
    LaunchedEffect(playlistArtUri, headerArtworkLoaded, themeRequestIssued) {
        if (!themeRequestIssued && headerArtworkLoaded && playlistArtUri != null) {
            themeRequestIssued = true
            playerViewModel.themeStateHolder.ensureAlbumColorScheme(playlistArtUri)
        }
    }

    val showLoading = currentPlaylist == null

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.playlistNotFound) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(id = R.string.playlist_not_found))
            }
        } else if (currentPlaylist != null) {
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val coroutineScope = rememberCoroutineScope()
            val lazyListState = listState

            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val minTopBarHeight = 64.dp + statusBarHeight
            val maxTopBarHeight = 300.dp

            val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
            val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

            val headerImageRequestSize = remember(configuration.screenWidthDp, density.density, maxTopBarHeightPx) {
                Size(
                    width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                    height = maxTopBarHeightPx.roundToInt()
                )
            }

            val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
            val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
                derivedStateOf {
                    1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
                }
            }

            var topBarSnapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        val isScrollingDown = delta < 0

                        if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                            return Offset.Zero
                        }

                        val previousHeight = topBarHeight.value
                        val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                        val consumed = newHeight - previousHeight

                        if (consumed.roundToInt() != 0) {
                            topBarSnapJob?.cancel()
                            topBarSnapJob = coroutineScope.launch {
                                topBarHeight.snapTo(newHeight)
                            }
                        }

                        val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                        return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
                    }
                }
            }

            LaunchedEffect(lazyListState.isScrollInProgress) {
                if (!lazyListState.isScrollInProgress) {
                    val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
                    val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

                    val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

                    if (topBarHeight.value != targetValue) {
                        topBarSnapJob?.cancel()
                        topBarSnapJob = coroutineScope.launch {
                            topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !showLoading,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(nestedScrollConnection)
                ) {
                val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

                CollapsingPlaylistTopBar(
                    playlist = currentPlaylist,
                    songs = displayedSongs,
                    collapseFraction = collapseFraction,
                    headerHeight = currentTopBarHeightDp,
                    headerImageRequestSize = headerImageRequestSize,
                    playlistColorScheme = playlistColorScheme,
                    onHeaderArtworkState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            headerArtworkLoaded = true
                        }
                    },
                    onBackPressed = onBackClick,
                    showPlaylistOptionsSheet = { showPlaylistOptionsSheet = true },
                    playerViewModel = playerViewModel
                )

                val actionButtonsHeight = 42.dp
                val playbackControlBottomPadding = if (isFolderPlaylist) 8.dp else 6.dp

                // Sticky controls: Play/Shuffle buttons and Action Chips
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = playbackControlBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val playButtonShape = remember {
                            AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 60.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusTR = 14.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBL = 60.dp,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusBR = 14.dp,
                                smoothnessAsPercentBL = 60
                            )
                        }
                        val shuffleButtonShape = remember {
                            AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 14.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusTR = 60.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBL = 14.dp,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusBR = 60.dp,
                                smoothnessAsPercentBL = 60
                            )
                        }

                        Button(
                            onClick = {
                                if (displayedSongs.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        displayedSongs,
                                        displayedSongs.first(),
                                        currentPlaylist.name,
                                        currentPlaylist.id
                                    )
                                    if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                } else if (isYoutubePlaylist) {
                                    playerViewModel.playRadio(
                                        unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(playlistId = currentPlaylist.id),
                                        currentPlaylist.name
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = isYoutubePlaylist || displayedSongs.isNotEmpty(),
                            shape = playButtonShape
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(playItLabel)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (displayedSongs.isNotEmpty()) {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = displayedSongs,
                                        queueName = currentPlaylist.name,
                                        playlistId = currentPlaylist.id,
                                        startAtZero = true
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = displayedSongs.isNotEmpty(),
                            shape = shuffleButtonShape
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = shuffleLabel,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(shuffleLabel)
                        }
                    }

                    if (!isFolderPlaylist) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val reorderCornerRadius by animateDpAsState(
                                targetValue = if (isReorderModeEnabled) 24.dp else 12.dp,
                                label = "reorderCornerRadius"
                            )
                            val reorderButtonColor by animateColorAsState(
                                targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                label = "reorderButtonColor"
                            )
                            val reorderIconColor by animateColorAsState(
                                targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                label = "reorderIconColor"
                            )

                            val removeCornerRadius by animateDpAsState(
                                targetValue = if (isRemoveModeEnabled) 24.dp else 12.dp,
                                label = "removeCornerRadius"
                            )
                            val removeButtonColor by animateColorAsState(
                                targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                label = "removeButtonColor"
                            )
                            val removeIconColor by animateColorAsState(
                                targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                label = "removeIconColor"
                            )

                            if (isYoutubePlaylist) {
                                Button(
                                    onClick = {
                                        playlistViewModel.togglePlaylistLike(currentPlaylist, songsInPlaylist)
                                        Toast.makeText(
                                            context,
                                            if (isPlaylistLiked) "Removed from library" else "Saved to library",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPlaylistLiked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (isPlaylistLiked) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .height(actionButtonsHeight)
                                        .animateContentSize()
                                ) {
                                    Icon(
                                        imageVector = if (isPlaylistLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isPlaylistLiked) "Unlike playlist" else "Like playlist",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isPlaylistLiked) "Liked" else "Like",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            if (!isYoutubePlaylist || isPlaylistLiked) {
                                Button(
                                    onClick = { showAddSongsSheet = true },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    modifier = Modifier
                                        .height(actionButtonsHeight)
                                        .animateContentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = addSongsCd,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = addLabel,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = { isRemoveModeEnabled = !isRemoveModeEnabled },
                                shape = RoundedCornerShape(removeCornerRadius),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = removeButtonColor,
                                    contentColor = removeIconColor
                                ),
                                modifier = Modifier
                                    .height(actionButtonsHeight)
                                    .animateContentSize()
                                    .clip(RoundedCornerShape(removeCornerRadius))
                            ) {
                                Icon(
                                    modifier = Modifier.size(18.dp),
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = removeSongsCd,
                                    tint = removeIconColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    modifier = Modifier.padding(end = 4.dp),
                                    text = removeLabel,
                                    color = removeIconColor,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Button(
                                onClick = { isReorderModeEnabled = !isReorderModeEnabled },
                                shape = RoundedCornerShape(reorderCornerRadius),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = reorderButtonColor,
                                    contentColor = reorderIconColor
                                ),
                                modifier = Modifier
                                    .height(actionButtonsHeight)
                                    .animateContentSize()
                                    .clip(RoundedCornerShape(reorderCornerRadius))
                            ) {
                                Icon(
                                    modifier = Modifier.size(22.dp),
                                    painter = painterResource(R.drawable.drag_order_icon),
                                    contentDescription = reorderSongsCd,
                                    tint = reorderIconColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    modifier = Modifier.padding(end = 4.dp),
                                    text = reorderLabel,
                                    color = reorderIconColor,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Box(
                                modifier = Modifier.size(actionButtonsHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPlaylistDownloading) {
                                    val total = playlistDownloadStatus.total
                                    if (total > 0) {
                                        val fraction = (playlistDownloadStatus.completed / total.toFloat())
                                            .coerceIn(0f, 1f)
                                        CircularProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.size(actionButtonsHeight),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(actionButtonsHeight),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        when {
                                            isPlaylistDownloading -> playerViewModel.cancelPlaylistDownload(currentPlaylist.id)
                                            isPlaylistFullyDownloaded -> Toast.makeText(context, "Playlist already fully downloaded", Toast.LENGTH_SHORT).show()
                                            else -> playerViewModel.downloadPlaylistSongs(currentPlaylist.id, currentPlaylist.songIds)
                                        }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isPlaylistFullyDownloaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (isPlaylistFullyDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.size(if (isPlaylistDownloading) actionButtonsHeight - 10.dp else actionButtonsHeight)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isPlaylistDownloading -> Icons.Rounded.Close
                                            isPlaylistFullyDownloaded -> Icons.Rounded.Check
                                            else -> Icons.Rounded.Download
                                        },
                                        contentDescription = if (isPlaylistDownloading) "Cancel download" else downloadPlaylistLabel
                                    )
                                }
                            }
                        }
                    }
                }

                // Song list occupies the rest of the height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (displayedSongs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isYoutubePlaylistHydrating) {
                                    CircularProgressIndicator(modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Syncing playlist songs…", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Songs will appear here automatically when YouTube Music hydration finishes.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(playlistEmptyTitle, style = MaterialTheme.typography.titleMedium)
                                    val emptyMessage = when {
                                        isFolderPlaylist -> playlistEmptyFolder
                                        isYoutubePlaylist && !isPlaylistLiked -> playlistEmptyTitle
                                        else -> playlistEmptyAddHint
                                    }
                                    Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        val showScrollBar by remember { derivedStateOf { lazyListState.canScrollForward || lazyListState.canScrollBackward } }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                                start = 16.dp,
                                end = if (showScrollBar) 24.dp else 16.dp
                            )
                        ) {
                            itemsIndexed(
                                displayedSongs,
                                key = { index, song -> reorderedKeys?.getOrNull(index) ?: "${song.id}_$index" },
                                contentType = { _, _ -> "playlist_song" }
                            ) { index, song ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = reorderedKeys?.getOrNull(index) ?: "${song.id}_$index",
                                ) { isDragging ->
                                    val scale by animateFloatAsState(
                                        targetValue = if (isReorderModeEnabled && isDragging) 1.05f else 1f,
                                        label = "scale"
                                    )

                                    QueuePlaylistSongItem(
                                        modifier = if (scale != 1f) {
                                            Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                        } else {
                                            Modifier.fillMaxWidth()
                                        },
                                        onClick = {
                                            playerViewModel.playSongs(
                                                displayedSongs,
                                                song,
                                                currentPlaylist.name,
                                                currentPlaylist.id
                                            )
                                        },
                                        song = song,
                                        isCurrentSong = playerStableState.currentSong?.id == song.id,
                                        isPlaying = playerStableState.isPlaying,
                                        isDragging = isDragging,
                                        onRemoveClick = {
                                            if (!isFolderPlaylist) {
                                                currentPlaylist.let {
                                                    playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                                }
                                            }
                                        },
                                        isFromPlaylist = true,
                                        isReorderModeEnabled = isReorderModeEnabled,
                                        isDragHandleVisible = isReorderModeEnabled,
                                        isRemoveButtonVisible = isRemoveModeEnabled,
                                        onMoreOptionsClick = stableOnMoreOptionsClick,
                                        dragHandle = {
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStarted = {
                                                            performAppCompatHapticFeedback(
                                                                view,
                                                                appHapticsConfig,
                                                                HapticFeedbackConstantsCompat.GESTURE_START
                                                            )
                                                        },
                                                        onDragStopped = {
                                                            performAppCompatHapticFeedback(
                                                                view,
                                                                appHapticsConfig,
                                                                HapticFeedbackConstantsCompat.GESTURE_END
                                                            )
                                                        }
                                                    )
                                                    .size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragIndicator,
                                                    contentDescription = reorderSongCd,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        ExpressiveScrollBar(
                            listState = lazyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    bottom = if (playerStableState.currentSong != null) MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                                    end = 14.dp,
                                    top = 18.dp
                                )
                        )
                    }
                }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showLoading,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(350))
        ) {
                com.unshoo.pixelmusic.presentation.components.PlaylistSkeletonDetail()
        }
    }

    if (showAddSongsSheet && currentPlaylist != null && !isFolderPlaylist && (!isYoutubePlaylist || isPlaylistLiked)) {
        SongPickerBottomSheet(
            playerViewModel = playerViewModel,
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
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
                        text = playlistOptionsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PlaylistActionItem(
                    icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.QueueMusic),
                    label = stringResource(R.string.cd_add_all_to_queue),
                    onClick = {
                        showPlaylistOptionsSheet = false
                        playerViewModel.addSongsToQueue(displayedSongs)
                    }
                )
                currentPlaylist?.let { playlist ->
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.PushPin),
                        label = if (playlist.isPinned) "Unpin Playlist" else "Pin Playlist",
                        onClick = {
                            showPlaylistOptionsSheet = false
                            playlistViewModel.togglePinPlaylist(playlist.id)
                        }
                    )
                }
                if (!isFolderPlaylist) {
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_edit_24),
                        label = editPlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            showEditPlaylistDialog = true
                        }
                    )
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_delete_24),
                        label = deletePlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            showDeleteConfirmation = true
                        }
                    )
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.outline_graph_1_24),
                        label = setDefaultTransitionLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                        }
                    )
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_attach_file_24),
                        label = exportPlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            showExportSheet = true
                        }
                    )
                }
                if (!isPlaylistFullyDownloaded) {
                    PlaylistActionItem(
                        icon = rememberVectorPainter(if (isPlaylistDownloading) Icons.Rounded.Close else Icons.Rounded.Download),
                        label = if (isPlaylistDownloading) "Cancel Download" else downloadPlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            currentPlaylist?.let { playlist ->
                                if (isPlaylistDownloading) {
                                    playerViewModel.cancelPlaylistDownload(playlist.id)
                                } else {
                                    playerViewModel.downloadPlaylistSongs(playlist.id, playlist.songIds)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }
        
        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(deletePlaylistConfirmTitle) },
            text = {
                Text(deletePlaylistConfirmBody)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.delete_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                playerViewModel = playerViewModel,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    scope.launch {
                        playerViewModel.toggleFavoriteSpecificSongSuspending(currentSong)
                        playlistViewModel.refreshCurrentPlaylist()
                    }
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastPlayingNext)
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
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
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
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = {
                showPlaylistBottomSheet = false
            },
            currentPlaylistId = playlistId,
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }

    if (showExportSheet && currentPlaylist != null) {
        ExportPlaylistSheet(
            playlistName = currentPlaylist.name,
            onDismiss = { showExportSheet = false },
            onSaveM3u = {
                showExportSheet = false
                m3uExportLauncher.launch("${currentPlaylist.name}.m3u")
            },
            onSaveCsv = {
                showExportSheet = false
                csvExportLauncher.launch("${currentPlaylist.name}.csv")
            },
            onShareM3u = {
                showExportSheet = false
                playlistViewModel.sharePlaylist(currentPlaylist, asCsv = false, context)
            },
            onShareCsv = {
                showExportSheet = false
                playlistViewModel.sharePlaylist(currentPlaylist, asCsv = true, context)
            }
        )
    }
}


@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
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
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// SongPickerBottomSheet moved to com.unshoo.pixelmusic.presentation.components
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    val renameTitle = stringResource(R.string.presentation_batch_b_rename_playlist_dialog_title)
    val newNameLabel = stringResource(R.string.presentation_batch_b_new_name)
    val renameAction = stringResource(R.string.action_rename)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(renameTitle) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(newNameLabel) },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text(renameAction, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportPlaylistSheet(
    playlistName: String,
    onDismiss: () -> Unit,
    onSaveM3u: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareM3u: () -> Unit,
    onShareCsv: () -> Unit,
) {
    // false = CSV selected, true = M3U selected  (CSV is default like ArchiveTune)
    var selectedCsv by remember { mutableStateOf(true) }

    val exportPlaylistTitle = stringResource(R.string.presentation_batch_b_export_playlist)
    val exportAsCsvLabel = stringResource(R.string.presentation_batch_b_export_as_csv)
    val exportAsCsvDesc = stringResource(R.string.presentation_batch_b_export_as_csv_desc)
    val exportAsM3uLabel = stringResource(R.string.presentation_batch_b_export_as_m3u)
    val exportAsM3uDesc = stringResource(R.string.presentation_batch_b_export_as_m3u_desc)
    val saveLabel = stringResource(R.string.presentation_batch_b_export_save)
    val shareLabel = stringResource(R.string.presentation_batch_b_export_share)
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
            // Title
            Text(
                text = exportPlaylistTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            // Format tiles
            @Composable
            fun FormatTile(
                icon: androidx.compose.ui.graphics.painter.Painter,
                label: String,
                description: String,
                selected: Boolean,
                onClick: () -> Unit
            ) {
                val bgColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "tileBg"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(bgColor)
                        .clickable(onClick = onClick)
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
                            painter = icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // CSV option (default selected)
            FormatTile(
                icon = painterResource(R.drawable.rounded_attach_file_24),
                label = exportAsCsvLabel,
                description = exportAsCsvDesc,
                selected = selectedCsv,
                onClick = { selectedCsv = true }
            )

            // M3U option
            FormatTile(
                icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.QueueMusic),
                label = exportAsM3uLabel,
                description = exportAsM3uDesc,
                selected = !selectedCsv,
                onClick = { selectedCsv = false }
            )

            Spacer(Modifier.height(4.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(cancelLabel, style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = { if (selectedCsv) onSaveCsv() else onSaveM3u() },
                    modifier = Modifier.weight(1.5f),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 60.dp, smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 60.dp, smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
                    )
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(saveLabel, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { if (selectedCsv) onShareCsv() else onShareM3u() },
                    modifier = Modifier.weight(1.5f),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 60.dp, smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 60.dp, smoothnessAsPercentBR = 60
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(shareLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CollapsingPlaylistTopBar(
    playlist: Playlist,
    songs: List<Song>,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    playlistColorScheme: androidx.compose.material3.ColorScheme,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
    onBackPressed: () -> Unit,
    showPlaylistOptionsSheet: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val statusBarColor =
        if (LocalPixelMusicDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha

    val fallbackSongs = remember(songs) {
        songs.asSequence().filter { !it.albumArtUriString.isNullOrBlank() }.take(4).toList()
    }
    val playlistArtUri = remember(playlist.coverImageUri, fallbackSongs) {
        playlist.coverImageUri?.takeIf { it.isNotBlank() }
            ?: fallbackSongs.firstOrNull()?.albumArtUriString
    }

    val dynamicBaseColor = playlistColorScheme.primary
    val headerOverlayBrush = remember(surfaceColor, dynamicBaseColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                dynamicBaseColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }

    PixelMusicStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (!playlistArtUri.isNullOrBlank()) {
                SmartImage(
                    model = if (playlistArtUri.startsWith("/")) java.io.File(playlistArtUri) else playlistArtUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    onState = onHeaderArtworkState,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PlaylistArtCollage(
                    songs = fallbackSongs,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        val playlistDisplaySongCount = playlist.displaySongCount ?: songs.size
        val songCountLabel = remember(songs, playlistDisplaySongCount) {
            context.getString(
                R.string.presentation_batch_f_status_bullet_step,
                formatSongCount(playlistDisplaySongCount),
                formatTotalDuration(songs)
            )
        }

        CollapsibleCommonTopBar(
            title = playlist.name,
            subtitle = songCountLabel,
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackPressed,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 88.dp,
            expandedTitleEndPadding = 24.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = {
                IconButton(
                    onClick = {
                        playerViewModel.showSortingSheet() 
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.presentation_batch_b_sort_songs)
                    )
                }
                IconButton(
                    onClick = showPlaylistOptionsSheet,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.presentation_batch_b_more_options)
                    )
                }
            }
        )
    }
}

@Composable
fun M3MicroAnimatedLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_loader")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Canvas(modifier = modifier.size(48.dp)) {
        val center = size.center
        val radius = size.minDimension / 4
        
        rotate(rotation) {
            drawCircle(
                color = color.copy(alpha = 0.85f),
                radius = radius * scale1,
                center = Offset(center.x - radius, center.y - radius)
            )
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = radius * scale2,
                center = Offset(center.x + radius, center.y - radius)
            )
            drawCircle(
                color = color.copy(alpha = 0.3f),
                radius = radius * scale1,
                center = Offset(center.x, center.y + radius)
            )
        }
    }
}

