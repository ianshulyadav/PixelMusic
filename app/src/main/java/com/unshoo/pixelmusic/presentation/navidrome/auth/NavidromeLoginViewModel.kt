package com.unshoo.pixelmusic.presentation.navidrome.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.unshoo.pixelmusic.data.navidrome.NavidromeRepository
import com.unshoo.pixelmusic.data.navidrome.model.NavidromeMusicFolder
import com.unshoo.pixelmusic.data.worker.NavidromeSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NavidromeLoginState {
    data object Idle : NavidromeLoginState
    data object Loading : NavidromeLoginState

    /**
     * Authenticated, but the server exposes more than one music folder —
     * ask which ones to sync before the first sync runs.
     */
    data class SelectLibraries(
        val username: String,
        val musicFolders: ImmutableList<NavidromeMusicFolder>
    ) : NavidromeLoginState

    data class Success(val username: String) : NavidromeLoginState
    data class Error(val message: String) : NavidromeLoginState
}

@HiltViewModel
class NavidromeLoginViewModel @Inject constructor(
    private val repository: NavidromeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow<NavidromeLoginState>(NavidromeLoginState.Idle)
    val state: StateFlow<NavidromeLoginState> = _state.asStateFlow()

    fun login(serverUrl: String, username: String, password: String) {
        if (_state.value is NavidromeLoginState.Loading) return

        viewModelScope.launch {
            _state.value = NavidromeLoginState.Loading

            val result = repository.login(serverUrl, username, password)

            _state.value = result.fold(
                onSuccess = { loggedInUser -> stateAfterLogin(loggedInUser) },
                onFailure = { NavidromeLoginState.Error(it.message ?: "Login failed") }
            )
        }
    }

    /**
     * Fetch the server's music folders right after login. With more than one
     * folder the user picks what to sync; otherwise the first sync starts
     * immediately with everything included.
     */
    private suspend fun stateAfterLogin(username: String): NavidromeLoginState {
        val musicFolders = repository.getMusicFolders().getOrElse { emptyList() }

        return if (musicFolders.size > 1) {
            NavidromeLoginState.SelectLibraries(username, musicFolders.toImmutableList())
        } else {
            enqueueInitialSync()
            NavidromeLoginState.Success(username)
        }
    }

    fun confirmLibrarySelection(folderIds: Set<String>) {
        val current = _state.value as? NavidromeLoginState.SelectLibraries ?: return
        viewModelScope.launch {
            repository.setSelectedMusicFolderIds(folderIds)
            enqueueInitialSync()
            _state.value = NavidromeLoginState.Success(current.username)
        }
    }

    /** Dismissing the picker keeps the default: sync every music folder. */
    fun skipLibrarySelection() {
        val current = _state.value as? NavidromeLoginState.SelectLibraries ?: return
        enqueueInitialSync()
        _state.value = NavidromeLoginState.Success(current.username)
    }

    private fun enqueueInitialSync() {
        workManager.enqueueUniqueWork(
            NavidromeSyncWorker.WORK_NAME_ALL,
            ExistingWorkPolicy.KEEP,
            NavidromeSyncWorker.startAllSync()
        )
    }

    fun clearError() {
        if (_state.value is NavidromeLoginState.Error) {
            _state.value = NavidromeLoginState.Idle
        }
    }

    fun reset() {
        _state.value = NavidromeLoginState.Idle
    }
}
