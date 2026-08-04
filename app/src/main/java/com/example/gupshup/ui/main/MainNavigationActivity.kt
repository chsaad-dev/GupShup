package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
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

    private val homeFragment by lazy { HomeFragment() }
    private val statusFragment by lazy { StatusFragment() }
    private val searchFragment by lazy { SearchFragment() }
    private val friendsFragment by lazy { FriendsFragment() }
    private val profileFragment by lazy { ProfileFragment() }

    private var activeFragment: Fragment = homeFragment

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

        // Session cache cleanup
        com.example.gupshup.data.local.CacheCleanupManager.runSessionCleanup(this)

        setupFragmentNavigation(savedInstanceState)
        checkPendingRequestsBadge()

        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_home -> switchFragment(homeFragment, "home")
                R.id.menu_status -> switchFragment(statusFragment, "status")
                R.id.menu_search -> switchFragment(searchFragment, "search")
                R.id.menu_friends -> switchFragment(friendsFragment, "friends")
                R.id.menu_profile -> switchFragment(profileFragment, "profile")
            }
            true
        }
    }

    private fun setupFragmentNavigation(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, homeFragment, "home")
                .commit()
            activeFragment = homeFragment
        } else {
            val restoredHome = supportFragmentManager.findFragmentByTag("home") ?: homeFragment
            val restoredStatus = supportFragmentManager.findFragmentByTag("status") ?: statusFragment
            val restoredSearch = supportFragmentManager.findFragmentByTag("search") ?: searchFragment
            val restoredFriends = supportFragmentManager.findFragmentByTag("friends") ?: friendsFragment
            val restoredProfile = supportFragmentManager.findFragmentByTag("profile") ?: profileFragment

            activeFragment = when (binding.bottomNav.selectedItemId) {
                R.id.menu_status -> restoredStatus
                R.id.menu_search -> restoredSearch
                R.id.menu_friends -> restoredFriends
                R.id.menu_profile -> restoredProfile
                else -> restoredHome
            }
        }
    }

    private fun switchFragment(targetFragment: Fragment, tag: String) {
        if (activeFragment == targetFragment) return

        val transaction = supportFragmentManager.beginTransaction()
        transaction.hide(activeFragment)

        if (!targetFragment.isAdded) {
            transaction.add(R.id.fragmentContainer, targetFragment, tag)
        } else {
            transaction.show(targetFragment)
        }

        transaction.commit()
        activeFragment = targetFragment
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

    fun updateHomeBadge(unreadMap: Map<String, Int>, totalUnread: Int = unreadMap.values.sum()) {
        val badge = binding.bottomNav.getOrCreateBadge(R.id.menu_home)
        badge.isVisible = totalUnread > 0
        badge.number = totalUnread
    }

    fun selectSearchTab() {
        binding.bottomNav.selectedItemId = R.id.menu_search
    }

    override fun onResume() {
        super.onResume()
        setUserOnline(true)
    }

    override fun onPause() {
        super.onPause()
        setUserOnline(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        setUserOnline(false)
    }

    private fun setUserOnline(status: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("isOnline", status)
    }
}