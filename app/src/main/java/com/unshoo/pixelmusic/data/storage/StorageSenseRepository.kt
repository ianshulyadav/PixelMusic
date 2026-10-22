package com.unshoo.pixelmusic.data.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.LyricsDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.utils.FileDeletionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StorageSpaceInfo(
    val totalDeviceBytes: Long,
    val freeDeviceBytes: Long,
    val appUsedBytes: Long,
    val streamCacheBytes: Long,
    val imageCacheBytes: Long,
    val generalCacheBytes: Long,
    val expiredStreamBytes: Long,
    val prunableSearchCount: Int,
    val lyricsCacheCount: Int,
    val duplicateGroupCount: Int,
    val duplicateWastedBytes: Long,
    val invalidSongCount: Int
) {
    val usedDeviceBytes: Long get() = maxOf(0L, totalDeviceBytes - freeDeviceBytes)
    val totalReclaimableBytes: Long get() = expiredStreamBytes + imageCacheBytes + generalCacheBytes
}

data class ForgottenSongCandidate(
    val song: Song,
    val lastPlayedTimestamp: Long,
    val fileSizeBytes: Long,
    val unplayedDays: Int,
    val isNeverPlayed: Boolean
)

data class StorageTrashItem(
    val songId: String,
    val filePath: String,
    val title: String,
    val artist: String,
    val fileSizeBytes: Long,
    val albumArtUri: String?,
    val deletedAt: Long,
    val unplayedDays: Int
)

data class DuplicateSongGroup(
    val groupKey: String,
    val title: String,
    val artist: String,
    val songs: List<Song>,
    val totalBytes: Long,
    val potentialSavingsBytes: Long
)

