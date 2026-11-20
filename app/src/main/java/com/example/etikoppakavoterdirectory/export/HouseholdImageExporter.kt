package com.example.etikoppakavoterdirectory.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.etikoppakavoterdirectory.data.ExportRecord
import com.example.etikoppakavoterdirectory.data.VoterRepository
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImageLayout { COMPACT, DETAILED }

/** What a household image shows. Sensitive identifiers are off by default. */
data class ImageOptions(
    val layout: ImageLayout = ImageLayout.COMPACT,
    val epic: Boolean = false,
    val assemblySerial: Boolean = false,
)

/**
 * Renders a household as one or more PNG images, entirely on the device.
 *
 * Text is laid out with [StaticLayout] (HarfBuzz shaping, so Telugu conjuncts and vowel signs are
 * correct) and wraps instead of being clipped; photos are fitted, never cropped or stretched. The
 * layout adapts to the household size (1-3 columns). Each image is at most [MAX_HEIGHT] px tall:
 * a larger household is split into several images ("Part 1 of 3") instead of one huge bitmap, and
 * only one bitmap is in memory at a time.
 */
class HouseholdImageExporter(private val context: Context) {

    private val width = 1080
    private val pad = 40f
    private val contentW = width - 2 * pad

    private val teal = Color.rgb(0x00, 0x69, 0x6A)
    private val ink = Color.rgb(0x18, 0x1C, 0x1C)
    private val muted = Color.rgb(0x55, 0x60, 0x60)
    private val hairline = Color.rgb(0xC4, 0xCD, 0xCC)
    private val cardFill = Color.rgb(0xF3, 0xF7, 0xF7)
    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int = ink, isBold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (isBold) bold else regular
    }

    private fun layout(text: String, p: TextPaint, w: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, w.roundToInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    private class Block(val height: Float, val draw: (Canvas, Float) -> Unit)

    /**
     * @return the PNG files written into [dir] (one per part), in order.
     */
    fun render(
        familyName: String,
        /** e.g. "House 8-144" or "Houses 8-143, 8-145 +1" */
        houseLabel: String?,
        records: List<ExportRecord>,
        options: ImageOptions,
        dir: File,
        baseName: String,
    ): List<File> {
        require(records.isNotEmpty()) { "Nothing to export" }
        val rows: List<Block> = if (options.layout == ImageLayout.COMPACT) compactRows(records) else detailedRows(records, options)
        val ward = records.first().voter.wardNo
        val footer = footerBlock()

        // Split rows into parts that fit MAX_HEIGHT together with the header and footer.
        val headerProbe = headerBlock(familyName, houseLabel, records.size, ward, part = 1, parts = 2)
        val room = MAX_HEIGHT - headerProbe.height - footer.height - 2 * pad
        val parts = ArrayList<MutableList<Block>>()
        var cur = ArrayList<Block>(); var used = 0f
        rows.forEach { r ->
            if (cur.isNotEmpty() && used + r.height > room) { parts += cur; cur = ArrayList(); used = 0f }
            cur += r; used += r.height + ROW_GAP
        }
        if (cur.isNotEmpty()) parts += cur

        dir.mkdirs()
        return parts.mapIndexed { i, part ->
            val header = headerBlock(familyName, houseLabel, records.size, ward, i + 1, parts.size)
            val h = ceil(pad + header.height + part.sumOf { (it.height + ROW_GAP).toDouble() }.toFloat() + footer.height + pad)
                .toInt().coerceAtMost(MAX_HEIGHT + 400)
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                var y = pad
                header.draw(c, y); y += header.height
                part.forEach { b -> b.draw(c, y); y += b.height + ROW_GAP }
                footer.draw(c, h - pad - footer.height)
                val suffix = if (parts.size > 1) "_part${i + 1}of${parts.size}" else ""
                File(dir, "$baseName$suffix.png").also { f -> f.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            } finally {
                bmp.recycle()
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    private fun headerBlock(name: String, house: String?, members: Int, ward: Int, part: Int, parts: Int): Block {
        val title = layout(name, paint(46f, Color.WHITE, true), contentW - 40f, 3)
        val line = listOfNotNull(house, "$members member${if (members == 1) "" else "s"}", "Ward $ward")
            .joinToString("  ·  ")
        val sub = layout(line, paint(28f, Color.WHITE), contentW - 40f, 2)
        val partL = if (parts > 1) layout("Part $part of $parts", paint(24f, Color.WHITE, true), contentW - 40f, 1) else null
        val inner = title.height + sub.height + (partL?.height ?: 0) + 8f
        val h = inner + 48f
        return Block(h + 24f) { c, y ->
            c.drawRoundRect(RectF(pad, y, pad + contentW, y + h), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal })
            var ty = y + 24f
            c.save(); c.translate(pad + 20f, ty); title.draw(c); c.restore(); ty += title.height + 4f
            c.save(); c.translate(pad + 20f, ty); sub.draw(c); c.restore(); ty += sub.height + 4f
            partL?.let { c.save(); c.translate(pad + 20f, ty); it.draw(c); c.restore() }
        }
    }

    private fun footerBlock(): Block {
        val generated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val l = layout(
            "Etikoppaka Voter Directory · Generated $generated\nPrivate – contains personal voter information. Share only with people entitled to see it.",
            paint(22f, muted), contentW, 4,
        )
        return Block(l.height + 16f) { c, y ->
            c.drawRect(pad, y, pad + contentW, y + 1.5f, Paint().apply { color = hairline })
            c.save(); c.translate(pad, y + 12f); l.draw(c); c.restore()
        }
    }

    /** Compact: a grid of member tiles (photo, serial, names, age / gender). */
    private fun compactRows(records: List<ExportRecord>): List<Block> {
        val cols = when (records.size) { 1 -> 1; 2, 4 -> 2; else -> 3 }
        val gap = 20f
        val tileW = (contentW - gap * (cols - 1)) / cols
        val photoH = if (cols == 1) 420f else if (cols == 2) 360f else 280f
        val photoW = photoH * 0.75f
        val tiles = records.map { r ->
            val v = r.voter
            val inner = tileW - 32f
            val ls = listOfNotNull(
                layout("${v.gpSerialNo}", paint(26f, teal, true), inner, 1),
                layout(v.nameEn ?: PdfExporter.NA, paint(30f, ink, true), inner, 3),
                layout(v.nameTe ?: PdfExporter.NA, paint(30f, ink), inner, 3),
                listOfNotNull(v.age?.let { "Age $it" }, v.gender).joinToString(" · ").takeIf { it.isNotEmpty() }
                    ?.let { layout(it, paint(24f, muted), inner, 2) },
            )
            val h = 16f + photoH + 12f + ls.sumOf { it.height + 2.0 }.toFloat() + 16f
            Triple(r, ls, h)
        }
        return tiles.chunked(cols).map { row ->
            val rowH = row.maxOf { it.third }
            Block(rowH) { c, y ->
                row.forEachIndexed { i, (r, ls, _) ->
                    val x = pad + i * (tileW + gap)
                    card(c, x, y, tileW, rowH)
                    photo(c, r.voter.photoFilename, RectF(x + (tileW - photoW) / 2, y + 16f, x + (tileW + photoW) / 2, y + 16f + photoH))
                    var ty = y + 16f + photoH + 12f
                    ls.forEach { l -> c.save(); c.translate(x + 16f, ty); l.draw(c); c.restore(); ty += l.height + 2f }
                }
            }
        }
    }

    /** Detailed: one card per member with all names, relation and the chosen identifiers. */
    private fun detailedRows(records: List<ExportRecord>, o: ImageOptions): List<Block> = records.map { r ->
        val v = r.voter
        val photoW = 210f; val photoH = 280f
        val textW = contentW - photoW - 3 * 20f
        val head = listOf(
            layout("GP Serial No. ${v.gpSerialNo}", paint(26f, teal, true), textW, 1),
            layout(v.nameEn ?: PdfExporter.NA, paint(34f, ink, true), textW, 3),
            layout(v.nameTe ?: PdfExporter.NA, paint(34f, ink), textW, 3),
        )
        val rel = v.relationship?.takeUnless { it.equals("Not specified", true) } ?: "Relative"
        val fields = buildList {
            add(rel to listOfNotNull(v.relativeNameEn, v.relativeNameTe).joinToString(" / ").ifEmpty { PdfExporter.NA })
            add("Age / Gender" to listOfNotNull(v.age?.toString(), v.gender).joinToString(" / ").ifEmpty { PdfExporter.NA })
            add("House no." to (v.houseNo ?: PdfExporter.NA))
            if (o.assemblySerial) add("Assembly roll serial" to (v.assemblySerial ?: PdfExporter.NA))
            if (o.epic) add("EPIC no." to (v.epicNo ?: PdfExporter.NA))
        }.map { (label, value) -> layout(label.uppercase(), paint(19f, muted, true), textW, 1) to layout(value, paint(26f, ink), textW, 4) }
        val textH = head.sumOf { it.height.toDouble() }.toFloat() + 10f + fields.sumOf { (it.first.height + it.second.height + 8).toDouble() }.toFloat()
        val h = max(photoH, textH) + 40f
        Block(h) { c, y ->
            card(c, pad, y, contentW, h)
            photo(c, v.photoFilename, RectF(pad + 20f, y + 20f, pad + 20f + photoW, y + 20f + photoH))
            val tx = pad + photoW + 40f
            var ty = y + 20f
            head.forEach { l -> c.save(); c.translate(tx, ty); l.draw(c); c.restore(); ty += l.height }
            ty += 10f
            fields.forEach { (label, value) ->
                c.save(); c.translate(tx, ty); label.draw(c); c.translate(0f, label.height.toFloat()); value.draw(c); c.restore()
                ty += label.height + value.height + 8f
            }
        }
    }

    private fun card(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = RectF(x, y, x + w, y + h)
        c.drawRoundRect(r, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardFill })
        c.drawRoundRect(r, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hairline; style = Paint.Style.STROKE; strokeWidth = 2f })
    }

    /** Original photo, fitted inside [box] (never cropped or stretched), decoded only as large as needed. */
    private fun photo(c: Canvas, fileName: String?, box: RectF) {
        val bmp = fileName?.let { load(it, box) }
        if (bmp == null) {
            c.drawRect(box, Paint().apply { color = Color.rgb(0xE6, 0xE9, 0xE9) })
            val p = paint(22f, muted); val t = "No photo"
            c.drawText(t, box.centerX() - p.measureText(t) / 2, box.centerY() + 8f, p)
            return
        }
        try {
            val s = min(box.width() / bmp.width, box.height() / bmp.height)
            val dw = bmp.width * s; val dh = bmp.height * s
            c.drawBitmap(bmp, null, RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        } finally {
            bmp.recycle()
        }
    }

    private fun load(fileName: String, box: RectF): Bitmap? = try {
        val path = "${VoterRepository.PHOTO_ASSET_DIR}/$fileName"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= box.width() && bounds.outHeight / (sample * 2) >= box.height()) sample *= 2
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    companion object {
        /** 1080 x 4000 ARGB = about 17 MB: the largest single bitmap created. */
        const val MAX_HEIGHT = 4000
        private const val ROW_GAP = 20f
    }
}
