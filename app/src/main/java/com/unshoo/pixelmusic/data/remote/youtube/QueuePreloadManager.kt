package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printd
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printe
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * QueuePreloadManager — Offline-Resilient Playback
 *
 * When the current song changes, proactively:
 *   1. Resolves + caches the stream URL for the next [PRELOAD_AHEAD] songs via
 *      [YoutubeHelper.getSongPlayerUrl] (which saves to the Room DB so the player
 *      finds the URL without a fresh network call on playback).
 *   2. Downloads + saves the album art to the thumbnail directory so the
 *      notification / lock-screen artwork is available immediately on transition.
 *
 * Call [attach] from your playback service's onCreate() and [detach] from onDestroy().
 */
@OptIn(UnstableApi::class)
object QueuePreloadManager {

    private var preloadJob: Job? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var exoCache: ExoCache? = null
    // Reference to the engine so we can check if the active URI is already locked.
    private var engineRef: DualPlayerEngine? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            triggerPreload()
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        exoCacheInstance: ExoCache,
        engine: DualPlayerEngine? = null
    ) {
        scope = coroutineScope
        appContext = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        exoCache = exoCacheInstance
        engineRef = engine
        player.addListener(playerListener)
        printd("QueuePreloadManager attached")
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        preloadJob?.cancel()
        scope = null
        appContext = null
        datastoreRepository = null
        exoCache = null
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
            printd("QueuePreloadManager player updated")
        }
    }

    fun onControllerReady(player: Player) {
        updatePlayer(player)
    }

    private fun triggerPreload() {
        val currentScope = scope ?: return
        val player = playerRef ?: return
        val ctx = appContext ?: return


        preloadJob?.cancel()
        preloadJob = currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.preloadQueueEnabled) return@launch

            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else Pair(player.currentMediaItemIndex, player.mediaItemCount)
            } ?: return@launch

            val (currentIndex, totalCount) = playerState

            val indicesAhead =
                (currentIndex + 1)..(currentIndex + settings.preloadQueueSize).coerceAtMost(totalCount - 1)

            for (i in indicesAhead) {
                val mediaItem = withContext(Dispatchers.Main) {
                    if (playerRef != null && i < player.mediaItemCount) player.getMediaItemAt(i) else null
                } ?: continue

                val videoId = mediaItem.mediaId
                if (videoId.isBlank()) continue

                // Build a minimal Song from the MediaItem for URL resolution
                val song = Song(
                    youtubeId = videoId,
                    title = mediaItem.mediaMetadata.title?.toString() ?: "",
                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                    thumbnailHref = upgradeThumbnailUrlToHighQuality(mediaItem.mediaMetadata.artworkUri?.toString()).orEmpty()
                )

                // 1. Preload stream URL (saves to DB so next play is instant)
                var streamUrl: String? = null
                try {
                    streamUrl = YoutubeHelper.getSongPlayerUrl(ctx, song, allowLocal = false)
                    printd("QueuePreloadManager: preloaded stream URL for $videoId")
                } catch (e: Exception) {
                    printe("QueuePreloadManager: failed to preload stream for $videoId: ${e.message}")
                }

                // 1b. Prefetch first 512KB of the audio stream
                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    prefetchAudioBytes(ctx, videoId, streamUrl)
                }

                // 2. Preload album art to thumbnail cache directory
                val thumbnailUrl = song.thumbnailHref
                if (thumbnailUrl.isNotBlank()) {
                    try {
                        val imageDir = UmihiHelper.getDownloadDirectory(
                            ctx,
                            Constants.Downloads.THUMBNAILS_FOLDER
                        )
                        val destFile = File(imageDir, "$videoId.jpg")
                        if (!destFile.exists()) {
                            val artBytes = UmihiHelper.fetchArtworkBytes(thumbnailUrl)
                            if (artBytes != null && artBytes.isNotEmpty()) {
                                destFile.writeBytes(artBytes)
                                printd("QueuePreloadManager: cached thumbnail for $videoId")
                            }
                        }
                    } catch (e: Exception) {
                        printe("QueuePreloadManager: failed to cache thumbnail for $videoId: ${e.message}")
                    }
                }

                // Small delay between preloads to avoid hammering the network
                delay(500)
            }
        }
    }

    private fun prefetchAudioBytes(ctx: Context, videoId: String, streamUrl: String) {
        val cache = exoCache?.cache ?: return
        try {
            val uri = Uri.parse(streamUrl)
            val baseDataSourceFactory = DefaultDataSource.Factory(ctx)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(baseDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val dataSource = cacheDataSourceFactory.createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(0)
                .setLength(512 * 1024)
                .build()

            val cacheWriter = CacheWriter(
                dataSource,
                dataSpec,
                null,
                null
            )

            printd("QueuePreloadManager: starting audio prefetch (512KB) for $videoId")
            cacheWriter.cache()
            printd("QueuePreloadManager: completed audio prefetch (512KB) for $videoId")
        } catch (e: Exception) {
            printe("QueuePreloadManager: failed to prefetch audio bytes for $videoId: ${e.message}")
        }
    }

}
