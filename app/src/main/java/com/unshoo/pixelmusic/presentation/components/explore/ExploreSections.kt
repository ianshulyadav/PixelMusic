package com.unshoo.pixelmusic.presentation.components.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageListTargetSize
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// ============================================================================
//  Section header — title + optional "see all"
// ============================================================================

@Composable
fun ExploreSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onAction != null && actionLabel != null) {
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = onAction,
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.92f)
                    .hapticPress(interaction),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                interactionSource = interaction
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
//  Hero carousel
// ============================================================================

private val HeroCardShape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 28.dp,
    cornerRadiusTR = 28.dp,
    cornerRadiusBL = 28.dp,
    cornerRadiusBR = 28.dp,
    smoothnessAsPercentTL = 60,
    smoothnessAsPercentTR = 60,
    smoothnessAsPercentBL = 60,
    smoothnessAsPercentBR = 60
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreHeroCarousel(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val rowState = rememberLazyListState()
    val stretch = rememberScrollStretch(rowState, maxStretch = 0.02f)

    LazyRow(
        state = rowState,
        modifier = modifier
            .fillMaxWidth()
            .scrollStretch(stretch),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> "hero_${song.id}" },
            contentType = { _, _ -> "explore_hero" }
        ) { index, song ->
            ExploreHeroCard(
                song = song,
                isCurrent = song.id == currentSongId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) },
                modifier = Modifier.revealOnEnter(index = index, staggerMillis = 55)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreHeroCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .width(258.dp)
            .height(300.dp)
            .pressScale(interaction, pressedScale = 0.965f)
            .hapticPress(interaction)
            .clip(HeroCardShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            modifier = Modifier.fillMaxSize(),
            shape = HeroCardShape,
            contentScale = ContentScale.Crop,
            targetSize = SmartImageListTargetSize
        )

        // Scrim so the text always passes contrast, regardless of artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.42f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.86f)
                    )
                )
        )

        // Tonal wash on press — driven by the interaction flow, not state.
        if (pressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(40.dp),
            shape = CircleShape,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Black.copy(alpha = 0.32f)
            },
            contentColor = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                Color.White
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ============================================================================
//  Quick picks — 2-column pill grid
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreQuickPicks(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    // Materialise the 2-column grid with plain Rows rather than a nested
    // LazyVerticalGrid: a nested lazy grid inside a LazyColumn forces the
    // parent to measure with infinite height and doubles the scroll machinery.
    val rows = remember(songs) { songs.chunked(2) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowSongs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowSongs.forEachIndexed { columnIndex, song ->
                    ExploreQuickPickPill(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) },
                        modifier = Modifier
                            .weight(1f)
                            .revealOnEnter(
                                index = rowIndex * 2 + columnIndex,
                                staggerMillis = 40,
                                travelDp = 12f
                            )
                    )
                }
                if (rowSongs.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreQuickPickPill(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .hapticPress(interaction)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                targetSize = com.unshoo.pixelmusic.presentation.components.SmartImageCompactListTargetSize
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
//  Made for you — square art cards
// ============================================================================

private val MadeForYouShape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 20.dp,
    cornerRadiusTR = 20.dp,
    cornerRadiusBL = 20.dp,
    cornerRadiusBR = 20.dp,
    smoothnessAsPercentTL = 60,
    smoothnessAsPercentTR = 60,
    smoothnessAsPercentBL = 60,
    smoothnessAsPercentBR = 60
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreMadeForYou(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val rowState = rememberLazyListState()
    val stretch = rememberScrollStretch(rowState, maxStretch = 0.018f)

    LazyRow(
        state = rowState,
        modifier = modifier
            .fillMaxWidth()
            .scrollStretch(stretch),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> "mfy_${song.id}" },
            contentType = { _, _ -> "explore_made_for_you" }
        ) { index, song ->
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .revealOnEnter(index = index, staggerMillis = 35, travelDp = 14f)
            ) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pressScale(interaction, pressedScale = 0.94f)
                        .hapticPress(interaction)
                        .clip(MadeForYouShape)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) }
                        )
                ) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = MadeForYouShape,
                        targetSize = SmartImageListTargetSize
                    )
                    if (song.id == currentSongId) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
//  Genre chips
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreGenreChips(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val containers = remember(scheme) {
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.surfaceContainerHighest to scheme.onSurface
        )
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        genres.forEachIndexed { index, genre ->
            val (container, content) = containers[index % containers.size]
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = { onGenreClick(genre) },
                modifier = Modifier
                    .tiltOnPress(interaction, maxDegrees = 4f)
                    .hapticPress(interaction)
                    .revealOnEnter(index = index, staggerMillis = 22, travelDp = 10f),
                shape = RoundedCornerShape(14.dp),
                color = container,
                contentColor = content,
                interactionSource = interaction
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ============================================================================
//  Compact song row (most played)
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreSongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .pressScale(interaction, pressedScale = 0.975f, bouncy = false)
            .hapticPress(interaction)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                targetSize = com.unshoo.pixelmusic.presentation.components.SmartImageCompactListTargetSize
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ============================================================================
//  Recently played pills
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreRecentlyPlayedPills(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val rowState = rememberLazyListState()
    val stretch = rememberScrollStretch(rowState, maxStretch = 0.016f)

    LazyRow(
        state = rowState,
        modifier = modifier
            .fillMaxWidth()
            .scrollStretch(stretch),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> "recent_${song.id}" },
            contentType = { _, _ -> "explore_recent_pill" }
        ) { index, song ->
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = { onSongClick(song) },
                modifier = Modifier
                    .width(148.dp)
                    .pressScale(interaction, pressedScale = 0.945f)
                    .hapticPress(interaction)
                    .revealOnEnter(index = index, staggerMillis = 28, travelDp = 12f),
                shape = RoundedCornerShape(14.dp),
                color = if (song.id == currentSongId) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                interactionSource = interaction
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp)),
                        shape = RoundedCornerShape(9.dp),
                        targetSize = com.unshoo.pixelmusic.presentation.components.SmartImageCompactListTargetSize
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.displayArtist,
                            style = MaterialTheme.typography.labelSmall,
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

// ============================================================================
//  Skeleton
// ============================================================================

/**
 * First-paint skeleton. Mirrors the real layout 1:1 so nothing reflows when the
 * data lands — the perceived load time is dominated by layout stability, not by
 * the actual network/DB latency.
 */
@Composable
fun ExploreSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(2) {
                ShimmerBox(
                    modifier = Modifier
                        .width(258.dp)
                        .height(300.dp),
                    shape = HeroCardShape
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .width(132.dp)
                        .height(132.dp),
                    shape = MadeForYouShape
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .height(38.dp)
                        .width(92.dp),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }
}

/** Kept for callers that need a placeholder without a shimmer. */
@Composable
fun ExploreEmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
