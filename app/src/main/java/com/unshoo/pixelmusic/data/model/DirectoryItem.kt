package com.unshoo.pixelmusic.data.model

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class DirectoryItem(
    val path: String,
    val isAllowed: Boolean
) {
    val displayName: String
        get() = File(path).name.ifEmpty { path }
}
