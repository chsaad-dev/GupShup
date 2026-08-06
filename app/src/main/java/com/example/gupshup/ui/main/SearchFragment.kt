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


        binding.searchToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = UsersAdapter(
            context = requireContext(),
            userList = allUsers,
            unreadCountMap = emptyMap(),
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
                if (_binding == null || !isAdded) return@addOnSuccessListener

                val searchResults = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                    .filter {
                        it.uid != currentUid &&
                                (it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true))
                    }

                db.collection("friend_requests")
                    .whereEqualTo("fromUid", currentUid)
                    .get()
                    .addOnSuccessListener { sentReqs ->
                        if (_binding == null || !isAdded) return@addOnSuccessListener

                        db.collection("friend_requests")
                            .whereEqualTo("toUid", currentUid)
                            .get()
                            .addOnSuccessListener { recvReqs ->
                                if (_binding == null || !isAdded) return@addOnSuccessListener

                                val blockedUids = mutableSetOf<String>()
                                for (req in sentReqs) {
                                    val status = req.getString("status")
                                    if (status == "accepted" || status == "pending") {
                                        req.getString("toUid")?.let { blockedUids.add(it) }
                                    }
                                }
                                for (req in recvReqs) {
                                    val status = req.getString("status")
                                    if (status == "accepted" || status == "pending") {
                                        req.getString("fromUid")?.let { blockedUids.add(it) }
                                    }
                                }

                                allUsers.clear()
                                allUsers.addAll(searchResults.filter { !blockedUids.contains(it.uid) })
                                adapter.notifyDataSetChanged()
                                updateEmptyState()
                            }
                            .addOnFailureListener { e ->
                                if (_binding != null && isAdded) {
                                    context?.let { Toast.makeText(it, "Failed to load requests: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        if (_binding != null && isAdded) {
                            context?.let { Toast.makeText(it, "Failed to load requests: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }

            }
            .addOnFailureListener { e ->
                if (_binding != null && isAdded) {
                    context?.let { Toast.makeText(it, "❌ Error loading users: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
    }

    private fun sendFriendRequest(user: User) {
        val fromUid = FirebaseAuth.getInstance().currentUser?.uid
        val toUid = user.uid

        if (fromUid.isNullOrEmpty() || toUid.isNullOrEmpty()) {
            context?.let { Toast.makeText(it, "❌ Invalid user info", Toast.LENGTH_SHORT).show() }
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
            .addOnSuccessListener { docRef ->
                lifecycleScope.launch {
                    com.example.gupshup.util.NotificationApiClient.notifyFriendRequest(docRef.id)
                }
                if (_binding == null || !isAdded) return@addOnSuccessListener
                context?.let { Toast.makeText(it, "✅ Friend request sent", Toast.LENGTH_SHORT).show() }
                binding.searchInput.setText("")
                allUsers.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
            .addOnFailureListener { e ->
                if (_binding != null && isAdded) {
                    context?.let { Toast.makeText(it, "❌ Failed to send request: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
