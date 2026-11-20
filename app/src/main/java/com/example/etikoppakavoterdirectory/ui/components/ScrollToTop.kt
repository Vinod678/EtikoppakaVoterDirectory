package com.example.etikoppakavoterdirectory.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

/**
 * Small "back to top" button for long lists. Hidden near the top; appears once roughly one screen
 * has been scrolled; scrolls smoothly back. Only moves the list - filters and searches are untouched.
 */
@Composable
fun ScrollToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.testTag("scrollToTop"),
        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top") }
    }
}

@Composable
fun ScrollToTopButton(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val show by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > maxOf(3, state.layoutInfo.visibleItemsInfo.size) }
    }
    ScrollToTopButton(show, { scope.launch { state.animateScrollToItem(0) } }, modifier)
}

@Composable
fun ScrollToTopButton(state: LazyGridState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val show by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > maxOf(6, state.layoutInfo.visibleItemsInfo.size) }
    }
    ScrollToTopButton(show, { scope.launch { state.animateScrollToItem(0) } }, modifier)
}

/** "House 8-144", or "Houses 8-143, 8-145, 8-146 +2" for a family spread over several houses. */
fun houseLabel(houses: Collection<String?>): String? {
    val distinct = houses.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        .sortedBy { com.example.etikoppakavoterdirectory.data.household.HouseholdGrouper.naturalKey(it) }
    return when (distinct.size) {
        0 -> null
        1 -> "House ${distinct.single()}"
        else -> "Houses " + distinct.take(3).joinToString(", ") + if (distinct.size > 3) " +${distinct.size - 3}" else ""
    }
}

/**
 * Visibility of a floating action button that duplicates an inline button (list item [inlineKey]).
 * Hidden while the inline button is on screen and while scrolling down (so it never sits on the
 * content being read); shown when scrolling up and at the end of the list, where the list's bottom
 * padding keeps the last item clear of it.
 */
@Composable
fun rememberFloatingActionVisible(state: LazyListState, inlineKey: Any): Boolean {
    var shown by androidx.compose.runtime.remember(state) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state, inlineKey) {
        var last = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        androidx.compose.runtime.snapshotFlow {
            listOf(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                if (state.canScrollForward) 1 else 0,
                if (state.layoutInfo.visibleItemsInfo.any { it.key == inlineKey }) 1 else 0,
            )
        }.collect { (i, o, more, inline) ->
            val down = i > last.first || (i == last.first && o > last.second + 4)
            val up = i < last.first || (i == last.first && o < last.second - 4)
            shown = when {
                inline == 1 -> false
                more == 0 -> true
                down -> false
                up -> true
                else -> shown
            }
            last = i to o
        }
    }
    return shown
}
