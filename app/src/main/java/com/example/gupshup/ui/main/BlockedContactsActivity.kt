package com.example.gupshup.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivityBlockedContactsBinding
import com.example.gupshup.model.User
import com.example.gupshup.util.ImageLoaderUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class BlockedContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedContactsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val blockedUsersList = mutableListOf<User>()
    private lateinit var adapter: BlockedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadBlockedUsers()
    }

    private fun setupToolbar() {
        binding.blockedToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = BlockedAdapter(blockedUsersList) { userToUnblock ->
            unblockUser(userToUnblock)
        }
        binding.blockedRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blockedRecyclerView.adapter = adapter
    }

    private fun loadBlockedUsers() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val blockedIds = doc.get("blockedUsers") as? List<String> ?: emptyList()

                    if (blockedIds.isEmpty()) {
                        showEmptyState(true)
                        return@addOnSuccessListener
                    }

                    db.collection("users")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), blockedIds.take(10))
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            blockedUsersList.clear()
                            for (userDoc in querySnapshot.documents) {
                                val user = userDoc.toObject(User::class.java)
                                if (user != null) {
                                    blockedUsersList.add(user)
                                }
                            }
                            adapter.notifyDataSetChanged()
                            showEmptyState(blockedUsersList.isEmpty())
                        }
                        .addOnFailureListener {
                            showEmptyState(true)
                        }
                } else {
                    showEmptyState(true)
                }
            }
            .addOnFailureListener {
                showEmptyState(true)
            }
    }

    private fun unblockUser(user: User) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .update("blockedUsers", FieldValue.arrayRemove(user.uid))
            .addOnSuccessListener {
                Toast.makeText(this, "${user.name} unblocked", Toast.LENGTH_SHORT).show()
                blockedUsersList.remove(user)
                adapter.notifyDataSetChanged()
                showEmptyState(blockedUsersList.isEmpty())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to unblock user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.blockedRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private class BlockedAdapter(
        private val users: List<User>,
        private val onUnblockClick: (User) -> Unit
    ) : RecyclerView.Adapter<BlockedAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: ShapeableImageView = v.findViewById(R.id.userAvatar)
            val name: TextView = v.findViewById(R.id.userName)
            val email: TextView = v.findViewById(R.id.userEmail)
            val btnUnblock: MaterialButton = v.findViewById(R.id.btnUnblock)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_user, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.name.text = user.name
            holder.email.text = user.email

            ImageLoaderUtil.loadAvatar(holder.avatar, user.profileImageUrl, user.updatedAt)

            holder.btnUnblock.setOnClickListener {
                onUnblockClick(user)
            }
        }

        override fun getItemCount(): Int = users.size
    }
}
