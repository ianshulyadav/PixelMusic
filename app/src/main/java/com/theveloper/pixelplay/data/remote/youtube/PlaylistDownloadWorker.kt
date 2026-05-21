package com.theveloper.pixelplay.data.remote.youtube

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.database.youtube.AppDatabase
import com.theveloper.pixelplay.data.model.youtube.Song
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
        fun musicDao(): com.theveloper.pixelplay.data.database.MusicDao
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
                                if (audioPath != null) {
                                    val mainId = -(15_000_000_000_000L + song.youtubeId.hashCode().toLong().absoluteValue)
                                    musicDao.updateSongFilePath(mainId, audioPath)
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

    companion object {
        const val PLAYLIST_KEY = "playlist"
    }
}
