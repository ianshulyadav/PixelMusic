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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.material3.LinearWavyProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File
import com.snapchat.kit.sdk.SnapCreative
import com.snapchat.kit.sdk.creative.models.SnapPhotoContent

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
            .map { it.trim() }
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
    colorScheme: ColorScheme = MaterialTheme.colorScheme
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, ShareBottomSheetEntryPoint::class.java)
    }
    val themeStateHolder = entryPoint.themeStateHolder()
    val albumColorSchemeState by themeStateHolder.getAlbumColorSchemeFlow(song.albumArtUriString.orEmpty()).collectAsState()

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Card mode: 0 = Song Card, 1 = Lyrics Card
    var selectedCardMode by remember { mutableStateOf(0) }
    val hasLyrics = remember(song.lyrics) { !song.lyrics.isNullOrBlank() }

    // Lyric state
    val cleanedLyrics = remember(song.lyrics) { LyricCleaner.clean(song.lyrics) }
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

    // Capture bitmap and run share operation
    fun captureAndShare(action: suspend (Bitmap) -> Unit) {
        isCapturing = true
        scope.launch {
            try {
                val bitmap = captureController.captureAsync().await().asAndroidBitmap()
                action(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to capture card", Toast.LENGTH_SHORT).show()
            } finally {
                isCapturing = false
            }
        }
    }

    // Save generated bitmap to cache
    suspend fun saveBitmapToCache(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "share_cards").also { it.mkdirs() }
        val file = File(cacheDir, "pixelmusic_share_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        val sheetShape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 28.dp, smoothnessAsPercentBR = 60,
            cornerRadiusBR = 0.dp, smoothnessAsPercentTL = 60,
            cornerRadiusTL = 28.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 0.dp, smoothnessAsPercentTR = 60
        )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            shape = sheetShape,
            dragHandle = null
        ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.surfaceContainerLow,
                                colorScheme.surfaceContainerLowest
                            )
                        )
                    )
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
                // ── Header ──────────────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.share_sheet_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

                // ── 9:16 Card Preview (Capturable) ──────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = Pair(selectedCardMode, selectedLyrics.toList()),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "cardPreview"
                    ) { (mode, lyricsList) ->
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
                            albumColorScheme = albumColorSchemeState
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
                        text = "Card Theme",
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
                                // Draw preview inside swatch circle
                                when (style) {
                                    ShareThemeStyle.DYNAMIC_PALETTE -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            colorScheme.primaryContainer,
                                                            colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                                            colorScheme.surfaceContainerLow
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    ShareThemeStyle.BLURRED_ARTWORK -> {
                                        SmartImage(
                                            model = song.albumArtUriString,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    ShareThemeStyle.SOOTHING_GRADIENT -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            primaryColor.copy(alpha = 0.85f),
                                                            tertiaryColor.copy(alpha = 0.7f),
                                                            colorScheme.surfaceContainerHighest
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
                                                            colors = listOf(primaryColor.copy(alpha = 0.6f), Color.Transparent)
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
                                                            primaryColor,
                                                            secondaryColor,
                                                            tertiaryColor
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
                                        shareToSnapchat(context, file)
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
                                        val topColor = when (activeThemeStyle) {
                                            ShareThemeStyle.DYNAMIC_PALETTE -> colorScheme.primaryContainer
                                            ShareThemeStyle.SOOTHING_GRADIENT -> primaryColor
                                            ShareThemeStyle.BLURRED_ARTWORK -> primaryColor.copy(alpha = 0.5f)
                                            ShareThemeStyle.MIDNIGHT_MINIMAL -> Color(0xFF0A0A0A)
                                            ShareThemeStyle.VIBRANT_GLOW -> primaryColor
                                        }
                                        val bottomColor = when (activeThemeStyle) {
                                            ShareThemeStyle.DYNAMIC_PALETTE -> colorScheme.surfaceContainerLow
                                            ShareThemeStyle.SOOTHING_GRADIENT -> colorScheme.surfaceContainerHighest
                                            ShareThemeStyle.BLURRED_ARTWORK -> Color(0xFF141414)
                                            ShareThemeStyle.MIDNIGHT_MINIMAL -> Color(0xFF0A0A0A)
                                            ShareThemeStyle.VIBRANT_GLOW -> secondaryColor
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

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))

                // ── Secondary Actions ────────────────────────────────────────
                ShareListItem(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = stringResource(R.string.share_action_add_to_playlist),
                    subtitle = stringResource(R.string.share_action_add_to_playlist_sub),
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )

                // Open in YouTube Music (for YT songs) or GitHub link (for local songs)
                if (!song.youtubeId.isNullOrEmpty()) {
                    val ytMusicLink = "https://music.youtube.com/watch?v=${song.youtubeId}"
                    ShareListItem(
                        icon = Icons.Rounded.OpenInNew,
                        title = "Open in YouTube Music",
                        subtitle = ytMusicLink,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ytMusicLink)).apply {
                                // Try to open in the YT Music app first
                                setPackage("com.google.android.apps.youtube.music")
                            }
                            val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                            if (resolved != null) {
                                context.startActivity(intent)
                            } else {
                                // Fallback: open in browser
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ytMusicLink))
                                )
                            }
                        }
                    )
                    ShareListItem(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Copy YouTube Music Link",
                        subtitle = ytMusicLink,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Music Link", ytMusicLink))
                            Toast.makeText(context, "YouTube Music link copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    ShareListItem(
                        icon = Icons.Rounded.ContentCopy,
                        title = stringResource(R.string.share_action_copy_github_link),
                        subtitle = GITHUB_LINK,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", GITHUB_LINK))
                            Toast.makeText(context, context.getString(R.string.share_link_copied), Toast.LENGTH_SHORT).show()
                        }
                    )
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
    albumColorScheme: ColorSchemePair?
) {
    val cardRatio = 9f / 16f
    val darkScheme = albumColorScheme?.dark ?: DarkColorScheme
    val lightScheme = albumColorScheme?.light ?: LightColorScheme

    val primaryColor = darkScheme.primary
    val onPrimaryColor = darkScheme.onPrimary
    val primaryContainerColor = darkScheme.primaryContainer
    val onPrimaryContainerColor = darkScheme.onPrimaryContainer
    val secondaryColor = darkScheme.secondary
    val tertiaryColor = darkScheme.tertiary

    val surfaceContainerLow = darkScheme.surfaceContainerLow
    val surfaceContainerLowest = darkScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .aspectRatio(cardRatio)
            .shadow(elevation = 16.dp, shape = cardShape, clip = true)
            .clip(cardShape)
    ) {
        // ── 1. Dynamic Background Render ─────────────────────────────────────
        when (themeStyle) {
            ShareThemeStyle.DYNAMIC_PALETTE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    surfaceContainerLow,
                                    surfaceContainerLowest
                                )
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    radius = 600f
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
                                colors = listOf(
                                    surfaceContainerLow,
                                    surfaceContainerLowest
                                )
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.2f),
                                        tertiaryColor.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
            ShareThemeStyle.BLURRED_ARTWORK -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        surfaceContainerLow.copy(alpha = 0.82f),
                                        surfaceContainerLowest.copy(alpha = 0.92f)
                                    )
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
                                    colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent)
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
                                colors = listOf(
                                    surfaceContainerLow,
                                    surfaceContainerLowest
                                )
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.22f),
                                        secondaryColor.copy(alpha = 0.15f),
                                        tertiaryColor.copy(alpha = 0.1f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Vignette Overlay for premium depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        radius = 1200f
                    )
                )
        )

        // ── 2. Content Column ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Centerpiece Floating Card (Player Styled & Frost Blended)
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .weight(1f, fill = false)
                    .shadow(16.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = lightScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, lightScheme.onPrimaryContainer.copy(alpha = 0.15f))
            ) {
                if (!isLyricsMode) {
                    // SONG SHARE CARD (MINI PLAYER WIDGET DESIGN)
                    val audioFormatInfo = remember(song) {
                        val sampleRateStr = when (val sr = song.sampleRate) {
                            null, 0 -> "44.1 kHz"
                            in 1..2000 -> "$sr Hz"
                            else -> {
                                val khz = sr / 1000f
                                if (khz % 1 == 0f) "${khz.toInt()}.0 kHz" else "${String.format("%.1f", khz)} kHz"
                            }
                        }
                        val bitrateStr = when (val br = song.bitrate) {
                            null, 0 -> {
                                if (!song.youtubeId.isNullOrEmpty()) "128 kbps" else "320 kbps"
                            }
                            else -> {
                                val kbps = if (br > 2000) br / 1000 else br
                                "$kbps kbps"
                            }
                        }
                        val codecStr = when {
                            song.mimeType?.contains("opus", ignoreCase = true) == true -> "OPUS"
                            song.mimeType?.contains("ogg", ignoreCase = true) == true -> "OGG"
                            song.mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                            song.mimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
                            song.mimeType?.contains("m4a", ignoreCase = true) == true -> "AAC"
                            !song.youtubeId.isNullOrEmpty() -> "OPUS"
                            else -> "MP3"
                        }
                        "$sampleRateStr • $bitrateStr • $codecStr"
                    }

                    val formattedDuration = remember(song.duration) {
                        val totalSecs = song.duration / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        String.format("%02d:%02d", mins, secs)
                    }

                    val formattedProgress = remember(song.duration) {
                        val progressSecs = (song.duration * 0.4f / 1000).toLong()
                        val mins = progressSecs / 60
                        val secs = progressSecs % 60
                        String.format("%02d:%02d", mins, secs)
                    }

                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Square Album Art with soft glow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(8.dp, shape = RoundedCornerShape(12.dp), clip = true)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 2. Song Details (Left-aligned for a modern player layout)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = song.title,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = lightScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.displayArtist,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = lightScheme.onPrimaryContainer.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // 3. Sleek Wavy Progress / Seek Bar with Thumb
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formattedProgress,
                                color = lightScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                LinearWavyProgressIndicator(
                                    progress = { 0.4f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = lightScheme.onPrimaryContainer,
                                    trackColor = lightScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 3.5.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(lightScheme.onPrimaryContainer)
                                    )
                                }
                            }
                            Text(
                                text = formattedDuration,
                                color = lightScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 3.5. Metadata Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(lightScheme.onPrimaryContainer.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = audioFormatInfo,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = lightScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                letterSpacing = 0.3.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // 4. Playback Controls Row (Dynamic Capsule Shape)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(lightScheme.primary)
                                    .clickable { },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = lightScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(lightScheme.tertiaryContainer)
                                    .clickable { },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = lightScheme.onTertiaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(lightScheme.primary)
                                    .clickable { },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = lightScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // LYRICS SHARE CARD
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        // 1. Header (Mini Song Card style)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(4.dp, RoundedCornerShape(8.dp), clip = true)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = lightScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.displayArtist,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = lightScheme.onPrimaryContainer.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = lightScheme.onPrimaryContainer.copy(alpha = 0.12f), thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))

                        // 2. Verses Quote Block
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (selectedLyrics.isEmpty()) {
                                Text(
                                    text = "Select lyrics below to share...",
                                    fontFamily = GoogleSansRounded,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 14.sp,
                                    color = lightScheme.onPrimaryContainer.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                                )
                            } else {
                                val fontSize = when (selectedLyrics.size) {
                                    1 -> 19.sp
                                    2, 3 -> 16.sp
                                    else -> 13.sp
                                }
                                val lineHeight = when (selectedLyrics.size) {
                                    1 -> 25.sp
                                    2, 3 -> 21.sp
                                    else -> 17.sp
                                }
                                selectedLyrics.forEach { line ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FormatQuote,
                                            contentDescription = null,
                                            tint = lightScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp).offset(y = 2.dp)
                                        )
                                        Text(
                                            text = line,
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = fontSize,
                                            lineHeight = lineHeight,
                                            color = lightScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Spacer(Modifier.height(8.dp))

                        // 3. Mini Progress line to match Player widget theme
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(lightScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.55f)
                                    .clip(CircleShape)
                                    .background(lightScheme.primary)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // 4. App Branding Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(lightScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.pixelmusic_base_monochrome),
                                        contentDescription = null,
                                        tint = lightScheme.onPrimary,
                                        modifier = Modifier
                                            .padding(2.5.dp)
                                            .fillMaxSize()
                                    )
                                }
                                Text(
                                    text = "PixelMusic",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = lightScheme.onPrimaryContainer
                                )
                            }
                            
                            Text(
                                text = "Lyric Card",
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                color = lightScheme.onPrimaryContainer.copy(alpha = 0.4f),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 3. Snapchat Replicated Link Clip Pill ────────────────────────
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .clickable {
                            try {
                                uriHandler.openUri(GITHUB_LINK)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "PixelMusic",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "github.com/ianshulyadav",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Tap to select lyrics (Max 5 lines)",
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line ->
                    val isSelected = selectedLines.contains(line)
                    val bgSelectedColor = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                    val borderSelectedColor = if (isSelected) primaryColor else Color.Transparent
                    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    val textColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgSelectedColor)
                            .border(1.dp, borderSelectedColor, RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleLine(line)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = line,
                            fontFamily = GoogleSansRounded,
                            fontWeight = textWeight,
                            fontSize = 15.sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(64.dp)
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
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
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
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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

private fun shareToSnapchat(context: Context, imageFile: File) {
    try {
        val snapCreative = SnapCreative.getApi(context)
        val mediaFactory = SnapCreative.getMediaFactory(context)
        val snapPhotoFile = mediaFactory.getSnapPhotoFromFile(imageFile)
        val snapPhotoContent = SnapPhotoContent(snapPhotoFile).apply {
            attachmentUrl = GITHUB_LINK
        }
        snapCreative.send(snapPhotoContent)
    } catch (e: Exception) {
        Toast.makeText(context, "Snapchat sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
    val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        type = "image/png"
        putExtra("interactive_asset_uri", imageUri)
        putExtra("content_url", GITHUB_LINK)
        if (topColorHex != null) {
            putExtra("top_background_color", topColorHex)
        }
        if (bottomColorHex != null) {
            putExtra("bottom_background_color", bottomColorHex)
        }
        `package` = INSTAGRAM_PACKAGE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            `package` = INSTAGRAM_PACKAGE
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallback)
        } catch (ex: Exception) {
            Toast.makeText(context, "Instagram not available", Toast.LENGTH_SHORT).show()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareBottomSheetEntryPoint {
    fun themeStateHolder(): ThemeStateHolder
}
