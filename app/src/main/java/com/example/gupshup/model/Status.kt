package com.example.gupshup.model

import java.io.Serializable

data class Status(
    val statusId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0L
) : Serializable
