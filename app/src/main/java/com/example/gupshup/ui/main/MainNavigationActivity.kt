package com.example.gupshup.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.gupshup.R
import com.example.gupshup.databinding.ActivityMainNavigationBinding
import com.example.gupshup.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.gupshup.ui.main.StatusFragment

class MainNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNavigationBinding
    private val db = FirebaseFirestore.getInstance()

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

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
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