package com.example.etikoppakavoterdirectory.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.etikoppakavoterdirectory.data.ExportRecord
import com.example.etikoppakavoterdirectory.data.VoterRepository
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfLayout { COMPACT, DETAILED }

/** What to print. Sensitive fields (EPIC number, private notes) are off by default. */
data class PdfOptions(
    val layout: PdfLayout = PdfLayout.COMPACT,
    val relativeNames: Boolean = true,
    val houseNo: Boolean = true,
    val ageGender: Boolean = true,
    val assemblySerial: Boolean = false,
    val epic: Boolean = false,
    val notes: Boolean = false,
    val tags: Boolean = false,
    val favourite: Boolean = false,
    val visited: Boolean = false,
    /** Household sections: start every household on a new page. */
    val householdNewPage: Boolean = false,
    /** Household sections: largest households first (otherwise by first serial number). */
    val householdsLargestFirst: Boolean = true,
)

/**
 * A part of the PDF with an optional heading, e.g. "Block A — 85 Voters" or a household.
 *
 * @param startNewPage the section begins on a new page
 * @param keepTogether move the whole section to the next page when it does not fit on the current
 *        one but would fit on an empty page; a longer section continues with a "(continued)" heading
 * @param references members already printed in an earlier section (one short line each), listed
 *        under the heading instead of repeating their full record
 */
data class PdfSection(
    val title: String?,
    val subtitle: String? = null,
    val records: List<ExportRecord>,
    val startNewPage: Boolean = false,
    val keepTogether: Boolean = false,
    val references: List<String> = emptyList(),
)

/**
 * Builds an A4 PDF entirely on the device.
 *
 * Text is laid out with Android's [StaticLayout], which shapes complex scripts (Telugu conjuncts,
 * vowel signs) with HarfBuzz before drawing; [PdfDocument] then records the already-shaped glyphs
 * and embeds the font subset (the device's Noto Sans Telugu, SIL Open Font License, which permits
 * embedding). Photos are the original extracted images, scaled only to print size.
 */
class PdfExporter(private val context: Context) {

    // A4 in PostScript points (1/72 inch).
    private val pageW = 595
    private val pageH = 842
    private val margin = 36f
    private val contentW = pageW - 2 * margin
    private val footerH = 22f

