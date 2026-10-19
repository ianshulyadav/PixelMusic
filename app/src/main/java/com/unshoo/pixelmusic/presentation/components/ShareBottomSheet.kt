@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_API_USAGE_ERROR", "EXPERIMENTAL_IS_NOT_ENABLED")
package com.unshoo.pixelmusic.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.DarkColorScheme
import com.unshoo.pixelmusic.ui.theme.LightColorScheme
import com.unshoo.pixelmusic.presentation.viewmodel.ThemeStateHolder
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import com.unshoo.pixelmusic.utils.AudioMetaUtils.mimeTypeToFormat
import com.unshoo.pixelmusic.utils.formatDuration
import java.util.Locale
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.snapchat.kit.sdk.SnapCreative
import com.snapchat.kit.sdk.creative.models.SnapPhotoContent
import com.snapchat.kit.sdk.creative.models.SnapVideoContent

private const val GITHUB_LINK = "https://github.com/ianshulyadav/PixelMusic"
private const val SNAPCHAT_PACKAGE = "com.snapchat.android"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"

/**
 * Spotify-inspired dynamic background themes for the 9:16 shared card
 */
enum class ShareThemeStyle(val displayName: String) {
    DYNAMIC_PALETTE("Dynamic Player"),
    SOOTHING_GRADIENT("Gradient"),
    BLURRED_ARTWORK("Artwork Blur"),
    MIDNIGHT_MINIMAL("Midnight"),
    VIBRANT_GLOW("Vibrant Accent")
}

/**
 * Utility to clean raw LRC lyric timestamps and metadata headers
 */
object LyricCleaner {
    fun clean(rawLyrics: String?): List<String> {
        if (rawLyrics.isNullOrBlank()) return emptyList()
        // Strip metadata headers like [ti:Title], [ar:Artist], or [offset:0]
        val noMeta = rawLyrics.replace(Regex("(?m)^\\[[a-zA-Z]+:.*\\]\\r?\\n?"), "")
        // Strip timestamp tags like [00:12.34], [01:23], [00:12.345] or empty brackets
        val noTimestamps = noMeta.replace(Regex("\\[\\d{2}:\\d{2}(?:\\.\\d{1,3})?\\]"), "")
        return noTimestamps.lines()
            .map { it.trim().replace("\"", "").replace("“", "").replace("”", "").trim() }
            // Filter out blank lines and noise starting with bracket tags
            .filter { it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("(") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ShareBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    lyricsLines: List<String> = emptyList(),
    formatTag: String? = null,
    playbackMimeType: String? = null,
    playbackSampleRate: Int? = null,
    playbackBitDepth: Int? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, ShareBottomSheetEntryPoint::class.java)
    }
    val themeStateHolder = entryPoint.themeStateHolder()
    val albumColorSchemeState by themeStateHolder.getAlbumColorSchemeFlow(song.albumArtUriString.orEmpty()).collectAsStateWithLifecycle(initialValue = null)
    val userPreferencesRepository = remember(entryPoint) { entryPoint.userPreferencesRepository() }
    val useBlurEffects by userPreferencesRepository.useBlurEffectsFlow.collectAsStateWithLifecycle(initialValue = true)
    val storyCardBlurEnabled by userPreferencesRepository.storyCardBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val isStoryCardBlurActive = useBlurEffects && storyCardBlurEnabled
    val bottomSheetBlurEnabled by userPreferencesRepository.bottomSheetBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val isBottomSheetBlurActive = useBlurEffects && bottomSheetBlurEnabled

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Card mode: 0 = Song Card, 1 = Lyrics Card
    var selectedCardMode by remember { mutableStateOf(0) }
    // Lyrics card style: false = Glass, true = Solid Color
    var useSolidLyricsCard by remember { mutableStateOf(false) }
    // Song card theme mode: true = Dark palette, false = Light palette
    var isCardDark by remember { mutableStateOf(true) }

    // Lyric state
    val cleanedLyrics = remember(song.lyrics, lyricsLines) {
        lyricsLines.ifEmpty { LyricCleaner.clean(song.lyrics) }
    }
    val hasLyrics = remember(cleanedLyrics) { cleanedLyrics.isNotEmpty() }
    val selectedLyrics = remember { mutableStateListOf<String>() }

    // Initialize with first 3 lines if available to provide a gorgeous preview immediately
    LaunchedEffect(cleanedLinesInit@ cleanedLyrics) {
        if (selectedLyrics.isEmpty() && cleanedLyrics.isNotEmpty()) {
            selectedLyrics.addAll(cleanedLyrics.take(3))
        }
    }

