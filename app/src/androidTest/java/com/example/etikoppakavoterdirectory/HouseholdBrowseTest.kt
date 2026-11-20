package com.example.etikoppakavoterdirectory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Households tab browsing state on the real app (read-only: searching, sorting and opening a
 * household never change data): search and sort survive opening a household and pressing Back,
 * the clear button keeps the sort, a bottom-tab switch resets everything, and the Unassigned tile.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HouseholdBrowseTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val navHouseholds = hasText("Households") and hasClickAction()
    private val navVoters = hasText("Voters") and hasClickAction()
    private val notTextField = SemanticsMatcher("not a text field") { !it.config.contains(SemanticsProperties.EditableText) }

    private fun openHouseholds() {
        rule.openWard11()
        rule.onNode(navHouseholds).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("unassignedTile"), 15_000)
    }

    private fun search(text: String) {
        rule.onNodeWithTag("householdSearchButton").performClick()
        rule.onNodeWithTag("householdSearch").performTextInput(text)
        rule.waitUntilAtLeastOneExists(hasTestTag("householdMatchCount"), 10_000)
    }

    private fun matchCount(): String =
        rule.onNodeWithTag("householdMatchCount").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }

    private fun pickSort(name: String) {
        rule.onNodeWithTag("householdSortLabel").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("sort-$name"), 5_000)
        rule.onNodeWithTag("sort-$name").performClick()
    }

    /** Opens the first household card on screen and comes back with the system Back button. */
    private fun openFirstHouseholdAndBack(containing: String) {
        rule.onAllNodes(hasText(containing, substring = true) and hasClickAction() and notTextField and hasText("member", substring = true) and !hasTestTag("householdSortLabel")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Members · long-press to select several"), 10_000)
        Espresso.pressBack()
        rule.waitUntilAtLeastOneExists(hasTestTag("unassignedTile"), 10_000)
    }

    @Test
    fun scenarioA_searchSurvivesOpeningAHouseholdAndBack() {
        openHouseholds()
        search("Pedapati")
        val before = matchCount()
        openFirstHouseholdAndBack("Pedapati")
        rule.onNodeWithTag("householdSearch").assertTextContains("Pedapati")
        assertEquals(before, matchCount())
    }

    @Test
    fun scenarioB_sortSurvivesOpeningAHouseholdAndBack() {
        openHouseholds()
        pickSort("MEMBERS_ASC")
        rule.onNodeWithTag("householdSortLabel").assertTextEquals("Sorted: Least members first")
        openFirstHouseholdAndBack("")
        rule.onNodeWithTag("householdSortLabel").assertTextEquals("Sorted: Least members first")
    }

    @Test
    fun scenarioC_clearButtonShowsAllAndKeepsSort() {
        openHouseholds()
        pickSort("MEMBERS_ASC")
        search("Pedapati")
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("householdMatchCount")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("householdSortLabel").assertTextEquals("Sorted: Least members first")
    }

    @Test
    fun scenarioD_switchingBottomTabResetsSearchAndSort() {
        openHouseholds()
        search("Pedapati")
        pickSort("MEMBERS_ASC")
        Espresso.closeSoftKeyboard()
        rule.onNode(navVoters).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · 369 Voters"), 10_000)
        rule.onNode(navHouseholds).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("unassignedTile"), 10_000)
        assertTrue(rule.onAllNodes(hasTestTag("householdSearch")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasTestTag("householdMatchCount")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("householdSortLabel").assertTextEquals("Sorted: Most members first")
        rule.onNodeWithTag("unassignedTile").assertIsDisplayed() // list is back at the top
    }

    @Test
    fun unassignedTile_withNoUnassignedVoters_saysAllAssigned_orOpensTheList() {
        openHouseholds()
        val tile = rule.onNodeWithTag("unassignedTile").fetchSemanticsNode()
        val desc = tile.config.getOrNull(SemanticsProperties.ContentDescription)!!.joinToString()
        val n = Regex("Unassigned Voters: (\\d+)").find(desc)!!.groupValues[1].toInt()
        rule.onNodeWithTag("unassignedTile").performClick()
        if (n == 0) {
            rule.waitUntilAtLeastOneExists(hasText("All voters are assigned to households."), 5_000)
        } else {
            rule.waitUntilAtLeastOneExists(hasTestTag("unassignedList"), 5_000)
            Espresso.pressBack()
        }
        assertTrue(rule.onAllNodes(hasSetTextAction() and hasTestTag("householdSearch")).fetchSemanticsNodes().isEmpty())
    }
}
