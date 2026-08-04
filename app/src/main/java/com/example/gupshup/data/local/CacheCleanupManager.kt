package com.example.gupshup.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CacheCleanupManager {
    private var hasRunSessionCleanup = false

    fun runSessionCleanup(context: Context) {
        if (hasRunSessionCleanup) return
        hasRunSessionCleanup = true

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appDb = AppDatabase.getInstance(appContext)
                val now = System.currentTimeMillis()
                
                // 1. Delete messages older than 7 days, excluding active chat
                val sevenDaysAgo = now - CacheConfig.MESSAGE_CLEANUP_THRESHOLD_MS
                appDb.messageDao().deleteOlderThanExcludingChat(
                    timestamp = sevenDaysAgo,
                    activeChatId = CacheConfig.activeChatId
                )

                // 2. Delete expired statuses
                appDb.statusDao().deleteExpired(cutoff = now)

                Log.d("CacheCleanupManager", "Session cache cleanup executed successfully")
            } catch (e: Exception) {
                Log.e("CacheCleanupManager", "Session cache cleanup failed", e)
            }
        }
    }
}
