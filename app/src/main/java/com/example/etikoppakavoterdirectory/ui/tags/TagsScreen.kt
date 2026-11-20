package com.example.etikoppakavoterdirectory.ui.tags

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.ui.components.EmptyState
import com.example.etikoppakavoterdirectory.ui.components.SearchField
import com.example.etikoppakavoterdirectory.ui.components.TextInputDialog

/** Extra explanation shown under specific default tags. */
private val TAG_HINTS = mapOf(
    "reported deceased" to "Unverified report only – the voter record is never changed or removed.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    ward: Int,
    onHome: () -> Unit,
    tags: List<TagWithCount>,
    onCreate: (String, () -> Unit, (String) -> Unit) -> Unit,
    onRename: (Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onDelete: (Long, () -> Unit) -> Unit,
    onShowVoters: (Long) -> Unit,
    onAddDefaults: () -> Unit,
) {
    // Dialog state survives rotation: "new", "rename:<id>", "delete:<id>"
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // "New tag" sits under the search field; the floating copy appears only once it has scrolled away.
    val fabShown = com.example.etikoppakavoterdirectory.ui.components.rememberFloatingActionVisible(listState, "newTag")

    Scaffold(
        containerColor = com.example.etikoppakavoterdirectory.ui.theme.AppTheme.brand.page,
        topBar = {
            com.example.etikoppakavoterdirectory.ui.components.ScreenHeader(
                title = "Tags",
                subtitle = "Ward $ward · counts are for this ward",
                navIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navDescription = "All wards (home)",
                onNav = onHome,
            ) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More tag options", tint = com.example.etikoppakavoterdirectory.ui.theme.AppTheme.brand.onHeader)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add missing suggested tags") }, onClick = { menu = false; onAddDefaults() })
                    }
                }
            }
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                fabShown,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            ) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = "new" },
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "New tag" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New tag", maxLines = 1) },
                )
            }
        },
    ) { padding ->
        val shown = tags.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search") { SearchField(query, { query = it }, "Search tags") }
            item(key = "newTag") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    androidx.compose.material3.FilledTonalButton(onClick = { dialog = "new" }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New tag")
                    }
                }
            }
            item(key = "intro") {
                Text(
                    "Private, neutral labels for organising contact work. Tap a tag to see its voters. " +
                        "Please never use tags to record anyone’s political preference or support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (tags.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Add,
                        title = "No tags yet",
                        message = "Tap \"New tag\", or add the suggested neutral tags.",
                        action = { TextButton(onClick = onAddDefaults) { Text("Add suggested tags") } },
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "nomatch") { EmptyState(Icons.Filled.Search, "No tags match", "Try another word, or create a new tag.") }
            }
            items(shown, key = { it.id }) { tag ->
                Surface(
                    onClick = { onShowVoters(tag.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.titleSmall)
                            TAG_HINTS[tag.name.lowercase()]?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (tag.usage > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.semantics { contentDescription = "${tag.usage} voters" },
                        ) {
                            Text(
                                "${tag.usage}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                        IconButton(onClick = { dialog = "rename:${tag.id}" }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename tag ${tag.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { dialog = "delete:${tag.id}" }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete tag ${tag.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    val d = dialog
    when {
        d == "new" -> TextInputDialog(
            title = "New tag",
            label = "Tag name",
            initial = "",
            confirm = "Create",
            maxLength = BackupCodec.MAX_TAG_NAME_LENGTH,
            onConfirm = { name, onError -> onCreate(name, { dialog = null }, onError) },
            onDismiss = { dialog = null },
        )
        d != null && d.startsWith("rename:") -> {
            val tag = tags.firstOrNull { it.id == d.removePrefix("rename:").toLongOrNull() }
            if (tag == null) LaunchedEffect(d) { dialog = null } else TextInputDialog(
                title = "Rename tag",
                label = "Tag name",
                initial = tag.name,
                confirm = "Rename",
                maxLength = BackupCodec.MAX_TAG_NAME_LENGTH,
                onConfirm = { name, onError -> onRename(tag.id, name, { dialog = null }, onError) },
                onDismiss = { dialog = null },
            )
        }
        d != null && d.startsWith("delete:") -> {
            val tag = tags.firstOrNull { it.id == d.removePrefix("delete:").toLongOrNull() }
            if (tag == null) LaunchedEffect(d) { dialog = null } else AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete tag \"${tag.name}\"?") },
                text = {
                    Text(
                        if (tag.usage > 0) "It will be removed from ${tag.usage} voter(s). Voter records themselves are not changed."
                        else "This tag is not used by any voter."
                    )
                },
                confirmButton = { TextButton(onClick = { onDelete(tag.id) { dialog = null } }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
}
