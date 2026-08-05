package com.example.gupshup.ui.main

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.gupshup.R
import com.example.gupshup.databinding.BottomSheetReportProblemBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportProblemBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportProblemBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReportProblemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        binding.btnSubmitReport.setOnClickListener {
            val description = binding.editDescription.text.toString().trim()
            if (description.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a description of the problem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = when (binding.radioGroupCategory.checkedRadioButtonId) {
                R.id.radioBug -> "Bug Report"
                R.id.radioFeature -> "Feature Request"
                R.id.radioPrivacy -> "Account & Privacy"
                else -> "Other"
            }

            val user = FirebaseAuth.getInstance().currentUser
            val reportData = hashMapOf(
                "userId" to (user?.uid ?: "Anonymous"),
                "userEmail" to (user?.email ?: "Not provided"),
                "category" to category,
                "description" to description,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to Build.VERSION.RELEASE,
                "appVersion" to "1.0.0",
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            binding.btnSubmitReport.isEnabled = false
            FirebaseFirestore.getInstance().collection("reports")
                .add(reportData)
                .addOnSuccessListener {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), "Thank you! Your report has been submitted.", Toast.LENGTH_LONG).show()
                        dismiss()
                    }
                }
                .addOnFailureListener { e ->
                    if (_binding != null && isAdded) {
                        binding.btnSubmitReport.isEnabled = true
                        Toast.makeText(requireContext(), "Failed to submit report: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
