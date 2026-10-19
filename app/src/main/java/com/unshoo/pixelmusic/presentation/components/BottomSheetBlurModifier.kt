package com.unshoo.pixelmusic.presentation.components

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Applies native hardware window blur behind the Bottom Sheet on Android 12+ (API 31+).
 */
@Composable
fun ApplyBottomSheetWindowBlur(isBlurActive: Boolean) {
    if (!isBlurActive || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    DisposableEffect(view, isBlurActive) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: (view.context as? Activity)?.window
        window?.let { win ->
            try {
                win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                win.attributes.blurBehindRadius = 50
            } catch (_: Exception) {
            }
        }
        onDispose { }
    }
}
