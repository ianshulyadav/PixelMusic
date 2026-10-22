package com.unshoo.pixelmusic.presentation.screens

import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import androidx.lifecycle.Lifecycle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.CollapsibleCommonTopBar
import com.unshoo.pixelmusic.presentation.components.ExpressiveTopBarContent
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.model.SettingsCategory
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SettingsViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SettingsUiState
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.preferences.LaunchTab

// SettingsTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {

    // Navigation handles screen transition natively without double-offset delay

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp 

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val launchTab = uiState.launchTab
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

    var showCornerRadiusOverlay by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
        ) {
            item {
                ProfileHeaderCard(
                    uiState = uiState,
                    onClick = {
                        if (uiState.ytUsername.isNotEmpty()) {
                            showAccountDialog = true
                        } else {
                            navController.navigateSafely(Screen.YoutubeAuth.route)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }
            item {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                ExpressiveSettingsGroup {
                    val mainCategories = SettingsCategory.entries.filter {
                        it != SettingsCategory.ABOUT && 
                        it != SettingsCategory.DEVICE_CAPABILITIES &&
                        it != SettingsCategory.LASTFM &&
                        it != SettingsCategory.LOSSLESS_SOURCES
                    }

                    val totalItems = mainCategories.size + 4 // Device + Accounts + Downloads + About
                    fun shapeFor(index: Int) =
                        when {
                            totalItems == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(4.dp)
                        }

                    var itemIndex = 0

                    mainCategories.forEach { category ->
                        val colors = getCategoryColors(category, isDark)

                        ExpressiveCategoryItem(
                            category = category,
                            customColors = colors,
                            onClick = {
                                if (category == SettingsCategory.EQUALIZER) {
                                    navController.navigateSafely(Screen.Equalizer.route)
                                } else if (category == SettingsCategory.STORAGE) {
                                    navController.navigateSafely(Screen.SmartStorage.route)
                                } else {
                                    navController.navigateSafely(Screen.SettingsCategory.createRoute(category.id))
                                }
                            },
                            shape = shapeFor(itemIndex)
                        )
                        if (itemIndex < totalItems - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        itemIndex++
                    }

                    ExpressiveCategoryItem(
                        category = SettingsCategory.DEVICE_CAPABILITIES,
                        customColors = getCategoryColors(SettingsCategory.DEVICE_CAPABILITIES, isDark),
                        onClick = {
                            navController.navigateSafely(Screen.DeviceCapabilities.route)
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    ExpressiveNavigationItem(
                        title = stringResource(R.string.settings_accounts_row_title),
                        subtitle = stringResource(R.string.settings_accounts_row_subtitle),
                        icon = Icons.Rounded.AccountCircle,
                        colors = getAccountsColors(isDark),
                        onClick = {
                            navController.navigateSafely(Screen.Accounts.route)
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    ExpressiveNavigationItem(
                        title = stringResource(R.string.cloud_downloads_title),
                        subtitle = stringResource(R.string.cloud_downloads_settings_subtitle),
                        icon = Icons.Rounded.Download,
                        colors = getDownloadsColors(isDark),
                        onClick = {
                            navController.navigateSafely(Screen.CloudDownloads.route)
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    ExpressiveCategoryItem(
                        category = SettingsCategory.ABOUT,
                        customColors = getCategoryColors(SettingsCategory.ABOUT, isDark),
                        onClick = {
                            navController.navigateSafely("about")
                        },
                        shape = shapeFor(itemIndex)
                    )
                }

                // for player active:
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        CollapsibleCommonTopBar(
                title = stringResource(R.string.settings_top_bar_title),
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackClick = onNavigationIconClick
        )



        if (showAccountDialog && uiState.ytUsername.isNotEmpty()) {
            val nameText = uiState.ytUsername
            val handleText = uiState.ytHandle
            val avatarUrl = uiState.ytAvatarUrl
            
            val dialogPrimary = MaterialTheme.colorScheme.primary
            val dialogSecondary = MaterialTheme.colorScheme.secondaryContainer
            val dialogAvatarGradient = remember(dialogPrimary, dialogSecondary) {
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(dialogPrimary, dialogSecondary)
                )
            }
            val dialogOnPrimary = MaterialTheme.colorScheme.onPrimary

            AlertDialog(
                onDismissRequest = { showAccountDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_youtube),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "YouTube Music Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(dialogAvatarGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initial = nameText.firstOrNull()?.toString()?.uppercase() ?: "Y"
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = dialogOnPrimary
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nameText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = handleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Surface(
                            onClick = {
                                showAccountDialog = false
                                settingsViewModel.logoutYoutube()
                                navController.navigateSafely(Screen.YoutubeAuth.route)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Login to a new account",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Sign in with a different YouTube account",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                showAccountDialog = false
                                settingsViewModel.logoutYoutube()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Log out",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Disconnect YouTube Music and clear cache",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccountDialog = false }) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun ExpressiveNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    customColors: Pair<Color, Color>? = null
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp) 
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            // Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(customColors?.first ?: MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
//            // Chevron or indicator
//             Box(
//                contentAlignment = Alignment.Center,
//                modifier = Modifier
//                    .size(36.dp)
//                    .clip(CircleShape)
//                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
//            ) {
//                 Icon(
//                    imageVector = Icons.Rounded.ChevronRight,
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.onSurface,
//                    modifier = Modifier.size(20.dp)
//                )
//            }
        }
    }
}

private fun getAccountsColors(isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        Color(0xFF37474F) to Color(0xFFBBD9E8)
    } else {
        Color(0xFFD6EAF5) to Color(0xFF103548)
    }
}

private fun getDownloadsColors(isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        Color(0xFF2E4D40) to Color(0xFFA8DFC5)
    } else {
        Color(0xFFD0EEDF) to Color(0xFF0E3827)
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
    ) {
        content()
    }
}

private fun getCategoryColors(category: SettingsCategory, isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        when (category) {
            SettingsCategory.LIBRARY -> Color(0xFF004A77) to Color(0xFFC2E7FF) 
            SettingsCategory.CONTENT -> Color(0xFF005049) to Color(0xFF88FFD9)
            SettingsCategory.APPEARANCE -> Color(0xFF7D5260) to Color(0xFFFFD8E4) 
            SettingsCategory.PLAYBACK -> Color(0xFF633B48) to Color(0xFFFFD8EC) 
            SettingsCategory.BEHAVIOR -> Color(0xFF3E4C63) to Color(0xFFD7E3FF)
            SettingsCategory.AI_INTEGRATION -> Color(0xFF004F58) to Color(0xFF88FAFF) 
            SettingsCategory.BACKUP_RESTORE -> Color(0xFF3B4869) to Color(0xFFD9E2FF)
            SettingsCategory.DEVELOPER -> Color(0xFF324F34) to Color(0xFFCBEFD0) 
            SettingsCategory.EQUALIZER -> Color(0xFF6E4E13) to Color(0xFFFFDEAC) 
            SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFF004D61) to Color(0xFFACEFEE) // Custom teal/cyan mix
            SettingsCategory.ABOUT -> Color(0xFF3F474D) to Color(0xFFDEE3EB) 
            SettingsCategory.LASTFM -> Color(0xFF6E1B1B) to Color(0xFFFFDAD9)
            SettingsCategory.LOSSLESS_SOURCES -> Color(0xFF1B4D6E) to Color(0xFFD1ECFF)
            SettingsCategory.STORAGE -> Color(0xFF5E3F00) to Color(0xFFFFDDB3)
        }
    } else {
        when (category) {
            SettingsCategory.LIBRARY -> Color(0xFFD7E3FF) to Color(0xFF005AC1)
            SettingsCategory.CONTENT -> Color(0xFF88FFD9) to Color(0xFF005049)
            SettingsCategory.APPEARANCE -> Color(0xFFFFD8E4) to Color(0xFF631835)
            SettingsCategory.PLAYBACK -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
            SettingsCategory.BEHAVIOR -> Color(0xFFD7E3FF) to Color(0xFF253347)
            SettingsCategory.AI_INTEGRATION -> Color(0xFFCCE8EA) to Color(0xFF004F58)
            SettingsCategory.BACKUP_RESTORE -> Color(0xFFD9E2FF) to Color(0xFF27304E)
            SettingsCategory.DEVELOPER -> Color(0xFFCBEFD0) to Color(0xFF042106)
            SettingsCategory.EQUALIZER -> Color(0xFFFFDEAC) to Color(0xFF281900)
            SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFFACEFEE) to Color(0xFF002022)
            SettingsCategory.ABOUT -> Color(0xFFEFF1F7) to Color(0xFF44474F)
            SettingsCategory.LASTFM -> Color(0xFFFFDAD9) to Color(0xFF6E1B1B)
            SettingsCategory.LOSSLESS_SOURCES -> Color(0xFFD1ECFF) to Color(0xFF1B4D6E)
            SettingsCategory.STORAGE -> Color(0xFFFFDDB3) to Color(0xFF2A1800)
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    uiState: SettingsUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val avatarGradient = remember(primary, secondaryContainer) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(primary, secondaryContainer)
        )
    }

    val hasProfile = uiState.ytUsername.isNotEmpty()
    val nameText = if (hasProfile) uiState.ytUsername else "Guest User"
    val handleText = if (hasProfile) uiState.ytHandle else "Sign in to sync"
    val avatarUrl = if (hasProfile) uiState.ytAvatarUrl else ""

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = surfaceContainer,
        tonalElevation = 2.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar circle with gradient and initial letter OR AsyncImage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(avatarGradient)
            ) {
                if (avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = nameText.firstOrNull()?.toString()?.uppercase() ?: "G"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = onPrimary
                    )
                }
            }

            // Name + handle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = handleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Decorative accent badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (hasProfile) Color.Transparent else primaryContainer)
            ) {
                if (hasProfile) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_youtube),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
