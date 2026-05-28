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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File

private const val GITHUB_LINK = "https://github.com/ianshulyadav/PixelMusic"
private const val SNAPCHAT_PACKAGE = "com.snapchat.android"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ShareBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Card mode: 0 = Song Card, 1 = Lyrics Card
    var selectedCardMode by remember { mutableStateOf(0) }
    val hasLyrics = remember(song.lyrics) { !song.lyrics.isNullOrBlank() }
    // Only show lyrics tab when lyrics available
    val lyricsExcerpt = remember(song.lyrics) {
        song.lyrics
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?.take(4)
            ?.joinToString("\n")
            ?: ""
    }

    var isCapturing by remember { mutableStateOf(false) }
    val captureController = rememberCaptureController()

    val snapchatInstalled = remember {
        isPackageInstalled(context, SNAPCHAT_PACKAGE)
    }
    val instagramInstalled = remember {
        isPackageInstalled(context, INSTAGRAM_PACKAGE)
    }

    // Colors matching player theme
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 24.dp, smoothnessAsPercentBR = 60,
        cornerRadiusBR = 24.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTL = 24.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBL = 24.dp, smoothnessAsPercentTR = 60
    )

    // Helper to capture bitmap and execute action
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

    // Helper to save bitmap to cache and return URI
    suspend fun saveBitmapToCache(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "share_cards").also { it.mkdirs() }
        val file = File(cacheDir, "pixelmusic_share_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 28.dp, smoothnessAsPercentBR = 60,
            cornerRadiusBR = 0.dp, smoothnessAsPercentTL = 60,
            cornerRadiusTL = 28.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 0.dp, smoothnessAsPercentTR = 60
        ),
        dragHandle = {
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
        }
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
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
                Spacer(Modifier.height(20.dp))

                // ── Card Mode Toggle (only show Lyrics tab when lyrics available) ──
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
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable { selectedCardMode = index },
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
                    Spacer(Modifier.height(20.dp))
                }

                // ── Card Preview (Capturable) ────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = selectedCardMode,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "cardPreview"
                    ) { mode ->
                        ShareableCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .capturable(captureController),
                            song = song,
                            isLyricsMode = mode == 1,
                            lyricsExcerpt = lyricsExcerpt,
                            primaryContainerColor = primaryContainerColor,
                            onPrimaryContainerColor = onPrimaryContainerColor,
                            primaryColor = primaryColor,
                            cardShape = cardShape
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Primary Share Actions (Horizontal Scrollable) ────────────
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        shareToSnapchat(context, uri)
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
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        shareToInstagramStory(context, uri)
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
                                    // Copy to Downloads
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                        android.os.Environment.DIRECTORY_PICTURES
                                    )
                                    val destFile = File(downloadsDir, "PixelMusic_${song.title.take(20)}_${System.currentTimeMillis()}.png")
                                    withContext(Dispatchers.IO) {
                                        file.copyTo(destFile, overwrite = true)
                                    }
                                    // Notify media scanner
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

                    // Copy YouTube Link (only for YT songs)
                    if (!song.youtubeId.isNullOrEmpty()) {
                        item {
                            ShareActionChip(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = stringResource(R.string.share_action_copy_yt_link),
                                containerColor = Color(0xFFFF0000).copy(alpha = 0.12f),
                                contentColor = Color(0xFFFF0000),
                                onClick = {
                                    val ytLink = "https://youtu.be/${song.youtubeId}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Link", ytLink))
                                    Toast.makeText(context, context.getString(R.string.share_link_copied), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Share via more apps
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
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            context.getString(
                                                R.string.share_text_format,
                                                song.title,
                                                song.displayArtist,
                                                GITHUB_LINK
                                            )
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
        }
    }

    // Capturing overlay
    if (isCapturing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Shareable Card Composable
// ────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShareableCard(
    modifier: Modifier = Modifier,
    song: Song,
    isLyricsMode: Boolean,
    lyricsExcerpt: String,
    primaryContainerColor: Color,
    onPrimaryContainerColor: Color,
    primaryColor: Color,
    cardShape: Shape
) {
    val cardRatio = 9f / 16f

    Box(
        modifier = modifier
            .aspectRatio(cardRatio)
            .shadow(elevation = 12.dp, shape = cardShape, clip = true)
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryContainerColor.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Background subtle pattern
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── App Branding (Top) ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(primaryColor)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_music_note_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize()
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimaryContainerColor
                )
            }

            // ── Center Content ──────────────────────────────────────────────
            if (!isLyricsMode) {
                // Song Card: Album Art + Info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .shadow(elevation = 16.dp, shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60,
                                cornerRadiusBR = 20.dp, smoothnessAsPercentTL = 60,
                                cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
                                cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
                            ), clip = true)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Song title + artist
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = song.title,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = onPrimaryContainerColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.displayArtist,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPrimaryContainerColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Lyrics Card
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Small album art + song info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                text = song.title,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onPrimaryContainerColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = onPrimaryContainerColor.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Lyrics excerpt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryColor.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = lyricsExcerpt,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                            color = onPrimaryContainerColor,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            // ── Footer: Link ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = GITHUB_LINK,
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Share action chip (horizontal row)
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
// Share list item (vertical list)
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
// Platform helpers
// ────────────────────────────────────────────────────────────────────────────
private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

private fun shareToSnapchat(context: Context, imageUri: android.net.Uri) {
    val intent = Intent("com.snapchat.android.MARKETING_OPT_IN_STORY").apply {
        type = "image/png"
        putExtra("com.snapchat.android.backgroundImage", imageUri)
        putExtra("com.snapchat.android.attachmentUrl", GITHUB_LINK)
        `package` = SNAPCHAT_PACKAGE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to generic share
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            `package` = SNAPCHAT_PACKAGE
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallback)
        } catch (ex: Exception) {
            Toast.makeText(context, "Snapchat not available", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareToInstagramStory(context: Context, imageUri: android.net.Uri) {
    val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        type = "image/png"
        putExtra("interactive_asset_uri", imageUri)
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
