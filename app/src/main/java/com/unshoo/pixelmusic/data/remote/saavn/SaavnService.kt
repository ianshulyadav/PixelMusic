package com.unshoo.pixelmusic.data.remote.saavn

import android.util.Base64
import com.unshoo.pixelmusic.data.preferences.StreamingAudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class SaavnStreamResult(
    val url: String,
    val bitrateKbps: Int,
    val quality: String,
    val saavnSong: SaavnSong
)

/**
 * JioSaavn audio streaming service.
 *
 * Fetches search results and stream details directly from JioSaavn's public API endpoints,
 * decrypts CDN media links locally on the device using DES-ECB, and matches tracks against
 * YouTube metadata to enable seamless high-bitrate (up to 320 kbps AAC) streaming.
 */
object SaavnService {

    private const val TAG = "SaavnService"
    private const val BASE_URL = "https://www.jiosaavn.com/api.php"
    private const val DES_KEY = "38346591" // JioSaavn DES 8-byte key

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val commonHeaders: Headers by lazy {
        Headers.Builder()
            .add("Accept", "application/json")
            .add(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
            )
            .add("X-Forwarded-For", "49.36.0.1")
            .add("X-Real-IP", "49.36.0.1")
            .add("Accept-Language", "en-IN,en;q=0.9")
            .add("Cookie", "explicit_content=1")
            .build()
    }

