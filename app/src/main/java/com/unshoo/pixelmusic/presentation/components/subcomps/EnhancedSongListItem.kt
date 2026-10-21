package com.unshoo.pixelmusic.presentation.components.subcomps

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import coil.size.Size
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.ShimmerBox
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageCache
import androidx.compose.ui.res.stringResource

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private val DefaultSurfaceShape = RoundedCornerShape(22.dp)
private val PlayingSurfaceShape = RoundedCornerShape(50.dp)
private val DefaultAlbumShape = RoundedCornerShape(10.dp)
private val PlayingAlbumShape = RoundedCornerShape(50.dp)

/**
 * Enhanced song list item with multi-selection support.
 * 
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Callback for more options button
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 50.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val performanceModeEnabled = SmartImageCache.performanceModeEnabled
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() }
    val isHighlighted = isCurrentSong && !isLoading
    val isStaticState = performanceModeEnabled || (!isHighlighted && !isSelected)

    val colors = MaterialTheme.colorScheme
    val baseContainerColor = containerColorOverride ?: colors.surfaceContainerLow
    val baseContentColor = colors.onSurface

    val surfaceShape: androidx.compose.ui.graphics.Shape
    val albumShape: androidx.compose.ui.graphics.Shape
    val containerColor: Color
    val contentColor: Color
    val selectionBorderWidth: Dp
    val selectionBorderColor: Color
    val mvContainerColor: Color
    val mvContentColor: Color
    val selectionOverlayColor: Color
    val selectionOverlayContentColor: Color
    val showSelectionDecoration: Boolean
    val selectionScaleProgressState: State<Float>

    if (isStaticState) {
        selectionScaleProgressState = rememberUpdatedState(if (isSelected) 1f else 0f)
        if (isSelected) {
            surfaceShape = customShape ?: DefaultSurfaceShape
            albumShape = DefaultAlbumShape
            containerColor = colors.secondaryContainer
            contentColor = colors.onSecondaryContainer
            selectionBorderWidth = 2.5.dp
            selectionBorderColor = colors.primary
            mvContainerColor = colors.onSurface
            mvContentColor = colors.surfaceContainerHigh
            selectionOverlayColor = colors.primary.copy(alpha = 0.7f)
            selectionOverlayContentColor = colors.onPrimary
            showSelectionDecoration = true
        } else if (isHighlighted) {
            surfaceShape = customShape ?: PlayingSurfaceShape
            albumShape = PlayingAlbumShape
            containerColor = colors.primaryContainer
            contentColor = colors.onPrimaryContainer
            selectionBorderWidth = 0.dp
            selectionBorderColor = Color.Transparent
            mvContainerColor = colors.primaryContainer
            mvContentColor = colors.onPrimaryContainer
            selectionOverlayColor = Color.Transparent
            selectionOverlayContentColor = Color.Transparent
            showSelectionDecoration = false
        } else {
            surfaceShape = customShape ?: DefaultSurfaceShape
            albumShape = DefaultAlbumShape
            containerColor = baseContainerColor
            contentColor = baseContentColor
            selectionBorderWidth = 0.dp
            selectionBorderColor = Color.Transparent
            mvContainerColor = colors.onSurface
            mvContentColor = colors.surfaceContainerHigh
            selectionOverlayColor = Color.Transparent
            selectionOverlayContentColor = Color.Transparent
            showSelectionDecoration = false
        }
    } else {
        val animationTarget = remember(isHighlighted, isSelected) {
            EnhancedSongAnimationTarget(
                isHighlighted = isHighlighted,
                isSelected = isSelected
            )
        }
        val transition = updateTransition(
            targetState = animationTarget,
            label = "EnhancedSongListItemTransition"
        )
        val highlightProgress = transition.animateFloat(
            transitionSpec = { tween(durationMillis = 400) },
            label = "highlightProgress"
        ) { state ->
            if (state.isHighlighted) 1f else 0f
        }.value
        val selectionVisualProgress = transition.animateFloat(
            transitionSpec = { tween(durationMillis = 250) },
            label = "selectionVisualProgress"
        ) { state ->
            if (state.isSelected) 1f else 0f
        }.value
        selectionScaleProgressState = transition.animateFloat(
            transitionSpec = {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            },
            label = "selectionScaleProgress"
        ) { state ->
            if (state.isSelected) 1f else 0f
        }

        val animatedCornerRadius = lerpDp(22.dp, 50.dp, highlightProgress)
        val animatedAlbumCornerRadius = lerpDp(10.dp, 50.dp, highlightProgress)
        selectionBorderWidth = lerpDp(0.dp, 2.5.dp, selectionVisualProgress)

        surfaceShape = customShape ?: RoundedCornerShape(animatedCornerRadius)
        albumShape = RoundedCornerShape(animatedAlbumCornerRadius)

        val playbackContainerColor = lerpColor(baseContainerColor, colors.primaryContainer, highlightProgress)
        containerColor = lerpColor(playbackContainerColor, colors.secondaryContainer, selectionVisualProgress)

        val playbackContentColor = lerpColor(baseContentColor, colors.onPrimaryContainer, highlightProgress)
        contentColor = lerpColor(playbackContentColor, colors.onSecondaryContainer, selectionVisualProgress)

        selectionBorderColor = lerpColor(colors.primary.copy(alpha = 0f), colors.primary, selectionVisualProgress)
        mvContainerColor = lerpColor(colors.onSurface, colors.primaryContainer, highlightProgress)
        mvContentColor = lerpColor(colors.surfaceContainerHigh, colors.onPrimaryContainer, highlightProgress)
        selectionOverlayColor = lerpColor(Color.Transparent, colors.primary.copy(alpha = 0.7f), selectionVisualProgress)
        selectionOverlayContentColor = lerpColor(Color.Transparent, colors.onPrimary, selectionVisualProgress)
        showSelectionDecoration = selectionVisualProgress > 0.001f
    }

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = containerColorOverride ?: colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else 4.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Actual Song Item Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = if (isStaticState && !isSelected) {
                        1f
                    } else {
                        lerpFloat(1f, 0.98f, selectionScaleProgressState.value)
                    }
                    scaleX = scale
                    scaleY = scale
                }
                .clip(surfaceShape)
                .then(
                    if (showSelectionDecoration) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onLongPress()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                ),
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    val resolvedTargetSize = remember(albumArtTargetSizePx) {
                        val clamped = albumArtTargetSizePx.coerceIn(48, 160)
                        Size(clamped, clamped)
                    }
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = albumShape,
                            targetSize = resolvedTargetSize,
                            crossfadeDurationMillis = 0,
                            isThumbnail = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Selection check overlay on album art
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = selectionOverlayColor,
                                        shape = albumShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = selectionOverlayContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.presentation_batch_g_list_cd_selected),
                                        tint = selectionOverlayContentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    // Keep list rows cheap: marquee/subcompose per current item can jank scrolling
                    // when playback changes while the user is browsing. Full player still uses marquee.
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = contentColor,
                        overflow = TextOverflow.Ellipsis
                    )
                    val isLossless = song.id.startsWith("monochrome_") || 
                        song.contentUriString.startsWith("monochrome://") || 
                        song.contentUriString.endsWith(".flac", ignoreCase = true)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLossless) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "FLAC",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = song.displayArtist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = showMoreOptionsButton && !isSelectionMode

                if (showPlayingIndicator) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }

                if (showPlayingIndicator || showTrailingAction) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (showTrailingAction) {
                    FilledIconButton(
                        onClick = { onMoreOptionsClick(song) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = mvContentColor,
                            contentColor = mvContainerColor
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.presentation_batch_g_list_cd_more_for_title, song.title),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
