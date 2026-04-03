package com.aau.saboteur.network

import java.net.HttpURLConnection
import java.net.URI

object HttpClient {
    fun createConnection(endpoint: String, method: String): HttpURLConnection {
        return (URI.create(endpoint).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
        }
    }
}
