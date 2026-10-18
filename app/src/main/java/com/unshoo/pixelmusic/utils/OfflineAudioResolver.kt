package com.unshoo.pixelmusic.utils

import android.content.Context
import android.net.Uri
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import java.io.File
import java.util.Locale

/**
 * Single source of truth for checking if a song has verified 100% offline audio on disk,
 * and resolving alternative stream keys for deduplication.
 */
object OfflineAudioResolver {

    /**
     * Returns true if the song has an accessible offline audio source
     * (verified physical file with size > 0, valid readable content:// URI, or downloaded YouTube audio file).
     * If there is metadata but no valid audio on disk/storage, this returns false.
     */
    fun hasOfflineAudio(
        context: Context,
        song: Song
    ): Boolean {
        // 1. Direct path check (local MediaStore / scanned folder / mapped file)
        if (song.path.isNotBlank()) {
            val file = File(song.path)
            if (file.exists() && file.isFile && file.length() > 0L) return true
        }

        // 2. Local URI check (file:// or content://)
        if (song.contentUriString.startsWith("file://")) {
            val parsedPath = Uri.parse(song.contentUriString).path
            if (!parsedPath.isNullOrBlank()) {
                val file = File(parsedPath)
                if (file.exists() && file.isFile && file.length() > 0L) return true
            }
        }
        if (song.contentUriString.startsWith("content://")) {
            if (isContentUriAccessible(context, song.contentUriString)) {
                return true
            }
        }

        // 3. Downloaded YouTube audio check
        val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
        if (ytId != null) {
            return getOfflineAudioFile(context, song) != null
        }

        return false
    }

    private fun isContentUriAccessible(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length > 0L || afd.length == android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Suspending version that also verifies cached Telegram files when telegramRepository is provided.
     */
    suspend fun hasOfflineAudioWithTelegram(
        context: Context,
        song: Song,
        telegramRepository: TelegramRepository? = null
    ): Boolean {
        if (hasOfflineAudio(context, song)) return true
        if (song.telegramFileId != null && song.telegramFileId > 0 && telegramRepository != null) {
            if (telegramRepository.isFileCached(song.telegramFileId)) return true
        }
        return false
    }

    /**
     * Resolves the offline File if present on disk with valid size, or null otherwise.
     */
    fun getOfflineAudioFile(context: Context, song: Song): File? {
        if (song.path.isNotBlank()) {
            val file = File(song.path)
            if (file.exists() && file.isFile && file.length() > 0L) return file
        }
        val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
        if (ytId != null) {
            val audioDir = PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
            val fallbackAudioDir = File(context.filesDir, "audio_files")
            val candidateDirs = listOf(audioDir, fallbackAudioDir)
            val possibleExtensions = listOf("flac", "webm", "m4a", "opus", "mp3", "ogg", "aac", "wav")
            for (dir in candidateDirs) {
                for (ext in possibleExtensions) {
                    val f = File(dir, "$ytId.$ext")
                    if (f.exists() && f.isFile && f.length() > 0L) return f
                }
            }
        }
        return null
    }

    /**
     * Returns a normalized key for matching titles & artists across different sources (local, telegram, youtube).
     */
    fun alternativeKey(title: String, artist: String): String? {
        val normalizedTitle = title.normalizeMetadataText()
        val normalizedArtist = artist.normalizeMetadataText()
        if (normalizedTitle.isNullOrBlank() || normalizedArtist.isNullOrBlank()) return null
        return "${normalizedTitle.lowercase(Locale.ROOT)}|${normalizedArtist.lowercase(Locale.ROOT)}"
    }

    /**
     * Deduplicates a list of songs by matching alternativeKey (or song id if key is null).
     * Retains original insertion order using stdlib groupBy.
     */
    fun deduplicateQueue(
        songs: List<Song>,
        startSongId: String? = null
    ): Pair<List<Song>, String?> {
        if (songs.size <= 1) return Pair(songs, startSongId)

        var resolvedStartSongId = startSongId
        val groups = songs.groupBy { alternativeKey(it.title, it.artist) ?: it.id }

        val result = groups.values.map { duplicates ->
            if (duplicates.size == 1) duplicates[0]
            else {
                val matchStart = if (startSongId != null) duplicates.firstOrNull { it.id == startSongId } else null
                val chosen = matchStart ?: duplicates.maxByOrNull { song ->
                    when {
                        song.path.isNotBlank() || song.contentUriString.startsWith("content://") || song.contentUriString.startsWith("file://") -> 2
                        song.telegramFileId != null || song.contentUriString.startsWith("telegram:") -> 1
                        else -> 0
                    }
                } ?: duplicates[0]
                if (matchStart != null) resolvedStartSongId = chosen.id
                chosen
            }
        }

        return Pair(result, resolvedStartSongId)
    }
}
