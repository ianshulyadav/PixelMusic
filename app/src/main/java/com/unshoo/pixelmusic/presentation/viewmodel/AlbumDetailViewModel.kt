package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.MusicRepository // Importar MusicRepository
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube as InnerTubeYouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.unshoo.pixelmusic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.LibraryMembershipEntity
import com.unshoo.pixelmusic.data.database.toEntity
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import kotlin.math.absoluteValue

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLiked: Boolean = false
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val engagementDao: EngagementDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()
    val isAlbumLiked: StateFlow<Boolean> = _uiState.map { it.isLiked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            val mappedBrowseId = albumId?.let {
                SearchStateHolder.albumIdMap[it] ?: AlbumIdMapper.getBrowseId(context, it)
            }
            if (mappedBrowseId != null) {
                loadOnlineAlbumData(mappedBrowseId)
            } else if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                loadOnlineAlbumData(albumIdString)
            }
            observeLikedState(albumIdString)
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_id_not_found), isLoading = false) }
        }
    }

    private fun observeLikedState(albumIdString: String) {
        val albumId = albumIdString.toLongOrNull()
        val browseId = albumId?.let {
            SearchStateHolder.albumIdMap[it] ?: AlbumIdMapper.getBrowseId(context, it)
        } ?: albumIdString

        viewModelScope.launch {
            userPreferencesRepository.likedAlbumIdsFlow.collect { likedSet ->
                val isLikedNow = likedSet.contains(browseId)
                _uiState.update { it.copy(isLiked = isLikedNow) }
            }
        }
    }

    fun toggleAlbumLikeStatus(album: Album) {
        val albumIdString: String = savedStateHandle.get("albumId") ?: return
        viewModelScope.launch {
            val isCurrentlyLiked = _uiState.value.isLiked
            val newLikedState = !isCurrentlyLiked

            val albumId = albumIdString.toLongOrNull() ?: album.id
            val browseId = SearchStateHolder.albumIdMap[album.id]
                ?: AlbumIdMapper.getBrowseId(context, album.id)
                ?: (if (albumIdString.toLongOrNull() == null) albumIdString else null)
                ?: albumIdString

            // 1. Update local preferences
            userPreferencesRepository.setLikedAlbum(browseId, newLikedState)

            // 2. Sync with YouTube if it is a YouTube album and logged in
            if (browseId.toLongOrNull() == null && InnerTubeYouTube.hasLoginCookie()) {
                withContext(Dispatchers.IO) {
                    InnerTubeYouTube.likePlaylist(browseId, newLikedState)
                }
            }

            // 3. Update local database cache
            withContext(Dispatchers.IO) {
                AlbumIdMapper.putMapping(context, album.id, browseId)
                val primaryArtistId = YouTubeIdUtils.toUnifiedYoutubeArtistId(album.artist)
                val albumEntity = album.toEntity(artistIdForAlbum = primaryArtistId)
                musicRepository.insertAlbums(listOf(albumEntity))

                val albumKey = "album_${album.id}"
                if (newLikedState) {
                    engagementDao.insertLibraryMembership(
                        LibraryMembershipEntity(songKey = albumKey, firstPlayedTimestamp = System.currentTimeMillis())
                    )
                    val currentSongs = _uiState.value.songs
                    if (currentSongs.isNotEmpty()) {
                        val memberships = mutableListOf<LibraryMembershipEntity>()
                        currentSongs.forEach { s ->
                            val ytId = s.youtubeId ?: if (s.id.startsWith("youtube_")) s.id.removePrefix("youtube_") else null
                            if (ytId != null) {
                                memberships.add(LibraryMembershipEntity(songKey = "youtube://$ytId", firstPlayedTimestamp = System.currentTimeMillis()))
                                memberships.add(LibraryMembershipEntity(songKey = s.id, firstPlayedTimestamp = System.currentTimeMillis()))
                            }
                        }
                        engagementDao.insertLibraryMemberships(memberships)
                    }
                } else {
                    engagementDao.deleteLibraryMembershipByKey(albumKey)
                }
            }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)

                    combine(albumDetailsFlow, albumSongsFlow) { album, songs ->
                        if (album != null) {
                            val sortedSongs = songs.sortedWith(
                                compareBy<Song> { it.discNumber ?: 1 }
                                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                    .thenBy { it.title.lowercase() }
                            )
                            AlbumDetailUiState(
                                album = album,
                                songs = sortedSongs,
                                isLoading = false
                            )
                        } else {
                            AlbumDetailUiState(
                                error = context.getString(R.string.album_not_found),
                                isLoading = false
                            )
                        }
                    }
                        .catch { e ->
                            emit(
                                AlbumDetailUiState(
                                    error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                                    isLoading = false
                                )
                            )
                        }
                        .flowOn(Dispatchers.Default)
                        .collect { newState ->
                            _uiState.value = newState
                        }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    fun retry() {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            val mappedBrowseId = albumId?.let {
                SearchStateHolder.albumIdMap[it] ?: AlbumIdMapper.getBrowseId(context, it)
            }
            if (mappedBrowseId != null) {
                loadOnlineAlbumData(mappedBrowseId)
            } else if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                loadOnlineAlbumData(albumIdString)
            }
        }
    }

    private fun sanitizeErrorMessage(e: Throwable, fallback: String): String {
        val msg = e.message ?: e.localizedMessage ?: return fallback
        return when {
            msg.contains("400") || msg.contains("INVALID_ARGUMENT") || msg.contains("Client request") ->
                "This album is currently unavailable."
            msg.contains("404") || msg.contains("NOT_FOUND") ->
                "Album not found."
            msg.contains("Unable to resolve host") || msg.contains("ConnectException") || msg.contains("SocketTimeout") ->
                "Network error. Please check your connection."
            else -> fallback
        }
    }

    private fun loadOnlinePlaylistAsAlbum(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val cleanId = playlistId.removePrefix("VL")
            val result = withContext(Dispatchers.IO) {
                InnerTubeYouTube.playlist(cleanId)
            }
            result.onSuccess { playlistPage ->
                val playlist = playlistPage.playlist
                val albumTitle = playlist.title
                val unifiedAlbumId = YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumTitle)
                val artistName = playlist.author?.name ?: ""
                val albumArt = playlist.thumbnail ?: ""

                AlbumIdMapper.putMapping(context, unifiedAlbumId, playlistId)

                val albumModel = Album(
                    id = unifiedAlbumId,
                    title = albumTitle,
                    artist = artistName,
                    year = 0,
                    dateAdded = System.currentTimeMillis(),
                    albumArtUriString = albumArt,
                    songCount = playlistPage.songs.size,
                    albumArtist = artistName
                )

                val songsModels = playlistPage.songs.mapIndexed { index, songItem ->
                    val nativeSong = songItem.toNativeSong()
                    nativeSong.copy(
                        album = albumTitle,
                        albumId = unifiedAlbumId,
                        albumBrowseId = playlistId,
                        albumArtist = artistName,
                        trackNumber = index + 1
                    )
                }

                _uiState.value = AlbumDetailUiState(
                    album = albumModel,
                    songs = songsModels,
                    isLoading = false,
                    error = null
                )
            }.onFailure { e ->
                val albumId = savedStateHandle.get<String>("albumId")?.toLongOrNull()
                if (albumId != null) {
                    loadAlbumData(albumId)
                } else {
                    val friendlyMsg = sanitizeErrorMessage(e, "This album is currently unavailable.")
                    _uiState.update { it.copy(error = friendlyMsg, isLoading = false) }
                }
            }
        }
    }

    private fun loadOnlineAlbumData(browseId: String) {
        if (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OLAK")) {
            loadOnlinePlaylistAsAlbum(browseId)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.album(browseId)
                }
                result.onSuccess { albumPage ->
                    val albumItem = albumPage.album
                    val albumTitle = albumItem.title
                    val unifiedAlbumId = YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumTitle)
                    val artistName = albumItem.artists?.joinToString { it.name } ?: ""
                    val primaryArtistName = albumItem.artists?.firstOrNull()?.name ?: artistName
                    val primaryArtistId = YouTubeIdUtils.toUnifiedYoutubeArtistId(primaryArtistName)
                    val albumYear = albumItem.year ?: 0
                    val albumArt = albumItem.thumbnail

                    AlbumIdMapper.putMapping(context, unifiedAlbumId, albumItem.browseId)

                    val albumModel = Album(
                        id = unifiedAlbumId,
                        title = albumTitle,
                        artist = artistName,
                        year = albumYear,
                        dateAdded = System.currentTimeMillis(),
                        albumArtUriString = albumArt,
                        songCount = albumPage.songs.size,
                        albumArtist = artistName
                    )

                    // Map all songs with permanent album metadata and 1-indexed track numbers
                    val songsModels = albumPage.songs.mapIndexed { index, songItem ->
                        val nativeSong = songItem.toNativeSong()
                        nativeSong.copy(
                            album = albumTitle,
                            albumId = unifiedAlbumId,
                            albumBrowseId = albumItem.browseId,
                            albumArtist = artistName,
                            trackNumber = index + 1,
                            year = albumYear
                        )
                    }

                    _uiState.value = AlbumDetailUiState(
                        album = albumModel,
                        songs = songsModels,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { e ->
                    // Fallback to playlist if browseId can be queried as playlist
                    val playlistResult = withContext(Dispatchers.IO) {
                        InnerTubeYouTube.playlist(browseId.removePrefix("VL")).getOrNull()
                    }
                    if (playlistResult != null) {
                        val playlist = playlistResult.playlist
                        val albumTitle = playlist.title
                        val unifiedAlbumId = YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumTitle)
                        val artistName = playlist.author?.name ?: ""
                        val albumArt = playlist.thumbnail ?: ""

                        AlbumIdMapper.putMapping(context, unifiedAlbumId, browseId)

                        val albumModel = Album(
                            id = unifiedAlbumId,
                            title = albumTitle,
                            artist = artistName,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            albumArtUriString = albumArt,
                            songCount = playlistResult.songs.size,
                            albumArtist = artistName
                        )

                        val songsModels = playlistResult.songs.mapIndexed { index, songItem ->
                            val nativeSong = songItem.toNativeSong()
                            nativeSong.copy(
                                album = albumTitle,
                                albumId = unifiedAlbumId,
                                albumBrowseId = browseId,
                                albumArtist = artistName,
                                trackNumber = index + 1
                            )
                        }

                        _uiState.value = AlbumDetailUiState(
                            album = albumModel,
                            songs = songsModels,
                            isLoading = false,
                            error = null
                        )
                    } else {
                        val albumId = savedStateHandle.get<String>("albumId")?.toLongOrNull()
                        if (albumId != null) {
                            loadAlbumData(albumId)
                        } else {
                            val friendlyMsg = sanitizeErrorMessage(e, "This album is currently unavailable.")
                            _uiState.update { it.copy(error = friendlyMsg, isLoading = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                val albumId = savedStateHandle.get<String>("albumId")?.toLongOrNull()
                if (albumId != null) {
                    loadAlbumData(albumId)
                } else {
                    val friendlyMsg = sanitizeErrorMessage(e, "This album is currently unavailable.")
                    _uiState.update { it.copy(error = friendlyMsg, isLoading = false) }
                }
            }
        }
    }
}

object AlbumIdMapper {
    private const val PREFS_NAME = "album_id_mapping"

    fun getBrowseId(context: android.content.Context, albumId: Long): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(albumId.toString(), null)
    }

    fun putMapping(context: android.content.Context, albumId: Long, browseId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(albumId.toString(), browseId).apply()
    }
}
