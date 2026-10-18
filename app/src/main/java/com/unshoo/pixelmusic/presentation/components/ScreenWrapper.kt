package com.unshoo.pixelmusic.presentation.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.currentBackStackEntryAsState
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.navigation.isMainRootRoute


@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenWrapper(
    navController: androidx.navigation.NavController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle State — initialized from the entry's real state; previously the
    // initial value was hardcoded to false and then "fixed" by writing
    // `isResumed = true` in the middle of composition, which is undefined
    // behavior (a state write during the composition pass).
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Visible entries is the public Navigation API designed for transition-aware stacking.
    // It stays stable while entries are entering / exiting, unlike the restricted currentBackStack.
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val myEntry = lifecycleOwner as? androidx.navigation.NavBackStackEntry
    val myIndex = visibleEntries.indexOfFirst { it.id == myEntry?.id }
    val topIndex = visibleEntries.indexOfLast {
        it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    // currentBackStackEntry updates synchronously with navigate()/popBackStack(), so it
    // identifies the destination the user is moving TO. The incoming screen during a pop
    // shares STARTED state with the outgoing one for a few frames; without this check the
    // dim overlay would flash onto the screen the user is navigating back to.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isNavigationTarget = myEntry != null && currentBackStackEntry?.id == myEntry.id
    val myRoute = myEntry?.destination?.route
    val isMainRootScreen = isMainRootRoute(myRoute)
    val hasVisibleNonMainRootScreen = visibleEntries.any { entry ->
        entry.destination.route?.let { route -> !isMainRootRoute(route) } == true
    }
    val shouldRunDepthEffects = !isMainRootScreen || hasVisibleNonMainRootScreen

    // ── INPUT ROUTING (hit-testing) FIX ───────────────────────────────────────────
    // While a transition runs, Navigation-Compose keeps BOTH destinations composed:
    //  • push: the old screen sits underneath, partially visible (parallax).
    //  • pop : the popped screen stays ON TOP of the screen you returned to until
    //          its exit transition fully settles and the entry is disposed.
    // Compose hit-testing delivers a tap to the topmost subtree first, so during
    // that window a tap meant for, say, "Appearance" on the Settings list lands on
    // the still-alive popped screen's row at the same coordinates ("App Theme")
    // and navigates to the wrong screen. The zombie is often already fully faded,
    // which is why it looks like "the previous screen still receives touch events".
    //
    // Hard rule: an entry may receive input only while it is the current
    // navigation target (the screen the user is moving TO) or it is RESUMED
    // (settled on top). Everything else — a background screen under a push and a
    // zombie screen under a pop — gets a touch-eating overlay on top of its
    // content for exactly as long as it is not a valid touch target. No timers,
    // no guessing: the gate clears itself the frame the lifecycle/nav state flips,
    // unlike the previous TRANSITION_DURATION-delayed overlay hack.
    // ── INPUT ROUTING (hit-testing) FIX ───────────────────────────────────────────
    // Only block input if this entry is strictly in the background (covered by a
    // higher entry in the visible stack) and is NOT the navigation target.
    // The incoming screen and topmost screen must never have their touches consumed.
    val isInputBlocked = myEntry != null && !isNavigationTarget && myIndex != -1 && topIndex != -1 && myIndex < topIndex

    // Dim Logic:
    // If I am BACKGROUND (myIndex < topIndex) -> Dim.
    // If I am TOP (myIndex == topIndex) -> Clear.
    // If I am EXITING (myIndex > topIndex, effectively in front during pop) -> Clear.
    // If I am the navigation target (incoming during a pop) -> Clear.
    // Created entries are on their way out, so we keep them clear instead of dimming them for a frame.
    val shouldDim = remember(visibleEntries, myEntry, myIndex, topIndex, isNavigationTarget) {
        !isNavigationTarget &&
            myIndex != -1 &&
            topIndex != -1 &&
            myIndex < topIndex &&
            myEntry?.lifecycle?.currentState != Lifecycle.State.CREATED
    }

    // Declarative Animations
    // Radius: Only background screens being covered by a sheet/detail screen should round their corners (20dp).
    // Foreground screens (active or entering) remain edge-to-edge without snapping.
    val targetRadius = if (shouldRunDepthEffects && shouldDim) 20f else 0f
    // M3 Expressive effects springs — critically damped (no overshoot), settling ~250ms.
    val motionScheme = MaterialTheme.motionScheme

    val cornerRadius by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cornerRadius"
    )

    // Dim: If strictly behind Top -> 0.4f. Else -> 0f.
    val targetDim = if (shouldRunDepthEffects && shouldDim) 0.4f else 0f
    val dimAlpha by animateFloatAsState(
        targetValue = targetDim,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "dimAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keep both the graphicsLayer modifier AND its compositingStrategy stable across
            // the full lifecycle of the screen. Toggling the strategy between Auto and
            // Offscreen mid-transition (when cornerRadius crosses the threshold) causes the
            // RenderNode's rendering mode to flip for one frame, producing a subtle flash on
            // the outgoing screen right as the animation starts. Main root tab switches are
            // the exception: Home/Search/Library keep the same slide/fade transition, but skip
            // the expensive offscreen depth layer while no deeper screen is visible.
            .graphicsLayer {
                // Smooth shape clipping without forcing GPU Offscreen framebuffer allocation
                if (shouldRunDepthEffects && cornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(cornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim Layer Overlay
        // Always composed with alpha-driven visibility instead of a conditional node.
        // Conditionally adding/removing this Box when dimAlpha crosses 0 added a node to
        // the composition tree mid-transition and contributed to the outgoing-screen flash.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dimAlpha }
                .background(Color.Black)
        )

        // Input gate overlay (see comment above). As the topmost child of this
        // entry's subtree it consumes every pointer change on the Main pass, so the
        // screen's own clickables never see an unconsumed down event and can't
        // fire while this entry is leaving/covered. Because it only exists while
        // the entry is not a valid touch target, it also can never swallow the *next*
        // screen's taps once this entry is disposed.
        if (isInputBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}
