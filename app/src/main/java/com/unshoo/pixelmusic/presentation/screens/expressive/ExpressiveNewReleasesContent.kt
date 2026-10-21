package com.unshoo.pixelmusic.presentation.screens.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.feed.YouTubePlaylistResult
import com.unshoo.pixelmusic.data.feed.toSong
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.AlbumArtQuality
import com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToQuality
import com.unshoo.pixelmusic.utils.ContentFilterUtils
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem

enum class NewReleasesFilter(val label: String) {
    ALL("All"),
    ALBUMS("Albums"),
    SINGLES("Singles"),
    MIXES("Mixes")
}

private data class AlbumCardItem(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveNewReleasesContent(
    playlist: YouTubePlaylistResult,
    newReleaseAlbums: List<AlbumItem>,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    onBackClick: () -> Unit,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.ORIGINAL,
    filterKeywords: List<String> = emptyList(),
    onPlayAlbumsClick: () -> Unit = {},
    onShuffleAlbumsClick: () -> Unit = {},
    isLoadingMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current
    var selectedFilter by remember { mutableStateOf(NewReleasesFilter.ALL) }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = navBarInset + MiniPlayerHeight + 24.dp

    val songs: List<Song> = remember(playlist.tracks, filterKeywords) {
        playlist.tracks
            .filterNot { ContentFilterUtils.matchesFilterKeywords(it.title, it.artist, it.album, filterKeywords) }
            .map { it.toSong() }
    }

    val filteredAlbums: List<AlbumItem> = remember(newReleaseAlbums, filterKeywords) {
        newReleaseAlbums.filterNot {
            ContentFilterUtils.matchesFilterKeywords(it.title, it.artists?.firstOrNull()?.name, null, filterKeywords)
        }
    }

    // Hero items for the 2 large top cards
    val heroItems = remember(songs, filteredAlbums, albumArtQuality) {
        if (songs.size >= 2) {
            listOf(
                Triple(songs[0].title, songs[0].artist, upgradeThumbnailUrlToQuality(songs[0].albumArtUriString, albumArtQuality)),
                Triple(songs[1].title, "Mix • YouTube Music", upgradeThumbnailUrlToQuality(songs[1].albumArtUriString, albumArtQuality))
            )
        } else if (filteredAlbums.size >= 2) {
            listOf(
                Triple(filteredAlbums[0].title, filteredAlbums[0].artists?.firstOrNull()?.name ?: "Artist", upgradeThumbnailUrlToQuality(filteredAlbums[0].thumbnail, albumArtQuality)),
                Triple(filteredAlbums[1].title, "Mix • YouTube Music", upgradeThumbnailUrlToQuality(filteredAlbums[1].thumbnail, albumArtQuality))
            )
        } else {
            emptyList()
        }
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentPlayingId = stablePlayerState.currentSong?.id

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = statusBarInset + 8.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Bar with back arrow and "New releases" title
        item(key = "new_releases_top_bar", contentType = "top_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "New releases",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }


        // 3. Hero Featured Releases (2 large portrait cards side by side)
        if (heroItems.isNotEmpty()) {
            item(key = "new_releases_hero_cards", contentType = "hero_cards") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    heroItems.forEachIndexed { index, (title, subtitle, artwork) ->
                        val cardShape = remember { AbsoluteSmoothCornerShape(22.dp, 60) }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(220.dp)
                                .bounceClick(scaleDown = 0.96f) {
                                    if (songs.isNotEmpty()) {
                                        playerViewModel.playSongs(songs, songs[index % songs.size], queueName = "New Releases")
                                    }
                                },
                            shape = cardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 0.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SmartImage(
                                    model = artwork,
                                    contentDescription = title,
                                    shape = cardShape,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Transparent,
                                                    0.35f to Color.Transparent,
                                                    0.65f to Color.Black.copy(alpha = 0.55f),
                                                    1.0f to Color.Black.copy(alpha = 0.90f)
                                                )
                                            )
                                        )
                                )

                                if (index == 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 14.dp, bottom = 40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "RELEASED",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.82f),
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

        // 4. Filter Pills: All | Albums | Singles | Mixes
        item(key = "new_releases_filter_pills", contentType = "filter_pills") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NewReleasesFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val pillBgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_bg_filter"
                    )
                    val pillTextColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_text_filter"
                    )

                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = CircleShape,
                        color = pillBgColor,
                        shadowElevation = 0.dp,
                        modifier = Modifier.bounceClick(scaleDown = 0.95f) { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter.label,
                            fontFamily = GoogleSansRounded,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = pillTextColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 5. Content based on selectedFilter: All | Albums | Singles | Mixes
        if (selectedFilter == NewReleasesFilter.ALL || selectedFilter == NewReleasesFilter.ALBUMS) {
            item(key = "new_releases_albums_section", contentType = "albums_section") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedFilter == NewReleasesFilter.ALBUMS) "All Albums & Singles" else "Albums & singles",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle Button (shuffles first 15 albums)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        onShuffleAlbumsClick()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle first 15 albums",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Play All Button (plays mix of first 10-15 albums)
                            FilledIconButton(
                                onClick = onPlayAlbumsClick,
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play all albums & singles mix",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val albumCards: List<AlbumCardItem> = if (filteredAlbums.isNotEmpty()) {
                            filteredAlbums.map {
                                AlbumCardItem(
                                    id = it.browseId,
                                    title = it.title,
                                    artist = it.artists?.firstOrNull()?.name ?: "Artist",
                                    artwork = upgradeThumbnailUrlToQuality(it.thumbnail, albumArtQuality)
                                )
                            }
                        } else {
                            songs.take(15).map {
                                AlbumCardItem(
                                    id = it.id,
                                    title = it.title,
                                    artist = it.artist,
                                    artwork = upgradeThumbnailUrlToQuality(it.albumArtUriString, albumArtQuality)
                                )
                            }
                        }

                        items(albumCards, key = { "album_${it.id}" }, contentType = { "album_card" }) { item ->
                            Column(
                                modifier = Modifier
                                    .width(135.dp)
                                    .bounceClick(scaleDown = 0.96f) {
                                        if (item.id.startsWith("MPREb_") || item.id.startsWith("OLAK") || item.id.startsWith("FEmusic_album")) {
                                            navController.navigateSafely(Screen.AlbumDetail.createRoute(item.id))
                                        } else {
                                            val songMatch = songs.firstOrNull { it.id == item.id }
                                            if (songMatch != null) {
                                                playerViewModel.playSongs(songs, songMatch, queueName = "Albums & singles")
                                            }
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val albumShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }
                                Box(
                                    modifier = Modifier
                                        .size(135.dp)
                                        .clip(albumShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    SmartImage(
                                        model = item.artwork,
                                        contentDescription = item.title,
                                        shape = albumShape,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Text(
                                    text = item.title,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "Album • ${item.artist}",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. "More to explore" Section
        if (selectedFilter == NewReleasesFilter.ALL || selectedFilter == NewReleasesFilter.SINGLES || selectedFilter == NewReleasesFilter.MIXES) {
            item(key = "new_releases_more_to_explore_section", contentType = "more_to_explore_section") {
                val exploreTracks = remember(songs, selectedFilter) {
                    when (selectedFilter) {
                        NewReleasesFilter.ALL -> songs.drop(if (heroItems.isNotEmpty()) 2 else 0)
                        NewReleasesFilter.SINGLES -> songs
                        NewReleasesFilter.MIXES -> songs.filter { it.title.contains("mix", ignoreCase = true) || it.artist.contains("mix", ignoreCase = true) }.ifEmpty { songs }
                        else -> songs
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedFilter) {
                                NewReleasesFilter.SINGLES -> "Popular Singles & Tracks"
                                NewReleasesFilter.MIXES -> "Mixes & Radio Drops"
                                else -> "More to explore"
                            },
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle Songs List Button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        if (exploreTracks.isNotEmpty()) {
                                            playerViewModel.playSongsShuffled(exploreTracks, queueName = "More to explore")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle songs list",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Play All Songs List Button
                            FilledIconButton(
                                onClick = {
                                    if (exploreTracks.isNotEmpty()) {
                                        playerViewModel.playSongs(exploreTracks, exploreTracks.first(), queueName = "More to explore")
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play all songs list",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    exploreTracks.forEach { song ->
                        val pillShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }
                        val currentSong = stablePlayerState.currentSong
                        val isCurrentPlaying = stablePlayerState.isPlaying && currentSong != null && (
                            currentSong.id == song.id ||
                            (song.youtubeId != null && currentSong.youtubeId == song.youtubeId) ||
                            (song.youtubeId != null && currentSong.id == "youtube_${song.youtubeId}")
                        )
                        val itemBgColor = if (isCurrentPlaying) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .bounceClick(scaleDown = 0.98f) {
                                    playerViewModel.playSongs(exploreTracks, song, queueName = "More to explore")
                                },
                            shape = pillShape,
                            color = itemBgColor,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    SmartImage(
                                        model = upgradeThumbnailUrlToQuality(song.albumArtUriString, albumArtQuality),
                                        contentDescription = song.title,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrentPlaying) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoadingMore) {
            item(key = "new_releases_loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }
        }
    }
}
