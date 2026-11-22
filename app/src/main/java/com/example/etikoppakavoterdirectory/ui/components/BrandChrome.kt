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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.withLink
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/**
 * Deep-blue page header used by Households, Blocks, Tags and Settings: navigation icon, white title
 * (up to two lines, never cut), a smaller subtitle (the current ward) and optional actions; the same
 * height, spacing and rounded lower corners as the Voters header.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    navIcon: androidx.compose.ui.graphics.vector.ImageVector,
    navDescription: String,
    onNav: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val brand = AppTheme.brand
    LightStatusBarIcons(enabled = true)
    BrandHeader(modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onNav) {
                androidx.compose.material3.Icon(navIcon, contentDescription = navDescription, tint = brand.onHeader)
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                HeaderTitle(
                    title,
                    androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    brand.onHeader,
                    Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    androidx.compose.material3.Text(
                        subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = brand.onHeaderVariant,
                        modifier = Modifier.testTag("screenSubtitle"),
                    )
                }
            }
            actions()
        }
    }
}

/** Exact developer credit shown on the sign-in screen and on Settings / About. */
const val FOOTER_CREDIT_PREFIX = "Digitizing Etikoppaka ❤️ — "
const val FOOTER_HANDLE = "@vinod_yedla"
const val FOOTER_CREDIT = FOOTER_CREDIT_PREFIX + FOOTER_HANDLE

/** The developer's Instagram profile (opened from the "@vinod_yedla" part of the credit). */
const val INSTAGRAM_PROFILE_URL = "https://www.instagram.com/vinod_yedla/"

/**
 * Small, centred, muted credit line (never a banner). Only "@vinod_yedla" is a link: it opens the
 * Instagram profile, in the Instagram app when installed, otherwise in the browser.
 */
@Composable
fun FooterCredit(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(FOOTER_CREDIT_PREFIX)
        withLink(
            androidx.compose.ui.text.LinkAnnotation.Clickable(
                tag = "instagram",
                // Teal and semi-bold, without the default link underline.
                styles = androidx.compose.ui.text.TextLinkStyles(
                    androidx.compose.ui.text.SpanStyle(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.None,
                    ),
                ),
                linkInteractionListener = { openInstagramProfile(context) },
            ),
        ) { append(FOOTER_HANDLE) }
    }
    androidx.compose.material3.Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("footerCredit"),
    )
}

/**
 * Opens [INSTAGRAM_PROFILE_URL]: first in the Instagram app, then in any browser. Never crashes - if
 * nothing can open the link, a short message is shown instead.
 */
fun openInstagramProfile(context: android.content.Context) {
    val uri = android.net.Uri.parse(INSTAGRAM_PROFILE_URL)
    fun view(pkg: String?) = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        if (pkg != null) setPackage(pkg)
        if (context !is android.app.Activity) addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    for (intent in listOf(view("com.instagram.android"), view(null))) {
        try {
            context.startActivity(intent)
            return
        } catch (e: android.content.ActivityNotFoundException) {
            // try the next way
        } catch (e: SecurityException) {
            // try the next way
        }
    }
    android.widget.Toast.makeText(context, "Could not open Instagram. Please visit instagram.com/vinod_yedla", android.widget.Toast.LENGTH_LONG).show()
}
