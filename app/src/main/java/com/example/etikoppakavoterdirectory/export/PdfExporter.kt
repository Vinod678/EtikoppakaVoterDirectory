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
 * @param keepTogether a short section (up to two card rows) moves to the next page instead of being
 *        split; a longer section continues on the next page with a "(continued)" heading
 * @param references members already printed in an earlier section (one short line each), listed
 *        under the heading instead of repeating their full record
 * @param household a household section: two consecutive single-member households are printed side
 *        by side, each with its own heading
 */
data class PdfSection(
    val title: String?,
    val subtitle: String? = null,
    val records: List<ExportRecord>,
    val startNewPage: Boolean = false,
    val keepTogether: Boolean = false,
    val references: List<String> = emptyList(),
    val household: Boolean = false,
)

/**
 * Builds an A4 PDF entirely on the device. Every export (voter lists, blocks, tags, households)
 * uses the same page layout and voter card: a compact header with "Page X of Y" at the top right,
 * no footer, and two voter cards per row.
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
    private val margin = 28f
    private val contentW = pageW - 2 * margin
    private val colGap = 8f
    private val rowGap = 6f
    /** Two cards per row on every export. */
    private val cardW = (contentW - colGap) / 2

    private val teal = Color.rgb(0x00, 0x69, 0x6A)
    private val ink = Color.rgb(0x18, 0x1C, 0x1C)
    private val muted = Color.rgb(0x55, 0x60, 0x60)
    private val hairline = Color.rgb(0xC4, 0xCD, 0xCC)
    private val cardFill = Color.rgb(0xF6, 0xF9, 0xF9)
    private val badgeFill = Color.rgb(0xD5, 0xEC, 0xEB)

    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int = ink, isBold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (isBold) bold else regular
    }

    /** Wraps freely (no line limit, nothing cut) unless [maxLines] is given. */
    private fun layout(text: String, p: TextPaint, width: Float, maxLines: Int = Int.MAX_VALUE, spacing: Float = 1.0f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width.roundToInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true) // keeps room for Telugu marks above/below the line
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    /** A measured block (card, heading, ...), drawn later at its final position. */
    private class Card(val height: Float, val draw: (Canvas, Float, Float) -> Unit)

    /** Writes a single list of records without section headings. */
    fun write(
        records: List<ExportRecord>,
        scopeLabel: String,
        options: PdfOptions,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = writeSections(listOf(PdfSection(title = null, records = records)), scopeLabel, options, out, onProgress)

    /**
     * Writes the PDF to [out]. Voter cards are never split across pages; headings always stay with
     * their first row of cards.
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
        val wardText = if (wards.size == 1) "Ward ${wards.first()}" else "All wards"
        val showWard = wards.size > 1
        fun card(r: ExportRecord) = voterCard(r, options, cardW, showWard)

        val generated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val first = firstHeader(wardText, scopeLabel, generated)
        val running = runningHeader(wardText, scopeLabel)

        // ---- paginate ----
        val firstTop = margin + first.height + HEADER_GAP
        val nextTop = margin + running.height + HEADER_GAP
        val bottom = pageH - margin
        val pages = ArrayList<MutableList<(Canvas) -> Unit>>()
        var current = ArrayList<(Canvas) -> Unit>().also { pages += it }
        var y = firstTop
        fun top() = if (pages.size == 1) firstTop else nextTop
        fun hasContent() = y > top() + 0.5f
        fun newPage() { current = ArrayList<(Canvas) -> Unit>().also { pages += it }; y = nextTop }
        fun put(c: Card, x: Float) { val py = y; current += { canvas -> c.draw(canvas, x, py) } }
        fun single(s: PdfSection?) = s != null && s.household && s.title != null && s.records.size == 1 && s.references.isEmpty()

        var i = 0
        while (i < sections.size) {
            val s = sections[i]
            val next = sections.getOrNull(i + 1)
            // Two consecutive single-member households: side by side, each with its own heading.
            if (single(s) && single(next) && !next!!.startNewPage) {
                if (s.startNewPage && hasContent()) newPage()
                val left = householdHalf(s, ::card)
                val right = householdHalf(next, ::card)
                val h = max(left.height, right.height)
                if (y + h > bottom && hasContent()) newPage()
                put(left, margin)
                put(right, margin + cardW + colGap)
                val py = y
                current += { c ->
                    val x = margin + cardW + colGap / 2
                    c.drawRect(x - 0.3f, py, x + 0.3f, py + h, Paint().apply { color = hairline })
                }
                y += h + SECTION_GAP
                i += 2
                continue
            }

            val heading = s.title?.let { sectionHeading(it, s.subtitle, s.references, contentW) }
            val rows = s.records.map(::card).chunked(2)
            val rowHeights = rows.map { row -> row.maxOf { it.height } }
            val headH = heading?.let { it.height + HEADING_GAP } ?: 0f
            val total = headH + rowHeights.sum() + rowGap * max(0, rows.size - 1)
            when {
                s.startNewPage && hasContent() -> newPage()
                // Short sections are not split; longer ones simply continue on the next page.
                s.keepTogether && rows.size <= 2 && hasContent() && y + total > bottom && total <= bottom - nextTop -> newPage()
            }
            if (heading != null) {
                if (y + headH + (rowHeights.firstOrNull() ?: 0f) > bottom && hasContent()) newPage()
                put(heading, margin)
                y += headH
            }
            rows.forEachIndexed { r, row ->
                if (y + rowHeights[r] > bottom && hasContent()) {
                    newPage()
                    s.title?.let { t ->
                        val cont = sectionHeading("$t (continued)", null, emptyList(), contentW, small = true)
                        put(cont, margin)
                        y += cont.height + HEADING_GAP
                    }
                }
                row.forEachIndexed { col, c -> put(c, margin + col * (cardW + colGap)) }
                y += rowHeights[r] + rowGap
            }
            if (rows.isNotEmpty()) y -= rowGap
            y += if (s.title != null) SECTION_GAP else rowGap
            i++
        }

        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, drawables ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
                val c = page.canvas
                val pageText = "Page ${index + 1} of ${pages.size}"
                if (index == 0) first.draw(c, pageText) else running.draw(c, pageText)
                drawables.forEach { it(c) }
                doc.finishPage(page)
                onProgress(index + 1, pages.size)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return pages.size
    }

    /** One single-member household in half the page width: compact heading and its voter card. */
    private fun householdHalf(s: PdfSection, card: (ExportRecord) -> Card): Card {
        val heading = sectionHeading(s.title!!, s.subtitle, emptyList(), cardW)
        val c = card(s.records.single())
        return Card(heading.height + HEADING_GAP + c.height) { canvas, x, y ->
            heading.draw(canvas, x, y)
            c.draw(canvas, x, y + heading.height + HEADING_GAP)
        }
    }

    /** Section heading: accent bar, bold title, optional subtitle and "already listed" references. */
    private fun sectionHeading(title: String, subtitle: String?, references: List<String>, width: Float, small: Boolean = false): Card {
        val textW = width - 9f
        val t = layout(title, paint(if (small) 9.5f else 11.5f, teal, true), textW)
        val sub = subtitle?.let { layout(it, paint(8.5f, muted), textW) }
        val refs = if (references.isEmpty()) null else layout(
            "Already listed in an earlier section: " + references.joinToString("; "),
            paint(7.5f, muted), textW,
        )
        val h = t.height + (sub?.height ?: 0) + (refs?.let { it.height + 2f } ?: 0f)
        return Card(h) { c, x, y ->
            c.drawRect(x, y + 1f, x + 3f, y + h - 1f, Paint().apply { color = teal })
            var ty = y
            c.save(); c.translate(x + 9f, ty); t.draw(c); c.restore(); ty += t.height
            sub?.let { c.save(); c.translate(x + 9f, ty); it.draw(c); c.restore(); ty += it.height }
            refs?.let { c.save(); c.translate(x + 9f, ty + 2f); it.draw(c); c.restore() }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Page headers ("Page X of Y" at the top right, above the divider; no footer)
    // ------------------------------------------------------------------------------------------

    private class Header(val height: Float, val draw: (Canvas, String) -> Unit)

    private val pagePaint get() = paint(8.5f, muted)
    /** Room for the widest page number ("Page 999 of 999"). */
    private val pageNumberW get() = pagePaint.measureText("Page 999 of 999") + 8f

    private fun drawPageNumber(c: Canvas, text: String, baseline: Float) {
        val p = pagePaint
        c.drawText(text, pageW - margin - p.measureText(text), baseline, p)
    }

    /** First page: app name, ward and Gram Panchayat, what is exported and when (first page only). */
    private fun firstHeader(ward: String, scope: String, generated: String): Header {
        val textW = contentW - pageNumberW
        val title = layout("Etikoppaka Voter Directory", paint(15f, teal, true), textW)
        val place = layout("$ward · $VILLAGE", paint(9.5f, ink), textW)
        val info = layout("$scope · Generated $generated", paint(8.5f, muted), textW)
        val textH = title.height + place.height + info.height
        val h = textH + 5f + 1.2f
        return Header(h) { c, pageText ->
            var y = margin
            listOf(title, place, info).forEach { l -> c.save(); c.translate(margin, y); l.draw(c); c.restore(); y += l.height }
            drawPageNumber(c, pageText, margin + title.getLineBaseline(0) - 2f)
            val dy = margin + textH + 5f
            c.drawRect(margin, dy, pageW - margin, dy + 1.2f, Paint().apply { color = teal })
        }
    }

    /** Later pages: one short line (app, ward, export) and the page number. */
    private fun runningHeader(ward: String, scope: String): Header {
        val l = layout("Etikoppaka Voter Directory · $ward · $scope", paint(8.5f, muted), contentW - pageNumberW, maxLines = 2)
        val h = l.height + 4f + 0.75f
        return Header(h) { c, pageText ->
            c.save(); c.translate(margin, margin); l.draw(c); c.restore()
            drawPageNumber(c, pageText, margin + l.getLineBaseline(0))
            val dy = margin + l.height + 4f
            c.drawRect(margin, dy, pageW - margin, dy + 0.75f, Paint().apply { color = hairline })
        }
    }

    // ------------------------------------------------------------------------------------------
    // Voter card (the same design in every export; Detailed = larger photo and labelled lines)
    // ------------------------------------------------------------------------------------------

    private fun voterCard(r: ExportRecord, o: PdfOptions, w: Float, showWard: Boolean): Card {
        val detailed = o.layout == PdfLayout.DETAILED
        val pad = 6f
        val photoW = if (detailed) 56f else 46f
        val photoH = photoW * 4f / 3f
        val textX = pad * 2 + photoW
        val textW = w - photoW - pad * 3
        val v = r.voter

        // Serial number: small highlighted box at the top right, beside the name.
        val badgeText = if (showWard) "Ward ${v.wardNo} · ${v.gpSerialNo}" else "${v.gpSerialNo}"
        val badgePaint = paint(8.5f, teal, true)
        val badgeW = badgePaint.measureText(badgeText) + 8f
        val badgeH = 13f

        val nameSize = if (detailed) 11f else 10f
        val small = if (detailed) 8.2f else 7.8f
        val nameEn = layout(v.nameEn ?: NA, paint(nameSize, ink, true), textW - badgeW - 4f)
        val lines = ArrayList<Pair<StaticLayout, Float>>() // layout, space after
        lines += layout(v.nameTe ?: NA, paint(nameSize, ink), textW, spacing = 1.05f) to 1f
        val status = statusText(r, o)
        if (status.isNotEmpty()) lines += layout(status, paint(small, teal, true), textW) to 0f
        if (o.relativeNames) {
            lines += layout(relativeLine(r), paint(small, muted), textW) to 0f
            v.relativeNameTe?.let { lines += layout(it, paint(small, muted), textW) to 0f }
        }
        if (detailed) {
            detailLines(r, o).forEach { lines += layout(it, paint(small, ink), textW) to 0f }
        } else {
            val facts = factsLine(r, o)
            if (facts.isNotEmpty()) lines += layout(facts, paint(7.5f, ink), textW) to 0f
        }
        if (o.tags && r.tags.isNotEmpty()) lines += layout("Tags: " + r.tags.joinToString(", "), paint(7.5f, ink), textW) to 0f
        if (o.notes && !r.note.isNullOrBlank()) lines += layout("Note: " + r.note.trim(), paint(7.5f, ink), textW) to 0f

        val headH = max(nameEn.height.toFloat(), badgeH)
        val textH = headH + lines.sumOf { (it.first.height + it.second).toDouble() }.toFloat()
        val h = max(photoH, textH) + pad * 2
        return Card(h) { c, x, y ->
            drawCardBackground(c, x, y, w, h)
            drawPhoto(c, v.photoFilename, RectF(x + pad, y + pad, x + pad + photoW, y + pad + photoH))
            val bx = x + w - pad - badgeW
            val by = y + pad
            c.drawRoundRect(RectF(bx, by, bx + badgeW, by + badgeH), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeFill })
            val fm = badgePaint.fontMetrics
            c.drawText(badgeText, bx + 4f, by + badgeH / 2 - (fm.ascent + fm.descent) / 2, badgePaint)
            c.save(); c.translate(x + textX, y + pad); nameEn.draw(c); c.restore()
            var ty = y + pad + headH
            lines.forEach { (l, after) ->
                c.save(); c.translate(x + textX, ty); l.draw(c); c.restore()
                ty += l.height + after
            }
        }
    }

    private fun drawCardBackground(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = RectF(x, y, x + w, y + h)
        c.drawRoundRect(r, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardFill })
        c.drawRoundRect(r, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hairline; style = Paint.Style.STROKE; strokeWidth = 0.6f })
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

    /** Detailed card: one labelled line per chosen fact. */
    private fun detailLines(r: ExportRecord, o: PdfOptions): List<String> {
        val v = r.voter
        return listOfNotNull(
            if (o.ageGender) "Age / Gender: " + (listOfNotNull(v.age?.toString(), v.gender).joinToString(", ").ifEmpty { NA }) else null,
            if (o.houseNo) "House no.: ${v.houseNo ?: NA}" else null,
            if (o.assemblySerial) "Assembly roll serial: ${v.assemblySerial ?: NA}" else null,
            if (o.epic) "EPIC no.: ${v.epicNo ?: NA}" else null,
        )
    }

    companion object {
        const val NA = "Not available"
        const val VILLAGE = "Etikoppaka Gram Panchayat, Andhra Pradesh"
        private const val HEADER_GAP = 8f
        private const val HEADING_GAP = 4f
        /** Space between two sections (households, blocks). */
        private const val SECTION_GAP = 10f
        private const val PRINT_DPI = 150f
    }
}
