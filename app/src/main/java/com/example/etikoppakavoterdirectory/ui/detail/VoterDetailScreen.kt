package com.example.etikoppakavoterdirectory.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.HouseholdEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.data.db.VoterUserDataEntity
import com.example.etikoppakavoterdirectory.ui.EditViewModel
import com.example.etikoppakavoterdirectory.ui.components.FavouriteToggle
import com.example.etikoppakavoterdirectory.ui.components.HouseholdPickerSheet
import com.example.etikoppakavoterdirectory.ui.components.MISSING
import com.example.etikoppakavoterdirectory.ui.components.PreviewData
import com.example.etikoppakavoterdirectory.ui.components.SectionCard
import com.example.etikoppakavoterdirectory.ui.components.TagEditSheet
import com.example.etikoppakavoterdirectory.ui.components.VisitedToggle
import com.example.etikoppakavoterdirectory.ui.components.VoterPhoto
import com.example.etikoppakavoterdirectory.ui.components.formatDateTime
import com.example.etikoppakavoterdirectory.ui.theme.EtikoppakaVoterDirectoryTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Voter details with horizontal swipe navigation.
 *
 * [serials] is the list the voter was opened from (search / filters / household), captured at that
 * moment. Swiping and Previous / Next move through exactly that list, so marking a voter visited
 * while a "Not visited" filter is active does not make the page jump to an unrelated voter.
 * Pages are keyed by the stable voter id (ward + serial), so a page can never show another voter's
 * data - also when the list mixes wards (global search).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoterPagerScreen(
    label: String,
    serials: List<Int>,
    initialSerial: Int,
    edit: EditViewModel,
    allTags: List<TagWithCount>,
    onSerialChange: (Int) -> Unit,
    onBack: () -> Unit,
    onManageTags: () -> Unit,
    onOpenHousehold: (Long) -> Unit,
    /** "Back to Home" (all wards); null hides the button. */
    onHome: (() -> Unit)? = null,
    /** Viewer: photo, names and the voter-list facts only; nothing that can be changed. */
    readOnly: Boolean = false,
) {
    val start = remember(serials) { serials.indexOf(initialSerial).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { serials.size }
    var editingNote by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    // Report the settled voter so rotation / process death reopen the same voter.
    LaunchedEffect(pager, serials) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            serials.getOrNull(page)?.let(onSerialChange)
        }
    }
    fun go(delta: Int) {
        focus.clearFocus()
        val target = (pager.targetPage + delta).coerceIn(0, serials.lastIndex)
        scope.launch { pager.animateScrollToPage(target, animationSpec = tween(320)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Voter details", maxLines = 1)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list") }
                },
                actions = {
                    if (onHome != null) {
                        IconButton(onClick = onHome) { Icon(com.example.etikoppakavoterdirectory.ui.components.AppIcons.Grid, contentDescription = "Back to Home") }
                    }
                },
            )
        },
        bottomBar = {
            PrevNextBar(
                position = pager.targetPage + 1,
                total = serials.size,
                canPrevious = pager.targetPage > 0,
                canNext = pager.targetPage < serials.lastIndex,
                onPrevious = { go(-1) },
                onNext = { go(+1) },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            key = { serials[it] },
            beyondViewportPageCount = 1, // neighbours are loaded in advance for smooth swiping
            pageSpacing = 12.dp,
            // While the note is being edited the pager is locked so text selection / cursor drags
            // never turn into a swipe to another voter.
            userScrollEnabled = !editingNote,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().testTag("voterPager"),
        ) { page ->
            val serial = serials[page]
            if (readOnly) ReadOnlyVoterPage(serial, edit) else VoterPage(
                serial = serial,
                isCurrent = pager.settledPage == page,
                edit = edit,
                allTags = allTags,
                onEditingNote = { if (pager.settledPage == page) editingNote = it },
                onManageTags = onManageTags,
                onOpenHousehold = onOpenHousehold,
            )
        }
    }
}

