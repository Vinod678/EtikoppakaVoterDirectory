package com.example.etikoppakavoterdirectory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.etikoppakavoterdirectory.auth.PasswordPolicy

/** Today's password (computed from the same rule the app uses, so tests keep working after 24 Nov 2026). */
fun todaysPassword(): String = "Vinod_Yedla" + PasswordPolicy.suffixFor(PasswordPolicy.dateAt(System.currentTimeMillis()))

/** Unlocks the app if the lock screen is showing. */
@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.unlock() {
    waitUntilAtLeastOneExists(hasTestTag("passwordField").or(hasTestTag("homeGrid")).or(hasTestTag("wardLabel")), 30_000)
    if (onAllNodes(hasTestTag("passwordField")).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("passwordField").performTextInput(todaysPassword())
        onNodeWithTag("unlockButton").performClick()
    }
}

/** Unlocks and opens Ward 11 from the home screen (or stays in it when already open). */
@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.openWard11() {
    unlock()
    waitUntilAtLeastOneExists(hasTestTag("homeGrid").or(hasTestTag("wardLabel")), 30_000)
    if (onAllNodes(hasTestTag("homeGrid")).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("homeGrid").performScrollToNode(hasTestTag("ward-11"))
        onNodeWithTag("ward-11").performClick()
    }
    waitUntilAtLeastOneExists(hasText("Ward 11 · 369 Voters"), 30_000)
}
