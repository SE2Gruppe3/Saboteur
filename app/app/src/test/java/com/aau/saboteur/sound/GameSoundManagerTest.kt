package com.aau.saboteur.sound

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSoundManagerTest {

    @Test
    fun `detectGameSoundEffects returns tunnel dig when path card is added`() {
        val previous = GameState(boardPlacements = emptyList())
        val current = GameState(
            boardPlacements = listOf(placement(CardType.PATH))
        )

        assertEquals(
            listOf(GameSoundEffect.TunnelDig),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects returns tunnel dig when dead end card is added`() {
        val previous = GameState(boardPlacements = emptyList())
        val current = GameState(
            boardPlacements = listOf(placement(CardType.DEAD_END))
        )

        assertEquals(
            listOf(GameSoundEffect.TunnelDig),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects ignores newly added non tunnel cards`() {
        val previous = GameState(boardPlacements = emptyList())
        val current = GameState(
            boardPlacements = listOf(placement(CardType.GOAL))
        )

        assertTrue(detectGameSoundEffects(previous, current).isEmpty())
    }

    @Test
    fun `detectGameSoundEffects returns explosion when a placement is removed`() {
        val previous = GameState(
            boardPlacements = listOf(placement(CardType.PATH))
        )
        val current = GameState(boardPlacements = emptyList())

        assertEquals(
            listOf(GameSoundEffect.Explosion),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects returns goal flip when gold goal is revealed`() {
        val position = BoardPosition(0, 0)
        val previous = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = false, isGoal = true))
        )
        val current = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = true, isGoal = true))
        )

        assertEquals(
            listOf(GameSoundEffect.GoalFlip),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects returns coal flip when stone goal is revealed`() {
        val position = BoardPosition(0, 0)
        val previous = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = false, isGoal = false))
        )
        val current = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = true, isGoal = false))
        )

        assertEquals(
            listOf(GameSoundEffect.CoalFlip),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects ignores unrevealed goal cards that stay unrevealed`() {
        val position = BoardPosition(0, 0)
        val previous = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = false, isGoal = true))
        )
        val current = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = false, isGoal = true))
        )

        assertTrue(detectGameSoundEffects(previous, current).isEmpty())
    }

    @Test
    fun `detectGameSoundEffects ignores existing non-goal placements at same position`() {
        val previous = GameState(
            boardPlacements = listOf(placement(CardType.PATH))
        )
        val current = GameState(
            boardPlacements = listOf(placement(CardType.PATH))
        )

        assertTrue(detectGameSoundEffects(previous, current).isEmpty())
    }

    @Test
    fun `detectGameSoundEffects ignores already revealed goal cards`() {
        val position = BoardPosition(0, 0)
        val previous = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = true, isGoal = true))
        )
        val current = GameState(
            boardPlacements = listOf(goalPlacement(position, isRevealed = true, isGoal = true))
        )

        assertTrue(detectGameSoundEffects(previous, current).isEmpty())
    }

    @Test
    fun `detectGameSoundEffects returns specific break sounds for newly blocked tools`() {
        val previous = GameState(
            players = listOf(player(blockedTools = emptySet()))
        )
        val current = GameState(
            players = listOf(
                player(
                    blockedTools = setOf(
                        ToolType.LANTERN,
                        ToolType.PICKAXE,
                        ToolType.CART
                    )
                )
            )
        )

        assertEquals(
            listOf(
                GameSoundEffect.LanternBreak,
                GameSoundEffect.PickaxeBreak,
                GameSoundEffect.CartBreak
            ),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects returns repair sound when a blocked tool is removed`() {
        val previous = GameState(
            players = listOf(player(blockedTools = setOf(ToolType.LANTERN)))
        )
        val current = GameState(
            players = listOf(player(blockedTools = emptySet()))
        )

        assertEquals(
            listOf(GameSoundEffect.ToolRepair),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects returns one repair sound when multiple tools are repaired together`() {
        val previous = GameState(
            players = listOf(player(blockedTools = setOf(ToolType.LANTERN, ToolType.CART)))
        )
        val current = GameState(
            players = listOf(player(blockedTools = emptySet()))
        )

        assertEquals(
            listOf(GameSoundEffect.ToolRepair),
            detectGameSoundEffects(previous, current)
        )
    }

    @Test
    fun `detectGameSoundEffects ignores new players without previous state`() {
        val previous = GameState(players = emptyList())
        val current = GameState(
            players = listOf(player(blockedTools = setOf(ToolType.LANTERN)))
        )

        assertTrue(detectGameSoundEffects(previous, current).isEmpty())
    }

    private fun placement(type: CardType): PlacedTunnelCard {
        return PlacedTunnelCard(
            position = BoardPosition(1, 2),
            card = TunnelCard(
                id = "$type-1-2",
                type = type,
                connections = setOf(Direction.TOP, Direction.BOTTOM)
            )
        )
    }

    private fun goalPlacement(
        position: BoardPosition,
        isRevealed: Boolean,
        isGoal: Boolean
    ): PlacedTunnelCard {
        return PlacedTunnelCard(
            position = position,
            card = TunnelCard(
                id = "goal-${position.row}-${position.column}",
                type = CardType.GOAL,
                connections = emptySet(),
                isRevealed = isRevealed,
                isGoal = isGoal
            )
        )
    }

    private fun player(
        blockedTools: Set<ToolType>
    ): PlayerTurn {
        return PlayerTurn(
            playerId = "P1",
            playerName = "Lukas",
            blockedTools = blockedTools
        )
    }
}
