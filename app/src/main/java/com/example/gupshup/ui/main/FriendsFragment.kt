package com.example.gupshup.ui.main

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.R
import com.example.gupshup.adapter.UsersAdapter
import com.example.gupshup.databinding.FragmentFriendsBinding
import com.example.gupshup.model.User
import com.facebook.shimmer.ShimmerFrameLayout
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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

    private var isDataLoaded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.friendsToolbar)
        binding.friendsToolbar.title = "Friend Requests"

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (currentUid != null) {
                checkCacheAndLoad(currentUid, isForceRefresh = true)
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

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
        binding.friendsRecyclerView.layoutAnimation =
            android.view.animation.AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_animation_fall_down
            )

        if (!isDataLoaded) {
            if (currentUid != null) {
                checkCacheAndLoad(currentUid, isForceRefresh = false)
            } else {
                loadFriendRequests(isForceRefresh = false)
            }
        } else {
            showContent()
        }
    }

    private fun checkCacheAndLoad(uid: String, isForceRefresh: Boolean = false) {
        if (isForceRefresh) {
            android.util.Log.d("FriendsFragment", "[CachePolicy] Force refresh requested -> Fetching from Firestore")
            showShimmer()
            loadFriendRequests(isForceRefresh = true)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val appDb = com.example.gupshup.data.local.AppDatabase.getInstance(requireContext())
            val requests = appDb.friendRequestDao().getPendingIncomingFlow(uid).firstOrNull() ?: emptyList()
            val pendingUids = requests.map { it.fromUid }

            if (pendingUids.isNotEmpty()) {
                val cachedUserEntities = appDb.userDao().getUsersByIds(pendingUids).firstOrNull() ?: emptyList()

                if (cachedUserEntities.isNotEmpty()) {
                    friendRequests.clear()
                    friendRequests.addAll(cachedUserEntities.map { entity ->
                        User(
                            uid = entity.uid,
                            name = entity.name,
                            email = entity.email,
                            profileImageUrl = entity.profileImageUrl,
                            bio = entity.bio,
                            isOnline = entity.online
                        )
                    })
                    adapter.notifyDataSetChanged()
                    showContent()

                    val maxReqCachedAt = requests.maxOfOrNull { it.cachedAt } ?: 0L
                    val maxUserCachedAt = cachedUserEntities.maxOfOrNull { it.cachedAt } ?: 0L
                    val reqStale = (System.currentTimeMillis() - maxReqCachedAt) > com.example.gupshup.data.local.CacheConfig.FRIEND_CACHE_STALENESS_MS
                    val usersStale = (System.currentTimeMillis() - maxUserCachedAt) > com.example.gupshup.data.local.CacheConfig.FRIEND_CACHE_STALENESS_MS

                    if (reqStale || usersStale) {
                        android.util.Log.d("FriendsFragment", "[CachePolicy] Room cache STALE -> Fetching from Firestore")
                        loadFriendRequests(isForceRefresh = false)
                    } else {
                        android.util.Log.d("FriendsFragment", "[CachePolicy] Room cache FRESH -> Skipping Firestore fetch")
                        isDataLoaded = true
                        _binding?.swipeRefreshLayout?.isRefreshing = false
                    }
                    return@launch
                }
            }

            android.util.Log.d("FriendsFragment", "[CachePolicy] No Room cache found -> Fetching from Firestore")
            showShimmer()
            loadFriendRequests(isForceRefresh = false)
        }
    }

    private fun showShimmer() {
        val shimmer = _binding?.root?.findViewById<ShimmerFrameLayout>(R.id.shimmerLayout)
        shimmer?.startShimmer()
        _binding?.root?.findViewById<View>(R.id.shimmerPlaceholder)?.visibility = View.VISIBLE
        _binding?.friendsRecyclerView?.visibility = View.GONE
        _binding?.emptyStateView?.visibility = View.GONE
    }

    private fun hideShimmer() {
        val shimmer = _binding?.root?.findViewById<ShimmerFrameLayout>(R.id.shimmerLayout)
        shimmer?.stopShimmer()
        _binding?.root?.findViewById<View>(R.id.shimmerPlaceholder)?.visibility = View.GONE
    }

    private fun showContent() {
        hideShimmer()
        if (friendRequests.isEmpty()) {
            _binding?.friendsRecyclerView?.visibility = View.GONE
            _binding?.emptyStateView?.visibility = View.VISIBLE
            _binding?.emptyStateView?.alpha = 0f
            _binding?.emptyStateView?.animate()?.alpha(1f)?.setDuration(400)?.start()
        } else {
            _binding?.friendsRecyclerView?.visibility = View.VISIBLE
            _binding?.emptyStateView?.visibility = View.GONE
            _binding?.friendsRecyclerView?.scheduleLayoutAnimation()
        }
    }

    private fun loadFriendRequests(isForceRefresh: Boolean = false) {
        if (isDataLoaded && !isForceRefresh) {
            showContent()
            return
        }

        if (currentUid == null) return

        val requestsRef = db.collection("friend_requests")
        val usersRef = db.collection("users")

        requestsRef.whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { pendingSnapshot ->
                if (_binding == null || !isAdded) return@addOnSuccessListener

                val pendingUids = pendingSnapshot.mapNotNull { it.getString("fromUid") }

                val appContext = context?.applicationContext
                val reqEntities = pendingSnapshot.map { doc ->
                    com.example.gupshup.data.local.entity.FriendRequestEntity(
                        id = doc.id,
                        fromUid = doc.getString("fromUid") ?: "",
                        toUid = doc.getString("toUid") ?: "",
                        status = "pending"
                    )
                }
                if (appContext != null && reqEntities.isNotEmpty()) {
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.gupshup.data.local.AppDatabase.getInstance(appContext).friendRequestDao().upsert(reqEntities)
                    }
                }

                if (pendingUids.isEmpty()) {
                    friendRequests.clear()
                    adapter.notifyDataSetChanged()
                    isDataLoaded = true
                    showContent()
                    _binding?.swipeRefreshLayout?.isRefreshing = false
                    return@addOnSuccessListener
                }

                usersRef.whereIn(com.google.firebase.firestore.FieldPath.documentId(), pendingUids)
                    .get()
                    .addOnSuccessListener { userSnapshot ->
                        if (_binding == null || !isAdded) return@addOnSuccessListener

                        val fetched = userSnapshot.mapNotNull { it.toObject(User::class.java) }
                        friendRequests.clear()
                        friendRequests.addAll(fetched)
                        
                        val userEntities = fetched.map { user ->
                            com.example.gupshup.data.local.entity.UserEntity(
                                uid = user.uid,
                                name = user.name,
                                email = user.email,
                                profileImageUrl = user.effectiveProfileImageUrl,
                                bio = user.bio ?: "",
                                online = user.isOnline
                            )
                        }
                        if (appContext != null && userEntities.isNotEmpty()) {
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.example.gupshup.data.local.AppDatabase.getInstance(appContext).userDao().upsert(userEntities)
                            }
                        }

                        adapter.notifyDataSetChanged()
                        isDataLoaded = true
                        showContent()
                        _binding?.swipeRefreshLayout?.isRefreshing = false
                    }
                    .addOnFailureListener {
                        if (_binding == null || !isAdded) return@addOnFailureListener
                        showToast("Failed to load users")
                        showContent()
                        _binding?.swipeRefreshLayout?.isRefreshing = false
                    }
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                showToast("Failed to load requests")
                showContent()
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
    }

    private fun handleFriendRequest(fromUid: String, accept: Boolean) {
        val ref = db.collection("friend_requests")
        ref.whereEqualTo("fromUid", fromUid)
            .whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null || !isAdded) return@addOnSuccessListener

                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0].reference
                    if (accept) {
                        doc.update("status", "accepted")
                            .addOnSuccessListener {
                                if (_binding == null || !isAdded) return@addOnSuccessListener
                                showToast("Friend request accepted")
                                loadFriendRequests(isForceRefresh = true)
                            }
                    } else {
                        doc.delete()
                            .addOnSuccessListener {
                                if (_binding == null || !isAdded) return@addOnSuccessListener
                                showToast("Friend request rejected")
                                loadFriendRequests(isForceRefresh = true)
                            }
                    }
                }
            }
    }

    private fun showToast(message: String) {
        if (_binding != null && isAdded) {
            context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
