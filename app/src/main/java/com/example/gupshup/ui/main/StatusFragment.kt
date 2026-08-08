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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.StatusAdapter
import com.example.gupshup.adapter.StatusBubbleAdapter
import com.example.gupshup.databinding.FragmentStatusBinding
import com.example.gupshup.model.Status
import com.example.gupshup.ui.chat.StatusStoryActivity
import com.example.gupshup.util.CloudinaryManager
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.data.local.AppDatabase
import com.example.gupshup.data.local.entity.StatusEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val statusList = ArrayList<Status>()

    private lateinit var bubbleAdapter: StatusBubbleAdapter
    private lateinit var verticalAdapter: StatusAdapter
    private var statusListener: ListenerRegistration? = null
    private var selectedImageUri: Uri? = null

    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.imagePreviewContainer.visibility = View.VISIBLE
            binding.imagePreview.setImageURI(uri)
            updatePostButtonState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupAdapters()
        loadCachedStatuses()
        fetchStatuses()
        setupInputListeners()

        binding.postStatusButton.setOnClickListener {
            val uri = selectedImageUri
            if (uri != null) {
                uploadAndPostPhotoStatus(uri)
            } else {
                postOrUpdateStatus(mediaUrl = null, mediaPublicId = null, type = "text")
            }
        }

        binding.addPhotoButton.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        binding.removeImageButton.setOnClickListener {
            selectedImageUri = null
            binding.imagePreviewContainer.visibility = View.GONE
            binding.imagePreview.setImageDrawable(null)
            updatePostButtonState()
        }
    }

    private fun setupInputListeners() {
        updatePostButtonState()
        binding.statusEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePostButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun updatePostButtonState() {
        if (_binding == null) return
        val text = binding.statusEditText.text.toString().trim()
        val hasContent = text.isNotEmpty() || selectedImageUri != null
        binding.postStatusButton.isEnabled = hasContent
        binding.postStatusButton.alpha = if (hasContent) 1.0f else 0.5f
    }

    private fun setupToolbar() {
        binding.statusToolbar.title = "Status"
        binding.statusToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupAdapters() {

        bubbleAdapter = StatusBubbleAdapter(statusList) { status ->
            openStatusStory(status)
        }
        binding.statusBubbleRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusBubbleRecyclerView.adapter = bubbleAdapter


        verticalAdapter = StatusAdapter(
            statusList = statusList,
            onStatusClick = { status -> openStatusStory(status) },
            onDeleteClick = { status -> deleteStatus(status) }
        )
        binding.statusRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.statusRecyclerView.adapter = verticalAdapter
    }

    private fun openStatusStory(status: Status) {
        val intent = Intent(requireContext(), StatusStoryActivity::class.java)
        intent.putExtra("STATUS_DATA", status)
        intent.putExtra("STATUS_ID", status.statusId)
        intent.putExtra("STATUS_USER_NAME", status.userName)
        intent.putExtra("STATUS_TEXT", status.text)
        intent.putExtra("STATUS_MEDIA_URL", status.mediaUrl)
        intent.putExtra("STATUS_TYPE", status.type)
        intent.putExtra("STATUS_TIMESTAMP", status.timestamp)
        intent.putExtra("STATUS_USER_ID", status.userId)
        startActivity(intent)
        com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(requireContext())
    }

    private fun uploadAndPostPhotoStatus(uri: Uri) {
        Toast.makeText(context, "Uploading photo status...", Toast.LENGTH_SHORT).show()
        binding.postStatusButton.isEnabled = false
        binding.postStatusButton.alpha = 0.5f

        CloudinaryManager.uploadImage(
            context = requireContext(),
            imageUri = uri,
            folder = "gupshup/status_media",
            onSuccess = { mediaUrl, publicId ->
                postOrUpdateStatus(mediaUrl, publicId, "image")
            },
            onError = { errorMsg ->
                if (_binding != null && isAdded) {
                    Toast.makeText(context, "Failed to upload photo status: $errorMsg", Toast.LENGTH_LONG).show()
                    updatePostButtonState()
                }
            }
        )
    }

    private fun postOrUpdateStatus(mediaUrl: String?, mediaPublicId: String? = null, type: String) {
        val text = binding.statusEditText.text.toString().trim()
        if (text.isEmpty() && mediaUrl.isNullOrBlank()) {
            Toast.makeText(context, "Please enter status text or pick a photo", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser ?: return
        val currentUid = currentUser.uid

        // Generate a new unique status document ID per post
        val statusRef = db.collection("status").document()
        val newStatusId = statusRef.id
        val now = System.currentTimeMillis()
        val expiresAt = now + (24 * 60 * 60 * 1000L)

        db.collection("users").document(currentUid).get().addOnSuccessListener { document ->
            val userName = document.getString("name") ?: "Anonymous"
            val userProfileUrl = document.getString("profileImageUrl") ?: ""

            val status = Status(
                statusId = newStatusId,
                userId = currentUid,
                userName = userName,
                userProfileUrl = userProfileUrl,
                text = text.ifEmpty { null },
                mediaUrl = mediaUrl,
                mediaPublicId = mediaPublicId,
                type = type,
                timestamp = now,
                expiresAt = expiresAt
            )

            statusRef
                .set(status)
                .addOnSuccessListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(context, "Status posted!", Toast.LENGTH_SHORT).show()
                        binding.statusEditText.setText("")
                        selectedImageUri = null
                        binding.imagePreviewContainer.visibility = View.GONE
                        binding.imagePreview.setImageDrawable(null)
                        updatePostButtonState()
                    }
                }
                .addOnFailureListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(context, "Failed to post status", Toast.LENGTH_SHORT).show()
                        updatePostButtonState()
                    }
                }
        }.addOnFailureListener {
            if (_binding != null && isAdded) {
                Toast.makeText(context, "User info fetch failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCachedStatuses() {
        val now = System.currentTimeMillis()
        val appDb = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.statusDao().getActiveStatusesFlow(now).collect { cachedEntities ->
                if (_binding == null || !isAdded) return@collect
                if (cachedEntities.isNotEmpty() && statusList.isEmpty()) {
                    statusList.clear()
                    statusList.addAll(cachedEntities.map { entity ->
                        Status(
                            statusId = entity.statusId,
                            userId = entity.userId,
                            userName = entity.userName,
                            userProfileUrl = entity.userProfileUrl,
                            text = entity.text,
                            mediaUrl = entity.mediaUrl,
                            mediaPublicId = entity.mediaPublicId,
                            type = entity.type,
                            timestamp = entity.timestamp,
                            expiresAt = entity.expiresAt
                        )
                    })
                    statusList.sortByDescending { it.timestamp }
                    bubbleAdapter.notifyDataSetChanged()
                    verticalAdapter.updateList(statusList)

                    binding.statusEmptyState.visibility = View.GONE
                    binding.statusRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun fetchStatuses() {
        if (statusListener != null) return
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        statusListener = db.collection("status")
            .whereGreaterThan("timestamp", twentyFourHoursAgo)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener

                statusList.clear()
                val statusEntities = mutableListOf<StatusEntity>()
                snapshot?.documents?.forEach { doc ->
                    android.util.Log.d("StatusFragment_DEBUG", "Status doc ${doc.id}: data=${doc.data}")
                    val status = doc.toObject(Status::class.java)
                    status?.let { st ->
                        statusList.add(st)
                        if (st.userProfileUrl.isEmpty()) {
                            db.collection("users").document(st.userId).get().addOnSuccessListener { uDoc ->
                                if (_binding == null || !isAdded || uDoc == null || !uDoc.exists()) return@addOnSuccessListener
                                val freshPhotoUrl = uDoc.getString("profileImageUrl") ?: ""
                                if (freshPhotoUrl.isNotEmpty()) {
                                    val index = statusList.indexOfFirst { s -> s.statusId == st.statusId }
                                    if (index != -1) {
                                        statusList[index] = statusList[index].copy(userProfileUrl = freshPhotoUrl)
                                        bubbleAdapter.notifyItemChanged(index)
                                        verticalAdapter.updateList(statusList)
                                    }
                                }
                            }
                        }
                        statusEntities.add(
                            StatusEntity(
                                statusId = st.statusId,
                                userId = st.userId,
                                userName = st.userName,
                                userProfileUrl = st.userProfileUrl,
                                text = st.text,
                                mediaUrl = st.mediaUrl,
                                mediaPublicId = st.mediaPublicId,
                                type = st.type,
                                timestamp = st.timestamp,
                                expiresAt = if (st.expiresAt > 0) st.expiresAt else (st.timestamp + 24 * 60 * 60 * 1000L)
                            )
                        )
                    }
                }
                statusList.sortByDescending { it.timestamp }
                bubbleAdapter.notifyDataSetChanged()
                verticalAdapter.updateList(statusList)


                if (statusEntities.isNotEmpty()) {
                    val appContext = context?.applicationContext
                    if (appContext != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val appDb = AppDatabase.getInstance(appContext)
                            appDb.statusDao().deleteExpired(System.currentTimeMillis())
                            appDb.statusDao().upsert(statusEntities)
                        }
                    }
                }

                if (statusList.isEmpty()) {
                    binding.statusEmptyState.visibility = View.VISIBLE
                    binding.statusRecyclerView.visibility = View.GONE
                } else {
                    binding.statusEmptyState.visibility = View.GONE
                    binding.statusRecyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun deleteStatus(status: Status) {
        val currentUser = auth.currentUser ?: return
        if (status.userId == currentUser.uid) {
            db.collection("status").document(status.statusId)
                .delete()
                .addOnSuccessListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Status deleted", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusListener?.remove()
        _binding = null
    }
}
