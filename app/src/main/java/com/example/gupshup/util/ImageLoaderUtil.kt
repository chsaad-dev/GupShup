package com.example.gupshup.util

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.signature.ObjectKey
import com.example.gupshup.R

object ImageLoaderUtil {

    private val DEFAULT_AVATAR_PLACEHOLDER = R.drawable.ic_profile_placeholder
    private val DEFAULT_MEDIA_PLACEHOLDER = R.drawable.rounded_image_bg

    /**
     * Loads a user profile avatar into an ImageView using Glide.
     * Uses [updatedAt] combined with [url] as a custom signature cache key so the cache
     * busts automatically whenever the user updates their profile photo.
     */
    fun loadAvatar(
        imageView: ImageView,
        url: String?,
        updatedAt: Long? = 0L,
        placeholderRes: Int = DEFAULT_AVATAR_PLACEHOLDER
    ) {
        loadImage(
            imageView = imageView,
            url = url,
            updatedAt = updatedAt,
            placeholderRes = placeholderRes,
            isAvatar = true
        )
    }

    /**
     * Loads a chat message image into an ImageView.
     */
    fun loadChatImage(
        imageView: ImageView,
        url: String?,
        placeholderRes: Int = DEFAULT_MEDIA_PLACEHOLDER
    ) {
        loadImage(
            imageView = imageView,
            url = url,
            updatedAt = 0L,
            placeholderRes = placeholderRes,
            isAvatar = false
        )
    }

    /**
     * Loads status story photo or media into an ImageView.
     */
    fun loadStatusMedia(
        imageView: ImageView,
        url: String?,
        placeholderRes: Int = DEFAULT_MEDIA_PLACEHOLDER
    ) {
        loadImage(
            imageView = imageView,
            url = url,
            updatedAt = 0L,
            placeholderRes = placeholderRes,
            isAvatar = false
        )
    }

    private fun loadImage(
        imageView: ImageView,
        url: String?,
        updatedAt: Long?,
        placeholderRes: Int,
        isAvatar: Boolean
    ) {
        val context = imageView.context ?: return

        if (url.isNullOrBlank()) {
            imageView.setImageResource(placeholderRes)
            return
        }

        val trimmed = url.trim()


        val cacheKeyString = if (updatedAt != null && updatedAt > 0L) {
            "${trimmed}_$updatedAt"
        } else {
            trimmed
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Glide.with(context)
                .load(trimmed)
                .signature(ObjectKey(cacheKeyString))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        } else if (trimmed.startsWith("data:image")) {
            Glide.with(context)
                .load(trimmed)
                .signature(ObjectKey(cacheKeyString))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        } else {
            // Raw Base64 string handling
            try {
                val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    Glide.with(context)
                        .load(bitmap)
                        .signature(ObjectKey(cacheKeyString))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .transition(DrawableTransitionOptions.withCrossFade())
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
