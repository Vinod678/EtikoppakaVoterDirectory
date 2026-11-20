package com.example.etikoppakavoterdirectory.ui.directory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.DirectoryStats
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.data.db.VoterListItem
import com.example.etikoppakavoterdirectory.ui.ImportState
import com.example.etikoppakavoterdirectory.ui.components.AppIcons
import com.example.etikoppakavoterdirectory.ui.components.BulkActionBar
import com.example.etikoppakavoterdirectory.ui.components.BulkExtra
import com.example.etikoppakavoterdirectory.ui.components.BulkTagSheet
import com.example.etikoppakavoterdirectory.ui.components.CountBadge
import com.example.etikoppakavoterdirectory.ui.components.EmptyState
import com.example.etikoppakavoterdirectory.ui.components.HouseholdPickerSheet
import com.example.etikoppakavoterdirectory.ui.components.LightStatusBarIcons
import com.example.etikoppakavoterdirectory.ui.components.rememberReducedMotion
import com.example.etikoppakavoterdirectory.ui.components.MISSING
import com.example.etikoppakavoterdirectory.ui.components.PreviewData
import com.example.etikoppakavoterdirectory.ui.components.SelectionBackHandler
import com.example.etikoppakavoterdirectory.ui.components.SelectionTopBar
import com.example.etikoppakavoterdirectory.ui.components.VoterPhoto
import com.example.etikoppakavoterdirectory.ui.components.relativeLine
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import com.example.etikoppakavoterdirectory.ui.theme.EtikoppakaVoterDirectoryTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu

/** Everything the directory needs to call back into the ViewModels. */
class DirectoryActions(
    val onQueryChange: (String) -> Unit,
    val onFavouritesOnly: (Boolean) -> Unit,
    val onVisitedFilter: (Boolean?) -> Unit,
    val onBlockFilter: (String?) -> Unit,
    val onTagFilter: (Long?) -> Unit,
    val onResetFilters: () -> Unit,
    val onRetryImport: () -> Unit,
    val onToggleFavourite: (Int, Boolean) -> Unit,
    val onToggleVisited: (Int, Boolean) -> Unit,
    /** serial, serials of the current list (navigation order), list label */
    val onOpenVoter: (Int, List<Int>, String) -> Unit,
    val onBulkFavourite: (List<Int>, Boolean) -> Unit,
    val onBulkVisited: (List<Int>, Boolean) -> Unit,
    val onBulkBlock: (List<Int>, String?) -> Unit,
    val onBulkTag: (List<Int>, TagWithCount, Boolean) -> Unit,
    val onAddToHousehold: (List<Int>, Long) -> Unit,
    val onCreateHousehold: (List<Int>) -> Unit,
    val onExport: (String, List<Int>) -> Unit,
)

/** Human label for the current list, used in navigation ("Favourites · 2 / 15") and PDF titles. */
fun filterLabel(filter: VoterFilter, tags: List<TagWithCount>): String {
    val parts = ArrayList<String>()
    if (filter.favouritesOnly) parts += "Favourites"
    when (filter.visited) { true -> parts += "Visited"; false -> parts += "Not visited"; null -> {} }
    filter.block?.let { parts += if (it == Blocks.UNASSIGNED) "Unassigned" else "Block $it" }
    filter.tagId?.let { id -> parts += "Tag: " + (tags.firstOrNull { it.id == id }?.name ?: "?") }
    if (filter.query.isNotBlank()) parts += "Search “${filter.query.trim()}”"
    return if (parts.isEmpty()) "All voters" else parts.joinToString(" · ")
}

// ------------------------------------------------------------------------------------------
// Motion: short tweens (120-250 ms); none at all when the system "Remove animations" /
// animator duration scale 0 setting is on.
// ------------------------------------------------------------------------------------------

private val LocalReducedMotion = compositionLocalOf { false }

@Composable
private fun <T> motion(ms: Int): AnimationSpec<T> = if (LocalReducedMotion.current) snap() else tween(ms)

