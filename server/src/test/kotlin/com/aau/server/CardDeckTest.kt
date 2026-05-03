package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.server.game.CardDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardDeckTest {

    // Adjusted: Special cards are included, so the total deck size is 51 now
    private val EXPECTED_TUNNEL_DECK_SIZE = 51

    @Test
    fun `createTunnelDeck returns correct total count`() {
        assertEquals(EXPECTED_TUNNEL_DECK_SIZE, CardDeck.createTunnelDeck().size)
    }

    @Test
    fun `createTunnelDeck contains only allowed card types`() {
        // Adjusted: Allow all card types now present in the deck, including special cards
        val allowedTypes = setOf(
            CardType.PATH,
            CardType.DEAD_END,
            CardType.CART_RED,
            CardType.CART_GREEN,
            CardType.LANTERN_RED,
            CardType.LANTERN_GREEN,
            CardType.PICKAXE_RED,
            CardType.PICKAXE_GREEN,
            CardType.ROCKFALL,
            CardType.MAPCARD,
            CardType.DOUBLE_LANTERN_CART,
            CardType.DOUBLE_PICKAXE_CART,
            CardType.DOUBLE_PICKAXE_LANTERN
        )
        val deck = CardDeck.createTunnelDeck()
        assertTrue(deck.all { it.type in allowedTypes })
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