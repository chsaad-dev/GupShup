package com.example.gupshup.model

data class ChatSummary(
    val chatId: String = "",
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val otherUserId: String = "",
    val otherUserName: String = "",
)
