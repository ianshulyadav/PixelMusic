package com.unshoo.pixelmusic.data.listenbrainz

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

class ListenBrainzEndpointTest {

    @Test
    fun parseBaseUrl_defaultsSchemeToHttpsAndAppendsSlash() {
        val parsed = ListenBrainzEndpoint.parseBaseUrl("listen.example.com")

        assertThat(parsed.toString()).isEqualTo("https://listen.example.com/")
    }

    @Test
    fun parseBaseUrl_keepsPathPrefixAndExplicitScheme() {
        val parsed = ListenBrainzEndpoint.parseBaseUrl("http://maloja.example.com/apis/listenbrainz/")

        assertThat(parsed.toString()).isEqualTo("http://maloja.example.com/apis/listenbrainz/")
    }

    @Test
    fun parseBaseUrl_rejectsNonHttpSchemesAndGarbage() {
        assertThat(ListenBrainzEndpoint.parseBaseUrl("ftp://example.com")).isNull()
        assertThat(ListenBrainzEndpoint.parseBaseUrl("not a url")).isNull()
        assertThat(ListenBrainzEndpoint.parseBaseUrl("   ")).isNull()
    }

    @Test
    fun rewrite_withoutCustomBaseLeavesRequestUntouched() {
        val endpoint = ListenBrainzEndpoint()
        val request = "https://api.listenbrainz.org/1/submit-listens".toHttpUrl()

        assertThat(endpoint.rewrite(request)).isEqualTo(request)
    }

    @Test
    fun rewrite_rerootsApiPathUnderCustomBaseWithPathPrefix() {
        val endpoint = ListenBrainzEndpoint()
        endpoint.setCustom(ListenBrainzEndpoint.parseBaseUrl("https://maloja.example.com/apis/listenbrainz"))

        val rewritten = endpoint.rewrite("https://api.listenbrainz.org/1/submit-listens".toHttpUrl())

        assertThat(rewritten.toString())
            .isEqualTo("https://maloja.example.com/apis/listenbrainz/1/submit-listens")
    }

    @Test
    fun rewrite_preservesQueryParameters() {
        val endpoint = ListenBrainzEndpoint()
        endpoint.setCustom(ListenBrainzEndpoint.parseBaseUrl("https://listen.example.com"))

        val rewritten = endpoint.rewrite("https://api.listenbrainz.org/1/validate-token?token=abc".toHttpUrl())

        assertThat(rewritten.toString())
            .isEqualTo("https://listen.example.com/1/validate-token?token=abc")
    }

    @Test
    fun rewrite_clearedCustomBaseRoutesBackToOfficialEndpoint() {
        val endpoint = ListenBrainzEndpoint()
        endpoint.setCustom(ListenBrainzEndpoint.parseBaseUrl("https://listen.example.com"))
        endpoint.setCustom(null)
        val request = "https://api.listenbrainz.org/1/submit-listens".toHttpUrl()

        assertThat(endpoint.rewrite(request)).isEqualTo(request)
    }
}
