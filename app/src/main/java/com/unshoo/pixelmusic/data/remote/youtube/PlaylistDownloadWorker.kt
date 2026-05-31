package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.absoluteValue

class PlaylistDownloadWorker(
    private val appContext: Context,
    private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun musicDao(): com.unshoo.pixelmusic.data.database.MusicDao
    }

    private val playlistRepository = AppDatabase.getInstance(appContext).playlistRepository()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()
    private val songRepository = SongRepository()
    private val musicDao = EntryPointAccessors.fromApplication(
        appContext,
        WorkerEntryPoint::class.java
    ).musicDao()

    @OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val playlistId = params.inputData.getString(PLAYLIST_KEY)
                ?: return@withContext Result.failure()

            val playlist = playlistRepository.getPlaylistById(playlistId)
                ?: return@withContext Result.failure()

            try {
                val totalSongs = playlist.songs.size
                var downloadedSongs = 0

                UmihiNotificationManager.showPlaylistDownloadProgress(
                    appContext,
                    playlist,
                    0,
                    totalSongs
                )

                val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)
                val playlistImage =
                    DownloadHelper.downloadImage(
                        appContext,
                        playlist.info.coverHref,
                        playlist.info.id
                    )
                playlistRepository.insertPlaylist(
                    playlist.info.copy(
                        coverPath = playlistImage?.path
                    )
                )

                playlist.songs.map { song ->
                    async {
                        semaphore.withPermit {
                            try {
                                var fullSong: Song? = null
                                songRepository.getSongInfo(song.youtubeId)
                                    .collect { apiResult ->
                                        when (apiResult) {
                                            is ApiResult.Success -> {
                                                fullSong = apiResult.data
                                            }
                                            else -> {}
                                        }
                                    }

                                val audioPath =
                                    DownloadHelper.downloadAudio(appContext, song)
                                val thumbnailPath =
                                    DownloadHelper.downloadImage(
                                        appContext,
                                        fullSong?.thumbnailHref ?: song.thumbnailHref,
                                        song.youtubeId
                                    )

                                val updatedSong = song.copy(
                                    thumbnailPath = thumbnailPath?.path,
                                    audioFilePath = audioPath,
                                )

                                localSongRepository.create(updatedSong)

                                ensureYoutubeSongInLibrary(updatedSong)

                                if (audioPath != null) {
                                    val mainId = -(15_000_000_000_000L + song.youtubeId.hashCode().toLong().absoluteValue)
                                    val parentDir = File(audioPath).parentFile?.absolutePath ?: ""
                                    musicDao.updateSongFilePathAndParent(mainId, audioPath, parentDir)
                                    
                                    playlistRepository.insertCrossRef(
                                        PlaylistSongCrossRef(
                                            playlistId = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
                                            songId = song.youtubeId
                                        )
                                    )
                                }
                                UmihiNotificationManager.showPlaylistDownloadProgress(
                                    appContext,
                                    playlist,
                                    ++downloadedSongs,
                                    totalSongs
                                )

                            } catch (e: CancellationException) {
                                UmihiHelper.printd("Song download canceled ${song.title}")
                            } catch (e: Exception) {
                                UmihiNotificationManager.showSongDownloadFailed(
                                    appContext,
                                    song
                                )
                                UmihiHelper.printe(
                                    message = "Error downloading song: ${song.title}",
                                    exception = e
                                )
                            }
                        }
                    }
                }.awaitAll()

                UmihiNotificationManager.showPlaylistDownloadSuccess(appContext, playlist)
                UmihiHelper.printd("Playlist download complete")
                Result.success()
            } catch (e: CancellationException) {
                UmihiNotificationManager.showPlaylistDownloadCanceled(appContext, playlist)
                UmihiHelper.printd("Playlist download canceled ${playlist.info.title}")
                Result.failure()
            } catch (e: Exception) {
                UmihiNotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                UmihiHelper.printe(message = e.toString(), exception = e)
                Result.failure()
            }
        }
    }

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long {
        return -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long {
        return -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeArtistId(artistName: String): Long {
        return -(17_000_000_000_000L + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun parseYoutubeArtistNames(artistStr: String): List<String> {
        if (artistStr.isBlank()) return listOf("Unknown Artist")
        val parsed = artistStr
            .split(Regex("\\s*[,/&;+、•]\\s*|\\s+(?:feat\\.|ft\\.|vs)\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private suspend fun ensureYoutubeSongInLibrary(song: Song) {
        val songId = toUnifiedYoutubeSongId(song.youtubeId)
        val title = song.title.takeIf { it.isNotBlank() } ?: "YouTube Video"
        val artist = song.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val artistNames = parseYoutubeArtistNames(artist)
        val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
        val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)

        val artistsToInsert = artistNames.map { name ->
            com.unshoo.pixelmusic.data.database.ArtistEntity(
                id = toUnifiedYoutubeArtistId(name),
                name = name,
                trackCount = 0,
                imageUrl = null
            )
        }

        val crossRefsToInsert = artistNames.mapIndexed { index, name ->
            val artistId = toUnifiedYoutubeArtistId(name)
            com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                songId = songId,
                artistId = artistId,
                isPrimary = index == 0
            )
        }

        val albumId = toUnifiedYoutubeAlbumId("YouTube Music")
        val albumName = "YouTube Music"
        val albumToInsert = com.unshoo.pixelmusic.data.database.AlbumEntity(
            id = albumId,
            title = albumName,
            artistName = primaryArtistName,
            artistId = primaryArtistId,
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = 0,
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref
        )

        val artistsJson = try {
            val arr = org.json.JSONArray()
            artistNames.forEachIndexed { idx, name ->
                val obj = org.json.JSONObject()
                obj.put("id", toUnifiedYoutubeArtistId(name))
                obj.put("name", name)
                obj.put("primary", idx == 0)
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            null
        }

        val durationMs = try {
            if (song.duration.contains(":")) {
                val parts = song.duration.split(":")
                when (parts.size) {
                    1 -> parts[0].toLong() * 1000L
                    2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
                    3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
                    else -> 0L
                }
            } else {
                song.duration.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }

        val songEntity = com.unshoo.pixelmusic.data.database.SongEntity(
            id = songId,
            title = title,
            artistName = artist,
            artistId = primaryArtistId,
            albumArtist = null,
            albumName = albumName,
            albumId = albumId,
            contentUriString = "youtube://${song.youtubeId}",
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref,
            duration = durationMs,
            genre = song.genre?.takeIf { it.isNotBlank() } ?: "YouTube Music",
            filePath = "",
            parentDirectoryPath = "youtube://",
            isFavorite = false,
            lyrics = null,
            trackNumber = 0,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            mimeType = "audio/webm",
            bitrate = null,
            sampleRate = null,
            telegramChatId = null,
            telegramFileId = null,
            artistsJson = artistsJson,
            sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE
        )

        musicDao.incrementalSyncMusicData(
            songs = listOf(songEntity),
            albums = listOf(albumToInsert),
            artists = artistsToInsert,
            crossRefs = crossRefsToInsert,
            deletedSongIds = emptyList()
        )
    }

    companion object {
        const val PLAYLIST_KEY = "playlist"
    }
}
