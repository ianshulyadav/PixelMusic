package com.theveloper.pixelplay.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.database.youtube.AppDatabase
import com.theveloper.pixelplay.data.model.youtube.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue

class SongDownloadWorker(
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
            val songId = params.inputData.getString(SONG_KEY)
                ?: return@withContext Result.failure()

            var song = localSongRepository.getSong(songId)
            if (song == null) {
                var fetchedSong: Song? = null
                songRepository.getSongInfo(songId).collect { apiResult ->
                    if (apiResult is ApiResult.Success) {
                        fetchedSong = apiResult.data
                    }
                }
                song = fetchedSong ?: return@withContext Result.failure()
                localSongRepository.create(song)
            }

            if (playlistId != null) {
                val playlist = playlistRepository.getPlaylistById(playlistId)
                if (playlist != null) {
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
                }
            }

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
                    DownloadHelper.downloadAudio(
                        appContext, song,
                    )
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
                    copyToPublicDownload(appContext, audioPath, song.title, song.artist)
                }

                UmihiNotificationManager.showSongDownloadSuccess(appContext, song)
                Result.success()
            } catch (_: CancellationException) {
                UmihiHelper.printd("Song download canceled ${song.title}")
                Result.failure()
            } catch (e: Exception) {
                UmihiNotificationManager.showSongDownloadFailed(
                    appContext,
                    song
                )
                UmihiHelper.printe(
                    message = "Error downloading song: ${song.youtubeId}",
                    exception = e
                )
                Result.failure()
            }
        }
    }

    private fun copyToPublicDownload(context: Context, sourceFilePath: String, songTitle: String, artistName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null

            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeArtist = artistName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeTitle - $safeArtist.webm"

            val publicDownloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PixelPlayer"
            )
            if (!publicDownloadDir.exists()) {
                publicDownloadDir.mkdirs()
            }
            val destinationFile = File(publicDownloadDir, fileName)

            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("audio/webm"),
                null
            )

            return destinationFile
        } catch (e: Exception) {
            UmihiHelper.printe("Failed to copy to public downloads: ${e.message}", exception = e)
            return null
        }
    }

    companion object {
        const val PLAYLIST_KEY = "playlist"
        const val SONG_KEY = "song"
        private val client = OkHttpClient()
    }
}
