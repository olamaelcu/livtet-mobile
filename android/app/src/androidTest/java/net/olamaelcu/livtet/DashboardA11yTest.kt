package net.olamaelcu.livtet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Regression tests for the dashboard accessibility fixes.
 *
 * Background: a screenshot-vs-a11y comparison on the dashboard surface
 * surfaced four problems:
 *
 *   1. The selected bottom-nav tab (Dashboard) had `selected="true"` in
 *      the a11y tree but no `clickable` flag, which can prevent
 *      screen-reader users from re-tapping the active tab.
 *   2. The greeting author/material line ("author - material") rendered
 *      as the literal text " - " when both fields are empty, and the
 *      bare separator was being announced by TalkBack.
 *   3. The newspaper and other decorative emojis inside dashboard cards
 *      were exposed as raw unicode codepoints to a11y services.
 *
 * These tests pin down the corrected behaviour.
 */
@RunWith(JUnit4::class)
class DashboardA11yTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<DashboardActivity>()

    @Test
    fun everyBottomNavTabExposesAClickAction() {
        composeTestRule.onNodeWithText("Dashboard").assertHasClickAction()
        composeTestRule.onNodeWithText("Library").assertHasClickAction()
        composeTestRule.onNodeWithText("Account").assertHasClickAction()
    }

    @Test
    fun dashboardTabIsSelectedOnLaunch() {
        composeTestRule.onNodeWithText("Dashboard").assertIsSelected()
    }

    @Test
    fun tappingLibraryTabSelectsIt() {
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Library").assertIsSelected()
    }

    @Test
    fun emptyGreetingDoesNotExposeBareSeparator() {
        // get_greeting() in core/livtet-ffi returns all-empty fields on
        // this emulator. The author-material line used to render as
        // bare " - " and announce the separator. The conditional in
        // DashboardScreen.kt now suppresses the line entirely when
        // both fields are blank.
        composeTestRule.onAllNodesWithText(" - ").assertCountEquals(0)
    }

    @Test
    fun feedPlaceholderHeadingIsVisible() {
        composeTestRule.onNodeWithText("Feed").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Friend activity and recommendations will appear here once the social feed is ready."
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Coming Soon").assertIsDisplayed()
    }
}
