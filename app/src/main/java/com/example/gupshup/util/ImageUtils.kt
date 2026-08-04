package com.example.gupshup.util

import android.content.Context
import android.widget.ImageView
import com.example.gupshup.R

object ImageUtils {

    fun loadProfileImage(
        context: Context,
        imageUrlOrBase64: String?,
        imageView: ImageView,
        updatedAt: Long? = 0L,
        placeholderRes: Int = R.drawable.ic_profile_placeholder
    ) {
        ImageLoaderUtil.loadAvatar(
            imageView = imageView,
            url = imageUrlOrBase64,
            updatedAt = updatedAt,
            placeholderRes = placeholderRes
        )
    }
}
