package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.data.model.LibraryTabId
import com.unshoo.pixelmusic.data.model.toLibraryTabIdOrNull
import com.unshoo.pixelmusic.utils.traceSection
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@ViewModelScoped
class LibraryTabsStateHolder @Inject constructor() {
    fun showSortingSheet(isSortingSheetVisible: MutableStateFlow<Boolean>) {
        isSortingSheetVisible.value = true
    }

    fun hideSortingSheet(isSortingSheetVisible: MutableStateFlow<Boolean>) {
        isSortingSheetVisible.value = false
    }

    fun onLibraryTabSelected(
        tabIndex: Int,
        libraryTabs: List<String>,
        loadedTabs: MutableStateFlow<Set<String>>,
        currentLibraryTabId: MutableStateFlow<LibraryTabId>,
        saveLastTabIndex: suspend (Int) -> Unit,
        scope: CoroutineScope,
        loadSongs: () -> Unit,
        loadAlbums: () -> Unit,
        loadArtists: () -> Unit,
        loadFolders: () -> Unit
    ): Unit = traceSection("PlayerViewModel.onLibraryTabSelected") {
        scope.launch { saveLastTabIndex(tabIndex) }

        val tabIdentifier = libraryTabs.getOrNull(tabIndex) ?: return
        val tabId = tabIdentifier.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
        currentLibraryTabId.value = tabId

        if (loadedTabs.value.contains(tabIdentifier)) {
            Timber.tag("PlayerViewModel").d("Tab '$tabIdentifier' already loaded. Skipping data load.")
            return
        }

        Timber.tag("PlayerViewModel").d("Tab '$tabIdentifier' selected. Attempting to load data.")
        scope.launch {
            traceSection("PlayerViewModel.onLibraryTabSelected_coroutine_load") {
                when (tabId) {
                    LibraryTabId.SONGS -> loadSongs()
                    LibraryTabId.ALBUMS -> loadAlbums()
                    LibraryTabId.ARTISTS -> loadArtists()
                    LibraryTabId.FOLDERS -> loadFolders()
                    else -> Unit
                }
                loadedTabs.update { currentTabs -> currentTabs + tabIdentifier }
                Timber.tag("PlayerViewModel").d("Tab '$tabIdentifier' marked as loaded. Current loaded tabs: ${loadedTabs.value}")
            }
        }
    }
}
