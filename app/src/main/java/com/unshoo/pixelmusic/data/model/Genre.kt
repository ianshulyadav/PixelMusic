package com.unshoo.pixelmusic.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: String,
    val name: String,
    val iconResId: Int? = null,
    val lightColorHex: String? = null,
    val onLightColorHex: String? = null,
    val darkColorHex: String? = null,
    val onDarkColorHex: String? = null
)
