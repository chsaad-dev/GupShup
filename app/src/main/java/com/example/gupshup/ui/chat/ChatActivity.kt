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
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
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

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.gupshup.data.local.AppDatabase
import com.example.gupshup.data.local.entity.MessageEntity
import com.example.gupshup.util.CloudinaryManager
import com.example.gupshup.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    
    // Search
    private var originalMessages = mutableListOf<Message>()
    private var isSearching = false

    // Pagination variables
    private var isLoading = false
    private val PAGE_SIZE = 50L
    private var oldestMessageSnapshot: DocumentSnapshot? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
            CloudinaryManager.uploadImage(
                context = this,
                imageUri = uri,
                folder = "gupshup/chat_media",
                onSuccess = { imageUrl ->
                    sendImageMessage(imageUrl)
                },
                onError = { errorMsg ->
                    Toast.makeText(this, "Failed to upload photo: $errorMsg", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUid = auth.currentUser?.uid ?: return
        receiverId = intent.getStringExtra("receiverId") ?: return

        chatId = if (currentUid < receiverId) "${currentUid}${receiverId}" else "${receiverId}${currentUid}"
        com.example.gupshup.data.local.CacheConfig.activeChatId = chatId

        setupToolbar()
        setupRecyclerView()
        
        // Load cached messages from Room first for instant display
        loadCachedMessages()
        
        // Initial Data Load from Firestore
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

        binding.attachButton.setOnClickListener {
            mediaPickerLauncher.launch("image/*")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterMessages(newText)
                return true
            }
        })
        
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                isSearching = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearching = false
                adapter.updateMessages(messages) // Restore original
                return true
            }
        })

        return true
    }

    private fun filterMessages(query: String?) {
        if (query.isNullOrEmpty()) {
            adapter.updateMessages(messages)
            return
        }
        val lowerCaseQuery = query.lowercase()
        val filteredList = messages.filter { 
            it.text?.lowercase()?.contains(lowerCaseQuery) == true 
        }
        adapter.updateMessages(filteredList)
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
        
        binding.recyclerView.layoutManager = layoutManager
        adapter = ChatAdapter(
            messages = messages,
            currentUserId = currentUid,
            onReactionClick = { message -> showReactionDialog(message) },
            onImageClick = { imageUrl -> showImagePreviewDialog(imageUrl) }
        )
        binding.recyclerView.adapter = adapter

        // Pagination Scroll Listener
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // If scrolled to top (dy < 0 means scrolling up) and not loading
                if (dy < 0 && !isLoading && !isSearching) {
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
        
        val query = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(PAGE_SIZE)

        // Step 1: Try loading from cache first for instant display
        query.get(Source.CACHE)
            .addOnSuccessListener { cacheSnapshot ->
                if (!cacheSnapshot.isEmpty) {
                    populateMessages(cacheSnapshot)
                }
                // Step 2: Then fetch from server to get fresh data
                query.get(Source.SERVER)
                    .addOnSuccessListener { serverSnapshot ->
                        if (!serverSnapshot.isEmpty) {
                            populateMessages(serverSnapshot)
                        } else if (cacheSnapshot.isEmpty) {
                            // Truly empty chat
                            listenForNewMessages(null)
                        }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        // Server failed but cache was shown, still ok
                        if (cacheSnapshot.isEmpty) {
                            listenForNewMessages(null)
                        }
                        isLoading = false
                    }
            }
            .addOnFailureListener {
                // No cache, go straight to server
                query.get(Source.SERVER)
                    .addOnSuccessListener { serverSnapshot ->
                        if (!serverSnapshot.isEmpty) {
                            populateMessages(serverSnapshot)
                        } else {
                            listenForNewMessages(null)
                        }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        isLoading = false
                    }
            }
    }

    private fun loadCachedMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val appDb = AppDatabase.getInstance(applicationContext)
            appDb.messageDao().getMessagesForChatFlow(chatId).collect { cachedEntities ->
                if (cachedEntities.isNotEmpty() && messages.isEmpty()) {
                    val cached = cachedEntities.map { entity ->
                        Message(
                            id = entity.id,
                            senderId = entity.senderId,
                            receiverId = entity.receiverId,
                            text = entity.text,
                            imageUrl = entity.imageUrl,
                            timestamp = if (entity.timestamp > 0) com.google.firebase.Timestamp(entity.timestamp / 1000, ((entity.timestamp % 1000) * 1000000).toInt()) else null,
                            seen = entity.seen
                        )
                    }
                    runOnUiThread {
                        messages.clear()
                        messages.addAll(cached)
                        adapter.notifyDataSetChanged()
                        binding.recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun writeMessagesToRoom(msgs: List<Message>) {
        val entities = msgs.mapNotNull { msg ->
            val id = msg.id ?: return@mapNotNull null
            MessageEntity(
                id = id,
                chatId = chatId,
                senderId = msg.senderId ?: "",
                receiverId = msg.receiverId ?: "",
                text = msg.text ?: "",
                imageUrl = msg.imageUrl ?: "",
                timestamp = msg.timestamp?.toDate()?.time ?: 0L,
                seen = msg.seen
            )
        }
        if (entities.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).messageDao().upsert(entities)
            }
        }
    }

    private fun populateMessages(snapshot: QuerySnapshot) {
        messages.clear()
        oldestMessageSnapshot = snapshot.documents.firstOrNull()
        val newestDoc = snapshot.documents.lastOrNull()

        val loadedMessages = mutableListOf<Message>()
        for (doc in snapshot.documents) {
            val msg = doc.toObject(Message::class.java)
            if (msg != null) {
                msg.id = doc.id
                messages.add(msg)
                loadedMessages.add(msg)

                if (msg.receiverId == currentUid && !msg.seen) {
                    markAsSeen(msg.id!!)
                }
            }
        }
        adapter.notifyDataSetChanged()
        binding.recyclerView.scrollToPosition(messages.size - 1)

        // Write-through to Room
        writeMessagesToRoom(loadedMessages)

        // Remove old listener before setting new one
        newMessagesListener?.remove()
        listenForNewMessages(newestDoc)
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
                            
                            // Write-through new message to Room
                            writeMessagesToRoom(listOf(msg))
                            
                            if (!isSearching) {
                                adapter.notifyItemInserted(messages.size - 1)
                                binding.recyclerView.smoothScrollToPosition(messages.size - 1)
                            }
                            
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
                            if (!isSearching) {
                                adapter.notifyItemChanged(index)
                            }
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
            type = "text",
            timestamp = Timestamp.now(),
            seen = false,
            reactions = emptyMap()
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }

    private fun sendImageMessage(imageUrl: String) {
        val message = Message(
            senderId = currentUid,
            receiverId = receiverId,
            text = "",
            imageUrl = imageUrl,
            type = "image",
            timestamp = Timestamp.now(),
            seen = false,
            reactions = emptyMap()
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }

    private fun showImagePreviewDialog(imageUrl: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_full_screen_image, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        val fullScreenImageView = dialogView.findViewById<android.widget.ImageView>(R.id.fullScreenImageView)
        val closeButton = dialogView.findViewById<android.view.View>(R.id.closeButton)

        if (fullScreenImageView != null) {
            com.example.gupshup.util.ImageLoaderUtil.loadChatImage(fullScreenImageView, imageUrl)
        }

        fullScreenImageView?.setOnClickListener {
            dialog.dismiss()
        }

        closeButton?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
        if (com.example.gupshup.data.local.CacheConfig.activeChatId == chatId) {
            com.example.gupshup.data.local.CacheConfig.activeChatId = null
        }
        userStatusListener?.remove()
        newMessagesListener?.remove()
        typingTimer?.cancel()
    }
}