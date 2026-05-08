package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivityMainNavigationBinding
import com.example.gupshup.ui.auth.LoginActivity
import com.example.gupshup.util.NetworkObserver
import com.example.gupshup.util.NetworkStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNavigationBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var networkObserver: NetworkObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Network observer
        networkObserver = NetworkObserver(this)
        observeNetwork()

        loadFragment(HomeFragment())
        checkPendingRequestsBadge()

        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.menu_home -> loadFragment(HomeFragment())
                R.id.menu_status -> loadFragment(StatusFragment())
                R.id.menu_search -> loadFragment(SearchFragment())
                R.id.menu_friends -> loadFragment(FriendsFragment())
                R.id.menu_profile -> loadFragment(ProfileFragment())
            }
            true
        }
    }

    private fun observeNetwork() {
        lifecycleScope.launch {
            networkObserver.networkStatus.collect { status ->
                when (status) {
                    NetworkStatus.Lost -> {
                        binding.offlineBanner.visibility = View.VISIBLE
                        binding.offlineBanner.alpha = 0f
                        binding.offlineBanner.animate().alpha(1f).setDuration(300).start()
                    }
                    NetworkStatus.Available -> {
                        binding.offlineBanner.animate().alpha(0f).setDuration(300).withEndAction {
                            binding.offlineBanner.visibility = View.GONE
                        }.start()
                    }
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun checkPendingRequestsBadge() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("friend_requests")
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                val badge = binding.bottomNav.getOrCreateBadge(R.id.menu_friends)
                badge.isVisible = count > 0
                badge.number = count
            }
    }

    // ✅ This is called by HomeFragment live when unread changes
    fun updateHomeBadge(unreadMap: Map<String, Int>, totalUnread: Int = unreadMap.values.sum()) {
        val badge = binding.bottomNav.getOrCreateBadge(R.id.menu_home)
        badge.isVisible = totalUnread > 0
        badge.number = totalUnread
    }

    override fun onResume() {
        super.onResume()
        setUserOnline(true)
    }

    override fun onPause() {
        super.onPause()
        setUserOnline(false)
    }

    private fun setUserOnline(status: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("isOnline", status)
    }
}