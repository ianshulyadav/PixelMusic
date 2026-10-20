@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.unshoo.pixelmusic.presentation.screens

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.CollagePattern
import com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode
import com.unshoo.pixelmusic.presentation.components.AlbumArtCollage
import com.unshoo.pixelmusic.presentation.components.BetaInfoBottomSheet
import com.unshoo.pixelmusic.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.unshoo.pixelmusic.presentation.components.ChangelogBottomSheet
import com.unshoo.pixelmusic.presentation.components.DailyMixSection
import com.unshoo.pixelmusic.presentation.components.HomeGradientTopBar
import com.unshoo.pixelmusic.presentation.components.HomeOptionsBottomSheet
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.QuickPicksSection
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.presentation.components.RecentlyPlayedSection
import com.unshoo.pixelmusic.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.unshoo.pixelmusic.presentation.viewmodel.QuickPicksViewModel
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.StatsOverviewCard
import com.unshoo.pixelmusic.presentation.viewmodel.FavoriteArtistReleasesViewModel
import com.unshoo.pixelmusic.presentation.components.FavoriteArtistReleasesSection
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.resolveMainScreenBottomGradientHeight
import com.unshoo.pixelmusic.presentation.model.collectRecentlyPlayedSongIds
import com.unshoo.pixelmusic.presentation.model.mapRecentlyPlayedSongs
import com.unshoo.pixelmusic.presentation.components.subcomps.PlayingEqIcon
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.components.StreamingProviderSheet
import com.unshoo.pixelmusic.presentation.telegram.auth.TelegramLoginActivity
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SettingsViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.StatsViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.AccountsViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.ExternalServiceAccount
import com.unshoo.pixelmusic.ui.theme.ExpTitleTypography
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

