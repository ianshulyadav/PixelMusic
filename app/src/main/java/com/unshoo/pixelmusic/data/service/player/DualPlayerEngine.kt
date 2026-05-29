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
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import com.unshoo.pixelmusic.data.model.TransitionSettings
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "gdrive", "youtube")
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    // Active Audio Session ID Flow
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

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
            if (audioSessionId != 0 && _activeAudioSessionId.value != audioSessionId) {
                _activeAudioSessionId.value = audioSessionId
                Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
            applyWakeModeForCurrentItem()

            // --- Pre-Resolve Next/Prev Tracks with Debounce to prevent flooding ---
            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(600) // Wait for user to stop skipping/navigating
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
                            if (scheme == "telegram" || scheme == "netease" || scheme == "qqmusic" || scheme == "navidrome" || scheme == "jellyfin" || scheme == "gdrive" || scheme == "youtube") {
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
            if (transitionRunning) return
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
                        !isPostSeekBuffering
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

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
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

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning

    fun getAudioSessionId(): Int = playerA.audioSessionId

    private var isReleased = false
    private val resolvedUriCache = LruCache<String, Uri>(100)
    private val localFilePathCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun registerLocalPath(youtubeUri: String, filePath: String) {
        if (filePath.isNotBlank()) {
            localFilePathCache[youtubeUri] = filePath
        }
    }

    init {
        initialize()
        scope.launch {
            userPreferencesRepository.audioOffloadEnabledFlow.collect { enabled ->
                if (audioOffloadEnabled != enabled) {
                    audioOffloadEnabled = enabled
                    rebuildPlayersPreservingMasterState("Audio offload setting changed to $enabled")
                }
            }
        }
    }

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return

        if (::playerA.isInitialized) {
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerB.isInitialized) {
            try { playerB.release() } catch (e: Exception) { /* Ignore */ }
        }

        playerA = buildPlayer()
        playerB = buildPlayer()

        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)

        _activeAudioSessionId.value = playerA.audioSessionId
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

    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = wakeModeFor(playerA.currentMediaItem)
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            if (::playerB.isInitialized) {
                playerB.setWakeMode(mode)
            }
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d", mode)
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
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB.release()

        playerA = buildPlayer()
        playerB = buildPlayer()

        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters

        if (mediaItems.isNotEmpty()) {
            playerA.setMediaItems(mediaItems, currentIndex, positionMs)
            playerA.repeatMode = repeatMode
            playerA.shuffleModeEnabled = shuffleMode
            playerA.prepare()
            playerA.playWhenReady = desiredPlayWhenReady
            applyWakeModeForCurrentItem()
        }

        _activeAudioSessionId.value = playerA.audioSessionId
        onPlayerSwappedListeners.forEach { it(playerA) }

        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    private fun buildPlayer(): ExoPlayer {
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiFiModeEnabled)
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
        }.setEnableAudioFloatOutput(hiFiModeEnabled)
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
                if (scheme in REMOTE_MEDIA_SCHEMES) {
                    val originalUri = uri.toString()
                    val localPath = localFilePathCache[originalUri]
                    if (localPath != null && java.io.File(localPath).exists()) {
                        return dataSpec.buildUpon().setUri(Uri.fromFile(java.io.File(localPath))).build()
                    }

                    val resolved = resolvedUriCache.get(originalUri)
                    if (resolved != null) {
                        return dataSpec.buildUpon().setUri(resolved).build()
                    }

                    try {
                        val fallbackResolved = runBlocking { resolveCloudUri(uri) }
                        if (fallbackResolved != uri) {
                            return dataSpec.buildUpon().setUri(fallbackResolved).build()
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Synchronous resolveCloudUri failed for %s", originalUri)
                    }
                    
                    Timber.tag("DualPlayerEngine").d("resolveDataSpec: Cache MISS for %s - attempting to use original URI", scheme)
                }
                return dataSpec
            }
        }
        
        val baseDataSourceFactory = DefaultDataSource.Factory(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(exoCache.cache)
            .setUpstreamDataSourceFactory(baseDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory, resolver)
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        val loadControl = DefaultLoadControl.Builder()
            // ── Spotify-like playback optimization ───────────────────────────
            .setBufferDurationsMs(
                /* minBufferMs                      = */ 30_000, // Robust buffer (30s) preventing stalls on slow network
                /* maxBufferMs                      = */ 60_000, // Buffer up to 60s
                /* bufferForPlaybackMs              = */ 500,    // Start play after 0.5s for instant startup
                /* bufferForPlaybackAfterRebufferMs = */ 2_000   // After stall, buffer 2.0s before resuming
            )
            .setBackBuffer(30_000, /* retainBackBufferFromKeyframe = */ true) // Cache 30s backwards for instant replay seeking
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            setAudioAttributes(audioAttributes, false)
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(offloadPreferences)
                .build()
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            playWhenReady = false
        }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        playerA.pauseAtEndOfMediaItems = shouldPause
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
            else -> currentAbsoluteIndex + 1
        }

        val targetItem = snapshot.getOrNull(targetIndex) ?: return null
        return TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = snapshot.size
        )
    }

    fun setHiFiMode(enabled: Boolean) {
        if (hiFiModeEnabled == enabled) return
        if (enabled && !HiFiCapabilityChecker.isSupported()) {
            Timber.tag("DualPlayerEngine").w("Hi-Fi mode requested but device does not support PCM_FLOAT")
            return
        }
        hiFiModeEnabled = enabled
        rebuildPlayersPreservingMasterState("Hi-Fi mode set to $enabled")
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        resolvedUriCache.get(uriString)?.let { return@withContext it }

        val resolved: Uri? = when (uri.scheme) {
            "telegram" -> resolveTelegramUriAsync(uri, uriString)
            "gdrive" -> resolveGDriveUriAsync(uriString)
            "youtube" -> resolveYoutubeUriAsync(uriString)
            else -> null
        }

        if (resolved != null) {
            resolvedUriCache.put(uriString, resolved)
            return@withContext resolved
        }
        uri
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
            val youtubeId = uriString.substringAfter("youtube://")
            val youtubeSong = com.unshoo.pixelmusic.data.model.youtube.Song(youtubeId = youtubeId)

            // ── STAGE 1: Instant low-quality stream URL (< 300 ms target) ──────
            // getLowestQualityStreamUrl checks offline-first (local file > LRU cache > network).
            val path = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                .getLowestQualityStreamUrl(context, youtubeSong)

            // If we got a local file path, register it and return it directly.
            if (!path.startsWith("http")) {
                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                    .registerLocalFilePath(youtubeId, path)
                return@withContext Uri.fromFile(java.io.File(path))
            }

            val lowUri = Uri.parse(path)

            // ── STAGE 2: Pre-warm high-quality URL in background ─────────────
            // We launch this without awaiting so ExoPlayer can begin buffering
            // the low-quality URL immediately while the high-quality URL resolves.
            scope.launch(Dispatchers.IO) {
                try {
                    val highPath = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                        .getHighestQualityStreamUrl(context, youtubeSong)
                    if (highPath.startsWith("http")) {
                        // Cache it so the next time this song is resolved it's instant
                        resolvedUriCache.put(uriString, Uri.parse(highPath))
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Stage-2 high-quality pre-warm failed for $youtubeId")
                }
            }

            lowUri
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").e(e, "resolveYoutubeUriAsync failed for $uriString")
            null
        }
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        if (scheme !in REMOTE_MEDIA_SCHEMES) return mediaItem
        val resolvedUri = resolveCloudUri(uri)
        return if (resolvedUri == uri) mediaItem else mediaItem.buildUpon().setUri(resolvedUri).build()
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        prepareNext(target.mediaItem, target.absoluteIndex, startPositionMs)
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val preferredIndex = findMediaItemIndex(
            items = ensureQueueSnapshot(),
            mediaId = mediaItem.mediaId,
            preferAfterExclusive = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, queueSnapshot)
        )
        prepareNext(mediaItem, preferredIndex, startPositionMs)
    }

    private suspend fun prepareNext(mediaItem: MediaItem, preferredAbsoluteIndex: Int, startPositionMs: Long = 0L) {
        try {
            val snapshot = ensureQueueSnapshot()
            val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, snapshot)
            val targetIndex = when {
                preferredAbsoluteIndex in snapshot.indices &&
                    snapshot[preferredAbsoluteIndex].mediaId == mediaItem.mediaId -> preferredAbsoluteIndex
                else -> findMediaItemIndex(snapshot, mediaItem.mediaId, currentAbsoluteIndex)
            }
            val resolvedItem = resolveMediaItem(mediaItem)

            playerB.stop()
            playerB.clearMediaItems()

            if (targetIndex != C.INDEX_UNSET && snapshot.isNotEmpty()) {
                val count = snapshot.size
                val (start, end) = auxiliaryWindowBounds(targetIndex, count)
                val windowItems = ArrayList<MediaItem>(end - start)
                for (i in start until end) {
                    val item = snapshot[i]
                    windowItems.add(if (i == targetIndex) resolvedItem else item)
                }
                preparedWindowStartIndex = start
                preparedPlayerUsesWindowedQueue = count > MAX_AUXILIARY_TIMELINE_ITEMS
                playerB.setMediaItems(windowItems, targetIndex - start, startPositionMs)
            } else {
                // Fallback for single item if not found in current timeline
                resetPreparedWindowState()
                playerB.setMediaItem(resolvedItem)
                playerB.seekTo(startPositionMs)
            }

            playerB.prepare()
            playerB.volume = 0f
            playerB.pause()
        } catch (e: Exception) {
            resetPreparedWindowState()
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    fun cancelNext() {
        transitionJob?.cancel()
        transitionRunning = false
        resetPreparedWindowState()
        if (::playerB.isInitialized && playerB.mediaItemCount > 0) {
            try {
                playerB.stop()
                playerB.clearMediaItems()
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
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                if (::playerB.isInitialized) playerB.stop()
            } finally {
                transitionRunning = false
                onTransitionFinishedListeners.forEach { it() }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        if (playerB.mediaItemCount == 0) {
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
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
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                
                val isOutgoingStalled = playerA.playbackState == Player.STATE_ENDED || 
                        playerA.playbackState == Player.STATE_BUFFERING ||
                        (!playerA.isPlaying && playerA.duration != C.TIME_UNSET && playerA.currentPosition >= playerA.duration - 40000L)
                if (isOutgoingStalled) {
                    if (playerA.hasNextMediaItem()) {
                        playerA.seekToNext()
                        playerA.prepare()
                        playerA.play()
                    }
                }
                return
            }
        }

        val outgoingStartVolume = playerA.volume.coerceIn(0f, 1f)
        playerB.volume = 0f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) playerA.play()
        playerB.playWhenReady = true
        playerB.play()

        val outgoingPlayer = playerA
        val incomingPlayer = playerB

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        outgoingPlayer.pauseAtEndOfMediaItems = true
        incomingPlayer.pauseAtEndOfMediaItems = false
        onTransitionDisplayPlayerListeners.forEach { it(incomingPlayer) }

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 32L
        val startedAtMs = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volIn = envelope(progress, settings.curveIn)
            val volOut = 1f - envelope(progress, settings.curveOut)
            val incomingTarget = incomingTrackReplayGainVolume ?: 1f
            incomingPlayer.volume = (volIn * incomingTarget).coerceIn(0f, 1f)
            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)

            if (elapsed >= duration) break
            delay(stepMs)
        }

        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTrackReplayGainVolume ?: 1f
        incomingTrackReplayGainVolume = null

        outgoingPlayer.removeListener(masterPlayerListener)
        outgoingPlayer.removeAnalyticsListener(masterPlayerListener)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB.pauseAtEndOfMediaItems = false
        playerA.addListener(masterPlayerListener)
        playerA.addAnalyticsListener(masterPlayerListener)
        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()

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
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        if (::playerB.isInitialized) playerB.release()
        isReleased = true
    }
}
