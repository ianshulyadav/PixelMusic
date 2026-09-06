package com.unshoo.pixelmusic.presentation.navidrome.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.unshoo.pixelmusic.data.database.NavidromePlaylistEntity
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.navidrome.NavidromeRepository
import com.unshoo.pixelmusic.data.navidrome.model.NavidromeMusicFolder
import com.unshoo.pixelmusic.data.worker.NavidromeSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@HiltViewModel
class NavidromeDashboardViewModel @Inject constructor(
    private val repository: NavidromeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val playlists: StateFlow<ImmutableList<NavidromePlaylistEntity>> = repository.getPlaylists()
        .map { it.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow<Float?>(null)
    val syncProgress: StateFlow<Float?> = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val selectedPlaylistSongs: StateFlow<ImmutableList<Song>> = _selectedPlaylistSongs.asStateFlow()

    private val _selectedPlaylistName = MutableStateFlow<String?>(null)
    val selectedPlaylistName: StateFlow<String?> = _selectedPlaylistName.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<NavidromeMusicFolder>>(persistentListOf())
    val musicFolders: StateFlow<ImmutableList<NavidromeMusicFolder>> = _musicFolders.asStateFlow()

    private val _musicFoldersLoadFailed = MutableStateFlow(false)
    val musicFoldersLoadFailed: StateFlow<Boolean> = _musicFoldersLoadFailed.asStateFlow()

    val selectedMusicFolderIds: StateFlow<Set<String>> = repository.selectedMusicFolderIdsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _librarySelectionNeedsSync = MutableStateFlow(false)
    val librarySelectionNeedsSync: StateFlow<Boolean> = _librarySelectionNeedsSync.asStateFlow()

    private var selectedPlaylistJob: Job? = null

    val username: String? get() = repository.username
    val serverUrl: String? get() = repository.serverUrl
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow
    val lastSyncTime: Long get() = repository.lastFullSyncTime

    init {
        observeSyncWorker()
        loadMusicFolders()
        val lastSync = repository.lastFullSyncTime
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSync > NavidromeRepository.SYNC_THRESHOLD_MS) {
            syncAllPlaylistsAndSongs()
        }
    }

    private fun observeSyncWorker() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_SYNC_ALL).collect { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@collect
                
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        _isSyncing.value = true
                        val progress = workInfo.progress.getFloat(NavidromeSyncWorker.PROGRESS_VALUE, 0f)
                        _syncProgress.value = if (progress > 0f) progress else null
                        _syncMessage.value = workInfo.progress.getString(NavidromeSyncWorker.PROGRESS_MESSAGE)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                        _librarySelectionNeedsSync.value = false
                    }
                    WorkInfo.State.FAILED -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                        _syncMessage.value = workInfo.outputData.getString(NavidromeSyncWorker.ERROR_MESSAGE) ?: "Sync failed"
                    }
                    else -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                    }
                }
            }
        }
    }

    fun syncAllPlaylistsAndSongs() {
        workManager.enqueueUniqueWork(
            WORK_NAME_SYNC_ALL,
            ExistingWorkPolicy.KEEP,
            NavidromeSyncWorker.startAllSync()
        )
    }

    fun loadMusicFolders() {
        viewModelScope.launch {
            repository.getMusicFolders()
                .onSuccess { folders ->
                    _musicFolders.value = folders.toImmutableList()
                    _musicFoldersLoadFailed.value = false
                }
                .onFailure {
                    _musicFolders.value = persistentListOf()
                    _musicFoldersLoadFailed.value = true
                }
        }
    }

    fun setSelectedMusicFolderIds(folderIds: Set<String>) {
        viewModelScope.launch {
            repository.setSelectedMusicFolderIds(folderIds)
            _librarySelectionNeedsSync.value = true
        }
    }

    fun syncPlaylistSongs(playlistId: String) {
        workManager.enqueueUniqueWork(
            "navidrome_sync_playlist_$playlistId",
            ExistingWorkPolicy.REPLACE,
            NavidromeSyncWorker.startPlaylistSync(playlistId)
        )
    }

    fun loadPlaylistSongs(playlistId: String, playlistName: String) {
        _selectedPlaylistName.value = playlistName
        selectedPlaylistJob?.cancel()
        selectedPlaylistJob = viewModelScope.launch {
            repository.getPlaylistSongs(playlistId).collect { songs ->
                _selectedPlaylistSongs.value = songs.toImmutableList()
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    companion object {
        private const val WORK_NAME_SYNC_ALL = NavidromeSyncWorker.WORK_NAME_ALL
    }
}
