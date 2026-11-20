package com.example.etikoppakavoterdirectory.ui.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.db.BlockCount
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.ui.components.SectionCard
import com.example.etikoppakavoterdirectory.ui.theme.EtikoppakaVoterDirectoryTheme


/**
 * Block overview, laid out as on the ground:
 *   C | D
 *   A | B
 * Range assignment and clearing live in a separate "Manage assignments" sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(
    ward: Int,
    /** Lowest..highest GP serial number of this ward (null while loading). */
    serialRange: IntRange?,
    onHome: () -> Unit,
    counts: List<BlockCount>,
    /** Optional labels of this ward's blocks ("A" -> "Main Street"). */
    labels: Map<String, String> = emptyMap(),
    /** block, new label (blank = clear), onDone, onError */
    onSetLabel: (String, String, () -> Unit, (String) -> Unit) -> Unit = { _, _, d, _ -> d() },
    onOpenBlock: (String) -> Unit,
    onClearBlock: (String) -> Unit,
    onAssignRange: (Int, Int, String?) -> Unit,
) {
    val byBlock = counts.associateBy { it.block }
    var manage by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(com.example.etikoppakavoterdirectory.ui.theme.AppTheme.brand.page)) {
        com.example.etikoppakavoterdirectory.ui.components.ScreenHeader(
            title = "Blocks",
            subtitle = "Ward $ward",
            navIcon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            navDescription = "All wards (home)",
            onNav = onHome,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Full-width action (was squeezed into the top bar, cutting the title on narrow phones).
            androidx.compose.material3.FilledTonalButton(onClick = { manage = true }, modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                Text("Manage assignments")
            }
            BlockGrid(byBlock, labels, onOpenBlock)
            BlockLabelsCard(labels) { editing = it }
            val un = byBlock[Blocks.UNASSIGNED]
            Surface(
                onClick = { onOpenBlock(Blocks.UNASSIGNED) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).semantics(mergeDescendants = true) {
                    contentDescription = "Unassigned: ${un?.total ?: 0} voters, ${un?.visited ?: 0} visited. Tap to show them."
                },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Unassigned", style = MaterialTheme.typography.titleSmall)
                        Text("${un?.visited ?: 0} visited", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${un?.total ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Tap a block to see its voters. To assign voters, change the block on the voter details page, " +
                    "or long-press voters in the Voters tab to select several and choose Block.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { b ->
        com.example.etikoppakavoterdirectory.ui.components.TextInputDialog(
            title = "Label for Block $b",
            label = "Label (optional)",
            initial = labels[b].orEmpty(),
            confirm = "Save",
            maxLength = com.example.etikoppakavoterdirectory.data.backup.BackupCodec.MAX_BLOCK_LABEL_LENGTH,
            allowBlank = true,
            supporting = "For example \"Main Street\". Leave empty to remove the label.",
            onConfirm = { text, onError -> onSetLabel(b, text, { editing = null }, onError) },
            onDismiss = { editing = null },
        )
    }

    if (manage) {
        ModalBottomSheet(onDismissRequest = { manage = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Manage assignments", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
                RangeAssignCard(ward, serialRange ?: 1..99_999, onAssignRange)
                ClearBlockCard(byBlock, onClearBlock)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BlockGrid(byBlock: Map<String, BlockCount>, labels: Map<String, String>, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlockTile("C", byBlock["C"], labels["C"], onOpen, Modifier.weight(1f))
            BlockTile("D", byBlock["D"], labels["D"], onOpen, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlockTile("A", byBlock["A"], labels["A"], onOpen, Modifier.weight(1f))
            BlockTile("B", byBlock["B"], labels["B"], onOpen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BlockTile(block: String, count: BlockCount?, label: String?, onOpen: (String) -> Unit, modifier: Modifier) {
    val total = count?.total ?: 0
    val visited = count?.visited ?: 0
    Surface(
        onClick = { onOpen(block) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Block $block${label?.let { ", $it" } ?: ""}: $total voters, $visited visited. Tap to show them."
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(block, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (total == 1) "voter" else "voters", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            val fraction = if (total > 0) visited.toFloat() / total else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Text("$visited visited", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearBlockCard(byBlock: Map<String, BlockCount>, onClearBlock: (String) -> Unit) {
    var block by rememberSaveable { mutableStateOf("A") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val n = byBlock[block]?.total ?: 0
    SectionCard(title = "Clear a block") {
        Text(
            "Makes every voter in the block unassigned. Visits, notes and tags are not affected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Blocks.ALL.forEachIndexed { i, b ->
                SegmentedButton(selected = block == b, onClick = { block = b }, shape = SegmentedButtonDefaults.itemShape(i, Blocks.ALL.size), label = { Text(b) })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { confirm = true }, enabled = n > 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (n > 0) "Clear block $block ($n)" else "Block $block is empty")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear block $block?") },
            text = { Text("$n voter${if (n == 1) "" else "s"} will become unassigned. Visits, notes and tags are not affected.") },
            confirmButton = { TextButton(onClick = { confirm = false; onClearBlock(block) }) { Text("Clear block") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Quick (re)assignment of a continuous range of GP serial numbers, e.g. one street. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeAssignCard(ward: Int, range: IntRange, onAssignRange: (Int, Int, String?) -> Unit) {
    val FIRST_SERIAL = range.first
    val LAST_SERIAL = range.last
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var block by rememberSaveable { mutableStateOf<String?>("A") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val f = from.toIntOrNull()
    val t = to.toIntOrNull()
    val valid = f != null && t != null && f <= t && f >= FIRST_SERIAL && t <= LAST_SERIAL
    val error = when {
        from.isEmpty() || to.isEmpty() -> null
        f == null || t == null -> "Enter numbers only"
        f < FIRST_SERIAL || t > LAST_SERIAL -> "Serials must be between $FIRST_SERIAL and $LAST_SERIAL"
        f > t -> "First serial must not be larger than the last"
        else -> null
    }

    SectionCard(title = "Assign a range of serial numbers") {
        Text(
            "Useful for assigning a whole street at once. Only Ward $ward is changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = from,
                onValueChange = { from = it.filter(Char::isDigit).take(5) },
                label = { Text("From") },
                placeholder = { Text("$FIRST_SERIAL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it.filter(Char::isDigit).take(5) },
                label = { Text("To") },
                placeholder = { Text("$LAST_SERIAL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        // "No block" is shown as ✕ (same as on the voter details page): the word did not fit in a
        // fifth of a narrow screen next to the selection tick.
        val options = Blocks.ALL.map { it to it } + ("✕" to null)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (label, value) ->
                SegmentedButton(
                    selected = block == value,
                    onClick = { block = value },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label, maxLines = 1) },
                    modifier = if (value == null) Modifier.semantics { contentDescription = "No block (unassign)" } else Modifier,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { confirm = true }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(if (block == null) "Unassign range" else "Assign to block $block")
        }
    }

    if (confirm && f != null && t != null) {
        val n = t - f + 1
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (block == null) "Unassign $n voters?" else "Assign $n voters to block $block?") },
            text = { Text("Ward $ward, serials $f to $t. Any existing block assignment in this range will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onAssignRange(f, t, block)
                    from = ""; to = ""
                }) { Text(if (block == null) "Unassign" else "Assign") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Preview(name = "Blocks", showBackground = true, widthDp = 380, heightDp = 900)
@Preview(name = "Blocks – dark", showBackground = true, widthDp = 380, heightDp = 900, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlocksPreview() {
    EtikoppakaVoterDirectoryTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            BlocksScreen(ward = 11, serialRange = 3691..4059, onHome = {},
                counts = listOf(
                    BlockCount("A", 92, 10), BlockCount("B", 80, 4), BlockCount("C", 101, 0),
                    BlockCount("D", 60, 2), BlockCount(Blocks.UNASSIGNED, 36, 1),
                ),
                onOpenBlock = {}, onClearBlock = {}, onAssignRange = { _, _, _ -> },
            )
        }
    }
}

/** Optional names of the blocks of this ward; tap a row to add, change or remove the label. */
@Composable
private fun BlockLabelsCard(labels: Map<String, String>, onEdit: (String) -> Unit) {
    SectionCard(title = "Block labels", modifier = Modifier.widthIn(max = 520.dp)) {
        Blocks.ALL.forEach { b ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Edit label") { onEdit(b) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Block $b", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
                Text(
                    labels[b] ?: "Add a label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (labels[b] != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
