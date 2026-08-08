package com.example.gupshup.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemChatPreviewBinding
import com.example.gupshup.model.ChatPreview
import com.example.gupshup.ui.chat.ChatActivity

class ChatPreviewAdapter(
    private val context: Context,
    private val previews: List<ChatPreview>
) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(val binding: ItemChatPreviewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val view = ItemChatPreviewBinding.inflate(LayoutInflater.from(context), parent, false)
        return PreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val item = previews[position]
        holder.binding.userName.text = item.user.name
        holder.binding.lastMessage.text = item.lastMessage

        holder.itemView.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("receiverId", item.user.uid)
            context.startActivity(intent)
            com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(context)
        }
    }

    override fun getItemCount(): Int = previews.size
}
