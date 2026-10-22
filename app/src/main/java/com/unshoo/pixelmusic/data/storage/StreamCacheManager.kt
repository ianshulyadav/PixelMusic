package com.unshoo.pixelmusic.data.storage

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.ExoCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class StreamCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoCache: ExoCache,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val streamCacheDir: File
        get() = File(context.cacheDir, Constants.ExoPlayer.Cache.NAME)

    private val imageCacheDir: File
        get() = context.cacheDir.resolve("image_cache")

    /**
     * Total bytes occupied by stream playback cache on disk.
     */
    suspend fun getStreamCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            val dir = streamCacheDir
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
        }.getOrDefault(0L)
    }

    /**
     * Calculates the bytes of stream cache that are older than [ttlDays].
     * If ttlDays <= 0, returns 0L.
     */
    suspend fun getExpiredStreamCacheSizeBytes(ttlDays: Int): Long = withContext(Dispatchers.IO) {
        if (ttlDays <= 0) return@withContext 0L
        var expiredBytes = 0L
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays.toLong())
        runCatching {
            val cache = exoCache.cache
            for (key in cache.keys) {
                val spans = cache.getCachedSpans(key)
                for (span in spans) {
                    if (span.lastTouchTimestamp < cutoff) {
                        expiredBytes += span.length
                    }
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to calculate expired stream cache size")
        }
        expiredBytes
    }

    /**
     * Evicts cached stream spans older than [ttlDays].
     * Returns total bytes reclaimed.
     */
    suspend fun pruneExpiredStreams(ttlDays: Int): Long = withContext(Dispatchers.IO) {
        if (ttlDays <= 0) return@withContext 0L
        var freedBytes = 0L
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays.toLong())
        runCatching {
            val cache = exoCache.cache
            val keys = cache.keys.toList()
            for (key in keys) {
                val spans = cache.getCachedSpans(key).toList()
                for (span in spans) {
                    if (span.lastTouchTimestamp < cutoff) {
                        val len = span.length
                        cache.removeSpan(span)
                        freedBytes += len
                    }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to prune expired stream cache")
        }
        freedBytes
    }

    /**
     * Enforces the max cache size cap (in megabytes).
     * If current cacheSpace exceeds [maxSizeMb], evicts oldest spans first until within budget.
     * Returns total bytes freed.
     */
    suspend fun enforceCacheCap(maxSizeMb: Int): Long = withContext(Dispatchers.IO) {
        if (maxSizeMb <= 0) return@withContext 0L
        val maxSizeBytes = maxSizeMb.toLong() * 1024L * 1024L
        var freedBytes = 0L
        runCatching {
            val cache = exoCache.cache
            var currentSpace = cache.cacheSpace
            if (currentSpace <= maxSizeBytes) return@withContext 0L

            val allSpans = mutableListOf<androidx.media3.datasource.cache.CacheSpan>()
            for (key in cache.keys) {
                allSpans.addAll(cache.getCachedSpans(key))
            }
            // Sort oldest accessed first
            allSpans.sortBy { it.lastTouchTimestamp }

            for (span in allSpans) {
                if (currentSpace <= maxSizeBytes) break
                val len = span.length
                cache.removeSpan(span)
                freedBytes += len
                currentSpace -= len
            }
        }.onFailure {
            Timber.e(it, "Failed to enforce stream cache cap")
        }
        freedBytes
    }

    /**
     * Clears all stream audio spans from ExoPlayer cache.
     * Returns total bytes freed.
     */
    suspend fun clearAllStreamCache(): Long = withContext(Dispatchers.IO) {
        val initialSize = getStreamCacheSizeBytes()
        runCatching {
            val cache = exoCache.cache
            val keys = cache.keys.toList()
            for (key in keys) {
                for (span in cache.getCachedSpans(key).toList()) {
                    runCatching { cache.removeSpan(span) }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to clear all stream cache")
        }
        val remaining = getStreamCacheSizeBytes()
        maxOf(0L, initialSize - remaining)
    }

    /**
     * Total bytes occupied by image/artwork cache.
     */
    suspend fun getImageCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            val dir = imageCacheDir
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
        }.getOrDefault(0L)
    }

    /**
     * Clears all disk image cache files.
     * Returns total bytes freed.
     */
    suspend fun clearImageCache(): Long = withContext(Dispatchers.IO) {
        val initialSize = getImageCacheSizeBytes()
        runCatching {
            val dir = imageCacheDir
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to clear image cache")
        }
        val remaining = getImageCacheSizeBytes()
        maxOf(0L, initialSize - remaining)
    }

    /**
     * Gets general app cache size (cacheDir excluding stream cache and image cache).
     */
    suspend fun getGeneralCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            val root = context.cacheDir
            if (!root.exists()) return@runCatching 0L
            val streamDir = streamCacheDir.canonicalPath
            val imageDir = imageCacheDir.canonicalPath

            root.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        !file.canonicalPath.startsWith(streamDir) &&
                        !file.canonicalPath.startsWith(imageDir)
                }
                .sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * Clears general app cache files (excluding stream cache and image cache).
     */
    suspend fun clearGeneralCache(): Long = withContext(Dispatchers.IO) {
        val initialSize = getGeneralCacheSizeBytes()
        runCatching {
            val root = context.cacheDir
            if (root.exists()) {
                val streamDir = streamCacheDir.canonicalPath
                val imageDir = imageCacheDir.canonicalPath

                root.listFiles()?.forEach { child ->
                    val path = child.canonicalPath
                    if (!path.startsWith(streamDir) && !path.startsWith(imageDir)) {
                        child.deleteRecursively()
                    }
                }
            }
        }.onFailure {
            Timber.e(it, "Failed to clear general cache")
        }
        val remaining = getGeneralCacheSizeBytes()
        maxOf(0L, initialSize - remaining)
    }

    /**
     * Automated lifecycle cleanup triggered during app warm-up or background sync.
     * Evaluates current user preferences for TTL and max size cap and performs auto-eviction.
     */
    suspend fun performScheduledCleanup(): Long = withContext(Dispatchers.IO) {
        var totalFreed = 0L
        runCatching {
            val ttlDays = userPreferencesRepository.streamCacheTtlDaysFlow.first()
            if (ttlDays > 0) {
                totalFreed += pruneExpiredStreams(ttlDays)
            }
            val maxSizeMb = userPreferencesRepository.streamCacheMaxSizeMbFlow.first()
            if (maxSizeMb > 0) {
                totalFreed += enforceCacheCap(maxSizeMb)
            }
        }.onFailure {
            Timber.e(it, "Error during scheduled stream cache cleanup")
        }
        totalFreed
    }
}
