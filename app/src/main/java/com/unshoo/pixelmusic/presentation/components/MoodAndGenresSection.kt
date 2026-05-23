package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint
import unshoo.ianshulyadav.pixelmusic.innertube.pages.MoodAndGenres
import java.util.concurrent.ConcurrentHashMap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

private val moodArtworkCache = ConcurrentHashMap<String, String>()
private val MoodCardShape = RoundedCornerShape(24.dp)
private val MoodCoverShape = RoundedCornerShape(16.dp)
val MoodAndGenresCardHeight = 96.dp

@Composable
fun MoodAndGenresSection(
    items: List<MoodAndGenres.Item>,
    onItemClick: (MoodAndGenres.Item) -> Unit,
    modifier: Modifier = Modifier
) {
    val columnsCount = 2
    val chunkedItems = remember(items) { items.chunked(columnsCount) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chunkedItems.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    MoodAndGenresCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    )
                }
                if (rowItems.size < columnsCount) {
                    repeat(columnsCount - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f).padding(4.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun MoodAndGenresCard(
    item: MoodAndGenres.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val base = remember(item.stripeColor) { Color(item.stripeColor) }
    val artworkUrl = rememberMoodArtworkUrl(item.endpoint)
    val context = LocalContext.current
    val density = LocalDensity.current
    val reqPx = remember(density) { with(density) { 90.dp.roundToPx() } }
    val artworkModel = remember(artworkUrl, context, reqPx) {
        if (artworkUrl == null) null
        else ImageRequest.Builder(context)
            .data(artworkUrl)
            .memoryCacheKey("mood_and_genres:${item.endpoint?.browseId.orEmpty()}")
            .diskCacheKey("mood_and_genres:${item.endpoint?.browseId.orEmpty()}")
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(reqPx)
            .build()
    }

    val cardStart = remember(base, colorScheme.primaryContainer) {
        lerp(base, colorScheme.primaryContainer, 0.18f)
    }
    val cardEnd = remember(base, colorScheme.surfaceContainerHighest) {
        lerp(base, colorScheme.surfaceContainerHighest, 0.34f)
    }
    val topGlow = remember(base) { lerp(base, Color.White, 0.24f).copy(alpha = 0.26f) }
    val coverStart = remember(base) { lerp(base, Color.White, 0.36f) }
    val coverEnd = remember(base, colorScheme.scrim) { lerp(base, colorScheme.scrim, 0.2f) }
    val coverAccent = remember(base, colorScheme.tertiary) {
        lerp(base, colorScheme.tertiary, 0.16f).copy(alpha = 0.5f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 560f),
        label = "MoodCardScale"
    )
    val coverRotation by animateFloatAsState(
        targetValue = if (isPressed) 14f else 21f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f),
        label = "MoodCoverRot"
    )
    val coverShadowPx = with(density) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .height(MoodAndGenresCardHeight)
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .clip(MoodCardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(cardStart, cardEnd),
                    start = Offset.Zero,
                    end = Offset(900f, 650f)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        // Ambient glow + depth overlay
        Box(
            modifier = Modifier.fillMaxSize().drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(topGlow, Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.16f),
                    radius = size.minDimension * 0.95f
                )
                val depth = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                    startY = size.height * 0.24f, endY = size.height
                )
                onDrawBehind { drawRect(glow); drawRect(depth) }
            }
        )
        // Background cover (ghost)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 32.dp)
                .size(80.dp)
                .graphicsLayer {
                    alpha = 0.24f; rotationZ = 13f
                    shape = MoodCoverShape; clip = true
                    transformOrigin = TransformOrigin(1f, 0f)
                }
        ) {
            if (artworkModel != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Front cover with animated rotation + shadow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .size(90.dp)
                .graphicsLayer {
                    rotationZ = coverRotation
                    shadowElevation = coverShadowPx
                    ambientShadowColor = base.copy(alpha = 0.28f)
                    spotShadowColor = base.copy(alpha = 0.42f)
                    shape = MoodCoverShape; clip = true
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(coverStart, coverEnd),
                        start = Offset.Zero, end = Offset(560f, 560f)
                    )
                )
        ) {
            if (artworkModel != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.fillMaxSize().drawWithCache {
                val sheen = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
                val accent = Brush.radialGradient(
                    colors = listOf(coverAccent, Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.22f),
                    radius = size.minDimension * 0.44f
                )
                onDrawBehind { drawRect(sheen); drawRect(accent) }
            })
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 86.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun rememberMoodArtworkUrl(endpoint: BrowseEndpoint?): String? {
    endpoint ?: return null
    val cacheKey = "${endpoint.browseId}:${endpoint.params.orEmpty()}"
    val cached = moodArtworkCache[cacheKey]
    val url by produceState(initialValue = cached, key1 = cacheKey) {
        if (!value.isNullOrBlank()) return@produceState
        val resolved = withContext(Dispatchers.IO) {
            YouTube.browse(endpoint.browseId, endpoint.params).getOrNull()?.thumbnail
        }
        if (!resolved.isNullOrBlank()) {
            moodArtworkCache[cacheKey] = resolved
            value = resolved
        }
    }
    return url
}
