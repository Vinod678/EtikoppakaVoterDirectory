package com.example.etikoppakavoterdirectory.ui.households

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.example.etikoppakavoterdirectory.ui.components.SummaryStat
import com.example.etikoppakavoterdirectory.ui.components.SummaryTiles
import com.example.etikoppakavoterdirectory.data.db.HouseholdCoverage
import com.example.etikoppakavoterdirectory.data.db.HouseholdMemberName
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.data.household.HouseholdGrouper
import com.example.etikoppakavoterdirectory.ui.EditViewModel
import com.example.etikoppakavoterdirectory.ui.components.AppIcons
import com.example.etikoppakavoterdirectory.ui.components.BrandHeader
import com.example.etikoppakavoterdirectory.ui.components.EmptyState
import com.example.etikoppakavoterdirectory.ui.components.HouseholdPickerSheet
import com.example.etikoppakavoterdirectory.ui.components.LightStatusBarIcons
import com.example.etikoppakavoterdirectory.ui.components.MISSING
import com.example.etikoppakavoterdirectory.ui.components.ProgressLine
import com.example.etikoppakavoterdirectory.ui.components.ScrollToTopButton
import com.example.etikoppakavoterdirectory.ui.components.VoterPhoto
import com.example.etikoppakavoterdirectory.ui.components.VoterPickerDialog
import com.example.etikoppakavoterdirectory.ui.components.houseLabel
import com.example.etikoppakavoterdirectory.ui.components.relativeLine
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import com.example.etikoppakavoterdirectory.ui.theme.EtikoppakaVoterDirectoryTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu
import java.util.Locale

