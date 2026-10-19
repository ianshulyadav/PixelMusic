/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */





package unshoo.ianshulyadav.pixelmusic.innertube.pages

import unshoo.ianshulyadav.pixelmusic.innertube.models.Album
import unshoo.ianshulyadav.pixelmusic.innertube.models.MusicResponsiveListItemRenderer
import unshoo.ianshulyadav.pixelmusic.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import unshoo.ianshulyadav.pixelmusic.innertube.models.MusicTwoRowItemRenderer
import unshoo.ianshulyadav.pixelmusic.innertube.models.Run
import unshoo.ianshulyadav.pixelmusic.innertube.models.splitBySeparator
import unshoo.ianshulyadav.pixelmusic.innertube.models.Artist
import unshoo.ianshulyadav.pixelmusic.innertube.models.oddElements
import unshoo.ianshulyadav.pixelmusic.innertube.utils.parseTime

object PageHelper {
    fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
        val filteredRuns = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }

    private val VIEWS_REGEX = Regex("""^\s*\d+([.,]\d+)?\s*(K|M|B|k|m|b|views|plays|visualizaciones|vues)?\s*$""", RegexOption.IGNORE_CASE)

    private fun isAlbumEndpoint(run: Run): Boolean {
        val endpoint = run.navigationEndpoint?.browseEndpoint ?: return false
        val pageType = endpoint.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        return endpoint.isAlbumEndpoint ||
                endpoint.browseId.startsWith("MPREb_") ||
                (pageType != null && "ALBUM" in pageType)
    }

    private fun isArtistEndpoint(run: Run): Boolean {
        val endpoint = run.navigationEndpoint?.browseEndpoint ?: return false
        val pageType = endpoint.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        return endpoint.isArtistEndpoint ||
                endpoint.browseId.startsWith("UC") ||
                (pageType != null && "ARTIST" in pageType)
    }

    private fun isNonAlbumMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed == "•") return true
        if (trimmed.parseTime() != null) return true
        if (VIEWS_REGEX.matches(trimmed)) return true
        return false
    }

    private fun isMediaTypeMarker(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower in setOf(
            "song", "songs", "video", "videos", "single", "singles", "album", "albums", "ep",
            "canción", "chanson", "lied", "canzone", "música", "music"
        )
    }

    private fun List<Run>.toArtistsList(): List<Artist> =
        this
            .oddElements()
            .mapNotNull { run ->
                val cleanName = run.text.trim()
                if (cleanName.isNotBlank() && cleanName != "•" && cleanName != "&" && cleanName != ",") {
                    Artist(
                        name = cleanName,
                        id = run.navigationEndpoint?.browseEndpoint?.browseId
                    )
                } else null
            }

    fun extractArtists(
        renderer: MusicResponsiveListItemRenderer,
        title: String? = null
    ): List<Artist> {
        val rawGroups = renderer.flexColumns
            .drop(1)
            .flatMap {
                it.musicResponsiveListItemFlexColumnRenderer.text?.runs?.splitBySeparator().orEmpty()
            }

        if (rawGroups.isEmpty()) return emptyList()

        // 1. Definite match: Find group containing an artist endpoint (UC... or pageType ARTIST)
        for (group in rawGroups) {
            val hasArtist = group.any { isArtistEndpoint(it) }
            if (hasArtist) {
                val artists = group.toArtistsList()
                if (artists.isNotEmpty()) return artists
            }
        }

        // 2. Fallback: Filter out media type, album, title-duplicate, duration, and view-count groups
        val normalizedTitle = title?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "")
        val candidateGroup = rawGroups.firstOrNull { group ->
            val firstRun = group.firstOrNull() ?: return@firstOrNull false
            val text = firstRun.text.trim()
            val normalizedText = text.lowercase().replace(Regex("[^a-z0-9]"), "")

            // Reject if it matches the song title
            if (!normalizedTitle.isNullOrEmpty() && (normalizedText == normalizedTitle || text.equals(title?.trim(), ignoreCase = true))) {
                return@firstOrNull false
            }

            // Reject album endpoints
            if (group.any { isAlbumEndpoint(it) }) return@firstOrNull false

            // Reject duration and view count
            if (isNonAlbumMetadata(text)) return@firstOrNull false

            // Reject standalone media type markers
            if (isMediaTypeMarker(text)) return@firstOrNull false

            true
        }

        return candidateGroup?.toArtistsList().orEmpty()
    }

    fun extractAlbum(renderer: MusicResponsiveListItemRenderer): Album? {
        // 1. First priority: Check all flex columns for an explicit Album browse endpoint
        for (column in renderer.flexColumns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs ?: continue
            for (run in runs) {
                if (isAlbumEndpoint(run)) {
                    return Album(name = run.text.trim(), id = run.navigationEndpoint?.browseEndpoint?.browseId)
                }
            }
        }

        // 2. Check flexColumns 2, 3, etc. (often dedicate a whole column to Album)
        for (i in 2 until renderer.flexColumns.size) {
            val run = renderer.flexColumns[i].musicResponsiveListItemFlexColumnRenderer.text?.runs?.firstOrNull { it.text.isNotBlank() }
            if (run != null && !isArtistEndpoint(run) && !isNonAlbumMetadata(run.text)) {
                return Album(
                    name = run.text.trim(),
                    id = run.navigationEndpoint?.browseEndpoint?.browseId
                )
            }
        }

        // 3. Check split runs in flex column 1 (e.g. [Artists, Album, Duration/Views])
        val col1Runs = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
        if (col1Runs != null) {
            val segments = col1Runs.splitBySeparator()
            if (segments.size >= 3) {
                // Segment 0 is usually Artists; segment 1 is often Album; last segment is Duration/Views
                val candidateRun = segments.getOrNull(1)?.firstOrNull { it.text.isNotBlank() }
                if (candidateRun != null && !isArtistEndpoint(candidateRun) && !isNonAlbumMetadata(candidateRun.text)) {
                    return Album(
                        name = candidateRun.text.trim(),
                        id = candidateRun.navigationEndpoint?.browseEndpoint?.browseId
                    )
                }
            }
        }

        return null
    }

    fun extractAlbumFromRuns(runs: List<Run>?): Album? {
        if (runs.isNullOrEmpty()) return null

        // 1. Explicit Album endpoint check
        for (run in runs) {
            if (isAlbumEndpoint(run)) {
                return Album(name = run.text.trim(), id = run.navigationEndpoint?.browseEndpoint?.browseId)
            }
        }

        // 2. Subtitle segments check
        val segments = runs.splitBySeparator()
        if (segments.size >= 3) {
            val candidateRun = segments.getOrNull(1)?.firstOrNull { it.text.isNotBlank() }
            if (candidateRun != null && !isArtistEndpoint(candidateRun) && !isNonAlbumMetadata(candidateRun.text)) {
                return Album(
                    name = candidateRun.text.trim(),
                    id = candidateRun.navigationEndpoint?.browseEndpoint?.browseId
                )
            }
        } else if (segments.size >= 4) {
            val candidateRun = segments.getOrNull(2)?.firstOrNull { it.text.isNotBlank() }
            if (candidateRun != null && !isArtistEndpoint(candidateRun) && !isNonAlbumMetadata(candidateRun.text)) {
                return Album(
                    name = candidateRun.text.trim(),
                    id = candidateRun.navigationEndpoint?.browseEndpoint?.browseId
                )
            }
        }

        return null
    }

    fun extractAlbum(renderer: MusicTwoRowItemRenderer): Album? {
        return extractAlbumFromRuns(renderer.subtitle?.runs)
    }
}