// Modern HomeScreen with collapsible top bar and staggered grid layout
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statsViewModel: StatsViewModel = hiltViewModel(),
    quickPicksViewModel: QuickPicksViewModel = hiltViewModel(),
    favoriteArtistReleasesViewModel: FavoriteArtistReleasesViewModel = hiltViewModel(),
    accountsViewModel: AccountsViewModel = hiltViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, quickPicksViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                quickPicksViewModel.refreshIfStale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val isOnline by playerViewModel.isOnline.collectAsStateWithLifecycle()
    val isLocalMode by playerViewModel.localModeEnabled.collectAsStateWithLifecycle()
    val isEffectivelyOnline = isOnline && !isLocalMode
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val accountsUiState by accountsViewModel.uiState.collectAsStateWithLifecycle()
    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val discoverYoutubeSongs = remember(exploreUiState.homePageSections, isEffectivelyOnline) {
        if (!isEffectivelyOnline) {
            emptyList()
        } else {
            exploreUiState.homePageSections
                .filter { section ->
                    val title = section.title
                    title.contains("daily discover", ignoreCase = true) ||
                    (title.contains("discover", ignoreCase = true) && title.contains("daily", ignoreCase = true))
                }
                .flatMap { section ->
                    section.items.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()
                        .map { songItem -> songItem.toNativeSong() }
                }
                .distinctBy { it.id }
        }
    }
    val dailyMixSongsRaw by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val dailyMixSongs = remember(dailyMixSongsRaw, isEffectivelyOnline) {
        if (!isEffectivelyOnline) {
            dailyMixSongsRaw.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it) }.toImmutableList()
        } else {
            dailyMixSongsRaw
        }
    }
    val mergedDailyMixSongs = remember(dailyMixSongs, discoverYoutubeSongs, isEffectivelyOnline) {
        if (!isEffectivelyOnline) {
            dailyMixSongs
        } else {
            (dailyMixSongs + discoverYoutubeSongs).distinctBy { it.id }.toImmutableList()
        }
    }
    LaunchedEffect(isEffectivelyOnline) {
        if (!isEffectivelyOnline && dailyMixSongs.isEmpty()) {
            playerViewModel.forceUpdateDailyMix()
        }
    }
    val userName = remember(accountsUiState.userName) {
        val rawName = accountsUiState.userName
        if (!rawName.isNullOrBlank()) {
            val cleanName = if (rawName.startsWith("@")) rawName.substring(1) else rawName
            val baseName = if (!cleanName.contains("@")) {
                cleanName
            } else {
                cleanName.substringBefore("@")
            }
            val formattedName = baseName.split(".", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            val firstName = formattedName.split(" ").firstOrNull()?.trim().orEmpty()
            if (firstName.length >= 3) {
                formattedName.trim()
            } else {
                if (firstName.isNotEmpty()) firstName else formattedName.trim()
            }
        } else {
            null
        }
    }
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val quickPicksDisplayMode by playerViewModel.quickPicksDisplayMode.collectAsStateWithLifecycle()

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs, isEffectivelyOnline) {
        val baseList = when {
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            else -> homeMixPreviewSongs
        }
        if (!isEffectivelyOnline) {
            baseList.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it) }.toImmutableList()
        } else {
            baseList
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty()) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        // Map even when DB has not hydrated YT songs yet — history entries carry title/artist/
        // thumbnail for optimistic + YT Music merged items (SpatialFlow-style live recents).
        if (recentlyPlayedSourceSongs == null && playbackHistory.isEmpty()) {
            return@remember emptyList()
        }
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = recentlyPlayedSourceSongs.orEmpty(),
            maxItems = 64
        )
    }
    val recentlyPlayedSongs = remember(latestRecentlyPlayedSongs, isEffectivelyOnline) {
        if (!isEffectivelyOnline) {
            latestRecentlyPlayedSongs.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it.song) }
        } else {
            latestRecentlyPlayedSongs
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = stringResource(R.string.home_todays_mix_subtitle)

    // 2) Observar sólo el currentSong (o null) para saber si mostrar padding
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.currentSong }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    // 3) Observe shuffle state for sync
    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarHeightOffsetRaw by playerViewModel.navBarHeightOffset.collectAsStateWithLifecycle()
    val navBarHeightOffset = navBarHeightOffsetRaw.dp
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode, navBarHeightOffset)

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val betaSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    LocalContext.current

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()
    val quickPicksRaw by quickPicksViewModel.quickPicks.collectAsStateWithLifecycle()
    val quickPicks = remember(quickPicksRaw, isEffectivelyOnline) {
        if (!isEffectivelyOnline) {
            quickPicksRaw.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it) }
        } else {
            quickPicksRaw
        }
    }
    val artistReleasesRaw by favoriteArtistReleasesViewModel.releases.collectAsStateWithLifecycle()
    val artistReleases = if (isEffectivelyOnline) artistReleasesRaw else emptyList()
    var isRefreshing by remember { mutableStateOf(false) }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } } // Adopted from PixelMusic: higher threshold = header flip-flops less
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    // Persist the scroll position across navigation away/back. The Stats card and other
    // conditional sections can shift indices while data re-emits when returning, which
    // would otherwise leave the list scrolled to the wrong place or jump to the top.
    var savedScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var needsScrollRestore by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, listState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                savedScrollIndex = listState.firstVisibleItemIndex
                savedScrollOffset = listState.firstVisibleItemScrollOffset
                needsScrollRestore = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        needsScrollRestore,
        yourMixSongs.isNotEmpty(),
        dailyMixSongs.isNotEmpty(),
        recentlyPlayedSongs.size,
        homeStatsOverview
    ) {
        if (!needsScrollRestore) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) return@LaunchedEffect
        val targetIndex = savedScrollIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
        listState.scrollToItem(targetIndex, savedScrollOffset)
        needsScrollRestore = false
    }

    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val shouldShowCleanInstallDisclaimer =
        settingsUiState.beta05CleanInstallDisclaimerDismissed == false &&
            !cleanInstallDisclaimerDismissedThisSession

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    onMoreOptionsClick = {
                        showChangelogBottomSheet = true
                    },
                    onBetaClick = {
                        showBetaInfoBottomSheet = true
                    },
                    onTelegramClick = {
                         showStreamingProviderSheet = true
                    },
                    onMenuClick = {
                        // onOpenSidebar() // Disabled
                    },
                    isScrolled = isScrolledPastThreshold.value,
                    floatingHeaderEnabled = settingsUiState.floatingHeaderBarEnabled
                )
            }
        ) { innerPadding ->
            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    quickPicksViewModel.refresh()
                    playerViewModel.forceUpdateDailyMix()
                    scope.launch {
                        delay(1800)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = innerPadding.calculateTopPadding() + 8.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + 38.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (quickPicksDisplayMode != QuickPicksDisplayMode.CARD) {
                    item(
                        key = "home_greeting",
                        contentType = "home_greeting"
                    ) {
                        HomeGreetingHeader(
                            userName = userName
                        )
                    }
                }

                // Quick Picks (above Your Mix, shown when there is engagement data)
                if (quickPicks.isNotEmpty()) {
                    item(
                        key = "quick_picks_section",
                        contentType = "quick_picks_section"
                    ) {
                        QuickPicksSection(
                            songs = quickPicks,
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(song, quickPicks, "Quick Picks")
                            },
                            onSeeAllClick = {
                                navController.navigateSafely(Screen.QuickPicksAll.route)
                            },
                            currentSongId = currentSong?.id,
                            displayMode = quickPicksDisplayMode
                        )
                    }
                } else if (shouldShowYourMixLoadingPlaceholder) {
                    item(
                        key = "quick_picks_loading",
                        contentType = "quick_picks_loading"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                }

                if (yourMixSongs.isEmpty()) {
                    item(
                        key = "your_mix_placeholder",
                        contentType = "your_mix_placeholder"
                    ) {
                        if (shouldShowYourMixLoadingPlaceholder) {
                            YourMixLoadingPlaceholder()
                        } else {
                            YourMixEmptyPlaceholder(
                                onRefresh = {
                                    homePlaceholderRefreshGeneration++
                                    settingsViewModel.refreshLibrary()
                                    playerViewModel.forceUpdateDailyMix()
                                }
                            )
                        }
                    }
                } else {
                    item(
                        key = "your_mix_header",
                        contentType = "your_mix_header"
                    ) {
                        YourMixHeader(
                            song = yourMixSong,
                            isShuffleEnabled = isShuffleEnabled,
                             onPlayShuffled = {
                                 val songsToUse = quickPicks.ifEmpty { yourMixSongs }
                                 if (songsToUse.isNotEmpty()) {
                                     playerViewModel.playSongsShuffled(songsToUse, "Your Mix")
                                 }
                             }
                        )
                    }
                }

                // Collage
                if (yourMixSongs.isNotEmpty() && settingsUiState.collageEnabled) {
                    item(
                        key = "album_art_collage",
                        contentType = "album_art_collage"
                    ) {
                        val basePattern = settingsUiState.collagePattern
                        val isAutoRotate = settingsUiState.collageAutoRotate
                        val patterns = remember { CollagePattern.entries }

                        val activePattern = if (isAutoRotate) {
                            var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                            LaunchedEffect(Unit) { rotationIndex++ }
                            remember(rotationIndex) {
                                patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                            }
                        } else {
                            basePattern
                        }

                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            songs = yourMixSongs,
                            padding = 14.dp,
                            height = 400.dp,
                            pattern = activePattern,
                            onSongClick = { song ->
                                if (usesFallbackHomeMix) {
                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                } else {
                                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                }
                            }
                        )
                    }
                }

                // Daily Mix (with YouTube Daily Discover songs merged)
                if (mergedDailyMixSongs.isNotEmpty()) {
                    item(
                        key = "daily_mix_section",
                        contentType = "daily_mix_section"
                    ) {
                        DailyMixSection(
                            songs = mergedDailyMixSongs,
                            onClickOpen = {
                                navController.navigateSafely(Screen.DailyMixScreen.route)
                            },
                            onNavigateToAlbum = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.AlbumDetail.createRoute(song.albumId),
                                    patternToPop = Screen.AlbumDetail.route
                                )
                            },
                            onNavigateToArtist = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRoute(
                                        song.artists.firstOrNull()?.id?.takeIf { it != 0L && it != -1L }?.toString()
                                            ?: song.artists.firstOrNull()?.name
                                            ?: song.artist
                                    ),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                            },
                            onNavigateToGenre = {},
                            playerViewModel = playerViewModel
                        )
                    }
                }

                if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                    item(
                        key = "recently_played_section",
                        contentType = "recently_played_section"
                    ) {
                        RecentlyPlayedSection(
                            songs = recentlyPlayedSongs,
                            onSongClick = { song ->
                                if (recentlyPlayedQueue.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        songsToPlay = recentlyPlayedQueue,
                                        startSong = song,
                                        queueName = "Recently Played"
                                    )
                                }
                            },
                            onOpenAllClick = {
                                navController.navigateSafely(Screen.RecentlyPlayed.route)
                            },
                            themeStateHolder = playerViewModel.themeStateHolder,
                            currentSongId = currentSong?.id,
                            contentPadding = PaddingValues(start = 8.dp, end = 24.dp)
                        )
                    }
                }

                if (isOnline && artistReleases.isNotEmpty()) {
                    item(
                        key = "favorite_artist_releases_section",
                        contentType = "favorite_artist_releases_section"
                    ) {
                        FavoriteArtistReleasesSection(
                            releases = artistReleases,
                            onSongClick = { songItem ->
                                val nativeSong = songItem.toNativeSong()
                                playerViewModel.showAndPlaySong(nativeSong)
                            },
                            onAlbumClick = { albumItem ->
                                val routeId = albumItem.browseId?.takeIf { it.isNotBlank() } ?: albumItem.playlistId
                                if (routeId.startsWith("VL") || routeId.startsWith("PL") || routeId.startsWith("RD")) {
                                    navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute(routeId))
                                } else {
                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(routeId))
                                }
                            }
                        )
                    }
                }

                if (homeStatsOverview != null) {
                    item(
                        key = "listening_stats_preview",
                        contentType = "listening_stats_preview"
                    ) {
                        StatsOverviewCard(
                            summary = homeStatsOverview,
                            onClick = { navController.navigateSafely(Screen.Stats.route) }
                        )
                    }
                }
            }
            } // end PullToRefreshBox
        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false },
            sheetState = sheetState
        ) {
            ChangelogBottomSheet()
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState,
            //contentWindowInsets = { WindowInsets.statusBars.only(WindowInsets.statusBars) }
        ) {
            BetaInfoBottomSheet()
        }
    }
    if (showStreamingProviderSheet) {
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            onNavigateToYoutubeAuth = {
                navController.navigateSafely(Screen.YoutubeAuth.route)
            }
        )
    }
    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YourMixLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        ContainedLoadingIndicator()
    }
}

