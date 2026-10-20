package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageCardTargetSize
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme

/**
 * Material 3 Expressive Mood & Genre Card with Dual Overlapping Covers.
 * Displays bold, unbroken typography on top-left, and authentic YouTube Music album arts on bottom-right.
 * Features atmospheric radial gradient glow and smooth squircle containment.
 */
@Composable
fun MoodGenreCard(
    item: MoodGenreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPixelMusicDarkTheme.current

    val baseColor = remember(isDark, item.darkCardColor, item.lightCardColor) {
        if (isDark) item.darkCardColor else item.lightCardColor
    }

    val textColor = remember(isDark) {
        if (isDark) Color.White else Color(0xFF191C1E)
    }

    // Direct reactive lookup in mutableStateMapOf (only this card recomposes when its image arrives)
    val cachedImages = CategoryPlaylistImageCache.get(item.title)
    val cover1 = cachedImages?.first ?: item.playlistImage1
    val cover2 = cachedImages?.second ?: item.playlistImage2 ?: cover1

    // Material 3 Expressive 22dp smooth squircle shape
    val cardShape = remember { racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(22.dp, 60) }

    // Expressive dynamic gradient: subtle tone shift across the card surface
    val surfaceBrush = remember(baseColor, isDark) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    baseColor,
                    baseColor.copy(alpha = 0.88f),
                    baseColor.copy(alpha = 0.72f)
                )
            } else {
                listOf(
                    baseColor,
                    baseColor.copy(alpha = 0.94f),
                    baseColor.copy(alpha = 0.86f)
                )
            },
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    Box(
        modifier = modifier
            .height(112.dp)
            .clip(cardShape)
            .background(surfaceBrush)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                cardShape
            )
            .bounceClick(scaleDown = 0.95f) { onClick() }
    ) {
        // Two Overlapping Rotated Featured Playlist Covers (Bottom Right Corner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxHeight()
                .width(112.dp)
                .clip(cardShape),
            contentAlignment = Alignment.BottomEnd
        ) {
            // 1. Back Image (Rotated -12 deg, sits slightly behind and to the left)
            if (!cover1.isNullOrBlank()) {
                SmartImage(
                    model = cover1,
                    contentDescription = null,
                    targetSize = SmartImageCardTargetSize,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .offset(x = (-16).dp, y = 6.dp)
                        .rotate(-12f)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // 2. Front Image (Rotated +12 deg, overlaps the back image and peeks over edge)
            if (!cover2.isNullOrBlank()) {
                SmartImage(
                    model = cover2,
                    contentDescription = null,
                    targetSize = SmartImageCardTargetSize,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .offset(x = 8.dp, y = 12.dp)
                        .rotate(12f)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }

        // Bold Expressive Title (Top-Left)
        // Ample width (0.68f) and end padding 8dp so words NEVER hyphenate or break awkwardly!
        Text(
            text = item.title,
            color = textColor,
            fontFamily = com.unshoo.pixelmusic.ui.theme.GoogleSansRounded,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                letterSpacing = (-0.2).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                hyphens = Hyphens.None
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp, end = 8.dp)
                .fillMaxWidth(0.68f)
        )
    }
}
