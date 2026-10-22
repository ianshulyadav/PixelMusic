package com.unshoo.pixelmusic.presentation.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.unshoo.pixelmusic.R

enum class SettingsCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    LIBRARY(
        id = "library",
        titleRes = R.string.settings_category_library_title,
        subtitleRes = R.string.settings_category_library_subtitle,
        icon = Icons.Rounded.LibraryMusic
    ),
    CONTENT(
        id = "content",
        titleRes = R.string.settings_category_content_title,
        subtitleRes = R.string.settings_category_content_subtitle,
        icon = Icons.Rounded.Language
    ),
    APPEARANCE(
        id = "appearance",
        titleRes = R.string.settings_category_appearance_title,
        subtitleRes = R.string.settings_category_appearance_subtitle,
        icon = Icons.Rounded.Palette
    ),
    PLAYBACK(
        id = "playback",
        titleRes = R.string.settings_category_playback_title,
        subtitleRes = R.string.settings_category_playback_subtitle,
        icon = Icons.Rounded.MusicNote // Using MusicNote again or maybe PlayCircle if available
    ),
    BEHAVIOR(
        id = "behavior",
        titleRes = R.string.settings_category_behavior_title,
        subtitleRes = R.string.settings_category_behavior_subtitle,
        iconRes = R.drawable.rounded_touch_app_24
    ),
    AI_INTEGRATION(
        id = "ai",
        titleRes = R.string.settings_category_ai_title,
        subtitleRes = R.string.settings_category_ai_subtitle,
        iconRes = R.drawable.gemini_ai
    ),
    BACKUP_RESTORE(
        id = "backup_restore",
        titleRes = R.string.settings_category_backup_title,
        subtitleRes = R.string.settings_category_backup_subtitle,
        iconRes = R.drawable.rounded_upload_file_24
    ),
    DEVELOPER(
        id = "developer",
        titleRes = R.string.settings_category_developer_title,
        subtitleRes = R.string.settings_category_developer_subtitle,
        icon = Icons.Rounded.DeveloperMode
    ),
    STORAGE(
        id = "storage",
        titleRes = R.string.settings_category_storage_title,
        subtitleRes = R.string.settings_category_storage_subtitle,
        icon = Icons.Rounded.Storage
    ),
    EQUALIZER(
        id = "equalizer",
        titleRes = R.string.settings_category_equalizer_title,
        subtitleRes = R.string.settings_category_equalizer_subtitle,
        icon = Icons.Rounded.GraphicEq
    ),
    DEVICE_CAPABILITIES(
        id = "device_capabilities",
        titleRes = R.string.settings_category_device_capabilities_title,
        subtitleRes = R.string.settings_category_device_capabilities_subtitle,
        icon = Icons.Rounded.DeveloperBoard // Placeholder, maybe Memory or SettingsInputComponent
    ),
    ABOUT(
        id = "about",
        titleRes = R.string.settings_category_about_title,
        subtitleRes = R.string.settings_category_about_subtitle,
        icon = Icons.Rounded.Info
    ),
    LASTFM(
        id = "lastfm",
        titleRes = R.string.lastfm_integration,
        subtitleRes = R.string.lastfm_integration_desc,
        icon = Icons.Rounded.MusicNote
    ),
    LOSSLESS_SOURCES(
        id = "lossless_sources",
        titleRes = R.string.settings_category_lossless_title,
        subtitleRes = R.string.settings_category_lossless_subtitle,
        icon = Icons.Rounded.HighQuality
    );

    companion object {
        fun fromId(id: String): SettingsCategory? = entries.find { it.id == id }
    }
}
