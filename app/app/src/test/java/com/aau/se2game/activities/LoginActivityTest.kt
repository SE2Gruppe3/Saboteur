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
class LoginActivityTest {
    @get:Rule
    val loginRule = createAndroidComposeRule<LoginActivity>()

    @Test
    fun testLoginActivityContent() {
        loginRule.onNodeWithText("Login Screen").assertIsDisplayed()
        loginRule.onNodeWithText("Username").assertIsDisplayed()
        loginRule.onNodeWithText("Login").assertIsDisplayed()
    }
}