@Composable
fun DirectoryScreen(
    ward: Int,
    onHome: () -> Unit,
    importState: ImportState,
    filter: VoterFilter,
    items: List<VoterListItem>,
    stats: DirectoryStats?,
    tags: List<TagWithCount>,
    households: List<HouseholdSummary>,
    listState: LazyListState,
    actions: DirectoryActions,
    /**
     * Viewer: total voters, search and voter cards only - no visit counts, favourite / visited
     * filters, stars, visit buttons, multi-selection or block / tag filters.
     */
    readOnly: Boolean = false,
) {
    var showFilters by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(ArrayList<Int>()) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) } // "tags" | "household"
    val selecting = !readOnly && selected.isNotEmpty()
    // Selection only ever contains voters that are still in the list.
    val visibleSerials = remember(items) { items.map { it.voter.voterId } }
    val selection = remember(selected, visibleSerials) { selected.filter { it in visibleSerials.toSet() } }
    fun setSelected(list: List<Int>) { selected = ArrayList(list) }
    SelectionBackHandler(selecting) { setSelected(emptyList()) }
    val label = filterLabel(filter, tags)
    val brand = AppTheme.brand
    val reduced = rememberReducedMotion()

    // A changed filter / search starts at the top (with its "Showing N of M" line); coming back to
    // the screen with the same filter keeps the scroll position.
    var seenFilter by remember { mutableStateOf(filter) }
    LaunchedEffect(filter) {
        if (filter != seenFilter) { seenFilter = filter; listState.scrollToItem(0) }
    }

    // Gentle entrance (fade + 12 dp rise), once per visit of the screen - never while scrolling.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220)) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }

    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Column(
            Modifier
                .fillMaxSize()
                .background(brand.page)
                .graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * rise },
        ) {
            BoxWithConstraints(Modifier.weight(1f)) {
                // On short screens (landscape, very large fonts) the header scrolls away with the list.
                val compact = maxHeight < 520.dp
                // Narrow screen or large font: the Visited pill moves under the names so they keep room.
                val fontScale = LocalDensity.current.fontScale
                val stackedControls = !readOnly && maxWidth - 32.dp - 96.dp - 104.dp * fontScale < 150.dp
                LightStatusBarIcons(enabled = !compact && !selecting)
                val header: @Composable () -> Unit = {
                    Header(ward = ward, onHome = onHome, stats = stats, applyStatusBarPadding = !compact, readOnly = readOnly)
                    if (!readOnly) {
                        QuickFilters(filter = filter, tags = tags, total = stats?.total, onOpenFilters = { showFilters = true }, actions = actions)
                    }
                    DirectorySearchField(filter.query, actions.onQueryChange, topPadding = if (readOnly) 14.dp else 8.dp)
                }
                Column(Modifier.fillMaxSize().then(if (compact || selecting) Modifier.statusBarsPadding() else Modifier)) {
                    if (selecting) {
                        SelectionTopBar(
                            count = selection.size,
                            total = items.size,
                            onClose = { setSelected(emptyList()) },
                            onSelectAll = { setSelected(visibleSerials) },
                            onClearAll = { setSelected(emptyList()) },
                        )
                    } else if (!compact) header()
                    when (importState) {
                        is ImportState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading voters" })
                        }
                        is ImportState.Failed -> EmptyState(
                            icon = Icons.Filled.Warning,
                            title = "Could not load the voter data",
                            message = importState.message,
                            action = { Button(onClick = actions.onRetryImport) { Text("Try again") } },
                        )
                        is ImportState.Ready -> {
                            val filtered = filter.query.isNotBlank() || filter.hasFilters
                            val side = Modifier.padding(horizontal = 16.dp)
                            LazyColumn(
                                state = listState,
                                // Bottom room so the back-to-top button never covers the last card.
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (compact && !selecting) {
                                    item(key = "header", contentType = "header") { Column { header() } }
                                }
                                if (filtered) {
                                    item(key = "count", contentType = "count") {
                                        Text(
                                            "Showing ${items.size} of ${stats?.total ?: "…"} voters",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = side.padding(start = 4.dp),
                                        )
                                    }
                                }
                                if (items.isEmpty() && filtered) {
                                    item(key = "empty", contentType = "empty") {
                                        EmptyState(
                                            icon = Icons.Filled.Search,
                                            title = "No voters match",
                                            message = if (filter.hasFilters) "Try a different search or reset the filters."
                                            else "Try the serial number, part of a name, a house number or an EPIC number.",
                                            action = {
                                                OutlinedButton(onClick = { actions.onResetFilters(); actions.onQueryChange("") }) {
                                                    Text("Clear search and filters")
                                                }
                                            },
                                        )
                                    }
                                }
                                items(items, key = { it.voter.voterId }, contentType = { "voter" }) { item ->
                                    val serial = item.voter.voterId
                                    val isSel = serial in selection
                                    Box(side) {
                                        VoterCard(
                                            item = item,
                                            selected = if (selecting) isSel else null,
                                            stackedControls = stackedControls,
                                            readOnly = readOnly,
                                            onClick = {
                                                if (selecting) setSelected(if (isSel) selection - serial else selection + serial)
                                                else actions.onOpenVoter(serial, visibleSerials, label)
                                            },
                                            onLongClick = { if (!readOnly) setSelected(if (isSel) selection - serial else selection + serial) },
                                            onToggleFavourite = { actions.onToggleFavourite(serial, it) },
                                            onToggleVisited = { actions.onToggleVisited(serial, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Back to the top of a long list (also for block / tag lists opened from those tabs).
                com.example.etikoppakavoterdirectory.ui.components.ScrollToTopButton(
                    listState,
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
            if (selecting) {
                BulkActionBar(
                    count = selection.size,
                    onFavourite = { actions.onBulkFavourite(selection, it) },
                    onVisited = { actions.onBulkVisited(selection, it) },
                    onBlock = { actions.onBulkBlock(selection, it) },
                    onTags = { sheet = "tags" },
                    extras = listOf(
                        BulkExtra("Add to household…") { sheet = "household" },
                        BulkExtra("Export selected to PDF") { actions.onExport("Selected voters", selection) },
                    ),
                )
            }
        }
    }

    if (showFilters && !readOnly) {
        FilterSheet(filter = filter, tags = tags, actions = actions, onDismiss = { showFilters = false })
    }
    if (!readOnly) when (sheet) {
        "tags" -> BulkTagSheet(tags, selection.size, onApply = { t, attach -> sheet = null; actions.onBulkTag(selection, t, attach) }, onDismiss = { sheet = null })
        "household" -> HouseholdPickerSheet(
            title = "Add ${selection.size} to a household",
            households = households,
            onPick = { sheet = null; actions.onAddToHousehold(selection, it.id) },
            onCreateNew = { sheet = null; actions.onCreateHousehold(selection) },
            onDismiss = { sheet = null },
        )
    }
}

// ------------------------------------------------------------------------------------------
// Header: deep-blue band with a softly rounded lower edge; the statistics overlap its edge.
// ------------------------------------------------------------------------------------------

@Composable
private fun Header(ward: Int, onHome: () -> Unit, stats: DirectoryStats?, applyStatusBarPadding: Boolean = true, readOnly: Boolean = false) {
    val brand = AppTheme.brand
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                // Admin: the summary tiles overlap the lower edge. Viewer: no tiles, so the blue
                // header ends right under the ward line (the count is shown there).
                .padding(bottom = if (readOnly) 0.dp else 30.dp)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.verticalGradient(listOf(brand.header, brand.headerEnd))),
        )
        Column(Modifier.fillMaxWidth().then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = if (readOnly) 18.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHome, modifier = Modifier.testTag("homeButton")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All wards (home)", tint = brand.onHeader)
                }
                Column(Modifier.weight(1f)) {
                    com.example.etikoppakavoterdirectory.ui.components.HeaderTitle(
                        "Etikoppaka Voter Directory",
                        MaterialTheme.typography.titleLarge,
                        brand.onHeader,
                    )
                    Text(
                        "Ward $ward · ${stats?.total ?: "…"} Voters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = brand.onHeaderVariant,
                        modifier = Modifier.testTag("wardLabel"),
                    )
                }
            }
            if (!readOnly) SummaryRow(stats, Modifier.padding(horizontal = 16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------
// Statistics (database counts for the selected ward)
// ------------------------------------------------------------------------------------------

/** Real counts from the database: total, visited, not visited. */
@Composable
fun SummaryRow(stats: DirectoryStats?, modifier: Modifier = Modifier) {
    val brand = AppTheme.brand
    val total = stats?.total
    val visited = stats?.visited
    val unvisited = if (total != null && visited != null) total - visited else null
    // Complete numbers at every width / font size (tiles stack when three do not fit).
    com.example.etikoppakavoterdirectory.ui.components.SummaryTiles(
        listOf(
            com.example.etikoppakavoterdirectory.ui.components.SummaryStat(AppIcons.Group, total?.toString() ?: "–", "Total Voters", brand.total, brand.totalContainer),
            com.example.etikoppakavoterdirectory.ui.components.SummaryStat(Icons.Filled.CheckCircle, visited?.toString() ?: "–", "Visited", AppTheme.status.visited, AppTheme.status.visitedContainer),
            com.example.etikoppakavoterdirectory.ui.components.SummaryStat(Icons.Filled.Person, unvisited?.toString() ?: "–", "Not Visited", brand.unvisited, brand.unvisitedContainer),
        ),
        modifier = modifier,
    )
}

// ------------------------------------------------------------------------------------------
// Filter pills: All · Favourites · Visited · Not Visited · (active block / tag) · Blocks & tags
// ------------------------------------------------------------------------------------------

@Composable
private fun PillChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    leadingTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val brand = AppTheme.brand
    val bg by animateColorAsState(if (selected) brand.selected else MaterialTheme.colorScheme.surfaceContainerLowest, motion(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) brand.onSelected else MaterialTheme.colorScheme.onSurface, motion(180), label = "chipFg")
    val border by animateColorAsState(if (selected) brand.selected else MaterialTheme.colorScheme.outlineVariant, motion(180), label = "chipBorder")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, border),
        modifier = modifier.heightIn(min = 38.dp).semantics { this.selected = selected; role = Role.Checkbox },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = if (selected) fg else (leadingTint ?: fg), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun QuickFilters(filter: VoterFilter, tags: List<TagWithCount>, total: Int?, onOpenFilters: () -> Unit, actions: DirectoryActions) {
    val sheetCount = listOf(filter.block != null, filter.tagId != null).count { it }
    val all = !filter.favouritesOnly && filter.visited == null
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillChip(
            selected = all,
            onClick = { actions.onFavouritesOnly(false); actions.onVisitedFilter(null) },
            label = if (total != null) "All ($total)" else "All",
        )
        PillChip(
            selected = filter.favouritesOnly,
            onClick = { actions.onFavouritesOnly(!filter.favouritesOnly) },
            label = "Favourites",
            leading = Icons.Filled.Star,
            leadingTint = AppTheme.status.favourite,
        )
        PillChip(
            selected = filter.visited == true,
            onClick = { actions.onVisitedFilter(if (filter.visited == true) null else true) },
            label = "Visited",
            leading = Icons.Filled.CheckCircle,
            leadingTint = AppTheme.status.visited,
        )
        PillChip(
            selected = filter.visited == false,
            onClick = { actions.onVisitedFilter(if (filter.visited == false) null else false) },
            label = "Not Visited",
            leading = Icons.Outlined.CheckCircle,
            leadingTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        filter.block?.let { b ->
            PillChip(
                selected = true,
                onClick = { actions.onBlockFilter(null) },
                label = if (b == Blocks.UNASSIGNED) "Unassigned" else "Block $b",
                trailing = { Icon(Icons.Filled.Close, contentDescription = "Remove block filter", Modifier.size(16.dp)) },
            )
        }
        filter.tagId?.let { id ->
            PillChip(
                selected = true,
                onClick = { actions.onTagFilter(null) },
                label = tags.firstOrNull { it.id == id }?.name ?: "Tag",
                trailing = { Icon(Icons.Filled.Close, contentDescription = "Remove tag filter", Modifier.size(16.dp)) },
            )
        }
        // Opens the existing block / tag filter sheet.
        PillChip(
            selected = false,
            onClick = onOpenFilters,
            label = "Blocks & tags",
            leading = AppIcons.Layers,
            leadingTint = AppTheme.brand.total,
            trailing = {
                if (sheetCount > 0) CountBadge(sheetCount)
                else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
            },
        )
    }
}

// ------------------------------------------------------------------------------------------
// Search (same matching as before: English / Telugu names, serial, house, EPIC)
// ------------------------------------------------------------------------------------------

@Composable
private fun DirectorySearchField(value: String, onChange: (String) -> Unit, topPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    val focus = LocalFocusManager.current
    val brand = AppTheme.brand
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 10.dp).testTag("wardSearch"),
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text("Search by name, serial, house or EPIC…", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange(""); focus.clearFocus() }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = brand.total,
            focusedLeadingIconColor = brand.total,
        ),
    )
}

// ------------------------------------------------------------------------------------------
// Voter card
// ------------------------------------------------------------------------------------------

/**
 * Voter card: photo, serial, English and Telugu names, relative; favourite star and a
 * Visited / Not Visited pill on the right (both toggle without opening the card). Tapping the rest
 * opens the details; long-press starts multi-selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoterCard(
    item: VoterListItem,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavourite: (Boolean) -> Unit,
    onToggleVisited: (Boolean) -> Unit,
    stackedControls: Boolean = false,
    /** Viewer: no favourite star, no visit button, no long-press selection. */
    readOnly: Boolean = false,
) {
    val v = item.voter
    val thumbPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    val isSel = selected == true
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, motion(120), label = "cardPress")
    val container by animateColorAsState(
        if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        motion(180),
        label = "cardBg",
    )
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = container,
        border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = if (readOnly) null else onLongClick,
                onClickLabel = if (selected != null) "Select or unselect" else "Open details",
                onLongClickLabel = if (readOnly) null else "Select",
            )
            .semantics { if (selected != null) this.selected = isSel },
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                VoterPhoto(
                    fileName = v.photoFilename,
                    contentDescription = "Photo of voter ${v.gpSerialNo}",
                    maxSidePx = thumbPx,
                    modifier = Modifier.size(width = 62.dp, height = 80.dp).clip(RoundedCornerShape(12.dp)),
                )
                if (isSel) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(3.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(56.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "${v.gpSerialNo}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "Serial number ${v.gpSerialNo}" },
                )
                Text(v.nameEn ?: MISSING, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.nameTe ?: MISSING, style = MaterialTheme.typography.bodyLarge.telugu(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        relativeLine(v),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected == null && stackedControls && !readOnly) {
                    Spacer(Modifier.height(4.dp))
                    VisitPill(item.visited, v.gpSerialNo, onToggleVisited)
                }
            }
            if (readOnly) {
                Spacer(Modifier.width(6.dp))
            } else if (selected == null) {
                Column(horizontalAlignment = Alignment.End) {
                    FavouriteStar(item.favourite, v.gpSerialNo, onToggleFavourite)
                    if (!stackedControls) VisitPill(item.visited, v.gpSerialNo, onToggleVisited)
                }
            } else {
                Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusDot(item.favourite, true)
                    Spacer(Modifier.height(10.dp))
                    StatusDot(item.visited, false)
                }
            }
        }
    }
}

