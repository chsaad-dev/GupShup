package com.example.gupshup.util

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.gupshup.R

object ImageUtils {

    fun loadProfileImage(
        context: Context,
        imageUrlOrBase64: String?,
        imageView: ImageView,
        placeholderRes: Int = R.drawable.ic_profile_placeholder
    ) {
        if (imageUrlOrBase64.isNullOrBlank()) {
            imageView.setImageResource(placeholderRes)
            return
        }

        val trimmed = imageUrlOrBase64.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // Standard URL (Cloudinary, Google photo, etc.)
            Glide.with(context)
                .load(trimmed)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(imageView)
        } else if (trimmed.startsWith("data:image")) {
            // Data URI formatted Base64
            Glide.with(context)
                .load(trimmed)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(imageView)
        } else {
            // Raw Base64 string fallback
            try {
                val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    Glide.with(context)
                        .load(bitmap)
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .into(imageView)
                } else {
                    imageView.setImageResource(placeholderRes)
                }
            } catch (e: Exception) {
                imageView.setImageResource(placeholderRes)
            }
        }
    }
}
