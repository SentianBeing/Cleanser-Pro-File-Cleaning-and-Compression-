package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanHistoryDao {
    @Query("SELECT * FROM clean_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CleanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CleanHistoryEntity): Long

    @Query("DELETE FROM clean_history")
    suspend fun clearAll()

    @Query("SELECT SUM(bytesFreed) FROM clean_history")
    fun getTotalBytesFreed(): Flow<Long?>
}
