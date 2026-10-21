package com.unshoo.pixelmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryStateHolder
import com.unshoo.pixelmusic.presentation.viewmodel.ThemeStateHolder
import com.unshoo.pixelmusic.utils.AlbumArtCacheManager
import com.unshoo.pixelmusic.utils.AlbumArtUtils
import com.unshoo.pixelmusic.utils.CrashHandler
import com.unshoo.pixelmusic.utils.AppLocaleManager
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.utils.MediaMetadataRetrieverPool
import com.unshoo.pixelmusic.utils.potoken.BotGuardTokenGenerator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelMusicApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.unshoo.pixelmusic.data.image.TelegramCoilFetcher.Factory>



    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.unshoo.pixelmusic.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var telegramRepository: dagger.Lazy<TelegramRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var advancedPerformanceDiagnosticsController: dagger.Lazy<com.unshoo.pixelmusic.data.diagnostics.AdvancedPerformanceDiagnosticsController>

    // BUGFIX (slow first playback): ExoCache.cache is a `by lazy` SimpleCache. SimpleCache's
    // constructor synchronously scans/reconciles its on-disk index - cheap when the cache is
    // small, but it grows slower as more audio gets cached over time. Because ExoCache is a
    // Hilt @Singleton, whichever thread touches `.cache` FIRST pays that cost. Previously nothing
    // touched it until MusicService.onCreate() -> DualPlayerEngine.initialize() -> buildPlayer()
    // did, on the MAIN THREAD, which is exactly what MediaController connection (and therefore
    // the very first "tap a song, wait for it to start" moment) is blocked behind. Warming it
    // here, off the main thread, as early as possible means that by the time MusicService needs
    // it, the lazy value is already resolved (or nearly done), instead of a synchronous main-
    // thread disk scan sitting directly in the cold-start-to-first-playback path.
    @Inject
    lateinit var exoCache: dagger.Lazy<com.unshoo.pixelmusic.data.remote.youtube.ExoCache>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // BUGFIX (startup strategy): warm-up work (ExoCache, BotGuard) previously ran on the shared
    // Dispatchers.IO pool, which runs at normal thread priority and can compete with other IO
    // work (including things the UI is actively waiting on) right when the app is trying to
    // render its first frame and become interactive. This dedicated single-thread dispatcher is
    // created at THREAD_PRIORITY_BACKGROUND once, up front - not toggled per-task on a shared
    // pooled thread, which would risk leaking a lowered priority onto unrelated later work on
    // that same reused thread. Android's scheduler is then free to prioritize the UI thread
    // while this still makes forward progress as early as possible in the background.
    private val warmUpDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelMusic-WarmUp").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }.asCoroutineDispatcher()
    private val warmUpScope = CoroutineScope(SupervisorJob() + warmUpDispatcher)
    private var exoCacheWarmUpJob: kotlinx.coroutines.Job? = null
    private var botGuardWarmUpJob: kotlinx.coroutines.Job? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelmusic_music_channel"
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
        }

        // BUGFIX (startup strategy - don't waste work the user won't benefit from): if the user
        // opens the app and leaves again before warm-up finished, keep spending CPU/battery on
        // it in the background for no benefit. Cancelling here is safe for actual playback: real
        // stream-resolution/BotGuard-minting triggered by an actual tap or the foreground service
        // runs on its own scopes (MusicService's serviceScope, YouTubeTelemetryManager's own
        // scope, DualPlayerEngine's resolve jobs) - never on warmUpScope - so this can never
        // cancel or interrupt playback that's actually in progress.
        override fun onStop(owner: LifecycleOwner) {
            exoCacheWarmUpJob?.takeIf { it.isActive }?.cancel()
            botGuardWarmUpJob?.takeIf { it.isActive }?.cancel()
        }
    }

    /**
     * Suspends until the main thread's [android.os.MessageQueue] reports idle (no pending
     * frame/input/message work queued), or [timeoutMs] elapses - whichever comes first.
     *
     * Used to schedule main-thread-affecting warm-up work (WebView creation) into a genuine
     * scheduling gap instead of racing it against active scrolling or animation. Falls back to
     * proceeding anyway after the timeout so a busy main thread (e.g. a long benchmark run)
     * never permanently blocks warm-up.
     */
    private suspend fun awaitMainThreadIdle(timeoutMs: Long = 4_000L) {
        try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    val idleHandler = android.os.MessageQueue.IdleHandler {
                        if (cont.isActive) {
                            cont.resumeWith(Result.success(Unit))
                        }
                        false // one-shot: remove after firing
                    }
                    android.os.Looper.getMainLooper().queue.addIdleHandler(idleHandler)
                    cont.invokeOnCancellation {
                        android.os.Looper.getMainLooper().queue.removeIdleHandler(idleHandler)
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal - proceed with pre-warm regardless.
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        advancedPerformanceDiagnosticsController.get().start(startupScope)

        // BUGFIX (startup jank): MediaItemBuilder/BotGuardTokenGenerator
        // initialisers are trivial (one String assignment, one Context
        // reference), so they can stay on Main. NewPipe.init() is the
        // expensive one — it sets up the YouTube extractor's locale
        // tables, content-country map, and several internal regex maps
        // — and was previously inline here, blocking the main thread for
        // 50-150ms on cold start. We move it to startupScope.
        MediaItemBuilder.initialize(this)
        BotGuardTokenGenerator.initialize(this)

        // Lossless sources (ArchiveTune source-pool port): initialize token providers, cached accounts,
        // and background instance health on startupScope immediately upon app launch, off the main thread.
        try {
            com.unshoo.pixelmusic.data.lossless.LosslessSources.start(this, startupScope)
        } catch (e: Throwable) {
            Timber.w(e, "Lossless sources start-up failed (non-fatal)")
        }
        // THERMAL OPTIMIZATION PIPELINE:
        // Consolidate background warm-up work into a staggered sequential pipeline
        // executing on warmUpScope (Thread.MIN_PRIORITY). Running 7+ parallel
        // coroutine launches simultaneously on startup forces the CPU governor to
        // scale all cores to max frequency, causing thermal dissipation (device heat).
        warmUpScope.launch {
            val userPrefs = userPreferencesRepository.get()
            val isSetupDone = runCatching { userPrefs.initialSetupDoneFlow.first() }.getOrDefault(false)
            if (!isSetupDone) {
                Timber.d("PixelMusicApplication: First install detected. Deferring heavy background warm-ups until setup completes.")
                // Wait for the user to complete the initial setup screen
                userPrefs.initialSetupDoneFlow.filter { it }.first()
                Timber.d("PixelMusicApplication: Initial setup completed! Commencing delayed warmups.")
                delay(1500L)
            }

            // Stage 1 (Immediate): Pre-warm ExoCache lazy SimpleCache index and DNS resolution off the main thread
            try {
                exoCache.get().cache
            } catch (e: Exception) {
                Timber.w(e, "ExoCache pre-warm failed (non-fatal)")
            }
            try {
                java.net.InetAddress.getAllByName("music.youtube.com")
                java.net.InetAddress.getAllByName("googlevideo.com")
            } catch (e: Exception) {
                Timber.w(e, "DNS pre-warming failed (non-fatal)")
            }

            // Stage 2 (Immediate): Initialize NewPipe YouTube Extractor and CardColorExtractor
            try {
                com.unshoo.pixelmusic.presentation.utils.CardColorExtractor.init(this@PixelMusicApplication)
                org.schabi.newpipe.extractor.NewPipe.init(
                    com.unshoo.pixelmusic.data.remote.youtube.YoutubeExtractor(
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.client
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "NewPipe / CardColorExtractor warm-up failed")
            }

            // Stage 3 (T+1000ms & Idle): Initialize LastFM
            kotlinx.coroutines.delay(1000L)
            awaitMainThreadIdle()

            val prefs = userPreferencesRepository.get()
            val savedApiKey = runCatching { prefs.lastfmApiKeyFlow.first() }.getOrDefault("")
            val savedSecret = runCatching { prefs.lastfmApiSecretFlow.first() }.getOrDefault("")
            if (savedApiKey.isNotEmpty() && savedSecret.isNotEmpty()) {
                com.unshoo.pixelmusic.data.lastfm.LastFM.initialize(
                    apiKey = savedApiKey,
                    secret = savedSecret
                )
            }
            val sessionKey = runCatching { prefs.lastfmSessionFlow.first() }.getOrDefault("")
            if (sessionKey.isNotEmpty()) {
                com.unshoo.pixelmusic.data.lastfm.LastFM.sessionKey = sessionKey
            }

            // Stage 4 (T+5000ms): Legacy cache migration and BotGuard warmup
            kotlinx.coroutines.delay(2000L)

            awaitMainThreadIdle()
            try {
                BotGuardTokenGenerator.preWarm("warmup_session")
            } catch (e: Throwable) {
                Timber.w(e, "BotGuard pre-warm deferred task failed")
            }

            AlbumArtUtils.migrateLegacyCacheLocation(this@PixelMusicApplication)
            val savedLimit = runCatching {
                prefs.albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }

            com.unshoo.pixelmusic.utils.JapaneseDictionaryManager.initAtAppStart(this@PixelMusicApplication)
        }

        // Initialize Last.fm client defaults
        com.unshoo.pixelmusic.data.lastfm.LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )

        // Bind Content Language and Country to YouTube.locale
        startupScope.launch {
            kotlinx.coroutines.flow.combine(
                userPreferencesRepository.get().contentLanguageFlow,
                userPreferencesRepository.get().contentCountryFlow
            ) { language, country ->
                unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeLocale(
                    gl = country,
                    hl = language
                )
            }.collect { locale ->
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.locale = locale
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelMusic Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }

    @Volatile
    private var customImageLoaderInstance: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        return customImageLoaderInstance ?: synchronized(this) {
            customImageLoaderInstance ?: run {
                val base = imageLoader.get()
                base.newBuilder()
                    .memoryCache(base.memoryCache)
                    .diskCache(base.diskCache)
                    .components {
                        add(localArtworkCoilFetcherFactory.get())
                        add(telegramCoilFetcherFactory.get())
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            add(coil.decode.ImageDecoderDecoder.Factory())
                        } else {
                            add(coil.decode.GifDecoder.Factory())
                        }
                    }
                    .build()
                    .also { customImageLoaderInstance = it }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)
        customImageLoaderInstance?.memoryCache?.trimMemory(level)

        val isRunning = level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        val isUiHidden = level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        val isBackground = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

        // Tier 1: Light pressure or UI hidden
        if (isUiHidden || (isRunning && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) || (isBackground && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)) {
            themeStateHolder.get().trimMemory(level)
        }

        // Tier 2: Moderate pressure
        if ((isRunning && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) || (isBackground && level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE)) {
            artistImageRepository.get().clearCache()
            telegramRepository.get().clearMemoryCache()
            MediaMetadataRetrieverPool.clear()
        }

        if (isUiHidden || isBackground) {
            startupScope.launch { BotGuardTokenGenerator.onAppBackgrounded() }
        }

        libraryStateHolder.get().trimMemory(level)

        // Tier 3: Critical pressure
        if ((isRunning && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) || (isBackground && level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            imageLoader.get().memoryCache?.clear()
            customImageLoaderInstance?.memoryCache?.clear()
        }
    }

    private val workManagerExecutor by lazy {
        java.util.concurrent.Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "PixelMusic-Work").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    private val workManagerTaskExecutor by lazy {
        java.util.concurrent.Executors.newFixedThreadPool(1) { runnable ->
            Thread(runnable, "PixelMusic-WorkTask").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(workManagerExecutor)
            .setTaskExecutor(workManagerTaskExecutor)
            .build()

}
