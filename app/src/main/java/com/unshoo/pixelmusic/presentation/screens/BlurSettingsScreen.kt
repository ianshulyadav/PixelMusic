/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.unshoo.pixelmusic.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlurSettingsScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = stablePlayerState.currentSong != null
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = bottomInset + if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 24.dp
    val view = LocalView.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = stringResource(R.string.blur_settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 1. Master Switch Card (Use blur effects) ──────────────────────
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                tonalElevation = 2.dp,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    settingsViewModel.setUseBlurEffects(!uiState.useBlurEffects)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.blur_settings_use_blur_effects),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Switch(
                        checked = uiState.useBlurEffects,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            settingsViewModel.setUseBlurEffects(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            // ── 2. Section Title ─────────────────────────────────────────────
            Text(
                text = stringResource(R.string.blur_settings_section_customize),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
            )

            // ── 3. Grouped Toggles ───────────────────────────────────────────
            val blurContentAlpha by animateFloatAsState(
                targetValue = if (uiState.useBlurEffects) 1.0f else 0.45f,
                animationSpec = spring(),
                label = "blur_content_alpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(blurContentAlpha),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BlurToggleItem(
                    title = stringResource(R.string.blur_settings_now_playing_title),
                    subtitle = stringResource(R.string.blur_settings_now_playing_desc),
                    icon = Icons.Outlined.Image,
                    checked = uiState.nowPlayingBlurEnabled,
                    enabled = uiState.useBlurEffects,
                    onCheckedChange = { settingsViewModel.setNowPlayingBlurEnabled(it) }
                )

                BlurToggleItem(
                    title = stringResource(R.string.blur_settings_player_expansion_title),
                    subtitle = stringResource(R.string.blur_settings_player_expansion_desc),
                    icon = Icons.Outlined.PictureInPicture,
                    checked = uiState.playerExpansionBlurEnabled,
                    enabled = uiState.useBlurEffects,
                    onCheckedChange = { settingsViewModel.setPlayerExpansionBlurEnabled(it) }
                )


                BlurToggleItem(
                    title = stringResource(R.string.blur_settings_bottom_sheet_title),
                    subtitle = stringResource(R.string.blur_settings_bottom_sheet_desc),
                    icon = Icons.Outlined.WaterDrop,
                    checked = uiState.bottomSheetBlurEnabled,
                    enabled = uiState.useBlurEffects,
                    onCheckedChange = { settingsViewModel.setBottomSheetBlurEnabled(it) }
                )

                BlurToggleItem(
                    title = stringResource(R.string.blur_settings_story_card_title),
                    subtitle = stringResource(R.string.blur_settings_story_card_desc),
                    icon = Icons.Outlined.Style,
                    checked = uiState.storyCardBlurEnabled,
                    enabled = uiState.useBlurEffects,
                    onCheckedChange = { settingsViewModel.setStoryCardBlurEnabled(it) }
                )

                BlurToggleItem(
                    title = stringResource(R.string.blur_settings_lyrics_screen_title),
                    subtitle = stringResource(R.string.blur_settings_lyrics_screen_desc),
                    icon = Icons.Outlined.MusicNote,
                    checked = uiState.lyricsScreenBlurEnabled,
                    enabled = uiState.useBlurEffects,
                    onCheckedChange = { settingsViewModel.setLyricsScreenBlurEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(contentBottomPadding))
        }
    }
}

@Composable
private fun BlurToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        onClick = {
            if (enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onCheckedChange(!checked)
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    if (enabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onCheckedChange(it)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}
