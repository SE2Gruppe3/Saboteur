package com.aau.saboteur.auth.model

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.MapResult
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {

    @Test
    fun `ToolType serializes and deserializes via JSON`() {
        val json = Json
        val encoded = json.encodeToString(ToolType.LANTERN)
        val decoded = json.decodeFromString<ToolType>(encoded)

        assertEquals(ToolType.LANTERN, decoded)
    }

    @Test
    fun `MapResult serializes and deserializes via JSON`() {
        val json = Json
        val original = MapResult(
            position = BoardPosition(2, 10),
            card = TunnelCard(
                id = "goal1",
                type = CardType.GOAL,
                connections = emptySet(),
                isGoal = true,
                isRevealed = false
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MapResult>(encoded)

        assertEquals(original, decoded)
    }
}