@Singleton
class StorageSenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamCacheManager: StreamCacheManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val musicDao: MusicDao,
    private val engagementDao: EngagementDao,
    private val lyricsDao: LyricsDao,
    private val favoritesDao: FavoritesDao
) {

    private val gson = Gson()
    private val trashFile: File
        get() = File(context.filesDir, "storage_trash.json")

    /**
     * Gathers complete storage metrics across device, app caches, duplicates, and orphans.
     */
    suspend fun getStorageSpaceInfo(): StorageSpaceInfo = withContext(Dispatchers.IO) {
        // Device storage statFs
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalDeviceBytes = stat.blockCountLong * blockSize
        val freeDeviceBytes = stat.availableBlocksLong * blockSize

        // Cache sizes
        val streamCacheBytes = streamCacheManager.getStreamCacheSizeBytes()
        val imageCacheBytes = streamCacheManager.getImageCacheSizeBytes()
        val generalCacheBytes = streamCacheManager.getGeneralCacheSizeBytes()

        val ttlDays = userPreferencesRepository.streamCacheTtlDaysFlow.first()
        val expiredStreamBytes = streamCacheManager.getExpiredStreamCacheSizeBytes(ttlDays)

        // Searches prunable
        val prunableSearchCount = musicRepository.getPrunableSearchHistoryCount(keepCount = 25)

        // Lyrics count
        val lyricsCount = runCatching { lyricsDao.getAll().size }.getOrDefault(0)

        // App footprint (cacheDir + filesDir + databases)
        val appUsedBytes = runCatching {
            val cacheSize = context.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            val filesSize = context.filesDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            val dbSize = context.getDatabasePath("pixelmusic_database")?.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            cacheSize + filesSize + dbSize
        }.getOrDefault(0L)

        // Song analysis
        val allSongs = musicRepository.getAllSongsOnce()
        val invalidCount = allSongs.count { it.isLocal && (it.path.isBlank() || !File(it.path).exists()) }

        val duplicates = findDuplicateSongGroups(allSongs)
        val duplicateGroupCount = duplicates.size
        val duplicateWastedBytes = duplicates.sumOf { it.potentialSavingsBytes }

        StorageSpaceInfo(
            totalDeviceBytes = totalDeviceBytes,
            freeDeviceBytes = freeDeviceBytes,
            appUsedBytes = appUsedBytes,
            streamCacheBytes = streamCacheBytes,
            imageCacheBytes = imageCacheBytes,
            generalCacheBytes = generalCacheBytes,
            expiredStreamBytes = expiredStreamBytes,
            prunableSearchCount = prunableSearchCount,
            lyricsCacheCount = lyricsCount,
            duplicateGroupCount = duplicateGroupCount,
            duplicateWastedBytes = duplicateWastedBytes,
            invalidSongCount = invalidCount
        )
    }

    /**
     * One-tap reclaimable cache cleanup:
     * - Prunes stream spans older than TTL
     * - Clears Coil image cache
     * - Clears temporary cache files
     * - Prunes search history to 25 items
     */
    suspend fun clearAllReclaimableCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val ttlDays = userPreferencesRepository.streamCacheTtlDaysFlow.first()
        freed += streamCacheManager.pruneExpiredStreams(ttlDays)
        freed += streamCacheManager.clearImageCache()
        freed += streamCacheManager.clearGeneralCache()
        musicRepository.pruneSearchHistory(keepCount = 25)
        freed
    }

    /**
     * Purges entire stream playback cache.
     */
    suspend fun clearStreamCache(): Long = streamCacheManager.clearAllStreamCache()

    /**
     * Purges image/artwork cache.
     */
    suspend fun clearImageCache(): Long = streamCacheManager.clearImageCache()

    /**
     * Purges downloaded synced lyrics cache.
     */
    suspend fun clearLyricsCache(): Unit = withContext(Dispatchers.IO) {
        lyricsDao.deleteAll()
    }

    /**
     * Prunes search history older than the 25th entry.
     */
    suspend fun pruneSearchHistory(): Int = withContext(Dispatchers.IO) {
        val count = musicRepository.getPrunableSearchHistoryCount(keepCount = 25)
        musicRepository.pruneSearchHistory(keepCount = 25)
        count
    }

    /**
     * Scans for forgotten songs matching the user's active Smart Storage rules.
     */
    suspend fun getForgottenSongCandidates(): List<ForgottenSongCandidate> = withContext(Dispatchers.IO) {
        val enabled = userPreferencesRepository.smartStorageEnabledFlow.first()
        if (!enabled) return@withContext emptyList()

        val unplayedDaysThreshold = userPreferencesRepository.smartStorageUnplayedDaysFlow.first()
        val includeNeverPlayed = userPreferencesRepository.smartStorageIncludeNeverPlayedFlow.first()
        val minFileSizeMb = userPreferencesRepository.smartStorageMinFileSizeMbFlow.first()
        val neverSuggestFavorites = userPreferencesRepository.smartStorageNeverSuggestFavoritesFlow.first()
        val ignoreRecentDays = userPreferencesRepository.smartStorageIgnoreRecentDaysFlow.first()

        val now = System.currentTimeMillis()
        val unplayedThresholdMs = TimeUnit.DAYS.toMillis(unplayedDaysThreshold.toLong())
        val recentGracePeriodMs = TimeUnit.DAYS.toMillis(ignoreRecentDays.toLong())
        val minFileSizeBytes = minFileSizeMb.toLong() * 1024L * 1024L

        val favoriteIds = if (neverSuggestFavorites) {
            favoritesDao.getFavoriteSongIdsOnce().toSet()
        } else {
            emptySet()
        }

        val engagementsMap = engagementDao.getAllEngagements().associateBy { it.songId }
        val trashedIds = getTrashItems().map { it.songId }.toSet()
        val allSongs = musicRepository.getAllSongsOnce()

        val candidates = mutableListOf<ForgottenSongCandidate>()

        for (song in allSongs) {
            if (!song.isLocal) continue
            if (trashedIds.contains(song.id)) continue

            // Safety net: never suggest favorites
            if (neverSuggestFavorites) {
                if (song.isFavorite) continue
                val longId = song.id.toLongOrNull()
                if (longId != null && favoriteIds.contains(longId)) continue
            }

            // Safety net: ignore recently added
            if (song.dateAdded > 0 && (now - song.dateAdded) < recentGracePeriodMs) {
                continue
            }

            // Check file existence & min size
            val file = if (song.path.isNotBlank()) File(song.path) else null
            val fileSizeBytes = if (file != null && file.exists()) file.length() else 0L
            if (fileSizeBytes < minFileSizeBytes) continue

            val engagement = engagementsMap[song.id]
            val lastPlayed = engagement?.lastPlayedTimestamp ?: 0L
            val playCount = engagement?.playCount ?: 0

            val isNeverPlayed = playCount == 0 || lastPlayed <= 0L

            if (isNeverPlayed) {
                if (includeNeverPlayed) {
                    val daysSinceAdded = if (song.dateAdded > 0) {
                        TimeUnit.MILLISECONDS.toDays(now - song.dateAdded).toInt()
                    } else unplayedDaysThreshold
                    candidates.add(
                        ForgottenSongCandidate(
                            song = song,
                            lastPlayedTimestamp = 0L,
                            fileSizeBytes = fileSizeBytes,
                            unplayedDays = daysSinceAdded,
                            isNeverPlayed = true
                        )
                    )
                }
            } else {
                val durationUnplayedMs = now - lastPlayed
                if (durationUnplayedMs >= unplayedThresholdMs) {
                    val daysUnplayed = TimeUnit.MILLISECONDS.toDays(durationUnplayedMs).toInt()
                    candidates.add(
                        ForgottenSongCandidate(
                            song = song,
                            lastPlayedTimestamp = lastPlayed,
                            fileSizeBytes = fileSizeBytes,
                            unplayedDays = daysUnplayed,
                            isNeverPlayed = false
                        )
                    )
                }
            }
        }

        // Sort largest files first, then longest unplayed
        candidates.sortedWith(
            compareByDescending<ForgottenSongCandidate> { it.fileSizeBytes }
                .thenByDescending { it.unplayedDays }
        )
    }

    /**
     * Reads all items currently in Smart Storage Trash.
     */
    suspend fun getTrashItems(): List<StorageTrashItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!trashFile.exists()) return@runCatching emptyList()
            val json = trashFile.readText()
            if (json.isBlank()) return@runCatching emptyList()
            val type = object : TypeToken<List<StorageTrashItem>>() {}.type
            gson.fromJson<List<StorageTrashItem>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * Moves candidates to the Smart Storage Trash list.
     */
    suspend fun moveToTrash(candidates: List<ForgottenSongCandidate>): Unit = withContext(Dispatchers.IO) {
        val currentTrash = getTrashItems().toMutableList()
        val now = System.currentTimeMillis()

        for (candidate in candidates) {
            val song = candidate.song
            if (currentTrash.none { it.songId == song.id }) {
                currentTrash.add(
                    StorageTrashItem(
                        songId = song.id,
                        filePath = song.path,
                        title = song.title,
                        artist = song.displayArtist,
                        fileSizeBytes = candidate.fileSizeBytes,
                        albumArtUri = song.albumArtUriString,
                        deletedAt = now,
                        unplayedDays = candidate.unplayedDays
                    )
                )
            }
        }

        saveTrash(currentTrash)
    }

    /**
     * Restores items from Trash back to library.
     */
    suspend fun restoreFromTrash(songIds: Set<String>): Unit = withContext(Dispatchers.IO) {
        val currentTrash = getTrashItems().filterNot { songIds.contains(it.songId) }
        saveTrash(currentTrash)
    }

    /**
     * Permanently deletes all items in Trash from disk and database.
     * Returns total bytes freed.
     */
    suspend fun emptyTrash(): Long = withContext(Dispatchers.IO) {
        val currentTrash = getTrashItems()
        var freedBytes = 0L
        val songIdsToDelete = mutableListOf<Long>()

        for (item in currentTrash) {
            freedBytes += item.fileSizeBytes
            // Delete actual file
            runCatching {
                if (item.filePath.isNotBlank()) {
                    FileDeletionUtils.deleteFile(context, item.filePath)
                }
            }
            item.songId.toLongOrNull()?.let { songIdsToDelete.add(it) }
        }

        if (songIdsToDelete.isNotEmpty()) {
            musicDao.deleteSongsAndRelatedData(songIdsToDelete)
        }

        saveTrash(emptyList())
        freedBytes
    }

    private fun saveTrash(items: List<StorageTrashItem>) {
        runCatching {
            trashFile.writeText(gson.toJson(items))
        }.onFailure {
            Timber.e(it, "Failed to persist storage trash")
        }
    }

    /**
     * Identifies duplicate song groups across local library.
     */
    suspend fun findDuplicateSongGroups(cachedSongs: List<Song>? = null): List<DuplicateSongGroup> = withContext(Dispatchers.IO) {
        val allSongs = cachedSongs ?: musicRepository.getAllSongsOnce()
        val localSongs = allSongs.filter { it.isLocal }

        val grouped = localSongs.groupBy { song ->
            val cleanTitle = song.title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
            val cleanArtist = song.artist.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
            "$cleanTitle|$cleanArtist"
        }.filter { it.value.size > 1 }

        grouped.map { (key, songs) ->
            val sizes = songs.map { song ->
                val file = if (song.path.isNotBlank()) File(song.path) else null
                if (file != null && file.exists()) file.length() else 0L
            }
            val totalBytes = sizes.sum()
            val potentialSavings = if (sizes.size > 1) sizes.drop(1).sum() else 0L
            val first = songs.first()

            DuplicateSongGroup(
                groupKey = key,
                title = first.title,
                artist = first.displayArtist,
                songs = songs,
                totalBytes = totalBytes,
                potentialSavingsBytes = potentialSavings
            )
        }
    }

    /**
     * Removes selected duplicate songs from library and disk.
     */
    suspend fun deleteDuplicateSongs(songsToDelete: List<Song>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val ids = mutableListOf<Long>()
        for (song in songsToDelete) {
            val file = if (song.path.isNotBlank()) File(song.path) else null
            if (file != null && file.exists()) {
                freed += file.length()
                FileDeletionUtils.deleteFile(context, song.path)
            }
            song.id.toLongOrNull()?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) {
            musicDao.deleteSongsAndRelatedData(ids)
        }
        freed
    }

    /**
     * Finds local songs in database whose audio file no longer exists.
     */
    suspend fun findInvalidSongs(): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getAllSongsOnce()
        allSongs.filter { song ->
            song.isLocal && (song.path.isBlank() || !File(song.path).exists())
        }
    }

    /**
     * Cleans missing local files and orphaned engagement records.
     * Returns count of cleaned items.
     */
    suspend fun cleanInvalidSongs(): Int = withContext(Dispatchers.IO) {
        val invalid = findInvalidSongs()
        val ids = invalid.mapNotNull { it.id.toLongOrNull() }
        if (ids.isNotEmpty()) {
            musicDao.deleteSongsAndRelatedData(ids)
        }
        engagementDao.deleteOrphanedEngagements()
        invalid.size
    }
}
