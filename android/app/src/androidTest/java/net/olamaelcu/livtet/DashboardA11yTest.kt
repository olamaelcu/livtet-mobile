package net.olamaelcu.livtet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        composeTestRule.onNodeWithText("Social").assertHasClickAction()
        composeTestRule.onNodeWithText("Account").assertHasClickAction()
    }

    @Test
    fun tappingSocialTabShowsFeedScreen() {
        composeTestRule.onNodeWithText("Social").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Social").assertIsSelected()
        composeTestRule
            .onNodeWithText("Social features and recommendations are on the way.")
            .assertIsDisplayed()
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
        // Regression guard: when `get_greeting()` returns populated fields,
        // the dashboard renders them as "Author - Material" and the
        // substring " - " legitimately appears inside that node. The
        // original bug was a stand-alone node whose entire text was
        // " - " (i.e. the separator with no surrounding content) — that
        // node was a11y-announced as the literal word "dash". We assert
        // here that no such stand-alone node exists.
        composeTestRule
            .onAllNodesWithText(" - ", substring = false)
            .assertCountEquals(0)
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

    @Test
    fun tappingDashboardFromSettingsReturnsToDashboard() {
        // Regression guard: opening Settings via the gear and then tapping
        // the Dashboard tab in the bottom nav must pop Settings off the
        // back stack and reveal Dashboard. The fix lives at
        // `DashboardActivity.kt:94` — the gear's `navigate("settings")`
        // call now uses the same `popUpTo(start) { saveState = true } /
        // launchSingleTop = true / restoreState = true` option set as the
        // bottom-nav handler, so the two transitions are symmetric.

        // Open Settings via the gear icon.
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        // The Settings screen has its own inner TopAppBar with the title
        // "Settings" (SettingsScreen.kt:65).
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Tap the Dashboard tab.
        composeTestRule.onNodeWithText("Dashboard").performClick()
        composeTestRule.waitForIdle()

        // Dashboard tab is selected and Settings is no longer visible.
        composeTestRule.onNodeWithText("Dashboard").assertIsSelected()
        composeTestRule.onNodeWithText("Settings").assertDoesNotExist()
    }
}
