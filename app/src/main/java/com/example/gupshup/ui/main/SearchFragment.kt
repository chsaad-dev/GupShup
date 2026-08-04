package com.example.gupshup.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.UsersAdapter
import com.example.gupshup.databinding.FragmentSearchBinding
import com.example.gupshup.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private val allUsers = mutableListOf<User>()
    private lateinit var adapter: UsersAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 Back button
        binding.searchToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = UsersAdapter(
            context = requireContext(),
            userList = allUsers,
            unreadCountMap = emptyMap(), // ✅ FIXED: No unread count needed here
            onUserClick = { user -> sendFriendRequest(user) },
            showAddButton = true
        )

        binding.searchRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.searchRecyclerView.adapter = adapter

        setupLiveSearch()
    }

    private fun setupLiveSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400)
                    val query = s.toString().trim()
                    if (query.isNotEmpty()) {
                        searchUsers(query)
                    } else {
                        allUsers.clear()
                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (_binding == null) return
        if (allUsers.isEmpty()) {
            binding.searchEmptyState.visibility = View.VISIBLE
            binding.searchRecyclerView.visibility = View.GONE
        } else {
            binding.searchEmptyState.visibility = View.GONE
            binding.searchRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun searchUsers(query: String) {
        if (currentUid == null) return

        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                val searchResults = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                    .filter {
                        it.uid != currentUid &&
                                (it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true))
                    }

                db.collection("friend_requests")
                    .whereIn("status", listOf("accepted", "pending"))
                    .get()
                    .addOnSuccessListener { requests ->
                        val blockedUids = mutableSetOf<String>()
                        for (req in requests) {
                            val from = req.getString("fromUid")
                            val to = req.getString("toUid")
                            if (from == currentUid || to == currentUid) {
                                blockedUids.add(if (from == currentUid) to!! else from!!)
                            }
                        }

                        allUsers.clear()
                        allUsers.addAll(searchResults.filter { !blockedUids.contains(it.uid) })
                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "❌ Failed to load friend requests: ${e.message}", Toast.LENGTH_LONG).show()
                    }

            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "❌ Error loading users: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendFriendRequest(user: User) {
        val fromUid = FirebaseAuth.getInstance().currentUser?.uid
        val toUid = user.uid

        if (fromUid.isNullOrEmpty() || toUid.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "❌ Invalid user info", Toast.LENGTH_SHORT).show()
            return
        }

        val request = hashMapOf(
            "fromUid" to fromUid,
            "toUid" to toUid,
            "status" to "pending",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("friend_requests")
            .add(request)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ Friend request sent", Toast.LENGTH_SHORT).show()
                binding.searchInput.setText("")
                allUsers.clear()
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "❌ Failed to send request: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
