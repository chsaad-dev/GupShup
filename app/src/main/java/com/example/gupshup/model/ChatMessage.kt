package com.example.gupshup.model

import com.google.firebase.Timestamp

data class Message(
    var id: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val text: String? = null,
    val timestamp: Timestamp? = null,  // ✅ Correct type
    val seen: Boolean = false,
    val reactions: Map<String, String> = emptyMap()
)
