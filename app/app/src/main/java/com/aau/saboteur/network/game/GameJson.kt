package com.aau.saboteur.network.game

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.PlayerTurn
import org.json.JSONArray
import org.json.JSONObject

fun CreateGameRequest.toJson(): String {
    val playersJson = JSONArray().apply {
        players.forEach { player ->
            put(
                JSONObject()
                    .put("id", player.id)
                    .put("name", player.name)
            )
        }
    }

    return JSONObject()
        .put("players", playersJson)
        .toString()
}

fun String.toGameState(): GameState {
    val json = JSONObject(this)
    val playersJson = json.optJSONArray("players") ?: JSONArray()
    val currentPlayerId = json.optString("currentPlayerId").takeIf { it.isNotBlank() }
    val players = buildList {
        for (index in 0 until playersJson.length()) {
            val playerJson = playersJson.getJSONObject(index)
            val playerId = firstNonBlank(
                playerJson.optString("playerId"),
                playerJson.optString("player_id"),
                playerJson.optString("playerid")
            )
            val playerName = firstNonBlank(
                playerJson.optString("playerName"),
                playerJson.optString("player_name"),
                playerJson.optString("playername")
            )
            val turnOrder = when {
                playerJson.has("turnOrder") -> playerJson.optInt("turnOrder")
                playerJson.has("turn_order") -> playerJson.optInt("turn_order")
                playerJson.has("turnorder") -> playerJson.optInt("turnorder")
                else -> 0
            }

            add(
                PlayerTurn(
                    playerId = playerId,
                    playerName = playerName,
                    turnOrder = turnOrder
                )
            )
        }
    }

    return GameState(
        players = players,
        currentPlayerId = currentPlayerId
    )
}

private fun firstNonBlank(vararg values: String): String {
    return values.firstOrNull { it.isNotBlank() } ?: ""
}
