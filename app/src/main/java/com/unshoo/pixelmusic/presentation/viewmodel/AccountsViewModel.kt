package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.gdrive.GDriveRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.drinkless.tdlib.TdApi

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    YOUTUBE
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    private val gDriveRepository: GDriveRepository,
    private val datastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository,
    private val syncManager: com.unshoo.pixelmusic.data.worker.SyncManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val isSyncing = syncManager.isSyncing

    fun syncLibrary() {
        viewModelScope.launch {
            syncManager.sync()
        }
    }

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    private val telegramStateFlow = combine(
        telegramRepository.authorizationState
            .map { it is TdApi.AuthorizationStateReady }
            .distinctUntilChanged(),
        musicRepository.getAllTelegramChannels().map { it.size }
    ) { connected, channelCount ->
        connected to channelCount
    }

    private val gDriveStateFlow = combine(
        gDriveRepository.isLoggedInFlow,
        gDriveRepository.getFolders().map { it.size }
    ) { connected, folderCount ->
        connected to folderCount
    }

    private val youtubeStateFlow = combine(
        datastoreRepository.cookies.map { it.toRawCookie().isNotEmpty() }.distinctUntilChanged(),
        com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).playlistRepository().observeAll().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        combine(
            listOf(
                telegramStateFlow,
                gDriveStateFlow,
                youtubeStateFlow
            )
        ) { it.toList() },
        loggingOutServices
    ) { states, activeLogouts ->
        val (telegramConnected, telegramChannelCount) = states[0] as Pair<Boolean, Int>
        val (gDriveConnected, gDriveFolderCount) = states[1] as Pair<Boolean, Int>
        val (youtubeConnected, youtubePlaylistCount) = states[2] as Pair<Boolean, Int>

        val connectedAccounts = buildList {
            if (telegramConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.TELEGRAM,
                        title = "Telegram",
                        accountLabel = "Active Telegram session",
                        syncedContentLabel = formatCount(
                            count = telegramChannelCount,
                            singular = "synced channel",
                            plural = "synced channels"
                        ),
                        isLoggingOut = ExternalServiceAccount.TELEGRAM in activeLogouts
                    )
                )
            }
            if (gDriveConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.GOOGLE_DRIVE,
                        title = "Google Drive",
                        accountLabel = gDriveRepository.userDisplayName
                            ?.takeIf { it.isNotBlank() }
                            ?: gDriveRepository.userEmail
                                ?.takeIf { it.isNotBlank() }
                            ?: "Google account connected",
                        syncedContentLabel = formatCount(
                            count = gDriveFolderCount,
                            singular = "synced folder",
                            plural = "synced folders"
                        ),
                        isLoggingOut = ExternalServiceAccount.GOOGLE_DRIVE in activeLogouts
                    )
                )
            }
            if (youtubeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.YOUTUBE,
                        title = "YouTube Client",
                        accountLabel = "YouTube session connected",
                        syncedContentLabel = formatCount(
                            count = youtubePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.YOUTUBE in activeLogouts
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!telegramConnected) add(ExternalServiceAccount.TELEGRAM)
            if (!gDriveConnected) add(ExternalServiceAccount.GOOGLE_DRIVE)
            if (!youtubeConnected) add(ExternalServiceAccount.YOUTUBE)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.TELEGRAM -> {
                            telegramRepository.logout()
                            telegramRepository.clearMemoryCache()
                            musicRepository.clearTelegramData()
                        }
                        ExternalServiceAccount.GOOGLE_DRIVE -> gDriveRepository.logout()
                        ExternalServiceAccount.YOUTUBE -> {
                            datastoreRepository.saveCookies(com.unshoo.pixelmusic.data.model.youtube.Cookies(""))
                            datastoreRepository.saveDataSyncId("")
                            com.unshoo.pixelmusic.data.database.youtube.AppDatabase.clearDownloads(context)
                        }
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
