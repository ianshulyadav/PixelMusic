package com.unshoo.pixelmusic.data.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TaskRemovedPolicyTest {

    @Test
    fun taskRemoved_continuesPlaybackWhenPlayingAndBackgroundPlaybackEnabled() {
        val shouldContinue = shouldContinuePlaybackAfterTaskRemoved(
            hasForegroundPlaybackIntent = true,
            keepPlayingInBackground = true
        )

        assertThat(shouldContinue).isTrue()
    }

    @Test
    fun taskRemoved_stopsWhenBackgroundPlaybackDisabled() {
        val shouldContinue = shouldContinuePlaybackAfterTaskRemoved(
            hasForegroundPlaybackIntent = true,
            keepPlayingInBackground = false
        )

        assertThat(shouldContinue).isFalse()
    }

    @Test
    fun taskRemoved_stopsWhenNothingIsPlaying() {
        val shouldContinue = shouldContinuePlaybackAfterTaskRemoved(
            hasForegroundPlaybackIntent = false,
            keepPlayingInBackground = true
        )

        assertThat(shouldContinue).isFalse()
    }

    @Test
    fun taskRemoved_stopsWhenIdleAndBackgroundPlaybackDisabled() {
        val shouldContinue = shouldContinuePlaybackAfterTaskRemoved(
            hasForegroundPlaybackIntent = false,
            keepPlayingInBackground = false
        )

        assertThat(shouldContinue).isFalse()
    }
}
