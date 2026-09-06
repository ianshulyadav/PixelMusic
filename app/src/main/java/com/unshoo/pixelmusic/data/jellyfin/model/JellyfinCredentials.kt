package com.unshoo.pixelmusic.data.jellyfin.model

import com.unshoo.pixelmusic.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class JellyfinCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val accessToken: String? = null,
    val userId: String? = null
) {
    companion object {
        fun empty() = JellyfinCredentials(
            serverUrl = "",
            username = "",
            password = "",
            accessToken = null,
            userId = null
        )
    }

    val isValid: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() &&
                (password.isNotBlank() || !accessToken.isNullOrBlank())

    val hasToken: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    val normalizedHttpUrlOrNull: HttpUrl?
        get() {
            val trimmed = serverUrl.trim().trimEnd('/')
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)

            val withScheme = if (!hasScheme) {
                val host = "http://$trimmed".toHttpUrlOrNull()?.host?.lowercase()
                val scheme = if (host != null && isHttpAllowedHost(host)) "http" else "https"
                "$scheme://$trimmed"
            } else {
                trimmed
            }
            return withScheme.toHttpUrlOrNull()
        }

    val normalizedServerUrl: String
        get() = normalizedHttpUrlOrNull?.toString()?.trimEnd('/') ?: serverUrl.trim().trimEnd('/')

    /** Whether Android 17's local-network runtime permission is needed for this server. */
    val requiresLocalNetworkAccess: Boolean
        get() = normalizedHttpUrlOrNull?.host?.let(CloudStreamSecurity::isLocalServerHost) == true

    fun connectionValidationError(): String? {
        val parsed = normalizedHttpUrlOrNull
            ?: return "Invalid server URL format"

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return "Server URL must not contain embedded credentials"
        }

        if (!parsed.isHttps) {
            val host = parsed.host.lowercase()
            if (!isHttpAllowedHost(host)) {
                return "Use https:// for remote Jellyfin servers. HTTP is only allowed for local network addresses."
            }
        }

        return null
    }

    private fun isHttpAllowedHost(host: String): Boolean =
        CloudStreamSecurity.isLocalServerHost(host)
}
