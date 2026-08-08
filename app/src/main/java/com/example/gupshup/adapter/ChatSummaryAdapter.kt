package com.example.gupshup.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemChatSummaryBinding
import com.example.gupshup.model.ChatSummary
import com.example.gupshup.ui.chat.ChatActivity
import java.text.SimpleDateFormat
import java.util.*

class ChatSummaryAdapter(
    private val context: Context,
    private val list: List<ChatSummary>
) : RecyclerView.Adapter<ChatSummaryAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatSummaryBinding.inflate(LayoutInflater.from(context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = list[position]
        holder.bind(chat)
    }

    override fun getItemCount(): Int = list.size

    inner class ChatViewHolder(private val binding: ItemChatSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatSummary) {
            binding.userName.text = chat.otherUserName
            binding.lastMessage.text = chat.lastMessage

            val date = Date(chat.timestamp)
            val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            binding.timeText.text = formatter.format(date)

            binding.root.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra("receiverId", chat.otherUserId)
                context.startActivity(intent)
                com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(context)
            }
        }
    }
}
