package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.annotation.OptIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.getNavigationBarHeight
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import com.unshoo.pixelmusic.presentation.utils.GenreIconProvider
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

@OptIn(UnstableApi::class)
@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_genres_available), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val systemNavBarHeight = getNavigationBarHeight()
    val customGenreIcons = playerViewModel.customGenreIcons.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
        context = kotlin.coroutines.EmptyCoroutineContext
    ).value

    val isGridView by playerViewModel.isGenreGridView.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTR = 70,
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTL = 70,
        cornerRadiusBR = 0.dp,
        smoothnessAsPercentBR = 70,
        cornerRadiusBL = 0.dp,
        smoothnessAsPercentBL = 70
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(shape)
            .padding(
                top = 8.dp,
                bottom = 28.dp + resolveNavBarOccupiedHeight(systemNavBarHeight, navBarCompactMode) + MiniPlayerHeight
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                style = MaterialTheme.typography.titleLarge
            )
            
            val toggleShape = androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (!isGridView) 12f else 50f,
                label = "shapeAnimation"
            )
            
            androidx.compose.material3.FilledIconButton(
                onClick = { playerViewModel.toggleGenreViewMode() },
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(toggleShape.value.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = "Toggle Grid/List View"
                )
            }
        }

        if (isGridView) {
            val columns = 2
            val chunkedGenres = remember(genres) { genres.chunked(columns) }
            chunkedGenres.forEach { rowGenres ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowGenres.forEach { genre ->
                        Box(modifier = Modifier.weight(1f)) {
                            GenreCard(
                                genre = genre,
                                customIcons = customGenreIcons,
                                onClick = { onGenreClick(genre) },
                                isGridView = true
                            )
                        }
                    }
                    if (rowGenres.size < columns) {
                        repeat(columns - rowGenres.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            genres.forEach { genre ->
                GenreCard(
                    genre = genre,
                    customIcons = customGenreIcons,
                    onClick = { onGenreClick(genre) },
                    isGridView = false
                )
            }
        }
    }
}


@Composable
private fun GenreCard(
    genre: Genre,
    customIcons: Map<String, Int>,
    onClick: () -> Unit,
    isGridView: Boolean
) {
    val isDark = LocalPixelMusicDarkTheme.current
    val themeColor = remember(genre, isDark) {
        com.unshoo.pixelmusic.ui.theme.GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = isDark,
            fallbackGenreId = genre.id
        )
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    val shape = RoundedCornerShape(20.dp)

//    val shape = AbsoluteSmoothCornerShape(
//        cornerRadiusTR = 24.dp,
//        smoothnessAsPercentTL = 70,
//        cornerRadiusTL = 24.dp,
//        smoothnessAsPercentTR = 70,
//        cornerRadiusBR = 24.dp,
//        smoothnessAsPercentBL = 70,
//        cornerRadiusBL = 24.dp,
//        smoothnessAsPercentBR = 70
//    )

    // Layout Modifier Logic
    val cardModifier = if (isGridView) {
        Modifier.aspectRatio(1.2f)
    } else {
        Modifier.fillMaxWidth().height(100.dp) // Fixed height for list view, full width
    }

    Card(
        modifier = cardModifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
        ) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titleStartPadding = 14.dp
            val titleEndPadding = 14.dp
            val titlePresentation = remember(
                genre.id,
                genre.name,
                isGridView,
                maxWidth,
                density.density,
                density.fontScale
            ) {
                GenreTypography.resolveTitlePresentation(
                    genreId = genre.id,
                    genreName = genre.name,
                    isGridView = isGridView,
                    cardWidthPx = with(density) { maxWidth.roundToPx() },
                    horizontalPaddingPx = with(density) { titleStartPadding.roundToPx() },
                    textMeasurer = textMeasurer
                )
            }

            // Imagen del género en esquina inferior derecha
            Box(
                modifier = Modifier
                    .size(90.dp) 
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 16.dp) 
            ) {
                SmartImage(
                    model = GenreIconProvider.getGenreImageResource(genre.name, customIcons),
                    contentDescription = "Genre illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    colorFilter = ColorFilter.tint(onBackgroundColor),
                    contentScale = ContentScale.Crop
                )
            }

            // Nombre del género en esquina superior izquierda
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = titleStartPadding, top = 14.dp, end = titleEndPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = titlePresentation.firstLine,
                    style = titlePresentation.style,
                    color = onBackgroundColor,
                    softWrap = false,
                    minLines = 1,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                titlePresentation.secondLine?.let { secondLine ->
                    Text(
                        text = secondLine,
                        style = titlePresentation.style,
                        color = onBackgroundColor,
                        softWrap = false,
                        minLines = 1,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(titlePresentation.secondLineWidthFraction)
                    )
                }
            }
        }
    }
}
