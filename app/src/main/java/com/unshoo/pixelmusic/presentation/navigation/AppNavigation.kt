package com.unshoo.pixelmusic.presentation.navigation

import DelimiterConfigScreen
import com.unshoo.pixelmusic.presentation.screens.WordDelimiterConfigScreen
import com.unshoo.pixelmusic.presentation.screens.FilterKeywordsConfigScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.R
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.unshoo.pixelmusic.data.preferences.LaunchTab
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.presentation.screens.AlbumDetailScreen
import com.unshoo.pixelmusic.presentation.screens.AccountsScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistDetailScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.DailyMixScreen
import com.unshoo.pixelmusic.presentation.screens.EditTransitionScreen
import com.unshoo.pixelmusic.presentation.screens.EasterEggScreen
import com.unshoo.pixelmusic.presentation.screens.ExperimentalSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.HomeScreen
import com.unshoo.pixelmusic.presentation.screens.ExploreScreen
import com.unshoo.pixelmusic.presentation.screens.LibraryScreen
import com.unshoo.pixelmusic.presentation.screens.MashupScreen
import com.unshoo.pixelmusic.presentation.screens.NavBarCornerRadiusScreen
import com.unshoo.pixelmusic.presentation.screens.PaletteStyleSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.BlurSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.PlaylistDetailScreen
import com.unshoo.pixelmusic.presentation.screens.FeedPlaylistDetailScreen
import com.unshoo.pixelmusic.presentation.screens.RecentlyPlayedScreen
import com.unshoo.pixelmusic.presentation.screens.QuickPicksAllScreen
import com.unshoo.pixelmusic.presentation.screens.SmartMixScreen

import com.unshoo.pixelmusic.presentation.screens.AboutScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistAlbumsAllScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistSongsAllScreen
import com.unshoo.pixelmusic.presentation.screens.SearchScreen
import com.unshoo.pixelmusic.presentation.screens.StatsScreen
import com.unshoo.pixelmusic.presentation.screens.SettingsScreen
import com.unshoo.pixelmusic.presentation.screens.SettingsCategoryScreen
import com.unshoo.pixelmusic.presentation.screens.EqualizerScreen
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.unshoo.pixelmusic.presentation.components.ScreenWrapper

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = launchTabToRoute(userPreferencesRepository.launchTabFlow.first())
    }

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            composable(
                Screen.Home.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    HomeScreen(
                        navController = navController, 
                        paddingValuesParent = paddingValues, 
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    )
                }
            }
            composable(
                Screen.Explore.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExploreScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        paddingValuesParent = paddingValues
                    )
                }
            }
            composable(
                Screen.Search.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    )
                }
            }
            composable(
                Screen.Library.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
            }
            composable(
                Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable(
                Screen.Accounts.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AccountsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenYoutubeAuth = {
                            navController.navigateSafely(Screen.YoutubeAuth.route)
                        },
                        onOpenLastfmSettings = {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute("lastfm"))
                        }
                    )
                }
            }
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val categoryId = backStackEntry.arguments?.getString("categoryId")
                    if (categoryId != null) {
                        androidx.compose.runtime.key(categoryId) {
                            SettingsCategoryScreen(
                                categoryId = categoryId,
                                navController = navController,
                                playerViewModel = playerViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
            composable(
                Screen.PaletteStyle.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.BlurSettings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    BlurSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.Experimental.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExperimentalSettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.DailyMixScreen.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DailyMixScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.SmartMix.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SmartMixScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.RecentlyPlayed.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    RecentlyPlayedScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.QuickPicksAll.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    QuickPicksAllScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.Stats.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    StatsScreen(
                        navController = navController
                    )
                }
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")
                val playlistViewModel: PlaylistViewModel = hiltViewModel()
                if (playlistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playerViewModel = playerViewModel,
                            playlistViewModel = playlistViewModel,
                            onBackClick = { navController.popBackStack() },
                            onDeletePlayListClick = { navController.popBackStack() },
                            navController = navController
                        )
                    }
                }
            }

            composable(
                route = Screen.FeedPlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val encodedId = backStackEntry.arguments?.getString("playlistId")
                val playlistId = encodedId?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                if (playlistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        FeedPlaylistDetailScreen(
                            playlistId = playlistId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }

            composable(
                Screen.DJSpace.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    MashupScreen()
                }
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")
                if (albumId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        AlbumDetailScreen(
                            albumId = albumId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId")
                if (artistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        ArtistDetailScreen(
                            artistId = artistId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistAlbumsAll.route,
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                ),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
                val albumType = backStackEntry.arguments?.getString("type") ?: "albums"
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistAlbumsAllScreen(
                        artistId = artistId,
                        type = albumType,
                        navController = navController
                    )
                }
            }
            composable(
                route = Screen.ArtistSongsAll.route,
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType }
                ),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSongsAllScreen(
                        artistId = artistId,
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                "nav_bar_corner_radius",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NavBarCornerRadiusScreen(navController)
                }
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    nullable = true
                }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EditTransitionScreen(navController = navController)
                }
            }
            composable(
                Screen.About.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AboutScreen(
                        navController = navController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.EasterEgg.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EasterEggScreen(
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() },
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSettingsScreen(navController = navController)
                }
            }
            composable(
                Screen.DelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    WordDelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.FilterKeywordsConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    FilterKeywordsConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.Equalizer.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EqualizerScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.DeviceCapabilities.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.DeviceCapabilitiesScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }

            composable(
                Screen.YoutubeAuth.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.youtube.AuthScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ── Lossless sources: provider sign-in screens (ArchiveTune port) ──
            composable(
                Screen.TidalLogin.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.lossless.TidalLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.DeezerLogin.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.lossless.DeezerLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.QobuzLogin.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.lossless.QobuzLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.AppleMusicLogin.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.lossless.AppleMusicLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                Screen.CloudDownloads.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.CloudDownloadsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }

            composable(
                Screen.SmartStorage.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.storage.SmartStorageScreen(
                        navController = navController
                    )
                }
            }
        }
    }
}

