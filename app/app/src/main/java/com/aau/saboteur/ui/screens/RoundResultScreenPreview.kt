package com.aau.saboteur.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.ui.theme.SE2GameTheme

@Preview(showBackground = true, widthDp = 1080, heightDp = 1920)
@Composable
fun RoundResultScreenPreview() {
    val mockPlayers = listOf(
        PlayerTurn(playerId = "1", playerName = "Stefan"),
        PlayerTurn(playerId = "2", playerName = "Lukas"),
        PlayerTurn(playerId = "3", playerName = "Chris")
    )

    val mockRoundResult = RoundResult(
        roundNumber = 1,
        winnerRole = Role.GOLDDIGGER,
        winningPlayerIds = listOf("1", "3"),
        revealedRoles = mapOf(
            "1" to Role.GOLDDIGGER,
            "2" to Role.SABOTEUR,
            "3" to Role.GOLDDIGGER
        ),
        playerGoldTotals = mapOf(
            "1" to 5,
            "2" to 0,
            "3" to 3
        )
    )

    SE2GameTheme {
        RoundResultScreen(
            roundResult = mockRoundResult,
            players = mockPlayers,
            onNextRound = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1080, heightDp = 1920)
@Composable
fun FinalResultScreenPreview() {
    val mockPlayers = listOf(
        PlayerTurn(playerId = "1", playerName = "Stefan"),
        PlayerTurn(playerId = "2", playerName = "Lukas"),
        PlayerTurn(playerId = "3", playerName = "Chris")
    )

    val mockRoundResult = RoundResult(
        roundNumber = 3,
        winnerRole = Role.GOLDDIGGER,
        winningPlayerIds = listOf("1", "3"),
        revealedRoles = mapOf(
            "1" to Role.GOLDDIGGER,
            "2" to Role.SABOTEUR,
            "3" to Role.GOLDDIGGER
        ),
        playerGoldTotals = mapOf(
            "1" to 12,
            "2" to 5,
            "3" to 10
        )
    )

    SE2GameTheme {
        FinalResultScreen(
            roundResult = mockRoundResult,
            players = mockPlayers,
            onBackToLobby = {}
        )
    }
}