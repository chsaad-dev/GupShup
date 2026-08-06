package com.example.gupshup.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gupshup.adapter.StatusViewerAdapter
import com.example.gupshup.databinding.BottomSheetStatusViewersBinding
import com.example.gupshup.model.StatusViewer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StatusViewersBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStatusViewersBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: StatusViewerAdapter
    private val viewerList = ArrayList<StatusViewer>()
    private var statusId: String = ""

    companion object {
        fun newInstance(statusId: String): StatusViewersBottomSheetFragment {
            val fragment = StatusViewersBottomSheetFragment()
            val args = Bundle()
            args.putString("STATUS_ID", statusId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusId = arguments?.getString("STATUS_ID") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStatusViewersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StatusViewerAdapter(viewerList)
        binding.viewersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.viewersRecyclerView.adapter = adapter

        loadViewers()
    }

    private fun loadViewers() {
        if (statusId.isEmpty()) return

        db.collection("status")
            .document(statusId)
            .collection("views")
            .orderBy("viewedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (_binding == null || error != null || snapshot == null) return@addSnapshotListener

                val count = snapshot.size()
                binding.viewersTitle.text = "Viewed by $count"

                if (count == 0) {
                    binding.emptyViewersText.visibility = View.VISIBLE
                    binding.viewersRecyclerView.visibility = View.GONE
                    return@addSnapshotListener
                }

                binding.emptyViewersText.visibility = View.GONE
                binding.viewersRecyclerView.visibility = View.VISIBLE

                val tempList = ArrayList<StatusViewer>()
                var pendingFetches = snapshot.documents.size

                snapshot.documents.forEach { doc ->
                    val viewerUid = doc.id
                    val viewedAt = doc.getLong("viewedAt") ?: 0L

                    db.collection("users").document(viewerUid).get()
                        .addOnSuccessListener { userDoc ->
                            val name = userDoc.getString("name") ?: "User"
                            val avatarUrl = userDoc.getString("profileImageUrl") ?: ""
                            tempList.add(StatusViewer(uid = viewerUid, name = name, avatarUrl = avatarUrl, viewedAt = viewedAt))

                            pendingFetches--
                            if (pendingFetches == 0) {
                                tempList.sortByDescending { it.viewedAt }
                                viewerList.clear()
                                viewerList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }
                        .addOnFailureListener {
                            pendingFetches--
                            if (pendingFetches == 0) {
                                tempList.sortByDescending { it.viewedAt }
                                viewerList.clear()
                                viewerList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
