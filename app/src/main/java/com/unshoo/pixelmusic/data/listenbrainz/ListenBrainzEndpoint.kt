package com.unshoo.pixelmusic.data.listenbrainz

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the ListenBrainz-compatible API root all requests are routed to.
 *
 * The Retrofit stack is built once against the official base URL; a client
 * interceptor re-roots every request through [rewrite], so a self-hosted
 * ListenBrainz or Maloja endpoint applies without rebuilding the network stack.
 */
@Singleton
class ListenBrainzEndpoint @Inject constructor() {

    @Volatile
    var customBaseUrl: HttpUrl? = null
        private set

    /** Null routes requests to the official endpoint. */
    fun setCustom(baseUrl: HttpUrl?) {
        customBaseUrl = baseUrl
    }

    /** Re-roots [requestUrl] under the custom base, keeping the API path and query. */
    fun rewrite(requestUrl: HttpUrl): HttpUrl {
        val base = customBaseUrl ?: return requestUrl
        val resolved = base.resolve(requestUrl.encodedPath.removePrefix("/"))
            ?: return requestUrl
        return resolved.newBuilder()
            .encodedQuery(requestUrl.encodedQuery)
            .build()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.listenbrainz.org/"

        /**
         * Normalizes user input into an API root: defaults the scheme to https and
         * guarantees a trailing slash so relative API paths append to a path prefix
         * (Maloja serves the ListenBrainz API under `/apis/listenbrainz/`) instead
         * of replacing it. Returns null when the input is not a usable http(s) URL.
         */
        fun parseBaseUrl(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return "$withScheme/".toHttpUrlOrNull()
        }
    }
}
