package com.example.gupshup.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivitySettingsBinding
import com.example.gupshup.ui.auth.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()

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
    }

    private fun setupListeners() {

        binding.rowProfile.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        binding.rowPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy settings", Toast.LENGTH_SHORT).show()
        }


        binding.rowBlocked.setOnClickListener {
            Toast.makeText(this, "Blocked contacts", Toast.LENGTH_SHORT).show()
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
}
