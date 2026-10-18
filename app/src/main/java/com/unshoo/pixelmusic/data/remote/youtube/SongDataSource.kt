package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.youtube.Song

import unshoo.ianshulyadav.pixelmusic.innertube.YouTube

class SongDataSource {
    suspend fun getSongInfo(songId: String): Song {
        val ytSongItem = runCatching {
            YouTube.song(songId).getOrNull()
        }.getOrNull()

        if (ytSongItem != null) {
            val converted = ytSongItem.toYoutubeSong()
            if (!converted.album.isNullOrBlank() || converted.title.isNotBlank()) {
                return converted
            }
        }

        return YoutubeHelper.extractSongInfo(
            YoutubeRequestHelper.getPlayerInfo(songId)
        )
    }

    fun search(query: String): List<Song> {
        return YoutubeHelper.extractSearchResults(
            YoutubeRequestHelper.search(query)
        )
    }

    fun getRelatedSongs(videoId: String): List<Song> {
        return YoutubeHelper.extractRelatedSongs(
            YoutubeRequestHelper.nextUp(videoId)
        )
    }
}
