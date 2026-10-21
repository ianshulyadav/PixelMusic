package com.unshoo.pixelmusic.data.feed

import androidx.compose.runtime.Immutable
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.utils.ContentFilterUtils

@Immutable
data class FeedQuickTile(
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val actionVideoId: String? = null,
    val playlistId: String? = null,
    val localPlaylistId: Long? = null,
    val isLiked: Boolean = false,
    val collection: String? = null,
)

@Immutable
data class FeedMix(val title: String, val seed: YouTubeMusicTrack)

@Immutable
data class FeedSectionData<T>(
    val title: String,
    val subtitle: String? = null,
    val items: List<T>,
)

@Immutable
data class FeedArtist(
    val name: String,
    val browseId: String? = null,
    val artworkUrl: String? = null,
)

@Immutable
data class FeedAlbum(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val browseId: String? = null,
)

@Immutable
data class FeedTopAlbum(
    val name: String,
    val artist: String,
    val artworkUrl: String? = null,
    val playCount: Long = 0L,
)

@Immutable
data class FeedSpotlight(
    val artistName: String,
    val artworkUrl: String? = null,
    val browseId: String? = null,
    val description: String? = null,
    val topTrackTitle: String? = null,
)

@Immutable
data class YouTubeMusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationSeconds: Int? = null,
    val isVideo: Boolean = false,
) {
    fun toSong(): Song {
        val cleanArtist = ArtistHelper.primaryArtist(artist)
        val artistsList = ArtistHelper.splitArtists(artist).mapIndexed { idx, aName ->
            ArtistRef(
                id = -(17_000_000_000_000L + kotlin.math.abs(aName.lowercase().hashCode().toLong())),
                name = aName,
                isPrimary = idx == 0
            )
        }
        val albumTitle = album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
            val isVid = isVideo || ContentFilterUtils.isVideoTitle(title)
            return Song(
                id = "youtube_$videoId",
                title = title,
                artist = cleanArtist,
                artistId = artistsList.firstOrNull()?.id ?: 0L,
                artists = artistsList,
                album = albumTitle,
                albumId = -(16_000_000_000_000L + kotlin.math.abs(albumTitle.lowercase().hashCode().toLong())),
                albumArtist = cleanArtist,
                path = "",
                contentUriString = "youtube://$videoId",
                albumArtUriString = artworkUrl,
                duration = (durationSeconds ?: 0) * 1000L,
                genre = "YouTube",
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis(),
                mimeType = if (isVid) "video/mp4" else "audio/opus",
                bitrate = 128000,
                sampleRate = 44100,
                youtubeId = videoId
            )
        }
}

@Immutable
data class YouTubePlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val trackCountText: String? = null,
    val artworkUrl: String? = null,
)

@Immutable
data class YouTubePlaylistResult(
    val id: String,
    val title: String,
    val author: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val tracks: List<YouTubeMusicTrack> = emptyList(),
)

@Immutable
data class GeneratedTrack(
    val name: String,
    val artist: String,
    val artworkUrl: String? = null,
    val url: String = "",
    val listeners: Long? = null,
    val playcount: Long? = null,
    val match: Double? = null,
    val album: String? = null,
) {
    val key: String get() = "${name.trim().lowercase()}|${artist.trim().lowercase()}"
    val title: String get() = name
}

fun GeneratedTrack.youtubeVideoIdOrNull(): String? {
    if (url.isBlank()) return null
    val clean = when {
        url.startsWith("youtube://") -> url.removePrefix("youtube://")
        url.startsWith("youtube_") -> url.removePrefix("youtube_")
        else -> url
    }
    if (clean.length == 11 && clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
        return clean
    }
    val match = Regex("(?:v=|/vi/|/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})").find(url)
    return match?.groupValues?.getOrNull(1)
}

fun GeneratedTrack.toSong(): Song {
    val videoId = youtubeVideoIdOrNull() ?: ""
    val cleanArtist = ArtistHelper.primaryArtist(artist)
    val artistsList = ArtistHelper.splitArtists(artist).mapIndexed { idx, aName ->
        ArtistRef(
            id = -(17_000_000_000_000L + kotlin.math.abs(aName.lowercase().hashCode().toLong())),
            name = aName,
            isPrimary = idx == 0
        )
    }
    val albumTitle = album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
    return Song(
        id = if (videoId.isNotBlank()) "youtube_$videoId" else "generated_${name.hashCode()}",
        title = name,
        artist = cleanArtist,
        artistId = artistsList.firstOrNull()?.id ?: 0L,
        artists = artistsList,
        album = albumTitle,
        albumId = -(16_000_000_000_000L + kotlin.math.abs(albumTitle.lowercase().hashCode().toLong())),
        albumArtist = cleanArtist,
        path = "",
        contentUriString = if (videoId.isNotBlank()) "youtube://$videoId" else url,
        albumArtUriString = artworkUrl,
        duration = 0L,
        genre = "Feed",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/opus",
        bitrate = 128000,
        sampleRate = 44100,
        youtubeId = videoId.takeIf { it.isNotBlank() }
    )
}

