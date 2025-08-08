package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemMessageReceivedBinding
import com.example.gupshup.databinding.ItemMessageSentBinding
import com.example.gupshup.model.Message
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val messages: List<Message>,
    private val currentUserId: String,
    private val onReactionClick: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_SENT = 1
    private val TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val binding = ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedViewHolder(binding)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timeFormatted = formatTimestamp(message.timestamp)

        val reactionsSummary = buildReactionSummary(message.reactions)

        if (holder is SentViewHolder) {
            holder.binding.messageText.text = message.text
            holder.binding.messageStatus.text = "$timeFormatted ${if (message.seen) "✓✓ Seen" else "✓ Sent"}"
            holder.binding.reactionView.text = reactionsSummary
            holder.binding.reactionView.visibility = if (reactionsSummary.isNotEmpty()) View.VISIBLE else View.GONE
            holder.itemView.setOnLongClickListener {
                onReactionClick(message)
                true
            }
        } else if (holder is ReceivedViewHolder) {
            holder.binding.messageText.text = message.text
            holder.binding.messageTime.text = timeFormatted
            holder.binding.reactionView.text = reactionsSummary
            holder.binding.reactionView.visibility = if (reactionsSummary.isNotEmpty()) View.VISIBLE else View.GONE
            holder.itemView.setOnLongClickListener {
                onReactionClick(message)
                true
            }
        }
    }

    private fun buildReactionSummary(reactions: Map<String, String>): String {
        val countMap = reactions.values.groupingBy { it }.eachCount()
        return countMap.entries.joinToString(" ") { "${it.key} ${it.value}" }
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        return if (timestamp != null) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = Date(timestamp.seconds * 1000) // ✅ Safe conversion
            sdf.format(date)
        } else {
            ""
        }
    }


    inner class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)

    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
}
