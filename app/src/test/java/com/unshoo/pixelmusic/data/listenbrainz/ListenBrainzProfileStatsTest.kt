package com.unshoo.pixelmusic.data.listenbrainz

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ListenBrainzProfileStatsTest {

    @Test
    fun nullWhenServerExposesNeitherEndpoint() {
        val stats = buildProfileStats(
            listenCount = null,
            playingNowAvailable = false,
            nowPlaying = null
        )

        assertThat(stats).isNull()
    }

    @Test
    fun carriesListenCountWithoutPlayingNowSupport() {
        val stats = buildProfileStats(
            listenCount = 1234,
            playingNowAvailable = false,
            nowPlaying = null
        )

        assertThat(stats).isEqualTo(
            ListenBrainzProfileStats(
                listenCount = 1234,
                playingNowAvailable = false,
                nowPlayingTrack = null,
                nowPlayingArtist = null
            )
        )
    }

    @Test
    fun supportedButIdlePlayingNowKeepsNullTrack() {
        val stats = buildProfileStats(
            listenCount = 42,
            playingNowAvailable = true,
            nowPlaying = null
        )

        assertThat(stats?.playingNowAvailable).isTrue()
        assertThat(stats?.nowPlayingTrack).isNull()
    }

    @Test
    fun mapsNowPlayingTrackAndArtist() {
        val stats = buildProfileStats(
            listenCount = 42,
            playingNowAvailable = true,
            nowPlaying = ListenBrainzTrackMetadata(
                artistName = "Rick Astley",
                trackName = "Never Gonna Give You Up"
            )
        )

        assertThat(stats?.nowPlayingTrack).isEqualTo("Never Gonna Give You Up")
        assertThat(stats?.nowPlayingArtist).isEqualTo("Rick Astley")
    }
}
