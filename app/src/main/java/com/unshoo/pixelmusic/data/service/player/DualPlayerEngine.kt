package com.unshoo.pixelmusic.data.service.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver
import com.unshoo.pixelmusic.data.lossless.applemusic.AppleMusicDrm
import com.unshoo.pixelmusic.data.lossless.playback.LosslessSchemeRoutingDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import unshoo.ianshulyadav.pixelmusic.innertube.utils.StreamClientUtils
import com.unshoo.pixelmusic.data.model.TransitionSettings
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import com.unshoo.pixelmusic.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume



data class ActiveDecoderInfo(
    val name: String,
    val isHardware: Boolean
)

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player. During a crossfade the MediaSession can
 * expose Player B early for UI continuity, while Player A remains alive to fade out.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
    private val telegramStreamProxy: com.unshoo.pixelmusic.data.telegram.TelegramStreamProxy,

    private val gdriveStreamProxy: com.unshoo.pixelmusic.data.gdrive.GDriveStreamProxy,
    private val telegramCacheManager: com.unshoo.pixelmusic.data.telegram.TelegramCacheManager,
    private val connectivityStateHolder: com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder,
    private val exoCache: com.unshoo.pixelmusic.data.remote.youtube.ExoCache,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private companion object {
        private const val AUDIO_OFFLOAD_BUFFERING_FALLBACK_MS = 4_000L
        private const val MAX_AUXILIARY_TIMELINE_ITEMS = 200
        private const val STREAM_RESOLVE_TIMEOUT_MS = 8_000L
        // BUGFIX: was 5_000L (shorter than normal!). Weak networks need MORE time, not less.
        private const val STREAM_RESOLVE_TIMEOUT_LOW_CONNECTIVITY_MS = 12_000L
        /**
         * How long the ExoPlayer load thread may cooperatively wait for a stream resolve to
         * finish before giving up. Must exceed [STREAM_RESOLVE_TIMEOUT_MS] so the load thread
         * never aborts while a legitimate resolution is still making progress. ExoPlayer runs
         * data-source opens on its background Loader thread, so waiting here does NOT touch the
         * UI thread — whereas throwing early pushed ProgressiveMediaSource into exponential
         * retry back-off (1s → 2s → 4s), which was the multi-second audio-start regression.
         */
        private const val LOAD_THREAD_RESOLVE_AWAIT_MS = 12_000L
        // SpatialFlow uses 5s HTTP timeouts for snappy fail/retry on weak links.
        private const val HTTP_CONNECT_TIMEOUT_MS = 5_000
        private const val HTTP_READ_TIMEOUT_MS = 5_000
        private const val FIRST_CHUNK_PRECACHE_BYTES = 256L * 1024L // 256KB — enough for instant start
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "gdrive", "youtube", "monochrome")
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    // BUGFIX: was `val` — release() cancels the SupervisorJob, but initialize() never
    // recreated it. Since DualPlayerEngine is @Singleton, the dead scope persisted across
    // MusicService destroy/create cycles, silently dropping all scope.launch calls
    // (kickBackgroundResolve, preResolve, preference collectors).
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var hiFiModeEnabled: Boolean = false
        private set
    private var audioOffloadEnabled = !shouldDisableAudioOffloadByDefault()
    private var transitionJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private var transitionRunning = false
    private var preResolutionJob: Job? = null
    private var queueSnapshot: List<MediaItem> = emptyList()
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onNextPlayerPreparedListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    // Active Audio Session ID Flow - pinned across both players to prevent DSP mute/reattach on swap
    @Volatile private var pinnedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()
    private var pendingTransitionTarget: TransitionTarget? = null

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

    private val _hiFiModeEnabledState = MutableStateFlow(false)
    val hiFiModeEnabledState: StateFlow<Boolean> = _hiFiModeEnabledState.asStateFlow()

    // Audio Focus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false
    private var lastPlayWhenReadyAtMs: Long = 0L
    private var lastPlayingAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a user seek from a real HAL offload
    // reset (where audio underflows mid-playback). Without this, seeking shortly after
    // playback starts re-enters BUFFERING within the HAL-reset window and triggers a full
    // player rebuild, which leaves the MediaSession briefly pointing at the released player
    // and silently drops any subsequent seeks.
    private var lastSeekAtMs: Long = 0L

    /**
     * Set by MusicService once ReplayGain for the incoming track is known.
     * The crossfade loop reads this at the end instead of hard-coding 1f,
     * so the incoming track reaches its correct RG volume without a jump.
     * Reset to null after each transition.
     */
    var incomingTrackReplayGainVolume: Float? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                isFocusLossPause = playerA.playWhenReady || (transitionRunning && playerB.playWhenReady)
                playerA.playWhenReady = false
                playerB.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB.playWhenReady = true
                }
            }
        }
    }

    // Listener to attach to the active master player (playerA)
    private val masterPlayerListener = object : Player.Listener, AnalyticsListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                lastPlayWhenReadyAtMs = SystemClock.elapsedRealtime()
                requestAudioFocus()
            } else {
                cancelAudioOffloadFallback()
                // Keep focus across user pauses so a quick resume doesn't have to re-acquire it.
                // Focus is abandoned explicitly on AUDIOFOCUS_LOSS and on release(); anything in
                // between (user pause/play) keeps the request alive to avoid contention races
                // that occasionally caused press-play to auto-pause after a short wait.
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastPlayingAtMs = SystemClock.elapsedRealtime()
                cancelAudioOffloadFallback()
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHardware = AudioDecoderPolicy.isLikelyHardwareDecoder(decoderName)
            _activeDecoderInfo.value = ActiveDecoderInfo(decoderName, isHardware)
            Timber.tag("DualPlayerEngine").d("Audio decoder initialized: %s (Hardware: %b)", decoderName, isHardware)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (pinnedAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
                    pinnedAudioSessionId = audioSessionId
                    if (::playerB.isInitialized) {
                        try { playerB.audioSessionId = audioSessionId } catch (_: Exception) {}
                    }
                }
                if (_activeAudioSessionId.value != audioSessionId) {
                    _activeAudioSessionId.value = audioSessionId
                    Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Only evict the OUTGOING item's resolved URI from the active-playback lock.
            // Clearing ALL entries here caused BUG 1: ExoPlayer's data-source re-entered
            // resolveDataSpec() ~1 second into a new YouTube track, got a fresh (different)
            // URL, and restarted playback from position 0.
            // We intentionally keep the INCOMING item's lock alive so subsequent data
            // reads for the same URI always return the same cached URL.
            // The lock for any truly stale entries will expire naturally when those
            // video-IDs are no longer the active item.
            val incomingUri = mediaItem?.localConfiguration?.uri
            activePlaybackResolvedUris.keys
                .filter { it != incomingUri?.toString() && activePlaybackResolvedUris[it] != incomingUri }
                .forEach { activePlaybackResolvedUris.remove(it) }
            cancelAudioOffloadFallback()
            
            // If the transition was not automatic (e.g. user skip or playlist change),
            // immediately cancel any background crossfade logic to ensure responsiveness.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelNext()
            }

            val uri = mediaItem?.localConfiguration?.uri
            if (uri?.scheme == "telegram") {
                scope.launch {
                    val result = telegramRepository.resolveTelegramUri(uri.toString())
                    val fileId = result?.first
                    telegramCacheManager.setActivePlayback(fileId)
                    Timber.tag("DualPlayerEngine").d("Telegram playback active: fileId=$fileId")
                }
            } else {
                telegramCacheManager.setActivePlayback(null)
            }
            isSleepingForOffload = false
            applyWakeModeForCurrentItem()

            // --- Pre-Resolve Next/Prev Tracks with Debounce to prevent flooding ---
            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(150) // Reduced from 600ms for faster online stream pre-resolution
                try {
                    val currentIndex = playerA.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        val itemsToPreResolve = mutableListOf<Uri>()
                        
                        if (currentIndex + 1 < playerA.mediaItemCount) {
                            playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let { 
                                itemsToPreResolve.add(it) 
                            }
                        }
                        if (currentIndex - 1 >= 0) {
                            playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let { 
                                itemsToPreResolve.add(it) 
                            }
                        }

                        for (uriToResolve in itemsToPreResolve) {
                            val scheme = uriToResolve.scheme
                            if (scheme == "telegram" || scheme == "netease" || scheme == "qqmusic" || scheme == "gdrive" || scheme == "youtube") {
                                resolveCloudUri(uriToResolve)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution in onMediaItemTransition")
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // BUG 2 FIX: Do NOT skip snapshot refresh when a transition is running.
            // AutoQueueManager calls player.addMediaItems() during a crossfade, which
            // fires PLAYLIST_CHANGED here. The old guard caused queueSnapshot to stay
            // stale, so getNextTransitionTarget() returned the wrong track.
            // We still refresh the snapshot but do NOT cancel any running transition —
            // that is TransitionController's responsibility.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || queueSnapshot.isEmpty()) {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    val timeSincePlayingMs = now - lastPlayingAtMs
                    val timeSinceSeekMs = now - lastSeekAtMs
                    val isPostSeekBuffering = lastSeekAtMs > 0L && timeSinceSeekMs < 1_500L
                    if (audioOffloadEnabled && !transitionRunning &&
                        lastPlayingAtMs > 0L && timeSincePlayingMs < 500L &&
                        !isPostSeekBuffering &&
                        isLikelyLocalMedia(playerA.currentMediaItem)
                    ) {
                        disableAudioOffloadForSession(
                            reason = "HAL offload reset detected: STATE_BUFFERING after ${timeSincePlayingMs}ms of playback"
                        )
                    } else {
                        scheduleAudioOffloadFallbackIfNeeded(playerA)
                    }
                }
                Player.STATE_READY, Player.STATE_IDLE, Player.STATE_ENDED -> cancelAudioOffloadFallback()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                lastSeekAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private val masterAudioOffloadListener = object : ExoPlayer.AudioOffloadListener {
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            isSleepingForOffload = sleepingForOffload
            applyWakeModeForCurrentItem()
            Timber.tag("DualPlayerEngine").d("Sleeping for offload changed: %b", sleepingForOffload)
        }
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    fun addNextPlayerPreparedListener(listener: (Player) -> Unit) {
        onNextPlayerPreparedListeners.add(listener)
    }

    fun removeNextPlayerPreparedListener(listener: (Player) -> Unit) {
        onNextPlayerPreparedListeners.remove(listener)
    }

    fun addTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.add(listener)
    }

    fun removeTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.remove(listener)
    }

    fun addTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.add(listener)
    }

    /**
     * Notifies the engine that an external caller (UI seek, etc.) is about to issue a
     * seek through the MediaController. Used to mark the upcoming STATE_BUFFERING as
     * seek-driven so the HAL-reset heuristic does not trigger a player rebuild that
     * would race with the in-flight seek command.
     *
     * Setting this here (synchronously, before the seek dispatches) is more reliable
     * than waiting for onPositionDiscontinuity, which is delivered on the next event
     * batch and can race with onPlaybackStateChanged on some Media3 versions.
     */
    fun notifyExternalSeekInitiated() {
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * Forces an immediate refresh of the internal queue snapshot from the current
     * master player timeline. Call this after programmatically adding/removing items
     * to the player queue from outside the engine (e.g. AutoQueueManager) to ensure
     * [getNextTransitionTarget] returns the correct next track immediately.
     */
    fun forceRefreshQueueSnapshot() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }
    }

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning

    fun getAudioSessionId(): Int {
        if (pinnedAudioSessionId != C.AUDIO_SESSION_ID_UNSET && pinnedAudioSessionId != 0) {
            return pinnedAudioSessionId
        }
        return if (::playerA.isInitialized) playerA.audioSessionId else 0
    }

    fun setAudioSessionId(sessionId: Int) {
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        pinnedAudioSessionId = sessionId
        if (::playerA.isInitialized) {
            try { playerA.audioSessionId = sessionId } catch (_: Exception) {}
        }
        if (::playerB.isInitialized) {
            try { playerB.audioSessionId = sessionId } catch (_: Exception) {}
        }
        _activeAudioSessionId.value = sessionId
    }

    fun invalidateResolvedUri(uriString: String) {
        resolvedUriCache.remove(uriString)
        activePlaybackResolvedUris.remove(uriString)
        activeResolutions.remove(uriString)?.cancel()
    }

    private var isReleased = false
    internal val resolvedUriCache = LruCache<String, Uri>(100)
    private val activePlaybackResolvedUris = java.util.concurrent.ConcurrentHashMap<String, Uri>()
    private val localFilePathCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val activeResolutions = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Uri>>()

    /** Fire-and-forget stream resolve used by the non-blocking ResolvingDataSource path. */
    fun kickBackgroundResolve(uri: Uri) {
        val uriString = uri.toString()
        if (resolvedUriCache.get(uriString) != null) return
        // Always funnel through resolveCloudUri so in-flight requests are deduped.
        scope.launch(Dispatchers.IO) {
            runCatching { resolveCloudUri(uri) }
                .onFailure { Timber.tag("DualPlayerEngine").w(it, "Background resolve failed for %s", uriString) }
        }
    }

    fun getCachedResolvedUri(uri: Uri): Uri? {
        val uriString = uri.toString()
        localFilePathCache[uriString]?.let { localPath ->
            if (java.io.File(localPath).exists()) return Uri.fromFile(java.io.File(localPath))
        }
        val cached = resolvedUriCache.get(uriString) ?: activePlaybackResolvedUris[uriString]
        return if (cached != null && isResolvedUriFresh(uriString, cached)) cached else null
    }

    fun getCachedResolvedMediaItem(mediaItem: MediaItem): MediaItem? {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val resolved = getCachedResolvedUri(uri) ?: return null
        return mediaItem.buildUpon().setUri(resolved).build()
    }

    /**
     * Pre-resolve a media item on a background dispatcher BEFORE prepare/play.
     * This keeps ExoPlayer's load thread free of network work and eliminates miniplayer freezes
     * when users tap song cards.
     */
    suspend fun preResolveForPlayback(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        val uriString = uri.toString()
        val originalUriString = mediaItem.mediaMetadata.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI") ?: uriString
        if (scheme == "http" || scheme == "https") {
            if (isResolvedUriFresh(originalUriString, uri)) {
                return mediaItem
            }
            // If the URL has expired or is nearing expiration (<15m left), re-resolve from source URI
            val sourceUri = Uri.parse(originalUriString)
            if (sourceUri.scheme in REMOTE_MEDIA_SCHEMES) {
                return resolveMediaItem(mediaItem.buildUpon().setUri(sourceUri).build())
            }
            return mediaItem
        }
        if (scheme !in REMOTE_MEDIA_SCHEMES) {
            return mediaItem
        }
        return resolveMediaItem(mediaItem)
    }

    suspend fun preResolveForPlayback(mediaItems: List<MediaItem>, startIndex: Int): List<MediaItem> {
        if (mediaItems.isEmpty()) return mediaItems
        val safeStart = startIndex.coerceIn(0, mediaItems.lastIndex)
        val result = mediaItems.toMutableList()
        // CRITICAL PATH ONLY: resolve the tapped track so play can start immediately.
        // Next/prev are warmed in the background AFTER we return — waiting on them
        // was adding multi-second delay on low connectivity before audio started.
        result[safeStart] = preResolveForPlayback(result[safeStart])

        val warm = buildList {
            if (safeStart + 1 <= result.lastIndex) add(safeStart + 1)
            if (safeStart - 1 >= 0) add(safeStart - 1)
        }
        for (idx in warm) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val resolved = preResolveForPlayback(result[idx])
                    // Cache only — do not mutate the already-returned list.
                    resolved.localConfiguration?.uri?.toString()?.let { u ->
                        val key = result[idx].mediaId.removePrefix("youtube_").takeIf { it.isNotBlank() }
                        if (u.startsWith("http")) preCacheFirstChunk(u, key)
                    }
                }
            }
        }
        return result
    }

    private val preCacheHttpFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
    }

    private val preCacheDataSourceFactory by lazy {
        val upstream = DefaultDataSource.Factory(context, preCacheHttpFactory)
        CacheDataSource.Factory()
            .setCache(exoCache.cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Pre-cache the first chunk of a stream into SimpleCache for instant playback start.
     * Ported from SpatialFlow AudioPlaybackService.preCacheFirstChunk.
     */
    fun preCacheFirstChunk(streamUrl: String, cacheKey: String? = null) {
        if (!streamUrl.startsWith("http")) return
        if (cacheKey != null && runCatching { exoCache.cache.isCached(cacheKey, 0, FIRST_CHUNK_PRECACHE_BYTES) }.getOrDefault(false)) return
        scope.launch(Dispatchers.IO) {
            try {
                val dataSource = preCacheDataSourceFactory.createDataSource()
                val dataSpecBuilder = DataSpec.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setPosition(0)
                    .setLength(FIRST_CHUNK_PRECACHE_BYTES)
                if (cacheKey != null) {
                    dataSpecBuilder.setKey(cacheKey)
                }
                val dataSpec = dataSpecBuilder.build()
                androidx.media3.datasource.cache.CacheWriter(
                    dataSource,
                    dataSpec,
                    /* temporaryBuffer= */ null,
                    /* progressListener= */ null
                ).cache()
                Timber.tag("DualPlayerEngine").d(
                    "Pre-cached first %dKB of stream for key=%s",
                    FIRST_CHUNK_PRECACHE_BYTES / 1024,
                    cacheKey
                )
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").d("preCacheFirstChunk skipped: %s", e.message)
            }
        }
    }


    fun registerLocalPath(youtubeUri: String, filePath: String) {
        if (filePath.isNotBlank()) {
            localFilePathCache[youtubeUri] = filePath
        }
    }

    fun resolveLocalDiskFile(uriString: String): Uri? {
        // 1. Check in-memory local file cache with disk validation
        localFilePathCache[uriString]?.let { path ->
            if (path.startsWith("content://")) {
                return Uri.parse(path)
            }
            val file = java.io.File(path)
            if (file.isFile && file.length() > 0L) {
                return Uri.fromFile(file)
            }
        }

        // 2. Check YouTube downloads folder directly on disk
        if (uriString.startsWith("youtube://")) {
            val videoId = uriString.removePrefix("youtube://")
            if (videoId.isNotBlank()) {
                val candidateDirs = listOf(
                    PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER),
                    File(context.filesDir, "audio_files")
                )
                val extensions = listOf(".flac", ".webm", ".m4a", ".opus", ".mp3", ".ogg", ".aac")
                for (audioDir in candidateDirs) {
                    for (ext in extensions) {
                        val diskFile = java.io.File(audioDir, "$videoId$ext")
                        if (diskFile.isFile && diskFile.length() > 0L) {
                            val path = diskFile.absolutePath
                            localFilePathCache[uriString] = path
                            return Uri.fromFile(diskFile)
                        }
                    }
                }
            }
        }

        // 3. Check cloud downloads directory by SHA-256 ID
        if (uriString.startsWith("youtube://")) {
            val cloudDir = java.io.File(context.filesDir, "cloud_downloads")
            if (cloudDir.isDirectory) {
                try {
                    val downloadId = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(uriString.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    val match = cloudDir.listFiles()?.firstOrNull { file ->
                        file.isFile && file.length() > 0L && file.name.startsWith(downloadId) &&
                            !file.name.endsWith(".part") && !file.name.endsWith(".jpg") && !file.name.endsWith(".lrc")
                    }
                    if (match != null) {
                        localFilePathCache[uriString] = match.absolutePath
                        return Uri.fromFile(match)
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun applyAudioOffloadToActivePlayers() {
        if (::playerA.isInitialized) applyAudioOffload(playerA)
        if (::playerB.isInitialized) applyAudioOffload(playerB)
    }

    private fun applyAudioOffload(player: ExoPlayer) {
        val mode = if (audioOffloadEnabled) {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(mode)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(offloadPreferences)
            .build()

        // Media3 versions expose different offload scheduling APIs. Mirror ArchiveTune's
        // defensive reflection so the setting works across builds instead of silently no-oping.
        val schedulingEnabled = audioOffloadEnabled && !transitionRunning
        listOf("experimentalSetOffloadSchedulingEnabled", "setOffloadSchedulingEnabled", "setOffloadEnabled")
            .firstNotNullOfOrNull { name ->
                runCatching { player.javaClass.getMethod(name, Boolean::class.javaPrimitiveType) }.getOrNull()
            }
            ?.let { method -> runCatching { method.invoke(player, schedulingEnabled) } }
    }

    init {
        // BUGFIX (was: initialize() called eagerly in init on the calling
        // thread, which is the main thread for the service path):
        // DualPlayerEngine is a Hilt @Singleton constructed by Hilt the
        // first time anything injects it. For MusicService that's the
        // main thread of `onCreate`, and `initialize()` builds two
        // ExoPlayer instances — each of which loads extractors,
        // decoders, audio sinks, and the LRU cache. Combined with the
        // rest of MusicService.onCreate, this pushed the foreground
        // service over its 5s start-up budget on slower devices.
        // We now defer the actual ExoPlayer build to the first
        // `initialize()` call from MusicService.onCreate, which itself
        // has been moved to run as the last step. The audioOffload
        // preferences collector stays since it doesn't allocate.
        scope.launch {
            userPreferencesRepository.audioOffloadEnabledFlow.collect { enabled ->
                if (audioOffloadEnabled != enabled) {
                    audioOffloadEnabled = enabled
                    // ArchiveTune-style: apply offload preferences in-place. Rebuilding players
                    // on every toggle causes audible gaps and UI churn on low-end devices.
                    if (::playerA.isInitialized) applyAudioOffloadToActivePlayers()
                }
            }
        }
    }

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return

        // Recreate scope if it was cancelled by a previous release() call.
        // DualPlayerEngine is @Singleton, so the scope must survive across
        // MusicService destroy/create cycles.
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerB.isInitialized) {
            try { playerB.release() } catch (e: Exception) { /* Ignore */ }
        }

        if (pinnedAudioSessionId == C.AUDIO_SESSION_ID_UNSET || pinnedAudioSessionId == 0) {
            val generated = audioManager.generateAudioSessionId()
            if (generated != 0 && generated != C.AUDIO_SESSION_ID_UNSET) {
                pinnedAudioSessionId = generated
            }
        }

        playerA = buildPlayer()
        playerB = buildPlayer()

        if (pinnedAudioSessionId != C.AUDIO_SESSION_ID_UNSET && pinnedAudioSessionId != 0) {
            try { playerA.audioSessionId = pinnedAudioSessionId } catch (_: Exception) {}
            try { playerB.audioSessionId = pinnedAudioSessionId } catch (_: Exception) {}
            _activeAudioSessionId.value = pinnedAudioSessionId
        } else {
            _activeAudioSessionId.value = playerA.audioSessionId
        }

        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)
        playerA.addAudioOffloadListener(masterAudioOffloadListener)

        isReleased = false
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            // Let the system queue our request behind a transient holder instead of failing.
            // Pairs with the AUDIOFOCUS_GAIN handler below: on DELAYED we pause and mark the
            // pause as focus-driven so the eventual GAIN callback resumes playback.
            .setAcceptsDelayedFocusGain(true)
            .build()

        val result = audioManager.requestAudioFocus(request)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                audioFocusRequest = request
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                audioFocusRequest = request
                isFocusLossPause = true
                playerA.playWhenReady = false
                if (transitionRunning) playerB.playWhenReady = false
            }
            else -> {
                Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
                playerA.playWhenReady = false
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun scheduleAudioOffloadFallbackIfNeeded(player: ExoPlayer) {
        cancelAudioOffloadFallback()
        if (!audioOffloadEnabled || transitionRunning || !player.playWhenReady) return
        if (!isLikelyLocalMedia(player.currentMediaItem)) return

        val watchedMediaId = player.currentMediaItem?.mediaId ?: return
        bufferingFallbackJob = scope.launch {
            delay(AUDIO_OFFLOAD_BUFFERING_FALLBACK_MS)

            val currentMediaId = player.currentMediaItem?.mediaId
            if (!audioOffloadEnabled || transitionRunning || player !== playerA) return@launch
            if (currentMediaId != watchedMediaId) return@launch
            if (player.playbackState != Player.STATE_BUFFERING || player.isPlaying || !player.playWhenReady) return@launch
            if (player.currentPosition > 1_000L) return@launch

            disableAudioOffloadForSession(
                reason = "Local media stayed buffering for ${AUDIO_OFFLOAD_BUFFERING_FALLBACK_MS}ms"
            )
        }
    }

    private fun cancelAudioOffloadFallback() {
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
    }

    private fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
    }

    private fun wakeModeFor(mediaItem: MediaItem?): Int {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
            C.WAKE_MODE_NETWORK
        } else {
            C.WAKE_MODE_LOCAL
        }
    }

    private var isSleepingForOffload: Boolean = false
    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = if (isSleepingForOffload) {
            C.WAKE_MODE_NONE
        } else {
            wakeModeFor(playerA.currentMediaItem)
        }
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            if (::playerB.isInitialized) {
                playerB.setWakeMode(mode)
            }
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d (sleepingForOffload=%b)", mode, isSleepingForOffload)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to update wake mode")
        }
    }

    private fun shouldDisableAudioOffloadByDefault(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val isXiaomiFamilyDevice = manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco"
        if (isXiaomiFamilyDevice && Build.VERSION.SDK_INT >= 36) return true

        val isGooglePixel = manufacturer == "google" && brand == "google"
        if (isGooglePixel && Build.VERSION.SDK_INT >= 34) return true

        return false
    }

    private fun disableAudioOffloadForSession(reason: String) {
        if (!audioOffloadEnabled) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload fallback during active transition. %s", reason)
            return
        }

        audioOffloadEnabled = false
        rebuildPlayersPreservingMasterState(
            logMessage = "Audio offload disabled for current session. $reason"
        )
    }

    private fun rebuildPlayersPreservingMasterState(logMessage: String) {
        cancelAudioOffloadFallback()

        val desiredPlayWhenReady = playerA.playWhenReady
        val positionMs = playerA.currentPosition
        val currentIndex = playerA.currentMediaItemIndex.coerceAtLeast(0)
        val mediaItems = (0 until playerA.mediaItemCount).map { playerA.getMediaItemAt(it) }
        val repeatMode = playerA.repeatMode
        val shuffleMode = playerA.shuffleModeEnabled
        val volume = playerA.volume
        val pauseAtEnd = playerA.pauseAtEndOfMediaItems
        val playbackParameters: PlaybackParameters = playerA.playbackParameters

        playerA.removeListener(masterPlayerListener)
        playerA.removeAnalyticsListener(masterPlayerListener)
        playerA.removeAudioOffloadListener(masterAudioOffloadListener)
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB.release()

        playerA = buildPlayer()
        playerB = buildPlayer()

        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)
        playerA.addAudioOffloadListener(masterAudioOffloadListener)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters

        if (mediaItems.isNotEmpty()) {
            playerA.setMediaItems(mediaItems, currentIndex, positionMs)
            playerA.repeatMode = repeatMode
            playerA.shuffleModeEnabled = shuffleMode
            playerB.repeatMode = repeatMode
            playerB.shuffleModeEnabled = shuffleMode
            playerA.prepare()
            playerA.playWhenReady = desiredPlayWhenReady
            applyWakeModeForCurrentItem()
        }

        if (pinnedAudioSessionId != C.AUDIO_SESSION_ID_UNSET && pinnedAudioSessionId != 0) {
            try { playerA.audioSessionId = pinnedAudioSessionId } catch (_: Exception) {}
            try { playerB.audioSessionId = pinnedAudioSessionId } catch (_: Exception) {}
            _activeAudioSessionId.value = pinnedAudioSessionId
        } else {
            _activeAudioSessionId.value = playerA.audioSessionId
        }
        onPlayerSwappedListeners.forEach { it(playerA) }

        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    private fun buildPlayer(): ExoPlayer {
        val floatEnabled = hiFiModeEnabled && HiFiCapabilityChecker.isSupported()
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos, floatEnabled)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(floatEnabled)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip video renderers to save memory and "renderers" count.
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip text renderers.
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip camera motion renderers.
            }
        }.setEnableAudioFloatOutput(floatEnabled)
         .setMediaCodecSelector(mediaCodecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val scheme = uri.scheme
                val uriString = uri.toString()

                val cleanId = dataSpec.key ?: when {
                    scheme == "youtube" -> uri.host?.takeIf { it.isNotBlank() } ?: uriString.removePrefix("youtube://")
                    uriString.startsWith("youtube://") -> uriString.removePrefix("youtube://")
                    scheme == "monochrome" -> "monochrome_" + (uri.host?.takeIf { it.isNotBlank() } ?: uriString.removePrefix("monochrome://"))
                    uriString.startsWith("monochrome://") -> "monochrome_" + uriString.removePrefix("monochrome://")
                    uriString.contains("monochrome.st/track/") -> "monochrome_" + uriString.substringAfterLast("/")
                    else -> null
                }

                // 1. Instant Start: Check if the song is already cached in ExoCache
                if (cleanId != null) {
                    val isCached = runCatching {
                        exoCache.cache.isCached(
                            cleanId,
                            dataSpec.position,
                            if (dataSpec.length > 0) dataSpec.length else 128 * 1024L
                        )
                    }.getOrDefault(false)

                    if (isCached) {
                        return dataSpec.buildUpon().setKey(cleanId).build()
                    }
                }

                // 2. Custom cloud schemes
                if (scheme == "monochrome") {
                    val trackId = uri.host?.takeIf { it.isNotBlank() } ?: uriString.removePrefix("monochrome://")
                    val directUri = Uri.parse("https://tracks.monochrome.st/track/$trackId")
                    activePlaybackResolvedUris[uriString] = directUri
                    resolvedUriCache.put(uriString, directUri)
                    return dataSpec.buildUpon()
                        .setUri(directUri)
                        .apply { if (cleanId != null) setKey(cleanId) }
                        .setHttpRequestHeaders(
                            mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                            )
                        )
                        .build()
                }

                if (scheme == "telegram" || scheme == "gdrive" || scheme == "youtube") {
                    val originalUri = uriString
                    val diskUri = resolveLocalDiskFile(originalUri)
                    val servesLossless = diskUri?.path?.endsWith(".flac", true) == true
                    if (diskUri != null && (scheme != "youtube" || servesLossless || !LosslessStreamResolver.isEnabledBlocking(context) || !connectivityStateHolder.isOnline.value)) {
                        return dataSpec.buildUpon()
                            .setUri(diskUri)
                            .build()
                    }

                    fun DataSpec.Builder.applyStreamHeaders(resolvedUri: Uri): DataSpec.Builder {
                        val urlStr = resolvedUri.toString()
                        if (urlStr.startsWith("http")) {
                            val profile = StreamClientUtils.resolveRequestProfile(urlStr)
                            val headers = buildMap {
                                put("User-Agent", profile.userAgent)
                                profile.origin?.let { put("Origin", it) }
                                profile.referer?.let { put("Referer", it) }
                            }
                            this.setHttpRequestHeaders(headers)
                        }
                        return this
                    }

                    activePlaybackResolvedUris[originalUri]?.let { locked ->
                        if (isResolvedUriFresh(originalUri, locked)) {
                            return dataSpec.buildUpon()
                                .setUri(locked)
                                .apply { if (cleanId != null) setKey(cleanId) }
                                .applyStreamHeaders(locked)
                                .build()
                        } else {
                            activePlaybackResolvedUris.remove(originalUri)
                            resolvedUriCache.remove(originalUri)
                        }
                    }

                    val resolved = resolvedUriCache.get(originalUri)
                    if (resolved != null) {
                        if (isResolvedUriFresh(originalUri, resolved)) {
                            activePlaybackResolvedUris[originalUri] = resolved
                            return dataSpec.buildUpon()
                                .setUri(resolved)
                                .apply { if (cleanId != null) setKey(cleanId) }
                                .applyStreamHeaders(resolved)
                                .build()
                        } else {
                            resolvedUriCache.remove(originalUri)
                            activePlaybackResolvedUris.remove(originalUri)
                        }
                    }

                    kickBackgroundResolve(uri)
                    try {
                        val awaited = kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.withTimeoutOrNull(LOAD_THREAD_RESOLVE_AWAIT_MS) {
                                resolveCloudUri(uri)
                            }
                        }
                        val ready = resolvedUriCache.get(originalUri)
                            ?: activePlaybackResolvedUris[originalUri]
                        if (awaited != null && ready != null && ready != uri &&
                            isResolvedUriFresh(originalUri, ready)
                        ) {
                            activePlaybackResolvedUris[originalUri] = ready
                            return dataSpec.buildUpon()
                                .setUri(ready)
                                .apply { if (cleanId != null) setKey(cleanId) }
                                .applyStreamHeaders(ready)
                                .build()
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(
                            e, "resolveDataSpec: cooperative resolve failed for %s", originalUri
                        )
                    }
                    throw java.io.IOException(
                        "Stream URL could not be resolved for $scheme://…"
                    )
                }

                if (cleanId != null && dataSpec.key == null) {
                    return dataSpec.buildUpon().setKey(cleanId).build()
                }

                return dataSpec
            }
        }

        // Explicit HTTP timeouts for low-connectivity resilience.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

        val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        // Lossless sources (ArchiveTune port): route `deezer://` through the on-the-fly Blowfish
        // decryptor and `tidal-dash://` through the progressive DASH stitcher; everything else
        // (http/https CDN links, flattened Apple Music files, local files) uses the default source.
        // The router sits UNDER the cache, exactly like ArchiveTune's ResolvedSchemeRoutingDataSource,
        // so the cache stores decrypted / stitched bytes and can serve them offline-fast next time.
        val losslessRoutingFactory = LosslessSchemeRoutingDataSource.create(
            defaultFactory = baseDataSourceFactory,
            httpUpstream = httpDataSourceFactory,
            httpClient = LosslessStreamResolver.httpClient,
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(exoCache.cache)
            .setUpstreamDataSourceFactory(losslessRoutingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory, resolver)
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setConstantBitrateSeekingEnabled(true)

        // BUGFIX (adaptive buffering): previously just metered-vs-unmetered, which treats a fast
        // 5G connection the same as a barely-there one just because both are "mobile data". Now
        // tiered by the OS's real link-speed estimate, with an additional "recently unstable"
        // override driven by actual rebuffer events reported by this player (see
        // reportPlaybackStall() below) - live ground truth beats a link-speed guess. A fast link
        // gets a short startup buffer for a quick start; a weak or currently-unstable one gets a
        // longer one, trading a bit of startup time for fewer mid-song stalls, matching "prefer
        // uninstalled playback over saving a few hundred ms at startup".
        val bandwidthKbps = connectivityStateHolder.linkDownstreamBandwidthKbps.value
        val isRecentlyUnstable = connectivityStateHolder.isNetworkRecentlyUnstable.value
        data class BufferProfile(val minMs: Int, val maxMs: Int, val forPlaybackMs: Int, val afterRebufferMs: Int)
        // Startup-latency-first profiles: forPlaybackMs is the amount of audio REQUIRED before
        // first sound. The earlier revision raised this to 1.2–3s (over-eager startup buffering),
        // directly adding that latency to every tap-to-play. Keep startup buffers tight (the
        // stream cache + pre-resolved URL make underruns rare) and only grow buffers for links
        // that have ACTUALLY rebuffed recently or are genuinely slow.
        val profile = when {
            isRecentlyUnstable -> BufferProfile(30_000, 60_000, 2_000, 4_000)
            bandwidthKbps < 1_000 -> BufferProfile(25_000, 50_000, 1_200, 3_000)
            bandwidthKbps < 5_000 -> BufferProfile(20_000, 45_000, 800, 2_500)
            else -> BufferProfile(15_000, 35_000, 500, 2_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(profile.minMs, profile.maxMs, profile.forPlaybackMs, profile.afterRebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15_000, /* retainBackBufferFromKeyframe = */ true)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolvingFactory, extractorsFactory)
                    // Apple Music (lossless sources) streams are Widevine-L3 protected fMP4; the
                    // provider answers DRM_UNSUPPORTED for everything else (see AppleMusicDrm).
                    .setDrmSessionManagerProvider(AppleMusicDrm.drmSessionManagerProvider)
            )
            .setLoadControl(loadControl)
            .build().apply {
                // BUGFIX (adaptive buffering): feed real rebuffer events back into
                // ConnectivityStateHolder so the *next* buffer-profile decision above (next
                // track, or next time this player is rebuilt) can react to it. A true mid-stream
                // LoadControl swap would need a custom LoadControl implementation - a larger,
                // separate change - so this closes the loop at track/rebuild boundaries instead,
                // which is where DefaultLoadControl's buffer targets can actually be changed.
                var hasStartedThisItem = false
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (hasStartedThisItem) {
                                    connectivityStateHolder.reportStablePlayback()
                                }
                                hasStartedThisItem = true
                            }
                            Player.STATE_BUFFERING -> {
                                // Only a genuine rebuffer (mid-playback stall), not the initial
                                // buffer-up before a track's first STATE_READY.
                                if (hasStartedThisItem) {
                                    connectivityStateHolder.reportPlaybackStall()
                                }
                            }
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        hasStartedThisItem = false
                    }
                })
            setAudioAttributes(audioAttributes, false)
            applyAudioOffload(this)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            playWhenReady = false
        }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        playerA.pauseAtEndOfMediaItems = shouldPause
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        if (!::playerA.isInitialized || playerA.mediaItemCount == 0) return null
        val currentIdx = playerA.currentMediaItemIndex
        if (currentIdx == C.INDEX_UNSET) return null

        val targetIndex = if (repeatMode == Player.REPEAT_MODE_ONE) {
            currentIdx
        } else {
            val nextIdx = playerA.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                nextIdx
            } else if (currentIdx + 1 < playerA.mediaItemCount) {
                currentIdx + 1
            } else if (repeatMode == Player.REPEAT_MODE_ALL && playerA.mediaItemCount > 0) {
                0
            } else {
                C.INDEX_UNSET
            }
        }

        if (targetIndex == C.INDEX_UNSET || targetIndex !in 0 until playerA.mediaItemCount) return null
        val targetItem = playerA.getMediaItemAt(targetIndex)
        val target = TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = playerA.mediaItemCount
        )
        pendingTransitionTarget = target
        return target
    }

    /**
     * Checks if the currently loaded item in the master player has an expired or stale stream URL
     * (e.g. after a long pause or app reopening) and proactively re-resolves it in-place.
     */
    suspend fun ensureFreshStreamForResume() {
        val (currentItem, currentUri) = withContext(Dispatchers.Main.immediate) {
            val item = if (::playerA.isInitialized) playerA.currentMediaItem else null
            val uri = item?.localConfiguration?.uri
            Pair(item, uri)
        }
        if (currentItem == null || currentUri == null) return

        val currentUriStr = currentUri.toString()
        val originalUriStr = currentItem.mediaMetadata.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI") ?: currentUriStr

        if (currentUri.scheme == "http" || currentUri.scheme == "https") {
            if (isResolvedUriFresh(originalUriStr, currentUri)) {
                return // Fast path: already fresh
            }
            Timber.tag("DualPlayerEngine").i("Stream URL stale on resume for %s, re-resolving...", originalUriStr)
            invalidateResolvedUri(originalUriStr)
            val sourceUri = Uri.parse(originalUriStr)
            if (sourceUri.scheme in REMOTE_MEDIA_SCHEMES) {
                val freshItem = withContext(Dispatchers.IO) {
                    val resolved = resolveMediaItem(currentItem.buildUpon().setUri(sourceUri).build())
                    resolved.localConfiguration?.uri?.toString()?.let { u ->
                        val key = currentItem.mediaId.removePrefix("youtube_").takeIf { it.isNotBlank() }
                        if (u.startsWith("http")) preCacheFirstChunk(u, key)
                    }
                    resolved
                }
                withContext(Dispatchers.Main.immediate) {
                    if (::playerA.isInitialized && playerA.currentMediaItem?.mediaId == currentItem.mediaId) {
                        val pos = playerA.currentPosition
                        val idx = playerA.currentMediaItemIndex
                        val playWhenReady = playerA.playWhenReady
                        playerA.replaceMediaItem(idx, freshItem)
                        playerA.seekTo(idx, pos)
                        playerA.playWhenReady = playWhenReady
                        // BUGFIX: a prior error leaves the player STATE_IDLE; setting
                        // playWhenReady alone does nothing without prepare().
                        if (playerA.playbackState == Player.STATE_IDLE || playerA.playbackState == Player.STATE_ENDED) {
                            playerA.prepare()
                        }
                    }
                }
            }
        }
    }

    fun setHiFiMode(enabled: Boolean) {
        if (hiFiModeEnabled == enabled) return
        if (enabled && !HiFiCapabilityChecker.isSupported()) {
            Timber.tag("DualPlayerEngine").w("Hi-Fi mode requested but device does not support PCM_FLOAT")
            return
        }
        hiFiModeEnabled = enabled
        _hiFiModeEnabledState.value = enabled
        rebuildPlayersPreservingMasterState("Hi-Fi mode set to $enabled")
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        
        // Return active playback locked URI to prevent ExoPlayer from stuttering or re-loading due to mid-stream URL changes.
        // YouTube googlevideo URLs expire; after a long pause using a stale locked URL causes endless buffering.
        activePlaybackResolvedUris[uriString]?.let { lockedUri ->
            if (isResolvedUriFresh(uriString, lockedUri)) return@withContext lockedUri
            activePlaybackResolvedUris.remove(uriString)
            resolvedUriCache.remove(uriString)
        }
        
        resolvedUriCache.get(uriString)?.let { cachedUri ->
            if (isResolvedUriFresh(uriString, cachedUri)) {
                activePlaybackResolvedUris[uriString] = cachedUri
                return@withContext cachedUri
            }
            resolvedUriCache.remove(uriString)
        }

        val deferred = activeResolutions.getOrPut(uriString) {
            scope.async(Dispatchers.IO) {
                try {
                    // BUGFIX: the outer timeout here must be the MAXIMUM of the two so
                    // resolveYoutubeUriAsync's own timeout (which also branches on metered)
                    // can complete before the outer fires. Previously the outer and inner
                    // used the same value, making the inner's fallback unreachable.
                    val timeoutMs = if (connectivityStateHolder.isMeteredNetwork.value) {
                        STREAM_RESOLVE_TIMEOUT_LOW_CONNECTIVITY_MS + 4_000L
                    } else {
                        STREAM_RESOLVE_TIMEOUT_MS + 4_000L
                    }
                    val resolved: Uri? = withTimeoutOrNull(timeoutMs) {
                        when (uri.scheme) {
                            "telegram" -> resolveTelegramUriAsync(uri, uriString)
                            "gdrive" -> resolveGDriveUriAsync(uriString)
                            "youtube" -> resolveYoutubeUriAsync(uriString)
                            "monochrome" -> {
                                val trackId = uriString.substringAfter("monochrome://")
                                Uri.parse("https://tracks.monochrome.st/track/$trackId")
                            }
                            else -> null
                        }
                    }
                    if (resolved != null) {
                        resolvedUriCache.put(uriString, resolved)
                        activePlaybackResolvedUris[uriString] = resolved
                        resolved
                    } else {
                        // BUGFIX: returning the original unresolvable youtube:// URI here made
                        // the caller hand an unplayable URI to ExoPlayer as if it were a success.
                        // Return the original URI only so callers can detect "no resolution" and
                        // the load-thread resolver can throw the proper IOException.
                        Timber.tag("DualPlayerEngine").w(
                            "resolveCloudUri timed out/null for %s (metered=%s)",
                            uriString,
                            connectivityStateHolder.isMeteredNetwork.value
                        )
                        uri
                    }
                } finally {
                    activeResolutions.remove(uriString)
                }
            }
        }

        try {
            deferred.await()
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").e(e, "Error awaiting resolution for %s", uriString)
            activeResolutions.remove(uriString)
            uri
        }
    }

    private fun isResolvedUriFresh(originalUriString: String, resolvedUri: Uri): Boolean {
        val resolved = resolvedUri.toString()
        // Lossless-source URIs carry their own freshness window (Tidal/Qobuz/Deezer links expire,
        // flattened Apple files can be evicted); the resolver tracks it, never the googlevideo
        // `expire=` heuristic below.
        if (LosslessStreamResolver.isLosslessUri(resolved)) {
            return LosslessStreamResolver.isFreshUri(resolved)
        }
        val isYoutubeResolution = originalUriString.startsWith("youtube://") ||
            resolved.contains("googlevideo.com", ignoreCase = true) ||
            resolved.contains("youtube.com", ignoreCase = true)
        if (!isYoutubeResolution) return true

        val expireSeconds = resolvedUri.getQueryParameter("expire")?.toLongOrNull()
            ?: return true
        val nowSeconds = System.currentTimeMillis() / 1000L
        // Require at least 15 minutes (900s) remaining validity.
        // YouTube URLs expire in 6 hours. If the app hasn't been used in 5.5+ hours,
        // force a silent refresh in the background BEFORE playback starts to guarantee zero lag/retries.
        return expireSeconds > (nowSeconds + 900L)
    }

    private suspend fun resolveTelegramUriAsync(uri: Uri, uriString: String): Uri? = withContext(Dispatchers.IO) {
        val pathSegments = uri.pathSegments
        val fileId = if (pathSegments.isNotEmpty()) {
            telegramRepository.resolveTelegramUri(uriString)?.first
        } else {
            uri.host?.toIntOrNull()
        } ?: return@withContext null

        val fileInfo = telegramRepository.getFile(fileId)
        if (fileInfo?.local?.isDownloadingCompleted == true && fileInfo.local.path.isNotEmpty()) {
            return@withContext Uri.fromFile(File(fileInfo.local.path))
        }

        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }

        if (!telegramStreamProxy.ensureReady(5_000L)) return@withContext null
        val proxyUrl = telegramStreamProxy.getProxyUrl(fileId, 0L)
        if (proxyUrl.isNotEmpty()) Uri.parse(proxyUrl) else null
    }



    private suspend fun resolveGDriveUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        if (!gdriveStreamProxy.ensureReady(5_000L)) return@withContext null
        gdriveStreamProxy.resolveGDriveUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveYoutubeUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val localDiskUri = resolveLocalDiskFile(uriString)
            val isFlacDiskFile = localDiskUri?.path?.endsWith(".flac", true) == true
            val isLosslessEnabled = LosslessStreamResolver.isEnabled(context)
            if (localDiskUri != null && (isFlacDiskFile || !isLosslessEnabled || !connectivityStateHolder.isOnline.value)) {
                return@withContext localDiskUri
            }

            val youtubeId = uriString.substringAfter("youtube://")
            val matchingItem = queueSnapshot.firstOrNull { it.mediaId == youtubeId || it.requestMetadata.mediaUri?.toString() == uriString }
                ?: withContext(Dispatchers.Main.immediate) {
                    if (::playerA.isInitialized && (playerA.currentMediaItem?.mediaId == youtubeId || playerA.currentMediaItem?.requestMetadata?.mediaUri?.toString() == uriString)) {
                        playerA.currentMediaItem
                    } else null
                }

            val youtubeSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                youtubeId = youtubeId,
                title = matchingItem?.mediaMetadata?.title?.toString().orEmpty(),
                artist = matchingItem?.mediaMetadata?.artist?.toString().orEmpty(),
                album = matchingItem?.mediaMetadata?.albumTitle?.toString().orEmpty()
            )

            // getSongPlayerUrl honors Settings quality:
            // HIGH → highest stream first; LOW → lowest first (weak nets).
            val timeoutMs = if (connectivityStateHolder.isMeteredNetwork.value) {
                STREAM_RESOLVE_TIMEOUT_LOW_CONNECTIVITY_MS
            } else {
                STREAM_RESOLVE_TIMEOUT_MS
            }
            val path = withTimeoutOrNull(timeoutMs) {
                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                    .getSongPlayerUrl(context, youtubeSong, allowLocal = localDiskUri == null)
            } ?: run {
                // If high-quality / lossless stream resolve timed out, fall back to local disk file if available
                if (localDiskUri != null) return@withContext localDiskUri
                // Timeout: still try quality-aware path once more without outer timeout;
                // on failure fall back to lowest so something can play.
                try {
                    com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                        .getSongPlayerUrl(context, youtubeSong, allowLocal = true)
                } catch (_: Exception) {
                    com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                        .getLowestQualityStreamUrl(context, youtubeSong)
                }
            }

            // Lossless sources: custom schemes (deezer://, tidal-dash://) are served by the
            // scheme router inside the player's data-source chain and flattened Apple Music
            // files come back as file:// URIs — none of them is a downloaded local song, so they
            // must not be registered in the local-file caches (they expire / get evicted).
            if (path.contains("monochrome.st/track/")) {
                preCacheFirstChunk(path, youtubeId)
                return@withContext Uri.parse(path)
            }

            if (LosslessSchemeRoutingDataSource.isCustomLosslessScheme(path) ||
                path.startsWith("file:") ||
                path.startsWith("apple-pending:")
            ) {
                return@withContext Uri.parse(path)
            }

            if (!path.startsWith("http")) {
                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                    .registerLocalFilePath(youtubeId, path)
                localFilePathCache[uriString] = path
                return@withContext Uri.fromFile(java.io.File(path))
            }

            preCacheFirstChunk(path, youtubeId)
            Uri.parse(path)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").e(e, "resolveYoutubeUriAsync failed for $uriString")
            val localDiskUri = resolveLocalDiskFile(uriString)
            if (localDiskUri != null) {
                return@withContext localDiskUri
            }
            // Last-ditch: lowest stream so weak nets still get audio
            try {
                val youtubeId = uriString.substringAfter("youtube://")
                val youtubeSong = com.unshoo.pixelmusic.data.model.youtube.Song(youtubeId = youtubeId)
                val low = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                    .getLowestQualityStreamUrl(context, youtubeSong)
                if (low.startsWith("http")) {
                    preCacheFirstChunk(low, youtubeId)
                    return@withContext Uri.parse(low)
                }
                if (low.isNotBlank() && java.io.File(low).exists()) {
                    return@withContext Uri.fromFile(java.io.File(low))
                }
            } catch (_: Exception) {
            }
            null
        }
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        if (scheme !in REMOTE_MEDIA_SCHEMES) return mediaItem
        val resolvedUri = resolveCloudUri(uri)
        if (resolvedUri == uri) return mediaItem
        
        val builder = mediaItem.buildUpon().setUri(resolvedUri)
        if (resolvedUri.path?.endsWith(".flac", true) == true) {
            builder.setMimeType("audio/flac")
        } else if (scheme == "youtube") {
            val videoId = uri.toString().removePrefix("youtube://")
            val cachedMime = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.streamMimeTypeLruCache.let { cache ->
                cache.get("${videoId}_high")
                    ?: cache.get("${videoId}_low")
                    ?: cache.snapshot().keys.find { it.startsWith("${videoId}_") }?.let { cache.get(it) }
            }
            if (cachedMime != null) {
                builder.setMimeType(cachedMime)
            }
        } else if (scheme == "monochrome" || resolvedUri.toString().contains("monochrome.st/track/")) {
            builder.setMimeType(androidx.media3.common.MimeTypes.AUDIO_FLAC)
        }
        return builder.build()
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        try {
            pendingTransitionTarget = target
            val resolvedItem = resolveMediaItem(target.mediaItem)

            playerB.stop()
            playerB.clearMediaItems()
            // Standby player loads ONLY the target item to pre-buffer.
            // Queue items are spliced around it at swap time from the live master timeline.
            playerB.setMediaItem(resolvedItem, startPositionMs.coerceAtLeast(0L))
            playerB.repeatMode = playerA.repeatMode
            playerB.shuffleModeEnabled = playerA.shuffleModeEnabled
            playerB.volume = 0f
            playerB.playWhenReady = false
            playerB.prepare()

            onNextPlayerPreparedListeners.forEach { it(playerB) }
        } catch (e: Exception) {
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val target = getNextTransitionTarget(mediaItem, playerA.repeatMode)
        if (target != null) {
            prepareNext(target, startPositionMs)
        } else {
            try {
                val resolvedItem = resolveMediaItem(mediaItem)
                playerB.stop()
                playerB.clearMediaItems()
                playerB.setMediaItem(resolvedItem, startPositionMs.coerceAtLeast(0L))
                playerB.repeatMode = playerA.repeatMode
                playerB.shuffleModeEnabled = playerA.shuffleModeEnabled
                playerB.volume = 0f
                playerB.playWhenReady = false
                playerB.prepare()
                onNextPlayerPreparedListeners.forEach { it(playerB) }
            } catch (e: Exception) {
                Timber.tag("TransitionDebug").e(e, "Failed to prepare next player with single item")
            }
        }
    }

    fun getPreparedNextMediaId(): String? {
        if (::playerB.isInitialized && playerB.mediaItemCount > 0) {
            return playerB.currentMediaItem?.mediaId
        }
        return null
    }

    fun cancelNext() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { cancelNext() }
            return
        }
        transitionJob?.cancel()
        transitionRunning = false
        pendingTransitionTarget = null
        resetPreparedWindowState()
        if (::playerB.isInitialized && playerB.mediaItemCount > 0) {
            try {
                playerB.stop()
                playerB.clearMediaItems()
                playerB.volume = 0f
            } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerA.isInitialized) {
            playerA.volume = 1f
        }
        incomingTrackReplayGainVolume = null
        setPauseAtEndOfMediaItems(false)
    }

    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionRunning = true
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.tag("TransitionDebug").e(e, "Error performing transition")
                }
                if (::playerA.isInitialized) playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                if (::playerB.isInitialized) {
                    try {
                        playerB.stop()
                        playerB.clearMediaItems()
                        playerB.volume = 0f
                    } catch (_: Exception) {}
                }
            } finally {
                transitionRunning = false
                pendingTransitionTarget = null
                onTransitionFinishedListeners.forEach { it() }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        if (!::playerB.isInitialized || playerB.mediaItemCount == 0) {
            if (::playerA.isInitialized) playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        // Ensure playerB's stream URI is still fresh before crossfading (e.g. if paused/idle for >15 min)
        playerB.currentMediaItem?.let { bItem ->
            val bUri = bItem.localConfiguration?.uri
            if (bUri != null && (bUri.scheme == "http" || bUri.scheme == "https")) {
                val origUriStr = bItem.mediaMetadata.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI") ?: bUri.toString()
                if (!isResolvedUriFresh(origUriStr, bUri)) {
                    Timber.tag("TransitionDebug").i("playerB stream URL stale before crossfade for %s, re-resolving...", origUriStr)
                    invalidateResolvedUri(origUriStr)
                    val sourceUri = Uri.parse(origUriStr)
                    if (sourceUri.scheme in REMOTE_MEDIA_SCHEMES) {
                        try {
                            val freshItem = withContext(Dispatchers.IO) {
                                val resolved = resolveMediaItem(bItem.buildUpon().setUri(sourceUri).build())
                                resolved.localConfiguration?.uri?.toString()?.let { u ->
                                    val key = bItem.mediaId.removePrefix("youtube_").takeIf { it.isNotBlank() }
                                    if (u.startsWith("http")) preCacheFirstChunk(u, key)
                                }
                                resolved
                            }
                            val bIdx = playerB.currentMediaItemIndex
                            val bPos = playerB.currentPosition
                            playerB.replaceMediaItem(bIdx, freshItem)
                            playerB.seekTo(bIdx, bPos)
                            playerB.prepare()
                        } catch (e: Exception) {
                            Timber.tag("TransitionDebug").w(e, "Failed to refresh playerB stale stream before crossfade")
                        }
                    }
                }
            }
        }

        if (playerB.playbackState == Player.STATE_IDLE) playerB.prepare()
        if (playerB.playbackState != Player.STATE_READY) {
            val isReady = if (playerB.playbackState == Player.STATE_BUFFERING) {
                awaitPlayerReady(playerB, 3000L)
            } else {
                false
            }
            if (!isReady) {
                Timber.tag("TransitionDebug").w("playerB not ready for transition (state=%d). Aborting and falling back to playerA.", playerB.playbackState)
                abortTransition(playerA, playerB)
                return
            }
        }

        val outgoingPlayer = playerA
        val incomingPlayer = playerB
        val outgoingItem = outgoingPlayer.currentMediaItem ?: run {
            abortTransition(outgoingPlayer, incomingPlayer)
            return
        }

        if (!outgoingPlayer.isPlaying && outgoingPlayer.playbackState == Player.STATE_READY) {
            outgoingPlayer.play()
        }

        val outgoingStartVolume = outgoingPlayer.volume.coerceIn(0.1f, 1f)
        val incomingTargetVolume = incomingTrackReplayGainVolume ?: 1f
        val incomingBeforeStart = incomingPlayer.currentPosition

        incomingPlayer.volume = 0f
        incomingPlayer.playWhenReady = true
        incomingPlayer.play()

        // SINK CLOCK VERIFICATION (adopted from Koda/PixelPlayer):
        // STATE_READY means decoded data exists, not that the audio sink's clock has begun.
        // Starting the fade on READY alone makes the outgoing track fade out while the incoming
        // track has not yet output audible sound (creating an audible mute/dip). Keep the outgoing
        // track untouched at full volume until the incoming position actually advances.
        val clockStarted = withTimeoutOrNull(2500L) {
            while (!incomingPlayer.isPlaying || incomingPlayer.currentPosition <= incomingBeforeStart + 10L) {
                currentCoroutineContext().ensureActive()
                if (!outgoingPlayer.isPlaying ||
                    incomingPlayer.playerError != null ||
                    incomingPlayer.playbackState == Player.STATE_IDLE ||
                    incomingPlayer.playbackState == Player.STATE_ENDED
                ) return@withTimeoutOrNull false
                delay(16L)
            }
            true
        } == true

        if (!clockStarted) {
            Timber.tag("TransitionDebug").w("Incoming playback sink clock did not start in time. Aborting transition.")
            abortTransition(outgoingPlayer, incomingPlayer)
            return
        }

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val startedAtMs = SystemClock.elapsedRealtime()
        var incomingStallStartedMs = 0L

        while (scope.isActive) {
            currentCoroutineContext().ensureActive()

            // If incoming player buffers or stalls, hold curve; if prolonged (>2000ms), abort cleanly.
            if (outgoingPlayer.isPlaying && incomingPlayer.playWhenReady && !incomingPlayer.isPlaying) {
                if (incomingStallStartedMs == 0L) {
                    incomingStallStartedMs = SystemClock.elapsedRealtime()
                } else if (SystemClock.elapsedRealtime() - incomingStallStartedMs >= 2000L) {
                    Timber.tag("TransitionDebug").w("Incoming player stalled mid-fade; aborting overlap")
                    abortTransition(outgoingPlayer, incomingPlayer)
                    return
                }
            } else {
                incomingStallStartedMs = 0L
            }

            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            // Equal-Power Sine/Cosine Curve:
            // cos^2(theta) + sin^2(theta) = 1.0 -> constant acoustic energy across transition!
            val angle = t * (Math.PI.toFloat() / 2f)
            val volOut = kotlin.math.cos(angle)
            val volIn = kotlin.math.sin(angle)

            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)
            incomingPlayer.volume = (volIn * incomingTargetVolume).coerceIn(0f, 1f)

            if (t >= 1f) break
            if (outgoingPlayer.playbackState == Player.STATE_ENDED) break
            if (outgoingPlayer.duration > 0 && outgoingPlayer.duration - outgoingPlayer.currentPosition <= 150L) break
            if (incomingPlayer.playbackState == Player.STATE_IDLE) {
                Timber.tag("TransitionDebug").w("Incoming player died mid-fade; aborting")
                abortTransition(outgoingPlayer, incomingPlayer)
                return
            }

            delay(16L)
        }

        // Complete Swap & Live Timeline Queue Splicing:
        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTargetVolume
        incomingTrackReplayGainVolume = null

        val targetIdx = pendingTransitionTarget?.absoluteIndex ?: outgoingPlayer.nextMediaItemIndex
        if (targetIdx in 0 until outgoingPlayer.mediaItemCount) {
            val before = ArrayList<MediaItem>(targetIdx.coerceAtLeast(0))
            for (i in 0 until targetIdx) {
                before.add(outgoingPlayer.getMediaItemAt(i))
            }
            val after = ArrayList<MediaItem>()
            for (i in (targetIdx + 1) until outgoingPlayer.mediaItemCount) {
                after.add(outgoingPlayer.getMediaItemAt(i))
            }
            if (after.isNotEmpty()) incomingPlayer.addMediaItems(after)
            if (before.isNotEmpty()) incomingPlayer.addMediaItems(0, before)
        }

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled

        outgoingPlayer.removeListener(masterPlayerListener)
        outgoingPlayer.removeAnalyticsListener(masterPlayerListener)
        outgoingPlayer.removeAudioOffloadListener(masterAudioOffloadListener)

        playerA = incomingPlayer
        playerB = outgoingPlayer

        playerA.pauseAtEndOfMediaItems = false
        playerB.pauseAtEndOfMediaItems = false

        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)
        playerA.addAudioOffloadListener(masterAudioOffloadListener)

        if (playerA.volume <= 0.01f) {
            playerA.volume = incomingTargetVolume
        }
        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        // Safely retire outgoing player
        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()
        playerB.volume = 0f

        setPauseAtEndOfMediaItems(false)
        pendingTransitionTarget = null
    }

    private fun abortTransition(outgoing: ExoPlayer, incoming: ExoPlayer) {
        pendingTransitionTarget = null
        runCatching {
            incoming.stop()
            incoming.clearMediaItems()
            incoming.volume = 0f
        }
        outgoing.volume = 1f
        setPauseAtEndOfMediaItems(false)
    }

    private fun ensureQueueSnapshot(): List<MediaItem> {
        if (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        if (queueSnapshot.isEmpty()) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    private fun refreshQueueSnapshotFromMaster(windowStartIndex: Int, usesWindowedQueue: Boolean) {
        if (!::playerA.isInitialized) return

        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    private fun resolveCurrentAbsoluteIndex(mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    private fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    private fun auxiliaryWindowBounds(targetIndex: Int, count: Int): Pair<Int, Int> {
        if (count <= MAX_AUXILIARY_TIMELINE_ITEMS) return 0 to count

        val halfWindow = MAX_AUXILIARY_TIMELINE_ITEMS / 2
        var start = (targetIndex - halfWindow).coerceAtLeast(0)
        var end = (start + MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtMost(count)
        start = (end - MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtLeast(0)
        return start to end
    }

    private fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    private suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.playbackState == Player.STATE_READY) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_BUFFERING) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(playbackState == Player.STATE_READY)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }

    private suspend fun awaitPlayerPlaying(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.isPlaying) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }

    fun release() {
        transitionJob?.cancel()
        preResolutionJob?.cancel()
        cancelAudioOffloadFallback()
        scope.coroutineContext[Job]?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            playerA.removeListener(masterPlayerListener)
            playerA.removeAnalyticsListener(masterPlayerListener)
            playerA.removeAudioOffloadListener(masterAudioOffloadListener)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        if (::playerB.isInitialized) playerB.release()
        isReleased = true
    }
}