/** Gold filled star / grey outline; small scale pop when toggled. 48 dp touch target. */
@Composable
private fun FavouriteStar(checked: Boolean, serial: Int, onToggle: (Boolean) -> Unit) {
    val tint by animateColorAsState(if (checked) AppTheme.status.favourite else MaterialTheme.colorScheme.outline, motion(180), label = "starTint")
    val pop = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        if (!reduced) {
            pop.snapTo(0.75f)
            pop.animateTo(1f, tween(200))
        }
    }
    IconButton(onClick = { onToggle(!checked) }) {
        Icon(
            if (checked) Icons.Filled.Star else AppIcons.StarBorder,
            contentDescription = if (checked) "Favourite, voter $serial. Tap to remove from Favourites" else "Add voter $serial to Favourites",
            tint = tint,
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

/**
 * Visited: green tick + "Visited" on light green. Not visited: grey outlined tick + "Not Visited"
 * on light grey. Width is reserved for the longer label so the card never jumps when it changes.
 */
@Composable
private fun VisitPill(visited: Boolean, serial: Int, onToggle: (Boolean) -> Unit) {
    val status = AppTheme.status
    val brand = AppTheme.brand
    val bg by animateColorAsState(if (visited) status.visitedContainer else brand.notVisitedPill, motion(200), label = "pillBg")
    val fg by animateColorAsState(if (visited) status.onVisitedContainer else brand.onNotVisitedPill, motion(200), label = "pillFg")
    val reduced = LocalReducedMotion.current
    Surface(
        onClick = { onToggle(!visited) },
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = if (visited) "Visited, voter $serial. Tap to mark not visited" else "Mark voter $serial as visited"
                stateDescription = if (visited) "Visited" else "Not visited"
                role = Role.Switch
            },
    ) {
        Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = visited,
                transitionSpec = {
                    if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                    else (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)) togetherWith fadeOut(tween(120))
                },
                label = "pillIcon",
            ) { on ->
                Icon(
                    if (on) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (on) status.visited else brand.onNotVisitedPill.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.clearAndSetSemantics {}) {
                // Invisible longer label keeps the pill width constant.
                Text("Not Visited", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.sp), fontWeight = FontWeight.Medium, maxLines = 1, color = Color.Transparent)
                Text(
                    if (visited) "Visited" else "Not Visited",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = fg,
                )
            }
        }
    }
}

