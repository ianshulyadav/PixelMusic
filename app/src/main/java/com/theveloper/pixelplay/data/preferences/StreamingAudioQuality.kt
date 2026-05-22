package com.theveloper.pixelplay.data.preferences

/**
 * Streaming audio quality levels for YouTube playback.
 * Controls the maximum bitrate ceiling when selecting stream formats.
 *
 * On WiFi: user's chosen quality is honored.
 * On metered/mobile data: defaults to LOW unless user overrides with
 * "Force high quality on mobile data" toggle.
 *
 * Playback always starts at the lowest available quality first,
 * then upgrades to the target quality in the background to
 * minimize buffering and ensure < 1s startup time.
 *
 * @property maxBitrateKbps Maximum bitrate ceiling in kbps
 * @property label Human-readable label for Settings UI
 */
enum class StreamingAudioQuality(val maxBitrateKbps: Int, val label: String) {
    LOW(64, "Low (64 kbps) — Saves data"),
    MEDIUM(128, "Medium (128 kbps) — Balanced"),
    HIGH(256, "High (256 kbps) — Best quality");

    companion object {
        fun fromName(name: String?): StreamingAudioQuality {
            return entries.find { it.name == name } ?: HIGH
        }
    }
}