    private val teal = Color.rgb(0x00, 0x69, 0x6A)
    private val ink = Color.rgb(0x18, 0x1C, 0x1C)
    private val muted = Color.rgb(0x55, 0x60, 0x60)
    private val hairline = Color.rgb(0xC4, 0xCD, 0xCC)
    private val cardFill = Color.rgb(0xF6, 0xF9, 0xF9)

    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int = ink, isBold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (isBold) bold else regular
    }

    private fun layout(text: String, p: TextPaint, width: Float, maxLines: Int = Int.MAX_VALUE, spacing: Float = 1.0f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width.roundToInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true) // keeps room for Telugu marks above/below the line
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    /** A measured card, drawn later at its final position. */
    private class Card(val height: Float, val draw: (Canvas, Float, Float) -> Unit)

    /** Writes a single list of records without section headings (all earlier export types). */
    fun write(
        records: List<ExportRecord>,
        scopeLabel: String,
        options: PdfOptions,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = writeSections(listOf(PdfSection(title = null, records = records)), scopeLabel, options, out, onProgress)

    /**
     * Writes the PDF to [out]. Card rows never split across pages; section headings are never left
     * alone at the bottom of a page.
     * @return number of pages.
     */
    fun writeSections(
        sections: List<PdfSection>,
        scopeLabel: String,
        options: PdfOptions,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val all = sections.flatMap { it.records }
        require(all.isNotEmpty() || sections.any { it.references.isNotEmpty() }) { "Nothing to export" }
        val wards = all.map { it.voter.wardNo }.toSortedSet()
        val place = if (wards.size == 1) "Ward ${wards.first()} · $VILLAGE" else "All wards · $VILLAGE"
        val showWard = wards.size > 1
        val count = all.map { it.voter.voterId }.toSet().size
        val columns = if (options.layout == PdfLayout.COMPACT) 2 else 1
        val gap = 10f
        val cardW = (contentW - gap * (columns - 1)) / columns
        fun card(r: ExportRecord) = if (options.layout == PdfLayout.COMPACT) compactCard(r, options, cardW, showWard) else detailedCard(r, options, cardW, showWard)

        // ---- paginate ----
        val firstTop = margin + firstHeaderHeight(scopeLabel, count, place)
        val nextTop = margin + 26f
        val bottom = pageH - margin - footerH
        val pages = ArrayList<MutableList<(Canvas) -> Unit>>()
        var current = ArrayList<(Canvas) -> Unit>().also { pages += it }
        var y = firstTop
        fun top() = if (pages.size == 1) firstTop else nextTop
        fun hasContent() = y > top() + 0.5f
        fun newPage() { current = ArrayList<(Canvas) -> Unit>().also { pages += it }; y = nextTop }
        fun put(c: Card, x: Float) { val py = y; current += { canvas -> c.draw(canvas, x, py) } }

        sections.forEach { s ->
            val heading = s.title?.let { sectionHeading(it, s.subtitle, s.references) }
            val rows = s.records.map(::card).chunked(columns)
            val rowHeights = rows.map { row -> row.maxOf { it.height } }
            val total = (heading?.height?.plus(HEADING_GAP) ?: 0f) + rowHeights.sum() + gap * rows.size
            when {
                s.startNewPage && hasContent() -> newPage()
                s.keepTogether && hasContent() && y + total > bottom && total <= bottom - nextTop -> newPage()
            }
            if (heading != null) {
                if (y + heading.height + (rowHeights.firstOrNull() ?: 0f) > bottom && hasContent()) newPage()
                put(heading, margin)
                y += heading.height + HEADING_GAP
            }
            rows.forEachIndexed { i, row ->
                if (y + rowHeights[i] > bottom && hasContent()) {
                    newPage()
                    s.title?.let { t ->
                        val cont = sectionHeading("$t (continued)", null, emptyList(), small = true)
                        put(cont, margin)
                        y += cont.height + HEADING_GAP
                    }
                }
                row.forEachIndexed { col, c -> put(c, margin + col * (cardW + gap)) }
                y += rowHeights[i] + gap
            }
            if (heading != null) y += SECTION_GAP
        }

        val generated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, drawables ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
                val c = page.canvas
                if (index == 0) drawFirstHeader(c, scopeLabel, count, generated, place) else drawRunningHeader(c, scopeLabel, place)
                drawables.forEach { it(c) }
                drawFooter(c, index + 1, pages.size)
                doc.finishPage(page)
                onProgress(index + 1, pages.size)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return pages.size
    }

    /** Section heading: accent bar, bold title, optional subtitle and "already listed" references. */
    private fun sectionHeading(title: String, subtitle: String?, references: List<String>, small: Boolean = false): Card {
        val textW = contentW - 10f
        val t = layout(title, paint(if (small) 10f else 13f, teal, true), textW, 2)
        val sub = subtitle?.let { layout(it, paint(9f, muted), textW, 2) }
        val refs = if (references.isEmpty()) null else layout(
            "Already listed in an earlier section: " + references.joinToString("; "),
            paint(7.5f, muted), textW, 8,
        )
        val h = t.height + (sub?.height ?: 0) + (refs?.let { it.height + 2f } ?: 0f) + 6f
        return Card(h) { c, x, y ->
            c.drawRect(x, y + 1f, x + 3.5f, y + h - 4f, Paint().apply { color = teal })
            var ty = y
            c.save(); c.translate(x + 10f, ty); t.draw(c); c.restore(); ty += t.height
            sub?.let { c.save(); c.translate(x + 10f, ty); it.draw(c); c.restore(); ty += it.height }
            refs?.let { c.save(); c.translate(x + 10f, ty + 2f); it.draw(c); c.restore() }
            c.drawRect(x, y + h - 1.5f, x + contentW, y + h - 1f, Paint().apply { color = hairline })
        }
    }

    // ------------------------------------------------------------------------------------------
    // Headers and footer
    // ------------------------------------------------------------------------------------------

    private fun firstHeaderLayouts(scope: String, count: Int, generated: String, place: String): List<StaticLayout> = listOf(
        layout("Etikoppaka Voter Directory", paint(19f, teal, true), contentW),
        layout(place, paint(10.5f, ink), contentW),
        layout("$scope · ${plural(count, "record")} · Generated $generated", paint(9f, muted), contentW),
    )

    private fun firstHeaderHeight(scope: String, count: Int, place: String) =
        firstHeaderLayouts(scope, count, "00 Mmm 0000, 00:00 AM", place).sumOf { it.height.toDouble() }.toFloat() + 18f

    private fun drawFirstHeader(c: Canvas, scope: String, count: Int, generated: String, place: String) {
        var y = margin
        firstHeaderLayouts(scope, count, generated, place).forEach { l ->
            c.save(); c.translate(margin, y); l.draw(c); c.restore()
            y += l.height + 1f
        }
        y += 6f
        c.drawRect(margin, y, pageW - margin, y + 1.5f, Paint().apply { color = teal })
    }

    private fun drawRunningHeader(c: Canvas, scope: String, place: String) {
        val l = layout("Etikoppaka Voter Directory · ${place.substringBefore(" · ")} · $scope", paint(8.5f, muted), contentW, maxLines = 1)
        c.save(); c.translate(margin, margin); l.draw(c); c.restore()
        c.drawRect(margin, margin + l.height + 4f, pageW - margin, margin + l.height + 4.75f, Paint().apply { color = hairline })
    }

    private fun drawFooter(c: Canvas, page: Int, total: Int) {
        val y = pageH - margin - 10f
        c.drawRect(margin, y - 6f, pageW - margin, y - 5.25f, Paint().apply { color = hairline })
        val left = layout("Private – contains personal voter information. Handle with care.", paint(7.5f, muted), contentW * 0.7f, maxLines = 1)
        c.save(); c.translate(margin, y); left.draw(c); c.restore()
        val p = paint(8f, muted)
        val text = "Page $page of $total"
        c.drawText(text, pageW - margin - p.measureText(text), y + 8f, p)
    }

    // ------------------------------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------------------------------

    private fun compactCard(r: ExportRecord, o: PdfOptions, w: Float, showWard: Boolean): Card {
        val pad = 7f
        val photoW = 50f
        val photoH = 66f
        val textW = w - photoW - pad * 3
        val v = r.voter
        val blocks = ArrayList<Pair<StaticLayout, Float>>() // layout, space after

        val status = statusText(r, o)
        val serial = if (showWard) "Ward ${v.wardNo} · ${v.gpSerialNo}" else "${v.gpSerialNo}"
        blocks += layout(serial + (if (status.isNotEmpty()) "   $status" else ""), paint(9.5f, teal, true), textW, 1) to 1f
        blocks += layout(v.nameEn ?: NA, paint(10.5f, ink, true), textW, 3) to 0f
        blocks += layout(v.nameTe ?: NA, paint(10.5f, ink), textW, 3, spacing = 1.05f) to 1f
        if (o.relativeNames) {
            blocks += layout(relativeLine(r), paint(8f, muted), textW, 2) to 0f
            v.relativeNameTe?.let { blocks += layout(it, paint(8f, muted), textW, 2) to 0f }
        }
        val facts = factsLine(r, o)
        if (facts.isNotEmpty()) blocks += layout(facts, paint(7.5f, ink), textW, 3) to 0f
        if (o.tags && r.tags.isNotEmpty()) blocks += layout("Tags: " + r.tags.joinToString(", "), paint(7.5f, ink), textW, 2) to 0f
        if (o.notes && !r.note.isNullOrBlank()) blocks += layout("Note: " + r.note.trim(), paint(7.5f, ink), textW, 4) to 0f

        val textH = blocks.sumOf { (it.first.height + it.second).toDouble() }.toFloat()
        val h = max(photoH, textH) + pad * 2
        return Card(h) { c, x, y ->
            drawCardBackground(c, x, y, w, h)
            drawPhoto(c, v.photoFilename, RectF(x + pad, y + pad, x + pad + photoW, y + pad + photoH))
            var ty = y + pad
            blocks.forEach { (l, after) ->
                c.save(); c.translate(x + pad * 2 + photoW, ty); l.draw(c); c.restore()
                ty += l.height + after
            }
        }
    }

    private fun detailedCard(r: ExportRecord, o: PdfOptions, w: Float, showWard: Boolean): Card {
        val pad = 9f
        val photoW = 72f
        val photoH = 96f
        val textW = w - photoW - pad * 3
        val v = r.voter
        val head = ArrayList<StaticLayout>()
        val status = statusText(r, o)
        val serial = (if (showWard) "Ward ${v.wardNo} · " else "") + "GP Serial No. ${v.gpSerialNo}"
        head += layout(serial + (if (status.isNotEmpty()) "   $status" else ""), paint(10f, teal, true), textW, 1)
        head += layout(v.nameEn ?: NA, paint(12.5f, ink, true), textW)
        head += layout(v.nameTe ?: NA, paint(12.5f, ink), textW, spacing = 1.05f)

        val fields = ArrayList<Pair<String, String>>()
        if (o.relativeNames) {
            fields += "Relationship" to (v.relationship ?: NA)
            fields += "Relative (English)" to (v.relativeNameEn ?: NA)
            fields += "Relative (Telugu)" to (v.relativeNameTe ?: NA)
        }
        if (o.ageGender) {
            fields += "Age" to (v.age?.toString() ?: NA)
            fields += "Gender" to (v.gender ?: NA)
        }
        if (o.houseNo) fields += "House no." to (v.houseNo ?: NA)
        if (o.assemblySerial) fields += "Assembly roll serial" to (v.assemblySerial ?: NA)
        if (o.epic) fields += "EPIC no." to (v.epicNo ?: NA)
        if (o.tags) fields += "Tags" to (r.tags.joinToString(", ").ifEmpty { "None" })

        val colGap = 10f
        val cols = 3
        val cellW = (textW - colGap * (cols - 1)) / cols
        val cells = fields.map { (label, value) ->
            layout(label.uppercase(), paint(6.5f, muted, true), cellW, 1) to layout(value, paint(9f, ink), cellW, 4)
        }
        val gridRows = cells.chunked(cols)
        val rowHeights = gridRows.map { row -> row.maxOf { it.first.height + it.second.height + 0f } + 3f }
        val note = if (o.notes && !r.note.isNullOrBlank()) {
            layout("NOTE", paint(6.5f, muted, true), textW, 1) to layout(r.note.trim(), paint(9f, ink), textW, 12)
        } else null

        val textH = head.sumOf { it.height.toDouble() }.toFloat() + 4f + rowHeights.sum() +
            (note?.let { it.first.height + it.second.height + 2f } ?: 0f)
        val h = max(photoH, textH) + pad * 2
        return Card(h) { c, x, y ->
            drawCardBackground(c, x, y, w, h)
            drawPhoto(c, v.photoFilename, RectF(x + pad, y + pad, x + pad + photoW, y + pad + photoH))
            val tx = x + pad * 2 + photoW
            var ty = y + pad
            head.forEach { l -> c.save(); c.translate(tx, ty); l.draw(c); c.restore(); ty += l.height }
            ty += 4f
            gridRows.forEachIndexed { i, row ->
                row.forEachIndexed { col, (label, value) ->
                    val cx = tx + col * (cellW + colGap)
                    c.save(); c.translate(cx, ty); label.draw(c); c.translate(0f, label.height.toFloat()); value.draw(c); c.restore()
                }
                ty += rowHeights[i]
            }
            note?.let { (label, value) ->
                c.save(); c.translate(tx, ty); label.draw(c); c.translate(0f, label.height.toFloat()); value.draw(c); c.restore()
            }
        }
    }

    private fun drawCardBackground(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = RectF(x, y, x + w, y + h)
        c.drawRoundRect(r, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardFill })
        c.drawRoundRect(r, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hairline; style = Paint.Style.STROKE; strokeWidth = 0.6f })
    }

    /** Original photo, fitted (never cropped or stretched) and scaled only to print resolution. */
    private fun drawPhoto(c: Canvas, fileName: String?, box: RectF) {
        val bmp = fileName?.let { loadPhoto(it, box) }
        if (bmp == null) {
            c.drawRect(box, Paint().apply { color = Color.rgb(0xE6, 0xE9, 0xE9) })
            val p = paint(7f, muted)
            val t = "No photo"
            c.drawText(t, box.centerX() - p.measureText(t) / 2, box.centerY() + 2.5f, p)
            return
        }
        try {
            val scale = min(box.width() / bmp.width, box.height() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
            c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } finally {
            bmp.recycle()
        }
    }

    /** Decodes at about 150 dpi for the target box (original photos are ~240x320 px). */
    private fun loadPhoto(fileName: String, box: RectF): Bitmap? = try {
        val path = "${VoterRepository.PHOTO_ASSET_DIR}/$fileName"
        val decoded = context.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: return null
        val targetW = (box.width() * PRINT_DPI / 72f).roundToInt()
        val targetH = (box.height() * PRINT_DPI / 72f).roundToInt()
        val s = min(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
        if (s < 1f) {
            val scaled = Bitmap.createScaledBitmap(decoded, max(1, (decoded.width * s).roundToInt()), max(1, (decoded.height * s).roundToInt()), true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else decoded
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    // ------------------------------------------------------------------------------------------

    private fun statusText(r: ExportRecord, o: PdfOptions): String = listOfNotNull(
        if (o.favourite && r.favourite) "★ Favourite" else null,
        if (o.visited) (if (r.visited) "✓ Visited" else "Not visited") else null,
    ).joinToString("  ")

    private fun relativeLine(r: ExportRecord): String {
        val v = r.voter
        val rel = v.relationship?.takeUnless { it.equals("Not specified", ignoreCase = true) }
        return when {
            v.relativeNameEn != null && rel != null -> "$rel: ${v.relativeNameEn}"
            v.relativeNameEn != null -> "Relative: ${v.relativeNameEn}"
            else -> "Relative: $NA"
        }
    }

    private fun factsLine(r: ExportRecord, o: PdfOptions): String {
        val v = r.voter
        return listOfNotNull(
            if (o.houseNo) "House ${v.houseNo ?: NA}" else null,
            if (o.ageGender) listOfNotNull(v.age?.let { "Age $it" }, v.gender).joinToString(", ").ifEmpty { null } else null,
            if (o.assemblySerial) "Roll ${v.assemblySerial ?: NA}" else null,
            if (o.epic) "EPIC ${v.epicNo ?: NA}" else null,
        ).joinToString(" · ")
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    companion object {
        const val NA = "Not available"
        const val VILLAGE = "Etikoppaka Gram Panchayat, Andhra Pradesh"
        private const val HEADING_GAP = 6f
        private const val SECTION_GAP = 8f
        private const val PRINT_DPI = 150f
    }
}
