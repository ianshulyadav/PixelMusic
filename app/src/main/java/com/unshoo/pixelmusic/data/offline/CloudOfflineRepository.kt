package com.unshoo.pixelmusic.data.offline

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.unshoo.pixelmusic.data.database.OfflineTrackDao
import com.unshoo.pixelmusic.data.database.OfflineTrackEntity
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.worker.CloudTrackDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class OfflineDownloadStatus(val storageValue: String) {
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    COMPLETE("complete"),
    FAILED("failed");

    companion object {
        fun fromStorage(value: String): OfflineDownloadStatus =
            entries.firstOrNull { it.storageValue == value } ?: FAILED
    }
}

data class OfflineDownload(
    val downloadId: String,
    val sourceUri: String,
    val status: OfflineDownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val errorMessage: String?,
    val title: String = "",
    val provider: String = ""
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

@Singleton
class CloudOfflineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineTrackDao,
    private val workManager: WorkManager
) {
    private val mutationMutex = Mutex()

    fun observe(song: Song): Flow<OfflineDownload?> {
        val sourceUri = canonicalSourceUri(song) ?: return kotlinx.coroutines.flow.flowOf(null)
        return observe(sourceUri)
    }

    fun observe(sourceUri: String): Flow<OfflineDownload?> =
        dao.observeBySourceUri(sourceUri).map { it?.toModel() }

    fun observeCompleted(): Flow<List<OfflineDownload>> =
        dao.observeCompleted().map { rows -> rows.map(OfflineTrackEntity::toModel) }

    fun observeAll(): Flow<List<OfflineDownload>> =
        dao.observeAll().map { rows -> rows.map(OfflineTrackEntity::toModel) }

    suspend fun enqueue(song: Song) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val sourceUri = canonicalSourceUri(song) ?: return@withLock
            val provider = providerFor(sourceUri) ?: return@withLock
            val downloadId = downloadId(sourceUri)
            val existing = dao.getBySourceUri(sourceUri)
            if (existing?.state == OfflineDownloadStatus.COMPLETE.storageValue &&
                existing.localPath?.let(::File)?.let { it.isFile && it.length() > 0L } == true
            ) {
                return@withLock
            }
            existing?.let {
                it.localPath?.let(::File)?.delete()
                deleteAttemptFiles(context, it.downloadId, it.attemptId)
            }

            val attemptId = UUID.randomUUID().toString()
            val request = downloadRequest(
                downloadId = downloadId,
                attemptId = attemptId,
                sourceUri = sourceUri,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                artworkUri = song.albumArtUriString,
                youtubeId = song.youtubeId
            )

            val now = System.currentTimeMillis()
            dao.upsert(
                OfflineTrackEntity(
                    downloadId = downloadId,
                    attemptId = attemptId,
                    songId = song.id,
                    sourceUri = sourceUri,
                    provider = provider,
                    title = song.title,
                    mimeType = song.mimeType,
                    localPath = null,
                    state = OfflineDownloadStatus.QUEUED.storageValue,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )

            workManager.enqueueUniqueWork(
                workName(downloadId),
                ExistingWorkPolicy.REPLACE,
                request
            ).await()
        }
    }

    suspend fun enqueueAll(songs: Collection<Song>) {
        songs.asSequence()
            .filter { isCloudSong(it) }
            .distinctBy { canonicalSourceUri(it) }
            .forEach { enqueue(it) }
    }

    suspend fun retry(sourceUri: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val existing = dao.getBySourceUri(sourceUri) ?: return@withLock
            if (existing.state != OfflineDownloadStatus.FAILED.storageValue) return@withLock

            existing.localPath?.let(::File)?.delete()
            deleteAttemptFiles(context, existing.downloadId, existing.attemptId)

            val attemptId = UUID.randomUUID().toString()
            val request = downloadRequest(
                downloadId = existing.downloadId,
                attemptId = attemptId,
                sourceUri = sourceUri
            )
            dao.upsert(
                existing.copy(
                    attemptId = attemptId,
                    localPath = null,
                    state = OfflineDownloadStatus.QUEUED.storageValue,
                    bytesDownloaded = 0L,
                    totalBytes = null,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
            )
            workManager.enqueueUniqueWork(
                workName(existing.downloadId),
                ExistingWorkPolicy.REPLACE,
                request
            ).await()
        }
    }

    suspend fun remove(song: Song) {
        val sourceUri = canonicalSourceUri(song) ?: return
        remove(sourceUri)
    }

    suspend fun remove(sourceUri: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val entity = dao.getBySourceUri(sourceUri) ?: return@withLock
            if (dao.deleteBySourceUriForAttempt(sourceUri, entity.attemptId) == 0) {
                return@withLock
            }

            try {
                workManager.cancelUniqueWork(workName(entity.downloadId)).await()
            } finally {
                entity.localPath?.let(::File)?.takeIf { it.exists() }?.delete()
                deleteAttemptFiles(context, entity.downloadId, entity.attemptId)
            }
        }
    }

    suspend fun deleteAllDownloaded() = withContext(Dispatchers.IO) {
        val completed = dao.getCompleted()
        completed.forEach { entity ->
            entity.localPath?.let(::File)?.delete()
            dao.deleteBySourceUri(entity.sourceUri)
        }
        downloadDirectory(context).listFiles()?.forEach(File::delete)
    }

    /** Called on playback loader thread; strictly verifies file exists and is non-empty on disk. */
    suspend fun resolveLocalUri(sourceUri: String): Uri? = withContext(Dispatchers.IO) {
        val entity = dao.getBySourceUri(sourceUri) ?: return@withContext null
        if (entity.state != OfflineDownloadStatus.COMPLETE.storageValue) return@withContext null
        val file = entity.localPath?.let(::File)
        if (file?.isFile == true && file.length() > 0L) {
            Uri.fromFile(file)
        } else {
            // Clean up stale database record so it doesn't mislead offline playback
            dao.deleteBySourceUri(sourceUri)
            null
        }
    }

    companion object {
        fun canonicalSourceUri(song: Song): String? {
            if (song.contentUriString.startsWith("youtube://")) {
                return song.contentUriString
            }
            if (!song.youtubeId.isNullOrBlank()) {
                return "youtube://${song.youtubeId}"
            }
            if (song.id.startsWith("youtube_")) {
                return "youtube://${song.id.removePrefix("youtube_")}"
            }
            return null
        }

        fun isCloudSong(song: Song): Boolean = canonicalSourceUri(song) != null

        fun providerFor(sourceUri: String): String? = when (sourceUri.substringBefore(':', "").lowercase()) {
            "youtube" -> "youtube"
            else -> null
        }

        fun downloadId(sourceUri: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sourceUri.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun workName(downloadId: String): String = "cloud_track_download_$downloadId"

        internal fun attemptFileStem(downloadId: String, attemptId: String): String =
            "$downloadId.$attemptId"

        internal fun deleteAttemptFiles(context: Context, downloadId: String, attemptId: String) {
            val prefix = "${attemptFileStem(downloadId, attemptId)}."
            downloadDirectory(context).listFiles()
                ?.asSequence()
                ?.filter { it.name.startsWith(prefix) }
                ?.forEach(File::delete)
        }

        fun downloadDirectory(context: Context): File =
            File(context.filesDir, "cloud_downloads").apply { mkdirs() }
    }

    private fun downloadRequest(
        downloadId: String,
        attemptId: String,
        sourceUri: String,
        title: String = "",
        artist: String = "",
        album: String = "",
        artworkUri: String? = null,
        youtubeId: String? = null
    ) = OneTimeWorkRequestBuilder<CloudTrackDownloadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setInputData(
            workDataOf(
                CloudTrackDownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                CloudTrackDownloadWorker.KEY_ATTEMPT_ID to attemptId,
                CloudTrackDownloadWorker.KEY_SOURCE_URI to sourceUri,
                CloudTrackDownloadWorker.KEY_TITLE to title,
                CloudTrackDownloadWorker.KEY_ARTIST to artist,
                CloudTrackDownloadWorker.KEY_ALBUM to album,
                CloudTrackDownloadWorker.KEY_ARTWORK_URI to (artworkUri ?: ""),
                CloudTrackDownloadWorker.KEY_YOUTUBE_ID to (youtubeId ?: "")
            )
        )
        .addTag(CloudTrackDownloadWorker.TAG)
        .addTag(workName(downloadId))
        .build()
}

private fun OfflineTrackEntity.toModel() = OfflineDownload(
    downloadId = downloadId,
    sourceUri = sourceUri,
    status = OfflineDownloadStatus.fromStorage(state),
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    localPath = localPath,
    errorMessage = errorMessage,
    title = title,
    provider = provider
)
