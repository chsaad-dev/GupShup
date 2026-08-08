package com.example.gupshup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.ui.auth.LoginActivity
import com.example.gupshup.ui.main.MainNavigationActivity
import com.example.gupshup.util.ActivityTransitionUtil
import com.google.firebase.auth.FirebaseAuth

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            startActivity(Intent(this, MainNavigationActivity::class.java))
            ActivityTransitionUtil.applyFadeTransition(this)
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            ActivityTransitionUtil.applyFadeTransition(this)
        }

        finish()
    }
}
