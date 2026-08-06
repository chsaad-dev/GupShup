package com.example.gupshup.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Manages dynamic versioned NotificationChannels on Android 8.0+ (API 26+).
 * Recreates the NotificationChannel whenever sound or vibration settings change,
 * as Android notification channels are immutable once created.
 */
object NotificationChannelManager {

    private const val TAG = "NotificationChannelMgr"
    private const val PREFS_NAME = "gupshup_prefs"
    private const val KEY_CHANNEL_VERSION = "pref_channel_version"
    private const val KEY_SOUND_NAME = "pref_notification_sound_name"
    private const val KEY_SOUND_URI = "pref_notification_sound_uri"
    private const val KEY_VIBRATE = "pref_vibrate"

    /**
     * Gets the currently active dynamic channel ID for Messages (e.g. "messages_v1")
     */
    fun getCurrentMessageChannelId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_CHANNEL_VERSION, 1)
        return "messages_v$version"
    }

    /**
     * Recreates the Messages notification channel with updated sound and vibration settings.
     * Deletes the old channel version and registers a new versioned channel (messages_v{N}).
     */
    fun rebuildMessageChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "messages"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentVersion = prefs.getInt(KEY_CHANNEL_VERSION, 1)
        val oldChannelId = "messages_v$currentVersion"
        val newVersion = currentVersion + 1
        val newChannelId = "messages_v$newVersion"

        val soundName = prefs.getString(KEY_SOUND_NAME, "Default") ?: "Default"
        val soundUriType = prefs.getString(KEY_SOUND_URI, "default") ?: "default"
        val vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, true)

        Log.d(TAG, "Rebuilding Message NotificationChannel. Old: $oldChannelId -> New: $newChannelId (Sound: $soundName, Vibrate: $vibrateEnabled)")

        // 1. Delete legacy / previous channel versions
        try {
            notificationManager.deleteNotificationChannel("messages")
            notificationManager.deleteNotificationChannel(oldChannelId)
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting old notification channel: ${e.message}")
        }

        // 2. Resolve Sound URI
        val soundUri: Uri? = when (soundUriType) {
            "silent" -> null
            "chime" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "classic" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            "whistle" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()

        // 3. Create new NotificationChannel with new versioned ID
        val newChannel = NotificationChannel(
            newChannelId,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New chat message notifications"

            if (soundUri != null) {
                setSound(soundUri, audioAttributes)
            } else {
                setSound(null, null)
            }

            if (vibrateEnabled) {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
            } else {
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
            }
        }

        // 4. Register new channel with system
        notificationManager.createNotificationChannel(newChannel)

        // 5. Update version counter in SharedPreferences
        prefs.edit().putInt(KEY_CHANNEL_VERSION, newVersion).apply()

        return newChannelId
    }

    /**
     * Initializes notification channels on app startup if not already created.
     */
    fun ensureChannelsInitialized(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val currentId = getCurrentMessageChannelId(context)

            if (notificationManager.getNotificationChannel(currentId) == null) {
                rebuildMessageChannel(context)
            }

            if (notificationManager.getNotificationChannel("friend_requests") == null) {
                val friendRequestsChannel = NotificationChannel(
                    "friend_requests",
                    "Friend Requests",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Friend request notifications"
                }
                notificationManager.createNotificationChannel(friendRequestsChannel)
            }

            if (notificationManager.getNotificationChannel("status") == null) {
                val statusChannel = NotificationChannel(
                    "status",
                    "Status Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Status story update notifications"
                }
                notificationManager.createNotificationChannel(statusChannel)
            }
        }
    }
}