@Composable
private fun VoterPage(
    serial: Int,
    isCurrent: Boolean,
    edit: EditViewModel,
    allTags: List<TagWithCount>,
    onEditingNote: (Boolean) -> Unit,
    onManageTags: () -> Unit,
    onOpenHousehold: (Long) -> Unit,
) {
    val repo = edit.repository
    val voter by remember(serial) { repo.voter(serial) }.collectAsState(initial = null)
    val userData by remember(serial) { repo.userData(serial).map { it ?: VoterUserDataEntity(serial) } }.collectAsState(initial = null)
    val tagIds by remember(serial) { repo.tagIdsFor(serial) }.collectAsState(initial = emptyList())
    val household by remember(serial) { repo.householdOf(serial) }.collectAsState(initial = null)
    var sheet by rememberSaveable(serial) { mutableStateOf<String?>(null) } // "tags" | "household"
    var showPhoto by rememberSaveable(serial) { mutableStateOf(false) }
    // Households of this voter's own ward (the list may come from a search across all wards).
    val wardNo = voter?.wardNo
    val households by remember(wardNo) {
        if (wardNo == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repo.householdSummaries(wardNo)
    }.collectAsState(initial = emptyList())

    val v = voter
    val ud = userData
    if (v == null || ud == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    // Comfortable reading width on landscape phones and tablets.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileHeader(v, onPhoto = { showPhoto = true })
        QuickActions(
            userData = ud,
            onFavourite = { edit.setFavourite(serial, it) },
            onVisited = { edit.setVisited(serial, it) },
            onClearTime = { edit.clearVisitTime(serial) },
        )
        HouseholdBlockCard(
            household = household,
            memberCount = household?.let { h -> households.firstOrNull { it.id == h.id }?.members },
            block = ud.block,
            onBlock = { edit.setBlock(serial, it) },
            onOpenHousehold = { household?.let { onOpenHousehold(it.id) } },
            onAddHousehold = { sheet = "household" },
        )
        PersonalInfoCard(v)
        NotesCard(serial = serial, savedNote = ud.note, isCurrent = isCurrent, onEditing = onEditingNote) { text, done -> edit.saveNote(serial, text, done) }
        TagsCard(allTags = allTags, attached = tagIds.toSet(), onRemove = { edit.setTag(serial, it, false) }, onEdit = { sheet = "tags" })
        Spacer(Modifier.height(4.dp))
    }
    }

    when (sheet) {
        "tags" -> TagEditSheet(
            tags = allTags,
            attached = tagIds.toSet(),
            onToggle = { id, on -> edit.setTag(serial, id, on) },
            onManage = { sheet = null; onManageTags() },
            onDismiss = { sheet = null },
        )
        "household" -> HouseholdPickerSheet(
            title = if (household == null) "Add voter ${v.gpSerialNo} to a household" else "Move voter ${v.gpSerialNo} to another household",
            households = households,
            excludeId = household?.id,
            onPick = { target ->
                sheet = null
                val current = household
                // Already in a household: the transactional move (empty source household is removed).
                if (current != null) edit.moveToHousehold(current.id, listOf(serial), target.id)
                else edit.addToHousehold(target.id, listOf(serial))
            },
            onCreateNew = { sheet = null; edit.createHousehold(null, listOf(serial)) },
            onDismiss = { sheet = null },
        )
    }
    if (showPhoto) PhotoDialog(v) { showPhoto = false }
}

/**
 * Viewer page: photo, ward, GP serial, names, age, gender, relationship, relative names, house
 * number, assembly roll serial and EPIC number. The user's own data (favourite, visits, household,
 * block, notes, tags) is not even loaded.
 */
@Composable
private fun ReadOnlyVoterPage(serial: Int, edit: EditViewModel) {
    val voter by remember(serial) { edit.repository.voter(serial) }.collectAsState(initial = null)
    var showPhoto by rememberSaveable(serial) { mutableStateOf(false) }
    val v = voter
    if (v == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileHeader(v, onPhoto = { showPhoto = true })
            PersonalInfoCard(v)
            Spacer(Modifier.height(4.dp))
        }
    }
    if (showPhoto) PhotoDialog(v) { showPhoto = false }
}

