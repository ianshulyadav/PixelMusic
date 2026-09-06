package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.data.model.Lyrics
import com.unshoo.pixelmusic.data.repository.LyricsSearchResult
import kotlinx.collections.immutable.ImmutableList

sealed interface LyricsSearchUiState {
    object Idle : LyricsSearchUiState
    object Loading : LyricsSearchUiState
    data class PickResult(val query: String, val results: ImmutableList<LyricsSearchResult>) : LyricsSearchUiState
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    data class NotFound(val message: String, val allowManualSearch: Boolean = true) : LyricsSearchUiState
    data class Error(val message: String, val query: String? = null) : LyricsSearchUiState
}
