package com.example.gupshup.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.databinding.BottomSheetEmojiPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EmojiPickerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEmojiPickerBinding? = null
    private val binding get() = _binding!!

    var onEmojiSelectedListener: ((String) -> Unit)? = null

    private val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "👍", "👎", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✌️", "🤞",
        "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆", "👇", "☝️",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "🔥", "✨", "🌟", "💫", "⭐", "🎉", "🎊", "💬", "💭", "💯"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEmojiPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.emojiRecyclerView.layoutManager = GridLayoutManager(requireContext(), 6)
        binding.emojiRecyclerView.adapter = EmojiAdapter(emojis) { selectedEmoji ->
            onEmojiSelectedListener?.invoke(selectedEmoji)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class EmojiAdapter(
        private val list: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

        inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val emojiText: TextView = itemView.findViewById(R.id.emojiTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji, parent, false)
            return EmojiViewHolder(view)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            val item = list[position]
            holder.emojiText.text = item
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
