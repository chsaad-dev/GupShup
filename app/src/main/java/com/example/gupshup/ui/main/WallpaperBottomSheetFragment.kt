package com.example.gupshup.ui.main

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.databinding.DialogWallpaperSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

data class WallpaperPreset(
    val id: String,
    val name: String,
    val colorHex: String
)

class WallpaperBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogWallpaperSelectionBinding? = null
    private val binding get() = _binding!!

    var onWallpaperSelectedListener: ((String) -> Unit)? = null

    private val presets = listOf(
        WallpaperPreset("default", "Default", "#ECE5DD"),
        WallpaperPreset("teal", "Teal", "#075E54"),
        WallpaperPreset("slate", "Slate", "#1F2C34"),
        WallpaperPreset("cream", "Soft Cream", "#F0EBE3"),
        WallpaperPreset("midnight", "Midnight", "#101D25"),
        WallpaperPreset("sage", "Sage Green", "#D5E8D4")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogWallpaperSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)
        val currentPresetId = prefs.getString("pref_chat_wallpaper", "default") ?: "default"

        binding.wallpaperRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.wallpaperRecyclerView.adapter = WallpaperAdapter(presets, currentPresetId) { selected ->
            prefs.edit().putString("pref_chat_wallpaper", selected.id).apply()
            onWallpaperSelectedListener?.invoke(selected.name)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class WallpaperAdapter(
        private val items: List<WallpaperPreset>,
        private val selectedId: String,
        private val onSelect: (WallpaperPreset) -> Unit
    ) : RecyclerView.Adapter<WallpaperAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView = v.findViewById(R.id.colorCard)
            val swatch: FrameLayout = v.findViewById(R.id.colorSwatch)
            val check: ImageView = v.findViewById(R.id.checkMark)
            val name: TextView = v.findViewById(R.id.presetName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper_preset, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.swatch.setBackgroundColor(Color.parseColor(item.colorHex))

            val isSelected = (item.id == selectedId)
            holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.card.strokeColor = if (isSelected) {
                holder.itemView.context.getColor(R.color.colorPrimary)
            } else {
                holder.itemView.context.getColor(R.color.outline)
            }

            holder.itemView.setOnClickListener { onSelect(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
