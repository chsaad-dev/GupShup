package com.example.gupshup

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.ui.auth.LoginActivity
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge window setup with light status bar icons (dark status bar content background)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val appName = findViewById<View>(R.id.splashAppName)
        val tagline = findViewById<View>(R.id.splashTagline)
        val progress = findViewById<View>(R.id.splashProgress)


        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        logo.alpha = 0f
        appName.alpha = 0f
        tagline.alpha = 0f
        progress.alpha = 0f


        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.8f, 1.0f).apply {
            duration = 500
            interpolator = OvershootInterpolator(1.2f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.8f, 1.0f).apply {
            duration = 500
            interpolator = OvershootInterpolator(1.2f)
        }
        val logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1.0f).apply {
            duration = 500
        }


        val nameAlpha = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1.0f).apply {
            duration = 400
            startDelay = 200
        }
        val taglineAlpha = ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1.0f).apply {
            duration = 400
            startDelay = 350
        }


        val progressAlpha = ObjectAnimator.ofFloat(progress, "alpha", 0f, 1.0f).apply {
            duration = 300
            startDelay = 500
        }

        AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha, nameAlpha, taglineAlpha, progressAlpha)
            start()
        }


        lifecycleScope.launch {
            delay(1800)
            val auth = FirebaseAuth.getInstance()
            val destination = if (auth.currentUser != null) {
                MainNavigationActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, destination))
            finish()
        }
    }
}
