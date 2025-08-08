package com.example.gupshup.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.CommentAdapter
import com.example.gupshup.databinding.ActivityStatusStoryBinding
import com.example.gupshup.model.Comment
import com.example.gupshup.model.Status
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StatusStoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusStoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = ArrayList<Comment>()
    private var status: Status? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusStoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get values from intent
        val statusId = intent.getStringExtra("STATUS_ID")
        val userId = intent.getStringExtra("STATUS_USER_ID")
        val userName = intent.getStringExtra("STATUS_USER_NAME")
        val text = intent.getStringExtra("STATUS_TEXT")
        val timestamp = intent.getLongExtra("STATUS_TIMESTAMP", 0L)

        // Validate
        if (statusId == null || userId == null) {
            Toast.makeText(this, "Status not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Build status object
        status = Status(
            statusId = statusId,
            userId = userId,
            userName = userName ?: "Unknown",
            userProfileUrl = "",
            text = text ?: "",
            timestamp = timestamp
        )

        setupUI()
        setupRecyclerView()
        loadComments()

        binding.sendCommentButton.setOnClickListener {
            sendComment()
        }

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.deleteStatusButton.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun setupUI() {
        binding.statusTextView.text = status?.text ?: ""
        binding.userNameTextView.text = status?.userName ?: "Unknown"
        binding.timestampTextView.text = formatTime(status?.timestamp ?: 0L)

        // Show delete button only if this is the current user's status
        if (status?.userId == auth.currentUser?.uid) {
            binding.deleteStatusButton.visibility = android.view.View.VISIBLE
        } else {
            binding.deleteStatusButton.visibility = android.view.View.GONE
        }
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(commentList)
        binding.commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.commentsRecyclerView.adapter = commentAdapter
    }

    private fun loadComments() {
        val statusId = status?.statusId ?: return

        db.collection("status")
            .document(statusId)
            .collection("comments")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                commentList.clear()
                snapshot?.documents?.forEach { doc ->
                    val comment = doc.toObject(Comment::class.java)
                    if (comment != null) {
                        db.collection("users").document(comment.userId)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                comment.userName = userDoc.getString("name") ?: "Unknown"
                                commentList.add(comment)
                                commentAdapter.notifyDataSetChanged()
                            }
                    }
                }
            }
    }

    private fun sendComment() {
        val commentText = binding.commentEditText.text.toString().trim()
        if (commentText.isEmpty()) {
            Toast.makeText(this, "Write something to comment", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val userName = userDoc.getString("name") ?: "Anonymous"

                val comment = Comment(
                    commentId = UUID.randomUUID().toString(),
                    userId = currentUser.uid,
                    userName = userName,
                    text = commentText,
                    timestamp = System.currentTimeMillis()
                )

                db.collection("status")
                    .document(status!!.statusId)
                    .collection("comments")
                    .document(comment.commentId)
                    .set(comment)
                    .addOnSuccessListener {
                        binding.commentEditText.setText("")
                        Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to send comment", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch user info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(com.example.gupshup.R.layout.dialog_delete_status, null)
        val dialog = AlertDialog.Builder(this).create()
        dialog.setView(dialogView)
        dialog.setCancelable(true)

        val cancelBtn = dialogView.findViewById<Button>(com.example.gupshup.R.id.cancelButton)
        val confirmBtn = dialogView.findViewById<Button>(com.example.gupshup.R.id.confirmButton)

        cancelBtn.setOnClickListener { dialog.dismiss() }

        confirmBtn.setOnClickListener {
            val statusId = status?.statusId ?: return@setOnClickListener
            db.collection("status").document(statusId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Status deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete status", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun formatTime(time: Long): String {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
