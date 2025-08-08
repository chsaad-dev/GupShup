package com.example.gupshup.model

data class Comment(
    val commentId: String = "",
    val userId: String = "",
    var userName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)
