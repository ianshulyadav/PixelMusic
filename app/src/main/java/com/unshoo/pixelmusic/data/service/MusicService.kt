package com.unshoo.pixelmusic.data.service

import android.app.AlarmManager
import android.app.BackgroundServiceStartNotAllowedException
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.unshoo.pixelmusic.PixelMusicApplication
import com.unshoo.pixelmusic.MainActivity
import com.unshoo.pixelmusic.presentation.utils.AppIconManager
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.PlayerInfo
import com.unshoo.pixelmusic.data.model.PlaybackQueueItemSnapshot
import com.unshoo.pixelmusic.data.model.PlaybackQueueSnapshot
import com.unshoo.pixelmusic.data.preferences.EqualizerPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.ThemePreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import com.unshoo.pixelmusic.data.service.player.TransitionController
import com.unshoo.pixelmusic.ui.glancewidget.ControlWidget4x2
import com.unshoo.pixelmusic.ui.glancewidget.PixelMusicGlanceWidget
import com.unshoo.pixelmusic.ui.glancewidget.PlayerActions
import com.unshoo.pixelmusic.ui.glancewidget.PlayerInfoStateDefinition
import com.unshoo.pixelmusic.utils.AlbumArtUtils
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.unshoo.pixelmusic.data.equalizer.EqualizerManager
import com.unshoo.pixelmusic.data.model.WidgetThemeColors
import com.unshoo.pixelmusic.data.preferences.AlbumArtColorAccuracy
import com.unshoo.pixelmusic.data.preferences.AlbumArtPaletteStyle
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemeProcessor
import androidx.compose.ui.graphics.toArgb
import com.unshoo.pixelmusic.ui.glancewidget.BarWidget4x1
import com.unshoo.pixelmusic.ui.glancewidget.GridWidget2x2
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.unshoo.pixelmusic.data.preferences.ThemePreference
import com.unshoo.pixelmusic.data.service.auto.AutoMediaBrowseTree
import com.unshoo.pixelmusic.data.service.wear.buildWearThemePalette
import com.unshoo.pixelmusic.data.service.wear.WearStatePublisher
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair
import com.unshoo.pixelmusic.utils.ArtworkTransportSanitizer
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.shared.WearIntents
import com.unshoo.pixelmusic.di.AppScope
import com.unshoo.pixelmusic.presentation.viewmodel.ListeningStatsTracker
import com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager
import com.unshoo.pixelmusic.data.remote.youtube.QueuePreloadManager
import com.unshoo.pixelmusic.data.remote.youtube.ExoCache
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository as YoutubeDatastoreRepository
import kotlin.math.abs
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

import javax.inject.Inject
import androidx.core.net.toUri

// Acciones personalizadas para compatibilidad con el widget existente


