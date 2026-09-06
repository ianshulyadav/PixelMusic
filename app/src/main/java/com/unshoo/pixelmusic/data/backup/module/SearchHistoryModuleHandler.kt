package com.unshoo.pixelmusic.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unshoo.pixelmusic.data.backup.model.BackupSection
import com.unshoo.pixelmusic.data.database.SearchHistoryDao
import com.unshoo.pixelmusic.data.database.SearchHistoryEntity
import com.unshoo.pixelmusic.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryModuleHandler @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.SEARCH_HISTORY

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(searchHistoryDao.getAll())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        searchHistoryDao.getAll().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, SearchHistoryEntity::class.java).type
        val history: List<SearchHistoryEntity> = gson.fromJson(payload, type)
        searchHistoryDao.replaceAll(history)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
