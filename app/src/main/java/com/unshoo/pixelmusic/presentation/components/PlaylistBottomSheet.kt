package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.isSmartPlaylist
import com.unshoo.pixelmusic.presentation.components.subcomps.LibraryActionRow
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistUiState
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.ui.theme.RoundedSans
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistBottomSheet(
    playlistUiState: PlaylistUiState,
    songs: ImmutableList<Song>,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    currentPlaylistId: String? = null
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var searchQuery by remember { mutableStateOf("") }
    val editablePlaylists = remember(playlistUiState.playlists) {
        playlistUiState.playlists.filterNot { it.isSmartPlaylist }.toImmutableList()
    }
    val filteredPlaylists = remember(searchQuery, editablePlaylists) {
        if (searchQuery.isBlank()) editablePlaylists
        else editablePlaylists.filter { it.name.contains(searchQuery, true) }.toImmutableList()
    }
    val selectedPlaylists = remember {
        mutableStateMapOf<String, Boolean>().apply {
            if (songs.size == 1) {
                val songId = songs.first().id
                filteredPlaylists.forEach {
                    put(it.id, it.songIds.contains(songId))
                }
            } else {
                filteredPlaylists.forEach {
                    put(it.id, false)
                }
            }
        }
    }

    val isAnyPlaylistSelected = selectedPlaylists.values.any { it }

    val alpha by animateFloatAsState(
        targetValue = if (isAnyPlaylistSelected) 1f else 0.4f,
        label = "fab_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val playlistCreatedToast = stringResource(R.string.playlist_created_songs_added_toast)
            val songsAddedToast = stringResource(R.string.playlist_songs_added_toast)
            val savedToast = stringResource(R.string.playlist_saved_toast)

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (songs.size > 1) {
                            stringResource(R.string.playlist_picker_add_songs_title, songs.size)
                        } else {
                            stringResource(R.string.playlist_picker_select_playlists)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        unfocusedTrailingIconColor = Color.Transparent,
                        focusedSupportingTextColor = Color.Transparent,
                    ),
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_playlists_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = {
                            searchQuery = ""
                        }) { Icon(Icons.Filled.Clear, null) }
                    }
                )




                LibraryActionRow(
                    modifier = Modifier.padding(
                        top = 10.dp,
                        start = 10.dp,
                        end = 10.dp
                    ),
                    onMainActionClick = {
                        showCreatePlaylistDialog = true
                    },
                    iconRotation = 0f,
                    showSortButton = false,
                    showImportButton = false,
                    onSortClick = { },
                    isPlaylistTab = true,
                    isFoldersTab = false,
                    currentFolder = null,
                    folderRootPath = "",
                    folderRootLabel = "Internal Storage",
                    onFolderClick = { },
                    onNavigateBack = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlaylistContainer(
                    playlistUiState = playlistUiState,
                    isRefreshing = false,
                    onRefresh = { },
                    bottomBarHeight = bottomBarHeight,
                    navController = null,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    currentSong = songs.firstOrNull() ?: Song.emptySong(),
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists
                )

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialogRedesigned(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name ->
                            playlistViewModel.createPlaylist(name, songIds = songs.map { it.id })
                            showCreatePlaylistDialog = false
                            onDismiss()
                            playerViewModel.sendToast(playlistCreatedToast)
                        }
                    )
                }
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                    },
                shape = CircleShape,
                onClick = {
                    if (!isAnyPlaylistSelected) return@MediumExtendedFloatingActionButton

                    if (songs.size == 1) {
                         playlistViewModel.addOrRemoveSongFromPlaylists(
                            songs.first().id,
                            selectedPlaylists.filter { it.value }.keys.toList(),
                            currentPlaylistId
                        )
                    } else {
                         val selectedPlaylistIds = selectedPlaylists.filter { it.value }.keys.toList()
                         if (selectedPlaylistIds.isNotEmpty()) {
                             playlistViewModel.addSongsToPlaylists(
                                 songs.map { it.id },
                                 selectedPlaylistIds
                             )
                         }
                    }
                    onDismiss()
                    playerViewModel.sendToast(if (songs.size > 1) songsAddedToast else savedToast)
                    playerViewModel.multiSelectionStateHolder.clearSelection()
                },
                icon = { Icon(Icons.Rounded.Save, stringResource(R.string.action_save)) },
                text = { Text(stringResource(if (songs.size > 1) R.string.song_picker_action_add else R.string.action_save)) },
            )
        }
    }
}
