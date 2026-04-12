package com.aau.saboteur.data.repository

import com.aau.saboteur.model.User
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loginUser(username: String, password: String?): Result<User> = withContext(Dispatchers.IO) {
        try {
            val requestBody = """{"username": "$username"}"""
                .toRequestBody("application/json".toMediaType())


            val request = Request.Builder()
                .url("http://10.0.2.2:8080/api/login")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val user = json.decodeFromString<User>(body)
                    Result.success(user)
                } else {
                    Result.failure(Exception("Login failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}