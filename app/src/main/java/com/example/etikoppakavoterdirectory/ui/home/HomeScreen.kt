package com.example.etikoppakavoterdirectory.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.etikoppakavoterdirectory.data.db.VoterListItem
import com.example.etikoppakavoterdirectory.ui.ImportState
import com.example.etikoppakavoterdirectory.ui.WardSummary
import com.example.etikoppakavoterdirectory.ui.components.AppIcons
import com.example.etikoppakavoterdirectory.ui.components.BrandHeader
import com.example.etikoppakavoterdirectory.ui.components.EmptyState
import com.example.etikoppakavoterdirectory.ui.components.LightStatusBarIcons
import com.example.etikoppakavoterdirectory.ui.components.MISSING
import com.example.etikoppakavoterdirectory.ui.components.VoterPhoto
import com.example.etikoppakavoterdirectory.ui.components.relativeLine
import com.example.etikoppakavoterdirectory.ui.components.SummaryStat
import com.example.etikoppakavoterdirectory.ui.components.SummaryTileStyle
import com.example.etikoppakavoterdirectory.ui.components.SummaryTiles
import com.example.etikoppakavoterdirectory.ui.components.rememberReducedMotion
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu
import java.text.NumberFormat
import java.util.Locale

/** "1,476" - grouped the Indian way (12,34,567 for large numbers). */
fun groupedCount(n: Int): String = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(n)

/**
 * Home: deep-blue header, one search box across every imported ward, ward / voter totals and all
 * ward entries (imported ones open the ward; the others show "No data imported").
 */
