package com.example.gupshup.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.gupshup.R

object CloudinaryManager {
    private const val TAG = "CloudinaryManager"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val cloudName = context.getString(R.string.cloudinary_cloud_name)
            val config = mapOf(
                "cloud_name" to cloudName,
                "secure" to true
            )
            MediaManager.init(context.applicationContext, config)
            isInitialized = true
            Log.d(TAG, "Cloudinary initialized with cloud_name: $cloudName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cloudinary", e)
        }
    }

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        folder: String = "gupshup/profiles",
        onSuccess: (url: String, publicId: String?) -> Unit,
        onError: (errorMsg: String) -> Unit
    ) {
        init(context)
        val uploadPreset = context.getString(R.string.cloudinary_upload_preset)

        try {
            MediaManager.get().upload(imageUri)
                .unsigned(uploadPreset)
                .option("folder", folder)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        Log.d(TAG, "Upload started: $requestId")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = if (totalBytes > 0) (bytes.toDouble() / totalBytes * 100).toInt() else 0
                        Log.d(TAG, "Upload progress: $progress%")
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                            ?: resultData["url"] as? String
                            ?: ""
                        val publicId = resultData["public_id"] as? String
                        Log.d(TAG, "Upload success: $url, publicId: $publicId")
                        onSuccess(url, publicId)
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e(TAG, "Upload error: ${error.description}")
                        onError(error.description ?: "Upload failed")
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        Log.w(TAG, "Upload rescheduled: ${error.description}")
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting upload", e)
            onError(e.localizedMessage ?: "Failed to upload image")
        }
    }
}
