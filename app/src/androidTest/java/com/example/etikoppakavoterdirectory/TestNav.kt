package com.example.etikoppakavoterdirectory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.etikoppakavoterdirectory.auth.Role

/**
 * Today's password for [role]. The passwords are not in this (public) source code; they are given
 * on the test command line:
 *
 *   adb shell am instrument -w -e adminPassword <today's Admin password> \
 *       -e viewerPassword <today's Viewer password> ...
 *
 * From 3 Dec 2026 the Viewer password is the fixed one (Admin needs its own configured password).
 */
fun testPassword(role: Role): String =
    InstrumentationRegistry.getArguments().getString(if (role == Role.ADMIN) "adminPassword" else "viewerPassword")
        ?: error("Pass -e ${if (role == Role.ADMIN) "adminPassword" else "viewerPassword"} <password> to am instrument")

fun todaysPassword(): String = testPassword(Role.ADMIN)

/** Unlocks the app as [role] if the lock screen is showing: picks the role card, then the password. */
@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.unlock(role: Role = Role.ADMIN) {
    waitUntilAtLeastOneExists(hasTestTag("passwordField").or(hasTestTag("homeGrid")).or(hasTestTag("wardLabel")), 30_000)
    if (onAllNodes(hasTestTag("passwordField")).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(if (role == Role.ADMIN) "roleAdmin" else "roleViewer").performScrollTo().performClick()
        onNodeWithTag("passwordField").performTextInput(testPassword(role))
        onNodeWithTag("unlockButton").performScrollTo().performClick()
        waitUntilAtLeastOneExists(hasTestTag("homeGrid").or(hasTestTag("wardLabel")), 30_000)
    }
}

/** Unlocks and opens Ward 11 from the home screen (or stays in it when already open). */
@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.openWard11(role: Role = Role.ADMIN) {
    unlock(role)
    waitUntilAtLeastOneExists(hasTestTag("homeGrid").or(hasTestTag("wardLabel")), 30_000)
    if (onAllNodes(hasTestTag("homeGrid")).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("homeGrid").performScrollToNode(hasTestTag("ward-11"))
        onNodeWithTag("ward-11").performClick()
    }
    waitUntilAtLeastOneExists(hasText("Ward 11 · 369 Voters"), 30_000)
}

/**
 * Signs in as Admin without the UI (for tests that call Admin-only repository functions such as
 * the PDF export directly). Pair with [signOutDirect].
 */
fun signInAdminDirect(app: VoterDirectoryApp) = kotlinx.coroutines.runBlocking {
    val r = app.auth.attempt(testPassword(Role.ADMIN).toCharArray(), Role.ADMIN)
    check(r == com.example.etikoppakavoterdirectory.auth.AttemptResult.Success) { "Admin sign-in failed: $r" }
}

fun signOutDirect(app: VoterDirectoryApp) = app.auth.logout()
