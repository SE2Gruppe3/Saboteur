package com.aau.se2game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.se2game.activities.GameActivity
import com.aau.se2game.activities.LoadingActivity
import com.aau.se2game.activities.LobbyActivity
import com.aau.se2game.activities.LoginActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<LoadingActivity>()

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testNavigationToLogin() {
        openMenu()
        composeTestRule.onNodeWithText("Login").performClick()
        intended(hasComponent(LoginActivity::class.java.name))
    }

    @Test
    fun testNavigationToLobby() {
        openMenu()
        composeTestRule.onNodeWithText("Lobby").performClick()
        intended(hasComponent(LobbyActivity::class.java.name))
    }

    @Test
    fun testNavigationToGame() {
        openMenu()
        composeTestRule.onNodeWithText("Game").performClick()
        intended(hasComponent(GameActivity::class.java.name))
    }

    @Test
    fun testNavigationToLoading() {
        openMenu()
        composeTestRule.onNodeWithText("Loading").performClick()
    }

    private fun openMenu() {
        composeTestRule.onNodeWithContentDescription("Navigation Menu").performClick()
        composeTestRule.onNodeWithText("Navigate to:").assertIsDisplayed()
    }
}
