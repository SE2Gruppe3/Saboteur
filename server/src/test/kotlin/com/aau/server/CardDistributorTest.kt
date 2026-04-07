package com.aau.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CardDistributorTest {

    private fun playerIds(count: Int) = (1..count).map { "player_$it" }

    // --- Hand size per bracket ---

    @ParameterizedTest
    @ValueSource(ints = [3, 4, 5])
    fun `3 to 5 players each receive 6 cards`(playerCount: Int) {
        val result = CardDistributor.distribute(playerIds(playerCount))
        result.hands.values.forEach { assertEquals(6, it.size) }
    }

    @ParameterizedTest
    @ValueSource(ints = [6, 7])
    fun `6 to 7 players each receive 5 cards`(playerCount: Int) {
        val result = CardDistributor.distribute(playerIds(playerCount))
        result.hands.values.forEach { assertEquals(5, it.size) }
    }

    @ParameterizedTest
    @ValueSource(ints = [8, 9, 10])
    fun `8 to 10 players each receive 4 cards`(playerCount: Int) {
        val result = CardDistributor.distribute(playerIds(playerCount))
        result.hands.values.forEach { assertEquals(4, it.size) }
    }

    // --- Card conservation ---

    @ParameterizedTest
    @ValueSource(ints = [3, 4, 5, 6, 7, 8, 9, 10])
    fun `total card count is conserved (hands + draw pile = 40)`(playerCount: Int) {
        val result = CardDistributor.distribute(playerIds(playerCount))
        val handCardCount = result.hands.values.sumOf { it.size }
        assertEquals(40, handCardCount + result.drawPile.size)
    }

    @Test
    fun `all 40 tunnel card ids are present across hands and draw pile`() {
        val result = CardDistributor.distribute(playerIds(5))
        val allDealtIds = result.hands.values.flatten().map { it.id } + result.drawPile.map { it.id }
        val expectedIds = CardDeck.createTunnelDeck().map { it.id }.toSet()
        assertEquals(expectedIds, allDealtIds.toSet())
    }

    // --- Invalid player counts ---

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 11, 100])
    fun `invalid player count throws IllegalArgumentException`(playerCount: Int) {
        assertThrows<IllegalArgumentException> {
            CardDistributor.distribute(playerIds(playerCount))
        }
    }

    // --- Board cards ---

    @Test
    fun `goal cards are exactly 3 and all face-down`() {
        val result = CardDistributor.distribute(playerIds(4))
        assertEquals(3, result.goalCards.size)
        assertTrue(result.goalCards.none { it.isRevealed })
    }

    @Test
    fun `start card is revealed`() {
        val result = CardDistributor.distribute(playerIds(4))
        assertTrue(result.startCard.isRevealed)
    }

    // --- Shuffle ---

    @Test
    fun `repeated distributions produce different card orders`() {
        val ids = playerIds(5)
        val firstOrder = CardDistributor.distribute(ids).drawPile.map { it.id }
        val differentFound = (1..20).any {
            CardDistributor.distribute(ids).drawPile.map { it.id } != firstOrder
        }
        assertTrue(differentFound, "Expected shuffled deck to differ across multiple calls")
    }
}