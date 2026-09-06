package com.unshoo.pixelmusic.presentation.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the slider losing track of playback position: it must follow the current
 * value provider even when the caller swaps the state object behind it, which happens when a caller
 * keys its remember on the current song.
 */
@RunWith(AndroidJUnit4::class)
class WavySliderExpressiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun reportedProgress(): Float {
        val node = composeRule.onNodeWithContentDescription(LABEL).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    @Test
    fun followsValueAfterBackingStateIsReplaced() {
        var stateKey by mutableStateOf(0)
        lateinit var latestState: MutableState<Float>

        composeRule.setContent {
            val position = remember(stateKey) { mutableStateOf(0.25f) }
            latestState = position
            WavySliderExpressive(
                value = { position.value },
                onValueChange = {},
                semanticsLabel = LABEL
            )
        }

        composeRule.waitForIdle()
        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0.25f)

        composeRule.runOnUiThread { stateKey = 1 }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { latestState.value = 0.8f }
        composeRule.waitForIdle()

        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0.8f)
    }

    @Test
    fun reportsNonFiniteValueAsZero() {
        composeRule.setContent {
            val position = remember { mutableFloatStateOf(Float.NaN) }
            WavySliderExpressive(
                value = { position.floatValue },
                onValueChange = {},
                semanticsLabel = LABEL
            )
        }

        composeRule.waitForIdle()
        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0f)
    }

    private companion object {
        const val LABEL = "Playback position"
        const val TOLERANCE = 0.02f
    }
}
