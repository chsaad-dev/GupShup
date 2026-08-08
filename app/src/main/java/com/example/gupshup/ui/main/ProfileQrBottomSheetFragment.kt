package com.example.gupshup.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.example.gupshup.databinding.BottomSheetProfileQrBinding
import com.example.gupshup.util.ImageLoaderUtil
import com.example.gupshup.util.QrCodeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.FileOutputStream

class ProfileQrBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetProfileQrBinding? = null
    private val binding get() = _binding!!

    private var uid: String = ""
    private var name: String = ""
    private var avatarUrl: String = ""
    private var qrBitmap: Bitmap? = null

    companion object {
        fun newInstance(uid: String, name: String, avatarUrl: String): ProfileQrBottomSheetFragment {
            val fragment = ProfileQrBottomSheetFragment()
            val args = Bundle().apply {
                putString("ARG_UID", uid)
                putString("ARG_NAME", name)
                putString("ARG_AVATAR", avatarUrl)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            uid = it.getString("ARG_UID", "")
            name = it.getString("ARG_NAME", "")
            avatarUrl = it.getString("ARG_AVATAR", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProfileQrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.qrUserName.text = name.ifEmpty { "GupShup User" }
        ImageLoaderUtil.loadAvatar(binding.qrUserAvatar, avatarUrl)

        val profileLink = "gupshup://profile/$uid"
        qrBitmap = QrCodeUtils.generateQrCode(profileLink, 600)
        qrBitmap?.let {
            binding.qrCodeImageView.setImageBitmap(it)
        }

        binding.shareQrButton.setOnClickListener {
            shareQrBitmap()
        }
    }

    private fun shareQrBitmap() {
        val bitmap = qrBitmap ?: return
        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "profile_qr_$uid.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Scan my GupShup QR code to connect with me! gupshup://profile/$uid")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
