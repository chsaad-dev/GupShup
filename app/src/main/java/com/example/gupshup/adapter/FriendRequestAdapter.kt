package com.example.gupshup.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemFriendRequestBinding
import com.example.gupshup.model.User

class FriendRequestAdapter(
    private val context: Context,
    private val users: List<User>,
    private val onAccept: (User) -> Unit,
    private val onReject: (User) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val user = users[position]
        val binding = holder.binding

        binding.nameTextView.text = user.name
        binding.emailTextView.text = user.email

        binding.acceptButton.setOnClickListener { onAccept(user) }
        binding.rejectButton.setOnClickListener { onReject(user) }
    }
}
