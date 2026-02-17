package com.example.gupshup.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gupshup.R
import com.example.gupshup.adapter.ChatAdapter
import com.example.gupshup.databinding.ActivityChatBinding
import com.example.gupshup.model.Message
import com.example.gupshup.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
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
    private var newMessagesListener: ListenerRegistration? = null

    // Pagination variables
    private var isLoading = false
    private val PAGE_SIZE = 50L
    private var oldestMessageSnapshot: DocumentSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUid = auth.currentUser?.uid ?: return
        receiverId = intent.getStringExtra("receiverId") ?: return

        chatId = if (currentUid < receiverId) "${currentUid}${receiverId}" else "${receiverId}${currentUid}"

        setupToolbar()
        setupRecyclerView()
        
        // Initial Data Load
        loadInitialMessages()
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
        val layoutManager = LinearLayoutManager(this)
        // stackFromEnd = true usually keeps the list at the bottom, but standard is simpler for pagination logic
        // Let's stick to standard and scrollToPosition
        
        binding.recyclerView.layoutManager = layoutManager
        adapter = ChatAdapter(messages, currentUid) { message ->
            showReactionDialog(message)
        }
        binding.recyclerView.adapter = adapter

        // Pagination Scroll Listener
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // If scrolled to top (dy < 0 means scrolling up) and not loading
                if (dy < 0 && !isLoading) {
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisibleItemPosition == 0 && oldestMessageSnapshot != null) {
                        loadMoreMessages()
                    }
                }
            }
        })
    }

    private fun loadInitialMessages() {
        isLoading = true
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                messages.clear()
                if (!snapshot.isEmpty) {
                    // Track oldest for pagination
                    oldestMessageSnapshot = snapshot.documents.firstOrNull()
                    
                    // Track newest for realtime listener, if needed. 
                    // Actually we can just start listener from the last doc's timestamp.
                    val newestDoc = snapshot.documents.lastOrNull()
                    
                    for (doc in snapshot.documents) {
                        val msg = doc.toObject(Message::class.java)
                        if (msg != null) {
                            msg.id = doc.id
                            messages.add(msg)
                            
                            // Mark as seen if receiver
                            if (msg.receiverId == currentUid && !msg.seen) {
                                markAsSeen(msg.id!!)
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                    
                    // Start listening for NEW messages
                    listenForNewMessages(newestDoc)
                } else {
                    // Chat is empty, just listen for new
                    listenForNewMessages(null)
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                isLoading = false
                // Handle error
            }
    }

    private fun loadMoreMessages() {
        if (oldestMessageSnapshot == null) return
        isLoading = true
        
        // Save current scroll state
        // We want to keep the current top item visible after insertion
        
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .endBefore(oldestMessageSnapshot!!)
            .limitToLast(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val newMessages = mutableListOf<Message>()
                    oldestMessageSnapshot = snapshot.documents.firstOrNull()
                    
                    for (doc in snapshot.documents) {
                        val msg = doc.toObject(Message::class.java)
                        if (msg != null) {
                            msg.id = doc.id
                            newMessages.add(msg)
                        }
                    }
                    
                    // Add all to TOP
                    messages.addAll(0, newMessages)
                    adapter.notifyItemRangeInserted(0, newMessages.size)
                } else {
                    // No more older messages
                    oldestMessageSnapshot = null
                }
                isLoading = false
            }
            .addOnFailureListener { 
                isLoading = false 
            }
    }

    private fun listenForNewMessages(startAfterDoc: DocumentSnapshot?) {
        var query = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            
        if (startAfterDoc != null) {
            query = query.startAfter(startAfterDoc)
        }
            
        newMessagesListener = query.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
            
            // Only add completely NEW documents that are unexpected
            // When we first attach, it *might* fire if we are not careful, 
            // but startAfter ensures we only get *subsequent* messages.
            
            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED) {
                    val msg = change.document.toObject(Message::class.java)
                    if (msg != null) {
                        msg.id = change.document.id
                        
                        // Avoid duplicates if any race condition
                        if (messages.none { it.id == msg.id }) {
                            messages.add(msg)
                            adapter.notifyItemInserted(messages.size - 1)
                            binding.recyclerView.smoothScrollToPosition(messages.size - 1)
                            
                            if (msg.receiverId == currentUid && !msg.seen) {
                                markAsSeen(msg.id!!)
                            }
                        }
                    }
                } else if (change.type == DocumentChange.Type.MODIFIED) {
                    // Handle reactions / seen updates
                    val msg = change.document.toObject(Message::class.java)
                    if (msg != null) {
                        msg.id = change.document.id
                        val index = messages.indexOfFirst { it.id == msg.id }
                        if (index != -1) {
                            messages[index] = msg
                            adapter.notifyItemChanged(index)
                        }
                    }
                }
            }
        }
    }
    
    private fun markAsSeen(messageId: String) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .update("seen", true)
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
        newMessagesListener?.remove()
        typingTimer?.cancel()
    }
}