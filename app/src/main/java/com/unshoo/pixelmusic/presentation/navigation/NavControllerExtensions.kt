package com.unshoo.pixelmusic.presentation.navigation

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

// Lifecycle gate for navigation controller extensions.
//
// 1. `lifecycle == null -> false`. Returns false when there is no current
//    back stack entry; navigating with a null entry (during graph setup races) is
//    the case this gate catches.
//
// 2. We allow navigation if the current entry is at least STARTED.
//    Deduplication of rapid double navigation is already handled correctly by
//    `launchSingleTop = true` and by the ScreenWrapper input gate, which stops
//    touches from reaching the screen that is being popped.
private fun NavController.isReadyForNavigation(): Boolean {
    return runCatching {
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    }.getOrDefault(false)
}

/**
 * Back-press equivalent of [navigateSafely]: only pops while the current entry is
 * at least STARTED, so a second back press that sneaks in during the pop transition
 * cannot pop a *second* destination (the classic "double back -> blank screen" bug).
 */
fun NavController.popBackStackSafely(): Boolean {
    if (!isReadyForNavigation()) return false
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
    if (!isReadyForNavigation()) return false
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
    if (!isReadyForNavigation()) return false
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
    if (!isReadyForNavigation()) return false
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
    if (!isReadyForNavigation()) return false
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
