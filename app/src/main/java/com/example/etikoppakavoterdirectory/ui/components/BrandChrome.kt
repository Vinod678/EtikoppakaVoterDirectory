package com.example.etikoppakavoterdirectory.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme

/** True when the system "Remove animations" setting (animator duration scale 0) is on. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/** Light status-bar icons while a deep-blue header sits under the status bar; restored afterwards. */
@Composable
fun LightStatusBarIcons(enabled: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(enabled, view) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (enabled) controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

/**
 * Deep-blue header band with a softly rounded lower edge (same look as the ward voter list). [content]
 * sits on the blue under the status bar; [overlap] content (search field, summary cards) straddles
 * the lower edge by [overlapHeight].
 */
@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    overlapHeight: Dp = 0.dp,
    applyStatusBarPadding: Boolean = true,
    overlap: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brand = AppTheme.brand
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (overlap != null) overlapHeight else 0.dp)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.verticalGradient(listOf(brand.header, brand.headerEnd))),
        )
        Column(Modifier.fillMaxWidth().then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)) {
            content()
            overlap?.invoke()
        }
    }
}

/**
 * App title on the blue header ("Etikoppaka Voter Directory"): up to two lines; on very narrow
 * screens or with large system fonts it steps down a little (never below 13 sp x font scale)
 * instead of being cut off with "...".
 */
@Composable
fun HeaderTitle(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxFontSize = style.fontSize,
        ),
    )
}
