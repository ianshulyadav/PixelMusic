package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.absoluteValue

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks: StateFlow<List<Song>> = _quickPicks.asStateFlow()

    init {
        loadQuickPicks()
    }

    private fun loadQuickPicks() {
        viewModelScope.launch {
            // 1. Instantly load from local Room database fallback
            try {
                val cachedEntities = musicDao.quickPicks(limit = 20).first()
                if (cachedEntities.isNotEmpty()) {
                    _quickPicks.value = cachedEntities.map { it.toSong() }
                    Timber.tag("QuickPicks").d("Loaded %d quick picks from local database fallback", cachedEntities.size)
                }
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error loading local quick picks")
            }

            // 2. Fetch fresh Quick Picks online from YouTube Music
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val homeResult = YouTube.home().getOrNull()
                    if (homeResult != null) {
                        val quickPicksSection = homeResult.sections.firstOrNull {
                            it.title.contains("picks", ignoreCase = true) || 
                            it.title.contains("mix", ignoreCase = true)
                        }
                        if (quickPicksSection != null) {
                            val songItems = quickPicksSection.items.filterIsInstance<SongItem>()
                            if (songItems.isNotEmpty()) {
                                val nativeSongs = songItems.map { it.toNativeSong() }
                                
                                // Update flow on Main thread
                                withContext(Dispatchers.Main) {
                                    _quickPicks.value = nativeSongs
                                }

                                // 3. Save these fetched songs in the local database
                                saveSongsToDb(nativeSongs)

                                // 4. Pre-resolve stream URLs and force save highest quality album art in parallel
                                preResolveAndCacheSongs(nativeSongs)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("QuickPicks").e(e, "Error fetching online quick picks from YTMusic")
                }
            }
        }
    }

    private suspend fun saveSongsToDb(songs: List<Song>) {
        try {
            val songEntities = mutableListOf<SongEntity>()
            val albumEntities = mutableListOf<AlbumEntity>()
            val artistEntities = mutableListOf<ArtistEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()

            songs.forEach { song ->
                val videoId = song.youtubeId ?: return@forEach
                val songLongId = getDatabaseIdForYoutubeId(videoId)
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
                        albumArtUriString = song.albumArtUriString,
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
                        albumArtUriString = song.albumArtUriString,
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
                        mimeType = "audio/webm",
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
            }

            musicDao.insertArtists(artistEntities)
            musicDao.insertAlbums(albumEntities)
            musicDao.insertSongs(songEntities)
            musicDao.insertSongArtistCrossRefs(crossRefs)
            Timber.tag("QuickPicks").d("Successfully cached %d online quick picks into DB", songs.size)
        } catch (e: Exception) {
            Timber.tag("QuickPicks").e(e, "Error saving online quick picks to local DB")
        }
    }

    private suspend fun preResolveAndCacheSongs(songs: List<Song>) {
        withContext(Dispatchers.IO) {
            val jobs = songs.map { song ->
                async {
                    try {
                        val videoId = song.youtubeId ?: return@async
                        val ytSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                            youtubeId = videoId,
                            title = song.title,
                            artist = song.artist,
                            thumbnailHref = song.albumArtUriString ?: ""
                        )
                        // Pre-resolve high-quality stream URL (saves to DB and LRU cache automatically)
                        YoutubeHelper.getHighestQualityStreamUrl(context, ytSong)

                        // Cache highest quality album art in DB
                        val highQualityArt = upgradeThumbnailUrlToHighQuality(song.albumArtUriString)
                        if (highQualityArt != null && highQualityArt != song.albumArtUriString) {
                            musicDao.updateSongAlbumArt(getDatabaseIdForYoutubeId(videoId), highQualityArt)
                        }
                    } catch (e: Exception) {
                        Timber.tag("QuickPicks").w("Failed to pre-resolve stream/art for Quick Pick: %s", song.title)
                    }
                }
            }
            jobs.awaitAll()
            Timber.tag("QuickPicks").d("Finished parallel background pre-resolution for %d Quick Picks", songs.size)
        }
    }

    private fun getDatabaseIdForYoutubeId(youtubeId: String): Long {
        val YOUTUBE_SONG_ID_OFFSET = 15_000_000_000_000L
        return -(YOUTUBE_SONG_ID_OFFSET + youtubeId.hashCode().toLong().absoluteValue)
    }
}
