package com.example.gupshup.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivityLoginBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            showLoading(false)
            val msg = if (e.statusCode == 10) {
                "Developer error (10): SHA-1 fingerprint missing in Firebase Console."
            } else {
                "Google sign-in failed (${e.statusCode}): ${e.message}"
            }
            showError(msg)
        }
    }

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

        setupGoogleSignIn()
        setupUI()

        // Fade-in animation for the login card
        binding.loginCard.alpha = 0f
        binding.loginCard.animate().alpha(1f).setDuration(500).setStartDelay(200).start()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupUI() {
        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.forgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.googleSignInButton.setOnClickListener {
            showLoading(true)
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    // Check if user doc exists in Firestore
                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                // First time Google sign-in: create user doc
                                val userData = User(
                                    uid = user.uid,
                                    name = user.displayName ?: "User",
                                    email = user.email ?: "",
                                    isEmailVerified = true,
                                    profileImageUrl = user.photoUrl?.toString() ?: ""
                                )
                                db.collection("users").document(user.uid).set(userData)
                                    .addOnSuccessListener {
                                        showLoading(false)
                                        navigateToMain()
                                    }
                                    .addOnFailureListener { e ->
                                        showLoading(false)
                                        showError("Failed to save user data: ${e.message}")
                                    }
                            } else {
                                showLoading(false)
                                navigateToMain()
                            }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            showError("Error checking user: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showError("Authentication failed: ${e.message}")
            }
    }

    private fun navigateToMain() {
        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainNavigationActivity::class.java))
        finish()
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
                                navigateToMain()
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
        binding.googleSignInButton.isEnabled = !show
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