package com.example.gupshup.ui.main

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.UsersAdapter
import com.example.gupshup.databinding.FragmentFriendsBinding
import com.example.gupshup.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private val friendRequests = mutableListOf<User>()
    private lateinit var adapter: UsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🧭 Optional: If using toolbar in fragment (for standalone navigation)
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.friendsToolbar)

        // 🔁 Swipe to Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFriendRequests()
        }

        // 👥 Set up adapter
        adapter = UsersAdapter(
            context = requireContext(),
            userList = friendRequests,
            showAddButton = false,
            showRequestButtons = true,
            onAcceptClick = { user -> handleFriendRequest(user.uid, true) },
            onRejectClick = { user -> handleFriendRequest(user.uid, false) }
        )

        binding.friendsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.friendsRecyclerView.adapter = adapter

        loadFriendRequests()
    }

    private fun loadFriendRequests() {
        if (currentUid == null) return

        // Start refresh animation
        binding.swipeRefreshLayout.isRefreshing = true

        val requestsRef = db.collection("friend_requests")
        val usersRef = db.collection("users")

        requestsRef.whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { pendingSnapshot ->
                val pendingUids = pendingSnapshot.mapNotNull { it.getString("fromUid") }

                if (pendingUids.isEmpty()) {
                    friendRequests.clear()
                    adapter.notifyDataSetChanged()
                    binding.swipeRefreshLayout.isRefreshing = false
                    return@addOnSuccessListener
                }

                usersRef.whereIn("uid", pendingUids)
                    .get()
                    .addOnSuccessListener { userSnapshot ->
                        friendRequests.clear()
                        friendRequests.addAll(userSnapshot.mapNotNull { it.toObject(User::class.java) })
                        adapter.notifyDataSetChanged()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                    .addOnFailureListener {
                        showToast("Failed to load users")
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
            }
            .addOnFailureListener {
                showToast("Failed to load requests")
                binding.swipeRefreshLayout.isRefreshing = false
            }
    }

    private fun handleFriendRequest(fromUid: String, accept: Boolean) {
        val ref = db.collection("friend_requests")
        ref.whereEqualTo("fromUid", fromUid)
            .whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0].reference
                    if (accept) {
                        doc.update("status", "accepted")
                            .addOnSuccessListener {
                                showToast("Friend request accepted")
                                loadFriendRequests()
                            }
                    } else {
                        doc.delete()
                            .addOnSuccessListener {
                                showToast("Friend request rejected")
                                loadFriendRequests()
                            }
                    }
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
