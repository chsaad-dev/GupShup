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
            if (auth.currentUser != null) {
                val extras = intent.extras
                val targetTab = intent.getStringExtra("target_tab") ?: extras?.getString("target_tab")
                val receiverId = intent.getStringExtra("receiverId")
                    ?: intent.getStringExtra("senderId")
                    ?: extras?.getString("receiverId")
                    ?: extras?.getString("senderId")
                val chatId = intent.getStringExtra("chatId") ?: extras?.getString("chatId")
                val notifType = intent.getStringExtra("type")
                    ?: intent.getStringExtra("notification_type")
                    ?: extras?.getString("type")
                    ?: extras?.getString("notification_type")

                android.util.Log.d("SplashActivity", "[COLD_START] Intent extras: targetTab=$targetTab, receiverId=$receiverId, chatId=$chatId, notifType=$notifType")

                if (!receiverId.isNullOrEmpty()) {
                    val chatIntent = Intent(this@SplashActivity, com.example.gupshup.ui.chat.ChatActivity::class.java).apply {
                        putExtra("receiverId", receiverId)
                        if (!chatId.isNullOrEmpty()) putExtra("chatId", chatId)
                    }
                    val stackBuilder = androidx.core.app.TaskStackBuilder.create(this@SplashActivity)
                    stackBuilder.addNextIntentWithParentStack(Intent(this@SplashActivity, MainNavigationActivity::class.java))
                    stackBuilder.addNextIntent(chatIntent)
                    stackBuilder.startActivities()
                } else if (targetTab == "friends" || notifType == "friend_request" || notifType == "friend_request_accepted") {
                    val mainIntent = Intent(this@SplashActivity, MainNavigationActivity::class.java).apply {
                        putExtra("target_tab", "friends")
                    }
                    startActivity(mainIntent)
                } else {
                    startActivity(Intent(this@SplashActivity, MainNavigationActivity::class.java))
                }
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}
