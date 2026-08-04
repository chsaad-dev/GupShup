package com.example.gupshup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val userName: String = "",
    val userProfileUrl: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val type: String = "text",
    val timestamp: Long = 0L,
    val expiresAt: Long = 0L
)
