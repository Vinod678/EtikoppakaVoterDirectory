package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.data.dataset.VoterJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Checks the bundled dataset and photos (plain JVM test; runs from the app module directory).
 */
class DatasetIntegrityTest {

    private val assets = File("src/main/assets")
    private val photosDir = File(assets, "voter_photos")
    private val json = File(assets, "voters_data.json").readText(Charsets.UTF_8)
    private val dataset = VoterJsonParser.parse(json)

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun exactly369Records_serials3691to4059_eachOnce() {
        val serials = dataset.voters.map { it.gpSerialNo }
        assertEquals(369, serials.size)
        assertEquals((3691..4059).toList(), serials.sorted())
        assertEquals(369, serials.toSet().size)
        assertTrue(dataset.voters.all { it.wardNo == 11 })
    }

    @Test
    fun everyRecordHasItsOwnPhoto_jpgOrPng_andNoExtraFiles() {
        val files = photosDir.listFiles()!!.filter { it.isFile }.map { it.name }.toSet() // ward_N/ sub-folders are other wards
        assertEquals(369, files.size)
        dataset.voters.forEach { v ->
            val name = v.photoFilename!!
            assertTrue("photo name for ${v.gpSerialNo}", name == "${v.gpSerialNo}.jpg" || name == "${v.gpSerialNo}.png")
            assertTrue("photo exists for ${v.gpSerialNo}", name in files)
        }
        assertEquals(365, dataset.voters.count { it.photoFilename!!.endsWith(".jpg") })
        assertEquals(4, dataset.voters.count { it.photoFilename!!.endsWith(".png") })
    }

    @Test
    fun bundledPhotosAreByteIdenticalToExtractedOriginals() {
        checkPhotosAgainstManifest(File("../output"), photosDir)
    }

    /**
     * Every bundled photo is either byte-identical to the extracted original (output/.../voter_photos),
     * or - for the few oversized scans - a resized copy listed in asset_photo_optimization.csv
     * (written by tools/sync_assets.py) whose original checksum matches the manifest, whose bundled
     * checksum matches the log, which is at most 800 px and keeps the original aspect ratio.
     */
    private fun checkPhotosAgainstManifest(outputDir: File, dir: File, count: Int = 369) {
        val manifest = File(outputDir, "voter_photos_manifest.csv")
        if (!manifest.isFile) return // source folder not present (e.g. on another machine)
        val lines = manifest.readLines().filter { it.isNotBlank() }
        assertEquals(count + 1, lines.size)
        val h = lines.first().split(",")
        val optLog = File(outputDir, "asset_photo_optimization.csv")
        val optimized = if (!optLog.isFile) emptyMap() else optLog.readLines().filter { it.isNotBlank() }.let { l ->
            val oh = l.first().split(",")
            l.drop(1).map { it.split(",") }.associate { c -> c[oh.indexOf("photo_filename")] to oh.zip(c).toMap() }
        }
        lines.drop(1).map { it.split(",") }.forEach { c ->
            val name = c[h.indexOf("photo_filename")]
            val original = c[h.indexOf("sha256")]
            val bundled = sha256(File(dir, name))
            val opt = optimized[name]
            if (opt == null) {
                assertEquals("checksum of $name", original, bundled)
            } else {
                assertEquals("original of resized $name", original, opt.getValue("original_sha256"))
                assertEquals("bundled resized $name", opt.getValue("asset_sha256"), bundled)
                val ow = opt.getValue("original_width_px").toDouble(); val oh = opt.getValue("original_height_px").toDouble()
                val aw = opt.getValue("asset_width_px").toDouble(); val ah = opt.getValue("asset_height_px").toDouble()
                assertTrue("size of $name", maxOf(aw, ah) <= 800 && maxOf(ow, oh) > 800)
                assertEquals("aspect ratio of $name", ow / oh, aw / ah, 0.01)
            }
        }
        assertTrue("every resized photo is bundled", optimized.keys.all { File(dir, it).isFile })
    }

