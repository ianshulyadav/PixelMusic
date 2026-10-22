package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.toArtist
import com.unshoo.pixelmusic.data.lossless.LosslessStreamResolver
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import com.unshoo.pixelmusic.utils.AudioMeta
import com.unshoo.pixelmusic.utils.AudioMetaUtils
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SongInfoBottomSheetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val musicRepository: MusicRepository,
    private val telegramRepository: TelegramRepository,
    private val downloadRepository: com.unshoo.pixelmusic.data.remote.youtube.DownloadRepository,
    private val userPreferencesRepository: com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository,
) : ViewModel() {

    data class SongLocationInfo(
        val label: String = "Provider",
        val value: String = "",
        val isCloud: Boolean = false,
        val provider: String? = null,
        val filePath: String? = null,
    )

    private val _audioMeta = MutableStateFlow<AudioMeta?>(null)
    val audioMeta: StateFlow<AudioMeta?> = _audioMeta.asStateFlow()

    val notInterestedSongIds: StateFlow<Set<String>> = userPreferencesRepository.notInterestedSongIdsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptySet()
        )

    private val _songLocationInfo = MutableStateFlow(SongLocationInfo(label = "Provider", value = "", isCloud = false))
    val songLocationInfo: StateFlow<SongLocationInfo> = _songLocationInfo.asStateFlow()

    private val _isResolvingAudioMeta = MutableStateFlow(false)
    val isResolvingAudioMeta: StateFlow<Boolean> = _isResolvingAudioMeta.asStateFlow()

    private val _resolvedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val resolvedArtists: StateFlow<List<Artist>> = _resolvedArtists.asStateFlow()
    private val _isSongDownloaded = MutableStateFlow(false)
    val isSongDownloaded: StateFlow<Boolean> = _isSongDownloaded.asStateFlow()

    private val _isSongDownloading = MutableStateFlow(false)
    val isSongDownloading: StateFlow<Boolean> = _isSongDownloading.asStateFlow()

    private var downloadJob: Job? = null

    fun loadArtistsForSong(song: Song) {
        val refs = song.artists
        if (refs.isEmpty() || refs.size < 2) {
            _resolvedArtists.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = refs.map { it.id }.filter { it != -1L && it != 0L }.distinct()
            val entitiesById = if (ids.isNotEmpty()) {
                musicDao.getArtistsByIds(ids).associateBy { it.id }
            } else {
                emptyMap()
            }
            val resolved = refs.map { ref ->
                entitiesById[ref.id]?.toArtist()
                    ?: Artist(id = ref.id, name = ref.name, songCount = 0)
            }
            _resolvedArtists.value = resolved
        }
    }

    private fun getLocalFilePath(song: Song): String? {
        return when {
            song.path.isNotBlank() && File(song.path).exists() -> song.path
            else -> null
        }
    }

    fun loadAudioMeta(song: Song) {
        _songLocationInfo.value = getSongLocationInfo(song)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val localPath = getLocalFilePath(song)
            val isLocalFile = song.path.isNotBlank() && File(song.path).exists() ||
                song.contentUriString.startsWith("content://") ||
                song.contentUriString.startsWith("file://")
            val isPureLocal = isLocalFile &&
                !song.id.startsWith("youtube_") && song.youtubeId == null &&
                getCloudProviderLabel(song.contentUriString) == null

            if (isPureLocal && localPath != null) {
                val meta = AudioMetaUtils.getAudioMetadata(
                    musicDao = musicDao,
                    id = song.id.toLongOrNull() ?: -1L,
                    filePath = localPath,
                    deepScan = true
                )
                _audioMeta.value = meta.copy(provider = "Local Storage")
                _songLocationInfo.value = SongLocationInfo(
                    label = "Path",
                    value = localPath,
                    isCloud = false,
                    provider = "Local Storage",
                    filePath = localPath
                )
                return@launch
            }

            // Online / streaming track (YouTube, Lossless, etc.)
            val videoId = song.youtubeId
                ?: song.contentUriString.substringAfter("youtube://").takeIf { it.isNotBlank() && !it.startsWith("http") }
                ?: song.id.removePrefix("youtube_")

            // 1. Check if LosslessStreamResolver already resolved this track
            val cachedLossless = LosslessStreamResolver.resultFor(videoId)
                ?: LosslessStreamResolver.resultFor(song.id)
            if (cachedLossless != null) {
                val providerStr = "${cachedLossless.source.name} (${cachedLossless.label})"
                _audioMeta.value = AudioMeta(
                    mimeType = cachedLossless.mimeType,
                    bitrate = cachedLossless.bitrate,
                    sampleRate = cachedLossless.sampleRate,
                    bitDepth = cachedLossless.bitDepth,
                    formatLabel = cachedLossless.label,
                    provider = providerStr
                )
                _songLocationInfo.value = SongLocationInfo(
                    label = "Provider",
                    value = providerStr,
                    isCloud = true,
                    provider = providerStr,
                    filePath = localPath
                )
                return@launch
            }

            // 2. If lossless streaming is enabled, resolve it dynamically
            val isLosslessEnabled = LosslessStreamResolver.isEnabled(context)
            if (isLosslessEnabled && videoId.isNotBlank()) {
                _isResolvingAudioMeta.value = true
                try {
                    val artistsList = song.artist.split(",", "&", "feat.", "ft.", ";")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .ifEmpty { listOf(song.artist).filter { it.isNotBlank() } }
                    val resolved = LosslessStreamResolver.resolve(
                        context = context,
                        request = LosslessStreamResolver.Request(
                            mediaId = videoId,
                            title = song.title,
                            artists = artistsList,
                            album = song.album,
                            durationMs = song.duration.takeIf { it > 0 }
                        ),
                        lowDataMode = false
                    )
                    if (resolved != null) {
                        val providerStr = "${resolved.source.name} (${resolved.label})"
                        _audioMeta.value = AudioMeta(
                            mimeType = resolved.mimeType,
                            bitrate = resolved.bitrate,
                            sampleRate = resolved.sampleRate,
                            bitDepth = resolved.bitDepth,
                            formatLabel = resolved.label,
                            provider = providerStr
                        )
                        _songLocationInfo.value = SongLocationInfo(
                            label = "Provider",
                            value = providerStr,
                            isCloud = true,
                            provider = providerStr,
                            filePath = localPath
                        )
                        _isResolvingAudioMeta.value = false
                        return@launch
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error resolving lossless metadata in SongInfo")
                } finally {
                    _isResolvingAudioMeta.value = false
                }
            }

            // 3. Lossless not found or disabled: if local file exists on disk, read its actual metadata!
            if (localPath != null) {
                val meta = AudioMetaUtils.getAudioMetadata(
                    musicDao = musicDao,
                    id = song.id.toLongOrNull() ?: -1L,
                    filePath = localPath,
                    deepScan = true
                )
                val fallbackProvider = getCloudProviderLabel(song.contentUriString)
                    ?: if (song.youtubeId != null || song.id.startsWith("youtube_")) "YouTube Music" else "Local Storage"
                _audioMeta.value = meta.copy(provider = fallbackProvider)
                _songLocationInfo.value = SongLocationInfo(
                    label = "Path",
                    value = localPath,
                    isCloud = false,
                    provider = fallbackProvider,
                    filePath = localPath
                )
                return@launch
            }

            // 4. Pure online streaming fallback: check actual YouTube / JioSaavn stream info
            val ytBitrate = YoutubeHelper.streamBitrateLruCache.get("${videoId}_high")
                ?: YoutubeHelper.streamBitrateLruCache.get("${videoId}_low")
                ?: YoutubeHelper.streamBitrateLruCache.snapshot().entries.find { it.key.startsWith("${videoId}_") }?.value
            val ytMime = YoutubeHelper.streamMimeTypeLruCache.get("${videoId}_high")
                ?: YoutubeHelper.streamMimeTypeLruCache.get("${videoId}_low")
                ?: YoutubeHelper.streamMimeTypeLruCache.snapshot().entries.find { it.key.startsWith("${videoId}_") }?.value

            val bitrate = ytBitrate ?: 160_000
            val mime = ytMime ?: "audio/webm; codecs=\"opus\""
            val sampleRate = if (mime.contains("opus", true)) 48000 else 44100
            val fallbackProvider = getCloudProviderLabel(song.contentUriString)
                ?: if (song.contentUriString.contains("saavn")) "JioSaavn" else "YouTube Music"
            _audioMeta.value = AudioMeta(
                mimeType = mime,
                bitrate = bitrate,
                sampleRate = sampleRate,
                bitDepth = null,
                formatLabel = null,
                provider = fallbackProvider
            )
            _songLocationInfo.value = SongLocationInfo(
                label = "Provider",
                value = fallbackProvider,
                isCloud = true,
                provider = fallbackProvider,
                filePath = null
            )
        }
    }

    fun getSongLocationInfo(song: Song): SongLocationInfo {
        val videoId = song.youtubeId
            ?: song.contentUriString.substringAfter("youtube://").takeIf { it.isNotBlank() && !it.startsWith("http") }
            ?: song.id.removePrefix("youtube_")
        val cachedLossless = LosslessStreamResolver.resultFor(videoId)
            ?: LosslessStreamResolver.resultFor(song.id)

        val localPath = getLocalFilePath(song)
        val cloudProvider = getCloudProviderLabel(song.contentUriString)
            ?: if (song.youtubeId != null || song.id.startsWith("youtube_")) "YouTube Music" else null

        val providerName = when {
            cachedLossless != null -> "${cachedLossless.source.name} (${cachedLossless.label})"
            cloudProvider != null -> cloudProvider
            localPath != null -> "Local Storage"
            else -> "YouTube Music"
        }

        return SongLocationInfo(
            label = if (localPath != null && cachedLossless == null) "Path" else "Provider",
            value = if (localPath != null && cachedLossless == null) localPath else providerName,
            isCloud = localPath == null || cachedLossless != null,
            provider = providerName,
            filePath = localPath,
        )
    }


    fun loadDownloadState(song: Song) {
        _isSongDownloaded.value = false
        _isSongDownloading.value = false

        val youtubeId = song.youtubeId
        val telegramFileId = song.telegramFileId
        if (youtubeId == null && telegramFileId == null) {
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            // Initial check
            if (telegramFileId != null) {
                _isSongDownloaded.value = telegramRepository.isFileCached(telegramFileId)
                return@launch
            }
            if (youtubeId == null) return@launch
            _isSongDownloaded.value = downloadRepository.isSongDownloaded(youtubeId)

            // Observe the work manager flow for this song
            downloadRepository.getSongDownloadWorkInfoFlow(youtubeId).collect { workInfos ->
                val active = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                }
                _isSongDownloading.value = active

                if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    _isSongDownloaded.value = true
                }
                if (workInfos.any { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }) {
                    _isSongDownloaded.value = downloadRepository.isSongDownloaded(youtubeId)
                }
            }
        }
    }

    fun downloadYoutubeSong(song: Song) {
        val youtubeId = song.youtubeId ?: return
        viewModelScope.launch {
            val youtubeSong = com.unshoo.pixelmusic.data.model.youtube.Song(
                youtubeId = youtubeId,
                title = song.title,
                artist = song.artist,
                duration = com.unshoo.pixelmusic.utils.formatDuration(song.duration),
                thumbnailHref = song.albumArtUriString ?: ""
            )
            val playlist = com.unshoo.pixelmusic.data.model.youtube.Playlist(
                info = com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo(
                    id = com.unshoo.pixelmusic.data.remote.youtube.Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
                    title = "Downloaded Songs"
                ),
                unsortedSongs = listOf(youtubeSong),
                crossRefs = listOf(
                    com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(
                        playlistId = com.unshoo.pixelmusic.data.remote.youtube.Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
                        songId = youtubeSong.youtubeId,
                        position = 0
                    )
                )
            )
            downloadRepository.downloadSong(playlist, youtubeSong)
        }
    }

    fun deleteYoutubeSong(song: Song) {
        val youtubeId = song.youtubeId ?: return
        viewModelScope.launch {
            downloadRepository.deleteSong(youtubeId)
            // Also remove the unified library row for downloaded YouTube songs so the item
            // disappears immediately instead of staying until a later sync.
            song.id.toLongOrNull()?.let { runCatching { musicRepository.deleteById(it) } }
            _isSongDownloaded.value = false
            _isSongDownloading.value = false
        }
    }

    fun downloadTelegramSong(song: Song) {
        val fileId = song.telegramFileId ?: return
        viewModelScope.launch {
            _isSongDownloading.value = true
            val path = runCatching { telegramRepository.downloadFileAwait(fileId, priority = 16) }.getOrNull()
            _isSongDownloaded.value = !path.isNullOrBlank()
            _isSongDownloading.value = false
        }
    }

    fun cancelYoutubeSongDownload(song: Song) {
        val youtubeId = song.youtubeId ?: return
        downloadRepository.cancelSongDownload(youtubeId)
        _isSongDownloading.value = false
    }

    private fun getCloudProviderLabel(contentUriString: String): String? {
        return when {
            contentUriString.startsWith("telegram://") -> "Telegram"
            contentUriString.startsWith("gdrive://") -> "Google Drive"
            contentUriString.startsWith("netease://") -> "NetEase Cloud Music"
            contentUriString.startsWith("qqmusic://") -> "QQ Music"
            contentUriString.contains("saavn") -> "JioSaavn"
            contentUriString.startsWith("youtube://") || contentUriString.contains("youtube") || contentUriString.contains("googlevideo") -> "YouTube Music"
            else -> null
        }
    }

    fun likeOnYouTube(song: Song, like: Boolean, onResult: (Boolean) -> Unit) {
        val videoId = song.youtubeId ?: if (song.contentUriString.startsWith("youtube://")) {
            song.contentUriString.substringAfter("youtube://")
        } else if (song.id.startsWith("youtube_")) {
            song.id.substringAfter("youtube_")
        } else {
            null
        }
        viewModelScope.launch {
            if (like) {
                try {
                    musicRepository.setDislikedStatus(song.id, false)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear disliked status on like")
                }
            }
            if (videoId != null && YouTube.hasLoginCookie()) {
                val result = YouTube.likeVideo(videoId, like)
                onResult(result.isSuccess)
            } else {
                onResult(true)
            }
        }
    }

    fun dislikeOnYouTube(song: Song, dislike: Boolean, onResult: (Boolean) -> Unit) {
        val videoId = song.youtubeId ?: if (song.contentUriString.startsWith("youtube://")) {
            song.contentUriString.substringAfter("youtube://")
        } else if (song.id.startsWith("youtube_")) {
            song.id.substringAfter("youtube_")
        } else {
            null
        }
        viewModelScope.launch {
            try {
                musicRepository.setDislikedStatus(song.id, dislike)
                if (dislike) {
                    musicRepository.setFavoriteStatus(song.id, false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update disliked status on dislike")
            }

            if (videoId != null && YouTube.hasLoginCookie()) {
                val result = YouTube.dislikeVideo(videoId, dislike)
                onResult(result.isSuccess)
            } else {
                onResult(true)
            }
        }
    }

    fun setNotInterested(song: Song, notInterested: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) {
                    song.id.substringAfter("youtube_")
                } else if (song.contentUriString.startsWith("youtube://")) {
                    song.contentUriString.substringAfter("youtube://")
                } else null
                userPreferencesRepository.setSongNotInterested(listOfNotNull(song.id, ytId), notInterested)
                if (notInterested) {
                    com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.pruneNotInterestedFromQueue(song.id, ytId)
                }
                onResult(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update not interested / recommend less status")
                onResult(false)
            }
        }
    }


    fun addToYouTubePlaylist(playlistId: String, song: Song, onResult: (String?) -> Unit) {
        val videoId = song.youtubeId ?: if (song.contentUriString.startsWith("youtube://")) {
            song.contentUriString.substringAfter("youtube://")
        } else if (song.id.startsWith("youtube_")) {
            song.id.substringAfter("youtube_")
        } else {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val result = YouTube.addToPlaylist(playlistId, videoId)
            onResult(result.getOrNull())
        }
    }

    fun isLoggedIn(): Boolean = YouTube.hasLoginCookie()
}
