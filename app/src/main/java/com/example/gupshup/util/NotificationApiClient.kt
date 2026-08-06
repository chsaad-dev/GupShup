package com.example.gupshup.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client helper to trigger push notifications via Cloudflare Worker endpoint
 */
object NotificationApiClient {

    private const val TAG = "NotificationApiClient"

    // Live Cloudflare Worker URL
    var workerBaseUrl: String = "https://gupshup-notifications.giveeaseapp.workers.dev"

    /**
     * Notify about a new message
     */
    suspend fun notifyMessage(chatId: String, messageId: String) {
        val payload = JSONObject().apply {
            put("chatId", chatId)
            put("messageId", messageId)
        }
        sendPostRequest("$workerBaseUrl/notify/message", payload.toString())
    }

    /**
     * Notify about a friend request (created or accepted)
     */
    suspend fun notifyFriendRequest(requestId: String) {
        val payload = JSONObject().apply {
            put("requestId", requestId)
        }
        sendPostRequest("$workerBaseUrl/notify/friend-request", payload.toString())
    }

    private suspend fun sendPostRequest(endpointUrl: String, jsonBody: String) {
        withContext(Dispatchers.IO) {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext

            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val idToken = result.token ?: return@addOnSuccessListener

                    Thread {
                        try {
                            val url = URL(endpointUrl)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.setRequestProperty("Authorization", "Bearer $idToken")
                            conn.doOutput = true
                            conn.connectTimeout = 10000
                            conn.readTimeout = 10000

                            OutputStreamWriter(conn.outputStream).use { writer ->
                                writer.write(jsonBody)
                                writer.flush()
                            }

                            val responseCode = conn.responseCode
                            Log.d(TAG, "Notification API [$endpointUrl] responded with code: $responseCode")
                            conn.disconnect()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send notification request to $endpointUrl: ${e.message}")
                        }
                    }.start()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to obtain Firebase ID token for notification request: ${e.message}")
                }
        }
    }
}
