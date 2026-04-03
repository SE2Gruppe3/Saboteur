package com.aau.saboteur.network.game

import com.aau.saboteur.network.HttpClient
import com.aau.saboteur.network.NetworkConstants
import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object GameApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchGameState(): Result<GameState> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(NetworkConstants.gameStateEndpoint)
            .get()
            .build()

        try {
            HttpClient.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Request failed with HTTP ${response.code}: $body"))
                }

                if (body.isBlank()) {
                    return@withContext Result.success(GameState(players = emptyList(), currentPlayerId = null))
                }

                Result.success(body.toGameState())
            }
        } catch (exception: IOException) {
            Result.failure(IllegalStateException("Could not parse or reach ${NetworkConstants.gameStateEndpoint}: ${exception.message}", exception))
        }
    }

    suspend fun startGame(players: List<Player>): Result<GameState> = withContext(Dispatchers.IO) {
        val requestBody = CreateGameRequest(players = players).toJson().toRequestBody(jsonMediaType)
        
        val request = Request.Builder()
            .url(NetworkConstants.startGameEndpoint)
            .post(requestBody)
            .header("Accept", "application/json")
            .build()

        try {
            HttpClient.okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Request failed with HTTP ${response.code}: $responseBody"))
                }

                if (responseBody.isBlank()) {
                    return@withContext fetchGameState()
                }

                Result.success(responseBody.toGameState())
            }
        } catch (exception: IOException) {
            Result.failure(IllegalStateException("Could not reach ${NetworkConstants.startGameEndpoint}: ${exception.message}", exception))
        }
    }
}
