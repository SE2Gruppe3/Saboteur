package com.aau.saboteur.network.game

import com.aau.saboteur.network.HttpClient
import com.aau.saboteur.network.NetworkConstants
import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object GameApi {
    suspend fun fetchGameState(): Result<GameState> = withContext(Dispatchers.IO) {
        val connection = HttpClient.createConnection(NetworkConstants.gameStateEndpoint, "GET")

        try {
            val statusCode = connection.responseCode
            val bodyReader = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = bodyReader?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()

            if (statusCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Request failed with HTTP $statusCode: $body"))
            }

            if (body.isBlank()) {
                return@withContext Result.success(GameState(players = emptyList(), currentPlayerId = null))
            }

            Result.success(body.toGameState())
        } catch (exception: Exception) {
            Result.failure(IllegalStateException("Could not parse or reach ${NetworkConstants.gameStateEndpoint}: ${exception.message}", exception))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun startGame(players: List<Player>): Result<GameState> = withContext(Dispatchers.IO) {
        val connection = HttpClient.createConnection(NetworkConstants.startGameEndpoint, "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val request = CreateGameRequest(players = players)
            val body = request.toJson()
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val bodyReader = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = bodyReader?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()

            if (statusCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Request failed with HTTP $statusCode: $responseBody"))
            }

            if (responseBody.isBlank()) {
                return@withContext fetchGameState()
            }

            Result.success(responseBody.toGameState())
        } catch (exception: Exception) {
            Result.failure(IllegalStateException("Could not reach ${NetworkConstants.startGameEndpoint}: ${exception.message}", exception))
        } finally {
            connection.disconnect()
        }
    }
}
