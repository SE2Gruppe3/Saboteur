package com.aau.se2game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.se2game.screens.ConnectivityTestScreen
import com.aau.se2game.ui.theme.SE2GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityTestScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testConnectivityFlow() {
        // Navigate to screen
        composeTestRule.onNodeWithTag("nav_btn_connectivity").performClick()

        // Verify initial state
        composeTestRule.onNodeWithText("Backend Connection Test").assertIsDisplayed()

        // Click the test button
        composeTestRule.onNodeWithText("Run Connection Test").performClick()

        // Wait for a final result (Success, Failed, or Error)
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Success", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Failed", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Connection error", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