@Composable
fun HomeScreen(
    importState: ImportState,
    wards: List<WardSummary>,
    query: String,
    results: List<VoterListItem>?,
    gridState: LazyGridState,
    onQueryChange: (String) -> Unit,
    onOpenWard: (Int) -> Unit,
    onMissingWard: (Int) -> Unit,
    /** voter id, ids of the result list (navigation order) */
    onOpenResult: (Int, List<Int>) -> Unit,
    onRetryImport: () -> Unit,
    onLogout: () -> Unit,
    /** Viewer only: opens About / Sign out (Admin settings live in the ward tabs). */
    onSettings: (() -> Unit)? = null,
) {
    val brand = AppTheme.brand
    val totalVoters = wards.sumOf { it.voters }
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val reduced = rememberReducedMotion()
    LightStatusBarIcons(enabled = true)

    // Gentle header entrance (fade + small drop), once per visit.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220)) }
    val drop = with(LocalDensity.current) { 10.dp.toPx() }

    Column(Modifier.fillMaxSize().background(brand.page)) {
        BrandHeader(
            modifier = Modifier.graphicsLayer { alpha = entrance.value; translationY = (entrance.value - 1f) * drop },
            overlapHeight = 30.dp,
            overlap = { GlobalSearchField(query, onQueryChange) },
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Group, contentDescription = null, tint = brand.onHeader, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    com.example.etikoppakavoterdirectory.ui.components.HeaderTitle(
                        "Etikoppaka Voter Directory",
                        MaterialTheme.typography.titleLarge,
                        brand.onHeader,
                    )
                    Text("Etikoppaka Village · All Wards", style = MaterialTheme.typography.bodyMedium, color = brand.onHeaderVariant)
                }
                if (onSettings != null) {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("viewerSettings")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = brand.onHeader)
                    }
                }
                IconButton(onClick = onLogout) { Icon(AppIcons.Logout, contentDescription = "Sign out", tint = brand.onHeader) }
            }
        }

        when (importState) {
            is ImportState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading voters" })
            }
            is ImportState.Failed -> EmptyState(
                icon = Icons.Filled.Warning,
                title = "Could not load the voter data",
                message = importState.message,
                action = { Button(onClick = onRetryImport) { Text("Try again") } },
            )
            is ImportState.Ready -> Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                // Two ward columns normally; one column when a card would be too narrow for
                // "Ward 14 / 369 voters" (very narrow phones or large system fonts).
                columns = GridCells.Adaptive(minSize = 132.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                state = gridState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("homeGrid"),
            ) {
                if (results != null) {
                    item(key = "resultsTitle", span = full) {
                        Text(
                            if (results.isEmpty()) "No voters match in any imported ward"
                            else "${results.size} result${if (results.size == 1) "" else "s"} across all wards",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    if (results.isEmpty()) {
                        item(key = "noResults", span = full) {
                            EmptyState(Icons.Filled.Search, "Nothing found", "Try part of a name, a family name, a serial number, a house number or an EPIC number.")
                        }
                    }
                    val ids = results.map { it.voter.voterId }
                    items(results, key = { "r" + it.voter.voterId }, span = { GridItemSpan(maxLineSpan) }) { item ->
                        SearchResultRow(item) { onOpenResult(item.voter.voterId, ids) }
                    }
                } else {
                    item(key = "stats", span = full) {
                        SummaryTiles(
                            listOf(
                                SummaryStat(AppIcons.Layers, "${wards.size}", "Total Wards", brand.total, brand.totalContainer),
                                SummaryStat(AppIcons.Group, groupedCount(totalVoters), "Total Voters", AppTheme.status.visited, AppTheme.status.visitedContainer),
                            ),
                            style = SummaryTileStyle.Large,
                            spacing = 10.dp,
                        )
                    }
                    item(key = "wardsTitle", span = full) {
                        Row(Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = brand.total, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        }
                    }
                    items(wards, key = { "w" + it.wardNo }) { w ->
                        WardCard(w, reduced) { if (w.imported) onOpenWard(w.wardNo) else onMissingWard(w.wardNo) }
                    }
                }
            }
            com.example.etikoppakavoterdirectory.ui.components.ScrollToTopButton(gridState, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
    }
}

/** Rounded white search field straddling the header edge; searches every imported ward (unchanged matching). */
@Composable
private fun GlobalSearchField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val brand = AppTheme.brand
    Surface(
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag("globalSearch"),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = {
                Text(
                    "Search across all wards (name, serial, house, EPIC)…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotEmpty()) IconButton(onClick = { onChange(""); focus.clearFocus() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            // The border colour fades between the two states (soft focus transition).
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                focusedBorderColor = brand.total,
                focusedLeadingIconColor = brand.total,
            ),
        )
    }
}

/** Imported ward: white card, light-teal badge. No data: light-grey card, grey badge, muted text. */
@Composable
private fun WardCard(w: WardSummary, reduced: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, if (reduced) snap() else tween(120), label = "wardPress")
    val on = w.imported
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(18.dp),
        color = if (on) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (on) 0.5f else 0.35f)),
        shadowElevation = if (on) 1.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .testTag("ward-${w.wardNo}")
            .semantics(mergeDescendants = true) {
                contentDescription = if (on) "Ward ${w.wardNo}, ${w.voters} voters" else "Ward ${w.wardNo}, no data imported"
            },
    ) {
        Row(Modifier.padding(start = 10.dp, top = 12.dp, bottom = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                    if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${w.wardNo}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Ward ${w.wardNo}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (on) {
                    Text("${w.voters} voters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "No data imported",
                        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One global search result: photo, names, ward, serial and relative. */
@Composable
private fun SearchResultRow(item: VoterListItem, onClick: () -> Unit) {
    val v = item.voter
    val thumbPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VoterPhoto(
                fileName = v.photoFilename,
                contentDescription = "Photo of voter ${v.gpSerialNo}, ward ${v.wardNo}",
                maxSidePx = thumbPx,
                modifier = Modifier.size(width = 50.dp, height = 66.dp).clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Ward ${v.wardNo} · Serial ${v.gpSerialNo}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(v.nameEn ?: MISSING, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.nameTe ?: MISSING, style = MaterialTheme.typography.bodyMedium.telugu(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    relativeLine(v),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
