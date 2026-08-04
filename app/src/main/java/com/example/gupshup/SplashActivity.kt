package com.example.gupshup

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.ui.auth.LoginActivity
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val appName = findViewById<View>(R.id.splashAppName)
        val tagline = findViewById<View>(R.id.splashTagline)
        val progress = findViewById<View>(R.id.splashProgress)

        // Initial state for logo
        logo.scaleX = 0f
        logo.scaleY = 0f
        logo.alpha = 0f

        // Logo entrance: scale + fade in with overshoot
        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.5f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.5f)
        }
        val logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 400
        }

        // App name fade + slide up
        val nameAlpha = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 400
        }
        val nameTransY = ObjectAnimator.ofFloat(appName, "translationY", 30f, 0f).apply {
            duration = 500
            startDelay = 400
            interpolator = DecelerateInterpolator()
        }

        // Tagline fade
        val taglineAlpha = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f).apply {
            duration = 400
            startDelay = 700
        }

        // Progress fade
        val progressAlpha = ObjectAnimator.ofFloat(progress, "alpha", 0f, 1f).apply {
            duration = 300
            startDelay = 900
        }

        AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha, nameAlpha, nameTransY, taglineAlpha, progressAlpha)
            start()
        }

        // Navigate after animation
        lifecycleScope.launch {
            delay(1800)
            val auth = FirebaseAuth.getInstance()
            val destination = if (auth.currentUser != null) {
                MainNavigationActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, destination))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