enum class HouseholdSort(val label: String) {
    MEMBERS_DESC("Most members first"),
    MEMBERS_ASC("Least members first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    HOUSE_ASC("House number ascending"),
    HOUSE_DESC("House number descending"),
}

/**
 * Sorts household cards by the live member counts (manual moves and assignments included). Only the
 * displayed order changes - no database record is touched. Member sorts break ties by household
 * name, then by household id; the other sorts by first serial number, then id - always deterministic.
 */
fun sortHouseholds(list: List<HouseholdSummary>, sort: HouseholdSort): List<HouseholdSummary> {
    val bySerial = compareBy<HouseholdSummary>({ it.firstSerial ?: Int.MAX_VALUE }, { it.id })
    val byName = compareBy<HouseholdSummary> { it.name.lowercase(Locale.ROOT) }
    val byNameThenId = byName.thenBy { it.id }
    val house = compareBy<HouseholdSummary>({ it.houseNo == null }, { HouseholdGrouper.naturalKey(it.houseNo) })
    return when (sort) {
        HouseholdSort.MEMBERS_DESC -> list.sortedWith(compareByDescending<HouseholdSummary> { it.members }.then(byNameThenId))
        HouseholdSort.MEMBERS_ASC -> list.sortedWith(compareBy<HouseholdSummary> { it.members }.then(byNameThenId))
        HouseholdSort.NAME_ASC -> list.sortedWith(byName.then(bySerial))
        HouseholdSort.NAME_DESC -> list.sortedWith(byName.reversed().then(bySerial))
        HouseholdSort.HOUSE_ASC -> list.sortedWith(house.then(bySerial))
        HouseholdSort.HOUSE_DESC -> list.sortedWith(
            compareBy<HouseholdSummary> { it.houseNo == null }.thenByDescending { HouseholdGrouper.naturalKey(it.houseNo) }.then(bySerial)
        )
    }
}

private fun norm(s: String) = s.lowercase(Locale.ROOT).replace('.', ' ').replace(Regex("\\s+"), " ")

/**
 * Search texts of every household: one entry per member (English and Telugu name, initials,
 * father / husband / mother name, house number, GP serial), each prefixed with the household
 * heading and house number. Lower-case, punctuation and spacing normalised.
 */
fun householdSearchIndex(households: List<HouseholdSummary>, names: List<HouseholdMemberName>): Map<Long, List<String>> {
    val byHousehold = names.groupBy { it.householdId }
    return households.associate { h ->
        val head = listOfNotNull(h.name, h.houseNo).joinToString(" ")
        val members = byHousehold[h.id].orEmpty().map { m ->
            val parts = ArrayList<String>()
            parts += head
            m.nameEn?.let { n ->
                parts += n
                val words = n.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
                if (words.size >= 2) {
                    val ini = words.joinToString("") { it.take(1) }
                    parts += ini; parts += ini.reversed()
                }
            }
            listOfNotNull(m.nameTe, m.relativeNameEn, m.relativeNameTe, m.houseNo).forEach { parts += it }
            if (m.gpSerialNo > 0) parts += m.gpSerialNo.toString()
            " " + norm(parts.joinToString(" ")) + " "
        }
        h.id to members.ifEmpty { listOf(" " + norm(head) + " ") }
    }
}

/**
 * Every word of [query] must match within ONE member's text (or the heading); word order does not
 * matter ("Mogga Mahesh" = "Mahesh Mogga"). A one-letter word matches the start of a word (initials).
 */
fun matchesHousehold(index: List<String>, query: String): Boolean {
    val words = norm(query).split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    return index.any { text -> words.all { w -> if (w.length == 1) text.contains(" $w") else text.contains(w) } }
}

/** "111 Households · 365/369 Voters Assigned" (counts from the database, never assumed). */
fun coverageLine(households: Int, coverage: HouseholdCoverage?): String =
    "$households Household${if (households == 1) "" else "s"}" +
        (coverage?.let { " · ${it.assigned}/${it.total} Voters Assigned" } ?: "")

/**
 * Temporary browsing state of the Households tab: search text, whether the search field is open,
 * the sort order and the list position. It is held by the app root (not by the screen), so it
 * survives opening a household, its members, voter details or assignment screens and coming back
 * (and rotation). It is reset only when the user switches to another bottom-navigation tab or
 * leaves the ward; the search alone is cleared by its clear / close buttons. Recomposition and
 * database updates never reset it.
 */
@Stable
class HouseholdBrowseState(
    query: String = "",
    searching: Boolean = false,
    sort: HouseholdSort = HouseholdSort.MEMBERS_DESC,
    listState: LazyListState = LazyListState(),
) {
    var query by mutableStateOf(query)
    var searching by mutableStateOf(searching)
    var sort by mutableStateOf(sort)
    var listState by mutableStateOf(listState)
        private set

    /** Back to the defaults: no search, Most members first, top of the list. */
    fun reset() {
        query = ""; searching = false; sort = HouseholdSort.MEMBERS_DESC; listState = LazyListState()
    }

    companion object {
        val Saver: Saver<HouseholdBrowseState, Any> = listSaver(
            save = { listOf(it.query, it.searching, it.sort.name, it.listState.firstVisibleItemIndex, it.listState.firstVisibleItemScrollOffset) },
            restore = {
                HouseholdBrowseState(
                    it[0] as String,
                    it[1] as Boolean,
                    runCatching { HouseholdSort.valueOf(it[2] as String) }.getOrDefault(HouseholdSort.MEMBERS_DESC),
                    LazyListState(it[3] as Int, it[4] as Int),
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HouseholdsScreen(
    ward: Int,
    households: List<HouseholdSummary>,
    edit: EditViewModel,
    browse: HouseholdBrowseState,
    onOpen: (Long) -> Unit,
    onHome: () -> Unit,
    /** Tap on the "Unassigned Voters" tile; receives the current number of unassigned voters. */
    onShowUnassigned: (Int) -> Unit,
    /** Opens the Export PDF screen with this ward's households selected (Admin only; null hides it). */
    onExportPdf: (() -> Unit)? = null,
) {
    val repo = edit.repository
    val brand = AppTheme.brand
    val names by remember(ward) { repo.householdMemberNames(ward) }.collectAsState(initial = null)
    // Live counts and the unassigned list (both re-query only when households / members change).
    val coverage by remember(ward) { repo.householdCoverage(ward) }.collectAsState(initial = null)
    val unassigned by remember(ward) { repo.unassignedVoters(ward) }.collectAsState(initial = null)
    val index = remember(households, names) { householdSearchIndex(households, names.orEmpty()) }
    val houses = remember(names) { names.orEmpty().groupBy { it.householdId }.mapValues { (_, m) -> houseLabel(m.map { it.houseNo }) } }
    val listState = browse.listState
    val query = browse.query
    val searching = browse.searching
    val sort = browse.sort
    var picking by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf<Int?>(null) } // voter id being assigned
    var sortMenu by remember { mutableStateOf(false) }
    var sortMenuInline by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmRegroup by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Only a tap on the search icon opens the keyboard - not coming back from a household.
    var focusSearch by remember { mutableStateOf(false) }
    LightStatusBarIcons(enabled = true)

    val shown = remember(households, index, query, sort) {
        sortHouseholds(households.filter { h -> query.isBlank() || matchesHousehold(index[h.id].orEmpty(), query) }, sort)
    }
    fun closeSearch() { browse.searching = false; browse.query = ""; keyboard?.hide() }
    LaunchedEffect(focusSearch) { if (focusSearch) { focus.requestFocus(); keyboard?.show(); focusSearch = false } }
    androidx.activity.compose.BackHandler(enabled = searching) { closeSearch() }

    // "New household" is a normal button at the top of the list. The floating copy is only shown
    // when that button has scrolled away: it slides out while scrolling down (so it never sits on
    // the cards being read), comes back when scrolling up, and stays at the end of the list, where
    // the bottom padding keeps the last card and the unassigned section clear of it.
    val fabShown = com.example.etikoppakavoterdirectory.ui.components.rememberFloatingActionVisible(listState, "count")

    Scaffold(
        containerColor = brand.page,
        topBar = {
            // Pinned blue header; while searching, the search field sits right below it.
            Column {
                BrandHeader {
                    val titles: @Composable (Modifier) -> Unit = { m ->
                        Column(m) {
                            com.example.etikoppakavoterdirectory.ui.components.HeaderTitle(
                                "Etikoppaka Voter Directory",
                                MaterialTheme.typography.titleMedium,
                                brand.onHeader,
                            )
                            Text("Ward $ward · Households", style = MaterialTheme.typography.bodyMedium, color = brand.onHeaderVariant)
                        }
                    }
                    val actions: @Composable () -> Unit = {
                        if (!searching) {
                            IconButton(onClick = { browse.searching = true; focusSearch = true }, modifier = Modifier.testTag("householdSearchButton")) {
                                Icon(Icons.Filled.Search, contentDescription = "Search households", tint = brand.onHeader)
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(AppIcons.Sort, contentDescription = "Sort households", tint = brand.onHeader) }
                            SortMenu(sortMenu, sort, onPick = { browse.sort = it }, onDismiss = { sortMenu = false })
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = brand.onHeader) }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                if (onExportPdf != null) {
                                    DropdownMenuItem(
                                        text = { Text("Export PDF") },
                                        onClick = { moreMenu = false; onExportPdf() },
                                        modifier = Modifier.testTag("householdsExportPdf"),
                                    )
                                }
                                DropdownMenuItem(text = { Text("Re-run automatic grouping") }, onClick = { moreMenu = false; confirmRegroup = true })
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Four 48 dp buttons leave too little room for the titles on very narrow
                        // phones / large fonts: then the buttons get their own row above the titles.
                        val roomy = maxWidth - 8.dp - 48.dp * 4 >= 150.dp * LocalDensity.current.fontScale
                        if (roomy) {
                            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onHome) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All wards (home)", tint = brand.onHeader)
                                }
                                titles(Modifier.weight(1f))
                                actions()
                            }
                        } else {
                            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onHome) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All wards (home)", tint = brand.onHeader)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    actions()
                                }
                                titles(Modifier.padding(start = 12.dp, end = 12.dp))
                            }
                        }
                    }
                }
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { browse.query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text("Surname, member, relative, house or serial", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            IconButton(onClick = ::closeSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { browse.query = ""; focus.requestFocus() }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = brand.total,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                            .focusRequester(focus).testTag("householdSearch"),
                    )
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ScrollToTopButton(listState)
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(fabShown, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                ExtendedFloatingActionButton(
                    onClick = { picking = true },
                    containerColor = brand.selected,
                    contentColor = brand.onSelected,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "New household" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New household", maxLines = 1) },
                )
                }
            }
        },
    ) { padding ->
        // A search restored on return needs the member names to filter; wait for them (a few
        // milliseconds) so the matching results and the list position come back at once.
        if (query.isNotBlank() && names == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            // Bottom space keeps the last card and the unassigned section clear of both floating buttons.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary", contentType = "summary") {
                CoverageSummary(households.size, coverage) { n -> onShowUnassigned(n) }
            }
            item(key = "count", contentType = "count") {
                Column(Modifier.padding(start = 4.dp)) {
                    // The tiles above already show the totals; while searching, the match count.
                    if (query.isNotBlank()) {
                        Text(
                            "${shown.size} of ${households.size} households match",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("householdMatchCount"),
                        )
                    }
                    // Current sort mode (tapping it opens the sort menu) and the "New household" button;
                    // the button moves under the sort line when both do not fit.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Row(
                                Modifier.testTag("householdSortLabel").clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Change sort order") { sortMenuInline = true }
                                    .heightIn(min = 40.dp).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(AppIcons.Sort, contentDescription = null, tint = brand.total, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sorted: ${sort.label}", style = MaterialTheme.typography.labelLarge, color = brand.total)
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = brand.total, modifier = Modifier.size(20.dp))
                            }
                            SortMenu(sortMenuInline, sort, onPick = { browse.sort = it }, onDismiss = { sortMenuInline = false })
                        }
                        FilledTonalButton(
                            onClick = { picking = true },
                            modifier = Modifier.testTag("newHouseholdInline"),
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New household")
                        }
                    }
                }
            }
            if (households.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Home,
                        title = "No households yet",
                        message = "Households are created automatically from family surnames. Tap \"New household\" to group voters yourself.",
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "nomatch") {
                    EmptyState(
                        Icons.Filled.Search,
                        "No households match \"${query.trim()}\"",
                        "Try a surname, a member or relative name, initials, a house number or a GP serial number.",
                    )
                }
            }
            items(shown, key = { it.id }, contentType = { "household" }) { h -> HouseholdCard(h, houses[h.id]) { onOpen(h.id) } }

            // ---- Unassigned voters: a separate section after all households ----
            val loose = unassigned
            if (query.isBlank() && loose != null) {
                if (loose.isEmpty()) {
                    item(key = "allAssigned", contentType = "allAssigned") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppTheme.status.visited, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("All voters are assigned to households.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    item(key = "unassignedTitle", contentType = "unassignedTitle") {
                        Row(Modifier.testTag("unassignedSection").padding(start = 4.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = brand.unvisited, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Unassigned Voters (${loose.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                    items(loose, key = { "u" + it.voterId }, contentType = { "unassigned" }) { v ->
                        UnassignedVoterCard(v) { assigning = v.voterId }
                    }
                }
            }
        }
    }

    if (picking) {
        VoterPickerDialog(
            ward = ward,
            title = "New household",
            confirmLabel = "Create",
            onConfirm = { ids ->
                picking = false
                edit.createHousehold(null, ids, onDone = onOpen)
            },
            onDismiss = { picking = false },
        )
    }
    assigning?.let { voterId ->
        val serial = unassigned?.firstOrNull { it.voterId == voterId }?.gpSerialNo
        HouseholdPickerSheet(
            title = "Assign voter ${serial ?: ""} to a household",
            households = households,
            onPick = { target -> assigning = null; edit.addToHousehold(target.id, listOf(voterId)) },
            onCreateNew = { assigning = null; edit.createHousehold(null, listOf(voterId)) },
            onDismiss = { assigning = null },
        )
    }
    if (confirmRegroup) {
        AlertDialog(
            onDismissRequest = { confirmRegroup = false },
            title = { Text("Re-run automatic grouping?") },
            text = {
                Text(
                    "Automatic households in Ward $ward are generated again from the voter data. Households you created " +
                        "or edited, and every member you moved or removed, are kept exactly as they are."
                )
            },
            confirmButton = { TextButton(onClick = { confirmRegroup = false; edit.regroupWard(ward) }) { Text("Re-run") } },
            dismissButton = { TextButton(onClick = { confirmRegroup = false }) { Text("Cancel") } },
        )
    }
}

/** Sort choices; the current one is marked. Only the displayed order changes. */
@Composable
private fun SortMenu(expanded: Boolean, current: HouseholdSort, onPick: (HouseholdSort) -> Unit, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        HouseholdSort.entries.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label) },
                leadingIcon = { RadioButton(selected = s == current, onClick = null) },
                onClick = { onPick(s); onDismiss() },
                modifier = Modifier.testTag("sort-${s.name}").semantics { selected = s == current },
            )
        }
    }
}

