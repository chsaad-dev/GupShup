package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gupshup.R
import com.example.gupshup.databinding.ItemMessageReceivedBinding
import com.example.gupshup.databinding.ItemMessageSentBinding
import com.example.gupshup.model.Message
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private var messages: List<Message>,
    private val currentUserId: String,
    private val onReactionClick: (Message) -> Unit,
    private val onImageClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

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
            bindSentMessage(holder, message, timeFormatted, reactionsSummary)
        } else if (holder is ReceivedViewHolder) {
            bindReceivedMessage(holder, message, timeFormatted, reactionsSummary)
        }
    }

    private fun bindSentMessage(
        holder: SentViewHolder,
        message: Message,
        timeFormatted: String,
        reactionsSummary: String
    ) {
        if (!message.imageUrl.isNullOrBlank()) {
            holder.binding.messageImage.visibility = View.VISIBLE
            com.example.gupshup.util.ImageLoaderUtil.loadChatImage(holder.binding.messageImage, message.imageUrl)

            holder.binding.messageImage.setOnClickListener {
                onImageClick?.invoke(message.imageUrl)
            }
        } else {
            holder.binding.messageImage.visibility = View.GONE
        }

        if (!message.text.isNullOrBlank()) {
            holder.binding.messageText.text = message.text
            holder.binding.messageText.visibility = View.VISIBLE
        } else {
            holder.binding.messageText.visibility = View.GONE
        }

        holder.binding.messageStatus.text = timeFormatted
        val statusIcon = if (message.seen) {
            holder.itemView.context.getDrawable(R.drawable.ic_check_double)
        } else {
            holder.itemView.context.getDrawable(R.drawable.ic_check_single)
        }
        holder.binding.messageStatus.setCompoundDrawablesWithIntrinsicBounds(null, null, statusIcon, null)
        holder.binding.messageStatus.compoundDrawablePadding = 8
        holder.binding.reactionView.text = reactionsSummary
        holder.binding.reactionView.visibility = if (reactionsSummary.isNotEmpty()) View.VISIBLE else View.GONE

        holder.itemView.setOnLongClickListener {
            onReactionClick(message)
            true
        }
    }

    private fun bindReceivedMessage(
        holder: ReceivedViewHolder,
        message: Message,
        timeFormatted: String,
        reactionsSummary: String
    ) {
        if (!message.imageUrl.isNullOrBlank()) {
            holder.binding.messageImage.visibility = View.VISIBLE
            com.example.gupshup.util.ImageLoaderUtil.loadChatImage(holder.binding.messageImage, message.imageUrl)

            holder.binding.messageImage.setOnClickListener {
                onImageClick?.invoke(message.imageUrl)
            }
        } else {
            holder.binding.messageImage.visibility = View.GONE
        }

        if (!message.text.isNullOrBlank()) {
            holder.binding.messageText.text = message.text
            holder.binding.messageText.visibility = View.VISIBLE
        } else {
            holder.binding.messageText.visibility = View.GONE
        }

        holder.binding.messageTime.text = timeFormatted
        holder.binding.reactionView.text = reactionsSummary
        holder.binding.reactionView.visibility = if (reactionsSummary.isNotEmpty()) View.VISIBLE else View.GONE

        holder.itemView.setOnLongClickListener {
            onReactionClick(message)
            true
        }
    }

    private fun buildReactionSummary(reactions: Map<String, String>): String {
        val countMap = reactions.values.groupingBy { it }.eachCount()
        return countMap.entries.joinToString(" ") { "${it.key} ${it.value}" }
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        return if (timestamp != null) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = Date(timestamp.seconds * 1000)
            sdf.format(date)
        } else {
            ""
        }
    }

    inner class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
}
