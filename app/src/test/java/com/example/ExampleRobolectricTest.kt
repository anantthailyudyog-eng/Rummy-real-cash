package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches Velvet Rummy Den`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Velvet Rummy Den", appName)
    }

    @Test
    fun `launches directly into lobby and enters CPU practice table with dealt cards`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Velvet Rummy Den").assertIsDisplayed()

        // Toggle language EN -> HI -> EN while top bar is visible
        composeRule.onNodeWithTag("lobby_lang_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("वेलवेट रमी डेन").assertIsDisplayed()
        composeRule.onNodeWithTag("lobby_lang_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("daily_bonus_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("play_now_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cpu_practice_button").performScrollTo().assertIsDisplayed()

        // Start CPU Practice immediately
        composeRule.onNodeWithTag("cpu_practice_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("closed_stock_pile").assertIsDisplayed()
        composeRule.onNodeWithTag("open_discard_pile").assertIsDisplayed()
        composeRule.onNodeWithTag("center_finish_target").assertIsDisplayed()
        composeRule.onNodeWithTag("sort_button").performClick()
        composeRule.waitForIdle()
    }
}
