package com.unshoo.pixelmusic.presentation.components.subcomps

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * A Glance Composable that automatically adjusts the text font size
 * to fill the specified dimensions.
 *
 * NOTE: Unlike Jetpack Compose, Glance requires the width and height
 * (`width` and `height`) to be specified explicitly for the calculation to work.
 *
 * @param text The text to display.
 * @param modifier The GlanceModifier to apply to the container.
 * @param style The base text style. The font size will be overridden, but properties
 * such as fontWeight will be respected.
 * @param color The text color.
 * @param width The exact width of the area available for the text.
 * @param height The exact height of the area available for the text.
 * @param textAlign The text alignment.
 * @param minFontSize The smallest allowed font size.
 * @param maxFontSize The largest allowed font size.
 */
@Composable
fun AutoSizingTextGlance(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    style: TextStyle,
    color: ColorProvider,
    width: Dp,
    height: Dp,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 8.sp,
    maxFontSize: TextUnit = 100.sp
) {
    val context = LocalContext.current
    val textColor = color.getColor(context).toArgb()
    val density = context.resources.displayMetrics.density

    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()

    val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor
        this.typeface = when (style.fontWeight) {
            FontWeight.Bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            FontWeight.Medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
    }

    val alignment = when (textAlign) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }


    var lowerBound = minFontSize.value
    var upperBound = maxFontSize.value
    var bestSize = lowerBound

    if (widthPx > 0 && heightPx > 0) {
        while (lowerBound <= upperBound) {
            val mid = (lowerBound + upperBound) / 2
            if (mid <= 0) break

            textPaint.textSize = mid * density

            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            if (staticLayout.height <= heightPx) {
                bestSize = mid
                lowerBound = mid + 0.1f
            } else {
                upperBound = mid - 0.1f
            }
        }
    }

    textPaint.textSize = bestSize * density
    val finalLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()

    finalLayout.draw(canvas)

    Box(
        modifier = modifier.size(width, height),
        contentAlignment = Alignment.CenterStart
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = text,
            modifier = GlanceModifier
                .fillMaxSize()
        )
    }
}