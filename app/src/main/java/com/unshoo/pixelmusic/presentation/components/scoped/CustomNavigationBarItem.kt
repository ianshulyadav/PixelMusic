package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.ui.theme.defaultEffects
import com.unshoo.pixelmusic.ui.theme.fastEffects
import com.unshoo.pixelmusic.ui.theme.fastSpatial

/**
 * Material 3 Expressive Navigation Bar Item:
 * - Active indicator: 68dp × 38dp stadium pill (60dp × 32dp in compact mode) with full zoom-in / zoom-out transformation
 * - Touch & Ripple: Contained strictly inside the selection pill shape (CircleShape), with no external ghost halos
 * - Active icon: 24dp with 1.15x spatial bounce and smooth crossfade between unselected and selected icons
 * - Tactile press compression (0.92x spring) & responsive hover feedback (1.04x scale)
 * - M3 Expressive typography and role-based contrast tokens
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compactMode: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val motionScheme = MaterialTheme.motionScheme

    // Track interaction states for tactile press and pointer hover
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Tactile press & hover scale for the entire interactive item
    val pressScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            isHovered -> 1.04f
            else -> 1.0f
        },
        animationSpec = motionScheme.fastSpatial(),
        label = "nav_item_press_scale"
    )

    // Active Indicator transformation:
    // Full zoom-in / zoom-out selection container resizing transformation with M3 Expressive physics
    val targetPillAlpha = when {
        selected -> 1.0f
        isPressed -> 0.35f
        isHovered -> 0.15f
        else -> 0.0f
    }
    val targetPillScaleX = when {
        selected -> 1.0f
        isPressed -> 0.65f
        isHovered -> 0.85f
        else -> 0.25f
    }
    val targetPillScaleY = when {
        selected -> 1.0f
        isPressed -> 0.75f
        isHovered -> 0.90f
        else -> 0.40f
    }

    val pillScaleX by animateFloatAsState(
        targetValue = targetPillScaleX,
        animationSpec = motionScheme.fastSpatial(),
        label = "nav_pill_scale_x"
    )
    val pillScaleY by animateFloatAsState(
        targetValue = targetPillScaleY,
        animationSpec = motionScheme.fastSpatial(),
        label = "nav_pill_scale_y"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = targetPillAlpha,
        animationSpec = motionScheme.fastEffects(),
        label = "nav_pill_alpha"
    )

    // Icon scale: lively spring bounce on selection (fast spatial token)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = motionScheme.fastSpatial(),
        label = "nav_icon_scale"
    )

    // Icon crossfade opacities for smooth transition between unselected and selected icons
    val selectedIconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionScheme.fastEffects(),
        label = "nav_selected_icon_alpha"
    )
    val unselectedIconAlpha by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = motionScheme.fastEffects(),
        label = "nav_unselected_icon_alpha"
    )

    // Color animations
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = motionScheme.defaultEffects(),
        label = "nav_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = motionScheme.defaultEffects(),
        label = "nav_text_color"
    )

    // Determine label visibility and animations
    val showLabel = label != null && (alwaysShowLabel || selected)
    val labelProgress by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = motionScheme.fastEffects(),
        label = "nav_label_progress"
    )
    val labelScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.94f,
        animationSpec = motionScheme.fastSpatial(),
        label = "nav_label_scale"
    )

    // Material 3 Expressive Dimensions
    // Enlarged for comfortable, expressive M3 look
    val indicatorWidth = if (compactMode) 60.dp else 68.dp
    val indicatorHeight = if (compactMode) 32.dp else 38.dp
    val indicatorShape = CircleShape
    val iconSize = 24.dp

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null // Column handles tap without drawing any separated ripple
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon & Indicator Container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(indicatorWidth)
                .height(indicatorHeight)
        ) {
            // 1. Selection Pill Container with Zoom-In / Zoom-Out Resizing Transformation & Contained Ripple
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pillScaleX * pressScale
                        scaleY = pillScaleY * pressScale
                        alpha = pillAlpha
                    }
                    .clip(indicatorShape)
                    .background(
                        color = indicatorColor,
                        shape = indicatorShape
                    )
                    .indication(
                        interactionSource = interactionSource,
                        indication = ripple(
                            bounded = true,
                            color = selectedIconColor.copy(alpha = 0.28f)
                        )
                    )
            )

            // 2. Icon with Transformation, Scale, and Crossfade Morphing
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        val combinedScale = iconScale * pressScale
                        scaleX = combinedScale
                        scaleY = combinedScale
                    }
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    // Unselected icon
                    if (unselectedIconAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = unselectedIconAlpha
                                    val scale = 0.85f + (0.15f * unselectedIconAlpha)
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clearAndSetSemantics {}
                        ) {
                            icon()
                        }
                    }

                    // Selected icon
                    if (selectedIconAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = selectedIconAlpha
                                    val scale = 0.85f + (0.15f * selectedIconAlpha)
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clearAndSetSemantics {}
                        ) {
                            selectedIcon()
                        }
                    }
                }
            }
        }

        // Label with M3 Expressive typography and smooth scale/alpha transition
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = labelScale * labelProgress
                    scaleX = scale
                    scaleY = scale
                    alpha = labelProgress
                }
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    )
                ) {
                    label()
                }
            }
        }
    }
}


