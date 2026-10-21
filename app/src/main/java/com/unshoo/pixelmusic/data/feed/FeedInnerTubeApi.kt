package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality
import com.unshoo.pixelmusic.utils.ContentFilterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistAlbumItem(
    val title: String,
    val browseId: String,
    val year: String? = null,
    val type: String? = null,
    val artworkUrl: String? = null,
)

data class ArtistPageData(
    val albums: List<ArtistAlbumItem> = emptyList(),
    val singles: List<ArtistAlbumItem> = emptyList(),
)

data class AlbumPageData(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val songs: List<YouTubeMusicTrack> = emptyList(),
)

@Singleton
class FeedInnerTubeApi @Inject constructor(
    private val http: OkHttpClient,
    private val ytAuth: FeedYtAuthBridge,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val configMutex = Mutex()
    @Volatile private var webConfig: WebConfig? = null

    private data class WebConfig(
        val apiKey: String,
        val clientVersion: String,
        val visitorData: String?,
    )

    private suspend fun getWebConfig(): WebConfig {
        webConfig?.let { return it }
        return configMutex.withLock {
            webConfig?.let { return@withLock it }
            val config = runCatching { fetchWebConfig() }
                .getOrElse { WebConfig(FALLBACK_WEB_KEY, FALLBACK_WEB_VERSION, null) }
            webConfig = config
            config
        }
    }

    private suspend fun fetchWebConfig(): WebConfig {
        val request = Request.Builder()
            .url("$YOUTUBE_MUSIC_ORIGIN/")
            .header("User-Agent", WEB_USER_AGENT)
            .build()
        val (status, html) = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        }
        if (status !in 200..299) throw IOException("YouTube Music config HTTP $status")
        return WebConfig(
            apiKey = findConfig(html, "INNERTUBE_API_KEY") ?: FALLBACK_WEB_KEY,
            clientVersion = findConfig(html, "INNERTUBE_CONTEXT_CLIENT_VERSION") ?: FALLBACK_WEB_VERSION,
            visitorData = findConfig(html, "VISITOR_DATA"),
        )
    }

    private fun findConfig(html: String, key: String): String? {
        if (html.isBlank()) return null
        val escaped = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(html)?.groupValues?.getOrNull(1)
        return escaped
            ?.replace("\\u003d", "=")
            ?.replace("\\x3d", "=")
            ?.replace("\\/", "/")
    }

    private suspend fun post(
        url: String,
        body: JsonObject,
        authenticated: Boolean = false,
        callTimeoutMs: Long? = null,
    ): JsonObject {
        val config = getWebConfig()
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Origin", YOUTUBE_MUSIC_ORIGIN)
            .header("X-Origin", YOUTUBE_MUSIC_ORIGIN)
            .header("Referer", "$YOUTUBE_MUSIC_ORIGIN/")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", config.clientVersion)

        if (!config.visitorData.isNullOrBlank()) {
            builder.header("X-Goog-Visitor-Id", config.visitorData)
        }

        if (authenticated) {
            val account = ytAuth.connection.value
            ytAuth.cookieHeaderValue(account)?.let { builder.header("Cookie", it) }
            ytAuth.authorizationHeaderValue(account = account)?.let { builder.header("Authorization", it) }
        }

        val request = builder
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        currentCoroutineContext().ensureActive()
        val call = http.newCall(request)
        callTimeoutMs?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        val (status, text) = withContext(Dispatchers.IO) {
            call.execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        }
        if (status !in 200..299) {
            if (status == 400 || status == 403 || status == 429) webConfig = null
            throw IOException("InnerTube HTTP $status")
        }
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IOException("Malformed InnerTube JSON response", e)
        }
    }

    private suspend fun browseRoot(browseId: String, authenticated: Boolean, params: String? = null): JsonObject {
        val config = getWebConfig()
        return post(
            url = "$MUSIC_API/browse?key=${config.apiKey}&prettyPrint=false",
            body = buildJsonObject {
                put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
                put("browseId", browseId)
                if (!params.isNullOrBlank()) {
                    put("params", params)
                }
            },
            authenticated = authenticated,
        )
    }

    private suspend fun browseContinuation(token: String, authenticated: Boolean): JsonObject {
        val config = getWebConfig()
        return post(
            url = "$MUSIC_API/browse?key=${config.apiKey}&prettyPrint=false",
            body = buildJsonObject {
                put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
                put("continuation", token)
            },
            authenticated = authenticated,
        )
    }

    private fun makeContext(clientName: String, clientVersion: String, visitorData: String?): JsonObject {
        val currentLocale = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.locale
        val gl = currentLocale.gl.ifBlank { "IN" }
        val hl = currentLocale.hl.ifBlank { "en" }
        return buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", hl)
                put("gl", gl)
                if (!visitorData.isNullOrBlank()) {
                    put("visitorData", visitorData)
                }
            })
        }
    }

    suspend fun fetchNewReleases(authenticated: Boolean = ytAuth.connection.value.isConnected): List<YouTubePlaylistSummary> = withContext(Dispatchers.IO) {
        val items = runCatching {
            val root = browseRoot(YT_NEW_RELEASES_BROWSE_ID, authenticated = authenticated)
            parsePlaylistRenderers(root)
        }.getOrDefault(emptyList())

        if (items.isNotEmpty()) return@withContext items

        runCatching {
            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.newReleaseAlbums().getOrNull()?.map { album ->
                YouTubePlaylistSummary(
                    id = album.browseId,
                    title = album.title,
                    author = album.artists?.firstOrNull()?.name ?: "Album",
                    artworkUrl = album.thumbnail
                )
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchCharts(): List<YouTubeMusicTrack> = withContext(Dispatchers.IO) {
        val tracksWithParams = runCatching {
            val root = browseRoot(YT_CHARTS_BROWSE_ID, authenticated = false, params = "ggMGCgQIgAQ%3D")
            parseSongRenderers(root)
        }.getOrDefault(emptyList())
        if (tracksWithParams.isNotEmpty()) return@withContext tracksWithParams

        val tracksRegular = runCatching {
            val root = browseRoot(YT_CHARTS_BROWSE_ID, authenticated = false)
            parseSongRenderers(root)
        }.getOrDefault(emptyList())
        if (tracksRegular.isNotEmpty()) return@withContext tracksRegular

        runCatching {
            val currentGl = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.locale.gl.ifBlank { "IN" }
            val chartsPage = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.getChartsPage(currentGl).getOrNull()
            chartsPage?.sections?.flatMap { it.items }
                ?.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()
                ?.map { songItem ->
                    val musicVideoType = songItem.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    val isVideo = (musicVideoType != null && musicVideoType != unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV) ||
                        ContentFilterUtils.isVideoTitle(songItem.title)
                    YouTubeMusicTrack(
                        videoId = songItem.id,
                        title = songItem.title,
                        artist = songItem.artists.firstOrNull()?.name ?: "Unknown artist",
                        album = songItem.album?.name,
                        artworkUrl = songItem.thumbnail,
                        isVideo = isVideo
                    )
                }.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchHomeMixes(): List<YouTubePlaylistSummary> = withContext(Dispatchers.IO) {
        val isAuth = ytAuth.connection.value.isConnected
        runCatching {
            val root = browseRoot(YT_HOME_BROWSE_ID, authenticated = isAuth)
            parsePlaylistRenderers(root)
        }.getOrDefault(emptyList())
    }

    suspend fun fetchHomeSongs(): List<YouTubeMusicTrack> = withContext(Dispatchers.IO) {
        val isAuth = ytAuth.connection.value.isConnected
        runCatching {
            val root = browseRoot(YT_HOME_BROWSE_ID, authenticated = isAuth)
            parseHomeFeedSongs(root)
        }.getOrDefault(emptyList())
    }

    suspend fun fetchHomeAlbums(limit: Int = 12): List<FeedAlbum> = withContext(Dispatchers.IO) {
        val isAuth = ytAuth.connection.value.isConnected
        val homeAlbums = runCatching {
            val root = browseRoot(YT_HOME_BROWSE_ID, authenticated = isAuth)
            parseHomeAlbums(root, limit)
        }.getOrDefault(emptyList())

        if (homeAlbums.isNotEmpty()) return@withContext homeAlbums

        runCatching {
            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.newReleaseAlbums().getOrNull()?.take(limit)?.map { album ->
                FeedAlbum(
                    title = album.title,
                    artist = album.artists?.firstOrNull()?.name ?: "Unknown artist",
                    artworkUrl = album.thumbnail,
                    browseId = album.browseId
                )
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchTasteSignals(
        recentLimit: Int = 30,
        likedLimit: Int = 24,
        feedLimit: Int = 40,
    ): YtMusicTasteSignals = withContext(Dispatchers.IO) {
        if (!ytAuth.connection.value.isConnected) return@withContext YtMusicTasteSignals()
        coroutineScope {
            val recent = async {
                runCatching { parseSongRenderers(browseRoot(YT_HISTORY_BROWSE_ID, authenticated = true)) }
                    .getOrDefault(emptyList())
                    .distinctBy { it.videoId }
                    .take(recentLimit.coerceIn(0, 50))
            }
            val liked = async {
                runCatching { parseSongRenderers(browseRoot(YT_LIKED_BROWSE_ID, authenticated = true)) }
                    .getOrDefault(emptyList())
                    .distinctBy { it.videoId }
                    .take(likedLimit.coerceIn(0, 50))
            }
            val feed = async {
                runCatching {
                    parseHomeFeedSongs(browseRoot(YT_HOME_BROWSE_ID, authenticated = true))
                }
                    .getOrDefault(emptyList())
                    .distinctBy { it.videoId }
                    .take(feedLimit.coerceIn(0, 60))
            }
            YtMusicTasteSignals(
                recentTracks = recent.await(),
                likedTracks = liked.await(),
                feedTracks = feed.await(),
            )
        }
    }

    suspend fun fetchRelatedSongs(
        videoId: String,
        limit: Int = 30,
        prefetchStreams: Boolean = false,
    ): List<YouTubeMusicTrack> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || limit <= 0) return@withContext emptyList()
        val config = getWebConfig()
        val root = post(
            url = "$MUSIC_API/next?key=${config.apiKey}&prettyPrint=false",
            body = buildJsonObject {
                put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("params", "wAEB")
                put("isAudioOnly", true)
            },
            callTimeoutMs = 8_000L,
        )
        parseSongRenderers(root)
            .filterNot { it.videoId == videoId }
            .take(limit)
    }

    suspend fun searchSongs(
        query: String,
        limit: Int = 30,
        prefetchStreams: Boolean = false,
    ): List<YouTubeMusicTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val config = getWebConfig()
        val body = buildJsonObject {
            put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
            put("query", query.trim())
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
        }
        val root = post(
            url = "$MUSIC_API/search?key=${config.apiKey}&prettyPrint=false",
            body = body,
        )
        parseSongRenderers(root).take(limit)
    }

    suspend fun searchArtists(query: String, limit: Int = 8): List<FeedArtist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val config = getWebConfig()
        val body = buildJsonObject {
            put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
            put("query", query.trim())
            put("params", "EgWKAQIgAWoKEAkQBRAKEAMQBA==")
        }
        val root = post(
            url = "$MUSIC_API/search?key=${config.apiKey}&prettyPrint=false",
            body = body,
        )
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicResponsiveListItemRenderer", renderers)
        renderers.mapNotNull { renderer ->
            val nav = renderer.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return@mapNotNull null
            val browseId = nav.string("browseId") ?: return@mapNotNull null
            if (!browseId.startsWith("UC")) return@mapNotNull null
            val columns = renderer.array("flexColumns")
            val name = columns?.getOrNull(0)?.asObject()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.array("runs")
                ?.joinToString("") { it.asObject()?.string("text").orEmpty() }
                ?.trim()?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } } ?: return@mapNotNull null
            val artworkUrl = extractArtwork(renderer)
            FeedArtist(name = name, browseId = browseId, artworkUrl = artworkUrl)
        }.distinctBy { it.name.trim().lowercase() }.take(limit)
    }

    suspend fun searchAlbums(query: String, limit: Int = 8): List<FeedAlbum> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val config = getWebConfig()
        val body = buildJsonObject {
            put("context", makeContext("WEB_REMIX", config.clientVersion, config.visitorData))
            put("query", query.trim())
            put("params", "EgWKAQIYAWoKEAkQBRAKEAMQBA==")
        }
        val root = post(
            url = "$MUSIC_API/search?key=${config.apiKey}&prettyPrint=false",
            body = body,
        )
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicResponsiveListItemRenderer", renderers)
        renderers.mapNotNull { renderer ->
            val nav = renderer.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return@mapNotNull null
            val browseId = nav.string("browseId") ?: return@mapNotNull null
            if (!browseId.startsWith("MPRE")) return@mapNotNull null
            val columns = renderer.array("flexColumns")
            val title = columns?.getOrNull(0)?.asObject()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.array("runs")
                ?.joinToString("") { it.asObject()?.string("text").orEmpty() }
                ?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val details = columns?.getOrNull(1)?.asObject()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.array("runs")
                ?.mapNotNull { it.asObject() }.orEmpty()
            val artist = details.firstOrNull { run ->
                run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
            }?.string("text") ?: "Unknown artist"
            val artworkUrl = extractArtwork(renderer)
            FeedAlbum(title = title, artist = artist, artworkUrl = artworkUrl, browseId = browseId)
        }.distinctBy { "${it.artist.trim().lowercase()}_${it.title.trim().lowercase()}" }.take(limit)
    }

    suspend fun fetchArtistPage(browseId: String, artistName: String): ArtistPageData? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null
        val root = runCatching { browseRoot(browseId, authenticated = false) }.getOrNull() ?: return@withContext null
        val twoRowItems = mutableListOf<JsonObject>()
        collectObjects(root, "musicTwoRowItemRenderer", twoRowItems)
        val albums = mutableListOf<ArtistAlbumItem>()
        val singles = mutableListOf<ArtistAlbumItem>()
        for (item in twoRowItems) {
            val title = item.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
                ?: item.obj("title")?.string("simpleText")
                ?: continue
            val nav = item.obj("navigationEndpoint")?.obj("browseEndpoint")
                ?: item.obj("title")?.array("runs")?.firstOrNull()?.asObject()?.obj("navigationEndpoint")?.obj("browseEndpoint")
            val itemBrowseId = nav?.string("browseId") ?: continue
            if (!itemBrowseId.startsWith("MPRE")) continue
            val subtitleRuns = item.obj("subtitle")?.array("runs")?.mapNotNull { it.asObject()?.string("text") }.orEmpty()
            val year = subtitleRuns.firstOrNull { it.trim().matches(Regex("^(19|20)\\d{2}$")) }
            val explicitType = subtitleRuns.firstOrNull { it.equals("Single", true) || it.equals("EP", true) || it.equals("Album", true) }
            val type = explicitType ?: if (itemBrowseId.startsWith("MPREb_")) "Album" else continue
            val artworkUrl = extractArtwork(item)
            val albumItem = ArtistAlbumItem(
                title = title.trim(),
                browseId = itemBrowseId,
                year = year,
                type = type,
                artworkUrl = artworkUrl
            )
            if (type.equals("Single", true) || type.equals("EP", true)) {
                singles.add(albumItem)
            } else {
                albums.add(albumItem)
            }
        }
        ArtistPageData(albums = albums.distinctBy { it.browseId }, singles = singles.distinctBy { it.browseId })
    }

    suspend fun fetchAlbumPage(browseId: String): AlbumPageData? = withContext(Dispatchers.IO) {
        if (browseId.isBlank()) return@withContext null
        val ytAlbum = runCatching { unshoo.ianshulyadav.pixelmusic.innertube.YouTube.album(browseId).getOrNull() }.getOrNull()
        if (ytAlbum != null) {
            val album = ytAlbum.album
            val albumArtist = album.artists?.firstOrNull()?.name ?: "Artist"
            val songs = ytAlbum.songs.map { s ->
                val trackArtist = s.artists.firstOrNull()?.name ?: albumArtist
                YouTubeMusicTrack(
                    videoId = s.id,
                    title = s.title,
                    artist = if (trackArtist.isBlank() || trackArtist == "Unknown artist") albumArtist else trackArtist,
                    album = s.album?.name ?: album.title,
                    artworkUrl = s.thumbnail?.takeIf { it.isNotBlank() } ?: album.thumbnail,
                    durationSeconds = s.duration
                )
            }
            return@withContext AlbumPageData(
                title = album.title,
                artist = albumArtist,
                artworkUrl = album.thumbnail,
                songs = songs
            )
        }
        val isAuth = ytAuth.connection.value.isConnected
        val root = runCatching { browseRoot(browseId, authenticated = isAuth) }.getOrNull()
            ?: runCatching { browseRoot(browseId, authenticated = false) }.getOrNull()
            ?: return@withContext null
        val rawSongs = parseSongRenderers(root)
        val header = root.obj("header")?.obj("musicDetailHeaderRenderer")
            ?: root.obj("header")?.obj("musicResponsiveHeaderRenderer")
        val title = header?.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?: header?.obj("title")?.string("simpleText")
            ?: rawSongs.firstOrNull()?.album
            ?: "Album"
        val artist = header?.obj("subtitle")?.array("runs")?.firstOrNull()?.asObject()?.string("text")
            ?: rawSongs.firstOrNull()?.artist
            ?: "Artist"
        val artworkUrl = extractArtwork(header ?: root) ?: rawSongs.firstOrNull()?.artworkUrl
        val songs = rawSongs.map { s ->
            s.copy(
                artist = if (s.artist == "Unknown artist" || s.artist.isBlank()) artist else s.artist,
                album = s.album ?: title,
                artworkUrl = s.artworkUrl ?: artworkUrl
            )
        }
        AlbumPageData(title = title, artist = artist, artworkUrl = artworkUrl, songs = songs)
    }

    suspend fun fetchPlaylist(
        playlistId: String,
        maxTracks: Int? = 100,
        progressive: Boolean = false,
        onPageLoaded: ((List<YouTubeMusicTrack>) -> Unit)? = null,
    ): YouTubePlaylistResult? = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext null
        val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("PL") ||
            playlistId.startsWith("RD") || playlistId.startsWith("OLAK") || playlistId == "LM"
        ) {
            if (playlistId.startsWith("PL")) "VL$playlistId" else playlistId
        } else {
            "VL$playlistId"
        }
        val isAuth = ytAuth.connection.value.isConnected
        val root = runCatching { browseRoot(browseId, authenticated = isAuth) }
            .getOrNull() ?: runCatching { browseRoot(browseId, authenticated = false) }.getOrNull()
            ?: return@withContext null

        val header = root.obj("header")?.obj("musicDetailHeaderRenderer")
            ?: root.obj("header")?.obj("musicResponsiveHeaderRenderer")
        val title = header?.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?: header?.obj("title")?.string("simpleText")
            ?: if (playlistId == "LM") "Liked on YouTube" else "Playlist"
        val author = header?.obj("subtitle")?.array("runs")?.firstOrNull()?.asObject()?.string("text")
            ?: if (playlistId == "LM") "Your favorites" else "YouTube Music"
        val artworkUrl = extractArtwork(header ?: root)

        val tracks = mutableListOf<YouTubeMusicTrack>()
        val initialTracks = parseSongRenderers(root)
        tracks.addAll(initialTracks)
        if (progressive && tracks.isNotEmpty()) onPageLoaded?.invoke(tracks.toList())

        // Continuations
        var continuationToken = playlistTrackContinuationToken(root)
        var pages = 0
        while (!continuationToken.isNullOrBlank() && pages < 10 && (maxTracks == null || tracks.size < maxTracks)) {
            pages++
            val nextJson = runCatching { browseContinuation(continuationToken!!, authenticated = isAuth) }.getOrNull() ?: break
            val nextTracks = parseSongRenderers(nextJson)
            if (nextTracks.isEmpty()) break
            tracks.addAll(nextTracks.filter { t -> tracks.none { it.videoId == t.videoId } })
            if (progressive) onPageLoaded?.invoke(tracks.toList())
            continuationToken = playlistTrackContinuationToken(nextJson)
        }

        val finalTracks = if (maxTracks != null) tracks.take(maxTracks) else tracks
        YouTubePlaylistResult(
            id = playlistId,
            title = title,
            author = author,
            artworkUrl = artworkUrl ?: finalTracks.firstOrNull()?.artworkUrl,
            trackCount = finalTracks.size,
            tracks = finalTracks
        )
    }

    suspend fun findBestMatchOrNull(title: String, artist: String): YouTubeMusicTrack? = withContext(Dispatchers.IO) {
        val query = "$title $artist".trim()
        val songs = searchSongs(query, limit = 5)
        songs.firstOrNull {
            it.title.contains(title, ignoreCase = true) || title.contains(it.title, ignoreCase = true)
        } ?: songs.firstOrNull()
    }

    suspend fun isPlayable(title: String, artist: String): Boolean =
        findBestMatchOrNull(title, artist) != null

    private fun parseSongRenderers(root: JsonElement): List<YouTubeMusicTrack> {
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicResponsiveListItemRenderer", renderers)
        val songs = renderers.mapNotNull(::parseSong).toMutableList()
        val queueRenderers = mutableListOf<JsonObject>()
        collectObjects(root, "playlistPanelVideoRenderer", queueRenderers)
        songs.addAll(queueRenderers.mapNotNull(::parsePlaylistPanelSong))
        if (songs.isEmpty()) {
            val ytVideos = mutableListOf<JsonObject>()
            collectObjects(root, "playlistVideoRenderer", ytVideos)
            songs.addAll(ytVideos.mapNotNull(::parsePlaylistVideoRenderer))
        }
        return songs.distinctBy { it.videoId }
    }

    private fun parseHomeFeedSongs(root: JsonElement): List<YouTubeMusicTrack> {
        val rows = mutableListOf<JsonObject>()
        collectObjects(root, "musicResponsiveListItemRenderer", rows)
        val songs = rows.filter { row -> directWatchVideoId(row) != null }
            .mapNotNull(::parseSong).toMutableList()
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicTwoRowItemRenderer", renderers)
        songs += renderers.mapNotNull(::parseTwoRowSong)
        return songs.distinctBy { it.videoId }
    }

    private fun parseSong(renderer: JsonObject): YouTubeMusicTrack? {
        val videoId = directWatchVideoId(renderer) ?: return null
        val columns = renderer.array("flexColumns")
        val titleRuns = columns?.getOrNull(0)?.asObject()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.array("runs")
        val title = titleRuns?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val detailRuns = columns?.getOrNull(1)?.asObject()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.array("runs")
            ?.mapNotNull { it.asObject() }.orEmpty()
        val artist = detailRuns.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
        }?.string("text")?.trim()?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }
            ?: detailRuns.mapNotNull { it.string("text") }
                .map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
                .firstOrNull { runText ->
                    runText.isNotBlank() &&
                    runText.any { ch -> ch.isLetterOrDigit() } &&
                    !runText.equals("Song", ignoreCase = true) &&
                    !runText.equals("Video", ignoreCase = true) &&
                    !runText.equals("Album", ignoreCase = true) &&
                    !runText.equals("Single", ignoreCase = true) &&
                    !runText.equals("EP", ignoreCase = true) &&
                    parseDuration(runText) == null
                } ?: "Unknown artist"
        val album = detailRuns.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("MPRE") == true
        }?.string("text")
        val duration = detailRuns.mapNotNull { it.string("text") }.firstNotNullOfOrNull(::parseDuration)
        val artwork = extractArtwork(renderer)
        val watchConfig = renderer.obj("navigationEndpoint")?.obj("watchEndpoint")
            ?.obj("watchEndpointMusicSupportedConfigs")?.obj("watchEndpointMusicConfig")
            ?: renderer.obj("thumbnailOverlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")
                ?.obj("watchEndpointMusicSupportedConfigs")?.obj("watchEndpointMusicConfig")
        val musicVideoType = watchConfig?.string("musicVideoType")
        val hasVideoBadge = detailRuns.any { run ->
            val txt = run.string("text")?.trim().orEmpty()
            txt.equals("Video", ignoreCase = true) || txt.equals("Music video", ignoreCase = true)
        }
        val isVideo = (musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV") || hasVideoBadge || ContentFilterUtils.isVideoTitle(title)
        return YouTubeMusicTrack(videoId, title, artist, album, artwork, duration, isVideo)
    }

    private fun parseTwoRowSong(renderer: JsonObject): YouTubeMusicTrack? {
        val titleRuns = renderer.obj("title")?.array("runs")
        val videoId = directWatchVideoId(renderer)
            ?: titleRuns?.firstOrNull()?.asObject()
                ?.obj("navigationEndpoint")?.obj("watchEndpoint")?.string("videoId")
            ?: return null
        val title = titleRuns?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?.trim()?.takeIf { it.isNotBlank() }
            ?: renderer.obj("title")?.string("simpleText")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val details = renderer.obj("subtitle")?.array("runs")?.mapNotNull { it.asObject() }.orEmpty()
        val artist = details.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
        }?.string("text")?.trim()?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }
            ?: details.mapNotNull { it.string("text") }
                .map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
                .firstOrNull { runText ->
                    runText.isNotBlank() &&
                    runText.any { ch -> ch.isLetterOrDigit() } &&
                    !runText.equals("Song", ignoreCase = true) &&
                    !runText.equals("Video", ignoreCase = true) &&
                    !runText.equals("Album", ignoreCase = true) &&
                    !runText.equals("Single", ignoreCase = true) &&
                    !runText.equals("EP", ignoreCase = true) &&
                    parseDuration(runText) == null
                } ?: "Unknown artist"
        val album = details.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("MPRE") == true
        }?.string("text")
        val duration = details.mapNotNull { it.string("text") }.firstNotNullOfOrNull(::parseDuration)
        val artwork = extractArtwork(renderer)
        val watchConfig = renderer.obj("navigationEndpoint")?.obj("watchEndpoint")
            ?.obj("watchEndpointMusicSupportedConfigs")?.obj("watchEndpointMusicConfig")
            ?: renderer.obj("thumbnailOverlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")
                ?.obj("watchEndpointMusicSupportedConfigs")?.obj("watchEndpointMusicConfig")
        val musicVideoType = watchConfig?.string("musicVideoType")
        val hasVideoBadge = details.any { run ->
            val txt = run.string("text")?.trim().orEmpty()
            txt.equals("Video", ignoreCase = true) || txt.equals("Music video", ignoreCase = true)
        }
        val isVideo = (musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV") || hasVideoBadge || ContentFilterUtils.isVideoTitle(title)
        return YouTubeMusicTrack(videoId, title, artist, album, artwork, duration, isVideo)
    }

    private fun parsePlaylistPanelSong(renderer: JsonObject): YouTubeMusicTrack? {
        if (renderer.obj("unplayableText") != null) return null
        val videoId = renderer.string("videoId")
            ?: renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.string("videoId")
            ?: return null
        val title = renderer.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?.trim()?.takeIf { it.isNotBlank() }
            ?: renderer.obj("title")?.string("simpleText")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val detailRuns = (renderer.obj("longBylineText") ?: renderer.obj("shortBylineText"))
            ?.array("runs")?.mapNotNull { it.asObject() }.orEmpty()
        val artist = detailRuns.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
        }?.string("text")?.trim()?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }
            ?: detailRuns.mapNotNull { it.string("text") }
                .map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
                .firstOrNull { runText ->
                    runText.isNotBlank() &&
                    runText.any { ch -> ch.isLetterOrDigit() } &&
                    !runText.equals("Song", ignoreCase = true) &&
                    !runText.equals("Video", ignoreCase = true) &&
                    parseDuration(runText) == null
                } ?: "Unknown artist"
        val album = detailRuns.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("MPRE") == true
        }?.string("text")
        val duration = renderer.obj("lengthText")?.array("runs")?.mapNotNull { it.asObject()?.string("text") }?.firstNotNullOfOrNull(::parseDuration)
        val artwork = extractArtwork(renderer)
        val watchConfig = renderer.obj("navigationEndpoint")?.obj("watchEndpoint")
            ?.obj("watchEndpointMusicSupportedConfigs")?.obj("watchEndpointMusicConfig")
        val musicVideoType = watchConfig?.string("musicVideoType")
        val hasVideoBadge = detailRuns.any { run ->
            val txt = run.string("text")?.trim().orEmpty()
            txt.equals("Video", ignoreCase = true) || txt.equals("Music video", ignoreCase = true)
        }
        val isVideo = (musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV") || hasVideoBadge || ContentFilterUtils.isVideoTitle(title)
        return YouTubeMusicTrack(videoId, title, artist, album, artwork, duration, isVideo)
    }

    private fun parsePlaylistVideoRenderer(renderer: JsonObject): YouTubeMusicTrack? {
        val videoId = renderer.string("videoId") ?: return null
        val title = renderer.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?: renderer.obj("title")?.string("simpleText")
            ?: return null
        val artist = renderer.obj("shortBylineText")?.array("runs")?.mapNotNull { it.asObject()?.string("text") }
            ?.map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
            ?.firstOrNull { runText ->
                runText.isNotBlank() &&
                runText.any { ch -> ch.isLetterOrDigit() } &&
                !runText.equals("Song", ignoreCase = true) &&
                !runText.equals("Video", ignoreCase = true)
            } ?: "Unknown artist"
        val duration = renderer.string("lengthSeconds")?.toIntOrNull()
        val artwork = extractArtwork(renderer)
        val isVideo = ContentFilterUtils.isVideoTitle(title) || true
        return YouTubeMusicTrack(videoId, title, artist, null, artwork, duration, isVideo)
    }

    private fun parsePlaylistRenderers(root: JsonElement): List<YouTubePlaylistSummary> {
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicResponsiveListItemRenderer", renderers)
        collectObjects(root, "musicTwoRowItemRenderer", renderers)
        collectObjects(root, "gridPlaylistRenderer", renderers)
        collectObjects(root, "musicGridItemRenderer", renderers)
        collectObjects(root, "playlistRenderer", renderers)
        return renderers.mapNotNull(::parsePlaylistSummary).distinctBy { it.id }
    }

    private fun parsePlaylistSummary(renderer: JsonObject): YouTubePlaylistSummary? {
        val nav = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")
            ?: renderer.obj("title")?.array("runs")?.firstOrNull()?.asObject()?.obj("navigationEndpoint")?.obj("browseEndpoint")
        var browseId = nav?.string("browseId")
        if (browseId.isNullOrBlank()) {
            browseId = renderer.string("playlistId") ?: return null
        }
        val cleanId = browseId.removePrefix("VL")
        val title = renderer.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
            ?: renderer.obj("title")?.string("simpleText")
            ?: return null
        val subtitleRuns = renderer.obj("subtitle")?.array("runs")?.mapNotNull { it.asObject() }.orEmpty()
        val author = subtitleRuns.firstOrNull { run ->
            run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
        }?.string("text")?.trim()?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }
            ?: subtitleRuns.mapNotNull { it.string("text") }
                .map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
                .firstOrNull { text ->
                    text.isNotBlank() &&
                    text.any { ch -> ch.isLetterOrDigit() } &&
                    !text.equals("Playlist", ignoreCase = true) &&
                    !text.equals("Album", ignoreCase = true) &&
                    !text.equals("Single", ignoreCase = true) &&
                    !text.equals("EP", ignoreCase = true) &&
                    !text.equals("Song", ignoreCase = true) &&
                    !text.equals("Video", ignoreCase = true) &&
                    !text.contains("song", ignoreCase = true) &&
                    !text.contains("track", ignoreCase = true)
                }
        val trackCountText = subtitleRuns.mapNotNull { it.string("text") }.firstOrNull {
            it.contains("song", ignoreCase = true) || it.contains("track", ignoreCase = true)
        }
        val artworkUrl = extractArtwork(renderer)
        return YouTubePlaylistSummary(
            id = cleanId,
            title = title.trim(),
            author = author,
            trackCountText = trackCountText,
            artworkUrl = artworkUrl
        )
    }

    private fun parseHomeAlbums(root: JsonElement, limit: Int): List<FeedAlbum> {
        val renderers = mutableListOf<JsonObject>()
        collectObjects(root, "musicTwoRowItemRenderer", renderers)
        return renderers.mapNotNull { item ->
            val nav = item.obj("navigationEndpoint")?.obj("browseEndpoint")
                ?: item.obj("title")?.array("runs")?.firstOrNull()?.asObject()?.obj("navigationEndpoint")?.obj("browseEndpoint")
            val browseId = nav?.string("browseId") ?: return@mapNotNull null
            if (!browseId.startsWith("MPRE")) return@mapNotNull null
            val pageType = nav.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")?.string("pageType")
            val subtitleRuns = item.obj("subtitle")?.array("runs")?.mapNotNull { it.asObject()?.string("text") }.orEmpty()
            val isAlbumType = pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
                pageType == "MUSIC_PAGE_TYPE_AUDIOBOOK" ||
                subtitleRuns.any { it.equals("Album", ignoreCase = true) || it.equals("EP", ignoreCase = true) || it.equals("Single", ignoreCase = true) } ||
                browseId.startsWith("MPREb_")
            if (!isAlbumType) return@mapNotNull null

            val title = item.obj("title")?.array("runs")?.joinToString("") { it.asObject()?.string("text").orEmpty() }
                ?: item.obj("title")?.string("simpleText")
                ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null

            val details = item.obj("subtitle")?.array("runs")?.mapNotNull { it.asObject() }.orEmpty()
            val artist = details.firstOrNull { run ->
                run.obj("navigationEndpoint")?.obj("browseEndpoint")?.string("browseId")?.startsWith("UC") == true
            }?.string("text")?.trim()?.takeIf { it.isNotBlank() }
                ?: details.mapNotNull { it.string("text") }
                    .map { it.trim().trim(',', '&', '/', ';', '•', '·', '.', '-').trim() }
                    .firstOrNull { runText ->
                        runText.isNotBlank() &&
                        !runText.equals("Album", ignoreCase = true) &&
                        !runText.equals("Single", ignoreCase = true) &&
                        !runText.equals("EP", ignoreCase = true) &&
                        !runText.matches(Regex("^(19|20)\\d{2}$"))
                    } ?: "Unknown artist"

            val artworkUrl = extractArtwork(item)
            FeedAlbum(
                title = title.trim(),
                artist = artist,
                artworkUrl = artworkUrl,
                browseId = browseId
            )
        }.distinctBy { "${it.artist.trim().lowercase()}_${it.title.trim().lowercase()}" }.take(limit)
    }

    private fun directWatchVideoId(renderer: JsonObject): String? =
        renderer.obj("playlistItemData")?.string("videoId")
            ?: renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.string("videoId")
            ?: renderer.obj("thumbnailOverlay")
                ?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.string("videoId")

    private fun extractArtwork(renderer: JsonElement): String? {
        val foundArrays = mutableListOf<JsonArray>()
        fun findThumbnails(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    el.array("thumbnails")?.takeIf { it.isNotEmpty() }?.let { foundArrays += it }
                    el.values.forEach { findThumbnails(it) }
                }
                is JsonArray -> el.forEach { findThumbnails(it) }
                else -> Unit
            }
        }
        findThumbnails(renderer)
        val bestArray = foundArrays.firstOrNull { it.isNotEmpty() } ?: return null
        val bestUrl = bestArray.lastOrNull()?.asObject()?.string("url")
            ?: bestArray.firstOrNull()?.asObject()?.string("url")
            ?: return null
        val formatted = if (bestUrl.startsWith("//")) "https:$bestUrl" else bestUrl
        return upgradeThumbnailUrlToHighQuality(formatted)
    }

    private fun playlistTrackContinuationToken(root: JsonElement): String? {
        val commands = mutableListOf<JsonObject>()
        collectObjects(root, "continuationCommand", commands)
        commands.firstNotNullOfOrNull { cmd ->
            cmd.string("token")?.takeIf { it.isNotBlank() }
        }?.let { return it }

        val legacyItems = mutableListOf<JsonObject>()
        collectObjects(root, "nextContinuationData", legacyItems)
        return legacyItems.firstNotNullOfOrNull { it.string("continuation")?.takeIf { s -> s.isNotBlank() } }
    }

    private fun collectObjects(element: JsonElement, key: String, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> element.forEach { (name, child) ->
                if (name == key && child is JsonObject) output += child
                collectObjects(child, key, output)
            }
            is JsonArray -> element.forEach { collectObjects(it, key, output) }
            else -> Unit
        }
    }

    private fun parseDuration(value: String): Int? {
        val parts = value.trim().split(':').mapNotNull { it.toIntOrNull() }
        if (parts.size !in 2..3) return null
        return parts.fold(0) { total, part -> total * 60 + part }
    }

    private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key)?.asObject()
    private fun JsonElement.array(key: String): JsonArray? = (this as? JsonObject)?.get(key)?.asArray()
    private fun JsonElement.string(key: String): String? = (this as? JsonObject)?.get(key)?.asString
    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
    private val JsonElement.asString: String? get() = (this as? JsonPrimitive)?.contentOrNull

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MUSIC_API = "https://music.youtube.com/youtubei/v1"
        const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"
        const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        const val FALLBACK_WEB_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val FALLBACK_WEB_VERSION = "1.20260707.12.00"

        const val YT_HOME_BROWSE_ID = "FEmusic_home"
        const val YT_NEW_RELEASES_BROWSE_ID = "FEmusic_new_releases"
        const val YT_CHARTS_BROWSE_ID = "FEmusic_charts"
        const val YT_HISTORY_BROWSE_ID = "FEmusic_history"
        const val YT_LIKED_BROWSE_ID = "LM"
    }
}
