package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.unshoo.pixelmusic.data.model.Song
import com.google.android.gms.cast.MediaStatus
import com.unshoo.pixelmusic.di.AppScope
import timber.log.Timber
import com.unshoo.pixelmusic.utils.QueueUtils
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.data.service.MusicNotificationProvider
import kotlin.math.abs

@Singleton
class PlaybackStateHolder @Inject constructor(
    private val dualPlayerEngine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    @param:ApplicationContext private val appContext: Context,
    @AppScope private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PlaybackStateHolder"
        private const val DURATION_MISMATCH_TOLERANCE_MS = 1500L
        // Cap how long we trust a pending seek override against an out-of-date player position.
        // The override exists to mask the few ticks between seekTo() and the player actually
        // reporting the new position. If we never see drift converge within this window we
        // assume the seek will not land and fall back to the reported position rather than
        // pinning the UI on a stale value forever.
        private const val PAUSED_OVERRIDE_MAX_AGE_MS = 4_000L
        // 250 ms keeps the slider/time display visibly smooth. We tried 500 ms to lower
        // Compose recomposition pressure, but the smooth-progress sampler does not actually
        // interpolate between source samples — it polls — so a 500 ms source cadence made the
        // slider stutter in half-second jumps. Background tick is throttled to 1 s since the
        // screen is off and no slider is visible.
        private const val FOREGROUND_PROGRESS_TICK_MS = 250L
        private const val BACKGROUND_PROGRESS_TICK_MS = 1000L
        /**
         * Tick used while the screen is on but *nothing is observing* position - the user is
         * on Library / Search / Settings, or the app is backgrounded with the display still lit.
         */
        private const val UNOBSERVED_PROGRESS_TICK_MS = 1000L
        /**
         * Threshold above which we skip per-item moveMediaItem calls and use
         * a single setMediaItems call instead. moveMediaItem triggers an IPC
         * round-trip for each call, which freezes the UI on large queues.
         */
        private const val BULK_REPLACE_THRESHOLD = 80
        private const val SHUFFLE_TOGGLE_COOLDOWN_MS = 400L
    }

    private val scope: CoroutineScope = appScope
    
    // MediaController
    var mediaController: MediaController? = null
        private set

    // Player State
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    // Internal State
    private var isSeeking = false
    private var remoteSeekUnlockJob: Job? = null
    private var activePositionOccurrenceMediaId: String? = null
    private var activePositionOccurrenceToken: Long = 0L
    private var nextPositionOccurrenceToken: Long = 1L
    private var pausedPositionOverrideMediaId: String? = null
    private var pausedPositionOverrideToken: Long? = null
    private var pausedPositionOverrideMs: Long? = null
    private var pausedPositionOverrideSetAtMs: Long = 0L
    private var consecutiveMediaMismatchProgressSkips = 0
    private var coldStartSnapshotMediaId: String? = null
    private var coldStartSnapshotToken: Long? = null
    private var coldStartSnapshotPositionMs: Long? = null
    private var shuffleToggleJob: Job? = null
    private var lastShuffleToggleFinishedAtMs: Long = 0L
    private val powerManager: PowerManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    // BUG 4 FIX: Play-history stack for reliable Previous navigation.
    // Each entry is the mediaId of a song that actually started playing (not just
    // queued). Capped at 200 entries so it never grows unbounded.
    private val playHistoryStack = ArrayDeque<String>(200)
    private val PLAY_HISTORY_MAX = 200

    private fun clearColdStartSnapshot() {
        coldStartSnapshotMediaId = null
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = null
    }

    /**
     * Binds a restored snapshot to the active playback occurrence when possible.
     *
     * If the first occurrence was already activated before the snapshot finished loading, we
     * attach the snapshot to that token so resume still works. If playback has already advanced
     * past the first occurrence, the snapshot is stale and must be discarded.
     */
    private fun rememberColdStartSnapshot(mediaId: String, positionMs: Long): Boolean {
        coldStartSnapshotMediaId = mediaId
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = positionMs

        if (nextPositionOccurrenceToken == 1L) {
            return true
        }

        if (
            activePositionOccurrenceToken == 1L &&
            nextPositionOccurrenceToken == 2L &&
            activePositionOccurrenceMediaId == mediaId
        ) {
            coldStartSnapshotToken = activePositionOccurrenceToken
            return true
        }

        clearColdStartSnapshot()
        return false
    }

    fun initialize(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val snapshotMediaId = snapshot.currentMediaId
                ?: snapshot.items.getOrNull(snapshot.currentIndex)?.mediaId
                ?: return@launch
            val snapshotPositionMs = snapshot.currentPositionMs.coerceAtLeast(0L)
            if (snapshotPositionMs <= 0L) return@launch
            if (!rememberColdStartSnapshot(snapshotMediaId, snapshotPositionMs)) {
                return@launch
            }

            val controller = mediaController
            if (controller != null) {
                val (isPlaying, currentMediaId) = withContext(Dispatchers.Main) {
                    try {
                        controller.isPlaying to controller.currentMediaItem?.mediaId
                    } catch (_: IllegalStateException) {
                        false to null
                    }
                }
                if (!isPlaying && currentMediaId == snapshotMediaId && _currentPosition.value == 0L) {
                    _currentPosition.value = snapshotPositionMs
                }
            }
        }
    }

    fun setMediaController(controller: MediaController?) {
        this.mediaController = controller
    }

    /** Clears only the controller owned by the caller, never a newer controller binding. */
    fun clearMediaController(controller: MediaController) {
        if (mediaController === controller) {
            mediaController = null
        }
    }
    
    fun updateStablePlayerState(update: (StablePlayerState) -> StablePlayerState) {
        _stablePlayerState.update { current ->
            val updated = update(current)
            // Auto-populate index from MediaController if not explicitly set by the update
            if (updated.currentMediaItemIndex == -1) {
                val controller = mediaController
                if (controller != null) {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        try {
                            updated.copy(currentMediaItemIndex = controller.currentMediaItemIndex)
                        } catch (_: IllegalStateException) {
                            updated
                        }
                    } else {
                        // We are on a background thread. Asynchronously fetch on Main thread and update state flow.
                        scope.launch(Dispatchers.Main) {
                            val mainController = mediaController
                            if (mainController != null) {
                                try {
                                    val index = mainController.currentMediaItemIndex
                                    _stablePlayerState.update { state ->
                                        if (state.currentMediaItemIndex == -1) {
                                            state.copy(currentMediaItemIndex = index)
                                        } else {
                                            state
                                        }
                                    }
                                } catch (_: IllegalStateException) {
                                    // Controller released or in invalid state
                                }
                            }
                        }
                        updated
                    }
                } else {
                    updated
                }
            } else {
                updated
            }
        }
    }

    fun setCurrentPosition(positionMs: Long) {
        _currentPosition.value = positionMs.coerceAtLeast(0L)
    }

    fun syncCurrentPositionFromPlayer(mediaId: String?, reportedPositionMs: Long) {
        _currentPosition.value = resolveUiPosition(mediaId, reportedPositionMs)
    }

    fun ensureCurrentPlaybackOccurrence(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = false)
    }

    fun onPlaybackOccurrenceTransition(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = true)
        // Record in play history for Previous navigation.
        // Avoid adding duplicates for the same song back-to-back (e.g. from repeated
        // MediaItemTransition events for the same item during buffering).
        val safeId = mediaId?.takeIf { it.isNotBlank() } ?: return
        if (playHistoryStack.lastOrNull() != safeId) {
            if (playHistoryStack.size >= PLAY_HISTORY_MAX) {
                playHistoryStack.removeFirst()
            }
            playHistoryStack.addLast(safeId)
        }
    }

    fun rememberPausedPositionOverride(mediaId: String?, positionMs: Long) {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return
        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false) ?: return
        val safePosition = positionMs.coerceAtLeast(0L)
        pausedPositionOverrideMediaId = safeMediaId
        pausedPositionOverrideToken = activeToken
        pausedPositionOverrideMs = safePosition
        pausedPositionOverrideSetAtMs = SystemClock.elapsedRealtime()
        _currentPosition.value = safePosition
    }

    fun clearCurrentPositionHints(mediaId: String? = null) {
        if (mediaId == null || pausedPositionOverrideMediaId == mediaId) {
            pausedPositionOverrideMediaId = null
            pausedPositionOverrideToken = null
            pausedPositionOverrideMs = null
            pausedPositionOverrideSetAtMs = 0L
        }
        if (mediaId == null || coldStartSnapshotMediaId == mediaId) {
            clearColdStartSnapshot()
        }
    }

    private fun resolveUiPosition(mediaId: String?, reportedPositionMs: Long): Long {
        val safeReportedPosition = reportedPositionMs.coerceAtLeast(0L)
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (safeMediaId == null) {
            return safeReportedPosition
        }

        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false)
            ?: return safeReportedPosition

        val pausedOverride = pausedPositionOverrideMs
            ?.takeIf {
                pausedPositionOverrideMediaId == safeMediaId &&
                    pausedPositionOverrideToken == activeToken
            }
        val coldStartSeed = coldStartSnapshotPositionMs
            ?.takeIf {
                coldStartSnapshotMediaId == safeMediaId &&
                    coldStartSnapshotToken == activeToken
            }
        val preferredPosition = pausedOverride ?: coldStartSeed

        if (preferredPosition == null) {
            return safeReportedPosition
        }

        if (safeReportedPosition <= 0L) {
            return preferredPosition
        }

        val drift = abs(safeReportedPosition - preferredPosition)
        val pausedOverrideOwnsThisToken =
            pausedPositionOverrideMediaId == safeMediaId &&
                pausedPositionOverrideToken == activeToken
        val pausedOverrideActive = pausedOverride != null
        // Stale override fallback: if the player never converges on a freshly-issued seek
        // we don't want to pin the UI on the requested position forever. After this window
        // we trust the reported position again.
        val overrideIsStale = pausedOverrideActive &&
            pausedPositionOverrideSetAtMs > 0L &&
            SystemClock.elapsedRealtime() - pausedPositionOverrideSetAtMs > PAUSED_OVERRIDE_MAX_AGE_MS
        // The `reported >= preferred` shortcut is only safe for the cold-start seed (where
        // preferred represents "where playback should start" and the player passing it means
        // the seed has served its purpose). Applying the same shortcut to an active paused
        // override broke backward seeks — the player still reports the pre-seek (larger)
        // position for a tick or two after seekTo(), wiping the override before the seek
        // had landed and snapping the UI back to the old position.
        val coldStartPassed = !pausedOverrideActive && safeReportedPosition >= preferredPosition
        if (drift <= DURATION_MISMATCH_TOLERANCE_MS || overrideIsStale || coldStartPassed) {
            if (pausedOverrideOwnsThisToken) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
                pausedPositionOverrideSetAtMs = 0L
            }
            if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotToken == activeToken) {
                clearColdStartSnapshot()
            }
            return safeReportedPosition
        }

        return preferredPosition
    }

    private fun activatePlaybackOccurrence(
        mediaId: String?,
        forceNewOccurrence: Boolean
    ): Long? {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: run {
            activePositionOccurrenceMediaId = null
            activePositionOccurrenceToken = 0L
            if (forceNewOccurrence) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
                pausedPositionOverrideSetAtMs = 0L
            }
            return null
        }

        val shouldAdvance =
            forceNewOccurrence ||
                activePositionOccurrenceToken == 0L ||
                activePositionOccurrenceMediaId != safeMediaId

        if (!shouldAdvance) {
            return activePositionOccurrenceToken
        }

        activePositionOccurrenceMediaId = safeMediaId
        activePositionOccurrenceToken = nextPositionOccurrenceToken++

        pausedPositionOverrideMediaId = null
        pausedPositionOverrideToken = null
        pausedPositionOverrideMs = null
        pausedPositionOverrideSetAtMs = 0L

        if (coldStartSnapshotToken != null) {
            clearColdStartSnapshot()
        } else if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotPositionMs != null) {
            coldStartSnapshotToken = activePositionOccurrenceToken
        } else if (coldStartSnapshotMediaId != null) {
            clearColdStartSnapshot()
        }

        return activePositionOccurrenceToken
    }
    
    /* -------------------------------------------------------------------------- */
    /*                               Playback Controls                            */
    /* -------------------------------------------------------------------------- */

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                _stablePlayerState.update {
                    it.copy(
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
            } else {
                if (remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                    _stablePlayerState.update {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else {
                    Timber.w("Remote queue empty, cannot resume.")
                }
            }
        } else {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                _stablePlayerState.update { it.copy(isBuffering = true) }
                scope.launch {
                    try {
                        dualPlayerEngine.ensureFreshStreamForResume()
                        withContext(Dispatchers.Main.immediate) {
                            val activeCtrl = mediaController ?: controller
                            val player = dualPlayerEngine.masterPlayer
                            if (player.playbackState == androidx.media3.exoplayer.ExoPlayer.STATE_IDLE ||
                                player.playbackState == androidx.media3.exoplayer.ExoPlayer.STATE_ENDED) {
                                activeCtrl.prepare()
                            }
                            activeCtrl.play()
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "ensureFreshStreamForResume failed; playing anyway")
                        withContext(Dispatchers.Main.immediate) {
                            val activeCtrl = mediaController ?: controller
                            activeCtrl.play()
                        }
                    } finally {
                        _stablePlayerState.update { it.copy(isBuffering = false) }
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val targetPosition = position.coerceAtLeast(0L)
            castStateHolder.setRemotelySeeking(true)
            castStateHolder.setRemotePosition(targetPosition)
            setCurrentPosition(targetPosition)
            castStateHolder.castPlayer?.seek(targetPosition)

            remoteSeekUnlockJob?.cancel()
            remoteSeekUnlockJob = scope.launch {
                // Fail-safe: never keep remote seeking lock indefinitely.
                delay(1800)
                castStateHolder.setRemotelySeeking(false)
                castSession.remoteMediaClient?.requestStatus()
            }
        } else {
            remoteSeekUnlockJob?.cancel()
            castStateHolder.setRemotelySeeking(false)
            val targetPosition = position.coerceAtLeast(0L)
            val currentMediaId = try {
                mediaController?.currentMediaItem?.mediaId
            } catch (_: IllegalStateException) {
                null
            }
            rememberPausedPositionOverride(currentMediaId, targetPosition)
            // Mark the seek before dispatching so the engine's HAL-reset heuristic does
            // not misinterpret the resulting STATE_BUFFERING as an audio HAL underflow and
            // rebuild the players (which would race with the in-flight seek command).
            dualPlayerEngine.notifyExternalSeekInitiated()
            try {
                mediaController?.seekTo(targetPosition)
            } catch (e: IllegalStateException) {
                Timber.w(e, "seekTo failed on released/invalid mediaController")
            }
        }
    }

    /**
     * @param currentQueueMediaIds Optional snapshot of the current playback queue as a list of
     *   media IDs (in queue order). When provided, the previous-song lookup is resolved in memory
     *   rather than by scanning the MediaController timeline via N Binder IPC calls — which would
     *   block the main thread on large queues and cause a visible UI freeze.
     */
    fun previousSong(currentQueueMediaIds: List<String>? = null) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.previous()
            return
        }
        val controller = mediaController ?: return

        // If the song has been playing for more than 3 seconds, rewind to the beginning
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
            return
        }

        // Always navigate to the actual previously-played song from the history stack
        if (playHistoryStack.size >= 2) {
            playHistoryStack.removeLast()
            val targetMediaId = playHistoryStack.lastOrNull()
            if (targetMediaId != null) {
                // Find matching media item directly in controller timeline to avoid index desync
                val targetIndex = (0 until controller.mediaItemCount)
                    .firstOrNull { controller.getMediaItemAt(it).mediaId == targetMediaId }
                    ?: (if (currentQueueMediaIds != null) {
                        val idx = currentQueueMediaIds.indexOf(targetMediaId)
                        if (idx >= 0 && idx < controller.mediaItemCount) idx else null
                    } else null)
                if (targetIndex != null) {
                    controller.seekTo(targetIndex, 0L)
                    return
                }
            }
        }
        // Fallback when history is too short or target not found in queue
        val prevIndex = controller.currentMediaItemIndex - 1
        if (prevIndex >= 0) {
            controller.seekTo(prevIndex, 0L)
        } else {
            controller.seekTo(0L)
        }
    }

    fun nextSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.next()
        } else {
            val controller = mediaController ?: return
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else {
                val nextIndex = controller.currentMediaItemIndex + 1
                if (nextIndex < controller.mediaItemCount) {
                    controller.seekTo(nextIndex, 0L)
                } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0) {
                    controller.seekTo(0, 0L)
                } else {
                    controller.sendCustomCommand(
                        androidx.media3.session.SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SKIP_NEXT, android.os.Bundle.EMPTY),
                        android.os.Bundle.EMPTY
                    )
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val currentRepeatMode = remoteMediaClient.mediaStatus?.getQueueRepeatMode() ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
            val newMode = when (currentRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(newMode)
            
            // Map remote mode back to local constant for persistence/UI
            val mappedLocalMode = when (newMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            scope?.launch { userPreferencesRepository.setRepeatMode(mappedLocalMode) }
            _stablePlayerState.update { it.copy(repeatMode = mappedLocalMode) }
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
            scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
            _stablePlayerState.update { it.copy(repeatMode = newMode) }
        }
    }

    fun setRepeatMode(mode: Int) {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val remoteMode = when (mode) {
                Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(remoteMode)
        } else {
             mediaController?.repeatMode = mode
        }
        
        scope?.launch { userPreferencesRepository.setRepeatMode(mode) }
        _stablePlayerState.update { it.copy(repeatMode = mode) }
    }

    /* -------------------------------------------------------------------------- */
    /*                               Progress Updates                             */
    /* -------------------------------------------------------------------------- */
    
    private var progressJob: Job? = null
    private var progressSubscriptionWatcherJob: Job? = null
    private val progressPoke = Channel<Unit>(Channel.CONFLATED)

    /**
     * Reconciles duration reported by the player with the current song metadata duration.
     *
     * Why:
     * - During some transitions (notably crossfade player swaps), the reported duration can lag
     *   behind the currently visible track for a short period.
     * - Relying only on one source can make progress run too slow/fast.
     */
    private fun resolveEffectiveDuration(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long {
        val reported = when {
            reportedDurationMs == C.TIME_UNSET -> 0L
            reportedDurationMs < 0L -> 0L
            else -> reportedDurationMs
        }
        val hint = songDurationHintMs.coerceAtLeast(0L)
        val position = currentPositionMs.coerceAtLeast(0L)

        if (reported <= 0L) return hint
        if (hint <= 0L) return reported

        val diff = abs(reported - hint)
        if (diff <= DURATION_MISMATCH_TOLERANCE_MS) return reported

        // If playback already passed the metadata hint, trust the reported duration to avoid clipping.
        if (position > hint + DURATION_MISMATCH_TOLERANCE_MS && reported >= position) {
            return reported
        }

        // Otherwise prefer the shorter duration to avoid stale longer values after swaps.
        val resolved = minOf(reported, hint)
        if (diff > 10_000L) {
            Timber.tag(TAG).w(
                "Duration mismatch resolved (reported=%dms, hint=%dms, pos=%dms, resolved=%dms)",
                reported, hint, position, resolved
            )
        }
        return resolved
    }

    fun resolveDurationForPlaybackState(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long = resolveEffectiveDuration(
        reportedDurationMs = reportedDurationMs,
        songDurationHintMs = songDurationHintMs,
        currentPositionMs = currentPositionMs
    )

    fun startProgressUpdates() {
        stopProgressUpdates()
        progressSubscriptionWatcherJob = scope?.launch(Dispatchers.Main.immediate) {
            _currentPosition.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .filter { it }   // only the 0 -> >0 edge; losing a subscriber needs no refresh
                .collect { progressPoke.trySend(Unit) }
        }
        progressJob = scope?.launch(Dispatchers.Main) {
            while (true) {
                val tickMs = currentProgressTickMs()
                try {
                    val castSession = castStateHolder.castSession.value
                    val isRemote = castSession?.remoteMediaClient != null
                    
                    if (isRemote) {
                        val remoteClient = castSession?.remoteMediaClient
                        if (remoteClient != null) {
                            val isRemotePlaying = remoteClient.isPlaying
                            val currentPosition = remoteClient.approximateStreamPosition.coerceAtLeast(0L)
                            val songDurationHint = _stablePlayerState.value.currentSong?.duration ?: 0L
                            val duration = resolveEffectiveDuration(
                                reportedDurationMs = remoteClient.streamDuration,
                                songDurationHintMs = songDurationHint,
                                currentPositionMs = currentPosition
                            )
                            val isRemotelySeeking = castStateHolder.isRemotelySeeking.value
                            if (!isRemotelySeeking) {
                                castStateHolder.setRemotePosition(currentPosition)
                            }

                            val nextPosition = if (isRemotelySeeking) _currentPosition.value else currentPosition
                            if (_currentPosition.value != nextPosition) {
                                _currentPosition.value = nextPosition
                            }

                            _stablePlayerState.update { state ->
                                if (
                                    state.totalDuration == duration &&
                                    state.isPlaying == isRemotePlaying &&
                                    state.playWhenReady == isRemotePlaying
                                ) {
                                    state
                                } else {
                                    state.copy(
                                        totalDuration = duration,
                                        isPlaying = isRemotePlaying,
                                        playWhenReady = isRemotePlaying
                                    )
                                }
                            }
                        }
                    } else {
                         val controller = mediaController
                         // Media3: Check isPlaying or playbackState == READY/BUFFERING
                         if (controller != null && controller.isPlaying && !isSeeking) {
                             val visibleSong = _stablePlayerState.value.currentSong
                             val currentMediaId = controller.currentMediaItem?.mediaId
                             val hasMediaMismatch = visibleSong?.id != null &&
                                 currentMediaId != null &&
                                 visibleSong.id != currentMediaId

                             if (hasMediaMismatch) {
                                consecutiveMediaMismatchProgressSkips++
                                if (consecutiveMediaMismatchProgressSkips <= 2) {
                                    Timber.tag(TAG).v(
                                         "Skipping local progress tick due media mismatch (visible=%s, player=%s)",
                                         visibleSong?.id,
                                         currentMediaId
                                     )
                                    delay(tickMs)
                                    continue
                                }
                            } else {
                                consecutiveMediaMismatchProgressSkips = 0
                            }

                              val currentPosition = controller.currentPosition.coerceAtLeast(0L)
                              val songDurationHint = visibleSong?.duration ?: 0L
                              val duration = resolveEffectiveDuration(
                                  reportedDurationMs = controller.duration,
                                  songDurationHintMs = songDurationHint,
                                  currentPositionMs = currentPosition
                              )

                              val resolvedPosition = resolveUiPosition(currentMediaId, currentPosition)
                              if (_currentPosition.value != resolvedPosition) {
                                  _currentPosition.value = resolvedPosition
                              }

                              _stablePlayerState.update { state ->
                                  if (state.totalDuration == duration) {
                                      state
                                  } else {
                                      state.copy(totalDuration = duration)
                                  }
                             }
                          }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in progress update loop")
                }
                // Interruptible wait: returns immediately when a UI surface starts observing position
                withTimeoutOrNull(tickMs) { progressPoke.receive() }
            }
        }
    }

    private fun currentProgressTickMs(): Long {
        if (!powerManager.isInteractive) return BACKGROUND_PROGRESS_TICK_MS
        // If nothing is rendering the playback slider/position, poll at 1s to stop 4 Binder IPCs/sec
        if (_currentPosition.subscriptionCount.value == 0) return UNOBSERVED_PROGRESS_TICK_MS
        return FOREGROUND_PROGRESS_TICK_MS
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
        progressSubscriptionWatcherJob?.cancel()
        progressSubscriptionWatcherJob = null
    }

    /* -------------------------------------------------------------------------- */
    /*                               Shuffle & Repeat                             */
    /* -------------------------------------------------------------------------- */

    private suspend fun applyNewQueueToPlayer(
        newQueue: List<Song>,
        targetIndex: Int
    ) {
        if (newQueue.isEmpty()) return
        val masterPlayer = dualPlayerEngine.masterPlayer
        val safeTargetIndex = targetIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))

        val (currentMediaItem, currentPosition, shouldResumePlayback) = withContext(Dispatchers.Main) {
            Triple(
                masterPlayer.currentMediaItem,
                masterPlayer.currentPosition,
                masterPlayer.playWhenReady || masterPlayer.isPlaying
            )
        }

        // 1. Map new queue to MediaItem instances (reusing current playing item where possible to prevent pops)
        val preparedMediaItems = withContext(Dispatchers.Default) {
            newQueue.mapIndexed { index, song ->
                if (index == safeTargetIndex && currentMediaItem != null && currentMediaItem.mediaId == song.id) {
                    currentMediaItem
                } else {
                    MediaItemBuilder.build(song)
                }
            }
        }

        // 2. Atomically update the player's timeline on the Main thread.
        withContext(Dispatchers.Main) {
            masterPlayer.setMediaItems(preparedMediaItems, safeTargetIndex, currentPosition)
            if (shouldResumePlayback) {
                masterPlayer.playWhenReady = true
                if (!masterPlayer.isPlaying) {
                    masterPlayer.play()
                }
            }
            dualPlayerEngine.cancelNext()
        }
    }

    fun toggleShuffle(
        currentSongs: List<Song>,
        currentSong: Song?,
        currentQueueSourceName: String = "None",
        updateQueueCallback: (List<Song>) -> Unit
    ) {
        val nowMs = SystemClock.elapsedRealtime()
        if (shuffleToggleJob?.isActive == true) return
        if ((nowMs - lastShuffleToggleFinishedAtMs) < SHUFFLE_TOGGLE_COOLDOWN_MS) return

        val coroutineScope = scope ?: return
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            shuffleToggleJob = coroutineScope.launch {
                val currentShuffle = _stablePlayerState.value.isShuffleEnabled
                val targetShuffle = !currentShuffle
                _stablePlayerState.update { it.copy(isShuffleEnabled = targetShuffle, isShuffleTransitionInProgress = true) }
                try {
                    val remoteMediaClient = castSession.remoteMediaClient
                    val newRepeatMode = if (remoteMediaClient?.mediaStatus?.getQueueRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL
                    } else {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                    }
                    castStateHolder.castPlayer?.setRepeatMode(newRepeatMode)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error toggling cast shuffle")
                    _stablePlayerState.update { it.copy(isShuffleEnabled = currentShuffle) }
                } finally {
                    lastShuffleToggleFinishedAtMs = SystemClock.elapsedRealtime()
                    _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = false) }
                    shuffleToggleJob = null
                }
            }
        } else {
            shuffleToggleJob = coroutineScope.launch {
                val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled
                val targetShuffle = !isCurrentlyShuffled
                _stablePlayerState.update { it.copy(isShuffleEnabled = targetShuffle, isShuffleTransitionInProgress = true) }
                try {
                    val player = dualPlayerEngine.masterPlayer
                    val effectiveSongs = if (currentSongs.isNotEmpty()) {
                        currentSongs
                    } else if (currentSong != null) {
                        listOf(currentSong)
                    } else {
                        emptyList()
                    }

                    if (effectiveSongs.isEmpty()) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = isCurrentlyShuffled) }
                        return@launch
                    }

                    if (targetShuffle) {
                        // Enable Shuffle: save original order before modifying
                        if (!queueStateHolder.hasOriginalQueue()) {
                            queueStateHolder.setOriginalQueueOrder(effectiveSongs)
                        }

                        val currentMediaId = withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId } ?: currentSong?.id
                        val playerCurrentIndex = withContext(Dispatchers.Main) { player.currentMediaItemIndex }
                            .takeIf { it in effectiveSongs.indices }
                        val currentIndex = when {
                            playerCurrentIndex != null && currentMediaId != null &&
                                effectiveSongs.getOrNull(playerCurrentIndex)?.id == currentMediaId -> playerCurrentIndex
                            playerCurrentIndex != null && currentMediaId == null -> playerCurrentIndex
                            currentMediaId != null ->
                                effectiveSongs.indexOfFirst { it.id == currentMediaId }.takeIf { it >= 0 }
                            else -> null
                        } ?: 0

                        val (cappedSongs, safeCurrentIndex) = capQueueAroundCurrent(effectiveSongs, currentIndex, shuffleOthers = true)

                        val shuffledQueue = withContext(Dispatchers.Default) {
                            QueueUtils.buildAnchoredShuffleQueueSuspending(cappedSongs, safeCurrentIndex, startAtZero = true)
                        }

                        // Apply shuffled queue atomically and smoothly with current song at 0 and all other songs following
                        applyNewQueueToPlayer(shuffledQueue, targetIndex = 0)

                        updateQueueCallback(shuffledQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = true) }

                        withContext(Dispatchers.Main) {
                            player.shuffleModeEnabled = true
                            (player as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                                androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(shuffledQueue.size)
                            )
                        }

                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(true)
                            }
                        }
                    } else {
                        // Disable Shuffle
                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(false)
                            }
                        }

                        if (!queueStateHolder.hasOriginalQueue()) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        val originalQueue = queueStateHolder.originalQueueOrder
                        val currentSongId = currentSong?.id ?: withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId }
                        val originalIndex = originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }

                        if (originalIndex == null) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        val (cappedOriginalQueue, safeOriginalIndex) = capQueueAroundCurrent(originalQueue, originalIndex, shuffleOthers = false)

                        // Apply original queue atomically and smoothly
                        applyNewQueueToPlayer(cappedOriginalQueue, safeOriginalIndex)

                        updateQueueCallback(cappedOriginalQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        queueStateHolder.clearOriginalQueue()

                        withContext(Dispatchers.Main) {
                            player.shuffleModeEnabled = false
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error toggling local shuffle")
                    _stablePlayerState.update { it.copy(isShuffleEnabled = isCurrentlyShuffled) }
                } finally {
                    lastShuffleToggleFinishedAtMs = SystemClock.elapsedRealtime()
                    _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = false) }
                    shuffleToggleJob = null
                }
            }
        }
    }

    private fun capQueueAroundCurrent(
        queue: List<Song>,
        currentIndex: Int,
        shuffleOthers: Boolean
    ): Pair<List<Song>, Int> {
        if (queue.size <= 500) return queue to currentIndex
        val currentItem = queue.getOrNull(currentIndex)
            ?: return queue.take(500) to currentIndex.coerceAtMost(499)
        val others = queue.filterIndexed { index, _ -> index != currentIndex }
        val chosenOthers = if (shuffleOthers) others.shuffled().take(499) else others.take(499)
        return (listOf(currentItem) + chosenOthers) to 0
    }

    fun onCleared() {
        stopProgressUpdates()
        remoteSeekUnlockJob?.cancel()
        remoteSeekUnlockJob = null
        shuffleToggleJob?.cancel()
        shuffleToggleJob = null
        // scope = null // scope in our class is private val scope: CoroutineScope = appScope, so we don't need to nullify it if it is not mutable, or we can just skip nullifying it.
    }
}