    @Test
    fun photosHaveValidImageSignatures() {
        dataset.voters.forEach { v ->
            val bytes = File(photosDir, v.photoFilename!!).readBytes()
            if (v.photoFilename!!.endsWith(".jpg")) {
                assertTrue(bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
            } else {
                assertTrue(bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte())
            }
        }
    }

    @Test
    fun bundledJsonIdenticalToPreparedDataset() {
        val prepared = File("../output/voters_data.json")
        if (!prepared.isFile) return
        assertEquals(sha256(prepared), sha256(File(assets, "voters_data.json")))
    }

    @Test
    fun epicNumbersUnique_andCoreFieldsPresent() {
        assertEquals(369, dataset.voters.mapNotNull { it.epicNo }.toSet().size)
        assertTrue(dataset.voters.all { it.age != null && it.gender != null && it.houseNo != null && it.assemblySerial != null })
    }

    @Test
    fun missingValuesStayMissing_andValuesAreVerbatim() {
        val v3717 = dataset.voters.first { it.gpSerialNo == 3717 }
        assertEquals("Not specified", v3717.relationship)
        assertNull(v3717.relativeNameEn)
        assertNull(v3717.relativeNameTe)
        assertNull(v3717.pdfRelationLabel)
        assertTrue(v3717.needsReview)
        assertTrue("RELATIVE_MISSING_RELATION_UNKNOWN" in v3717.issueCodes)

        val v3691 = dataset.voters.first { it.gpSerialNo == 3691 }
        assertEquals("Varahalamma", v3691.nameEn)
        assertEquals("వరహలమ్మా", v3691.nameTe)
        assertEquals("నాగేశ్వ రరావు", v3691.relativeNameTe) // stray space kept exactly as in the source
        assertEquals("8-139", v3691.houseNo)
        assertEquals("XOO1357615", v3691.epicNo)
        assertEquals("32-7-264", v3691.assemblySerial)
        assertEquals(60, v3691.age)
        assertEquals("Female", v3691.gender)
    }

    @Test
    fun reviewCountsMatchPreparedDataset() {
        assertEquals(140, dataset.voters.count { it.needsReview })
        assertTrue(dataset.voters.filter { it.needsReview }.all { it.issueCodes.isNotEmpty() })
        assertTrue(dataset.voters.filter { !it.needsReview }.all { it.issueCodes.isEmpty() })
    }

    /** Later wards: assets/wards/voters_ward_N.json + assets/voter_photos/ward_N/, identical to output/ward_N/. */
    private fun checkWard(ward: Int, first: Int, male: Int, female: Int, count: Int = 369) {
        val file = File(assets, "wards/voters_ward_$ward.json")
        val w = VoterJsonParser.parse(file.readText(Charsets.UTF_8), "ward_$ward")
        assertEquals(ward, w.wardNo)
        assertEquals((first until first + count).toList(), w.voters.map { it.gpSerialNo })
        assertTrue(w.voters.all { it.wardNo == ward })
        assertEquals(count, w.voters.mapNotNull { it.epicNo }.toSet().size)
        assertTrue(w.voters.all { it.age != null && it.gender != null && it.houseNo != null && it.assemblySerial != null && !it.nameTe.isNullOrBlank() && !it.nameEn.isNullOrBlank() })
        assertTrue(w.voters.all { it.relationship in setOf("Father", "Husband", "Mother", "Not specified") })
        assertEquals(male, w.voters.count { it.gender == "Male" }) // official summary table of the roll
        assertEquals(female, w.voters.count { it.gender == "Female" })
        val dir = File(photosDir, "ward_$ward")
        assertEquals(count, dir.listFiles()!!.size)
        w.voters.forEach { v ->
            val name = v.photoFilename!!
            assertTrue(name == "ward_$ward/${v.gpSerialNo}.jpg" || name == "ward_$ward/${v.gpSerialNo}.png")
            assertTrue("photo for ${v.gpSerialNo}", File(photosDir, name).isFile)
        }
        val prepared = File("../output/ward_$ward/voters_ward_$ward.json")
        if (prepared.isFile) assertEquals(sha256(prepared), sha256(file))
        checkPhotosAgainstManifest(File("../output/ward_$ward"), dir, count)
    }

    @Test fun ward2_complete_serials370to738_ownPhotos_matchesPreparedOutput() = checkWard(2, 370, 165, 204)
    @Test fun ward3_complete_serials739to1107_ownPhotos_matchesPreparedOutput() = checkWard(3, 739, 179, 190)
    @Test fun ward4_complete_serials1108to1476_ownPhotos_matchesPreparedOutput() = checkWard(4, 1108, 186, 183)
    @Test fun ward1_complete_serials1to369_ownPhotos_matchesPreparedOutput() = checkWard(1, 1, 180, 189)
    @Test fun ward5_complete_serials1477to1845_ownPhotos_matchesPreparedOutput() = checkWard(5, 1477, 176, 193)
    @Test fun ward6_complete_serials1846to2214_ownPhotos_matchesPreparedOutput() = checkWard(6, 1846, 183, 186)
    @Test fun ward7_complete_serials2215to2583_ownPhotos_matchesPreparedOutput() = checkWard(7, 2215, 176, 193)
    @Test fun ward8_complete_serials2584to2952_ownPhotos_matchesPreparedOutput() = checkWard(8, 2584, 183, 186)
    @Test fun ward9_complete_serials2953to3321_ownPhotos_matchesPreparedOutput() = checkWard(9, 2953, 182, 187)
    @Test fun ward10_complete_serials3322to3690_ownPhotos_matchesPreparedOutput() = checkWard(10, 3322, 170, 199)
    @Test fun ward12_complete_serials4060to4428_ownPhotos_matchesPreparedOutput() = checkWard(12, 4060, 186, 183)
    @Test fun ward13_complete_serials4429to4797_ownPhotos_matchesPreparedOutput() = checkWard(13, 4429, 171, 198)
    @Test fun ward14_complete_serials4798to5170_ownPhotos_matchesPreparedOutput() = checkWard(14, 4798, 188, 185, count = 373)

    /** Every GP serial 1-5170 exactly once across the 14 bundled wards (Ward 11 in voters_data.json). */
    @Test
    fun allFourteenWards_coverEverySerialOnce() {
        val wards = (1..14).filter { it != 11 }.map { VoterJsonParser.parse(File(assets, "wards/voters_ward_$it.json").readText(Charsets.UTF_8), "ward_$it") }
        val serials = wards.flatMap { w -> w.voters.map { it.gpSerialNo } } + dataset.voters.map { it.gpSerialNo }
        assertEquals((1..5170).toList(), serials.sorted())
        assertEquals(5170, (wards.flatMap { w -> w.voters.mapNotNull { it.epicNo } } + dataset.voters.mapNotNull { it.epicNo }).toSet().size)
    }
}
