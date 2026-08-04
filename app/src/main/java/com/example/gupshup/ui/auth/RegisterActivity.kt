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
import com.example.gupshup.databinding.ActivityRegisterBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.main.MainNavigationActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
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


        auth.currentUser?.let { user ->
            if (user.isEmailVerified) {
                startActivity(Intent(this, MainNavigationActivity::class.java))
                finish()
                return
            }
        }

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupUI()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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
                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
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

    private fun registerUser() {
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()


        clearErrors()


        if (!validateInputs(name, email, password)) return

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {

                    user.sendEmailVerification()
                        .addOnSuccessListener {

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
        binding.googleSignInButton.isEnabled = !show
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