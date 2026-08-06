package com.example.gupshup.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gupshup.R
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service that handles:
 * - FCM token lifecycle (onNewToken -> write to Firestore)
 * - Foreground notification display (onMessageReceived)
 *
 * Background notifications are handled automatically by the system
 * when the app is not in the foreground, as long as the FCM payload
 * includes a "notification" block.
 */
class GupShupMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "GupShupFCM"

        // Notification channel IDs (must match channels created in GupShupApp)
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_FRIEND_REQUESTS = "friend_requests"
        const val CHANNEL_STATUS = "status"

        /**
         * Writes the given FCM token to users/{uid}.fcmToken in Firestore.
         * Safe to call from anywhere (login, registration, app start, token refresh).
         */
        fun saveFcmTokenToFirestore(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d(TAG, "FCM token saved to Firestore") }
                .addOnFailureListener { e -> Log.w(TAG, "Failed to save FCM token: ${e.message}") }
        }

        /**
         * Clears the FCM token from the user's Firestore document.
         * Call this on logout so notifications stop reaching a signed-out device.
         */
        fun clearFcmTokenFromFirestore() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", "")
                .addOnSuccessListener { Log.d(TAG, "FCM token cleared from Firestore") }
                .addOnFailureListener { e -> Log.w(TAG, "Failed to clear FCM token: ${e.message}") }
        }

        /**
         * Fetches the current FCM token and writes it to Firestore.
         * Should be called on login success, registration success, and app start
         * (if user is already logged in).
         */
        fun refreshAndSaveFcmToken() {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "Current FCM token: $token")
                    saveFcmTokenToFirestore(token)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to fetch FCM token: ${e.message}")
                }
        }
    }

    /**
     * Called when FCM generates a new token or refreshes the existing one.
     * Overwrites (not appends) the token in Firestore.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveFcmTokenToFirestore(token)
    }

    /**
     * Called when a message is received while the app is in the foreground.
     * Builds and shows a local notification using NotificationCompat.
     *
     * When the app is backgrounded, the system tray handles notification
     * display automatically from the "notification" payload block.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // Check if the user has notifications enabled in Settings
        val prefs = getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("pref_notifications", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled by user preference, skipping display")
            return
        }

        // Determine notification type from data payload
        val data = message.data
        val type = data["type"] ?: "message" // "message", "friend_request", "status"
        val title = data["title"] ?: message.notification?.title ?: "GupShup"
        val body = data["body"] ?: message.notification?.body ?: ""
        val senderId = data["senderId"] ?: ""
        val chatId = data["chatId"]

        // Suppress foreground notification if the user currently has this exact chat open
        if (type == "message" && chatId != null && chatId == com.example.gupshup.data.local.CacheConfig.activeChatId) {
            Log.d(TAG, "Active chat $chatId is open in foreground, suppressing notification")
            return
        }

        // Pick the appropriate notification channel
        val channelId = when (type) {
            "friend_request" -> CHANNEL_FRIEND_REQUESTS
            "status" -> CHANNEL_STATUS
            else -> CHANNEL_MESSAGES
        }

        // Build an intent that opens the app when the notification is tapped
        val intent = Intent(this, MainNavigationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
            putExtra("senderId", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_send)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                when (type) {
                    "friend_request" -> NotificationCompat.PRIORITY_DEFAULT
                    "status" -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )
            .build()

        // Show the notification
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
