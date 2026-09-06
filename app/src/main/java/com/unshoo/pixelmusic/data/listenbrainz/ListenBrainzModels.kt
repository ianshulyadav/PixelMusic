package com.unshoo.pixelmusic.data.listenbrainz

import com.google.gson.annotations.SerializedName

/**
 * Request body for `POST /1/submit-listens`.
 * Gson omits null fields, which matters: `playing_now` payloads must not carry `listened_at`.
 */
data class ListenBrainzSubmission(
    @SerializedName("listen_type") val listenType: String,
    @SerializedName("payload") val payload: List<ListenBrainzListen>
) {
    companion object {
        const val TYPE_PLAYING_NOW = "playing_now"
        const val TYPE_IMPORT = "import"
    }
}

data class ListenBrainzListen(
    /** Epoch seconds of when the listen started. Null for `playing_now`. */
    @SerializedName("listened_at") val listenedAt: Long? = null,
    @SerializedName("track_metadata") val trackMetadata: ListenBrainzTrackMetadata
)

data class ListenBrainzTrackMetadata(
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("track_name") val trackName: String,
    @SerializedName("release_name") val releaseName: String? = null,
    @SerializedName("additional_info") val additionalInfo: ListenBrainzAdditionalInfo? = null
)

data class ListenBrainzAdditionalInfo(
    @SerializedName("media_player") val mediaPlayer: String? = null,
    @SerializedName("submission_client") val submissionClient: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("recording_mbid") val recordingMbid: String? = null
)

/** Response of `GET /1/validate-token`. */
data class ListenBrainzTokenValidation(
    @SerializedName("valid") val valid: Boolean = false,
    @SerializedName("user_name") val userName: String? = null
)

/** Response of `GET /1/user/{user}/listen-count`. */
data class ListenBrainzListenCountResponse(
    @SerializedName("payload") val payload: ListenBrainzListenCountPayload? = null
)

data class ListenBrainzListenCountPayload(
    @SerializedName("count") val count: Long = 0
)

/** Response of `GET /1/user/{user}/playing-now`. */
data class ListenBrainzPlayingNowResponse(
    @SerializedName("payload") val payload: ListenBrainzPlayingNowPayload? = null
)

data class ListenBrainzPlayingNowPayload(
    @SerializedName("listens") val listens: List<ListenBrainzListen> = emptyList()
)

/**
 * Profile numbers shown on the Accounts screen. Null [listenCount] or a false
 * [playingNowAvailable] means the connected server doesn't expose that endpoint
 * (Maloja's ListenBrainz shim only implements submission), not an error state.
 */
data class ListenBrainzProfileStats(
    val listenCount: Long? = null,
    val playingNowAvailable: Boolean = false,
    val nowPlayingTrack: String? = null,
    val nowPlayingArtist: String? = null
)

internal fun buildProfileStats(
    listenCount: Long?,
    playingNowAvailable: Boolean,
    nowPlaying: ListenBrainzTrackMetadata?
): ListenBrainzProfileStats? {
    if (listenCount == null && !playingNowAvailable) return null
    return ListenBrainzProfileStats(
        listenCount = listenCount,
        playingNowAvailable = playingNowAvailable,
        nowPlayingTrack = nowPlaying?.trackName,
        nowPlayingArtist = nowPlaying?.artistName
    )
}

/** Connection state surfaced on the Accounts screen. */
data class ListenBrainzAccountState(
    val isConnected: Boolean = false,
    val userName: String? = null,
    /** True when the stored token was rejected (HTTP 401); flushing is paused until reconnect. */
    val needsReauth: Boolean = false,
    /** Custom ListenBrainz-compatible API root; null means the official endpoint. */
    val serverUrl: String? = null
)

sealed interface ListenBrainzSubmitResult {
    data object Success : ListenBrainzSubmitResult

    /** Token rejected; queue flushing pauses until the user reconnects. */
    data object AuthFailed : ListenBrainzSubmitResult

    /** The server rejected the payload as invalid (HTTP 400) — permanent for this payload. */
    data object InvalidPayload : ListenBrainzSubmitResult

    /**
     * Rate limited or transient failure — retry later with backoff, but no earlier than
     * [retryAfterSeconds] when the server sent a retry window.
     */
    data class TransientError(val retryAfterSeconds: Long? = null) : ListenBrainzSubmitResult
}
