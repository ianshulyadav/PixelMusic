package com.unshoo.pixelmusic.data.network.navidrome

import com.google.common.truth.Truth.assertThat
import com.unshoo.pixelmusic.data.navidrome.model.NavidromeAuthMethod
import org.junit.jupiter.api.Test

class NavidromeApiServiceAuthTest {

    @Test
    fun `token auth uses the salted md5 token from the Subsonic spec`() {
        val params = subsonicAuthParams(
            password = "sesame",
            method = NavidromeAuthMethod.TOKEN,
            salt = "c19b2d"
        )

        assertThat(params).containsExactly(
            "t", "26719a1196d2a940705a59634eb18eab",
            "s", "c19b2d"
        )
    }

    @Test
    fun `password auth sends the hex-encoded password and no token`() {
        val params = subsonicAuthParams(
            password = "sesame",
            method = NavidromeAuthMethod.PASSWORD,
            salt = "c19b2d"
        )

        assertThat(params).containsExactly("p", "enc:736573616d65")
    }

    @Test
    fun `password auth hex-encodes non-ascii passwords as utf-8`() {
        val params = subsonicAuthParams(
            password = "pä",
            method = NavidromeAuthMethod.PASSWORD
        )

        assertThat(params["p"]).isEqualTo("enc:70c3a4")
    }

    @Test
    fun `token auth salt changes between calls`() {
        val first = subsonicAuthParams("sesame", NavidromeAuthMethod.TOKEN)
        val second = subsonicAuthParams("sesame", NavidromeAuthMethod.TOKEN)

        assertThat(first["s"]).isNotEqualTo(second["s"])
    }

    @Test
    fun `error 41 makes a token request retry with password auth`() {
        val error = SubsonicApiException(41, "Token-based authentication not supported")

        assertThat(shouldFallBackToPasswordAuth(error, NavidromeAuthMethod.TOKEN)).isTrue()
    }

    @Test
    fun `error 41 does not loop once password auth is already in use`() {
        val error = SubsonicApiException(41, "Token-based authentication not supported")

        assertThat(shouldFallBackToPasswordAuth(error, NavidromeAuthMethod.PASSWORD)).isFalse()
    }

    @Test
    fun `wrong credentials do not trigger the password fallback`() {
        val error = SubsonicApiException(40, "Wrong username or password")

        assertThat(shouldFallBackToPasswordAuth(error, NavidromeAuthMethod.TOKEN)).isFalse()
    }

    @Test
    fun `stored auth method falls back to token when missing or unknown`() {
        assertThat(NavidromeAuthMethod.fromStorageKey(null)).isEqualTo(NavidromeAuthMethod.TOKEN)
        assertThat(NavidromeAuthMethod.fromStorageKey("nonsense")).isEqualTo(NavidromeAuthMethod.TOKEN)
        assertThat(NavidromeAuthMethod.fromStorageKey("PASSWORD"))
            .isEqualTo(NavidromeAuthMethod.PASSWORD)
    }
}
