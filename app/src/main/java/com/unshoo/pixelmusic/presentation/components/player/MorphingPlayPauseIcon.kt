package com.unshoo.pixelmusic.presentation.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.unshoo.pixelmusic.R

private val ICON_CORNER = CornerRounding(radius = 0.07f)
private val SEAM_CORNER = CornerRounding.Unrounded

private fun quad(vertices: FloatArray, roundings: List<CornerRounding>) =
    RoundedPolygon(vertices, perVertexRounding = roundings)

private val LEFT_MORPH = Morph(
    quad(
        floatArrayOf(0.333f, 0.208f, 0.58f, 0.365f, 0.58f, 0.635f, 0.333f, 0.792f),
        listOf(ICON_CORNER, SEAM_CORNER, SEAM_CORNER, ICON_CORNER)
    ),
    quad(
        floatArrayOf(0.25f, 0.208f, 0.417f, 0.208f, 0.417f, 0.792f, 0.25f, 0.792f),
        listOf(ICON_CORNER, ICON_CORNER, ICON_CORNER, ICON_CORNER)
    )
)

private val RIGHT_MORPH = Morph(
    quad(
        floatArrayOf(0.54f, 0.34f, 0.792f, 0.485f, 0.792f, 0.515f, 0.54f, 0.66f),
        listOf(SEAM_CORNER, ICON_CORNER, ICON_CORNER, SEAM_CORNER)
    ),
    quad(
        floatArrayOf(0.583f, 0.208f, 0.75f, 0.208f, 0.75f, 0.792f, 0.583f, 0.792f),
        listOf(ICON_CORNER, ICON_CORNER, ICON_CORNER, ICON_CORNER)
    )
)

/**
 * A play/pause glyph that morphs between its two states by interpolating shape geometry
 * (YouTube-style) instead of crossfading two icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playPauseMorph"
    )
    val leftAndroidPath = remember { android.graphics.Path() }
    val rightAndroidPath = remember { android.graphics.Path() }
    val leftPath = remember { leftAndroidPath.asComposePath() }
    val rightPath = remember { rightAndroidPath.asComposePath() }
    val glyphPath = remember { androidx.compose.ui.graphics.Path() }
    val contentDesc = if (isPlaying) {
        stringResource(R.string.cd_pause)
    } else {
        stringResource(R.string.cd_play)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDesc }
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        LEFT_MORPH.toPath(fraction, leftAndroidPath)
        RIGHT_MORPH.toPath(fraction, rightAndroidPath)
        glyphPath.reset()
        glyphPath.addPath(leftPath)
        glyphPath.addPath(rightPath)
        scale(this.size.minDimension, pivot = Offset.Zero) {
            drawPath(glyphPath, tint)
        }
    }
}
