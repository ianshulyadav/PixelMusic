package com.unshoo.pixelmusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SearchHistoryEntity>)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSearches(limit: Int): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldSearches(keepCount: Int = 25)

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun getPrunableCount(keepCount: Int = 25): Int

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    @Transaction
    suspend fun replaceAll(items: List<SearchHistoryEntity>) {
        clearAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}
