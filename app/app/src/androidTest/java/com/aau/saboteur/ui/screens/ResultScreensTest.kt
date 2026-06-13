package com.aau.saboteur.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.ui.theme.SE2GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockPlayers = listOf(
        PlayerTurn(playerId = "1", playerName = "Stefan"),
        PlayerTurn(playerId = "2", playerName = "Lukas"),
        PlayerTurn(playerId = "3", playerName = "Chris")
    )

    private val mockRoundResult = RoundResult(
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

    private val mockFinalResult = RoundResult(
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

    // ===== ROUND RESULT SCREEN TESTS =====

    @Test
    fun roundResultScreenDisplaysRoundNumber() {
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = mockRoundResult,
                    players = mockPlayers,
                    onNextRound = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Runde 1 beendet").assertExists()
    }

    @Test
    fun roundResultScreenDisplaysGolddiggerWinner() {
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = mockRoundResult,
                    players = mockPlayers,
                    onNextRound = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Goldsucher gewinnen").assertExists()
    }

    @Test
    fun roundResultScreenDisplaysAllPlayerNames() {
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = mockRoundResult,
                    players = mockPlayers,
                    onNextRound = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Stefan").assertExists()
        composeTestRule.onNodeWithText("Lukas").assertExists()
        composeTestRule.onNodeWithText("Chris").assertExists()
    }

    @Test
    fun roundResultScreenNextRoundButtonTriggersCallback() {
        var callbackTriggered = false
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = mockRoundResult,
                    players = mockPlayers,
                    onNextRound = { callbackTriggered = true }
                )
            }
        }
        composeTestRule.onNodeWithText("Weiter zur nächsten Runde").performClick()
        assert(callbackTriggered)
    }

    @Test
    fun roundResultScreenDisplaysSaboteurWinner() {
        val saboteurRoundResult = mockRoundResult.copy(winnerRole = Role.SABOTEUR)
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = saboteurRoundResult,
                    players = mockPlayers,
                    onNextRound = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Saboteure gewinnen").assertExists()
    }

    @Test
    fun roundResultScreenDisplaysRoleHeader() {
        composeTestRule.setContent {
            SE2GameTheme {
                RoundResultScreen(
                    roundResult = mockRoundResult,
                    players = mockPlayers,
                    onNextRound = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Rollen dieser Runde").assertExists()
    }

    // ===== FINAL RESULT SCREEN TESTS =====

    @Test
    fun finalResultScreenDisplaysGameEndedHeader() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Spiel beendet").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysGolddiggerWinner() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Goldsucher gewinnen").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysWinnerName() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Stefan").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysGoldCount() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("12").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysAllPlayerNames() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Stefan").assertExists()
        composeTestRule.onNodeWithText("Lukas").assertExists()
        composeTestRule.onNodeWithText("Chris").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysFinalScoresHeader() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Endgültiger Goldstand").assertExists()
    }

    @Test
    fun finalResultScreenBackToLobbyButtonTriggersCallback() {
        var backToLobbyClicked = false
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = { backToLobbyClicked = true }
                )
            }
        }
        // Hinweis: Falls euer Button im UI anders heißt (z.B. "Zurück zur Lobby"), passe den Text hier an
        composeTestRule.onNodeWithText("Zurück zur Lobby").performClick()
        assert(backToLobbyClicked)
    }

    @Test
    fun finalResultScreenDisplaysSaboteurWinner() {
        val saboteurFinalResult = mockFinalResult.copy(winnerRole = Role.SABOTEUR)
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = saboteurFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Saboteure gewinnen").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysLastRoleHeading() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Rollen der letzten Runde").assertExists()
    }

    @Test
    fun finalResultScreenDisplaysBackToLobbyButton() {
        composeTestRule.setContent {
            SE2GameTheme {
                FinalResultScreen(
                    roundResult = mockFinalResult,
                    players = mockPlayers,
                    onBackToLobby = {}
                )
            }
        }
        // Überprüft, ob der neue Haupt-Button im Screen existiert
        composeTestRule.onNodeWithText("Zurück zur Lobby").assertExists()
    }
}