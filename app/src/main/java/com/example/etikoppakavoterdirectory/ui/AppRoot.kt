package com.example.etikoppakavoterdirectory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.etikoppakavoterdirectory.VoterDirectoryApp
import com.example.etikoppakavoterdirectory.auth.Role
import com.example.etikoppakavoterdirectory.ui.blocks.BlocksScreen
import com.example.etikoppakavoterdirectory.ui.components.FitLabel
import com.example.etikoppakavoterdirectory.ui.detail.VoterPagerScreen
import com.example.etikoppakavoterdirectory.ui.directory.DirectoryActions
import com.example.etikoppakavoterdirectory.ui.directory.DirectoryScreen
import com.example.etikoppakavoterdirectory.ui.directory.filterLabel
import com.example.etikoppakavoterdirectory.ui.export.ExportScreen
import com.example.etikoppakavoterdirectory.ui.export.ImageExportScreen
import com.example.etikoppakavoterdirectory.ui.home.HomeScreen
import com.example.etikoppakavoterdirectory.ui.households.HouseholdDetailScreen
import com.example.etikoppakavoterdirectory.ui.households.HouseholdBrowseState
import com.example.etikoppakavoterdirectory.ui.households.HouseholdsScreen
import com.example.etikoppakavoterdirectory.ui.households.UnassignedVotersScreen
import com.example.etikoppakavoterdirectory.ui.settings.SettingsScreen
import com.example.etikoppakavoterdirectory.ui.tags.TagsScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Voters("Voters", Icons.Filled.Person),
    Households("Households", Icons.Filled.Home),
    Blocks("Blocks", Icons.Filled.LocationOn),
    Tags("Tags", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Screens opened on top of the home screen or a ward. Stored as strings in a saveable back stack
 * so the open voter, its list position, an open household or an export screen survive rotation.
 * Voter routes hold stable voter ids (ward + serial), so lists may mix wards (global search).
 */
private sealed interface Route {
    fun encode(): String

    /** Voter pager: [ids] is the list order captured when it was opened. */
    data class Voter(val label: String, val current: Int, val ids: List<Int>, val fromSearch: Boolean = false) : Route {
        override fun encode() = listOf("V", label, current.toString(), ids.joinToString(","), if (fromSearch) "1" else "0").joinToString(SEP)
    }
    data class Household(val id: Long) : Route {
        override fun encode() = listOf("H", id.toString()).joinToString(SEP)
    }
    data class Export(val presetLabel: String?, val ids: List<Int>) : Route {
        override fun encode() = listOf("E", presetLabel ?: "", ids.joinToString(",")).joinToString(SEP)
    }
    data class Image(val householdId: Long, val share: Boolean) : Route {
        override fun encode() = listOf("I", householdId.toString(), if (share) "1" else "0").joinToString(SEP)
    }
    /** All unassigned voters of a ward (from the Households tab's "Unassigned Voters" tile). */
    data class Unassigned(val ward: Int) : Route {
        override fun encode() = listOf("U", ward.toString()).joinToString(SEP)
    }
    /** Viewer settings (About and Sign out), opened from the home screen. */
    data object Settings : Route {
        override fun encode() = "S"
    }

    /** Screens a Viewer may open. Everything else is Admin-only and is never shown to a Viewer. */
    fun allowedFor(role: Role) = role == Role.ADMIN || this is Voter || this is Settings

    companion object {
        const val SEP = "\u001F"
        private fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(",").mapNotNull { it.toIntOrNull() }
        fun decode(s: String): Route? {
            val p = s.split(SEP)
            return when (p.firstOrNull()) {
                "V" -> if (p.size >= 4) Voter(p[1], p[2].toInt(), ints(p[3]), p.getOrNull(4) == "1") else null
                "H" -> p.getOrNull(1)?.toLongOrNull()?.let { Household(it) }
                "E" -> if (p.size == 3) Export(p[1].ifEmpty { null }, ints(p[2])) else null
                "I" -> p.getOrNull(1)?.toLongOrNull()?.let { Image(it, p.getOrNull(2) == "1") }
                "U" -> p.getOrNull(1)?.toIntOrNull()?.let { Unassigned(it) }
                "S" -> Settings
                else -> null
            }
        }
    }
}

/**
 * The signed-in app. [role] decides what is shown: Admin gets every screen; Viewer gets the home
 * screen, ward voter lists, read-only voter details and a small settings page. [session] is new for
 * every sign-in, which resets searches, filters and navigation.
 */
@Composable
fun AppRoot(role: Role, session: Long) {
    val app = LocalContext.current.applicationContext as VoterDirectoryApp
    val directoryVm: DirectoryViewModel = viewModel(factory = DirectoryViewModel.Factory)
    val tagsVm: TagsViewModel = viewModel(factory = TagsViewModel.Factory)
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val edit: EditViewModel = viewModel(factory = EditViewModel.Factory)
    val exportVm: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
    val imageVm: ImageExportViewModel = viewModel(factory = ImageExportViewModel.Factory)
    val viewer = role == Role.VIEWER
    // A fresh sign-in starts from the home screen with empty searches and no filters.
    remember(session) {
        if (directoryVm.startSession(session)) { exportVm.discard(); imageVm.discard() }
    }

    val importState by directoryVm.importState.collectAsState()
    val ward by directoryVm.ward.collectAsState()
    val filter by directoryVm.filter.collectAsState()
    val items by directoryVm.items.collectAsState()
    val stats by directoryVm.stats.collectAsState()
    val blockCounts by directoryVm.blockCounts.collectAsState()
    val tags by directoryVm.tags.collectAsState()
    val households by directoryVm.households.collectAsState()
    val blockLabels by directoryVm.blockLabels.collectAsState()
    val wards by directoryVm.wards.collectAsState()
    val globalQuery by directoryVm.globalQuery.collectAsState()
    val globalResults by directoryVm.globalResults.collectAsState()

    var tabName by rememberSaveable { mutableStateOf(Tab.Voters.name) }
    // A Viewer has no tabs: always the voter list.
    val tab = if (viewer) Tab.Voters else Tab.valueOf(tabName)
    var stack by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    // Households tab browsing state (search, sort, list position): kept while moving inside the
    // Households tab (household details, members, voters, assignment, back), reset on a tab switch.
    val householdBrowse = rememberSaveable(saver = HouseholdBrowseState.Saver) { HouseholdBrowseState() }
    /** The single place where the bottom-navigation tab changes. Leaving Households resets its browsing state. */
    fun selectTab(t: Tab) {
        if (tab == Tab.Households && t != Tab.Households) householdBrowse.reset()
        tabName = t.name
    }
    fun push(r: Route) { stack = ArrayList(stack + r.encode()) }
    fun pop() { stack = ArrayList(stack.dropLast(1)) }
    fun replaceTop(r: Route) { if (stack.isNotEmpty()) stack = ArrayList(stack.dropLast(1) + r.encode()) }
    fun goToTab(t: Tab) { stack = ArrayList(); selectTab(t) }
    /** Back to the 14-ward home screen. The ward's filters are remembered for when it is opened again. */
    fun goHome() { stack = ArrayList(); householdBrowse.reset(); directoryVm.setWard(null) }
    fun openWard(w: Int) { stack = ArrayList(); householdBrowse.reset(); tabName = Tab.Voters.name; directoryVm.setWard(w) }

    val listState = rememberLazyListState()
    val homeGridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val directoryActions = remember(directoryVm, edit) {
        DirectoryActions(
            onQueryChange = directoryVm::setQuery,
            onFavouritesOnly = directoryVm::setFavouritesOnly,
            onVisitedFilter = directoryVm::setVisitedFilter,
            onBlockFilter = directoryVm::setBlockFilter,
            onTagFilter = directoryVm::setTagFilter,
            onResetFilters = directoryVm::clearFilters,
            onRetryImport = directoryVm::runImport,
            onToggleFavourite = { s, on -> edit.setFavourite(s, on) },
            // List pill: same visit semantics, plus a short "Undo" that restores the exact previous state.
            onToggleVisited = { s, on ->
                edit.setVisitedUndoable(s, on) { undo ->
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val r = snackbar.showSnackbar(
                            message = if (on) "Marked as visited" else "Marked as not visited",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo()
                    }
                }
            },
            onOpenVoter = { id, list, label -> push(Route.Voter(label, id, list)) },
            onBulkFavourite = { s, on -> edit.bulkFavourite(s, on) },
            onBulkVisited = { s, on -> edit.bulkVisited(s, on) },
            onBulkBlock = { s, b -> edit.bulkBlock(s, b) },
            onBulkTag = { s, t, on -> edit.bulkTag(s, t.id, t.name, on) },
            onAddToHousehold = { s, id -> edit.addToHousehold(id, s) },
            onCreateHousehold = { s -> edit.createHousehold(null, s, onDone = { id -> push(Route.Household(id)) }) },
            onExport = { label, s -> push(Route.Export(label, s)) },
        )
    }

    // Viewer: searching and opening voters only; nothing that changes data.
    val viewerActions = remember(directoryVm) {
        DirectoryActions(
            onQueryChange = directoryVm::setQuery,
            onFavouritesOnly = {},
            onVisitedFilter = {},
            onBlockFilter = {},
            onTagFilter = {},
            onResetFilters = directoryVm::clearFilters,
            onRetryImport = directoryVm::runImport,
            onToggleFavourite = { _, _ -> },
            onToggleVisited = { _, _ -> },
            onOpenVoter = { id, list, label -> push(Route.Voter(label, id, list)) },
            onBulkFavourite = { _, _ -> },
            onBulkVisited = { _, _ -> },
            onBulkBlock = { _, _ -> },
            onBulkTag = { _, _, _ -> },
            onAddToHousehold = { _, _ -> },
            onCreateHousehold = {},
            onExport = { _, _ -> },
        )
    }

    LaunchedEffect(Unit) {
        app.messages.events.collect { m ->
            snackbar.showSnackbar(
                message = m.text,
                withDismissAction = m.isError,
                duration = if (m.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    // ---------------- screens on top of home / the ward tabs ----------------
    // Admin-only screens can never be shown in a Viewer session (whatever is on the back stack).
    val top = stack.lastOrNull()?.let(Route::decode)?.takeIf { it.allowedFor(role) }
    if (viewer && stack.isNotEmpty() && top == null) {
        LaunchedEffect(stack) { stack = ArrayList(stack.filter { Route.decode(it)?.allowedFor(role) == true }) }
    }
    if (top != null) {
        BackHandler {
            when (top) {
                is Route.Export -> exportVm.discard()
                is Route.Image -> imageVm.discard()
                else -> {}
            }
            pop()
        }
        Box(Modifier.fillMaxSize()) {
            when (top) {
                is Route.Voter -> VoterPagerScreen(
                    label = top.label,
                    serials = top.ids,
                    initialSerial = top.current,
                    edit = edit,
                    allTags = tags,
                    onSerialChange = { s -> if (s != top.current) replaceTop(top.copy(current = s)) },
                    onBack = { pop() },
                    onManageTags = { if (ward != null && !viewer) goToTab(Tab.Tags) },
                    onOpenHousehold = { id -> if (!viewer) push(Route.Household(id)) },
                    onHome = { goHome() },
                    readOnly = viewer,
                )
                Route.Settings -> SettingsScreen(
                    vm = settingsVm,
                    ward = null,
                    importState = importState,
                    onRetryImport = directoryVm::runImport,
                    onExportPdf = {},
                    onHome = { pop() },
                    onLogout = app.auth::logout,
                    viewer = viewer,
                )
                is Route.Household -> HouseholdDetailScreen(
                    id = top.id,
                    edit = edit,
                    allTags = tags,
                    onBack = { pop() },
                    onOpenVoter = { id, list, label -> push(Route.Voter(label, id, list)) },
                    onExport = { label, s -> push(Route.Export(label, s)) },
                    onExportImage = { id, share -> push(Route.Image(id, share)) },
                )
                is Route.Export -> {
                    val currentLabel = filterLabel(filter, tags)
                    val filtered = if (ward != null && (filter.hasFilters || filter.query.isNotBlank())) {
                        ExportScope.Preset("Filtered: $currentLabel", items.map { it.voter.voterId })
                    } else null
                    ExportScreen(
                        vm = exportVm,
                        ward = ward,
                        preset = top.presetLabel?.let { ExportScope.Preset(it, top.ids) },
                        filtered = filtered,
                        households = households,
                        blockCounts = blockCounts,
                        blockLabels = blockLabels,
                        onBack = { exportVm.discard(); pop() },
                    )
                }
                is Route.Unassigned -> UnassignedVotersScreen(
                    ward = top.ward,
                    households = households,
                    edit = edit,
                    onBack = { pop() },
                )
                is Route.Image -> {
                    val name by remember(top.householdId) { edit.repository.household(top.householdId) }.collectAsState(initial = null)
                    ImageExportScreen(
                        vm = imageVm,
                        householdId = top.householdId,
                        householdName = name?.name,
                        shareWhenReady = top.share,
                        onBack = { imageVm.discard(); pop() },
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
        return
    }

    // ---------------- home: all 14 wards ----------------
    val w = ward
    if (w == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                HomeScreen(
                    importState = importState,
                    wards = wards,
                    query = globalQuery,
                    results = globalResults,
                    gridState = homeGridState,
                    onQueryChange = directoryVm::setGlobalQuery,
                    onOpenWard = ::openWard,
                    onMissingWard = { n -> app.messages.show("Ward $n: no data imported yet.") },
                    onOpenResult = { id, ids ->
                        push(Route.Voter("Search “${globalQuery.trim()}” · all wards", id, ids, fromSearch = true))
                    },
                    onRetryImport = directoryVm::runImport,
                    onLogout = app.auth::logout,
                    onSettings = if (viewer) ({ push(Route.Settings) }) else null,
                )
            }
        }
        return
    }

    // ---------------- Viewer: one ward's voter list, read-only, no tabs ----------------
    if (viewer) {
        BackHandler { goHome() }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                DirectoryScreen(
                    ward = w,
                    onHome = ::goHome,
                    importState = importState,
                    filter = filter,
                    items = items,
                    stats = stats,
                    tags = emptyList(),
                    households = emptyList(),
                    listState = listState,
                    actions = viewerActions,
                    readOnly = true,
                )
            }
        }
        return
    }

    // ---------------- one ward: the existing tabs ----------------
    // Back: other tab -> Voters; Voters -> home (all wards).
    BackHandler { if (tab != Tab.Voters) selectTab(Tab.Voters) else goHome() }
    val serialRange by produceState<IntRange?>(null, w, importState) { value = directoryVm.serialRange() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    val brand = com.example.etikoppakavoterdirectory.ui.theme.AppTheme.brand
                    Column {
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = t == tab,
                                    onClick = { selectTab(t) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { FitLabel(t.label, MaterialTheme.typography.labelMedium) },
                                    alwaysShowLabel = true,
                                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        selectedIconColor = brand.navSelected,
                                        selectedTextColor = brand.navSelected,
                                        indicatorColor = brand.navIndicator,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
            Row(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
                if (wide) {
                    NavigationRail {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = t == tab,
                                onClick = { selectTab(t) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tab) {
                        Tab.Voters -> DirectoryScreen(
                            ward = w,
                            onHome = ::goHome,
                            importState = importState,
                            filter = filter,
                            items = items,
                            stats = stats,
                            tags = tags,
                            households = households,
                            listState = listState,
                            actions = directoryActions,
                        )
                        Tab.Households -> HouseholdsScreen(
                            ward = w,
                            households = households,
                            edit = edit,
                            browse = householdBrowse,
                            onOpen = { id -> push(Route.Household(id)) },
                            onHome = ::goHome,
                            onShowUnassigned = { n ->
                                if (n > 0) push(Route.Unassigned(w))
                                else app.messages.show("All voters are assigned to households.")
                            },
                        )
                        Tab.Blocks -> BlocksScreen(
                            ward = w,
                            serialRange = serialRange,
                            onHome = ::goHome,
                            counts = blockCounts,
                            labels = blockLabels,
                            onSetLabel = directoryVm::setBlockLabel,
                            onOpenBlock = { block ->
                                directoryVm.clearFilters()
                                directoryVm.setBlockFilter(block)
                                selectTab(Tab.Voters)
                                scope.launch { listState.scrollToItem(0) }
                            },
                            onClearBlock = directoryVm::clearBlock,
                            onAssignRange = directoryVm::assignRange,
                        )
                        Tab.Tags -> TagsScreen(
                            ward = w,
                            onHome = ::goHome,
                            tags = tags,
                            onCreate = tagsVm::create,
                            onRename = tagsVm::rename,
                            onDelete = { id, done -> tagsVm.delete(id) { directoryVm.onTagDeleted(id); done() } },
                            onShowVoters = { id ->
                                directoryVm.clearFilters()
                                directoryVm.setTagFilter(id)
                                selectTab(Tab.Voters)
                                scope.launch { listState.scrollToItem(0) }
                            },
                            onAddDefaults = edit::addDefaultTags,
                        )
                        Tab.Settings -> SettingsScreen(
                            vm = settingsVm,
                            ward = w,
                            importState = importState,
                            onRetryImport = directoryVm::runImport,
                            onExportPdf = { push(Route.Export(null, emptyList())) },
                            onHome = ::goHome,
                            onLogout = app.auth::logout,
                        )
                    }
                }
            }
        }
    }
}
