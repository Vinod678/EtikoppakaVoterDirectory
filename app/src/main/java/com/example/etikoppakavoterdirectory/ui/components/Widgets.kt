package com.example.etikoppakavoterdirectory.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu

/** Compact pill toggle with a clear on/off look (used for Favourite and Visited). */
@Composable
fun StatusToggle(
    checked: Boolean,
    label: String,
    iconOn: ImageVector,
    iconOff: ImageVector,
    accent: Color,
    container: Color,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (checked) container else Color.Transparent, label = "toggleBg")
    val border by animateColorAsState(if (checked) accent else MaterialTheme.colorScheme.outlineVariant, label = "toggleBorder")
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .semantics { stateDescription = if (checked) "On" else "Off" },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (checked) iconOn else iconOff,
                contentDescription = null,
                tint = if (checked) accent else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FavouriteToggle(checked: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) = StatusToggle(
    checked, label, Icons.Filled.Star, AppIcons.StarBorder, AppTheme.status.favourite, AppTheme.status.favouriteContainer, onToggle, modifier,
)

@Composable
fun VisitedToggle(checked: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) = StatusToggle(
    checked, label, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, AppTheme.status.visited, AppTheme.status.visitedContainer, onToggle, modifier,
)

/** 48 dp star icon button for lists. */
@Composable
fun FavouriteIconButton(checked: Boolean, serial: Int, onToggle: (Boolean) -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = onToggle) {
        Icon(
            if (checked) Icons.Filled.Star else AppIcons.StarBorder,
            contentDescription = if (checked) "Favourite, voter $serial. Tap to remove from Favourites" else "Add voter $serial to Favourites",
            tint = if (checked) AppTheme.status.favourite else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 48 dp visited icon button for lists. */
@Composable
fun VisitedIconButton(checked: Boolean, serial: Int, onToggle: (Boolean) -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = onToggle) {
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = if (checked) "Visited, voter $serial. Tap to mark not visited" else "Mark voter $serial as visited",
            tint = if (checked) AppTheme.status.visited else MaterialTheme.colorScheme.outline,
        )
    }
}

/** "Visited  4/5 · 80%" with a slim bar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProgressLine(label: String, done: Int, total: Int, color: Color, modifier: Modifier = Modifier, showPercent: Boolean = true) {
    val fraction = if (total > 0) done.toFloat() / total else 0f
    val pct = (fraction * 100).toInt()
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "$label $done of $total" + if (showPercent) ", $pct percent" else ""
    }) {
        // Label left, value right; when both do not fit on one line the value moves below the label
        // (words are never split).
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
            Text(
                "$done/$total" + if (showPercent) " · $pct%" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * Compact voter row (photo, serial, names) used in households, pickers and suggestions.
 * In selection mode a checkbox is shown and taps toggle selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoterRow(
    voter: VoterEntity,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val thumbPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Select" else null)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
        VoterPhoto(
            fileName = voter.photoFilename,
            contentDescription = "Photo of voter ${voter.gpSerialNo}",
            maxSidePx = thumbPx,
            modifier = Modifier.size(width = 44.dp, height = 58.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // Serial on its own line (as on the voter cards), so the name gets the full width and
            // long names wrap between words instead of being split.
            Text(
                "${voter.gpSerialNo}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                voter.nameEn ?: MISSING,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                voter.nameTe ?: MISSING,
                style = MaterialTheme.typography.bodyMedium.telugu(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/**
 * One-line label that shrinks (down to 6 sp) only when it does not fit, so short labels such as
 * navigation items stay complete with very large system font sizes instead of being cut off.
 */
@Composable
fun FitLabel(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, color: Color = androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, letterSpacing = androidx.compose.ui.unit.TextUnit(0f, androidx.compose.ui.unit.TextUnitType.Sp)),
        maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            // 6 sp (x font scale) is only reached with the largest fonts on the narrowest phones,
            // where "Households" would otherwise lose letters.
            minFontSize = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxFontSize = style.fontSize,
        ),
    )
}

/** Small rounded badge with a number, e.g. active filter count. */
@Composable
fun CountBadge(n: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}
