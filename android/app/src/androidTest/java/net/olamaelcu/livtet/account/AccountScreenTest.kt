package net.olamaelcu.livtet.account

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.olamaelcu.livtet.DashboardActivity
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<DashboardActivity>()

    @Test
    fun `account screen shows sign-in prompt when signed out`() {
        composeTestRule.onNodeWithText("Account").assertExists()
        composeTestRule.onNodeWithText("Sign in to unlock social features").assertExists()
    }

    @Test
    fun `ATProto sign-in button is present`() {
        composeTestRule.onNodeWithText("Sign in with AT Protocol").assertExists()
    }
}
