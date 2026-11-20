package com.example.etikoppakavoterdirectory

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Swipe and button navigation on the real app and data. Every change a test makes (favourite,
 * visited, note text) is undone by the same test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class VoterPagerTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForList() {
        rule.openWard11()
        rule.waitUntilAtLeastOneExists(hasText("Varahalamma"), 30_000)
    }

    private fun showing(serial: Int, position: String) {
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · GP Serial No. $serial"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText(position), 5_000)
        rule.waitForIdle()
        rule.onNodeWithText(position).assertExists()
    }

    private fun pager() = rule.onNodeWithTag("voterPager")
    private fun openFromList(serial: Int) {
        rule.onNode(hasScrollToKeyAction()).performScrollToKey(com.example.etikoppakavoterdirectory.data.db.VoterIds.of(11, serial))
        rule.onNode(hasContentDescription("Serial number $serial", substring = false)).performClick()
    }

    private fun search(text: String) {
        val field = rule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(text)
    }

    /** Reads "k / n" from the bottom bar. */
    private fun position(): Pair<Int, Int> {
        val node = rule.onNode(hasContentDescription("Voter ", substring = true).and(hasText(" / ", substring = true))).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.Text)!!.first().text
        val (k, n) = text.split(" / ").map { it.trim().toInt() }
        return k to n
    }

    @Test
    fun swipeLeftRight_andButtons_allVoters_firstBoundary() {
        openFromList(3691)
        showing(3691, "1 / 369")
        rule.onNodeWithContentDescription("Previous voter").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Next voter").assertIsEnabled()

        pager().performTouchInput { swipeLeft() }        // swipe left -> next
        showing(3692, "2 / 369")
        pager().performTouchInput { swipeRight() }       // swipe right -> previous
        showing(3691, "1 / 369")
        pager().performTouchInput { swipeRight() }       // nothing before the first voter
        showing(3691, "1 / 369")

        rule.onNodeWithContentDescription("Next voter").performClick()
        showing(3692, "2 / 369")
        rule.onNodeWithContentDescription("Previous voter").performClick()
        showing(3691, "1 / 369")
    }

    @Test
    fun lastBoundary_nextDisabled_andSwipeStops() {
        openFromList(4059)
        showing(4059, "369 / 369")
        rule.onNodeWithContentDescription("Next voter").assertIsNotEnabled()
        pager().performTouchInput { swipeLeft() }
        showing(4059, "369 / 369")
        rule.onNodeWithContentDescription("Previous voter").performClick()
        showing(4058, "368 / 369")
    }

    @Test
    fun filteredList_navigatesOnlyThroughResults() {
        search("Gutti")
        rule.waitUntilAtLeastOneExists(hasText("Showing ", substring = true), 10_000)
        val first = 3703
        openFromList(first)
        val (k, n) = position()
        assertEquals(1, k)
        assertTrue("search should give a short list, got $n", n in 2..30)
        rule.onNodeWithContentDescription("Previous voter").assertIsNotEnabled()
        val seen = mutableListOf(first)
        repeat(n - 1) {
            rule.onNodeWithContentDescription("Next voter").performClick()
            rule.waitUntil(5_000) { position().first == seen.size + 1 && currentSerial() != seen.last() }
            val (pos, total) = position()
            assertEquals(n, total)
            assertEquals(seen.size + 1, pos)
            seen += currentSerial()
        }
        rule.onNodeWithContentDescription("Next voter").assertIsNotEnabled()
        assertEquals(seen.sorted(), seen)          // list order is preserved
        assertEquals(n, seen.toSet().size)
    }

    private fun currentSerial(): Int {
        // The page that is fully visible shows "GP Serial No. x"; neighbours are off-screen.
        val nodes = rule.onAllNodesWithTextPrefix("GP Serial No. ")
        return nodes.first { it.second }.first
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextPrefix(prefix: String): List<Pair<Int, Boolean>> {
        val root = onNodeWithTag("voterPager").fetchSemanticsNode().boundsInRoot
        return onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().map { node ->
            val t = node.config.getOrNull(SemanticsProperties.Text)!!.first().text
            val b = node.boundsInRoot
            // Off-screen neighbour pages are clipped to zero width at the pager edge.
            val visible = b.width > 1f && b.left >= root.left - 1 && b.right <= root.right + 1
            t.substringAfter(prefix).trim().toInt() to visible
        }
    }

    @Test
    fun rapidSwipes_positionAndVoterAlwaysAgree() {
        openFromList(3691)
        showing(3691, "1 / 369")
        pager().performTouchInput { repeat(6) { swipeLeft(durationMillis = 80) } }
        rule.waitUntil(5_000) { 3690 + position().first == currentSerial() }
        assertTrue(position().first > 1)
        pager().performTouchInput { repeat(3) { swipeRight(durationMillis = 80) } }
        rule.waitUntil(5_000) { 3690 + position().first == currentSerial() }
    }

    @Test
    fun rotation_keepsVoterAndPosition() {
        openFromList(3691)
        pager().performTouchInput { swipeLeft() }
        showing(3692, "2 / 369")
        pager().performTouchInput { swipeLeft() }
        showing(3693, "3 / 369")
        rule.activityRule.scenario.recreate()
        showing(3693, "3 / 369")
        rule.onNodeWithContentDescription("Next voter").performClick()
        showing(3694, "4 / 369")
    }

    /** The node matching [m] on the page currently on screen (neighbour pages are pre-loaded off-screen). */
    private fun onVisible(m: androidx.compose.ui.test.SemanticsMatcher): androidx.compose.ui.test.SemanticsNodeInteraction {
        val root = rule.onNodeWithTag("voterPager").fetchSemanticsNode().boundsInRoot
        val nodes = rule.onAllNodes(m).fetchSemanticsNodes()
        val i = nodes.indexOfFirst { val b = it.boundsInRoot; b.width > 1f && b.left >= root.left - 1 && b.right <= root.right + 1 }
        assertTrue("no visible node", i >= 0)
        return rule.onAllNodes(m)[i]
    }

    private fun isOn(m: androidx.compose.ui.test.SemanticsMatcher) =
        onVisible(m).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "On"

    @Test
    fun tappingActions_neverSwipes_andTogglesOnlyThatMarker() {
        openFromList(3692)
        showing(3692, "2 / 369")
        val fav = hasText("Favourite").and(hasStateDescription())
        val visited = hasText("Visited").and(hasStateDescription())
        val favBefore = isOn(fav)
        val visitedBefore = isOn(visited)

        onVisible(fav).performClick()
        showing(3692, "2 / 369")                       // tap did not move to another voter
        rule.waitUntil(8_000) { isOn(fav) != favBefore }
        assertEquals(visitedBefore, isOn(visited))     // the other marker is untouched
        onVisible(fav).performClick()                  // restore
        rule.waitUntil(8_000) { isOn(fav) == favBefore }

        onVisible(visited).performClick()
        showing(3692, "2 / 369")
        rule.waitUntil(8_000) { isOn(visited) != visitedBefore }
        assertEquals(favBefore, isOn(fav))
        onVisible(visited).performClick()              // restore
        rule.waitUntil(8_000) { isOn(visited) == visitedBefore }
        showing(3692, "2 / 369")
    }

    private fun hasStateDescription() = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

    @Test
    fun editingNote_locksSwiping_untilFocusLeaves() {
        openFromList(3693)
        showing(3693, "3 / 369")
        val note = rule.onNodeWithTag("note-${com.example.etikoppakavoterdirectory.data.db.VoterIds.of(11, 3693)}")
        note.performClick()
        note.performTextInput("x")
        pager().performTouchInput { swipeLeft() }
        showing(3693, "3 / 369")                  // locked while editing
        note.performTextClearance()               // back to the saved (empty) note - nothing saved
        rule.onNodeWithContentDescription("Next voter").performClick() // clears focus and moves
        showing(3694, "4 / 369")
        pager().performTouchInput { swipeRight() } // swiping works again
        showing(3693, "3 / 369")
    }
}
