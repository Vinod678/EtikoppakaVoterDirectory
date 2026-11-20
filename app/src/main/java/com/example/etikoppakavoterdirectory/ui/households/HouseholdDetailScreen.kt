package com.example.etikoppakavoterdirectory.ui.households

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.ui.EditViewModel
import com.example.etikoppakavoterdirectory.ui.components.BulkActionBar
import com.example.etikoppakavoterdirectory.ui.components.BulkExtra
import com.example.etikoppakavoterdirectory.ui.components.BulkTagSheet
import com.example.etikoppakavoterdirectory.ui.components.FavouriteIconButton
import com.example.etikoppakavoterdirectory.ui.components.HouseholdPickerSheet
import com.example.etikoppakavoterdirectory.ui.components.ProgressLine
import com.example.etikoppakavoterdirectory.ui.components.SelectionBackHandler
import com.example.etikoppakavoterdirectory.ui.components.SelectionTopBar
import com.example.etikoppakavoterdirectory.ui.components.TextInputDialog
import com.example.etikoppakavoterdirectory.ui.components.VisitedIconButton
import com.example.etikoppakavoterdirectory.ui.components.VoterPickerDialog
import com.example.etikoppakavoterdirectory.ui.components.VoterRow
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf

/**
 * One household: every member with photo, serial and names, individual Favourite / Visited
 * toggles, and multi-select for bulk changes. Each member keeps their own state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdDetailScreen(
    id: Long,
    edit: EditViewModel,
    allTags: List<TagWithCount>,
    onBack: () -> Unit,
    onOpenVoter: (Int, List<Int>, String) -> Unit,
    onExport: (String, List<Int>) -> Unit,
    /** Opens the image export for this household; true = share right after creating. */
    onExportImage: (Long, Boolean) -> Unit,
) {
    val repo = edit.repository
    val household by remember(id) { repo.household(id) }.collectAsState(initial = null)
    val wardNo = household?.wardNo
    val households by remember(wardNo) { if (wardNo == null) flowOf(emptyList()) else repo.householdSummaries(wardNo) }
        .collectAsState(initial = emptyList())
    val members by remember(id) { repo.householdMembers(id) }.collectAsState(initial = null)
    var loaded by remember(id) { mutableStateOf(false) }
    var selected by rememberSaveable(id) { mutableStateOf(ArrayList<Int>()) }
    var dialog by rememberSaveable(id) { mutableStateOf<String?>(null) } // rename|add|merge|delete|tags|move|split|remove
    var menu by remember { mutableStateOf(false) }
    val summary = households.firstOrNull { it.id == id }

    // The household was deleted (or merged into another one): leave this screen.
    LaunchedEffect(household, members) {
        if (household != null) loaded = true
        if (loaded && household == null) onBack()
    }
    val h = household
    val list = members.orEmpty()
    val serials = list.map { it.voter.voterId }
    val selection = selected.filter { it in serials }
    val selecting = selection.isNotEmpty()
    fun setSelected(l: List<Int>) { selected = ArrayList(l) }
    SelectionBackHandler(selecting) { setSelected(emptyList()) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(selection.size, serials.size, { setSelected(emptyList()) }, { setSelected(serials) }, { setSelected(emptyList()) })
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    title = {
                        Column {
                            Text(h?.name ?: "Household", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            h?.let { Text("Ward ${it.wardNo}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    actions = {
                        IconButton(onClick = { dialog = "rename" }, enabled = h != null) { Icon(Icons.Filled.Edit, contentDescription = "Rename household") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More household options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Add members") }, onClick = { menu = false; dialog = "add" })
                                DropdownMenuItem(text = { Text("Select members") }, onClick = { menu = false; setSelected(serials.take(1)) })
                                DropdownMenuItem(text = { Text("Merge another household into this") }, onClick = { menu = false; dialog = "merge" })
                                DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menu = false; onExport(h?.name ?: "Household", serials) })
                                DropdownMenuItem(text = { Text("Export Image") }, onClick = { menu = false; onExportImage(id, false) })
                                DropdownMenuItem(text = { Text("Share Image") }, onClick = { menu = false; onExportImage(id, true) })
                                DropdownMenuItem(text = { Text("Delete household") }, onClick = { menu = false; dialog = "delete" })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                BulkActionBar(
                    count = selection.size,
                    onFavourite = { edit.bulkFavourite(selection, it) },
                    onVisited = { edit.bulkVisited(selection, it) },
                    onBlock = { edit.bulkBlock(selection, it) },
                    onTags = { dialog = "tags" },
                    extras = listOf(
                        BulkExtra("Move to another household…") { dialog = "move" },
                        BulkExtra("Move to a new household") { dialog = "split" },
                        BulkExtra("Remove from household") { dialog = "remove" },
                        BulkExtra("Export selected to PDF") { onExport("${h?.name ?: "Household"} (selected)", selection) },
                    ),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "summary") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            listOfNotNull(
                                "${list.size} member${if (list.size == 1) "" else "s"}",
                                com.example.etikoppakavoterdirectory.ui.components.houseLabel(list.map { it.voter.houseNo }),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProgressLine("Visited", summary?.visited ?: list.count { it.visited }, list.size, AppTheme.status.visited, Modifier.weight(1f))
                            ProgressLine("Favourites", summary?.favourites ?: list.count { it.favourite }, list.size, AppTheme.status.favourite, Modifier.weight(1f), showPercent = false)
                        }
                    }
                }
            }
            item(key = "membersTitle") {
                Text(
                    if (selecting) "Tap members to select" else "Members · long-press to select several",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp).semantics { heading() },
                )
            }
            items(list, key = { it.voter.voterId }) { item ->
                val s = item.voter.voterId
                val on = s in selection
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    VoterRow(
                        voter = item.voter,
                        selected = if (selecting) on else null,
                        subtitle = listOfNotNull(
                            item.voter.relationship?.let { rel -> item.voter.relativeNameEn?.let { "$rel: $it" } },
                            item.voter.houseNo?.let { "House $it" },
                        ).joinToString(" · ").ifEmpty { null },
                        onClick = {
                            if (selecting) setSelected(if (on) selection - s else selection + s)
                            else onOpenVoter(s, serials, h?.name ?: "Household")
                        },
                        onLongClick = { setSelected(if (on) selection - s else selection + s) },
                        trailing = {
                            if (!selecting) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FavouriteIconButton(item.favourite, s) { edit.setFavourite(s, it) }
                                    VisitedIconButton(item.visited, s) { edit.setVisited(s, it) }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // ---------------- dialogs ----------------
    when (dialog) {
        "rename" -> if (h != null) TextInputDialog(
            title = "Rename household",
            label = "Household name",
            initial = h.name,
            confirm = "Rename",
            maxLength = BackupCodec.MAX_HOUSEHOLD_NAME_LENGTH,
            onConfirm = { name, onError -> edit.renameHousehold(id, name, { dialog = null }, onError) },
            onDismiss = { dialog = null },
        )
        "add" -> if (h != null) VoterPickerDialog(
            ward = h.wardNo,
            title = "Add members",
            confirmLabel = "Add",
            initialQuery = h?.houseNo ?: "",
            excludeSerials = serials.toSet(),
            currentHouseholdId = id,
            onConfirm = { picked -> dialog = null; edit.addToHousehold(id, picked) },
            onDismiss = { dialog = null },
        )
        "merge" -> HouseholdPickerSheet(
            title = "Merge into “${h?.name ?: ""}”",
            households = households,
            excludeId = id,
            onPick = { other -> dialog = null; edit.mergeHouseholds(id, listOf(other.id)) },
            onCreateNew = null,
            onDismiss = { dialog = null },
        )
        "tags" -> BulkTagSheet(allTags, selection.size, onApply = { t, attach -> dialog = null; edit.bulkTag(selection, t.id, t.name, attach) }, onDismiss = { dialog = null })
        "move" -> HouseholdPickerSheet(
            title = "Move ${selection.size} to another household",
            households = households,
            excludeId = id,
            onPick = { target ->
                dialog = null
                edit.moveToHousehold(id, selection, target.id) { setSelected(emptyList()) }
            },
            onCreateNew = { dialog = "split" },
            onDismiss = { dialog = null },
        )
        "split" -> Confirm(
            title = "Move ${selection.size} to a new household?",
            text = "The selected members will form a new household. Their own details, visits and favourites are not changed." +
                if (selection.size == serials.size) " This household will be removed because it would have no members." else "",
            confirm = "Move",
            onConfirm = { dialog = null; edit.splitHousehold(id, selection) { setSelected(emptyList()) } },
            onDismiss = { dialog = null },
        )
        "remove" -> Confirm(
            title = "Remove ${selection.size} from this household?",
            text = "They will no longer belong to any household. Voter records are not changed." +
                if (selection.size == serials.size) " The household will be removed because it would have no members." else "",
            confirm = "Remove",
            onConfirm = { dialog = null; edit.removeFromHousehold(selection) { setSelected(emptyList()) } },
            onDismiss = { dialog = null },
        )
        "delete" -> Confirm(
            title = "Delete this household?",
            text = "The grouping is removed. All ${serials.size} voters stay in the directory with their favourites, visits, notes and tags unchanged.",
            confirm = "Delete",
            onConfirm = { dialog = null; edit.deleteHousehold(id) },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
