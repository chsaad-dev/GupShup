package com.example.gupshup.ui.chat

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.gupshup.R
import com.example.gupshup.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatusViewActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var userNameTextView: TextView
    private lateinit var timestampTextView: TextView
    private lateinit var viewersButton: Button
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var statusId: String? = null
    private var statusUserName: String? = null
    private var statusText: String? = null
    private var statusTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_view)

        statusTextView = findViewById(R.id.statusTextView)
        userNameTextView = findViewById(R.id.userNameTextView)
        timestampTextView = findViewById(R.id.timestampTextView)
        viewersButton = findViewById(R.id.viewersButton)
        progressBar = findViewById(R.id.progressBar)

        statusId = intent.getStringExtra("STATUS_ID")
        if (statusId == null) {
            finish()
            return
        }

        statusUserName = intent.getStringExtra("STATUS_USER_NAME")
        statusText = intent.getStringExtra("STATUS_TEXT")
        statusTimestamp = intent.getLongExtra("STATUS_TIMESTAMP", 0L)


        statusTextView.text = statusText ?: ""
        userNameTextView.text = statusUserName ?: ""
        timestampTextView.text = formatTimestamp(statusTimestamp)


        addViewRecord()

        // Show viewers only if the status belongs to current user
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == statusId) {
            viewersButton.visibility = View.VISIBLE
            viewersButton.setOnClickListener {
                showViewersDialog(statusId!!)
            }
        } else {
            viewersButton.visibility = View.GONE
        }
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a, dd MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    private fun addViewRecord() {
        val currentUserId = auth.currentUser?.uid ?: return
        val viewDocRef = db.collection("status")
            .document(statusId!!)
            .collection("views")
            .document(currentUserId)

        viewDocRef.set(mapOf("viewedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                Toast.makeText(this, "Status Marked as viewed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to mark view", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showViewersDialog(statusId: String) {
        progressBar.visibility = View.VISIBLE
        db.collection("status").document(statusId).collection("views")
            .get()
            .addOnSuccessListener { viewsSnapshot ->
                val viewerIds = viewsSnapshot.documents.map { it.id }
                if (viewerIds.isEmpty()) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "No viewers yet", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), viewerIds)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        progressBar.visibility = View.GONE
                        val users = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                        showUsersListDialog(users)
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to fetch viewers", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to fetch viewers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUsersListDialog(users: List<User>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Viewed by")

        if (users.isEmpty()) {
            builder.setMessage("No viewers yet")
            builder.setPositiveButton("OK", null)
        } else {
            val userNames = users.map { it.name ?: "Anonymous" }.toTypedArray()
            builder.setItems(userNames, null)
            builder.setPositiveButton("OK", null)
        }

        builder.show()
    }
}