@Immutable
data class ImageDto(
    val url: String,
    val size: String = "",
)

@Immutable
data class RecentTrackArtistRef(
    val name: String = "",
    val displayName: String = name,
)

@Immutable
data class RecentTrackDate(
    val uts: String = "",
    val text: String = "",
)

@Immutable
data class RecentTrack(
    val name: String,
    val artist: RecentTrackArtistRef,
    val album: RecentTrackArtistRef = RecentTrackArtistRef(),
    val image: List<ImageDto> = emptyList(),
    val url: String = "",
    val date: RecentTrackDate? = null,
) {
    val artworkUrl: String? get() = image.lastOrNull()?.url?.takeIf(ArtworkNormalizer::isRealImage)
}

fun RecentTrack.toSong(): Song {
    val videoId = GeneratedTrack(name, artist.displayName, artworkUrl, url).youtubeVideoIdOrNull() ?: ""
    val cleanArtist = ArtistHelper.primaryArtist(artist.displayName)
    val artistsList = ArtistHelper.splitArtists(artist.displayName).mapIndexed { idx, aName ->
        ArtistRef(
            id = -(17_000_000_000_000L + kotlin.math.abs(aName.lowercase().hashCode().toLong())),
            name = aName,
            isPrimary = idx == 0
        )
    }
    val albumTitle = album.displayName.takeIf { it.isNotBlank() } ?: "YouTube Music"
    val songId = when {
        videoId.isNotBlank() -> "youtube_$videoId"
        url.isNotBlank() -> url
        else -> "recent_${name.hashCode()}"
    }
    val contentUri = when {
        videoId.isNotBlank() -> "youtube://$videoId"
        url.startsWith("youtube://") -> url
        url.startsWith("youtube_") -> "youtube://${url.removePrefix("youtube_")}"
        else -> url
    }
    return Song(
        id = songId,
        title = name,
        artist = cleanArtist,
        artistId = artistsList.firstOrNull()?.id ?: 0L,
        artists = artistsList,
        album = albumTitle,
        albumId = -(16_000_000_000_000L + kotlin.math.abs(albumTitle.lowercase().hashCode().toLong())),
        albumArtist = cleanArtist,
        path = "",
        contentUriString = contentUri,
        albumArtUriString = artworkUrl,
        duration = 0L,
        genre = "Recent",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/opus",
        bitrate = 128000,
        sampleRate = 44100,
        youtubeId = videoId.takeIf { it.isNotBlank() }
    )
}

@Immutable
data class FriendEntry(
    val name: String,
    val displayName: String = name,
    val realName: String? = null,
    val avatarUrl: String? = null,
    val lastTrack: RecentTrack? = null,
)

@Immutable
data class TasteProfile(
    val artistAffinity: Map<String, Double> = emptyMap(),
    val topTracksRaw: List<GeneratedTrack> = emptyList(),
    val topArtistsRaw: List<String> = emptyList(),
    val topTags: Set<String> = emptySet(),
    val hasPersonalSignals: Boolean = false,
    val builtAtMillis: Long = System.currentTimeMillis(),
)

data class YtMusicTasteSignals(
    val recentTracks: List<YouTubeMusicTrack> = emptyList(),
    val likedTracks: List<YouTubeMusicTrack> = emptyList(),
    val feedTracks: List<YouTubeMusicTrack> = emptyList(),
)

@Immutable
data class FeedData(
    val isYtConnected: Boolean = false,
    val ytAccountName: String? = null,
    val userName: String? = null,
    val hasYtRecommendations: Boolean = false,
    val hasYtMixes: Boolean = false,
    val hasPersonalContent: Boolean = false,
    val tasteTags: List<String> = emptyList(),
    val ytSuggestedPlaylists: List<YouTubePlaylistSummary> = emptyList(),
    val spotlight: FeedSpotlight? = null,
    val quickTiles: List<FeedQuickTile> = emptyList(),
    val quickPicks: List<YouTubeMusicTrack> = emptyList(),
    val newReleases: List<YouTubePlaylistSummary> = emptyList(),
    val charts: List<YouTubeMusicTrack> = emptyList(),
    val mixes: List<FeedMix> = emptyList(),
    val jumpBackIn: List<RecentTrack> = emptyList(),
    val recentAlbums: List<FeedAlbum> = emptyList(),
    val topArtists: List<FeedArtist> = emptyList(),
    val heavyRotation: List<GeneratedTrack> = emptyList(),
    val ytLikedSongs: List<YouTubeMusicTrack> = emptyList(),
    val ytRecentSongs: List<YouTubeMusicTrack> = emptyList(),
    val becauseYouListenTo: FeedSectionData<YouTubeMusicTrack>? = null,
    val freshFinds: List<YouTubeMusicTrack> = emptyList(),
    val friends: List<FriendEntry> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
)
