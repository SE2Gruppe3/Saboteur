package com.aau.se2game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testInitialScreenIsMenu() {
        composeTestRule.onNodeWithText("Navigation Menu").assertIsDisplayed()
    }

    @Test
    fun testNavigateToLogin() {
        composeTestRule.onNodeWithTag("nav_btn_login").performClick()
        composeTestRule.onNodeWithText("Login Screen").assertIsDisplayed()
    }

    @Test
    fun testNavigateToLobby() {
        composeTestRule.onNodeWithTag("nav_btn_lobby").performClick()
        composeTestRule.onNodeWithText("Lobby Screen").assertIsDisplayed()
    }

    @Test
    fun testNavigateToGame() {
        composeTestRule.onNodeWithTag("nav_btn_game").performClick()
        composeTestRule.onNodeWithText("Game Screen").assertIsDisplayed()
    }

    @Test
    fun testNavigateToConnectivity() {
        composeTestRule.onNodeWithTag("nav_btn_connectivity").performClick()
        composeTestRule.onNodeWithText("Backend Connection Test").assertIsDisplayed()
    }
}
