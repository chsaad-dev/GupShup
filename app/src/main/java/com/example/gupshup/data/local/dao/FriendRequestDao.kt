package com.example.gupshup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gupshup.data.local.entity.FriendRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE (fromUid = :uid OR toUid = :uid) AND status = 'accepted'")
    fun getAcceptedFlow(uid: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE toUid = :uid AND status = 'pending'")
    fun getPendingIncomingFlow(uid: String): Flow<List<FriendRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(requests: List<FriendRequestEntity>)

    @Query("DELETE FROM friend_requests WHERE id = :id")
    suspend fun deleteById(id: String)
}