@Composable
private fun YourMixEmptyPlaceholder(
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val surfaceShape = remember {
                AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 28.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 28.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 28.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 28.dp,
                    smoothnessAsPercentBL = 60,
                )
            }
            val buttonShape = remember {
                AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 22.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 22.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 22.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 22.dp,
                    smoothnessAsPercentBL = 60,
                )
            }

            Surface(
                modifier = Modifier.size(76.dp),
                shape = surfaceShape,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_empty_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_empty_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FilledTonalButton(
                onClick = onRefresh,
                shape = buttonShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    isShuffleEnabled: Boolean = false,
    onPlayShuffled: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val titleStyle = rememberYourMixTitleStyle()
    val playShuffledLabel = stringResource(R.string.cd_shuffle_play)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 96.dp)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            // Your Mix Title
            Text(
                text = stringResource(R.string.home_your_mix_title),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Clip
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist/Song subtitle
            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        // Play Button - fully expressive CircleShape for maximum performance and touch response
        LargeExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .bounceClick(scaleDown = 0.93f) { onPlayShuffled() },
            onClick = onPlayShuffled,
            containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
            contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_shuffle_24),
                contentDescription = playShuffledLabel,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.97f) { onClick() },
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, title),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist, style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp), // similar al tamaño del ícono
                    color = colors.primary,
                    isPlaying = isPlaying  // o conectalo a tu estado real de reproducción
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the stablePlayerState once
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    // Derive isThisSongPlaying using remember
    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id
    }

    // Call the presentational composable
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 36.sp,
            lineHeight = 38.sp
        )
    }
}