private fun launchTabToRoute(tab: String): String = when (tab) {
    LaunchTab.EXPLORE -> Screen.Explore.route
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

private enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

private const val BOTTOM_NAV_TRANSITION_DURATION = 240
private const val BOTTOM_NAV_FADE_OUT_DURATION = 150
private const val BOTTOM_NAV_FADE_IN_DURATION = 180

private fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition {
    return when (mainRootDirection(fromRoute, toRoute)) {
        MainRootDirection.FORWARD -> {
            slideInHorizontally(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 0.82f
                ),
                initialOffsetX = { (it * 0.08f).toInt() }
            ) + scaleIn(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 0.82f
                ),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin.Center
            ) + fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    delayMillis = 85,
                    easing = EmphasizedDecelerateEasing
                )
            )
        }
        MainRootDirection.BACKWARD -> {
            slideInHorizontally(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 0.82f
                ),
                initialOffsetX = { -(it * 0.08f).toInt() }
            ) + scaleIn(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 0.82f
                ),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin.Center
            ) + fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    delayMillis = 85,
                    easing = EmphasizedDecelerateEasing
                )
            )
        }
        null -> fallback
    }
}

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition {
    return when (mainRootDirection(fromRoute, toRoute)) {
        MainRootDirection.FORWARD -> {
            slideOutHorizontally(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 1f
                ),
                targetOffsetX = { -(it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 1f
                ),
                targetScale = 0.92f,
                transformOrigin = TransformOrigin.Center
            ) + fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 160,
                    easing = EmphasizedAccelerateEasing
                )
            )
        }
        MainRootDirection.BACKWARD -> {
            slideOutHorizontally(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 1f
                ),
                targetOffsetX = { (it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = spring(
                    stiffness = ExpressiveSprings.DefaultSpatialStiffness,
                    dampingRatio = 1f
                ),
                targetScale = 0.92f,
                transformOrigin = TransformOrigin.Center
            ) + fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 160,
                    easing = EmphasizedAccelerateEasing
                )
            )
        }
        null -> fallback
    }
}


