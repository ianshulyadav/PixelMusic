package com.unshoo.pixelmusic.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * [SheetState] for a modal bottom sheet host, hidden initially.
 *
 * Replacement for the deprecated `rememberModalBottomSheetState`; unlike the deprecated
 * function, the partially expanded state is controlled solely by [skipPartiallyExpanded]
 * (no automatic exclusion based on sheet height).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberModalSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = if (skipPartiallyExpanded) {
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    } else {
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    },
    confirmValueChange = confirmValueChange,
)
