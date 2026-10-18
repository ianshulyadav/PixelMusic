package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.ComponentCallbacks2
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.paging.filter
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.MusicFolder
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.StorageFilter
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val ENABLE_FOLDERS_STORAGE_FILTER = false

/**
 * Library state — wired to local Room DB (local + YouTube songs, albums, artists).
 * YouTube subscribed artists / liked songs are synced into the DB by YouTubeLibrarySyncManager
 * before this state holder observes them, so all paging flows pick them up automatically.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val connectivityStateHolder: ConnectivityStateHolder
) {

    // --- Non-paged state kept for compat with callers that still use them ---
    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs = _allSongs.asStateFlow()

    private val _allSongsById = MutableStateFlow<Map<String, Song>>(emptyMap())
    val allSongsById = _allSongsById.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders = _musicFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // --- Sort / filter options ---
    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongDefaultOrder)
    val currentSongSortOption = _currentSongSortOption.asStateFlow()

    private val _currentStorageFilter = MutableStateFlow(StorageFilter.ALL)
    val currentStorageFilter = _currentStorageFilter.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()

    /**
     * Effective storage filter — when offline, force LOCAL so unplayable online/YouTube
     * metadata without downloaded audio does not appear; when "hide local media" pref is on,
     * force ONLINE; otherwise use the user-selected filter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val effectiveStorageFilter: Flow<StorageFilter> =
        combine(
            _currentStorageFilter,
            userPreferencesRepository.hideLocalMediaFlow,
            connectivityStateHolder.isOnline,
            userPreferencesRepository.localModeEnabledFlow
        ) { filter, hideLocal, isOnline, localMode ->
            if (localMode || !isOnline) {
                StorageFilter.LOCAL
            } else if (hideLocal) {
                StorageFilter.ONLINE
            } else {
                filter
            }
        }.distinctUntilChanged()

    // --- Paging flows wired to the local Room DB ---

    @OptIn(ExperimentalCoroutinesApi::class)
    val songsPagingFlow: Flow<androidx.paging.PagingData<Song>> =
        combine(_currentSongSortOption, effectiveStorageFilter) { sort, filter ->
            sort to filter
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter) ->
            musicRepository.getPaginatedSongs(sortOption, filter)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumsPagingFlow: Flow<androidx.paging.PagingData<Album>> =
        combine(
            _currentAlbumSortOption,
            effectiveStorageFilter,
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { sort, filter, minTracks ->
            Triple(sort, filter, minTracks)
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter, minTracks) ->
            musicRepository.getPaginatedAlbums(sortOption, filter, minTracks)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsPagingFlow: Flow<androidx.paging.PagingData<Artist>> =
        combine(
            _currentArtistSortOption,
            effectiveStorageFilter
        ) { sortOption, filter ->
            sortOption to filter
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter) ->
            musicRepository.getPaginatedArtists(sortOption, filter)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoritesPagingFlow: Flow<androidx.paging.PagingData<Song>> =
        combine(
            _currentFavoriteSortOption,
            effectiveStorageFilter,
            userPreferencesRepository.localModeEnabledFlow
        ) { sort, filter, localMode ->
            Triple(sort, filter, localMode)
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, storageFilter, localMode) ->
            musicRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
                .map { pagingData ->
                    if (localMode || storageFilter == StorageFilter.LOCAL) {
                        pagingData.filter { song ->
                            song.isLocal || com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, song)
                        }
                    } else {
                        pagingData
                    }
                }
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteSongCountFlow: Flow<Int> = effectiveStorageFilter
        .distinctUntilChanged()
        .flatMapLatest { filter -> musicRepository.getFavoriteSongCountFlow(filter) }
        .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val genres: Flow<ImmutableList<Genre>> = musicRepository.getGenres()
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private var scope: CoroutineScope? = null
    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var foldersJob: Job? = null
    @Volatile
    private var needsReloadAfterTrim: Boolean = false

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            val songSortKey = userPreferencesRepository.songsSortOptionFlow.first()
            _currentSongSortOption.value = SortOption.SONGS.find { it.storageKey == songSortKey } ?: SortOption.SongDefaultOrder

            val albumSortKey = userPreferencesRepository.albumsSortOptionFlow.first()
            _currentAlbumSortOption.value = SortOption.ALBUMS.find { it.storageKey == albumSortKey } ?: SortOption.AlbumTitleAZ

            val artistSortKey = userPreferencesRepository.artistsSortOptionFlow.first()
            _currentArtistSortOption.value = SortOption.ARTISTS.find { it.storageKey == artistSortKey } ?: SortOption.ArtistNameAZ

            val folderSortKey = userPreferencesRepository.foldersSortOptionFlow.first()
            _currentFolderSortOption.value = SortOption.FOLDERS.find { it.storageKey == folderSortKey } ?: SortOption.FolderNameAZ

            val likedSortKey = userPreferencesRepository.likedSongsSortOptionFlow.first()
            _currentFavoriteSortOption.value = SortOption.LIKED.find { it.storageKey == likedSortKey } ?: SortOption.LikedSongDateLiked

            _currentStorageFilter.value = userPreferencesRepository.lastStorageFilterFlow.first()
        }
    }

    fun onCleared() {
        songsJob?.cancel()
        albumsJob?.cancel()
        artistsJob?.cancel()
        foldersJob?.cancel()
        songsJob = null
        albumsJob = null
        artistsJob = null
        foldersJob = null
        scope = null
    }

    fun startObservingLibraryData() {
        if (
            songsJob?.isActive == true &&
            albumsJob?.isActive == true &&
            artistsJob?.isActive == true &&
            foldersJob?.isActive == true
        ) {
            return
        }

        Timber.tag("LibraryStateHolder").d("startObservingLibraryData called.")
        needsReloadAfterTrim = false

        songsJob = scope?.launch(Dispatchers.IO) {
            _isLoadingLibrary.value = true
            musicRepository.getAudioFiles().conflate().collect { songs ->
                val immutableSongs = songs.toImmutableList()
                val songsMap = songs.associateBy { it.id }

                _allSongs.value = immutableSongs
                _allSongsById.value = songsMap
                _isLoadingLibrary.value = false
            }
        }

        albumsJob = scope?.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(100L) // Stagger by 100ms so initial cold start song load has exclusive IO priority
            _isLoadingCategories.value = true
            @OptIn(ExperimentalCoroutinesApi::class)
            combine(
                effectiveStorageFilter,
                userPreferencesRepository.minTracksPerAlbumFlow
            ) { filter, minTracks ->
                filter to minTracks
            }.flatMapLatest { (filter, minTracks) ->
                musicRepository.getAlbums(filter, minTracks)
            }.conflate().collect { albums ->
                val sortedAlbums = sortAlbumsList(albums, _currentAlbumSortOption.value).toImmutableList()
                _albums.value = sortedAlbums
                _isLoadingCategories.value = false
            }
        }

        artistsJob = scope?.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(180L) // Stagger by 180ms
            _isLoadingCategories.value = true
            @OptIn(ExperimentalCoroutinesApi::class)
            effectiveStorageFilter.flatMapLatest { filter ->
                musicRepository.getArtists(filter)
            }.conflate().collect { artists ->
                val sortedArtists = sortArtistsList(artists, _currentArtistSortOption.value).toImmutableList()
                _artists.value = sortedArtists
                _isLoadingCategories.value = false
            }
        }

        foldersJob = scope?.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(260L) // Stagger by 260ms
            @OptIn(ExperimentalCoroutinesApi::class)
            effectiveStorageFilter.flatMapLatest { filter ->
                musicRepository.getMusicFolders(filter)
            }.conflate().collect { folders ->
                val sortedFolders = sortFoldersList(folders, _currentFolderSortOption.value).toImmutableList()
                _musicFolders.value = sortedFolders
            }
        }
    }

    fun loadSongsFromRepository() {
        startObservingLibraryData()
    }

    fun loadAlbumsFromRepository() {
        startObservingLibraryData()
    }

    fun loadArtistsFromRepository() {
        startObservingLibraryData()
    }

    fun loadFoldersFromRepository() {
        startObservingLibraryData()
    }

    fun loadSongsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadAlbumsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadArtistsIfNeeded() {
        startObservingLibraryData()
    }

    // --- Sort options ---
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentSongSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setSongsSortOption(sortOption.storageKey)
            }
            _currentSongSortOption.value = sortOption
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentAlbumSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setAlbumsSortOption(sortOption.storageKey)
            }
            _currentAlbumSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortAlbumsList(_albums.value, sortOption).toImmutableList()
            }
            _albums.value = sorted
        }
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentArtistSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setArtistsSortOption(sortOption.storageKey)
            }
            _currentArtistSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortArtistsList(_artists.value, sortOption).toImmutableList()
            }
            _artists.value = sorted
        }
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentFolderSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setFoldersSortOption(sortOption.storageKey)
            }
            _currentFolderSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortFoldersList(_musicFolders.value, sortOption).toImmutableList()
            }
            _musicFolders.value = sorted
        }
    }

    private fun sortAlbumsList(albums: Iterable<Album>, sortOption: SortOption): List<Album> {
        return when (sortOption) {
            SortOption.AlbumTitleAZ -> albums.sortedWith(
                compareBy<Album> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumTitleZA -> albums.sortedWith(
                compareByDescending<Album> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumArtist -> albums.sortedWith(
                compareBy<Album> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumArtistDesc -> albums.sortedWith(
                compareByDescending<Album> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumReleaseYear -> albums.sortedWith(
                compareByDescending<Album> { it.year }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumReleaseYearAsc -> albums.sortedWith(
                compareBy<Album> { it.year }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumDateAdded -> albums.sortedWith(
                compareByDescending<Album> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumSizeAsc -> albums.sortedWith(
                compareBy<Album> { it.songCount }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumSizeDesc -> albums.sortedWith(
                compareByDescending<Album> { it.songCount }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            else -> albums.toList()
        }
    }

    private fun sortArtistsList(artists: Iterable<Artist>, sortOption: SortOption): List<Artist> {
        return when (sortOption) {
            SortOption.ArtistNameAZ -> artists.sortedWith(
                compareBy<Artist> { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNameZA -> artists.sortedWith(
                compareByDescending<Artist> { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNumSongsDesc -> artists.sortedWith(
                compareByDescending<Artist> { it.songCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNumSongsAsc -> artists.sortedWith(
                compareBy<Artist> { it.songCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            else -> artists.toList()
        }
    }

    private fun sortFoldersList(folders: Iterable<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
        return when (sortOption) {
            SortOption.FolderNameAZ -> folders.sortedWith(
                compareBy<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderNameZA -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            else -> folders.toList()
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentFavoriteSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setLikedSongsSortOption(sortOption.storageKey)
            }
            _currentFavoriteSortOption.value = sortOption
        }
    }

    fun updateSong(updatedSong: Song) {
        _allSongs.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }

    fun removeSong(songId: String) {
        _allSongs.update { it.filter { s -> s.id != songId }.toImmutableList() }
    }

    fun setStorageFilter(filter: StorageFilter) {
        _currentStorageFilter.value = filter
        scope?.launch {
            userPreferencesRepository.saveLastStorageFilter(filter)
        }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        val shouldReleaseLibraryState =
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        if (!shouldReleaseLibraryState) return

        val hasLoadedData =
            _allSongs.value.isNotEmpty() ||
                _albums.value.isNotEmpty() ||
                _artists.value.isNotEmpty() ||
                _musicFolders.value.isNotEmpty()
        val hasActiveCollectors =
            songsJob?.isActive == true ||
                albumsJob?.isActive == true ||
                artistsJob?.isActive == true ||
                foldersJob?.isActive == true
        if (!hasLoadedData && !hasActiveCollectors) return

        songsJob?.cancel()
        albumsJob?.cancel()
        artistsJob?.cancel()
        foldersJob?.cancel()
        songsJob = null
        albumsJob = null
        artistsJob = null
        foldersJob = null

        _allSongs.value = persistentListOf()
        _allSongsById.value = emptyMap()
        _albums.value = persistentListOf()
        _artists.value = persistentListOf()
        _musicFolders.value = persistentListOf()
        _isLoadingLibrary.value = false
        _isLoadingCategories.value = false
        needsReloadAfterTrim = true
    }

    fun restoreAfterTrimIfNeeded() {
        if (!needsReloadAfterTrim || scope == null) return
        startObservingLibraryData()
    }
}
