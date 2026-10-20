package com.unshoo.pixelmusic.presentation.screens.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.preferences.AlbumArtQuality
import com.unshoo.pixelmusic.data.feed.YouTubePlaylistResult
import com.unshoo.pixelmusic.data.feed.toSong
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToQuality
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage

data class ChartCountry(val code: String, val name: String)

val chartCountries = listOf(
    ChartCountry("IN", "India"),
    ChartCountry("GLOBAL", "Global"),
    ChartCountry("US", "United States"),
    ChartCountry("GB", "United Kingdom"),
    ChartCountry("JP", "Japan"),
    ChartCountry("KR", "South Korea"),
    ChartCountry("DE", "Germany"),
    ChartCountry("FR", "France"),
    ChartCountry("BR", "Brazil"),
    ChartCountry("CA", "Canada"),
    ChartCountry("AU", "Australia")
)

@Composable
fun ExpressiveChartsContent(
    playlist: YouTubePlaylistResult,
    chartsPage: ChartsPage?,
    selectedCountry: String,
    onCountrySelected: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    onBackClick: () -> Unit,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.ORIGINAL,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    var countryMenuExpanded by remember { mutableStateOf(false) }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = navBarInset + MiniPlayerHeight + 24.dp

    // Extract all structured data from chartsPage or fallback to playlist tracks
    val allSongs: List<Song> = remember(playlist.tracks, chartsPage) {
        val pageSongs = chartsPage?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<SongItem>()
            ?.map { it.toNativeSong() }
            .orEmpty()
        if (pageSongs.isNotEmpty()) pageSongs else playlist.tracks.map { it.toSong() }
    }

    val topArtists: List<ArtistItem> = remember(chartsPage, allSongs) {
        val pageArtists = chartsPage?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<ArtistItem>()
            ?.distinctBy { it.id }
            .orEmpty()
        if (pageArtists.isNotEmpty()) {
            pageArtists
        } else {
            allSongs.mapNotNull { song ->
                if (song.artist.isNotBlank() && song.artist != "Unknown artist") {
                    val artistIdStr = song.artists.firstOrNull()?.id?.toString() ?: song.id
                    ArtistItem(
                        id = artistIdStr,
                        title = song.artist,
                        thumbnail = song.albumArtUriString,
                        shuffleEndpoint = null,
                        radioEndpoint = null
                    )
                } else null
            }.distinctBy { it.title }.take(15)
        }
    }

    val videoCharts: List<PlaylistItem> = remember(chartsPage) {
        chartsPage?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<PlaylistItem>()
            ?.distinctBy { it.id }
            .orEmpty()
    }

    val currentCountryName = remember(selectedCountry) {
        chartCountries.firstOrNull { it.code.equals(selectedCountry, ignoreCase = true) }?.name ?: "India"
    }



    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = statusBarInset + 8.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Top Bar with back arrow, "Charts" title, and Country selector pill dropdown
        item(key = "charts_top_bar") {
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
                    text = "Charts",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // Country Selector Stadium Pill Dropdown
                Box {
                    Surface(
                        onClick = { countryMenuExpanded = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 0.dp,
                        modifier = Modifier.bounceClick(scaleDown = 0.95f) { countryMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = currentCountryName,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select country",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        chartCountries.forEach { country ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = country.name,
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = if (selectedCountry.equals(country.code, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (selectedCountry.equals(country.code, ignoreCase = true)) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    countryMenuExpanded = false
                                    onCountrySelected(country.code)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. "Trending 20 [Country]" Ranked Section with Shuffle & Play buttons
        item(key = "charts_trending_20_section", contentType = "trending_section") {
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
                        text = "Trending 20 $currentCountryName",
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    if (allSongs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(allSongs.take(20), queueName = "Trending 20 $currentCountryName")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle songs list",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play All Songs List Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    if (allSongs.isNotEmpty()) {
                                        playerViewModel.playSongs(allSongs.take(20), allSongs.first(), queueName = "Trending 20 $currentCountryName")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Play all songs list",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                allSongs.take(20).forEachIndexed { index, song ->
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
                                playerViewModel.playSongs(allSongs.take(20), song, queueName = "Trending 20 $currentCountryName")
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
                            // Rank Number
                            Text(
                                text = "${index + 1}",
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isCurrentPlaying || index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Artwork Square
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                SmartImage(
                                    model = upgradeThumbnailUrlToQuality(song.albumArtUriString, albumArtQuality),
                                    contentDescription = song.title,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song Title & Artist
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

                            // Playing Indicator when active
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

        // 3. "Video charts" Section
        if (videoCharts.isNotEmpty()) {
            item(key = "charts_video_charts_section", contentType = "video_charts") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Video charts",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 20.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(videoCharts, key = { "vc_${it.id}" }, contentType = { "video_chart_card" }) { videoChart ->
                            VideoChartCardItem(
                                title = videoChart.title,
                                thumbnail = videoChart.thumbnail,
                                albumArtQuality = albumArtQuality,
                                onClick = {
                                    navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute(videoChart.id))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. "Top artists" Section
        if (topArtists.isNotEmpty()) {
            item(key = "charts_top_artists_section", contentType = "top_artists") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Top artists",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 20.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(topArtists, key = { _, artist -> "artist_${artist.id}" }, contentType = { _, _ -> "artist_avatar" }) { index, artist ->
                            TopArtistCardItem(
                                index = index,
                                artist = artist,
                                albumArtQuality = albumArtQuality,
                                onClick = {
                                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoChartCardItem(
    title: String,
    thumbnail: String?,
    albumArtQuality: AlbumArtQuality,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(156.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SmartImage(
                    model = upgradeThumbnailUrlToQuality(thumbnail, albumArtQuality),
                    contentDescription = title,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Video chart",
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopArtistCardItem(
    index: Int,
    artist: ArtistItem,
    albumArtQuality: AlbumArtQuality,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp)
        ) {
            SmartImage(
                model = upgradeThumbnailUrlToQuality(artist.thumbnail, albumArtQuality),
                contentDescription = artist.title,
                shape = CircleShape,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            )

            // Pill Rank Badge #1, #2, #3
            Surface(
                shape = CircleShape,
                color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
            ) {
                Text(
                    text = "#${index + 1}",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (index < 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.title,
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Top Artist",
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

