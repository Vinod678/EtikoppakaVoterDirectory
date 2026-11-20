package com.example.etikoppakavoterdirectory.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One statistic tile (value + label), e.g. "369/369 · Assigned Voters". */
data class SummaryStat(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val accent: Color,
    val container: Color,
    /** When set, the whole tile is a button (e.g. "show the unassigned voters"). */
    val onClick: (() -> Unit)? = null,
    val clickLabel: String? = null,
    val testTag: String? = null,
)

/** Compact: small round icon + value on one line, label below (ward / household headers). Large: home screen. */
enum class SummaryTileStyle { Compact, Large }

/**
 * A row of equal statistic tiles that never cuts off a number or splits a word of a label.
 *
 * The real width each tile needs (padding + icon + the full value, or the longest label word) is
 * measured with the current font and system font scale. When all tiles fit side by side they share
 * the row (equal widths when possible, otherwise in proportion to their content); when they do not
 * (very narrow phones, large accessibility fonts) the tiles are stacked full-width, one per line,
 * with icon, value and label in a single row. Values are never shrunk or ellipsised.
 */
@Composable
fun SummaryTiles(
    stats: List<SummaryStat>,
    modifier: Modifier = Modifier,
    style: SummaryTileStyle = SummaryTileStyle.Compact,
    spacing: Dp = 8.dp,
) {
    val measurer = rememberTextMeasurer()
    val spec = tileSpec(style)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        fun w(text: String, ts: TextStyle) = measurer.measure(text, ts, softWrap = false, maxLines = 1).size.width
        // Widths (px) each tile needs: [0] icon beside the value, [1] icon above the value.
        val needed = stats.map { s ->
            val value = w(s.value, spec.value)
            val word = s.label.split(' ').maxOf { w(it, spec.label) }
            with(density) {
                val pad = (spec.hPad * 2).roundToPx() + 2 // + border
                val icon = (spec.iconBox + spec.gap).roundToPx()
                val side = when (style) {
                    SummaryTileStyle.Compact -> maxOf(pad + icon + value, pad + word)
                    SummaryTileStyle.Large -> pad + icon + maxOf(value, word)
                }
                intArrayOf(side, pad + maxOf(value, word, spec.iconBox.roundToPx()))
            }
        }
        val available = with(density) { (maxWidth - spacing * (stats.size - 1)).roundToPx() }
        // 1) side by side with the icon beside the number; 2) side by side with the icon above the
        // number; 3) one tile per line.
        val mode = when {
            needed.sumOf { it[0] } <= available -> 0
            needed.sumOf { it[1] } <= available -> 1
            else -> 2
        }
        if (mode < 2) {
            val equal = available / stats.size
            val allEqual = needed.all { it[mode] <= equal }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                stats.forEachIndexed { i, s ->
                    SummaryTile(s, spec, TileMode.entries[mode], Modifier.weight(if (allEqual) 1f else needed[i][mode].toFloat()).fillMaxHeight())
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing * 0.75f)) {
                stats.forEach { s -> SummaryTile(s, spec, TileMode.Stacked, Modifier.fillMaxWidth()) }
            }
        }
    }
}

private enum class TileMode { Side, Upright, Stacked }

private class TileSpec(
    val style: SummaryTileStyle,
    val value: TextStyle,
    val label: TextStyle,
    val iconBox: Dp,
    val icon: Dp,
    val iconShape: Shape,
    val gap: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val shape: Shape,
)

@Composable
private fun tileSpec(style: SummaryTileStyle): TileSpec {
    val t = MaterialTheme.typography
    return when (style) {
        SummaryTileStyle.Compact -> TileSpec(
            style, t.titleLarge.copy(fontWeight = FontWeight.Bold), t.labelMedium.copy(letterSpacing = 0.sp),
            iconBox = 28.dp, icon = 17.dp, iconShape = CircleShape, gap = 8.dp, hPad = 10.dp, vPad = 8.dp, shape = RoundedCornerShape(16.dp),
        )
        SummaryTileStyle.Large -> TileSpec(
            style, t.headlineSmall.copy(fontWeight = FontWeight.Bold), t.bodyMedium,
            iconBox = 42.dp, icon = 24.dp, iconShape = RoundedCornerShape(13.dp), gap = 10.dp, hPad = 12.dp, vPad = 12.dp, shape = RoundedCornerShape(18.dp),
        )
    }
}

@Composable
private fun SummaryTile(s: SummaryStat, spec: TileSpec, mode: TileMode, modifier: Modifier) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    val color = MaterialTheme.colorScheme.surfaceContainerLowest
    val elevation = 1.dp
    val m = modifier
        .then(if (s.testTag != null) Modifier.testTag(s.testTag) else Modifier)
        .semantics(mergeDescendants = true) {
            contentDescription = "${s.label}: ${s.value}" + if (s.onClick != null && s.clickLabel != null) ". ${s.clickLabel}" else ""
            if (s.onClick != null) role = Role.Button
        }
    val content: @Composable () -> Unit = { TileContent(s, spec, mode) }
    if (s.onClick != null) {
        Surface(onClick = s.onClick, modifier = m, shape = spec.shape, color = color, border = border, shadowElevation = elevation) { content() }
    } else {
        Surface(modifier = m, shape = spec.shape, color = color, border = border, shadowElevation = elevation) { content() }
    }
}

@Composable
private fun TileContent(s: SummaryStat, spec: TileSpec, mode: TileMode) {
    val icon: @Composable () -> Unit = {
        Box(Modifier.size(spec.iconBox).clip(spec.iconShape).background(s.container), contentAlignment = Alignment.Center) {
            Icon(s.icon, contentDescription = null, tint = s.accent, modifier = Modifier.size(spec.icon))
        }
    }
    val value: @Composable () -> Unit = { Text(s.value, style = spec.value, softWrap = false, maxLines = 1) }
    val label: @Composable (Modifier) -> Unit = { mod ->
        Text(s.label, style = spec.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = mod)
    }
    val trailing: @Composable () -> Unit = {
        if (s.onClick != null) {
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = s.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    when {
        mode == TileMode.Upright -> Column(Modifier.padding(horizontal = spec.hPad, vertical = spec.vPad)) {
            icon()
            Spacer(Modifier.height(4.dp))
            value()
            label(Modifier)
        }
        mode == TileMode.Stacked -> Row(Modifier.padding(horizontal = spec.hPad, vertical = spec.vPad * 0.75f), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(spec.gap))
            value()
            Spacer(Modifier.width(10.dp))
            label(Modifier.weight(1f))
            trailing()
        }
        spec.style == SummaryTileStyle.Compact -> Column(Modifier.padding(horizontal = spec.hPad, vertical = spec.vPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(spec.gap))
                value()
            }
            Spacer(Modifier.height(2.dp))
            label(Modifier)
        }
        else -> Row(Modifier.padding(horizontal = spec.hPad, vertical = spec.vPad), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(spec.gap))
            Column {
                value()
                label(Modifier)
            }
        }
    }
}