/**
 * Total Households | Assigned Voters | Unassigned Voters (database counts for this ward). Complete
 * numbers at every width and font size (the tiles stack when three do not fit). The Unassigned tile
 * is a button that shows the unassigned voters.
 */
@Composable
private fun CoverageSummary(households: Int, coverage: HouseholdCoverage?, onUnassigned: (Int) -> Unit) {
    val brand = AppTheme.brand
    SummaryTiles(
        listOf(
            SummaryStat(Icons.Filled.Home, "$households", "Total Households", brand.total, brand.totalContainer),
            SummaryStat(AppIcons.Group, coverage?.let { "${it.assigned}/${it.total}" } ?: "–", "Assigned Voters", AppTheme.status.visited, AppTheme.status.visitedContainer),
            SummaryStat(
                Icons.Filled.Person, coverage?.unassigned?.toString() ?: "–", "Unassigned Voters", brand.unvisited, brand.unvisitedContainer,
                onClick = coverage?.let { c -> { onUnassigned(c.unassigned) } },
                clickLabel = "Show unassigned voters",
                testTag = "unassignedTile",
            ),
        ),
    )
}

/**
 * Household card: teal member-count badge, surname, member count, house number(s), visited and
 * favourite progress. [houses] = e.g. "Houses 8-143, 8-145 +1" for a family spread over several houses.
 */
