package com.aau.saboteur.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tests for card counts and composition of the generated decks
class CardDeckTest {

    // PATH (31) + DEAD_END (9) = 40 tunnel cards total from the provided spec.
    private val EXPECTED_TUNNEL_DECK_SIZE = 40

    @Test
    fun `createTunnelDeck returns correct total count`() {
        assertEquals(EXPECTED_TUNNEL_DECK_SIZE, CardDeck.createTunnelDeck().size)
    }

    @Test
    fun `createTunnelDeck contains only PATH and DEAD_END cards`() {
        val deck = CardDeck.createTunnelDeck()
        assertTrue(deck.all { it.type == CardType.PATH || it.type == CardType.DEAD_END })
    }

    @Test
    fun `createTunnelDeck PATH cards count is 31`() {
        val pathCount = CardDeck.createTunnelDeck().count { it.type == CardType.PATH }
        assertEquals(31, pathCount)
    }

    @Test
    fun `createTunnelDeck DEAD_END cards count is 9`() {
        val deadEndCount = CardDeck.createTunnelDeck().count { it.type == CardType.DEAD_END }
        assertEquals(9, deadEndCount)
    }

    @Test
    fun `createTunnelDeck all cards have unique ids`() {
        val deck = CardDeck.createTunnelDeck()
        assertEquals(deck.size, deck.map { it.id }.toSet().size)
    }

    @Test
    fun `createGoalCards returns exactly 3 cards`() {
        assertEquals(3, CardDeck.createGoalCards().size)
    }

    @Test
    fun `createGoalCards contains exactly one gold goal`() {
        val goalCount = CardDeck.createGoalCards().count { it.isGoal }
        assertEquals(1, goalCount)
    }

    @Test
    fun `createGoalCards gold card has id goal_gold`() {
        val gold = CardDeck.createGoalCards().single { it.isGoal }
        assertEquals("goal_gold", gold.id)
    }

    @Test
    fun `createGoalCards all cards are unrevealed`() {
        assertTrue(CardDeck.createGoalCards().none { it.isRevealed })
    }

    @Test
    fun `createGoalCards all cards have type GOAL`() {
        assertTrue(CardDeck.createGoalCards().all { it.type == CardType.GOAL })
    }

    @Test
    fun `createStartCard has id start and is revealed`() {
        val start = CardDeck.createStartCard()
        assertEquals("start", start.id)
        assertTrue(start.isRevealed)
        assertEquals(CardType.START, start.type)
    }

    @Test
    fun `shuffled returns same size as input`() {
        val deck = CardDeck.createTunnelDeck()
        assertEquals(deck.size, CardDeck.shuffled(deck).size)
    }

    @Test
    fun `shuffled returns all original cards`() {
        val deck = CardDeck.createTunnelDeck()
        assertEquals(deck.map { it.id }.toSet(), CardDeck.shuffled(deck).map { it.id }.toSet())
    }
}
