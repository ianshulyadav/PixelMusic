package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely
import com.unshoo.pixelmusic.presentation.navigation.enterTransition
import com.unshoo.pixelmusic.presentation.navigation.exitTransition
import com.unshoo.pixelmusic.presentation.navigation.popEnterTransition
import com.unshoo.pixelmusic.presentation.navigation.popExitTransition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.presentation.utils.itemsUnique
import com.unshoo.pixelmusic.presentation.utils.uniqueLazyKeys
import androidx.compose.runtime.produceState
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.bounceClick
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageListTargetSize
import com.unshoo.pixelmusic.presentation.components.SongInfoBottomSheet
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.PlaylistBottomSheet
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import com.unshoo.pixelmusic.presentation.components.resolveMainScreenBottomGradientHeight
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.presentation.screens.search.components.MoodGenreCatalog
import com.unshoo.pixelmusic.presentation.screens.search.components.MoodGenreItem
import com.unshoo.pixelmusic.presentation.screens.search.components.MoodGenreContentView
import com.unshoo.pixelmusic.presentation.screens.search.components.MoodsAndGenresBrowseView
import com.unshoo.pixelmusic.presentation.screens.search.components.BrowseDiscoverSection
import com.unshoo.pixelmusic.presentation.screens.search.components.RecentSearchesSection
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

