package com.unshoo.pixelmusic.presentation.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.unshoo.pixelmusic.R
import androidx.compose.runtime.collectAsState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.AlbumArtQuality

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmartImageEntryPoint {
    fun connectivityStateHolder(): ConnectivityStateHolder
    fun userPreferencesRepository(): UserPreferencesRepository
}

val SmartImageCompactListTargetSize = Size(96, 96)
val SmartImageListTargetSize = Size(128, 128)
private val DefaultSmartImageSize = Size(300, 300)

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.ic_music_placeholder,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = true,
    targetSize: Size = DefaultSmartImageSize,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    placeholderModel: Any? = null,
    placeHolderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, SmartImageEntryPoint::class.java)
    }
    val connectivityStateHolder = entryPoint.connectivityStateHolder()
    val userPreferencesRepository = entryPoint.userPreferencesRepository()

    val isMeteredNetwork by connectivityStateHolder.isMeteredNetwork.collectAsState()
    val albumArtQualityWifi by userPreferencesRepository.albumArtQualityFlow.collectAsState(initial = AlbumArtQuality.ORIGINAL)
    val albumArtQualityMobile by userPreferencesRepository.albumArtQualityMobileFlow.collectAsState(initial = AlbumArtQuality.ORIGINAL)
    val performanceModeEnabled by userPreferencesRepository.performanceModeEnabledFlow.collectAsState(initial = false)

    val effectiveQuality = if (performanceModeEnabled) {
        AlbumArtQuality.LOW
    } else if (isMeteredNetwork) {
        albumArtQualityMobile
    } else {
        albumArtQualityWifi
    }

    val clippedModifier = modifier.clip(shape)
    val requestTargetSize = remember(targetSize, effectiveQuality) {
        val baseSize = safeAlbumArtTargetSize(targetSize)
        val maxSize = effectiveQuality.maxSize
        if (maxSize > 0) {
            val widthPx = (baseSize.width as? coil.size.Dimension.Pixels)?.px ?: maxSize
            val heightPx = (baseSize.height as? coil.size.Dimension.Pixels)?.px ?: maxSize
            val clampedW = if (widthPx > maxSize) maxSize else widthPx
            val clampedH = if (heightPx > maxSize) maxSize else heightPx
            Size(clampedW, clampedH)
        } else {
            coil.size.Size.ORIGINAL
        }
    }

    // Handle direct models (Bitmap, Vector, etc) early to avoid ImageRequest overhead
    if (model == null || model is ImageVector || model is Painter || model is ImageBitmap || model is Bitmap) {
        if (model == null) {
            Placeholder(
                modifier = clippedModifier,
                drawableResId = placeholderResId,
                contentDescription = contentDescription,
                containerColor = placeHolderBackgroundColor,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                alpha = alpha
            )
        } else {
            handleDirectModel(
                data = model,
                modifier = clippedModifier,
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
        }
        return
    }

    val request = remember(
        context,
        model,
        crossfadeDurationMillis,
        useDiskCache,
        useMemoryCache,
        allowHardware,
        requestTargetSize
    ) {
        if (model is ImageRequest) {
            model.newBuilder(context)
                .size(requestTargetSize)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(crossfadeDurationMillis)
                .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .allowHardware(allowHardware)
                .size(requestTargetSize)
                .build()
        }
    }

    if (onState != null || placeholderModel != null) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = clippedModifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) {
            val state = painter.state
            LaunchedEffect(state) {
                onState?.invoke(state)
            }

            when (state) {
                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent()
                }
                is AsyncImagePainter.State.Loading -> {
                    if (placeholderModel != null) {
                        AsyncImage(
                            model = placeholderModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale,
                            colorFilter = colorFilter,
                            alpha = alpha
                        )
                    } else {
                        Placeholder(
                            modifier = Modifier.fillMaxSize(),
                            drawableResId = placeholderResId,
                            contentDescription = contentDescription,
                            containerColor = placeHolderBackgroundColor,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            alpha = alpha
                        )
                    }
                }
                else -> {
                    Placeholder(
                        modifier = Modifier.fillMaxSize(),
                        drawableResId = if (state is AsyncImagePainter.State.Error) errorResId else placeholderResId,
                        contentDescription = contentDescription,
                        containerColor = placeHolderBackgroundColor,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        alpha = alpha
                    )
                }
            }
        }
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = clippedModifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha,
            placeholder = painterResource(placeholderResId),
            error = painterResource(errorResId)
        )
    }
}

@Composable
private fun handleDirectModel(
    data: Any?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    alpha: Float
): Any? {
    return when (data) {
        is ImageVector -> {
            Image(
                imageVector = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Painter -> {
            Image(
                painter = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is ImageBitmap -> {
            Image(
                bitmap = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Bitmap -> {
            Image(
                bitmap = data.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        else -> null
    }
}

@Composable
private fun Placeholder(
    modifier: Modifier,
    @DrawableRes drawableResId: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    alpha: Float,
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(drawableResId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit
        )
    }
}
