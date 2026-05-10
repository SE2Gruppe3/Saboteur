package com.aau.saboteur.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SessionInfo should serialize and deserialize correctly`() {
        val player = Player(id = "p1", name = "TestPlayer")
        val sessionInfo = SessionInfo(
            sessionId = "ABCD",
            players = listOf(player),
            isStarted = true
        )

        val serialized = json.encodeToString(sessionInfo)
        val deserialized = json.decodeFromString<SessionInfo>(serialized)

        assertEquals(sessionInfo.sessionId, deserialized.sessionId)
        assertEquals(sessionInfo.players.size, deserialized.players.size)
        assertEquals(sessionInfo.players[0].name, deserialized.players[0].name)
        assertEquals(sessionInfo.isStarted, deserialized.isStarted)
    }

    @Test
    fun `ReconnectRequest should serialize and deserialize correctly`() {
        val request = ReconnectRequest(playerId = "p1", sessionId = "S1")
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<ReconnectRequest>(serialized)

        assertEquals(request.playerId, deserialized.playerId)
        assertEquals(request.sessionId, deserialized.sessionId)
    }
}
