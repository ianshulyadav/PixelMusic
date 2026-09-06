package com.unshoo.pixelmusic.presentation.components.subcomps

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizingTextToFill(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    minFontSize: TextUnit = 8.sp,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    maxFontSizeLimit: TextUnit = 100.sp,
    lineHeightRatio: Float = 1.2f
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var currentFontSize by remember { mutableStateOf(minFontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val maxHeightPx = with(density) { maxHeight.toPx() }.toInt()

        LaunchedEffect(text, style, minFontSize, maxFontSizeLimit, lineHeightRatio, maxWidthPx, maxHeightPx) {
            readyToDraw = false
            var bestFitFontSize = minFontSize

            var lowerBoundSp = minFontSize.value
            var upperBoundSp = maxFontSizeLimit.value.coerceAtLeast(minFontSize.value)

            if (lowerBoundSp > upperBoundSp + 0.01f) {
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            }

            val minFontEffectiveLineHeight = minFontSize * lineHeightRatio
            val minFontEffectiveStyle = style.copy(
                fontSize = minFontSize,
                lineHeight = minFontEffectiveLineHeight
            )
            val minFontLayoutResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = minFontEffectiveStyle,
                overflow = TextOverflow.Clip,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                constraints = Constraints(
                    maxWidth = maxWidthPx.coerceAtLeast(0),
                    maxHeight = maxHeightPx.coerceAtLeast(0)
                )
            )

            if (minFontLayoutResult.hasVisualOverflow) {
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            } else {
                bestFitFontSize = minFontSize
            }

            repeat(15) {
                if (upperBoundSp - lowerBoundSp < 0.1f) {
                    currentFontSize = bestFitFontSize
                    readyToDraw = true
                    return@LaunchedEffect
                }

                val midSp = (lowerBoundSp + upperBoundSp) / 2f
                val candidateFontSize = midSp.sp

                if (candidateFontSize.value < bestFitFontSize.value && candidateFontSize.value < midSp) {
                    lowerBoundSp = midSp + 0.01f
                    return@repeat
                }

                val currentEffectiveLineHeight = candidateFontSize * lineHeightRatio
                val candidateStyle = style.copy(
                    fontSize = candidateFontSize,
                    lineHeight = currentEffectiveLineHeight
                )

                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = candidateStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(
                        maxWidth = maxWidthPx.coerceAtLeast(0),
                        maxHeight = maxHeightPx.coerceAtLeast(0)
                    )
                )

                if (layoutResult.hasVisualOverflow) {
                    upperBoundSp = midSp - 0.01f
                } else {
                    bestFitFontSize = candidateFontSize
                    lowerBoundSp = midSp + 0.01f
                }
            }

            currentFontSize = bestFitFontSize
            readyToDraw = true
        }

        if (readyToDraw) {
            val finalEffectiveLineHeight = currentFontSize * lineHeightRatio
            Text(
                text = text,
                modifier = Modifier,
                style = style.copy(
                    fontSize = currentFontSize,
                    lineHeight = finalEffectiveLineHeight
                ),
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                maxLines = Int.MAX_VALUE
            )
        }
    }
}