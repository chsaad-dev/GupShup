package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gupshup.R
import com.example.gupshup.model.Status
import de.hdodenhof.circleimageview.CircleImageView

class StatusBubbleAdapter(
    private val statusList: List<Status>,
    private val onStatusClick: (Status) -> Unit
) : RecyclerView.Adapter<StatusBubbleAdapter.StatusViewHolder>() {

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.bubbleProfileImage)
        val userName: TextView = itemView.findViewById(R.id.bubbleUserName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status_bubble, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val status = statusList[position]
        holder.userName.text = status.userName

        val profileUrl = status.userProfileUrl
        if (profileUrl.isNotEmpty()) {
            com.example.gupshup.util.ImageLoaderUtil.loadAvatar(holder.profileImage, profileUrl)
        } else {
            com.example.gupshup.util.ImageLoaderUtil.loadAvatar(holder.profileImage, null)
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(status.userId)
                .get()
                .addOnSuccessListener { uDoc ->
                    if (uDoc != null && uDoc.exists()) {
                        val avatarUrl = uDoc.getString("profileImageUrl")
                            ?: uDoc.getString("photoUrl")
                            ?: uDoc.getString("photoUri")
                        val updatedAt = uDoc.getLong("updatedAt") ?: 0L
                        com.example.gupshup.util.ImageLoaderUtil.loadAvatar(holder.profileImage, avatarUrl, updatedAt)
                    }
                }
        }

        holder.itemView.setOnClickListener {
            onStatusClick(status)
        }
    }

    override fun getItemCount(): Int = statusList.size
}
