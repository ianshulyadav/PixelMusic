package com.unshoo.pixelmusic.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.unshoo.pixelmusic.BottomNavItem
import com.unshoo.pixelmusic.data.preferences.NavBarStyle
import com.unshoo.pixelmusic.presentation.components.scoped.CustomNavigationBarItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 76.dp
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight
private val FloatingPillContentHeight = 76.dp
private val MainScreenBottomGradientExtraHeight = 76.dp + MiniPlayerBottomSpacer + 8.dp
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    (if (compactMode) NavBarCompactContentHeight else NavBarContentHeight) + heightOffset

internal fun resolveNavBarContentHeight(navBarStyle: String, compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    when (navBarStyle) {
        NavBarStyle.FLOATING_PILL -> FloatingPillContentHeight + heightOffset
        else -> resolveNavBarContentHeight(compactMode, heightOffset)
    }

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    resolveNavBarContentHeight(compactMode, heightOffset) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean,
    heightOffset: Dp = 0.dp
): Dp {
    val contentHeight = resolveNavBarContentHeight(navBarStyle, compactMode, heightOffset)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else {
        contentHeight
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarOccupiedHeight(NavBarStyle.DEFAULT, systemNavBarInset, compactMode, 0.dp)

internal fun resolveNavBarOccupiedHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean,
    heightOffset: Dp = 0.dp
): Dp {
    val contentHeight = resolveNavBarContentHeight(navBarStyle, compactMode, heightOffset)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else if (navBarStyle == NavBarStyle.FLOATING_PILL) {
        val bottomMargin = if (systemNavBarInset > 0.dp) systemNavBarInset else 6.dp
        contentHeight + bottomMargin
    } else {
        val bottomMargin = if (systemNavBarInset > 0.dp) systemNavBarInset else 14.dp
        contentHeight + bottomMargin
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveFloatingPillNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)
    var lastSearchTapTimestamp by remember { mutableStateOf(0L) }

    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = configuration.screenWidthDp

    val searchItem = navItems.find { it.screen.route == Screen.Search.route }
    val mainItems = navItems.filter { it.screen.route != Screen.Search.route }

    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && mainItems.size > 3)

    val selectedIndex = mainItems.indexOfFirst { it.screen.route == latestCurrentRoute }

    val motionScheme = MaterialTheme.motionScheme

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tomato-style Material You Expressive Floating Pill Navigation Bar
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .weight(1f)
                .shadow(elevation = 6.dp, shape = CircleShape),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = solidTintedColor
            ),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            mainItems.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val itemInteractionSource = remember { MutableInteractionSource() }
                val isItemPressed by itemInteractionSource.collectIsPressedAsState()
                val isItemHovered by itemInteractionSource.collectIsHoveredAsState()

                val itemTactileScale by animateFloatAsState(
                    targetValue = when {
                        isItemPressed -> 0.93f
                        isItemHovered -> 1.03f
                        else -> 1.0f
                    },
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "pill_item_tactile_$index"
                )

                val dynamicActiveWeight = 1.4f + (item.label.length * 0.20f)
                val itemWeight by animateFloatAsState(
                    targetValue = if (isSelected && !shouldHideLabel) dynamicActiveWeight else 1.0f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "pill_item_weight_$index"
                )

                val animColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "pill_bg_$index"
                )

                val animContentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "pill_content_$index"
                )

                val iconBounceScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "pill_icon_bounce_$index"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (latestNavigationEnabled && latestCurrentRoute != item.screen.route) {
                            navController.navigateToTopLevelSafely(item.screen.route)
                        }
                    },
                    modifier = Modifier
                        .weight(itemWeight)
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = itemTactileScale
                            scaleY = itemTactileScale
                        },
                    shape = CircleShape,
                    color = animColor,
                    contentColor = animContentColor,
                    interactionSource = itemInteractionSource
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                    ) {
                        val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                            item.selectedIconResId
                        } else {
                            item.iconResId
                        }

                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = item.label,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    scaleX = iconBounceScale
                                    scaleY = iconBounceScale
                                }
                        )

                        AnimatedVisibility(
                            visible = isSelected && !shouldHideLabel,
                            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                expandHorizontally(
                                    expandFrom = Alignment.Start,
                                    animationSpec = motionScheme.fastSpatialSpec()
                                ),
                            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec()) +
                                shrinkHorizontally(
                                    shrinkTowards = Alignment.Start,
                                    animationSpec = motionScheme.fastSpatialSpec()
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                                    maxLines = 1,
                                    fontWeight = FontWeight.ExtraBold,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Disconnected Search FAB — M3 Expressive floating action button
        if (searchItem != null) {
            val isSearchSelected = latestCurrentRoute == searchItem.screen.route
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
            val isSearchHovered by searchInteractionSource.collectIsHoveredAsState()

            val fabScale by animateFloatAsState(
                targetValue = when {
                    isSearchPressed -> 0.92f
                    isSearchHovered -> 1.06f
                    isSearchSelected -> 1.03f
                    else -> 1.0f
                },
                animationSpec = motionScheme.fastSpatialSpec(),
                label = "search_fab_scale"
            )

            val searchIconBounce by animateFloatAsState(
                targetValue = if (isSearchSelected) 1.15f else 1.0f,
                animationSpec = motionScheme.fastSpatialSpec(),
                label = "search_icon_bounce"
            )

            val searchIconRes = if (isSearchSelected && searchItem.selectedIconResId != null && searchItem.selectedIconResId != 0) {
                searchItem.selectedIconResId
            } else {
                searchItem.iconResId
            }

            val searchColor by animateColorAsState(
                targetValue = if (isSearchSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    solidTintedColor
                },
                animationSpec = motionScheme.defaultEffectsSpec(),
                label = "search_fab_bg"
            )

            val searchContentColor by animateColorAsState(
                targetValue = if (isSearchSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                animationSpec = motionScheme.defaultEffectsSpec(),
                label = "search_fab_content"
            )

            Surface(
                onClick = {
                    if (!latestNavigationEnabled) return@Surface

                    val now = SystemClock.elapsedRealtime()
                    val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                    lastSearchTapTimestamp = now

                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    if (!isSearchSelected) {
                        navController.navigateToTopLevelSafely(searchItem.screen.route)
                    } else if (isDoubleTap) {
                        lastSearchTapTimestamp = 0L
                        onSearchIconDoubleTap()
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    },
                shape = CircleShape,
                color = searchColor,
                contentColor = searchContentColor,
                tonalElevation = if (isSearchHovered) 4.dp else 2.dp,
                shadowElevation = if (isSearchHovered) 8.dp else 6.dp,
                interactionSource = searchInteractionSource
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(id = searchIconRes),
                        contentDescription = searchItem.label,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = searchIconBounce
                                scaleY = searchIconBounce
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        var lastSearchTapTimestamp by remember { mutableStateOf(0L) }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
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
            val indicatorColorFromTheme = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                solidTintedColor
            }
            val selectedIconColor = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            }
            val unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            val selectedTextColor = MaterialTheme.colorScheme.onSurface
            val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
                null
            } else {
                remember(item.label) {
                    { Text(item.label) }
                }
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }
            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isSelected,
                onClick = onClickLambda,
                enabled = currentRoute != null,
                compactMode = compactMode,
                icon = iconLambda,
                selectedIcon = selectedIconLambda,
                label = labelLambda,
                contentDescription = item.label,
                // Label only the selected tab, and drop labels entirely on compact
                // bars / large font scales. Previously every tab kept its label
                // composed permanently.
                alwaysShowLabel = false,
                selectedIconColor = selectedIconColor,
                unselectedIconColor = unselectedIconColor,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedTextColor,
                indicatorColor = indicatorColorFromTheme
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    if (navBarStyle == NavBarStyle.FLOATING_PILL) {
        ExpressiveFloatingPillNavigationBar(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            modifier = modifier,
            onSearchIconDoubleTap = onSearchIconDoubleTap
        )
    } else {
        PlayerInternalNavigationItemsRow(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            navBarStyle = navBarStyle,
            compactMode = compactMode,
            bottomBarPadding = bottomBarPadding,
            onSearchIconDoubleTap = onSearchIconDoubleTap,
            modifier = modifier
        )
    }
}
