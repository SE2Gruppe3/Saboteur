package com.aau.saboteur.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ConnectivityApi {
    suspend fun runConnectionTest(): String = withContext(Dispatchers.IO) {
        val connection = HttpClient.createConnection(NetworkConstants.pingEndpoint, "GET")

        try {
            val statusCode = connection.responseCode
            val bodyReader = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = bodyReader?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()

            if (statusCode in 200..299) {
                "Success: HTTP $statusCode\n$body"
            } else {
                "Failed: HTTP $statusCode\n$body"
            }
        } catch (exception: Exception) {
            "Connection error to ${NetworkConstants.baseUrl}: ${exception.message}"
        } finally {
            connection.disconnect()
        }
    }
}
