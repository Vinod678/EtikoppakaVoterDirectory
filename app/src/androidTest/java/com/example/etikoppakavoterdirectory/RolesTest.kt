package com.example.etikoppakavoterdirectory

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.etikoppakavoterdirectory.auth.Role
import com.example.etikoppakavoterdirectory.ui.components.FOOTER_CREDIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Admin and Viewer sign-in, the Viewer's read-only screens, and the reset of searches after
 * signing out. Read-only on the real app data: nothing here changes a voter or a household.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RolesTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun none(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun text(tag: String): String {
        val n = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        return n.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
    }
    private fun signOutFromHome() {
        rule.onNodeWithContentDescription("Sign out").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 10_000)
    }

    @Test
    fun lockScreen_showsFooterCredit() {
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 30_000)
        rule.onNodeWithTag("footerCredit").assertIsDisplayed()
        rule.onNodeWithText("Digitizing Etikoppaka ❤️ — @vinod_yedla").assertIsDisplayed()
        assertEquals("Digitizing Etikoppaka ❤️ — @vinod_yedla", FOOTER_CREDIT)
    }

    @Test
    fun lockScreen_roleCards_viewerByDefault_switchingClearsPassword() {
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 30_000)
        rule.onNodeWithTag("roleViewer").assertIsSelected()
        rule.onNodeWithTag("roleAdmin").assertIsNotSelected()
        rule.onNodeWithText("Unlock as Viewer").assertExists()
        rule.onNodeWithTag("passwordField").performTextInput("something")
        rule.onNodeWithTag("roleAdmin").performClick()
        rule.onNodeWithTag("roleAdmin").assertIsSelected()
        rule.onNodeWithTag("roleViewer").assertIsNotSelected()
        rule.onNodeWithText("Unlock as Admin").assertExists()
        assertEquals("password cleared on role change", "", text("passwordField"))
        rule.onNodeWithTag("roleViewer").performClick()
        rule.onNodeWithText("Unlock as Viewer").assertExists()
    }

    @Test
    fun lockScreen_adminPasswordWithViewerSelected_isRefused() {
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 30_000)
        rule.onNodeWithTag("roleViewer").performClick()
        rule.onNodeWithTag("passwordField").performTextInput(testPassword(Role.ADMIN))
        rule.onNodeWithTag("unlockButton").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Incorrect password", substring = true), 10_000)
        assertTrue("still locked", none(hasTestTag("homeGrid")))
        // Correct role: signs in (also resets the failure counter).
        rule.unlock(Role.VIEWER)
        rule.waitUntilAtLeastOneExists(hasTestTag("viewerSettings"), 30_000)
        signOutFromHome()
    }

    @Test
    fun viewer_wardList_isReadOnly_withoutTabs() {
        rule.openWard11(Role.VIEWER)
        rule.onNodeWithText("Total Voters").assertIsDisplayed()
        assertTrue("no visit counts", none(hasText("Visited")) && none(hasText("Not Visited")))
        assertTrue("no favourites filter", none(hasText("Favourites")))
        assertTrue("no block / tag filters", none(hasText("Blocks & tags")))
        assertTrue("no favourite stars", none(hasContentDescription("Favourites", substring = true)))
        assertTrue("no bottom tabs", none(hasText("Households") and hasClickAction()) && none(hasText("Blocks") and hasClickAction()) && none(hasText("Tags") and hasClickAction()))
        // Search by name / serial still works.
        rule.onNodeWithTag("wardSearch").performTextInput("3700")
        rule.waitUntilAtLeastOneExists(hasText("Showing 1 of 369 voters"), 10_000)
    }

    @Test
    fun viewer_voterDetails_showOnlyBasicInformation_andPrevNextWork() {
        rule.openWard11(Role.VIEWER)
        rule.onAllNodes(hasClickAction() and hasText("3691", substring = true)).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Personal information"), 10_000)
        rule.onNodeWithText("Ward 11 · GP Serial No. 3691").assertIsDisplayed()
        rule.onAllNodes(hasText("EPIC no.")).onFirst().assertExists()
        rule.onAllNodes(hasText("Assembly roll serial")).onFirst().assertExists()
        for (hidden in listOf("Favourite", "Visited", "Household", "Block", "Private note", "Tags", "Move", "Remove time")) {
            assertTrue("$hidden must not be shown to a Viewer", none(hasText(hidden)))
        }
        rule.onNodeWithContentDescription("Next voter", substring = true).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · GP Serial No. 3692"), 10_000)
        Espresso.pressBack()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · 369 Voters"), 10_000)
    }

    @Test
    fun viewer_settings_onlyAboutAndSignOut_withFooter() {
        rule.unlock(Role.VIEWER)
        rule.waitUntilAtLeastOneExists(hasTestTag("viewerSettings"), 30_000)
        rule.onNodeWithTag("viewerSettings").performClick()
        rule.waitUntilAtLeastOneExists(hasText("About"), 10_000)
        rule.onNodeWithText("Sign out").assertIsDisplayed()
        rule.onNodeWithTag("footerCredit").assertExists()
        for (hidden in listOf("Export PDF", "Backup & Restore", "Sign-in & Security")) {
            assertTrue("$hidden must not be shown to a Viewer", none(hasText(hidden)))
        }
        Espresso.pressBack()
        rule.waitUntilAtLeastOneExists(hasTestTag("homeGrid"), 10_000)
    }

    @Test
    fun admin_hasAllTabs_andBlueHeadersWithTheWard() {
        rule.openWard11(Role.ADMIN)
        rule.onAllNodes(hasText("Visited")).onFirst().assertExists()
        rule.onNode(hasText("Blocks") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Manage assignments"), 10_000)
        rule.onNodeWithTag("screenSubtitle").assertTextEqualsWard(11)
        rule.onNode(hasText("Tags") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ward 11 · counts are for this ward"), 10_000)
        rule.onNode(hasText("Settings") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Backup & Restore"), 10_000)
        rule.onNodeWithText("Export PDF").assertExists()
        rule.onNodeWithTag("screenSubtitle").assertTextEqualsWard(11)
        rule.onNodeWithTag("footerCredit").assertExists()
    }

    @Test
    fun signOut_thenFreshSignIn_startsWithEmptySearchesOnHome() {
        rule.unlock(Role.ADMIN)
        rule.waitUntilAtLeastOneExists(hasTestTag("globalSearch"), 30_000)
        rule.onNodeWithTag("globalSearch").performTextInput("Pedapati")
        rule.waitUntilAtLeastOneExists(hasText("across all wards", substring = true), 10_000)
        Espresso.closeSoftKeyboard()
        signOutFromHome()
        // Fresh sign-in as the other role: home screen, empty search, ward list shown.
        rule.unlock(Role.VIEWER)
        rule.waitUntilAtLeastOneExists(hasTestTag("homeGrid"), 30_000)
        assertEquals("", text("globalSearch"))
        rule.onNodeWithText("Total Wards").assertIsDisplayed()
        signOutFromHome()
        rule.unlock(Role.ADMIN)
        rule.waitUntilAtLeastOneExists(hasTestTag("homeGrid"), 30_000)
        assertEquals("", text("globalSearch"))
    }

    @Test
    fun wardSearch_survivesOpeningAVoterAndBack_butNotSignOut() {
        rule.openWard11(Role.ADMIN)
        rule.onNodeWithTag("wardSearch").performTextInput("Pedapati")
        rule.waitUntilAtLeastOneExists(hasText("Showing", substring = true), 10_000)
        Espresso.closeSoftKeyboard()
        rule.onAllNodes(hasClickAction() and hasText("Pedapati", substring = true) and !hasTestTag("wardSearch")).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Personal information"), 10_000)
        Espresso.pressBack()
        rule.waitUntilAtLeastOneExists(hasTestTag("wardSearch"), 10_000)
        assertEquals("Pedapati", text("wardSearch"))
        // Sign out from Settings, sign in again: home screen; the ward opens with an empty search.
        rule.onNode(hasText("Settings") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Sign out"), 10_000)
        rule.onNodeWithText("Sign out").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Sign out?"), 10_000)
        rule.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 10_000)
        rule.openWard11(Role.ADMIN)
        assertEquals("", text("wardSearch"))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextEqualsWard(ward: Int) {
        val t = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        assertEquals("Ward $ward", t)
    }
}
