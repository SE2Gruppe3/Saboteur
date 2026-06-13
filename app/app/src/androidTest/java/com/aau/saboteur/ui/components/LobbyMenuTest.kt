package com.aau.saboteur.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.ui.theme.SE2GameTheme
import com.aau.saboteur.util.LanguageManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LobbyMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setGermanAsDefault() {
        LanguageManager.SUPPORTED  // ensure object is initialized
    }

    private fun setContent() {
        composeTestRule.setContent {
            SE2GameTheme {
                LanguageSelector()
            }
        }
    }

    @Test
    fun languageSelectorShowsCurrentFlagInitially() {
        setContent()
        composeTestRule.onNodeWithText("🇩🇪").assertIsDisplayed()
    }

    @Test
    fun alternativeFlagHiddenBeforeClick() {
        setContent()
        composeTestRule.onNodeWithText("🇬🇧").assertIsNotDisplayed()
    }

    @Test
    fun clickCurrentFlagShowsAlternativeFlag() {
        setContent()
        composeTestRule.onNodeWithText("🇩🇪").performClick()
        composeTestRule.onNodeWithText("🇬🇧").assertIsDisplayed()
    }

    @Test
    fun clickCurrentFlagTogglesTogglesAlternativeVisibility() {
        setContent()
        composeTestRule.onNodeWithText("🇩🇪").performClick()
        composeTestRule.onNodeWithText("🇬🇧").assertIsDisplayed()
        composeTestRule.onNodeWithText("🇩🇪").performClick()
        composeTestRule.onNodeWithText("🇬🇧").assertIsNotDisplayed()
    }

    @Test
    fun selectingAlternativeFlagSwitchesLanguageAndHidesOption() {
        setContent()
        composeTestRule.onNodeWithText("🇩🇪").performClick()
        composeTestRule.onNodeWithText("🇬🇧").performClick()
        // After switching to English the EN flag becomes the current flag
        composeTestRule.onNodeWithText("🇬🇧").assertIsDisplayed()
        // The alternative (DE) should now be hidden again
        composeTestRule.onNodeWithText("🇩🇪").assertIsNotDisplayed()
    }
}
