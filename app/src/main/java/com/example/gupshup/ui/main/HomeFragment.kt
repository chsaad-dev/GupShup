package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.R
import com.example.gupshup.adapter.UsersAdapter
import com.example.gupshup.databinding.FragmentHomeBinding
import com.example.gupshup.model.User
import com.example.gupshup.ui.chat.ChatActivity
import com.facebook.shimmer.ShimmerFrameLayout
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val users = mutableListOf<User>()
    private lateinit var adapter: UsersAdapter

    private val unreadCountMap = mutableMapOf<String, Int>()
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()
    private var isDataLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.homeToolbar)
        binding.homeToolbar.title = "Chats"

        binding.fabNewChat.setOnClickListener {
            (activity as? MainNavigationActivity)?.selectSearchTab()
        }

        adapter = UsersAdapter(
            requireContext(),
            users,
            unreadCountMap = unreadCountMap
        ) { user ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("receiverId", user.uid)
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.homeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.homeRecyclerView.adapter = adapter
        binding.homeRecyclerView.layoutAnimation =
            android.view.animation.AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_animation_fall_down
            )

        binding.swipeRefreshLayout.setOnRefreshListener {
            val currentUid = auth.currentUser?.uid
            if (currentUid != null) {
                checkCacheAndLoad(currentUid, isForceRefresh = true)
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        if (!isDataLoaded) {
            val currentUid = auth.currentUser?.uid
            if (currentUid != null) {
                checkCacheAndLoad(currentUid, isForceRefresh = false)
            } else {
                loadAcceptedFriends(isForceRefresh = false)
            }
        } else {
            showContent()
        }
    }

    private fun checkCacheAndLoad(currentUid: String, isForceRefresh: Boolean = false) {
        if (isForceRefresh) {
            android.util.Log.d("HomeFragment", "[CachePolicy] Force refresh requested -> Fetching from Firestore")
            showShimmer()
            loadAcceptedFriends(isForceRefresh = true)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val appDb = AppDatabase.getInstance(requireContext())
            val requests = appDb.friendRequestDao().getAcceptedFlow(currentUid).firstOrNull() ?: emptyList()
            val friendIds = requests.map { if (it.fromUid == currentUid) it.toUid else it.fromUid }

            if (friendIds.isNotEmpty()) {
                val cachedUserEntities = appDb.userDao().getUsersByIds(friendIds).firstOrNull() ?: emptyList()

                if (cachedUserEntities.isNotEmpty()) {
                    users.clear()
                    users.addAll(cachedUserEntities.map { entity ->
                        User(
                            uid = entity.uid,
                            name = entity.name,
                            email = entity.email,
                            profileImageUrl = entity.profileImageUrl,
                            bio = entity.bio,
                            isOnline = entity.online
                        )
                    })
                    users.forEach { observeUnreadMessages(it.uid) }
                    adapter.notifyDataSetChanged()
                    showContent()

                    val maxRequestCachedAt = requests.maxOfOrNull { it.cachedAt } ?: 0L
                    val maxUserCachedAt = cachedUserEntities.maxOfOrNull { it.cachedAt } ?: 0L
                    val reqStale = (System.currentTimeMillis() - maxRequestCachedAt) > com.example.gupshup.data.local.CacheConfig.FRIEND_CACHE_STALENESS_MS
                    val usersStale = (System.currentTimeMillis() - maxUserCachedAt) > com.example.gupshup.data.local.CacheConfig.FRIEND_CACHE_STALENESS_MS

                    if (reqStale || usersStale) {
                        android.util.Log.d("HomeFragment", "[CachePolicy] Room cache STALE -> Fetching from Firestore")
                        loadAcceptedFriends(isForceRefresh = false)
                    } else {
                        android.util.Log.d("HomeFragment", "[CachePolicy] Room cache FRESH -> Skipping Firestore fetch")
                        isDataLoaded = true
                        _binding?.swipeRefreshLayout?.isRefreshing = false
                    }
                    return@launch
                }
            }

            android.util.Log.d("HomeFragment", "[CachePolicy] No Room cache found -> Fetching from Firestore")
            showShimmer()
            loadAcceptedFriends(isForceRefresh = false)
        }
    }

    private fun showShimmer() {
        val shimmer = _binding?.root?.findViewById<ShimmerFrameLayout>(R.id.shimmerLayout)
        shimmer?.startShimmer()
        _binding?.root?.findViewById<View>(R.id.shimmerPlaceholder)?.visibility = View.VISIBLE
        _binding?.homeRecyclerView?.visibility = View.GONE
        _binding?.emptyStateView?.visibility = View.GONE
    }

    private fun hideShimmer() {
        val shimmer = _binding?.root?.findViewById<ShimmerFrameLayout>(R.id.shimmerLayout)
        shimmer?.stopShimmer()
        _binding?.root?.findViewById<View>(R.id.shimmerPlaceholder)?.visibility = View.GONE
    }

    private fun showContent() {
        hideShimmer()
        if (users.isEmpty()) {
            _binding?.homeRecyclerView?.visibility = View.GONE
            _binding?.emptyStateView?.visibility = View.VISIBLE

            _binding?.emptyStateView?.alpha = 0f
            _binding?.emptyStateView?.animate()?.alpha(1f)?.setDuration(400)?.start()
        } else {
            _binding?.homeRecyclerView?.visibility = View.VISIBLE
            _binding?.emptyStateView?.visibility = View.GONE
            _binding?.homeRecyclerView?.scheduleLayoutAnimation()
        }
    }

    private fun loadAcceptedFriends(isForceRefresh: Boolean = false) {
        if (isDataLoaded && !isForceRefresh) {
            showContent()
            return
        }

        val currentUid = auth.currentUser?.uid ?: return
        val friendIds = mutableSetOf<String>()

        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        unreadCountMap.clear()

        db.collection("friend_requests")
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { requests ->
                if (_binding == null || !isAdded) return@addOnSuccessListener

                val reqEntities = mutableListOf<com.example.gupshup.data.local.entity.FriendRequestEntity>()
                for (doc in requests) {
                    val from = doc.getString("fromUid")
                    val to = doc.getString("toUid")
                    if (from == currentUid) friendIds.add(to ?: "")
                    if (to == currentUid) friendIds.add(from ?: "")
                    reqEntities.add(
                        com.example.gupshup.data.local.entity.FriendRequestEntity(
                            id = doc.id,
                            fromUid = from ?: "",
                            toUid = to ?: "",
                            status = "accepted"
                        )
                    )
                }

                val appContext = context?.applicationContext
                if (appContext != null) {
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        AppDatabase.getInstance(appContext).friendRequestDao().upsert(reqEntities)
                    }
                }

                if (friendIds.isEmpty()) {
                    users.clear()
                    adapter.notifyDataSetChanged()
                    updateHomeBadge()
                    isDataLoaded = true
                    showContent()
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
                            if (_binding == null || !isAdded) return@addOnSuccessListener

                            val fetchedUsers = mutableListOf<User>()
                            val fetchedEntities = mutableListOf<com.example.gupshup.data.local.entity.UserEntity>()

                            for (doc in result) {
                                val user = doc.toObject(User::class.java)
                                if (user.uid != currentUid) {
                                    users.add(user)
                                    fetchedUsers.add(user)
                                    fetchedEntities.add(
                                        com.example.gupshup.data.local.entity.UserEntity(
                                            uid = user.uid,
                                            name = user.name,
                                            email = user.email,
                                            profileImageUrl = user.profileImageUrl ?: "",
                                            bio = user.bio ?: "",
                                            online = user.isOnline
                                        )
                                    )
                                    observeUnreadMessages(user.uid)
                                }
                            }

                            if (appContext != null && fetchedEntities.isNotEmpty()) {
                                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    AppDatabase.getInstance(appContext).userDao().upsert(fetchedEntities)
                                }
                            }
                            
                            completedChunks++
                            adapter.notifyDataSetChanged()
                            
                            if (completedChunks == chunks.size) {
                                isDataLoaded = true
                                showContent()
                                _binding?.swipeRefreshLayout?.isRefreshing = false
                            }
                        }
                        .addOnFailureListener {
                            if (_binding == null || !isAdded) return@addOnFailureListener
                            completedChunks++
                            if (completedChunks == chunks.size) {
                                isDataLoaded = true
                                showContent()
                                _binding?.swipeRefreshLayout?.isRefreshing = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                showContent()
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
                if (_binding == null || !isAdded) return@addSnapshotListener
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
        val navActivity = activity as? MainNavigationActivity ?: return
        val totalUnread = unreadCountMap.values.sum()
        navActivity.updateHomeBadge(unreadCountMap, totalUnread)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        listenerRegistrations.forEach { it.remove() }
    }
}
