package com.aau.saboteur.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

object ConnectivityApi {
    suspend fun runConnectionTest(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(NetworkConstants.pingEndpoint)
            .build()

        try {
            HttpClient.okHttpClient.newCall(request).execute().use { response ->
                val statusCode = response.code
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    "Success: HTTP $statusCode\n$body"
                } else {
                    "Failed: HTTP $statusCode\n$body"
                }
            }
        } catch (exception: IOException) {
            "Connection error to ${NetworkConstants.baseUrl}: ${exception.message}"
        }
    }
}