private enum class SearchScreenMode {
    BROWSE_GENRES,
    MOOD_CONTENT,
    SEARCH_RESULTS
}

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMoodGenre by remember { mutableStateOf<MoodGenreItem?>(null) }
    var lastSelectedMoodGenre by remember { mutableStateOf<MoodGenreItem?>(null) }
    var isBrowsingAllMoodsAndGenres by rememberSaveable { mutableStateOf(false) }
    if (selectedMoodGenre != null) {
        lastSelectedMoodGenre = selectedMoodGenre
    }

    LaunchedEffect(isBrowsingAllMoodsAndGenres, selectedMoodGenre) {
        val shouldHideNav = isBrowsingAllMoodsAndGenres || selectedMoodGenre != null
        onSearchBarActiveChange(shouldHideNav)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchInputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        com.unshoo.pixelmusic.utils.AppReadinessSignal.markInitialFetchFinished()
    }

    val sheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = sheetState == com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState.COLLAPSED) {
        when {
            selectedMoodGenre != null -> {
                selectedMoodGenre = null
            }
            isBrowsingAllMoodsAndGenres -> {
                isBrowsingAllMoodsAndGenres = false
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
                playerViewModel.updateSearchQuery("")
                playerViewModel.performSearch("")
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            else -> {
                navController.navigateToTopLevelSafely(com.unshoo.pixelmusic.presentation.navigation.Screen.Home.route)
            }
        }
    }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val navBarHeightOffsetRaw by playerViewModel.navBarHeightOffset.collectAsStateWithLifecycle()
    val navBarHeightOffset = navBarHeightOffsetRaw.dp
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(navBarStyle, systemNavBarInset, navBarCompactMode, navBarHeightOffset)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode, navBarHeightOffset)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults,
                    searchHistory = uiState.searchHistory
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val isSearching by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { it.isSearching }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val coolSearchMessages = remember {
        listOf(
            "Searching the infinite music universe...",
            "Scanning frequencies for your sound...",
            "Digging up the next masterpiece...",
            "Curating the perfect soundtrack...",
            "Tuning into the music space..."
        )
    }
    val currentSearchMessage = remember(isSearching) {
        if (isSearching) coolSearchMessages.random() else ""
    }
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val currentPlayingSongId by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val searchSource by playerViewModel.searchSource.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }

    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    // On every visit to the search screen, clear the query + results so we
    // always land on the clean Browse & Discover page.
    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
        searchQuery = ""
        selectedMoodGenre = null
        isBrowsingAllMoodsAndGenres = false
        playerViewModel.performSearch("")
        playerViewModel.loadSearchHistory()
        browseListState.scrollToItem(0, 0)
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Search debouncing is centralized in SearchStateHolder.
    LaunchedEffect(searchQuery, currentFilter, searchSource) {
        playerViewModel.performSearch(searchQuery)
    }
    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    LaunchedEffect(showSongInfoBottomSheet) {
        if (showSongInfoBottomSheet) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelMusicDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            onSearchBarActiveChange(false)
        }
    }

    val isScrolledDown by remember {
        derivedStateOf {
            if (searchQuery.isNotBlank()) {
                searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 40
            } else {
                browseListState.firstVisibleItemIndex > 0 || browseListState.firstVisibleItemScrollOffset > 60
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Layer 1: Scrollable Content (Underneath the sticky header)
                AnimatedContent(
                    targetState = searchQuery.isNotBlank(),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "search_query_mode_transition"
                ) { isSearchActive ->
                    if (!isSearchActive) {
                        AnimatedContent(
                            targetState = isBrowsingAllMoodsAndGenres,
                            transitionSpec = {
                                if (targetState) {
                                    enterTransition() togetherWith exitTransition()
                                } else {
                                    popEnterTransition() togetherWith popExitTransition()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "moods_and_genres_browse_transition"
                        ) { browsingMoods ->
                            if (browsingMoods) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = statusBarTopInset)
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { isBrowsingAllMoodsAndGenres = false },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "Moods & Genres",
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                MoodsAndGenresBrowseView(
                                    searchHistory = emptyList(),
                                    bottomBarHeight = 0.dp,
                                    topPadding = 0.dp,
                                    listState = browseListState,
                                    onMoodGenreClick = { item ->
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        selectedMoodGenre = item
                                    },
                                    onHistoryItemClick = { query ->
                                        searchQuery = query
                                        selectedMoodGenre = null
                                        isBrowsingAllMoodsAndGenres = false
                                        playerViewModel.updateSearchQuery(query)
                                        playerViewModel.performSearch(query)
                                        playerViewModel.onSearchQuerySubmitted(query)
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                    },
                                    onHistoryItemDelete = { query ->
                                        playerViewModel.deleteSearchHistoryItem(query)
                                    },
                                    onClearAllHistory = {
                                        playerViewModel.clearSearchHistory()
                                    }
                                )
                            }
                        } else {
                            val cachedCover = remember(playerViewModel) {
                                playerViewModel.stablePlayerState.value.currentSong?.albumArtUriString
                            }

                            LazyColumn(
                                state = browseListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = statusBarTopInset + 66.dp,
                                    bottom = bottomBarHeightDp + MiniPlayerHeight + 36.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item(key = "browse_discover_grid", contentType = "browse_discover") {
                                    BrowseDiscoverSection(
                                        onNewReleasesClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute("new_releases"))
                                        },
                                        onChartsClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute("charts"))
                                        },
                                        onMoodsGenresClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            isBrowsingAllMoodsAndGenres = true
                                        },
                                        onPodcastsClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute("podcasts"))
                                        }
                                    )
                                }

                                if (searchUiState.searchHistory.isNotEmpty()) {
                                    item(key = "recent_searches_section", contentType = "recent_searches") {
                                        RecentSearchesSection(
                                            searchHistory = searchUiState.searchHistory,
                                            onItemClick = { query ->
                                                searchQuery = query
                                                selectedMoodGenre = null
                                                playerViewModel.updateSearchQuery(query)
                                                playerViewModel.performSearch(query)
                                                playerViewModel.onSearchQuerySubmitted(query)
                                                keyboardController?.hide()
                                                focusManager.clearFocus(force = true)
                                            },
                                            onItemDelete = { query ->
                                                playerViewModel.deleteSearchHistoryItem(query)
                                            },
                                            onClearAll = {
                                                playerViewModel.clearSearchHistory()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .padding(top = statusBarTopInset + 62.dp)
                        ) {
                            if (isSearching) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val shimmerBrush = com.unshoo.pixelmusic.presentation.components.rememberShimmerBrush()
                                    repeat(6) {
                                        Box(
                                            modifier = Modifier
                                                .width(80.dp)
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(shimmerBrush)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.VIDEOS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                                }
                            }
                            if (isSearching) {
                                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                                val isKeyboardOpen = imeBottom > 0.dp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .imePadding()
                                        .padding(bottom = if (isKeyboardOpen) 0.dp else bottomBarHeightDp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ContainedLoadingIndicator()
                                }
                            } else {
                                Crossfade(
                                    targetState = searchResults.isEmpty(),
                                    animationSpec = tween(durationMillis = 190),
                                    label = "search_results_fade"
                                ) { isEmpty ->
                                    if (isEmpty) {
                                        EmptySearchResults(
                                            searchQuery = searchQuery,
                                            colorScheme = colorScheme
                                        )
                                    } else {
                                        SearchResultsList(
                                            results = searchResults,
                                            searchQuery = searchQuery,
                                            playerViewModel = playerViewModel,
                                            currentFilter = currentFilter,
                                            onItemSelected = {
                                                if (searchQuery.isNotBlank()) {
                                                    playerViewModel.onSearchQuerySubmitted(searchQuery)
                                                }
                                            },
                                            currentPlayingSongId = currentPlayingSongId,
                                            isPlaying = isPlaying,
                                            onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                            navController = navController,
                                            listState = searchListState
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Layer 2: Sticky Header with Solid Status Bar & Smooth Feathered Blend
                val headerBgAlpha by animateFloatAsState(
                    targetValue = if (isScrolledDown) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "header_bg_alpha"
                )

                val headerScale by animateFloatAsState(
                    targetValue = if (isScrolledDown) 0.985f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "header_scale"
                )

                val searchPillColor by animateColorAsState(
                    targetValue = if (isScrolledDown) {
                        if (dm) Color(0xFF252525) else MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        if (dm) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "search_pill_color"
                )

                val baseContainerColor = MaterialTheme.colorScheme.primaryContainer
                val surfaceColor = MaterialTheme.colorScheme.surface
                val solidTintedColor = remember(baseContainerColor, surfaceColor) {
                    Color(
                        red = (baseContainerColor.red * 0.45f) + (surfaceColor.red * 0.55f),
                        green = (baseContainerColor.green * 0.45f) + (surfaceColor.green * 0.55f),
                        blue = (baseContainerColor.blue * 0.45f) + (surfaceColor.blue * 0.55f),
                        alpha = 1f
                    )
                }

                val headerBgColor = MaterialTheme.colorScheme.background

                if (!isBrowsingAllMoodsAndGenres && selectedMoodGenre == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Solid status bar fill - ALWAYS 100% solid, covers 0..statusBarTopInset so content NEVER leaks into system clock/battery
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(statusBarTopInset)
                                .background(headerBgColor)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .background(headerBgColor.copy(alpha = headerBgAlpha))
                                .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 5.dp)
                                .graphicsLayer {
                                    scaleX = headerScale
                                    scaleY = headerScale
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        androidx.compose.material3.Surface(
                            shape = CircleShape,
                            color = searchPillColor,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.cd_search_icon),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        val placeholderText = if (searchSource == com.unshoo.pixelmusic.data.preferences.SearchSource.LOCAL) {
                                            "Search Local Library..."
                                        } else {
                                            "Search for any tracks, albums, lyrics..."
                                        }
                                        Text(
                                            text = placeholderText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            if (it.isNotBlank() && selectedMoodGenre != null) {
                                                selectedMoodGenre = null
                                            }
                                            playerViewModel.updateSearchQuery(it)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchInputFocusRequester),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onSearch = {
                                                if (searchQuery.isNotBlank()) {
                                                    playerViewModel.onSearchQuerySubmitted(searchQuery)
                                                }
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                            }
                                        )
                                    )
                                }

                                // Search Source Toggle Button
                                IconButton(
                                    onClick = { playerViewModel.toggleSearchSource() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    if (searchSource == com.unshoo.pixelmusic.data.preferences.SearchSource.LOCAL) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.rounded_library_music_24),
                                            contentDescription = "Toggle Search Source",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_youtube),
                                            contentDescription = "Toggle Search Source",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedMoodGenre = null
                                            playerViewModel.updateSearchQuery("")
                                            playerViewModel.performSearch("")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_clear_search_query),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Settings circular button on the same horizontal line!
                        androidx.compose.material3.Surface(
                            onClick = { navController.navigateSafely(Screen.Settings.route) },
                            shape = CircleShape,
                            color = solidTintedColor,
                            shadowElevation = 0.dp,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_settings_24),
                                    contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // Full Screen Musify Mood & Genre View - Overlay with fluid spring exit and retained model
        val displayedMoodGenre = selectedMoodGenre ?: lastSelectedMoodGenre
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedMoodGenre != null,
            enter = enterTransition(),
            exit = popExitTransition(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayedMoodGenre != null) {
                MoodGenreContentView(
                    moodGenre = displayedMoodGenre,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    currentPlayingSongId = currentPlayingSongId,
                    isPlaying = isPlaying,
                    bottomBarHeight = bottomBarHeightDp,
                    onBack = { selectedMoodGenre = null },
                    onSongMoreOptionsClick = handleSongMoreOptionsClick
                )
            }
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                playerViewModel = playerViewModel,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    playerViewModel.clearSelectedSongForInfo()
                },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    playlistSheetSongs = listOf(currentSong)
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {},
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = { showPlaylistBottomSheet = false },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(
                top = 2.dp,
            )
        ) {
            itemsUnique(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_no_search_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    currentFilter: SearchFilterType,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val localDensity = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isShuffleEnabled by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.isShuffleEnabled }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results, currentFilter) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> if (currentFilter == SearchFilterType.VIDEOS) SearchFilterType.VIDEOS else SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    val sectionOrder = remember(currentFilter) {
        if (currentFilter == SearchFilterType.VIDEOS) {
            listOf(SearchFilterType.VIDEOS)
        } else {
            listOf(
                SearchFilterType.SONGS,
                SearchFilterType.ALBUMS,
                SearchFilterType.ARTISTS,
                SearchFilterType.PLAYLISTS
            )
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, searchQueueName, focusManager, keyboardController) {
        { song: Song ->
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            playerViewModel.showAndPlaySong(song, listOf(song), searchQueueName)
            onItemSelected()
        }
    }

    val onArtistPlayClick = remember(playerViewModel, onItemSelected) {
        { artist: Artist ->
            playerViewModel.playArtist(artist)
            onItemSelected()
        }
    }

    val onArtistOpenClick = remember(navController, onItemSelected) {
        { artist: Artist ->
            val targetId = artist.channelId ?: artist.id.toString()
            navController.navigateSafelyReplacing(
                route = Screen.ArtistDetail.createRoute(targetId),
                patternToPop = Screen.ArtistDetail.route
            )
            onItemSelected()
        }
    }

    val onAlbumPlayClick = remember(playerViewModel, onItemSelected) {
        { album: Album ->
            playerViewModel.playAlbum(album)
            onItemSelected()
        }
    }

    val onAlbumOpenClick = remember(navController, onItemSelected) {
        { album: Album ->
            navController.navigateSafelyReplacing(
                route = Screen.AlbumDetail.createRoute(album.id),
                patternToPop = Screen.AlbumDetail.route
            )
            onItemSelected()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val onPlaylistPlayClick: (Playlist) -> Unit = remember(playerViewModel, onItemSelected, isShuffleEnabled, coroutineScope) {
        { playlist ->
            val playlistId = playlist.id
            coroutineScope.launch {
                if (playlistId.startsWith("PL") || playlistId.startsWith("VL") || playlistId.toLongOrNull() == null) {
                    val ytPlaylistResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        unshoo.ianshulyadav.pixelmusic.innertube.YouTube.playlist(playlistId)
                    }
                    if (ytPlaylistResult.isSuccess) {
                        val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                        val firstPageSongs = ytPlaylistPage.songs.map { it.toNativeSong() }
                        
                        if (firstPageSongs.isNotEmpty()) {
                            playerViewModel.insertYoutubeSongs(firstPageSongs)
                            playerViewModel.playSongs(
                                firstPageSongs,
                                firstPageSongs.first(),
                                playlist.name
                            )
                            if (isShuffleEnabled) playerViewModel.toggleShuffle()
                            onItemSelected()
                        } else {
                            playerViewModel.sendToast("Empty playlist")
                            onItemSelected()
                        }
                    } else {
                        playerViewModel.sendToast("Failed to fetch playlist")
                        onItemSelected()
                    }
                } else {
                    val songs = playerViewModel.getSongs(playlist.songIds)
                    if (songs.isNotEmpty()) {
                        playerViewModel.playSongs(
                            songs,
                            songs.first(),
                            playlist.name
                        )
                        if (isShuffleEnabled) playerViewModel.toggleShuffle()
                    } else {
                        playerViewModel.sendToast("Empty playlist")
                    }
                    onItemSelected()
                }
            }
        }
    }

    val onPlaylistOpenClick = remember(navController, onItemSelected) {
        { playlist: Playlist ->
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
            onItemSelected()
        }
    }

    val lazyListState = listState

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsNumber > 0 && lastVisibleItemIndex >= totalItemsNumber - 5
        }.distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) {
                    playerViewModel.loadMoreSearch()
                }
            }
    }

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    val allResultKeys = remember(results) {
        uniqueLazyKeys(results) { item ->
            when (item) {
                is SearchResultItem.SongItem -> "song_${item.song.id}"
                is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}"
            }
        }
    }
    val sectionKeysMap = remember(groupedResults) {
        groupedResults.mapValues { (_, items) ->
            uniqueLazyKeys(items) { item ->
                when (item) {
                    is SearchResultItem.SongItem -> "song_${item.song.id}"
                    is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                    is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                    is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}"
                }
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                )
            ),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        if (currentFilter == SearchFilterType.ALL) {
            items(
                count = results.size,
                key = { index -> allResultKeys.getOrElse(index) { "result_$index" } },
                contentType = { index ->
                    when (results[index]) {
                        is SearchResultItem.SongItem -> "search_song"
                        is SearchResultItem.AlbumItem -> "search_album"
                        is SearchResultItem.ArtistItem -> "search_artist"
                        is SearchResultItem.PlaylistItem -> "search_playlist"
                    }
                }
            ) { index ->
                val item = results[index]
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    when (item) {
                        is SearchResultItem.SongItem -> {
                            SwipeActionWrapper(
                                onSwipeRight = {
                                    playerViewModel.addSongNextToQueue(item.song)
                                    playerViewModel.sendToast("Added to Play Next")
                                },
                                onSwipeLeft = {
                                    playerViewModel.addSongToQueue(item.song)
                                    playerViewModel.sendToast("Added to Queue")
                                }
                            ) {
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) }
                                )
                            }
                        }

                        is SearchResultItem.AlbumItem -> {
                            SearchResultAlbumItem(
                                album = item.album,
                                onPlayClick = { onAlbumPlayClick(item.album) },
                                onOpenClick = { onAlbumOpenClick(item.album) }
                            )
                        }

                        is SearchResultItem.ArtistItem -> {
                            SearchResultArtistItem(
                                artist = item.artist,
                                onPlayClick = { onArtistPlayClick(item.artist) },
                                onOpenClick = { onArtistOpenClick(item.artist) }
                            )
                        }

                        is SearchResultItem.PlaylistItem -> {
                            SearchResultPlaylistItem(
                                playlist = item.playlist,
                                playerViewModel = playerViewModel,
                                onPlayClick = { onPlaylistPlayClick(item.playlist) },
                                onOpenClick = { onPlaylistOpenClick(item.playlist) }
                            )
                        }
                    }
                }
            }
        } else {
            sectionOrder.forEach { filterType ->
                val itemsForSection = groupedResults[filterType] ?: emptyList()

                if (itemsForSection.isNotEmpty()) {
                    item(key = "header_${filterType.name}") {
                        SearchResultSectionHeader(
                            title = when (filterType) {
                                SearchFilterType.SONGS -> "Songs"
                                SearchFilterType.VIDEOS -> "Videos"
                                SearchFilterType.ALBUMS -> "Albums"
                                SearchFilterType.ARTISTS -> "Artists"
                                SearchFilterType.PLAYLISTS -> "Playlists"
                                else -> "Results"
                            }
                        )
                    }

                    val currentSectionKeys = sectionKeysMap[filterType] ?: emptyList()
                    items(
                        count = itemsForSection.size,
                        key = { index -> currentSectionKeys.getOrElse(index) { "sec_${filterType}_$index" } },
                        contentType = { index ->
                            when (itemsForSection[index]) {
                                is SearchResultItem.SongItem -> "search_song"
                                is SearchResultItem.AlbumItem -> "search_album"
                                is SearchResultItem.ArtistItem -> "search_artist"
                                is SearchResultItem.PlaylistItem -> "search_playlist"
                            }
                        }
                    ) { index ->
                        val item = itemsForSection[index]
                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            when (item) {
                                is SearchResultItem.SongItem -> {
                                    SwipeActionWrapper(
                                        onSwipeRight = {
                                            playerViewModel.addSongNextToQueue(item.song)
                                            playerViewModel.sendToast("Added to Play Next")
                                        },
                                        onSwipeLeft = {
                                            playerViewModel.addSongToQueue(item.song)
                                            playerViewModel.sendToast("Added to Queue")
                                        }
                                    ) {
                                        EnhancedSongListItem(
                                            song = item.song,
                                            isPlaying = isPlaying,
                                            isCurrentSong = currentPlayingSongId == item.song.id,
                                            onMoreOptionsClick = onSongMoreOptionsClick,
                                            onClick = { onSongResultClick(item.song) }
                                        )
                                    }
                                }

                                is SearchResultItem.AlbumItem -> {
                                    SearchResultAlbumItem(
                                        album = item.album,
                                        onPlayClick = { onAlbumPlayClick(item.album) },
                                        onOpenClick = { onAlbumOpenClick(item.album) }
                                    )
                                }

                                is SearchResultItem.ArtistItem -> {
                                    SearchResultArtistItem(
                                        artist = item.artist,
                                        onPlayClick = { onArtistPlayClick(item.artist) },
                                        onOpenClick = { onArtistOpenClick(item.artist) }
                                    )
                                }

                                is SearchResultItem.PlaylistItem -> {
                                    SearchResultPlaylistItem(
                                        playlist = item.playlist,
                                        playerViewModel = playerViewModel,
                                        onPlayClick = { onPlaylistPlayClick(item.playlist) },
                                        onOpenClick = { onPlaylistOpenClick(item.playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = "Album Art: ${album.title}",
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = "Artist: ${artist.name}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = "Artist",
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel? = null,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val needsCollage = playlist.coverImageUri.isNullOrBlank() && playlist.coverColorArgb == null && playlist.songIds.isNotEmpty()
    val playlistSongs by produceState(initialValue = emptyList<Song>(), key1 = playlist.id, key2 = needsCollage) {
        if (needsCollage && playerViewModel != null) {
            value = playerViewModel.getSongs(playlist.songIds.take(4))
        }
    }
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 56.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    FilterChip(
        selected = selected,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(filterType.name.lowercase().replaceFirstChar { it.titlecase() }) },
        modifier = modifier,
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor =  MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
         leadingIcon = if (selected) {
             {
                 Icon(
                     painter = painterResource(R.drawable.rounded_check_circle_24),
                     contentDescription = "Selected",
                     tint = MaterialTheme.colorScheme.onPrimary,
                     modifier = Modifier.size(FilterChipDefaults.IconSize)
                 )
             }
         } else {
             null
         }
    )
}

@Composable
fun SwipeActionWrapper(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 90.dp.toPx() } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    dragOffset.value > 0 -> Color(0xFF2E7D32) // Soft Green
                    dragOffset.value < 0 -> Color(0xFF1565C0) // Soft Blue
                    else -> Color.Transparent
                }
            )
    ) {
        if (dragOffset.value > 0) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Next",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play Next",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (dragOffset.value < 0) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Add to Queue",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = "Add to Queue",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.toInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = dragOffset.value
                                if (finalOffset > thresholdPx) {
                                    onSwipeRight()
                                } else if (finalOffset < -thresholdPx) {
                                    onSwipeLeft()
                                }
                                dragOffset.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (kotlin.math.abs(dragAmount) > 0.5f) {
                                change.consume()
                            }
                            coroutineScope.launch {
                                val currentOffset = dragOffset.value
                                val newOffset = currentOffset + dragAmount
                                val dampenedOffset = if (newOffset > thresholdPx) {
                                    thresholdPx + (newOffset - thresholdPx) * 0.3f
                                } else if (newOffset < -thresholdPx) {
                                    -thresholdPx + (newOffset + thresholdPx) * 0.3f
                                } else {
                                    newOffset
                                }
                                dragOffset.snapTo(dampenedOffset)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
