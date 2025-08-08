package com.example.gupshup.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.gupshup.databinding.EmailVerificationActivityBinding
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: EmailVerificationActivityBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var resendTimer: CountDownTimer? = null
    private var canResend = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is null or already verified
        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToLogin()
            return
        }

        if (currentUser.isEmailVerified) {
            redirectToMain()
            return
        }

        binding = EmailVerificationActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        startResendTimer()
    }

    private fun setupUI() {
        val email = intent.getStringExtra("email") ?: auth.currentUser?.email ?: ""
        binding.emailText.text = email

        binding.resendButton.setOnClickListener {
            if (canResend) {
                resendVerificationEmail()
            }
        }

        binding.checkStatusButton.setOnClickListener {
            checkVerificationStatus()
        }

        binding.backToLogin.setOnClickListener {
            auth.signOut()
            redirectToLogin()
        }
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser ?: return

        showLoading(true)

        user.sendEmailVerification()
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Verification email sent!", Toast.LENGTH_SHORT).show()
                startResendTimer()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Failed to send email: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkVerificationStatus() {
        val user = auth.currentUser ?: return

        showLoading(true)

        user.reload().addOnCompleteListener { task ->
            showLoading(false)
            if (task.isSuccessful) {
                if (user.isEmailVerified) {
                    // Update user verification status in Firestore
                    updateUserVerificationStatus()
                } else {
                    Toast.makeText(
                        this,
                        "Email not verified yet. Please check your inbox and click the verification link.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserVerificationStatus() {
        val user = auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .update("isEmailVerified", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show()
                redirectToMain()
            }
            .addOnFailureListener {
                // Even if Firestore update fails, user is verified in Auth
                Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show()
                redirectToMain()
            }
    }

    private fun startResendTimer() {
        canResend = false
        binding.resendButton.isEnabled = false
        binding.resendTimerText.isVisible = true

        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.resendTimerText.text = "Resend available in ${seconds}s"
            }

            override fun onFinish() {
                canResend = true
                binding.resendButton.isEnabled = true
                binding.resendTimerText.isVisible = false
            }
        }.start()
    }

    private fun showLoading(show: Boolean) {
        binding.progressIndicator.isVisible = show
        binding.resendButton.isEnabled = !show && canResend
        binding.checkStatusButton.isEnabled = !show
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun redirectToMain() {
        startActivity(Intent(this, MainNavigationActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}