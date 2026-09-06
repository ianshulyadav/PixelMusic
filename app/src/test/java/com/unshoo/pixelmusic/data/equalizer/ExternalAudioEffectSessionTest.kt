package com.unshoo.pixelmusic.data.equalizer

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExternalAudioEffectSessionTest {

    private lateinit var context: Context
    private lateinit var session: ExternalAudioEffectSession

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.packageName } returns "com.unshoo.pixelmusic"
        session = ExternalAudioEffectSession(context)
    }

    @Test
    fun `open announces the session once`() {
        session.open(42)

        verify(exactly = 1) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `re-opening the same session does not re-announce it`() {
        session.open(42)
        session.open(42)
        session.open(42)

        verify(exactly = 1) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `opening a different session closes the previous one first`() {
        session.open(42)
        session.open(43)

        verify(exactly = 3) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `invalid session ids are ignored`() {
        session.open(0)
        session.open(-1)

        verify(exactly = 0) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `close is a no-op when nothing was announced`() {
        session.close()

        verify(exactly = 0) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `close after open detaches once`() {
        session.open(42)
        session.close()
        session.close()

        verify(exactly = 2) { context.sendBroadcast(any<Intent>()) }
    }

    @Test
    fun `a session can be reopened after being closed`() {
        session.open(42)
        session.close()
        session.open(42)

        verify(exactly = 3) { context.sendBroadcast(any<Intent>()) }
    }
}
