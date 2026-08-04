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

        com.example.gupshup.util.ImageUtils.loadProfileImage(holder.itemView.context, status.userProfileUrl, holder.profileImage)

        holder.itemView.setOnClickListener {
            onStatusClick(status)
        }
    }

    override fun getItemCount(): Int = statusList.size
}
