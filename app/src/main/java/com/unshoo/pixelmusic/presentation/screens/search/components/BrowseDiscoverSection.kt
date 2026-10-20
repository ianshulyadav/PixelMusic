package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Realistic Canvas-rendered Vinyl Record with concentric micro-grooves,
 * dual specular light sheen, and colored center spindle label.
 */
@Composable
fun VinylRecordDisc(
    modifier: Modifier = Modifier,
    spindleColor: Color = Color(0xFF2E7D32)
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1. Vinyl Base (Charcoal / Pure Vinyl Black)
        drawCircle(
            color = Color(0xFF141416),
            radius = radius,
            center = center
        )

        // 2. Micro-grooves (Concentric sound tracks)
        val grooveCount = 7
        val minGrooveRadius = radius * 0.44f
        val maxGrooveRadius = radius * 0.94f
        val step = (maxGrooveRadius - minGrooveRadius) / grooveCount

        for (i in 0 until grooveCount) {
            val r = minGrooveRadius + (i * step)
            val alpha = if (i % 2 == 0) 0.08f else 0.04f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = center,
                style = Stroke(width = 0.75.dp.toPx())
            )
        }

        // 3. Dual Specular Light Sweep (Angular reflection on vinyl surface)
        val sweepBrush = Brush.sweepGradient(
            0.0f to Color.Transparent,
            0.12f to Color.White.copy(alpha = 0.15f),
            0.18f to Color.Transparent,
            0.50f to Color.Transparent,
            0.62f to Color.White.copy(alpha = 0.15f),
            0.68f to Color.Transparent,
            1.0f to Color.Transparent,
            center = center
        )
        drawCircle(
            brush = sweepBrush,
            radius = radius,
            center = center
        )

        // 4. Center Spindle Label
        val labelRadius = radius * 0.38f
        drawCircle(
            color = spindleColor,
            radius = labelRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = labelRadius,
            center = center,
            style = Stroke(width = 0.8.dp.toPx())
        )

        // 5. Center Hole
        val holeRadius = radius * 0.11f
        drawCircle(
            color = Color(0xFF09090B),
            radius = holeRadius,
            center = center
        )
    }
}

/**
 * Material 3 Expressive Card with large smooth rounded corners,
 * custom circular icon badge, title, subtitle, and spring bounce animation.
 */
