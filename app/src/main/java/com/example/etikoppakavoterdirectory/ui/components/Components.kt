package com.example.etikoppakavoterdirectory.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.ui.theme.AppTheme
import com.example.etikoppakavoterdirectory.ui.theme.telugu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Shown wherever the source data has no value. Nothing is ever filled in. */
const val MISSING = "Not available"

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** "Husband: Nageswara Rao" using the relationship exactly as stored; never invents a relation. */
fun relativeLine(v: VoterEntity): String {
    val name = v.relativeNameEn
    val rel = v.relationship?.takeUnless { it.equals("Not specified", ignoreCase = true) }
    return when {
        name != null && rel != null -> "$rel: $name"
        name != null -> "Relative: $name"
        else -> "Relative: $MISSING"
    }
}

// ------------------------------------------------------------------------------------------
// Photos (original extracted images only - never generated or substituted)
// ------------------------------------------------------------------------------------------

object PhotoLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** @param maxSidePx 0 = decode at the photo's original resolution. */
    suspend fun load(context: Context, fileName: String, maxSidePx: Int): Bitmap? {
        val key = "$fileName@$maxSidePx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val path = "${VoterRepository.PHOTO_ASSET_DIR}/$fileName"
            try {
                val sample = if (maxSidePx <= 0) 1 else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                    var s = 1
                    while (minOf(bounds.outWidth, bounds.outHeight) / (s * 2) >= maxSidePx) s *= 2
                    s
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
                    ?.also { cache.put(key, it) }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }
}

/**
 * Shows a voter's original photo. [contentScale] Crop is used for uniform thumbnails;
 * Fit is used on the detail page so the original is never cut or distorted.
 */
@Composable
fun VoterPhoto(
    fileName: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 0,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (LocalInspectionMode.current) { // Android Studio preview: no assets available
        PhotoPlaceholder(modifier, missing = false)
        return
    }
    val context = LocalContext.current
    val state = produceState<PhotoState>(PhotoState.Loading, fileName, maxSidePx) {
        value = if (fileName == null) PhotoState.Missing else {
            PhotoLoader.load(context, fileName, maxSidePx)?.let { PhotoState.Loaded(it.asImageBitmap()) }
                ?: PhotoState.Missing
        }
    }.value
    when (state) {
        is PhotoState.Loaded -> Image(
            bitmap = state.bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        PhotoState.Missing -> PhotoPlaceholder(modifier, missing = true)
        PhotoState.Loading -> Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier, missing: Boolean) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { if (missing) contentDescription = "No photo available" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            if (missing) {
                Text("No photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data object Missing : PhotoState
    data class Loaded(val bitmap: ImageBitmap) : PhotoState
}

// ------------------------------------------------------------------------------------------
// Pills / badges
// ------------------------------------------------------------------------------------------

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

@Composable
fun BlockPill(block: String?, modifier: Modifier = Modifier) = if (block != null) {
    Pill("Block $block", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, modifier)
} else {
    Pill("No block", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, modifier)
}

// ------------------------------------------------------------------------------------------
// Layout helpers
// ------------------------------------------------------------------------------------------

/** A titled card used for grouped content throughout the app. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** Label above value - works for long Telugu text and large font settings. */
@Composable
fun InfoCell(label: String, value: String?, modifier: Modifier = Modifier, telugu: Boolean = false) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val base = MaterialTheme.typography.bodyLarge
        Text(
            value ?: MISSING,
            style = if (telugu && value != null) base.telugu() else base,
            fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal,
            color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Two cells side by side. */
@Composable
fun InfoRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Sample records for Android Studio previews only (never used at runtime). */
object PreviewData {
    val voter = VoterEntity(
        voterId = 1103691, gpSerialNo = 3691, wardNo = 11, nameEn = "Varahalamma", nameTe = "వరహలమ్మా",
        relationship = "Husband", relativeNameEn = "Nageswara Rao", relativeNameTe = "నాగేశ్వ రరావు",
        age = 60, gender = "Female", houseNo = "8-139", assemblySerial = "32-7-264", epicNo = "XOO1357615",
        photoFilename = "3691.jpg", photoWidthPx = 240, photoHeightPx = 320, pdfPage = 1,
        pdfRelationLabel = "Husband", excelReviewStatus = "Automated normalization — verify spelling",
        needsReview = false, reviewIssueCodes = "", searchText = "",
    )
    val longNameVoter = voter.copy(
        voterId = 1103781, gpSerialNo = 3781, nameEn = "Naga Veera Venkata Satya Jagannadha",
        nameTe = "నాగ్‌వీరవెంకట్‌సత్య జగన్నా ధ", relationship = "Father",
        relativeNameEn = "Lanka Bandayya Charyulu", needsReview = true,
        reviewIssueCodes = "EXCEL_FLAG_CHECK_PDF,TELUGU_HIDDEN_CHARACTER",
    )
    val missingRelative = voter.copy(
        voterId = 1103717, gpSerialNo = 3717, nameEn = "Yashwanth Teja Rao", nameTe = "యశ్వంత్ తేజ రావు", relationship = "Not specified",
        relativeNameEn = null, relativeNameTe = null, gender = "Male", age = 27, pdfRelationLabel = null,
        needsReview = true, reviewIssueCodes = "EXCEL_FLAG_CHECK_PDF,RELATIVE_MISSING_RELATION_UNKNOWN",
    )
}