// ------------------------------------------------------------------------------------------
// Profile header
// ------------------------------------------------------------------------------------------

@Composable
private fun ProfileHeader(v: VoterEntity, onPhoto: () -> Unit) {
    val w = v.photoWidthPx ?: 0
    val h = v.photoHeightPx ?: 0
    val ratio = if (w > 0 && h > 0) w.toFloat() / h else 3f / 4f
    val fontScale = LocalDensity.current.fontScale
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(14.dp)) {
            val stacked = maxWidth < 330.dp || fontScale >= 1.6f
            val photo: @Composable (Modifier) -> Unit = { m ->
                // Original photo; Fit + its own aspect ratio = never cropped or stretched.
                VoterPhoto(
                    fileName = v.photoFilename,
                    contentDescription = "Original photo of voter ${v.gpSerialNo}. Tap to enlarge.",
                    maxSidePx = 0,
                    contentScale = ContentScale.Fit,
                    modifier = m.aspectRatio(ratio).clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Enlarge photo", onClick = onPhoto),
                )
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    photo(Modifier.width(150.dp))
                    Spacer(Modifier.height(12.dp))
                    HeaderText(v, centered = true)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    photo(Modifier.width(if (ratio > 1f) 150.dp else 116.dp))
                    Spacer(Modifier.width(16.dp))
                    HeaderText(v, centered = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderText(v: VoterEntity, centered: Boolean, modifier: Modifier = Modifier) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.semantics { contentDescription = "GP serial number ${v.gpSerialNo}" },
        ) {
            Text(
                "Ward ${v.wardNo} · GP Serial No. ${v.gpSerialNo}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            v.nameEn ?: MISSING,
            style = MaterialTheme.typography.titleLarge,
            textAlign = align,
            modifier = Modifier.semantics { heading() },
        )
        Text(v.nameTe ?: MISSING, style = MaterialTheme.typography.titleMedium.telugu(), fontWeight = FontWeight.Normal, textAlign = align)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Fact(v.age?.let { "$it yrs" } ?: "Age $MISSING", "Age ${v.age ?: MISSING}")
            Fact(v.gender ?: "Gender $MISSING", "Gender ${v.gender ?: MISSING}")
        }
    }
}

