package com.example.gupshup.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.gupshup.databinding.ActivityRegisterBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect if already logged in and verified
        auth.currentUser?.let { user ->
            if (user.isEmailVerified) {
                startActivity(Intent(this, MainNavigationActivity::class.java))
                finish()
                return
            }
        }

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.registerButton.setOnClickListener {
            registerUser()
        }

        binding.goToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        // Clear previous errors
        clearErrors()

        // Validate inputs
        if (!validateInputs(name, email, password)) return

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    // Send email verification
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            // Save user data to Firestore
                            val userData = User(
                                uid = user.uid,
                                name = name,
                                email = email,
                                isEmailVerified = false
                            )

                            db.collection("users")
                                .document(user.uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    showLoading(false)
                                    // Navigate to email verification screen
                                    val intent = Intent(this, EmailVerificationActivity::class.java)
                                    intent.putExtra("email", email)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    showLoading(false)
                                    showError("Failed to save user data: ${e.message}")
                                }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            showError("Failed to send verification email: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                handleAuthError(e)
            }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(name)) {
            binding.nameLayout.error = "Name is required"
            isValid = false
        } else if (name.length < 2) {
            binding.nameLayout.error = "Name must be at least 2 characters"
            isValid = false
        }

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
        } else if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun clearErrors() {
        binding.nameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }

    private fun showLoading(show: Boolean) {
        binding.progressIndicator.isVisible = show
        binding.registerButton.isEnabled = !show
        binding.nameInput.isEnabled = !show
        binding.emailInput.isEnabled = !show
        binding.passwordInput.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleAuthError(exception: Exception) {
        val message = when {
            exception.message?.contains("email address is already in use") == true ->
                "This email is already registered"
            exception.message?.contains("weak password") == true ->
                "Password is too weak"
            exception.message?.contains("badly formatted") == true ->
                "Please enter a valid email address"
            else -> "Registration failed: ${exception.message}"
        }
        showError(message)
    }
}