@UnstableApi
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var engine: DualPlayerEngine
    @Inject
    lateinit var controller: TransitionController
    @Inject
    lateinit var musicRepository: MusicRepository
    @Inject
    lateinit var musicDao: com.unshoo.pixelmusic.data.database.MusicDao
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var equalizerPreferencesRepository: EqualizerPreferencesRepository
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var equalizerManager: EqualizerManager
    @Inject
    lateinit var colorSchemeProcessor: ColorSchemeProcessor
    @Inject
    lateinit var autoMediaBrowseTree: AutoMediaBrowseTree
    @Inject
    lateinit var wearStatePublisher: WearStatePublisher
    @Inject
    lateinit var replayGainManager: com.unshoo.pixelmusic.data.media.ReplayGainManager
    @Inject
    lateinit var listeningStatsTracker: ListeningStatsTracker
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope
    @Inject
    lateinit var youtubeDatastoreRepository: YoutubeDatastoreRepository
    @Inject
    lateinit var exoCache: ExoCache
    @Inject
    lateinit var engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao

    // FIXED: Proper auth provider for telemetry
    private lateinit var telemetryManager: com.unshoo.pixelmusic.data.remote.youtube.YouTubeTelemetryManager

    // Telemetry state tracking
    private var lastTelemetryVideoId: String? = null

    private var scrobbleManager: com.unshoo.pixelmusic.data.lastfm.ScrobbleManager? = null

    private var replayGainEnabled = false
    private var replayGainUseAlbumGain = false
    private var replayGainJob: Job? = null
    private var replayGainRequestToken = 0L
    private var userSelectedVolume = 1f
    private var expectedReplayGainVolume: Float? = null
    private var pendingReplayGainVolume: Float? = null
    // Last successfully applied RG volume — used to avoid a full-volume spike
    // during the IO read for the next track (Repeat/Shuffle/Queue changes).
    private var lastAppliedReplayGainVolume: Float? = null
    // MediaId for which lastAppliedReplayGainVolume was computed.
    private var lastReplayGainMediaId: String? = null

    private var favoriteSongIds = emptySet<String>()
    private var mediaSession: MediaLibrarySession? = null
    @Volatile
    private var isServiceDestroyed = false
    private val controllerLastBrowsedParent = mutableMapOf<String, String>()
    // BUGFIX (lag — service was serialised with the UI): the previous
    // `Dispatchers.Main` made every coroutine this service spawned compete
    // with the main thread for the very same looper the foreground
    // notification, the Media3 session callbacks, and the Compose UI all
    // run on. A single song tap that triggered a listener -> serviceScope
    // coroutine would therefore block the next frame; the runtime log
    // shows a 51-frame Choreographer skip during playback, plus repeated
    // BUFFERING -> READY transitions stacking up on the same thread.
    //
    // We now use a dedicated 4-thread dispatcher with daemon threads that
    // live as long as the service. Audio-engine mutations still hop to
    // Main with explicit `withContext(Dispatchers.Main)` at the call site
    // (see e.g. the auto-queue re-seed path), but the *bulk* of the
    // service's work — snapshot writes, queue re-seeds, MediaLibrary
    // callbacks, telemetry — now runs off the UI thread.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keepPlayingInBackground = true
    private var isManualShuffleEnabled = false
    private var persistentShuffleEnabled = false
    // Holds the previous main-thread UncaughtExceptionHandler so we can restore it in onDestroy.
    private var previousMainThreadExceptionHandler: Thread.UncaughtExceptionHandler? = null
    // --- Counted Play State ---
    private var countedPlayActive = false
    private var countedPlayTarget = 0
    private var countedPlayCount = 0
    private var countedOriginalId: String? = null
    private var countedPlayListener: Player.Listener? = null
    private val alarmManager by lazy {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private var endOfTrackTimerSongId: String? = null
    private var castSessionManager: SessionManager? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var castRemoteClientCallback: RemoteMediaClient.Callback? = null
    private var observedCastSession: CastSession? = null
    private var observedCastRemoteClient: RemoteMediaClient? = null
    private var castRemoteAttachRetryJob: Job? = null
    private var activeCastStatsOccurrenceId: String? = null
    private var playbackSnapshotPersistJob: Job? = null
    private var telemetryJob: Job? = null
    private var telemetryWatchdogJob: Job? = null
    private var streamContinuityWatchdogJob: Job? = null
    private var isRestoringPlaybackSnapshot = false
    private var isPlaybackUnloadInProgress = false
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var headsetReconnectCallback: AudioDeviceCallback? = null
    private var shouldResumeAfterHeadsetReconnect = false
    private var lastNoisyPauseRealtimeMs = 0L
    private var resumeOnHeadsetReconnectEnabled = false
    private var temporaryForegroundStartedInOnCreate = false

    companion object {
        private const val TAG = "MusicService_PixelMusic"
        const val NOTIFICATION_ID = 101
        const val ACTION_SLEEP_TIMER_EXPIRED = "com.unshoo.pixelmusic.ACTION_SLEEP_TIMER_EXPIRED"
        const val EXTRA_FORCE_FOREGROUND_ON_START =
            "com.unshoo.pixelmusic.extra.FORCE_FOREGROUND_ON_START"
        // Queue/index/flags snapshot is only used for restore on process death. A full-queue
        // JSON+DataStore rewrite on every Media3 event (track transition fires 3-4 listeners
        // within ~200ms) is unnecessary work. 1500ms coalesces those without harming restore.
        private const val PLAYBACK_SNAPSHOT_DEBOUNCE_MS = 300L
        private const val FORCED_WIDGET_STATE_DEBOUNCE_MS = 250L
        private const val MEDIA_SESSION_BUTTON_DEBOUNCE_MS = 250L

        /**
         * How often the telemetry watchdog checks that the 1s history/heartbeat loop is alive
         * while the player is playing. Cheap enough to run for the whole service lifetime and
         * short enough that at most one heartbeat window is ever lost.
         */
        private const val TELEMETRY_WATCHDOG_INTERVAL_MS = 5_000L

        /**
         * How often the stream-continuity watchdog samples playback progress to detect a stall
         * that ExoPlayer never reported as an error (typical when a long-cached googlevideo URL
         * runs past its cached bytes and the CDN answers 403 on the very first range request
         * for the uncached remainder).
         */
        private const val STREAM_CONTINUITY_WATCHDOG_INTERVAL_MS = 2_000L

        /**
         * A player that claims to be playing but whose position has not advanced for this long
         * is treated as stalled and force-recovered (re-resolve + prepare + play), never paused.
         */
        private const val STREAM_STALL_THRESHOLD_MS = 9_000L
        private val pendingMediaButtonForegroundStarts = AtomicInteger(0)

        private const val APP_PACKAGE_PREFIX = "com.unshoo.pixelmusic"
        private val BLOCKED_WEAR_CONTROLLER_PREFIXES = listOf(
            "com.google.android.wearable",
            "com.google.android.clockwork",
            "com.google.android.apps.wearable",
            "com.google.android.apps.wear.companion",
            "com.samsung.android.app.watchmanager",
            "com.mobvoi.wear",
        )
        private val WEAR_HINT_KEY_MARKERS = listOf(
            "wear",
            "clockwork",
            "companion",
            "node",
            "remote_device",
        )
        private const val AUTO_CONTEXT_RECENT = "recent"
        private const val AUTO_CONTEXT_FAVORITES = "favorites"
        private const val AUTO_CONTEXT_ALL_SONGS = "all_songs"
        private const val AUTO_CONTEXT_ALBUM = "album"
        private const val AUTO_CONTEXT_ARTIST = "artist"
        private const val AUTO_CONTEXT_PLAYLIST = "playlist"
        private const val DEFAULT_STREAM_BUFFER_SIZE = 8 * 1024
        private const val WIDGET_ART_FAILURE_RETRY_MS = 30_000L
        private const val WIDGET_QUEUE_PREVIEW_LIMIT = 4
        private const val HEADSET_RECONNECT_RESUME_WINDOW_MS = 15_000L
        private const val CAST_REMOTE_CLIENT_ATTACH_RETRY_MS = 250L
        private const val CAST_REMOTE_CLIENT_ATTACH_RETRIES = 40

        fun markPendingMediaButtonForegroundStart() {
            pendingMediaButtonForegroundStarts.incrementAndGet()
        }

        fun unmarkPendingMediaButtonForegroundStart() {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return
                }
            }
        }

        private fun consumePendingMediaButtonForegroundStart(): Boolean {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return false
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return true
                }
            }
        }
    }

    private val playerSwapListener: (Player) -> Unit = { newPlayer ->
        publishMediaSessionPlayer(newPlayer, "Swapped MediaSession player to new instance.")
        prepareReplayGainForTransitionPlayer(newPlayer)
    }

    private val nextPlayerPreparedListener: (Player) -> Unit = { preparedPlayer ->
        val incomingItem = preparedPlayer.currentMediaItem
        if (incomingItem != null) {
            prefetchReplayGain(incomingItem)
            val cachedVolume = getCachedReplayGainVolume(incomingItem)
            if (cachedVolume != null) {
                engine.incomingTrackReplayGainVolume = cachedVolume
            }
        }
    }

    private val transitionDisplayPlayerListener: (Player) -> Unit = { displayPlayer ->
        prepareReplayGainForTransitionPlayer(displayPlayer)
    }

    private val transitionFinishedListener: () -> Unit = {
        onTransitionFinished()
    }

    private fun publishMediaSessionPlayer(player: Player, logMessage: String) {
        val session = mediaSession ?: return
        // While casting, the MediaSession must remain attached to the remote-state adapter.
        // Crossfade/master-player callbacks can still arrive from the local engine; allowing one
        // to replace the adapter makes Android Auto, lock-screen controls and the notification
        // suddenly control a paused local player while the Chromecast keeps playing.
        if (observedCastSession != null && player !is CastMediaSessionPlayer) {
            Timber.tag(TAG).d("Ignoring local MediaSession player swap while casting: %s", logMessage)
            return
        }
        val oldPlayer = session.player
        if (oldPlayer !== player) {
            oldPlayer.removeListener(playerListener)
            session.player = player
            player.addListener(playerListener)
            AutoQueueManager.updatePlayer(player)
            QueuePreloadManager.updatePlayer(player)

            // Explicitly sync the new player's track transition state
            handleMediaItemTransition(player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        }

        Timber.tag("MusicService").d(logMessage)
        syncLocalListeningStatsFromPlayer(player)
        requestWidgetFullUpdate(force = true)
        refreshMediaSessionUi(session, force = true)
    }

    private fun prepareReplayGainForTransitionPlayer(player: Player) {
        // Pre-compute ReplayGain for the incoming track while the crossfade is still running.
        // isTransitionRunning() is true here, so applyReplayGain stores the result as
        // pendingReplayGainVolume. onTransitionFinished() applies it cleanly once the fade
        // loop ends, avoiding any volume jump on the incoming track.
        //
        // Also try to set incomingTrackReplayGainVolume immediately from cache so the
        // fade loop can use the correct final volume even before the IO coroutine finishes.
        val incomingItem = player.currentMediaItem
        val cachedVolume = getCachedReplayGainVolume(incomingItem)
        if (cachedVolume != null) {
            engine.incomingTrackReplayGainVolume = cachedVolume
        }
        applyReplayGain(incomingItem)
    }

    private fun syncLocalListeningStatsFromPlayer(
        player: Player = engine.masterPlayer,
        forceNewSession: Boolean = false
    ) {
        val mediaItem = player.currentMediaItem
        val songId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
        if (songId == null) {
            if (
                player.mediaItemCount == 0 ||
                player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED
            ) {
                listeningStatsTracker.onPlaybackStopped()
            }
            return
        }

        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val durationMs = player.duration
        val fallbackDurationMs = mediaItem.mediaMetadata.extras
            ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L)
            ?: 0L

        val title = mediaItem.mediaMetadata.title?.toString()
        val artist = mediaItem.mediaMetadata.artist?.toString()
        val thumbnail = mediaItem.mediaMetadata.artworkUri?.toString()
            ?: mediaItem.mediaMetadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)

        if (forceNewSession) {
            listeningStatsTracker.onTrackChanged(
                songId = songId,
                positionMs = positionMs,
                durationMs = durationMs,
                fallbackDurationMs = fallbackDurationMs,
                isPlaying = player.isPlaying,
                title = title,
                artist = artist,
                thumbnail = thumbnail
            )
        } else {
            listeningStatsTracker.ensureSession(
                songId = songId,
                positionMs = positionMs,
                durationMs = durationMs,
                fallbackDurationMs = fallbackDurationMs,
                isPlaying = player.isPlaying,
                title = title,
                artist = artist,
                thumbnail = thumbnail
            )
        }
    }

    override fun onCreate() {
        // --- CRITICAL LIFECYCLE FIX (adopted from PixelMusic) ---
        // Self-start the service to decouple it from MainActivity's bound lifecycle.
        // This guarantees the OS won't instantly destroy it when the app is swiped
        // from recents, so music + the media notification keep running.
        try {
            startService(Intent(this, MusicService::class.java))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to self-start service")
        }

        // Media3's Cast SDK callback path (MediaSessionImpl$$ExternalSyntheticLambda →
        // Util.postOrRun → MediaNotificationManager.updateNotificationInternal) calls
        // Service.startForeground() directly, bypassing onUpdateNotification() entirely.
        // Since startForeground() is final we cannot override it. Instead we intercept
        // ForegroundServiceStartNotAllowedException on the main thread before it reaches
        // ActivityThread and crashes the process.
        val existingHandler = Thread.currentThread().uncaughtExceptionHandler
        previousMainThreadExceptionHandler = existingHandler
        Thread.currentThread().setUncaughtExceptionHandler { thread, throwable ->
            fun isStartForegroundIssue(t: Throwable?): Boolean {
                if (t == null) return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (t is ForegroundServiceStartNotAllowedException || t is BackgroundServiceStartNotAllowedException)
                ) return true
                if (Build.VERSION.SDK_INT >= 34 && t.javaClass.name == "android.app.MissingForegroundServiceTypeException") return true
                if (t is IllegalStateException) {
                    val msg = t.message.orEmpty()
                    val hasStartForegroundFrame = t.stackTrace.any { it.methodName == "startForeground" }
                    if (hasStartForegroundFrame || msg.contains("startForeground", ignoreCase = true)) {
                        return true
                    }
                }
                return isStartForegroundIssue(t.cause)
            }

            if (isStartForegroundIssue(throwable)) {
                Timber.tag(TAG).w(throwable, "Foreground promotion rejected by Android runtime; gracefully detaching foreground state")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                } catch (detachErr: Throwable) {
                    Timber.tag(TAG).w(detachErr, "Failed to detach foreground state after startForeground restriction")
                }
            } else {
                existingHandler?.uncaughtException(thread, throwable)
            }
        }

        // A MEDIA_BUTTON broadcast starts the 5-second foreground-service timeout
        // BEFORE MusicService is created. Promote to foreground before super.onCreate()
        // — Hilt injection plus MediaLibraryService.onCreate() can otherwise consume
        // most of the budget on a slow cold-start. The temporary notification only uses
        // Context/resources, no injected fields, so it is safe before super.onCreate().
        temporaryForegroundStartedInOnCreate = consumePendingMediaButtonForegroundStart()
        if (temporaryForegroundStartedInOnCreate) {
            startTemporaryForegroundForCommand()
        }

        super.onCreate()

        // Initialize telemetry with direct Innertube mapping (no constructor parameters needed now)
        telemetryManager = com.unshoo.pixelmusic.data.remote.youtube.YouTubeTelemetryManager()

        listeningStatsTracker.initialize(appScope)
        scrobbleManager = com.unshoo.pixelmusic.data.lastfm.ScrobbleManager(scope = serviceScope)

        // Ensure engine is ready (re-initialize if service was restarted)
        // ExoPlayer must be created on the thread that owns its Looper,
        // which is the main thread for a foreground service, so this has
        // to stay here.
        engine.initialize()
        engine.setOnPlayerAboutToBeReleasedListener { oldPlayer ->
            oldPlayer.removeListener(playerListener)
        }
        userSelectedVolume = engine.masterPlayer.volume.coerceIn(0f, 1f)
        syncLocalListeningStatsFromPlayer(engine.masterPlayer)

        engine.masterPlayer.addListener(playerListener)

        // Adopted from PixelMusic: force a PARTIAL_WAKE_LOCK so aggressive OEM skins
        // (Funtouch OS etc.) don't sleep the CPU when the app is swiped from Recents.
        (engine.masterPlayer as? androidx.media3.exoplayer.ExoPlayer)?.setWakeMode(C.WAKE_MODE_LOCAL)

        // Handle player swaps (crossfade) to keep MediaSession in sync
        engine.addPlayerSwapListener(playerSwapListener)
        engine.addNextPlayerPreparedListener(nextPlayerPreparedListener)
        engine.addTransitionDisplayPlayerListener(transitionDisplayPlayerListener)
        engine.addTransitionFinishedListener(transitionFinishedListener)

        // Self-healing supervisor for history/telemetry.
        startTelemetryWatchdog()

        // BUGFIX (was: all on main thread, in onCreate): the calls below
        // — `AutoQueueManager.attach`, `QueuePreloadManager.attach`,
        // `controller.initialize()`, `initializeCastWearSync()`,
        // `registerHeadsetReconnectMonitor()` — used to run synchronously
        // here, eating into the foreground-service startup budget.
        // The runtime log shows the service takes 500-1500ms to start
        // before the foreground promotion is acknowledged, which is
        // enough to trigger ForegroundServiceStartNotAllowedException
        // on some OEM ROMs. We now hand them off to the background
        // serviceDispatcher (see the field declaration at the top of
        // this class) so the main thread is free to return from onCreate
        // as quickly as possible.
        //
        // BUGFIX (was: ALL of these in a single serviceScope.launch):
        // the Cast SDK and AudioManager both require their setup calls
        // (CastContext.getSharedInstance, addSessionManagerListener,
        // audioManager.registerAudioDeviceCallback) to run on the main
        // thread — running them on a background dispatcher will throw
        // IllegalStateException. The safe split is:
        //   - on the background dispatcher: AutoQueueManager.attach,
        //     QueuePreloadManager.attach, controller.initialize() (all
        //     pure network/DB work).
        //   - on the main thread: initializeCastWearSync() and
        //     registerHeadsetReconnectMonitor() (must be Main, but
        //     each is fast — they just register callbacks).

        // Attach YouTube radio-mode auto-queue and stream-URL preloader immediately
        AutoQueueManager.attach({ engine.masterPlayer }, this@MusicService, youtubeDatastoreRepository, serviceScope, musicDao, engagementDao, engine::forceRefreshQueueSnapshot, userPreferencesRepository)
        QueuePreloadManager.attach(engine.masterPlayer, this@MusicService, youtubeDatastoreRepository, serviceScope, exoCache, engine)

        serviceScope.launch {
            controller.initialize()
        }

        // The Cast and AudioManager setup must run on Main, but we kick
        // them off via a post so onCreate can return immediately. Posting
        // to the main looper is effectively "as soon as possible on Main",
        // which is still much faster than running synchronously in the
        // foreground-service startup window because the rest of onCreate
        // (the MediaLibrarySession build, the widget update, the
        // notification update) has already been queued.
        android.os.Handler(Looper.getMainLooper()).post {
            try {
                initializeCastWearSync()
                registerHeadsetReconnectMonitor()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Cast/audio setup failed during deferred init")
            }
        }

        serviceScope.launch {
            musicRepository.telegramRepository.downloadCompleted.collect {
                if (isCurrentWidgetArtworkBackedByTelegram()) {
                    invalidateCachedWidgetArtwork()
                    requestWidgetAndWearRefreshWithFollowUp()
                }
            }
        }

        // Restore equalizer state from preferences and only attach audio effects when
        // the user actually has at least one effect enabled for the current session.
        serviceScope.launch {
            val eqEnabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
            val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
            val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
            val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
            val bassBoostStrength = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
            val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
            val virtualizerStrength = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
            val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
            val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()

            equalizerManager.restoreState(
                eqEnabled, presetName, customBands,
                bassBoostEnabled, bassBoostStrength,
                virtualizerEnabled, virtualizerStrength,
                loudnessEnabled, loudnessStrength
            )

            val sessionId = engine.getAudioSessionId()
            if (sessionId != 0) {
                equalizerManager.attachToAudioSessionIfNeeded(sessionId)
            }

            // Re-attach equalizer whenever the active audio session changes (e.g. crossfade)
            engine.activeAudioSessionId.collect { newSessionId ->
                if (newSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(newSessionId)
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.youtubeSyncListeningActivityFlow.collect { enabled ->
                telemetryManager.setTelemetryEnabled(enabled)
            }
        }

        // Ensure YouTube.personalizedQueueEnabled is set before the first AutoQueue
        // refill, instead of depending on ExploreViewModel being created. Without
        // this, the flag stays at its default (true) and authenticated/personalized
        // "next" API calls may return items with higher overlap against the user's
        // library, amplifying the duplicate-page problem in AutoQueueManager.
        serviceScope.launch {
            userPreferencesRepository.youtubePersonalizedQueueFlow.collect { enabled ->
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.personalizedQueueEnabled = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.keepPlayingInBackgroundFlow.collect { enabled ->
                keepPlayingInBackground = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.lastfmScrobblingEnabledFlow.collect { enabled ->
                scrobbleManager?.scrobblingEnabled = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.lastfmUseNowPlayingFlow.collect { enabled ->
                scrobbleManager?.useNowPlaying = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.scrobbleMinSongDurationFlow.collect { duration ->
                scrobbleManager?.minSongDuration = duration
            }
        }

        serviceScope.launch {
            userPreferencesRepository.scrobbleDelayPercentFlow.collect { percent ->
                scrobbleManager?.scrobbleDelayPercent = percent
            }
        }

        serviceScope.launch {
            userPreferencesRepository.scrobbleDelaySecondsFlow.collect { seconds ->
                scrobbleManager?.scrobbleDelaySeconds = seconds
            }
        }

        serviceScope.launch {
            userPreferencesRepository.hiFiModeEnabledFlow.collect { enabled ->
                engine.setHiFiMode(enabled)
            }
        }

        serviceScope.launch {
            userPreferencesRepository.resumeOnHeadsetReconnectFlow.collect { enabled ->
                resumeOnHeadsetReconnectEnabled = enabled
                if (!enabled) {
                    clearHeadsetReconnectResume()
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.persistentShuffleEnabledFlow.collect { enabled ->
                persistentShuffleEnabled = enabled
            }
        }

        // ReplayGain preference collectors
        serviceScope.launch {
            userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
                replayGainEnabled = enabled
                // Re-apply to current track when toggled
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
                replayGainUseAlbumGain = useAlbum
                // Re-apply to current track when mode changes
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }

        // Initialize shuffle state from preferences
        serviceScope.launch {
            val persistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (persistent) {
                isManualShuffleEnabled = userPreferencesRepository.isShuffleOnFlow.first()
                mediaSession?.let { refreshMediaSessionUi(it) }
            }
        }

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val controllerPackage = controller.packageName
                val hintKeys = controller.connectionHints.keySet().joinToString(",")
                Timber.tag(TAG).d(
                    "onConnect from package=%s uid=%s trusted=%s version=%s hints=[%s]",
                    controllerPackage,
                    controller.uid,
                    controller.isTrusted,
                    controller.controllerVersion,
                    hintKeys
                )
                if (shouldRejectWearController(controller)) {
                    Timber.tag(TAG).i(
                        "Rejecting Wear system controller connection from package=%s",
                        controllerPackage
                    )
                    return MediaSession.ConnectionResult.reject()
                }

                val defaultResult = super.onConnect(session, controller)
                val customCommands = listOf(
                    MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER,
                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON,
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
                    MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
                    MusicNotificationProvider.CUSTOM_COMMAND_SKIP_NEXT,
                    MusicNotificationProvider.CUSTOM_COMMAND_SKIP_PREVIOUS,
                ).map { SessionCommand(it, Bundle.EMPTY) }

                val sessionCommandsBuilder = SessionCommands.Builder()
                    .addSessionCommands(defaultResult.availableSessionCommands.commands)
                customCommands.forEach { sessionCommandsBuilder.add(it) }
                grantArtworkUriPermissions(
                    controller.packageName,
                    listOfNotNull(session.player.currentMediaItem)
                )

                return MediaSession.ConnectionResult.accept(
                    sessionCommandsBuilder.build(),
                    defaultResult.availablePlayerCommands
                )
            }

            override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
                clearLastBrowsedParent(controller)
                super.onDisconnected(session, controller)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                Timber.tag("MusicService")
                    .d("onCustomCommand received: ${customCommand.customAction}")
                when (customCommand.customAction) {
                    MusicNotificationProvider.CUSTOM_COMMAND_SKIP_NEXT -> {
                        session.player.seekToNext()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SKIP_PREVIOUS -> {
                        session.player.seekToPrevious()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER -> {
                        closeNotificationPlayer()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY -> {
                        val count = args.getInt("count", 1)
                        startCountedPlay(count)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY -> {
                        stopCountedPlay()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION -> {
                        val minutes = args.getInt(
                            MusicNotificationProvider.EXTRA_SLEEP_TIMER_MINUTES,
                            0
                        )
                        setDurationSleepTimer(minutes)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_END_OF_TRACK_ENABLED,
                            true
                        )
                        setEndOfTrackSleepTimer(enabled)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER -> {
                        cancelSleepTimers()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE -> {
                        val enabled = !isManualShuffleEnabled
                        updateManualShuffleState(session, enabled = enabled, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_ON. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        updateManualShuffleState(session, enabled = true, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_OFF. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        updateManualShuffleState(session, enabled = false, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                            false
                        )
                        updateManualShuffleState(session, enabled = enabled, broadcast = false)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE -> {
                        val currentMode = session.player.repeatMode
                        val newMode = when (currentMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        session.player.repeatMode = newMode
                        refreshMediaSessionUi(session)
                        requestWidgetFullUpdate(force = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE -> {
                        val songId = session.player.currentMediaItem?.mediaId
                            ?: return@onCustomCommand Futures.immediateFuture(
                                SessionResult(SessionError.ERROR_UNKNOWN)
                            )
                        val targetFavoriteState = !isSongFavorite(songId)
                        return setCurrentSongFavoriteState(
                            session = session,
                            targetFavoriteState = targetFavoriteState
                        )
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_FAVORITE_ENABLED,
                            false
                        )
                        return setCurrentSongFavoriteState(
                            session = session,
                            targetFavoriteState = enabled
                        )
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            // --- Android Auto: Media Library Browsing ---

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId(AutoMediaBrowseTree.ROOT_ID)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle("PixelMusic")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    try {
                        rememberLastBrowsedParent(browser, parentId)
                        val children = autoMediaBrowseTree.getChildren(parentId, page, pageSize)
                        grantArtworkUriPermissions(browser.packageName, children)
                        LibraryResult.ofItemList(children, params)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetChildren failed for parentId=$parentId")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future {
                    try {
                        val item = autoMediaBrowseTree.getItem(mediaId)
                        if (item != null) {
                            grantArtworkUriPermissions(browser.packageName, listOf(item))
                            LibraryResult.ofItem(item, null)
                        } else {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetItem failed for mediaId=$mediaId")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                // Media3's search contract is asynchronous: Android Auto waits for this
                // notification before requesting onGetSearchResult. Returning success without
                // notifying leaves voice/keyboard search permanently spinning on many hosts.
                return serviceScope.future {
                    try {
                        val resultCount = autoMediaBrowseTree.search(query).size
                        session.notifySearchResultChanged(browser, query, resultCount, params)
                        LibraryResult.ofVoid()
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onSearch failed for query=$query")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    try {
                        val allResults = autoMediaBrowseTree.search(query)
                        val effectivePage = page.coerceAtLeast(0)
                        val effectivePageSize = if (pageSize > 0) pageSize else Int.MAX_VALUE
                        val offset = (effectivePage.toLong() * effectivePageSize.toLong())
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                        val pagedResults = allResults
                            .drop(offset)
                            .take(effectivePageSize)

                        grantArtworkUriPermissions(browser.packageName, pagedResults)
                        LibraryResult.ofItemList(pagedResults, params)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetSearchResult failed for query=$query")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                return serviceScope.future {
                    if (mediaItems.size == 1) {
                        resolveContextQueueForRequestedItem(mediaItems.first(), controller)?.let { queue ->
                            grantArtworkUriPermissions(controller.packageName, queue.mediaItems)
                            return@future queue.mediaItems
                        }
                    }
                    resolveMediaItemsByIds(mediaItems).also { resolvedItems ->
                        grantArtworkUriPermissions(
                            controller.packageName,
                            resolvedItems.trustedArtworkGrantItems
                        )
                    }.mediaItems
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                return serviceScope.future {
                    val requestedIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
                    val requestedItem = mediaItems.getOrNull(requestedIndex)

                    val contextQueue = requestedItem?.let {
                        resolveContextQueueForRequestedItem(it, controller)
                    }
                    if (contextQueue != null) {
                        grantArtworkUriPermissions(controller.packageName, contextQueue.mediaItems)
                        return@future MediaSession.MediaItemsWithStartPosition(
                            contextQueue.mediaItems,
                            contextQueue.startIndex,
                            startPositionMs
                        )
                    }

                    val resolvedItems = resolveMediaItemsByIds(mediaItems)
                    grantArtworkUriPermissions(
                        controller.packageName,
                        resolvedItems.trustedArtworkGrantItems
                    )
                    val safeStartIndex = requestedIndex.coerceIn(
                        0,
                        (resolvedItems.mediaItems.size - 1).coerceAtLeast(0)
                    )
                    MediaSession.MediaItemsWithStartPosition(
                        resolvedItems.mediaItems,
                        safeStartIndex,
                        startPositionMs
                    )
                }
            }
        }

        mediaSession = MediaLibrarySession.Builder(this, engine.masterPlayer, callback)
            .setSessionActivity(getOpenAppPendingIntent())
            .setBitmapLoader(CoilBitmapLoader(this, serviceScope))
            .build()

        mediaSession?.let { session ->
            val initialButtons = buildMediaButtonPreferences(session)
            session.setMediaButtonPreferences(initialButtons)
            session.setCustomLayout(initialButtons)
            lastAppliedMediaButtonSignature = buildMediaButtonPreferencesSignature(session)
        }

        var lastAutoQueueEnabled: Boolean? = null
        serviceScope.launch {
            youtubeDatastoreRepository.settings
                .map { it.autoQueueEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    val wasDisabled = lastAutoQueueEnabled == false
                    lastAutoQueueEnabled = enabled
                    if (enabled && wasDisabled) {
                        // resetAndReseedFromCurrentSong clears addedVideoIds fully, then
                        // appends a fresh related-songs queue on top of whatever is already
                        // queued. Deliberately NOT trimming first: trimming here raced the
                        // network fetch that rebuilds the queue and left the user with an
                        // empty queue whenever that fetch was slow or failed outright (e.g.
                        // on a weak connection).
                        AutoQueueManager.resetAndReseedFromCurrentSong()
                    } else if (!enabled) {
                        AutoQueueManager.disableAndTrimQueue()
                    }
                }
        }

        val localOnlyProvider = LocalOnlyMediaNotificationProvider(this).also {
            it.setSmallIcon(R.drawable.monochrome_player)
        }
        setMediaNotificationProvider(localOnlyProvider)
        serviceScope.launch {
            restorePlaybackQueueSnapshotIfNeeded()
            mediaSession?.let { refreshMediaSessionUi(it) }
            requestWidgetFullUpdate(force = true)
        }

        // React to theme preference changes by force-updating the widget and Wear OS state immediately
        serviceScope.launch {
            themePreferencesRepository.playerThemePreferenceFlow.drop(1).collect {
                requestWidgetFullUpdate(force = true)
            }
        }
        serviceScope.launch {
            themePreferencesRepository.colorPalettePreferenceFlow.drop(1).collect {
                requestWidgetFullUpdate(force = true)
            }
        }
        serviceScope.launch {
            themePreferencesRepository.albumArtPaletteStyleFlow.drop(1).collect {
                requestWidgetFullUpdate(force = true)
            }
        }
        serviceScope.launch {
            themePreferencesRepository.albumArtColorAccuracyFlow.drop(1).collect {
                requestWidgetFullUpdate(force = true)
            }
        }
        serviceScope.launch {
            themePreferencesRepository.appLauncherIconFlow.drop(1).collect { iconId ->
                try {
                    val pendingIntent = getOpenAppPendingIntent()
                    mediaSession?.setSessionActivity(pendingIntent)
                    Timber.tag("MusicService").d("Updated mediaSession sessionActivity for appLauncherIcon: $iconId")
                } catch (e: Exception) {
                    Timber.tag("MusicService").w(e, "Failed to update sessionActivity for appLauncherIcon $iconId")
                }
            }
        }

        serviceScope.launch {
            musicRepository.getFavoriteSongIdsFlow().collect { ids ->
                Timber.tag("MusicService")
                    .d("favoriteSongIdsFlow(Room) collected. New ids size: ${ids.size}")
                val oldIds = favoriteSongIds
                favoriteSongIds = ids
                val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
                if (currentSongId != null) {
                    val wasFavorite = isSongFavoriteWithSet(currentSongId, oldIds)
                    val isFavorite = isSongFavoriteWithSet(currentSongId, ids)
                    if (wasFavorite != isFavorite) {
                        Timber.tag("MusicService")
                            .d("Favorite status changed for current song. Updating notification.")
                        mediaSession?.let { refreshMediaSessionUi(it, force = true) }
                        requestWidgetFullUpdate(force = true)
                    }
                }
            }
        }
    }

    private fun shouldRejectWearController(controller: MediaSession.ControllerInfo): Boolean {
        val controllerPackage = controller.packageName
        if (controllerPackage.startsWith(APP_PACKAGE_PREFIX)) {
            return false
        }
        val blockedByPackage = BLOCKED_WEAR_CONTROLLER_PREFIXES.any { prefix ->
            controllerPackage.startsWith(prefix)
        }
        if (blockedByPackage) {
            return true
        }

        val hasWearHints = controller.connectionHints.keySet().any { key ->
            WEAR_HINT_KEY_MARKERS.any { marker ->
                key.contains(marker, ignoreCase = true)
            }
        }
        // If hints identify a Wear/remote controller and it's not our app package,
        // reject to avoid the default Wear system media player hijacking the session.
        return hasWearHints
    }

    private fun createSleepTimerPendingIntent(): PendingIntent {
        val intent = Intent(this, SleepTimerReceiver::class.java).apply {
            action = ACTION_SLEEP_TIMER_EXPIRED
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelDurationSleepTimerInternal() {
        alarmManager.cancel(createSleepTimerPendingIntent())
    }

    private fun setDurationSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimers()
            return
        }
        endOfTrackTimerSongId = null
        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60_000L)
        val pendingIntent = createSleepTimerPendingIntent()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } else
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            Timber.tag(TAG).d("Sleep timer set from Wear for %d minutes", minutes)
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Exact alarm denied; using inexact sleep timer")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun setEndOfTrackSleepTimer(enabled: Boolean) {
        if (!enabled) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer disabled from Wear")
            return
        }
        cancelDurationSleepTimerInternal()
        val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
        if (currentSongId.isNullOrBlank()) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer ignored: no active song")
            return
        }
        endOfTrackTimerSongId = currentSongId
        Timber.tag(TAG).d("End-of-track timer set from Wear for mediaId=%s", currentSongId)
    }

    private fun cancelSleepTimers() {
        cancelDurationSleepTimerInternal()
        endOfTrackTimerSongId = null
        Timber.tag(TAG).d("Sleep timers cancelled from Wear")
    }

    private fun startTemporaryForegroundForCommand() {
        val notification = NotificationCompat.Builder(
            this,
            PixelMusicApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_processing_action))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(getOpenAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()
        // Pass mediaPlayback explicitly. On API 34+ a missing/mismatched type can throw
        // MissingForegroundServiceTypeException; ServiceCompat picks the right overload
        // per API level and matches the manifest-declared foregroundServiceType.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to promote service to foreground for external command")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            } catch (detachErr: Throwable) {
                Timber.tag(TAG).w(detachErr, "Failed to detach foreground after failed external command promotion")
            }
        }
    }

    private fun isServiceAlreadyForeground(): Boolean {
        val player = mediaSession?.player ?: return false
        return player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedTemporaryForegroundInOnCreate = temporaryForegroundStartedInOnCreate
        temporaryForegroundStartedInOnCreate = false
        val pendingMediaButtonForegroundStart = consumePendingMediaButtonForegroundStart()
        val forcedForegroundStart =
            intent?.getBooleanExtra(EXTRA_FORCE_FOREGROUND_ON_START, false) == true
        val isMediaButtonIntent = intent?.action == Intent.ACTION_MEDIA_BUTTON
        val needsTemporaryForeground = forcedForegroundStart ||
            pendingMediaButtonForegroundStart ||
            (isMediaButtonIntent &&
                !startedTemporaryForegroundInOnCreate &&
                !isServiceAlreadyForeground()) ||
            when (intent?.action) {
                PlayerActions.PLAY_PAUSE,
                PlayerActions.NEXT,
                PlayerActions.PREVIOUS,
                PlayerActions.FAVORITE,
                PlayerActions.PLAY_FROM_QUEUE,
                PlayerActions.SHUFFLE,
                PlayerActions.REPEAT -> true
                else -> false
            }
        if (needsTemporaryForeground && !startedTemporaryForegroundInOnCreate) {
            startTemporaryForegroundForCommand()
        }

        intent?.action?.let { action ->
            Timber.tag(TAG).d("onStartCommand widget action: %s", action)
            val player = mediaSession?.player ?: engine.masterPlayer
            when (action) {
                PlayerActions.PLAY_PAUSE -> {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    player.playWhenReady = !player.playWhenReady
                    requestWidgetFullUpdate(force = true)
                }
                PlayerActions.NEXT -> {
                    player.seekToNext()
                    requestWidgetFullUpdate(force = true)
                }
                PlayerActions.PREVIOUS -> {
                    player.seekToPrevious()
                    requestWidgetFullUpdate(force = true)
                }
                PlayerActions.FAVORITE -> {
                    val songId = player.currentMediaItem?.mediaId
                    if (!songId.isNullOrBlank()) {
                        serviceScope.launch {
                            val updatedFavorite = musicRepository.toggleFavoriteStatus(songId)
                            val allIds = getEquivalentSongIds(songId)
                            favoriteSongIds = if (updatedFavorite) {
                                favoriteSongIds + allIds
                            } else {
                                favoriteSongIds - allIds
                            }
                            mediaSession?.let { refreshMediaSessionUi(it, force = true) }
                            requestWidgetFullUpdate(force = true)
                        }
                    }
                }
                PlayerActions.PLAY_FROM_QUEUE -> {
                    val songId = intent.getLongExtra("song_id", -1L)
                    if (songId != -1L) {
                        val timeline = player.currentTimeline
                        if (!timeline.isEmpty) {
                            val window = Timeline.Window()
                            for (i in 0 until timeline.windowCount) {
                                timeline.getWindow(i, window)
                                if (window.mediaItem.mediaId.toLongOrNull() == songId) {
                                    player.seekTo(i, C.TIME_UNSET)
                                    player.prepare()
                                    player.play()
                                    break
                                }
                            }
                        }
                    }
                }
                PlayerActions.SHUFFLE -> {
                    val newState = !isManualShuffleEnabled
                    mediaSession?.let { session ->
                        updateManualShuffleState(session, enabled = newState, broadcast = true)
                    } ?: run {
                        // Fallback if session not ready
                        isManualShuffleEnabled = newState
                        requestWidgetFullUpdate(force = true)
                    }
                }
                PlayerActions.REPEAT -> {
                    val newMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = newMode
                    requestWidgetFullUpdate(force = true)
                }
                ACTION_SLEEP_TIMER_EXPIRED -> {
                    Timber.tag(TAG).d("Sleep timer expired action received. Pausing player.")
                    cancelDurationSleepTimerInternal()
                    player.pause()
                }
            }
        }
        val startCommandResult = super.onStartCommand(intent, flags, startId)
        if (needsTemporaryForeground) {
            val player = mediaSession?.player
            val isActivelyPlaying = player?.let {
                it.playWhenReady &&
                    it.playbackState != Player.STATE_IDLE &&
                    it.playbackState != Player.STATE_ENDED
            } == true
            if (!isActivelyPlaying) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return startCommandResult
    }

    private fun getNavidromeId(mediaItem: MediaItem?): String? = null
    private fun isNavidromeMediaItem(mediaItem: MediaItem?): Boolean = false
    private fun reportNavidromePlayback(state: String, mediaItem: MediaItem? = engine.masterPlayer.currentMediaItem) {}
    private var navidromePlaybackReportJob: Job? = null
    private fun startNavidromePlaybackReporting() {}
    private fun stopNavidromePlaybackReporting() {}

    // ==================== FIXED TELEMETRY HELPERS ====================

    /**
     * BUGFIX: PixelMusic's unified YouTube media IDs are "youtube_<11-char-video-id>"
     * (see MusicRepositoryImpl / PlaylistViewModel, which build Song.id as
     * "youtube_${youtubeId}"). MediaItemBuilder.build() sets mediaId = song.id directly, so
     * item.mediaId for almost every YouTube song is 19 characters, NOT the bare 11-char video
     * ID YouTube's /player and /api/stats endpoints require as `docid`.
     *
     * The old code only stripped nothing at all: it passed item.mediaId straight through to
     * YouTubeTelemetryManager, so a real ping looked like docid=youtube_dQw4w9WgXcQ instead of
     * docid=dQw4w9WgXcQ. That is not a valid YouTube video ID, so InnerTube's /player lookup for
     * tracking URLs silently fails and the fallback ping YouTube receives is rejected too -
     * explaining "history updates once out of 100 songs" (the rare hit being whatever path
     * happened to hand back a bare, unprefixed ID).
     *
     * item.mediaId survives MediaItem.buildUpon().setUri(...) (used when the youtube:// URI is
     * swapped for a resolved googlevideo.com stream URL during instant playback), so mediaId is
     * always the reliable source of truth here - we never try to parse a video ID out of the
     * CDN stream URL itself.
     */
    private fun canonicalYoutubeVideoId(item: androidx.media3.common.MediaItem): String? {
        val mediaId = item.mediaId
        val rawId = when {
            mediaId.startsWith("youtube_") -> mediaId.removePrefix("youtube_")
            isLikelyYoutubeVideoId(mediaId) -> mediaId
            else -> {
                val uri = item.localConfiguration?.uri
                val metaUri = item.mediaMetadata.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI")
                if (uri?.scheme == "youtube") {
                    uri.toString().substringAfter("youtube://").substringBefore('?')
                } else if (metaUri?.startsWith("youtube://") == true) {
                    metaUri.removePrefix("youtube://").substringBefore('?')
                } else {
                    null
                }
            }
        }
        return rawId?.takeIf { isLikelyYoutubeVideoId(it) }
    }

    /** YouTube video IDs are always exactly 11 URL-safe base64 characters. */
    private fun isLikelyYoutubeVideoId(candidate: String): Boolean =
        candidate.length == 11 && candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** True if this item is YouTube content at all (used to gate telemetry independent of ID format). */
    private fun isYoutubeMediaItem(item: androidx.media3.common.MediaItem): Boolean =
        item.mediaId.startsWith("youtube_") ||
            isLikelyYoutubeVideoId(item.mediaId) ||
            item.localConfiguration?.uri?.scheme == "youtube" ||
            item.localConfiguration?.uri?.toString()?.contains("googlevideo") == true

    /**
     * Opens (or re-opens) the remote YouTube telemetry session for whatever the player is
     * currently on.
     *
     * BUGFIX (history stops recording mid-session): `lastTelemetryVideoId` used to be latched
     * unconditionally, even when YouTubeTelemetryManager refused to start a session (telemetry
     * disabled, or no login cookie yet — very common on a cold start, where the player begins
     * before the cookie jar has been restored). Once latched, this method never called
     * onSongChanged() for that track again, so the song played to completion with no session at
     * all and nothing reached YouTube. onSongChanged() now reports whether it actually started,
     * and we only latch on success — so the very next 1s tick retries.
     */
    private fun ensureTelemetrySessionForCurrentItem(player: Player) {
        val item = player.currentMediaItem ?: return
        if (!isYoutubeMediaItem(item)) return

        // BUGFIX: use the canonical 11-char video ID, not item.mediaId, as the docid.
        val videoId = canonicalYoutubeVideoId(item)
        if (videoId == null) {
            Timber.tag(TAG).w("YouTube item with no extractable video ID: mediaId=%s", item.mediaId)
            return
        }

        val durationMs = if (player.duration != androidx.media3.common.C.TIME_UNSET && player.duration > 0) {
            player.duration
        } else {
            item.mediaMetadata.extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L) ?: 0L
        }

        // Re-arm whenever the latch disagrees with the manager's real state. The second half of
        // this condition is the self-healing watchdog: if a session was torn down by any path
        // that did not clear the latch, this notices on the next tick instead of staying silent
        // for the rest of the queue.
        if (lastTelemetryVideoId != videoId || !telemetryManager.hasActiveSessionFor(videoId)) {
            if (telemetryManager.onSongChanged(videoId, durationMs)) {
                lastTelemetryVideoId = videoId
            } else {
                lastTelemetryVideoId = null
            }
        }
    }

    private fun startTelemetryReporting() {
        telemetryJob?.cancel()
        val player = mediaSession?.player ?: engine.masterPlayer

        ensureTelemetrySessionForCurrentItem(player)
        if (player.currentMediaItem?.let { isYoutubeMediaItem(it) } == true) {
            telemetryManager.onPlaybackStateChanged(true)
        }

        telemetryJob = serviceScope.launch {
            while (isActive) {
                // BUGFIX (silent telemetry death): the body used to be able to throw straight
                // out of the coroutine (a Player accessor on a released instance, a listener
                // that raised). The loop then stayed dead until the next isPlaying edge, and if
                // playback never dipped to paused - a fully gapless queue - local listening
                // accumulation AND YouTube heartbeats stopped for the rest of the session while
                // songs kept playing. That is precisely the reported symptom. The tick is now
                // individually guarded so one bad tick can never end the loop.
                try {
                    val tickPlayer = mediaSession?.player ?: engine.masterPlayer
                    if (tickPlayer.isPlaying) {
                        val positionMs = tickPlayer.currentPosition.coerceAtLeast(0L)
                        listeningStatsTracker.onProgress(positionMs, true)

                        val item = tickPlayer.currentMediaItem
                        if (item != null && isYoutubeMediaItem(item)) {
                            // Watchdog: re-open the session if it went missing (expired cookie
                            // restored late, a torn-down session, a mid-queue transition that
                            // no listener reported).
                            ensureTelemetrySessionForCurrentItem(tickPlayer)
                            telemetryManager.onPlaybackStateChanged(true)

                            // BUGFIX: same canonical extraction for the progress/heartbeat loop.
                            if (canonicalYoutubeVideoId(item) != null) {
                                val durationMs = if (tickPlayer.duration != androidx.media3.common.C.TIME_UNSET && tickPlayer.duration > 0) {
                                    tickPlayer.duration
                                } else 0L
                                telemetryManager.updateProgress(positionMs, durationMs)
                            }
                        }

                        // Keep the local stats session pinned to the item that is really
                        // playing. ensureSession() is a no-op when they already agree, and
                        // repairs the session when a transition callback was missed.
                        syncLocalListeningStatsFromPlayer(tickPlayer)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "Telemetry tick failed; loop continues")
                }
                delay(1000L)
            }
        }
    }

    private fun stopTelemetryReporting(isPause: Boolean = false) {
        telemetryJob?.cancel()
        telemetryJob = null
        val player = mediaSession?.player ?: engine.masterPlayer
        listeningStatsTracker.onPlayStateChanged(false, player.currentPosition.coerceAtLeast(0L))
        telemetryManager.onPlaybackStateChanged(false)
        if (!isPause) {
            telemetryManager.stopTelemetry()
            lastTelemetryVideoId = null
        }
    }

    /**
     * Supervises [telemetryJob].
     *
     * BUGFIX (the reported "after playing some songs no history is recorded, only songs play"):
     * every driver of history — local listening accumulation, YouTube watchtime heartbeats, and
     * the state=ended ping that commits a play — hung off a single 1s coroutine that was created
     * only on an `isPlaying -> true` edge and destroyed on the opposite edge. Any path that
     * cancelled it without a matching restart (a cancelled serviceScope child, a crossfade
     * player swap arriving between the two edges, a throwing tick, a queue that never dips to
     * paused) silenced history for the rest of the session while audio kept playing, with no
     * recovery short of a full app restart.
     *
     * This watchdog makes that class of failure self-healing: while the player is actually
     * playing, the reporting loop is guaranteed to exist within one watchdog period.
     */
    private fun startTelemetryWatchdog() {
        telemetryWatchdogJob?.cancel()
        telemetryWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(TELEMETRY_WATCHDOG_INTERVAL_MS)
                try {
                    val player = mediaSession?.player ?: engine.masterPlayer
                    if (player.isPlaying && telemetryJob?.isActive != true) {
                        Timber.tag(TAG).w("Telemetry reporting loop was dead while playing — restarting")
                        startTelemetryReporting()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "Telemetry watchdog tick failed")
                }
            }
        }
    }

    // Prevent infinite re-resolve loops on permanent stream failures.
    private var streamRecoveryAttempts = 0
    private var lastStreamRecoveryMediaId: String? = null
    private var lastStreamRecoveryAtMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onVolumeChanged(volume: Float) {
            if (engine.isTransitionRunning()) return
            val expectedVolume = expectedReplayGainVolume
            if (expectedVolume != null && abs(expectedVolume - volume) < 0.001f) {
                expectedReplayGainVolume = null
                return
            }
            expectedReplayGainVolume = null
            if (volume > 0.001f) {
                userSelectedVolume = volume.coerceIn(0f, 1f)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PlaybackActivityTracker.setPlaybackActive(isPlaying)
            val player = mediaSession?.player ?: engine.masterPlayer
            Timber.tag(TAG).d("onIsPlayingChanged: $isPlaying. Duration: ${player.duration}, Seekable: ${player.isCurrentMediaItemSeekable}")
            syncLocalListeningStatsFromPlayer(player)
            
            val durationMs = if (player.duration != androidx.media3.common.C.TIME_UNSET && player.duration > 0) {
                player.duration
            } else {
                player.currentMediaItem?.mediaMetadata?.extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L) ?: 0L
            }
            scrobbleManager?.onPlayerStateChanged(isPlaying, player.currentMediaItem, durationMs)
            
            if (isPlaying) {
                reportNavidromePlayback("playing")
                startNavidromePlaybackReporting()
                startTelemetryReporting()
            } else {
                val state = if (player.playbackState == Player.STATE_ENDED) "stopped" else "paused"
                reportNavidromePlayback(state)
                stopNavidromePlaybackReporting()
                stopTelemetryReporting(isPause = true)
            }

            // Re-apply the last known RG volume immediately when resuming playback.
            // After a pause, ExoPlayer may reset the audio track volume internally,
            // causing a brief full-volume spike before the IO coroutine finishes.
            if (isPlaying && !engine.isTransitionRunning()) {
                lastAppliedReplayGainVolume?.let { setPlayerVolume(player, it) }
            }
            // Push state immediately so the watch can foreground PixelMusic before
            // system media surfaces take over.
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist(immediate = !isPlaying)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            when {
                playWhenReady -> clearHeadsetReconnectResume()
                !resumeOnHeadsetReconnectEnabled -> clearHeadsetReconnectResume()
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                    shouldResumeAfterHeadsetReconnect = true
                    lastNoisyPauseRealtimeMs = SystemClock.elapsedRealtime()
                    Timber.tag(TAG).d("Marked playback for headset reconnect resume")
                }
                else -> clearHeadsetReconnectResume()
            }
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            val canSeek = availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            val player = engine.masterPlayer
            Timber.tag(TAG).w("onAvailableCommandsChanged. Can Seek Command? $canSeek. IsSeekable? ${player.isCurrentMediaItemSeekable}. Duration: ${player.duration}")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.tag(TAG).d("Playback state changed: $playbackState")
            val player = mediaSession?.player ?: engine.masterPlayer
            if (playbackState == Player.STATE_ENDED) {
                listeningStatsTracker.finalizeCurrentSession()
                telemetryManager.stopTelemetry()
                lastTelemetryVideoId = null
                val mediaItem = player.currentMediaItem

                endOfTrackTimerSongId = null
                reportNavidromePlayback("stopped")
                stopNavidromePlaybackReporting()
                
                scrobbleManager?.onPlayerStateChanged(false, mediaItem, 0)
            } else {
                syncLocalListeningStatsFromPlayer(player)
                
                val durationMs = if (player.duration != androidx.media3.common.C.TIME_UNSET && player.duration > 0) {
                    player.duration
                } else {
                    player.currentMediaItem?.mediaMetadata?.extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L) ?: 0L
                }
                scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMediaItem, durationMs)
            }
            mediaSession?.let { refreshMediaSessionUi(it) }
            if (!isRestoringPlaybackSnapshot) {
                schedulePlaybackSnapshotPersist(immediate = playbackState == Player.STATE_IDLE)
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            requestWidgetFullUpdate(force = true)
            if (!isRestoringPlaybackSnapshot) {
                schedulePlaybackSnapshotPersist(immediate = timeline.isEmpty)
            }
            // Pre-fetch RG for the next track so the cache is warm before playback starts
            val player = engine.masterPlayer
            val nextIndex = player.nextMediaItemIndex
            if (nextIndex != androidx.media3.common.C.INDEX_UNSET) {
                runCatching { prefetchReplayGain(player.getMediaItemAt(nextIndex)) }
            }
            val activePlayer = mediaSession?.player ?: engine.masterPlayer
            if (activePlayer.shuffleModeEnabled && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                applyCurrentFirstShuffleOrder()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                val state = if (engine.masterPlayer.isPlaying) "playing" else "paused"
                reportNavidromePlayback(state)
            }

            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                val finishedItem = oldPosition.mediaItem
                if (isNavidromeMediaItem(finishedItem)) {
                    reportNavidromePlayback("stopped", finishedItem)
                }
            }

            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_SEEK
            ) {
                val currentItem = mediaSession?.player?.currentMediaItem
                val oldMediaId = oldPosition.mediaItem?.mediaId
                val newMediaId = newPosition.mediaItem?.mediaId
                if (oldMediaId != null && oldMediaId == newMediaId) {
                    // Same track (e.g. repeat, seek) — no IO needed, apply last known RG volume
                    // immediately to avoid a spike while the coroutine reads tags again.
                    lastAppliedReplayGainVolume?.let {
                        if (!engine.isTransitionRunning()) setPlayerVolume(engine.masterPlayer, it)
                    }
                } else {
                    // Different track — full recompute needed
                    applyReplayGain(currentItem)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaSession?.let { refreshMediaSessionUi(it, force = true) }
            this@MusicService.handleMediaItemTransition(mediaItem, reason)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Some devices/apps deliver title/artist/art after transition callback.
            // Force an immediate publish for real-time watch metadata.
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUiWithFollowUp(it) }
            grantArtworkPermissionsForCurrentSong()
            // Only recompute RG if the track actually changed — onMediaMetadataChanged
            // also fires on queue edits (add/remove) without a track change, which would
            // launch a redundant IO coroutine and cause a brief volume spike.
            val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId
            if (currentMediaId != null && currentMediaId != lastReplayGainMediaId) {
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            } else if (currentMediaId != null) {
                lastAppliedReplayGainVolume?.let {
                    if (!engine.isTransitionRunning()) setPlayerVolume(engine.masterPlayer, it)
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Timber.tag("MusicService")
                .d("playerListener.onShuffleModeEnabledChanged: $shuffleModeEnabled")
            clearLastShuffleTimelineCache()
            if (shuffleModeEnabled) {
                applyCurrentFirstShuffleOrder()
            }
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }



        override fun onRepeatModeChanged(repeatMode: Int) {
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Player error code=%s message=%s", error.errorCode, error.message)

            val player = mediaSession?.player ?: engine.masterPlayer
            val currentItem = player.currentMediaItem
            val currentUri = currentItem?.localConfiguration?.uri?.toString()
            // Lossless-source streams (Tidal/Qobuz/Deezer CDN links, deezer://, tidal-dash://,
            // flattened Apple files) expire or get evicted; they recover through the same
            // youtube:// re-resolve path, with the resolver's cached decision dropped first.
            val isLosslessStream = com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.isLosslessUri(currentUri)
            val isYoutube = currentUri?.startsWith("youtube://") == true ||
                currentUri?.contains("googlevideo.com") == true ||
                currentUri?.contains("saavncdn.com") == true ||
                currentUri?.contains("jiosaavn.com") == true ||
                currentItem?.mediaId?.startsWith("youtube_") == true ||
                isLosslessStream

            val mediaId = currentItem?.mediaId
            val now = android.os.SystemClock.elapsedRealtime()
            // BUGFIX: widened window from 15s to 30s. A recovery slower than 15s used to
            // reset streamRecoveryAttempts to 0 forever, so the escalation path was never reached.
            val isRepeatFailure = mediaId != null && mediaId == lastStreamRecoveryMediaId &&
                now - lastStreamRecoveryAtMs < 30_000L
            val effectiveAttempt = if (isRepeatFailure) streamRecoveryAttempts + 1 else 0

            // Invalidate any stale resolved stream so the next attempt re-fetches a fresh URL.
            if (!currentUri.isNullOrBlank()) {
                engine.invalidateResolvedUri(currentUri)
                val youtubeVideoId: String? = when {
                    currentUri.startsWith("youtube://") -> currentUri.removePrefix("youtube://")
                    currentUri.contains("googlevideo.com") || currentUri.contains("saavncdn.com") || currentUri.contains("jiosaavn.com") || isLosslessStream -> {
                        val fromPrefix = currentItem.mediaId
                            .takeIf { it.startsWith("youtube_") }
                            ?.removePrefix("youtube_")
                        // Fallback: if mediaId is a bare 11-char video ID, use it directly
                        val id = fromPrefix ?: currentItem.mediaId.takeIf { it.length == 11 && !it.contains("_") }
                        id?.also { engine.invalidateResolvedUri("youtube://$it") }
                    }
                    else -> null
                }
                if (youtubeVideoId != null && isLosslessStream) {
                    // Drop the resolver's cached decision so the re-resolve walks the source chain
                    // again (fresh CDN link / next source) instead of replaying the dead stream.
                    com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.clearResolved(youtubeVideoId)
                    com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.invalidateStreamCache(youtubeVideoId)
                }
                if (youtubeVideoId != null && effectiveAttempt >= 1) {
                    com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.invalidateStreamCache(youtubeVideoId)
                    serviceScope.launch {
                        try {
                            com.unshoo.pixelmusic.utils.potoken.BotGuardTokenGenerator.invalidatePlayerToken(youtubeVideoId)
                        } catch (_: Exception) {}
                    }
                }
            }

            val isNetworkish = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
                (error.cause is java.io.IOException)

            if (isYoutube || isNetworkish) {
                if (isRepeatFailure) {
                    streamRecoveryAttempts += 1
                } else {
                    streamRecoveryAttempts = 0
                    lastStreamRecoveryMediaId = mediaId
                }
                lastStreamRecoveryAtMs = now

                serviceScope.launch {
                    try {
                        if (streamRecoveryAttempts >= 3) {
                            Timber.tag(TAG).w("Stream recovery exhausted for %s; skipping", mediaId)
                            withContext(Dispatchers.Main.immediate) {
                                streamRecoveryAttempts = 0
                                lastStreamRecoveryMediaId = null
                                if (player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem()
                                    player.prepare()
                                    player.play()
                                }
                                // BUGFIX: last track in queue used to silently halt.
                                // Now we at least prepare() so the user can retry manually.
                            }
                            return@launch
                        }

                        val position = player.currentPosition.coerceAtLeast(0L)
                        val rawItem = currentItem ?: return@launch
                        val rawUriString = rawItem.localConfiguration?.uri?.toString().orEmpty()
                        // BUGFIX: also handle bare 11-char mediaIds for googlevideo remap
                        val rawIsLossless = com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.isLosslessUri(rawUriString)
                        val ytVideoId = rawItem.mediaId
                            .takeIf { it.startsWith("youtube_") }
                            ?.removePrefix("youtube_")
                            ?: rawItem.mediaId.takeIf {
                                (rawUriString.contains("googlevideo.com") || rawIsLossless) && it.length == 11 && !it.contains("_")
                            }
                        val item = if (
                            (rawUriString.contains("googlevideo.com") || rawIsLossless) && !ytVideoId.isNullOrBlank()
                        ) {
                            rawItem.buildUpon()
                                .setUri(android.net.Uri.parse("youtube://$ytVideoId"))
                                .build()
                        } else {
                            rawItem
                        }
                        val resolved = engine.preResolveForPlayback(item)
                        withContext(Dispatchers.Main.immediate) {
                            val master = engine.masterPlayer
                            val wasPlaying = master.playWhenReady
                            val index = master.currentMediaItemIndex
                            if (index != androidx.media3.common.C.INDEX_UNSET) {
                                master.replaceMediaItem(index, resolved)
                                master.seekTo(index, position)
                            } else if (master.mediaItemCount > 0) {
                                val matchIndex = (0 until master.mediaItemCount).firstOrNull { master.getMediaItemAt(it).mediaId == resolved.mediaId } ?: 0
                                master.replaceMediaItem(matchIndex, resolved)
                                master.seekTo(matchIndex, position)
                            } else {
                                master.setMediaItem(resolved, position)
                            }
                            master.prepare()
                            master.playWhenReady = wasPlaying
                            if (wasPlaying) {
                                master.play()
                            }
                        }
                        Timber.tag(TAG).i("Recovered from player error by re-resolving stream")
                    } catch (recover: Exception) {
                        Timber.tag(TAG).e(recover, "In-place stream recovery failed (attempt $streamRecoveryAttempts)")
                        withContext(Dispatchers.Main.immediate) {
                            try {
                                if (streamRecoveryAttempts >= 2) {
                                    Timber.tag(TAG).w("Recovery exhausted after $streamRecoveryAttempts attempts; skipping to next")
                                    streamRecoveryAttempts = 0
                                    lastStreamRecoveryMediaId = null
                                    if (player.hasNextMediaItem()) {
                                        player.seekToNextMediaItem()
                                        player.prepare()
                                        player.play()
                                    }
                                } else {
                                    // BUGFIX: "will retry on next error" was a dead end because
                                    // nothing called prepare(). ExoPlayer in STATE_IDLE never
                                    // fires another error. Call prepare() so the retry cycle works.
                                    streamRecoveryAttempts++
                                    Timber.tag(TAG).w("Recovery attempt $streamRecoveryAttempts failed; preparing for retry")
                                    player.prepare()
                                    player.playWhenReady = true
                                }
                            } catch (skipErr: Exception) {
                                Timber.tag(TAG).e(skipErr, "Skip-after-error also failed")
                            }
                        }
                    }
                }
            } else {
                // BUGFIX: non-youtube/non-network errors were a silent halt — no recovery
                // attempted at all. At minimum, try prepare() so the user can tap play again.
                Timber.tag(TAG).w("Non-network error (code=%s); attempting prepare() recovery", error.errorCode)
                serviceScope.launch {
                    withContext(Dispatchers.Main.immediate) {
                        try {
                            player.prepare()
                            player.playWhenReady = true
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "prepare() recovery for non-network error also failed")
                        }
                    }
                }
            }
        }
    }

    private fun handleMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val player = mediaSession?.player ?: engine.masterPlayer
        syncLocalListeningStatsFromPlayer(player, forceNewSession = true)
        grantArtworkPermissionsForCurrentSong(mediaItem)
        // Reset recovery counters — a successful transition clears any per-song blacklist.
        streamRecoveryAttempts = 0
        lastStreamRecoveryMediaId = null
        
        val durationMs = if (player.duration != androidx.media3.common.C.TIME_UNSET && player.duration > 0) {
            player.duration
        } else {
            mediaItem?.mediaMetadata?.extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, 0L) ?: 0L
        }
        scrobbleManager?.onPlayerStateChanged(player.isPlaying, mediaItem, durationMs)
        
        if (isNavidromeMediaItem(mediaItem)) {
            reportNavidromePlayback("starting")
            if (engine.masterPlayer.isPlaying) {
                startNavidromePlaybackReporting()
            }
        } else {
            stopNavidromePlaybackReporting()
        }

        val eotTargetSongId = endOfTrackTimerSongId
        if (!eotTargetSongId.isNullOrBlank()) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val previousSongId = engine.masterPlayer.run {
                    if (previousMediaItemIndex != C.INDEX_UNSET) {
                        runCatching { getMediaItemAt(previousMediaItemIndex).mediaId }.getOrNull()
                    } else {
                        null
                    }
                }
                if (previousSongId == eotTargetSongId) {
                    endOfTrackTimerSongId = null
                    engine.masterPlayer.seekTo(0L)
                    engine.masterPlayer.pause()
                    Timber.tag(TAG).d("Paused playback at end of track from Wear timer")
                }
            } else if (mediaItem?.mediaId != eotTargetSongId) {
                endOfTrackTimerSongId = null
                Timber.tag(TAG).d("Cleared end-of-track timer after manual track change")
            }
        }
        applyReplayGain(mediaSession?.player?.currentMediaItem)
        // Pre-fetch RG for the track after this one so it's cached when needed
        val nextIndex = engine.masterPlayer.nextMediaItemIndex
        if (nextIndex != androidx.media3.common.C.INDEX_UNSET) {
            runCatching { prefetchReplayGain(engine.masterPlayer.getMediaItemAt(nextIndex)) }
        }
        // BUG 3 FIX: Force an immediate widget update (not debounced) on track transition
        // so album art and song info appear without the 300-800ms blank period.
        requestWidgetFullUpdate(force = true)
        mediaSession?.let { refreshMediaSessionUi(it, force = true) }
        schedulePlaybackSnapshotPersist(immediate = true)

        // BUG 4 FIX: Ensure new telemetry session starts on track transition (vital for gapless auto-transitions)
        // BUG 4b FIX (YT Music sync): player.isPlaying is false while the new item is still
        // buffering (STATE_BUFFERING), which is common right after a transition to a track
        // whose stream wasn't pre-resolved/cached yet. Gating solely on isPlaying meant
        // telemetry was stopped for the old track here but never restarted for the new one;
        // it only had a chance to recover later via onIsPlayingChanged(true) - and if that
        // edge never cleanly fires (e.g. isPlaying was already true and never dipped to
        // false around a near-gapless transition), YouTube Music's "now playing"/history
        // sync would silently stop updating after the first track of a session. playWhenReady
        // reflects intent to play regardless of buffering state, so include it too.
        lastTelemetryVideoId = null
        telemetryManager.stopTelemetry()
        if (player.isPlaying || player.playWhenReady) {
            startTelemetryReporting()
        }
    }

    /**
     * Applies ReplayGain volume normalization to the current track.
     * Reads RG tags from the file and adjusts player.volume accordingly.
     */
    private fun applyReplayGain(mediaItem: MediaItem?) {
        replayGainJob?.cancel()
        replayGainRequestToken += 1
        val requestToken = replayGainRequestToken

        if (mediaItem == null) {
            return
        }

        if (!replayGainEnabled) {
            pendingReplayGainVolume = null
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
            return
        }

        val mediaId = mediaItem.mediaId
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)

        if (filePath.isNullOrBlank()) {
            Timber.tag(TAG).d("ReplayGain: No file path for track, keeping user-selected volume")
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
            return
        }

        val useAlbumGain = replayGainUseAlbumGain

        // Apply the last known RG volume immediately so there is no full-volume spike
        // while the IO coroutine reads the tags for the new track.
        if (!engine.isTransitionRunning()) {
            lastAppliedReplayGainVolume?.let { setPlayerVolume(engine.masterPlayer, it) }
        }

        // Read ReplayGain tags on IO thread to avoid blocking main
        replayGainJob = serviceScope.launch {
            val rgValues = withContext(Dispatchers.IO) {
                replayGainManager.readReplayGain(filePath)
            }

            if (requestToken != replayGainRequestToken) {
                return@launch
            }

            val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId
            if (currentMediaId != mediaId) {
                Timber.tag(TAG).d("ReplayGain: Ignoring stale result for mediaId=%s", mediaId)
                return@launch
            }

            val volume = replayGainManager.getVolumeMultiplier(
                rgValues,
                useAlbumGain = useAlbumGain
            )

            if (engine.isTransitionRunning()) {
                // Store for application after transition completes.
                // Also pass to engine so the crossfade loop ends at the correct RG
                // volume instead of hard-coding 1f, preventing the audible jump.
                pendingReplayGainVolume = volume
                engine.incomingTrackReplayGainVolume = volume
                Timber.tag(TAG).d("ReplayGain: Stored pending volume=%.2f for %s (transition running)",
                    volume, mediaItem.mediaMetadata.title
                )
            } else {
                pendingReplayGainVolume = null
                engine.incomingTrackReplayGainVolume = null
                lastAppliedReplayGainVolume = volume
                lastReplayGainMediaId = mediaId
                setPlayerVolume(engine.masterPlayer, volume)
                Timber.tag(TAG).d("ReplayGain: Applied volume=%.2f for %s",
                    volume, mediaItem.mediaMetadata.title
                )
            }
        }
    }

    /**
     * Returns the cached ReplayGain volume for a media item if already computed, or null.
     * Does NOT trigger an IO read — only reads from the in-memory cache.
     */
    private fun getCachedReplayGainVolume(mediaItem: MediaItem?): Float? {
        if (!replayGainEnabled || mediaItem == null) return null
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return null
        if (filePath.isBlank()) return null
        val cached = replayGainManager.getCachedReplayGain(filePath) ?: return null
        return replayGainManager.getVolumeMultiplier(cached, useAlbumGain = replayGainUseAlbumGain)
    }

    /**
     * Pre-fetches ReplayGain tags for a media item into the cache without applying the volume.
     * Called on queue changes and track transitions so the cache is warm by the time
     * applyReplayGain() runs, avoiding the 1-2s JNI read delay on playback start.
     */
    private fun prefetchReplayGain(mediaItem: MediaItem?) {
        if (!replayGainEnabled || mediaItem == null) return
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return
        if (filePath.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            replayGainManager.readReplayGain(filePath)
        }
    }

    private fun setPlayerVolume(player: Player, volume: Float) {
        val safeVolume = if (volume <= 0.001f) {
            if (userSelectedVolume > 0.05f) userSelectedVolume else 1f
        } else {
            volume.coerceIn(0.01f, 1f)
        }
        val clampedVolume = safeVolume.coerceIn(0.01f, 1f)
        expectedReplayGainVolume = clampedVolume
        player.volume = clampedVolume
    }

    private fun onTransitionFinished() {
        val player = engine.masterPlayer
        val pending = pendingReplayGainVolume
        pendingReplayGainVolume = null

        if (!replayGainEnabled) {
            setPlayerVolume(player, userSelectedVolume)
            Timber.tag(TAG).d("ReplayGain: Transition finished, RG disabled — restored userSelectedVolume=%.2f", userSelectedVolume)
            return
        }

        if (pending != null) {
            // The crossfade loop ramps to this value; apply it now as the stable post-fade volume.
            // Also update lastAppliedReplayGainVolume so any subsequent onPositionDiscontinuity
            // (REASON_AUTO_TRANSITION fires right after crossfade ends) uses this value
            // immediately instead of launching a new IO coroutine and causing a spike.
            lastAppliedReplayGainVolume = pending
            setPlayerVolume(player, pending)
            Timber.tag(TAG).d("ReplayGain: Transition finished, applied pending volume=%.2f", pending)
        } else {
            // No pending volume was computed during transition, trigger full computation
            applyReplayGain(mediaSession?.player?.currentMediaItem)
            Timber.tag(TAG).d("ReplayGain: Transition finished, no pending volume — triggering full recomputation")
        }
    }

    private fun initializeCastWearSync() {
        val sessionManager = runCatching {
            CastContext.getSharedInstance(this).sessionManager
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "CastContext unavailable; skipping cast wear sync setup")
            return
        }
        castSessionManager = sessionManager

        val remoteCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
                (mediaSession?.player as? CastMediaSessionPlayer)?.notifyStateChanged()
            }

            override fun onMetadataUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
                (mediaSession?.player as? CastMediaSessionPlayer)?.notifyStateChanged()
            }

            override fun onQueueStatusUpdated() {
                syncCastListeningStatsFromRemote()
                requestWidgetFullUpdate(force = false)
                (mediaSession?.player as? CastMediaSessionPlayer)?.notifyStateChanged()
            }

            override fun onPreloadStatusUpdated() {
                requestWidgetFullUpdate(force = false)
                (mediaSession?.player as? CastMediaSessionPlayer)?.notifyStateChanged()
            }
        }
        castRemoteClientCallback = remoteCallback

        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                attachCastRemoteClient(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                attachCastRemoteClient(session)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                if (observedCastSession === session) {
                    attachCastRemoteClient(null)
                } else {
                    requestWidgetFullUpdate(force = true)
                }
            }

            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionSuspended(session: CastSession, reason: Int) = requestWidgetFullUpdate(force = true)
        }
        castSessionManagerListener = sessionListener
        runCatching {
            sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to register Cast session listener")
        }

        attachCastRemoteClient(sessionManager.currentCastSession)
    }

    private fun attachCastRemoteClient(session: CastSession?) {
        val newClient = session?.remoteMediaClient
        if (
            observedCastSession === session &&
            observedCastRemoteClient === newClient &&
            (session == null || newClient != null)
        ) return

        observedCastRemoteClient?.let { oldClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { oldClient.unregisterCallback(callback) }
            }
        }

        observedCastSession = session
        observedCastRemoteClient = newClient
        if (newClient != null || session == null) {
            castRemoteAttachRetryJob?.cancel()
            castRemoteAttachRetryJob = null
        }
        if (session != null) {
            val castPlayer = CastMediaSessionPlayer(engine.masterPlayer)
            publishMediaSessionPlayer(castPlayer, "Casting active")

            session.remoteMediaClient?.let { remoteClient ->
                castRemoteClientCallback?.let { callback ->
                    runCatching { remoteClient.registerCallback(callback) }
                }
                remoteClient.requestStatus()
                syncCastListeningStatsFromRemote()
            } ?: run {
                activeCastStatsOccurrenceId = null
                listeningStatsTracker.onPlaybackStopped()
                // Some Cast SDK versions publish onSessionStarted just before
                // remoteMediaClient is attached. Retry for a bounded period instead of leaving
                // the session permanently connected but uncontrollable.
                if (castRemoteAttachRetryJob?.isActive != true) {
                    castRemoteAttachRetryJob = serviceScope.launch {
                        repeat(CAST_REMOTE_CLIENT_ATTACH_RETRIES) {
                            delay(CAST_REMOTE_CLIENT_ATTACH_RETRY_MS)
                            if (observedCastSession !== session) return@launch
                            if (session.remoteMediaClient != null) {
                                attachCastRemoteClient(session)
                                return@launch
                            }
                        }
                        Timber.tag(TAG).w("Cast remote client did not attach before retry timeout")
                    }
                }
            }
        } else {
            publishMediaSessionPlayer(engine.masterPlayer, "Casting stopped")
            activeCastStatsOccurrenceId = null
            listeningStatsTracker.onPlaybackStopped()
        }
        requestWidgetFullUpdate(force = true)
    }

    private inner class CastMediaSessionPlayer(
        localPlayer: Player
    ) : ForwardingPlayer(localPlayer) {

        // Stable singleton sentinels so that getIndexOfPeriod/getUidOfPeriod work
        // correctly across repeated Timeline instances. Using `Any()` per-call breaks
        // equality checks inside Media3 because each construction yields a new object.
        private val windowUid = Object()
        private val periodUid = Object()

        private val listeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()

        // Track last-notified state to avoid firing expensive transition callbacks on every
        // Cast status poll (every ~1 s) when only position changed.
        private var lastNotifiedMediaId: String? = null
        private var lastNotifiedIsPlaying: Boolean? = null

        override fun addListener(listener: Player.Listener) {
            // Do not register on the local delegate. Local ExoPlayer events are unrelated while
            // Cast is active and previously leaked through ForwardingPlayer, overwriting remote
            // metadata/state in MediaSession and causing visible UI jumps.
            listeners.addIfAbsent(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners.remove(listener)
        }

        fun notifyStateChanged() {
            val currentItem = getCurrentMediaItem()
            val isPlaying = isPlaying()
            val playbackState = getPlaybackState()

            val mediaIdChanged = currentItem?.mediaId != lastNotifiedMediaId
            val playingChanged = isPlaying != lastNotifiedIsPlaying

            if (!mediaIdChanged && !playingChanged) {
                // Only position/buffering changed — nothing worth re-notifying listeners about.
                return
            }

            lastNotifiedMediaId = currentItem?.mediaId
            lastNotifiedIsPlaying = isPlaying

            val timeline = getCurrentTimeline()
            listeners.forEach { listener ->
                runCatching {
                    if (mediaIdChanged) {
                        listener.onMediaItemTransition(
                            currentItem,
                            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                        )
                        listener.onTimelineChanged(
                            timeline,
                            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
                        )
                    }
                    if (playingChanged) {
                        listener.onIsPlayingChanged(isPlaying)
                        listener.onPlaybackStateChanged(playbackState)
                    }
                }
            }
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val remote = resolveCastRemoteSnapshot() ?: return null
            val metadata = MediaMetadata.Builder()
                .setTitle(remote.title)
                .setArtist(remote.artist)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(remote.artworkUri)
                .build()
            return MediaItem.Builder()
                .setMediaId(remote.songId ?: "")
                .setMediaMetadata(metadata)
                .build()
        }

        override fun getPlayWhenReady(): Boolean {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.isPlaying ?: false
        }

        override fun isPlaying(): Boolean {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.isPlaying ?: false
        }

        override fun getPlaybackState(): Int {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return if (remoteSnapshot != null) Player.STATE_READY else Player.STATE_IDLE
        }

        override fun getCurrentPosition(): Long {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.currentPositionMs ?: 0L
        }

        override fun getDuration(): Long {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.totalDurationMs ?: 0L
        }

        override fun getBufferedPosition(): Long {
            return getCurrentPosition()
        }

        override fun getBufferedPercentage(): Int {
            return 100
        }

        override fun getRepeatMode(): Int {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.repeatMode ?: Player.REPEAT_MODE_OFF
        }

        override fun getShuffleModeEnabled(): Boolean {
            val remoteSnapshot = resolveCastRemoteSnapshot()
            return remoteSnapshot?.isShuffleEnabled ?: false
        }

        override fun getCurrentTimeline(): Timeline {
            val item = getCurrentMediaItem() ?: return Timeline.EMPTY
            val durationUs = getDuration() * 1000L
            // Stable uid references are captured from the instance fields above.
            val capturedWindowUid = windowUid
            val capturedPeriodUid = periodUid
            return object : Timeline() {
                override fun getWindowCount(): Int = 1
                override fun getPeriodCount(): Int = 1
                override fun getWindow(
                    windowIndex: Int,
                    window: Window,
                    defaultPositionProjectionUs: Long
                ): Window {
                    window.set(
                        capturedWindowUid,
                        item,
                        null,
                        C.TIME_UNSET,
                        C.TIME_UNSET,
                        C.TIME_UNSET,
                        true,
                        false,
                        null,
                        0,
                        durationUs,
                        0,
                        0,
                        0
                    )
                    return window
                }
                override fun getPeriod(
                    periodIndex: Int,
                    period: Period,
                    setIds: Boolean
                ): Period {
                    period.set(
                        if (setIds) capturedPeriodUid else null,
                        if (setIds) capturedWindowUid else null,
                        0,
                        durationUs,
                        0
                    )
                    return period
                }
                override fun getIndexOfPeriod(uid: Any): Int =
                    if (uid === capturedPeriodUid) 0 else C.INDEX_UNSET
                override fun getUidOfPeriod(periodIndex: Int): Any = capturedPeriodUid
            }
        }

        override fun pause() {
            observedCastSession?.remoteMediaClient?.pause()
        }

        override fun play() {
            observedCastSession?.remoteMediaClient?.play()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        override fun setRepeatMode(repeatMode: Int) {
            val castRepeatMode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            observedCastSession?.remoteMediaClient?.queueSetRepeatMode(castRepeatMode, null)
        }

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            val castRepeatMode = if (shuffleModeEnabled) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            } else {
                when (getRepeatMode()) {
                    Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                    Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                    else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                }
            }
            observedCastSession?.remoteMediaClient?.queueSetRepeatMode(castRepeatMode, null)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            observedCastSession?.remoteMediaClient?.seek(positionMs)
        }

        override fun seekTo(positionMs: Long) {
            observedCastSession?.remoteMediaClient?.seek(positionMs)
        }

        override fun stop() {
            observedCastSession?.remoteMediaClient?.stop()
        }

        override fun seekToNext() {
            observedCastSession?.remoteMediaClient?.queueNext(null)
        }

        override fun seekToPrevious() {
            observedCastSession?.remoteMediaClient?.queuePrev(null)
        }
    }

    private fun stopCastWearSync() {
        castRemoteAttachRetryJob?.cancel()
        castRemoteAttachRetryJob = null
        observedCastRemoteClient?.let { remoteClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { remoteClient.unregisterCallback(callback) }
            }
        }
        observedCastSession = null
        observedCastRemoteClient = null

        val listener = castSessionManagerListener
        val manager = castSessionManager
        if (listener != null && manager != null) {
            runCatching { manager.removeSessionManagerListener(listener, CastSession::class.java) }
                .onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to remove Cast session listener")
                }
        }
        castSessionManagerListener = null
        castRemoteClientCallback = null
        castSessionManager = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val allowBackground = keepPlayingInBackground

        if (!allowBackground) {
            stopPlaybackAndUnload(
                reason = "task_removed_background_disabled"
            )
            return
        }

        if (player == null || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            stopPlaybackAndUnload(
                reason = "task_removed_idle_or_ended",
                preservePlaybackSnapshot = false
            )
            return
        }

        //keep playback service and notification retained when paused with items in queue
        Timber.tag(TAG).i(
            "Task removed with active media items (count=%d, playWhenReady=%b). Preserving background service.",
            player.mediaItemCount,
            player.playWhenReady
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (isServiceDestroyed) null else mediaSession

    override fun onDestroy() {
        isServiceDestroyed = true
        PlaybackActivityTracker.setPlaybackActive(false)
        telemetryManager.destroy()
        listeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)
        reportNavidromePlayback("stopped")
        stopNavidromePlaybackReporting()
        playbackSnapshotPersistJob?.cancel()
        mediaSessionButtonRefreshJob?.cancel()
        followUpMediaSessionUiRefreshJob?.cancel()
        followUpWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob?.cancel()
        stopCastWearSync()
        unregisterHeadsetReconnectMonitor()
        wearStatePublisher.clearState()
        replayGainJob?.cancel()
        scrobbleManager?.destroy()
        scrobbleManager = null

        // Detach YouTube radio-mode auto-queue and stream-URL preloader
        AutoQueueManager.detach(engine.masterPlayer)
        QueuePreloadManager.detach(engine.masterPlayer)

        engine.removePlayerSwapListener(playerSwapListener)
        engine.removeNextPlayerPreparedListener(nextPlayerPreparedListener)
        engine.removeTransitionDisplayPlayerListener(transitionDisplayPlayerListener)
        engine.removeTransitionFinishedListener(transitionFinishedListener)
        mediaSession?.player?.removeListener(playerListener)
        engine.masterPlayer.removeListener(playerListener)

        mediaSession?.run {
            release()
            mediaSession = null
        }
        engine.setOnPlayerAboutToBeReleasedListener {}
        engine.release()
        controller.release()
        serviceScope.cancel()
        Thread.currentThread().setUncaughtExceptionHandler(previousMainThreadExceptionHandler)
        previousMainThreadExceptionHandler = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Release in-process bitmap caches when the system signals memory pressure.
        // MusicService holds up to ~220 KB in cachedWidgetArtBytes and another copy
        // inside lastWidgetPlayerInfo.albumArtBitmapData. Under moderate-to-critical
        // pressure these are safe to drop — the next widget update cycle will reload them.
        //
        // Raw int value used instead of deprecated ComponentCallbacks2 constant:
        //   TRIM_MEMORY_RUNNING_LOW = 10
        // This single threshold covers all higher-severity levels too
        // (TRIM_MEMORY_BACKGROUND = 40, TRIM_MEMORY_COMPLETE = 80, etc.).
        if (level >= 10 /* TRIM_MEMORY_RUNNING_LOW */) {
            Timber.tag(TAG).d("onTrimMemory(level=%d): releasing widget bitmap caches", level)
            invalidateCachedWidgetArtwork()
            // Drop the stale PlayerInfo copy so its embedded ByteArray is GC-eligible.
            // The next processWidgetUpdateInternal() call will rebuild it from scratch.
            lastWidgetPlayerInfo = null
            // Sweep expired resolved-stream-URL entries so a long session doesn't
            // accumulate an unbounded cache. Purely memory hygiene: every read path
            // already re-validates expiry before trusting a cached URL.
            com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.pruneExpiredCaches()
        }
    }

    private fun registerHeadsetReconnectMonitor() {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!addedDevices.any(::isReconnectableHeadsetOutput)) return
                maybeResumeAfterHeadsetReconnect()
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        headsetReconnectCallback = callback
    }

    private fun unregisterHeadsetReconnectMonitor() {
        headsetReconnectCallback?.let { callback ->
            runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        }
        headsetReconnectCallback = null
        clearHeadsetReconnectResume()
    }

    private fun maybeResumeAfterHeadsetReconnect() {
        if (!resumeOnHeadsetReconnectEnabled || !shouldResumeAfterHeadsetReconnect) return

        val elapsedSinceNoisyPause = SystemClock.elapsedRealtime() - lastNoisyPauseRealtimeMs
        if (elapsedSinceNoisyPause > HEADSET_RECONNECT_RESUME_WINDOW_MS) {
            clearHeadsetReconnectResume()
            return
        }

        if (!hasReconnectableHeadsetOutput()) {
            return
        }

        val player = engine.masterPlayer
        if (
            player.currentMediaItem == null ||
            player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            clearHeadsetReconnectResume()
            return
        }

        Timber.tag(TAG).d("Resuming playback after headset reconnect")
        clearHeadsetReconnectResume()
        player.play()
    }

    private fun hasReconnectableHeadsetOutput(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any(::isReconnectableHeadsetOutput)
    }

    private fun isReconnectableHeadsetOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
    }

    private fun clearHeadsetReconnectResume() {
        shouldResumeAfterHeadsetReconnect = false
        lastNoisyPauseRealtimeMs = 0L
    }

    private fun schedulePlaybackSnapshotPersist(immediate: Boolean = false) {
        if (isPlaybackUnloadInProgress) {
            return
        }
        playbackSnapshotPersistJob?.cancel()
        playbackSnapshotPersistJob = serviceScope.launch {
            if (!immediate) {
                delay(PLAYBACK_SNAPSHOT_DEBOUNCE_MS)
            }
            persistPlaybackSnapshot()
        }
    }

    private suspend fun persistPlaybackSnapshot(playWhenReadyOverride: Boolean? = null) {
        if (isRestoringPlaybackSnapshot) return
        val snapshot = capturePlaybackSnapshot(playWhenReadyOverride)
        // BUGFIX: Never overwrite or remove the persisted DataStore snapshot on transient idle/empty states.
        // Doing so wipes the user's queue from DataStore on process teardown or restart.
        if (snapshot == null || snapshot.items.isEmpty()) {
            return
        }
        runCatching {
            userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to persist playback snapshot")
        }
    }

    private suspend fun capturePlaybackSnapshot(playWhenReadyOverride: Boolean? = null): PlaybackQueueSnapshot? =
        withContext(Dispatchers.Main.immediate) {
            capturePlaybackSnapshotFromPlayer(playWhenReadyOverride)
        }

    // Reuse cache for [capturePlaybackSnapshotFromPlayer]. Main-thread only: every read of the
    // player already has to happen on its application thread.
    private var cachedSnapshotSources: List<MediaItem> = emptyList()
    private var cachedSnapshotItems: List<PlaybackQueueItemSnapshot> = emptyList()

    /**
     * Per-item snapshot entries for the whole queue.
     *
     * The snapshot is persisted on every play/pause, state change and timeline event, but the queue
     * itself almost never changes between those. Rebuilding it meant allocating an entry plus several
     * strings for every song on the main thread each time (thousands of objects for a large queue).
     * Entries are a pure function of each [MediaItem], and a replaced item is a new instance, so when
     * every item is reference-identical to the last capture the previous entries are still exact.
     * Any difference (added, removed, reordered or replaced item) falls through to a full rebuild.
     */
    private fun snapshotItemsFor(player: Player, mediaItemCount: Int): List<PlaybackQueueItemSnapshot> {
        val cachedSources = cachedSnapshotSources
        if (cachedSources.size == mediaItemCount) {
            var unchanged = true
            for (index in 0 until mediaItemCount) {
                if (player.getMediaItemAt(index) !== cachedSources[index]) {
                    unchanged = false
                    break
                }
            }
            if (unchanged) return cachedSnapshotItems
        }

        val sources = ArrayList<MediaItem>(mediaItemCount)
        val items = ArrayList<PlaybackQueueItemSnapshot>(mediaItemCount)
        for (index in 0 until mediaItemCount) {
            val mediaItem = player.getMediaItemAt(index)
            sources.add(mediaItem)
            val metadata = mediaItem.mediaMetadata
            val rawContentUri = metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            val localUri = mediaItem.localConfiguration?.uri?.toString()
            val uri = when {
                !rawContentUri.isNullOrBlank() && !rawContentUri.startsWith("http") -> rawContentUri
                mediaItem.mediaId.startsWith("youtube_") -> "youtube://${mediaItem.mediaId.removePrefix("youtube_")}"
                mediaItem.mediaId.length == 11 && !mediaItem.mediaId.startsWith("external:") -> "youtube://${mediaItem.mediaId}"
                rawContentUri?.startsWith("youtube://") == true -> rawContentUri
                localUri?.startsWith("youtube://") == true -> localUri
                !rawContentUri.isNullOrBlank() -> rawContentUri
                else -> localUri
            }

            val finalUri = if (!uri.isNullOrBlank()) {
                uri
            } else if (mediaItem.mediaId.startsWith("youtube_")) {
                "youtube://${mediaItem.mediaId.removePrefix("youtube_")}"
            } else if (mediaItem.mediaId.length == 11 && !mediaItem.mediaId.startsWith("external:")) {
                "youtube://${mediaItem.mediaId}"
            } else {
                null
            }

            if (mediaItem.mediaId.isBlank() || finalUri.isNullOrBlank()) {
                continue
            }

            val durationMs = metadata.extras
                ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION)
                ?.takeIf { it > 0L }

            items.add(
                PlaybackQueueItemSnapshot(
                    mediaId = mediaItem.mediaId,
                    uri = finalUri,
                    title = metadata.title?.toString(),
                    artist = metadata.artist?.toString(),
                    albumTitle = metadata.albumTitle?.toString(),
                    artworkUri = resolveStoredArtworkUriString(metadata),
                    durationMs = durationMs,
                )
            )
        }

        cachedSnapshotSources = sources
        cachedSnapshotItems = items
        return items
    }

    private fun capturePlaybackSnapshotFromPlayer(
        playWhenReadyOverride: Boolean? = null
    ): PlaybackQueueSnapshot? {
        val player = engine.masterPlayer
        val mediaItemCount = player.mediaItemCount
        if (mediaItemCount <= 0) {
            cachedSnapshotSources = emptyList()
            cachedSnapshotItems = emptyList()
            return null
        }

        val snapshotItems = snapshotItemsFor(player, mediaItemCount)

        if (snapshotItems.isEmpty()) {
            return null
        }

        val currentMediaId = player.currentMediaItem?.mediaId
        val indexFromMediaId = currentMediaId
            ?.let { id -> snapshotItems.indexOfFirst { it.mediaId == id } }
            ?.takeIf { it >= 0 }

        val safeCurrentIndex = when {
            indexFromMediaId != null -> indexFromMediaId
            player.currentMediaItemIndex in snapshotItems.indices -> player.currentMediaItemIndex
            else -> 0
        }

        val safeRepeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL -> player.repeatMode
            else -> Player.REPEAT_MODE_OFF
        }

        return PlaybackQueueSnapshot(
            items = snapshotItems,
            currentMediaId = currentMediaId,
            currentIndex = safeCurrentIndex,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = playWhenReadyOverride ?: player.playWhenReady,
            repeatMode = safeRepeatMode,
            shuffleEnabled = isManualShuffleEnabled,
        )
    }

    private suspend fun restorePlaybackQueueSnapshotIfNeeded() {
        val alreadyHasQueue = withContext(Dispatchers.Main.immediate) {
            engine.masterPlayer.mediaItemCount > 0
        }
        if (alreadyHasQueue) return

        val snapshot = runCatching {
            userPreferencesRepository.getPlaybackQueueSnapshotOnce()
        }.getOrNull() ?: return

        if (snapshot.items.isEmpty()) {
            return
        }

        val restoredItems = snapshot.items.mapNotNull(::buildMediaItemFromSnapshot)
        if (restoredItems.isEmpty()) {
            return
        }

        val resolvedIndex = when {
            snapshot.currentIndex in restoredItems.indices -> snapshot.currentIndex
            !snapshot.currentMediaId.isNullOrBlank() -> {
                restoredItems.indexOfFirst { it.mediaId == snapshot.currentMediaId }
                    .takeIf { it >= 0 } ?: 0
            }
            else -> 0
        }

        val preparedItems = restoredItems.toMutableList()
        // Do not block player restoration on resolving the track's streaming URI from network.
        // Instead, restore immediately with the unresolved media item so the UI displays the song instantly.
        // The URI resolution is launched asynchronously to warm up the cache with fresh URLs.
        preparedItems.getOrNull(resolvedIndex)?.let { currentItem ->
            serviceScope.launch(Dispatchers.IO) {
                runCatching { engine.preResolveForPlayback(currentItem) }
            }
        }

        withContext(Dispatchers.Main.immediate) {
            val player = engine.masterPlayer
            if (player.mediaItemCount > 0) {
                return@withContext
            }

            val safeRepeatMode = when (snapshot.repeatMode) {
                Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE,
                Player.REPEAT_MODE_ALL -> snapshot.repeatMode
                else -> Player.REPEAT_MODE_OFF
            }

            isRestoringPlaybackSnapshot = true
            try {
                player.setMediaItems(
                    preparedItems,
                    resolvedIndex,
                    snapshot.currentPositionMs.coerceAtLeast(0L)
                )
                player.repeatMode = safeRepeatMode
                player.shuffleModeEnabled = false
                isManualShuffleEnabled = snapshot.shuffleEnabled
                // Paused restore: do NOT call player.prepare()!
                // setMediaItems() alone creates the timeline and populates currentMediaItem.
                // Delaying prepare() until playback is actually requested avoids loading
                // unresolved cloud streams in the background on cold start.
                player.playWhenReady = false
            } finally {
                isRestoringPlaybackSnapshot = false
            }
        }

        Timber.tag(TAG).i(
            "Restored playback snapshot: items=%d index=%d",
            restoredItems.size,
            snapshot.currentIndex
        )
        schedulePlaybackSnapshotPersist(immediate = true)

    }

    private fun buildMediaItemFromSnapshot(snapshotItem: PlaybackQueueItemSnapshot): MediaItem? {
        if (snapshotItem.mediaId.isBlank()) {
            return null
        }
        val uriStr = when {
            snapshotItem.uri.isNotBlank() -> snapshotItem.uri
            snapshotItem.mediaId.startsWith("youtube_") -> "youtube://${snapshotItem.mediaId.removePrefix("youtube_")}"
            snapshotItem.mediaId.length == 11 && !snapshotItem.mediaId.startsWith("external:") -> "youtube://${snapshotItem.mediaId}"
            snapshotItem.mediaId.startsWith("-15") -> "youtube://${snapshotItem.mediaId}"
            else -> snapshotItem.mediaId
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        snapshotItem.title?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setTitle(it) }
        snapshotItem.artist?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setArtist(it) }
        snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setAlbumTitle(it) }
        MediaItemBuilder.externalControllerArtworkUri(this, snapshotItem.artworkUri)
            ?.let { metadataBuilder.setArtworkUri(it) }

        val extras = Bundle().apply {
            putBoolean(
                MediaItemBuilder.EXTERNAL_EXTRA_FLAG,
                snapshotItem.mediaId.startsWith("external:")
            )
            putString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI, uriStr)
            snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM, it)
            }
            snapshotItem.artworkUri?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART, it)
            }
            snapshotItem.durationMs?.takeIf { it > 0L }?.let {
                putLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, it)
            }
        }
        metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(snapshotItem.mediaId)
            .setUri(MediaItemBuilder.playbackUri(uriStr))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val activeIcon = AppIconManager.getActiveLauncherIcon(this)
        val intent = Intent().apply {
            component = ComponentName(packageName, activeIcon.aliasClassName)
            action = WearIntents.ACTION_OPEN_PLAYER
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACTION_SHOW_PLAYER", true) // Signal to MainActivity to show the player
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- LÓGICA PARA ACTUALIZACIÓN DE WIDGETS Y DATOS ---
    private var debouncedWidgetUpdateJob: Job? = null
    private var followUpWidgetUpdateJob: Job? = null
    private var followUpMediaSessionUiRefreshJob: Job? = null
    private var mediaSessionButtonRefreshJob: Job? = null
    private var lastAppliedMediaButtonSignature: String? = null
    private val widgetStateDebounceMs = 300L

    private fun requestWidgetFullUpdate(force: Boolean = false) {
        debouncedWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob = serviceScope.launch {
            val debounceMs = if (force) {
                FORCED_WIDGET_STATE_DEBOUNCE_MS
            } else {
                widgetStateDebounceMs
            }
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            processWidgetUpdateInternal()
        }
    }

    private fun requestWidgetAndWearRefreshWithFollowUp() {
        requestWidgetFullUpdate(force = true)
        followUpWidgetUpdateJob?.cancel()
        followUpWidgetUpdateJob = serviceScope.launch {
            delay(250L)
            requestWidgetFullUpdate(force = true)
        }
    }

    private data class RemotePlaybackSnapshot(
        val occurrenceId: String,
        val songId: String?,
        val title: String,
        val artist: String,
        val artworkUri: Uri?,
        val isPlaying: Boolean,
        val currentPositionMs: Long,
        val totalDurationMs: Long,
        val repeatMode: Int,
        val isShuffleEnabled: Boolean,
    )

    private fun syncCastListeningStatsFromRemote() {
        val snapshot = resolveCastRemoteSnapshot() ?: return
        val songId = snapshot.songId?.takeIf { it.isNotBlank() }
        if (songId == null) {
            activeCastStatsOccurrenceId = null
            listeningStatsTracker.onPlaybackStopped()
            return
        }

        if (activeCastStatsOccurrenceId != snapshot.occurrenceId) {
            activeCastStatsOccurrenceId = snapshot.occurrenceId
            listeningStatsTracker.onTrackChanged(
                songId = songId,
                positionMs = snapshot.currentPositionMs,
                durationMs = snapshot.totalDurationMs,
                isPlaying = snapshot.isPlaying
            )
            return
        }

        listeningStatsTracker.ensureSession(
            songId = songId,
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.totalDurationMs,
            isPlaying = snapshot.isPlaying
        )
    }

    private fun resolveCastRemoteSnapshot(): RemotePlaybackSnapshot? {
        val remoteClient = observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient
            ?: return null

        val mediaStatus = remoteClient.mediaStatus ?: return null
        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_UNKNOWN) {
            return null
        }

        val currentItem = mediaStatus.getQueueItemById(mediaStatus.currentItemId)
        val mediaInfo = currentItem?.media ?: remoteClient.mediaInfo
        val metadata = mediaInfo?.metadata
        if (metadata == null && currentItem == null) {
            return null
        }

        val songId = currentItem
            ?.customData
            ?.optString("songId")
            ?.takeIf { it.isNotBlank() }
        val occurrenceId = currentItem
            ?.itemId
            ?.takeIf { it > 0 }
            ?.toString()
            ?: songId
            ?: mediaInfo?.contentId
            ?: return null

        val durationHintMs = currentItem
            ?.customData
            ?.optLong("durationHintMs", -1L)
            ?.takeIf { it > 0L }

        val streamDurationMs = remoteClient.streamDuration.takeIf { it > 0L }
        val effectiveDurationMs = (streamDurationMs ?: durationHintMs ?: 0L).coerceAtLeast(0L)
        val imageUri = metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
                ?.takeIf { it.isNotBlank() }?.toUri()

        val mappedRepeatMode = when (mediaStatus.queueRepeatMode) {
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
            MediaStatus.REPEAT_MODE_REPEAT_ALL,
            MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }

        return RemotePlaybackSnapshot(
            occurrenceId = occurrenceId,
            songId = songId,
            title = metadata?.getString(CastMediaMetadata.KEY_TITLE).orEmpty(),
            artist = metadata?.getString(CastMediaMetadata.KEY_ARTIST).orEmpty(),
            artworkUri = imageUri,
            isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            currentPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            totalDurationMs = effectiveDurationMs,
            repeatMode = mappedRepeatMode,
            isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
        )
    }

    private suspend fun resolveCurrentMediaIdForWear(): String? {
        val remoteSongId = resolveCastRemoteSnapshot()?.songId
        if (!remoteSongId.isNullOrBlank()) {
            return remoteSongId
        }
        val player = engine.masterPlayer
        return withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId }
    }

    private var lastWidgetPlayerInfo: PlayerInfo? = null

    private fun shouldUpdateWidget(old: PlayerInfo, new: PlayerInfo): Boolean {
        if (old.songTitle != new.songTitle) return true
        if (old.artistName != new.artistName) return true
        if (old.isPlaying != new.isPlaying) return true
        if (old.albumArtUri != new.albumArtUri) return true
        // Detect when artwork bytes arrive (null → non-null) or are cleared
        if ((old.albumArtBitmapData == null) != (new.albumArtBitmapData == null)) return true
        if (old.isFavorite != new.isFavorite) return true
        if (old.queue != new.queue) return true
        if (old.themeColors != new.themeColors) return true
        if (old.isShuffleEnabled != new.isShuffleEnabled) return true
        if (old.repeatMode != new.repeatMode) return true
        if (old.totalDurationMs != new.totalDurationMs) return true
        if (old.wearThemePalette != new.wearThemePalette) return true

        val drift = kotlin.math.abs(old.currentPositionMs - new.currentPositionMs)
        return drift > 3000L
    }

    private fun shouldPublishWearState(old: PlayerInfo, new: PlayerInfo): Boolean {
        return shouldUpdateWidget(old, new) || old.wearQueueRevision != new.wearQueueRevision
    }

    private suspend fun processWidgetUpdateInternal() {
        val playerInfo = buildPlayerInfo()
        val oldInfo = lastWidgetPlayerInfo

        val shouldUpdateWidgets = oldInfo == null || shouldUpdateWidget(oldInfo, playerInfo)
        val shouldPublishWear = oldInfo == null || shouldPublishWearState(oldInfo, playerInfo)

        if (shouldUpdateWidgets || shouldPublishWear) {
            lastWidgetPlayerInfo = playerInfo
        }

        if (shouldUpdateWidgets) {
            updateGlanceWidgets(playerInfo)
        }

        if (shouldPublishWear) {
            val currentMediaId = resolveCurrentMediaIdForWear()
            // Publish state to Wear OS watch
            wearStatePublisher.publishState(currentMediaId, playerInfo)
        }
    }

    private fun buildWearQueueRevision(
        timeline: Timeline,
        currentIndex: Int,
        currentMediaId: String?,
    ): String {
        val remoteClient = observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient
        val remoteStatus = remoteClient?.mediaStatus
        val remoteQueueItems = remoteStatus?.queueItems.orEmpty()
        if (remoteQueueItems.isNotEmpty()) {
            val remoteCurrentIndex = remoteQueueItems.indexOfFirst {
                it.itemId == remoteStatus?.currentItemId
            }.takeIf { it >= 0 } ?: 0
            val remoteTokens = remoteQueueItems.map { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
                    ?: item.media?.contentId
                    ?: item.itemId.toString()
            }
            return encodeWearQueueRevision(remoteTokens, remoteStatus?.currentItemId ?: 0)
        }

        if (timeline.isEmpty) {
            return currentMediaId.orEmpty()
        }

        val window = Timeline.Window()
        val tokens = buildList(timeline.windowCount) {
            for (index in 0 until timeline.windowCount) {
                timeline.getWindow(index, window)
                val mediaItem = window.mediaItem
                add(
                    mediaItem.mediaId.ifBlank {
                        mediaItem.localConfiguration?.uri?.toString()
                            ?: mediaItem.mediaMetadata.title?.toString()
                            ?: index.toString()
                    }
                )
            }
        }
        val safeCurrentIndex = currentIndex.coerceIn(0, (timeline.windowCount - 1).coerceAtLeast(0))
        return encodeWearQueueRevision(tokens, safeCurrentIndex)
    }

    private fun encodeWearQueueRevision(queueTokens: List<String>, currentIndex: Int): String {
        if (queueTokens.isEmpty()) return ""
        return buildString {
            append(currentIndex)
            append('|')
            queueTokens.forEachIndexed { index, token ->
                if (index > 0) append(',')
                append(token)
            }
        }.hashCode().toString()
    }

    private suspend fun buildPlayerInfo(): PlayerInfo {
        val player = engine.masterPlayer
        // Batch all main-thread reads into a single context switch (was 7 separate hops → 1)
        var currentItem: MediaItem? = null
        var isPlaying = false
        var repeatMode = Player.REPEAT_MODE_OFF
        var currentPosition = 0L
        var totalDuration = 0L
        var snapshotWindowIndex = 0
        var snapshotTimeline: Timeline = Timeline.EMPTY

        withContext(Dispatchers.Main) {
            currentItem = player.currentMediaItem
            isPlaying = player.isPlaying
            repeatMode = player.repeatMode
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0)
            snapshotWindowIndex = player.currentMediaItemIndex
            snapshotTimeline = player.currentTimeline
        }

        var shuffleEnabled = isManualShuffleEnabled // Manual shuffle for sync with PlayerViewModel

        var title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        var artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        var mediaId = currentItem?.mediaId
        var artworkUri = resolveWidgetArtworkUriCandidates(currentItem?.mediaMetadata).firstOrNull()
        var artworkData = currentItem?.mediaMetadata?.artworkData

        resolveCastRemoteSnapshot()?.let { remote ->
            if (remote.title.isNotBlank()) {
                title = remote.title
            }
            if (remote.artist.isNotBlank()) {
                artist = remote.artist
            }
            if (!remote.songId.isNullOrBlank()) {
                mediaId = remote.songId
            }
            if (remote.artworkUri != null) {
                artworkUri = remote.artworkUri
                artworkData = null
            }
            isPlaying = remote.isPlaying
            currentPosition = remote.currentPositionMs
            if (remote.totalDurationMs > 0L) {
                totalDuration = remote.totalDurationMs
            }
            repeatMode = remote.repeatMode
            shuffleEnabled = remote.isShuffleEnabled
        }

        val artworkCandidates = resolveWidgetArtworkUriCandidates(
            metadata = currentItem?.mediaMetadata,
            preferredArtworkUri = artworkUri,
        )
        val (artBytes, artUriString) = getAlbumArtForWidget(
            mediaId = mediaId,
            embeddedArt = artworkData,
            artUris = artworkCandidates,
        )

        // Merge theme preference reads into a single context switch
        data class ThemeParams(
            val playerTheme: String,
            val paletteStyle: AlbumArtPaletteStyle,
            val colorAccuracyLevel: Int,
            val colorPalette: String
        )
        val params = withContext(Dispatchers.IO) {
            ThemeParams(
                playerTheme = themePreferencesRepository.playerThemePreferenceFlow.first(),
                paletteStyle = AlbumArtPaletteStyle.fromStorageKey(themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey),
                colorAccuracyLevel = AlbumArtColorAccuracy.clamp(themePreferencesRepository.albumArtColorAccuracyFlow.first()),
                colorPalette = themePreferencesRepository.colorPalettePreferenceFlow.first()
            )
        }
        val playerTheme = params.playerTheme
        val paletteStyle = params.paletteStyle
        val colorAccuracyLevel = params.colorAccuracyLevel
        val colorPalette = params.colorPalette

        val schemePair: ColorSchemePair? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC ->
                ColorSchemePair(
                    light = dynamicLightColorScheme(applicationContext),
                    dark = dynamicDarkColorScheme(applicationContext)
                )
            playerTheme == ThemePreference.GLOBAL -> {
                if (colorPalette == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ColorSchemePair(
                        light = dynamicLightColorScheme(applicationContext),
                        dark = dynamicDarkColorScheme(applicationContext)
                    )
                } else {
                    val darkScheme = com.unshoo.pixelmusic.ui.theme.getStaticColorScheme(colorPalette, true)
                    val lightScheme = com.unshoo.pixelmusic.ui.theme.getStaticColorScheme(colorPalette, false)
                    ColorSchemePair(light = lightScheme, dark = darkScheme)
                }
            }
            artUriString != null ->
                // Skip heavy palette recomputation when art, style, and accuracy haven't changed
                if (
                    artUriString == cachedSchemeArtUri &&
                    paletteStyle == cachedSchemePaletteStyle &&
                    colorAccuracyLevel == cachedSchemeColorAccuracy
                ) {
                    cachedColorSchemePair
                } else {
                    colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = artUriString,
                        paletteStyle = paletteStyle,
                        colorAccuracyLevel = colorAccuracyLevel
                    ).also {
                        cachedSchemeArtUri = artUriString
                        cachedSchemePaletteStyle = paletteStyle
                        cachedSchemeColorAccuracy = colorAccuracyLevel
                        cachedColorSchemePair = it
                    }
                }
            else -> null
        }

        val widgetColors = schemePair?.let {
            WidgetThemeColors(
                lightSurfaceContainer = it.light.surfaceContainer.toArgb(),
                lightSurfaceContainerLowest = it.light.surfaceContainerLowest.toArgb(),
                lightSurfaceContainerLow = it.light.surfaceContainerLow.toArgb(),
                lightSurfaceContainerHigh = it.light.surfaceContainerHigh.toArgb(),
                lightSurfaceContainerHighest = it.light.surfaceContainerHighest.toArgb(),
                lightTitle = it.light.onSurface.toArgb(),
                lightArtist = it.light.onSurfaceVariant.toArgb(),
                lightPlayPauseBackground = it.light.primary.toArgb(),
                lightPlayPauseIcon = it.light.onPrimary.toArgb(),
                lightPrevNextBackground = it.light.onPrimary.toArgb(),
                lightPrevNextIcon = it.light.primary.toArgb(),
                
                darkSurfaceContainer = it.dark.surfaceContainer.toArgb(),
                darkSurfaceContainerLowest = it.dark.surfaceContainerLowest.toArgb(),
                darkSurfaceContainerLow = it.dark.surfaceContainerLow.toArgb(),
                darkSurfaceContainerHigh = it.dark.surfaceContainerHigh.toArgb(),
                darkSurfaceContainerHighest = it.dark.surfaceContainerHighest.toArgb(),
                darkTitle = it.dark.onSurface.toArgb(),
                darkArtist = it.dark.onSurfaceVariant.toArgb(),
                darkPlayPauseBackground = it.dark.primary.toArgb(),
                darkPlayPauseIcon = it.dark.onPrimary.toArgb(),
                darkPrevNextBackground = it.dark.onPrimary.toArgb(),
                darkPrevNextIcon = it.dark.primary.toArgb()
            )
        }
        val wearThemePalette = schemePair?.let { buildWearThemePalette(it.dark) }

        val isFavorite = isSongFavorite(mediaId)
        val wearQueueRevision = buildWearQueueRevision(
            timeline = snapshotTimeline,
            currentIndex = snapshotWindowIndex,
            currentMediaId = mediaId,
        )

        val queueItems = mutableListOf<com.unshoo.pixelmusic.data.model.QueueItem>()
        // Reuse snapshotTimeline / snapshotWindowIndex captured at the top — no extra main-thread hop
        if (!snapshotTimeline.isEmpty) {
            val window = Timeline.Window()

            // Empezar desde la siguiente canción en la cola
            val startIndex = if (snapshotWindowIndex + 1 < snapshotTimeline.windowCount) snapshotWindowIndex + 1 else 0

            // Limitar el número de elementos de la cola a 4
            val endIndex = (startIndex + 4).coerceAtMost(snapshotTimeline.windowCount)
            for (i in startIndex until endIndex) {
                snapshotTimeline.getWindow(i, window)
                val mediaItem = window.mediaItem
                val songId = mediaItem.mediaId.toLongOrNull()
                if (songId != null) {
                    val initialQueueArtworkUri = resolveWidgetArtworkUriCandidates(mediaItem.mediaMetadata)
                        .firstOrNull()
                    val queueArtworkUri = when {
                        initialQueueArtworkUri == null -> resolveRepositoryArtworkUri(mediaItem.mediaId)
                        initialQueueArtworkUri.scheme?.lowercase() == "content" &&
                            initialQueueArtworkUri.authority == "$packageName.provider" ->
                            resolveRepositoryArtworkUri(mediaItem.mediaId) ?: initialQueueArtworkUri
                        else -> initialQueueArtworkUri
                    }
                    queueItems.add(
                        com.unshoo.pixelmusic.data.model.QueueItem(
                            id = songId,
                            albumArtUri = queueArtworkUri?.toString()
                        )
                    )
                }
            }
        }

        return PlayerInfo(
            songTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            albumArtUri = artUriString,
            albumArtBitmapData = artBytes,
            currentPositionMs = currentPosition,
            totalDurationMs = totalDuration,
            isFavorite = isFavorite,
            queue = queueItems,
            themeColors = widgetColors,
            isShuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            wearThemePalette = wearThemePalette,
            wearQueueRevision = wearQueueRevision,
        )
    }

    // Color scheme cache: skip recomputation when art URI, palette style, and accuracy haven't changed
    private var cachedSchemeArtUri: String? = null
    private var cachedSchemePaletteStyle: AlbumArtPaletteStyle? = null
    private var cachedSchemeColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT
    private var cachedColorSchemePair: ColorSchemePair? = null
    private var cachedWidgetArtSourceKey: String? = null
    private var cachedWidgetArtResolvedUri: String? = null
    private var cachedWidgetArtBytes: ByteArray? = null
    private var cachedWidgetArtLoadFailureKey: String? = null
    private var cachedWidgetArtLoadFailureAtMs: Long = 0L

    private fun invalidateCachedWidgetArtwork() {
        cachedWidgetArtSourceKey = null
        cachedWidgetArtResolvedUri = null
        cachedWidgetArtBytes = null
        cachedWidgetArtLoadFailureKey = null
        cachedWidgetArtLoadFailureAtMs = 0L
    }

    private fun isCurrentWidgetArtworkBackedByTelegram(): Boolean {
        val currentItem = engine.masterPlayer.currentMediaItem ?: return false
        val metadata = currentItem.mediaMetadata
        val contentUriString = currentItem.localConfiguration?.uri?.toString()
            ?: metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
        val artworkUriString = resolveStoredArtworkUriString(metadata)
        return contentUriString?.startsWith("telegram://") == true ||
            artworkUriString?.startsWith("telegram_art://") == true
    }

    private suspend fun getAlbumArtForWidget(
        mediaId: String?,
        embeddedArt: ByteArray?,
        artUris: List<Uri>,
    ): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        // Try embedded art first — but fall through to URI loading if sanitization fails
        val sanitizedFromEmbedded = embeddedArt?.takeIf { it.isNotEmpty() }?.let { bytes ->
            runCatching {
                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                    data = bytes,
                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                )
            }.getOrNull()
        }
        val candidateUriStrings = LinkedHashSet<String>().apply {
            artUris.forEach { candidate ->
                candidate.toString()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.toList()
        val preferredUriString = candidateUriStrings.firstOrNull()
        val sourceKey = buildWidgetArtworkSourceKey(
            mediaId = mediaId,
            candidateUriStrings = candidateUriStrings,
        )

        if (sanitizedFromEmbedded != null) {
            cachedWidgetArtSourceKey = sourceKey
            cachedWidgetArtResolvedUri = preferredUriString
            cachedWidgetArtBytes = sanitizedFromEmbedded
            cachedWidgetArtLoadFailureKey = null
            cachedWidgetArtLoadFailureAtMs = 0L
            return@withContext sanitizedFromEmbedded to preferredUriString
        }

        if (sourceKey != null && sourceKey == cachedWidgetArtSourceKey && cachedWidgetArtBytes != null) {
            return@withContext cachedWidgetArtBytes to (cachedWidgetArtResolvedUri ?: preferredUriString)
        }
        if (sourceKey != null && sourceKey == cachedWidgetArtLoadFailureKey) {
            val failureAgeMs = SystemClock.elapsedRealtime() - cachedWidgetArtLoadFailureAtMs
            if (failureAgeMs < WIDGET_ART_FAILURE_RETRY_MS) {
                return@withContext null to preferredUriString
            }
        }

        val repositoryArtUriString = if (mediaId.isNullOrBlank()) {
            null
        } else {
            resolveRepositoryArtworkUri(mediaId)?.toString()
        }
        val resolvedUriStrings = LinkedHashSet<String>().apply {
            addAll(candidateUriStrings)
            repositoryArtUriString
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }

        for (candidateUriString in resolvedUriStrings) {
            val candidateUri = parseArtworkUriString(candidateUriString) ?: continue
            val loadedBytes = loadArtworkBytesForWidget(candidateUri)
            if (loadedBytes != null) {
                cachedWidgetArtSourceKey = sourceKey
                cachedWidgetArtResolvedUri = candidateUriString
                cachedWidgetArtBytes = loadedBytes
                cachedWidgetArtLoadFailureKey = null
                cachedWidgetArtLoadFailureAtMs = 0L
                return@withContext loadedBytes to candidateUriString
            }
        }

        cachedWidgetArtLoadFailureKey = sourceKey
        cachedWidgetArtLoadFailureAtMs = SystemClock.elapsedRealtime()
        return@withContext null to (repositoryArtUriString ?: preferredUriString)
    }

    private fun resolveStoredArtworkUriString(metadata: MediaMetadata?): String? {
        metadata ?: return null
        return metadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
            ?.takeIf { it.isNotBlank() }
            ?: metadata.artworkUri
                ?.toString()
                ?.takeIf { it.isNotBlank() }
    }

    private fun resolveWidgetArtworkUriCandidates(
        metadata: MediaMetadata?,
        preferredArtworkUri: Uri? = null,
    ): List<Uri> {
        val candidates = LinkedHashSet<String>()
        preferredArtworkUri
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(candidates::add)
        resolveStoredArtworkUriString(metadata)?.let(candidates::add)
        metadata?.artworkUri
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(candidates::add)
        return candidates.mapNotNull(::parseArtworkUriString)
    }

    private fun parseArtworkUriString(rawArtworkUri: String?): Uri? {
        if (rawArtworkUri.isNullOrBlank()) {
            return null
        }

        return MediaItemBuilder.artworkUri(rawArtworkUri)
            ?: if (rawArtworkUri.startsWith("/")) {
                Uri.fromFile(java.io.File(rawArtworkUri))
            } else {
                runCatching { rawArtworkUri.toUri() }.getOrNull()
            }
    }

    private fun buildWidgetArtworkSourceKey(
        mediaId: String?,
        candidateUriStrings: List<String>,
    ): String? {
        val normalizedMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (normalizedMediaId == null && candidateUriStrings.isEmpty()) {
            return null
        }
        return buildString {
            normalizedMediaId?.let {
                append("mediaId=")
                append(it)
            }
            if (candidateUriStrings.isNotEmpty()) {
                if (isNotEmpty()) append('|')
                append(candidateUriStrings.joinToString(separator = ","))
            }
        }
    }

    private fun resolveArtworkUri(metadata: MediaMetadata?): Uri? {
        metadata ?: return null
        metadata.artworkUri?.let { return it }
        val extrasUri = metadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return parseArtworkUriString(extrasUri)
    }

    private suspend fun resolveRepositoryArtworkUri(mediaId: String?): Uri? {
        val songId = mediaId?.takeIf { it.isNotBlank() } ?: return null
        val song = withContext(Dispatchers.IO) {
            musicRepository.getSong(songId).first()
        } ?: return null

        return MediaItemBuilder.artworkUri(song.albumArtUriString)
            ?: song.albumArtUriString
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    if (raw.startsWith("/")) Uri.fromFile(java.io.File(raw))
                    else runCatching { Uri.parse(raw) }.getOrNull()
                }
    }

    private suspend fun loadArtworkBytesForWidget(uri: Uri): ByteArray? {
        val uriString = uri.toString()
        val scheme = uri.scheme?.lowercase()
        val isLocalArtworkUri = com.unshoo.pixelmusic.utils.LocalArtworkUri.isLocalArtworkUri(uriString)
        return when {
            isLocalArtworkUri || scheme == "content" || scheme == "file" || scheme == "android.resource" -> {
                runCatching {
                    AlbumArtUtils.openArtworkInputStream(applicationContext, uri)?.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                                )
                            }
                    }
                }.getOrElse { error ->
                    Timber.tag(TAG).w(error, "Widget artwork read failed for local uri=%s", uri)
                    null
                }
            }
            scheme == "http" || scheme == "https" -> {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(uriString).openConnection() as? HttpURLConnection)
                        ?: return null
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 6_000
                    connection.instanceFollowRedirects = true
                    connection.doInput = true
                    connection.inputStream.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                                )
                            }
                    }
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Widget artwork read failed for remote uri=%s", uri)
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            else -> loadArtworkBytesViaCoil(uri)
        }
    }

    private suspend fun loadArtworkBytesViaCoil(uri: Uri): ByteArray? {
        val request = ImageRequest.Builder(applicationContext)
            .data(uri)
            .size(
                ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx,
                ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx,
            )
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()

        return runCatching {
            val drawable = applicationContext.imageLoader.execute(request).drawable ?: return@runCatching null
            val fallbackSizePx = ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx
            val bitmap = drawable.toBitmap(
                width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallbackSizePx,
                height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallbackSizePx,
                config = Bitmap.Config.ARGB_8888,
            )
            val encodedBytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                output.toByteArray()
            }
            ArtworkTransportSanitizer.sanitizeEncodedBytes(
                data = encodedBytes,
                config = ArtworkTransportSanitizer.WIDGET_CONFIG,
            )
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "Widget artwork read failed via Coil for uri=%s", uri)
            null
        }
    }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalRead += read
            if (totalRead > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) = withContext(Dispatchers.IO) {
        try {
            val glanceManager = GlanceAppWidgetManager(applicationContext)
            val widgetPlayerInfo = playerInfo.toWidgetTransportState()

            val glanceIds = glanceManager.getGlanceIds(PixelMusicGlanceWidget::class.java)
            glanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                PixelMusicGlanceWidget().update(applicationContext, id)
            }

            val barGlanceIds = glanceManager.getGlanceIds(BarWidget4x1::class.java)
            barGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                BarWidget4x1().update(applicationContext, id)
            }

            val controlGlanceIds = glanceManager.getGlanceIds(ControlWidget4x2::class.java)
            controlGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                ControlWidget4x2().update(applicationContext, id)
            }

            val gridGlanceIds = glanceManager.getGlanceIds(GridWidget2x2::class.java)
            gridGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                GridWidget2x2().update(applicationContext, id)
            }
            
            if (glanceIds.isNotEmpty() || barGlanceIds.isNotEmpty() || controlGlanceIds.isNotEmpty() || gridGlanceIds.isNotEmpty()) {
                Timber.tag(TAG)
                    .d("Widgets actualizados: ${playerInfo.songTitle} (Original: ${glanceIds.size}, Bar: ${barGlanceIds.size}, Control: ${controlGlanceIds.size})")
            } else {
                Timber.tag(TAG).w("No se encontraron widgets para actualizar")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al actualizar el widget")
        }
    }

    private fun PlayerInfo.toWidgetTransportState(): PlayerInfo {
        return copy(
            lyrics = null,
            isLoadingLyrics = false,
            queue = queue.take(WIDGET_QUEUE_PREVIEW_LIMIT),
            wearThemePalette = null,
            wearQueueRevision = "",
        )
    }

    private fun getEquivalentSongIds(songId: String?): Set<String> {
        val bare = songId?.removePrefix("youtube_")?.takeIf { it.isNotBlank() } ?: return emptySet()
        val unifiedId = runCatching { YouTubeIdUtils.toUnifiedYoutubeSongId(bare).toString() }.getOrNull()
        return setOfNotNull(songId, bare, "youtube_$bare", unifiedId)
    }

    private fun isSongFavoriteWithSet(songId: String?, idsSet: Set<String>): Boolean =
        getEquivalentSongIds(songId).any { it in idsSet }

    fun isSongFavorite(songId: String?): Boolean {
        return isSongFavoriteWithSet(songId, favoriteSongIds)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val playWhenReady = session.player.playWhenReady
        val playbackState = session.player.playbackState

        // Android 12+ (API 31+): Only request foreground when actively playing.
        // This prevents requesting foreground start when player is idle/ended.
        val shouldStartInForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startInForegroundRequired && playWhenReady
                    && playbackState != Player.STATE_IDLE
                    && playbackState != Player.STATE_ENDED
        } else {
            startInForegroundRequired
        }

        try {
            super.onUpdateNotification(session, shouldStartInForeground)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "onUpdateNotification suppressed: ${e.message}")
        }
    }

    override fun startForegroundService(serviceIntent: Intent?): ComponentName? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                super.startForegroundService(serviceIntent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.tag(TAG).w(
                    e,
                    "startForegroundService not allowed; ignoring redundant self-start request"
                )
                serviceIntent?.component ?: ComponentName(this, javaClass)
            } catch (e: BackgroundServiceStartNotAllowedException) {
                Timber.tag(TAG).w(
                    e,
                    "startForegroundService blocked (app in background); ignoring self-start request"
                )
                serviceIntent?.component ?: ComponentName(this, javaClass)
            }
        }
        return super.startForegroundService(serviceIntent)
    }

    private fun applyMediaSessionUiButtons(session: MediaSession, signature: String) {
        if (mediaSession !== session) return
        val buttons = buildMediaButtonPreferences(session)
        session.setMediaButtonPreferences(buttons)
        session.setCustomLayout(buttons)
        lastAppliedMediaButtonSignature = signature
    }

    private fun refreshMediaSessionUi(session: MediaSession, force: Boolean = false) {
        val pendingSignature = buildMediaButtonPreferencesSignature(session)
        if (!force && pendingSignature == lastAppliedMediaButtonSignature) {
            return
        }

        mediaSessionButtonRefreshJob?.cancel()

        // When forced or running directly on the Main thread where the signature has changed,
        // apply immediately and synchronously so track transitions and user action toggles
        // update the media notification / PlaybackStateCompat before the next frame is rendered.
        if (force || (Looper.myLooper() == Looper.getMainLooper() && pendingSignature != lastAppliedMediaButtonSignature)) {
            applyMediaSessionUiButtons(session, pendingSignature)
            return
        }

        mediaSessionButtonRefreshJob = serviceScope.launch {
            if (!force) {
                delay(MEDIA_SESSION_BUTTON_DEBOUNCE_MS)
            }
            if (mediaSession !== session) {
                return@launch
            }

            val latestSignature = buildMediaButtonPreferencesSignature(session)
            if (latestSignature == lastAppliedMediaButtonSignature) {
                return@launch
            }

            applyMediaSessionUiButtons(session, latestSignature)
        }
    }

    private fun closeNotificationPlayer() {
        stopPlaybackAndUnload(
            reason = "notification_close_button",
            preservePlaybackSnapshot = false
        )
    }

    private fun stopPlaybackAndUnload(
        reason: String,
        preservePlaybackSnapshot: Boolean = true,
    ) {
        Timber.tag(TAG).d(
            "Stopping playback and unloading service. reason=%s",
            reason
        )
        isPlaybackUnloadInProgress = true
        followUpMediaSessionUiRefreshJob?.cancel()
        mediaSessionButtonRefreshJob?.cancel()
        followUpWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob?.cancel()
        playbackSnapshotPersistJob?.cancel()

        val sessionToRelease = mediaSession
        val player = sessionToRelease?.player ?: engine.masterPlayer

        clearHeadsetReconnectResume()
        cancelDurationSleepTimerInternal()
        endOfTrackTimerSongId = null

        if (preservePlaybackSnapshot) {
            persistPlaybackSnapshotBlocking()
        } else {
            clearPlaybackSnapshotBlocking()
        }

        listeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        requestWidgetFullUpdate(force = true)
        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    private fun persistPlaybackSnapshotBlocking() {
        val snapshot = capturePlaybackSnapshotFromPlayer(playWhenReadyOverride = false)
        if (snapshot != null && snapshot.items.isNotEmpty()) {
            writePlaybackSnapshotBlocking(snapshot)
        } else {
            clearPlaybackSnapshotBlocking()
        }
    }

    private fun clearPlaybackSnapshotBlocking() {
        writePlaybackSnapshotBlocking(null)
    }

    private fun writePlaybackSnapshotBlocking(snapshot: PlaybackQueueSnapshot?) {
        // BUGFIX (freeze after long idle): this used to be an unbounded runBlocking, called from
        // the main thread during service teardown (onTaskRemoved/onDestroy). If the DataStore
        // write ever stalled - e.g. right after the process/disk resumes from a long idle period
        // - the main thread would hang forever with no way to recover short of a force-stop.
        // withTimeoutOrNull() bounds the worst case instead of leaving it open-ended.
        // BUGFIX (task-specific timeouts): 2s, matching ListeningStatsTracker's DB timeout - a
        // healthy local write is fast; DB timeouts should be the shortest tier in this app.
        runCatching {
            runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                    userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
                } ?: Timber.tag(TAG).w("Playback snapshot write timed out during unload")
            }
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "Failed to persist playback snapshot during unload")
        }
    }

    private fun refreshMediaSessionUiWithFollowUp(
        session: MediaSession,
        delayMs: Long = 250L
    ) {
        refreshMediaSessionUi(session, force = true)
        followUpMediaSessionUiRefreshJob?.cancel()
        followUpMediaSessionUiRefreshJob = serviceScope.launch {
            delay(delayMs)
            if (mediaSession === session) {
                refreshMediaSessionUi(session)
            }
        }
    }

    private fun updateManualShuffleState(
        session: MediaSession,
        enabled: Boolean,
        broadcast: Boolean
    ) {
        val changed = isManualShuffleEnabled != enabled
        isManualShuffleEnabled = enabled
        session.player.shuffleModeEnabled = enabled
        
        if (persistentShuffleEnabled) {
            serviceScope.launch {
                userPreferencesRepository.setShuffleOn(enabled)
            }
        }

        if (broadcast && changed) {
            val args = Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
            }
            session.broadcastCustomCommand(
                SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle.EMPTY),
                args
            )
        }
        refreshMediaSessionUi(session)
        requestWidgetFullUpdate(force = true)
    }

    private var isApplyingShuffleOrder = false
    private var lastShuffleTimelineSize = -1
    private var lastShuffleFirstItemId: String? = null
    private var lastShuffleMediaItemIds: List<String>? = null

    private fun clearLastShuffleTimelineCache() {
        lastShuffleTimelineSize = -1
        lastShuffleFirstItemId = null
        lastShuffleMediaItemIds = null
    }

    private fun getMediaItemIds(player: Player): List<String> {
        val count = player.mediaItemCount
        return List(count) { idx -> player.getMediaItemAt(idx).mediaId }
    }

    private fun applyCurrentFirstShuffleOrder() {
        if (isApplyingShuffleOrder) return
        val player = mediaSession?.player ?: engine.masterPlayer
        val count = player.mediaItemCount
        if (count <= 1) return

        isApplyingShuffleOrder = true
        try {
            (player as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
                androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(count)
            )
        } finally {
            isApplyingShuffleOrder = false
        }
    }

    private fun setCurrentSongFavoriteState(
        session: MediaSession,
        targetFavoriteState: Boolean
    ): ListenableFuture<SessionResult> {
        val songId = session.player.currentMediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))

        val isCurrentlyFavorite = isSongFavorite(songId)
        if (isCurrentlyFavorite == targetFavoriteState) {
            refreshMediaSessionUi(session, force = true)
            requestWidgetFullUpdate(force = true)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        val allIds = getEquivalentSongIds(songId)
        favoriteSongIds = if (targetFavoriteState) {
            favoriteSongIds + allIds
        } else {
            favoriteSongIds - allIds
        }

        refreshMediaSessionUi(session, force = true)
        requestWidgetFullUpdate(force = true)

        serviceScope.launch {
            Timber.tag("MusicService")
                .d("Applying favorite=$targetFavoriteState for songId: $songId")
            musicRepository.setFavoriteStatus(songId, targetFavoriteState)
            refreshMediaSessionUi(session, force = true)
            requestWidgetFullUpdate(force = true)
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private data class ContextQueueResolution(
        val mediaItems: MutableList<MediaItem>,
        val startIndex: Int
    )

    private fun controllerKey(controller: MediaSession.ControllerInfo): String {
        return "${controller.packageName}:${controller.uid}"
    }

    private fun rememberLastBrowsedParent(controller: MediaSession.ControllerInfo, parentId: String) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)] = parentId
        }
    }

    private fun getLastBrowsedParent(controller: MediaSession.ControllerInfo): String? {
        return synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)]
        }
    }

    private fun clearLastBrowsedParent(controller: MediaSession.ControllerInfo) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent.remove(controllerKey(controller))
        }
    }

    private suspend fun resolveContextQueueForRequestedItem(
        requestedItem: MediaItem,
        controller: MediaSession.ControllerInfo
    ): ContextQueueResolution? {
        var contextType = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_TYPE_EXTRA)
        var contextId = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_ID_EXTRA)

        if (contextType.isNullOrBlank()) {
            val parentId = requestedItem.mediaMetadata.extras
                ?.getString(AutoMediaBrowseTree.CONTEXT_PARENT_ID_EXTRA)
                ?: getLastBrowsedParent(controller)
            val parentContext = parentId?.let { resolveAutoContextFromParentId(it) }
            contextType = parentContext?.first
            contextId = parentContext?.second
        }

        if (contextType.isNullOrBlank()) {
            return null
        }

        val queueSongs = autoMediaBrowseTree.getSongsForContext(contextType, contextId)
        if (queueSongs.isEmpty()) {
            return null
        }

        val startIndex = queueSongs.indexOfFirst { it.id == requestedItem.mediaId }
        if (startIndex < 0) {
            return null
        }

        val queueMediaItems = queueSongs.map { song ->
            MediaItemBuilder.buildForExternalController(this, song)
        }.toMutableList()

        return ContextQueueResolution(
            mediaItems = queueMediaItems,
            startIndex = startIndex
        )
    }

    private suspend fun resolveMediaItemsByIds(
        requestedItems: List<MediaItem>
    ): TrustedMediaItemsResolution {
        val songIds = requestedItems.map { it.mediaId }
        val songs = musicRepository.getSongsByIdsOnce(songIds)
        val songMap = songs.associateBy { it.id }

        return resolveMediaItemsWithTrustedArtworkGrants(requestedItems) { mediaId ->
            songMap[mediaId]?.let { song ->
                MediaItemBuilder.buildForExternalController(this, song)
            }
        }
    }

    private fun grantArtworkUriPermissions(
        targetPackage: String,
        mediaItems: List<MediaItem>
    ) {
        if (targetPackage.isBlank()) return

        val providerAuthority = "$packageName.provider"
        val artworkAuthority = "$packageName.artwork"
        mediaItems.forEach { mediaItem ->
            val artworkUri = resolveArtworkUri(mediaItem.mediaMetadata) ?: return@forEach
            val authority = artworkUri.authority
            if (artworkUri.scheme?.lowercase() != "content" || 
                (authority != providerAuthority && authority != artworkAuthority)
            ) {
                return@forEach
            }

            runCatching {
                grantUriPermission(targetPackage, artworkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error ->
                Timber.tag(TAG).w(
                    error,
                    "Failed to grant artwork URI permission to package=%s uri=%s",
                    targetPackage,
                    artworkUri
                )
            }
        }
    }

    private fun grantArtworkPermissionsForCurrentSong(mediaItem: MediaItem? = null) {
        val currentItem = mediaItem ?: mediaSession?.player?.currentMediaItem ?: return
        val targetPackages = LinkedHashSet<String>().apply {
            add("com.android.systemui")
            add("com.miui.systemui")
            add("com.miui.miwallpaper")
            add("com.miui.aod")
            add("com.miui.player")
            add("com.xiaomi.xmsf")
            add("com.miui.notification")
            add("com.miui.home")
            add("com.miui.personalassistant")
            add("com.android.settings")
            // Other vendor system UIs for general robustness:
            add("com.huawei.systemui")
            add("com.huawei.aod")
            add("com.huawei.android.totemweather")
            add("com.samsung.android.app.aodservice")
            add("com.sec.android.systemui")
            add("com.oneplus.aod")
            add("com.oppo.aod")
            mediaSession?.connectedControllers?.forEach { controller ->
                if (controller.packageName.isNotBlank()) {
                    add(controller.packageName)
                }
            }
        }
        targetPackages.forEach { pkg ->
            grantArtworkUriPermissions(pkg, listOf(currentItem))
        }
    }

    private fun resolveAutoContextFromParentId(parentId: String): Pair<String, String?>? {
        return when {
            parentId == AutoMediaBrowseTree.RECENT_ID -> AUTO_CONTEXT_RECENT to null
            parentId == AutoMediaBrowseTree.FAVORITES_ID -> AUTO_CONTEXT_FAVORITES to null
            parentId == AutoMediaBrowseTree.SONGS_ID -> AUTO_CONTEXT_ALL_SONGS to null
            parentId.startsWith(AutoMediaBrowseTree.ALBUM_PREFIX) -> {
                AUTO_CONTEXT_ALBUM to parentId.removePrefix(AutoMediaBrowseTree.ALBUM_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.ARTIST_PREFIX) -> {
                AUTO_CONTEXT_ARTIST to parentId.removePrefix(AutoMediaBrowseTree.ARTIST_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.PLAYLIST_PREFIX) -> {
                AUTO_CONTEXT_PLAYLIST to parentId.removePrefix(AutoMediaBrowseTree.PLAYLIST_PREFIX)
            }
            else -> null
        }
    }

    private fun buildMediaButtonPreferencesSignature(session: MediaSession): String {
        val player = session.player
        return buildString {
            append(player.currentMediaItem?.mediaId.orEmpty())
            append('|')
            append(isSongFavorite(player.currentMediaItem?.mediaId))
            append('|')
            append(isManualShuffleEnabled)
            append('|')
            append(player.repeatMode)
        }
    }

    private fun buildMediaButtonPreferences(session: MediaSession): List<CommandButton> {
        val player = session.player
        val songId = player.currentMediaItem?.mediaId
        val isFavorite = isSongFavorite(songId)
        val likeButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isFavorite) "Unlike" else "Like")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val shuffleOn = isManualShuffleEnabled
        val shuffleCommandAction = if (shuffleOn) {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF
        } else {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON
        }
        val shuffleButton = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName("Shuffle")
            .setSessionCommand(SessionCommand(shuffleCommandAction, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val repeatButton = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName("Repeat")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.rounded_close_24)
            .setDisplayName(getString(R.string.close_notification_player))
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        // Let Media3 provide the primary previous/play-next transport buttons from player
        // commands instead of advertising custom back/forward slots here. When custom
        // SLOT_BACK/SLOT_FORWARD buttons are present, Media3 strips the legacy
        // ACTION_SKIP_TO_PREVIOUS/NEXT flags from PlaybackStateCompat, which causes some
        // OEM compact system players (including ColorOS Control Center) to gray out skip.
        return listOf(likeButton, closeButton, shuffleButton, repeatButton)
    }

    // ------------------------
    // Counted Play Controls
    // ------------------------
    fun startCountedPlay(count: Int) {
        val player = engine.masterPlayer
        val currentItem = player.currentMediaItem ?: return

        stopCountedPlay()  // reset previous

        countedPlayTarget = count
        countedPlayCount = 1
        countedOriginalId = currentItem.mediaId
        countedPlayActive = true

        // Force repeat-one
        player.repeatMode = Player.REPEAT_MODE_ONE

        val listener = object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!countedPlayActive) return

                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    countedPlayCount++

                    if (countedPlayCount > countedPlayTarget) {
                        player.pause()
                        stopCountedPlay()
                        return
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!countedPlayActive) return

                // If user manually changes the song -> cancel
                if (mediaItem?.mediaId != countedOriginalId) {
                    stopCountedPlay()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // User explicitly changed repeat mode while counted play is active:
                // cancel counted play and accept the new mode instead of fighting back.
                if (countedPlayActive && repeatMode != Player.REPEAT_MODE_ONE) {
                    stopCountedPlay(restoreRepeatMode = false)
                }
            }
        }

        countedPlayListener = listener
        player.addListener(listener)
    }

    fun stopCountedPlay(restoreRepeatMode: Boolean = true) {
        if (!countedPlayActive) return

        countedPlayActive = false
        countedPlayTarget = 0
        countedPlayCount = 0
        countedOriginalId = null

        countedPlayListener?.let {
            engine.masterPlayer.removeListener(it)
        }
        countedPlayListener = null

        // Restore normal repeat mode (OFF) only when not triggered by a user repeat-mode change
        if (restoreRepeatMode) {
            engine.masterPlayer.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Bridges a suspend block into a [ListenableFuture] for Media3 callback methods.
     */
    private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
