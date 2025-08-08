package com.example.gupshup.model

import com.google.firebase.Timestamp

data class User(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var profileImageUrl: String? = null,
    var bio: String? = null,
    var isOnline: Boolean = false,
    var lastSeen: Timestamp? = null,
    var typingTo: String? = null,
    val isEmailVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val fcmToken: String = ""
)