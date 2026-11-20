package com.example.etikoppakavoterdirectory.ui.households

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.ui.EditViewModel
import com.example.etikoppakavoterdirectory.ui.components.BrandHeader
import com.example.etikoppakavoterdirectory.ui.components.EmptyState
import com.example.etikoppakavoterdirectory.ui.components.HeaderTitle
import com.example.etikoppakavoterdirectory.ui.components.HouseholdPickerSheet
import com.example.etikoppakavoterdirectory.ui.components.LightStatusBarIcons
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme

/**
 * Every voter of the ward who belongs to no household (live from the database, serial order), opened
 * from the "Unassigned Voters" tile. Same cards and the same "Assign to Household" action as the
 * section at the end of the Households list; the list and the counts update as soon as a voter is
 * assigned. Independent of the household search, which stays as it was for when the user goes back.
 */
@Composable
fun UnassignedVotersScreen(
    ward: Int,
    households: List<HouseholdSummary>,
    edit: EditViewModel,
    onBack: () -> Unit,
) {
    val brand = AppTheme.brand
    val voters by remember(ward) { edit.repository.unassignedVoters(ward) }.collectAsState(initial = null)
    var assigning by rememberSaveable { mutableStateOf<Int?>(null) }
    LightStatusBarIcons(enabled = true)

    Scaffold(
        containerColor = brand.page,
        topBar = {
            BrandHeader {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to households", tint = brand.onHeader)
                    }
                    Column(Modifier.weight(1f)) {
                        HeaderTitle("Unassigned Voters", MaterialTheme.typography.titleMedium, brand.onHeader, Modifier.semantics { heading() })
                        Text(
                            "Ward $ward" + (voters?.let { " · ${it.size} not in any household" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = brand.onHeaderVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val list = voters
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = "All voters are assigned to households.",
                    message = "Every voter of Ward $ward belongs to a household.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("unassignedList"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hint") {
                    Text(
                        "Assign each voter to an existing household or start a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                items(list, key = { it.voterId }) { v -> UnassignedVoterCard(v) { assigning = v.voterId } }
                item(key = "navPad") { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }

    assigning?.let { voterId ->
        val serial = voters?.firstOrNull { it.voterId == voterId }?.gpSerialNo
        HouseholdPickerSheet(
            title = "Assign voter ${serial ?: ""} to a household",
            households = households,
            onPick = { target -> assigning = null; edit.addToHousehold(target.id, listOf(voterId)) },
            onCreateNew = { assigning = null; edit.createHousehold(null, listOf(voterId)) },
            onDismiss = { assigning = null },
        )
    }
}
