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

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        com.example.gupshup.util.NotificationChannelManager.ensureChannelsInitialized(this)
    }
}
