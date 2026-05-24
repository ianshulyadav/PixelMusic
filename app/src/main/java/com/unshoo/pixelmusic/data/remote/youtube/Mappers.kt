package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.ArtistRef
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem

fun upgradeThumbnailUrlToHighQuality(url: String?): String? {
    if (url.isNullOrBlank()) return url
    val resizeRegex = Regex("=w\\d+-h\\d+.*")
    if (resizeRegex.containsMatchIn(url)) {
        return url.replace(resizeRegex, "=w1000-h1000")
    }
    if (url.contains("googleusercontent.com")) {
        return if (url.contains("=")) {
            url.substringBeforeLast("=") + "=w1000-h1000"
        } else {
            "$url=w1000-h1000"
        }
    }
    return url
}

fun SongItem.toNativeSong(): Song {
    val artistName = artists.joinToString { it.name }
    val artistIdHash = artists.firstOrNull()?.name?.hashCode()?.toLong() ?: 0L
    val songId = "youtube_$id"
    val artistRefs = artists.map { artist ->
        ArtistRef(
            id = artist.name.hashCode().toLong(),
            name = artist.name,
            isPrimary = artist == artists.firstOrNull()
        )
    }
    return Song(
        id = songId,
        title = title,
        artist = artistName,
        artistId = artistIdHash,
        artists = artistRefs,
        album = album?.name ?: "YouTube Music",
        albumId = (album?.name ?: "YouTube Music").hashCode().toLong(),
        albumArtist = artistName,
        path = "",
        contentUriString = "youtube://$id",
        albumArtUriString = upgradeThumbnailUrlToHighQuality(thumbnail),
        duration = (duration ?: 0) * 1000L,
        genre = "YouTube",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/mpeg",
        bitrate = 128,
        sampleRate = 44100,
        telegramFileId = null,
        telegramChatId = null,
        neteaseId = null,
        gdriveFileId = null,
        qqMusicMid = null,
        navidromeId = null,
        jellyfinId = null,
        youtubeId = id
    )
}

fun com.unshoo.pixelmusic.data.model.youtube.Song.toNativeSong(): Song {
    val durationMillis = parseDurationStringToMillis(duration)
    val artistIdHash = artist.hashCode().toLong()
    val songId = "youtube_$youtubeId"
    return Song(
        id = songId,
        title = title,
        artist = artist,
        artistId = artistIdHash,
        artists = listOf(ArtistRef(id = artistIdHash, name = artist, isPrimary = true)),
        album = "YouTube Music",
        albumId = "YouTube Music".hashCode().toLong(),
        albumArtist = artist,
        path = audioFilePath.orEmpty(),
        contentUriString = "youtube://$youtubeId",
        albumArtUriString = upgradeThumbnailUrlToHighQuality(thumbnailPath ?: thumbnailHref),
        duration = durationMillis,
        genre = "YouTube",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/mpeg",
        bitrate = 128,
        sampleRate = 44100,
        telegramFileId = null,
        telegramChatId = null,
        neteaseId = null,
        gdriveFileId = null,
        qqMusicMid = null,
        navidromeId = null,
        jellyfinId = null,
        youtubeId = youtubeId
    )
}

private fun parseDurationStringToMillis(durationStr: String): Long {
    if (durationStr.isBlank()) return 0L
    val parts = durationStr.split(":")
    return try {
        when (parts.size) {
            1 -> parts[0].toLong() * 1000L
            2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
            3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
            else -> 0L
        }
    } catch (e: Exception) {
        0L
    }
}
