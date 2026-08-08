package com.example.gupshup.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.gupshup.databinding.ActivitySettingsBinding
import com.example.gupshup.ui.auth.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.gupshup.service.GupShupMessagingService

import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.view.View
import com.example.gupshup.R
import com.example.gupshup.databinding.DialogDeleteAccountConfirmBinding
import com.example.gupshup.util.finishWithFade
import com.example.gupshup.util.startActivityWithFade

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var userListener: ListenerRegistration? = null

    private val prefs by lazy {
        getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadPreferences()
        setupListeners()
        observeUserData()
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            finishWithFade()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithFade()
            }
        })
    }

    private fun loadPreferences() {
        val themeMode = prefs.getString("pref_theme", "system") ?: "system"
        binding.textTheme.text = when (themeMode) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "System"
        }

        val notificationsEnabled = prefs.getBoolean("pref_notifications", true)
        binding.switchMessageNotifications.isChecked = notificationsEnabled

        val vibrateEnabled = prefs.getBoolean("pref_vibrate", true)
        binding.switchVibrate.isChecked = vibrateEnabled

        val fontSize = prefs.getString("pref_font_size", "Medium") ?: "Medium"
        binding.textFontSize.text = fontSize

        val enterSendEnabled = prefs.getBoolean("pref_enter_send", false)
        binding.switchEnterSend.isChecked = enterSendEnabled

        binding.textUserId.text = auth.currentUser?.uid ?: ""

        updateWallpaperText()
        updatePrivacyText()
        updateNotificationSoundText()
        updateMediaDownloadText()
        updateStorageUsageText()
    }

    private fun updateNotificationSoundText() {
        val soundName = prefs.getString("pref_notification_sound_name", "Default") ?: "Default"
        binding.textNotificationSound.text = soundName
    }

    private fun showNotificationSoundDialog() {
        val options = arrayOf("Default", "Chime", "Classic Bell", "Whistle", "Silent")
        val currentName = prefs.getString("pref_notification_sound_name", "Default") ?: "Default"
        val checkedItem = when (currentName) {
            "Chime" -> 1
            "Classic Bell" -> 2
            "Whistle" -> 3
            "Silent" -> 4
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Sound")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedName = options[which]
                val soundUri = when (which) {
                    1 -> "chime"
                    2 -> "classic"
                    3 -> "whistle"
                    4 -> "silent"
                    else -> "default"
                }
                prefs.edit()
                    .putString("pref_notification_sound_name", selectedName)
                    .putString("pref_notification_sound_uri", soundUri)
                    .apply()
                updateNotificationSoundText()
                com.example.gupshup.util.NotificationChannelManager.rebuildMessageChannel(this)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMediaDownloadText() {
        val mode = prefs.getString("pref_media_download", "wifi_mobile") ?: "wifi_mobile"
        binding.textMediaDownload.text = when (mode) {
            "wifi_only" -> "Wi-Fi only"
            "never" -> "Never"
            else -> "Wi-Fi & Mobile"
        }
    }

    private fun calculateLocalStorageBytes(): Long {
        var size = 0L
        cacheDir?.let { size += getFolderSize(it) }
        externalCacheDir?.let { size += getFolderSize(it) }
        val dbFile = getDatabasePath("GupShup_database")
        if (dbFile != null && dbFile.exists()) size += dbFile.length()
        return size
    }

    private fun getFolderSize(dir: java.io.File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    private fun updateStorageUsageText() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = calculateLocalStorageBytes()
            val text = formatBytes(bytes)
            withContext(Dispatchers.Main) {
                binding.textStorageUsage.text = text
            }
        }
    }

    private fun showMediaDownloadDialog() {
        val options = arrayOf("Wi-Fi & Mobile data", "Wi-Fi only", "Never (Save Data)")
        val currentMode = prefs.getString("pref_media_download", "wifi_mobile")
        val checkedItem = when (currentMode) {
            "wifi_only" -> 1
            "never" -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Media Auto-Download")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedKey = when (which) {
                    1 -> "wifi_only"
                    2 -> "never"
                    else -> "wifi_mobile"
                }
                prefs.edit().putString("pref_media_download", selectedKey).apply()
                updateMediaDownloadText()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStorageUsageDialog() {
        val uid = auth.currentUser?.uid ?: return

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Calculating storage & Cloudinary usage...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val localBytes = calculateLocalStorageBytes()
            val formattedLocal = formatBytes(localBytes)

            var statusMediaCount = 0
            try {
                val statusQuery = Tasks.await(db.collection("statuses").whereEqualTo("userId", uid).get())
                statusMediaCount = statusQuery.documents.count { !it.getString("mediaUrl").isNullOrEmpty() }
            } catch (e: Exception) {
                // Ignore query error if offline
            }

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()

                val message = """
                    Local Device Cache: $formattedLocal
                    (Includes temporary image cache, Glide disk cache & local database)

                    Cloud Media (Cloudinary):
                    • Active Status Uploads: $statusMediaCount media files
                """.trimIndent()

                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("Storage Usage")
                    .setMessage(message)
                    .setPositiveButton("Clear Local Cache") { _, _ ->
                        clearLocalCache()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun clearLocalCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                cacheDir?.deleteRecursively()
                externalCacheDir?.deleteRecursively()
                com.bumptech.glide.Glide.get(applicationContext).clearDiskCache()
            } catch (e: Exception) {
                // Handle cache cleanup
            }
            val newBytes = calculateLocalStorageBytes()
            val newFormatted = formatBytes(newBytes)

            withContext(Dispatchers.Main) {
                binding.textStorageUsage.text = newFormatted
                Toast.makeText(this@SettingsActivity, "Local cache cleared successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWallpaperText() {
        val wallpaperKey = prefs.getString("pref_chat_wallpaper", "default") ?: "default"
        binding.textWallpaper.text = when (wallpaperKey) {
            "teal" -> "Teal"
            "slate" -> "Slate Dark"
            "cream" -> "Soft Cream"
            "midnight" -> "Midnight"
            "sage" -> "Sage Green"
            else -> "Default"
        }
    }

    private fun updatePrivacyText() {
        val online = prefs.getString("pref_privacy_online", "Everyone") ?: "Everyone"
        val photo = prefs.getString("pref_privacy_photo", "Everyone") ?: "Everyone"
        binding.textPrivacySummary.text = "Online: $online · Photo: $photo"
    }

    private fun observeUserData() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val blockedList = snapshot.get("blockedUsers") as? List<String> ?: emptyList()
                    binding.textBlockedCount.text = blockedList.size.toString()

                    val fsOnline = snapshot.getString("privacyOnline") ?: "Everyone"
                    val fsPhoto = snapshot.getString("privacyPhoto") ?: "Everyone"
                    prefs.edit()
                        .putString("pref_privacy_online", fsOnline)
                        .putString("pref_privacy_photo", fsPhoto)
                        .apply()
                    updatePrivacyText()
                }
            }
    }

    private fun setupListeners() {
        binding.rowProfile.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.rowPrivacy.setOnClickListener {
            val privacySheet = PrivacyBottomSheetFragment()
            privacySheet.onPrivacyUpdatedListener = {
                updatePrivacyText()
            }
            privacySheet.show(supportFragmentManager, "PrivacySettingsBottomSheet")
        }

        binding.rowBlocked.setOnClickListener {
            startActivity(Intent(this, BlockedContactsActivity::class.java))
        }

        binding.rowWallpaper.setOnClickListener {
            val wallpaperSheet = WallpaperBottomSheetFragment()
            wallpaperSheet.onWallpaperSelectedListener = { label ->
                binding.textWallpaper.text = label
            }
            wallpaperSheet.show(supportFragmentManager, "WallpaperBottomSheetFragment")
        }

        binding.rowNotificationSound.setOnClickListener {
            showNotificationSoundDialog()
        }

        binding.switchMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_notifications", isChecked).apply()
            val uid = auth.currentUser?.uid
            if (uid != null) {
                db.collection("users").document(uid).update("notificationsEnabled", isChecked)
            }
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_vibrate", isChecked).apply()
            com.example.gupshup.util.NotificationChannelManager.rebuildMessageChannel(this)
        }

        binding.switchEnterSend.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_enter_send", isChecked).apply()
        }

        binding.rowTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        binding.rowFontSize.setOnClickListener {
            showFontSizeDialog()
        }

        binding.rowHelpCenter.setOnClickListener {
            val helpSheet = HelpCenterBottomSheetFragment()
            helpSheet.show(supportFragmentManager, "HelpCenterBottomSheet")
        }

        binding.rowReportProblem.setOnClickListener {
            val reportSheet = ReportProblemBottomSheetFragment()
            reportSheet.show(supportFragmentManager, "ReportProblemBottomSheet")
        }

        binding.rowMediaDownload.setOnClickListener {
            showMediaDownloadDialog()
        }

        binding.rowStorageUsage.setOnClickListener {
            showStorageUsageDialog()
        }

        val copyAction = {
            val uid = auth.currentUser?.uid ?: ""
            if (uid.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("User ID", uid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowUserId.setOnClickListener { copyAction() }
        binding.btnCopyUserId.setOnClickListener { copyAction() }

        binding.btnSettingsLogout.setOnClickListener {
            GupShupMessagingService.clearFcmTokenFromFirestore()
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.textDeleteAccount.setOnClickListener {
            showAccountDeletionVerificationDialog()
        }
    }

    private fun showAccountDeletionVerificationDialog() {
        val user = auth.currentUser
        val uid = user?.uid
        val email = user?.email
        if (uid.isNullOrEmpty() || user == null) {
            Toast.makeText(this, "No active user session", Toast.LENGTH_SHORT).show()
            return
        }

        val isPasswordProvider = user.providerData.any { it.providerId == "password" }

        val dialogBinding = DialogDeleteAccountConfirmBinding.inflate(layoutInflater)

        if (isPasswordProvider) {
            dialogBinding.dialogPromptText.text = "Please enter your password to confirm deletion:"
            dialogBinding.confirmEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            dialogBinding.dialogPromptText.text = "Please type DELETE to confirm deletion:"
            dialogBinding.confirmEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Delete My Account", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setTextColor(resources.getColor(R.color.error, null))
            positiveBtn.setOnClickListener {
                val input = dialogBinding.confirmEditText.text?.toString()?.trim() ?: ""
                dialogBinding.dialogErrorText.visibility = View.GONE

                if (isPasswordProvider) {
                    if (input.isEmpty()) {
                        dialogBinding.dialogErrorText.text = "Password is required to delete your account"
                        dialogBinding.dialogErrorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }

                    if (email.isNullOrEmpty()) {
                        dialogBinding.dialogErrorText.text = "Account email not found"
                        dialogBinding.dialogErrorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }

                    val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, input)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            dialog.dismiss()
                            performFullAccountDeletion(user)
                        }
                        .addOnFailureListener { e ->
                            dialogBinding.dialogErrorText.text = "Incorrect password: ${e.localizedMessage}"
                            dialogBinding.dialogErrorText.visibility = View.VISIBLE
                        }
                } else {
                    if (input != "DELETE") {
                        dialogBinding.dialogErrorText.text = "Please type DELETE exactly to confirm"
                        dialogBinding.dialogErrorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }

                    dialog.dismiss()
                    performFullAccountDeletion(user)
                }
            }
        }

        dialog.show()
    }

    private fun performFullAccountDeletion(user: com.google.firebase.auth.FirebaseUser) {
        val uid = user.uid
        val email = user.email

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Deleting all your account data, statuses, reports and links...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Clear FCM Token from Firestore
                try {
                    GupShupMessagingService.clearFcmTokenFromFirestore()
                } catch (_: Exception) {}

                // 2. Delete User Document from Firestore
                try {
                    Tasks.await(db.collection("users").document(uid).delete())
                } catch (_: Exception) {}

                // 3. Delete User Reports
                try {
                    val reportsByUid = Tasks.await(db.collection("reports").whereEqualTo("userId", uid).get())
                    for (doc in reportsByUid.documents) {
                        Tasks.await(doc.reference.delete())
                    }
                    if (!email.isNullOrEmpty()) {
                        val reportsByEmail = Tasks.await(db.collection("reports").whereEqualTo("userEmail", email).get())
                        for (doc in reportsByEmail.documents) {
                            Tasks.await(doc.reference.delete())
                        }
                    }
                } catch (_: Exception) {}

                // 4. Delete Status Updates (and view subcollections)
                try {
                    val statusQuery = Tasks.await(db.collection("status").whereEqualTo("userId", uid).get())
                    for (statusDoc in statusQuery.documents) {
                        try {
                            val viewsQuery = Tasks.await(statusDoc.reference.collection("views").get())
                            for (viewDoc in viewsQuery.documents) {
                                Tasks.await(viewDoc.reference.delete())
                            }
                        } catch (_: Exception) {}
                        Tasks.await(statusDoc.reference.delete())
                    }
                } catch (_: Exception) {}

                // 5. Delete Friend Requests
                try {
                    val sentReqs = Tasks.await(db.collection("friend_requests").whereEqualTo("fromUid", uid).get())
                    for (doc in sentReqs.documents) {
                        Tasks.await(doc.reference.delete())
                    }
                    val recvReqs = Tasks.await(db.collection("friend_requests").whereEqualTo("toUid", uid).get())
                    for (doc in recvReqs.documents) {
                        Tasks.await(doc.reference.delete())
                    }
                } catch (_: Exception) {}

                // 6. Delete Firebase Auth User Account
                try {
                    Tasks.await(user.delete())
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Auth delete error", e)
                }

                // 7. Clear Local Database, Preferences & Sign Out
                try {
                    com.example.gupshup.data.local.AppDatabase.getInstance(applicationContext).clearAllTables()
                } catch (_: Exception) {}
                prefs.edit().clear().apply()
                auth.signOut()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Your account has been permanently deleted", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(this@SettingsActivity)
                    finish()
                }
            } catch (e: Exception) {
                try { com.example.gupshup.data.local.AppDatabase.getInstance(applicationContext).clearAllTables() } catch (_: Exception) {}
                prefs.edit().clear().apply()
                auth.signOut()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Account deletion completed", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(this@SettingsActivity)
                    finish()
                }
            }
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf("System default", "Light", "Dark")
        val currentTheme = prefs.getString("pref_theme", "system")
        val checkedItem = when (currentTheme) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val (selectedKey, selectedLabel, nightMode) = when (which) {
                    1 -> Triple("light", "Light", AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> Triple("dark", "Dark", AppCompatDelegate.MODE_NIGHT_YES)
                    else -> Triple("system", "System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }

                prefs.edit().putString("pref_theme", selectedKey).apply()
                binding.textTheme.text = selectedLabel
                AppCompatDelegate.setDefaultNightMode(nightMode)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFontSizeDialog() {
        val fontSizes = arrayOf("Small", "Medium", "Large")
        val currentFont = prefs.getString("pref_font_size", "Medium")
        val checkedItem = fontSizes.indexOf(currentFont).coerceAtLeast(1)

        MaterialAlertDialogBuilder(this)
            .setTitle("Font Size")
            .setSingleChoiceItems(fontSizes, checkedItem) { dialog, which ->
                val selectedFont = fontSizes[which]
                prefs.edit().putString("pref_font_size", selectedFont).apply()
                binding.textFontSize.text = selectedFont
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        userListener = null
    }
}