@Composable
fun BrowseDiscoverCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeBgColor: Color,
    badgeIconColor: Color,
    cardBgGradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    decorativeContent: (@Composable () -> Unit)? = null
) {
    val cardShape = remember { AbsoluteSmoothCornerShape(26.dp, 60) }
    val isDark = LocalPixelMusicDarkTheme.current

    Surface(
        modifier = modifier
            .height(116.dp)
            .bounceClick(scaleDown = 0.96f) { onClick() },
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(cardBgGradient))
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                    shape = cardShape
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Optional decorative layer (e.g. half-shown vinyl record)
            if (decorativeContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp)
                ) {
                    decorativeContent()
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Circular Icon Badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bottom Titles
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 2x2 Grid Section for Browse & Discover
 */
@Composable
fun BrowseDiscoverSection(
    onNewReleasesClick: () -> Unit,
    onChartsClick: () -> Unit,
    onMoodsGenresClick: () -> Unit,
    onPodcastsClick: () -> Unit,
    newReleaseCoverUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current

    // Curated pastel palettes for Light and Dark modes
    val newReleasesGradient = if (isDark) {
        listOf(Color(0xFF14291E), Color(0xFF0F1F17))
    } else {
        listOf(Color(0xFFE6F8F0), Color(0xFFD6F2E4))
    }
    val newReleasesBadgeBg = if (isDark) Color(0xFF204E35) else Color(0xFFBAEBD2)
    val newReleasesBadgeIcon = if (isDark) Color(0xFF94E4BA) else Color(0xFF0C3C24)

    val chartsGradient = if (isDark) {
        listOf(Color(0xFF2C161D), Color(0xFF201015))
    } else {
        listOf(Color(0xFFFDF0F3), Color(0xFFFCE1E7))
    }
    val chartsBadgeBg = if (isDark) Color(0xFF52212E) else Color(0xFFF7CAD6)
    val chartsBadgeIcon = if (isDark) Color(0xFFF899B2) else Color(0xFF4C1320)

    val moodsGradient = if (isDark) {
        listOf(Color(0xFF122725), Color(0xFF0E1E1C))
    } else {
        listOf(Color(0xFFE9F8F6), Color(0xFFD9F3EF))
    }
    val moodsBadgeBg = if (isDark) Color(0xFF1C4944) else Color(0xFFC4EBE5)
    val moodsBadgeIcon = if (isDark) Color(0xFF86DDD0) else Color(0xFF0B3A35)

    val podcastsGradient = if (isDark) {
        listOf(Color(0xFF1D172B), Color(0xFF161121))
    } else {
        listOf(Color(0xFFF3F1FD), Color(0xFFE7E3FA))
    }
    val podcastsBadgeBg = if (isDark) Color(0xFF342854) else Color(0xFFDCD5F7)
    val podcastsBadgeIcon = if (isDark) Color(0xFFB8ABF0) else Color(0xFF2B1D4B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Browse & Discover",
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        // Row 1: New releases (with Vinyl disc half-shown) + Charts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrowseDiscoverCard(
                title = "New releases",
                subtitle = "Fresh albums & singles",
                icon = Icons.Rounded.Album,
                badgeBgColor = newReleasesBadgeBg,
                badgeIconColor = newReleasesBadgeIcon,
                cardBgGradient = newReleasesGradient,
                onClick = onNewReleasesClick,
                modifier = Modifier.weight(1f),
                decorativeContent = {
                    // Half-shown Vinyl Record peeking out from the album art
                    Box(
                        modifier = Modifier.size(width = 68.dp, height = 50.dp)
                    ) {
                        // Vinyl Record sliding out to the right
                        VinylRecordDisc(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 6.dp),
                            spindleColor = Color(0xFF2E7D32)
                        )
                        // Album sleeve / cover overlay on the left
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
                                    )
                                )
                        ) {
                            if (!newReleaseCoverUrl.isNullOrBlank()) {
                                SmartImage(
                                    model = newReleaseCoverUrl,
                                    contentDescription = null,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            )

            BrowseDiscoverCard(
                title = "Charts",
                subtitle = "Top trending tracks",
                icon = Icons.Rounded.TrendingUp,
                badgeBgColor = chartsBadgeBg,
                badgeIconColor = chartsBadgeIcon,
                cardBgGradient = chartsGradient,
                onClick = onChartsClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Moods & genres + Podcasts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrowseDiscoverCard(
                title = "Moods & genres",
                subtitle = "Vibes for every mood",
                icon = Icons.Rounded.SentimentSatisfied,
                badgeBgColor = moodsBadgeBg,
                badgeIconColor = moodsBadgeIcon,
                cardBgGradient = moodsGradient,
                onClick = onMoodsGenresClick,
                modifier = Modifier.weight(1f)
            )

            BrowseDiscoverCard(
                title = "Podcasts",
                subtitle = "Shows & episodes",
                icon = Icons.Rounded.Podcasts,
                badgeBgColor = podcastsBadgeBg,
                badgeIconColor = podcastsBadgeIcon,
                cardBgGradient = podcastsGradient,
                onClick = onPodcastsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Full-width stadium pill for individual recent search items.
 */
@Composable
fun RecentSearchPillItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .bounceClick(scaleDown = 0.98f) { onClick() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular history icon badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = query,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Vertical section for Recent searches with header and "Clear all" button.
 */
@Composable
fun RecentSearchesSection(
    searchHistory: List<SearchHistoryItem>,
    onItemClick: (String) -> Unit,
    onItemDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (searchHistory.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                text = "Recent searches",
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(onClick = onClearAll) {
                Text(
                    text = "Clear all",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Vertical stack of search pills
        searchHistory.take(8).forEach { item ->
            RecentSearchPillItem(
                query = item.query,
                onClick = { onItemClick(item.query) },
                onDelete = { onItemDelete(item.query) }
            )
        }
    }
}
