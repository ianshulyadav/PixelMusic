package com.unshoo.pixelmusic.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unshoo.pixelmusic.data.jellyfin.JellyfinRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class JellyfinSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: JellyfinRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("JellyfinSyncWorker: Starting full sync")

        return try {
            repository.syncAllPlaylistsAndSongs()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "JellyfinSyncWorker: Sync failed")
            Result.failure(workDataOf(ERROR_MESSAGE to e.message))
        }
    }

    companion object {
        const val WORK_NAME_ALL = "jellyfin_sync_all"
        const val ERROR_MESSAGE = "error_message"

        fun startAllSync() = OneTimeWorkRequestBuilder<JellyfinSyncWorker>()
            .build()
    }
}
