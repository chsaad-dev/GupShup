package com.example.gupshup.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Helper for requesting POST_NOTIFICATIONS permission on Android 13+ (API 33+).
 *
 * Usage in an Activity:
 * ```
 * private val notifPermHelper = NotificationPermissionHelper(this)
 * override fun onCreate(...) {
 *     notifPermHelper.requestIfNeeded()
 * }
 * ```
 */
class NotificationPermissionHelper(activity: AppCompatActivity) {

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* User granted or denied — FCM still delivers silently if denied */ }

    private val context = activity

    /**
     * Requests POST_NOTIFICATIONS permission if the device is running Android 13+
     * and the permission has not yet been granted.
     */
    fun requestIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
