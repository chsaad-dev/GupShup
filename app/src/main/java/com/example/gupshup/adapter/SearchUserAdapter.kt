package com.example.gupshup.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemUserSearchBinding
import com.example.gupshup.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SearchUserAdapter(
    private val context: Context,
    private val users: List<User>,
    private val onAddFriendClick: (User) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(context), parent, false)
        return UserViewHolder(binding)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        val binding = holder.binding

        binding.nameTextView.text = user.name
        binding.emailTextView.text = user.email

        checkIfAlreadyRequested(user.uid) { alreadyRequested ->
            if (alreadyRequested) {
                binding.addFriendButton.isEnabled = false
                binding.addFriendButton.text = "Requested"
            } else {
                binding.addFriendButton.isEnabled = true
                binding.addFriendButton.text = "Add Friend"
                binding.addFriendButton.setOnClickListener {
                    onAddFriendClick(user)
                    binding.addFriendButton.isEnabled = false
                    binding.addFriendButton.text = "Requested"
                }
            }
        }
    }

    private fun checkIfAlreadyRequested(toUid: String, callback: (Boolean) -> Unit) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return callback(false)
        FirebaseFirestore.getInstance().collection("friend_requests")
            .whereEqualTo("fromUid", currentUid)
            .whereEqualTo("toUid", toUid)
            .get()
            .addOnSuccessListener { snapshot ->
                callback(!snapshot.isEmpty)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}
