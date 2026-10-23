package com.unshoo.pixelmusic.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Explore.route,
    Screen.Search.route,
    Screen.Library.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?, isFloatingPill: Boolean = false): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Explore.route -> 1
    Screen.Search.route -> if (isFloatingPill) 3 else 2
    Screen.Library.route -> if (isFloatingPill) 2 else 3
    else -> null
}


