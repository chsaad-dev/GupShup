package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemCommentBinding
import com.example.gupshup.model.Comment
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(private val comments: List<Comment>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding =
            ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun getItemCount(): Int = comments.size

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.binding.commentText.text = comment.text
        holder.binding.commentUser.text = comment.userName
        holder.binding.commentTime.text = formatTime(comment.timestamp)
    }

    private fun formatTime(time: Long): String {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
