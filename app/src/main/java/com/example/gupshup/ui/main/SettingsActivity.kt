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

import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            onBackPressedDispatcher.onBackPressed()
        }
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

        binding.switchMessageNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_notifications", isChecked).apply()
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_vibrate", isChecked).apply()
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
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.textDeleteAccount.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? All your profile data, messages, statuses, and friend links will be permanently deleted.")
                .setPositiveButton("Delete Permanently") { _, _ ->
                    deleteAccount()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        val uid = user?.uid
        if (uid.isNullOrEmpty() || user == null) {
            Toast.makeText(this, "No active user session", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Deleting account and associated data...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete User document from Firestore
                Tasks.await(db.collection("users").document(uid).delete())

                // 2. Delete User's statuses
                val statusQuery = Tasks.await(db.collection("statuses").whereEqualTo("userId", uid).get())
                for (doc in statusQuery.documents) {
                    Tasks.await(doc.reference.delete())
                }

                // 3. Delete Friend Requests
                val sentReqs = Tasks.await(db.collection("friend_requests").whereEqualTo("fromUid", uid).get())
                for (doc in sentReqs.documents) {
                    Tasks.await(doc.reference.delete())
                }
                val recvReqs = Tasks.await(db.collection("friend_requests").whereEqualTo("toUid", uid).get())
                for (doc in recvReqs.documents) {
                    Tasks.await(doc.reference.delete())
                }

                // 4. Clear Local Database & Preferences
                com.example.gupshup.data.local.AppDatabase.getInstance(applicationContext).clearAllTables()
                prefs.edit().clear().apply()

                // 5. Delete Firebase Auth User Account
                Tasks.await(user.delete())

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Please log in again to confirm account deletion for security",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                        val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Failed to delete account: ${e.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
