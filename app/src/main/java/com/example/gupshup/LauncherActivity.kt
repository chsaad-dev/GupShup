package com.example.gupshup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.ui.auth.LoginActivity
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            // ✅ Already logged in → Go to Main App with Bottom Navigation
            startActivity(Intent(this, MainNavigationActivity::class.java))
        } else {
            // 🚫 Not logged in → Go to Login screen
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }
}
