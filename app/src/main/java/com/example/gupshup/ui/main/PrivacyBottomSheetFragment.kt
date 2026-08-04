package com.example.gupshup.ui.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.gupshup.databinding.DialogPrivacySettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PrivacyBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogPrivacySettingsBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var onPrivacyUpdatedListener: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPrivacySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPrivacyState()
        setupListeners()
    }

    private fun loadPrivacyState() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)

        val onlineVal = prefs.getString("pref_privacy_online", "Everyone") ?: "Everyone"
        val lastSeenVal = prefs.getString("pref_privacy_last_seen", "Everyone") ?: "Everyone"
        val photoVal = prefs.getString("pref_privacy_photo", "Everyone") ?: "Everyone"

        updateSwitchUI(onlineVal, lastSeenVal, photoVal)

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (_binding == null || !isAdded || doc == null || !doc.exists()) return@addOnSuccessListener
            val fsOnline = doc.getString("privacyOnline") ?: onlineVal
            val fsLastSeen = doc.getString("privacyLastSeen") ?: lastSeenVal
            val fsPhoto = doc.getString("privacyPhoto") ?: photoVal

            prefs.edit()
                .putString("pref_privacy_online", fsOnline)
                .putString("pref_privacy_last_seen", fsLastSeen)
                .putString("pref_privacy_photo", fsPhoto)
                .apply()

            updateSwitchUI(fsOnline, fsLastSeen, fsPhoto)
        }
    }

    private fun updateSwitchUI(online: String, lastSeen: String, photo: String) {
        binding.switchOnlineStatus.setOnCheckedChangeListener(null)
        binding.switchLastSeen.setOnCheckedChangeListener(null)
        binding.switchProfilePhoto.setOnCheckedChangeListener(null)

        binding.switchOnlineStatus.isChecked = (online == "Everyone")
        binding.textSubtitleOnline.text = if (online == "Everyone") "Visible to everyone" else "Hidden (Nobody)"

        binding.switchLastSeen.isChecked = (lastSeen == "Everyone")
        binding.textSubtitleLastSeen.text = if (lastSeen == "Everyone") "Visible to everyone" else "Hidden (Nobody)"

        binding.switchProfilePhoto.isChecked = (photo == "Everyone")
        binding.textSubtitlePhoto.text = if (photo == "Everyone") "Visible to everyone" else "Hidden (Nobody)"

        setupListeners()
    }

    private fun setupListeners() {
        binding.switchOnlineStatus.setOnCheckedChangeListener { _, isChecked ->
            val newValue = if (isChecked) "Everyone" else "Nobody"
            updatePrivacySetting("privacyOnline", newValue, "pref_privacy_online")
        }

        binding.switchLastSeen.setOnCheckedChangeListener { _, isChecked ->
            val newValue = if (isChecked) "Everyone" else "Nobody"
            updatePrivacySetting("privacyLastSeen", newValue, "pref_privacy_last_seen")
        }

        binding.switchProfilePhoto.setOnCheckedChangeListener { _, isChecked ->
            val newValue = if (isChecked) "Everyone" else "Nobody"
            updatePrivacySetting("privacyPhoto", newValue, "pref_privacy_photo")
        }
    }

    private fun updatePrivacySetting(fieldName: String, value: String, prefKey: String) {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("gupshup_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(prefKey, value).apply()

        db.collection("users").document(uid).update(fieldName, value)
        onPrivacyUpdatedListener?.invoke()

        val online = prefs.getString("pref_privacy_online", "Everyone") ?: "Everyone"
        val lastSeen = prefs.getString("pref_privacy_last_seen", "Everyone") ?: "Everyone"
        val photo = prefs.getString("pref_privacy_photo", "Everyone") ?: "Everyone"
        updateSwitchUI(online, lastSeen, photo)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
