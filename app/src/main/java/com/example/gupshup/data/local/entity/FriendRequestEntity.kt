package com.example.gupshup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey val id: String,
    val fromUid: String,
    val toUid: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val cachedAt: Long = System.currentTimeMillis()
)
