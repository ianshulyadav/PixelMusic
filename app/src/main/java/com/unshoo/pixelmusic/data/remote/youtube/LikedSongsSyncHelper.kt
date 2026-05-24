package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.SourceType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.absoluteValue

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LikedSongsSyncHelperEntryPoint {
    fun pixelMusicDatabase(): com.unshoo.pixelmusic.data.database.PixelMusicDatabase
    fun musicDao(): com.unshoo.pixelmusic.data.database.MusicDao
    fun favoritesDao(): com.unshoo.pixelmusic.data.database.FavoritesDao
}

object LikedSongsSyncHelper {
    private var lastSyncTime = 0L
    private val SYNC_INTERVAL = 2 * 60 * 1000L // 2 minutes

    fun syncLikedSongsIfNeeded(
        context: Context,
        scope: CoroutineScope
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime < SYNC_INTERVAL) {
            return
        }
        lastSyncTime = currentTime

        scope.launch(Dispatchers.IO) {
            try {
                val datastoreRepository = DatastoreRepository(context)
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    return@launch
                }

                // Fetch remote Liked Songs playlist ("LM")
                val responseJson = YoutubeRequestHelper.browse("LM", settings)
                val remoteSongs = YoutubeHelper.extractSongList(responseJson, settings)

                if (remoteSongs.isNotEmpty()) {
                    // 1. Sync to the YouTube module database (AppDatabase)
                    val localPlaylistRepository = AppDatabase.getInstance(context).playlistRepository()
                    val localSongRepository = AppDatabase.getInstance(context).songRepository()

                    val playlistInfo = PlaylistInfo(
                        id = "liked_songs",
                        title = "Liked Songs"
                    )
                    localPlaylistRepository.insertPlaylist(playlistInfo)

                    val remoteIds = remoteSongs.map { it.youtubeId }.toSet()
                    val currentSongs = localPlaylistRepository.getPlaylistById("liked_songs")?.songs ?: emptyList()

                    // Insert/update remote songs and add cross-refs
                    remoteSongs.forEach { song ->
                        localSongRepository.create(song)
                        localPlaylistRepository.insertCrossRef(
                            PlaylistSongCrossRef("liked_songs", song.youtubeId)
                        )
                    }

                    // Remove local cross-refs that are no longer in remote list
                    currentSongs.forEach { localSong ->
                        if (localSong.youtubeId !in remoteIds) {
                            localPlaylistRepository.deleteCrossRef("liked_songs", localSong.youtubeId)
                        }
                    }

                    // 2. Sync to the main App Database (PixelMusicDatabase) so it populates Favorites section
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        LikedSongsSyncHelperEntryPoint::class.java
                    )
                    val mainMusicDao = entryPoint.musicDao()
                    val mainFavoritesDao = entryPoint.favoritesDao()

                    val remoteUnifiedIds = remoteIds.map { toUnifiedYoutubeSongId(it) }.toSet()

                    // Fetch all current YouTube favorite song IDs in main DB
                    val currentFavIds = mainFavoritesDao.getFavoriteSongIdsOnce()
                    val currentYtFavIds = currentFavIds.filter { it < -15_000_000_000_000L && it >= -16_000_000_000_000L }.toSet()

                    val artistEntities = mutableListOf<ArtistEntity>()
                    val albumEntities = mutableListOf<AlbumEntity>()
                    val songEntities = mutableListOf<SongEntity>()
                    val crossRefs = mutableListOf<SongArtistCrossRef>()
                    val favoritesToInsert = mutableListOf<FavoritesEntity>()

                    remoteSongs.forEach { song ->
                        val songId = toUnifiedYoutubeSongId(song.youtubeId)
                        favoritesToInsert.add(FavoritesEntity(songId = songId, isFavorite = true))

                        val exists = mainMusicDao.getSongByIdOnce(songId) != null
                        if (!exists) {
                            val artistNames = parseYoutubeArtistNames(song.artist)
                            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
                            val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)

                            artistNames.forEach { name ->
                                artistEntities.add(
                                    ArtistEntity(
                                        id = toUnifiedYoutubeArtistId(name),
                                        name = name,
                                        trackCount = 0,
                                        imageUrl = null
                                    )
                                )
                            }

                            artistNames.forEachIndexed { index, name ->
                                val artistId = toUnifiedYoutubeArtistId(name)
                                crossRefs.add(
                                    SongArtistCrossRef(
                                        songId = songId,
                                        artistId = artistId,
                                        isPrimary = index == 0
                                    )
                                )
                            }

                            val albumId = toUnifiedYoutubeAlbumId("YouTube Music")
                            val albumName = "YouTube Music"
                            albumEntities.add(
                                AlbumEntity(
                                    id = albumId,
                                    title = albumName,
                                    artistName = primaryArtistName,
                                    artistId = primaryArtistId,
                                    songCount = 0,
                                    dateAdded = System.currentTimeMillis(),
                                    year = 0,
                                    albumArtUriString = upgradeThumbnailUrlToHighQuality(song.thumbnailHref)
                                )
                            )

                            val songEntity = SongEntity(
                                id = songId,
                                title = song.title,
                                artistName = song.artist.ifBlank { primaryArtistName },
                                artistId = primaryArtistId,
                                albumArtist = null,
                                albumName = albumName,
                                albumId = albumId,
                                contentUriString = "youtube://${song.youtubeId}",
                                albumArtUriString = upgradeThumbnailUrlToHighQuality(song.thumbnailPath ?: song.thumbnailHref),
                                duration = parseDurationStringToMillis(song.duration),
                                genre = song.genre?.takeIf { it.isNotBlank() } ?: "YouTube Music",
                                filePath = song.audioFilePath.orEmpty(),
                                parentDirectoryPath = "youtube://",
                                isFavorite = true,
                                lyrics = null,
                                trackNumber = 0,
                                year = 0,
                                dateAdded = System.currentTimeMillis(),
                                mimeType = "audio/webm",
                                bitrate = null,
                                sampleRate = null,
                                telegramChatId = null,
                                telegramFileId = null,
                                artistsJson = null,
                                sourceType = SourceType.YOUTUBE
                            )
                            songEntities.add(songEntity)
                        }
                    }

                    // Perform insertions/updates
                    if (artistEntities.isNotEmpty() || albumEntities.isNotEmpty() || songEntities.isNotEmpty()) {
                        mainMusicDao.incrementalSyncMusicData(
                            songs = songEntities,
                            albums = albumEntities,
                            artists = artistEntities,
                            crossRefs = crossRefs,
                            deletedSongIds = emptyList()
                        )
                    }

                    if (favoritesToInsert.isNotEmpty()) {
                        mainFavoritesDao.insertAll(favoritesToInsert)
                    }

                    // Remove favorites in main database that are no longer remotely liked
                    currentYtFavIds.forEach { localYtSongId ->
                        if (localYtSongId !in remoteUnifiedIds) {
                            mainFavoritesDao.removeFavorite(localYtSongId)
                        }
                    }
                }
            } catch (e: Exception) {
                UmihiHelper.printe("Failed to sync liked songs in background: ${e.message}")
            }
        }
    }

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long {
        return -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long {
        return -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeArtistId(artistName: String): Long {
        return -(17_000_000_000_000L + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun parseYoutubeArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+、]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private fun parseDurationStringToMillis(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toLong() * 1000L
                2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
                3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
