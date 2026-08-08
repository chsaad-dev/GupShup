package com.example.gupshup.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivityStatusStoryBinding
import com.example.gupshup.model.Status
import com.example.gupshup.ui.main.StatusViewersBottomSheetFragment
import com.example.gupshup.util.ImageLoaderUtil
import com.example.gupshup.util.finishWithFade
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date

class StatusStoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusStoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var status: Status? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusStoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passedStatus = if (BuildUtils.isAtLeastTiramisu()) {
            intent.getSerializableExtra("STATUS_DATA", Status::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("STATUS_DATA") as? Status
        }

        val statusId = passedStatus?.statusId ?: intent.getStringExtra("STATUS_ID")
        val userId = passedStatus?.userId ?: intent.getStringExtra("STATUS_USER_ID")
        val userName = passedStatus?.userName ?: intent.getStringExtra("STATUS_USER_NAME")
        val text = passedStatus?.text ?: intent.getStringExtra("STATUS_TEXT")
        val mediaUrl = passedStatus?.mediaUrl ?: intent.getStringExtra("STATUS_MEDIA_URL")
        val type = passedStatus?.type ?: intent.getStringExtra("STATUS_TYPE") ?: "text"
        val timestamp = if (passedStatus != null && passedStatus.timestamp > 0L) passedStatus.timestamp else intent.getLongExtra("STATUS_TIMESTAMP", System.currentTimeMillis())

        if (statusId.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Toast.makeText(this, "Status not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        status = Status(
            statusId = statusId,
            userId = userId,
            userName = userName ?: "Unknown",
            userProfileUrl = passedStatus?.userProfileUrl ?: "",
            text = text,
            mediaUrl = mediaUrl,
            type = type,
            timestamp = timestamp
        )

        setupUI()
        setupSegmentedProgressBar(userId, statusId)
        recordStatusView(statusId, userId)

        binding.backButton.setOnClickListener {
            finishWithFade()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithFade()
            }
        })

        binding.deleteStatusButton.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun setupUI() {
        val currentStatus = status ?: return

        // 1. Media & Text / Caption rendering
        val hasMedia = !currentStatus.mediaUrl.isNullOrBlank()
        if (hasMedia) {
            binding.statusImageView.visibility = View.VISIBLE
            binding.statusTextView.visibility = View.GONE
            ImageLoaderUtil.loadStatusMedia(binding.statusImageView, currentStatus.mediaUrl)

            if (!currentStatus.text.isNullOrBlank()) {
                binding.statusCaptionTextView.visibility = View.VISIBLE
                binding.statusCaptionTextView.text = currentStatus.text
            } else {
                binding.statusCaptionTextView.visibility = View.GONE
            }
        } else {
            binding.statusImageView.visibility = View.GONE
            binding.statusCaptionTextView.visibility = View.GONE
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = currentStatus.text ?: ""
        }

        // 2. User Info & Avatar
        binding.userNameTextView.text = currentStatus.userName
        binding.timestampTextView.text = formatRelativeTime(currentStatus.timestamp)

        loadUserAvatar(currentStatus.userId, currentStatus.userProfileUrl)

        // 3. Owner Controls (Delete & Viewer Count)
        val currentUid = auth.currentUser?.uid
        val isOwner = (currentUid != null && currentUid == currentStatus.userId)

        if (isOwner) {
            binding.deleteStatusButton.visibility = View.VISIBLE
            binding.viewersCountContainer.visibility = View.VISIBLE
            fetchViewersCount(currentStatus.statusId)

            binding.viewersCountContainer.setOnClickListener {
                val sheet = StatusViewersBottomSheetFragment.newInstance(currentStatus.statusId)
                sheet.show(supportFragmentManager, "StatusViewersBottomSheet")
            }
        } else {
            binding.deleteStatusButton.visibility = View.GONE
            binding.viewersCountContainer.visibility = View.GONE
        }
    }

    private fun loadUserAvatar(userId: String, fallbackUrl: String) {
        if (fallbackUrl.isNotEmpty()) {
            ImageLoaderUtil.loadAvatar(binding.headerAvatar, fallbackUrl)
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val avatarUrl = doc.getString("profileImageUrl")
                        ?: doc.getString("photoUrl")
                        ?: doc.getString("photoUri")
                        ?: fallbackUrl
                    val updatedAt = doc.getLong("updatedAt") ?: 0L
                    ImageLoaderUtil.loadAvatar(binding.headerAvatar, avatarUrl, updatedAt)
                }
            }
    }

    private fun fetchViewersCount(statusId: String) {
        db.collection("status")
            .document(statusId)
            .collection("views")
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                binding.viewersCountText.text = snapshot.count.toString()
            }
    }

    private fun recordStatusView(statusId: String, ownerUserId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        if (currentUid != ownerUserId) {
            val viewData = mapOf("viewedAt" to System.currentTimeMillis())
            db.collection("status")
                .document(statusId)
                .collection("views")
                .document(currentUid)
                .set(viewData)
        }
    }

    private fun setupSegmentedProgressBar(userId: String, currentStatusId: String) {
        val now = System.currentTimeMillis()
        db.collection("status")
            .whereEqualTo("userId", userId)
            .whereGreaterThan("expiresAt", now)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) return@addOnSuccessListener

                val statuses = snapshot.documents.mapNotNull { it.toObject(Status::class.java) }
                    .sortedBy { it.timestamp }

                binding.segmentedProgressBar.removeAllViews()
                val total = statuses.size

                for (i in 0 until total) {
                    val segment = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            if (i < total - 1) marginEnd = 6
                        }
                        background = getDrawable(R.drawable.bg_rounded_gray)
                        val isPastOrCurrent = statuses[i].statusId == currentStatusId ||
                                statuses.indexOfFirst { it.statusId == currentStatusId } >= i
                        alpha = if (isPastOrCurrent) 1.0f else 0.4f
                    }
                    binding.segmentedProgressBar.addView(segment)
                }
            }
    }

    private fun showDeleteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_status, null)
        val dialog = AlertDialog.Builder(this).create()
        dialog.setView(dialogView)
        dialog.setCancelable(true)

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmBtn = dialogView.findViewById<Button>(R.id.confirmButton)

        cancelBtn.setOnClickListener { dialog.dismiss() }

        confirmBtn.setOnClickListener {
            val statusId = status?.statusId ?: return@setOnClickListener
            db.collection("status").document(statusId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Status deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    finishWithFade()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete status", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (60 * 1000)
        val hours = diff / (60 * 60 * 1000)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(Date(timestamp))
        }
    }
}

object BuildUtils {
    fun isAtLeastTiramisu(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    }
}
