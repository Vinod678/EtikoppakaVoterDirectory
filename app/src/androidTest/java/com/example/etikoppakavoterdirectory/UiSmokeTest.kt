package com.example.etikoppakavoterdirectory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only UI smoke tests on the real app (they do not change favourites, visits, blocks, notes,
 * tags or households): lock screen, home screen with 14 wards, global search, Ward 11 directory.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun search(text: String) {
        val field = rule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun lockScreen_rejectsWrongAndWrongCase_thenUnlocks() {
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 30_000)
        rule.onNodeWithText("For any information regarding login, please contact: 938one0638eight").assertIsDisplayed()
        // No voter data is composed behind the lock.
        assertTrue(rule.onAllNodes(hasText("Varahalamma")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("passwordField").performTextInput(todaysPassword().lowercase())
        rule.onNodeWithTag("unlockButton").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Incorrect password", substring = true), 10_000)
        // Show / hide toggle exists; the typed text is never shown as plain text by default.
        rule.onNodeWithContentDescription("Show password").assertIsDisplayed()
        rule.onNodeWithTag("passwordField").performTextInput(todaysPassword())
        rule.onNodeWithTag("unlockButton").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Etikoppaka Village · All Wards"), 30_000)
    }

    @Test
    fun home_showsFourteenWards_realCounts_andNoDataForMissingWards() {
        rule.unlock()
        rule.waitUntilAtLeastOneExists(hasTestTag("homeGrid"), 30_000)
        rule.onNodeWithText("Total Wards").assertIsDisplayed()
        rule.waitUntilAtLeastOneExists(hasText("1,476"), 10_000) // total voters from the database (Wards 2, 3, 4, 11)
        for (w in 1..14) {
            rule.onNodeWithTag("homeGrid").performScrollToNode(hasTestTag("ward-$w"))
            rule.onNodeWithTag("ward-$w").assertIsDisplayed()
        }
        rule.onNodeWithTag("homeGrid").performScrollToNode(hasTestTag("ward-11"))
        rule.waitUntilAtLeastOneExists(hasText("369 voters"), 10_000)
        rule.onNodeWithTag("homeGrid").performScrollToNode(hasTestTag("ward-1"))
        assertTrue(rule.onAllNodes(hasText("No data imported")).fetchSemanticsNodes().size >= 1)
    }

    @Test
    fun globalSearch_opensVoterDetail_withBackToHome() {
        rule.unlock()
        rule.waitUntilAtLeastOneExists(hasTestTag("globalSearch"), 30_000)
        search("Saibabu Challa")
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · Serial 3700"), 10_000)
        rule.onNodeWithText("Ward 11 · Serial 3700").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · GP Serial No. 3700"), 10_000)
        rule.onNodeWithContentDescription("Back to Home").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Etikoppaka Village · All Wards"), 10_000)
    }

    @Test
    fun ward11_directorySearchAndDetail() {
        rule.openWard11()
        rule.waitUntilAtLeastOneExists(hasText("Varahalamma"), 30_000)

        // English, Telugu, relative, house number, EPIC and serial searches.
        search("Polavarapu")
        rule.waitUntilAtLeastOneExists(hasText("Lakshmi Polavarapu"), 10_000)
        search("లక్ష్మి పోలవరపు")
        rule.waitUntilAtLeastOneExists(hasText("Lakshmi Polavarapu"), 10_000)
        search("pentayya")
        rule.waitUntilAtLeastOneExists(hasText("Bhoolakshmi Sappa"), 10_000)
        search("XOO1357615")
        rule.waitUntilAtLeastOneExists(hasText("Varahalamma"), 10_000)
        search("zzzz-no-such-voter")
        rule.waitUntilAtLeastOneExists(hasText("No voters match"), 10_000)

        // Open a detail page.
        search("4058")
        rule.waitUntilAtLeastOneExists(hasText("Malleswari Pedapati"), 10_000)
        rule.onNodeWithText("Malleswari Pedapati").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · GP Serial No. 4058"), 10_000)
        rule.onNodeWithText("Voter details").assertIsDisplayed()
        search("") // leave the ward search empty for the next test
    }

    @Test
    fun ward11_households_autoGenerated_searchAndNoSuggestionsTab() {
        rule.openWard11()
        rule.onNodeWithText("Households").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("unassignedTile"), 15_000)
        assertTrue(rule.onAllNodes(hasText("Needs review", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasText("Suggestions", substring = true)).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("householdSearchButton").performClick()
        rule.onNodeWithTag("householdSearch").performTextInput("Gutti")
        rule.waitUntilAtLeastOneExists(hasText("Gutti"), 10_000)
        rule.onNodeWithContentDescription("Close search").performClick()
    }
}
