package com.aau.saboteur.mockeddata

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.ui.model.BoardPlacement
import com.aau.saboteur.ui.model.BoardPosition

val mockPlayers = listOf(
    Player(id = "1", name = "Alice"),
    Player(id = "2", name = "Bob"),
    Player(id = "3", name = "Charlie"),
    Player(id = "4", name = "Diana"),
    Player(id = "5", name = "Ethan"),
    Player(id = "6", name = "Fiona"),
    Player(id = "7", name = "George"),
    Player(id = "8", name = "Hannah"),
    Player(id = "9", name = "Isaac"),
    Player(id = "10", name = "Julia")
)

val boardStartPosition = BoardPosition(row = 10, column = 4)

val mockBoardPlacements = listOf(
    BoardPlacement(
        position = BoardPosition(row = 0, column = 2),
        card = TunnelCard(
            id = "goal_stone_1",
            type = CardType.GOAL,
            connections = setOf(Direction.TOP, Direction.RIGHT),
            isRevealed = false
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 0, column = 4),
        card = TunnelCard(
            id = "goal_gold",
            type = CardType.GOAL,
            connections = setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM),
            isRevealed = false,
            isGoal = true
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 0, column = 6),
        card = TunnelCard(
            id = "goal_stone_2",
            type = CardType.GOAL,
            connections = setOf(Direction.BOTTOM, Direction.RIGHT),
            isRevealed = false
        )
    ),
    BoardPlacement(
        position = boardStartPosition,
        card = TunnelCard(
            id = "start",
            type = CardType.START,
            connections = setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM),
            isRevealed = true
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 9, column = 4),
        card = TunnelCard(
            id = "path_tb_preview_1",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.BOTTOM)
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 8, column = 4),
        card = TunnelCard(
            id = "path_tlb_preview_1",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM)
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 8, column = 3),
        card = TunnelCard(
            id = "path_tr_preview_1",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.RIGHT)
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 7, column = 3),
        card = TunnelCard(
            id = "path_tb_preview_2",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.BOTTOM)
        )
    ),
    BoardPlacement(
        position = BoardPosition(row = 6, column = 3),
        card = TunnelCard(
            id = "path_tlr_preview_1",
            type = CardType.PATH,
            connections = setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT)
        )
    )
)
