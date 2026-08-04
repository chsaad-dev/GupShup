package com.example.gupshup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gupshup.data.local.entity.StatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses WHERE timestamp > :cutoff ORDER BY timestamp DESC")
    fun getActiveStatusesFlow(cutoff: Long): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(statuses: List<StatusEntity>)

    @Query("DELETE FROM statuses WHERE expiresAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)
}
