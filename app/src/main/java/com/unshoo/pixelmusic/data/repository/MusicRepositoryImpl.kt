package com.unshoo.pixelmusic.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.SearchHistoryDao
import com.unshoo.pixelmusic.data.database.SearchHistoryEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.model.ArtistRef
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.absoluteValue
import com.unshoo.pixelmusic.data.database.TelegramChannelEntity
import com.unshoo.pixelmusic.data.database.TelegramDao
import com.unshoo.pixelmusic.data.database.toAlbum
import com.unshoo.pixelmusic.data.database.toArtist
import com.unshoo.pixelmusic.data.database.toSearchHistoryItem
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.database.toTelegramEntity
import com.unshoo.pixelmusic.data.database.toTelegramEntityWithThread
import com.unshoo.pixelmusic.data.database.TelegramTopicEntity
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Lyrics
import com.unshoo.pixelmusic.data.model.LyricsSourcePreference
import com.unshoo.pixelmusic.data.model.MusicFolder
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.FolderSource
import com.unshoo.pixelmusic.data.model.StorageFilter
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.ui.theme.GenreThemeUtils
import com.unshoo.pixelmusic.utils.DirectoryFilterUtils
import com.unshoo.pixelmusic.utils.LogUtils
import com.unshoo.pixelmusic.utils.StorageType
import com.unshoo.pixelmusic.utils.StorageUtils
import com.unshoo.pixelmusic.utils.toHexString
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import com.unshoo.pixelmusic.data.remote.youtube.SongRepository as YoutubeSongRepository
import com.unshoo.pixelmusic.data.remote.youtube.ApiResult as YoutubeApiResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val telegramDao: TelegramDao,
    private val telegramCacheManagerProvider: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramRepository>,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao,
    private val artistImageRepository: ArtistImageRepository,
    private val folderTreeBuilder: FolderTreeBuilder,
    private val youtubeDatastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
) : MusicRepository {

    companion object {
        /** Maximum number of search results to load at once to avoid memory issues with large libraries. */
        private const val SEARCH_RESULTS_LIMIT = 100
        private const val UNKNOWN_GENRE_NAME = "Unknown"
        private const val UNKNOWN_GENRE_ID = "unknown"
    }

    private val directoryScanMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultLibraryPagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )
    // Tracks the active prefetch job so a new flow emission cancels the previous one.
    @Volatile private var prefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchSongId: Long? = null
    @Volatile private var telegramDownloadSyncObserverStarted = false
    private val telegramCacheManager: com.unshoo.pixelmusic.data.telegram.TelegramCacheManager
        get() = telegramCacheManagerProvider.get()
    override val telegramRepository: com.unshoo.pixelmusic.data.telegram.TelegramRepository
        get() = telegramRepositoryProvider.get()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    /** Cached directory filter — recomputed only when allowed/blocked dirs preferences change. */
    data class CachedDirFilter(val allowedParentDirs: List<String> = emptyList(), val applyFilter: Boolean = false)

    private val cachedDirFilter: StateFlow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked ->
        val (dirs, apply) = DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowed,
            blockedDirs = blocked,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
        CachedDirFilter(dirs, apply)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, CachedDirFilter())

    private fun ensureTelegramDownloadSyncObserverStarted() {
        if (telegramDownloadSyncObserverStarted) return
        telegramDownloadSyncObserverStarted = true

        repositoryScope.launch {
            telegramRepository.songFileUpdated.collect {
                androidx.work.WorkManager.getInstance(context).enqueue(
                    com.unshoo.pixelmusic.data.worker.SyncWorker.incrementalSyncWork()
                )
            }
        }
    }

    private fun List<Artist>.missingImageCandidates(): List<Pair<Long, String>> =
        asSequence()
            .filter { it.effectiveImageUrl.isNullOrBlank() && it.name.isNotBlank() }
            .map { it.id to it.name }
            .distinctBy { (_, name) -> name.trim().lowercase() }
            .toList()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getAllSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().conflate().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(sortOption: SortOption, storageFilter: com.unshoo.pixelmusic.data.model.StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedSongs(sortOption, storageFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedAlbums(
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): Flow<PagingData<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getAlbumsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey,
                                minTracks = minTracks
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedArtists(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getArtistsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
    }

    override suspend fun getFavoriteSongsOnce(storageFilter: StorageFilter): List<Song> {
        return songRepository.getFavoriteSongsOnce(storageFilter)
    }

    override suspend fun getFavoriteSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    override fun getFavoriteSongCountFlow(storageFilter: StorageFilter): Flow<Int> {
        return songRepository.getFavoriteSongCountFlow(storageFilter)
    }

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getSongCount().distinctUntilChanged()
    }

    override fun getCloudSongCountFlow(): Flow<Int> {
        return musicDao.getCloudSongCount().distinctUntilChanged()
    }

    override suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getRandomSongs(limit, filter.allowedParentDirs, filter.applyFilter).map { it.toSong() }
    }

    override suspend fun getSongsByGenre(genre: String, excludeId: Long, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        musicDao.getSongsByGenre(genre, excludeId, limit).map { it.toSong() }
    }

    override suspend fun getSongsByArtistName(artistName: String, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        musicDao.getSongsByArtistName(artistName, limit).map { it.toSong() }
    }

    override suspend fun getSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    override suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = limit,
            offset = offset
        ).map { it.toAlbum() }
    }

    override suspend fun getArtistsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toArtist() }
    }

    override suspend fun getFirstPlayableSong(): Song? = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getFirstPlayableSong(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        )?.toSong()
    }

    override suspend fun saveTelegramSongs(songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }
        if (entities.isNotEmpty()) {
            ensureTelegramDownloadSyncObserverStarted()
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
            // Trigger sync to update main DB
            androidx.work.WorkManager.getInstance(context).enqueue(
                com.unshoo.pixelmusic.data.worker.SyncWorker.incrementalSyncWork()
            )
        }
    }

    override suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }.filter { it.chatId == chatId }
        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByChatId(chatId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }
        // Sync into the unified `songs` table is triggered later by saveTelegramChannel().
        // We deliberately do NOT enqueue here: the SyncWorker gates Telegram processing on
        // an existing channel row (telegramDao.getAllChannels()), and on first add this
        // function runs BEFORE the channel entity is persisted. Triggering here would race
        // and skip the sync, leaving songs invisible until the next app launch.
    }

    /**
     * Compute allowed parent directories by subtracting blocked dirs from all known dirs.
     * Returns Pair(allowedDirs, applyFilter) for use with Room DAO filtered queries.
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private fun StorageFilter.toFilterMode(): Int = when (this) {
        StorageFilter.ALL -> 0
        StorageFilter.OFFLINE -> 1
        StorageFilter.ONLINE -> 2
        StorageFilter.DOWNLOADED_ONLY -> 3
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAlbums(storageFilter: StorageFilter, minTracks: Int): Flow<List<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getAlbums(allowedParentDirs, applyFilter, storageFilter.toFilterMode(), minTracks)
                .map { entities -> entities.map { it.toAlbum() } }
                .distinctUntilChanged()
        }.conflate().flowOn(Dispatchers.IO)
    }

    override fun getAlbumById(id: Long): Flow<Album?> {
        return musicDao.getAlbumById(id).map { it?.toAlbum() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getArtists(storageFilter: StorageFilter): Flow<List<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getArtistsWithSongCountsFiltered(
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
                .distinctUntilChanged()
                .map { entities ->
                    val artists = entities.map { it.toArtist() }
                    // Trigger prefetch for missing images (non-blocking)
                    val missingImages = artists.missingImageCandidates()
                    if (missingImages.isNotEmpty()) {
                        // Cancel any in-flight prefetch before starting a new one — the flow
                        // can emit multiple times during sync, and concurrent launches would
                        // create N × artist-count coroutines simultaneously.
                        prefetchJob?.cancel()
                        prefetchJob = repositoryScope.launch {
                            artistImageRepository.prefetchArtistImages(missingImages)
                        }
                    }
                    artists
                }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return musicDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toSong() }.sortedBy { it.trackNumber }
        }.flowOn(Dispatchers.IO)
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        return musicDao.getArtistById(artistId).map { it?.toArtist() }
    }

    override suspend fun getArtistIdByName(name: String): Long? = withContext(Dispatchers.IO) {
        musicDao.getArtistIdByName(name)
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        return musicDao.getArtistsForSong(songId)
            .map { entities -> entities.map { it.toArtist() } }
            .distinctUntilChanged()
            .onEach { artists ->
                val missingImages = artists.missingImageCandidates()
                if (missingImages.isNotEmpty()) {
                    val isNewSong = currentSongArtistPrefetchSongId != songId
                    if (isNewSong) {
                        currentSongArtistPrefetchJob?.cancel()
                        currentSongArtistPrefetchSongId = songId
                    } else if (currentSongArtistPrefetchJob?.isActive == true) {
                        // Room re-emits as artist rows are updated; keep the current song batch
                        // alive so one successful image write does not cancel the remaining fetches.
                        return@onEach
                    }

                    currentSongArtistPrefetchJob = repositoryScope.launch {
                        artistImageRepository.prefetchArtistImages(missingImages)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForArtist(artistId).map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    private fun getYoutubeSearchFlow(query: String): Flow<List<Song>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        try {
            val youtubeSongRepository = YoutubeSongRepository()
            youtubeSongRepository.search(query).collect { apiResult ->
                when (apiResult) {
                    is YoutubeApiResult.Success -> {
                        val mappedSongs = apiResult.data.map { ytSong ->
                            val durationMs = parseYoutubeDuration(ytSong.duration)
                            Song(
                                id = "youtube_${ytSong.youtubeId}",
                                title = ytSong.title,
                                artist = ytSong.artist,
                                artistId = -2L,
                                artists = emptyList(),
                                album = "YouTube",
                                albumId = -2L,
                                path = ytSong.audioFilePath ?: "",
                                contentUriString = ytSong.audioFilePath ?: "",
                                albumArtUriString = ytSong.thumbnailPath ?: ytSong.thumbnailHref,
                                duration = durationMs,
                                mimeType = "audio/webm",
                                bitrate = null,
                                sampleRate = null,
                                youtubeId = ytSong.youtubeId
                            )
                        }
                        emit(mappedSongs)
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    private fun parseYoutubeDuration(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        return try {
            val parts = durationStr.split(":")
            var seconds = 0L
            if (parts.size == 1) {
                seconds = parts[0].toLongOrNull() ?: 0L
            } else if (parts.size == 2) {
                val minutes = parts[0].toLongOrNull() ?: 0L
                val secs = parts[1].toLongOrNull() ?: 0L
                seconds = minutes * 60 + secs
            } else if (parts.size == 3) {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secs = parts[2].toLongOrNull() ?: 0L
                seconds = hours * 3600 + minutes * 60 + secs
            }
            seconds * 1000L
        } catch (e: Exception) {
            0L
        }
    }

    override fun searchSongs(query: String, titleOnly: Boolean): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        
        val localSearchFlow = combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.searchSongsLimited(
                        query = query,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        limit = SEARCH_RESULTS_LIMIT,
                        titleOnly = titleOnly
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }

        return localSearchFlow.flowOn(Dispatchers.IO)
    }


    override fun searchAlbums(query: String, minTracks: Int): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchAlbums(query, emptyList(), false, minTracks).map { entities ->
            entities.map { it.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchArtists(query, emptyList(), false).map { entities ->
            entities.map { it.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return playlistPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return combine(
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { (minTracks) ->
            minTracks
        }.flatMapLatest { minTracks ->
            when (filterType) {
                SearchFilterType.ALL -> {
                    combine(
                        searchSongs(query),
                        searchAlbums(query, minTracks),
                        searchArtists(query),
                        playlistsFlow
                    ) { songs, albums, artists, playlists ->
                        mutableListOf<SearchResultItem>().apply {
                            songs.forEach { add(SearchResultItem.SongItem(it)) }
                            albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                            artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                            playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                        }
                    }
                }
                SearchFilterType.SONGS -> searchSongs(query, titleOnly = true).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
                SearchFilterType.ALBUMS -> searchAlbums(query, minTracks).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
                SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
                SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.mockGenresEnabledFlow,
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { mockEnabled, allowedDirs, blockedDirs ->
            Triple(mockEnabled, allowedDirs, blockedDirs)
        }.flatMapLatest { (mockEnabled, allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                val genreName = if (mockEnabled) "Mock" else genreId
                emit(
                    if (genreName.equals("unknown", ignoreCase = true)) {
                        musicDao.getSongsWithNullGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    } else {
                        musicDao.getSongsByGenre(
                            genreName = genreName,
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    }
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())

        // Map each requested songId to a Long database ID.
        // IDs can be:
        //   1. Plain numeric Long string (local MediaStore songs)
        //   2. "youtube_<videoId>" prefixed string (YouTube songs not yet persisted via ensureSongsPersisted)
        //   3. Already-mapped negative Long strings generated by PlaylistViewModel.ensureSongsPersisted
        val idMappings = songIds.map { rawId ->
            val longId = rawId.toLongOrNull()
            if (longId != null) {
                // Already a numeric ID (local or already-hashed YouTube ID)
                rawId to longId
            } else if (rawId.startsWith("youtube_")) {
                // Map youtube_<videoId> → -(15_000_000_000_000L + hash)
                val videoId = rawId.removePrefix("youtube_")
                val hashedId = -(15_000_000_000_000L + videoId.hashCode().toLong().let {
                    if (it < 0L) -it else it
                })
                rawId to hashedId
            } else {
                rawId to null
            }
        }

        val validLongIds = idMappings.mapNotNull { it.second }
        if (validLongIds.isEmpty()) return flowOf(emptyList())

        return musicDao.getSongsByIds(validLongIds, emptyList(), false).map { entities ->
            val songMapById = entities.associate { it.id to it.toSong() }
            // Preserve the requested order using the mapped IDs
            idMappings.mapNotNull { (_, longId) ->
                longId?.let { songMapById[it] }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getAllSongs(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        ).first().map { it.toSong() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDistinctAlbumArtSongs(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getDistinctAlbumArtSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getHomeMixPreviewSongs(
                        limit = limit,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun getAllAlbumsOnce(storageFilter: StorageFilter, minTracks: Int): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = SortOption.AlbumTitleAZ.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = Int.MAX_VALUE,
            offset = 0
        ).map { it.toAlbum() }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsWithSongCountsFiltered(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            filterMode = StorageFilter.ALL.toFilterMode()
        ).first().map { it.toArtist() }
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

    private suspend fun insertYoutubeSongSkeleton(
        youtubeId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        duration: Long,
        genre: String?
    ) {
        val songId = toUnifiedYoutubeSongId(youtubeId)
        val artistNames = parseYoutubeArtistNames(artist)
        val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
        val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)

        val artistsToInsert = artistNames.map { name ->
            ArtistEntity(
                id = toUnifiedYoutubeArtistId(name),
                name = name,
                trackCount = 0,
                imageUrl = null
            )
        }

        val crossRefsToInsert = artistNames.mapIndexed { index, name ->
            val artistId = toUnifiedYoutubeArtistId(name)
            com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                songId = songId,
                artistId = artistId,
                isPrimary = index == 0
            )
        }

        val albumId = toUnifiedYoutubeAlbumId("YouTube Music")
        val albumName = "YouTube Music"
        val albumToInsert = AlbumEntity(
            id = albumId,
            title = albumName,
            artistName = primaryArtistName,
            artistId = primaryArtistId,
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = 0,
            albumArtUriString = thumbnailUrl
        )

        // Build artists JSON
        val youtubeArtistRefs = artistNames.mapIndexed { idx, name ->
            ArtistRef(
                id = toUnifiedYoutubeArtistId(name),
                name = name,
                isPrimary = idx == 0
            )
        }
        val artistsJson = try {
            val arr = JSONArray()
            youtubeArtistRefs.forEach { ref ->
                val obj = JSONObject()
                obj.put("id", ref.id)
                obj.put("name", ref.name)
                obj.put("primary", ref.isPrimary)
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            null
        }

        val songEntity = SongEntity(
            id = songId,
            title = title,
            artistName = artist.ifBlank { primaryArtistName },
            artistId = primaryArtistId,
            albumArtist = null,
            albumName = albumName,
            albumId = albumId,
            contentUriString = "youtube://$youtubeId",
            albumArtUriString = thumbnailUrl,
            duration = duration,
            genre = genre?.takeIf { it.isNotBlank() } ?: "YouTube Music",
            filePath = "",
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
            artistsJson = artistsJson,
            sourceType = SourceType.YOUTUBE
        )

        musicDao.incrementalSyncMusicData(
            songs = listOf(songEntity),
            albums = listOf(albumToInsert),
            artists = artistsToInsert,
            crossRefs = crossRefsToInsert,
            deletedSongIds = emptyList()
        )
    }

    override suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val youtubeId = if (songId.startsWith("youtube_")) {
            songId.substringAfter("youtube_")
        } else if (songId.toLongOrNull() == null) {
            songId
        } else {
            null
        }

        val id = if (youtubeId != null) {
            toUnifiedYoutubeSongId(youtubeId)
        } else {
            songId.toLongOrNull() ?: return@withContext
        }

        if (isFavorite) {
            if (youtubeId != null) {
                val exists = musicDao.getSongsByIdsListSimple(listOf(id)).isNotEmpty()
                if (!exists) {
                    val ytDb = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                    val ytSong = ytDb.songRepository().getSong(youtubeId)
                    if (ytSong != null) {
                        insertYoutubeSongSkeleton(
                            youtubeId = youtubeId,
                            title = ytSong.title,
                            artist = ytSong.artist,
                            thumbnailUrl = ytSong.thumbnailPath ?: ytSong.thumbnailHref,
                            duration = parseDurationStringToMillis(ytSong.duration),
                            genre = ytSong.genre ?: "YouTube Music"
                        )
                    } else {
                        try {
                            val networkSong = com.unshoo.pixelmusic.data.remote.youtube.SongDataSource().getSongInfo(youtubeId)
                            insertYoutubeSongSkeleton(
                                youtubeId = youtubeId,
                                title = networkSong.title,
                                artist = networkSong.artist,
                                thumbnailUrl = networkSong.thumbnailPath ?: networkSong.thumbnailHref,
                                duration = parseDurationStringToMillis(networkSong.duration),
                                genre = networkSong.genre ?: "YouTube Music"
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch online metadata for liked song $youtubeId")
                            insertYoutubeSongSkeleton(
                                youtubeId = youtubeId,
                                title = "YouTube Video",
                                artist = "Unknown Artist",
                                thumbnailUrl = null,
                                duration = 0L,
                                genre = "YouTube Music"
                            )
                        }
                    }
                }
            }

            favoritesDao.setFavorite(
                com.unshoo.pixelmusic.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }

        if (youtubeId != null) {
            repositoryScope.launch {
                try {
                    val settings = youtubeDatastoreRepository.settings.first()
                    if (isFavorite) {
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeRequestHelper.like(youtubeId, settings)
                    } else {
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeRequestHelper.removeLike(youtubeId, settings)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync like state to YouTube for song $youtubeId")
                }
            }
        }
    }

    override suspend fun setFavoriteStatusWithMetadata(song: Song, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val youtubeId = song.youtubeId 
            ?: if (song.id.startsWith("youtube_")) song.id.substringAfter("youtube_")
               else if (song.contentUriString?.startsWith("youtube://") == true) song.contentUriString.substringAfter("youtube://")
               else null
               
        val id = if (youtubeId != null) {
            toUnifiedYoutubeSongId(youtubeId)
        } else {
            song.id.toLongOrNull() ?: return@withContext
        }

        if (isFavorite) {
            if (youtubeId != null) {
                val exists = musicDao.getSongsByIdsListSimple(listOf(id)).isNotEmpty()
                if (!exists) {
                    insertYoutubeSongSkeleton(
                        youtubeId = youtubeId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.albumArtUriString,
                        duration = song.duration,
                        genre = song.genre ?: "YouTube Music"
                    )
                }
            }

            favoritesDao.setFavorite(
                com.unshoo.pixelmusic.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }

        if (youtubeId != null) {
            repositoryScope.launch {
                try {
                    val settings = youtubeDatastoreRepository.settings.first()
                    if (isFavorite) {
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeRequestHelper.like(youtubeId, settings)
                    } else {
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeRequestHelper.removeLike(youtubeId, settings)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync like state to YouTube for song $youtubeId")
                }
            }
        }
    }

    override suspend fun getFavoriteSongIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        val ids = favoritesDao.getFavoriteSongIdsOnce()
        val idStrings = ids.map { it.toString() }.toMutableSet()
        val youtubeSongs = musicDao.getSongsByIdsListSimple(ids)
        youtubeSongs.forEach { item ->
            if (item.contentUriString.startsWith("youtube://")) {
                val ytId = item.contentUriString.removePrefix("youtube://")
                idStrings.add("youtube_$ytId")
            }
        }
        idStrings
    }

    override fun getFavoriteSongIdsFlow(): Flow<Set<String>> {
        return favoritesDao.getFavoriteSongIds()
            .map { ids ->
                val idStrings = ids.asSequence().map(Long::toString).toMutableSet()
                val youtubeSongs = musicDao.getSongsByIdsListSimple(ids)
                youtubeSongs.forEach { item ->
                    if (item.contentUriString.startsWith("youtube://")) {
                        val ytId = item.contentUriString.removePrefix("youtube://")
                        idStrings.add("youtube_$ytId")
                    }
                }
                idStrings.toSet()
            }
            .distinctUntilChanged()
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val youtubeId = if (songId.startsWith("youtube_")) songId.substringAfter("youtube_") else null
        val id = if (youtubeId != null) {
            toUnifiedYoutubeSongId(youtubeId)
        } else {
            songId.toLongOrNull() ?: return@withContext false
        }
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        setFavoriteStatus(songId, newFav)
        return@withContext newFav
    }

    override suspend fun updateSongFilePath(songId: Long, filePath: String) = withContext(Dispatchers.IO) {
        musicDao.updateSongFilePath(songId, filePath)
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            combine(
                telegramDao.getSongsByIds(listOf(songId)),
                telegramDao.getAllChannels()
            ) { songs, channels ->
                val channelMap = channels.associateBy { it.chatId }
                songs.firstOrNull()?.let {
                    it.toSong(channelTitle = channelMap[it.chatId]?.title)
                }
            }.flowOn(Dispatchers.IO)
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    combine(
                        musicDao.getUniqueGenres(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        ),
                        musicDao.hasUnknownGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    ) { genreNames, hasUnknown ->
                        val knownGenres = genreNames
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { buildGenre(it) }
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                        val unknownAlreadyPresent = knownGenres.any { it.id == UNKNOWN_GENRE_ID }
                        if (hasUnknown && !unknownAlreadyPresent) {
                            knownGenres + buildGenre(UNKNOWN_GENRE_NAME)
                        } else {
                            knownGenres
                        }
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private fun buildGenre(genreName: String): Genre {
        val id = if (genreName.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)) {
            UNKNOWN_GENRE_ID
        } else {
            genreName
                .lowercase()
                .replace(" ", "_")
                .replace("/", "_")
        }
        val lightThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = false)
        val darkThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = true)
        return Genre(
            id = id,
            name = genreName,
            lightColorHex = lightThemeColor.container.toHexString(),
            onLightColorHex = lightThemeColor.onContainer.toHexString(),
            darkColorHex = darkThemeColor.container.toHexString(),
            onDarkColorHex = darkThemeColor.onContainer.toHexString()
        )
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? {
        return lyricsRepository.getStoredLyrics(song)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow,
            userPreferencesRepository.foldersSourceFlow
        ) { allowedDirs, blockedDirs, isFolderFilterActive, folderSource ->
            FolderFlowConfig(
                allowedDirs = allowedDirs,
                blockedDirs = blockedDirs,
                isFolderFilterActive = isFolderFilterActive,
                folderSource = folderSource
            )
        }.flatMapLatest { config ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) = computeAllowedDirs(
                    allowedDirs = config.allowedDirs,
                    blockedDirs = config.blockedDirs
                )
                emit(
                    musicDao.getFolderSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        filterMode = storageFilter.toFilterMode()
                    ).map { folderSongs ->
                        folderTreeBuilder.buildFolderTree(
                            folderSongs = folderSongs,
                            allowedDirs = config.allowedDirs,
                            blockedDirs = config.blockedDirs,
                            isFolderFilterActive = config.isFolderFilterActive,
                            folderSource = config.folderSource,
                            context = context
                        )
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private data class FolderFlowConfig(
        val allowedDirs: Set<String>,
        val blockedDirs: Set<String>,
        val isFolderFilterActive: Boolean,
        val folderSource: FolderSource
    )

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun clearTelegramData() {
        // Delete all Telegram playlists from app playlists
        val allChannels = telegramDao.getAllChannels().first()
        allChannels.forEach { channel ->
            telegramRepository.deleteAppPlaylistForTelegramChannel(channel.chatId)
            telegramRepository.deleteAllTopicPlaylistsForChannel(channel.chatId)
        }

        musicDao.clearAllTelegramSongs()
        telegramDao.clearAll()
        // Clear all Telegram caches (TDLib files, embedded art, memory)
        telegramRepository.clearMemoryCache()
        telegramCacheManager.clearAllCache()
    }

    override suspend fun saveTelegramChannel(channel: TelegramChannelEntity) {
        telegramDao.insertChannel(channel)

        // Create or update the corresponding app playlist.
        // Forum channels use per-topic playlists (managed by replaceTelegramSongsForTopic),
        // so we skip the flat channel-level playlist when topics exist.
        try {
            val topics = withContext(Dispatchers.IO) {
                telegramDao.getTopicsByChannelOnce(channel.chatId)
            }
            if (topics.isEmpty()) {
                // Flat (non-forum) channel: create/update a single channel playlist
                val channelSongs = withContext(Dispatchers.IO) {
                    telegramDao.getSongsByChatId(channel.chatId)
                }
                telegramRepository.updateAppPlaylistForTelegramChannel(
                    channel.chatId,
                    channel.title,
                    channelSongs
                )
            }
            // Forum channels: topic playlists are managed by replaceTelegramSongsForTopic
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to update app playlist for Telegram channel ${channel.chatId}", e)
        }

        // Trigger the unified-library sync now that the channel row exists. SyncWorker's
        // Telegram phase is gated on telegramDao.getAllChannels() being non-empty, so this
        // is the earliest moment the sync can succeed. KEEP (not REPLACE) ensures we never
        // cancel a heavier full/rebuild that might be running under the same unique name.
        requestTelegramUnifiedSync()
    }

    override fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> {
        return telegramDao.getAllChannels()
    }

    override suspend fun deleteTelegramChannel(chatId: Long) {
        musicDao.clearTelegramSongsForChat(chatId)
        telegramDao.deleteSongsByChatId(chatId)
        telegramDao.deleteTopicsByChannel(chatId)     // NEW: remove topics
        telegramDao.deleteChannel(chatId)
        telegramRepository.deleteAppPlaylistForTelegramChannel(chatId)
        telegramRepository.deleteAllTopicPlaylistsForChannel(chatId)  // NEW: remove topic playlists
    }

    override suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>) {
        telegramDao.insertTopics(topics)
    }

    override suspend fun replaceTopicsForChannel(
        chatId: Long,
        freshTopics: List<TelegramTopicEntity>
    ) {
        val existingTopics = telegramDao.getTopicsByChannelOnce(chatId)
        val freshThreadIds = freshTopics.map { it.threadId }.toSet()

        // Delete topics that are no longer returned by Telegram
        val removedTopics = existingTopics.filter { it.threadId !in freshThreadIds }
        for (removed in removedTopics) {
            // Delete their songs from the telegram_songs table
            telegramDao.deleteSongsByTopicId(chatId, removed.threadId)
            // Delete their songs from the main music DB
            musicDao.clearTelegramSongsForTopic(chatId, removed.threadId)
            // Delete their playlist from the app playlist store
            telegramRepository.deleteAppPlaylistForTopic(chatId, removed.threadId)
            // Delete the topic row itself
            telegramDao.deleteTopic(removed.id)
        }

        // Insert/update the fresh topic list
        if (freshTopics.isNotEmpty()) {
            telegramDao.insertTopics(freshTopics)
        }
    }

    override suspend fun getTopicsForChannel(chatId: Long): List<TelegramTopicEntity> {
        return telegramDao.getTopicsByChannelOnce(chatId)
    }
    override fun getAllTelegramTopics(): Flow<List<TelegramTopicEntity>> {
        return telegramDao.getAllTopics()
    }

    override suspend fun replaceTelegramSongsForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        songs: List<Song>
    ) {
        // Stamp each song entity with the threadId before inserting
        val entities = songs.mapNotNull { it.toTelegramEntityWithThread(threadId) }
            .filter { it.chatId == chatId }

        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByTopicId(chatId, threadId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }

        // Create/update the per-topic app playlist
        telegramRepository.updateAppPlaylistForTopic(chatId, threadId, topicName, entities)

        // Best-effort sync trigger: if no worker is in flight yet, KEEP enqueues a fresh
        // incremental run that will catch the rows being committed during the topic
        // loop. This is the safety net for forum flows that fail mid-loop and never
        // reach the final saveTelegramChannel() call (the dashboard's syncForumChannel
        // path in particular — its only sync trigger is the end-of-flow channel save).
        // Subsequent topic insertions are no-ops here because the existing worker is
        // running; the calling VM's finally block ensures a follow-up sync runs
        // afterwards to pick up rows added past the worker's Telegram phase.
        requestTelegramUnifiedSync()
    }

    override suspend fun getSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.unshoo.pixelmusic.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getFavoriteSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.unshoo.pixelmusic.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getSongIdByContentUri(contentUri: String): Long? =
        withContext(Dispatchers.IO) {
            musicDao.getSongIdByContentUri(contentUri)
        }

    override fun requestTelegramUnifiedSync() {
        // KEEP — never disturb a full/rebuild sync that shares this unique work name.
        // If no worker is queued or running, this enqueues a fresh incremental run.
        // If one is already in flight, we let it complete; its Telegram phase reads
        // telegram_songs at run time and will pick up rows committed before then.
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.unshoo.pixelmusic.data.worker.SyncWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            com.unshoo.pixelmusic.data.worker.SyncWorker.incrementalSyncWork()
        )
    }

    override suspend fun insertYoutubeSongs(songs: List<Song>): Unit = withContext(Dispatchers.IO) {
        songs.forEach { song ->
            val youtubeId = song.youtubeId 
                ?: if (song.id.startsWith("youtube_")) song.id.substringAfter("youtube_")
                   else if (song.contentUriString.startsWith("youtube://")) song.contentUriString.substringAfter("youtube://")
                   else null
            if (youtubeId != null) {
                val songId = toUnifiedYoutubeSongId(youtubeId)
                val exists = musicDao.getSongsByIdsListSimple(listOf(songId)).isNotEmpty()
                if (!exists) {
                    insertYoutubeSongSkeleton(
                        youtubeId = youtubeId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.albumArtUriString,
                        duration = song.duration,
                        genre = song.genre ?: "YouTube Music"
                    )
                }
            }
        }
    }
}
