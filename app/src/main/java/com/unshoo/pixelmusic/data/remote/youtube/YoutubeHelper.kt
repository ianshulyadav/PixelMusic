package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.util.LruCache
import android.widget.Toast
import androidx.core.net.toUri
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.model.youtube.UmihiSettings
import com.unshoo.pixelmusic.data.preferences.StreamingAudioQuality
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import java.io.File
import java.util.Locale
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_REMIX
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.IOS
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.MOBILE
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.TVHTML5
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import unshoo.ianshulyadav.pixelmusic.innertube.utils.StreamClientUtils
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
import unshoo.ianshulyadav.pixelmusic.innertube.PlaybackAuthState
import unshoo.ianshulyadav.pixelmusic.innertube.models.response.PlayerResponse
import com.unshoo.pixelmusic.data.preferences.PlayerStreamClient
import java.util.concurrent.ConcurrentHashMap


object YoutubeHelper {
    val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build()

    /**
     * LRU cache for resolved YouTube stream URLs.
     * Key format: "<videoId>_low" or "<videoId>_high".
     * Holds up to 100 entries; expired/invalid entries are evicted lazily on next access.
     */
    val streamUrlLruCache = LruCache<String, String>(200)

    /** Register a locally-available file path for a YouTube video ID so playback is instant. */
    private val localFilePathCache = LruCache<String, String>(200)

    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()
    val playbackTrackingCache = ConcurrentHashMap<String, String>()
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    @Volatile private var lastSuccessfulClientKey: String? = null

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
            UmihiHelper.printe("Failed to extract genre: ${e.message}")
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
        if (url.isBlank()) return url
        val resizeRegex = Regex("=w\\d+-h\\d+.*")
        if (resizeRegex.containsMatchIn(url)) {
            return url.replace(resizeRegex, "=w1000-h1000")
        }
        val sRegex = Regex("=s\\d+.*")
        if (sRegex.containsMatchIn(url)) {
            return url.replace(sRegex, "=s1000")
        }
        if (url.contains("googleusercontent.com")) {
            return if (url.contains("=")) {
                url.substringBeforeLast("=") + "=w1000-h1000"
            } else {
                "$url=w1000-h1000"
            }
        }
        return url
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
        settings: UmihiSettings
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
                    ?.jsonPrimitive?.contentOrNull ?: return@forEach

                val thumbnailUrl =
                    getBestThumbnailUrl(playlistRenderer["thumbnailRenderer"] ?: return@forEach)

