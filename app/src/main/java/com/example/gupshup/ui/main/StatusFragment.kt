package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.R
import com.example.gupshup.adapter.StatusBubbleAdapter
import com.example.gupshup.databinding.FragmentStatusBinding
import com.example.gupshup.model.Status
import com.example.gupshup.ui.chat.StatusStoryActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatusFragment : Fragment() {

    private lateinit var binding: FragmentStatusBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val statusList = ArrayList<Status>()
    private lateinit var adapter: StatusBubbleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentStatusBinding.inflate(inflater, container, false)

        setupToolbar()
        setupStatusBubbles()
        fetchStatuses()

        binding.postStatusButton.setOnClickListener {
            postOrUpdateStatus()
        }

        return binding.root
    }

    private fun setupToolbar() {
        // Set the title programmatically
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
            intent.putExtra("STATUS_TIMESTAMP", status.timestamp)
            intent.putExtra("STATUS_USER_ID", status.userId)
            startActivity(intent)
        }

        binding.statusBubbleRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusBubbleRecyclerView.adapter = adapter
    }

    private fun postOrUpdateStatus() {
        val text = binding.statusEditText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(context, "Please enter a status", Toast.LENGTH_SHORT).show()
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
                timestamp = System.currentTimeMillis()
            )

            db.collection("status").document(statusId)
                .set(status)
                .addOnSuccessListener {
                    Toast.makeText(context, "Status posted!", Toast.LENGTH_SHORT).show()
                    binding.statusEditText.setText("")
                    fetchStatuses()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to post status", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(context, "User info fetch failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchStatuses() {
        // Calculate timestamp for 24 hours ago
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

    private fun showDeleteDialog(status: Status) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_status, null)
        val dialog = AlertDialog.Builder(requireContext()).create()
        dialog.setView(dialogView)
        dialog.setCancelable(true)

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmBtn = dialogView.findViewById<Button>(R.id.confirmButton)

        cancelBtn.setOnClickListener { dialog.dismiss() }

        confirmBtn.setOnClickListener {
            db.collection("status").document(status.statusId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Status deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to delete status", Toast.LENGTH_SHORT).show()
                }
            dialog.dismiss()
        }

        dialog.show()
    }
}
