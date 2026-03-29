package com.aau.se2game.activities

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], manifest = Config.NONE)
class LobbyActivityTest {
    @get:Rule
    val lobbyRule = createAndroidComposeRule<LobbyActivity>()

    @Test
    fun testLobbyActivityContent() {
        lobbyRule.onNodeWithText("Lobby").assertIsDisplayed()
        lobbyRule.onNodeWithText("Waiting for players...").assertIsDisplayed()
        lobbyRule.onNodeWithText("Start Game").assertIsDisplayed()
    }
}
