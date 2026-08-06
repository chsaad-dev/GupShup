package com.example.gupshup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.model.StatusViewer
import com.example.gupshup.util.ImageLoaderUtil
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class StatusViewerAdapter(
    private var viewerList: List<StatusViewer>
) : RecyclerView.Adapter<StatusViewerAdapter.ViewerViewHolder>() {

    inner class ViewerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: CircleImageView = itemView.findViewById(R.id.viewerAvatar)
        val name: TextView = itemView.findViewById(R.id.viewerName)
        val time: TextView = itemView.findViewById(R.id.viewTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_status_viewer, parent, false)
        return ViewerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewerViewHolder, position: Int) {
        val viewer = viewerList[position]
        holder.name.text = viewer.name
        holder.time.text = formatRelativeTime(viewer.viewedAt)

        ImageLoaderUtil.loadAvatar(holder.avatar, viewer.avatarUrl)
    }

    override fun getItemCount(): Int = viewerList.size

    fun updateList(newList: List<StatusViewer>) {
        viewerList = newList
        notifyDataSetChanged()
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Just now"
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
