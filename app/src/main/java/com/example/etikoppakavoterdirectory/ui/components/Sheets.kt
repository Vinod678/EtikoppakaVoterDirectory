package com.example.etikoppakavoterdirectory.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.etikoppakavoterdirectory.VoterDirectoryApp
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme

// ------------------------------------------------------------------------------------------
// Selection mode: top bar + bulk action bar
// ------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, total: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onClearAll: () -> Unit) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") } },
        title = { Text("$count selected") },
        actions = {
            if (count < total) TextButton(onClick = onSelectAll) { Text("Select all") }
            else TextButton(onClick = onClearAll) { Text("Clear") }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

/** Extra item for the "More" menu of [BulkActionBar]. */
class BulkExtra(val label: String, val onClick: () -> Unit)

/**
 * Actions for the selected voters. Only the chosen marker changes: e.g. "Mark visited" never
 * touches favourites, and voters not selected keep their own state.
 */
@Composable
fun BulkActionBar(
    count: Int,
    onFavourite: (Boolean) -> Unit,
    onVisited: (Boolean) -> Unit,
    onBlock: (String?) -> Unit,
    onTags: () -> Unit,
    extras: List<BulkExtra>,
) {
    var confirmUnvisit by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MenuAction(Icons.Filled.Star, "Favourite", count > 0, AppTheme.status.favourite, Modifier.weight(1f)) { close ->
                DropdownMenuItem(text = { Text("Add to Favourites") }, onClick = { close(); onFavourite(true) })
                DropdownMenuItem(text = { Text("Remove from Favourites") }, onClick = { close(); onFavourite(false) })
            }
            MenuAction(Icons.Filled.CheckCircle, "Visited", count > 0, AppTheme.status.visited, Modifier.weight(1f)) { close ->
                DropdownMenuItem(text = { Text("Mark visited") }, onClick = { close(); onVisited(true) })
                DropdownMenuItem(text = { Text("Mark not visited") }, onClick = { close(); confirmUnvisit = true })
            }
            MenuAction(Icons.Filled.LocationOn, "Block", count > 0, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { close ->
                Blocks.ALL.forEach { b -> DropdownMenuItem(text = { Text("Block $b") }, onClick = { close(); onBlock(b) }) }
                DropdownMenuItem(text = { Text("Remove block") }, onClick = { close(); onBlock(null) })
            }
            BarButton(Icons.AutoMirrored.Filled.List, "Tags", count > 0, MaterialTheme.colorScheme.primary, Modifier.weight(1f), onTags)
            MenuAction(Icons.Filled.MoreVert, "More", count > 0, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { close ->
                extras.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { close(); e.onClick() }) }
            }
        }
    }
    if (confirmUnvisit) {
        AlertDialog(
            onDismissRequest = { confirmUnvisit = false },
            title = { Text("Mark $count not visited?") },
            text = { Text("Their visit status and any recorded visit times will be cleared. Favourites, notes and tags are not changed.") },
            confirmButton = { TextButton(onClick = { confirmUnvisit = false; onVisited(false) }) { Text("Mark not visited") } },
            dismissButton = { TextButton(onClick = { confirmUnvisit = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        FitLabel(label, MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    modifier: Modifier,
    items: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        BarButton(icon, label, enabled, tint, Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

// ------------------------------------------------------------------------------------------
// Tags
// ------------------------------------------------------------------------------------------

/** Adds or removes one tag for many voters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkTagSheet(tags: List<TagWithCount>, count: Int, onApply: (TagWithCount, attach: Boolean) -> Unit, onDismiss: () -> Unit) {
    var attach by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Tags for $count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = attach, onClick = { attach = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Add tag") }
                SegmentedButton(selected = !attach, onClick = { attach = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Remove tag") }
            }
            Spacer(Modifier.height(8.dp))
            if (tags.isEmpty()) {
                Text("No tags yet. Create tags in the Tags tab.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(tags, key = { it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onApply(t, attach) }.padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(if (attach) "Add" else "Remove", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Check / uncheck tags for one voter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditSheet(tags: List<TagWithCount>, attached: Set<Long>, onToggle: (Long, Boolean) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onManage) { Text("Manage tags") }
            }
            if (tags.size > 8) {
                SearchField(query, { query = it }, "Search tags")
                Spacer(Modifier.height(8.dp))
            }
            val shown = tags.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(shown, key = { it.id }) { t ->
                    val on = t.id in attached
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.Checkbox) { onToggle(t.id, !on) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------
// Households
// ------------------------------------------------------------------------------------------

/** Pick an existing household, or start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdPickerSheet(
    title: String,
    households: List<HouseholdSummary>,
    excludeId: Long? = null,
    onPick: (HouseholdSummary) -> Unit,
    onCreateNew: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            if (onCreateNew != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onCreateNew).padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text("New household", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            val options = households.filter { it.id != excludeId }
            if (options.size > 6) {
                Spacer(Modifier.height(8.dp))
                SearchField(query, { query = it }, "Search households or house no.")
            }
            val shown = options.filter {
                query.isBlank() || it.name.contains(query.trim(), true) || (it.houseNo?.contains(query.trim(), true) == true)
            }
            if (options.isEmpty()) {
                Text("No other households yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shown, key = { it.id }) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(h) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${h.members} member${if (h.members == 1) "" else "s"}" + (h.houseNo?.let { " · House $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Full-screen voter chooser with search (serial, English/Telugu name, relative, house no., EPIC).
 * Shows each voter's current household so moves are never a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun VoterPickerDialog(
    ward: Int,
    title: String,
    confirmLabel: String,
    initialQuery: String = "",
    excludeSerials: Set<Int> = emptySet(),
    currentHouseholdId: Long? = null,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val repo = (LocalContext.current.applicationContext as VoterDirectoryApp).repository
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var picked by rememberSaveable { mutableStateOf(ArrayList<Int>()) }
    val queryFlow = remember { MutableStateFlow(initialQuery) }
    LaunchedEffect(query) { queryFlow.value = query }
    val items by remember {
        queryFlow.debounce(150).flatMapLatest { q -> repo.directory(VoterFilter(ward = ward, query = q)) }
    }.collectAsState(initial = emptyList())
    val membership by remember {
        combine(repo.householdMemberships(ward), repo.householdSummaries(ward)) { m, hs ->
            val names = hs.associate { it.id to it.name }
            m.associate { it.voterId to (it.householdId to (names[it.householdId] ?: "")) }
        }
    }.collectAsState(initial = emptyMap())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        TextButton(onClick = { onConfirm(picked.toList()) }, enabled = picked.isNotEmpty()) {
                            Text(if (picked.isEmpty()) confirmLabel else "$confirmLabel (${picked.size})")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(query, { query = it }, "Serial, name, house or EPIC")
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    items(items.filter { it.voter.voterId !in excludeSerials }, key = { it.voter.voterId }) { item ->
                        val s = item.voter.voterId
                        val on = s in picked
                        val hh = membership[s]
                        VoterRow(
                            voter = item.voter,
                            selected = on,
                            subtitle = when {
                                hh == null -> "House ${item.voter.houseNo ?: MISSING}"
                                hh.first == currentHouseholdId -> "Already in this household"
                                else -> "In ${hh.second} – will be moved"
                            },
                            onClick = { picked = ArrayList(if (on) picked - s else picked + s) },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Small shared pieces
// ------------------------------------------------------------------------------------------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** One-line text dialog (tag / household names). Errors from saving are shown inside the dialog. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    maxLength: Int,
    onConfirm: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    allowBlank: Boolean = false,
    supporting: String? = null,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val name = field.text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        if (it.text.length <= maxLength) {
                            field = it
                            error = null
                        }
                    },
                    singleLine = true,
                    isError = error != null,
                    label = { Text(label) },
                    supportingText = (error ?: supporting)?.let { msg -> { Text(msg) } },
                    modifier = Modifier.focusRequester(focus),
                )
                Text("${name.length}/$maxLength", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) { error = it } }, enabled = (allowBlank || name.isNotBlank()) && error == null) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Exits selection mode on system back. */
@Composable
fun SelectionBackHandler(active: Boolean, onExit: () -> Unit) = BackHandler(enabled = active, onBack = onExit)
