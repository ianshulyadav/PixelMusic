package com.unshoo.pixelmusic.presentation.components

import android.widget.Toast
import com.unshoo.pixelmusic.presentation.components.ExpressiveOfflineDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.PlayerBackgroundStyle
import com.unshoo.pixelmusic.data.preferences.sanitizeNavBarCornerRadius
import com.unshoo.pixelmusic.presentation.components.player.FullPlayerAtmosphereBackground
import com.unshoo.pixelmusic.presentation.components.scoped.PlayerAlbumNavigationEffect
import com.unshoo.pixelmusic.presentation.components.scoped.PlayerArtistNavigationEffect
import com.unshoo.pixelmusic.presentation.components.scoped.PlayerSheetPredictiveBackHandler
import com.unshoo.pixelmusic.presentation.components.scoped.QueueSheetRuntimeEffects
import com.unshoo.pixelmusic.presentation.components.scoped.SheetMotionController
import com.unshoo.pixelmusic.presentation.components.scoped.miniPlayerDismissHorizontalGesture
import com.unshoo.pixelmusic.presentation.components.scoped.playerSheetVerticalDragGesture
import com.unshoo.pixelmusic.presentation.components.scoped.rememberFullPlayerCompositionPolicy
import com.unshoo.pixelmusic.presentation.components.scoped.rememberCastSheetState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberFullPlayerVisualState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberMiniPlayerDismissGestureHandler
import com.unshoo.pixelmusic.presentation.components.scoped.rememberPrewarmFullPlayer
import com.unshoo.pixelmusic.presentation.components.scoped.rememberQueueSheetState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetActionHandlers
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetBackAndDragState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetInteractionState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetModalOverlayController
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetOverlayState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetThemeState
import com.unshoo.pixelmusic.presentation.components.scoped.rememberSheetVisualState
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.StablePlayerState
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PlayerUiSheetSliceV2(
    val currentQueueSourceName: String = "",
    val preparingSongId: String? = null
)