                playlistInfos.add(
                    PlaylistInfo(id = browseId, title = title, coverHref = thumbnailUrl)
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

            playlistInfos.add(
                PlaylistInfo(id = browseId, title = title, coverHref = thumbnailUrl)
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
            UmihiHelper.printe("extractRelatedSongs failed: ${e.message}")
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

    fun extractSongList(jsonString: String, settings: UmihiSettings): List<Song> {
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

    fun extractContinuationSongs(jsonString: String, settings: UmihiSettings): List<Song> {
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
        settings: UmihiSettings
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
    private suspend fun getTargetBitrateCeiling(context: Context): Int {
        return try {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                YoutubeHelperEntryPoint::class.java
            )
            val connectivityStateHolder = entryPoint.connectivityStateHolder()
            val userPreferencesRepository = entryPoint.userPreferencesRepository()

            val isMetered = connectivityStateHolder.isMeteredNetwork.value
            val forceHigh = userPreferencesRepository.forceHighQualityOnMobileFlow.first()

            val targetQuality = if (isMetered && !forceHigh) {
                userPreferencesRepository.streamingAudioQualityMobileFlow.first()
            } else {
                userPreferencesRepository.streamingAudioQualityWifiFlow.first()
            }
            if (targetQuality == StreamingAudioQuality.HIGH) 0 else targetQuality.maxBitrateKbps
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getSongPlayerUrl(
        context: Context,
        song: Song,
        allowLocal: Boolean = false
    ): String {
        val videoId = song.youtubeId

        if (song.audioFilePath?.isNotBlank() == true && File(song.audioFilePath).exists()) {
            UmihiHelper.printd("$videoId : Playing directly from song.audioFilePath: ${song.audioFilePath}")
            return song.audioFilePath
        }

        // ── OFFLINE-FIRST GATE ─────────────────────────────────────────────────
        // Check in-memory local path cache (populated by downloads/workers)
        val cachedLocalPath = localFilePathCache.get(videoId)
        if (cachedLocalPath != null && File(cachedLocalPath).exists()) {
            UmihiHelper.printd("$videoId : Playing from in-memory local file cache")
            return cachedLocalPath
        }
        // ──────────────────────────────────────────────────────────────────────

        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        var savedSong: Song? = null
        try {
            savedSong = localSongRepository.getSong(videoId)
        } catch (ex: Exception) {
            Toast.makeText(context, "Failed to get song from local repository", Toast.LENGTH_LONG)
                .show()
            UmihiHelper.printe(ex.toString())
        }

        val maxBitrate = getTargetBitrateCeiling(context)
        val cacheKey = if (maxBitrate > 0) "${videoId}_q$maxBitrate" else "${videoId}_high"

        if (savedSong != null) {
            // Always prefer local file — not just when allowLocal is true
            if (savedSong.audioFilePath != null && File(savedSong.audioFilePath).exists()) {
                UmihiHelper.printd("$videoId : Was downloaded, playing from local file")
                // Populate in-memory cache for next time
                localFilePathCache.put(videoId, savedSong.audioFilePath)
                return savedSong.audioFilePath
            }

            // Check quality-specific LRU cache
            val cachedQuality = streamUrlLruCache.get(cacheKey)
            if (cachedQuality != null) {
                UmihiHelper.printd("$videoId : Got quality-specific URL from LRU cache")
                return cachedQuality
            }

            // Check high-quality LRU cache
            if (maxBitrate == 0 || maxBitrate >= 256) {
                val cachedHigh = streamUrlLruCache.get("${videoId}_high")
                if (cachedHigh != null) {
                    UmihiHelper.printd("$videoId : Got high-quality URL from LRU cache")
                    return cachedHigh
                }
            }

            if (savedSong.streamUrl != null) {
                if (isYoutubeUrlValid(savedSong.streamUrl)) {
                    UmihiHelper.printd("$videoId : Got url from saved")
                    streamUrlLruCache.put(cacheKey, savedSong.streamUrl)
                    if (maxBitrate == 0 || maxBitrate >= 256) {
                        streamUrlLruCache.put("${videoId}_high", savedSong.streamUrl)
                    }
                    return savedSong.streamUrl
                }
                UmihiHelper.printd("$videoId : Saved url was invalid")
            }
        }

        val newUri = getSongUrlFromYoutube(context, song, lowQuality = false, maxBitrateKbps = maxBitrate)
        streamUrlLruCache.put(cacheKey, newUri)
        if (maxBitrate == 0 || maxBitrate >= 256) {
            streamUrlLruCache.put("${videoId}_high", newUri)
        }
        localSongRepository.setStreamUrl(songId = videoId, streamUrl = newUri)
        UmihiHelper.printd("$videoId : Got quality-specific url from YouTube and saved song")
        return newUri
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

        // LRU cache hit
        streamUrlLruCache.get("${videoId}_low")?.let { return it }
        // If high-quality is already cached, use it immediately (better than re-resolving)
        streamUrlLruCache.get("${videoId}_high")?.let { return it }

        val lowUrl = getSongUrlFromYoutube(context, song, lowQuality = true)
        streamUrlLruCache.put("${videoId}_low", lowUrl)
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
        streamUrlLruCache.get(cacheKey)?.let { return it }

        val highUrl = getSongUrlFromYoutube(context, song, lowQuality = false, maxBitrateKbps = maxBitrate)
        streamUrlLruCache.put(cacheKey, highUrl)
        if (maxBitrate == 0 || maxBitrate >= 256) {
            streamUrlLruCache.put("${videoId}_high", highUrl)
        }
        return highUrl
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
        maxBitrateKbps: Int = 0
    ): String {
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

        // LRU cache check
        val cacheKey = if (maxBitrateKbps > 0) "${videoId}_q${maxBitrateKbps}" else "${videoId}_high"
        streamUrlLruCache.get(cacheKey)?.let { return it }

        val url = getSongUrlFromYoutube(context, song, lowQuality = false, maxBitrateKbps = maxBitrateKbps)
        streamUrlLruCache.put(cacheKey, url)
        return url
    }

    /** Invalidate a cached stream URL (e.g. after the remote URL expires). */
    fun invalidateStreamCache(youtubeId: String) {
        streamUrlLruCache.remove("${youtubeId}_low")
        streamUrlLruCache.remove("${youtubeId}_high")
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

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        IOS,
        MOBILE,
        ANDROID_MUSIC,
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_43_32,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        WEB,
        WEB_CREATOR,
        WEB_REMIX
    )

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
            UmihiHelper.printd("Skipping ciphered ${client.clientName} stream candidate because Web PoToken playback is enabled but no GVS token is available")
            return true
        }
        return false
    }

    private fun isStreamClientTemporarilyBlocked(
        videoId: String,
        clientKey: String?,
        authFingerprint: String,
    ): Boolean {
        val normalizedClientKey = StreamClientUtils.normalizeClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false
        val key = "$authFingerprint:$videoId:$normalizedClientKey"
        val until = failedStreamClientsUntil[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(key)
            return false
        }
        return true
    }

    private fun markStreamClientFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int,
        authFingerprint: String
    ) {
        if (httpStatusCode !in setOf(403, 404, 410, 416)) return
        val normalizedClientKey = StreamClientUtils.normalizeClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        val key = "$authFingerprint:$videoId:$normalizedClientKey"
        failedStreamClientsUntil[key] = System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
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
                STREAM_FALLBACK_CLIENTS.filter { it.loginSupported } + STREAM_FALLBACK_CLIENTS.filterNot { it.loginSupported }
            } else {
                STREAM_FALLBACK_CLIENTS.toList()
            }

        return buildList {
            lastSuccessfulClient?.let { add(it) }
            add(preferredYouTubeClient)
            addAll(orderedFallbackClients)
            if (preferredYouTubeClient != WEB_REMIX) add(WEB_REMIX)
            if (preferredStreamClient == PlayerStreamClient.WEB_REMIX) {
                addAll(STREAM_FALLBACK_CLIENTS)
            }
        }.distinct()
    }

    private fun buildPlaybackProbeRanges(): List<String> =
        listOf(
            "bytes=0-0",
        )

    private fun validateStatus(url: String): Boolean {
        UmihiHelper.printd("Validating stream URL status")
        try {
            val requestProfile = StreamClientUtils.resolveRequestProfile(url)
            val probeRanges = buildPlaybackProbeRanges()

            var sawReadableProbe = false
            for (range in probeRanges) {
                val rangeRequest = StreamClientUtils
                    .applyRequestProfile(
                        okhttp3.Request.Builder()
                            .get()
                            .header("Range", range)
                            .url(url),
                        requestProfile
                    ).build()
                val streamProxy = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.streamProxy
                val httpClient = if (streamProxy != null) {
                    OkHttpClient.Builder()
                        .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
                        .proxy(streamProxy)
                        .build()
                } else {
                    client
                }
                val probeValid =
                    httpClient.newCall(rangeRequest).execute().use { response ->
                        val code = response.code
                        if (code == 403) return@use false
                        if (code !in 200..399 && code != 416) return@use false
                        if (code == 416) return@use sawReadableProbe

                        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
                        if (
                            contentType.startsWith("text/html") ||
                            contentType.startsWith("text/plain") ||
                            contentType.startsWith("application/json") ||
                            contentType.startsWith("application/xml") ||
                            contentType.startsWith("text/xml")
                        ) {
                            UmihiHelper.printd("Rejecting stream probe because it returned non-media content-type: $contentType")
                            return@use false
                        }

                        val readable = response.body.source().request(1) == true
                        if (readable) {
                            sawReadableProbe = true
                        }
                        readable
                    }
                if (!probeValid) return false
            }

            return true
        } catch (e: Exception) {
            UmihiHelper.printe("Stream URL validation failed with exception: ${e.message}")
        }
        return false
    }

    /**
     * Resolves a stream URL from YouTube.
     * @param lowQuality If true, picks the lowest-bitrate audio stream for fastest startup.
     *                   If false (default), picks the highest bitrate for best quality.
     * @param maxBitrateKbps If > 0, caps the selected stream to this bitrate ceiling (in kbps).
     *                       Picks the highest bitrate stream that doesn't exceed the ceiling.
     *                       If no stream is within the ceiling, falls back to the lowest available.
     */
    private fun selectCandidates(
        playerResponse: PlayerResponse,
        lowQuality: Boolean,
        maxBitrateKbps: Int
    ): List<PlayerResponse.StreamingData.Format> {
        val formats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { 
                it.mimeType.contains("audio", ignoreCase = true) && 
                it.bitrate > 0 &&
                !it.mimeType.contains("mp3", ignoreCase = true) &&
                !it.mimeType.contains("mpeg", ignoreCase = true) &&
                !it.mimeType.contains("mpga", ignoreCase = true)
            }
            .orEmpty()
        if (formats.isEmpty()) return emptyList()

        val webmFormats = formats.filter { it.mimeType.contains("webm", ignoreCase = true) }
        val m4aFormats = formats.filter { it.mimeType.contains("mp4", ignoreCase = true) || it.mimeType.contains("m4a", ignoreCase = true) || it.mimeType.contains("mp4a", ignoreCase = true) }
        val opusFormats = formats.filter { it.mimeType.contains("opus", ignoreCase = true) && !it.mimeType.contains("webm", ignoreCase = true) }

        fun sortGroup(group: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
            if (group.isEmpty()) return emptyList()
            return when {
                lowQuality -> group.sortedBy { it.bitrate }
                maxBitrateKbps > 0 -> {
                    val bpsCeiling = maxBitrateKbps * 1000
                    val withinCeiling = group.filter { it.bitrate <= bpsCeiling }
                    if (withinCeiling.isNotEmpty()) {
                        withinCeiling.sortedByDescending { it.bitrate }
                    } else {
                        group.sortedBy { it.bitrate }
                    }
                }
                else -> group.sortedByDescending { it.bitrate }
            }
        }

        return sortGroup(webmFormats) + sortGroup(m4aFormats) + sortGroup(opusFormats)
    }

    /**
     * Resolves a stream URL from YouTube using premium client fallbacks and validation ranges.
     */
    private suspend fun getSongUrlFromYoutube(
        context: Context,
        song: Song,
        retries: Int = Constants.YoutubeApi.RETRY_COUNT,
        lowQuality: Boolean = false,
        maxBitrateKbps: Int = 0
    ): String {
        val videoId = song.youtubeId

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, YoutubeHelperEntryPoint::class.java)
        val preferredClient = entryPoint.userPreferencesRepository().playerStreamClientFlow.first()
        var authState = YouTube.currentPlaybackAuthState()

        val clients = buildStreamClientOrder(preferredClient, authState).filterNot { client ->
            isStreamClientTemporarilyBlocked(videoId, client.clientName, authState.fingerprint)
        }

        var signatureTimestamp: Int? = null
        try {
            signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        } catch (e: Exception) {
            UmihiHelper.printe("Failed to get signature timestamp: ${e.message}")
        }

        var didRefreshVisitorData = false

        for (clientObj in clients) {
            try {
                UmihiHelper.printd("Trying playback client: ${clientObj.clientName}")
                var playerResResult = YouTube.player(
                    videoId = videoId,
                    playlistId = null,
                    client = clientObj,
                    signatureTimestamp = signatureTimestamp,
                    setLogin = authState.hasPlaybackLoginContext,
                    authState = authState
                )

                var playerResponse = playerResResult.getOrNull()
                if (playerResponse == null) {
                    UmihiHelper.printe("Player response was null for client ${clientObj.clientName}")
                    continue
                }

                var status = playerResponse.playabilityStatus.status
                var reason = playerResponse.playabilityStatus.reason.orEmpty()
                val isBot = "bot" in reason.lowercase(Locale.US) || "unusual traffic" in reason.lowercase(Locale.US) || "automated" in reason.lowercase(Locale.US)

                if (status != "OK" && isBot && !didRefreshVisitorData) {
                    UmihiHelper.printd("Bot detection triggered. Refreshing visitorData...")
                    val refreshedVisitorData = YouTube.visitorData().getOrNull()
                    if (!refreshedVisitorData.isNullOrBlank()) {
                        YouTube.visitorData = refreshedVisitorData
                        authState = authState.copy(visitorData = refreshedVisitorData).normalized()
                        didRefreshVisitorData = true

                        playerResResult = YouTube.player(
                            videoId = videoId,
                            playlistId = null,
                            client = clientObj,
                            signatureTimestamp = signatureTimestamp,
                            setLogin = authState.hasPlaybackLoginContext,
                            authState = authState
                        )
                        playerResponse = playerResResult.getOrNull()
                        if (playerResponse != null) {
                            status = playerResponse.playabilityStatus.status
                            reason = playerResponse.playabilityStatus.reason.orEmpty()
                        }
                    }
                }

                if (playerResponse == null || status != "OK") {
                    UmihiHelper.printe("Playability check failed for client ${clientObj.clientName}: status=$status, reason=$reason")
                    continue
                }

                val candidates = selectCandidates(playerResponse, lowQuality, maxBitrateKbps)
                if (candidates.isEmpty()) {
                    UmihiHelper.printe("No audio formats found for client ${clientObj.clientName}")
                    continue
                }

                var resolvedUrl: String? = null
                for (candidate in candidates) {
                    if (shouldSkipCipheredWebCandidate(clientObj, candidate, authState)) continue
                    val deobfuscated = NewPipeUtils.getStreamUrl(candidate, videoId, clientObj, authState).getOrNull() ?: continue
                    val patched = StreamClientUtils.patchClientVersion(deobfuscated, clientObj.clientVersion)
                    
                    if (validateStatus(patched)) {
                        resolvedUrl = patched
                        lastSuccessfulClientKey = StreamClientUtils.buildClientKey(clientObj)
                        UmihiHelper.printd("Successfully validated stream URL with client: ${clientObj.clientName}")
                        break
                    } else {
                        UmihiHelper.printe("Stream URL validation failed for client ${clientObj.clientName}")
                    }
                }

                if (resolvedUrl != null) {
                    playerResponse.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.let { baseUrl ->
                        playbackTrackingCache[videoId] = baseUrl
                    }
                    return resolvedUrl
                }

            } catch (e: Exception) {
                UmihiHelper.printe("Error with client ${clientObj.clientName}: ${e.message}")
            }
        }

        // Failsafe: Original NewPipe Extractor fallback
        UmihiHelper.printd("All premium stream clients failed. Using failsafe NewPipe extractor...")
        val service = ServiceList.YouTube
        var attempts = 0
        repeat(retries) { attempt ->
            try {
                attempts++
                val streamUrl = withContext(Dispatchers.IO) {
                    val extractor = service.getStreamExtractor(song.youtubeUrl)
                    extractor.fetchPage()
                    val streams = extractor.audioStreams.filter { stream ->
                        val suffix = stream.format?.suffix?.lowercase().orEmpty()
                        val name = stream.format?.name?.lowercase().orEmpty()
                        !suffix.contains("mp3") && !suffix.contains("mpeg") && !suffix.contains("mpga") &&
                        !name.contains("mp3") && !name.contains("mpeg") && !name.contains("mpga")
                    }
                    val webmStreams = streams.filter { stream ->
                        val suffix = stream.format?.suffix?.lowercase().orEmpty()
                        val name = stream.format?.name?.lowercase().orEmpty()
                        suffix.contains("webm") || name.contains("webm")
                    }
                    val m4aStreams = streams.filter { stream ->
                        val suffix = stream.format?.suffix?.lowercase().orEmpty()
                        val name = stream.format?.name?.lowercase().orEmpty()
                        suffix.contains("m4a") || name.contains("m4a") || suffix.contains("mp4") || name.contains("mp4")
                    }
                    val otherStreams = streams.filter { stream ->
                        val suffix = stream.format?.suffix?.lowercase().orEmpty()
                        val name = stream.format?.name?.lowercase().orEmpty()
                        (suffix.contains("opus") || name.contains("opus")) &&
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

                    val orderedStreams = sortNewPipeGroup(webmStreams) + sortNewPipeGroup(m4aStreams) + sortNewPipeGroup(otherStreams)
                    val selectedStream = orderedStreams.firstOrNull() ?: streams.firstOrNull() ?: throw Exception("No audio streams found after filtering")
                    selectedStream.content
                }
                return streamUrl
            } catch (e: Exception) {
                UmihiHelper.printe("Failsafe NewPipe extraction failed: ${e.message}")
                delay(Constants.YoutubeApi.RETRY_DELAY * (attempt + 1))
            }
        }

        throw Exception("Fatal fail for song ${song.youtubeId}. Could not get it after $attempts failsafe attempts")
    }

    private suspend fun isYoutubeUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fast check: Extract expire timestamp from the URL if present
            val expireParam = url.substringAfter("expire=", "").substringBefore("&")
            if (expireParam.isNotEmpty()) {
                val expireTimeSecs = expireParam.toLongOrNull()
                if (expireTimeSecs != null) {
                    val currentTimeSecs = System.currentTimeMillis() / 1000
                    // If the URL expires in more than 10 minutes, treat it as valid immediately!
                    if (expireTimeSecs > currentTimeSecs + 600) {
                        return@withContext true
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            val streamProxy = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.streamProxy
            val httpClient = if (streamProxy != null) {
                OkHttpClient.Builder()
                    .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
                    .proxy(streamProxy)
                    .build()
            } else {
                client
            }
            httpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (_: Exception) {
            return@withContext false
        }
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
        settings: UmihiSettings
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
                UmihiHelper.printe("Error fetching playlists continuation: ${e.message}")
            }
        }

        return playlistsList.distinctBy { it.id }
    }

    fun extractAccountAlbums(
        jsonString: String,
        settings: UmihiSettings
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
                UmihiHelper.printe("Error fetching albums continuation: ${e.message}")
            }
        }

        return albumsList.distinctBy { it.id }
    }

    fun extractAccountArtists(
        jsonString: String,
        settings: UmihiSettings
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
                UmihiHelper.printe("Error fetching artists continuation: ${e.message}")
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
