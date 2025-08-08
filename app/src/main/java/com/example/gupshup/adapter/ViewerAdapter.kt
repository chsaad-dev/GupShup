package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.databinding.ItemViewerBinding
import com.example.gupshup.model.Viewer
import java.text.SimpleDateFormat
import java.util.*

class ViewerAdapter(private val viewers: List<Viewer>) : RecyclerView.Adapter<ViewerAdapter.ViewerViewHolder>() {

    inner class ViewerViewHolder(val binding: ItemViewerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewerViewHolder {
        val binding = ItemViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewerViewHolder, position: Int) {
        val viewer = viewers[position]
        holder.binding.userNameTextView.text = viewer.userName

        val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        holder.binding.viewedAtTextView.text = sdf.format(Date(viewer.timestamp))
    }

    override fun getItemCount(): Int = viewers.size
}
