package com.unshoo.pixelmusic.presentation.navigation

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

// STARTED does not mean a navigation transition has settled. Coalesce operations
// while an outgoing destination is being disposed to avoid overlapping stack mutations.
private const val NAVIGATION_DEBOUNCE_MS = 350L
private var lastNavigationUptimeMs = 0L

@Synchronized
private fun NavController.claimNavigationSlot(): Boolean {
    val now = SystemClock.uptimeMillis()
    if (now - lastNavigationUptimeMs < NAVIGATION_DEBOUNCE_MS) return false
    val ready = runCatching {
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    }.getOrDefault(false)
    if (ready) lastNavigationUptimeMs = now
    return ready
}

/**
 * Back-press equivalent of [navigateSafely]: only pops while the current entry is
 * at least STARTED, so a second back press that sneaks in during the pop transition
 * cannot pop a *second* destination (the classic "double back -> blank screen" bug).
 */
fun NavController.popBackStackSafely(): Boolean {
    if (!claimNavigationSlot()) return false
    return try {
        popBackStack()
    } catch (e: IllegalStateException) {
        Log.w("NavController", "Back navigation ignored during a stack transition", e)
        false
    } catch (e: Exception) {
        Log.e("NavController", "Safe popBackStack failed", e)
        false
    }
}

fun NavController.navigateSafely(route: String): Boolean {
    if (!claimNavigationSlot()) return false
    if (isMainRootRoute(route)) {
        return navigateToTopLevelSafely(route)
    }
    return try {
        navigate(route) {
            launchSingleTop = true
        }
        true
    } catch (e: Exception) {
        Log.e("NavController", "Safe navigation failed for route: $route", e)
        false
    }
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!claimNavigationSlot()) return false
    return try {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
        true
    } catch (e: Exception) {
        Log.e("NavController", "Safe navigation failed for route: $route", e)
        false
    }
}

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!claimNavigationSlot()) return false
    return try {
        navigate(route) {
            launchSingleTop = false
            popUpTo(patternToPop) {
                inclusive = true
            }
            builder()
        }
        true
    } catch (e: Exception) {
        Log.e("NavController", "Safe navigation replacing failed for route: $route", e)
        false
    }
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    if (!claimNavigationSlot()) return false
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    return try {
        navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        true
    } catch (e: Exception) {
        Log.e("NavController", "Safe top-level navigation failed for route: $route", e)
        false
    }
}
