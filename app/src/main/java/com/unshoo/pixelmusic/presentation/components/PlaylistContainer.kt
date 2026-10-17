package com.unshoo.pixelmusic.presentation.components

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.presentation.components.subcomps.SineWaveLine
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.screens.PlayerSheetCollapsedCornerRadius
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistUiState
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistSelectionStateHolder
import com.unshoo.pixelmusic.utils.formatSongCount
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.defaultSpatial
import androidx.compose.foundation.combinedClickable


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistContainer(
    playlistUiState: PlaylistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    currentSong: Song? = null,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    filteredPlaylists: List<Playlist> = playlistUiState.playlists,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistSelectionStateHolder: PlaylistSelectionStateHolder? = null
) {

    // Plain conditionals — no Crossfade.
    // Crossfade double-composed the loading + content trees on every transition,
    // causing jank when switching to the Playlists tab.
    Column(modifier = Modifier.fillMaxSize()) {
        if (playlistUiState.isLoading && filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { ContainedLoadingIndicator() }
        } else if (filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        alpha = 0.95f,
                        strokeWidth = 3.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_no_playlist_created),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.presentation_batch_e_new_playlist_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (isAddingToPlaylist) {
                PlaylistItems(
                    currentSong = currentSong,
                    bottomBarHeight = bottomBarHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists,
                    currentSortOption = currentSortOption
                )
            } else {
                val playlistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = playlistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = playlistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    PlaylistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isSelectionMode = isSelectionMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    currentSong: Song? = null,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: List<Playlist>,
    currentSortOption: SortOption? = null,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val playlistFastScrollLabelProvider = remember(filteredPlaylists, currentSortOption) {
        { index: Int ->
            playlistFastScrollLabel(
                playlist = filteredPlaylists.getOrNull(index),
                sortOption = currentSortOption
            )
        }
    }
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }
    var pendingPlaylistSortScrollReset by remember { mutableStateOf(false) }

    LaunchedEffect(currentSortOption) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        pendingPlaylistSortScrollReset = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(filteredPlaylists, pendingPlaylistSortScrollReset) {
        if (!pendingPlaylistSortScrollReset) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingPlaylistSortScrollReset = false
    }

    val initialPlaylistBatch = 30
    var visiblePlaylistLimit by remember(filteredPlaylists) {
        mutableIntStateOf(minOf(initialPlaylistBatch, filteredPlaylists.size))
    }
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && visiblePlaylistLimit < filteredPlaylists.size) {
            visiblePlaylistLimit = minOf(visiblePlaylistLimit + 25, filteredPlaylists.size)
        }
    }

    val playlistsToRender = remember(filteredPlaylists, visiblePlaylistLimit) {
        if (filteredPlaylists.size <= initialPlaylistBatch) filteredPlaylists
        else filteredPlaylists.take(visiblePlaylistLimit)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                ),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
        ) {
            itemsIndexed(playlistsToRender, key = { index, playlist -> "${playlist.id}_$index" }) { _, playlist ->
                val rememberedOnClick = remember(playlist.id) {
                    {
                        if (isAddingToPlaylist && currentSong != null && selectedPlaylists != null) {
                            val currentSelection = selectedPlaylists[playlist.id] ?: false
                            selectedPlaylists[playlist.id] = !currentSelection
                        } else if (isSelectionMode) {
                            onPlaylistSelectionToggle(playlist)
                        } else {
                            navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                        }
                    }
                }
                val selectionIndex = remember(playlist.id, selectedPlaylistIds) {
                    if (selectedPlaylistIds.contains(playlist.id)) {
                        selectedPlaylistIds.toList().indexOf(playlist.id)
                    } else {
                        -1
                    }
                }
                PlaylistItem(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    onClick = { rememberedOnClick() },
                    isAddingToPlaylist = isAddingToPlaylist,
                    selectedPlaylists = selectedPlaylists,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPlaylistIds.contains(playlist.id),
                    selectionIndex = selectionIndex,
                    onLongPress = { onPlaylistLongPress(playlist) },
                    onPlaylistSelectionToggle = { onPlaylistSelectionToggle(playlist) }
                )
            }

            if (visiblePlaylistLimit < filteredPlaylists.size) {
                item(key = "playlists_load_more_loader") {
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
        
        val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong()) 
            bottomBarHeight + MiniPlayerHeight + 16.dp 
        else 
            bottomBarHeight + 16.dp

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            listState = listState,
            dragLabelProvider = playlistFastScrollLabelProvider
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    isAddingToPlaylist: Boolean,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int = -1,
    onLongPress: () -> Unit = {},
    onPlaylistSelectionToggle: () -> Unit = {}
) {
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val syncProgressMap by playlistViewModel.playlistSyncProgress.collectAsStateWithLifecycle()
    val syncProgress = syncProgressMap[playlist.id]
    val playlistPreviewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    // Use a one-shot load instead of a reactive Flow per-item.
    // Previously, each PlaylistItem subscribed its own reactive DB Flow via observeSongs(),
    // creating N simultaneous DB watchers for N playlists — causing ANR/freeze on large libraries.
    // Now we do a single non-reactive fetch on the IO dispatcher and cache the result.
    var playlistSongs by remember(playlistPreviewSongIds) {
        mutableStateOf<List<Song>?>(if (playlistPreviewSongIds.isEmpty()) emptyList() else null)
    }
    LaunchedEffect(playlistPreviewSongIds) {
        if (playlistPreviewSongIds.isNotEmpty()) {
            playlistSongs = playerViewModel.getSongs(playlistPreviewSongIds)
        }
    }

    val isLikedMusicHero = playlist.id == "ytm_liked_music"

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistSelectionScaleAnimation"
    )

    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "playlistSelectionBorderAnimation"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isAddingToPlaylist -> MaterialTheme.colorScheme.surfaceContainerHigh
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isLikedMusicHero -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "playlistContainerColorAnimation"
    )

    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "playlistBorderColorAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scrollReveal(initialOffsetY = 20.dp, initialScale = 0.95f)
            .scale(selectionScale)
            .then(
                if (isSelected && !isAddingToPlaylist) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = selectionBorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onPlaylistSelectionToggle()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongPress()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs ?: emptyList(),
                size = if (isLikedMusicHero) 52.dp else 48.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLikedMusicHero) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (playlist.isAiGenerated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.gemini_ai),
                            contentDescription = "AI Generated",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                val baseCount = playlist.displaySongCount ?: playlist.songIds.size
                val baseCountText = if (playlist.source == "YOUTUBE" && playlist.songIds.isEmpty() && playlist.displaySongCount == null) {
                    ""
                } else {
                    formatSongCount(baseCount)
                }

                if (syncProgress != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (baseCountText.isNotBlank()) {
                            Text(
                                text = "$baseCountText • ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Syncing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Syncing ${syncProgress.first}/${syncProgress.second}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = baseCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            val sourceLabel = when (playlist.source?.uppercase()) {
                "SPOTIFY" -> "Spotify"
                "YOUTUBE" -> "YouTube"
                "TELEGRAM", "TELEGRAM_TOPIC" -> "Telegram"
                "NETEASE" -> "Netease"
                "QQMUSIC" -> "QQ Music"
                "NAVIDROME" -> "Navidrome"
                "LOCAL", "", null -> "Local"
                else -> playlist.source?.lowercase()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() } ?: "Local"
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.height(18.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = GoogleSansRounded
                        )
                    )
                }
            }



            if (isSelected && isSelectionMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex >= 0) {
                        Text(
                            text = (selectionIndex + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isAddingToPlaylist && selectedPlaylists != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = selectedPlaylists[playlist.id] ?: false,
                    onCheckedChange = { isChecked -> selectedPlaylists[playlist.id] = isChecked }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    onGenerateClick: () -> Unit
) {
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val isYoutubeLoggedIn by playlistViewModel.youtubeLoggedInFlow.collectAsStateWithLifecycle(initialValue = false)
    var privacyStatus by remember { mutableStateOf("LOCAL") }
    var playlistName by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.create_playlist_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.create_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.create_playlist_name_placeholder)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Text(
                    text = "Visibility",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLocalSelected = privacyStatus == "LOCAL"
                    FilterChip(
                        selected = isLocalSelected,
                        onClick = { privacyStatus = "LOCAL" },
                        label = { Text("Local") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_phonef),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    if (isYoutubeLoggedIn) {
                        listOf(
                            "PRIVATE" to "Private",
                            "UNLISTED" to "Unlisted",
                            "PUBLIC" to "Public"
                        ).forEach { (status, label) ->
                            val isSelected = privacyStatus == status
                            FilterChip(
                                selected = isSelected,
                                onClick = { privacyStatus = status },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when(status) {
                                            "PRIVATE" -> Icons.Rounded.Lock
                                            "UNLISTED" -> Icons.Rounded.Link
                                            else -> Icons.Rounded.Public
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "Sign in to YT for Cloud sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                            fontFamily = GoogleSansRounded
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { onCreate(playlistName, privacyStatus) },
                        modifier = Modifier.weight(1f),
                        enabled = playlistName.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_create), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
