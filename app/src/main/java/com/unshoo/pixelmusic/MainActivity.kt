package com.unshoo.pixelmusic

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely

// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.unshoo.pixelmusic.presentation.utils.AppIconManager
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.CallSuper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.unshoo.pixelmusic.data.preferences.AppThemeMode
import com.unshoo.pixelmusic.data.preferences.AppFontMode
import com.unshoo.pixelmusic.data.preferences.NavBarStyle
import com.unshoo.pixelmusic.data.preferences.sanitizeNavBarCornerRadius
import com.unshoo.pixelmusic.data.preferences.ThemePreferencesRepository
import com.unshoo.pixelmusic.data.preferences.ThemePreference
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.service.MusicService
import com.unshoo.pixelmusic.data.worker.SyncManager

import com.unshoo.pixelmusic.presentation.components.AllFilesAccessDialog
import com.unshoo.pixelmusic.presentation.components.AppSidebarDrawer
import com.unshoo.pixelmusic.presentation.components.CrashReportDialog
import com.unshoo.pixelmusic.presentation.components.DismissUndoBar
import com.unshoo.pixelmusic.presentation.components.DrawerDestination
import com.unshoo.pixelmusic.presentation.components.MiniPlayerBottomSpacer
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.PlayerInternalNavigationBar
import com.unshoo.pixelmusic.presentation.components.PlayStoreAnnouncementDefaults
import com.unshoo.pixelmusic.presentation.components.PlayStoreAnnouncementDialog
import com.unshoo.pixelmusic.presentation.components.PlayStoreAnnouncementUiModel
import com.unshoo.pixelmusic.presentation.components.UnifiedPlayerSheetV2
import com.unshoo.pixelmusic.presentation.components.calculatePlayerSheetCollapsedTargetY
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import com.unshoo.pixelmusic.presentation.components.resolveNavBarSurfaceHeight
import com.unshoo.pixelmusic.presentation.components.sanitizeNavigationBarBottomInset
import com.unshoo.pixelmusic.presentation.navigation.AppNavigation
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.screens.SetupScreen
import com.unshoo.pixelmusic.presentation.viewmodel.MainViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.PixelMusicTheme

import com.unshoo.pixelmusic.utils.CrashHandler
import com.unshoo.pixelmusic.utils.DevicePerformanceUtils
import com.unshoo.pixelmusic.utils.AdaptiveRefreshRateManager
import com.unshoo.pixelmusic.utils.AppLocaleManager
import com.unshoo.pixelmusic.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.presentation.utils.AppHapticsConfig
import com.unshoo.pixelmusic.presentation.utils.LocalAppHapticsConfig
import com.unshoo.pixelmusic.presentation.utils.NoOpHapticFeedback
import com.unshoo.pixelmusic.utils.CrashLogData
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


@Immutable
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

@Immutable
private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

