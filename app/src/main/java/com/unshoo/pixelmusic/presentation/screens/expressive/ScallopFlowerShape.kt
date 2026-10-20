package com.unshoo.pixelmusic.presentation.screens.expressive

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive Scallop / Flower Shape:
 * Creates a wavy flower contour with specified petal lobes.
 * Used for artist avatars and expressive hero cards.
 */
class ScallopFlowerShape(
    val petals: Int = 10,
    val depthRatio: Float = 0.08f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = min(centerX, centerY) * 0.94f
        val amplitude = baseRadius * depthRatio
        val steps = 360

        for (i in 0 until steps) {
            val angle = (i * 2 * Math.PI / steps).toFloat()
            val r = baseRadius + amplitude * cos(petals * angle)
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