@Composable
private fun Fact(text: String, description: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PhotoDialog(v: VoterEntity, onDismiss: () -> Unit) {
    val w = v.photoWidthPx ?: 0
    val h = v.photoHeightPx ?: 0
    val ratio = if (w > 0 && h > 0) w.toFloat() / h else 3f / 4f
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                VoterPhoto(
                    fileName = v.photoFilename,
                    contentDescription = "Original photo of voter ${v.gpSerialNo}",
                    maxSidePx = 0,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(ratio).clip(MaterialTheme.shapes.medium),
                )
                Text("${v.gpSerialNo} · Original photo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Quick actions
// ------------------------------------------------------------------------------------------

@Composable
private fun QuickActions(userData: VoterUserDataEntity, onFavourite: (Boolean) -> Unit, onVisited: (Boolean) -> Unit, onClearTime: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FavouriteToggle(userData.favourite, "Favourite", onFavourite, Modifier.weight(1f))
            VisitedToggle(userData.visited, "Visited", onVisited, Modifier.weight(1f))
        }
        if (userData.visited) {
            val at = userData.visitedAt
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (at != null) "Visited on ${formatDateTime(at)}" else "Visited · no time recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (at != null) TextButton(onClick = onClearTime) { Text("Remove time", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Household & block
// ------------------------------------------------------------------------------------------

@Composable
private fun HouseholdBlockCard(
    household: HouseholdEntity?,
    memberCount: Int?,
    block: String?,
    onBlock: (String?) -> Unit,
    onOpenHousehold: () -> Unit,
    onAddHousehold: () -> Unit,
) {
    SectionCard(title = null) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (household != null) "Open household" else "Add to a household") {
                    if (household != null) onOpenHousehold() else onAddHousehold()
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Household", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (household != null) {
                    Text(household.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (memberCount != null) {
                        Text("$memberCount member${if (memberCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Not in a household", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (household != null) {
                // Same picker as "Add": moves this voter to another household (an emptied household is removed).
                TextButton(onClick = onAddHousehold) { Text("Move") }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = onAddHousehold) { Text("Add") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Text("Block", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BlockSelector(block, onBlock)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSelector(block: String?, onBlock: (String?) -> Unit) {
    // With very large fonts "None" no longer fits in its segment: show a cross (labelled "No block").
    val noneLabel = if (LocalDensity.current.fontScale >= 1.3f) "✕" else "None"
    val options = Blocks.ALL.map { it to it } + (noneLabel to null)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = block == value,
                onClick = { onBlock(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, maxLines = 1) },
                icon = {},
                modifier = Modifier.semantics { contentDescription = if (value == null) "No block" else "Block $value" },
            )
        }
    }
}

// ------------------------------------------------------------------------------------------
// Personal information
// ------------------------------------------------------------------------------------------

@Composable
private fun PersonalInfoCard(v: VoterEntity) {
    SectionCard(title = "Personal information") {
        InfoLine("Relationship", v.relationship)
        InfoLine("Relative (English)", v.relativeNameEn)
        if (v.relativeNameTe != null) InfoLine("Relative (Telugu)", v.relativeNameTe, telugu = true)
        InfoLine("House no.", v.houseNo)
        InfoLine("Assembly roll serial", v.assemblySerial)
        InfoLine("EPIC no.", v.epicNo, last = true)
    }
}

/** Label | value on one row; wraps cleanly for Telugu and large fonts. */
@Composable
private fun InfoLine(label: String, value: String?, telugu: Boolean = false, last: Boolean = false) {
    val valueStyle = if (telugu && value != null) MaterialTheme.typography.bodyLarge.telugu() else MaterialTheme.typography.bodyLarge
    val valueColor = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    // Label beside the value when the label column fits its longest word; otherwise label above the
    // value (narrow phones, large fonts) - a word such as "Relationship" is never split.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyMedium
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val density = LocalDensity.current
    // "Relationship" is the longest label word of the section, so all rows switch layout together.
    val longest = (label.split(' ') + "Relationship").maxOf { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width }
    val stack = with(density) { (maxWidth * 0.42f - 8.dp).roundToPx() } < longest || density.fontScale >= 1.4f
    if (stack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: MISSING, style = valueStyle, color = valueColor)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.42f).padding(end = 8.dp, top = 1.dp),
            )
            Text(value ?: MISSING, style = valueStyle, color = valueColor, modifier = Modifier.weight(0.58f))
        }
    }
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

// ------------------------------------------------------------------------------------------
// Notes and tags
// ------------------------------------------------------------------------------------------

@Composable
private fun NotesCard(
    serial: Int,
    savedNote: String?,
    isCurrent: Boolean,
    onEditing: (Boolean) -> Unit,
    onSave: (String, () -> Unit) -> Unit,
) {
    var text by rememberSaveable(serial) { mutableStateOf(savedNote ?: "") }
    var lastSaved by rememberSaveable(serial) { mutableStateOf(savedNote ?: "") }
    var confirmClear by rememberSaveable(serial) { mutableStateOf(false) }
    LaunchedEffect(savedNote) {
        if (text == lastSaved) {
            text = savedNote ?: ""
            lastSaved = text
        }
    }
    val dirty = text != lastSaved
    // Never lose typed text: save automatically when moving to another voter or leaving.
    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(lastSaved)
    LaunchedEffect(isCurrent) {
        if (!isCurrent && latestText != latestSaved) { val t = latestText; onSave(t) { lastSaved = t } }
    }
    DisposableEffect(serial) {
        onDispose {
            onEditing(false)
            if (latestText != latestSaved) onSave(latestText) {}
        }
    }
    SectionCard(title = "Private note") {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= BackupCodec.MAX_NOTE_LENGTH) text = it },
            modifier = Modifier.fillMaxWidth().testTag("note-$serial").onFocusChanged { onEditing(it.isFocused) },
            minLines = 2,
            maxLines = 8,
            placeholder = { Text("Add a note (kept only on this device)") },
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    dirty -> "Unsaved changes"
                    lastSaved.isEmpty() -> "No note"
                    else -> "Saved"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (lastSaved.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Delete") }
            FilledTonalButton(onClick = { val t = text; onSave(t) { lastSaved = t } }, enabled = dirty) { Text("Save") }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete this note?") },
            text = { Text("The note for this voter will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    text = ""
                    onSave("") { lastSaved = "" }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(allTags: List<TagWithCount>, attached: Set<Long>, onRemove: (Long) -> Unit, onEdit: () -> Unit) {
    val mine = allTags.filter { it.id in attached }
    SectionCard(title = "Tags", trailing = { TextButton(onClick = onEdit) { Text(if (mine.isEmpty()) "Add" else "Edit") } }) {
        if (mine.isEmpty()) {
            Text("No tags", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mine.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(tag.id) },
                        label = { Text(tag.name) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag ${tag.name}", Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Previous / next
// ------------------------------------------------------------------------------------------

@Composable
private fun PrevNextBar(position: Int, total: Int, canPrevious: Boolean, canNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val buttonStyle = MaterialTheme.typography.labelLarge
    val countStyle = MaterialTheme.typography.titleSmall
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
        // The position ("12 / 369") must always be complete; when the buttons' words do not fit
        // beside it (narrow phones, large fonts), the buttons show only their arrows (same size,
        // same accessible names).
        val density = LocalDensity.current
        fun w(t: String, st: androidx.compose.ui.text.TextStyle) = measurer.measure(t, st, softWrap = false, maxLines = 1).size.width
        val need = with(density) {
            w("Previous", buttonStyle) + w("Next", buttonStyle) + w("$total / $total", countStyle) + (24.dp * 4 + 24.dp * 2 + 24.dp + 16.dp).roundToPx()
        }
        val labels = with(density) { need <= maxWidth.roundToPx() }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = canPrevious,
                contentPadding = if (labels) ButtonDefaults.ContentPadding else PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics { contentDescription = "Previous voter" },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                if (labels) Text("Previous", maxLines = 1)
            }
            Text(
                "$position / $total",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Voter $position of $total"
                    liveRegion = LiveRegionMode.Polite
                },
            )
            OutlinedButton(
                onClick = onNext,
                enabled = canNext,
                contentPadding = if (labels) ButtonDefaults.ContentPadding else PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics { contentDescription = "Next voter" },
            ) {
                if (labels) Text("Next", maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Previews (static parts; the full screen needs the database)
// ------------------------------------------------------------------------------------------

@Preview(name = "Detail parts", showBackground = true, widthDp = 360, heightDp = 1100)
@Preview(name = "Detail parts – dark", showBackground = true, widthDp = 360, heightDp = 1100, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detail parts – large font", showBackground = true, widthDp = 360, heightDp = 1400, fontScale = 1.6f)
@Composable
private fun DetailPreview() {
    EtikoppakaVoterDirectoryTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val v = PreviewData.voter
            ProfileHeader(v) {}
            QuickActions(VoterUserDataEntity(v.voterId, favourite = true, visited = true, visitedAt = 1_763_500_000_000), {}, {}, {})
            HouseholdBlockCard(HouseholdEntity(1, 11, "Household — 8-139", "8-139"), 5, "C", {}, {}, {})
            PersonalInfoCard(v)
            PrevNextBar(12, 369, true, true, {}, {})
        }
    }
}
