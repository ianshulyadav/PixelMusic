package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube as InnerTubeYouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ArtistPage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Holds the full UI state for ArtistDetailScreen.
 *
 * [effectiveImageUrl] is the resolved image to display (custom takes priority over Deezer).
 * It is updated after artist data loads and again whenever the user changes the custom image.
 */
data class ArtistDetailUiState(
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val albumSections: List<ArtistAlbumSection> = emptyList(),
    val effectiveImageUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class ArtistAlbumSection(
    val albumId: Long,
    val title: String,
    val year: Int?,
    val albumArtUriString: String?,
    val songs: List<Song>
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    val themeStateHolder: ThemeStateHolder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private val _artistColorScheme = MutableStateFlow<ColorSchemePair?>(null)
    val artistColorScheme: StateFlow<ColorSchemePair?> = _artistColorScheme.asStateFlow()

    val isSubscribed: Flow<Boolean> = combine(
        savedStateHandle.getStateFlow<String?>("artistId", null),
        userPreferencesRepository.subscribedArtistIdsFlow
    ) { artistIdStr, subscribedIds ->
        artistIdStr != null && subscribedIds.contains(artistIdStr)
    }

    fun toggleSubscription() {
        val artistIdStr = savedStateHandle.get<String?>("artistId") ?: return
        viewModelScope.launch {
            val currentSubscribed = userPreferencesRepository.subscribedArtistIdsFlow.first()
            val isCurrentlySubscribed = currentSubscribed.contains(artistIdStr)
            userPreferencesRepository.subscribeArtist(artistIdStr, !isCurrentlySubscribed)
        }
    }

    init {
        savedStateHandle.getStateFlow<String?>("artistId", null)
            .onEach { idString ->
                if (idString != null) {
                    loadArtistData(idString)
                } else {
                    _uiState.update { it.copy(error = context.getString(R.string.artist_id_not_found), isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    private var currentLoadJob: Job? = null

    private fun loadArtistData(artistIdStr: String) {
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            Log.d("ArtistDebug", "loadArtistData: idStr=$artistIdStr")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val numericId = artistIdStr.toLongOrNull()
                var browseId: String? = null
                if (numericId == null || artistIdStr.startsWith("UC") || artistIdStr.startsWith("LA")) {
                    browseId = artistIdStr
                } else {
                    val localArtist = musicRepository.getArtistById(numericId).first()
                    if (localArtist != null) {
                        val searchResult = withContext(Dispatchers.IO) {
                            InnerTubeYouTube.search(localArtist.name, InnerTubeYouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                        }
                        val artistItem = searchResult?.items?.find { it is ArtistItem } as? ArtistItem
                        browseId = artistItem?.id ?: ("UC" + localArtist.name.hashCode().toString())
                    }
                }

                if (browseId != null && (browseId.startsWith("UC") || browseId.startsWith("LA") || numericId == null)) {
                    val artistPageResult = withContext(Dispatchers.IO) {
                        InnerTubeYouTube.artist(browseId)
                    }

                    artistPageResult.onSuccess { artistPage ->
                        val artistItem = artistPage.artist
                        val artistModel = Artist(
                            id = artistIdStr.hashCode().toLong(),
                            name = artistItem.title,
                            songCount = 0,
                            imageUrl = artistItem.thumbnail
                        )

                        val ytSongsSection = artistPage.sections.find {
                            it.title.contains("Songs", ignoreCase = true)
                        }
                        val nativeSongs = ytSongsSection?.items?.mapNotNull { item ->
                            (item as? SongItem)?.toNativeSong()
                        } ?: emptyList()

                        val albumSections = artistPage.sections.filter {
                            it.title.contains("Albums", ignoreCase = true) ||
                            it.title.contains("Singles", ignoreCase = true) ||
                            it.title.contains("Releases", ignoreCase = true)
                        }.map { section ->
                            val sectionSongs = section.items.mapNotNull { item ->
                                when (item) {
                                    is SongItem -> item.toNativeSong()
                                    is AlbumItem -> {
                                        Song(
                                            id = "youtube_album_placeholder_${item.browseId}",
                                            title = item.title,
                                            artist = artistItem.title,
                                            artistId = artistIdStr.hashCode().toLong(),
                                            artists = emptyList<com.unshoo.pixelmusic.data.model.ArtistRef>(),
                                            album = item.title,
                                            albumId = item.browseId.hashCode().toLong(),
                                            albumArtist = artistItem.title,
                                            path = "",
                                            contentUriString = "youtube_album://${item.browseId}",
                                            albumArtUriString = item.thumbnail,
                                            duration = 0L,
                                            genre = "YouTube",
                                            lyrics = null,
                                            isFavorite = false,
                                            trackNumber = 1,
                                            discNumber = null,
                                            year = item.year ?: 0,
                                            dateAdded = System.currentTimeMillis(),
                                            dateModified = System.currentTimeMillis(),
                                            mimeType = "audio/mpeg",
                                            bitrate = 128,
                                            sampleRate = 44100,
                                            telegramFileId = null,
                                            telegramChatId = null,
                                            neteaseId = null,
                                            gdriveFileId = null,
                                            qqMusicMid = null,
                                            navidromeId = null,
                                            jellyfinId = null,
                                            youtubeId = null
                                        )
                                    }
                                    else -> null
                                }
                            }

                            val sectionTitle = section.title
                            val sectionAlbumId = section.title.hashCode().toLong()
                            ArtistAlbumSection(
                                albumId = sectionAlbumId,
                                title = sectionTitle,
                                year = null,
                                albumArtUriString = sectionSongs.firstOrNull()?.albumArtUriString,
                                songs = sectionSongs
                            )
                        }

                        val effectiveImageUrl = artistItem.thumbnail
                        val newScheme = if (!effectiveImageUrl.isNullOrBlank()) {
                            try {
                                themeStateHolder.getOrGenerateColorScheme(effectiveImageUrl)
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        _artistColorScheme.value = newScheme
                        _uiState.value = ArtistDetailUiState(
                            artist = artistModel,
                            songs = nativeSongs,
                            albumSections = albumSections,
                            effectiveImageUrl = effectiveImageUrl,
                            isLoading = false
                        )
                    }.onFailure { e ->
                        _uiState.update {
                            it.copy(
                                error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        }
                    }
                } else {
                    val id = numericId ?: return@launch
                    val artistDetailsFlow = musicRepository.getArtistById(id)
                    val artistSongsFlow = musicRepository.getSongsForArtist(id)

                    combine(artistDetailsFlow, artistSongsFlow) { artist, songs ->
                        Log.d("ArtistDebug", "loadArtistData: id=$id found=${artist != null} songs=${songs.size}")
                        artist to songs
                    }
                        .catch { e ->
                            _uiState.update {
                                it.copy(
                                    error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                                    isLoading = false
                                )
                            }
                        }
                        .collect { (artist, songs) ->
                            if (artist == null) {
                                _uiState.update {
                                    it.copy(error = context.getString(R.string.could_not_find_artist), isLoading = false)
                                }
                                return@collect
                            }

                            val albumSections = buildAlbumSections(songs)
                            val orderedSongs = albumSections.flatMap { it.songs }

                            val effectiveUrl = try {
                                artistImageRepository.getEffectiveArtistImageUrl(
                                    artistId = artist.id,
                                    artistName = artist.name
                                )
                            } catch (e: Exception) {
                                Log.w("ArtistDebug", "Failed to resolve effective artist image: ${e.message}")
                                artist.effectiveImageUrl
                            }

                            val newScheme = if (!effectiveUrl.isNullOrBlank()) {
                                try {
                                    themeStateHolder.getOrGenerateColorScheme(effectiveUrl)
                                } catch (e: Exception) {
                                    Log.w("ArtistDebug", "Color scheme pre-warm failed: ${e.message}")
                                    null
                                }
                            } else null

                            _artistColorScheme.value = newScheme
                            _uiState.value = ArtistDetailUiState(
                                artist = artist.copy(
                                    imageUrl = if (artist.customImageUri.isNullOrBlank()) effectiveUrl else artist.imageUrl
                                ),
                                songs = orderedSongs,
                                albumSections = albumSections,
                                effectiveImageUrl = effectiveUrl,
                                isLoading = false
                            )
                        }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Called from the UI when the user selects a custom image from the system photo picker.
     * Copies the image to internal storage, persists the path to DB, and triggers palette regeneration.
     */
    fun setCustomImage(sourceUri: Uri) {
        val artistId = _uiState.value.artist?.id ?: return
        viewModelScope.launch {
            try {
                val internalPath = artistImageRepository.setCustomArtistImage(context, artistId, sourceUri)
                if (!internalPath.isNullOrBlank()) {
                    val oldEffectiveUrl = _uiState.value.effectiveImageUrl

                    // Regenerate palette from the new image url — invalidate old and warm-up new
                    if (!oldEffectiveUrl.isNullOrBlank() && oldEffectiveUrl != internalPath) {
                        themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                    }
                    val newScheme = try {
                        themeStateHolder.forceRegenerateColorScheme(internalPath)
                        themeStateHolder.getOrGenerateColorScheme(internalPath)
                    } catch (e: Exception) {
                        Log.w("ArtistDebug", "Failed to regenerate color scheme for custom image: ${e.message}")
                        null
                    }

                    _artistColorScheme.value = newScheme
                    _uiState.update { state ->
                        // Cache-busting: add timestamp to internalPath to force Coil to reload
                        val effectiveUrlWithBust = "$internalPath?t=${System.currentTimeMillis()}"
                        state.copy(
                            effectiveImageUrl = effectiveUrlWithBust,
                            artist = state.artist?.copy(customImageUri = internalPath)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistDebug", "Failed to set custom image: ${e.message}")
            }
        }
    }

    /**
     * Called when the user wants to revert to the Deezer-sourced image.
     */
    fun clearCustomImage() {
        val artist = _uiState.value.artist ?: return
        viewModelScope.launch {
            try {
                val oldEffectiveUrl = _uiState.value.effectiveImageUrl
                artistImageRepository.clearCustomArtistImage(context, artist.id)

                // Fall back to Deezer URL
                val deezerUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                val newEffectiveUrl = deezerUrl.takeIf { !it.isNullOrBlank() }

                // Invalidate old custom image palette
                if (!oldEffectiveUrl.isNullOrBlank()) {
                    themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                }

                val newScheme = if (!newEffectiveUrl.isNullOrBlank()) {
                    try {
                        themeStateHolder.getOrGenerateColorScheme(newEffectiveUrl)
                    } catch (e: Exception) {
                        Log.w("ArtistDebug", "Failed to regenerate palette after clear: ${e.message}")
                        null
                    }
                } else null

                _artistColorScheme.value = newScheme
                _uiState.update { state ->
                    state.copy(
                        effectiveImageUrl = newEffectiveUrl,
                        artist = state.artist?.copy(customImageUri = null, imageUrl = deezerUrl)
                    )
                }

            } catch (e: Exception) {
                Log.e("ArtistDebug", "Failed to clear custom image: ${e.message}")
            }
        }
    }

    fun removeSongFromAlbumSection(songId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.albumSections.map { section ->
                val updatedSongs = section.songs.filterNot { it.id == songId }
                section.copy(songs = updatedSongs)
            }.filter { it.songs.isNotEmpty() }

            currentState.copy(
                albumSections = updatedAlbumSections,
                songs = currentState.songs.filterNot { it.id == songId }
            )
        }
    }
}

private val songDisplayComparator = compareBy<Song> { it.discNumber ?: 1 }
    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
    .thenBy { it.title.lowercase() }

private fun buildAlbumSections(songs: List<Song>): List<ArtistAlbumSection> {
    if (songs.isEmpty()) return emptyList()

    val sections = songs
        .groupBy { it.albumId to it.album }
        .map { (key, albumSongs) ->
            val sortedSongs = albumSongs.sortedWith(songDisplayComparator)
            val albumYear = albumSongs.mapNotNull { song -> song.year.takeIf { it > 0 } }.maxOrNull()
            val albumArtUri = albumSongs.firstNotNullOfOrNull { it.albumArtUriString }
            ArtistAlbumSection(
                albumId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Album"),
                year = albumYear,
                albumArtUriString = albumArtUri,
                songs = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<ArtistAlbumSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
