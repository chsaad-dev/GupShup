package com.example.gupshup.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.gupshup.databinding.BottomSheetHelpCenterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth

class HelpCenterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHelpCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHelpCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnContactEmail.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            val recipient = "saaddevlabs@gmail.com"
            val subject = "[GupShup Support] Support & Inquiry"
            val bodyText = """
                Hi GupShup Support Team,

                [Write your inquiry or message here]

                -----------------------------------------
                Device Information:
                • User ID: ${user?.uid ?: "Anonymous"}
                • Account Email: ${user?.email ?: "N/A"}
                • Device: ${Build.MANUFACTURER} ${Build.MODEL}
                • Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                • App Version: 1.0.0
                -----------------------------------------
            """.trimIndent()

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyText)
            }

            try {
                startActivity(Intent.createChooser(intent, "Send Email via..."))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No email client found on device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
