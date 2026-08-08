package com.example.gupshup.ui.chat

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.gupshup.databinding.ActivityImagePreviewBinding
import com.example.gupshup.util.ImageLoaderUtil
import com.example.gupshup.util.finishWithFade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private var imageUrl: String = ""

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_TITLE = "extra_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Photo"

        binding.textTitle.text = title

        if (imageUrl.isNotEmpty()) {
            ImageLoaderUtil.loadChatImage(binding.photoView, imageUrl)
        }

        binding.btnBack.setOnClickListener {
            finishWithFade()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithFade()
            }
        })

        binding.btnDownload.setOnClickListener {
            saveImageToGallery()
        }
    }

    private fun saveImageToGallery() {
        if (imageUrl.isEmpty()) {
            Toast.makeText(this, "Image URL invalid", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Saving photo...", Toast.LENGTH_SHORT).show()

        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val saved = saveBitmapToMediaStore(resource)
                        withContext(Dispatchers.Main) {
                            if (saved) {
                                Toast.makeText(this@ImagePreviewActivity, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ImagePreviewActivity, "Failed to save photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Toast.makeText(this@ImagePreviewActivity, "Failed to load photo for saving", Toast.LENGTH_SHORT).show()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Boolean {
        val filename = "GupShup_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GupShup")
                }
                val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = contentResolver.openOutputStream(uri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/GupShup"
                val file = File(imagesDir)
                if (!file.exists()) {
                    file.mkdirs()
                }
                val imageFile = File(imagesDir, filename)
                outputStream = FileOutputStream(imageFile)
            }

            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
