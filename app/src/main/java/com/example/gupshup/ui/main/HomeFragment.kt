package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.UsersAdapter
import com.example.gupshup.databinding.FragmentHomeBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.chat.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val users = mutableListOf<User>()
    private lateinit var adapter: UsersAdapter

    private val unreadCountMap = mutableMapOf<String, Int>()
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.homeToolbar)
        binding.homeToolbar.title = "Home"

        adapter = UsersAdapter(
            requireContext(),
            users,
            unreadCountMap = unreadCountMap
        ) { user ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("receiverId", user.uid)
            startActivity(intent)
        }

        binding.homeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.homeRecyclerView.adapter = adapter
        binding.homeRecyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadAcceptedFriends()
        }

        binding.swipeRefreshLayout.isRefreshing = true
        loadAcceptedFriends()
    }

    private fun loadAcceptedFriends() {
        val currentUid = auth.currentUser?.uid ?: return
        val friendIds = mutableSetOf<String>()

        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        unreadCountMap.clear()

        db.collection("friend_requests")
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { requests ->
                for (doc in requests) {
                    val from = doc.getString("fromUid")
                    val to = doc.getString("toUid")
                    if (from == currentUid) friendIds.add(to ?: "")
                    if (to == currentUid) friendIds.add(from ?: "")
                }

                if (friendIds.isEmpty()) {
                    users.clear()
                    adapter.notifyDataSetChanged()
                    updateHomeBadge()
                    _binding?.swipeRefreshLayout?.isRefreshing = false
                    return@addOnSuccessListener
                }

                // Firestore 'whereIn' supports max 10 values. We must split into chunks.
                val chunks = friendIds.toList().chunked(10)
                users.clear() // Clear once before fetching all chunks

                // We need to track when all chunks are loaded to stop refreshing
                var completedChunks = 0

                chunks.forEach { chunk ->
                    db.collection("users")
                        .whereIn("uid", chunk)
                        .get()
                        .addOnSuccessListener { result ->
                            for (doc in result) {
                                val user = doc.toObject(User::class.java)
                                if (user.uid != currentUid) {
                                    users.add(user)
                                    observeUnreadMessages(user.uid)
                                }
                            }
                            
                            completedChunks++
                            // Only notify and stop refreshing when we have at least some data or all done
                            // Adapting to simple incremental update for responsiveness
                            adapter.notifyDataSetChanged()
                            
                            if (completedChunks == chunks.size) {
                                _binding?.swipeRefreshLayout?.isRefreshing = false
                            }
                        }
                        .addOnFailureListener {
                            completedChunks++
                            if (completedChunks == chunks.size) {
                                _binding?.swipeRefreshLayout?.isRefreshing = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
    }

    private fun observeUnreadMessages(friendUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < friendUid) "${currentUid}${friendUid}" else "${friendUid}${currentUid}"

        val listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereEqualTo("receiverId", currentUid)
            .whereEqualTo("seen", false)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    unreadCountMap[friendUid] = snapshot.size()
                    _binding?.let {
                        adapter.notifyDataSetChanged()
                        updateHomeBadge()
                    }
                }
            }

        listenerRegistrations.add(listener)
    }

    private fun updateHomeBadge() {
        val activity = requireActivity() as? MainNavigationActivity ?: return
        val totalUnread = unreadCountMap.values.sum()
        activity.updateHomeBadge(unreadCountMap, totalUnread)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        listenerRegistrations.forEach { it.remove() }
    }
}
