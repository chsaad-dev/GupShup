package com.example.gupshup.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.gupshup.R
import com.example.gupshup.databinding.FragmentProfileBinding
import com.example.gupshup.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var isEditMode = false
    private var selectedBase64Image: String? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Compress image
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val byteArray = outputStream.toByteArray()
                selectedBase64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

                // Show image instantly
                Glide.with(requireContext())
                    .load(bitmap)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(binding.profileImageView)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Image selection failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uid = auth.currentUser?.uid ?: return

        // Toolbar back button
        binding.profileToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadUserProfile(uid)

        binding.editSaveButton.setOnClickListener {
            if (!isEditMode) {
                enableEditing(true)
            } else {
                saveUserProfile(uid)
            }
        }

        binding.editProfilePicBtn.setOnClickListener {
            if (!isEditMode) {
                enableEditing(true)
                Toast.makeText(requireContext(), "Edit mode enabled", Toast.LENGTH_SHORT).show()
            }
            imagePickerLauncher.launch("image/*")
        }


        binding.logoutBtn.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun loadUserProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                val email = doc.getString("email") ?: ""
                val bio = doc.getString("bio") ?: ""
                val imageBase64 = doc.getString("profileImageUrl") ?: ""

                binding.nameEditText.setText(name)
                binding.emailEditText.setText(email)
                binding.userIdEditText.setText(uid)
                binding.bioEditText.setText(bio)

                if (imageBase64.isNotBlank()) {
                    Glide.with(requireContext())
                        .load("data:image/png;base64,$imageBase64")
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .into(binding.profileImageView)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "❌ Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile(uid: String) {
        val name = binding.nameEditText.text.toString().trim()
        val bio = binding.bioEditText.text.toString().trim()

        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "bio" to bio
        )

        // If image is selected, add to Firestore
        selectedBase64Image?.let {
            updates["profileImageUrl"] = it
        }

        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ Profile updated", Toast.LENGTH_SHORT).show()
                enableEditing(false)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "❌ Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enableEditing(enable: Boolean) {
        binding.nameEditText.isEnabled = enable
        binding.bioEditText.isEnabled = enable
        isEditMode = enable
        binding.editSaveButton.text = if (enable) "Save" else "Edit"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
