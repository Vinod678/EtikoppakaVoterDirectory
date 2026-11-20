package com.example.etikoppakavoterdirectory.ui.directory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.TagWithCount

/** All filters in one place. Every option combines with the others and with the search text. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: VoterFilter,
    tags: List<TagWithCount>,
    actions: DirectoryActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))

            SwitchRow("Favourites only", filter.favouritesOnly) { actions.onFavouritesOnly(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Group("Visit status") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Pair<String, Boolean?>>("Any" to null, "Visited" to true, "Not visited" to false).forEach { (label, value) ->
                        FilterChip(selected = filter.visited == value, onClick = { actions.onVisitedFilter(value) }, label = { Text(label) })
                    }
                }
            }
            Group("Block") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf<Pair<String, String?>>("Any" to null) + Blocks.ALL.map { "Block $it" to it } +
                        ("Unassigned" to Blocks.UNASSIGNED)
                    options.forEach { (label, value) ->
                        FilterChip(selected = filter.block == value, onClick = { actions.onBlockFilter(value) }, label = { Text(label) })
                    }
                }
            }
            Group("Tag") {
                if (tags.isEmpty()) {
                    Text(
                        "No tags yet. Create tags in the Tags tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter.tagId == null, onClick = { actions.onTagFilter(null) }, label = { Text("Any") })
                        tags.forEach { t ->
                            FilterChip(
                                selected = filter.tagId == t.id,
                                onClick = { actions.onTagFilter(t.id) },
                                label = { Text("${t.name} (${t.usage})") },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = actions.onResetFilters, enabled = filter.hasFilters, modifier = Modifier.weight(1f)) {
                    Text("Reset all")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Show results") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one labelled switch: larger touch target and a proper label for screen readers.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}
