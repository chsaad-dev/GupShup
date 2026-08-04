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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val statusList = ArrayList<Status>()

    private lateinit var bubbleAdapter: StatusBubbleAdapter
    private lateinit var verticalAdapter: StatusAdapter
    private var statusListener: ListenerRegistration? = null

    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadPhotoStatus(uri)
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
        fetchStatuses()

        binding.postStatusButton.setOnClickListener {
            postOrUpdateStatus(null, "text")
        }

        binding.addPhotoButton.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
    }

    private fun setupToolbar() {
        binding.statusToolbar.title = "Status"
        binding.statusToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupAdapters() {
        // Horizontal Bubble Adapter
        bubbleAdapter = StatusBubbleAdapter(statusList) { status ->
            openStatusStory(status)
        }
        binding.statusBubbleRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusBubbleRecyclerView.adapter = bubbleAdapter

        // Vertical Status List Adapter
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
    }

    private fun uploadPhotoStatus(uri: Uri) {
        Toast.makeText(context, "Uploading photo status...", Toast.LENGTH_SHORT).show()
        CloudinaryManager.uploadImage(
            context = requireContext(),
            imageUri = uri,
            folder = "gupshup/status_media",
            onSuccess = { mediaUrl ->
                postOrUpdateStatus(mediaUrl, "image")
            },
            onError = { errorMsg ->
                if (_binding != null && isAdded) {
                    Toast.makeText(context, "Failed to upload photo status: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun postOrUpdateStatus(mediaUrl: String?, type: String) {
        val text = binding.statusEditText.text.toString().trim()
        if (text.isEmpty() && mediaUrl.isNullOrBlank()) {
            Toast.makeText(context, "Please enter status text or pick a photo", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser ?: return
        val statusId = currentUser.uid

        db.collection("users").document(statusId).get().addOnSuccessListener { document ->
            val userName = document.getString("name") ?: "Anonymous"
            val userProfileUrl = document.getString("profileImageUrl") ?: ""

            val status = Status(
                statusId = statusId,
                userId = statusId,
                userName = userName,
                userProfileUrl = userProfileUrl,
                text = text,
                mediaUrl = mediaUrl ?: "",
                type = type,
                timestamp = System.currentTimeMillis()
            )

            db.collection("status").document(statusId)
                .set(status)
                .addOnSuccessListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(context, "Status posted!", Toast.LENGTH_SHORT).show()
                        binding.statusEditText.setText("")
                    }
                }
                .addOnFailureListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(context, "Failed to post status", Toast.LENGTH_SHORT).show()
                    }
                }
        }.addOnFailureListener {
            if (_binding != null && isAdded) {
                Toast.makeText(context, "User info fetch failed", Toast.LENGTH_SHORT).show()
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
                snapshot?.documents?.forEach { doc ->
                    val status = doc.toObject(Status::class.java)
                    status?.let { statusList.add(it) }
                }
                statusList.sortByDescending { it.timestamp }
                bubbleAdapter.notifyDataSetChanged()
                verticalAdapter.updateList(statusList)

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
