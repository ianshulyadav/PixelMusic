package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SongInfoBottomSheetViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.unshoo.pixelmusic.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode
import com.unshoo.pixelmusic.presentation.utils.CardColorExtractor
import com.unshoo.pixelmusic.presentation.utils.itemsUnique
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import com.unshoo.pixelmusic.presentation.components.SmartImageCompactListTargetSize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val QuickPicksPillHeight = 56.dp
private val QuickPicksPillSpacing = 8.dp
private const val QuickPicksPillsPerColumn = 3
private const val QuickPicksLimit = 48
private val QuickPicksPillArtSize = 36.dp
private val QuickPicksWidthSteps = listOf(148.dp, 166.dp, 184.dp, 202.dp, 220.dp)

private data class QuickPicksPillCell(val song: Song, val width: Dp)
private data class QuickPicksPillRow(val pills: List<QuickPicksPillCell>, val contentWidth: Dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPicksSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    currentSongId: String? = null,
    displayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.LIST,
    cardSize: Dp = 140.dp,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onDownload: ((Song) -> Unit)? = null,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val context = LocalContext.current
    val isPlaybackActive by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.stablePlayerState.value.isPlaying)
    val currentPlayingId by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.stablePlayerState.value.currentSong?.id)
    val activePlayingSongId = if (isPlaybackActive) {
        currentPlayingId ?: currentSongId
    } else null

    val handlePlayNext: (Song) -> Unit = onPlayNext ?: { song ->
        playerViewModel.addSongNextToQueue(song)
        Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
    }
    val handleAddToQueue: (Song) -> Unit = onAddToQueue ?: { song ->
        playerViewModel.addSongToQueue(song)
        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
    }
    val handleDownload: (Song) -> Unit = onDownload ?: { song ->
        if (song.telegramFileId != null && song.youtubeId == null) {
            songInfoViewModel.downloadTelegramSong(song)
        } else {
            songInfoViewModel.downloadYoutubeSong(song)
        }
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    }

    val visible = remember(songs) {
        val count = (songs.size / 3) * 3
        songs.take(count.coerceAtMost(QuickPicksLimit))
    }
    val rows = remember(visible) { buildQuickPickRows(visible) }
    val scrollState = rememberScrollState()
    val actualRowsCount = rows.size
    val sectionHeight = if (actualRowsCount > 0) {
        QuickPicksPillHeight * actualRowsCount + QuickPicksPillSpacing * (actualRowsCount - 1)
    } else 0.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.quick_picks_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp)
            )
            if (onSeeAllClick != null) {
                FilledIconButton(
                    modifier = Modifier
                        .height(40.dp)
                        .width(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    onClick = onSeeAllClick
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.see_all_quick_picks_cd),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        if (displayMode == QuickPicksDisplayMode.CARD) {
            val limitSongs = remember(songs) { songs.take(20) }
            val carouselState = rememberCarouselState { limitSongs.size }
            val animationScope = rememberCoroutineScope()
            val flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(
                state = carouselState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )

            HorizontalCenteredHeroCarousel(
                state = carouselState,
                itemSpacing = 8.dp,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) { page ->
                val song = limitSongs[page]
                CenteredHeroCard(
                    song = song,
                    isPlaying = song.id == activePlayingSongId,
                    loadArtImmediately = page < 3,
                    onClick = {
                        animationScope.launch { carouselState.animateScrollToItem(page) }
                        onSongClick(song)
                    },
                    onPlayNext = handlePlayNext,
                    onAddToQueue = handleAddToQueue,
                    onDownload = handleDownload
                )
            }
        } else if (displayMode == QuickPicksDisplayMode.CARD_CLASSIC) {
            val limitSongs = remember(songs) { songs.take(20) }
            val lazyListState = rememberLazyListState()
            val context = LocalContext.current
            
            // Query system reduced motion (animation scale)
            val isReducedMotion = remember(context) {
                try {
                    android.provider.Settings.Global.getFloat(
                        context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f
                    ) == 0f
                } catch (e: Exception) {
                    false
                }
            }

            // Same snapshotFlow-based fix as CARD mode above
            LaunchedEffect(limitSongs) {
                if (limitSongs.isEmpty()) return@LaunchedEffect
                while (isActive) {
                    snapshotFlow { lazyListState.isScrollInProgress }
                        .filter { !it }
                        .first()
                    delay(2500)
                    if (isActive && !lazyListState.isScrollInProgress) {
                        val nextIndex = (lazyListState.firstVisibleItemIndex + 1) % limitSongs.size
                        if (isReducedMotion) {
                            lazyListState.scrollToItem(nextIndex)
                        } else {
                            lazyListState.animateScrollToItem(nextIndex)
                        }
                    }
                }
            }

            val cardShape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsUnique(limitSongs, key = { it.id }) { song ->
                    Column(
                        modifier = Modifier
                            .width(cardSize)
                            .bounceClick(scaleDown = 0.96f) { onSongClick(song) }
                    ) {
                        Card(
                            modifier = Modifier
                                .size(cardSize)
                                .graphicsLayer {
                                    if (isReducedMotion) {
                                        scaleX = 1f
                                        scaleY = 1f
                                        alpha = 1f
                                    } else {
                                        val layoutInfo = lazyListState.layoutInfo
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        val itemInfo = visibleItems.firstOrNull { it.key == song.id }
                                        if (itemInfo != null) {
                                            val focalPoint = layoutInfo.viewportStartOffset + 16.dp.toPx()
                                            val distanceFromStart = (itemInfo.offset.toFloat() - focalPoint).absoluteValue
                                            val maxDistance = (cardSize + 8.dp).toPx()
                                            val fraction = (distanceFromStart / maxDistance).coerceIn(0f, 1f)
                                            val scale = 0.9f + (1f - 0.9f) * (1f - fraction)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 0.8f + (1f - 0.8f) * (1f - fraction)
                                        } else {
                                            scaleX = 0.9f
                                            scaleY = 0.9f
                                            alpha = 0.8f
                                        }
                                    }
                                },
                            shape = cardShape,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val classicIndex = limitSongs.indexOf(song)
                                val loadArtImmediately = classicIndex in 0..2
                                val artUri = if (loadArtImmediately) {
                                    song.albumArtUriString
                                } else {
                                    val calmUri by produceState<String?>(initialValue = null, song.albumArtUriString) {
                                        delay(120L)
                                        value = song.albumArtUriString
                                    }
                                    calmUri
                                }
                                SmartImage(
                                    model = artUri,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    targetSize = SmartImageCardTargetSize,
                                    crossfadeDurationMillis = 200,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                val isPlaying = song.id == activePlayingSongId
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EqualizerAnimation(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(18.dp),
                                            isPlaying = true,
                                            color = Color.White,
                                            barCount = 3
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(7.dp))
                        
                        // Metadata details below card
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = if (song.id == currentSongId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else if (displayMode == QuickPicksDisplayMode.UNCONTAINED) {
            val limitSongs = remember(songs) { songs.take(20) }
            val lazyListState = rememberLazyListState()
            
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 76.dp), // Leaves next item cut off for standard uncontained look
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsUnique(limitSongs, key = { it.id }) { song ->
                    val uncontainedCardSize = 150.dp
                    Column(
                        modifier = Modifier
                            .width(uncontainedCardSize)
                            .clickable { onSongClick(song) }
                    ) {
                        Card(
                            modifier = Modifier
                                .size(uncontainedCardSize)
                                .graphicsLayer {
                                    val layoutInfo = lazyListState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.firstOrNull { it.key == song.id }
                                    if (itemInfo != null) {
                                        val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                        val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                        val fraction = (itemCenter / viewportWidth.toFloat()).coerceIn(0f, 1f)
                                        
                                        // dynamic Material 3 uncontained scale
                                        val scale = if (fraction > 0.6f) {
                                            1f - (fraction - 0.6f) * 0.3f
                                        } else {
                                            1f
                                        }
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 0.7f + (1f - 0.7f) * scale
                                        
                                        // Dynamic Material 3 shape morphing: squircle corner sizes change on scroll position
                                        val currentCorner = if (fraction > 0.5f) {
                                            val morphProgress = (fraction - 0.5f) * 2f
                                            24.dp + (56.dp - 24.dp) * morphProgress.coerceIn(0f, 1f)
                                        } else {
                                            24.dp
                                        }
                                        shape = AbsoluteSmoothCornerShape(currentCorner, 80)
                                        clip = true
                                    } else {
                                        scaleX = 0.88f
                                        scaleY = 0.88f
                                        alpha = 0.7f
                                        shape = AbsoluteSmoothCornerShape(56.dp, 80)
                                        clip = true
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val uncontainedIndex = limitSongs.indexOf(song)
                                val loadArtImmediately = uncontainedIndex in 0..2
                                val artUri = if (loadArtImmediately) {
                                    song.albumArtUriString
                                } else {
                                    val calmUri by produceState<String?>(initialValue = null, song.albumArtUriString) {
                                        delay(120L)
                                        value = song.albumArtUriString
                                    }
                                    calmUri
                                }
                                SmartImage(
                                    model = artUri,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    targetSize = SmartImageCardTargetSize,
                                    crossfadeDurationMillis = 200,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                val isPlaying = song.id == activePlayingSongId
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EqualizerAnimation(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(18.dp),
                                            isPlaying = true,
                                            color = Color.White,
                                            barCount = 3
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(7.dp))
                        
                        // Metadata details below card
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = if (song.id == currentSongId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .horizontalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing)
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        row.pills.forEachIndexed { colIndex, cell ->
                            val absoluteIndex = colIndex * QuickPicksPillsPerColumn + rowIndex
                            val loadArtImmediately = absoluteIndex < 3
                            QuickPickPill(
                                song = cell.song,
                                width = cell.width,
                                isPlaying = cell.song.id == activePlayingSongId,
                                loadArtImmediately = loadArtImmediately,
                                onClick = { onSongClick(cell.song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerAnimation(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    
    val heights = (0 until barCount).map { index ->
        val duration = when(index) {
            0 -> 550
            1 -> 750
            2 -> 450
            3 -> 650
            else -> 550
        }
        val delay = index * 120
        val animatedHeight by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
        if (isPlaying) animatedHeight else when(index % 3) {
            0 -> 0.35f
            1 -> 0.55f
            else -> 0.35f
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightVal)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun rememberAlbumVibrantColor(
    imageUrl: String?,
    fallbackColor: Color
): Color {
    val context = LocalContext.current
    val initialArgb = remember(imageUrl) {
        if (!imageUrl.isNullOrBlank()) CardColorExtractor.colorCache.get(imageUrl) else null
    }
    var targetColor by remember(imageUrl, fallbackColor) {
        mutableStateOf(if (initialArgb != null) Color(initialArgb) else fallbackColor)
    }

    if (initialArgb == null && !imageUrl.isNullOrBlank()) {
        LaunchedEffect(imageUrl, fallbackColor) {
            val argb = CardColorExtractor.extractColorArgb(context, imageUrl)
            if (argb != null) {
                targetColor = Color(argb)
            }
        }
    }

    if (initialArgb != null) {
        return targetColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "album_vibrant_color"
    )
    return animatedColor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.CenteredHeroCard(
    song: Song,
    isPlaying: Boolean,
    loadArtImmediately: Boolean = true,
    onClick: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawInfo = carouselItemDrawInfo
    val sizeFraction = if (drawInfo.maxSize > drawInfo.minSize) {
        ((drawInfo.size - drawInfo.minSize) / (drawInfo.maxSize - drawInfo.minSize)).coerceIn(0f, 1f)
    } else 1f

    // 1. Dynamic curve transformation: quantized to prevent GC churn for ultra-smooth 60/120fps scrolling
    val cornerRadiusDp = remember((sizeFraction * 12f).toInt()) {
        (16 + (sizeFraction * 12f).toInt()).dp
    }
    val cardShape = remember(cornerRadiusDp) { AbsoluteSmoothCornerShape(cornerRadiusDp, 60) }

    // 2. Text opacity: strictly visible for the centered carousel card
    val textAlpha = ((sizeFraction - 0.55f) / 0.45f).coerceIn(0f, 1f)

    // Calm loading of artwork and colors for smooth initial render & scroll
    val shouldLoadImmediately = loadArtImmediately || textAlpha > 0.05f
    val artUri = if (shouldLoadImmediately) {
        song.albumArtUriString
    } else {
        val calmUri by produceState<String?>(initialValue = null, song.albumArtUriString) {
            delay(120L)
            value = song.albumArtUriString
        }
        calmUri
    }

    // Dynamic extracted color directly from the album art
    val defaultColor = MaterialTheme.colorScheme.primary
    val albumColor = rememberAlbumVibrantColor(
        imageUrl = if (shouldLoadImmediately) song.albumArtUriString else null,
        fallbackColor = defaultColor
    )

    // Deep, rich dynamic base tones derived directly from the album's extracted color
    // Guarantees pristine, rich aesthetics and zero washed-out milky gray in both light & dark themes
    val darkBase = remember(albumColor) {
        lerp(albumColor, Color(0xFF07090E), 0.72f)
    }
    val midBase = remember(albumColor) {
        lerp(albumColor, Color(0xFF07090E), 0.35f)
    }

    // Dynamic album gradient brush appearing on both center and side carousel shapes
    val dynamicGradientBrush = remember(albumColor, midBase, darkBase) {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                albumColor.copy(alpha = 0.15f),
                midBase.copy(alpha = 0.65f),
                darkBase.copy(alpha = 0.94f),
                darkBase
            )
        )
    }

    val cardBorder = remember(albumColor) {
        BorderStroke(
            width = 0.75.dp,
            color = albumColor.copy(alpha = 0.35f)
        )
    }

    Card(
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = darkBase
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (sizeFraction > 0.85f) 3.dp else 1.dp
        ),
        modifier = modifier
            .fillMaxSize()
            .maskClip(cardShape)
            .maskBorder(cardBorder, cardShape)
            .bounceClick(scaleDown = 0.97f) { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed Artwork
            SmartImage(
                model = artUri,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Album Color Gradient appearing on ALL carousel cards (center and side shapes!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
                    .background(dynamicGradientBrush)
            )

            // 3-dots overflow options in top-right corner of the centered card
            if (textAlpha > 0.01f) {
                var menuExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .graphicsLayer { alpha = textAlpha }
                ) {
                    FilledIconButton(
                        onClick = { menuExpanded = true },
                        enabled = textAlpha > 0.5f,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.42f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme.copy(
                            surface = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Play next",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext(song)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Add to queue",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueue(song)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Download",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDownload(song)
                                }
                            )
                        }
                    }
                }
            }

            // Text and Equalizer strictly for the centered active hero card
            if (textAlpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .graphicsLayer { alpha = textAlpha }
                ) {
                    // Title and Equalizer Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                letterSpacing = 0.15.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Equalizer Bar displayed strictly when song is actively playing!
                        if (isPlaying) {
                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    0.5.dp,
                                    Color.White.copy(alpha = 0.20f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EqualizerAnimation(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(15.dp),
                                        isPlaying = true,
                                        color = MaterialTheme.colorScheme.primary,
                                        barCount = 3
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "PLAYING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Secondary info: Artist • Album • Year
                    val secondaryText = remember(song) {
                        val artist = song.displayArtist
                        val album = song.album.takeIf { it.isNotBlank() && it != song.title }
                        val year = song.year.takeIf { it > 0 }
                        when {
                            album != null && year != null -> "$artist • $album ($year)"
                            album != null -> "$artist • $album"
                            year != null -> "$artist • $year"
                            else -> artist
                        }
                    }
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickPill(
    song: Song,
    width: Dp,
    isPlaying: Boolean,
    loadArtImmediately: Boolean = true,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val targetBg = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 220),
        label = "QuickPickBg"
    )
    Card(
        modifier = Modifier
            .width(width)
            .height(QuickPicksPillHeight)
            .bounceClick(scaleDown = 0.96f) { onClick() },
        shape = RoundedCornerShape(QuickPicksPillHeight / 2),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artUri = if (loadArtImmediately) {
                song.albumArtUriString
            } else {
                val calmUri by produceState<String?>(initialValue = null, song.albumArtUriString) {
                    delay(120L)
                    value = song.albumArtUriString
                }
                calmUri
            }
            SmartImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                shape = CircleShape,
                targetSize = SmartImageCompactListTargetSize,
                crossfadeDurationMillis = 200,
                modifier = Modifier.size(QuickPicksPillArtSize)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildQuickPickRows(songs: List<Song>): List<QuickPicksPillRow> {
    val groups = songs.chunked(QuickPicksPillsPerColumn).take(QuickPicksLimit / QuickPicksPillsPerColumn)
    val columns = groups.mapIndexed { colIndex, group ->
        val widthStep = QuickPicksWidthSteps[colIndex % QuickPicksWidthSteps.size]
        group.map { QuickPicksPillCell(it, widthStep) }
    }
    // Transpose columns -> rows
    val rows = mutableListOf<QuickPicksPillRow>()
    for (rowIdx in 0 until QuickPicksPillsPerColumn) {
        val pills = columns.mapNotNull { col -> col.getOrNull(rowIdx) }
        if (pills.isEmpty()) continue
        val totalWidth = pills.sumOf { it.width.value.toDouble() }.dp +
                QuickPicksPillSpacing * (pills.size - 1)
        rows.add(QuickPicksPillRow(pills, totalWidth))
    }
    return rows
}

@Composable
private fun QuickPickClassicCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 300),
        label = "ClassicCardBg"
    )
    
    // BUGFIX (was: a rememberInfiniteTransition was created unconditionally
    // on every QuickPicks card, even when not playing). Each
    // rememberInfiniteTransition starts a clock-driven animation that
    // runs forever and wakes the UI thread at ~16ms intervals to advance
    // the value. With ~10 cards on the home screen that's 10 parallel
    // animators ticking even when no card is playing — pure waste. We
    // now only create the transition when the card is actually playing.
    val animatedBorderGlowAlpha = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderGlow"
        ).value
    } else {
        1f
    }
    val borderGlowAlpha = animatedBorderGlowAlpha

    // M3 Expressive card: press-scale spring + corner morph instead of a static Card.
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
            .height(112.dp)
            .padding(bottom = 6.dp),
        cornerRadius = 24.dp,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isPlaying) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderGlowAlpha))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerAnimation(
                            modifier = Modifier
                                .width(20.dp)
                                .height(16.dp),
                            color = Color.White,
                            barCount = 3
                        )
                    }
                }
            }
            
            // Metadata details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Badge indicating state
                if (isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NOW PLAYING",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee() else Modifier
                )
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Action Button
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPlaying) {
                        EqualizerAnimation(
                            modifier = Modifier
                                .width(14.dp)
                                .height(11.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            barCount = 3
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
