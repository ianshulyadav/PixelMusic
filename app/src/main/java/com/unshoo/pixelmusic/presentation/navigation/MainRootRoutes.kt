package com.unshoo.pixelmusic.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Explore.route,
    Screen.Search.route,
    Screen.Library.route -> true
    else -> false
}

