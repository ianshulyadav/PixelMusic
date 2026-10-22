package com.unshoo.pixelmusic.presentation.components.scoped

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
internal fun rememberPrewarmFullPlayer(currentSongId: String?): Boolean {
    // Disabled: composing FullPlayerContent in an invisible alpha=0 layer doubles
    // composition overhead and queue flow subscriptions while the player sheet is collapsed.
    return false
}
