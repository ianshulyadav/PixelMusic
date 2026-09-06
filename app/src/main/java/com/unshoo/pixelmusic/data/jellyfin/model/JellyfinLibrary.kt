package com.unshoo.pixelmusic.data.jellyfin.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents a media library (user view) on a Jellyfin server.
 *
 * Based on the Jellyfin "/Users/{userId}/Views" response. Only libraries with
 * [collectionType] "music" contain syncable audio; other views (movies, shows, ...)
 * are surfaced in the picker but cannot be selected.
 *
 * @property id The unique identifier of the library
 * @property name The display name of the library
 * @property collectionType Jellyfin collection type ("music", "movies", ...), null for mixed folders
 */
@Immutable
@Parcelize
data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String?
) : Parcelable {
    val isMusic: Boolean
        get() = collectionType == COLLECTION_TYPE_MUSIC

    companion object {
        const val COLLECTION_TYPE_MUSIC = "music"
    }
}
