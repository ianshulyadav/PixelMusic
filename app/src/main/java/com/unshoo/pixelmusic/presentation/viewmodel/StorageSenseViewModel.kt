package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.storage.DuplicateSongGroup
import com.unshoo.pixelmusic.data.storage.ForgottenSongCandidate
import com.unshoo.pixelmusic.data.storage.StorageSenseRepository
import com.unshoo.pixelmusic.data.storage.StorageSpaceInfo
import com.unshoo.pixelmusic.data.storage.StorageTrashItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageSenseUiState(
    val storageInfo: StorageSpaceInfo? = null,
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val selectedTab: Int = 0, // 0 = Review, 1 = Trash, 2 = Rules
    val candidates: List<ForgottenSongCandidate> = emptyList(),
    val selectedCandidateIds: Set<String> = emptySet(),
    val trashItems: List<StorageTrashItem> = emptyList(),
    val selectedTrashIds: Set<String> = emptySet(),
    val duplicateGroups: List<DuplicateSongGroup> = emptyList(),
    val invalidSongs: List<Song> = emptyList(),
    // Rules
    val smartStorageEnabled: Boolean = true,
    val unplayedDays: Int = 90,
    val includeNeverPlayed: Boolean = false,
    val minFileSizeMb: Int = 0,
    val neverSuggestFavorites: Boolean = true,
    val ignoreRecentDays: Int = 14,
    val streamCacheTtlDays: Int = 7,
    val streamCacheMaxSizeMb: Int = 1024,
    val autoPruneSearchHistory: Boolean = true,
    val autoCleanInvalidSongs: Boolean = false
)