    // Active theme style for the background
    var activeThemeStyle by remember { mutableStateOf(ShareThemeStyle.DYNAMIC_PALETTE) }

    var isCapturing by remember { mutableStateOf(false) }
    val captureController = rememberCaptureController()

    val snapchatInstalled = remember { isPackageInstalled(context, SNAPCHAT_PACKAGE) }
    val instagramInstalled = remember { isPackageInstalled(context, INSTAGRAM_PACKAGE) }

    // Primary player theme colors
    val primaryColor = colorScheme.primary
    val onPrimaryColor = colorScheme.onPrimary
    val primaryContainerColor = colorScheme.primaryContainer
    val onPrimaryContainerColor = colorScheme.onPrimaryContainer
    val secondaryColor = colorScheme.secondary
    val tertiaryColor = colorScheme.tertiary
    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 24.dp, smoothnessAsPercentBR = 60,
        cornerRadiusBR = 24.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTL = 24.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBL = 24.dp, smoothnessAsPercentTR = 60
    )

    // Capture bitmap and run share operation (scaled to high quality 1080x1920 9:16)
    fun captureAndShare(action: suspend (Bitmap) -> Unit) {
        isCapturing = true
        scope.launch {
            try {
                val rawBitmap = captureController.captureAsync().await().asAndroidBitmap()
                val targetWidth = 1080
                val targetHeight = 1920
                val scaledBitmap = if (rawBitmap.width != targetWidth || rawBitmap.height != targetHeight) {
                    Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                } else {
                    rawBitmap
                }
                action(scaledBitmap)
            } catch (e: Exception) {
                android.util.Log.e("ShareBottomSheet", "Failed to capture card", e)
                Toast.makeText(context, "Failed to capture card", Toast.LENGTH_SHORT).show()
            } finally {
                isCapturing = false
            }
        }
    }

    // Save generated bitmap to cache (1080x1920 9:16 PNG)
    suspend fun saveBitmapToCache(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "share_cards").also { it.mkdirs() }
        val file = File(cacheDir, "pixelmusic_share_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    val sheetShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 28.dp, smoothnessAsPercentBR = 60,
            cornerRadiusBR = 0.dp, smoothnessAsPercentTL = 60,
            cornerRadiusTL = 28.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 0.dp, smoothnessAsPercentTR = 60
        )
    }
    val sheetContainerColor = if (isBottomSheetBlurActive) colorScheme.surfaceContainer.copy(alpha = 0.82f) else colorScheme.surfaceContainer
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        ApplyBottomSheetWindowBlur(isBottomSheetBlurActive)
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .background(sheetContainerColor)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                // Custom Drag Handle inside the Column
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }
                // ── Header (Title + Light/Dark Mode Switcher) ───────────────
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.share_sheet_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Light / Dark Theme Switcher Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Light Option
                            val lightSelected = !isCardDark
                            val lightBgColor by animateColorAsState(
                                targetValue = if (lightSelected) primaryColor else Color.Transparent,
                                animationSpec = tween(200),
                                label = "lightOptionBg"
                            )
                            val lightContentColor by animateColorAsState(
                                targetValue = if (lightSelected) onPrimaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(200),
                                label = "lightOptionContent"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(lightBgColor)
                                    .clickable {
                                        if (isCardDark) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isCardDark = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LightMode,
                                        contentDescription = "Light Theme",
                                        tint = lightContentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Light",
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = if (lightSelected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = lightContentColor
                                    )
                                }
                            }

                            // Dark Option
                            val darkSelected = isCardDark
                            val darkBgColor by animateColorAsState(
                                targetValue = if (darkSelected) primaryColor else Color.Transparent,
                                animationSpec = tween(200),
                                label = "darkOptionBg"
                            )
                            val darkContentColor by animateColorAsState(
                                targetValue = if (darkSelected) onPrimaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(200),
                                label = "darkOptionContent"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(darkBgColor)
                                    .clickable {
                                        if (!isCardDark) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isCardDark = true
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DarkMode,
                                        contentDescription = "Dark Theme",
                                        tint = darkContentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Dark",
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = if (darkSelected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = darkContentColor
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── Card Mode Tabs (Song / Lyrics) ──────────────────────────
                if (hasLyrics) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            stringResource(R.string.share_card_tab_song),
                            stringResource(R.string.share_card_tab_lyrics)
                        ).forEachIndexed { index, label ->
                            val isSelected = selectedCardMode == index
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) primaryColor else Color.Transparent,
                                animationSpec = tween(250),
                                label = "tabColor$index"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) onPrimaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(250),
                                label = "tabTextColor$index"
                            )
                            val tabScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.03f else 1f,
                                label = "tabScale$index"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                    }
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedCardMode = index
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Card Style Selector for Lyrics Mode ─────────────────────
                if (selectedCardMode == 1) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.share_card_style),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        
                        // Sleek Segmented Pill Container
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val glassScale by animateFloatAsState(targetValue = if (!useSolidLyricsCard) 1.02f else 1f, label = "glassScale")
                                Surface(
                                    shape = CircleShape,
                                    color = if (!useSolidLyricsCard) primaryColor else Color.Transparent,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = glassScale
                                            scaleY = glassScale
                                        }
                                        .clickable { useSolidLyricsCard = false }
                                ) {
                                    Text(
                                        text = stringResource(R.string.share_glass),
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = if (!useSolidLyricsCard) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (!useSolidLyricsCard) onPrimaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                val solidScale by animateFloatAsState(targetValue = if (useSolidLyricsCard) 1.02f else 1f, label = "solidScale")
                                Surface(
                                    shape = CircleShape,
                                    color = if (useSolidLyricsCard) primaryColor else Color.Transparent,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = solidScale
                                            scaleY = solidScale
                                        }
                                        .clickable { useSolidLyricsCard = true }
                                ) {
                                    Text(
                                        text = stringResource(R.string.share_solid),
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = if (useSolidLyricsCard) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (useSolidLyricsCard) onPrimaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // ── 9:16 Card Preview (Capturable) ──────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = Triple(selectedCardMode, selectedLyrics.toList(), useSolidLyricsCard),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "cardPreview"
                    ) { (mode, lyricsList, solidMode) ->
                        ShareableCard(
                            modifier = Modifier
                                .fillMaxWidth(0.70f)
                                .capturable(captureController),
                            song = song,
                            isLyricsMode = mode == 1,
                            selectedLyrics = lyricsList,
                            themeStyle = activeThemeStyle,
                            colorScheme = colorScheme,
                            cardShape = cardShape,
                            albumColorScheme = albumColorSchemeState,
                            useSolidLyricsCard = solidMode,
                            isCardDark = isCardDark,
                            isBlurEnabled = isStoryCardBlurActive,
                            formatTag = formatTag,
                            playbackMimeType = playbackMimeType,
                            playbackSampleRate = playbackSampleRate,
                            playbackBitDepth = playbackBitDepth,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── Dynamic Theme Selector Carousel (Circular Swatches) ─────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.share_card_theme),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShareThemeStyle.values().forEach { style ->
                            val isSelected = activeThemeStyle == style
                            val outlineColor = if (isSelected) primaryColor else Color.Transparent
                            val borderWidth = if (isSelected) 2.dp else 0.dp
                            val swatchScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1f,
                                label = "swatchScale_${style.name}"
                            )

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .graphicsLayer {
                                        scaleX = swatchScale
                                        scaleY = swatchScale
                                    }
                                    .clip(CircleShape)
                                    .border(borderWidth, outlineColor, CircleShape)
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        activeThemeStyle = style
                                    }
                            ) {
                                // Draw preview inside swatch circle (Always rich dark theme palette)
                                val darkPalette = albumColorSchemeState?.dark ?: DarkColorScheme
                                when (style) {
                                    ShareThemeStyle.DYNAMIC_PALETTE -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            darkPalette.primaryContainer,
                                                            darkPalette.secondaryContainer.copy(alpha = 0.6f),
                                                            darkPalette.surfaceContainerLow
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    ShareThemeStyle.BLURRED_ARTWORK -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            SmartImage(
                                                model = song.albumArtUriString,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                            Modifier.blur(10.dp)
                                                        } else Modifier
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.25f))
                                            )
                                        }
                                    }
                                    ShareThemeStyle.SOOTHING_GRADIENT -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            darkPalette.primary.copy(alpha = 0.85f),
                                                            darkPalette.tertiary.copy(alpha = 0.7f),
                                                            darkPalette.surfaceContainerHighest
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    ShareThemeStyle.MIDNIGHT_MINIMAL -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF0C0C0C))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .align(Alignment.Center)
                                                    .background(
                                                        brush = Brush.radialGradient(
                                                            colors = listOf(darkPalette.primary.copy(alpha = 0.6f), Color.Transparent)
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                    ShareThemeStyle.VIBRANT_GLOW -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            darkPalette.primary,
                                                            darkPalette.secondary,
                                                            darkPalette.tertiary
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── Interactive Lyric Selector (When Lyric Mode Active) ─────
                if (selectedCardMode == 1 && cleanedLyrics.isNotEmpty()) {
                    LyricLineSelector(
                        lines = cleanedLyrics,
                        selectedLines = selectedLyrics,
                        onToggleLine = { line ->
                            if (selectedLyrics.contains(line)) {
                                selectedLyrics.remove(line)
                            } else {
                                if (selectedLyrics.size < 5) {
                                    selectedLyrics.add(line)
                                } else {
                                    Toast.makeText(context, "Maximum 5 lines allowed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        primaryColor = primaryColor,
                        haptic = haptic
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── Primary Share Actions (Horizontal scroll) ───────────────
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    // Snapchat Story
                    if (snapchatInstalled) {
                        item {
                            ShareActionChip(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_snapchat),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = Color.Unspecified
                                    )
                                },
                                label = stringResource(R.string.share_action_snapchat),
                                containerColor = Color(0xFFFFFC00).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFFD600),
                                onClick = {
                                    captureAndShare { bitmap ->
                                        val file = saveBitmapToCache(bitmap)
                                        val attachmentUrl = if (!song.youtubeId.isNullOrEmpty()) {
                                            "https://music.youtube.com/watch?v=${song.youtubeId}"
                                        } else {
                                            GITHUB_LINK
                                        }
                                        shareToSnapchat(context, file, attachmentUrl)
                                    }
                                }
                            )
                        }
                    }

                    // Instagram Story
                    if (instagramInstalled) {
                        item {
                            ShareActionChip(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_instagram),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = Color.Unspecified
                                    )
                                },
                                label = stringResource(R.string.share_action_instagram),
                                containerColor = Color(0xFFE1306C).copy(alpha = 0.12f),
                                contentColor = Color(0xFFE1306C),
                                onClick = {
                                    captureAndShare { bitmap ->
                                        val file = saveBitmapToCache(bitmap)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val activeDarkScheme = albumColorSchemeState?.dark ?: DarkColorScheme
                                        val topColor = when (activeThemeStyle) {
                                            ShareThemeStyle.DYNAMIC_PALETTE -> activeDarkScheme.primaryContainer
                                            ShareThemeStyle.SOOTHING_GRADIENT -> activeDarkScheme.primary
                                            ShareThemeStyle.BLURRED_ARTWORK -> activeDarkScheme.primaryContainer
                                            ShareThemeStyle.MIDNIGHT_MINIMAL -> Color(0xFF0C0C0C)
                                            ShareThemeStyle.VIBRANT_GLOW -> activeDarkScheme.primaryContainer
                                        }
                                        val bottomColor = when (activeThemeStyle) {
                                            ShareThemeStyle.DYNAMIC_PALETTE -> activeDarkScheme.surfaceContainerLowest
                                            ShareThemeStyle.SOOTHING_GRADIENT -> activeDarkScheme.surfaceContainerLowest
                                            ShareThemeStyle.BLURRED_ARTWORK -> activeDarkScheme.surfaceContainerLowest
                                            ShareThemeStyle.MIDNIGHT_MINIMAL -> Color(0xFF0C0C0C)
                                            ShareThemeStyle.VIBRANT_GLOW -> activeDarkScheme.surfaceContainerLowest
                                        }
                                        shareToInstagramStory(
                                            context = context,
                                            imageUri = uri,
                                            topColorHex = topColor.toInstagramHex(),
                                            bottomColorHex = bottomColor.toInstagramHex()
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Download Card
                    item {
                        ShareActionChip(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = stringResource(R.string.share_action_download_card),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                captureAndShare { bitmap ->
                                    val file = saveBitmapToCache(bitmap)
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                        android.os.Environment.DIRECTORY_PICTURES
                                    )
                                    val destFile = File(downloadsDir, "PixelMusic_${song.title.take(20)}_${System.currentTimeMillis()}.png")
                                    withContext(Dispatchers.IO) {
                                        file.copyTo(destFile, overwrite = true)
                                    }
                                    android.media.MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(destFile.absolutePath),
                                        arrayOf("image/png"),
                                        null
                                    )
                                    Toast.makeText(context, context.getString(R.string.share_card_saved), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // Copy YT Music Link (if available)
                    if (!song.youtubeId.isNullOrEmpty()) {
                        item {
                            ShareActionChip(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = "YT Music Link",
                                containerColor = Color(0xFFFF0000).copy(alpha = 0.12f),
                                contentColor = Color(0xFFFF0000),
                                onClick = {
                                    val ytMusicLink = "https://music.youtube.com/watch?v=${song.youtubeId}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Music Link", ytMusicLink))
                                    Toast.makeText(context, "YouTube Music link copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Standard Android Share
                    item {
                        ShareActionChip(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = stringResource(R.string.share_action_more_apps),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                captureAndShare { bitmap ->
                                    val file = saveBitmapToCache(bitmap)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    // Include YouTube Music link when sharing a YT song
                                    val linkSuffix = if (!song.youtubeId.isNullOrEmpty()) {
                                        "\n🎵 https://music.youtube.com/watch?v=${song.youtubeId}"
                                    } else {
                                        "\n$GITHUB_LINK"
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${song.title} — ${song.displayArtist}$linkSuffix"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, context.getString(R.string.share_sheet_chooser_title))
                                    )
                                }
                            }
                        )
                    }
                }


                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
    }

    // Capture overlay
    if (isCapturing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Shareable Card Composable (9:16 captures)
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShareableCard(
    modifier: Modifier = Modifier,
    song: Song,
    isLyricsMode: Boolean,
    selectedLyrics: List<String>,
    themeStyle: ShareThemeStyle,
    colorScheme: ColorScheme,
    cardShape: Shape,
    albumColorScheme: ColorSchemePair?,
    useSolidLyricsCard: Boolean = false,
    isCardDark: Boolean = true,
    isBlurEnabled: Boolean = false,
    formatTag: String? = null,
    playbackMimeType: String? = null,
    playbackSampleRate: Int? = null,
    playbackBitDepth: Int? = null,
) {
    val cardRatio = 9f / 16f
    val darkScheme = albumColorScheme?.dark ?: DarkColorScheme
    val lightScheme = albumColorScheme?.light ?: LightColorScheme

    // Outer card background is ALWAYS dark & rich for Instagram / Snapchat share cards
    val bgScheme = darkScheme
    val primaryColor = bgScheme.primary
    val secondaryColor = bgScheme.secondary
    val tertiaryColor = bgScheme.tertiary
    val surfaceContainerLow = bgScheme.surfaceContainerLow
    val surfaceContainerLowest = bgScheme.surfaceContainerLowest

    // Inner song mini card theme (switchable between Light and Dark palette)
    val activeCardScheme = if (isCardDark) darkScheme else lightScheme

    Box(
        modifier = modifier
            .aspectRatio(cardRatio)
            .shadow(elevation = 16.dp, shape = cardShape, clip = true)
            .clip(cardShape)
    ) {
        // ── 1. Outer Background (Always Dark & Dynamic) ─────────────────────
        // Lyrics card: full-bleed album art as background
        // Song card: themeStyle dynamic dark bg
        val primaryContainer = bgScheme.primaryContainer
        val secondaryContainer = bgScheme.secondaryContainer

        when (themeStyle) {
            ShareThemeStyle.DYNAMIC_PALETTE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryContainer, surfaceContainerLowest)
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.45f), Color.Transparent),
                                    radius = 700f
                                )
                            )
                    )
                }
            }
            ShareThemeStyle.SOOTHING_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryContainer, secondaryContainer, surfaceContainerLowest)
                            )
                        )
                )
            }
            ShareThemeStyle.BLURRED_ARTWORK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(surfaceContainerLowest)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUriString)
                            .size(1080, 1920)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                alpha = if (isCardDark) 0.90f else 0.85f
                            }
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(if (isCardDark) 14.dp else 16.dp)
                                } else {
                                    Modifier
                                }
                            )
                    )
                    // Atmospheric subtle depth tint so the artwork is clearly recognizable and visible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isCardDark) Color.Black.copy(alpha = 0.16f)
                                else Color.White.copy(alpha = 0.10f)
                            )
                    )
                    // Gradient scrim only at top and bottom for readability, leaving center artwork visible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    0.0f to if (isCardDark) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
                                    0.25f to Color.Transparent,
                                    0.70f to Color.Transparent,
                                    1.0f to if (isCardDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.25f)
                                )
                            )
                    )
                }
            }
            ShareThemeStyle.MIDNIGHT_MINIMAL -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(surfaceContainerLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .size(420.dp)
                            .align(Alignment.TopStart)
                            .offset(x = (-120).dp, y = (-60).dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.18f), Color.Transparent)
                                )
                            )
                    )
                }
            }
            ShareThemeStyle.VIBRANT_GLOW -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryContainer, surfaceContainerLowest)
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.38f),
                                        secondaryColor.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
        // Vignette for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (themeStyle == ShareThemeStyle.BLURRED_ARTWORK) 0.20f else 0.45f)),
                        radius = 1200f
                    )
                )
        )

        // ── 2. Foreground Content Column ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spacer above the song minicard
            Spacer(Modifier.height(19.dp))

            if (!isLyricsMode) {
                // ── SONG CARD (Full Player Sheet Layout) ──────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(AbsoluteSmoothCornerShape(18.dp, 60))
                        .background(activeCardScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBlurEnabled) {
                        // 1. Vibrant Gaussian Blur Layer (exact match with FullPlayerAtmosphereBackground)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = 1.20f
                                    scaleY = 1.20f
                                    alpha = if (isCardDark) 0.65f else 0.45f
                                }
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(if (isCardDark) 52.dp else 60.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(song.albumArtUriString)
                                    .size(512, 512)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 2. Contrast & Harmonization Layer (exact match with FullPlayerAtmosphereBackground)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawBehind {
                                    if (!isCardDark) {
                                        drawRect(
                                            color = activeCardScheme.primaryContainer.copy(alpha = 0.20f)
                                        )
                                    } else {
                                        drawRect(
                                            color = Color(0xFF090D14).copy(alpha = 0.28f)
                                        )
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.0f to Color.Black.copy(alpha = 0.18f),
                                                0.35f to Color.Transparent,
                                                1.0f to Color.Transparent
                                            )
                                        )
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.65f to Color.Transparent,
                                                0.82f to Color.Black.copy(alpha = 0.42f),
                                                0.92f to Color.Black.copy(alpha = 0.68f),
                                                1.0f to Color(0xFF090D13).copy(alpha = 0.82f)
                                            )
                                        )
                                    }
                                }
                        )
                    }

                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp)
                    ) {
                        SongMiniCard(
                            song = song,
                            albumScheme = activeCardScheme,
                            isCardDark = isCardDark,
                            formatTag = formatTag,
                            playbackMimeType = playbackMimeType,
                            playbackSampleRate = playbackSampleRate,
                            playbackBitDepth = playbackBitDepth,
                        )
                    }
                }
            } else {
                // ── LYRICS PANEL ─────────────────────────────────────────────
                val containerColor = if (useSolidLyricsCard) {
                    lightScheme.surfaceContainerHigh
                } else {
                    Color.White.copy(alpha = 0.18f)
                }
                val borderColor = if (useSolidLyricsCard) {
                    Color.White.copy(alpha = 0.25f)
                } else {
                    Color.White.copy(alpha = 0.18f)
                }
                val borderStrokeWidth = 1.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .shadow(
                            elevation = if (useSolidLyricsCard) 20.dp else 0.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = primaryColor.copy(alpha = 0.4f)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(containerColor)
                        .border(
                            width = borderStrokeWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(16.dp)
                ) {
                    LyricsGlassPanel(
                        song = song,
                        selectedLyrics = selectedLyrics,
                        isSolid = useSolidLyricsCard,
                        lightScheme = lightScheme
                    )
                }
            }

            // ── PixelMusic pill — OUTSIDE mini card, in outer column ─────────
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .clickable {
                            try { uriHandler.openUri(GITHUB_LINK) } catch (e: Exception) { }
                        }
                        .padding(horizontal = 10.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "PixelMusic",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = "github.com/ianshulyadav",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    fontSize = 7.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Song Mini Card — inner content (album-art dynamic colors)
// Layout: flush full-width art → title → artist → thin progress bar
// NO controls. NO branding inside this card.
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SongMiniCard(
    song: Song,
    albumScheme: ColorScheme,
    isCardDark: Boolean = true,
    formatTag: String? = null,
    playbackMimeType: String? = null,
    playbackSampleRate: Int? = null,
    playbackBitDepth: Int? = null,
) {
    val durationMs = remember(song.duration) { if (song.duration > 0) song.duration else 180000L }
    val formattedDuration = remember(durationMs) {
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        String.format("%02d:%02d", mins, secs)
    }
    val progressRatio = 0.31f
    val formattedProgress = remember(durationMs, progressRatio) {
        val progressSecs = ((durationMs * progressRatio) / 1000).toLong()
        val mins = progressSecs / 60
        val secs = progressSecs % 60
        String.format("%02d:%02d", mins, secs)
    }

    val audioMetaLabel = remember(
        playbackMimeType,
        song.mimeType,
        formatTag,
        song.path,
        playbackSampleRate,
        song.sampleRate,
        playbackBitDepth
    ) {
        com.unshoo.pixelmusic.utils.AudioMetaUtils.formatShareCardAudioTag(
            mimeType = playbackMimeType ?: song.mimeType,
            formatTag = formatTag,
            filePath = song.path,
            sampleRate = playbackSampleRate ?: song.sampleRate,
            bitDepth = playbackBitDepth
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ── 1. Album Artwork (Rounded corners matching full player sheet) ─────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(albumScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 2. Song Info (Full width, no action buttons) ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = song.title,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = albumScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                color = albumScheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── 3. Wavy Progress Bar Slider + Thumb Indicator ──────────────────
        val density = LocalDensity.current
        val stroke = remember(density) {
            Stroke(width = with(density) { 2.2.dp.toPx() }, cap = StrokeCap.Round)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LinearWavyProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = albumScheme.primary,
                trackColor = albumScheme.onPrimaryContainer.copy(alpha = 0.2f),
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 10.dp,
                amplitude = { 0.35f },
                waveSpeed = 4.dp
            )

            // Thumb Indicator dot
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
            ) {
                val thumbRadiusPx = 3.5.dp.toPx()
                val thumbX = size.width * progressRatio
                drawCircle(
                    color = albumScheme.onPrimaryContainer,
                    radius = thumbRadiusPx,
                    center = Offset(thumbX.coerceIn(thumbRadiusPx, size.width - thumbRadiusPx), size.height / 2)
                )
            }
        }

        // ── 4. Timestamps & Audio Meta Badge ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-2).dp)
                .padding(horizontal = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formattedProgress,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 6.5.sp,
                    color = albumScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    formattedDuration,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 6.5.sp,
                    color = albumScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 26.dp),
                shape = RoundedCornerShape(999.dp),
                color = albumScheme.onPrimaryContainer.copy(alpha = 0.14f),
                contentColor = albumScheme.onPrimaryContainer.copy(alpha = 0.96f)
            ) {
                Text(
                    text = audioMetaLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Medium,
                        fontSize = 6.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        // ── 5. Expressive Playback Transport Controls (Prev | Pause | Next) ──
        val playPauseBg = albumScheme.tertiaryFixedDim
        val playPauseTint = albumScheme.onTertiaryFixed
        val skipBg = albumScheme.primary
        val skipTint = albumScheme.onPrimary
        val heroCorner = 12.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(skipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = skipTint,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Hero Play/Pause Button
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = heroCorner,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = heroCorner,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = heroCorner,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = heroCorner,
                            smoothnessAsPercentBR = 60
                        )
                    )
                    .background(playPauseBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = null,
                    tint = playPauseTint,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Next Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(skipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = skipTint,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}


// ────────────────────────────────────────────────────────────────────────────
// Lyrics Glass Panel — content inside the frosted glass box on the lyrics card
// Layout: thumbnail header → divider → dynamic-font lyric lines
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricsGlassPanel(
    song: Song,
    selectedLyrics: List<String>,
    isSolid: Boolean = false,
    lightScheme: ColorScheme = LightColorScheme
) {
    val textColor = if (isSolid) lightScheme.onSurface else Color.White
    val artistColor = if (isSolid) lightScheme.onSurfaceVariant else Color.White.copy(alpha = 0.65f)
    val dividerColor = if (isSolid) lightScheme.outlineVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
    val activeProgressColor = if (isSolid) lightScheme.onPrimaryContainer else Color.White
    val progressTrackColor = if (isSolid) lightScheme.onPrimaryContainer.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.22f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header: Capsule container with rotating Vinyl CD disc + title & artist
        val infiniteTransition = rememberInfiniteTransition(label = "vinylSpinTransition")
        val vinylAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vinylSpinAngle"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(
                    if (isSolid) lightScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .border(
                    width = 1.dp,
                    color = if (isSolid) lightScheme.outlineVariant.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.18f),
                    shape = CircleShape
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Rotating Vinyl CD Capsule Disc
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer { rotationZ = vinylAngle }
                    .shadow(4.dp, CircleShape, clip = true)
                    .clip(CircleShape)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl outer ridges
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                )
                // Center Album Artwork Label
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize(0.55f)
                        .clip(CircleShape)
                )
                // Center Spindle Hole
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp
        )
        Spacer(Modifier.height(14.dp))

        // Lyric lines — dynamic font size shrinks as more lines are selected
        if (selectedLyrics.isEmpty()) {
            Text(
                text = stringResource(R.string.share_select_lyrics_hint),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.38f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            )
        } else {
            val fontSize = when (selectedLyrics.size) {
                1    -> 22.sp
                2    -> 18.sp
                3    -> 15.sp
                4    -> 13.sp
                else -> 11.sp
            }
            val lineHeight = when (selectedLyrics.size) {
                1    -> 28.sp
                2    -> 24.sp
                3    -> 20.sp
                4    -> 17.sp
                else -> 15.sp
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                selectedLyrics.forEach { line ->
                    Text(
                        text = line.replace("\"", "").replace("“", "").replace("”", "").trim(),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = textColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Premium Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(progressTrackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .fillMaxHeight()
                    .background(activeProgressColor)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────

// Scrollable Lyric Multi-Selector Composable
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricLineSelector(
    lines: List<String>,
    selectedLines: List<String>,
    onToggleLine: (String) -> Unit,
    primaryColor: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val containerShape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.share_select_lyrics_title),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = CircleShape,
                color = if (selectedLines.size >= 5) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${selectedLines.size}/5 lines",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedLines.size >= 5) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = containerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line ->
                    val isSelected = selectedLines.contains(line)
                    val bgSelectedColor = if (isSelected) primaryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow
                    val borderSelectedColor = if (isSelected) primaryColor else Color.Transparent
                    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    val textColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                    val itemShape = remember { AbsoluteSmoothCornerShape(14.dp, 60) }

                    Surface(
                        shape = itemShape,
                        color = bgSelectedColor,
                        border = BorderStroke(1.dp, borderSelectedColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onToggleLine(line)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = line,
                                fontFamily = GoogleSansRounded,
                                fontWeight = textWeight,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Share Action Chip Composable
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShareActionChip(
    icon: @Composable () -> Unit,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "chipScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                },
            shape = CircleShape,
            color = containerColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
        }
        Text(
            text = label,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(68.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Share List Item Composable
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShareListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        ),
        headlineContent = {
            Text(
                text = title,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                fontFamily = GoogleSansRounded,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ────────────────────────────────────────────────────────────────────────────
// Platform Helpers
// ────────────────────────────────────────────────────────────────────────────
private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

private fun shareToSnapchat(context: Context, imageFile: File, attachmentUrl: String = GITHUB_LINK) {
    try {
        val snapCreative = SnapCreative.getApi(context)
        val mediaFactory = SnapCreative.getMediaFactory(context)
        val snapPhotoFile = mediaFactory.getSnapPhotoFromFile(imageFile)
        val snapPhotoContent = SnapPhotoContent(snapPhotoFile).apply {
            this.attachmentUrl = attachmentUrl
        }
        snapCreative.send(snapPhotoContent)
    } catch (e: Exception) {
        Toast.makeText(context, "Snapchat sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareVideoToSnapchat(context: Context, videoFile: File, attachmentUrl: String = GITHUB_LINK) {
    try {
        val snapCreative = SnapCreative.getApi(context)
        val mediaFactory = SnapCreative.getMediaFactory(context)
        val snapVideoFile = mediaFactory.getSnapVideoFromFile(videoFile)
        val snapVideoContent = SnapVideoContent(snapVideoFile).apply {
            this.attachmentUrl = attachmentUrl
        }
        snapCreative.send(snapVideoContent)
    } catch (e: Exception) {
        Toast.makeText(context, "Snapchat video sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun Color.toInstagramHex(): String {
    return String.format("#%06X", 0xFFFFFF and this.toArgb())
}

private fun shareToInstagramStory(
    context: Context,
    imageUri: android.net.Uri,
    topColorHex: String? = null,
    bottomColorHex: String? = null
) {
    fun grantInstagramRead() {
        runCatching { context.grantUriPermission(INSTAGRAM_PACKAGE, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    grantInstagramRead()

    val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        setDataAndType(imageUri, "image/png")
        // Use background_asset_uri so the rendered card fills the story as the background without creating a floating duplicate sticker
        putExtra("background_asset_uri", imageUri)
        putExtra("source_application", context.packageName)
        putExtra("content_url", GITHUB_LINK)
        if (topColorHex != null) putExtra("top_background_color", topColorHex)
        if (bottomColorHex != null) putExtra("bottom_background_color", bottomColorHex)
        clipData = ClipData.newUri(context.contentResolver, "PixelMusic share card", imageUri)
        `package` = INSTAGRAM_PACKAGE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(storyIntent)
        return
    } catch (_: Exception) {
        // Fall through to feed/share intent. Instagram does not allow silent direct posting;
        // this opens Instagram's composer with the card attached.
    }

    val feedIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        clipData = ClipData.newUri(context.contentResolver, "PixelMusic share card", imageUri)
        `package` = INSTAGRAM_PACKAGE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(feedIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Instagram sharing failed", Toast.LENGTH_SHORT).show()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareBottomSheetEntryPoint {
    fun themeStateHolder(): ThemeStateHolder
    fun userPreferencesRepository(): UserPreferencesRepository
}
