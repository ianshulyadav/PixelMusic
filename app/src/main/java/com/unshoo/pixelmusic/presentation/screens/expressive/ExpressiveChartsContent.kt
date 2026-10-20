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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class ChartsFilterTab(val title: String, val icon: String? = null) {
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas", "👤")
}

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
    var selectedTab by remember { mutableStateOf(ChartsFilterTab.SONGS) }
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow

    // Dynamic Material 3 tonal palettes for top hero expressive cards
    val heroPalettes = remember(primaryColor, secondaryColor, tertiaryColor, isDark) {
        if (isDark) {
            listOf(
                listOf(primaryColor.copy(alpha = 0.40f), surfaceLow) to primaryColor,
                listOf(tertiaryColor.copy(alpha = 0.40f), surfaceLow) to tertiaryColor,
                listOf(secondaryColor.copy(alpha = 0.40f), surfaceLow) to secondaryColor,
                listOf(primaryContainer.copy(alpha = 0.50f), surfaceLow) to primaryColor,
                listOf(tertiaryContainer.copy(alpha = 0.50f), surfaceLow) to tertiaryColor
            )
        } else {
            listOf(
                listOf(primaryContainer, surfaceHigh) to primaryColor,
                listOf(tertiaryContainer, surfaceHigh) to tertiaryColor,
                listOf(secondaryContainer, surfaceHigh) to secondaryColor,
                listOf(primaryColor.copy(alpha = 0.20f), surfaceHighest) to primaryColor,
                listOf(tertiaryColor.copy(alpha = 0.20f), surfaceHighest) to tertiaryColor
            )
        }
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

        // Play & Shuffle Action Row
        item(key = "charts_action_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Surface(
                    onClick = {
                        if (allSongs.isNotEmpty()) {
                            playerViewModel.playSongs(allSongs, allSongs.first(), queueName = "Charts ($currentCountryName)")
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.bounceClick(scaleDown = 0.95f) {
                        if (allSongs.isNotEmpty()) {
                            playerViewModel.playSongs(allSongs, allSongs.first(), queueName = "Charts ($currentCountryName)")
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Play",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Shuffle Button
                Surface(
                    onClick = {
                        if (allSongs.isNotEmpty()) {
                            playerViewModel.playSongsShuffled(allSongs, queueName = "Charts ($currentCountryName)")
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.bounceClick(scaleDown = 0.95f) {
                        if (allSongs.isNotEmpty()) {
                            playerViewModel.playSongsShuffled(allSongs, queueName = "Charts ($currentCountryName)")
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Shuffle",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 2. Segmented Pill Filter: Canciones | Álbumes | Artistas (Screenshot 1)
        item(key = "charts_filter_pills") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChartsFilterTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val pillBgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_bg_anim"
                    )
                    val pillTextColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_text_anim"
                    )

                    Surface(
                        onClick = { selectedTab = tab },
                        shape = CircleShape,
                        color = pillBgColor,
                        shadowElevation = 0.dp,
                        modifier = Modifier.bounceClick(scaleDown = 0.95f) { selectedTab = tab }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (tab.icon != null) {
                                Text(
                                    text = tab.icon,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = tab.title,
                                fontFamily = GoogleSansRounded,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = pillTextColor
                            )
                        }
                    }
                }
            }
        }

        // 3. Hero Expressive Cards Carousel (Screenshot 1: "Más escuchado" / "Top charts")
        item(key = "charts_hero_carousel") {
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
                        text = "Más escuchado",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Trailing circular arrow button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                if (allSongs.isNotEmpty()) {
                                    playerViewModel.playSongs(allSongs, allSongs.first(), queueName = "Top Charts")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Play all",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Horizontal Row of Big Expressive Rounded Cards
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val heroItems = if (selectedTab == ChartsFilterTab.ARTISTS && topArtists.isNotEmpty()) {
                        topArtists.take(5).mapIndexed { idx, it ->
                            Triple(it.title, "Top Artist", it.thumbnail)
                        }
                    } else {
                        allSongs.take(5).mapIndexed { idx, it ->
                            Triple(it.title, it.artist, it.albumArtUriString)
                        }
                    }

                    itemsIndexed(heroItems) { index, (title, subtitle, artwork) ->
                        val (gradientColors, badgeTint) = heroPalettes[index % heroPalettes.size]
                        val cardShape = remember { AbsoluteSmoothCornerShape(26.dp, 60) }

                        Surface(
                            modifier = Modifier
                                .width(175.dp)
                                .height(225.dp)
                                .bounceClick(scaleDown = 0.96f) {
                                    if (selectedTab != ChartsFilterTab.ARTISTS && index < allSongs.size) {
                                        playerViewModel.playSongs(allSongs, allSongs[index], queueName = "Top Charts")
                                    } else if (index < topArtists.size) {
                                        navController.navigateSafely(Screen.ArtistDetail.createRoute(topArtists[index].id))
                                    }
                                },
                            shape = cardShape,
                            color = Color.Transparent,
                            shadowElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(brush = Brush.verticalGradient(gradientColors))
                                    .padding(14.dp)
                            ) {
                                // Top-Left #1, #2, #3 Pill Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(badgeTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF141416)
                                    )
                                }

                                // Center Circular Avatar Photo
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    SmartImage(
                                        model = upgradeThumbnailUrlToQuality(artwork, albumArtQuality),
                                        contentDescription = title,
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Bottom Titles
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f),
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

        // 4. "Trending 20 [Country]" Ranked Section (Screenshot 3)
        item(key = "charts_trending_20_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Trending 20 $currentCountryName",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )

                allSongs.take(20).forEachIndexed { index, song ->
                    val pillShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .bounceClick(scaleDown = 0.98f) {
                                playerViewModel.playSongs(allSongs, song, queueName = "Trending 20 $currentCountryName")
                            },
                        shape = pillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.onSurface,
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

                            // Trailing Play Icon
                            IconButton(
                                onClick = {
                                    playerViewModel.playSongs(allSongs, song, queueName = "Trending 20 $currentCountryName")
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. "Video charts" Section (Screenshots 3 & 4)
        if (videoCharts.isNotEmpty()) {
            item(key = "charts_video_charts_section") {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(videoCharts) { videoChart ->
                            val cardShape = remember { RoundedCornerShape(22.dp) }
                            Surface(
                                modifier = Modifier
                                    .width(165.dp)
                                    .height(165.dp)
                                    .bounceClick(scaleDown = 0.96f) {
                                        navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute(videoChart.id))
                                    },
                                shape = cardShape,
                                color = Color.Transparent,
                                shadowElevation = 0.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.65f else 0.85f),
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
                                            )
                                        )
                                        .padding(12.dp)
                                ) {
                                    // Centered artwork
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        SmartImage(
                                            model = upgradeThumbnailUrlToQuality(videoChart.thumbnail, albumArtQuality),
                                            contentDescription = videoChart.title,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Floating Play Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play chart",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Title
                                    Text(
                                        text = videoChart.title,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.TopStart)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. "Top artists" with Screenshot 4 Expressive Squircle Cards
        if (topArtists.isNotEmpty()) {
            item(key = "charts_top_artists_section") {
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
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(topArtists) { index, artist ->
                            val cardShape = remember { AbsoluteSmoothCornerShape(22.dp, 60) }
                            val (bgGradient, badgeColor) = remember(index) {
                                when (index % 4) {
                                    0 -> listOf(Color(0xFF5A350E), Color(0xFF381F05)) to Color(0xFFFFB77F) // Warm amber #1
                                    1 -> listOf(Color(0xFF2C2D30), Color(0xFF1E1E20)) to Color(0xFFA6A8AE) // Graphite slate #2
                                    2 -> listOf(Color(0xFF432240), Color(0xFF281126)) to Color(0xFFDAA0D5) // Plum berry #3
                                    else -> listOf(Color(0xFF1D3245), Color(0xFF0F1E2B)) to Color(0xFF8AC0DE) // Cyan navy #4+
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(180.dp)
                                    .bounceClick(scaleDown = 0.96f) {
                                        navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                                    },
                                shape = cardShape,
                                color = Color.Transparent,
                                shadowElevation = 0.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(brush = Brush.verticalGradient(bgGradient))
                                        .padding(12.dp)
                                ) {
                                    // Pill Rank Badge #1, #2, #3
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Color.Black
                                        )
                                    }

                                    // Circular Artist Photo
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(top = 8.dp)
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    ) {
                                        SmartImage(
                                            model = upgradeThumbnailUrlToQuality(artist.thumbnail, albumArtQuality),
                                            contentDescription = artist.title,
                                            shape = CircleShape,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Artist Name & subtitle at the bottom
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = artist.title,
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Top Artist",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 11.5.sp,
                                            color = Color.White.copy(alpha = 0.70f),
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
        }
    }
}
