package com.example.gupshup.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.model.Status
import com.example.gupshup.ui.chat.StatusStoryActivity
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class StatusAdapter(
    private var statusList: List<Status>,
    private val onStatusClick: (Status) -> Unit,
    private val onDeleteClick: (Status) -> Unit
) : RecyclerView.Adapter<StatusAdapter.StatusViewHolder>() {

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusUserName: TextView = itemView.findViewById(R.id.statusUserName)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val statusTimestamp: TextView = itemView.findViewById(R.id.statusTimestamp)
        val deleteIcon: ImageView = itemView.findViewById(R.id.deleteStatusIcon)
        val viewIcon: ImageView = itemView.findViewById(R.id.viewStatusIcon)
        val viewCount: TextView = itemView.findViewById(R.id.viewStatusCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val status = statusList[position]

        holder.statusUserName.text = status.userName
        holder.statusText.text = status.text

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val isOwner = status.userId == currentUserId
        holder.deleteIcon.visibility = if (isOwner) View.VISIBLE else View.GONE

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("status")
            .document(status.statusId)
            .collection("views")
            .count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                holder.viewCount.text = snapshot.count.toString()
            }

        val openViewersAction = View.OnClickListener {
            if (isOwner) {
                val context = holder.itemView.context
                if (context is androidx.fragment.app.FragmentActivity) {
                    val sheet = com.example.gupshup.ui.main.StatusViewersBottomSheetFragment.newInstance(status.statusId)
                    sheet.show(context.supportFragmentManager, "StatusViewersBottomSheet")
                }
            }
        }

        holder.viewIcon.setOnClickListener(openViewersAction)
        holder.viewCount.setOnClickListener(openViewersAction)

        holder.itemView.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                val context = holder.itemView.context
                val intent = Intent(context, StatusStoryActivity::class.java)
                intent.putExtra("STATUS_DATA", status)
                context.startActivity(intent)
                com.example.gupshup.util.ActivityTransitionUtil.applyFadeTransition(context)
                onStatusClick(status)
            }
        }

        holder.deleteIcon.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                onDeleteClick(status)
            }
        }
    }

    override fun getItemCount(): Int = statusList.size

    fun updateList(newList: List<Status>) {
        statusList = newList
        notifyDataSetChanged()
    }
}
