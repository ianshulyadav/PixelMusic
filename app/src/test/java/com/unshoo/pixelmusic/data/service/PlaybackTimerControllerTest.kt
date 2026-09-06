package com.unshoo.pixelmusic.data.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackTimerControllerTest {

    private class FakeAlarmScheduler : SleepTimerAlarmScheduler {
        val scheduledAt = mutableListOf<Long>()
        var cancelCount = 0

        override fun schedule(triggerAtMillis: Long) {
            scheduledAt += triggerAtMillis
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private lateinit var player: Player
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var controller: PlaybackTimerController
    private var nowMillis = 1_000_000L

    private fun mediaItem(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    @BeforeEach
    fun setUp() {
        player = mockk(relaxed = true)
        alarmScheduler = FakeAlarmScheduler()
        controller = PlaybackTimerController(
            playerProvider = { player },
            alarmScheduler = alarmScheduler,
            clock = { nowMillis },
        )
    }

    @Nested
    inner class DurationTimer {

        @Test
        fun `schedules alarm at now plus requested minutes`() {
            controller.setDurationSleepTimer(15)

            assertEquals(listOf(nowMillis + 15 * 60_000L), alarmScheduler.scheduledAt)
            assertEquals(0, alarmScheduler.cancelCount)
        }

        @Test
        fun `non-positive minutes cancels instead of scheduling`() {
            controller.setDurationSleepTimer(0)

            assertEquals(emptyList<Long>(), alarmScheduler.scheduledAt)
            assertEquals(1, alarmScheduler.cancelCount)
        }

        @Test
        fun `expiry cancels the alarm and pauses playback`() {
            controller.onDurationSleepTimerExpired()

            assertEquals(1, alarmScheduler.cancelCount)
            verify { player.pause() }
        }
    }

    @Nested
    inner class EndOfTrackTimer {

        private fun armEndOfTrackTimer(songId: String = "song-1") {
            every { player.currentMediaItem } returns mediaItem(songId)
            controller.setEndOfTrackSleepTimer(true)
        }

        @Test
        fun `arming cancels any duration alarm`() {
            armEndOfTrackTimer()

            assertEquals(1, alarmScheduler.cancelCount)
        }

        @Test
        fun `auto transition away from tracked song pauses and rewinds`() {
            armEndOfTrackTimer("song-1")
            every { player.previousMediaItemIndex } returns 0
            every { player.getMediaItemAt(0) } returns mediaItem("song-1")

            controller.handleMediaItemTransition(
                mediaItem("song-2"),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )

            verify { player.seekTo(0L) }
            verify { player.pause() }
        }

        @Test
        fun `auto transition from an unrelated song does not pause`() {
            armEndOfTrackTimer("song-1")
            every { player.previousMediaItemIndex } returns 0
            every { player.getMediaItemAt(0) } returns mediaItem("other-song")

            controller.handleMediaItemTransition(
                mediaItem("song-2"),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )

            verify(exactly = 0) { player.pause() }
        }

        @Test
        fun `manual track change clears the timer`() {
            armEndOfTrackTimer("song-1")

            controller.handleMediaItemTransition(
                mediaItem("song-2"),
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            )
            every { player.previousMediaItemIndex } returns 0
            every { player.getMediaItemAt(0) } returns mediaItem("song-1")
            controller.handleMediaItemTransition(
                mediaItem("song-3"),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )

            verify(exactly = 0) { player.pause() }
        }

        @Test
        fun `arming without an active song is ignored`() {
            every { player.currentMediaItem } returns null
            controller.setEndOfTrackSleepTimer(true)

            every { player.previousMediaItemIndex } returns C.INDEX_UNSET
            controller.handleMediaItemTransition(
                mediaItem("song-2"),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )

            verify(exactly = 0) { player.pause() }
        }
    }

    @Nested
    inner class CountedPlay {

        private lateinit var listenerSlot: CapturingSlot<Player.Listener>

        @BeforeEach
        fun armCountedPlay() {
            listenerSlot = slot()
            every { player.currentMediaItem } returns mediaItem("song-1")
            justRun { player.addListener(capture(listenerSlot)) }
        }

        private fun autoReplay(listener: Player.Listener) {
            listener.onPositionDiscontinuity(
                mockk(relaxed = true),
                mockk(relaxed = true),
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            )
        }

        @Test
        fun `start forces repeat-one and registers a listener`() {
            controller.startCountedPlay(3)

            verify { player.repeatMode = Player.REPEAT_MODE_ONE }
            verify { player.addListener(any()) }
        }

        @Test
        fun `pauses after the requested number of plays and restores repeat mode`() {
            controller.startCountedPlay(2)
            val listener = listenerSlot.captured

            autoReplay(listener)
            verify(exactly = 0) { player.pause() }

            autoReplay(listener)
            verify { player.pause() }
            verify { player.repeatMode = Player.REPEAT_MODE_OFF }
            verify { player.removeListener(listener) }
        }

        @Test
        fun `manual song change cancels counted play`() {
            controller.startCountedPlay(5)
            val listener = listenerSlot.captured

            listener.onMediaItemTransition(
                mediaItem("song-2"),
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            )

            verify { player.removeListener(listener) }
            verify { player.repeatMode = Player.REPEAT_MODE_OFF }
        }

        @Test
        fun `user repeat mode change cancels without fighting back`() {
            controller.startCountedPlay(5)
            val listener = listenerSlot.captured

            listener.onRepeatModeChanged(Player.REPEAT_MODE_ALL)

            verify { player.removeListener(listener) }
            verify(exactly = 0) { player.repeatMode = Player.REPEAT_MODE_OFF }
        }

        @Test
        fun `start without a current item is a no-op`() {
            every { player.currentMediaItem } returns null

            controller.startCountedPlay(3)

            verify(exactly = 0) { player.addListener(any()) }
        }
    }

    @Test
    fun `release cancels alarm and counted play`() {
        every { player.currentMediaItem } returns mediaItem("song-1")
        val listenerSlot = slot<Player.Listener>()
        justRun { player.addListener(capture(listenerSlot)) }
        controller.startCountedPlay(3)

        controller.release()

        assertEquals(1, alarmScheduler.cancelCount)
        verify { player.removeListener(listenerSlot.captured) }
    }
}
