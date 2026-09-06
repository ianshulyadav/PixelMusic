package com.unshoo.pixelmusic.data.backup.module

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.isSmartPlaylistSource
import com.unshoo.pixelmusic.data.backup.model.BackupSection
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.SongSummary
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.PreferenceBackupEntry
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        val playlists = allPlaylists.filter { isBackedUpPlaylistSource(it.source) }

        val cloudSongIds = buildCloudSongIdSet()

        val allLocalSummaries = musicDao.getAllLocalSongSummaries()
        val summaryById = allLocalSummaries.associateBy { it.id.toString() }

        val songMetadata = mutableMapOf<String, SongMetadataEntry>()
        val filteredPlaylists = playlists.map { playlist ->
            val localSongIds = playlist.songIds.filter { id -> id !in cloudSongIds }
            localSongIds.forEach { id ->
                if (id !in songMetadata) {
                    summaryById[id]?.let { summary ->
                        songMetadata[id] = SongMetadataEntry(
                            title = summary.title,
                            artist = summary.artistName,
                            album = summary.albumName,
                            duration = summary.duration
                        )
                    }
                }
            }
            playlist.copy(songIds = localSongIds)
        }

        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .count { isBackedUpPlaylistSource(it.source) }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        playlists + orderModes.size + if (sortOption.isNotBlank()) 1 else 0
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        val payload = PlaylistsBackupPayload(
            playlists = playlistPreferencesRepository.getPlaylistsOnce(),
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        )
        gson.toJson(payload)
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrElse { e ->
            throw IllegalStateException("Playlists payload could not be parsed: ${e.message}", e)
        } ?: throw IllegalStateException("Playlists payload could not be parsed: empty JSON document")

        val backupPlaylists = parsed.playlists.orEmpty()
        val songMetadata = parsed.songMetadata
        val coverImages = parsed.coverImages

        val resolvedPlaylists = if (songMetadata != null && songMetadata.isNotEmpty()) {
            resolvePlaylists(backupPlaylists, songMetadata)
        } else {
            backupPlaylists
        }

        val finalPlaylists = if (coverImages != null && coverImages.isNotEmpty()) {
            restoreCoverImages(resolvedPlaylists, coverImages)
        } else {
            resolvedPlaylists
        }

        playlistPreferencesRepository.replaceAllPlaylists(finalPlaylists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read cover image: $path")
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to restore cover image for playlist ${playlist.id}")
                playlist.copy(coverImageUri = null)
            }
        }
    }

    /**
     * Resolves backup song IDs to current device song IDs using metadata matching.
     *
     * Strategy:
     * 1. Direct ID match + metadata verification → confirmed
     * 2. If direct ID exists but metadata doesn't match → try metadata match (avoids false positives)
     * 3. If direct ID doesn't exist → try metadata match
     * 4. Metadata match: title + artist (case-insensitive), disambiguate with album + duration
     * 5. No confident match → song is dropped from the playlist (kept as unresolved would risk false matches)
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, SongMetadataEntry>
    ): List<Playlist> {
        val localSummaries = musicDao.getAllLocalSongSummaries()
        val currentSongsById = localSummaries.associateBy { it.id.toString() }

        val metadataIndex = mutableMapOf<String, MutableList<SongSummary>>()
        localSummaries.forEach { song ->
            val key = normalizeMatchKey(song.title, song.artistName)
            metadataIndex.getOrPut(key) { mutableListOf() }.add(song)
        }

        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = resolveSongId(songId, songMetadata, currentSongsById, metadataIndex)
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Timber.tag(TAG).w("Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        return playlists.map { playlist ->
            val resolvedSongIds = playlist.songIds.mapNotNull { songId ->
                resolutionCache[songId]
            }
            playlist.copy(songIds = resolvedSongIds)
        }
    }

    private fun resolveSongId(
        backupSongId: String,
        songMetadata: Map<String, SongMetadataEntry>,
        currentSongsById: Map<String, SongSummary>,
        metadataIndex: Map<String, List<SongSummary>>
    ): String? {
        val meta = songMetadata[backupSongId]

        val directMatch = currentSongsById[backupSongId]
        if (directMatch != null) {
            if (meta == null) {
                return backupSongId
            }
            if (metadataMatches(meta, directMatch)) {
                return backupSongId
            }
        }

        if (meta == null) {
            return if (directMatch != null) backupSongId else null
        }

        val matchKey = normalizeMatchKey(meta.title, meta.artist)
        val candidates = metadataIndex[matchKey] ?: return null

        if (candidates.size == 1) {
            return candidates[0].id.toString()
        }

        val albumMatch = candidates.filter { candidate ->
            normalizeText(candidate.albumName) == normalizeText(meta.album)
        }
        if (albumMatch.size == 1) {
            return albumMatch[0].id.toString()
        }

        val durationCandidates = (albumMatch.ifEmpty { candidates }).filter { candidate ->
            kotlin.math.abs(candidate.duration - meta.duration) <= DURATION_TOLERANCE_MS
        }
        if (durationCandidates.size == 1) {
            return durationCandidates[0].id.toString()
        }

        return null
    }

    private fun metadataMatches(meta: SongMetadataEntry, song: SongSummary): Boolean {
        return normalizeText(meta.title) == normalizeText(song.title) &&
            normalizeText(meta.artist) == normalizeText(song.artistName)
    }

    private fun normalizeMatchKey(title: String, artist: String): String {
        return "${normalizeText(title)}|${normalizeText(artist)}"
    }

    private fun normalizeText(text: String): String {
        return text.trim().lowercase()
    }

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllNavidromeSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllJellyfinSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    private suspend fun restoreLegacyPreferenceEntries(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    /** Song metadata stored alongside playlists for cross-device matching. */
    data class SongMetadataEntry(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long
    )

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, SongMetadataEntry>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val DURATION_TOLERANCE_MS = 2000L

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private fun isBackedUpPlaylistSource(source: String): Boolean =
            source == "LOCAL" || isSmartPlaylistSource(source)

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY
        )
    }
}