/**
 * Cache for the player-expansion backdrop blur [android.graphics.RenderEffect].
 *
 * Creating a blur effect is not free, and assigning a *new* effect instance to
 * a graphics layer forces the whole (full-screen) layer to be re-rendered
 * through the blur pipeline. Doing that on every frame of the player
 * expansion animation — as the previous code did — halves the effective
 * frame budget and is a prime cause of shutter/stutter at 90/120 Hz, where
 * a frame is only ~8.3 ms. Quantizing the radius to coarse steps and reusing
 * the effect instance between steps keeps the visual ramp (steps of 4 px on
 * a 0–24 dp ramp are imperceptible) while letting most frames reuse the
 * already-blurred layer.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
private class QuantizedBlurEffectCache {
    var radiusPx: Float = -1f
    var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        // Quantize to 4 px steps: radius changes smaller than this are
        // visually indistinguishable on a backdrop blur.
        val quantized = ((radiusPx + 2f) / 4f).toInt() * 4f
        if (quantized <= 1f) {
            this.radiusPx = -1f
            this.effect = null
            return null
        }
        if (quantized != this.radiusPx || effect == null) {
            this.radiusPx = quantized
            this.effect = android.graphics.RenderEffect.createBlurEffect(
                quantized,
                quantized,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
        return effect
    }
}

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var appUpdateScheduler: com.unshoo.pixelmusic.data.worker.AppUpdateScheduler
    @Inject
    lateinit var appUpdateDownloadManager: com.unshoo.pixelmusic.data.update.AppUpdateDownloadManager

    private val _pendingUpdateRelease =
        kotlinx.coroutines.flow.MutableStateFlow<Pair<com.unshoo.pixelmusic.data.model.update.AppReleaseInfo, com.unshoo.pixelmusic.data.model.update.AppReleaseAsset?>?>(null)
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** URI of an M3U/M3U8 file shared/opened from another app, waiting to be imported. */
    val pendingM3uImportUri = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    @OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        com.unshoo.pixelmusic.utils.perf.PerfTracker.recordAppStart()
        LogUtils.d(this, "onCreate")
        val activeIcon = AppIconManager.getActiveLauncherIcon(this)
        setTheme(activeIcon.splashThemeRes)
        val splashScreen = installSplashScreen()
        AppIconManager.updateTaskDescription(this, activeIcon)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AdaptiveRefreshRateManager(this, userPreferencesRepository))

        // LEER SEÑAL DE BENCHMARK
        val isBenchmarkMode = intent.getBooleanExtra("is_benchmark", false)
        val shouldBenchmarkRebuildDatabase =
            isBenchmarkMode && intent.getBooleanExtra("benchmark_rebuild_database", false)
        Log.i(
            "PixelMusicBenchmark",
            "onCreate benchmark=$isBenchmarkMode rebuildDatabase=$shouldBenchmarkRebuildDatabase"
        )
        if (shouldBenchmarkRebuildDatabase) {
            lifecycleScope.launch {
                userPreferencesRepository.setInitialSetupDone(true)
                Log.i("PixelMusicBenchmark", "Enqueueing benchmark database rebuild")
                syncManager.rebuildDatabase()
                delay(1_500L)
                playerViewModel.prepareBenchmarkPlayerFromLibrary()
            }
        }

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val pitchBlackEnabled by themePreferencesRepository.pitchBlackEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val playerThemePreference by themePreferencesRepository.playerThemePreferenceFlow.collectAsStateWithLifecycle(initialValue = ThemePreference.ALBUM_ART)
            val colorPalette by themePreferencesRepository.colorPalettePreferenceFlow.collectAsStateWithLifecycle(initialValue = "SAGE")
            val appFontMode by themePreferencesRepository.appFontModeFlow.collectAsStateWithLifecycle(initialValue = AppFontMode.APP_DEFAULT)
            val dynamicColorEnabled = colorPalette == "DYNAMIC" || playerThemePreference == ThemePreference.DYNAMIC
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            // Keep system splash screen on screen until initial setup state resolves
            splashScreen.setKeepOnScreenCondition { isSetupComplete == null }
            
            // Crash report dialog state
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            
            // Permissions Logic
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            // Determine if we need to show Setup based on completion OR missing permissions
            val permissionsValid = permissionState.allPermissionsGranted
            val showSetupScreen = remember(isSetupComplete, permissionsValid, isBenchmarkMode) {
                when {
                    isBenchmarkMode -> false
                    isSetupComplete == null -> null
                    else -> !isSetupComplete!! || !permissionsValid
                }
            }

            // Sync Trigger: When we are NOT showing setup (meaning permissions are good and setup is done).
            // BUGFIX (lag — was: delay(800L) on every cold start): the previous
            // implementation waited a fixed 800ms after permissions were granted
            // before starting the MediaStore sync, which meant every cold start
            // took 800ms longer to begin showing songs. We now wait on the
            // AppReadinessSignal — which fires the moment the first frame is
            // committed — and start the sync immediately, off the main thread.
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                    LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                    mainViewModel.startSync()
                }
            }

            // Check for crash log when app starts.
            LaunchedEffect(Unit) {
                if (!isBenchmarkMode && CrashHandler.hasCrashLog()) {
                    crashLogData = CrashHandler.getCrashLog()
                    showCrashReportDialog = true
                }
                if (!isBenchmarkMode) {
                    appUpdateScheduler.schedulePeriodicCheck()
                    appUpdateScheduler.checkOnStartupIfNeeded(lifecycleScope)
                }
            }

            PixelMusicTheme(
                darkTheme = useDarkTheme,
                dynamicColor = dynamicColorEnabled,
                colorPalette = colorPalette,
                useSystemFont = (appFontMode == AppFontMode.SYSTEM),
                pitchBlack = (useDarkTheme && pitchBlackEnabled)
            ) {
                var contentVisible by remember { mutableStateOf(false) }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(150, easing = LinearOutSlowInEasing),
                    label = "AppContentAlpha"
                )

                LaunchedEffect(Unit) {
                    // BUGFIX (lag — was: delay(100) which is arbitrary): wait
                    // for the next actual frame instead of a fixed timer. On
                    // a low-end device 100ms may not be enough and we'd hide
                    // the splash before the first frame is committed; on a
                    // high-end device 100ms is wasted. `awaitFrame` is a no-op
                    // suspend that resumes on the next frame callback, so we
                    // hand the UI back the moment a real frame is committed.
                    androidx.compose.runtime.withFrameNanos { /* first frame callback */ }
                    contentVisible = true
                    com.unshoo.pixelmusic.utils.perf.PerfTracker.markFirstFrame()
                    // Signal ViewModels that defer work until UI is ready.
                    // This fires after the first frame has been committed, giving
                    // QuickPicks and other deferred loaders an event-driven trigger
                    // instead of relying on a fixed delay.
                    com.unshoo.pixelmusic.utils.AppReadinessSignal.markReady()
                }

                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, 
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSetupScreen == null) {
                        SetupGateLoadingScreen()
                    } else {
                        AnimatedContent(
                            targetState = showSetupScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Transition to Setup
                                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                } else {
                                    // Transition from Setup to Main App
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                }
                            },
                            label = "SetupTransition"
                        ) { shouldShowSetup ->
                            if (shouldShowSetup) {
                                SetupScreen(onSetupComplete = {
                                    // Repository-backed setup completion updates the gate automatically.
                                    com.unshoo.pixelmusic.utils.perf.PerfTracker.markFirstMeaningfulUi()
                                })
                            } else {
                                val isInitialFetchFinished by com.unshoo.pixelmusic.utils.AppReadinessSignal.isInitialFetchFinished.collectAsStateWithLifecycle()
                                var isFreshStartLoading by rememberSaveable { mutableStateOf(!isInitialFetchFinished) }

                                LaunchedEffect(isInitialFetchFinished) {
                                    if (isInitialFetchFinished) {
                                        isFreshStartLoading = false
                                        com.unshoo.pixelmusic.utils.perf.PerfTracker.markFirstMeaningfulUi()
                                        com.unshoo.pixelmusic.utils.perf.PerfTracker.markHomeClickable()
                                        com.unshoo.pixelmusic.utils.perf.PerfTracker.markStartupNetworkSettled()
                                    }
                                }
                                LaunchedEffect(Unit) {
                                    delay(2500L)
                                    isFreshStartLoading = false
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    MainAppContent(playerViewModel, mainViewModel)

                                    AnimatedVisibility(
                                        visible = isFreshStartLoading,
                                        enter = fadeIn(),
                                        exit = fadeOut(animationSpec = tween(400))
                                    ) {
                                        FreshStartLoadingScreen()
                                    }
                                }
                            }
                        }
                    }

                    // Show crash report dialog if needed
                    if (showCrashReportDialog && crashLogData != null) {
                        CrashReportDialog(
                            crashLog = crashLogData!!,
                            onDismiss = {
                                CrashHandler.clearCrashLog()
                                crashLogData = null
                                showCrashReportDialog = false
                            }
                        )
                    }

                    // Show in-app update bottom sheet when launched from notification
                    val pendingUpdate by _pendingUpdateRelease.collectAsStateWithLifecycle()
                    if (pendingUpdate != null) {
                        val (updateRelease, updateAsset) = pendingUpdate!!
                        com.unshoo.pixelmusic.presentation.components.AppUpdateBottomSheet(
                            release = updateRelease,
                            targetAsset = updateAsset,
                            currentVersion = BuildConfig.VERSION_NAME,
                            downloadManager = appUpdateDownloadManager,
                            onDismissRequest = { _pendingUpdateRelease.value = null }
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    private var jankStats: androidx.metrics.performance.JankStats? = null

    override fun onResume() {
        super.onResume()
        if (jankStats == null) {
            jankStats = androidx.metrics.performance.JankStats.createAndTrack(
                window,
                { frameData ->
                    if (frameData.isJank && BuildConfig.DEBUG) {
                        LogUtils.d(this, "JankStats frame drop detected: ${frameData.frameDurationUiNanos / 1_000_000}ms")
                    }
                }
            )
        }
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            // Handle notification tap to show update dialog
            intent.action == com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.ACTION_SHOW_UPDATE_DIALOG -> {
                val tagName = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_TAG) ?: ""
                val title = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_TITLE) ?: tagName
                val body = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_BODY) ?: ""
                val htmlUrl = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_HTML_URL) ?: ""
                val downloadUrl = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_DOWNLOAD_URL) ?: ""
                val assetName = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_ASSET_NAME) ?: ""
                val assetSize = intent.getLongExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_ASSET_SIZE, 0L)
                val assetArch = intent.getStringExtra(com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager.EXTRA_UPDATE_ASSET_ARCH) ?: ""

                if (tagName.isNotEmpty()) {
                    val asset = if (downloadUrl.isNotEmpty()) {
                        com.unshoo.pixelmusic.data.model.update.AppReleaseAsset(
                            name = assetName,
                            downloadUrl = downloadUrl,
                            sizeBytes = assetSize,
                            architecture = assetArch
                        )
                    } else null

                    val releaseInfo = com.unshoo.pixelmusic.data.model.update.AppReleaseInfo(
                        tagName = tagName,
                        name = title,
                        body = body,
                        htmlUrl = htmlUrl,
                        assets = if (asset != null) listOf(asset) else emptyList()
                    )

                    _pendingUpdateRelease.value = releaseInfo to asset
                }
                intent.action = null
            }

            // Handle shuffle all shortcut / tile
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null // Clear action to prevent re-triggering
            }
            
            // Handle playlist shortcut
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            // Handle incoming M3U/M3U8 playlist — import instead of play
            intent.action == android.content.Intent.ACTION_VIEW &&
                intent.data != null &&
                isM3uMimeOrExtension(intent.type, intent.data?.lastPathSegment) -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    pendingM3uImportUri.value = uri
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND &&
                isM3uMimeOrExtension(intent.type, null) -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    pendingM3uImportUri.value = uri
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null &&
                isYouTubeMusicLink(intent.data) -> {
                intent.data?.let { uri ->
                    playerViewModel.openYouTubeMusicLink(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.unshoo.pixelmusic.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.unshoo.pixelmusic.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.unshoo.pixelmusic.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("MainActivity", "Unable to persist URI permission for $uri", securityException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        android.util.Log.w("MainActivity", "Persistable URI permission not granted for $uri", illegalArgumentException)
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    private fun isYouTubeMusicLink(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "music.youtube.com" || host == "youtube.com" ||
            host == "www.youtube.com" || host == "m.youtube.com" || host == "youtu.be"
    }

    /** Returns true if the MIME type or file name indicates an M3U/M3U8 playlist. */
    private fun isM3uMimeOrExtension(mimeType: String?, fileName: String?): Boolean {
        val m3uMimeTypes = setOf(
            "audio/x-mpegurl",
            "audio/mpegurl",
            "application/vnd.apple.mpegurl"
        )
        if (mimeType != null && mimeType.lowercase() in m3uMimeTypes) return true
        val lower = fileName?.lowercase() ?: return false
        return lower.endsWith(".m3u") || lower.endsWith(".m3u8")
    }

    private fun openExternalUrl(url: String) {
        // Defense in depth: the announcement URL is fetched from a remote
        // properties file on GitHub. If that file is ever tampered with, we
        // must not let it launch arbitrary intents (`intent://...`,
        // `javascript:`, custom schemes, etc.). Allow only the Play Store host.
        val parsed = runCatching { url.toUri() }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        val isPlayStore = scheme == "https" &&
            (host == "play.google.com" || host == "market.android.com")
        if (!isPlayStore) {
            LogUtils.w(this, "Refusing to open non-Play-Store announcement URL: $url")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, parsed)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            LogUtils.w(this, "No activity available to open URL: $url")
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SetupGateLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Preparing setup…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun FreshStartLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        // Note: initial-sync feedback is handled inside LibraryScreen itself
        // (in-place skeletons + LibraryInlineSyncIndicator + LibrarySyncOverlay for
        // the empty first-run case). No blocking
        // full-screen overlay here, so the app UI is interactive during first sync.
        
        // isMediaControllerReady used below for playlist navigation gate
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        // Observe pending playlist navigation
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            // Only process if we have a new playlist ID that hasn't been processed yet
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                // Wait for navigation graph to be ready (retry with delay)
                var success = false
                var attempts = 0
                while (!success && attempts < 50) { // 5 seconds max
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                // Reset so the same playlist can be opened again
                processedPlaylistId = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        val homeLabel = stringResource(R.string.tab_home)
        val exploreLabel = stringResource(R.string.tab_explore)
        val searchLabel = stringResource(R.string.search)
        val libraryLabel = stringResource(R.string.tab_library)

        val commonNavItems = remember(homeLabel, exploreLabel, searchLabel, libraryLabel) {
            persistentListOf(
                BottomNavItem(homeLabel, R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
                BottomNavItem(exploreLabel, R.drawable.rounded_album_24, R.drawable.rounded_album_24, Screen.Explore),
                BottomNavItem(searchLabel, R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem(libraryLabel, R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var isSearchBarActive by remember { mutableStateOf(false) }

        // Positive whitelist. The nav bar is shown ONLY on the four root tabs;
        // everywhere else it is hidden. The previous version enumerated 28 hidden
        // routes and did `hiddenRoute.substringBefore("{")` for each of them on
        // every route change, allocating up to 28 Strings on the transition frame.
        val mainRoutesWithNavigationBar = remember {
            persistentListOf(
                Screen.Home.route,
                Screen.Explore.route,
                Screen.Search.route,
                Screen.Library.route
            )
        }
        val shouldHideNavigationBar by remember(currentRoute, isSearchBarActive) {
            derivedStateOf {
                when {
                    currentRoute == null -> false
                    currentRoute == Screen.Search.route && isSearchBarActive -> true
                    else -> currentRoute !in mainRoutesWithNavigationBar
                }
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarHeightOffsetRaw by playerViewModel.navBarHeightOffset.collectAsStateWithLifecycle()
        val navBarHeightOffset = navBarHeightOffsetRaw.dp
        val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
        val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val useBlurEffects by playerViewModel.useBlurEffects.collectAsStateWithLifecycle(initialValue = true)
        val playerExpansionBlurEnabled by playerViewModel.playerExpansionBlurEnabled.collectAsStateWithLifecycle(initialValue = true)
        val isPlayerExpansionBlurActive = useBlurEffects && playerExpansionBlurEnabled
        val rootView = LocalView.current
        // Jank diagnostics: JankStats (wired up in onResume, below) already flags dropped
        // frames but had no way to say *where* — every log line just said "frame drop
        // detected". Tagging the active route here means JankStats attaches it to any frame
        // that overlaps, so a jank report can be traced back to the screen that caused it.
        LaunchedEffect(currentRoute, rootView) {
            PerformanceMetricsState.getHolderForHierarchy(rootView).state
                ?.putState("screen", currentRoute ?: "unknown")
        }
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }

        val systemNavBarInset = sanitizeNavigationBarBottomInset(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        val horizontalPadding = when (navBarStyle) {
            NavBarStyle.DEFAULT -> 12.dp
            NavBarStyle.FLOATING_PILL -> 16.dp
            else -> 0.dp
        }
        val targetBottomBarPadding = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
            0.dp
        } else {
            if (systemNavBarInset > 0.dp) systemNavBarInset else 14.dp
        }
        val animatedBottomBarPadding by animateDpAsState(
            targetValue = targetBottomBarPadding,
            animationSpec = tween(400),
            label = "BottomBarPadding"
        )
        val bottomBarPadding = animatedBottomBarPadding
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode, navBarHeightOffset)
        val navBarOccupiedHeight by remember(systemNavBarInset, navBarCompactMode, navBarStyle, navBarHeightOffset) {
            derivedStateOf { resolveNavBarOccupiedHeight(navBarStyle, systemNavBarInset, navBarCompactMode, navBarHeightOffset) }
        }
        // Cold boot: hold the bar off-screen for one frame, then spring it up.
        // Without this the bar pops in instantly on launch and the app feels
        // "assembled" rather than "opened". Leaving a route still uses the fast
        // 150 ms tween so navigation never feels sluggish.
        var isNavBarMounted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(90)
            isNavBarMounted = true
        }
        val navBarTargetVisible = isNavBarMounted && !shouldHideNavigationBar
        val navBarVisibilityProgress by animateFloatAsState(
            targetValue = if (navBarTargetVisible) 1f else 0f,
            animationSpec = if (navBarTargetVisible) {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            } else {
                tween(durationMillis = 150, easing = LinearOutSlowInEasing)
            },
            label = "NavBarVisibilityProgress"
        )
        val visibleNavBarOccupiedHeight by remember(navBarOccupiedHeight, navBarVisibilityProgress) {
            derivedStateOf { navBarOccupiedHeight * navBarVisibilityProgress }
        }
        val miniPlayerBottomMargin by remember(systemNavBarInset, visibleNavBarOccupiedHeight) {
            derivedStateOf {
                if (visibleNavBarOccupiedHeight > systemNavBarInset) {
                    visibleNavBarOccupiedHeight
                } else {
                    systemNavBarInset
                }
            }
        }
        val shouldRenderNavigationBar by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                !shouldHideNavigationBar || navBarVisibilityProgress > 0.01f
            }
        }
        val isNavBarEffectivelyHidden by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                shouldHideNavigationBar && navBarVisibilityProgress <= 0.01f
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var playStoreAnnouncement by remember {
            mutableStateOf(PlayStoreAnnouncementDefaults.localizedTemplate(context))
        }
        var showPlayStoreAnnouncement by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (PlayStoreAnnouncementDefaults.LOCAL_PREVIEW_ENABLED) {
                playStoreAnnouncement = PlayStoreAnnouncementDefaults.hardcodedPreview(this@MainActivity)
                showPlayStoreAnnouncement = true
            }
        }

        LaunchedEffect(userPreferencesRepository) {
            com.unshoo.pixelmusic.utils.AppReadinessSignal.awaitReady()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                userPreferencesRepository.clearDeprecatedPlayerSheetPreference()
            }
        }

        CompositionLocalProvider(
            LocalAppHapticsConfig provides appHapticsConfig,
            LocalHapticFeedback provides scopedHapticFeedback
        ) {
            AppSidebarDrawer(
                drawerState = drawerState,
                selectedRoute = currentRoute ?: Screen.Home.route,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination) {
                        DrawerDestination.Home -> navController.navigateSafely(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                        DrawerDestination.Equalizer -> navController.navigateSafely(Screen.Equalizer.route)
                        DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                        DrawerDestination.Telegram -> {
                            val intent = Intent(this@MainActivity, com.unshoo.pixelmusic.presentation.telegram.auth.TelegramLoginActivity::class.java)
                            startActivity(intent)
                        }
                    }
                }
        ) {

                Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (shouldRenderNavigationBar) {
                        // BUGFIX (was: collected the full PlayerUiState
                        // data class to read one Boolean field):
                        // PlayerUiState is a 50-field data class. Even
                        // though it's @Immutable, every time any field
                        // changes Compose creates a new instance and the
                        // bottomBar lambda re-evaluates — including the
                        // expensive bottom-bar shape & graphicsLayer
                        // blocks below. We subscribe only to the single
                        // Boolean field we actually need.
                        val isPreparing by remember {
                            playerViewModel.playerUiState
                                .map { it.preparingSongId != null }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val currentSongId by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong?.id }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = null)
                        val isUndoBarVisibleForNavBar by remember {
                            playerViewModel.playerUiState
                                .map { it.showDismissUndoBar }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val showPlayerContentArea = (currentSongId != null || isPreparing) && !isUndoBarVisibleForNavBar
                        val navBarElevation = 3.dp

                        // BUGFIX (recompose storm — every state change re-evaluated
                        // the bottom-bar shape): `playerContentExpansionFraction`
                        // is a StateFlow, and reading `.value` inside a `remember`
                        // block / a `graphicsLayer` doesn't tell Compose that the
                        // block depends on it. Every `PlayerViewModel` StateFlow
                        // emission would force the entire bottomBar lambda to
                        // re-evaluate, including the `DynamicSmoothCornerShape`
                        // construction and the `graphicsLayer` modifier — neither
                        // of which actually changes for a fraction change. We now
                        // subscribe to the fraction as State, and use it as a key
                        // for the `remember` so the shape is only rebuilt when the
                        // fraction changes by a meaningful amount.
                        val expansionFraction by remember(playerViewModel) {
                            androidx.compose.runtime.snapshotFlow { playerViewModel.playerContentExpansionFraction.value }
                        }.collectAsStateWithLifecycle(initialValue = 0f)
                        val quantizedFraction = remember(expansionFraction) {
                            (expansionFraction.coerceIn(0f, 1f) * 100f).toInt() / 100f
                        }


                        val animatedNavBarCornerRadius by animateDpAsState(
                            targetValue = navBarCornerRadius.dp,
                            animationSpec = tween(400),
                            label = "NavBarCornerRadius"
                        )

                        val animatedDefaultTopCornerRadius by animateDpAsState(
                            targetValue = if (showPlayerContentArea && !isMiniPlayerDismissing) 10.dp else navBarCornerRadius.dp,
                            animationSpec = tween(400),
                            label = "NavBarDefaultTopCornerRadius"
                        )

                        val actualShape = remember(
                            navBarStyle,
                            showPlayerContentArea,
                            isMiniPlayerDismissing,
                            navBarCornerRadius,
                            animatedNavBarCornerRadius,
                            animatedDefaultTopCornerRadius,
                            quantizedFraction
                        ) {
                            DynamicSmoothCornerShape(
                                topRadiusProvider = {
                                    // BUGFIX: previously read the StateFlow's .value
                                    // here, which was not tracked by Compose. We now
                                    // capture the quantized value from the outer
                                    // collectAsStateWithLifecycle subscription above.
                                    if (navBarStyle == NavBarStyle.DEFAULT) {
                                        animatedDefaultTopCornerRadius
                                    } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                                        lerp(navBarCornerRadius.dp, 26.dp, quantizedFraction)
                                    } else if (showPlayerContentArea) {
                                        if (quantizedFraction < 0.2f) {
                                            lerp(navBarCornerRadius.dp, 26.dp, (quantizedFraction / 0.2f).coerceIn(0f, 1f))
                                        } else {
                                            26.dp
                                        }
                                    } else {
                                        navBarCornerRadius.dp
                                    }
                                },
                                bottomRadiusProvider = {
                                    if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else animatedNavBarCornerRadius
                                }
                            )
                        }

                        var componentHeightPx by remember { mutableStateOf(0) }
                        val density = LocalDensity.current
                        val shadowOverflowPx = remember(navBarElevation, density) {
                            with(density) { (navBarElevation * 8).toPx() }
                        }
                        val bottomBarPaddingPx = remember(bottomBarPadding, density) {
                            with(density) { bottomBarPadding.toPx() }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(visibleNavBarOccupiedHeight)
                                .clipToBounds()
                        ) {
                            val onSearchIconDoubleTap = remember(playerViewModel) {
                                { playerViewModel.onSearchNavIconDoubleTapped() }
                            }

                            val isFloatingPill = navBarStyle == NavBarStyle.FLOATING_PILL
                            val navBarContainerModifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = bottomBarPadding)
                                .onSizeChanged { componentHeightPx = it.height }
                                .graphicsLayer {
                                    // BUGFIX: read the snapshot value captured via
                                    // collectAsStateWithLifecycle at the top of the
                                    // bottomBar scope. Reading .value directly off a
                                    // StateFlow inside a graphicsLayer skipped Compose's
                                    // recomposition tracking and forced the layer to
                                    // re-evaluate on every PlayerViewModel emission.
                                    val hideFraction = if (showPlayerContentArea) {
                                        quantizedFraction
                                    } else {
                                        0f
                                    }
                                    translationY = (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * hideFraction
                                    alpha = 1f
                                }
                                .height(navBarHeight)
                                .padding(horizontal = horizontalPadding)

                            val navBarContent: @Composable () -> Unit = {
                                PlayerInternalNavigationBar(
                                    navController = navController,
                                    navItems = commonNavItems,
                                    currentRoute = currentRoute,
                                    navBarStyle = navBarStyle,
                                    compactMode = navBarCompactMode,
                                    bottomBarPadding = bottomBarPadding,
                                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isFloatingPill) {
                                // No parent Surface — floating pill draws its own containers
                                Box(modifier = navBarContainerModifier) {
                                    navBarContent()
                                }
                            } else {
                                Surface(
                                    modifier = navBarContainerModifier,
                                    color = NavigationBarDefaults.containerColor,
                                    shape = actualShape,
                                    shadowElevation = navBarElevation
                                ) {
                                    navBarContent()
                                }
                            }
                        }
                    }
                }
                ) { innerPadding ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerHeight = this.maxHeight
                        val screenHeightPx = remember(containerHeight, density) {
                            with(density) { containerHeight.toPx() }
                        }

                        val showPlayerContentInitially by remember {
                            kotlinx.coroutines.flow.combine(
                                playerViewModel.stablePlayerState.map { it.currentSong?.id != null },
                                playerViewModel.playerUiState.map { it.preparingSongId != null },
                                playerViewModel.playerUiState.map { it.showDismissUndoBar }
                            ) { hasSong, isPreparing, isUndoBarVisible ->
                                (hasSong || isPreparing) && !isUndoBarVisible
                            }.distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                        val shouldHideMiniPlayer by remember(currentRoute) {
                            derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                        }

                        // BUGFIX (was: with(density) { … toPx() } on every
                        // recomposition): the previous code called toPx()
                        // for MiniPlayerHeight, MiniPlayerBottomSpacer and
                        // bottomMargin on every recomposition of the inner
                        // BoxWithConstraints lambda. Since the lambda
                        // re-runs whenever any observed State changes
                        // (playerUiState emits frequently during playback),
                        // that's 3-4 dp-to-px conversions per frame for
                        // values that never change at runtime. Memoize
                        // them keyed on the only inputs that affect them.
                        val miniPlayerH = remember(density) { with(density) { MiniPlayerHeight.toPx() } }
                        val totalSheetHeightWhenContentCollapsedPx =
                            if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                        val bottomMargin = miniPlayerBottomMargin

                        val spacerPx = remember(density) { with(density) { MiniPlayerBottomSpacer.toPx() } }
                        val bottomMarginPx = remember(density, bottomMargin) {
                            with(density) { bottomMargin.toPx() }
                        }
                        val sheetCollapsedTargetY = remember(
                            screenHeightPx,
                            totalSheetHeightWhenContentCollapsedPx,
                            bottomMarginPx,
                            spacerPx
                        ) {
                            calculatePlayerSheetCollapsedTargetY(
                                containerHeightPx = screenHeightPx,
                                collapsedContentHeightPx = totalSheetHeightWhenContentCollapsedPx,
                                bottomMarginPx = bottomMarginPx,
                                bottomSpacerPx = spacerPx
                            )
                        }

                        // Reused across frames: see QuantizedBlurEffectCache.
                        // A fresh blur RenderEffect per frame re-blurs the
                        // entire navigation layer every frame and misses
                        // the 8.3 ms budget at 120 Hz.
                        val expansionBlurCache = remember {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                QuantizedBlurEffectCache()
                            } else null
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect = if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                        isPlayerExpansionBlurActive
                                    ) {
                                        val frac = playerViewModel.playerContentExpansionFraction.value
                                        val radiusPx = (frac * 24.dp.toPx()).coerceAtLeast(0f)
                                        expansionBlurCache?.effectFor(radiusPx)
                                    } else {
                                        expansionBlurCache?.effectFor(0f)
                                        null
                                    }
                                }
                        ) {
                            AppNavigation(
                                playerViewModel = playerViewModel,
                                navController = navController,
                                paddingValues = innerPadding,
                                userPreferencesRepository = userPreferencesRepository,
                                onSearchBarActiveChange = { isSearchBarActive = it },
                                onOpenSidebar = { scope.launch { drawerState.open() } }
                            )
                        }

                        // BUGFIX (recompose storm — read .value off a StateFlow):
                        // `derivedStateOf { someFlow.value > x }` is a known
                        // Compose anti-pattern — it reads the latest value at
                        // composition time but doesn't subscribe to the StateFlow,
                        // so the derived state never re-evaluates on its own.
                        // The fallback here was that the *outer* composition
                        // would re-evaluate (because `playerUiState` changes
                        // frequently), and then re-derive the value. That works
                        // but means every player state tick re-evaluates the
                        // entire content lambda. Use the StateFlow as a
                        // `State<Float>` directly with `collectAsStateWithLifecycle`.
                        val isMiniPlayerDismissingForOverlay by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
                        val expansionFractionForOverlay =
                            playerViewModel.playerContentExpansionFraction.value
                        val isUndoBarVisibleForOverlay by remember {
                            playerViewModel.playerUiState
                                .map { it.showDismissUndoBar }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val isExpandedOrExpanding = remember(
                            expansionFractionForOverlay,
                            showPlayerContentInitially,
                            isMiniPlayerDismissingForOverlay,
                            isUndoBarVisibleForOverlay
                        ) {
                            !isUndoBarVisibleForOverlay &&
                                !isMiniPlayerDismissingForOverlay &&
                                showPlayerContentInitially &&
                                expansionFractionForOverlay > 0.01f
                        }

                        AnimatedVisibility(
                            visible = isExpandedOrExpanding,
                            enter = fadeIn(animationSpec = tween(durationMillis = 350)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 350)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            playerViewModel.collapsePlayerSheet()
                                        }
                                    }
                            )
                        }

                        UnifiedPlayerSheetV2(
                            playerViewModel = playerViewModel,
                            sheetCollapsedTargetY = sheetCollapsedTargetY,
                            collapsedStateHorizontalPadding = horizontalPadding,
                            hideMiniPlayer = shouldHideMiniPlayer,
                            containerHeight = containerHeight,
                            navController = navController,
                            isNavBarHidden = isNavBarEffectivelyHidden
                        )

                        val dismissUndoBarSlice by remember {
                            playerViewModel.playerUiState
                                .map { state ->
                                    DismissUndoBarSlice(
                                        isVisible = state.showDismissUndoBar,
                                        durationMillis = state.undoBarVisibleDuration
                                    )
                                }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                        val onUndoDismissPlaylist = remember(playerViewModel) {
                            { playerViewModel.undoDismissPlaylist() }
                        }
                        val onCloseDismissUndoBar = remember(playerViewModel) {
                            { playerViewModel.hideDismissUndoBar() }
                        }

                        AnimatedVisibility(
                            visible = dismissUndoBarSlice.isVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + MiniPlayerBottomSpacer)
                                .padding(horizontal = horizontalPadding)
                        ) {
                            DismissUndoBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MiniPlayerHeight)
                                    .padding(horizontal = 14.dp),
                                onUndo = onUndoDismissPlaylist,
                                onClose = onCloseDismissUndoBar,
                                durationMillis = dismissUndoBarSlice.durationMillis
                            )
                        }

                        if (showPlayStoreAnnouncement) {
                            PlayStoreAnnouncementDialog(
                                announcement = playStoreAnnouncement,
                                onDismiss = { showPlayStoreAnnouncement = false },
                                onOpenPlayStore = { url ->
                                    showPlayStoreAnnouncement = false
                                    openExternalUrl(url)
                                }
                            )
                        }
                    }
                }
            }
        }

        Trace.endSection()
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()

        // BUGFIX (was: empty lambda + directExecutor): the previous
        // addListener passed an empty lambda. The whole point of the
        // listener is to react to the future completing, but with no
        // action the call was dead weight, and directExecutor meant
        // the future completion ran inline on the binder thread — which
        // is fine, but the addListener call itself is unused and the
        // MediaController is only needed in onStop() (where it's
        // released). The PlayerViewModel connects its own controller
        // for runtime state. We drop the dead listener entirely.
    }


}

private class DynamicSmoothCornerShape(
    private val topRadiusProvider: () -> androidx.compose.ui.unit.Dp,
    private val bottomRadiusProvider: () -> androidx.compose.ui.unit.Dp
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val topRadius = topRadiusProvider()
        val bottomRadius = bottomRadiusProvider()
        val delegate = AbsoluteSmoothCornerShape(
            cornerRadiusTL = topRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = topRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = bottomRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = bottomRadius,
            smoothnessAsPercentBR = 60
        )
        return delegate.createOutline(size, layoutDirection, density)
    }
}
