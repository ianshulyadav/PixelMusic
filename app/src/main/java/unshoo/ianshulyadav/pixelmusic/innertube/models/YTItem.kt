/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */





package unshoo.ianshulyadav.pixelmusic.innertube.models

import androidx.compose.runtime.Immutable
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import java.util.Locale

// PERF: @Immutable on this hierarchy (and on HomePage/ChartsPage/ExplorePage) tells the Compose
// compiler these types are safe to treat as stable, even though they hold plain `List<...>`
// (an interface Compose otherwise can't prove is immutable). Every property below is a `val` of
// a primitive, String, or another immutable data class here, so the annotation is accurate.
// Without it, every composable on the Explore screen that takes a YTItem/SongItem/AlbumItem/
// PlaylistItem/ArtistItem parameter (YTItemCarousel, AlbumCarouselItem, PlaylistCardItem,
// ArtistCardItem, ...) is non-skippable and fully recomposes on every recomposition of its
// parent - which, given Explore's multi-stage background loading, happens often. That's why the
// Explore screen kept feeling laggy no matter how long it had been open, unlike the Library
// screens which use this project's own (already-@Immutable) domain models. See the app's
// `Playlist` data class for the same pattern already applied correctly elsewhere.
sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

@Immutable
data class Artist(
    val name: String,
    val id: String?,
)

@Immutable
data class Album(
    val name: String,
    val id: String? = null,
)

enum class AlbumReleaseType {
    ALBUM,
    SINGLE,
    EP;

    companion object {
        fun fromLabel(label: String?): AlbumReleaseType {
            return when (label?.trim()?.lowercase(Locale.ROOT)) {
                "single", "singles" -> SINGLE
                "ep", "eps" -> EP
                else -> ALBUM
            }
        }
    }
}

@Immutable
data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val likeStatus: String? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

@Immutable
data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val releaseType: AlbumReleaseType = AlbumReleaseType.ALBUM,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

@Immutable
data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
    val description: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

@Immutable
data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val subscriberCountText: String? = null,
    val monthlyListenerCountText: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filter {
            when (it) {
                is SongItem -> {
                    val musicVideoType = it.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    val isMusicVideo = musicVideoType == MUSIC_VIDEO_TYPE_OMV ||
                        musicVideoType == MUSIC_VIDEO_TYPE_UGC ||
                        (musicVideoType != null && musicVideoType != WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV) ||
                        com.unshoo.pixelmusic.utils.ContentFilterUtils.isVideoTitle(it.title)
                    !isMusicVideo
                }
                else -> true
            }
        }
    } else {
        this
    }
