package com.example.gupshup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.gupshup.service.GupShupMessagingService
import com.example.gupshup.util.CloudinaryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class GupShupApp : Application() {

    override fun onCreate() {
        super.onCreate()

        CloudinaryManager.init(this)

        // Configure Firestore for offline persistence with a larger cache
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Create notification channels for Android 8.0+ (API 26+)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Messages channel — high importance with sound and vibration
            val messagesChannel = NotificationChannel(
                GupShupMessagingService.CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New chat message notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
            }

            // Friend Requests channel — default importance
            val friendRequestsChannel = NotificationChannel(
                GupShupMessagingService.CHANNEL_FRIEND_REQUESTS,
                "Friend Requests",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Friend request notifications"
            }

            // Status Updates channel — low importance (no sound)
            val statusChannel = NotificationChannel(
                GupShupMessagingService.CHANNEL_STATUS,
                "Status Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status story update notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(messagesChannel, friendRequestsChannel, statusChannel)
            )
        }
    }
}
