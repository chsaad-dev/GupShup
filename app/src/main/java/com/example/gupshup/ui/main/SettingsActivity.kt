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
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    Toast.makeText(this, "Account deletion requested", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
