package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Material 3 Expressive Card matching the reference design:
 * Smooth rounded corners, circular icon badge, title and subtitle.
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
    modifier: Modifier = Modifier
) {
    val cardShape = remember { AbsoluteSmoothCornerShape(22.dp, 60) }

    Surface(
        modifier = modifier
            .height(112.dp)
            .bounceClick(scaleDown = 0.96f) { onClick() },
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(cardBgGradient))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Circular Icon Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
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
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current

    // Dynamic Material 3 palettes based on current app UI & wallpaper theme
    val newReleasesGradient = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.50f),
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val newReleasesBadgeBg = MaterialTheme.colorScheme.primaryContainer
    val newReleasesBadgeIcon = MaterialTheme.colorScheme.onPrimaryContainer

    val chartsGradient = listOf(
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.35f else 0.50f),
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val chartsBadgeBg = MaterialTheme.colorScheme.tertiaryContainer
    val chartsBadgeIcon = MaterialTheme.colorScheme.onTertiaryContainer

    val moodsGradient = listOf(
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.35f else 0.50f),
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val moodsBadgeBg = MaterialTheme.colorScheme.secondaryContainer
    val moodsBadgeIcon = MaterialTheme.colorScheme.onSecondaryContainer

    val podcastsGradient = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.40f else 0.55f),
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val podcastsBadgeBg = MaterialTheme.colorScheme.surfaceVariant
    val podcastsBadgeIcon = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Browse & Discover",
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            fontSize = 18.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 2.dp, bottom = 0.dp)
        )

        // Row 1: New releases + Charts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BrowseDiscoverCard(
                title = "New releases",
                subtitle = "Fresh albums & singles",
                icon = Icons.Rounded.Album,
                badgeBgColor = newReleasesBadgeBg,
                badgeIconColor = newReleasesBadgeIcon,
                cardBgGradient = newReleasesGradient,
                onClick = onNewReleasesClick,
                modifier = Modifier.weight(1f)
            )

            BrowseDiscoverCard(
                title = "Charts",
                subtitle = "Top trending tracks",
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    val isDark = LocalPixelMusicDarkTheme.current
    val pillBgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val badgeBgColor = MaterialTheme.colorScheme.secondaryContainer
    val badgeIconTint = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .bounceClick(scaleDown = 0.98f) { onClick() },
        shape = CircleShape,
        color = pillBgColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular history icon badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = badgeIconTint,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = query,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(19.dp)
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
    val isDark = LocalPixelMusicDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                fontSize = 18.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Clear all",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Vertical stack of search pills
        searchHistory.take(15).forEach { item ->
            RecentSearchPillItem(
                query = item.query,
                onClick = { onItemClick(item.query) },
                onDelete = { onItemDelete(item.query) }
            )
        }
    }
}
