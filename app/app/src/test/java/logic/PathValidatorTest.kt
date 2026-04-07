package com.aau.saboteur.logic

import com.aau.saboteur.model.Card
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Position
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathValidatorTest {

    private lateinit var pathValidator: PathValidator
    private lateinit var gameState: GameState

    @Before
    fun setUp() {
        pathValidator = PathValidator()
        gameState = GameState(
            placedCards = emptyMap()
        )
    }

    @Test
    fun testCardPlaceableAtEmptyPosition() {
        val card = Card(
            id = "1",
            connections = setOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        )
        val position = Position(0, 0)

        val result = pathValidator.isCardPlaceable(card, position, gameState)
        assertTrue(result, "Karte sollte auf leerer Position platzierbar sein")
    }

    @Test
    fun testCardNotPlaceableAtOccupiedPosition() {
        val existingCard = Card(
            id = "existing",
            connections = setOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        )
        val position = Position(0, 0)
        gameState = gameState.copy(
            placedCards = mapOf(position to existingCard)
        )

        val newCard = Card(
            id = "new",
            connections = setOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        )

        val result = pathValidator.isCardPlaceable(newCard, position, gameState)
        assertFalse(result, "Karte sollte nicht auf besetzter Position platzierbar sein")
    }

    @Test
    fun testCardCompatibilityWithNeighbor() {
        // Karte mit Verbindung nach Norden
        val southCard = Card(
            id = "south",
            connections = setOf(Direction.NORTH)
        )

        // Karte mit Verbindung nach Süden
        val northCard = Card(
            id = "north",
            connections = setOf(Direction.SOUTH)
        )

        val result = pathValidator.areCardsCompatible(southCard, northCard, Direction.NORTH)
        assertTrue(result, "Karten mit kompatiblen Verbindungen sollten kompatibel sein")
    }

    @Test
    fun testCardIncompatibilityWithNeighbor() {
        // Karte ohne Verbindung nach Norden
        val southCard = Card(
            id = "south",
            connections = emptySet()
        )

        // Karte mit Verbindung nach Süden
        val northCard = Card(
            id = "north",
            connections = setOf(Direction.SOUTH)
        )

        val result = pathValidator.areCardsCompatible(southCard, northCard, Direction.NORTH)
        assertFalse(result, "Karten mit inkompatiblen Verbindungen sollten nicht kompatibel sein")
    }

    @Test
    fun testGameStateValidation() {
        val card1 = Card(
            id = "1",
            connections = setOf(Direction.EAST)
        )
        val card2 = Card(
            id = "2",
            connections = setOf(Direction.WEST)
        )

        gameState = gameState.copy(
            placedCards = mapOf(
                Position(0, 0) to card1,
                Position(1, 0) to card2
            )
        )

        val result = pathValidator.validateGameState(gameState)
        assertTrue(result, "Spielzustand mit kompatiblen Karten sollte valid sein")
    }

    @Test
    fun testInvalidPositionNotPlaceable() {
        val card = Card(
            id = "1",
            connections = setOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        )
        val invalidPosition = Position(-1, -1) // Ungültige Position

        val result = pathValidator.isCardPlaceable(card, invalidPosition, gameState)
        assertFalse(result, "Karte sollte nicht auf ungültiger Position platzierbar sein")
    }

    @Test
    fun testPredefinedCardTypes() {
        val straightH = Card.STRAIGHT_HORIZONTAL
        val corner = Card.CORNER_NE

        assertTrue(
            straightH.hasConnection(Direction.EAST) && straightH.hasConnection(Direction.WEST),
            "Horizontale Gerade sollte East und West haben"
        )

        assertTrue(
            corner.hasConnection(Direction.NORTH) && corner.hasConnection(Direction.EAST),
            "NE Corner sollte North und East haben"
        )
    }
}