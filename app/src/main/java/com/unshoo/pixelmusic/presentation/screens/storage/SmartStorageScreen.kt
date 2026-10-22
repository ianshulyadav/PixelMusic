package com.unshoo.pixelmusic.presentation.screens.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.storage.ForgottenSongCandidate
import com.unshoo.pixelmusic.data.storage.StorageSpaceInfo
import com.unshoo.pixelmusic.data.storage.StorageTrashItem
import com.unshoo.pixelmusic.presentation.viewmodel.StorageSenseUiState
import com.unshoo.pixelmusic.presentation.viewmodel.StorageSenseViewModel
import java.util.Locale

@Composable
fun SmartStorageScreen(
    navController: NavController,
    viewModel: StorageSenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LaunchedEffect(Unit) {
        viewModel.actionMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SmartStorageTopBar(
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refreshAll() },
                isCleaning = uiState.isCleaning
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.selectedTab == 0 && uiState.selectedCandidateIds.isNotEmpty()) {
                val selectedCount = uiState.selectedCandidateIds.size
                val selectedBytes by remember(uiState.selectedCandidateIds, uiState.candidates) {
                    derivedStateOf {
                        uiState.candidates
                            .filter { uiState.selectedCandidateIds.contains(it.song.id) }
                            .sumOf { it.fileSizeBytes }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedCount track(s) selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reclaims ${formatBytes(selectedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.moveSelectedToTrash() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF5A2A2A) else Color(0xFF4C566A),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Move to Trash", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Triple pill tab switcher matching screenshots
            SeparatePillTabBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = { viewModel.setSelectedTab(it) },
                isDark = isDark
            )

            Spacer(Modifier.height(14.dp))

            when (uiState.selectedTab) {
                0 -> ReviewTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    isDark = isDark
                )
                1 -> TrashTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    isDark = isDark
                )
                2 -> RulesTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
private fun SmartStorageTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    isCleaning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.smart_storage_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isCleaning) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
        } else {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SeparatePillTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDark: Boolean
) {
    val tabs = listOf(
        stringResource(R.string.smart_storage_review_tab),
        stringResource(R.string.smart_storage_trash_tab),
        stringResource(R.string.smart_storage_rules_tab)
    )

    val activeColor = if (isDark) Color(0xFF3E4756) else Color(0xFF4C566A)
    val inactiveColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) activeColor else inactiveColor,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tabBg"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(animatedBg)
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = animatedTextColor
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/*                             REVIEW TAB CONTENT                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun ReviewTabContent(
    uiState: StorageSenseUiState,
    viewModel: StorageSenseViewModel,
    isDark: Boolean
) {
    val candidates = uiState.candidates
    val totalReclaimableBytes by remember(candidates, uiState.storageInfo) {
        derivedStateOf {
            val candidatesBytes = candidates.sumOf { it.fileSizeBytes }
            val cacheBytes = uiState.storageInfo?.totalReclaimableBytes ?: 0L
            candidatesBytes + cacheBytes
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Reclaimable Header Card matching Screenshot 1
        item(key = "reclaimable_banner", contentType = "banner") {
            val bannerBg = if (isDark) Color(0xFF1E2838) else Color(0xFFE5ECFB)
            val iconBg = if (isDark) Color(0xFF2A374D) else Color(0xFFD4E0F8)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = bannerBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF90B4E8) else Color(0xFF3E506D),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.smart_storage_reclaimable_header),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = if (isDark) Color(0xFF90B4E8) else Color(0xFF4C5B75)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatBytes(totalReclaimableBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.smart_storage_reclaimable_desc, candidates.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Storage Breakdown Card
        uiState.storageInfo?.let { info ->
            item(key = "storage_breakdown", contentType = "breakdown") {
                StorageBreakdownCard(info = info, viewModel = viewModel, isDark = isDark)
            }
        }

        // 3. Smart Storage Master Status Banner
        if (!uiState.smartStorageEnabled) {
            item(key = "disabled_banner", contentType = "info") {
                SmartStorageOffBanner(onOpenRules = { viewModel.setSelectedTab(2) }, isDark = isDark)
            }
        } else if (candidates.isEmpty()) {
            item(key = "empty_candidates", contentType = "info") {
                CleanLibraryBanner(isDark = isDark)
            }
        } else {
            // Header for Candidate List
            item(key = "candidates_header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Forgotten Tracks (${candidates.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            if (uiState.selectedCandidateIds.size == candidates.size) {
                                viewModel.deselectAllCandidates()
                            } else {
                                viewModel.selectAllCandidates()
                            }
                        }
                    ) {
                        Text(
                            text = if (uiState.selectedCandidateIds.size == candidates.size) "Deselect All" else "Select All",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Candidate Items
            items(
                items = candidates,
                key = { it.song.id },
                contentType = { "forgotten_candidate" }
            ) { candidate ->
                ForgottenCandidateCard(
                    candidate = candidate,
                    isSelected = uiState.selectedCandidateIds.contains(candidate.song.id),
                    onToggle = { viewModel.toggleCandidateSelection(candidate.song.id) },
                    isDark = isDark
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StorageBreakdownCard(
    info: StorageSpaceInfo,
    viewModel: StorageSenseViewModel,
    isDark: Boolean
) {
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Cache & Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { viewModel.clearAllReclaimable() },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF3E4756) else Color(0xFF4C566A),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Clean All", style = MaterialTheme.typography.labelMedium)
                }
            }

            BreakdownItemRow(
                icon = Icons.Rounded.MusicNote,
                title = "Stream Playback Cache",
                subtitle = "Expired: ${formatBytes(info.expiredStreamBytes)}",
                size = formatBytes(info.streamCacheBytes),
                actionLabel = "Clear",
                onAction = { viewModel.clearStreamCache() },
                isDark = isDark
            )
            BreakdownItemRow(
                icon = Icons.Rounded.Image,
                title = "Album & Artist Artwork",
                subtitle = "Cached thumbnails",
                size = formatBytes(info.imageCacheBytes),
                actionLabel = "Clear",
                onAction = { viewModel.clearImageCache() },
                isDark = isDark
            )
            BreakdownItemRow(
                icon = Icons.Rounded.History,
                title = "Search History",
                subtitle = "${info.prunableSearchCount} older than 25th entry",
                size = "${info.prunableSearchCount} items",
                actionLabel = "Prune",
                onAction = { viewModel.pruneSearchHistory() },
                isDark = isDark
            )
            if (info.invalidSongCount > 0) {
                BreakdownItemRow(
                    icon = Icons.Rounded.FolderDelete,
                    title = "Invalid Library Songs",
                    subtitle = "${info.invalidSongCount} missing local files",
                    size = "${info.invalidSongCount} tracks",
                    actionLabel = "Clean",
                    onAction = { viewModel.cleanInvalidSongs() },
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
private fun BreakdownItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    size: String,
    actionLabel: String,
    onAction: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = size,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ForgottenCandidateCard(
    candidate: ForgottenSongCandidate,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isDark: Boolean
) {
    val song = candidate.song
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onToggle() },
        color = cardBg,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (!song.albumArtUriString.isNullOrBlank()) {
                    AsyncImage(
                        model = song.albumArtUriString,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillBadge(
                        text = formatBytes(candidate.fileSizeBytes),
                        containerColor = if (isDark) Color(0xFF2A374D) else Color(0xFFE2E8F0)
                    )
                    PillBadge(
                        text = formatUnplayedDays(candidate.unplayedDays, candidate.isNeverPlayed),
                        containerColor = if (isDark) Color(0xFF374151) else Color(0xFFDCE4F2),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun PillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun SmartStorageOffBanner(onOpenRules: () -> Unit, isDark: Boolean) {
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)
    val circleBg = if (isDark) Color(0xFF2A374D) else Color(0xFFE5E7EB)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.smart_storage_is_off_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.smart_storage_is_off_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onOpenRules,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF3E4756) else Color(0xFF4C566A),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.smart_storage_open_rules),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CleanLibraryBanner(isDark: Boolean) {
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Library is clean",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No forgotten tracks found matching your rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/*                              TRASH TAB CONTENT                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun TrashTabContent(
    uiState: StorageSenseUiState,
    viewModel: StorageSenseViewModel,
    isDark: Boolean
) {
    val trashItems = uiState.trashItems
    val bannerBg = if (isDark) Color(0xFF1E2838) else Color(0xFFE5ECFB)
    val iconBg = if (isDark) Color(0xFF2A374D) else Color(0xFFD4E0F8)

    var showEmptyConfirmation by remember { mutableStateOf(false) }

    if (showEmptyConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEmptyConfirmation = false },
            title = { Text("Empty Trash?") },
            text = { Text("Permanently delete ${trashItems.size} track(s) from device storage? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyConfirmation = false
                        viewModel.emptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card matching Screenshot 2
        item(key = "in_trash_banner", contentType = "banner") {
            val totalBytes = trashItems.sumOf { it.fileSizeBytes }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = bannerBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreTime,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF90B4E8) else Color(0xFF3E506D),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "IN TRASH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = if (isDark) Color(0xFF90B4E8) else Color(0xFF4C5B75)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatBytes(totalBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${trashItems.size} track(s) recoverable until they expire",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (trashItems.isEmpty()) {
            item(key = "empty_trash_banner", contentType = "info") {
                val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = cardBg,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE5E7EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.smart_storage_trash_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.smart_storage_trash_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
            item(key = "trash_actions", contentType = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restoreAllFromTrash() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.smart_storage_restore_all))
                    }
                    Button(
                        onClick = { showEmptyConfirmation = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.smart_storage_empty_trash))
                    }
                }
            }

            items(
                items = trashItems,
                key = { it.songId },
                contentType = { "trash_item" }
            ) { item ->
                TrashItemCard(
                    item = item,
                    onRestore = { viewModel.restoreFromTrash(setOf(item.songId)) },
                    isDark = isDark
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TrashItemCard(
    item: StorageTrashItem,
    onRestore: () -> Unit,
    isDark: Boolean
) {
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = item.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.artist} · ${formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2A374D) else Color(0xFFE2E8F0))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/*                              RULES TAB CONTENT                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun RulesTabContent(
    uiState: StorageSenseUiState,
    viewModel: StorageSenseViewModel,
    isDark: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: What counts as forgotten
        item(key = "section_what_counts_title", contentType = "header") {
            Text(
                text = "What counts as forgotten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        // Card: Not played for
        item(key = "card_not_played_for", contentType = "card") {
            ScreenshotRulesCard(
                title = "Not played for",
                subtitle = "A track is only suggested once it has gone untouched this long.",
                isDark = isDark
            ) {
                val options = listOf(
                    30 to "1 month",
                    90 to "3 months",
                    180 to "6 months",
                    365 to "1 year",
                    730 to "2 years"
                )
                RadioOptionsPillGroup(
                    options = options,
                    selectedValue = uiState.unplayedDays,
                    onSelect = { viewModel.setSmartStorageUnplayedDays(it) },
                    isDark = isDark
                )
            }
        }

        // Pill Switch: Include never-played tracks
        item(key = "pill_include_never_played", contentType = "pill_switch") {
            DarkPillSwitchRow(
                title = "Include never-played tracks",
                checked = uiState.includeNeverPlayed,
                onCheckedChange = { viewModel.setSmartStorageIncludeNeverPlayed(it) },
                isDark = isDark
            )
        }

        // Card: Minimum file size
        item(key = "card_min_file_size", contentType = "card") {
            ScreenshotRulesCard(
                title = "Minimum file size",
                subtitle = "Skip small files that would not free meaningful space.",
                isDark = isDark
            ) {
                val options = listOf(
                    0 to "No minimum",
                    5 to "5 MB",
                    10 to "10 MB",
                    25 to "25 MB"
                )
                RadioOptionsPillGroup(
                    options = options,
                    selectedValue = uiState.minFileSizeMb,
                    onSelect = { viewModel.setSmartStorageMinFileSizeMb(it) },
                    isDark = isDark
                )
            }
        }

        // Section: Safety net
        item(key = "section_safety_net_title", contentType = "header") {
            Text(
                text = "Safety net",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        // Pill Switch: Never suggest favourites
        item(key = "pill_never_suggest_favorites", contentType = "pill_switch") {
            DarkPillSwitchRow(
                title = "Never suggest favourites",
                checked = uiState.neverSuggestFavorites,
                onCheckedChange = { viewModel.setSmartStorageNeverSuggestFavorites(it) },
                isDark = isDark
            )
        }

        // Card: Ignore recently added
        item(key = "card_ignore_recently_added", contentType = "card") {
            ScreenshotRulesCard(
                title = "Ignore recently added",
                subtitle = "Newly imported music is never suggested during this window.",
                isDark = isDark
            ) {
                val options = listOf(
                    7 to "7 days",
                    14 to "14 days",
                    30 to "30 days"
                )
                RadioOptionsPillGroup(
                    options = options,
                    selectedValue = uiState.ignoreRecentDays,
                    onSelect = { viewModel.setSmartStorageIgnoreRecentDays(it) },
                    isDark = isDark
                )
            }
        }

        // Section: Stream Cache & Automation
        item(key = "section_stream_cache_title", contentType = "header") {
            Text(
                text = "Stream Cache & Automation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        // Card: Auto-clear older streams
        item(key = "card_stream_ttl", contentType = "card") {
            ScreenshotRulesCard(
                title = "Auto-clear older streams",
                subtitle = "Auto-clears stream audio chunks older than retention window.",
                isDark = isDark
            ) {
                val options = listOf(
                    7 to "7 days",
                    14 to "14 days",
                    30 to "30 days",
                    90 to "90 days",
                    0 to "Never"
                )
                RadioOptionsPillGroup(
                    options = options,
                    selectedValue = uiState.streamCacheTtlDays,
                    onSelect = { viewModel.setStreamCacheTtlDays(it) },
                    isDark = isDark
                )
            }
        }

        // Card: Stream cache size limit
        item(key = "card_stream_max_size", contentType = "card") {
            ScreenshotRulesCard(
                title = "Stream cache size limit",
                subtitle = "Maximum storage space allocated for stream playback cache.",
                isDark = isDark
            ) {
                val options = listOf(
                    512 to "500 MB",
                    1024 to "1 GB",
                    2048 to "2 GB",
                    5120 to "5 GB"
                )
                RadioOptionsPillGroup(
                    options = options,
                    selectedValue = uiState.streamCacheMaxSizeMb,
                    onSelect = { viewModel.setStreamCacheMaxSizeMb(it) },
                    isDark = isDark
                )
            }
        }

        // Pill Switch: Auto-prune search history
        item(key = "pill_auto_prune_search", contentType = "pill_switch") {
            DarkPillSwitchRow(
                title = "Auto-prune search history",
                checked = uiState.autoPruneSearchHistory,
                onCheckedChange = { viewModel.setAutoPruneSearchHistory(it) },
                isDark = isDark
            )
        }

        // Pill Switch: Auto-clean invalid tracks
        item(key = "pill_auto_clean_invalid", contentType = "pill_switch") {
            DarkPillSwitchRow(
                title = "Auto-clean invalid tracks",
                checked = uiState.autoCleanInvalidSongs,
                onCheckedChange = { viewModel.setAutoCleanInvalidSongs(it) },
                isDark = isDark
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ScreenshotRulesCard(
    title: String,
    subtitle: String,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFF3F4F7)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun <T> RadioOptionsPillGroup(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    isDark: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = selectedValue == value
            val pillBg = when {
                isSelected && isDark -> Color(0xFF2A374D)
                isSelected && !isDark -> Color(0xFFE2EAFD)
                else -> Color.Transparent
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(value) },
                color = pillBg,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(value) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (isDark) Color(0xFF90B4E8) else Color(0xFF3E4756),
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkPillSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    val pillBg = if (isDark) Color(0xFF2E3846) else Color(0xFF3E4756)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = pillBg,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isDark) Color(0xFF5A687D) else Color(0xFF2B3442),
                    uncheckedThumbColor = Color(0xFFCBD5E1),
                    uncheckedTrackColor = Color(0xFF475569)
                )
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/*                                    HELPERS                                */
/* ------------------------------------------------------------------------- */

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatUnplayedDays(days: Int, isNeverPlayed: Boolean): String {
    if (isNeverPlayed) return "Never played"
    return when {
        days < 30 -> "$days d unplayed"
        days < 365 -> "${days / 30} mo unplayed"
        else -> "${days / 365} yr unplayed"
    }
}
