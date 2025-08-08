package com.example.gupshup.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.R
import com.example.gupshup.adapter.ChatAdapter
import com.example.gupshup.databinding.ActivityChatBinding
import com.example.gupshup.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: ChatAdapter

    private lateinit var receiverId: String
    private lateinit var currentUid: String
    private lateinit var chatId: String

    private var typingTimer: Timer? = null
    private var userStatusListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUid = auth.currentUser?.uid ?: return
        receiverId = intent.getStringExtra("receiverId") ?: return

        chatId = if (currentUid < receiverId) "${currentUid}${receiverId}" else "${receiverId}${currentUid}"

        setupToolbar()
        setupRecyclerView()
        listenForMessages()
        observeReceiverStatus()
        setupTypingWatcher()
        updateTypingStatus(false)

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.messageInput.setText("")
                updateTypingStatus(false)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.chatToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""  // We now use custom toolbar views
        binding.chatToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun observeReceiverStatus() {
        val userRef = db.collection("users").document(receiverId)
        userStatusListener = userRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val name = snapshot.getString("name") ?: "Chat"
                val isOnline = snapshot.getBoolean("isOnline") ?: false
                val typingTo = snapshot.getString("typingTo")
                val lastSeen = snapshot.getTimestamp("lastSeen")

                binding.userNameText.text = name

                binding.userStatusText.text = when {
                    typingTo == currentUid -> "Typing..."
                    isOnline -> "Online"
                    lastSeen != null -> "Last seen: ${formatTimestamp(lastSeen)}"
                    else -> ""
                }
            }
        }
    }

    private fun setupTypingWatcher() {
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateTypingStatus(true)

                typingTimer?.cancel()
                typingTimer = Timer()
                typingTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        updateTypingStatus(false)
                    }
                }, 2000)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateTypingStatus(isTyping: Boolean) {
        val updateMap = if (isTyping) {
            mapOf("typingTo" to receiverId)
        } else {
            mapOf("typingTo" to "")
        }

        db.collection("users").document(currentUid).update(updateMap)
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages, currentUid) { message ->
            showReactionDialog(message)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun sendMessage(text: String) {
        val message = Message(
            senderId = currentUid,
            receiverId = receiverId,
            text = text,
            timestamp = Timestamp.now(),
            seen = false,
            reactions = emptyMap()
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }

    private fun listenForMessages() {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages.clear()
                    val batch = db.batch()

                    for (doc in snapshot.documents) {
                        val msg = doc.toObject(Message::class.java)
                        msg?.id = doc.id
                        msg?.let {
                            messages.add(it)
                            if (it.receiverId == currentUid && !it.seen) {
                                val msgRef = db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .document(it.id!!)
                                batch.update(msgRef, "seen", true)
                            }
                        }
                    }

                    batch.commit()
                    adapter.notifyDataSetChanged()
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                }
            }
    }

    private fun showReactionDialog(message: Message) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reaction_picker, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()
        dialog.show()

        val emojis = listOf("❤", "👍", "😂", "😢", "😡", "🔥")

        emojis.forEach { emoji ->
            val textView = TextView(this)
            textView.text = emoji
            textView.textSize = 24f
            textView.setPadding(16, 8, 16, 8)
            (dialogView.findViewById<ViewGroup>(R.id.reactionContainer)).addView(textView)

            textView.setOnClickListener {
                updateMessageReaction(message, emoji)
                dialog.dismiss()
            }
        }
    }

    private fun updateMessageReaction(message: Message, emoji: String) {
        val userId = auth.currentUser?.uid ?: return
        if (message.id.isNullOrEmpty()) return

        val updatedReactions = message.reactions.toMutableMap()
        updatedReactions[userId] = emoji

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(message.id!!)
            .update("reactions", updatedReactions)
    }

    override fun onPause() {
        super.onPause()
        val userRef = db.collection("users").document(currentUid)
        userRef.update(
            mapOf(
                "isOnline" to false,
                "typingTo" to "",
                "lastSeen" to Timestamp.now()
            )
        )
    }

    override fun onResume() {
        super.onResume()
        val userRef = db.collection("users").document(currentUid)
        userRef.update("isOnline", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        userStatusListener?.remove()
        typingTimer?.cancel()
    }
}