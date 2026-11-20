package com.example.etikoppakavoterdirectory.ui.export

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.db.BlockCount
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.export.PdfLayout
import com.example.etikoppakavoterdirectory.export.PdfOptions
import com.example.etikoppakavoterdirectory.ui.ExportScope
import com.example.etikoppakavoterdirectory.ui.ExportStatus
import com.example.etikoppakavoterdirectory.ui.ExportViewModel
import com.example.etikoppakavoterdirectory.ui.components.SearchField
import com.example.etikoppakavoterdirectory.ui.components.SectionCard
import com.example.etikoppakavoterdirectory.ui.sortBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline PDF export: choose voters (a list, or several blocks and households as separate
 * sections), layout and fields, confirm the privacy notice, then preview, save (Storage Access
 * Framework) or share (Android share sheet). Nothing is sent automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    vm: ExportViewModel,
    /** The open ward; null when the export was started outside a ward (e.g. from the global search). */
    ward: Int?,
    preset: ExportScope.Preset?,
    filtered: ExportScope.Preset?,
    households: List<HouseholdSummary>,
    blockCounts: List<BlockCount>,
    blockLabels: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val count by vm.count.collectAsState()

    var scopeKey by rememberSaveable { mutableStateOf(if (preset != null) "preset" else if (ward == null) "allwards" else "all") }
    var pickedBlocks by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var pickedHouseholds by rememberSaveable { mutableStateOf(ArrayList<Long>()) }
    var blocksOpen by rememberSaveable { mutableStateOf(true) }
    var householdsOpen by rememberSaveable { mutableStateOf(false) }
    var householdQuery by rememberSaveable { mutableStateOf("") }
    var layoutName by rememberSaveable { mutableStateOf(PdfLayout.COMPACT.name) }
    var opts by rememberSaveable { mutableStateOf(BooleanArray(DEFAULTS.size).also { d -> DEFAULTS.forEachIndexed { i, v -> d[i] = v } }) }
    var confirm by rememberSaveable { mutableStateOf(false) }

    val validHouseholds = remember(households) { households.map { it.id }.toSet() }
    val scope: ExportScope = when {
        scopeKey == "preset" && preset != null -> preset
        scopeKey == "filtered" && filtered != null -> filtered
        scopeKey == "allwards" || ward == null -> ExportScope.All(null)
        scopeKey == "fav" -> ExportScope.Favourites(ward)
        scopeKey == "visited" -> ExportScope.Visited(ward)
        scopeKey == "notvisited" -> ExportScope.NotVisited(ward)
        scopeKey == "selection" -> ExportScope.Selection(ward, sortBlocks(pickedBlocks), pickedHouseholds.filter { it in validHouseholds })
        else -> ExportScope.All(ward)
    }
    val options = PdfOptions(
        layout = PdfLayout.valueOf(layoutName),
        relativeNames = opts[0], houseNo = opts[1], ageGender = opts[2], assemblySerial = opts[3],
        favourite = opts[4], visited = opts[5], tags = opts[6], epic = opts[7], notes = opts[8],
        householdNewPage = opts[9], householdsLargestFirst = opts[10],
    )
    LaunchedEffect(scope, options.householdsLargestFirst) { vm.updateCount(scope, options) }

    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.save(context.contentResolver, uri)
    }
    fun select(blocks: List<String> = pickedBlocks, hh: List<Long> = pickedHouseholds) {
        pickedBlocks = ArrayList(blocks); pickedHouseholds = ArrayList(hh)
        scopeKey = if (blocks.isEmpty() && hh.isEmpty()) (if (preset != null) "preset" else "all") else "selection"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (status is ExportStatus.Ready) "PDF preview" else "Export PDF", maxLines = 1)
                        Text(
                            if (ward == null) "All wards" else "Ward $ward",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (status is ExportStatus.Ready) vm.reset() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = status) {
            is ExportStatus.Ready -> PreviewPane(
                ready = s,
                modifier = Modifier.padding(padding),
                onPreview = {
                    vm.viewIntent(context)?.let {
                        try { context.startActivity(it) } catch (e: ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, "No PDF viewer app is installed. The pages are shown below.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSave = { saveDoc.launch(s.file.name) },
                onShare = {
                    vm.shareIntent(context)?.let {
                        try { context.startActivity(it) } catch (e: ActivityNotFoundException) { /* no app can receive PDFs */ }
                    }
                },
                onChange = vm::reset,
            )
            is ExportStatus.Working -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(s.message)
                }
            }
            ExportStatus.Idle -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(title = "Voters to export") {
                    val choices = buildList {
                        preset?.let { add("preset" to "${it.label} (${it.ids.size})") }
                        filtered?.let { add("filtered" to "Current filtered results (${it.ids.size})") }
                        if (ward != null) {
                            add("all" to "All voters of Ward $ward")
                            add("fav" to "Favourites")
                            add("visited" to "Visited")
                            add("notvisited" to "Not visited")
                            add("selection" to "Selected blocks and households (below)")
                        }
                        add("allwards" to "All imported wards (multi-ward export)")
                    }
                    choices.forEach { (key, label) -> Radio(label, scopeKey == key) { scopeKey = key } }
                    Text(
                        count?.let { "$it unique voter${if (it == 1) "" else "s"} will be exported" } ?: "Counting…",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (count == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp).testTag("uniqueCount"),
                    )
                }
                if (ward != null) {
                    // ---------------- Blocks ----------------
                    val byBlock = blockCounts.associateBy { it.block }
                    val allBlocks = Blocks.ALL + Blocks.UNASSIGNED
                    ExpandableSection(
                        title = "Blocks",
                        summary = if (pickedBlocks.isEmpty()) "None selected" else "${pickedBlocks.size} selected",
                        open = blocksOpen,
                        onToggle = { blocksOpen = !blocksOpen },
                    ) {
                        SelectAllRow(
                            onSelectAll = { select(blocks = allBlocks.filter { (byBlock[it]?.total ?: 0) > 0 }) },
                            onClearAll = { select(blocks = emptyList()) },
                        )
                        allBlocks.forEach { b ->
                            val n = byBlock[b]?.total ?: 0
                            val label = if (b == Blocks.UNASSIGNED) "Unassigned (no block)" else "Block $b" + (blockLabels[b]?.let { " — $it" } ?: "")
                            Check("$label · $n voter${if (n == 1) "" else "s"}", b in pickedBlocks, enabled = n > 0) { on ->
                                select(blocks = if (on) pickedBlocks + b else pickedBlocks - b)
                            }
                        }
                        Text(
                            "Each block starts on a new page, in A, B, C, D order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    // ---------------- Households ----------------
                    ExpandableSection(
                        title = "Households",
                        summary = if (pickedHouseholds.isEmpty()) "None selected" else "${pickedHouseholds.count { it in validHouseholds }} selected",
                        open = householdsOpen,
                        onToggle = { householdsOpen = !householdsOpen },
                    ) {
                        SearchField(householdQuery, { householdQuery = it }, "Search family, house no.")
                        val shown = households
                            .filter { householdQuery.isBlank() || it.name.contains(householdQuery.trim(), true) || it.houseNo?.contains(householdQuery.trim(), true) == true }
                            .sortedWith(compareByDescending<HouseholdSummary> { it.members }.thenBy { it.name.lowercase() })
                        SelectAllRow(
                            onSelectAll = { select(hh = (pickedHouseholds + shown.map { it.id }).distinct()) },
                            onClearAll = { select(hh = pickedHouseholds - shown.map { it.id }.toSet()) },
                            allLabel = if (householdQuery.isBlank()) "Select all" else "Select all shown",
                        )
                        if (shown.isEmpty()) {
                            Text("No households match.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                        }
                        shown.forEach { h ->
                            Check(
                                h.name + " · " + listOfNotNull("${h.members} member${if (h.members == 1) "" else "s"}", h.houseNo?.let { "House $it" }).joinToString(" · "),
                                h.id in pickedHouseholds,
                            ) { on -> select(hh = if (on) pickedHouseholds + h.id else pickedHouseholds - h.id) }
                        }
                    }
                    if (scopeKey == "selection" && pickedHouseholds.isNotEmpty()) {
                        SectionCard(title = "Household sections") {
                            Check("Largest households first", opts[10]) { opts = opts.copyOf().also { a -> a[10] = it } }
                            Check("Start each household on a new page", opts[9]) { opts = opts.copyOf().also { a -> a[9] = it } }
                            Text(
                                "Members of a household are kept together on one page whenever they fit. " +
                                    (if (pickedBlocks.isNotEmpty()) "Voters already listed under a selected block are not repeated; the household heading lists them instead." else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                        }
                    }
                }
                SectionCard(title = "Layout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LayoutOption("Compact directory", "Two cards per row, many per page", layoutName == PdfLayout.COMPACT.name, Modifier.weight(1f)) {
                            layoutName = PdfLayout.COMPACT.name
                        }
                        LayoutOption("Detailed profiles", "Larger cards with more details", layoutName == PdfLayout.DETAILED.name, Modifier.weight(1f)) {
                            layoutName = PdfLayout.DETAILED.name
                        }
                    }
                }
                SectionCard(title = "Include") {
                    val labels = listOf("Relative names", "House number", "Age and gender", "Assembly roll serial", "Favourite status", "Visited status", "Tags")
                    labels.forEachIndexed { i, l -> Check(l, opts[i]) { opts = opts.copyOf().also { a -> a[i] = it } } }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("Sensitive – off by default", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Check("EPIC number", opts[7]) { opts = opts.copyOf().also { a -> a[7] = it } }
                    Check("Private notes", opts[8]) { opts = opts.copyOf().also { a -> a[8] = it } }
                }
                Button(onClick = { confirm = true }, enabled = count != null && count!! > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Create PDF")
                }
                Text(
                    "The PDF is created on this phone. Nothing is uploaded or shared unless you choose Save or Share.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text("Personal information") },
            text = {
                Text(
                    "This PDF will contain personal information of ${count ?: 0} voter(s): photos and names" +
                        (if (options.epic) ", EPIC numbers" else "") + (if (options.notes) ", your private notes" else "") +
                        ". Store it safely and share it only with people who are entitled to see it. " +
                        "The app never uploads or shares it by itself."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; vm.generate(scope, options) }) { Text("Create PDF") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** relative names, house no., age/gender, assembly serial, favourite, visited, tags, EPIC, notes,
 *  household on new page, largest households first */
private val DEFAULTS = booleanArrayOf(true, true, true, false, false, false, false, false, false, false, true)

/** Card with a header that expands / collapses its content. */
@Composable
private fun ExpandableSection(title: String, summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
    }
}

@Composable
private fun SelectAllRow(onSelectAll: () -> Unit, onClearAll: () -> Unit, allLabel: String = "Select all") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedButton(onClick = onSelectAll) { Text(allLabel) }
        TextButton(onClick = onClearAll) { Text("Clear all") }
    }
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Check(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun LayoutOption(title: String, text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Preview of the finished PDF (rendered on the device with PdfRenderer)
// ------------------------------------------------------------------------------------------

@Composable
private fun PreviewPane(ready: ExportStatus.Ready, modifier: Modifier, onPreview: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, onChange: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(ready.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${ready.records} voter${if (ready.records == 1) "" else "s"} · ${ready.pages} page${if (ready.pages == 1) "" else "s"} · A4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Preview PDF", maxLines = 1) }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save PDF", maxLines = 1) }
                    FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share PDF", maxLines = 1)
                    }
                }
                TextButton(onClick = onChange) { Text("Change export settings") }
            }
        }
        PdfPages(ready.file, ready.pages)
    }
}

@Composable
private fun PdfPages(file: File, pageCount: Int) {
    val renderer = remember(file) {
        try {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        } catch (e: Exception) {
            null
        }
    }
    val lock = remember { Mutex() }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        Text("Preview not available.", Modifier.padding(16.dp))
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(200, 1400)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items((0 until pageCount).toList(), key = { it }) { index ->
                val bitmap by produceState<Bitmap?>(null, index, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        lock.withLock {
                            try {
                                renderer.openPage(index).use { page ->
                                    val h = (widthPx.toFloat() * page.height / page.width).toInt()
                                    Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
                Surface(shadowElevation = 2.dp, color = Color.White, modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f)) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = "Page ${index + 1} of $pageCount", modifier = Modifier.fillMaxSize())
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
                Text(
                    "Page ${index + 1} of $pageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { heading() },
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
