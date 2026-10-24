package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.util.LruCache
import androidx.core.net.toUri
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.model.youtube.PixelMusicSettings
import com.unshoo.pixelmusic.data.preferences.StreamingAudioQuality
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.saavn.SaavnService
import com.unshoo.pixelmusic.data.remote.saavn.SaavnAudioQuality
import com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import java.io.File
import java.util.Locale
import unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
import unshoo.ianshulyadav.pixelmusic.innertube.PlaybackAuthState
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.IOS
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.IPADOS
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.MOBILE
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.TVHTML5
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.VISIONOS
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_REMIX
import unshoo.ianshulyadav.pixelmusic.innertube.utils.StreamClientUtils
import com.unshoo.pixelmusic.data.preferences.PlayerStreamClient
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.response.PlayerResponse
import java.util.concurrent.ConcurrentHashMap


object YoutubeHelper {
    private val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )
    val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
        // Fail fast on connect, but no callTimeout: a hard 12s whole-call cap killed
        // slow-but-working Innertube resolves on weak links, after which the quality-fallback
        // pass re-ran the ENTIRE multi-client resolution — doubling worst-case tap-to-play time.
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * LRU cache for resolved YouTube stream URLs.
     * Key format: "<videoId>_low", "<videoId>_high", or "<videoId>_q<bitrate>".
     * Holds up to 200 entries; expired/invalid entries are evicted lazily on next access.
     */
    val streamUrlLruCache = LruCache<String, String>(200)

    /**
     * Parallel cache storing the MIME type (e.g. "audio/opus", "audio/mp4") for the
     * corresponding entry in [streamUrlLruCache]. Keyed identically to [streamUrlLruCache].
     * Allows ExoPlayer to be told the exact codec upfront so it can skip WEBM container
     * sniffing, cutting several hundred ms off the initial buffer/decode latency.
     */
    val streamMimeTypeLruCache = LruCache<String, String>(200)

    /**
     * Parallel cache storing the actual bitrate (in bps) for the corresponding entry in
     * [streamUrlLruCache]. Keyed identically. Allows the player UI to show the real
     * YouTube stream bitrate (e.g. 160000 → "160 kbps") without an extra network probe.
     */
    val streamBitrateLruCache = LruCache<String, Int>(200)

    /** Clear all resolved stream URL and format caches (e.g. when quality or Saavn toggle changes). */
    fun clearStreamCache() {
        streamUrlLruCache.evictAll()
        streamMimeTypeLruCache.evictAll()
        streamBitrateLruCache.evictAll()
    }

    /** Register a locally-available file path for a YouTube video ID so playback is instant. */
    private val localFilePathCache = LruCache<String, String>(200)

    val playbackTrackingCache = ConcurrentHashMap<String, String>()
    val watchtimeTrackingCache = ConcurrentHashMap<String, String>()

    // ── ArchiveTune-style resolved-candidate cache ─────────────────────────────────
    // Key: videoId|itag|clientName|authFp → (url, expiresAtMs). Lets a second resolution of the
    // same song skip NewPipe's signature/n-throttle deobfuscation entirely (ArchiveTune parity).
    private val resolvedCandidateUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val CANDIDATE_URL_EXPIRY_SAFETY_MS = 60_000L
    private const val DEFAULT_STREAM_EXPIRE_SECONDS = 300
    @Volatile private var lastSuccessfulClientKey: String? = null
    @Volatile private var lastSuccessfulClientKeyLoaded: Boolean = false

    /**
     * ArchiveTune STREAM_FALLBACK_CLIENTS order: ANDROID_VR variants first (plain URLs = zero
     * decipher work), then the Web/TV family, then native mobile clients.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_43_32,
        TVHTML5,
        WEB_CREATOR,
        WEB_REMIX,
        WEB,
        IOS,
        MOBILE,
        ANDROID_MUSIC,
        ANDROID_CREATOR,
        IPADOS,
        VISIONOS,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
    )

    suspend fun extractGenre(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val jsonString = YoutubeRequestHelper.getPlayerInfo(videoId)
            val json = Json.parseToJsonElement(jsonString).jsonObject
            val category = json["microformat"]
                ?.jsonObject?.get("microformatDataRenderer")
                ?.jsonObject?.get("category")
                ?.jsonPrimitive?.contentOrNull
            category?.takeIf { it.isNotBlank() && it != "Music" }
        } catch (e: Exception) {
            PixelMusicHelper.printe("Failed to extract genre: ${e.message}")
            null
        }
    }

    fun extractYouTubeVideoId(url: String): String? {
        val uri = url.toUri()

        return when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
            uri.host?.contains("youtube.com") == true || uri.host?.contains("music.youtube.com") == true -> uri.getQueryParameter(
                "v"
            )
            else -> null
        }
    }

    fun getBestThumbnailUrl(thumbnailElement: JsonElement): String {
        val url =
            thumbnailElement.jsonObject["musicThumbnailRenderer"]?.jsonObject?.get("thumbnail")?.jsonObject?.get(
                "thumbnails"
            )?.jsonArray?.last()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        return upgradeThumbnailUrlToHighQuality(url)
    }

    private fun upgradeThumbnailUrlToHighQuality(url: String): String {
        return com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality(url) ?: url
    }

    fun getSongInfo(songMap: JsonElement, songInfoIndex: SongInfoType): String {
        return songMap.jsonObject["flexColumns"]
            ?.jsonArray?.getOrNull(songInfoIndex.index)
            ?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("runs")
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.contentOrNull ?: ""
    }

    fun extractPlaylists(
        jsonString: String,
        settings: PixelMusicSettings
    ): List<PlaylistInfo> {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val playlistInfos = mutableListOf<PlaylistInfo>()

        val tabs = json["contents"]
            ?.jsonObject?.get("singleColumnBrowseResultsRenderer")
            ?.jsonObject?.get("tabs")
            ?.jsonArray

        val selectedTab = tabs?.firstOrNull {
            it.jsonObject["tabRenderer"]
                ?.jsonObject?.get("selected")
                ?.jsonPrimitive?.booleanOrNull == true
        }?.jsonObject?.get("tabRenderer")?.jsonObject

        val sectionList = selectedTab?.get("content")
            ?.jsonObject?.get("sectionListRenderer")
            ?.jsonObject?.get("contents")
            ?.jsonArray

        sectionList?.forEach { section ->
            val renderer = section.jsonObject["gridRenderer"]?.jsonObject ?: return@forEach

            renderer["items"]?.jsonArray?.forEach { item ->
                val playlistRenderer = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                    ?: return@forEach

                val title = playlistRenderer["title"]
                    ?.jsonObject?.get("runs")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.contentOrNull ?: return@forEach

                val browseId = playlistRenderer["navigationEndpoint"]
                    ?.jsonObject?.get("browseEndpoint")
                    ?.jsonObject?.get("browseId")
                    ?.jsonPrimitive?.contentOrNull
                    ?.removePrefix("VL") ?: return@forEach

                if (YouTubeItemFilter.isPodcastOrEpisode(title, browseId)) {
                    return@forEach
                }

                val thumbnailUrl =
                    getBestThumbnailUrl(playlistRenderer["thumbnailRenderer"] ?: return@forEach)
                val songCount = extractPlaylistSongCount(playlistRenderer)

                playlistInfos.add(
                    PlaylistInfo(
                        id = browseId,
                        title = title,
                        coverHref = thumbnailUrl,
                        lastSyncSongCount = songCount
                    )
                )
            }

            val continuationToken = renderer["continuations"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("nextContinuationData")
                ?.jsonObject?.get("continuation")
                ?.jsonPrimitive?.contentOrNull

            if (continuationToken != null) {
                val continuationJson = YoutubeRequestHelper.requestContinuation(
                    continuationToken = continuationToken,
                    settings = settings
                )
                playlistInfos.addAll(extractPlaylists(continuationJson, settings))
            }
        }

        val continuationGridItems = json["continuationContents"]
            ?.jsonObject
            ?.get("gridContinuation")
            ?.jsonObject
            ?.get("items")
            ?.jsonArray

        continuationGridItems?.forEach { item ->
            val playlistRenderer = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                ?: return@forEach

            val title = playlistRenderer["title"]
                ?.jsonObject?.get("runs")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.contentOrNull ?: return@forEach

            val browseId = playlistRenderer["navigationEndpoint"]
                ?.jsonObject?.get("browseEndpoint")
                ?.jsonObject?.get("browseId")
                ?.jsonPrimitive?.contentOrNull ?: return@forEach

            val thumbnailUrl =
                getBestThumbnailUrl(playlistRenderer["thumbnailRenderer"] ?: return@forEach)
            val songCount = extractPlaylistSongCount(playlistRenderer)

            playlistInfos.add(
                PlaylistInfo(
                    id = browseId,
                    title = title,
                    coverHref = thumbnailUrl,
                    lastSyncSongCount = songCount
                )
            )
        }

        val continuationToken = json["continuationContents"]
            ?.jsonObject
            ?.get("gridContinuation")
            ?.jsonObject
            ?.get("continuations")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject
            ?.get("nextContinuationData")
            ?.jsonObject
            ?.get("continuation")
            ?.jsonPrimitive?.contentOrNull

        if (continuationToken != null) {
            val continuationJson = YoutubeRequestHelper.requestContinuation(
                continuationToken = continuationToken,
                settings = settings
            )
            playlistInfos.addAll(extractPlaylists(continuationJson, settings))
        }

        return playlistInfos
    }

    private fun extractPlaylistSongCount(playlistRenderer: JsonObject): Int {
        fun parseCount(text: String): Int? {
            val normalized = text.replace("\u00A0", " ")
            val match = Regex("""(?i)(\d[\d,\.]*)\s*(songs?|tracks?|videos?|episodes?)""")
                .find(normalized)
                ?: return null
            return match.groupValues[1]
                .filter { it.isDigit() }
                .toIntOrNull()
                ?.takeIf { it > 0 }
        }

        val subtitleText = playlistRenderer["subtitle"]
            ?.jsonObject?.get("runs")
            ?.jsonArray
            ?.joinToString(" ") { run ->
                run.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            .orEmpty()
        parseCount(subtitleText)?.let { return it }

        // Fallback for renderer variants/locales where the count is not in subtitle.runs.
        return parseCount(playlistRenderer.toString()) ?: 0
    }

    fun extractSearchResults(jsonString: String): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val tabs = json["contents"]
            ?.jsonObject?.get("tabbedSearchResultsRenderer")
            ?.jsonObject?.get("tabs")
            ?.jsonArray ?: return emptyList()

        val selectedTab = tabs.firstOrNull {
            it.jsonObject["tabRenderer"]
                ?.jsonObject?.get("selected")
                ?.jsonPrimitive?.booleanOrNull == true
        }?.jsonObject?.get("tabRenderer")?.jsonObject ?: return emptyList()

        val contents = selectedTab["content"]
            ?.jsonObject?.get("sectionListRenderer")
            ?.jsonObject?.get("contents")
            ?.jsonArray ?: return emptyList()

        val songRendererList =
            contents.jsonArray
                .firstNotNullOfOrNull {
                    it.jsonObject["musicShelfRenderer"]
                        ?.jsonObject?.get("contents")
                        ?.jsonArray
                }
                ?: return emptyList()

        return songRendererList.mapNotNull { extractSong(it) }
    }

    fun extractRelatedSongs(jsonString: String): List<Song> {
        return try {
            val root = Json.parseToJsonElement(jsonString).jsonObject

            // Primary path: singleColumnWatchNextResults
            val autoplayItems = root["contents"]
                ?.jsonObject?.get("singleColumnWatchNextResults")
                ?.jsonObject?.get("playlist")
                ?.jsonObject?.get("playlist")
                ?.jsonObject?.get("contents")
                ?.jsonArray

            if (autoplayItems != null && autoplayItems.size > 1) {
                // skip index 0 (current song), take up to 10 next
                return autoplayItems.drop(1).take(10).mapNotNull { item ->
                    val renderer = item.jsonObject["playlistPanelVideoRenderer"]?.jsonObject
                        ?: return@mapNotNull null
                    val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val title = renderer["title"]?.jsonObject?.get("runs")
                        ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                    val artist = renderer["longBylineText"]?.jsonObject?.get("runs")
                        ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                    val thumbnail = renderer["thumbnail"]?.jsonObject?.get("thumbnails")
                        ?.jsonArray?.last()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
                    Song(youtubeId = videoId, title = title, artist = artist, thumbnailHref = upgradeThumbnailUrlToHighQuality(thumbnail))
                }
            }

            // Fallback: tabbedRenderer → musicQueueRenderer
            val queueItems = root["contents"]
                ?.jsonObject?.get("singleColumnWatchNextResults")
                ?.jsonObject?.get("tabbedRenderer")
                ?.jsonObject?.get("watchNextTabbedResultsRenderer")
                ?.jsonObject?.get("tabs")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("tabRenderer")
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("musicQueueRenderer")
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("playlistPanelRenderer")
                ?.jsonObject?.get("contents")
                ?.jsonArray

            queueItems?.drop(1)?.take(10)?.mapNotNull { item ->
                val renderer = item.jsonObject["playlistPanelVideoRenderer"]?.jsonObject
                    ?: return@mapNotNull null
                val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                val title = renderer["title"]?.jsonObject?.get("runs")
                    ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                val artist = renderer["longBylineText"]?.jsonObject?.get("runs")
                    ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                val thumbnail = renderer["thumbnail"]?.jsonObject?.get("thumbnails")
                    ?.jsonArray?.last()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
                Song(youtubeId = videoId, title = title, artist = artist, thumbnailHref = upgradeThumbnailUrlToHighQuality(thumbnail))
            } ?: emptyList()
        } catch (e: Exception) {
            PixelMusicHelper.printe("extractRelatedSongs failed: ${e.message}")
            emptyList()
        }
    }

    fun extractSongInfo(jsonString: String): Song {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val details = json.jsonObject["videoDetails"]?.jsonObject

        val videoId = details?.get("videoId")?.jsonPrimitive?.contentOrNull ?: ""
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val author = details?.get("author")?.jsonPrimitive?.contentOrNull ?: ""
        val lengthSeconds: Int =
            details?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull?.toInt()
                ?: 0

        return Song(
            youtubeId = videoId,
            title = title,
            artist = author,
            duration = formatSecondsForYouTubeDisplay(lengthSeconds),
            thumbnailHref = extractHighQualityThumbnail(jsonString)
        )
    }

    fun extractSongList(jsonString: String, settings: PixelMusicSettings): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val contents = json["contents"]
            ?.jsonObject?.get("twoColumnBrowseResultsRenderer")
            ?.jsonObject?.get("secondaryContents")
            ?.jsonObject?.get("sectionListRenderer")
            ?.jsonObject?.get("contents")
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("musicPlaylistShelfRenderer")
            ?.jsonObject?.get("contents")
            ?.jsonArray
        return parseSongsFromContents(contents, settings)
    }

    fun extractContinuationSongs(jsonString: String, settings: PixelMusicSettings): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val contents = json["onResponseReceivedActions"]
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("appendContinuationItemsAction")
            ?.jsonObject?.get("continuationItems")
            ?.jsonArray

        return parseSongsFromContents(contents, settings)
    }

    private fun formatSecondsForYouTubeDisplay(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun extractHighQualityThumbnail(jsonString: String): String {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val url = json["videoDetails"]
            ?.jsonObject?.get("thumbnail")
            ?.jsonObject?.get("thumbnails")
            ?.jsonArray?.last()
            ?.jsonObject?.get("url")
            ?.jsonPrimitive?.contentOrNull

        return upgradeThumbnailUrlToHighQuality(url ?: "")
    }

    private fun parseSongsFromContents(
        contents: JsonArray?,
        settings: PixelMusicSettings
    ): List<Song> {
        val songs = mutableListOf<Song>()
        if (contents == null) return songs

        for (shelf in contents) {
            val continuationContent = shelf.jsonObject["continuationItemRenderer"]

            if (continuationContent != null) {
                val token = continuationContent.jsonObject["continuationEndpoint"]
                    ?.jsonObject?.get("continuationCommand")
                    ?.jsonObject?.get("token")
                    ?.jsonPrimitive?.contentOrNull ?: ""

                val otherSongs = extractContinuationSongs(
                    YoutubeRequestHelper.requestContinuation(
                        continuationToken = token,
                        settings = settings
                    ), settings
                )
                songs.addAll(otherSongs)

                continue
            }

            val song = extractSong(shelf) ?: continue
            songs.add(song)
        }

        return songs
    }

    fun extractSong(json: JsonElement): Song? {
        val songContent =
            json.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return null
        val thumbnailUrl = getBestThumbnailUrl(songContent["thumbnail"] ?: return null)

        val title = getSongInfo(songContent, SongInfoType.TITLE)
        val artist = getSongInfo(songContent, SongInfoType.ARTIST)
        val videoId = songContent["playlistItemData"]
            ?.jsonObject?.get("videoId")
            ?.jsonPrimitive?.contentOrNull ?: return null

        val duration = extractDuration(songContent)

        return Song(
            youtubeId = videoId,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailHref = thumbnailUrl
        )
    }

    /**
     * Returns the highest-quality stream URL for the given YouTube song.
     * Checks the in-memory LRU cache first, then falls back to local file if available,
     * then resolves from YouTube.
     */
    /**
     * Effective streaming quality for the current network + user settings.
     * HIGH on Wi‑Fi (or forced high on mobile) → unbounded highest stream.
     * LOW/MEDIUM on metered → capped; offline → LOW.
     */
    private data class StreamQualityPlan(
        val quality: StreamingAudioQuality,
        /** 0 = no ceiling (pick highest available). */
        val maxBitrateKbps: Int,
        /** When true, pick lowest bitrate first (Data Saver / weak nets). */
        val preferLowFirst: Boolean,
    )

    private suspend fun resolveStreamQualityPlan(context: Context): StreamQualityPlan {
        return try {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                YoutubeHelperEntryPoint::class.java
            )
            val connectivityStateHolder = entryPoint.connectivityStateHolder()
            val userPreferencesRepository = entryPoint.userPreferencesRepository()

            val isOnline = connectivityStateHolder.isOnline.value
            val isMetered = connectivityStateHolder.isMeteredNetwork.value
            val forceHigh = userPreferencesRepository.forceHighQualityOnMobileFlow.first()

            if (!isOnline) {
                return StreamQualityPlan(
                    quality = StreamingAudioQuality.LOW,
                    maxBitrateKbps = StreamingAudioQuality.LOW.maxBitrateKbps,
                    preferLowFirst = true,
                )
            }

            val targetQuality = if (isMetered && !forceHigh) {
                userPreferencesRepository.streamingAudioQualityMobileFlow.first()
            } else {
                userPreferencesRepository.streamingAudioQualityWifiFlow.first()
            }

            when (targetQuality) {
                StreamingAudioQuality.AUTO -> {
                    val isWifi = !isMetered
                    val speed = connectivityStateHolder.linkDownstreamBandwidthKbps.value
                    val recentlyUnstable = connectivityStateHolder.isNetworkRecentlyUnstable.value
                    val isConnectionFastAndStable = isWifi || (speed >= 2_000 && !recentlyUnstable)
                    val targetCeiling = if (isMetered && !forceHigh && !isConnectionFastAndStable) {
                        StreamingAudioQuality.MEDIUM.maxBitrateKbps
                    } else {
                        0 // No ceiling → highest available quality (256+ kbps)
                    }
                    StreamQualityPlan(
                        quality = StreamingAudioQuality.AUTO,
                        maxBitrateKbps = targetCeiling,
                        preferLowFirst = false
                    )
                }
                StreamingAudioQuality.HIGH -> StreamQualityPlan(
                    quality = StreamingAudioQuality.HIGH,
                    maxBitrateKbps = 0, // no ceiling → highest available
                    preferLowFirst = false,
                )
                StreamingAudioQuality.MEDIUM -> StreamQualityPlan(
                    quality = StreamingAudioQuality.MEDIUM,
                    maxBitrateKbps = StreamingAudioQuality.MEDIUM.maxBitrateKbps,
                    // Medium: pick best under ceiling (not lowest).
                    preferLowFirst = false,
                )
                StreamingAudioQuality.LOW -> StreamQualityPlan(
                    quality = StreamingAudioQuality.LOW,
                    maxBitrateKbps = StreamingAudioQuality.LOW.maxBitrateKbps,
                    preferLowFirst = true,
                )
            }
        } catch (_: Exception) {
            StreamQualityPlan(
                quality = StreamingAudioQuality.HIGH,
                maxBitrateKbps = 0,
                preferLowFirst = false,
            )
        }
    }

    private suspend fun getTargetBitrateCeiling(context: Context): Int =
        resolveStreamQualityPlan(context).maxBitrateKbps

    private suspend fun shouldPreferLowQualityFirst(context: Context): Boolean =
        resolveStreamQualityPlan(context).preferLowFirst

    private fun parseDurationToSeconds(durationStr: String?): Int? {
        if (durationStr.isNullOrBlank()) return null
        durationStr.toIntOrNull()?.let { return it }
        val parts = durationStr.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private suspend fun fetchSongMetadata(videoId: String): Song? {
        var result: Song? = null
        try {
            SongRepository().getSongInfo(videoId).collect { apiResult ->
                if (apiResult is ApiResult.Success<Song>) {
                    result = apiResult.data
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Resolve the stream URL for playback using the user's quality setting:
     * - **HIGH**: fetch highest available bitrate first and start on that stream immediately.
     * - **MEDIUM**: highest stream under the 128 kbps ceiling.
     * - **LOW** / weak-offline: lowest available stream for fastest first-byte.
     */
    suspend fun getSongPlayerUrl(
        context: Context,
        song: Song,
        allowLocal: Boolean = false
    ): String {
        val videoId = song.youtubeId

        if (song.audioFilePath?.isNotBlank() == true && File(song.audioFilePath).exists()) {
            PixelMusicHelper.printd("$videoId : Playing directly from song.audioFilePath: ${song.audioFilePath}")
            return song.audioFilePath
        }

        // ── OFFLINE-FIRST GATE ─────────────────────────────────────────────────
        val cachedLocalPath = localFilePathCache.get(videoId)
        if (cachedLocalPath != null && File(cachedLocalPath).exists()) {
            PixelMusicHelper.printd("$videoId : Playing from in-memory local file cache")
            return cachedLocalPath
        }

        val plan = resolveStreamQualityPlan(context)
        val maxBitrate = plan.maxBitrateKbps
        val preferLowFirst = plan.preferLowFirst
        val targetCacheKey = if (maxBitrate > 0) "${videoId}_q$maxBitrate" else "${videoId}_high"

        val userPreferencesRepository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            YoutubeHelperEntryPoint::class.java
        ).userPreferencesRepository()

        val isSaavnEnabled = userPreferencesRepository.enableSaavnStreamingFlow.first()
        val saavnQuality = userPreferencesRepository.saavnAudioQualityFlow.first()

        // If Saavn is disabled, proactively evict any stale Saavn URLs so playback never touches Saavn
        if (!isSaavnEnabled) {
            streamUrlLruCache.get(targetCacheKey)?.let {
                if (isSaavnUrl(it)) {
                    streamUrlLruCache.remove(targetCacheKey)
                    streamMimeTypeLruCache.remove(targetCacheKey)
                    streamBitrateLruCache.remove(targetCacheKey)
                }
            }
            streamUrlLruCache.get("${videoId}_high")?.let {
                if (isSaavnUrl(it)) {
                    streamUrlLruCache.remove("${videoId}_high")
                    streamMimeTypeLruCache.remove("${videoId}_high")
                    streamBitrateLruCache.remove("${videoId}_high")
                }
            }
            streamUrlLruCache.get("${videoId}_low")?.let {
                if (isSaavnUrl(it)) {
                    streamUrlLruCache.remove("${videoId}_low")
                    streamMimeTypeLruCache.remove("${videoId}_low")
                    streamBitrateLruCache.remove("${videoId}_low")
                }
            }
        }

        // ── Cache hit at the quality the user actually wants ───────────────────
        streamUrlLruCache.get(targetCacheKey)?.let {
            if (isUrlAllowed(it, isSaavnEnabled) && isYoutubeUrlValid(it)) {
                PixelMusicHelper.printd(
                    "$videoId : INSTANT start from cache ($targetCacheKey, quality=${plan.quality})"
                )
                return it
            }
        }
        // HIGH: also accept a valid _high entry under any alias.
        if (maxBitrate == 0) {
            streamUrlLruCache.get("${videoId}_high")?.let {
                if (isUrlAllowed(it, isSaavnEnabled) && isYoutubeUrlValid(it)) {
                    PixelMusicHelper.printd("$videoId : INSTANT start from cached HIGH stream")
                    return it
                }
            }
        }

        // ── On LRU cache miss, check if song was saved/downloaded locally in DB ──
        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        var savedSong: Song? = null
        try {
            savedSong = localSongRepository.getSong(videoId)
        } catch (ex: Exception) {
            PixelMusicHelper.printe(ex.toString())
        }

        if (savedSong != null) {
            if (savedSong.audioFilePath != null && File(savedSong.audioFilePath).exists()) {
                PixelMusicHelper.printd("$videoId : Was downloaded, playing from local file")
                localFilePathCache.put(videoId, savedSong.audioFilePath)
                return savedSong.audioFilePath
            }
        }

        // ── LOSSLESS SOURCES GATE (Tidal / Qobuz / Deezer / Apple Music via the Source Pool) ──
        // Ported from ArchiveTune's resolveMultiSourceDataSpec: sits in front of JioSaavn and
        // YouTube so a lossless copy wins whenever a pool or user account can serve one.
        resolveLosslessStreamUrl(
            context = context,
            song = song,
            savedSong = savedSong,
            cacheKeys = listOf(targetCacheKey, "${videoId}_high"),
            lowDataMode = plan.quality == StreamingAudioQuality.LOW || plan.quality == StreamingAudioQuality.MEDIUM,
        )?.let { return it }

        // ── JIOSAAVN HIGH-FIDELITY STREAMING GATE (3s timeout → YouTube fallback) ──
        if (isSaavnEnabled) {
            try {
                var effectiveTitle = song.title.ifBlank { savedSong?.title.orEmpty() }
                var effectiveArtist = song.artist.ifBlank { savedSong?.artist.orEmpty() }
                var effectiveAlbum = song.album?.ifBlank { null } ?: savedSong?.album
                var durationStr = song.duration.ifBlank { savedSong?.duration.orEmpty() }
                var effectiveDuration = parseDurationToSeconds(durationStr)

                if (effectiveTitle.isBlank() && videoId.isNotBlank()) {
                    val fetched = withTimeoutOrNull(1500L) { fetchSongMetadata(videoId) }
                    if (fetched != null) {
                        effectiveTitle = fetched.title
                        effectiveArtist = fetched.artist
                        effectiveAlbum = fetched.album
                        effectiveDuration = parseDurationToSeconds(fetched.duration)
                    }
                }

                if (effectiveTitle.isNotBlank()) {
                    val artistsList = effectiveArtist.split(",", "&", "feat.", "ft.", ";")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val saavnResult = withTimeoutOrNull(3000L) {
                        SaavnService.resolveStream(
                            title = effectiveTitle,
                            artists = if (artistsList.isNotEmpty()) artistsList else listOf(effectiveArtist),
                            album = effectiveAlbum,
                            durationSeconds = effectiveDuration,
                            streamingQuality = plan.quality,
                            maxBitrateKbps = maxBitrate,
                            saavnQuality = saavnQuality
                        )
                    }

                    if (saavnResult != null && saavnResult.url.isNotBlank()) {
                        val saavnUrl = saavnResult.url
                        val saavnBitrate = saavnResult.bitrateKbps * 1000
                        val mimeType = "audio/mp4"

                        streamUrlLruCache.put(targetCacheKey, saavnUrl)
                        streamMimeTypeLruCache.put(targetCacheKey, mimeType)
                        streamBitrateLruCache.put(targetCacheKey, saavnBitrate)
                        streamUrlLruCache.put("${videoId}_high", saavnUrl)
                        streamMimeTypeLruCache.put("${videoId}_high", mimeType)
                        streamBitrateLruCache.put("${videoId}_high", saavnBitrate)

                        PixelMusicHelper.printd(
                            "$videoId : Playing via JioSaavn HQ stream (${saavnResult.quality}, bitrate=${saavnBitrate})"
                        )
                        return saavnUrl
                    } else {
                        PixelMusicHelper.printd(
                            "$videoId : JioSaavn stream resolve timed out (>3s) or returned null, falling back to YouTube"
                        )
                    }
                }
            } catch (saavnEx: Exception) {
                PixelMusicHelper.printe("$videoId : JioSaavn stream resolve exception: ${saavnEx.message}, falling back to YouTube")
            }
        }

        // ── Resolve at the selected quality FIRST (no forced low on HIGH) ──────
        // HIGH  → lowQuality=false, maxBitrate=0  → highest available instantly
        // MEDIUM→ lowQuality=false, maxBitrate=128 → best under ceiling
        val useLowQuality = plan.quality == StreamingAudioQuality.LOW
        val result = try {
            getSongUrlFromYoutube(
                context = context,
                song = song,
                retries = 1,
                lowQuality = useLowQuality,
                maxBitrateKbps = maxBitrate,
            )
        } catch (primary: Exception) {
            PixelMusicHelper.printe(
                "$videoId : ${plan.quality} stream resolve failed (${primary.message}); " +
                    "retrying with fallback quality"
            )
            // Fallback: if HIGH fails (weak link), try LOW so playback still starts.
            // If LOW fails, try unrestricted once more.
            try {
                if (!useLowQuality) {
                    getSongUrlFromYoutube(
                        context = context,
                        song = song,
                        retries = 1,
                        lowQuality = true,
                        maxBitrateKbps = StreamingAudioQuality.LOW.maxBitrateKbps,
                    )
                } else {
                    getSongUrlFromYoutube(
                        context = context,
                        song = song,
                        retries = 1,
                        lowQuality = false,
                        maxBitrateKbps = 0,
                    )
                }
            } catch (secondary: Exception) {
                throw primary
            }
        }

        val newUri = result.first
        val mimeType = result.second
        val bitrate = result.third

        streamUrlLruCache.put(targetCacheKey, newUri)
        mimeType?.let { streamMimeTypeLruCache.put(targetCacheKey, it) }
        bitrate?.let { streamBitrateLruCache.put(targetCacheKey, it) }

        if (useLowQuality) {
            streamUrlLruCache.put("${videoId}_low", newUri)
            mimeType?.let { streamMimeTypeLruCache.put("${videoId}_low", it) }
            bitrate?.let { streamBitrateLruCache.put("${videoId}_low", it) }
        } else if (maxBitrate == 0 || maxBitrate >= StreamingAudioQuality.HIGH.maxBitrateKbps) {
            streamUrlLruCache.put("${videoId}_high", newUri)
            mimeType?.let { streamMimeTypeLruCache.put("${videoId}_high", it) }
            bitrate?.let { streamBitrateLruCache.put("${videoId}_high", it) }
        }

        // Trigger background warming for the actual desired target quality if we had to start on LOW first
        if (plan.quality == StreamingAudioQuality.AUTO && preferLowFirst && targetCacheKey == "${videoId}_low") {
            warmHigherQualityInBackground(context, song, maxBitrate)
        }

        PixelMusicHelper.printd(
            "$videoId : INSTANT ${plan.quality} stream ready " +
                "(ceiling=${maxBitrate}kbps lowFirst=$preferLowFirst targetCacheKey=$targetCacheKey bitrate=$bitrate)"
        )
        return newUri
    }

    /**
     * Resolve a higher-bitrate stream in the background so the next skip/replay can use it.
     * Never blocks the critical click-to-play path.
     */
    private fun warmHigherQualityInBackground(context: Context, song: Song, maxBitrateKbps: Int) {
        val videoId = song.youtubeId
        val cacheKey = if (maxBitrateKbps > 0) "${videoId}_q$maxBitrateKbps" else "${videoId}_high"
        if (streamUrlLruCache.get(cacheKey) != null) return
        // Fire-and-forget on OkHttp's dispatcher via a cheap coroutine scope-less launch
        backgroundScope.launch(Dispatchers.IO) {
            try {
                // Wait briefly for initial buffer before warming higher quality
                delay(3_000L)
                if (streamUrlLruCache.get(cacheKey) != null) return@launch

                val userPreferencesRepository = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    YoutubeHelperEntryPoint::class.java
                ).userPreferencesRepository()
                val isSaavnEnabled = userPreferencesRepository.enableSaavnStreamingFlow.first()

                var resolvedUrl: String? = null
                var resolvedMime: String? = null
                var resolvedBitrate: Int? = null

                if (isSaavnEnabled) {
                    val saavnQuality = userPreferencesRepository.saavnAudioQualityFlow.first()
                    val artistsList = song.artist.split(",", "&", "feat.", "ft.", ";")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val saavnResult = withTimeoutOrNull(3000L) {
                        SaavnService.resolveStream(
                            title = song.title,
                            artists = if (artistsList.isNotEmpty()) artistsList else listOf(song.artist),
                            album = song.album,
                            durationSeconds = parseDurationToSeconds(song.duration),
                            streamingQuality = StreamingAudioQuality.HIGH,
                            maxBitrateKbps = maxBitrateKbps,
                            saavnQuality = saavnQuality
                        )
                    }

                    if (saavnResult != null && saavnResult.url.isNotBlank()) {
                        resolvedUrl = saavnResult.url
                        resolvedMime = "audio/mp4"
                        resolvedBitrate = saavnResult.bitrateKbps * 1000
                    }
                }

                val finalUrl = resolvedUrl ?: run {
                    val result = getSongUrlFromYoutube(
                        context = context,
                        song = song,
                        lowQuality = false,
                        maxBitrateKbps = maxBitrateKbps
                    )
                    resolvedMime = result.second
                    resolvedBitrate = result.third
                    result.first
                }

                streamUrlLruCache.put(cacheKey, finalUrl)
                resolvedMime?.let { streamMimeTypeLruCache.put(cacheKey, it) }
                resolvedBitrate?.let { streamBitrateLruCache.put(cacheKey, it) }
                if (maxBitrateKbps == 0 || maxBitrateKbps >= 256) {
                    streamUrlLruCache.put("${videoId}_high", finalUrl)
                    resolvedMime?.let { streamMimeTypeLruCache.put("${videoId}_high", it) }
                    resolvedBitrate?.let { streamBitrateLruCache.put("${videoId}_high", it) }
                }
                PixelMusicHelper.printd("$videoId : Background quality upgrade cached ($cacheKey, bitrate=$resolvedBitrate)")
            } catch (e: Exception) {
                PixelMusicHelper.printe("$videoId : Background quality upgrade failed: ${e.message}")
            }
        }
    }

    /**
     * Returns the LOWEST-bitrate stream URL for the given song for instant playback start.
     * Uses the LRU cache keyed by "<videoId>_low".
     * Target resolution time: < 200 ms on a normal connection.
     */
    suspend fun getLowestQualityStreamUrl(context: Context, song: Song): String {
        val videoId = song.youtubeId

        if (song.audioFilePath?.isNotBlank() == true && File(song.audioFilePath).exists()) {
            return song.audioFilePath
        }

        // Offline-first gate
        val cachedLocalPath = localFilePathCache.get(videoId)
        if (cachedLocalPath != null && File(cachedLocalPath).exists()) {
            return cachedLocalPath
        }
        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        val savedSong = try { localSongRepository.getSong(videoId) } catch (_: Exception) { null }
        if (savedSong?.audioFilePath != null && File(savedSong.audioFilePath).exists()) {
            localFilePathCache.put(videoId, savedSong.audioFilePath)
            return savedSong.audioFilePath
        }

        // LRU cache hit with validation
        streamUrlLruCache.get("${videoId}_low")?.let { 
            if (isYoutubeUrlValid(it)) return it 
        }
        // If high-quality is already cached and valid, use it immediately (better than re-resolving)
        streamUrlLruCache.get("${videoId}_high")?.let { 
            if (isYoutubeUrlValid(it)) return it 
        }

        val lowResult = getSongUrlFromYoutube(context, song, retries = 1, lowQuality = true)
        val lowUrl = lowResult.first
        val mimeType = lowResult.second
        val bitrate = lowResult.third
        streamUrlLruCache.put("${videoId}_low", lowUrl)
        mimeType?.let { streamMimeTypeLruCache.put("${videoId}_low", it) }
        bitrate?.let { streamBitrateLruCache.put("${videoId}_low", it) }
        return lowUrl
    }

    /**
     * Returns the HIGHEST-bitrate stream URL. Checks LRU cache first.
     */
    suspend fun getHighestQualityStreamUrl(context: Context, song: Song): String {
        val videoId = song.youtubeId

        if (song.audioFilePath?.isNotBlank() == true && File(song.audioFilePath).exists()) {
            return song.audioFilePath
        }

        // Offline-first gate
        val cachedLocalPath = localFilePathCache.get(videoId)
        if (cachedLocalPath != null && File(cachedLocalPath).exists()) {
            return cachedLocalPath
        }
        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        val savedSong = try { localSongRepository.getSong(videoId) } catch (_: Exception) { null }
        if (savedSong?.audioFilePath != null && File(savedSong.audioFilePath).exists()) {
            localFilePathCache.put(videoId, savedSong.audioFilePath)
            return savedSong.audioFilePath
        }

        val maxBitrate = getTargetBitrateCeiling(context)
        val cacheKey = if (maxBitrate > 0) "${videoId}_q$maxBitrate" else "${videoId}_high"
        streamUrlLruCache.get(cacheKey)?.let { 
            if (isYoutubeUrlValid(it)) return it 
        }

        val highResult = getSongUrlFromYoutube(context, song, lowQuality = false, maxBitrateKbps = maxBitrate)
        val highUrl = highResult.first
        val mimeType = highResult.second
        val bitrate = highResult.third
        streamUrlLruCache.put(cacheKey, highUrl)
        mimeType?.let { streamMimeTypeLruCache.put(cacheKey, it) }
        bitrate?.let { streamBitrateLruCache.put(cacheKey, it) }
        if (maxBitrate == 0 || maxBitrate >= 256) {
            streamUrlLruCache.put("${videoId}_high", highUrl)
            mimeType?.let { streamMimeTypeLruCache.put("${videoId}_high", it) }
            bitrate?.let { streamBitrateLruCache.put("${videoId}_high", it) }
        }
        return highUrl
    }

    /**
     * Lossless sources gate shared by [getSongPlayerUrl] and [getSongPlayerUrlWithQuality].
     *
     * Builds the same title/artist/album/duration query the JioSaavn gate uses (falling back to
     * the saved song row and finally to a metadata fetch), runs it through
     * [LosslessStreamResolver] (Tidal → Qobuz → Deezer → Apple Music in the user's order, each
     * behind ArchiveTune's title/duration match gate) and, on a hit, primes the stream LRU caches
     * under every [cacheKeys] alias exactly like the JioSaavn path does. Returns null when the
     * feature is disabled, no source produced a usable stream, or anything threw — the caller then
     * continues with JioSaavn / YouTube unchanged.
     */
    private suspend fun resolveLosslessStreamUrl(
        context: Context,
        song: Song,
        savedSong: Song?,
        cacheKeys: List<String>,
        lowDataMode: Boolean,
        fastTimeoutMs: Long? = null,
    ): String? {
        val videoId = song.youtubeId
        return try {
            if (lowDataMode || !LosslessStreamResolver.hasAnyActiveLosslessSource(context)) return null

            // A previous resolve for this song that is still fresh wins immediately.
            LosslessStreamResolver.resultFor(videoId)?.let { previous ->
                if (LosslessStreamResolver.isFreshUri(previous.uri)) {
                    cacheKeys.forEach { key ->
                        streamUrlLruCache.put(key, previous.uri)
                        streamMimeTypeLruCache.put(key, previous.mimeType)
                        streamBitrateLruCache.put(key, previous.bitrate)
                    }
                    PixelMusicHelper.printd("$videoId : INSTANT start from fresh lossless resolve (${previous.label})")
                    return previous.uri
                }
            }

            var effectiveTitle = song.title.ifBlank { savedSong?.title.orEmpty() }
            var effectiveArtist = song.artist.ifBlank { savedSong?.artist.orEmpty() }
            var effectiveAlbum = song.album?.ifBlank { null } ?: savedSong?.album
            var effectiveDuration = parseDurationToSeconds(song.duration.ifBlank { savedSong?.duration.orEmpty() })

            if (effectiveTitle.isBlank() && videoId.isNotBlank()) {
                val fetched = fetchSongMetadata(videoId)
                if (fetched != null) {
                    effectiveTitle = fetched.title
                    effectiveArtist = fetched.artist
                    effectiveAlbum = fetched.album
                    effectiveDuration = parseDurationToSeconds(fetched.duration)
                }
            }
            if (effectiveTitle.isBlank()) return null

            val artistsList = effectiveArtist.split(",", "&", "feat.", "ft.", ";")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(effectiveArtist).filter { it.isNotBlank() } }

            val result = withTimeoutOrNull(fastTimeoutMs ?: 2500L) {
                LosslessStreamResolver.resolve(
                    context = context,
                    request = LosslessStreamResolver.Request(
                        mediaId = videoId,
                        title = effectiveTitle,
                        artists = artistsList,
                        album = effectiveAlbum,
                        durationMs = effectiveDuration?.takeIf { it > 0 }?.let { it * 1000L },
                    ),
                    lowDataMode = lowDataMode,
                )
            } ?: return null

            cacheKeys.forEach { key ->
                streamUrlLruCache.put(key, result.uri)
                streamMimeTypeLruCache.put(key, result.mimeType)
                streamBitrateLruCache.put(key, result.bitrate)
            }
            PixelMusicHelper.printd(
                "$videoId : Playing via lossless source ${result.source.name} (${result.label}, mime=${result.mimeType}, bitrate=${result.bitrate})"
            )
            result.uri
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            PixelMusicHelper.printe("$videoId : Lossless resolve exception: ${e.message}, falling back")
            null
        }
    }

    /** Register a downloaded local file path so future plays are instant (offline gate). */
    fun registerLocalFilePath(youtubeId: String, filePath: String) {
        if (filePath.isNotBlank() && File(filePath).exists()) {
            localFilePathCache.put(youtubeId, filePath)
        }
    }

    /**
     * Returns a stream URL respecting the user's quality ceiling.
     * Used by the network-aware playback system:
     * - On WiFi: maxBitrateKbps comes from StreamingAudioQuality (user's WiFi setting)
     * - On metered: maxBitrateKbps comes from StreamingAudioQuality (user's mobile setting)
     * - Always starts at lowest quality first, then upgrades (handled by caller)
     *
     * @param maxBitrateKbps Maximum bitrate ceiling in kbps. 0 = no ceiling (highest available).
     */
    suspend fun getSongPlayerUrlWithQuality(
        context: Context,
        song: Song,
        maxBitrateKbps: Int = 0,
        forDownload: Boolean = true,
        bypassLossless: Boolean = false,
        bypassSaavn: Boolean = false
    ): String {
        val videoId = song.youtubeId

        if (!forDownload && song.audioFilePath?.isNotBlank() == true && File(song.audioFilePath).exists()) {
            return song.audioFilePath
        }

        // Offline-first gate (skip if forDownload because we are trying to download it fresh)
        if (!forDownload) {
            val cachedLocalPath = localFilePathCache.get(videoId)
            if (cachedLocalPath != null && File(cachedLocalPath).exists()) {
                return cachedLocalPath
            }
            val localSongRepository = AppDatabase.getInstance(context).songRepository()
            val savedSong = try { localSongRepository.getSong(videoId) } catch (_: Exception) { null }
            if (savedSong?.audioFilePath != null && File(savedSong.audioFilePath).exists()) {
                localFilePathCache.put(videoId, savedSong.audioFilePath)
                return savedSong.audioFilePath
            }
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            YoutubeHelperEntryPoint::class.java
        )
        val userPreferencesRepository = entryPoint.userPreferencesRepository()
        val connectivityStateHolder = entryPoint.connectivityStateHolder()

        val isSaavnEnabled = userPreferencesRepository.enableSaavnStreamingFlow.first()
        val saavnQuality = userPreferencesRepository.saavnAudioQualityFlow.first()
        val wifiQuality = userPreferencesRepository.streamingAudioQualityWifiFlow.first()
        val isLosslessEnabled = com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver.isEnabled(context)

        val cacheKey = if (maxBitrateKbps > 0) "${videoId}_q${maxBitrateKbps}" else "${videoId}_high"
        if (!forDownload) {
            streamUrlLruCache.get(cacheKey)?.let { 
                if (isUrlAllowed(it, isSaavnEnabled) && isYoutubeUrlValid(it)) return it 
            }
        }

        // Effective quality determination:
        // Downloads use the dedicated download quality preference.
        // Playback uses the streaming/saavn quality settings.
        val downloadQuality = if (forDownload) userPreferencesRepository.downloadAudioQualityFlow.first() else null
        val effectiveQuality = if (forDownload) {
            when (downloadQuality) {
                com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.MAX -> StreamingAudioQuality.HIGH
                com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.HIGH -> StreamingAudioQuality.HIGH
                com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.MEDIUM -> StreamingAudioQuality.MEDIUM
                com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.LOW -> StreamingAudioQuality.LOW
                null -> StreamingAudioQuality.HIGH
            }
        } else {
            when (saavnQuality) {
                SaavnAudioQuality.AUTO -> when (wifiQuality) {
                    StreamingAudioQuality.LOW -> StreamingAudioQuality.LOW
                    StreamingAudioQuality.MEDIUM -> StreamingAudioQuality.MEDIUM
                    else -> StreamingAudioQuality.HIGH
                }
                SaavnAudioQuality.QUALITY_96 -> StreamingAudioQuality.LOW
                SaavnAudioQuality.QUALITY_160 -> StreamingAudioQuality.MEDIUM
                SaavnAudioQuality.QUALITY_320 -> StreamingAudioQuality.HIGH
            }
        }

        val isMetered = connectivityStateHolder.isMeteredNetwork.value
        val forceHigh = userPreferencesRepository.forceHighQualityOnMobileFlow.first()
        val targetQuality = if (isMetered && !forceHigh) {
            userPreferencesRepository.streamingAudioQualityMobileFlow.first()
        } else {
            userPreferencesRepository.streamingAudioQualityWifiFlow.first()
        }
        val isAutoQuality = !forDownload && (targetQuality == StreamingAudioQuality.AUTO || saavnQuality == SaavnAudioQuality.AUTO)
        val speed = connectivityStateHolder.linkDownstreamBandwidthKbps.value
        val isLowBandwidth = isMetered || (speed in 1..2_000) || connectivityStateHolder.isNetworkRecentlyUnstable.value

        // Lossless sources gate (ArchiveTune source-pool port)
        // Downloads: only attempt Lossless (FLAC/ALAC) when user configured MAX quality and lossless is not bypassed.
        // Playback: check lossless first when enabled.
        // When quality is AUTO and network is constrained, skip lossless immediately to guarantee fastest zero-delay playback.
        val shouldTryLossless = if (bypassLossless) {
            false
        } else if (forDownload) {
            isLosslessEnabled && downloadQuality == com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.MAX
        } else if (isAutoQuality && isLowBandwidth) {
            false
        } else {
            isLosslessEnabled
        }
        if (shouldTryLossless) {
            val localSongRepository = AppDatabase.getInstance(context).songRepository()
            val savedSong = try { localSongRepository.getSong(videoId) } catch (_: Exception) { null }
            val fastTimeout = if (isAutoQuality) 1200L else if (isLowBandwidth) 2000L else 3000L
            resolveLosslessStreamUrl(
                context = context,
                song = song,
                savedSong = savedSong,
                cacheKeys = listOf(cacheKey, "${videoId}_high"),
                lowDataMode = if (forDownload) false else (isLowBandwidth || maxBitrateKbps in 1..127),
                fastTimeoutMs = fastTimeout,
            )?.let { return it }
        }

        // JioSaavn check:
        // - If JioSaavn is turned OFF, never query JioSaavn (neither for downloads nor streaming).
        // - For downloads when JioSaavn is enabled:
        //   * Max / High: JioSaavn 320 kbps
        //   * Medium: JioSaavn 160 kbps
        //   * Low: bypass JioSaavn completely, fall back to YouTube downloads
        // - For playback: only if JioSaavn streaming toggle is ON
        val shouldTrySaavn = if (bypassSaavn || !isSaavnEnabled) {
            false
        } else if (forDownload) {
            downloadQuality != com.unshoo.pixelmusic.data.preferences.DownloadAudioQuality.LOW
        } else {
            isSaavnEnabled
        }

        if (shouldTrySaavn) {
            try {
                val localSongRepository = AppDatabase.getInstance(context).songRepository()
                val savedSong = try { localSongRepository.getSong(videoId) } catch (_: Exception) { null }
                var effectiveTitle = song.title.ifBlank { savedSong?.title.orEmpty() }
                var effectiveArtist = song.artist.ifBlank { savedSong?.artist.orEmpty() }
                var effectiveAlbum = song.album?.ifBlank { null } ?: savedSong?.album
                var durationStr = song.duration.ifBlank { savedSong?.duration.orEmpty() }
                var effectiveDuration = parseDurationToSeconds(durationStr)

                if (effectiveTitle.isBlank() && videoId.isNotBlank()) {
                    val fetched = withTimeoutOrNull(1500L) { fetchSongMetadata(videoId) }
                    if (fetched != null) {
                        effectiveTitle = fetched.title
                        effectiveArtist = fetched.artist
                        effectiveAlbum = fetched.album
                        effectiveDuration = parseDurationToSeconds(fetched.duration)
                    }
                }

                if (effectiveTitle.isNotBlank()) {
                    val artistsList = effectiveArtist.split(",", "&", "feat.", "ft.", ";")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val streamingQuality = if (forDownload) {
                        effectiveQuality
                    } else {
                        when {
                            maxBitrateKbps in 1..127 -> StreamingAudioQuality.LOW
                            maxBitrateKbps in 128..255 -> StreamingAudioQuality.MEDIUM
                            else -> StreamingAudioQuality.HIGH
                        }
                    }

                    val effectiveSaavnQuality = if (forDownload) {
                        when (effectiveQuality) {
                            StreamingAudioQuality.MEDIUM -> SaavnAudioQuality.QUALITY_160
                            else -> SaavnAudioQuality.QUALITY_320
                        }
                    } else {
                        saavnQuality
                    }

                    val timeout = if (forDownload) 5000L else 3000L
                    val saavnResult = withTimeoutOrNull(timeout) {
                        SaavnService.resolveStream(
                            title = effectiveTitle,
                            artists = if (artistsList.isNotEmpty()) artistsList else listOf(effectiveArtist),
                            album = effectiveAlbum,
                            durationSeconds = effectiveDuration,
                            streamingQuality = streamingQuality,
                            maxBitrateKbps = maxBitrateKbps,
                            saavnQuality = effectiveSaavnQuality
                        )
                    }

                    if (saavnResult != null && saavnResult.url.isNotBlank()) {
                        val saavnUrl = saavnResult.url
                        val saavnBitrate = saavnResult.bitrateKbps * 1000
                        val mimeType = "audio/mp4"

                        if (isSaavnEnabled) {
                            streamUrlLruCache.put(cacheKey, saavnUrl)
                            streamMimeTypeLruCache.put(cacheKey, mimeType)
                            streamBitrateLruCache.put(cacheKey, saavnBitrate)
                        }
                        PixelMusicHelper.printd(
                            "$videoId : ${if (forDownload) "Downloading" else "Streaming"} via JioSaavn (${saavnResult.quality}, bitrate=${saavnBitrate})"
                        )
                        return saavnUrl
                    }
                }
            } catch (e: Exception) {
                PixelMusicHelper.printe("$videoId : JioSaavn resolve for ${if (forDownload) "download" else "stream"} failed: ${e.message}")
            }
        }

        val urlResult = getSongUrlFromYoutube(
            context = context,
            song = song,
            lowQuality = (forDownload && effectiveQuality == StreamingAudioQuality.LOW),
            maxBitrateKbps = maxBitrateKbps
        )
        val url = urlResult.first
        val mimeType = urlResult.second
        val bitrate = urlResult.third
        if (!forDownload || isSaavnEnabled) {
            streamUrlLruCache.put(cacheKey, url)
            mimeType?.let { streamMimeTypeLruCache.put(cacheKey, it) }
            bitrate?.let { streamBitrateLruCache.put(cacheKey, it) }
        }
        return url
    }

    /** Invalidate ALL cached stream URLs for a video ID (including quality-specific _q* keys). */
    fun invalidateStreamCache(youtubeId: String) {
        // Always remove the known aliases first.
        streamUrlLruCache.remove("${youtubeId}_low")
        streamUrlLruCache.remove("${youtubeId}_high")
        streamMimeTypeLruCache.remove("${youtubeId}_low")
        streamMimeTypeLruCache.remove("${youtubeId}_high")
        streamBitrateLruCache.remove("${youtubeId}_low")
        streamBitrateLruCache.remove("${youtubeId}_high")
        // Also sweep quality-specific _q* keys so stale expired URLs can't be replayed.
        val prefix = "${youtubeId}_"
        streamUrlLruCache.snapshot().keys.filter { it.startsWith(prefix) }.forEach {
            streamUrlLruCache.remove(it)
            streamMimeTypeLruCache.remove(it)
            streamBitrateLruCache.remove(it)
        }
    }

    private const val MAX_TRACKING_CACHE_ENTRIES = 300

    /**
     * Drops expired entries from the resolved-candidate-URL cache and caps the size of the
     * playback-tracking maps.
     *
     * Note this is memory hygiene, not a playback-correctness fix: [resolvedCandidateUrlCache]
     * is an unbounded map keyed by video/itag/client/auth, so a long-running session that plays
     * many different songs can otherwise accumulate entries indefinitely. The stream/mime/bitrate
     * caches above are already size-capped LruCaches, and every read site (e.g. the
     * resolvedCandidateUrlCache lookup during resolution) already re-checks expiry with a safety
     * margin before trusting a cached URL — a stale entry is never served either way, pruned or
     * not. Safe to call periodically (e.g. from onTrimMemory or a background sync tick).
     */
    fun pruneExpiredCaches() {
        val now = System.currentTimeMillis()
        resolvedCandidateUrlCache.entries.removeIf { (_, cached) -> cached.second <= now }
        if (playbackTrackingCache.size > MAX_TRACKING_CACHE_ENTRIES) {
            playbackTrackingCache.keys
                .take(playbackTrackingCache.size - MAX_TRACKING_CACHE_ENTRIES)
                .forEach { playbackTrackingCache.remove(it) }
        }
        if (watchtimeTrackingCache.size > MAX_TRACKING_CACHE_ENTRIES) {
            watchtimeTrackingCache.keys
                .take(watchtimeTrackingCache.size - MAX_TRACKING_CACHE_ENTRIES)
                .forEach { watchtimeTrackingCache.remove(it) }
        }
    }

    private fun extractDuration(songContent: JsonObject): String {
        val durationRegex = Regex("""\d+:\d{2}(:\d{2})?""")

        val fixedDuration = songContent["fixedColumns"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("musicResponsiveListItemFixedColumnRenderer")
            ?.jsonObject
            ?.get("text")
            ?.jsonObject
            ?.get("runs")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull

        if (fixedDuration != null) {
            return fixedDuration
        }

        val flexColumns = songContent["flexColumns"]
            ?.jsonArray
            ?: return ""

        for (column in flexColumns) {
            val runs = column.jsonObject["musicResponsiveListItemFlexColumnRenderer"]
                ?.jsonObject
                ?.get("text")
                ?.jsonObject
                ?.get("runs")
                ?.jsonArray
                ?: continue

            for (run in runs) {
                val text = run.jsonObject["text"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: continue

                if (durationRegex.matches(text)) {
                    return text
                }
            }
        }

        return ""
    }

    /**
     * Resolves the playable stream URL for [song].
     *
     * Strategy ported 1:1 from umihi-music (measurably the fastest resolver):
     *  1. ONE unauthenticated Innertube `player` POST with the ANDROID_VR client — it returns
     *     PLAIN (non-ciphered) audio URLs, so there is no signature deciphering, no n-throttle
     *     deobfuscation, no BotGuard/PoToken minting and no byte-range validation probe.
     *     Retries only when the server rotates visitorData (bot check), mirroring umihi.
     *  2. Fallback: the original NewPipe extractor (watch-page scrape), RETRY_COUNT attempts
     *     with linear back-off.
     */
    private suspend fun getSongUrlFromYoutube(
        context: Context,
        song: Song,
        retries: Int = Constants.YoutubeApi.RETRY_COUNT,
        lowQuality: Boolean = false,
        maxBitrateKbps: Int = 0
    ): Triple<String, String?, Int?> {
        val videoId = song.youtubeId
        var lastError: Throwable? = null

        // Stage 1 — umihi pure: single ANDROID_VR shot.
        resolveAndroidVrStreamUrl(videoId, lowQuality, maxBitrateKbps, retries)?.let { return it }

        // Stage 2 — ArchiveTune: full multi-client Innertube resolution as the ROBUST middle
        // layer (only runs when the umihi fast path fails — bot-throttles, region/age gates,
        // premium formats). Direct-url candidates first, zero validation probes.
        resolveArchiveTuneStreamUrl(context, videoId, lowQuality, maxBitrateKbps)?.let { return it }

        // Stage 3 — NewPipe extractor (both apps' final fallback).
        PixelMusicHelper.printd("$videoId : Falling back to NewPipe extractor")
        repeat(Constants.YoutubeApi.RETRY_COUNT) { attempt ->
            try {
                return resolveNewPipeStreamUrl(song, lowQuality, maxBitrateKbps)
            } catch (e: Throwable) {
                lastError = e
                PixelMusicHelper.printe(
                    "$videoId : NewPipe attempt ${attempt + 1}/${Constants.YoutubeApi.RETRY_COUNT} failed: " +
                        "${e::class.simpleName}: ${e.message ?: "no message"}"
                )
                if (attempt < Constants.YoutubeApi.RETRY_COUNT - 1) {
                    delay(Constants.YoutubeApi.RETRY_DELAY * (attempt + 1))
                }
            }
        }

        throw Exception(
            "$videoId : Fatal fail. Could not get stream URL after ${Constants.YoutubeApi.RETRY_COUNT} attempts",
            lastError
        )
    }

    /**
     * umihi-music fast path: a single Innertube player request with the ANDROID_VR client.
     * BotGuard minting is explicitly skipped for this call (copy of the auth state with
     * webClientPoTokenEnabled=false): ANDROID_VR plain URLs need no PoToken, and the synchronous
     * BotGuardTokenGenerator.mintToken() inside YouTube.player() was adding seconds per request.
     */
    private suspend fun resolveAndroidVrStreamUrl(
        videoId: String,
        lowQuality: Boolean,
        maxBitrateKbps: Int,
        retries: Int
    ): Triple<String, String?, Int?>? = withContext(Dispatchers.IO) {
        repeat(retries) { attempt ->
            val previousVisitorData = YouTube.visitorData

            val response = try {
                YouTube.player(
                    videoId = videoId,
                    client = ANDROID_VR_1_61_48,
                    authState = YouTube.currentPlaybackAuthState()
                        .copy(webClientPoTokenEnabled = false),
                ).getOrNull()
            } catch (e: Exception) {
                null
            } ?: run {
                PixelMusicHelper.printe("$videoId : ANDROID_VR player request failed (attempt ${attempt + 1}/$retries)")
                return@withContext null
            }

            val status = response.playabilityStatus.status
            val reason = response.playabilityStatus.reason.orEmpty()

            if (status == "OK") {
                val picked = pickDirectAudioFormat(response, lowQuality, maxBitrateKbps)
                if (picked != null) {
                    response.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.let {
                        playbackTrackingCache[videoId] = it
                    }
                    response.playbackTracking?.videostatsWatchtimeUrl?.baseUrl?.let {
                        watchtimeTrackingCache[videoId] = it
                    }
                    PixelMusicHelper.printd(
                        "$videoId : INSTANT stream via ANDROID_VR (bitrate=${picked.bitrate} low=$lowQuality)"
                    )
                    return@withContext Triple(picked.url!!, normalizeMimeType(picked.mimeType), picked.bitrate)
                }

                PixelMusicHelper.printe("$videoId : ANDROID_VR returned no direct audio formats")
                return@withContext null
            }

            PixelMusicHelper.printe("$videoId : ANDROID_VR playability failed status=$status reason=$reason")

            // umihi parity: on bot checks, rotate visitorData once and retry the same fast client;
            // anything else is not recoverable here, so bail to the NewPipe fallback.
            val lowerReason = reason.lowercase(Locale.US)
            val isBot = "bot" in lowerReason || "unusual traffic" in lowerReason || "automated" in lowerReason
            if (isBot && attempt < retries - 1) {
                val refreshedVisitorData = try {
                    YouTube.visitorData().getOrNull()
                } catch (_: Exception) {
                    null
                }
                if (!refreshedVisitorData.isNullOrBlank() && refreshedVisitorData != previousVisitorData) {
                    YouTube.visitorData = refreshedVisitorData
                    PixelMusicHelper.printd("$videoId : Retrying ANDROID_VR with rotated visitorData (${attempt + 2}/$retries)")
                } else {
                    return@withContext null
                }
            } else {
                return@withContext null
            }
        }
        null
    }

    /**
     * umihi's selection rule, parameterised by our quality ceiling: among the PLAIN-url audio
     * formats pick the highest bitrate (best under the ceiling when configured; lowest first
     * only for the weak-link LOW path). Direct `url` formats only — never ciphered signatures.
     */
    private fun codecRank(mimeType: String): Int = when {
        mimeType.contains("opus", ignoreCase = true) -> 3
        mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> 2
        else -> 1
    }

    private fun pickDirectAudioFormat(
        response: PlayerResponse,
        lowQuality: Boolean,
        maxBitrateKbps: Int
    ): PlayerResponse.StreamingData.Format? {
        val directAudio = response.streamingData?.adaptiveFormats.orEmpty()
            .filter { !it.url.isNullOrBlank() }
            .filter { it.mimeType.startsWith("audio/", ignoreCase = true) }
            .filter {
                !it.mimeType.contains("mp3", ignoreCase = true) &&
                    !it.mimeType.contains("mpeg", ignoreCase = true) &&
                    !it.mimeType.contains("mpga", ignoreCase = true)
            }
        if (directAudio.isEmpty()) return null

        val preferOpusHigher = compareByDescending<PlayerResponse.StreamingData.Format> { codecRank(it.mimeType) }
            .thenByDescending { it.bitrate }

        val preferLower = compareBy<PlayerResponse.StreamingData.Format> { it.bitrate }
            .thenByDescending { codecRank(it.mimeType) }

        return when {
            lowQuality -> directAudio.sortedWith(preferLower).firstOrNull()
            maxBitrateKbps > 0 -> {
                val bpsCeiling = maxBitrateKbps * 1000
                val withinCeiling = directAudio.filter { it.bitrate <= bpsCeiling }
                if (withinCeiling.isNotEmpty()) {
                    withinCeiling.sortedWith(preferOpusHigher).firstOrNull()
                } else {
                    directAudio.sortedWith(preferOpusHigher).firstOrNull()
                }
            }
            else -> directAudio.sortedWith(preferOpusHigher).firstOrNull()
        }
    }

    /**
     * umihi fallback: NewPipe extractor (watch-page scrape) best audio stream, with the same
     * quality-cap parameterisation and zero cipher handling.
     */
    private suspend fun resolveNewPipeStreamUrl(
        song: Song,
        lowQuality: Boolean,
        maxBitrateKbps: Int
    ): Triple<String, String?, Int?> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val extractor = service.getStreamExtractor(song.youtubeUrl)
        extractor.fetchPage()

        val streams = extractor.audioStreams.filter { stream ->
            val suffix = stream.format?.suffix?.lowercase().orEmpty()
            val name = stream.format?.name?.lowercase().orEmpty()
            stream.content.isNotBlank() &&
                !suffix.contains("mp3") && !suffix.contains("mpeg") && !suffix.contains("mpga") &&
                !name.contains("mp3") && !name.contains("mpeg") && !name.contains("mpga")
        }

        val opusStreams = streams.filter { stream ->
            val suffix = stream.format?.suffix?.lowercase().orEmpty()
            val name = stream.format?.name?.lowercase().orEmpty()
            suffix.contains("opus") || name.contains("opus")
        }
        val m4aStreams = streams.filter { stream ->
            val suffix = stream.format?.suffix?.lowercase().orEmpty()
            val name = stream.format?.name?.lowercase().orEmpty()
            (suffix.contains("m4a") || name.contains("m4a") || suffix.contains("mp4") || name.contains("mp4")) &&
                !(suffix.contains("opus") || name.contains("opus"))
        }
        val webmStreams = streams.filter { stream ->
            val suffix = stream.format?.suffix?.lowercase().orEmpty()
            val name = stream.format?.name?.lowercase().orEmpty()
            (suffix.contains("webm") || name.contains("webm")) &&
                !(suffix.contains("opus") || name.contains("opus"))
        }
        val otherStreams = streams.filter { stream ->
            val suffix = stream.format?.suffix?.lowercase().orEmpty()
            val name = stream.format?.name?.lowercase().orEmpty()
            !(suffix.contains("opus") || name.contains("opus")) &&
                !(suffix.contains("m4a") || name.contains("m4a") || suffix.contains("mp4") || name.contains("mp4")) &&
                !(suffix.contains("webm") || name.contains("webm"))
        }

        fun sortNewPipeGroup(group: List<org.schabi.newpipe.extractor.stream.AudioStream>): List<org.schabi.newpipe.extractor.stream.AudioStream> {
            if (group.isEmpty()) return emptyList()
            return when {
                lowQuality -> group.sortedBy { it.averageBitrate }
                maxBitrateKbps > 0 -> {
                    val bpsCeiling = maxBitrateKbps * 1000
                    val withinCeiling = group.filter { it.averageBitrate <= bpsCeiling }
                    if (withinCeiling.isNotEmpty()) {
                        withinCeiling.sortedByDescending { it.averageBitrate }
                    } else {
                        group.sortedBy { it.averageBitrate }
                    }
                }
                else -> group.sortedByDescending { it.averageBitrate }
            }
        }

        val orderedStreams = sortNewPipeGroup(opusStreams) + sortNewPipeGroup(m4aStreams) +
            sortNewPipeGroup(webmStreams) + sortNewPipeGroup(otherStreams)
        val selectedStream = orderedStreams.firstOrNull()
            ?: streams.firstOrNull()
            ?: throw Exception("No valid audio streams found")

        val suffix = selectedStream.format?.suffix?.lowercase().orEmpty()
        val name = selectedStream.format?.name?.lowercase().orEmpty()
        val mime = when {
            suffix.contains("opus") || name.contains("opus") -> "audio/opus"
            suffix.contains("m4a") || name.contains("m4a") -> "audio/mp4"
            suffix.contains("webm") || name.contains("webm") -> "audio/webm"
            else -> null
        }
        Triple(selectedStream.content, mime, selectedStream.averageBitrate.toInt())
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    //  Stage 2 — ArchiveTune (rukamori/ArchiveTune YTPlayerUtils) robust multi-client fallback
    //  Ported faithfully, with the PixelMusic-slowdown pieces REMOVED:
    //   ✗ no per-candidate byte-range validateStatus probe (ArchiveTune trusts direct URLs)
    //   ✗ no double full-resolution on failure
    //   ✓ direct-url formats tried FIRST (zero decipher work on the common path)
    //   ✓ per-(videoId|itag|client) resolved URL cache — skips repeat n-throttle deobfuscation
    //   ✓ single BotGuard/visitorData repair on bot-detection, then same-client retry
    //   ✓ login-context-aware client ordering + loginRequired gating
    // ════════════════════════════════════════════════════════════════════════════════════

    private suspend fun resolveArchiveTuneStreamUrl(
        context: Context,
        videoId: String,
        lowQuality: Boolean,
        maxBitrateKbps: Int,
    ): Triple<String, String?, Int?>? = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            YoutubeHelperEntryPoint::class.java
        )
        val userPrefs = entryPoint.userPreferencesRepository()
        val preferredClient = try {
            userPrefs.playerStreamClientFlow.first()
        } catch (_: Exception) {
            PlayerStreamClient.WEB_REMIX
        }
        if (lastSuccessfulClientKey == null) {
            try {
                lastSuccessfulClientKey = userPrefs.lastSuccessfulYoutubeClientKeyFlow.first()
            } catch (_: Exception) {}
        }

        var authState = YouTube.currentPlaybackAuthState()
        // Signature timestamp once per resolve (NewPipe caches per videoId) — ArchiveTune parity.
        val signatureTimestamp = try {
            NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        } catch (_: Exception) {
            null
        }

        // Restored c1f0a41 serial resolution (instant tap-to-play): one client at a time,
        // ANDROID_VR plain-URL clients first, early exit on first success. Parallel racing
        // + 2s bounds + 60s global quarantine (d145ebe/3500878) regressed this path — do not
        // reintroduce without measuring tap-to-audio on weak networks.
        val clients = buildStreamClientOrder(preferredClient, authState)
        var didRepairAuthAfterBotDetection = false

        for (client in clients) {
            // ArchiveTune rule: never burn a request on a login-required client when anonymous.
            if (client.loginRequired && !(authState.hasPlaybackLoginContext && client.loginSupported)) {
                continue
            }

            val response = try {
                YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    authState = authState,
                ).getOrNull()
            } catch (e: Exception) {
                PixelMusicHelper.printe("$videoId : ${client.clientName} player request failed: ${e.message}")
                null
            } ?: continue

            var status = response.playabilityStatus.status
            var streamResponse = response

            if (status != "OK") {
                val reason = response.playabilityStatus.reason.orEmpty()
                PixelMusicHelper.printe("$videoId : ${client.clientName} playability status=$status reason=$reason")

                // ArchiveTune bot-detection repair: invalidate BotGuard session + rotate
                // visitorData once per resolve, then retry the SAME client a single time.
                if (isBotDetectionReason(reason) && !didRepairAuthAfterBotDetection) {
                    didRepairAuthAfterBotDetection = true
                    repairPlaybackAuthAfterBotDetection()?.let { repaired ->
                        authState = repaired
                    }
                    val retried = try {
                        YouTube.player(
                            videoId = videoId,
                            client = client,
                            signatureTimestamp = signatureTimestamp,
                            authState = authState,
                        ).getOrNull()
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    status = retried.playabilityStatus.status
                    streamResponse = retried
                }
                if (status != "OK") continue
            }

            val candidates = selectArchiveTuneCandidates(streamResponse, lowQuality, maxBitrateKbps)
            if (candidates.isEmpty()) continue

            var resolved: Triple<String, String?, Int?>? = null
            for (candidate in candidates) {
                if (shouldSkipCipheredWebCandidate(client, candidate, authState)) continue

                val cacheKey = "$videoId|${candidate.itag}|${client.clientName}|${authState.fingerprint.hashCode()}"
                val cached = resolvedCandidateUrlCache[cacheKey]
                val url: String? = if (
                    cached != null && cached.second > System.currentTimeMillis() + CANDIDATE_URL_EXPIRY_SAFETY_MS
                ) {
                    cached.first
                } else {
                    // ArchiveTune findUrl: direct URL → client-version patch; ciphered → NewPipe
                    // deobfuscation; n-parameter only when the URL actually carries one.
                    NewPipeUtils.getStreamUrl(candidate, videoId, client, authState).getOrNull()
                        ?.let { StreamClientUtils.patchClientVersion(it, client.clientVersion) }
                        ?.also { resolvedCandidateUrlCache[cacheKey] = it to expiryFromStreamUrl(it) }
                }

                // ArchiveTune purity: NO byte-range validation probe before accepting.
                if (url != null) {
                    resolved = Triple(url, normalizeMimeType(candidate.mimeType), candidate.bitrate)
                    break
                }
            }

            if (resolved != null) {
                streamResponse.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.let {
                    playbackTrackingCache[videoId] = it
                }
                streamResponse.playbackTracking?.videostatsWatchtimeUrl?.baseUrl?.let {
                    watchtimeTrackingCache[videoId] = it
                }
                val clientKey = StreamClientUtils.buildClientKey(client)
                lastSuccessfulClientKey = clientKey
                lastSuccessfulClientKeyLoaded = true
                backgroundScope.launch {
                    try {
                        entryPoint.userPreferencesRepository().setLastSuccessfulYoutubeClientKey(clientKey)
                    } catch (_: Exception) {}
                }
                PixelMusicHelper.printd(
                    "$videoId : stream via ArchiveTune fallback client ${client.clientName} (bitrate=${resolved.third})"
                )
                return@withContext resolved
            }
        }

        PixelMusicHelper.printe("$videoId : ArchiveTune fallback could not resolve a playable stream")
        null
    }

    /**
     * ArchiveTune client order: last-successful first, the user's preferred client next,
     * then the fallback list (ANDROID_VR family first). Login-capable clients ordered first
     * when a YouTube Music session exists.
     */
    private fun buildStreamClientOrder(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): List<YouTubeClient> {
        val preferredYouTubeClient = resolvePreferredPlaybackClient(preferredStreamClient, authState)
        val lastSuccessfulClient = lastSuccessfulClientKey?.let { key ->
            STREAM_FALLBACK_CLIENTS.find { StreamClientUtils.buildClientKey(it) == key }
        }

        val orderedFallbackClients =
            if (authState.hasPlaybackLoginContext) {
                STREAM_FALLBACK_CLIENTS.filter { it.loginSupported } +
                    STREAM_FALLBACK_CLIENTS.filterNot { it.loginSupported }
            } else {
                STREAM_FALLBACK_CLIENTS.toList()
            }

        return buildList {
            lastSuccessfulClient?.let { add(it) }
            add(preferredYouTubeClient)
            addAll(orderedFallbackClients)
            if (preferredYouTubeClient != WEB_REMIX) add(WEB_REMIX)
        }.distinct()
    }

    private fun resolvePreferredPlaybackClient(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): YouTubeClient {
        val hasPlayerPoToken = !authState.resolvePlayerPoToken(WEB_REMIX).isNullOrBlank()
        val hasGvsPoToken = !authState.resolveGvsPoToken(WEB_REMIX).isNullOrBlank()

        if (preferredStreamClient == PlayerStreamClient.ANDROID_VR &&
            authState.hasPlaybackLoginContext &&
            authState.webClientPoTokenEnabled &&
            hasPlayerPoToken &&
            hasGvsPoToken
        ) {
            return WEB_REMIX
        }

        return when (preferredStreamClient) {
            PlayerStreamClient.ANDROID_VR ->
                if (authState.hasPlaybackLoginContext) ANDROID_MUSIC else ANDROID_VR_NO_AUTH
            PlayerStreamClient.WEB_REMIX -> WEB_REMIX
        }
    }

    private fun isCipheredFormat(format: PlayerResponse.StreamingData.Format): Boolean {
        return format.url == null && (format.signatureCipher != null || format.cipher != null)
    }

    private fun shouldSkipCipheredWebCandidate(
        client: YouTubeClient,
        format: PlayerResponse.StreamingData.Format,
        authState: PlaybackAuthState,
    ): Boolean {
        val isWebClient = StreamClientUtils.isWebClient(client.clientName)
        val isCiphered = isCipheredFormat(format)
        val hasGvsPoToken = !authState.resolveGvsPoToken(client).isNullOrBlank()
        if (authState.webClientPoTokenEnabled && isWebClient && isCiphered && !hasGvsPoToken) {
            PixelMusicHelper.printd(
                "Skipping ciphered ${client.clientName} stream candidate because Web PoToken playback is enabled but no GVS token is available"
            )
            return true
        }
        return false
    }

    /**
     * ArchiveTune `selectAudioFormatCandidates`: DIRECT-URL formats first (they need zero
     * decipher work), then bitrate under the quality ceiling, then codec rank (opus > mp4a),
     * then sample rate.
     */
    private fun selectArchiveTuneCandidates(
        response: PlayerResponse,
        lowQuality: Boolean,
        maxBitrateKbps: Int,
    ): List<PlayerResponse.StreamingData.Format> {
        val formats = response.streamingData?.adaptiveFormats.orEmpty()
            .filter {
                it.mimeType.contains("audio", ignoreCase = true) &&
                    it.bitrate > 0 &&
                    !it.mimeType.contains("mp3", ignoreCase = true) &&
                    !it.mimeType.contains("mpeg", ignoreCase = true) &&
                    !it.mimeType.contains("mpga", ignoreCase = true)
            }
        if (formats.isEmpty()) return emptyList()

        fun codecRank(mimeType: String): Int = when {
            mimeType.contains("opus", ignoreCase = true) -> 3
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("mp4", ignoreCase = true) -> 2
            else -> 1
        }

        val directFirst = compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
        val preferHigher = directFirst
            .thenByDescending { codecRank(it.mimeType) }
            .thenByDescending { it.bitrate }
            .thenByDescending { it.audioSampleRate ?: 0 }
        val preferLower = directFirst
            .thenBy { it.bitrate }
            .thenByDescending { codecRank(it.mimeType) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val bpsCeiling = maxBitrateKbps * 1000
        return when {
            lowQuality -> formats.sortedWith(preferLower)
            bpsCeiling > 0 -> {
                val withinCeiling = formats.filter { it.bitrate <= bpsCeiling }.sortedWith(preferHigher)
                val aboveCeiling = formats.filter { it.bitrate > bpsCeiling }.sortedWith(preferLower)
                withinCeiling + aboveCeiling
            }
            else -> formats.sortedWith(preferHigher)
        }
    }

    private fun isBotDetectionReason(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            ("confirm" in lower && "not a" in lower) ||
            "not a robot" in lower
    }

    /**
     * ArchiveTune `repairAuthStateAfterBotDetection`: drop the poisoned BotGuard session so the
     * next request remints, and rotate visitorData. Returns the repaired auth state when it
     * actually changed.
     */
    private suspend fun repairPlaybackAuthAfterBotDetection(): PlaybackAuthState? {
        return try {
            com.unshoo.pixelmusic.utils.potoken.BotGuardTokenGenerator.invalidateAll()
            val refreshed = YouTube.visitorData().getOrNull()
            if (!refreshed.isNullOrBlank()) {
                YouTube.visitorData = refreshed
            }
            YouTube.currentPlaybackAuthState()
        } catch (_: Exception) {
            null
        }
    }

    private fun expiryFromStreamUrl(url: String): Long {
        val expireSeconds = url.substringAfter("expire=", "").substringBefore("&").toLongOrNull()
        val nowMs = System.currentTimeMillis()
        return if (expireSeconds != null) {
            expireSeconds * 1000L
        } else {
            nowMs + DEFAULT_STREAM_EXPIRE_SECONDS * 1000L
        }
    }


    /**
     * Normalises a raw MIME type string from the YouTube player response into a simple
     * ExoPlayer-compatible MIME type.
     *
     * Examples:
     *   "audio/webm; codecs=\"opus\""  →  "audio/opus"
     *   "audio/mp4; codecs=\"mp4a.40.2\""  →  "audio/mp4"
     *   "audio/webm; codecs=\"vorbis\""  →  "audio/webm"
     */
    private fun normalizeMimeType(rawMimeType: String): String {
        val lower = rawMimeType.lowercase(Locale.US)
        return when {
            lower.contains("opus") -> "audio/opus"
            lower.contains("mp4a") || lower.contains("mp4") || lower.contains("m4a") -> "audio/mp4"
            lower.contains("vorbis") -> "audio/ogg"
            lower.contains("webm") -> "audio/webm"
            else -> rawMimeType.substringBefore(";").trim()
        }
    }

    fun isSaavnUrl(url: String): Boolean =
        url.contains("saavncdn.com", ignoreCase = true) ||
        url.contains("jiosaavn.com", ignoreCase = true) ||
        url.contains("saavn", ignoreCase = true)

    private fun isUrlAllowed(url: String, isSaavnEnabled: Boolean): Boolean =
        isSaavnEnabled || !isSaavnUrl(url)

    private suspend fun isYoutubeUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
        // HOT PATH: never hit the network here. Cache revalidation must stay O(1).
        // Network probes belong in validateStatus during first resolve only.
        if (url.isBlank()) return@withContext false
        // Lossless-source URIs (Tidal/Qobuz CDN links, deezer://, tidal-dash://, flattened Apple
        // files) carry their own freshness window tracked by the resolver — never fall through to
        // the googlevideo `expire=` / host heuristics below, which would reject them.
        if (LosslessStreamResolver.isLosslessUri(url)) {
            return@withContext LosslessStreamResolver.isFreshUri(url)
        }
        if (!url.startsWith("http")) {
            // Local file path
            return@withContext java.io.File(url).exists()
        }
        val expireParam = url.substringAfter("expire=", "").substringBefore("&")
        if (expireParam.isNotEmpty()) {
            val expireTimeSecs = expireParam.toLongOrNull()
            if (expireTimeSecs != null) {
                val currentTimeSecs = System.currentTimeMillis() / 1000
                // Require at least 90s of remaining life so ExoPlayer can finish first buffer.
                return@withContext expireTimeSecs > currentTimeSecs + 90
            }
        }
        // No expire param (rare): treat as valid and let ExoPlayer / error recovery handle it.
        // Doing a HEAD/range probe here was a major low-connectivity stall source.
        return@withContext url.contains("googlevideo.com", ignoreCase = true) ||
            url.contains("youtube.com", ignoreCase = true) ||
            url.contains("ggpht.com", ignoreCase = true) ||
            url.contains("saavncdn.com", ignoreCase = true) ||
            url.contains("jiosaavn.com", ignoreCase = true)
    }

    fun findObjectsWithKey(element: JsonElement, key: String, result: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey(key)) {
                    element[key]?.jsonObject?.let { result.add(it) }
                }
                for (value in element.values) {
                    findObjectsWithKey(value, key, result)
                }
            }
            is JsonArray -> {
                for (value in element) {
                    findObjectsWithKey(value, key, result)
                }
            }
            else -> {}
        }
    }

    fun findContinuationToken(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("nextContinuationData")) {
                    return element["nextContinuationData"]?.jsonObject?.get("continuation")?.jsonPrimitive?.contentOrNull
                }
                if (element.containsKey("continuationEndpoint")) {
                    return element["continuationEndpoint"]?.jsonObject?.get("continuationCommand")?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
                }
                for (value in element.values) {
                    val token = findContinuationToken(value)
                    if (token != null) return token
                }
            }
            is JsonArray -> {
                for (value in element) {
                    val token = findContinuationToken(value)
                    if (token != null) return token
                }
            }
            else -> {}
        }
        return null
    }

    fun extractAccountPlaylists(
        jsonString: String,
        settings: PixelMusicSettings
    ): List<PlaylistItem> {
        val root = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        findObjectsWithKey(root, "musicTwoRowItemRenderer", items)
        findObjectsWithKey(root, "musicResponsiveListItemRenderer", items)

        val playlistsList = mutableListOf<PlaylistItem>()
        for (item in items) {
            val title = item["title"]
                ?.jsonObject?.get("runs")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.contentOrNull ?: continue

            val browseId = item["navigationEndpoint"]
                ?.jsonObject?.get("browseEndpoint")
                ?.jsonObject?.get("browseId")
                ?.jsonPrimitive?.contentOrNull ?: continue

            if (browseId == "SE") continue

            val thumbnailUrl = item["thumbnailRenderer"]?.let { getBestThumbnailUrl(it) }
                ?: item["thumbnail"]?.let { getBestThumbnailUrl(it) }

            playlistsList.add(PlaylistItem(id = browseId, title = title, thumbnailUrl = thumbnailUrl))
        }

        val continuationToken = findContinuationToken(root)
        if (continuationToken != null) {
            try {
                val nextJson = YoutubeRequestHelper.requestContinuation(continuationToken, settings)
                playlistsList.addAll(extractAccountPlaylists(nextJson, settings))
            } catch (e: Exception) {
                PixelMusicHelper.printe("Error fetching playlists continuation: ${e.message}")
            }
        }

        return playlistsList.distinctBy { it.id }
    }

    fun extractAccountAlbums(
        jsonString: String,
        settings: PixelMusicSettings
    ): List<AlbumItem> {
        val root = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        findObjectsWithKey(root, "musicTwoRowItemRenderer", items)
        findObjectsWithKey(root, "musicResponsiveListItemRenderer", items)

        val albumsList = mutableListOf<AlbumItem>()
        for (item in items) {
            val title = item["title"]
                ?.jsonObject?.get("runs")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.contentOrNull ?: continue

            val browseId = item["navigationEndpoint"]
                ?.jsonObject?.get("browseEndpoint")
                ?.jsonObject?.get("browseId")
                ?.jsonPrimitive?.contentOrNull ?: continue

            val thumbnailUrl = item["thumbnailRenderer"]?.let { getBestThumbnailUrl(it) }
                ?: item["thumbnail"]?.let { getBestThumbnailUrl(it) }

            val subtitleRuns = item["subtitle"]?.jsonObject?.get("runs")?.jsonArray
            val artist = if (subtitleRuns != null) {
                val filterWords = setOf("album", "ep", "single", "playlist", "artist", "•", "·", " ")
                subtitleRuns.mapNotNull { 
                    it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                }.firstOrNull { runText ->
                    runText.trim().lowercase() !in filterWords && runText.trim().isNotEmpty()
                }
            } else null

            albumsList.add(AlbumItem(id = browseId, title = title, artist = artist, thumbnailUrl = thumbnailUrl))
        }

        val continuationToken = findContinuationToken(root)
        if (continuationToken != null) {
            try {
                val nextJson = YoutubeRequestHelper.requestContinuation(continuationToken, settings)
                albumsList.addAll(extractAccountAlbums(nextJson, settings))
            } catch (e: Exception) {
                PixelMusicHelper.printe("Error fetching albums continuation: ${e.message}")
            }
        }

        return albumsList.distinctBy { it.id }
    }

    fun extractAccountArtists(
        jsonString: String,
        settings: PixelMusicSettings
    ): List<ArtistItem> {
        val root = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        findObjectsWithKey(root, "musicTwoRowItemRenderer", items)
        findObjectsWithKey(root, "musicResponsiveListItemRenderer", items)

        val artistsList = mutableListOf<ArtistItem>()
        for (item in items) {
            val title = item["title"]
                ?.jsonObject?.get("runs")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.contentOrNull ?: continue

            val browseId = item["navigationEndpoint"]
                ?.jsonObject?.get("browseEndpoint")
                ?.jsonObject?.get("browseId")
                ?.jsonPrimitive?.contentOrNull ?: continue

            val thumbnailUrl = item["thumbnailRenderer"]?.let { getBestThumbnailUrl(it) }
                ?: item["thumbnail"]?.let { getBestThumbnailUrl(it) }

            artistsList.add(ArtistItem(id = browseId, name = title, thumbnailUrl = thumbnailUrl))
        }

        val continuationToken = findContinuationToken(root)
        if (continuationToken != null) {
            try {
                val nextJson = YoutubeRequestHelper.requestContinuation(continuationToken, settings)
                artistsList.addAll(extractAccountArtists(nextJson, settings))
            } catch (e: Exception) {
                PixelMusicHelper.printe("Error fetching artists continuation: ${e.message}")
            }
        }

        return artistsList.distinctBy { it.id }
    }
}

enum class SongInfoType(val index: Int) {
    TITLE(0),
    ARTIST(1),
}

@Serializable
data class PlaylistItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String?
)

@Serializable
data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?
)

@Serializable
data class ArtistItem(
    val id: String,
    val name: String,
    val thumbnailUrl: String?
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface YoutubeHelperEntryPoint {
    fun connectivityStateHolder(): ConnectivityStateHolder
    fun userPreferencesRepository(): UserPreferencesRepository
}
