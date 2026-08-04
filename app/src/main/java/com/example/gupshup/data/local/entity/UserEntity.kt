package com.example.gupshup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val cachedAt: Long = System.currentTimeMillis(),
    val privacyOnline: String = "Everyone",
    val privacyLastSeen: String = "Everyone",
    val privacyPhoto: String = "Everyone"
)
