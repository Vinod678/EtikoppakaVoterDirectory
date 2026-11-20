package com.example.etikoppakavoterdirectory

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Release data validation on a real Android runtime: imports the bundled dataset into a fresh
 * database and compares every stored value with assets/voters_data.json (read independently of
 * the app's parser), and decodes every bundled photo with the platform decoder.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceDatasetTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private lateinit var json: List<JSONObject>
    private lateinit var stored: Map<Int, VoterEntity>

    @Before
    fun setUp() = runBlocking<Unit> {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).build()
        repo = VoterRepository(
            db,
            { name -> context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() } },
            File(context.cacheDir, "safety-ondevice"),
        )
        repo.ensureImported()
        val raw = context.assets.open(VoterRepository.DATA_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(raw).getJSONArray("voters")
        json = List(arr.length()) { arr.getJSONObject(it) }
        stored = repo.directory(VoterFilter(ward = 11)).first().associate { it.voter.gpSerialNo to it.voter }
    }

    @After
    fun tearDown() = db.close()

    private fun JSONObject.s(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.i(k: String): Int? = if (isNull(k)) null else getInt(k)

    @Test
    fun exactly369_serials3691to4059_noDuplicates() {
        assertEquals(369, stored.size)
        assertEquals((3691..4059).toList(), stored.keys.sorted())
        assertEquals(369, json.map { it.getInt("gp_serial_no") }.toSet().size)
    }

    @Test
    fun everyImportedValueMatchesJson_includingNullsAndReviewFlags() {
        var nullsJson = 0
        var nullsDb = 0
        json.forEach { o ->
            val s = o.getInt("gp_serial_no")
            val v = stored.getValue(s)
            val msg = "serial $s"
            assertEquals(msg, o.getInt("ward_no"), v.wardNo)
            assertEquals(msg, o.s("name_en"), v.nameEn)
            assertEquals(msg, o.s("name_te"), v.nameTe)
            assertEquals(msg, o.s("relationship"), v.relationship)
            assertEquals(msg, o.s("relative_name_en"), v.relativeNameEn)
            assertEquals(msg, o.s("relative_name_te"), v.relativeNameTe)
            assertEquals(msg, o.i("age"), v.age)
            assertEquals(msg, o.s("gender"), v.gender)
            assertEquals(msg, o.s("house_no"), v.houseNo)
            assertEquals(msg, o.s("assembly_serial"), v.assemblySerial)
            assertEquals(msg, o.s("epic_no"), v.epicNo)
            assertEquals(msg, o.s("photo_filename"), v.photoFilename)
            assertEquals(msg, o.i("photo_width_px"), v.photoWidthPx)
            assertEquals(msg, o.i("photo_height_px"), v.photoHeightPx)
            assertEquals(msg, o.i("pdf_page"), v.pdfPage)
            assertEquals(msg, o.s("pdf_relation_label"), v.pdfRelationLabel)
            assertEquals(msg, o.s("excel_review_status"), v.excelReviewStatus)
            assertEquals(msg, o.getBoolean("needs_review"), v.needsReview)
            val codes = o.getJSONArray("review_issue_codes")
            assertEquals(msg, List(codes.length()) { codes.getString(it) }, v.issueCodes)
            listOf("name_en", "name_te", "relative_name_en", "relative_name_te", "pdf_relation_label")
                .forEach { if (o.isNull(it)) nullsJson++ }
            listOf(v.nameEn, v.nameTe, v.relativeNameEn, v.relativeNameTe, v.pdfRelationLabel)
                .forEach { if (it == null) nullsDb++ }
        }
        assertEquals("null values preserved", nullsJson, nullsDb)
        assertEquals(143, stored.values.count { it.needsReview })
    }

    @Test
    fun allPhotosDecode_365Jpeg_4Png_withRecordedDimensions() {
        var jpg = 0
        var png = 0
        stored.values.forEach { v ->
            val name = v.photoFilename!!
            assertTrue("photo named after serial: $name", name.startsWith("${v.gpSerialNo}."))
            val opts = BitmapFactory.Options()
            val bmp = context.assets.open("${VoterRepository.PHOTO_ASSET_DIR}/$name").use { BitmapFactory.decodeStream(it, null, opts) }
            assertNotNull("decodes: $name", bmp)
            val w = v.photoWidthPx!!; val h = v.photoHeightPx!!
            if (bmp!!.width == w && bmp.height == h) {
                // original, unchanged
            } else {
                // Otherwise an oversized scan resized for the APK by tools/sync_assets.py (max side
                // 800 px, same aspect ratio; listed in output/asset_photo_optimization.csv).
                assertTrue("only oversized photos are resized: $name", maxOf(w, h) > 800)
                assertEquals("resized $name", 800, maxOf(bmp!!.width, bmp.height))
                assertEquals("aspect $name", w.toDouble() / h, bmp.width.toDouble() / bmp.height, 0.01)
            }
            when (opts.outMimeType) {
                "image/jpeg" -> jpg++
                "image/png" -> png++
                else -> throw AssertionError("unexpected type ${opts.outMimeType} for $name")
            }
            bmp.recycle()
        }
        assertEquals(365, jpg)
        assertEquals(4, png)
    }

    @Test
    fun spotChecks_firstLast_pageBoundaries_andPngCases() {
        // First and last record.
        stored.getValue(3691).let {
            assertEquals("Varahalamma", it.nameEn); assertEquals("XOO1357615", it.epicNo)
            assertEquals("3691.jpg", it.photoFilename); assertEquals(1, it.pdfPage)
        }
        stored.getValue(4059).let {
            assertEquals("Brahmaji Lokkoju", it.nameEn); assertEquals("4059.jpg", it.photoFilename); assertEquals(14, it.pdfPage)
        }
        // PDF page boundaries: 27 cards per page.
        for (page in 1..13) {
            val lastOnPage = 3691 + page * 27 - 1
            assertEquals("last card of page $page", page, stored.getValue(lastOnPage).pdfPage)
            assertEquals("first card of page ${page + 1}", page + 1, stored.getValue(lastOnPage + 1).pdfPage)
        }
        // The four photos stored losslessly in the PDF.
        listOf(3827, 3840, 3841, 3897).forEach { assertEquals("$it.png", stored.getValue(it).photoFilename) }
        // Missing values stay missing.
        stored.getValue(3717).let {
            assertEquals(null, it.relativeNameEn); assertEquals(null, it.relativeNameTe); assertEquals("Not specified", it.relationship)
        }
    }
}