@Composable
fun HouseholdCard(h: HouseholdSummary, houses: String? = h.houseNo?.let { "House $it" }, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${h.members}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { contentDescription = "${h.members} members" },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull("${h.members} member${if (h.members == 1) "" else "s"}", houses).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Side by side normally; one above the other on narrow screens / large fonts.
            BoxWithConstraints {
                if (maxWidth >= 236.dp * LocalDensity.current.fontScale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgressLine("Visited", h.visited, h.members, AppTheme.status.visited, Modifier.weight(1f))
                        ProgressLine("Favourites", h.favourites, h.members, AppTheme.brand.total, Modifier.weight(1f), showPercent = false)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressLine("Visited", h.visited, h.members, AppTheme.status.visited)
                        ProgressLine("Favourites", h.favourites, h.members, AppTheme.brand.total, showPercent = false)
                    }
                }
            }
        }
    }
}

/** A voter of this ward who belongs to no household, with an "Assign to Household" action. */
@Composable
internal fun UnassignedVoterCard(v: VoterEntity, onAssign: () -> Unit) {
    val thumbPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoterPhoto(
                    fileName = v.photoFilename,
                    contentDescription = "Photo of voter ${v.gpSerialNo}",
                    maxSidePx = thumbPx,
                    modifier = Modifier.size(width = 52.dp, height = 68.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("${v.gpSerialNo}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(v.nameEn ?: MISSING, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(v.nameTe ?: MISSING, style = MaterialTheme.typography.bodyMedium.telugu(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(relativeLine(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Text("House: ${v.houseNo ?: MISSING}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            FilledTonalButton(onClick = onAssign, modifier = Modifier.align(Alignment.End).testTag("assignVoter-${v.voterId}")) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Assign to Household")
            }
        }
    }
}

@Preview(name = "Household card", showBackground = true, widthDp = 360)
@Preview(name = "Household card – dark", showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HouseholdCardPreview() {
    EtikoppakaVoterDirectoryTheme {
        Column(Modifier.background(AppTheme.brand.page).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverageSummary(111, HouseholdCoverage(369, 365)) {}
            HouseholdCard(HouseholdSummary(1, "Gutti", "8-144", 4, 2, 1, 3703, 11, locked = false)) {}
            HouseholdCard(HouseholdSummary(2, "Polavarapu", "8-145", 7, 0, 0, 3692, 11, locked = false), "Houses 8-139, 8-145, 8-147 +1") {}
        }
    }
}
