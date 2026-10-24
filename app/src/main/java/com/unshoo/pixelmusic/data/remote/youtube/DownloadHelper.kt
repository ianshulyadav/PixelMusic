package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.intPreferencesKey
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.preferences.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository

object DownloadHelper {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadHelperEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    private val client = YoutubeHelper.client.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadImage(context: Context, imageUrl: String, id: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val imageDir =
                    PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.THUMBNAILS_FOLDER)
                val imageFile = File(imageDir, "$id.jpg")

                if (imageFile.exists()) {
                    PixelMusicHelper.printd("Song Image $id was already downloaded")
                    return@withContext imageFile
                }

                URL(imageUrl).openStream().use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                imageFile

            } catch (e: Exception) {
                PixelMusicHelper.printe(
                    tag = "PlaylistDownloadWorker",
                    message = "Error Downloading Thumbnail",
                    exception = e
                )
                null
            }
        }
    }

    suspend fun downloadAudio(
        context: Context,
        song: Song,
        connections: Int = 8
    ): String? = withContext(Dispatchers.IO) {

        val userPreferencesRepository = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DownloadHelperEntryPoint::class.java
            ).userPreferencesRepository()
        } catch (_: Exception) {
            null
        }
        val downloadQuality = userPreferencesRepository?.downloadAudioQualityFlow?.first()
            ?: DownloadAudioQuality.HIGH
        val allowLossless = (downloadQuality == DownloadAudioQuality.MAX)

        // ── Check if audio file already exists locally with >= 80% data similarity and matching artist ──
        val existingLocalFile = com.unshoo.pixelmusic.utils.LocalAudioDuplicateMatcher.findMatchingLocalFilePath(
            context,
            song,
            allowLossless = allowLossless
        )
        if (existingLocalFile != null) {
            PixelMusicHelper.printd("Download skipped: '${song.title}' already exists locally at $existingLocalFile (>=80% match)")
            return@withContext existingLocalFile
        }

        val repo = DatastoreRepository(context)
        val customPath = repo.customDownloadPath.first()
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
        val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")

        if (customPath.isNotBlank()) {
            try {
                val treeUri = Uri.parse(customPath)
                val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                if (allowLossless) {
                    val existingFlac = documentDir?.findFile("$safeTitle - $safeArtist.flac")
                    if (existingFlac != null && existingFlac.exists()) {
                        return@withContext existingFlac.uri.toString()
                    }
                }
                val existingM4a = documentDir?.findFile("$safeTitle - $safeArtist.m4a")
                if (existingM4a != null && existingM4a.exists()) {
                    return@withContext existingM4a.uri.toString()
                }
                val existingWebm = documentDir?.findFile("$safeTitle - $safeArtist.webm")
                if (existingWebm != null && existingWebm.exists()) {
                    return@withContext existingWebm.uri.toString()
                }
            } catch (e: Exception) {
                PixelMusicHelper.printe("Error checking custom download path exists: ${e.message}")
            }
        }

        val audioDir =
            PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        val flacFile = File(audioDir, "${song.youtubeId}.flac")
        val m4aFile = File(audioDir, "${song.youtubeId}.m4a")
        val webmFile = File(audioDir, "${song.youtubeId}.webm")

        if (allowLossless && flacFile.exists() && flacFile.length() > 0) {
            return@withContext flacFile.absolutePath
        }
        if (m4aFile.exists() && m4aFile.length() > 0) {
            return@withContext m4aFile.absolutePath
        }
        if (webmFile.exists() && webmFile.length() > 0) {
            return@withContext webmFile.absolutePath
        }

        val tempFile = File(audioDir, "${song.youtubeId}.tmp")

        fun cleanupTempFile() {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        val isSaavnEnabled = userPreferencesRepository?.enableSaavnStreamingFlow?.first() ?: true
        val maxRetries = 3
        var lastException: Exception? = null
        var bypassLossless = !allowLossless
        var bypassSaavn = (downloadQuality == DownloadAudioQuality.LOW) || !isSaavnEnabled

        for (attempt in 1..maxRetries) {
            try {
                // Invalidate any cached/expired stream URL to guarantee fresh highest-quality URL
                YoutubeHelper.invalidateStreamCache(song.youtubeId)
                val url = YoutubeHelper.getSongPlayerUrlWithQuality(
                    context = context,
                    song = song,
                    maxBitrateKbps = 0,
                    forDownload = true,
                    bypassLossless = bypassLossless,
                    bypassSaavn = bypassSaavn
                )
                if (url.isBlank()) {
                    throw IOException("Empty stream URL for song ${song.youtubeId}")
                }

                val isLossless = url.contains(".flac", ignoreCase = true) ||
                    url.contains("flac", ignoreCase = true) ||
                    com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.isLosslessUri(url)

                if (!allowLossless && isLossless) {
                    PixelMusicHelper.printd("Stream URL for ${song.youtubeId} resolved to lossless but download quality is $downloadQuality. Bypassing lossless and retrying...")
                    bypassLossless = true
                    throw IOException("Bypassing unwanted lossless stream for quality $downloadQuality")
                }

                val isSaavn = url.contains("saavncdn.com") || url.contains("jiosaavn.com")
                val isM4a = isSaavn || url.contains(".mp4") || url.contains(".m4a")
                val ext = when {
                    isLossless -> "flac"
                    isM4a -> "m4a"
                    else -> "webm"
                }
                val mimeType = when {
                    isLossless -> "audio/flac"
                    isM4a -> "audio/mp4"
                    else -> "audio/webm"
                }
                val dynamicFileName = "$safeTitle - $safeArtist.$ext"
                val outputFile = File(audioDir, "${song.youtubeId}.$ext")

                val userAgent = if (isSaavn) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                } else {
                    Constants.YoutubeApi.USER_AGENT
                }

                // Determine total length for multi-part parallel downloading if requested
                var totalLength: Long = -1L
                try {
                    val headReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .apply {
                            if (isSaavn) {
                                header("Accept-Language", "en-IN,en;q=0.9")
                                header("Cookie", "explicit_content=1")
                            }
                        }
                        .header("Range", "bytes=0-0")
                        .build()

                    client.newCall(headReq).execute().use { res ->
                        val contentRange = res.header("Content-Range")
                        if (contentRange != null && contentRange.contains("/")) {
                            totalLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        }
                    }
                } catch (_: Exception) {}

                val numConnections = connections.coerceIn(1, 16)
                if (totalLength > 0 && numConnections > 1) {
                    val partFiles = (0 until numConnections).map { i -> File(audioDir, "${song.youtubeId}.part$i") }
                    fun cleanupPartFiles() {
                        partFiles.forEach { runCatching { it.delete() } }
                    }

                    try {
                        val chunkSize = totalLength / numConnections
                        (0 until numConnections).map { i ->
                            async {
                                val start = i * chunkSize
                                val end = if (i == numConnections - 1) totalLength - 1 else (start + chunkSize - 1)
                                val partFile = partFiles[i]

                                val req = Request.Builder()
                                    .url(url)
                                    .header("User-Agent", userAgent)
                                    .apply {
                                        if (isSaavn) {
                                            header("Accept-Language", "en-IN,en;q=0.9")
                                            header("Cookie", "explicit_content=1")
                                        }
                                    }
                                    .header("Range", "bytes=$start-$end")
                                    .header("Accept", "*/*")
                                    .build()

                                client.newCall(req).execute().use { response ->
                                    if (!response.isSuccessful && response.code != 206) {
                                        throw IOException("Failed chunk $i (HTTP ${response.code})")
                                    }
                                    response.body.byteStream().use { input ->
                                        FileOutputStream(partFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        }.awaitAll()

                        FileOutputStream(tempFile).use { out ->
                            partFiles.forEach { part ->
                                part.inputStream().use { it.copyTo(out) }
                            }
                        }
                        cleanupPartFiles()
                    } catch (e: Exception) {
                        cleanupPartFiles()
                        throw e
                    }
                } else {
                    // Single stream download with Range header
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .apply {
                            if (isSaavn) {
                                header("Accept-Language", "en-IN,en;q=0.9")
                                header("Cookie", "explicit_content=1")
                            }
                        }
                        .header("Range", "bytes=0-")
                        .header("Accept", "*/*")
                        .build()

                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            throw IOException("Failed download (HTTP ${response.code}) for song ${song.youtubeId}")
                        }

                        response.body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() <= 0) {
                    throw IOException("Downloaded file is zero bytes")
                }

                // Copy to custom path if set
                if (customPath.isNotBlank()) {
                    val treeUri = Uri.parse(customPath)
                    val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                    val file = documentDir?.createFile(mimeType, dynamicFileName)
                    val outputUri = file?.uri
                    if (outputUri != null) {
                        context.contentResolver.openOutputStream(outputUri)?.use { out ->
                            tempFile.inputStream().use { it.copyTo(out) }
                        }
                        cleanupTempFile()
                        return@withContext outputUri.toString()
                    }
                }

                // Finished downloading audio file successfully
                if (!tempFile.renameTo(outputFile)) {
                    tempFile.copyTo(outputFile, overwrite = true)
                    cleanupTempFile()
                }

                enforceStorageLimit(context, keepFile = outputFile)
                return@withContext outputFile.absolutePath

            } catch (e: CancellationException) {
                cleanupTempFile()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupTempFile()
                PixelMusicHelper.printe("Download attempt $attempt/$maxRetries failed for ${song.title}: ${e.message}")
                if (!bypassLossless) {
                    bypassLossless = true
                    PixelMusicHelper.printd("Lossless stream failed for ${song.title}, falling back to JioSaavn/Standard stream")
                } else if (!bypassSaavn) {
                    bypassSaavn = true
                    PixelMusicHelper.printd("High-quality stream failed for ${song.title}, falling back to YouTube audio stream")
                }
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(attempt * 600L)
                }
            }
        }

        PixelMusicHelper.printe("All $maxRetries download attempts failed for ${song.title}")
        return@withContext null
    }

    private suspend fun enforceStorageLimit(context: Context, keepFile: File? = null) = withContext(Dispatchers.IO) {
        // User downloaded songs and thumbnails are permanent offline media and must NOT be silently deleted.
        // Only clean up dangling temporary files (.tmp / .part) here.
        val audioDir = PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        audioDir.listFiles()?.filter { it.name.endsWith(".tmp") || it.name.contains(".part") }?.forEach { temp ->
            try { temp.delete() } catch (_: Exception) {}
        }
    }

    suspend fun copyToPublicDownload(context: Context, sourceFilePath: String, songTitle: String, artistName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null

            val isFlac = sourceFilePath.endsWith(".flac", ignoreCase = true)
            val isM4a = sourceFilePath.endsWith(".m4a", ignoreCase = true) || sourceFilePath.endsWith(".mp4", ignoreCase = true)
            val ext = when {
                isFlac -> "flac"
                isM4a -> "m4a"
                else -> "webm"
            }
            val mimeType = when {
                isFlac -> "audio/flac"
                isM4a -> "audio/mp4"
                else -> "audio/webm"
            }
            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val safeArtist = artistName.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val fileName = "$safeTitle - $safeArtist.$ext"

            val repo = DatastoreRepository(context)
            val customPath = repo.customDownloadPath.first()
            val publicDownloadDir = if (customPath.isNotBlank()) {
                File(customPath)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PixelMusic")
            }
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
                arrayOf(mimeType),
                null
            )

            return destinationFile
        } catch (e: Exception) {
            PixelMusicHelper.printe("Failed to copy to public downloads: ${e.message}", exception = e)
            return null
        }
    }
}
