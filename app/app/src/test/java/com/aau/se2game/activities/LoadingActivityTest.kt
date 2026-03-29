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
class LoadingActivityTest {

    @get:Rule
    val loadingRule = createAndroidComposeRule<LoadingActivity>()

    @Test
    fun testLoadingActivityContent() {
        loadingRule.onNodeWithText("Loading & Connection Test").assertIsDisplayed()
        loadingRule.onNodeWithText("Run Connection Test").assertIsDisplayed()
    }
}
