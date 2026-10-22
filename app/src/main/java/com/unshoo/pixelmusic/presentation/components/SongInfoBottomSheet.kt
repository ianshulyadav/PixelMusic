package com.unshoo.pixelmusic.presentation.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.subcomps.AutoSizingTextToFill
import com.unshoo.pixelmusic.utils.formatDuration
import com.unshoo.pixelmusic.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.unshoo.pixelmusic.data.ai.SongMetadata
import com.unshoo.pixelmusic.data.media.CoverArtUpdate
import com.unshoo.pixelmusic.ui.theme.MontserratFamily
import com.unshoo.pixelmusic.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import androidx.compose.material.icons.rounded.Shuffle
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.TransformOrigin
import com.unshoo.pixelmusic.presentation.screens.TabAnimation
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.utils.AudioMetaUtils
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoBottomSheet(
    song: Song,
    playerViewModel: PlayerViewModel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onDeleteFromDevice: (activity: Activity, song: Song, onResult: (Boolean) -> Unit) -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToArtistById: (String) -> Unit = { _ -> onNavigateToArtist() },
    onNavigateToGenre: () -> Unit = {},
    onEditSong: (
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit,
    generateAiMetadata: suspend (List<String>) -> Result<SongMetadata>,
    removeFromListTrigger: () -> Unit,
    isGeneratingMetadata: Boolean = false,
    aiMetadataSuccess: Boolean = false,
    aiError: String? = null,
    onRetryMetadata: () -> Unit = {},
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // Capture the HOST scope, outside ModalBottomSheet's separate window.
    // IME hide alone retains editor focus, allowing a stale input connection to
    // resume when the song-info dialog returns window focus to its caller.
    val hostFocusManager = LocalFocusManager.current
    val hostKeyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(hostFocusManager, hostKeyboardController) {
        hostFocusManager.clearFocus(force = true)
        hostKeyboardController?.hide()
        // No delayed/disposal hide: Edit and playlist actions may open an editor.
        onDispose { }
    }
    var showEditSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showAudioFormatInfoDialog by remember { mutableStateOf(false) }
    val audioMeta by songInfoViewModel.audioMeta.collectAsStateWithLifecycle()
    val notInterestedSongIds by songInfoViewModel.notInterestedSongIds.collectAsStateWithLifecycle()
    val resolvedArtists by songInfoViewModel.resolvedArtists.collectAsStateWithLifecycle()
    val isDownloaded by songInfoViewModel.isSongDownloaded.collectAsStateWithLifecycle()
    val isDownloading by songInfoViewModel.isSongDownloading.collectAsStateWithLifecycle()

    val evenCornerRadiusElems = 26.dp

    val isYouTubeSong = remember(song) {
        !song.youtubeId.isNullOrEmpty() ||
            song.contentUriString.startsWith("youtube://") ||
            song.id.startsWith("youtube_") ||
            song.isYouTube
    }
    var isDislikedState by remember(song.id, song.isDisliked) { mutableStateOf(song.isDisliked) }
    val isNotInterested = remember(song.id, song.youtubeId, notInterestedSongIds) {
        val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) {
            song.id.removePrefix("youtube_")
        } else if (song.contentUriString.startsWith("youtube://")) {
            song.contentUriString.removePrefix("youtube://")
        } else null
        song.id in notInterestedSongIds || (ytId != null && ytId in notInterestedSongIds)
    }

    LaunchedEffect(isFavorite) {
        if (isFavorite && isDislikedState) {
            isDislikedState = false
        }
    }

    val listItemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
            smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
        )
    }
    val albumArtShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }
    val playButtonShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )

    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }
    val infoSegmentContainerShape = remember {
        RoundedCornerShape(20.dp)
    }
    val infoSegmentItemShape = remember {
        RoundedCornerShape(8.dp)
    }

    val songLocationInfo by songInfoViewModel.songLocationInfo.collectAsStateWithLifecycle()
    val isResolvingAudioMeta by songInfoViewModel.isResolvingAudioMeta.collectAsStateWithLifecycle()

    val audioMetaLabel = remember(audioMeta) {
        val meta = audioMeta ?: return@remember null
        val format = AudioMetaUtils.mimeTypeToFormat(meta.mimeType).takeIf { it != "-" }?.uppercase()
        val parts = buildList {
            meta.formatLabel?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            meta.sampleRate?.takeIf { it > 0 }?.let { add(String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)) }
            meta.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
            meta.bitrate?.takeIf { it > 0 }?.let { add("${java.text.NumberFormat.getIntegerInstance().format(it / 1000)} kbps") }
            if (format != null && meta.formatLabel?.contains(format, true) != true) add(format)
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    LaunchedEffect(song.id) {
        songInfoViewModel.loadAudioMeta(song)
        songInfoViewModel.loadArtistsForSong(song)
        songInfoViewModel.loadDownloadState(song)
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val currentColorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = {
            if (showEditSheet) {
                showEditSheet = false
            } else if (showArtistPicker) {
                showArtistPicker = false
            } else if (showAudioFormatInfoDialog) {
                showAudioFormatInfoDialog = false
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = currentColorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        MaterialTheme(
            colorScheme = currentColorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            // AQUÍ APLICAMOS EL FIX: Anulamos la fábrica de overscroll para todo lo que esté aquí adentro
            CompositionLocalProvider(
                LocalOverscrollFactory provides null
            ) {
            val view = LocalView.current
            DisposableEffect(view) {
                var parent = view.parent
                while (parent != null) {
                    if (parent is DialogWindowProvider) {
                        val window = parent.window
                        // This window contains actions/info, never a text editor.
                        // Keep it focusable (Back/accessibility still work), but
                        // prevent it from becoming an IME connection target.
                        // EditSongSheet owns a separate, unrestricted window.
                        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        break
                    }
                    parent = parent.parent
                }
                // This flag belongs to the disposable modal window. Do not clear
                // it during teardown and briefly reconnect it to the IME.
                onDispose { }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        // Fila para la carátula del álbum y el título (Always visible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = stringResource(R.string.widget_album_art),
                                shape = albumArtShape,
                                modifier = Modifier.size(80.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                AutoSizingTextToFill(
                                    modifier = Modifier.padding(end = 4.dp),
                                    fontWeight = FontWeight.Light,
                                    maxFontSizeLimit = 28.sp,
                                    text = song.title
                                )
                            }
                            FilledTonalIconButton(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 6.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                onClick = { showEditSheet = true },
                            ) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_song_metadata)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Swipeable Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = tween(durationMillis = 280),
                                alignment = Alignment.TopCenter
                            )
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> { // Options / Actions
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item {
                                            FilledTonalButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 66.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = CircleShape,
                                                onClick = {
                                                    onDismiss()
                                                    playerViewModel.startMixFromSong(song)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Shuffle,
                                                    contentDescription = stringResource(R.string.player_start_mix_from_this),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = stringResource(R.string.player_start_mix_from_this),
                                                    fontFamily = GoogleSansRounded,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                MediumExtendedFloatingActionButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .fillMaxHeight(),
                                                    onClick = onPlaySong,
                                                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                                    shape = playButtonShape,
                                                    icon = {
                                                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_song_action))
                                                    },
                                                    text = {
                                                        Text(
                                                            modifier = Modifier.padding(end = 10.dp),
                                                            text = stringResource(R.string.play_playback)
                                                        )
                                                    }
                                                )

                                                FilledIconButton(
                                                    modifier = Modifier
                                                        .weight(0.25f)
                                                        .fillMaxHeight(),
                                                    onClick = {
                                                        val targetFavoriteState = !isFavorite
                                                        onToggleFavorite()
                                                        if (targetFavoriteState && isDislikedState) {
                                                            isDislikedState = false
                                                            songInfoViewModel.dislikeOnYouTube(song, false) {}
                                                        }
                                                        if (isYouTubeSong && songInfoViewModel.isLoggedIn()) {
                                                            songInfoViewModel.likeOnYouTube(song, targetFavoriteState) { success ->
                                                                if (success) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        if (targetFavoriteState) "Added to YouTube Liked Songs" else "Removed from YouTube Liked Songs",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    shape = favoriteButtonShape,
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = favoriteButtonContainerColor,
                                                        contentColor = favoriteButtonContentColor
                                                    )
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = stringResource(
                                                            if (isFavorite) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
                                                        )
                                                    )
                                                }

                                                FilledTonalIconButton(
                                                    modifier = Modifier
                                                        .weight(0.25f)
                                                        .fillMaxHeight(),
                                                    onClick = {
                                                        try {
                                                            val isYtSong = !song.youtubeId.isNullOrEmpty()
                                                            val link = if (isYtSong) {
                                                                "https://music.youtube.com/watch?v=${song.youtubeId}"
                                                            } else {
                                                                "https://github.com/ianshulyadav/PixelMusic"
                                                            }
                                                            
                                                            // Copy to clipboard
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            val clip = ClipData.newPlainText(
                                                                if (isYtSong) "YouTube Music Link" else "GitHub Link",
                                                                link
                                                            )
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(
                                                                context,
                                                                if (isYtSong) "YouTube Music link copied to clipboard" else "App link copied to clipboard",
                                                                Toast.LENGTH_SHORT
                                                            ).show()

                                                            // Share the text link
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.displayArtist}\n🎵 $link")
                                                            }
                                                            context.startActivity(
                                                                Intent.createChooser(
                                                                    shareIntent,
                                                                    context.getString(R.string.song_info_share_chooser_title)
                                                                )
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.error_share_song_format, e.localizedMessage ?: ""),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    },
                                                    shape = CircleShape
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                                        imageVector = Icons.Rounded.Share,
                                                        contentDescription = stringResource(R.string.cd_share_song_file)
                                                    )
                                                }
                                            }
                                        }
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.6f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                                    shape = CircleShape,
                                                    onClick = onAddToQueue
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                                        contentDescription = stringResource(R.string.cd_add_to_queue)
                                                    )
                                                    Spacer(Modifier.width(14.dp))
                                                    Text(
                                                        stringResource(R.string.action_add_to_queue),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.4f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                                    shape = CircleShape,
                                                    onClick = onAddNextToQueue
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.QueueMusic,
                                                        contentDescription = stringResource(R.string.cd_play_next_in_queue)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        stringResource(R.string.action_queue_next),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    ),
                                                    shape = CircleShape,
                                                    onClick = onAddToPlayList
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                        contentDescription = stringResource(R.string.cd_add_to_playlist)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        stringResource(R.string.shortcut_playlist_short),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    shape = CircleShape,
                                                    onClick = {
                                                        (context as? Activity)?.let { activity ->
                                                            onDeleteFromDevice(activity, song) { result ->
                                                                if (result) {
                                                                    removeFromListTrigger()
                                                                    onDismiss()
                                                                }
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteForever,
                                                        contentDescription = stringResource(R.string.delete_action)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        stringResource(R.string.delete_action),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        if (song.youtubeId != null || song.telegramFileId != null) {
                                            item {
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = when {
                                                            isDownloaded -> MaterialTheme.colorScheme.primaryContainer
                                                            isDownloading -> MaterialTheme.colorScheme.secondaryContainer
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        contentColor = when {
                                                            isDownloaded -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            isDownloading -> MaterialTheme.colorScheme.onSecondaryContainer
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    ),
                                                    shape = CircleShape,
                                                    onClick = {
                                                        when {
                                                            isDownloaded -> {
                                                                songInfoViewModel.deleteYoutubeSong(song)
                                                                Toast.makeText(context, context.getString(R.string.toast_deleted_from_offline_cache), Toast.LENGTH_SHORT).show()
                                                            }
                                                            isDownloading -> {
                                                                songInfoViewModel.cancelYoutubeSongDownload(song)
                                                                Toast.makeText(context, context.getString(R.string.toast_download_cancelled), Toast.LENGTH_SHORT).show()
                                                            }
                                                            else -> {
                                                                if (song.telegramFileId != null && song.youtubeId == null) {
                                                                    songInfoViewModel.downloadTelegramSong(song)
                                                                } else {
                                                                    songInfoViewModel.downloadYoutubeSong(song)
                                                                }
                                                                Toast.makeText(context, context.getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    when {
                                                        isDownloaded -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.CheckCircle,
                                                                contentDescription = stringResource(R.string.song_info_available_offline)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                stringResource(R.string.song_info_available_offline),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        isDownloading -> {
                                                            LoadingIndicator(modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(
                                                                stringResource(R.string.song_info_downloading),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        else -> {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Download,
                                                                contentDescription = stringResource(R.string.song_info_download_for_offline)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                stringResource(R.string.song_info_download_for_offline),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (isYouTubeSong) {
                                                        FilledTonalButton(
                                                            modifier = Modifier
                                                                .weight(0.5f)
                                                                .heightIn(min = 66.dp),
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = if (isDislikedState) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                                contentColor = if (isDislikedState) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                            ),
                                                            shape = CircleShape,
                                                            onClick = {
                                                                val targetState = !isDislikedState
                                                                songInfoViewModel.dislikeOnYouTube(song, targetState) { ytSuccess ->
                                                                    isDislikedState = targetState
                                                                    if (targetState) {
                                                                        if (isFavorite) {
                                                                            onToggleFavorite()
                                                                        }
                                                                        val msg = if (songInfoViewModel.isLoggedIn()) {
                                                                            if (ytSuccess) {
                                                                                "Disliked on YouTube"
                                                                            } else {
                                                                                "Disliked locally (YouTube sync failed)"
                                                                            }
                                                                        } else {
                                                                            "Disliked locally"
                                                                        }
                                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        Toast.makeText(context, context.getString(R.string.toast_removed_dislike), Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isDislikedState) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                                                contentDescription = stringResource(if (isDislikedState) R.string.action_disliked else R.string.action_dislike)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = stringResource(if (isDislikedState) R.string.action_disliked else R.string.action_dislike),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    FilledTonalButton(
                                                        modifier = Modifier
                                                            .weight(if (isYouTubeSong) 0.5f else 1f)
                                                            .heightIn(min = 66.dp),
                                                        colors = ButtonDefaults.filledTonalButtonColors(
                                                            containerColor = if (isNotInterested) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                            contentColor = if (isNotInterested) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        shape = CircleShape,
                                                        onClick = {
                                                            val targetState = !isNotInterested
                                                            songInfoViewModel.setNotInterested(song, targetState) { success ->
                                                                if (success) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        context.getString(if (targetState) R.string.toast_tuned_out_added else R.string.toast_tuned_out_removed),
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Block,
                                                            contentDescription = stringResource(if (isNotInterested) R.string.marked_not_interested else R.string.not_interested)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = if (isNotInterested) stringResource(R.string.marked_not_interested) else stringResource(R.string.not_interested),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                          }

                                        item {
                                            Spacer(Modifier.navigationBarsPadding().height(80.dp))
                                        }
                                    }
                                }
                                1 -> { // Details / Info
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(infoSegmentContainerShape),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_info_label_duration),
                                                    supporting = formatDuration(song.duration),
                                                    icon = Icons.Rounded.Schedule,
                                                    iconDescription = stringResource(R.string.cd_duration_icon),
                                                    shape = infoSegmentItemShape,
                                                )

                                                if (!song.genre.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_field_genre),
                                                        supporting = song.genre,
                                                        icon = Icons.Rounded.MusicNote,
                                                        iconDescription = stringResource(R.string.cd_genre_icon),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                }

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_album),
                                                    supporting = song.album,
                                                    icon = Icons.Rounded.Album,
                                                    iconDescription = stringResource(R.string.cd_album_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = onNavigateToAlbum,
                                                )

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_artist),
                                                    supporting = song.displayArtist,
                                                    icon = Icons.Rounded.Person,
                                                    iconDescription = stringResource(R.string.cd_artist_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = {
                                                        if (song.artists.size > 1) {
                                                            showArtistPicker = true
                                                        } else {
                                                            val primary = song.primaryArtist
                                                            onNavigateToArtistById(primary.channelId ?: primary.id.toString())
                                                        }
                                                    },
                                                )

                                                if (!audioMetaLabel.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_info_label_song_metadata),
                                                        supporting = audioMetaLabel,
                                                        icon = Icons.Rounded.Info,
                                                        iconDescription = stringResource(R.string.cd_audio_format_icon),
                                                        shape = infoSegmentItemShape,
                                                        onClick = { showAudioFormatInfoDialog = true },
                                                    )
                                                } else if (isResolvingAudioMeta) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_info_label_song_metadata),
                                                        supporting = "Resolving audio format...",
                                                        icon = Icons.Rounded.Info,
                                                        iconDescription = stringResource(R.string.cd_audio_format_icon),
                                                        shape = infoSegmentItemShape,
                                                        onClick = { showAudioFormatInfoDialog = true },
                                                    )
                                                }

                                                val providerText = songLocationInfo.provider
                                                if (!providerText.isNullOrBlank()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = "Provider",
                                                        supporting = providerText,
                                                        icon = Icons.Rounded.Cloud,
                                                        iconDescription = stringResource(R.string.cd_provider_icon),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                }

                                                val filePathText = songLocationInfo.filePath
                                                if (!filePathText.isNullOrBlank()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = "Path",
                                                        supporting = filePathText,
                                                        icon = Icons.Rounded.AudioFile,
                                                        iconDescription = stringResource(R.string.cd_file_icon),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                } else if (providerText.isNullOrBlank()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = songLocationInfo.label,
                                                        supporting = songLocationInfo.value,
                                                        icon = if (songLocationInfo.isCloud) Icons.Rounded.Cloud else Icons.Rounded.AudioFile,
                                                        iconDescription = stringResource(
                                                            if (songLocationInfo.isCloud) {
                                                                R.string.cd_provider_icon
                                                            } else {
                                                                R.string.cd_file_icon
                                                            }
                                                        ),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                }
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.navigationBarsPadding().height(80.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Tab Bar
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(5.dp),
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    TabAnimation(
                        index = 0,
                        title = stringResource(R.string.song_info_tab_options),
                        selectedIndex = pagerState.currentPage,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_options),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.song_info_tab_options_badge),
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TabAnimation(
                        index = 1,
                        title = stringResource(R.string.song_info_tab_details),
                        selectedIndex = pagerState.currentPage,
                        onClick = {
                            scope.launch {
                                songInfoViewModel.loadAudioMeta(song)
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.cd_details_tab),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.song_info_tab_info_badge),
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        }
    }

    EditSongSheet(
        visible = showEditSheet,
        song = song,
        onDismiss = { showEditSheet = false },
        onSave = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArt ->
            onEditSong(
                title,
                artist,
                album,
                albumArtist,
                composer,
                genre,
                lyrics,
                trackNumber,
                discNumber,
                replayGainTrackGainDb,
                replayGainAlbumGainDb,
                coverArt
            )
            showEditSheet = false
        },
    )

    if (showAudioFormatInfoDialog) {
        AudioFormatInfoDialog(
            song = song,
            mimeType = audioMeta?.mimeType ?: song.mimeType,
            bitrate = audioMeta?.bitrate ?: song.bitrate,
            sampleRate = audioMeta?.sampleRate ?: song.sampleRate,
            bitDepth = audioMeta?.bitDepth,
            formatTag = audioMeta?.formatLabel,
            provider = songLocationInfo.provider,
            filePath = songLocationInfo.filePath ?: song.path.takeIf { !it.isNullOrBlank() } ?: if (song.isLocal) song.contentUriString else null,
            onDismiss = { showAudioFormatInfoDialog = false }
        )
    }

    val artistPickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && resolvedArtists.isNotEmpty()) {
        com.unshoo.pixelmusic.presentation.components.player.PlayerArtistPickerBottomSheet(
            song = song,
            artists = resolvedArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                                showArtistPicker = false
                                onNavigateToArtistById(artist.channelId ?: artist.id.toString())
                            }
        )
    }
}

@Composable
private fun SongInfoSegmentedListItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    iconDescription: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .let { baseModifier ->
            if (onClick != null) {
                baseModifier.clickable(onClick = onClick)
            } else {
                baseModifier
            }
        }

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(headline) },
            supportingContent = { Text(supporting) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                )
            }
        )
    }
}