    /**
     * Decrypt the encrypted media URL string returned by JioSaavn using DES-ECB.
     */
    fun decryptUrl(encryptedUrl: String): String {
        if (encryptedUrl.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val decryptedUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            Timber.tag(TAG).d("decryptUrl: successfully decrypted: $decryptedUrl")
            decryptedUrl
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "JioSaavn URL decryption failed for URL length: ${encryptedUrl.length}")
            ""
        }
    }

    /**
     * Decode and build direct CDN download URLs for different audio bitrates.
     */
    fun createDownloadLinks(encryptedUrl: String): List<SaavnDownloadUrl> {
        val decryptedUrl = decryptUrl(encryptedUrl)
        if (decryptedUrl.isBlank()) {
            return emptyList()
        }

        val qualities = listOf(
            Pair("_96", "96kbps"),
            Pair("_160", "160kbps"),
            Pair("_320", "320kbps")
        )

        val suffixRegex = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$")
        return qualities.map { (suffix, bitrate) ->
            val url = if (decryptedUrl.contains(suffixRegex)) {
                decryptedUrl.replace(suffixRegex) { match ->
                    "${suffix}.${match.groupValues[2]}"
                }
            } else {
                decryptedUrl.replace("_96", suffix)
            }
            SaavnDownloadUrl(quality = bitrate, url = url)
        }
    }

    /**
     * Clean and generate higher quality image URLs matching typical grid sizes.
     */
    private fun createImageLinks(link: String): List<SaavnImage> {
        if (link.isBlank()) return emptyList()
        val qualities = listOf("50x50", "150x150", "500x500")
        val qualityRegex = Regex("150x150|50x50")
        val protocolRegex = Regex("^http://")

        return qualities.map { quality ->
            val url = link.replace(qualityRegex, quality).replace(protocolRegex, "https://")
            SaavnImage(quality = quality, url = url)
        }
    }

    /**
     * Map the raw API structure received from JioSaavn into SaavnSong models used in playback.
     */
    private fun mapRawToSaavnSong(raw: RawSongItem): SaavnSong {
        val primaryArtists = raw.moreInfo.artistMap.primaryArtists.map {
            SaavnArtistItem(id = it.id, name = it.name)
        }
        val featuredArtists = raw.moreInfo.artistMap.featuredArtists.map {
            SaavnArtistItem(id = it.id, name = it.name)
        }
        val allArtists = raw.moreInfo.artistMap.artists.map {
            SaavnArtistItem(id = it.id, name = it.name)
        }

        return SaavnSong(
            id = raw.id,
            name = raw.title,
            duration = raw.moreInfo.duration.toIntOrNull(),
            explicitContent = raw.explicitContent == "1",
            artists = SaavnArtists(
                primary = primaryArtists,
                featured = featuredArtists,
                all = if (allArtists.isNotEmpty()) allArtists else primaryArtists + featuredArtists
            ),
            image = createImageLinks(raw.image),
            downloadUrl = createDownloadLinks(raw.moreInfo.encryptedMediaUrl),
            album = SaavnAlbum(
                id = raw.moreInfo.albumId,
                name = raw.moreInfo.album
            ),
            isProOnly = raw.moreInfo.rights.isProOnly
        )
    }

    /**
     * Search for songs on JioSaavn directly by a query string.
     */
    suspend fun searchSongs(query: String): Result<List<SaavnSong>> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.tag(TAG).d("searchSongs: query=\"$query\"")
            val httpUrl = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("__call", "search.getResults")
                .addQueryParameter("_format", "json")
                .addQueryParameter("_marker", "0")
                .addQueryParameter("api_version", "4")
                .addQueryParameter("ctx", "android")
                .addQueryParameter("q", query)
                .addQueryParameter("p", "1")
                .addQueryParameter("n", "10")
                .build()

            val request = Request.Builder()
                .url(httpUrl)
                .headers(commonHeaders)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Saavn search failed: HTTP ${response.code}")
            }

            val responseText = response.body?.string().orEmpty()
            val body = json.decodeFromString<RawSearchResponse>(responseText)
            val results = body.results.map { mapRawToSaavnSong(it) }
            Timber.tag(TAG).d("searchSongs: results.size=${results.size}")

            if (results.isEmpty()) {
                throw NoSuchElementException("No songs found on JioSaavn for: \"$query\"")
            }

            results
        }.onFailure {
            Timber.tag(TAG).e(it, "searchSongs: failed for query=\"$query\"")
        }
    }

    /**
     * Choose the best stream URL matching [quality] from a list of download URLs.
     * If the exact quality is not found, it falls back to 320kbps or the highest available bitrate.
     */
    fun selectBestUrl(urls: List<SaavnDownloadUrl>, quality: String): String? {
        val filteredUrls = urls.filter { it.url.isNotBlank() }
        if (filteredUrls.isEmpty()) return null

        // 1. Exact requested quality
        val exactUrl = filteredUrls.firstOrNull { it.quality.equals(quality, ignoreCase = true) }?.url
        if (exactUrl != null) return exactUrl

        // 2. If 160kbps requested and missing, check 320 or 96
        if (quality.equals("160kbps", ignoreCase = true)) {
            val fallback320 = filteredUrls.firstOrNull { it.quality.equals("320kbps", ignoreCase = true) }?.url
            if (fallback320 != null) return fallback320
            val fallback96 = filteredUrls.firstOrNull { it.quality.equals("96kbps", ignoreCase = true) }?.url
            if (fallback96 != null) return fallback96
        }

        // 3. Fall back to 320kbps
        val fallback320 = filteredUrls.firstOrNull { it.quality.equals("320kbps", ignoreCase = true) }?.url
        if (fallback320 != null) return fallback320

        // 4. Fall back to highest available bitrate
        return filteredUrls.lastOrNull()?.url
    }

    /**
     * Fetch the [SaavnSong] detail for a known Saavn song ID and extract the
     * best stream URL matching [quality].
     */
    suspend fun getBestStreamUrl(saavnSongId: String, quality: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val httpUrl = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("__call", "song.getDetails")
                .addQueryParameter("_format", "json")
                .addQueryParameter("_marker", "0")
                .addQueryParameter("api_version", "4")
                .addQueryParameter("ctx", "android")
                .addQueryParameter("pids", saavnSongId)
                .build()

            val request = Request.Builder()
                .url(httpUrl)
                .headers(commonHeaders)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching null

            val responseText = response.body?.string().orEmpty()
            val decodedMap = json.decodeFromString<Map<String, RawSongItem>>(responseText)
            val rawSong = decodedMap.values.firstOrNull() ?: return@runCatching null
            val saavnSong = mapRawToSaavnSong(rawSong)
            selectBestUrl(saavnSong.downloadUrl, quality)
        }.onFailure {
            Timber.tag(TAG).e(it, "getBestStreamUrl failed for saavnSongId=$saavnSongId")
        }.getOrNull()
    }

    /**
     * Clean noisy metadata tokens from titles (e.g. "(Official Music Video)", "ft.", etc.)
     */
    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(official\\s*(music)?\\s*(video|audio)?\\)"), "")
            .replace(Regex("(?i)\\[official\\s*(music)?\\s*(video|audio)?\\]"), "")
            .replace(Regex("(?i)\\(lyrics?\\)"), "")
            .replace(Regex("(?i)\\[lyrics?\\]"), "")
            .replace(Regex("(?i)\\(audio\\)"), "")
            .replace(Regex("(?i)\\[audio\\]"), "")
            .replace(Regex("(?i)\\bft\\.?\\b.*"), "")
            .replace(Regex("(?i)\\bfeat\\.?\\b.*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Resolves a JioSaavn stream URL matching the provided metadata and app audio quality.
     *
     * @param title Track title (from YouTube or library)
     * @param artists Comma-separated or list of artist names
     * @param album Album name (optional)
     * @param durationSeconds Track length in seconds (optional)
     * @param isExplicit Explicit flag if known
     * @param streamingQuality App's streaming quality (StreamingAudioQuality.HIGH, MEDIUM, LOW, AUTO)
     * @param maxBitrateKbps Optional bitrate ceiling from the active stream plan
     * @param saavnQuality User's configured JioSaavn audio quality preference (default: AUTO)
     * @return [SaavnStreamResult] containing CDN URL, bitrate, quality string, and metadata, or null if unresolvable
     */
    suspend fun resolveStream(
        title: String,
        artists: List<String>,
        album: String? = null,
        durationSeconds: Int? = null,
        isExplicit: Boolean = false,
        streamingQuality: StreamingAudioQuality = StreamingAudioQuality.HIGH,
        maxBitrateKbps: Int = 0,
        saavnQuality: SaavnAudioQuality = SaavnAudioQuality.AUTO
    ): SaavnStreamResult? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null

        val cleanedTitle = cleanTitle(title)
        val artistStr = artists.filter { it.isNotBlank() }.joinToString(" ")
        val albumStr = album?.trim().orEmpty()

        val primaryQuery = if (albumStr.isNotBlank()) {
            "$albumStr $cleanedTitle $artistStr"
        } else {
            "$cleanedTitle $artistStr"
        }.replace(Regex("\\s+"), " ").trim()

        val fallbackQuery = "$cleanedTitle $artistStr".replace(Regex("\\s+"), " ").trim()

        val wantedTitleLower = cleanedTitle.lowercase(Locale.US).trim()
        val wantedArtistsLower = artists.map { it.lowercase(Locale.US).trim() }.filter { it.isNotBlank() }
        val wantedAlbumLower = albumStr.lowercase(Locale.US).trim()

        suspend fun findMatch(searchQuery: String): SaavnSong? {
            if (searchQuery.isBlank()) return null
            Timber.tag(TAG).d("resolveStream: querying Saavn with: \"$searchQuery\"")
            val rawSongs = searchSongs(searchQuery).getOrNull() ?: return null

            val sortedCandidates = rawSongs.sortedWith(
                compareByDescending<SaavnSong> { candidate ->
                    val candAlbum = candidate.album?.name?.lowercase(Locale.US)?.trim()
                    wantedAlbumLower.isNotBlank() && candAlbum != null && candAlbum == wantedAlbumLower
                }.thenByDescending { candidate ->
                    candidate.explicitContent == isExplicit
                }
            )

            return sortedCandidates.firstOrNull { candidate ->
                if (candidate.isProOnly) {
                    Timber.tag(TAG).d("resolveStream: candidate \"${candidate.name}\" skipped (Pro-Only)")
                    return@firstOrNull false
                }

                val candTitleClean = cleanTitle(candidate.name).lowercase(Locale.US).trim()
                val candArtists = candidate.artists.all.map { it.name.lowercase(Locale.US).trim() }

                // Avoid matching mismatched versions (e.g. remix, instrumental, karaoke, cover, live, slowed, acoustic, etc.)
                val versionKeywords = listOf("remix", "instrumental", "karaoke", "cover", "acoustic", "live", "sped up", "speed up", "slowed", "reverb", "tribute")
                for (keyword in versionKeywords) {
                    val wantedHas = wantedTitleLower.contains(keyword)
                    val candHas = candTitleClean.contains(keyword)
                    if (wantedHas != candHas) {
                        return@firstOrNull false
                    }
                }

                // 1. Title match (exact or clean)
                val titleMatches = candTitleClean == wantedTitleLower ||
                        (candTitleClean.contains(wantedTitleLower) && (candTitleClean.length - wantedTitleLower.length <= 10)) ||
                        (wantedTitleLower.contains(candTitleClean) && (wantedTitleLower.length - candTitleClean.length <= 10))

                if (!titleMatches) return@firstOrNull false

                // 2. Artist match: check intersection or name substring containment
                val artistMatches = if (wantedArtistsLower.isEmpty()) {
                    true
                } else {
                    val intersection = candArtists.intersect(wantedArtistsLower.toSet())
                    intersection.isNotEmpty() || wantedArtistsLower.any { wanted ->
                        candArtists.any { it.contains(wanted) || wanted.contains(it) }
                    }
                }

                if (!artistMatches) return@firstOrNull false

                // 3. Duration match: within 12 seconds tolerance if both are available
                val candDuration = candidate.duration
                val durationMatches = if (durationSeconds != null && candDuration != null && durationSeconds > 0) {
                    abs(durationSeconds - candDuration) <= 12
                } else {
                    true
                }

                if (!durationMatches) {
                    Timber.tag(TAG).d(
                        "resolveStream: Candidate \"${candidate.name}\" matches title/artist but duration differs (wanted=$durationSeconds, cand=$candDuration)"
                    )
                    return@firstOrNull false
                }

                true
            }
        }

        var matchedSong = findMatch(primaryQuery)
        if (matchedSong == null && primaryQuery != fallbackQuery) {
            Timber.tag(TAG).d("resolveStream: primary query failed, trying fallback: \"$fallbackQuery\"")
            matchedSong = findMatch(fallbackQuery)
        }

        if (matchedSong == null) {
            Timber.tag(TAG).d("resolveStream: no match found on JioSaavn for \"$title\"")
            return@withContext null
        }

        // Resolve effective quality: if AUTO, follow streamingQuality and maxBitrateKbps; otherwise use explicit setting
        val effectiveQuality = SaavnAudioQuality.resolveQuality(
            userSetting = saavnQuality,
            streamingQuality = streamingQuality,
            maxBitrateKbps = maxBitrateKbps
        )
        val apiQuality = effectiveQuality.apiValue
        val bitrate = effectiveQuality.bitrateKbps

        Timber.tag(TAG).d(
            "resolveStream: userSetting=$saavnQuality streamingQuality=$streamingQuality " +
            "maxBitrate=$maxBitrateKbps → effectiveQuality=${effectiveQuality.name}(${effectiveQuality.apiValue})"
        )

        // 1. Try to get stream URL directly from search results downloadUrl list (saves an extra round-trip)
        var streamUrl = selectBestUrl(matchedSong.downloadUrl, apiQuality)
        if (streamUrl.isNullOrBlank()) {
            Timber.tag(TAG).d("resolveStream: downloadUrl empty in search results, fetching details for ${matchedSong.id}")
            streamUrl = getBestStreamUrl(matchedSong.id, apiQuality)
        }

        if (streamUrl.isNullOrBlank()) {
            Timber.tag(TAG).d("resolveStream: failed to resolve stream URL for ${matchedSong.name}")
            return@withContext null
        }

        Timber.tag(TAG).i(
            "resolveStream: SUCCESS! Matched \"${matchedSong.name}\" on JioSaavn (quality=$apiQuality, bitrate=${bitrate}kbps): $streamUrl"
        )

        SaavnStreamResult(
            url = streamUrl,
            bitrateKbps = bitrate,
            quality = apiQuality,
            saavnSong = matchedSong
        )
    }
}
