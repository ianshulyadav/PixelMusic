package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printe
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.RelatedSongMap
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder

/**
 * AutoQueueManager — Radio Mode (ArchiveTune 2026 Engine)
 *
 * Maintains a constant upcoming playback queue of 40–50 songs.
 * Automatically appends related songs when the remaining count falls below 40.
 * Supports online related tracks (YouTube Music next) and offline hybrid local fallback.
 */
object AutoQueueManager {

    private const val TARGET_QUEUE_SIZE = 45 // Targets exactly 45 upcoming songs
    private const val MAX_HISTORY = 150

    private var fetchJob: Job? = null
    private var lastFetchedVideoId: String? = null
    private var continuationToken: String? = null
    private var currentWatchEndpoint: WatchEndpoint? = null
    private val addedVideoIds = mutableSetOf<String>()

    // Memory cache mapping local/offline song IDs to matched YouTube video IDs
    private val localToYoutubeIdMap = mutableMapOf<String, String>()
    
    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var musicDaoRef: MusicDao? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            checkAndRefillQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                checkAndRefillQueue()
            }
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao
    ) {
        scope = coroutineScope
        contextRef = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        musicDaoRef = musicDao
        player.addListener(playerListener)
        printd("AutoQueueManager attached")
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
            printd("AutoQueueManager player updated")
        }
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        fetchJob?.cancel()
        scope = null
        contextRef = null
        datastoreRepository = null
        musicDaoRef = null
    }

    fun reset() {
        lastFetchedVideoId = null
        continuationToken = null
        currentWatchEndpoint = null
        addedVideoIds.clear()
        fetchJob?.cancel()
    }

    private fun checkAndRefillQueue() {
        forceRefill(forceRefresh = false)
    }

    fun forceRefill(forceRefresh: Boolean) {
        val currentScope = scope ?: return
        val player = playerRef ?: return

        currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.autoQueueEnabled) return@launch

            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val currentIndex = player.currentMediaItemIndex
                    val totalCount = player.mediaItemCount
                    val remaining = totalCount - currentIndex - 1
                    val currentId = player.currentMediaItem?.mediaId
                    Triple(remaining, currentId, totalCount)
                }
            } ?: return@launch

            val (remaining, currentId, _) = playerState
            if (currentId == null) return@launch

            if (forceRefresh) {
                fetchJob?.cancel()
            } else {
                if (fetchJob?.isActive == true) return@launch
            }

            fetchJob = launch(Dispatchers.IO) {
                refillQueueLoop(currentId, forceRefresh)
            }
        }
    }

    private suspend fun refillQueueLoop(currentId: String, forceRefresh: Boolean) {
        val player = playerRef ?: return
        val dao = musicDaoRef ?: return
        val context = contextRef ?: return

        val entryPoint = try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                YoutubeHelperEntryPoint::class.java
            )
        } catch (e: Exception) {
            null
        }
        val connectivityStateHolder = entryPoint?.connectivityStateHolder()
        connectivityStateHolder?.initialize()

        // 1. Identify if current song is YouTube or local/offline
        val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem }
        val playbackUriStr = currentMediaItem?.localConfiguration?.uri?.toString()
        val metadataUriStr = currentMediaItem?.mediaMetadata?.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI")
        val contentUriStr = playbackUriStr ?: metadataUriStr

        var rawVideoId: String? = if (currentId.startsWith("youtube_")) {
            currentId.substringAfter("youtube_")
        } else if (contentUriStr?.startsWith("youtube://") == true) {
            contentUriStr.removePrefix("youtube://")
        } else {
            null
        }

        if (rawVideoId == null) {
            val songId = currentId.toLongOrNull()
            if (songId != null) {
                val dbSong = dao.getSongByIdOnce(songId)
                if (dbSong?.contentUriString?.startsWith("youtube://") == true) {
                    rawVideoId = dbSong.contentUriString.removePrefix("youtube://")
                }
            }
        }
        val isLocal = rawVideoId == null
        val resolvedVideoId = rawVideoId ?: ""

        if (forceRefresh) {
            lastFetchedVideoId = if (isLocal) currentId else resolvedVideoId
            continuationToken = null
            currentWatchEndpoint = null
            addedVideoIds.clear()
            addedVideoIds.add(lastFetchedVideoId!!)
        } else {
            val activeId = if (isLocal) currentId else resolvedVideoId
            if (activeId != lastFetchedVideoId) {
                lastFetchedVideoId = activeId
                continuationToken = null
                currentWatchEndpoint = null
                addedVideoIds.clear()
                addedVideoIds.add(activeId)
            }
        }

        var loopCount = 0
        while (true) {
            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val currentIndex = player.currentMediaItemIndex
                    val totalCount = player.mediaItemCount
                    val remaining = totalCount - currentIndex - 1
                    Pair(remaining, totalCount)
                }
            } ?: break

            val (remaining, totalCount) = playerState
            if (remaining >= TARGET_QUEUE_SIZE) {
                printd("AutoQueueManager: Queue is full. Current remaining: $remaining (>= $TARGET_QUEUE_SIZE)")
                break
            }

            if (loopCount >= 8) {
                printd("AutoQueueManager: Max loop count reached. Breaking.")
                break
            }
            loopCount++

            printd("AutoQueueManager: Refilling queue. Remaining: $remaining, Target: $TARGET_QUEUE_SIZE, Loop: $loopCount")

            val isOnline = connectivityStateHolder?.isOnline?.value ?: true

            if (!isOnline) {
                printd("AutoQueueManager: Device is offline. Appending local related songs.")
                fetchAndAppendLocal(currentId, player)
                continue
            }

            // Online flow: Resolve YouTube Video ID for the song
            var matchedVideoId: String? = null
            if (!isLocal) {
                matchedVideoId = resolvedVideoId
            } else {
                matchedVideoId = localToYoutubeIdMap[currentId]
                if (matchedVideoId == null) {
                    printd("AutoQueueManager: Matching local song $currentId to YouTube online.")
                    val songId = currentId.toLongOrNull()
                    val localSong = if (songId != null) dao.getSongByIdOnce(songId) else null
                    if (localSong != null) {
                        val query = "${localSong.title} ${localSong.artistName}"
                        val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val firstSongItem = searchResult?.items?.firstOrNull { it is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem } as? unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
                        if (firstSongItem != null) {
                            matchedVideoId = firstSongItem.id
                            localToYoutubeIdMap[currentId] = matchedVideoId
                            printd("AutoQueueManager: Successfully matched $currentId -> $matchedVideoId")
                        }
                    }
                }
            }

            if (matchedVideoId != null) {
                printd("AutoQueueManager: Fetching related songs online for YouTube ID: $matchedVideoId")
                val success = fetchAndAppendOnline(matchedVideoId, player)
                if (!success) {
                    printd("AutoQueueManager: Online related fetch failed. Falling back to local related.")
                    fetchAndAppendLocal(currentId, player)
                }
            } else {
                printd("AutoQueueManager: No YouTube match found for local song. Falling back to local related.")
                fetchAndAppendLocal(currentId, player)
            }
        }
    }

    private suspend fun fetchAndAppendOnline(videoId: String, player: Player): Boolean {
        try {
            val endpoint = currentWatchEndpoint ?: WatchEndpoint(videoId = videoId)
            val result = YouTube.next(endpoint = endpoint, continuation = continuationToken, followAutomixPreview = true)
            
            var fetchSuccess = false
            result.onSuccess { nextResult ->
                continuationToken = nextResult.continuation
                currentWatchEndpoint = nextResult.endpoint
                
                val addedVideoIdsLocal = addedVideoIds
                val filteredItems = nextResult.items
                    .filter { it.id !in addedVideoIdsLocal }

                if (filteredItems.isEmpty()) {
                    printd("AutoQueueManager: No new related songs found from YouTube")
                    return@onSuccess
                }

                // Add to history
                filteredItems.forEach { addedVideoIds.add(it.id) }
                if (addedVideoIds.size > MAX_HISTORY) {
                    val excess = addedVideoIds.size - MAX_HISTORY
                    val toRemove = addedVideoIds.take(excess)
                    addedVideoIds.removeAll(toRemove.toSet())
                }

                // Map to native Songs and MediaItems
                val nativeSongs = filteredItems.map { it.toNativeSong() }
                val mediaItems = nativeSongs.map { MediaItemBuilder.build(it) }

                // Insert into the local database to preserve related maps and enable Quick Picks
                saveRelatedSongsToDb(videoId, nativeSongs, player)

                withContext(Dispatchers.Main) {
                    player.addMediaItems(mediaItems)
                }
                
                printd("AutoQueueManager: Appended ${mediaItems.size} online related songs to queue")
                fetchSuccess = true
            }.onFailure { e ->
                printe("AutoQueueManager: Failed to fetch related: ${e.message}")
            }
            return fetchSuccess
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching related songs: ${e.message}")
            return false
        }
    }

    private suspend fun fetchAndAppendLocal(songIdStr: String, player: Player) {
        val dao = musicDaoRef ?: return
        try {
            val songId = songIdStr.toLongOrNull()
            val currentSong = if (songId != null) dao.getSongByIdOnce(songId) else null
            
            // Get current queue IDs to avoid duplicates
            val currentQueueIds = withContext(Dispatchers.Main) {
                if (playerRef == null) emptySet()
                else (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId }.toSet()
            }
            
            var filtered = emptyList<SongEntity>()

            if (currentSong != null) {
                // Get local related songs based on artist, album, genre, and playlists
                val relatedEntities = dao.getLocalRelatedSongs(
                    songId = currentSong.id,
                    artistId = currentSong.artistId,
                    albumId = currentSong.albumId,
                    genre = currentSong.genre,
                    limit = 10
                ).toMutableList()
                
                filtered = relatedEntities.filter { 
                    it.id.toString() !in addedVideoIds && it.id.toString() !in currentQueueIds
                }
            }
            
            // Solid offline fallback: if we don't have enough specific related songs, query any random local songs
            if (filtered.size < 5) {
                val allLocalSongs = dao.getAllSongsList()
                val extraLocal = allLocalSongs.filter {
                    it.id.toString() !in addedVideoIds && it.id.toString() !in currentQueueIds && (currentSong == null || it.id != currentSong.id)
                }.shuffled().take(15)
                filtered = (filtered + extraLocal).distinctBy { it.id }
            }
            
            if (filtered.isEmpty()) {
                printd("AutoQueueManager: No local fallback songs found in database")
                return
            }
            
            filtered.forEach { addedVideoIds.add(it.id.toString()) }
            if (addedVideoIds.size > MAX_HISTORY) {
                val excess = addedVideoIds.size - MAX_HISTORY
                val toRemove = addedVideoIds.take(excess)
                addedVideoIds.removeAll(toRemove.toSet())
            }
            
            val nativeSongs = filtered.map { it.toSong() }
            val mediaItems = nativeSongs.map { MediaItemBuilder.build(it) }
            
            withContext(Dispatchers.Main) {
                player.addMediaItems(mediaItems)
            }
            printd("AutoQueueManager: Appended ${mediaItems.size} local related songs to queue")
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching local related songs: ${e.message}")
        }
    }

    private suspend fun saveRelatedSongsToDb(sourceVideoId: String, relatedSongs: List<Song>, player: Player) {
        val dao = musicDaoRef ?: return

        try {
            val sourceLongId = getDatabaseIdForYoutubeId(sourceVideoId)
            
            val songEntities = mutableListOf<SongEntity>()
            val albumEntities = mutableListOf<AlbumEntity>()
            val artistEntities = mutableListOf<ArtistEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()
            val relatedMaps = mutableListOf<RelatedSongMap>()

            withContext(Dispatchers.IO) {
                // Check if source song exists in DB, if not, insert it first!
                val exists = dao.getSongByIdOnce(sourceLongId) != null
                if (!exists) {
                    val currentMediaItem = withContext(Dispatchers.Main) {
                        player.currentMediaItem
                    }
                    if (currentMediaItem != null) {
                        val title = currentMediaItem.mediaMetadata.title?.toString() ?: ""
                        val artist = currentMediaItem.mediaMetadata.artist?.toString() ?: ""
                        val artistLongId = artist.hashCode().toLong()
                        val album = currentMediaItem.mediaMetadata.albumTitle?.toString() ?: "YouTube Music"
                        val albumLongId = album.hashCode().toLong()
                        
                        val sourceArtist = ArtistEntity(id = artistLongId, name = artist, trackCount = 1, imageUrl = null)
                        val sourceAlbum = AlbumEntity(
                            id = albumLongId,
                            title = album,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(currentMediaItem.mediaMetadata.artworkUri?.toString()),
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = artist
                        )
                        val sourceSong = SongEntity(
                            id = sourceLongId,
                            title = title,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtist = artist,
                            albumName = album,
                            albumId = albumLongId,
                            contentUriString = "youtube://$sourceVideoId",
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(currentMediaItem.mediaMetadata.artworkUri?.toString()),
                            duration = player.duration.coerceAtLeast(0L),
                            genre = "YouTube",
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/mpeg",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                        val sourceCrossRef = SongArtistCrossRef(songId = sourceLongId, artistId = artistLongId, isPrimary = true)
                        
                        dao.insertArtists(listOf(sourceArtist))
                        dao.insertAlbums(listOf(sourceAlbum))
                        dao.insertSongs(listOf(sourceSong))
                        dao.insertSongArtistCrossRefs(listOf(sourceCrossRef))
                    }
                }

                relatedSongs.forEach { song ->
                    val songVideoId = song.youtubeId ?: song.id.substringAfter("youtube_")
                    val songLongId = getDatabaseIdForYoutubeId(songVideoId)
                    val artistLongId = song.artistId
                    val albumLongId = song.albumId

                    artistEntities.add(
                        ArtistEntity(
                            id = artistLongId,
                            name = song.artist,
                            trackCount = 1,
                            imageUrl = null
                        )
                    )

                    albumEntities.add(
                        AlbumEntity(
                            id = albumLongId,
                            title = song.album,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(song.albumArtUriString),
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = song.artist
                        )
                    )

                    songEntities.add(
                        SongEntity(
                            id = songLongId,
                            title = song.title,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtist = song.artist,
                            albumName = song.album,
                            albumId = albumLongId,
                            contentUriString = song.contentUriString,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(song.albumArtUriString),
                            duration = song.duration,
                            genre = song.genre,
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/mpeg",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                    )

                    crossRefs.add(
                        SongArtistCrossRef(
                            songId = songLongId,
                            artistId = artistLongId,
                            isPrimary = true
                        )
                    )

                    relatedMaps.add(
                        RelatedSongMap(
                            songId = sourceLongId,
                            relatedSongId = songLongId
                        )
                    )
                }

                // Strictly insert artist/album first due to Foreign Key constraints referenced in SongEntity
                dao.insertArtists(artistEntities.distinctBy { it.id })
                dao.insertAlbums(albumEntities.distinctBy { it.id })
                dao.insertSongs(songEntities.distinctBy { it.id })
                dao.insertSongArtistCrossRefs(crossRefs.distinct())
                dao.insertRelatedSongMaps(relatedMaps.distinct())
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Error saving related songs to DB: ${e.message}")
        }
    }

    private fun getDatabaseIdForYoutubeId(youtubeId: String): Long {
        val YOUTUBE_SONG_ID_OFFSET = 15_000_000_000_000L
        return -(YOUTUBE_SONG_ID_OFFSET + youtubeId.hashCode().toLong().absoluteValue)
    }
}
