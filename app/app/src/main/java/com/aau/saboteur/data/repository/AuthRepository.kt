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
            val requestBodyJson = """
                {
                    "username": "$username",
                    "password": "${password ?: ""}"
                }
            """.trimIndent().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("http://10.0.2.2:8080/api/auth/login")
                .post(requestBodyJson)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val user = json.decodeFromString<User>(bodyString)
                    Result.success(user)
                } else {
                    // Hier lesen wir jetzt die Nachricht aus, die wir im Controller
                    // mit .body("Nachricht") definiert haben (z.B. "Passwort falsch")
                    val errorMessage = if (bodyString.isNotBlank()) bodyString else "Fehler: ${response.code}"
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}