package com.example.gupshup.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.gupshup.databinding.ActivityLoginBinding
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is already logged in and verified
        auth.currentUser?.let { user ->
            if (user.isEmailVerified) {
                startActivity(Intent(this, MainNavigationActivity::class.java))
                finish()
                return
            }
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.forgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun loginUser() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        // Clear previous errors
        clearErrors()

        // Validate inputs
        if (!validateInputs(email, password)) return

        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    // Reload user to get latest email verification status
                    user.reload().addOnCompleteListener { reloadTask ->
                        showLoading(false)
                        if (reloadTask.isSuccessful) {
                            if (user.isEmailVerified) {
                                // Email is verified, proceed to main app
                                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainNavigationActivity::class.java))
                                finish()
                            } else {
                                // Email not verified, go to verification screen
                                val intent = Intent(this, EmailVerificationActivity::class.java)
                                intent.putExtra("email", user.email)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            showError("Failed to check verification status")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                handleAuthError(e)
            }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(email)) {
            binding.emailLayout.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Please enter a valid email"
            isValid = false
        }

        if (TextUtils.isEmpty(password)) {
            binding.passwordLayout.error = "Password is required"
            isValid = false
        }

        return isValid
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }

    private fun showLoading(show: Boolean) {
        binding.progressIndicator.isVisible = show
        binding.loginButton.isEnabled = !show
        binding.emailInput.isEnabled = !show
        binding.passwordInput.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleAuthError(exception: Exception) {
        val message = when {
            exception.message?.contains("no user record") == true ||
                    exception.message?.contains("invalid-email") == true ||
                    exception.message?.contains("wrong-password") == true ->
                "Invalid email or password"
            exception.message?.contains("too-many-requests") == true ->
                "Too many failed attempts. Please try again later"
            exception.message?.contains("user-disabled") == true ->
                "This account has been disabled"
            else -> "Login failed: ${exception.message}"
        }
        showError(message)
    }

    private fun showForgotPasswordDialog() {
        val email = binding.emailInput.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address first", Toast.LENGTH_SHORT).show()
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Password reset email sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { e ->
                val message = when {
                    e.message?.contains("no user record") == true ->
                        "No account found with this email address"
                    else -> "Failed to send password reset email: ${e.message}"
                }
                showError(message)
            }
    }
}