@HiltViewModel
class StorageSenseViewModel @Inject constructor(
    private val storageSenseRepository: StorageSenseRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageSenseUiState())
    val uiState: StateFlow<StorageSenseUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()

    init {
        observePreferences()
        refreshAll()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.smartStorageActiveTabFlow.collect { tab ->
                _uiState.update { it.copy(selectedTab = tab) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(smartStorageEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageUnplayedDaysFlow.collect { days ->
                _uiState.update { it.copy(unplayedDays = days) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageIncludeNeverPlayedFlow.collect { inc ->
                _uiState.update { it.copy(includeNeverPlayed = inc) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageMinFileSizeMbFlow.collect { mb ->
                _uiState.update { it.copy(minFileSizeMb = mb) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageNeverSuggestFavoritesFlow.collect { fav ->
                _uiState.update { it.copy(neverSuggestFavorites = fav) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.smartStorageIgnoreRecentDaysFlow.collect { days ->
                _uiState.update { it.copy(ignoreRecentDays = days) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.streamCacheTtlDaysFlow.collect { ttl ->
                _uiState.update { it.copy(streamCacheTtlDays = ttl) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.streamCacheMaxSizeMbFlow.collect { maxMb ->
                _uiState.update { it.copy(streamCacheMaxSizeMb = maxMb) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoPruneSearchHistoryFlow.collect { prune ->
                _uiState.update { it.copy(autoPruneSearchHistory = prune) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoCleanInvalidSongsFlow.collect { clean ->
                _uiState.update { it.copy(autoCleanInvalidSongs = clean) }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val info = storageSenseRepository.getStorageSpaceInfo()
            val candidates = storageSenseRepository.getForgottenSongCandidates()
            val trash = storageSenseRepository.getTrashItems()
            val invalid = storageSenseRepository.findInvalidSongs()
            val duplicates = storageSenseRepository.findDuplicateSongGroups()

            _uiState.update {
                it.copy(
                    storageInfo = info,
                    candidates = candidates,
                    trashItems = trash,
                    invalidSongs = invalid,
                    duplicateGroups = duplicates,
                    isLoading = false
                )
            }
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageActiveTab(tabIndex)
            _uiState.update { it.copy(selectedTab = tabIndex) }
        }
    }

    fun toggleCandidateSelection(songId: String) {
        _uiState.update { current ->
            val set = current.selectedCandidateIds.toMutableSet()
            if (set.contains(songId)) set.remove(songId) else set.add(songId)
            current.copy(selectedCandidateIds = set)
        }
    }

    fun selectAllCandidates() {
        _uiState.update { current ->
            current.copy(selectedCandidateIds = current.candidates.map { it.song.id }.toSet())
        }
    }

    fun deselectAllCandidates() {
        _uiState.update { it.copy(selectedCandidateIds = emptySet()) }
    }

    fun moveSelectedToTrash() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedCandidateIds
            if (selectedIds.isEmpty()) return@launch

            val selectedCandidates = _uiState.value.candidates.filter { selectedIds.contains(it.song.id) }
            storageSenseRepository.moveToTrash(selectedCandidates)
            _uiState.update { it.copy(selectedCandidateIds = emptySet()) }
            _actionMessage.emit("Moved ${selectedCandidates.size} track(s) to Trash")
            refreshAll()
        }
    }

    fun toggleTrashSelection(songId: String) {
        _uiState.update { current ->
            val set = current.selectedTrashIds.toMutableSet()
            if (set.contains(songId)) set.remove(songId) else set.add(songId)
            current.copy(selectedTrashIds = set)
        }
    }

    fun selectAllTrash() {
        _uiState.update { current ->
            current.copy(selectedTrashIds = current.trashItems.map { it.songId }.toSet())
        }
    }

    fun deselectAllTrash() {
        _uiState.update { it.copy(selectedTrashIds = emptySet()) }
    }

    fun restoreSelectedFromTrash() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedTrashIds
            if (selectedIds.isEmpty()) return@launch

            storageSenseRepository.restoreFromTrash(selectedIds)
            _uiState.update { it.copy(selectedTrashIds = emptySet()) }
            _actionMessage.emit("Restored ${selectedIds.size} track(s)")
            refreshAll()
        }
    }

    fun restoreFromTrash(songIds: Set<String>) {
        viewModelScope.launch {
            if (songIds.isEmpty()) return@launch
            storageSenseRepository.restoreFromTrash(songIds)
            _uiState.update { current ->
                current.copy(selectedTrashIds = current.selectedTrashIds - songIds)
            }
            _actionMessage.emit("Restored ${songIds.size} track(s)")
            refreshAll()
        }
    }

    fun restoreAllFromTrash() {
        viewModelScope.launch {
            val allIds = _uiState.value.trashItems.map { it.songId }.toSet()
            if (allIds.isEmpty()) return@launch

            storageSenseRepository.restoreFromTrash(allIds)
            _uiState.update { it.copy(selectedTrashIds = emptySet()) }
            _actionMessage.emit("Restored all items")
            refreshAll()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val freed = storageSenseRepository.emptyTrash()
            _uiState.update { it.copy(isCleaning = false, selectedTrashIds = emptySet()) }
            val mb = freed / (1024 * 1024)
            _actionMessage.emit("Trash emptied. Reclaimed ${mb} MB")
            refreshAll()
        }
    }

    fun clearAllReclaimable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val freed = storageSenseRepository.clearAllReclaimableCache()
            _uiState.update { it.copy(isCleaning = false) }
            val mb = freed / (1024 * 1024)
            _actionMessage.emit("Reclaimed ${mb} MB cache")
            refreshAll()
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val freed = storageSenseRepository.clearStreamCache()
            _uiState.update { it.copy(isCleaning = false) }
            val mb = freed / (1024 * 1024)
            _actionMessage.emit("Stream cache cleared (${mb} MB)")
            refreshAll()
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val freed = storageSenseRepository.clearImageCache()
            _uiState.update { it.copy(isCleaning = false) }
            val mb = freed / (1024 * 1024)
            _actionMessage.emit("Image cache cleared (${mb} MB)")
            refreshAll()
        }
    }

    fun clearLyricsCache() {
        viewModelScope.launch {
            storageSenseRepository.clearLyricsCache()
            _actionMessage.emit("Lyrics cache cleared")
            refreshAll()
        }
    }

    fun pruneSearchHistory() {
        viewModelScope.launch {
            val pruned = storageSenseRepository.pruneSearchHistory()
            _actionMessage.emit("Pruned $pruned old search query(s)")
            refreshAll()
        }
    }

    fun cleanInvalidSongs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val count = storageSenseRepository.cleanInvalidSongs()
            _uiState.update { it.copy(isCleaning = false) }
            _actionMessage.emit("Cleaned $count invalid track entries")
            refreshAll()
        }
    }

    fun cleanDuplicateSongs(songsToDelete: List<Song>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val freed = storageSenseRepository.deleteDuplicateSongs(songsToDelete)
            _uiState.update { it.copy(isCleaning = false) }
            val mb = freed / (1024 * 1024)
            _actionMessage.emit("Removed ${songsToDelete.size} duplicates (${mb} MB)")
            refreshAll()
        }
    }

    // Rules updates
    fun setSmartStorageEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageEnabled(enabled)
            refreshAll()
        }
    }

    fun setSmartStorageUnplayedDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageUnplayedDays(days)
            refreshAll()
        }
    }

    fun setSmartStorageIncludeNeverPlayed(include: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageIncludeNeverPlayed(include)
            refreshAll()
        }
    }

    fun setSmartStorageMinFileSizeMb(minMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageMinFileSizeMb(minMb)
            refreshAll()
        }
    }

    fun setSmartStorageNeverSuggestFavorites(neverSuggest: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageNeverSuggestFavorites(neverSuggest)
            refreshAll()
        }
    }

    fun setSmartStorageIgnoreRecentDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartStorageIgnoreRecentDays(days)
            refreshAll()
        }
    }

    fun setStreamCacheTtlDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setStreamCacheTtlDays(days)
            refreshAll()
        }
    }

    fun setStreamCacheMaxSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setStreamCacheMaxSizeMb(sizeMb)
            refreshAll()
        }
    }

    fun setAutoPruneSearchHistory(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoPruneSearchHistory(enabled)
        }
    }

    fun setAutoCleanInvalidSongs(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoCleanInvalidSongs(enabled)
        }
    }
}
