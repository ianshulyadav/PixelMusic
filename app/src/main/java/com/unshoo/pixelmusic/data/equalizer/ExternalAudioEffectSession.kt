package com.unshoo.pixelmusic.data.equalizer

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces the player's audio session to external audio effect apps
 * (ViPER4Android, JamesDSP, Wavelet, the OEM "audio effects" panel, ...).
 *
 * Those apps do not process an app's output unless they are told which audio session to attach
 * to: they listen for [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION] and create their own
 * effect on the session id carried by the broadcast. Without it our output is either untouched or
 * only incidentally processed, which is exactly the "V4A does nothing until I enable the built-in
 * equalizer" symptom — attaching *any* effect to the session is what pulled the track off the
 * offloaded output where system effects are bypassed.
 *
 * Both actions are on Android's implicit-broadcast exemption list, so manifest-declared receivers
 * in those apps still receive them on O+.
 */
@Singleton
class ExternalAudioEffectSession @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private companion object {
        private const val TAG = "ExternalAudioEffect"
    }

    /** Session id we last announced as open, or `null` when nothing is open. */
    private var openedSessionId: Int? = null

    /**
     * Announces [audioSessionId] as available for external effects, closing any previously
     * announced session first. Safe to call repeatedly with the same id.
     */
    @Synchronized
    fun open(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (openedSessionId == audioSessionId) return

        openedSessionId?.let { previous ->
            broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, previous)
        }
        openedSessionId = audioSessionId
        broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    /**
     * Tells external effect apps to detach from the session, so they release their effect engines
     * once we stop owning the session. No-op when nothing is open.
     */
    @Synchronized
    fun close() {
        val sessionId = openedSessionId ?: return
        openedSessionId = null
        broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId)
    }

    private fun broadcast(action: String, audioSessionId: Int) {
        val intent = Intent(action).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        try {
            context.sendBroadcast(intent)
            Timber.tag(TAG).d("Broadcast %s for session %d", action, audioSessionId)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to broadcast %s for session %d", action, audioSessionId)
        }
    }
}
