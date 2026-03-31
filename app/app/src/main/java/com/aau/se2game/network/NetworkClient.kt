package com.aau.se2game.network

import com.aau.se2game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

object NetworkConstants {
    var baseUrl = BuildConfig.BASE_URL
    val pingEndpoint: String
        get() = "$baseUrl/api/ping"
}

object NetworkClient {
    suspend fun runConnectionTest(): String = withContext(Dispatchers.IO) {
        val connection = (URI.create(NetworkConstants.pingEndpoint).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }

        try {
            val statusCode = connection.responseCode
            val bodyReader = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = bodyReader?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()

            if (statusCode in 200..299) {
                "Success: HTTP $statusCode\n$body"
            } else {
                "Failed: HTTP $statusCode\n$body"
            }
        } catch (exception: Exception) {
            "Connection error: ${exception.message}"
        } finally {
            connection.disconnect()
        }
    }
}
