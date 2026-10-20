package com.unshoo.pixelmusic.data.feed

import androidx.compose.runtime.Immutable
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.utils.ContentFilterUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val innerTube: FeedInnerTubeApi,
    private val tasteProfileProvider: FeedTasteProfileProvider,
    private val datastoreRepository: DatastoreRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private var cachedFeed: FeedData? = null
    private var cachedKey: String? = null

    fun getCachedFeed(): FeedData? = cachedFeed

    suspend fun loadFeed(
        username: String? = null,
        forceRefresh: Boolean = false,
        onUpdate: (FeedData) -> Unit = {},
    ): FeedData = coroutineScope {
        val exploreLastFmEnabled = runCatching { userPreferencesRepository.exploreLastfmEnabledFlow.first() }.getOrDefault(true)
        val resolvedUsername = if (!exploreLastFmEnabled) "" else (username ?: runCatching { userPreferencesRepository.lastfmUsernameFlow.first() }.getOrDefault(""))
        val cookies = runCatching { datastoreRepository.cookies.first() }.getOrNull()
        val isYtConnected = cookies?.toRawCookie()?.let {
            it.contains("SAPISID=") || it.contains("__Secure-3PAPISID=")
        } == true
        val ytAccountName = runCatching { datastoreRepository.ytUsername.first() }.getOrDefault("")
        val connectionKey = if (isYtConnected) ytAccountName else "disconnected"

        val cacheKey = "${resolvedUsername.trim()}|$connectionKey"
        val previous = if (forceRefresh) null else cachedFeed?.takeIf { cachedKey == cacheKey }
        if (!forceRefresh) {
            previous?.let(onUpdate)
        }

        val random = kotlin.random.Random(System.nanoTime())

        val pureYtMusicOnly = runCatching { userPreferencesRepository.pureYtMusicOnlyFlow.first() }.getOrDefault(false)
        val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
        val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
        val videoTitlePattern = Regex("""(?i)\b(official\s+video|official\s+music\s+video|music\s+video|lyric\s+video|visualizer|video\s+song)\b|\((video|music\s+video)\)|\[(video|music\s+video)\]""")
        fun isSongItemVideo(song: unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem): Boolean {
            val musicVideoType = song.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
            val isOmv = musicVideoType == unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_OMV ||
                        musicVideoType == unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_UGC
            return isOmv || videoTitlePattern.containsMatchIn(song.title)
        }
        fun isVideoTrack(t: YouTubeMusicTrack): Boolean = t.isVideo || videoTitlePattern.containsMatchIn(t.title)
        fun filterVideoTrack(t: YouTubeMusicTrack): Boolean = !pureYtMusicOnly || !isVideoTrack(t)
        fun filterTrack(t: YouTubeMusicTrack): Boolean =
            filterVideoTrack(t) &&
            !ContentFilterUtils.matchesFilterKeywords(t, filterKeywords) &&
            (!filterCoverAndLofi || !ContentFilterUtils.isCoverOrLofi(t, filterKeywords))
        fun filterRecent(t: RecentTrack): Boolean =
            !ContentFilterUtils.matchesFilterKeywords(t.name, t.artist.displayName, t.album.displayName, filterKeywords) &&
            (!filterCoverAndLofi || !ContentFilterUtils.isCoverOrLofi(t, filterKeywords))
        fun filterGenerated(t: GeneratedTrack): Boolean =
            !ContentFilterUtils.matchesFilterKeywords(t.name, t.artist, t.album, filterKeywords) &&
            (!filterCoverAndLofi || !ContentFilterUtils.isCoverOrLofi(t, filterKeywords))

        val homeDef = async(Dispatchers.IO) {
            runCatching { unshoo.ianshulyadav.pixelmusic.innertube.YouTube.home().getOrNull() }.getOrNull()
        }

        val chartsDef = async(Dispatchers.IO) {
            runCatching { innerTube.fetchCharts() }.getOrDefault(emptyList())
        }

        val ytTasteDef = async(Dispatchers.IO) {
            if (isYtConnected) {
                runCatching { innerTube.fetchTasteSignals(recentLimit = 30, likedLimit = 30, feedLimit = 40) }.getOrNull()
            } else null
        }

        val recentTracksDef = async(Dispatchers.IO) { emptyList<RecentTrack>() }
        val friendsDef = async(Dispatchers.IO) { emptyList<FriendEntry>() }
        val tasteProfileDef = async(Dispatchers.IO) {
            runCatching { tasteProfileProvider.get(forceRefresh = forceRefresh) }.getOrNull()
        }

        val home = homeDef.await()
        val sections = home?.sections.orEmpty()

        val releaseSections = sections.filter { s ->
            val t = s.title.lowercase()
            !t.contains("video") && !t.contains("videos") && (
                t.contains("released") ||
                t.contains("new release") ||
                t.contains("new album") || t.contains("latest release") ||
                t.contains("new music") || t.contains("recent release") ||
                t.contains("novedades") || t.contains("nouveautés") ||
                t.contains("veröffentlichungen") || t.contains("release radar") ||
                t.contains("new for you") || t.contains("fresh")
            )
        }

        val newReleaseSongTracks = releaseSections.flatMap { it.items }
            .filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()
            .map { song ->
                YouTubeMusicTrack(
                    videoId = song.id,
                    title = song.title,
                    artist = song.artists.firstOrNull()?.name ?: "Unknown artist",
                    album = song.album?.name,
                    artworkUrl = song.thumbnail,
                    isVideo = isSongItemVideo(song)
                )
            }.filter(::filterTrack)

        val releaseCandidates = releaseSections.flatMap { it.items }.mapNotNull { item ->
            when (item) {
                is unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem -> YouTubePlaylistSummary(
                    id = item.browseId,
                    title = item.title,
                    author = item.artists?.firstOrNull()?.name ?: "Album",
                    artworkUrl = item.thumbnail
                )
                is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem -> {
                    if (pureYtMusicOnly && isSongItemVideo(item)) null
                    else YouTubePlaylistSummary(
                        id = item.id,
                        title = item.title,
                        author = item.artists.firstOrNull()?.name ?: "Single",
                        artworkUrl = item.thumbnail
                    )
                }
                else -> null
            }
        }.filterNot {
            ContentFilterUtils.matchesFilterKeywords(it.title, it.author, null, filterKeywords)
        }.distinctBy { it.id }

        val onlineCharts = chartsDef.await().filter(::filterTrack)
        val sectionCharts = sections.filter { s ->
            val t = s.title.lowercase()
            t.contains("trending") || t.contains("chart") || t.contains("top")
        }.flatMap { it.items }.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>().map { song ->
            YouTubeMusicTrack(
                videoId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull()?.name ?: "Unknown artist",
                album = song.album?.name,
                artworkUrl = song.thumbnail,
                isVideo = isSongItemVideo(song)
            )
        }.filter(::filterTrack)

        val charts = (sectionCharts + onlineCharts).distinctBy { it.videoId }.ifEmpty { onlineCharts }

        val homePlaylists = sections.flatMap { it.items }
            .filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem>()
            .filter { it.id.startsWith("PL") || it.id.startsWith("RD") || it.id.startsWith("OLAK") || it.id == "LM" }
            .map { p ->
                YouTubePlaylistSummary(
                    id = p.id,
                    title = p.title,
                    author = p.author?.name ?: "Playlist",
                    artworkUrl = p.thumbnail
                )
            }

        val homeAlbums = sections.filter { s ->
            val t = s.title.lowercase()
            !t.contains("new release") && !t.contains("new album") && (
                t.contains("album") || t.contains("disc") || t.contains("for you")
            )
        }
            .flatMap { it.items }
            .filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem>()
            .map { album ->
                FeedAlbum(
                    title = album.title,
                    artist = album.artists?.firstOrNull()?.name ?: "Unknown artist",
                    artworkUrl = album.thumbnail,
                    browseId = album.browseId
                )
            }

        val homeSongsPreferred = sections.filter { s ->
            val t = s.title.lowercase()
            t.contains("quick") || t.contains("listen") || t.contains("recommend") || t.contains("pick")
        }.flatMap { it.items }.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()

        val allHomeSongItems = if (homeSongsPreferred.isNotEmpty()) {
            homeSongsPreferred
        } else {
            sections.flatMap { it.items }.filterIsInstance<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>()
        }

        val parsedHomeSongs = allHomeSongItems.map { song ->
            YouTubeMusicTrack(
                videoId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull()?.name ?: "Unknown artist",
                album = song.album?.name,
                artworkUrl = song.thumbnail,
                isVideo = isSongItemVideo(song)
            )
        }.filter(::filterTrack)

        val homeSongs = if (parsedHomeSongs.isNotEmpty()) parsedHomeSongs else charts
        val recentTracks = recentTracksDef.await()
        val friends = friendsDef.await()
        val tasteProfile = tasteProfileDef.await()
        val ytTaste = ytTasteDef.await()

        val ytLikedSongs = ytTaste?.likedTracks.orEmpty().filter(::filterTrack).ifEmpty { previous?.ytLikedSongs.orEmpty() }
        val ytRecentSongs = ytTaste?.recentTracks.orEmpty().filter(::filterTrack).ifEmpty { previous?.ytRecentSongs.orEmpty() }
        val ytQuickPicks = ytTaste?.feedTracks.orEmpty().filter(::filterTrack).ifEmpty { homeSongs }.ifEmpty { previous?.quickPicks.orEmpty() }

        val affinity = tasteProfile?.artistAffinity.orEmpty()
        val previousPickIds = previous?.quickPicks.orEmpty().mapTo(mutableSetOf()) { it.videoId }

        fun trackScore(t: YouTubeMusicTrack, index: Int, sourceBoost: Double): Double {
            val keys = ArtistHelper.splitArtists(t.artist).map { it.trim().lowercase() }.ifEmpty { listOf(t.artist.trim().lowercase()) }
            val aff = keys.maxOfOrNull { affinity[it] ?: 0.0 } ?: 0.0
            val hasVideo = t.videoId.isNotBlank()
            val hasArt = ArtworkNormalizer.isRealImage(t.artworkUrl)
            val positionDecay = 1.0 / (1.0 + index / 9.0)
            val jitter = random.nextDouble()
            return aff * 60.0 + sourceBoost * 14.0 * positionDecay +
                (if (hasVideo) 10.0 else -6.0) + (if (hasArt) 4.0 else 0.0) + jitter * 35.0 - (if (t.videoId in previousPickIds) 30.0 else 0.0)
        }

        fun <T> diversify(
            items: List<T>,
            artistOf: (T) -> String,
            maxPerArtist: Int = 2,
        ): List<T> {
            val counts = mutableMapOf<String, Int>()
            val out = ArrayList<T>(items.size)
            val deferred = ArrayList<T>()
            for (item in items) {
                val key = ArtistHelper.splitArtists(artistOf(item)).firstOrNull()?.trim()?.lowercase()
                    ?: artistOf(item).trim().lowercase()
                if ((counts[key] ?: 0) < maxPerArtist) {
                    counts[key] = (counts[key] ?: 0) + 1
                    out.add(item)
                } else deferred.add(item)
            }
            for (item in deferred) {
                val key = ArtistHelper.splitArtists(artistOf(item)).firstOrNull()?.trim()?.lowercase()
                    ?: artistOf(item).trim().lowercase()
                if ((counts[key] ?: 0) < maxPerArtist + 1) {
                    counts[key] = (counts[key] ?: 0) + 1
                    out.add(item)
                }
            }
            return out
        }

        fun <T> blend(first: List<T>, second: List<T>): List<T> = buildList {
            repeat(maxOf(first.size, second.size)) { index ->
                first.getOrNull(index)?.let { add(it) }
                second.getOrNull(index)?.let { add(it) }
            }
        }

        val regularPicks = tasteProfile?.topTracksRaw.orEmpty().map {
            YouTubeMusicTrack(it.youtubeVideoIdOrNull().orEmpty(), it.name, it.artist, it.album, it.artworkUrl)
        }.filter(::filterTrack)
        val quickCandidates = buildList {
            ytQuickPicks.filter(::filterTrack).forEachIndexed { i, t -> add(t to trackScore(t, i, 3.0)) }
            ytLikedSongs.filter(::filterTrack).forEachIndexed { i, t -> add(t to trackScore(t, i, 2.2)) }
            ytRecentSongs.filter(::filterTrack).forEachIndexed { i, t -> add(t to trackScore(t, i, 1.6)) }
            regularPicks.forEachIndexed { i, t -> add(t to trackScore(t, i, 2.6)) }
            homeSongs.filter(::filterTrack).forEachIndexed { i, t -> add(t to trackScore(t, i, 1.2)) }
        }
            .distinctBy { (t, _) -> t.artist.trim().lowercase() to t.title.trim().lowercase() }
            .sortedByDescending { it.second }
            .map { it.first }

        val quickPicks = diversify(quickCandidates, YouTubeMusicTrack::artist, maxPerArtist = 2)
            .take(18)
            .ifEmpty { charts.filter(::filterTrack) }
            .distinctBy { it.artist.trim().lowercase() to it.title.trim().lowercase() }
            .take(15)

        onUpdate(
            (previous ?: FeedData()).copy(
                isYtConnected = isYtConnected,
                ytAccountName = ytAccountName.takeIf { isYtConnected },
                userName = resolvedUsername.takeIf { it.isNotBlank() },
                hasYtRecommendations = ytTaste?.feedTracks?.isNotEmpty() == true,
                hasPersonalContent = tasteProfile?.hasPersonalSignals == true || ytLikedSongs.isNotEmpty() || ytRecentSongs.isNotEmpty(),
                quickPicks = quickPicks,
                ytLikedSongs = ytLikedSongs,
                ytRecentSongs = ytRecentSongs,
            )
        )

        val knownArtists = buildSet {
            addAll(affinity.keys)
            addAll((ytLikedSongs + ytRecentSongs).flatMap { ArtistHelper.splitArtists(it.artist) }.map { it.trim().lowercase() })
            addAll(recentTracks.flatMap { ArtistHelper.splitArtists(it.artist.displayName) }.map { it.trim().lowercase() })
        }

        val artistSignalTracks = ytRecentSongs + ytLikedSongs + ytQuickPicks + homeSongs + charts
        val ytArtistNames = (ytRecentSongs + ytLikedSongs + ytQuickPicks + homeSongs)
            .flatMap { ArtistHelper.splitArtists(it.artist) }
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) }
            .groupBy { it.trim().lowercase() }
            .values.sortedByDescending { it.size }
            .map { it.first().trim() }
        val listeningArtists = tasteProfile?.topArtistsRaw.orEmpty().flatMap(ArtistHelper::splitArtists)
        val tasteArtists = (blend(ytArtistNames, listeningArtists) +
            recentTracks.flatMap { ArtistHelper.splitArtists(it.artist.displayName) } +
            regularPicks.flatMap { ArtistHelper.splitArtists(it.artist) })
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) }
            .map { it.lowercase() }.distinct()
        val artistRanks = tasteArtists.withIndex().associate { it.value to it.index }
        val matchedReleases = if (artistRanks.isEmpty()) emptyList() else releaseCandidates
            .mapNotNull { release ->
                val rank = ArtistHelper.splitArtists(release.author).mapNotNull { artistRanks[it.lowercase()] }.minOrNull()
                rank?.let { release to it }
            }.sortedBy { it.second }.map { it.first }.distinctBy { it.id }.take(15)

        val topArtistNames = buildList {
            repeat(maxOf(ytArtistNames.size, listeningArtists.size).coerceAtMost(8)) { index ->
                ytArtistNames.getOrNull(index)?.let { add(it) }
                listeningArtists.getOrNull(index)?.let { add(it) }
            }
            addAll(recentTracks.map { it.artist.displayName })
            if (isEmpty()) {
                addAll(ytArtistNames.take(8))
            }
        }
            .flatMap(ArtistHelper::splitArtists)
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) && it.any { ch -> ch.isLetterOrDigit() } }
            .distinctBy { it.lowercase() }
            .take(8)
            .shuffled(random)

        val discoverySeeds = (ytLikedSongs + ytRecentSongs + quickPicks).filter { it.videoId.isNotBlank() }
            .shuffled(random).distinctBy { it.artist.lowercase() }.take(3)

        val artistSearchSemaphore = Semaphore(2)
        val discoverySemaphore = Semaphore(2)

        val discoveryDef = async(Dispatchers.IO) {
            discoverySeeds.map { seed ->
                async {
                    discoverySemaphore.withPermit {
                        try {
                            innerTube.fetchRelatedSongs(seed.videoId, limit = 30)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
        }

        val topArtists = topArtistNames.map { name ->
            async(Dispatchers.IO) {
                val trackArtwork = artistSignalTracks
                    .firstOrNull { ArtistHelper.splitArtists(it.artist).any { artist -> artist.equals(name, ignoreCase = true) } }
                    ?.artworkUrl
                previous?.topArtists?.firstOrNull { it.name.equals(name, ignoreCase = true) && it.browseId != null }
                    ?.let { return@async it }
                val entity = artistSearchSemaphore.withPermit {
                    runCatching {
                        innerTube.searchArtists(name, limit = 3)
                            .firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
                    }.getOrNull()
                }
                FeedArtist(
                    name = name,
                    browseId = entity?.browseId,
                    artworkUrl = entity?.artworkUrl?.takeIf(ArtworkNormalizer::isRealImage) ?: trackArtwork,
                )
            }
        }.awaitAll()

        val blockedReleaseIds = runCatching { userPreferencesRepository.blockedReleaseIdsFlow.first() }.getOrDefault(emptySet())
        val onlineNewReleases = runCatching { innerTube.fetchNewReleases(authenticated = isYtConnected) }.getOrDefault(emptyList())

        // Pure latest releases: all singles and albums in release order, strictly not based on artists
        val newReleases: List<YouTubePlaylistSummary> = (releaseCandidates + onlineNewReleases)
            .distinctBy { it.id }
            .filter { it.id !in blockedReleaseIds }
            .ifEmpty { previous?.newReleases.orEmpty().filter { it.id !in blockedReleaseIds } }
            .take(30)

        val discoveryResults = discoverySeeds.zip(discoveryDef.await())
        val discoveryTracks = discoveryResults.flatMap { it.second }.filter(::filterTrack).distinctBy { it.videoId }
        val familiarIds = (ytLikedSongs + ytRecentSongs + regularPicks).mapTo(mutableSetOf()) { it.videoId }
        val freshPool = if (newReleaseSongTracks.isNotEmpty()) {
            newReleaseSongTracks.filter { it.videoId.isNotBlank() && filterTrack(it) }
        } else {
            (discoveryTracks + charts + homeSongs + ytQuickPicks)
                .filter { it.videoId.isNotBlank() && it.videoId !in familiarIds && filterTrack(it) }
                .distinctBy { it.videoId }.shuffled(random)
        }
        val previousFreshIds = previous?.freshFinds.orEmpty().mapTo(mutableSetOf()) { it.videoId }
        val freshFinds = if (newReleaseSongTracks.isNotEmpty()) {
            freshPool.distinctBy { it.videoId }.take(20)
        } else {
            diversify(
                freshPool.sortedWith(
                    compareBy<YouTubeMusicTrack> { it.videoId in previousFreshIds }
                        .thenBy { track -> ArtistHelper.splitArtists(track.artist).any { it.trim().lowercase() in knownArtists } }
                ),
                YouTubeMusicTrack::artist,
                maxPerArtist = 1,
            ).take(20)
        }

        val previousMixIds = previous?.mixes.orEmpty().mapTo(mutableSetOf()) { it.seed.videoId }
        val mixes = (quickPicks + ytLikedSongs + discoveryTracks)
            .filter { it.videoId.isNotBlank() && filterTrack(it) }.distinctBy { it.videoId }.shuffled(random)
            .sortedBy { it.videoId in previousMixIds }
            .distinctBy { ArtistHelper.primaryArtist(it.artist).trim().lowercase() }.take(8)
            .map { FeedMix(title = "${ArtistHelper.primaryArtist(it.artist)} mix", seed = it) }

        val previousHeavyKeys = previous?.heavyRotation.orEmpty().mapTo(mutableSetOf()) { it.key }
        val heavyCandidates = buildList {
            tasteProfile?.topTracksRaw?.forEachIndexed { i, t ->
                val aff = ArtistHelper.splitArtists(t.artist).maxOfOrNull { affinity[it.trim().lowercase()] ?: 0.0 } ?: 0.0
                val jitter = random.nextDouble() * 25.0
                val prevPenalty = if (t.key in previousHeavyKeys) 22.0 else 0.0
                add(t to (aff * 40 + 20.0 / (1 + i / 6.0) + jitter - prevPenalty))
            }
            blend(ytRecentSongs, ytLikedSongs)
                .distinctBy { it.artist.trim().lowercase() to it.title.trim().lowercase() }
                .forEachIndexed { i, it ->
                    val aff = affinity[ArtistHelper.primaryArtist(it.artist).trim().lowercase()] ?: 0.0
                    val jitter = random.nextDouble() * 25.0
                    val prevPenalty = if (it.videoId in previousHeavyKeys || it.title in previousHeavyKeys) 22.0 else 0.0
                    add(
                        GeneratedTrack(
                            it.title, it.artist, it.artworkUrl,
                            url = "https://www.youtube.com/watch?v=${it.videoId}", album = it.album,
                        ) to (12.0 / (1 + i / 6.0) + aff * 30 + jitter - prevPenalty),
                    )
                }
        }.distinctBy { (t, _) -> t.key }
            .sortedByDescending { it.second }
            .map { it.first }
        val heavyRotation = diversify(
            heavyCandidates.filter(::filterGenerated).distinctBy(GeneratedTrack::key),
            GeneratedTrack::artist,
            maxPerArtist = 2
        ).take(15)

        val ytJumpCandidates = buildList {
            ytRecentSongs.forEach {
                val pArtist = ArtistHelper.primaryArtist(it.artist)
                add(
                    RecentTrack(
                        name = it.title,
                        artist = RecentTrackArtistRef(name = pArtist),
                        album = RecentTrackArtistRef(name = it.album.orEmpty()),
                        image = it.artworkUrl?.let { url -> listOf(ImageDto(url, "extralarge")) }.orEmpty(),
                        url = "https://www.youtube.com/watch?v=${it.videoId}",
                    ),
                )
            }
        }
        val lastFmJumpCandidates = recentTracks.map {
            it.copy(artist = RecentTrackArtistRef(name = ArtistHelper.primaryArtist(it.artist.displayName)))
        }
        val jumpCandidates = blend(ytJumpCandidates, lastFmJumpCandidates)
            .distinctBy { it.artist.displayName.trim().lowercase() to it.name.trim().lowercase() }
        val jumpBackIn = diversify(jumpCandidates.filter(::filterRecent), { it.artist.displayName }, maxPerArtist = 2).take(15)

        val albumArtworkRequests = Semaphore(4)
        val artistPageRequests = Semaphore(3)
        val filteredHomeAlbums = homeAlbums
            .filter { !it.browseId.isNullOrBlank() && ArtworkNormalizer.isRealImage(it.artworkUrl) }
            .distinctBy { it.browseId }

        val ytRealAlbums = if (filteredHomeAlbums.isNotEmpty()) {
            emptyList()
        } else {
            topArtists
                .filter { !it.browseId.isNullOrBlank() }
                .take(6)
                .map { artist ->
                    async(Dispatchers.IO) {
                        artistPageRequests.withPermit {
                            val browseId = artist.browseId?.takeIf(String::isNotBlank)
                                ?: return@withPermit emptyList<FeedAlbum>()
                            val page = runCatching { innerTube.fetchArtistPage(browseId, artist.name) }.getOrNull()
                            page?.albums.orEmpty()
                                .filter { item ->
                                    item.browseId.isNotBlank() &&
                                        item.browseId.startsWith("MPRE") &&
                                        item.title.isNotBlank() &&
                                        (item.type == null || item.type.equals("Album", ignoreCase = true))
                                }
                                .take(3)
                                .map { item ->
                                    FeedAlbum(
                                        title = item.title,
                                        artist = artist.name,
                                        artworkUrl = item.artworkUrl?.takeIf(ArtworkNormalizer::isRealImage),
                                        browseId = item.browseId,
                                    )
                                }
                        }
                    }
                }.awaitAll().flatten()
        }

        val personalAlbums = ytRealAlbums
            .distinctBy { "${it.artist.trim().lowercase()}_${it.title.trim().lowercase()}" }
            .filter { !it.browseId.isNullOrBlank() }
            .distinctBy { it.browseId }

        val recentAlbums = if (filteredHomeAlbums.isNotEmpty()) {
            filteredHomeAlbums.take(15)
        } else if (personalAlbums.isNotEmpty()) {
            personalAlbums.take(20).map { album ->
                async(Dispatchers.IO) {
                    if (ArtworkNormalizer.isRealImage(album.artworkUrl)) {
                        album
                    } else albumArtworkRequests.withPermit {
                        val pageArt = album.browseId?.takeIf(String::isNotBlank)?.let { id ->
                            runCatching { innerTube.fetchAlbumPage(id) }.getOrNull()?.artworkUrl
                        }?.takeIf(ArtworkNormalizer::isRealImage)
                        album.copy(artworkUrl = pageArt ?: album.artworkUrl)
                    }
                }
            }.awaitAll()
            .filter { ArtworkNormalizer.isRealImage(it.artworkUrl) }
            .distinctBy { it.browseId }
            .take(12)
        } else {
            previous?.recentAlbums.orEmpty()
                .filter { !it.browseId.isNullOrBlank() && ArtworkNormalizer.isRealImage(it.artworkUrl) }
                .distinctBy { it.browseId }
                .take(12)
        }

        val topSpotlightArtist = topArtists.filterNot { it.name == previous?.spotlight?.artistName }
            .randomOrNull(random) ?: topArtists.firstOrNull()
        val spotlight = if (topSpotlightArtist != null) {
            val topTrackTitle = heavyRotation
                .firstOrNull { it.artist.equals(topSpotlightArtist.name, ignoreCase = true) }
                ?.name
                ?: artistSignalTracks
                    .firstOrNull { it.artist.equals(topSpotlightArtist.name, ignoreCase = true) }
                    ?.title
            FeedSpotlight(
                artistName = topSpotlightArtist.name,
                artworkUrl = topSpotlightArtist.artworkUrl,
                browseId = topSpotlightArtist.browseId,
                description = "Spotlight Artist",
                topTrackTitle = topTrackTitle,
            )
        } else null

        val topArtist = topArtistNames.firstOrNull()

        fun seedScore(t: YouTubeMusicTrack, boost: Double): Double {
            val aff = ArtistHelper.splitArtists(t.artist).maxOfOrNull { affinity[it.trim().lowercase()] ?: 0.0 } ?: 0.0
            return aff * 50 + boost + (if (t.videoId.isNotBlank()) 8.0 else -10.0)
        }

        val seedCandidates = buildList {
            ytRecentSongs.forEach { add(it to seedScore(it, 12.0)) }
            ytLikedSongs.forEach { add(it to seedScore(it, 10.0)) }
            quickPicks.take(6).forEach { add(it to seedScore(it, 6.0)) }
        }.sortedByDescending { it.second }

        val personalRadioSeed = discoveryResults.firstOrNull { it.second.isNotEmpty() }?.first
            ?: seedCandidates.filter { it.first.videoId.isNotBlank() }.take(15).randomOrNull(random)?.first
            ?: heavyRotation.firstOrNull()?.takeIf { tasteProfile?.hasPersonalSignals == true }?.let { seed ->
                try {
                    innerTube.findBestMatchOrNull(seed.name, seed.artist)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            }

        val radioSeed = personalRadioSeed ?: (quickPicks + homeSongs + charts).firstOrNull { it.videoId.isNotBlank() }
            ?: (quickPicks + homeSongs + charts).firstOrNull()

        val radioTracks = discoveryResults.firstOrNull { it.first.videoId == radioSeed?.videoId }?.second
            ?.takeIf { it.isNotEmpty() }
            ?: radioSeed?.takeIf { it.videoId.isNotBlank() }?.let { seed ->
                try {
                    innerTube.fetchRelatedSongs(seed.videoId, limit = 15)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    emptyList()
                }
            }.orEmpty()

        val radioArtist = radioSeed?.artist ?: topArtist
        val radioFallback = if (radioTracks.isEmpty() && !radioArtist.isNullOrBlank()) {
            try {
                innerTube.searchSongs("$radioArtist radio", limit = 15)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        val becausePool: List<YouTubeMusicTrack> = radioTracks.ifEmpty { radioFallback }
        val becauseTracks = diversify(
            becausePool.filter(::filterTrack).distinctBy { it.videoId.ifBlank { it.title + "|" + it.artist } },
            YouTubeMusicTrack::artist,
            maxPerArtist = 2,
        ).take(15)

        val becauseSection = becauseTracks
            .takeIf { it.isNotEmpty() }
            ?.let { tracks ->
                FeedSectionData(
                    title = personalRadioSeed?.let { "Because you listen to ${it.artist}" } ?: "Discover something new",
                    subtitle = radioSeed?.let { "A mix inspired by ${it.title}" } ?: "Fresh tracks for your next listen",
                    items = tracks,
                )
            }

        val quickTiles = buildList {
            add(
                FeedQuickTile(
                    title = "Liked Songs",
                    subtitle = "Your collection",
                    localPlaylistId = -1L,
                    isLiked = true,
                )
            )
            if (isYtConnected) {
                add(
                    FeedQuickTile(
                        title = "Liked on YouTube",
                        subtitle = "Your favorites",
                        artworkUrl = ytLikedSongs.firstOrNull()?.artworkUrl,
                        playlistId = "yt_liked",
                        collection = "yt_liked",
                        isLiked = true,
                    )
                )
            }
            val savedMix = homePlaylists.firstOrNull { it.title.equals("Mix", ignoreCase = true) }
            add(
                savedMix?.let {
                    FeedQuickTile(title = it.title, subtitle = it.author, artworkUrl = it.artworkUrl, playlistId = it.id)
                } ?: FeedQuickTile(
                    title = "Mix",
                    subtitle = "Made for you",
                    artworkUrl = quickPicks.firstOrNull()?.artworkUrl,
                    collection = "radio"
                )
            )
            add(
                FeedQuickTile(
                    title = "New releases",
                    subtitle = "Fresh drops",
                    artworkUrl = newReleases.firstOrNull()?.artworkUrl,
                    collection = "new_releases",
                )
            )
        }

        val tasteTags = tasteProfile?.topTags.orEmpty().take(8)
        val hasPersonalContent = tasteProfile?.hasPersonalSignals == true ||
            ytRecentSongs.isNotEmpty() || ytLikedSongs.isNotEmpty() || recentTracks.isNotEmpty()

        val result = FeedData(
            isYtConnected = isYtConnected,
            ytAccountName = ytAccountName.takeIf { isYtConnected },
            userName = resolvedUsername.takeIf { it.isNotBlank() },
            hasYtRecommendations = ytTaste?.feedTracks?.isNotEmpty() == true,
            hasYtMixes = false,
            hasPersonalContent = hasPersonalContent,
            tasteTags = tasteTags,
            ytSuggestedPlaylists = homePlaylists.filter { it.id != "LM" }.take(12),
            spotlight = spotlight,
            quickTiles = quickTiles,
            quickPicks = quickPicks,
            newReleases = newReleases,
            charts = charts,
            mixes = mixes,
            jumpBackIn = jumpBackIn,
            recentAlbums = recentAlbums,
            topArtists = topArtists,
            heavyRotation = heavyRotation,
            ytLikedSongs = ytLikedSongs,
            ytRecentSongs = ytRecentSongs,
            becauseYouListenTo = becauseSection,
            freshFinds = freshFinds,
            friends = friends.take(10),
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        ensureActive()
        cachedFeed = result
        cachedKey = cacheKey
        result
    }
}