/**
 * V2 real host: no longer delegates to the legacy `UnifiedPlayerSheet`.
 *
 * This path keeps behavior parity, but now owns its own runtime wiring so we can
 * profile and optimize V2 independently while preserving the Experimental switch.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun UnifiedPlayerSheetV2(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    navController: NavHostController,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // MediaStore write-permission launcher (for metadata editing without MANAGE_EXTERNAL_STORAGE)
    val writePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onWritePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // MediaStore delete-permission launcher (system delete confirmation dialog)
    val deletePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onDeletePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                playerViewModel.toastEvents.collect { message ->
                    Toast.makeText(latestContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            launch {
                playerViewModel.showNoInternetDialog.collect {
                    showNoInternetDialog = true
                }
            }
            launch {
                playerViewModel.writePermissionRequest.collect { intentSender ->
                    writePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
            launch {
                playerViewModel.deletePermissionRequest.collect { intentSender ->
                    deletePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
        }
    }

    if (showNoInternetDialog) {
        ExpressiveOfflineDialog(
            onDismiss = { showNoInternetDialog = false },
            onRetry = {
                 playerViewModel.refreshLocalConnectionInfo()
                 showNoInternetDialog = false
            }
        )
    }

    val infrequentPlayerStateReference = playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val infrequentPlayerState = infrequentPlayerStateReference.value
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentPositionState = playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val remotePositionState = playerViewModel.remotePosition.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val positionToDisplayProvider = remember(isRemotePlaybackActive) {
        {
            if (isRemotePlaybackActive) remotePositionState.value
            else currentPositionState.value
        }
    }

    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()

    val playerUiSheetSlice by remember {
        playerViewModel.playerUiState
            .map { state ->
                PlayerUiSheetSliceV2(
                    currentQueueSourceName = state.currentQueueSourceName,
                    preparingSongId = state.preparingSongId
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerUiSheetSliceV2())
    val currentQueueSourceName = playerUiSheetSlice.currentQueueSourceName
    val preparingSongId = playerUiSheetSlice.preparingSongId

    val currentSheetContentState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
    val predictiveBackSwipeEdge by playerViewModel.predictiveBackSwipeEdge.collectAsStateWithLifecycle()
    val prewarmFullPlayer = rememberPrewarmFullPlayer(infrequentPlayerState.currentSong?.id)

    val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
    val navBarCornerRadius = sanitizeNavBarCornerRadius(playerConfig.navBarCornerRadius)
    val navBarStyle = playerConfig.navBarStyle
    val carouselStyle = playerConfig.carouselStyle
    val fullPlayerLoadingTweaks = playerConfig.fullPlayerLoadingTweaks
    val tapBackgroundClosesPlayer = playerConfig.tapBackgroundClosesPlayer
    val useSmoothCorners = playerConfig.useSmoothCorners
    val playerThemePreference = playerConfig.playerThemePreference

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }
    // Dismiss micro-animation properties — all driven by MiniPlayerDismissGestureHandler
    val dismissAlpha = remember { Animatable(1f) }
    val dismissRotation = remember { Animatable(0f) }
    val dismissScale = remember { Animatable(1f) }
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }
    val swipeDismissProgress by remember(dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
    val showDismissUndoBar by remember {
        playerViewModel.playerUiState
            .map { it.showDismissUndoBar }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val showPlayerContentArea by remember(
        infrequentPlayerState.currentSong,
        preparingSongId,
        isCastConnecting,
        isMiniPlayerDismissing,
        showDismissUndoBar
    ) {
        derivedStateOf {
            !showDismissUndoBar &&
                !isMiniPlayerDismissing &&
                (infrequentPlayerState.currentSong != null || preparingSongId != null || isCastConnecting)
        }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val visualOvershootScaleY = remember { Animatable(1f) }
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    // Material 3 Emphasized Decelerate easing — no overshoot, no bounce, one clean arc.
    val emphasizedDecelerate = remember { CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) }
    val sheetExpandAnimSpec = remember { tween<Float>(durationMillis = 450, easing = emphasizedDecelerate) }
    val sheetCollapseAnimSpec = remember { tween<Float>(durationMillis = 350, easing = emphasizedDecelerate) }
    // Use expand spec as the default (collapse overrides it in SheetMotionController)
    val sheetAnimationSpec = sheetExpandAnimSpec
    val sheetAnimationMutex = remember { MutatorMutex() }
    val sheetExpandedTargetY = 0f
    val initialY =
        if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY
        else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }
    val sheetMotionController = remember(
        currentSheetTranslationY,
        playerContentExpansionFraction,
        sheetAnimationMutex,
        sheetExpandAnimSpec,
        sheetCollapseAnimSpec
    ) {
        SheetMotionController(
            translationY = currentSheetTranslationY,
            expansionFraction = playerContentExpansionFraction,
            mutex = sheetAnimationMutex,
            expandAnimationSpec = sheetExpandAnimSpec,
            collapseAnimationSpec = sheetCollapseAnimSpec,
            expandedY = sheetExpandedTargetY
        )
    }

    PlayerArtistNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )
    PlayerAlbumNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )

    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = playerContentExpansionFraction,
        initialOffsetY = initialFullPlayerOffsetY
    )
    val fullPlayerCompositionPolicy = rememberFullPlayerCompositionPolicy(
        currentSongId = infrequentPlayerState.currentSong?.id,
        currentSheetState = currentSheetContentState,
        expansionFraction = playerContentExpansionFraction
    )
    val shouldRenderFullPlayer = fullPlayerCompositionPolicy.shouldRenderFullPlayer

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float>? = null,
        initialVelocity: Float = 0f
    ) {
        sheetMotionController.animateTo(
            targetExpanded = targetExpanded,
            canExpand = showPlayerContentArea,
            collapsedY = sheetCollapsedTargetY,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity
        )
    }

    LaunchedEffect(sheetCollapsedTargetY, sheetMotionController) {
        sheetMotionController.syncToExpansion(sheetCollapsedTargetY)
    }

    var previousSheetState by remember { mutableStateOf<PlayerSheetState?>(null) }
    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        if (currentSheetContentState == previousSheetState && previousSheetState != null) return@LaunchedEffect
        previousSheetState = currentSheetContentState
        val targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        // Scale stays exactly 1f — no secondary bounce pulse.
        visualOvershootScaleY.snapTo(1f)
        animatePlayerSheet(targetExpanded = targetExpanded)
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = showPlayerContentArea,
        collapsedStateHorizontalPadding = collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress = predictiveBackCollapseProgress,
        predictiveBackSwipeEdge = predictiveBackSwipeEdge,
        currentSheetContentState = currentSheetContentState,
        playerContentExpansionFraction = playerContentExpansionFraction,
        containerHeight = containerHeight,
        currentSheetTranslationY = currentSheetTranslationY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        navBarStyle = navBarStyle,
        navBarCornerRadiusDp = navBarCornerRadius.dp,
        isNavBarHidden = isNavBarHidden,
        isPlaying = infrequentPlayerState.isPlaying,
        hasCurrentSong = infrequentPlayerState.currentSong != null,
        swipeDismissProgress = swipeDismissProgress
    )
    val currentBottomPadding = sheetVisualState.currentBottomPadding
    val playerContentAreaHeightPxProvider = sheetVisualState.playerContentAreaHeightPxProvider
    val visualSheetTranslationYProvider = sheetVisualState.visualSheetTranslationYProvider
    val overallSheetTopCornerRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider
    val playerContentActualBottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider
    val currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider
    val currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider

    val queueSheetState = rememberQueueSheetState(
        scope = scope,
        screenHeightPx = screenHeightPx,
        density = density,
        currentBottomPadding = currentBottomPadding,
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val showQueueSheet = queueSheetState.showQueueSheet
    val allowQueueSheetInteraction = queueSheetState.allowQueueSheetInteraction
    val queueSheetOffset = queueSheetState.queueSheetOffset
    val queueSheetHeightPx = queueSheetState.queueSheetHeightPx
    val queueHiddenOffsetPx = queueSheetState.queueHiddenOffsetPx
    val queueSheetController = queueSheetState.queueSheetController
    val onQueueSheetHeightPxChange = queueSheetState.onQueueSheetHeightPxChange

    val castSheetState = rememberCastSheetState()
    val sheetBackAndDragState = rememberSheetBackAndDragState(
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val velocityTracker = remember { VelocityTracker() }
    val sheetModalOverlayController = rememberSheetModalOverlayController(
        scope = scope,
        queueSheetController = queueSheetController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onCollapsePlayerSheet = { playerViewModel.collapsePlayerSheet() }
    )
    val pendingSaveQueueOverlay = sheetModalOverlayController.pendingSaveQueueOverlay
    val selectedSongForInfo = sheetModalOverlayController.selectedSongForInfo
    val canHandlePlayerBack by remember(
        sheetBackAndDragState.predictiveBackEnabled,
        showQueueSheet,
        castSheetState.showCastSheet,
        selectedSongForInfo
    ) {
        derivedStateOf {
            sheetBackAndDragState.predictiveBackEnabled &&
                !showQueueSheet &&
                !castSheetState.showCastSheet &&
                selectedSongForInfo == null
        }
    }
    val sheetActionHandlers = rememberSheetActionHandlers(
        scope = scope,
        navController = navController,
        playerViewModel = playerViewModel,
        sheetMotionController = sheetMotionController,
        queueSheetController = queueSheetController,
        sheetModalOverlayController = sheetModalOverlayController,
        sheetCollapsedTargetY = sheetCollapsedTargetY
    )

    val hapticFeedback = LocalHapticFeedback.current
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() },
        onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) }
    )

    QueueSheetRuntimeEffects(
        queueSheetController = queueSheetController,
        queueSheetOffset = queueSheetOffset,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        onTopEdgeReached = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    PlayerSheetPredictiveBackHandler(
        enabled = canHandlePlayerBack,
        playerViewModel = playerViewModel,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        sheetMotionController = sheetMotionController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onSwipeEdgeChanged = { playerViewModel.updatePredictiveBackSwipeEdge(it) },
        registrationKey = currentBackStackEntry?.id
    )

    val sheetOverlayState = rememberSheetOverlayState(
        density = density,
        showPlayerContentArea = showPlayerContentArea,
        hideMiniPlayer = hideMiniPlayer,
        showQueueSheet = showQueueSheet,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        screenHeightPx = screenHeightPx,
        castSheetOpenFraction = castSheetState.castSheetOpenFraction
    )
    val internalIsKeyboardVisible = sheetOverlayState.internalIsKeyboardVisible
    val actuallyShowSheetContent = sheetOverlayState.actuallyShowSheetContent
    val isQueueVisible = sheetOverlayState.isQueueVisible
    val bottomSheetOpenFraction = sheetOverlayState.bottomSheetOpenFraction
    val queueScrimAlpha = sheetOverlayState.queueScrimAlpha
    val shouldRenderQueueHost = true
    val isQueueTelemetryActive = showQueueSheet

    val isPlayerSheetHandlingBack = canHandlePlayerBack || (isQueueVisible && !internalIsKeyboardVisible)
    SideEffect {
        playerViewModel.playerSheetBackCoordinator.updatePlayerSheetHandlingBack(isPlayerSheetHandlingBack)
    }

    LaunchedEffect(showQueueSheet) {
        playerViewModel.updateQueueSheetVisibility(showQueueSheet)
    }
    LaunchedEffect(castSheetState.showCastSheet) {
        playerViewModel.updateCastSheetVisibility(castSheetState.showCastSheet)
    }
    DisposableEffect(playerViewModel) {
        onDispose {
            playerViewModel.updateQueueSheetVisibility(false)
            playerViewModel.updateCastSheetVisibility(false)
            playerViewModel.playerSheetBackCoordinator.updatePlayerSheetHandlingBack(false)
        }
    }

    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelMusicDarkTheme.current
    val currentSong = infrequentPlayerState.currentSong
    
    val colorPalette by playerViewModel.colorPalette.collectAsStateWithLifecycle()

    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = preparingSongId,
        systemColorScheme = MaterialTheme.colorScheme,
        colorPalette = colorPalette
    )
    val albumColorScheme = sheetThemeState.albumColorScheme
    val miniPlayerScheme = sheetThemeState.miniPlayerScheme
    val isPreparingPlayback = sheetThemeState.isPreparingPlayback
    val miniReadyAlpha = sheetThemeState.miniReadyAlpha
    val miniAppearScale = sheetThemeState.miniAppearScale
    val playerAreaBackground = sheetThemeState.playerAreaBackground
    // Elevation is only visible in the mini/collapsed state (expansion < 0.18).
    // miniReadyAlpha fades the shadow in during the initial song-appear animation.
    val visualCardShadowElevation by remember(showQueueSheet, miniReadyAlpha) {
        derivedStateOf {
            if (
                showQueueSheet ||
                playerContentExpansionFraction.isRunning ||
                playerContentExpansionFraction.value > 0.18f
            ) {
                0.dp
            } else {
                (3f * miniReadyAlpha.value).dp
            }
        }
    }

    val sheetInteractionState = rememberSheetInteractionState(
        scope = scope,
        velocityTracker = velocityTracker,
        sheetMotionController = sheetMotionController,
        playerContentExpansionFraction = playerContentExpansionFraction,
        currentSheetTranslationY = currentSheetTranslationY,
        visualOvershootScaleY = visualOvershootScaleY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        miniPlayerContentHeightPx = miniPlayerContentHeightPx,
        currentSheetContentState = currentSheetContentState,
        showPlayerContentArea = showPlayerContentArea,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        useSmoothCorners = useSmoothCorners,
        isDragging = sheetBackAndDragState.isDragging,
        onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
            if (animationSpec == null) {
                animatePlayerSheet(targetExpanded = targetExpanded)
            } else {
                animatePlayerSheet(
                    targetExpanded = targetExpanded,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity
                )
            }
        },
        onExpandSheetState = { playerViewModel.expandPlayerSheet() },
        onCollapseSheetState = { playerViewModel.collapsePlayerSheet() },
        onDraggingChange = sheetBackAndDragState.onDraggingChange,
        onDraggingPlayerAreaChange = sheetBackAndDragState.onDraggingPlayerAreaChange
    )

    if (!actuallyShowSheetContent || !showPlayerContentArea) return

    val playerSheetSemanticsDescription = remember(
        currentSheetContentState,
        infrequentPlayerState.currentSong?.title
    ) {
        "PixelMusic player sheet ${currentSheetContentState.name.lowercase()} " +
            (infrequentPlayerState.currentSong?.title ?: "")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, visualSheetTranslationYProvider().roundToInt()) }
            .height(containerHeight),
        shadowElevation = 0.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = currentBottomPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Modifier.layout reads from pixel lambdas during the layout phase —
                            // this avoids recomposition per drag frame (unlike derivedStateOf).
                            // Layout still runs per-frame, but composition is skipped entirely.
                            .layout { measurable, constraints ->
                                val targetHeightPx = playerContentAreaHeightPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val endPaddingPx = currentHorizontalPaddingEndPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    .coerceAtLeast(0)
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = innerWidth,
                                        maxWidth = innerWidth,
                                        minHeight = targetHeightPx,
                                        maxHeight = targetHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, targetHeightPx) {
                                    placeable.placeRelative(startPaddingPx, 0)
                                }
                            }
                            .miniPlayerDismissHorizontalGesture(
                                enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                handler = miniDismissGestureHandler
                            )
                            .graphicsLayer {
                                translationX = offsetAnimatable.value
                                // Immersive dismiss: tilt, fade, and scale-down as card is swiped
                                rotationZ = dismissRotation.value
                                alpha = miniReadyAlpha.value * dismissAlpha.value
                                scaleX = dismissScale.value * miniAppearScale.value
                                scaleY = visualOvershootScaleY.value * dismissScale.value * miniAppearScale.value
                                // Pivot at bottom-center: card tilts as if held at its base
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            // Always apply Modifier.shadow with the dynamic elevation
                            // (0.dp renders nothing). Keeping the modifier chain
                            // structurally stable avoids the costly relayout/redraw
                            // restructure when the elevation crosses 0.dp during
                            // expand/collapse or right after play/pause.
                            .shadow(
                                elevation = visualCardShadowElevation,
                                shape = sheetInteractionState.playerShadowShape,
                                clip = false
                            )
                            .background(
                                color = playerAreaBackground,
                                shape = sheetInteractionState.playerShadowShape
                            )
                            .clip(sheetInteractionState.playerShadowShape)
                            .semantics {
                                contentDescription = playerSheetSemanticsDescription
                            }
                            .playerSheetVerticalDragGesture(
                                enabled = sheetInteractionState.canDragSheet,
                                handler = sheetInteractionState.sheetVerticalDragGestureHandler
                            )
                            .clickable(
                                enabled = tapBackgroundClosesPlayer || currentSheetContentState == PlayerSheetState.COLLAPSED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playerViewModel.togglePlayerSheetState()
                            }
                    ) {
                        val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
                        if (playerConfig.useBlurEffects && playerConfig.nowPlayingBlurEnabled && infrequentPlayerState.currentSong != null) {
                            FullPlayerAtmosphereBackground(
                                currentSong = infrequentPlayerState.currentSong,
                                albumColorScheme = albumColorScheme,
                                isDarkTheme = isDarkTheme,
                                expansionFractionProvider = { playerContentExpansionFraction.value },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        UnifiedPlayerMiniAndFullLayers(
                            currentSong = infrequentPlayerState.currentSong,
                            miniPlayerScheme = miniPlayerScheme,
                            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
                            infrequentPlayerState = infrequentPlayerState,
                            isCastConnecting = isCastConnecting,
                            isPreparingPlayback = isPreparingPlayback,
                            playerContentExpansionFraction = playerContentExpansionFraction,
                            albumColorScheme = albumColorScheme,
                            bottomSheetOpenFraction = bottomSheetOpenFraction,
                            fullPlayerVisualState = fullPlayerVisualState,
                            containerHeight = containerHeight,
                            currentQueueSourceName = currentQueueSourceName,
                            currentSheetContentState = currentSheetContentState,
                            carouselStyle = carouselStyle,
                            fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                            isSheetDragGestureActive = sheetBackAndDragState.isDraggingPlayerArea,
                            playerViewModel = playerViewModel,
                            currentPositionProvider = positionToDisplayProvider,
                            isFavorite = isFavorite,
                            shouldRenderFullPlayer = shouldRenderFullPlayer,
                            onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                            onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                            onQueueDrag = sheetActionHandlers.dragQueueBy,
                            onQueueRelease = sheetActionHandlers.endQueueDrag,
                            onShowCastClicked = castSheetState.openCastSheet
                        )
                    }
                }

                UnifiedPlayerPrewarmLayer(
                    prewarmFullPlayer = prewarmFullPlayer && !shouldRenderFullPlayer,
                    currentSong = infrequentPlayerState.currentSong,
                    containerHeight = containerHeight,
                    albumColorScheme = albumColorScheme,
                    currentQueueSourceName = currentQueueSourceName,
                    infrequentPlayerState = infrequentPlayerState,
                    carouselStyle = carouselStyle,
                    fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = positionToDisplayProvider,
                    isCastConnecting = isCastConnecting,
                    isFavorite = isFavorite,
                    onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                    onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                    onQueueDrag = sheetActionHandlers.dragQueueBy,
                    onQueueRelease = sheetActionHandlers.endQueueDrag
                )
            }

            BackHandler(enabled = isQueueVisible && selectedSongForInfo == null) {
                sheetActionHandlers.animateQueueSheet(false)
            }

            UnifiedPlayerQueueAndSongInfoHost(
                shouldRenderHost = shouldRenderQueueHost,
                isQueueTelemetryActive = isQueueTelemetryActive,
                albumColorScheme = albumColorScheme,
                queueScrimAlpha = queueScrimAlpha,
                showQueueSheet = showQueueSheet,
                queueHiddenOffsetPx = queueHiddenOffsetPx,
                queueSheetOffset = queueSheetOffset,
                queueSheetHeightPx = queueSheetHeightPx,
                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                configurationResetKey = configuration,
                currentQueueSourceName = currentQueueSourceName,
                infrequentPlayerState = infrequentPlayerState,
                playerViewModel = playerViewModel,
                selectedSongForInfo = selectedSongForInfo,
                onSelectedSongForInfoChange = sheetActionHandlers.onSelectedSongForInfoChange,
                onAnimateQueueSheet = sheetActionHandlers.animateQueueSheet,
                onBeginQueueDrag = sheetActionHandlers.beginQueueDrag,
                onDragQueueBy = sheetActionHandlers.dragQueueBy,
                onEndQueueDrag = sheetActionHandlers.endQueueDrag,
                onLaunchSaveQueueOverlay = sheetActionHandlers.onLaunchSaveQueueOverlay,
                onNavigateToAlbum = sheetActionHandlers.onNavigateToAlbum,
                onNavigateToArtist = sheetActionHandlers.onNavigateToArtist,
                onNavigateToGenre = sheetActionHandlers.onNavigateToGenre
            )
        }
    }

    UnifiedPlayerCastLayer(
        showCastSheet = castSheetState.showCastSheet,
        internalIsKeyboardVisible = internalIsKeyboardVisible,
        albumColorScheme = albumColorScheme,
        playerViewModel = playerViewModel,
        onDismiss = castSheetState.dismissCastSheet,
        onExpansionChanged = castSheetState.onCastExpansionChanged
    )

    UnifiedPlayerSaveQueueLayer(
        pendingOverlay = pendingSaveQueueOverlay,
        onDismissOverlay = { sheetModalOverlayController.dismissSaveQueueOverlay() }
    )
}
