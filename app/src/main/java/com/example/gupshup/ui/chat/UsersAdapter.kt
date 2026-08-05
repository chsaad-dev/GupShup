package com.example.gupshup.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gupshup.R
import com.example.gupshup.databinding.ItemUserBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.chat.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UsersAdapter(
    private val context: Context,
    private val userList: List<User>,
    private val unreadCountMap: Map<String, Int> = emptyMap(),
    private val lastMessageMap: Map<String, String> = emptyMap(),
    private val lastMessageTimeMap: Map<String, Long> = emptyMap(),
    private val onUserClick: ((User) -> Unit)? = null,
    private val showAddButton: Boolean = false,
    private val showRequestButtons: Boolean = false,
    private val onAcceptClick: ((User) -> Unit)? = null,
    private val onRejectClick: ((User) -> Unit)? = null
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userName.text = user.name

            val lastMsg = lastMessageMap[user.uid]
            val timeMs = lastMessageTimeMap[user.uid] ?: 0L

            val displaySubtitle = when {
                !lastMsg.isNullOrBlank() -> lastMsg
                showAddButton || showRequestButtons -> user.email
                !user.bio.isNullOrBlank() -> user.bio
                else -> "No messages yet"
            }
            binding.userEmail.text = displaySubtitle

            val formattedTime = formatTime(timeMs)
            if (formattedTime.isNotEmpty()) {
                binding.lastMessageTime.text = formattedTime
                binding.lastMessageTime.visibility = View.VISIBLE
            } else {
                binding.lastMessageTime.visibility = View.GONE
            }

            android.util.Log.d("UsersAdapter", "Loading avatar for user ${user.name}, url: '${user.effectiveProfileImageUrl}', privacyPhoto: ${user.privacyPhoto}")
            val canSeePhoto = (user.privacyPhoto != "Nobody")
            val avatarUrl = if (canSeePhoto) user.effectiveProfileImageUrl else null
            com.example.gupshup.util.ImageLoaderUtil.loadAvatar(binding.userImage, avatarUrl, user.updatedAt)

            binding.onlineIndicator.visibility = if (user.isOnline) View.VISIBLE else View.GONE

            val unreadCount = unreadCountMap[user.uid] ?: 0
            if (unreadCount > 0) {
                binding.unreadCountText.text = unreadCount.toString()
                binding.unreadCountText.visibility = View.VISIBLE
                binding.userEmail.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                binding.unreadCountText.visibility = View.GONE
                binding.userEmail.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            binding.addFriendBtn.visibility = View.GONE
            binding.acceptButton.visibility = View.GONE
            binding.rejectButton.visibility = View.GONE

            when {
                showAddButton -> {
                    binding.addFriendBtn.visibility = View.VISIBLE
                    binding.addFriendBtn.setOnClickListener {
                        onUserClick?.invoke(user)
                    }
                    binding.root.setOnClickListener(null)
                }

                showRequestButtons -> {
                    binding.acceptButton.visibility = View.VISIBLE
                    binding.rejectButton.visibility = View.VISIBLE
                    binding.acceptButton.setOnClickListener {
                        onAcceptClick?.invoke(user)
                    }
                    binding.rejectButton.setOnClickListener {
                        onRejectClick?.invoke(user)
                    }
                    binding.root.setOnClickListener(null)
                }

                onUserClick != null -> {
                    binding.root.setOnClickListener {
                        onUserClick.invoke(user)
                    }
                }

                else -> {
                    binding.root.setOnClickListener {
                        checkIfFriends(user)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    private fun checkIfFriends(user: User) {
        if (currentUid == null) return

        val ref = db.collection("friend_requests")

        ref.whereEqualTo("fromUid", currentUid)
            .whereEqualTo("toUid", user.uid)
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { sent ->
                ref.whereEqualTo("fromUid", user.uid)
                    .whereEqualTo("toUid", currentUid)
                    .whereEqualTo("status", "accepted")
                    .get()
                    .addOnSuccessListener { received ->
                        if (!sent.isEmpty || !received.isEmpty) {
                            val intent = Intent(context, ChatActivity::class.java)
                            intent.putExtra("receiverId", user.uid)
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Not friends yet!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
    }

    private fun formatTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
        val nowCal = java.util.Calendar.getInstance()

        return if (msgCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            msgCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)) {
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(timestampMs)
        } else if (msgCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            nowCal.get(java.util.Calendar.DAY_OF_YEAR) - msgCal.get(java.util.Calendar.DAY_OF_YEAR) == 1) {
            "Yesterday"
        } else {
            java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault()).format(timestampMs)
        }
    }
}