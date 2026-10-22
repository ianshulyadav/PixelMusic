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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import coil.memory.MemoryCache
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmartImageEntryPoint {
    fun connectivityStateHolder(): ConnectivityStateHolder
    fun userPreferencesRepository(): UserPreferencesRepository
}

// OPTIMIZED: Static pre-compiled regex to avoid allocation per SmartImage composable
private object SmartImageRegex {
    val sizeParamRegex = Regex("=[ws]\\d+.*")
    val sizeParamRegex2 = Regex("/[ws]\\d+.*")
}

val SmartImageCompactListTargetSize = Size(96, 96)
val SmartImageListTargetSize = Size(128, 128)
val SmartImageCardTargetSize = Size(512, 512)
val SmartImageLargeCardTargetSize = Size(1024, 1024)
private val DefaultSmartImageSize = Size(512, 512)

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.ic_music_placeholder,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    // BUGFIX (was: 300ms crossfade everywhere): the previous default
    // of 300ms applied to every thumbnail in every LazyColumn, which
    // meant scrolling the library fired 6-8 crossfade animations per
    // second, each forcing a recomposition of the surrounding card.
    // 120ms is long enough to look polished on the full-player cover,
    // and short enough that scrolling lists don't visibly queue.
    crossfadeDurationMillis: Int = 220,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = true,
    targetSize: Size = DefaultSmartImageSize,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    placeholderModel: Any? = null,
    placeHolderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    isThumbnail: Boolean = false,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    if (!SmartImageCache.isInitialized) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, SmartImageEntryPoint::class.java)
        SmartImageCache.initialize(entryPoint.connectivityStateHolder(), entryPoint.userPreferencesRepository())
    }

    val isMeteredNetwork = SmartImageCache.isMeteredNetwork
    val albumArtQualityWifi = SmartImageCache.albumArtQualityWifi
    val albumArtQualityMobile = SmartImageCache.albumArtQualityMobile
    val performanceModeEnabled = SmartImageCache.performanceModeEnabled

    val effectiveQuality = if (performanceModeEnabled) {
        AlbumArtQuality.LOW
    } else if (isMeteredNetwork) {
        albumArtQualityMobile
    } else {
        albumArtQualityWifi
    }

    val density = LocalDensity.current.density
    val clippedModifier = modifier.clip(shape)
    val requestTargetSize = remember(targetSize, effectiveQuality, isThumbnail) {
        val effectiveTargetSize = if (isThumbnail && targetSize == DefaultSmartImageSize) {
            SmartImageListTargetSize
        } else {
            targetSize
        }
        val baseSize = safeAlbumArtTargetSize(effectiveTargetSize)
        val maxSize = if (isThumbnail) {
            256
        } else {
            effectiveQuality.maxSize
        }
        val rawW = (baseSize.width as? coil.size.Dimension.Pixels)?.px
        val rawH = (baseSize.height as? coil.size.Dimension.Pixels)?.px
        val limit = if (maxSize > 0) maxSize else MaxSafeAlbumArtDimensionPx
        val finalW = if (rawW != null) rawW.coerceIn(1, limit) else limit
        val finalH = if (rawH != null) rawH.coerceIn(1, limit) else limit
        Size(finalW, finalH)
    }

    // Handle direct models (Bitmap, Vector, etc) early to avoid ImageRequest overhead
    if (model == null || model is ImageVector || model is Painter || model is ImageBitmap || model is Bitmap) {
        if (model == null) {
            if (placeholderResId != 0) {
                Placeholder(
                    modifier = clippedModifier,
                    drawableResId = placeholderResId,
                    contentDescription = contentDescription,
                    containerColor = placeHolderBackgroundColor,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    alpha = alpha
                )
            } else {
                Box(modifier = clippedModifier.background(placeHolderBackgroundColor))
            }
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

    // Size-qualified memory cache key so thumbnail and full-size bitmaps don't collide or evict each other
    val memoryCacheKey = remember(model, requestTargetSize, isThumbnail) {
        if (model is String) {
            val w = (requestTargetSize.width as? coil.size.Dimension.Pixels)?.px ?: 0
            val h = (requestTargetSize.height as? coil.size.Dimension.Pixels)?.px ?: 0
            if (isThumbnail) {
                MemoryCache.Key("${model}_thumb_${w}x${h}")
            } else if (w > 0 && h > 0) {
                MemoryCache.Key("${model}_${w}x${h}")
            } else {
                MemoryCache.Key(model)
            }
        } else {
            null
        }
    }

    val request = remember(
        context,
        model,
        crossfadeDurationMillis,
        useDiskCache,
        useMemoryCache,
        allowHardware,
        requestTargetSize,
        memoryCacheKey,
        isThumbnail
    ) {
        val optimizedModel = if (model is String && (model.contains("googleusercontent.com") || model.contains("ggpht.com"))) {
            val widthPx = (requestTargetSize.width as? coil.size.Dimension.Pixels)?.px ?: (if (isThumbnail) 128 else 512)
            val heightPx = (requestTargetSize.height as? coil.size.Dimension.Pixels)?.px ?: (if (isThumbnail) 128 else 512)
            val sizeStr = if (isThumbnail) {
                val thumbDim = maxOf(widthPx, heightPx).coerceIn(96, 256)
                "=w$thumbDim-h$thumbDim-c-rj"
            } else if (widthPx >= 800 || heightPx >= 800) {
                "=w800-h800-l90-rj"
            } else {
                val cardDim = maxOf(widthPx, heightPx).coerceIn(256, 512)
                "=w$cardDim-h$cardDim-c-rj"
            }
            if (SmartImageRegex.sizeParamRegex.containsMatchIn(model)) {
                model.replace(SmartImageRegex.sizeParamRegex, sizeStr)
            } else if (SmartImageRegex.sizeParamRegex2.containsMatchIn(model)) {
                model.replace(SmartImageRegex.sizeParamRegex2, sizeStr.replace("=", "/"))
            } else if (model.contains("=")) {
                model.substringBeforeLast("=") + sizeStr
            } else {
                "$model$sizeStr"
            }
        } else if (model is String && (model.contains("i.ytimg.com") || model.contains("img.youtube.com"))) {
            val match = Regex("/vi(?:_webp)?/([^/]+)/").find(model)
            if (match != null) {
                val videoId = match.groupValues[1]
                if (isThumbnail) {
                    "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                } else {
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                }
            } else {
                model
            }
        } else if (model is String && model.startsWith("/")) {
            java.io.File(model)
        } else {
            model
        }

        val effectiveCrossfade = crossfadeDurationMillis

        if (optimizedModel is ImageRequest) {
            optimizedModel.newBuilder(context)
                .size(requestTargetSize)
                .crossfade(effectiveCrossfade)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(optimizedModel)
                .crossfade(effectiveCrossfade)
                .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .allowHardware(allowHardware)
                .apply {
                    if (allowHardware && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        bitmapConfig(Bitmap.Config.HARDWARE)
                    }
                }
                .size(requestTargetSize)
                // PERF: use the URL as a memory cache key so that returning
                // to a tab or scrolling rapidly instantly shows the cached bitmap
                // instead of flashing a blank/decode frame or firing recomposition animations.
                .apply {
                    if (memoryCacheKey != null) {
                        memoryCacheKey(memoryCacheKey)
                        placeholderMemoryCacheKey(memoryCacheKey)
                    }
                }
                .build()
        }
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = clippedModifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        placeholder = if (placeholderResId != 0) painterResource(placeholderResId) else null,
        error = if (errorResId != 0) painterResource(errorResId) else null,
        onLoading = { state -> onState?.invoke(state) },
        onSuccess = { state -> onState?.invoke(state) },
        onError = { state -> onState?.invoke(state) }
    )
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

object SmartImageCache {
    var isMeteredNetwork by androidx.compose.runtime.mutableStateOf(false)
    var albumArtQualityWifi by androidx.compose.runtime.mutableStateOf(AlbumArtQuality.ORIGINAL)
    var albumArtQualityMobile by androidx.compose.runtime.mutableStateOf(AlbumArtQuality.ORIGINAL)
    var performanceModeEnabled by androidx.compose.runtime.mutableStateOf(false)

    @Volatile
    var isInitialized = false
        private set(value) { field = value }
    private val cacheScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )

    fun initialize(
        connectivityStateHolder: ConnectivityStateHolder,
        userPreferencesRepository: UserPreferencesRepository
    ) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
        }

        // Pre-warm with initial values from ConnectivityStateHolder
        isMeteredNetwork = connectivityStateHolder.isMeteredNetwork.value

        cacheScope.launch {
            connectivityStateHolder.isMeteredNetwork.collect { isMeteredNetwork = it }
        }
        cacheScope.launch {
            userPreferencesRepository.albumArtQualityFlow.collect { albumArtQualityWifi = it }
        }
        cacheScope.launch {
            userPreferencesRepository.albumArtQualityMobileFlow.collect { albumArtQualityMobile = it }
        }
        cacheScope.launch {
            userPreferencesRepository.performanceModeEnabledFlow.collect { performanceModeEnabled = it }
        }
    }
}
