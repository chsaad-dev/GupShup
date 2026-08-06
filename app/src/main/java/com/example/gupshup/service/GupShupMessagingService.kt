package com.example.gupshup.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.example.gupshup.R
import com.example.gupshup.ui.chat.ChatActivity
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service that handles:
 * - FCM token lifecycle (onNewToken -> write to Firestore)
 * - Foreground notification display (onMessageReceived)
 * - Deep-linking tap behavior for messages (ChatActivity) and friend requests (Friends tab)
 */
class GupShupMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "GupShupFCM"

        // Notification channel IDs (must match channels created in GupShupApp)
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_FRIEND_REQUESTS = "friend_requests"
        const val CHANNEL_STATUS = "status"

        const val FRIEND_REQUEST_NOTIFICATION_ID = 8888

        /**
         * Writes the given FCM token to users/{uid}.fcmToken in Firestore.
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

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveFcmTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        val prefs = getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("pref_notifications", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled by user preference, skipping display")
            return
        }

        val data = message.data
        val type = data["type"] ?: "message"
        val title = data["title"] ?: message.notification?.title ?: "GupShup"
        val body = data["body"] ?: message.notification?.body ?: ""
        val senderId = data["senderId"] ?: ""
        val chatId = data["chatId"]

        // Suppress foreground notification if the user currently has this exact chat open
        if (type == "message" && chatId != null && chatId == com.example.gupshup.data.local.CacheConfig.activeChatId) {
            Log.d(TAG, "Active chat $chatId is open in foreground, suppressing notification")
            return
        }

        val channelId = when (type) {
            "friend_request", "friend_request_accepted" -> CHANNEL_FRIEND_REQUESTS
            "status" -> CHANNEL_STATUS
            else -> CHANNEL_MESSAGES
        }

        // Deep-linking PendingIntent & deterministic notification ID setup
        val (pendingIntent, notificationId) = when (type) {
            "message" -> {
                val notifId = chatId?.hashCode() ?: senderId.hashCode()
                val chatIntent = Intent(this, ChatActivity::class.java).apply {
                    action = "com.example.gupshup.ACTION_CHAT_${chatId ?: senderId}"
                    setData(android.net.Uri.parse("gupshup://chat/${chatId ?: senderId}"))
                    putExtra("receiverId", senderId)
                    if (chatId != null) putExtra("chatId", chatId)
                }
                val pi = TaskStackBuilder.create(this).run {
                    addNextIntentWithParentStack(Intent(this@GupShupMessagingService, MainNavigationActivity::class.java))
                    addNextIntent(chatIntent)
                    getPendingIntent(
                        notifId,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                Pair(pi, notifId)
            }
            "friend_request", "friend_request_accepted" -> {
                val notifId = FRIEND_REQUEST_NOTIFICATION_ID
                val friendsIntent = Intent(this, MainNavigationActivity::class.java).apply {
                    action = "com.example.gupshup.ACTION_FRIENDS"
                    setData(android.net.Uri.parse("gupshup://friends"))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("target_tab", "friends")
                }
                val pi = PendingIntent.getActivity(
                    this,
                    notifId,
                    friendsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                Pair(pi, notifId)
            }
            else -> {
                val notifId = System.currentTimeMillis().toInt()
                val mainIntent = Intent(this, MainNavigationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pi = PendingIntent.getActivity(
                    this,
                    notifId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                Pair(pi, notifId)
            }
        }

        // Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_send)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                when (type) {
                    "friend_request", "friend_request_accepted" -> NotificationCompat.PRIORITY_DEFAULT
                    "status" -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
