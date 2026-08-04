package com.example.gupshup.data.local

object CacheConfig {
    /**
     * Staleness threshold for Friend list (HomeFragment) and Pending requests (FriendsFragment).
     * 5 minutes (300,000 ms).
     */
    const val FRIEND_CACHE_STALENESS_MS = 5 * 60 * 1000L

    /**
     * User profile online status staleness threshold (30 seconds).
     * Note: Profile online status is updated live via Firestore addSnapshotListener,
     * so this threshold only serves as a fallback check if the live listener temporarily disconnects.
     */
    const val PROFILE_ONLINE_STATUS_STALENESS_MS = 30 * 1000L

    /**
     * Message retention cleanup threshold (7 days).
     */
    const val MESSAGE_CLEANUP_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Active Chat ID currently opened in ChatActivity.
     * Prevents cleanup from deleting messages of the active chat session.
     */
    @Volatile
    var activeChatId: String? = null
}
