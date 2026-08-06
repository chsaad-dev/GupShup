package com.example.gupshup.model

import java.io.Serializable

data class Status(
    val statusId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileUrl: String = "",
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaPublicId: String? = null,
    val type: String = "text", // "text" or "image"
    val timestamp: Long = 0L,
    val expiresAt: Long = 0L
) : Serializable
