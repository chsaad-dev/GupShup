package com.example.gupshup.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.gupshup.R
import com.example.gupshup.databinding.FragmentProfileBinding
import com.example.gupshup.util.CloudinaryManager
import com.example.gupshup.util.ImageUtils
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var isEditMode = false
    private var selectedImageUri: Uri? = null


    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            com.example.gupshup.util.ImageLoaderUtil.loadAvatar(binding.profileImageView, uri.toString())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uid = auth.currentUser?.uid ?: return


        binding.profileToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.profileToolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_settings) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        observeUserProfile(uid)

        binding.editSaveButton.setOnClickListener {
            if (!isEditMode) {
                enableEditing(true)
            } else {
                saveUserProfile(uid)
            }
        }

        binding.avatarContainer.setOnClickListener {
            if (!isEditMode) {
                enableEditing(true)
            }
            imagePickerLauncher.launch("image/*")
        }
    }

    private var profileListener: ListenerRegistration? = null

    private fun observeRoomCache(uid: String) {
        val appDb = com.example.gupshup.data.local.AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.userDao().getUserFlow(uid).collect { userEntity ->
                if (_binding == null || !isAdded || userEntity == null) return@collect
                if (!isEditMode) {
                    binding.profileNameHeader.text = userEntity.name
                    binding.nameEditText.setText(userEntity.name)
                    binding.emailEditText.setText(userEntity.email)
                    binding.bioEditText.setText(userEntity.bio)

                    com.example.gupshup.util.ImageLoaderUtil.loadAvatar(
                        binding.profileImageView,
                        userEntity.photoUrl,
                        userEntity.updatedAt
                    )
                }
            }
        }
    }

    private fun observeUserProfile(uid: String) {
        observeRoomCache(uid)
        if (profileListener != null) return

        profileListener = db.collection("users").document(uid)
            .addSnapshotListener { doc, _ ->
                if (_binding == null || !isAdded || doc == null || !doc.exists()) return@addSnapshotListener

                val name = doc.getString("name") ?: ""
                val email = doc.getString("email") ?: ""
                val bio = doc.getString("bio") ?: ""
                val profileUrl = doc.getString("profileImageUrl") ?: ""
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                val appContext = context?.applicationContext
                if (appContext != null) {
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.gupshup.data.local.AppDatabase.getInstance(appContext).userDao().upsertOne(
                            com.example.gupshup.data.local.entity.UserEntity(
                                uid = uid,
                                name = name,
                                email = email,
                                photoUrl = profileUrl,
                                bio = bio,
                                updatedAt = updatedAt
                            )
                        )
                    }
                }

                if (!isEditMode) {
                    binding.profileNameHeader.text = name
                    binding.nameEditText.setText(name)
                    binding.emailEditText.setText(email)
                    binding.bioEditText.setText(bio)

                    com.example.gupshup.util.ImageLoaderUtil.loadAvatar(
                        binding.profileImageView,
                        profileUrl,
                        updatedAt
                    )
                }
            }
    }

    private fun saveUserProfile(uid: String) {
        val name = binding.nameEditText.text.toString().trim()
        val bio = binding.bioEditText.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.editSaveButton.isEnabled = false
        binding.editSaveButton.text = "Saving..."

        val uriToUpload = selectedImageUri
        if (uriToUpload != null) {
            Toast.makeText(requireContext(), "Uploading photo to Cloudinary...", Toast.LENGTH_SHORT).show()
            CloudinaryManager.uploadImage(
                context = requireContext(),
                imageUri = uriToUpload,
                folder = "gupshup/profiles",
                onSuccess = { uploadedUrl ->
                    updateFirestoreProfile(uid, name, bio, uploadedUrl)
                },
                onError = { errorMsg ->
                    if (isAdded) {
                        Toast.makeText(requireContext(), "❌ Photo upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                        binding.editSaveButton.isEnabled = true
                        binding.editSaveButton.text = "Save"
                    }
                }
            )
        } else {
            updateFirestoreProfile(uid, name, bio, null)
        }
    }

    private fun updateFirestoreProfile(uid: String, name: String, bio: String, profileImageUrl: String?) {
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "bio" to bio,
            "updatedAt" to now
        )

        profileImageUrl?.let {
            updates["profileImageUrl"] = it
        }

        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "✅ Profile updated", Toast.LENGTH_SHORT).show()
                    selectedImageUri = null
                    binding.editSaveButton.isEnabled = true
                    enableEditing(false)
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "❌ Profile update failed", Toast.LENGTH_SHORT).show()
                    binding.editSaveButton.isEnabled = true
                    binding.editSaveButton.text = "Save"
                }
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
        profileListener?.remove()
        profileListener = null
        _binding = null
    }
}
