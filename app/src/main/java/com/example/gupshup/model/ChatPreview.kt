package com.example.gupshup.model

data class ChatPreview(
    val user: User = User(),
    val lastMessage: String = "",
    val timestamp: Long = 0L
)
