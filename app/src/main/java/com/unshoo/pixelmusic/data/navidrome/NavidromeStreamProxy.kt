package com.unshoo.pixelmusic.data.navidrome

import android.net.Uri
import com.unshoo.pixelmusic.data.stream.CloudStreamProxy
import com.unshoo.pixelmusic.data.stream.CloudStreamSecurity
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Navidrome/Subsonic audio.
 *
 * Resolves `navidrome://{songId}` URIs by generating authenticated streaming URLs
 * from the Navidrome server and proxying the audio data to ExoPlayer.
 */
@Singleton
class NavidromeStreamProxy @Inject constructor(
    private val repository: NavidromeRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String>
        get() = repository.serverUrl?.toHttpUrlOrNull()?.host?.let { setOf(it) } ?: emptySet()

    override val cacheExpirationMs = 30L * 60 * 1000

    override val proxyTag = "NavidromeStreamProxy"
    override val routePath = "/navidrome/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "navidrome"
    override val routePrefix = "/navidrome"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateNavidromeSongId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "NavidromeStreamProxy: Failed to resolve stream URL for song $id")
            null
        }
    }

    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveNavidromeUri(uriString: String): String? = resolveUri(uriString)

    /**
     * Pre-fetches and caches the real stream URL for a song so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "navidrome") return
        val songId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateNavidromeSongId(songId)) return
        try {
            getOrFetchStreamUrl(songId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "warmUpStreamUrl failed for $songId")
        }
    }
}
