package com.unshoo.pixelmusic.presentation.jellyfin.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.unshoo.pixelmusic.data.jellyfin.JellyfinRepository
import com.unshoo.pixelmusic.data.jellyfin.model.JellyfinLibrary
import com.unshoo.pixelmusic.data.worker.JellyfinSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JellyfinLoginState {
    data object Idle : JellyfinLoginState
    data object Loading : JellyfinLoginState

    /**
     * Authenticated, but the server exposes more than one music library —
     * ask which ones to sync before the first sync runs.
     */
    data class SelectLibraries(
        val username: String,
        val libraries: ImmutableList<JellyfinLibrary>
    ) : JellyfinLoginState

    data class Success(val username: String) : JellyfinLoginState
    data class Error(val message: String) : JellyfinLoginState
}

@HiltViewModel
class JellyfinLoginViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow<JellyfinLoginState>(JellyfinLoginState.Idle)
    val state: StateFlow<JellyfinLoginState> = _state.asStateFlow()

    fun login(serverUrl: String, username: String, password: String) {
        if (_state.value is JellyfinLoginState.Loading) return

        viewModelScope.launch {
            _state.value = JellyfinLoginState.Loading

            val result = repository.login(serverUrl, username, password)

            _state.value = result.fold(
                onSuccess = { loggedInUser -> stateAfterLogin(loggedInUser) },
                onFailure = { JellyfinLoginState.Error(it.message ?: "Login failed") }
            )
        }
    }

    /**
     * Fetch the server's libraries right after login. With more than one music
     * library the user picks what to sync; otherwise the first sync starts
     * immediately with everything included.
     */
    private suspend fun stateAfterLogin(username: String): JellyfinLoginState {
        val libraries = repository.getLibraries().getOrElse { emptyList() }
        val musicLibraryCount = libraries.count { it.isMusic }

        return if (musicLibraryCount > 1) {
            JellyfinLoginState.SelectLibraries(username, libraries.toImmutableList())
        } else {
            enqueueInitialSync()
            JellyfinLoginState.Success(username)
        }
    }

    fun confirmLibrarySelection(libraryIds: Set<String>) {
        val current = _state.value as? JellyfinLoginState.SelectLibraries ?: return
        viewModelScope.launch {
            repository.setSelectedLibraryIds(libraryIds)
            enqueueInitialSync()
            _state.value = JellyfinLoginState.Success(current.username)
        }
    }

    /** Dismissing the picker keeps the default: sync every music library. */
    fun skipLibrarySelection() {
        val current = _state.value as? JellyfinLoginState.SelectLibraries ?: return
        enqueueInitialSync()
        _state.value = JellyfinLoginState.Success(current.username)
    }

    private fun enqueueInitialSync() {
        workManager.enqueueUniqueWork(
            JellyfinSyncWorker.WORK_NAME_ALL,
            ExistingWorkPolicy.KEEP,
            JellyfinSyncWorker.startAllSync()
        )
    }

    fun clearError() {
        if (_state.value is JellyfinLoginState.Error) {
            _state.value = JellyfinLoginState.Idle
        }
    }

    fun reset() {
        _state.value = JellyfinLoginState.Idle
    }
}
