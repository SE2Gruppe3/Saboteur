package com.aau.se2game.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.se2game.ui.theme.SE2GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], manifest = Config.NONE)
class NavigationMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun navigationMenu_initialState_showsOpenButton() {
        composeTestRule.setContent {
            SE2GameTheme {
                NavigationMenu()
            }
        }

        composeTestRule.onNodeWithContentDescription("Open Navigation Menu").assertIsDisplayed()
        composeTestRule.onNodeWithText("NAVIGATE TO").assertDoesNotExist()
    }

    @Test
    fun navigationMenu_clickOpen_showsOverlay() {
        composeTestRule.setContent {
            SE2GameTheme {
                NavigationMenu()
            }
        }

        composeTestRule.onNodeWithContentDescription("Open Navigation Menu").performClick()
        
        composeTestRule.onNodeWithText("NAVIGATE TO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithText("Game").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close Navigation Menu").assertIsDisplayed()
    }

    @Test
    fun navigationMenu_clickClose_hidesOverlay() {
        composeTestRule.setContent {
            SE2GameTheme {
                NavigationMenu()
            }
        }

        // Open menu
        composeTestRule.onNodeWithContentDescription("Open Navigation Menu").performClick()
        
        // Close menu
        composeTestRule.onNodeWithContentDescription("Close Navigation Menu").performClick()

        composeTestRule.onNodeWithText("NAVIGATE TO").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Open Navigation Menu").assertIsDisplayed()
    }
}
