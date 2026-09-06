package com.unshoo.pixelmusic.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * OPT #6 — Cached instances of frequently-used AbsoluteSmoothCornerShape.
 *
 * AbsoluteSmoothCornerShape is significantly more expensive than RoundedCornerShape because it
 * computes cubic Bézier curves analytically. In LazyColumn items (song cards, album cards, etc.)
 * each item pays this cost on first composition. By reusing singleton instances we avoid
 * repeated Path construction for the most common radii.
 *
 * Usage:
 *   Modifier.clip(ShapeCache.smooth12)
 *   Modifier.background(color, ShapeCache.smooth16)
 */
object ShapeCache {
    /** 8dp smooth corners — compact chips, small surfaces */
    val smooth8 = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)

    /** 10dp smooth corners */
    val smooth10 = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)

    /** 12dp smooth corners — song list items, small cards */
    val smooth12 = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)

    /** 14dp smooth corners */
    val smooth14 = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)

    /** 16dp smooth corners — album cards, playlist items */
    val smooth16 = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)

    /** 20dp smooth corners — larger cards */
    val smooth20 = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)

    /** 24dp smooth corners — dialog surfaces */
    val smooth24 = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)

    /** 28dp smooth corners */
    val smooth28 = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)

    /** 32dp smooth corners — bottom sheets, floating panels */
    val smooth32 = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)

    /** Fully smooth (pill) — 50dp, used for buttons and chips */
    val smoothPill = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)

    /** M3 Expressive cookie — artist/people avatars and placeholders */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val expressiveAvatar: Shape = RoundedPolygonShape(MaterialShapes.Cookie9Sided)

    /** M3 Expressive clover — folder/collection icon containers */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val expressiveClover: Shape = RoundedPolygonShape(MaterialShapes.Clover8Leaf)

    /** M3 Expressive soft burst — empty-state hero icon containers */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val expressiveHero: Shape = RoundedPolygonShape(MaterialShapes.SoftBurst)
}

/**
 * Singleton-friendly alternative to material3's @Composable RoundedPolygon.toShape():
 * the normalized (0..1 bounds) polygon path is computed once and scaled to the outline size,
 * so the shape can be cached here like the other ShapeCache entries.
 */
private class RoundedPolygonShape(polygon: RoundedPolygon) : Shape {
    private val basePath = polygon.toPath().asComposePath()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        val path = Path()
        path.addPath(basePath)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
