package com.example.gupshup.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.R
import com.example.gupshup.adapter.StatusBubbleAdapter
import com.example.gupshup.databinding.FragmentStatusBinding
import com.example.gupshup.model.Status
import com.example.gupshup.ui.chat.StatusStoryActivity
import com.example.gupshup.util.CloudinaryManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatusFragment : Fragment() {

    private lateinit var binding: FragmentStatusBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val statusList = ArrayList<Status>()
    private lateinit var adapter: StatusBubbleAdapter

    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadPhotoStatus(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentStatusBinding.inflate(inflater, container, false)

        setupToolbar()
        setupStatusBubbles()
        fetchStatuses()

        binding.postStatusButton.setOnClickListener {
            postOrUpdateStatus(null, "text")
        }

        binding.addPhotoButton.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        return binding.root
    }

    private fun setupToolbar() {
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.statusToolbar)
        activity.supportActionBar?.title = "Status"
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.statusToolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupStatusBubbles() {
        adapter = StatusBubbleAdapter(statusList) { status ->
            val intent = Intent(requireContext(), StatusStoryActivity::class.java)
            intent.putExtra("STATUS_ID", status.statusId)
            intent.putExtra("STATUS_USER_NAME", status.userName)
            intent.putExtra("STATUS_TEXT", status.text)
            intent.putExtra("STATUS_MEDIA_URL", status.mediaUrl)
            intent.putExtra("STATUS_TYPE", status.type)
            intent.putExtra("STATUS_TIMESTAMP", status.timestamp)
            intent.putExtra("STATUS_USER_ID", status.userId)
            startActivity(intent)
        }

        binding.statusBubbleRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusBubbleRecyclerView.adapter = adapter
    }

    private fun uploadPhotoStatus(uri: Uri) {
        Toast.makeText(context, "Uploading photo status to Cloudinary...", Toast.LENGTH_SHORT).show()
        CloudinaryManager.uploadImage(
            context = requireContext(),
            imageUri = uri,
            folder = "gupshup/status_media",
            onSuccess = { mediaUrl ->
                postOrUpdateStatus(mediaUrl, "image")
            },
            onError = { errorMsg ->
                if (isAdded) {
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
                    if (isAdded) {
                        Toast.makeText(context, "Status posted!", Toast.LENGTH_SHORT).show()
                        binding.statusEditText.setText("")
                        fetchStatuses()
                    }
                }
                .addOnFailureListener {
                    if (isAdded) {
                        Toast.makeText(context, "Failed to post status", Toast.LENGTH_SHORT).show()
                    }
                }
        }.addOnFailureListener {
            if (isAdded) {
                Toast.makeText(context, "User info fetch failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchStatuses() {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        db.collection("status")
            .whereGreaterThan("timestamp", twentyFourHoursAgo)
            .addSnapshotListener { snapshot, _ ->
                statusList.clear()
                snapshot?.documents?.forEach { doc ->
                    val status = doc.toObject(Status::class.java)
                    status?.let { statusList.add(it) }
                }
                statusList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }
    }
}