/** Read-only marker shown while selecting (taps select the card instead of toggling). */
@Composable
private fun StatusDot(on: Boolean, favourite: Boolean) {
    val icon = if (favourite) {
        if (on) Icons.Filled.Star else AppIcons.StarBorder
    } else {
        if (on) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
    }
    val tint = when {
        !on -> MaterialTheme.colorScheme.outlineVariant
        favourite -> AppTheme.status.favourite
        else -> AppTheme.status.visited
    }
    Icon(icon, contentDescription = if (favourite) (if (on) "Favourite" else null) else (if (on) "Visited" else null), tint = tint, modifier = Modifier.size(22.dp))
}

// ------------------------------------------------------------------------------------------
// Previews
// ------------------------------------------------------------------------------------------

@Preview(name = "Voter card", showBackground = true, widthDp = 360)
@Preview(name = "Voter card – dark", showBackground = true, widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Voter card – large font", showBackground = true, widthDp = 360, fontScale = 1.5f)
@Composable
private fun VoterCardPreview() {
    EtikoppakaVoterDirectoryTheme {
        Column(Modifier.background(AppTheme.brand.page).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow(DirectoryStats(369, 12, 40, 0))
            VoterCard(VoterListItem(PreviewData.voter, favourite = true, visited = true, block = "C", hasNote = true, tagCount = 1), null, {}, {}, {}, {})
            VoterCard(VoterListItem(PreviewData.longNameVoter, favourite = false, visited = false, block = null, hasNote = false, tagCount = 0), null, {}, {}, {}, {}, stackedControls = true)
            VoterCard(VoterListItem(PreviewData.longNameVoter, favourite = false, visited = false, block = null, hasNote = false, tagCount = 0), true, {}, {}, {}, {})
        }
    }
}