@Composable
fun HomeGreetingHeader(
    userName: String?,
    modifier: Modifier = Modifier
) {
    val calendar = remember { java.util.Calendar.getInstance() }
    val hour = remember(calendar) { calendar.get(java.util.Calendar.HOUR_OF_DAY) }
    
    val greetingRes = remember(hour, userName != null) {
        val resIds = when (hour) {
            in 5..11 -> if (userName != null) {
                listOf(
                    R.string.home_greeting_morning_1,
                    R.string.home_greeting_morning_2,
                    R.string.home_greeting_morning_3,
                    R.string.home_greeting_morning_4
                )
            } else {
                listOf(
                    R.string.home_greeting_morning_anon_1,
                    R.string.home_greeting_morning_anon_2,
                    R.string.home_greeting_morning_anon_3,
                    R.string.home_greeting_morning_anon_4
                )
            }
            in 12..16 -> if (userName != null) {
                listOf(
                    R.string.home_greeting_afternoon_1,
                    R.string.home_greeting_afternoon_2,
                    R.string.home_greeting_afternoon_3,
                    R.string.home_greeting_afternoon_4
                )
            } else {
                listOf(
                    R.string.home_greeting_afternoon_anon_1,
                    R.string.home_greeting_afternoon_anon_2,
                    R.string.home_greeting_afternoon_anon_3,
                    R.string.home_greeting_afternoon_anon_4
                )
            }
            in 17..21 -> if (userName != null) {
                listOf(
                    R.string.home_greeting_evening_1,
                    R.string.home_greeting_evening_2,
                    R.string.home_greeting_evening_3,
                    R.string.home_greeting_evening_4
                )
            } else {
                listOf(
                    R.string.home_greeting_evening_anon_1,
                    R.string.home_greeting_evening_anon_2,
                    R.string.home_greeting_evening_anon_3,
                    R.string.home_greeting_evening_anon_4
                )
            }
            else -> if (userName != null) {
                listOf(
                    R.string.home_greeting_night_1,
                    R.string.home_greeting_night_2,
                    R.string.home_greeting_night_3,
                    R.string.home_greeting_night_4
                )
            } else {
                listOf(
                    R.string.home_greeting_night_anon_1,
                    R.string.home_greeting_night_anon_2,
                    R.string.home_greeting_night_anon_3,
                    R.string.home_greeting_night_anon_4
                )
            }
        }
        resIds.random()
    }
    val greeting = if (userName != null) {
        stringResource(greetingRes, userName)
    } else {
        stringResource(greetingRes)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp)
    ) {
        Text(
            text = greeting,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (greeting.length > 20) 24.sp else 28.sp,
                lineHeight = if (greeting.length > 20) 30.sp else 34.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
