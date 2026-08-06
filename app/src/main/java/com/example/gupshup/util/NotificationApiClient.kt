package com.example.gupshup.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkConfig {
    const val WORKER_BASE_URL: String = "https://gupshup-notifications.giveeaseapp.workers.dev"
}

/**
 * Client helper to trigger push notifications via Cloudflare Worker endpoints.
 * Fire-and-forget, non-blocking to UI.
 * Handles Firebase Auth ID token caching & 401 token force-refresh retries.
 */
@OptIn(DelicateCoroutinesApi::class)
object NotificationApiClient {

    private const val TAG = "NotificationApiClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Notify Cloudflare Worker about a new chat message.
     */
    fun notifyMessage(chatId: String, messageId: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("chatId", chatId)
                put("messageId", messageId)
            }.toString()

            executeWorkerPost("${NetworkConfig.WORKER_BASE_URL}/notify/message", json)
        }
    }

    /**
     * Notify Cloudflare Worker about a friend request (created or accepted).
     */
    fun notifyFriendRequest(requestId: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("requestId", requestId)
            }.toString()

            executeWorkerPost("${NetworkConfig.WORKER_BASE_URL}/notify/friend-request", json)
        }
    }

    private suspend fun fetchIdToken(forceRefresh: Boolean): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            val result = user.getIdToken(forceRefresh).await()
            result.token
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Firebase ID token (forceRefresh=$forceRefresh): ${e.message}")
            null
        }
    }

    private suspend fun executeWorkerPost(endpointUrl: String, jsonBody: String) {
        try {
            // Step 1: Use cached Firebase ID token (forceRefresh = false)
            var idToken = fetchIdToken(forceRefresh = false)
            if (idToken == null) {
                Log.w(TAG, "No authenticated user or ID token available. Skipping notification request.")
                return
            }

            var responseCode = performHttpCall(endpointUrl, jsonBody, idToken)

            // Step 2: If 401 Unauthorized, force refresh token once and retry
            if (responseCode == 401) {
                Log.w(TAG, "Worker returned 401 Unauthorized. Force refreshing ID token and retrying once...")
                idToken = fetchIdToken(forceRefresh = true)
                if (idToken != null) {
                    responseCode = performHttpCall(endpointUrl, jsonBody, idToken)
                }
            }

            Log.d(TAG, "Worker endpoint [$endpointUrl] completed with status code: $responseCode")
        } catch (e: Exception) {
            Log.w(TAG, "Exception during notification request to $endpointUrl: ${e.message}")
        }
    }

    private fun performHttpCall(url: String, jsonBody: String, idToken: String): Int {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Worker response [${response.code}]: $bodyStr")
                response.code
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP execution error: ${e.message}")
            -1
        }
    }
}
