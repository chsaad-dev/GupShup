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

        (activity as? AppCompatActivity)?.setSupportActionBar(binding.friendsToolbar)

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFriendRequests()
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

        showShimmer()
        loadFriendRequests()
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

    private fun loadFriendRequests() {
        if (currentUid == null) return

        val requestsRef = db.collection("friend_requests")
        val usersRef = db.collection("users")

        requestsRef.whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { pendingSnapshot ->
                if (_binding == null || !isAdded) return@addOnSuccessListener

                val pendingUids = pendingSnapshot.mapNotNull { it.getString("fromUid") }

                if (pendingUids.isEmpty()) {
                    friendRequests.clear()
                    adapter.notifyDataSetChanged()
                    showContent()
                    _binding?.swipeRefreshLayout?.isRefreshing = false
                    return@addOnSuccessListener
                }

                usersRef.whereIn("uid", pendingUids)
                    .get()
                    .addOnSuccessListener { userSnapshot ->
                        if (_binding == null || !isAdded) return@addOnSuccessListener

                        friendRequests.clear()
                        friendRequests.addAll(userSnapshot.mapNotNull { it.toObject(User::class.java) })
                        adapter.notifyDataSetChanged()
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
                                loadFriendRequests()
                            }
                    } else {
                        doc.delete()
                            .addOnSuccessListener {
                                if (_binding == null || !isAdded) return@addOnSuccessListener
                                showToast("Friend request rejected")
                                loadFriendRequests()
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
