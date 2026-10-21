package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageCardTargetSize
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo

data class MoodPlaylistSection(
    val title: String,
    val playlists: List<PlaylistItem>
)

data class MoodAlbumSection(
    val title: String,
    val albums: List<AlbumItem>
)

/**
 * 100% Musify-style category and featured playlists detail page.
 * Completely replaces the search bar upon interaction, loading authentic YouTube Music
 * playlists, hero cover images, and popular tracks with fluid 120Hz physics.
 */
@Composable
fun MoodGenreContentView(
    moodGenre: MoodGenreItem,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    bottomBarHeight: Dp,
    onBack: () -> Unit,
    onSongMoreOptionsClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current

    var isLoading by remember(moodGenre.id) { mutableStateOf(true) }
    var playlistSections by remember(moodGenre.id) { mutableStateOf<List<MoodPlaylistSection>>(emptyList()) }
    var albumSections by remember(moodGenre.id) { mutableStateOf<List<MoodAlbumSection>>(emptyList()) }
    var songsList by remember(moodGenre.id) { mutableStateOf<List<Song>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isTopAppBarVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    val artworkShape = remember { RoundedCornerShape(18.dp) }
    val backgroundColor = MaterialTheme.colorScheme.background

    val topTint = if (isDark) moodGenre.darkCardColor else moodGenre.lightCardColor
    val gradientBrush = remember(topTint, backgroundColor, isDark) {
        Brush.verticalGradient(
            colors = listOf(
                topTint.copy(alpha = if (isDark) 0.90f else 0.85f),
                topTint.copy(alpha = if (isDark) 0.50f else 0.40f),
                backgroundColor
            )
        )
    }

    // Authentic YouTube Music Data Fetching
    LaunchedEffect(moodGenre.id, moodGenre.params) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch authenticated YouTube browse endpoint
                val browseResult = if (!moodGenre.params.isNullOrBlank()) {
                    YouTube.browse(moodGenre.browseId, moodGenre.params).getOrNull()
                } else null

                val sections = mutableListOf<MoodPlaylistSection>()
                val albumSecs = mutableListOf<MoodAlbumSection>()
                var resolvedSongs = listOf<Song>()

                if (browseResult != null) {
                    // Parse playlists & albums grouped by YouTube shelves/sections
                    browseResult.items.forEach { sectionItem ->
                        val playlists = sectionItem.items.filterIsInstance<PlaylistItem>()
                        if (playlists.isNotEmpty()) {
                            val title = sectionItem.title ?: "Featured Playlists"
                            sections.add(MoodPlaylistSection(title, playlists))
                        }
                        val albums = sectionItem.items.filterIsInstance<AlbumItem>()
                        if (albums.isNotEmpty()) {
                            val title = sectionItem.title ?: "Popular Albums"
                            albumSecs.add(MoodAlbumSection(title, albums))
                        }
                    }

                    // Parse popular songs
                    resolvedSongs = browseResult.items.flatMap { it.items }
                        .filterIsInstance<SongItem>()
                        .filterVideo(true)
                        .map { it.toNativeSong() }
                }

                // Fallback to searching YouTube for playlists if empty
                val searchQuery = moodGenre.title.replace("&", "and")
                if (sections.isEmpty()) {
                    val featuredResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                    val communityResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                    val fallbackPlaylists = (featuredResult?.items.orEmpty() + communityResult?.items.orEmpty())
                        .filterIsInstance<PlaylistItem>()
                        .distinctBy { it.id }

                    if (fallbackPlaylists.isNotEmpty()) {
                        sections.add(MoodPlaylistSection("Featured Playlists & Mixes", fallbackPlaylists))
                    }
                }

                // Fetch Category Albums from YouTube if not present in browse
                if (albumSecs.isEmpty()) {
                    val albumsResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                    val fetchedAlbums = albumsResult?.items?.filterIsInstance<AlbumItem>()?.distinctBy { it.id }.orEmpty()
                    if (fetchedAlbums.isNotEmpty()) {
                        albumSecs.add(MoodAlbumSection("Albums & Releases", fetchedAlbums.take(15)))
                    }
                }

                // Fallback to searching YouTube for songs if empty
                if (resolvedSongs.isEmpty()) {
                    val songsResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    resolvedSongs = songsResult?.items
                        ?.filterIsInstance<SongItem>()
                        ?.filterVideo(true)
                        ?.map { it.toNativeSong() }
                        .orEmpty()
                }

                withContext(Dispatchers.Main) {
                    playlistSections = sections
                    albumSections = albumSecs
                    songsList = resolvedSongs
                    isLoading = false

                    // Cache real featured playlist thumbnails for the browse card
                    val allPlaylists = sections.flatMap { it.playlists }
                    if (allPlaylists.isNotEmpty()) {
                        val p1 = allPlaylists.getOrNull(0)?.thumbnail
                        val p2 = allPlaylists.getOrNull(1)?.thumbnail ?: p1
                        if (!p1.isNullOrBlank()) {
                            CategoryPlaylistImageCache.put(moodGenre.title, p1, p2)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleTextColor = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val uniqueSongsList = remember(songsList) { songsList.distinctBy { it.id } }

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = bottomBarHeight + MiniPlayerHeight + navBarInset + 36.dp
            )
        ) {
            // Musify / Spotify Hero Header Item
            item(key = "hero_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradientBrush)
                        .padding(start = 20.dp, end = 20.dp, top = statusBarTopInset + 8.dp, bottom = 22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top back button & category pill row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledIconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isDark) Color.Black.copy(alpha = 0.40f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = if (moodGenre.category == MoodGenreCategory.MOOD_MOMENT) "Mood & Moment" else "Genre",
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = GoogleSansRounded,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Category Title
                        Text(
                            text = moodGenre.title,
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = headerTextColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Subtitle info
                        val totalPlaylists = playlistSections.sumOf { it.playlists.size }
                        val playlistLabel = if (totalPlaylists > 0) "$totalPlaylists playlists" else "Featured playlists"
                        val totalAlbums = albumSections.sumOf { it.albums.size }
                        val albumLabel = if (totalAlbums > 0) "$totalAlbums albums" else null
                        val tracksLabel = if (songsList.isNotEmpty()) "${songsList.size} popular songs" else "Curated tracks"
                        val subtitleText = listOfNotNull("YouTube Music", playlistLabel, albumLabel, tracksLabel).joinToString(" • ")

                        Text(
                            text = subtitleText,
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                color = subtitleTextColor
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Play / Shuffle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (songsList.isNotEmpty()) {
                                FilledIconButton(
                                    onClick = {
                                        playerViewModel.showAndPlaySong(
                                            songsList.first(),
                                            songsList,
                                            "${moodGenre.title} Mix"
                                        )
                                    },
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play ${moodGenre.title}",
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        val shuffled = songsList.shuffled()
                                        if (shuffled.isNotEmpty()) {
                                            playerViewModel.showAndPlaySong(
                                                shuffled.first(),
                                                shuffled,
                                                "${moodGenre.title} Shuffle"
                                            )
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shuffle",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading state
            if (isLoading) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                // Playlists Shelves from YouTube Music
                playlistSections.forEachIndexed { sIndex, section ->
                    item(key = "shelf_header_${sIndex}_${section.title}") {
                        Text(
                            text = section.title,
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    item(key = "shelf_row_${sIndex}_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = section.playlists,
                                key = { "pl_${it.id}_$sIndex" },
                                contentType = { "mood_playlist_card" }
                            ) { playlist ->
                                FeaturedPlaylistCard(
                                    title = playlist.title,
                                    subtitle = playlist.author?.name ?: "YouTube Music",
                                    thumbnailUrl = playlist.thumbnail,
                                    onClick = {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                    }
                                )
                            }
                        }
                    }
                }

                // Album Shelves from YouTube Music
                albumSections.forEachIndexed { aIndex, section ->
                    item(key = "album_shelf_header_${aIndex}_${section.title}") {
                        Text(
                            text = section.title,
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    item(key = "album_shelf_row_${aIndex}_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = section.albums,
                                key = { "album_${it.id}_$aIndex" },
                                contentType = { "mood_album_card" }
                            ) { album ->
                                val artistName = album.artists?.firstOrNull()?.name
                                    ?: album.year?.toString()
                                    ?: "Album"
                                FeaturedPlaylistCard(
                                    title = album.title,
                                    subtitle = artistName,
                                    thumbnailUrl = album.thumbnail,
                                    onClick = {
                                        navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId.ifBlank { album.id }))
                                    }
                                )
                            }
                        }
                    }
                }

                // Popular Songs Section
                if (uniqueSongsList.isNotEmpty()) {
                    item(key = "songs_header") {
                        Text(
                            text = "Popular in ${moodGenre.title}",
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(
                        items = uniqueSongsList,
                        key = { song -> "mood_song_${song.id}" },
                        contentType = { "mood_song" }
                    ) { song ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 3.dp)
                        ) {
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = isPlaying,
                                isCurrentSong = currentPlayingSongId == song.id,
                                onMoreOptionsClick = onSongMoreOptionsClick,
                                onClick = {
                                    playerViewModel.showAndPlaySong(
                                        song,
                                        songsList,
                                        "${moodGenre.title} Mix"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sticky Detail Screen Top App Bar
        AnimatedVisibility(
            visible = isTopAppBarVisible,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) +
                slideInVertically(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) { -it },
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) +
                slideOutVertically(animationSpec = androidx.compose.animation.core.tween(140)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(58.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = moodGenre.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
