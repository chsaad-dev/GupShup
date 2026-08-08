package com.example.gupshup.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.R
import com.example.gupshup.data.local.AppDatabase
import com.example.gupshup.data.local.entity.UserEntity
import com.example.gupshup.databinding.FragmentProfileBinding
import com.example.gupshup.model.Status
import com.example.gupshup.ui.chat.StatusStoryActivity
import com.example.gupshup.util.CloudinaryManager
import com.example.gupshup.util.ImageLoaderUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var isEditingName = false
    private var isEditingBio = false

    private var currentUserName = ""
    private var currentUserBio = ""
    private var currentUserProfileUrl = ""
    private var currentUserCreatedAt: Long = 0L
    private var friendsCount: Long = 0L

    private var activeUserStatus: Status? = null
    private var profileListener: ListenerRegistration? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadNewProfileImage(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uid = auth.currentUser?.uid ?: return

        setupToolbar()
        setupClickListeners(uid)
        observeUserProfile(uid)
        fetchFriendsCount(uid)
        checkActiveStatus(uid)
    }

    private fun setupToolbar() {
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
    }

    private fun setupClickListeners(uid: String) {
        // Avatar Click: View active status story if present, or pick new photo if absent
        binding.avatarContainer.setOnClickListener {
            val status = activeUserStatus
            if (status != null) {
                val intent = Intent(requireContext(), StatusStoryActivity::class.java).apply {
                    putExtra("STATUS_DATA", status)
                    putExtra("STATUS_ID", status.statusId)
                    putExtra("STATUS_USER_NAME", status.userName)
                    putExtra("STATUS_TEXT", status.text)
                    putExtra("STATUS_MEDIA_URL", status.mediaUrl)
                    putExtra("STATUS_TYPE", status.type)
                    putExtra("STATUS_TIMESTAMP", status.timestamp)
                    putExtra("STATUS_USER_ID", status.userId)
                }
                startActivity(intent)
            } else {
                imagePickerLauncher.launch("image/*")
            }
        }

        binding.cameraBadge.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Action Chips: QR Code & Copy Link
        binding.qrCodeChip.setOnClickListener {
            val sheet = ProfileQrBottomSheetFragment.newInstance(uid, currentUserName, currentUserProfileUrl)
            sheet.show(childFragmentManager, "ProfileQrBottomSheet")
        }

        binding.copyLinkChip.setOnClickListener {
            val profileLink = "gupshup://profile/$uid"
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GupShup Profile Link", profileLink)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Inline Name Editing
        binding.editNameButton.setOnClickListener {
            if (!isEditingName) {
                startNameEditing()
            } else {
                saveName(uid)
            }
        }

        // Inline Bio Editing
        binding.editBioButton.setOnClickListener {
            if (!isEditingBio) {
                startBioEditing()
            } else {
                saveBio(uid)
            }
        }

        // Bio Emoji Picker Button
        binding.bioEmojiButton.setOnClickListener {
            val emojiPicker = EmojiPickerBottomSheetFragment()
            emojiPicker.onEmojiSelectedListener = { emoji ->
                val cursorPosition = binding.bioEditText.selectionStart
                val currentText = binding.bioEditText.text.toString()
                val updatedText = StringBuilder(currentText).insert(cursorPosition, emoji).toString()
                if (updatedText.length <= 150) {
                    binding.bioEditText.setText(updatedText)
                    binding.bioEditText.setSelection(cursorPosition + emoji.length)
                }
            }
            emojiPicker.show(childFragmentManager, "EmojiPickerBottomSheet")
        }

        // Invite Friends Row
        binding.rowInviteFriends.setOnClickListener {
            val inviteText = "Join me on GupShup! Connect with me here: gupshup://profile/$uid"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, inviteText)
            }
            startActivity(Intent.createChooser(shareIntent, "Invite Friends to GupShup"))
        }
    }

    private fun observeRoomCache(uid: String) {
        val appDb = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.userDao().getUserFlow(uid).collect { userEntity ->
                if (_binding == null || !isAdded || userEntity == null) return@collect
                if (!isEditingName && !isEditingBio) {
                    currentUserName = userEntity.name
                    currentUserBio = userEntity.bio
                    currentUserProfileUrl = userEntity.profileImageUrl

                    binding.profileNameHeader.text = userEntity.name
                    binding.nameTextView.text = userEntity.name
                    binding.nameEditText.setText(userEntity.name)

                    binding.emailTextView.text = userEntity.email

                    binding.bioTextView.text = userEntity.bio.ifEmpty { "No bio added yet" }
                    binding.bioEditText.setText(userEntity.bio)

                    ImageLoaderUtil.loadAvatar(
                        binding.profileImageView,
                        userEntity.profileImageUrl,
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

                // Backfill createdAt for existing accounts missing the field
                var createdAt = doc.getLong("createdAt") ?: 0L
                if (createdAt == 0L) {
                    val creationTime = auth.currentUser?.metadata?.creationTimestamp ?: System.currentTimeMillis()
                    createdAt = creationTime
                    db.collection("users").document(uid).update("createdAt", creationTime)
                }
                currentUserCreatedAt = createdAt

                currentUserName = name
                currentUserBio = bio
                currentUserProfileUrl = profileUrl

                val appContext = context?.applicationContext
                if (appContext != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.getInstance(appContext).userDao().upsertOne(
                            UserEntity(
                                uid = uid,
                                name = name,
                                email = email,
                                profileImageUrl = profileUrl,
                                bio = bio,
                                updatedAt = updatedAt
                            )
                        )
                    }
                }

                if (!isEditingName && !isEditingBio) {
                    binding.profileNameHeader.text = name
                    binding.nameTextView.text = name
                    binding.nameEditText.setText(name)

                    binding.emailTextView.text = email

                    binding.bioTextView.text = bio.ifEmpty { "No bio added yet" }
                    binding.bioEditText.setText(bio)

                    ImageLoaderUtil.loadAvatar(
                        binding.profileImageView,
                        profileUrl,
                        updatedAt
                    )

                    updateStatsLine(friendsCount, currentUserCreatedAt)
                }
            }
    }

    private fun fetchFriendsCount(uid: String) {
        val requestsRef = db.collection("friend_requests")
        requestsRef.whereEqualTo("fromUid", uid)
            .whereEqualTo("status", "accepted")
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { sentSnapshot ->
                requestsRef.whereEqualTo("toUid", uid)
                    .whereEqualTo("status", "accepted")
                    .count()
                    .get(AggregateSource.SERVER)
                    .addOnSuccessListener { receivedSnapshot ->
                        if (_binding != null && isAdded) {
                            friendsCount = sentSnapshot.count + receivedSnapshot.count
                            updateStatsLine(friendsCount, currentUserCreatedAt)
                        }
                    }
            }
    }

    private fun updateStatsLine(friends: Long, createdAtMs: Long) {
        if (_binding == null) return
        val dateStr = if (createdAtMs > 0L) {
            SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(createdAtMs))
        } else {
            "Recent"
        }
        binding.profileStatsText.text = "$friends friends · Member since $dateStr"
    }

    private fun checkActiveStatus(uid: String) {
        val now = System.currentTimeMillis()
        db.collection("status")
            .whereEqualTo("userId", uid)
            .whereGreaterThan("expiresAt", now)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener
                if (snapshot != null && !snapshot.isEmpty) {
                    val statusDoc = snapshot.documents.first()
                    activeUserStatus = statusDoc.toObject(Status::class.java)
                    binding.statusRingView.visibility = View.VISIBLE
                } else {
                    activeUserStatus = null
                    binding.statusRingView.visibility = View.GONE
                }
            }
    }

    private fun startNameEditing() {
        isEditingName = true
        binding.nameTextView.visibility = View.GONE
        binding.nameEditText.visibility = View.VISIBLE
        binding.nameEditText.requestFocus()
        binding.editNameButton.setImageResource(R.drawable.ic_check_single)
        binding.editNameButton.setColorFilter(resources.getColor(R.color.colorPrimary, null))
    }

    private fun saveName(uid: String) {
        val newName = binding.nameEditText.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.editNameButton.isEnabled = false
        val now = System.currentTimeMillis()

        db.collection("users").document(uid)
            .set(mapOf("name" to newName, "updatedAt" to now), SetOptions.merge())
            .addOnSuccessListener {
                if (_binding != null && isAdded) {
                    isEditingName = false
                    currentUserName = newName
                    binding.profileNameHeader.text = newName
                    binding.nameTextView.text = newName
                    binding.nameTextView.visibility = View.VISIBLE
                    binding.nameEditText.visibility = View.GONE

                    binding.editNameButton.isEnabled = true
                    binding.editNameButton.setImageResource(R.drawable.ic_edit)
                    binding.editNameButton.setColorFilter(resources.getColor(R.color.onSurfaceVariant, null))
                    Toast.makeText(requireContext(), "Name updated", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    binding.editNameButton.isEnabled = true
                    Toast.makeText(requireContext(), "Failed to update name", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startBioEditing() {
        isEditingBio = true
        binding.bioTextView.visibility = View.GONE
        binding.bioEditContainer.visibility = View.VISIBLE

        binding.bioEditText.filters = arrayOf(InputFilter.LengthFilter(150))
        updateCharCounter(binding.bioEditText.text?.length ?: 0)

        binding.bioEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCharCounter(s?.length ?: 0)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.bioEditText.requestFocus()
        binding.editBioButton.setImageResource(R.drawable.ic_check_single)
        binding.editBioButton.setColorFilter(resources.getColor(R.color.colorPrimary, null))
    }

    private fun updateCharCounter(count: Int) {
        if (_binding == null) return
        binding.bioCharCounterText.text = "$count/150"
    }

    private fun saveBio(uid: String) {
        val newBio = binding.bioEditText.text.toString().trim()
        binding.editBioButton.isEnabled = false
        val now = System.currentTimeMillis()

        db.collection("users").document(uid)
            .set(mapOf("bio" to newBio, "updatedAt" to now), SetOptions.merge())
            .addOnSuccessListener {
                if (_binding != null && isAdded) {
                    isEditingBio = false
                    currentUserBio = newBio
                    binding.bioTextView.text = newBio.ifEmpty { "No bio added yet" }
                    binding.bioTextView.visibility = View.VISIBLE
                    binding.bioEditContainer.visibility = View.GONE

                    binding.editBioButton.isEnabled = true
                    binding.editBioButton.setImageResource(R.drawable.ic_edit)
                    binding.editBioButton.setColorFilter(resources.getColor(R.color.onSurfaceVariant, null))
                    Toast.makeText(requireContext(), "Bio updated", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    binding.editBioButton.isEnabled = true
                    Toast.makeText(requireContext(), "Failed to update bio", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadNewProfileImage(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(requireContext(), "Uploading profile photo...", Toast.LENGTH_SHORT).show()

        CloudinaryManager.uploadImage(
            context = requireContext(),
            imageUri = uri,
            folder = "gupshup/profiles",
            onSuccess = { uploadedUrl, _ ->
                val now = System.currentTimeMillis()
                db.collection("users").document(uid)
                    .set(mapOf("profileImageUrl" to uploadedUrl, "updatedAt" to now), SetOptions.merge())
                    .addOnSuccessListener {
                        if (_binding != null && isAdded) {
                            currentUserProfileUrl = uploadedUrl
                            ImageLoaderUtil.loadAvatar(binding.profileImageView, uploadedUrl, now)
                            Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
                        }
                    }
            },
            onError = { errorMsg ->
                if (_binding != null && isAdded) {
                    Toast.makeText(requireContext(), "Photo upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileListener?.remove()
        profileListener = null
        _binding = null
    }
}
