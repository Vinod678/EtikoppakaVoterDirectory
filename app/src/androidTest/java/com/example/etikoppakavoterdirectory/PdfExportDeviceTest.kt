package com.example.etikoppakavoterdirectory

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.export.PdfExporter
import com.example.etikoppakavoterdirectory.export.PdfLayout
import com.example.etikoppakavoterdirectory.export.PdfOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Generates real PDFs on the device (read-only use of the app's data) and checks them with the
 * platform PdfRenderer. The files are written to the app's external files folder ("qa-pdf") so they
 * can be pulled and inspected visually.
 */
private fun w11(s: Int) = com.example.etikoppakavoterdirectory.data.db.VoterIds.of(11, s)

@RunWith(AndroidJUnit4::class)
class PdfExportDeviceTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VoterDirectoryApp
    private val repo = app.repository
    private val outDir = File(app.getExternalFilesDir(null), "qa-pdf").also { it.mkdirs() }

    // The export reads data for Admin only (Viewer sessions cannot export).
    @org.junit.Before fun signIn() = signInAdminDirect(app)
    @org.junit.After fun signOut() = signOutDirect(app)

    private fun export(name: String, serials: List<Int>, label: String, options: PdfOptions): Pair<File, Int> = runBlocking {
        repo.ensureImported()
        val records = repo.exportRecords(serials)
        assertEquals(serials.size, records.size)
        val file = File(outDir, "$name.pdf")
        val pages = file.outputStream().buffered().use { PdfExporter(app).write(records, label, options, it) }
        val head = file.inputStream().use { s -> ByteArray(5).also { s.read(it) } }
        assertEquals("%PDF-", String(head, Charsets.US_ASCII))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                assertEquals(pages, r.pageCount)
                r.openPage(0).use { p -> assertEquals(595, p.width); assertEquals(842, p.height) } // A4 in points
            }
        }
        file to pages
    }

    @Test
    fun allVoters_compact_andDetailed() = runBlocking<Unit> {
        val all = repo.allIds(11)
        assertEquals(369, all.size)
        val (_, compactPages) = export("all_compact", all, "All voters", PdfOptions())
        assertTrue("compact pages $compactPages", compactPages in 10..40)
        val (_, detailedPages) = export(
            "all_detailed_everything", all, "All voters",
            PdfOptions(layout = PdfLayout.DETAILED, assemblySerial = true, epic = true, notes = true, tags = true, favourite = true, visited = true),
        )
        assertTrue("detailed pages $detailedPages", detailedPages in (compactPages + 1)..100)
    }

    @Test
    fun filtered_household_selected_andLongNames() = runBlocking<Unit> {
        val blockA = repo.directory(VoterFilter(ward = 11, block = "A")).first().map { it.voter.voterId }
        if (blockA.isNotEmpty()) export("block_a", blockA, "Block A", PdfOptions(visited = true, favourite = true))
        export("household_like_8_144", listOf(3703, 3704, 3705, 3706).map(::w11), "Household — 8-144", PdfOptions(layout = PdfLayout.DETAILED, tags = true))
        // Long English and Telugu names (3781 has the longest Telugu name in the dataset) and a voter without relative.
        export("long_names", listOf(3781, 3717, 3874, 3875).map(::w11), "Selected voters", PdfOptions(assemblySerial = true, epic = true))
        export("single", listOf(w11(3691)), "Selected voters", PdfOptions(layout = PdfLayout.DETAILED, notes = true))
    }

    /**
     * Blocks + households as sections (Telugu, photos), each block on its own page; household
     * members already in a block are not printed twice. Uses whatever blocks exist on the device.
     */
    @Test
    fun sections_multiBlock_multiHousehold_combined() = runBlocking<Unit> {
        repo.ensureImported()
        val vm = com.example.etikoppakavoterdirectory.ui.ExportViewModel(repo, app)
        val blocks = repo.blockCounts(11).first().filter { it.total > 0 && it.block != com.example.etikoppakavoterdirectory.data.db.Blocks.UNASSIGNED }.map { it.block }
        val households = repo.householdSummaries(11).first().sortedByDescending { it.members }.take(3).map { it.id }
        for ((name, scope, layout) in listOf(
            Triple("blocks_only", com.example.etikoppakavoterdirectory.ui.ExportScope.Selection(11, blocks, emptyList()), PdfLayout.COMPACT),
            Triple("households_only", com.example.etikoppakavoterdirectory.ui.ExportScope.Selection(11, emptyList(), households), PdfLayout.DETAILED),
            Triple("blocks_and_households", com.example.etikoppakavoterdirectory.ui.ExportScope.Selection(11, blocks, households), PdfLayout.COMPACT),
        )) {
            val options = PdfOptions(layout = layout, householdNewPage = name == "households_only")
            val plan = vm.plan(scope, options)
            val sections = vm.pdfSections(plan)
            val printed = sections.flatMap { s -> s.records.map { it.voter.voterId } }
            assertEquals("no voter printed twice in $name", printed.size, printed.toSet().size)
            assertEquals(plan.uniqueIds.toSet(), printed.toSet())
            val file = File(outDir, "$name.pdf")
            val pages = file.outputStream().buffered().use { PdfExporter(app).writeSections(sections, scope.label, options, it) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { r -> assertEquals(pages, r.pageCount) } }
            if (name == "blocks_only") assertTrue("each block starts a new page", pages >= blocks.size)
            if (name == "households_only") assertTrue("each household on a new page", pages >= households.size)
        }
    }

    /** Compact and Detailed household images; a very large household is split into several images. */
    @Test
    fun householdImages_compactDetailed_andLargeSplit() = runBlocking<Unit> {
        repo.ensureImported()
        val exporter = com.example.etikoppakavoterdirectory.export.HouseholdImageExporter(app)
        val gutti = repo.householdSummaries(11).first().first { it.name == "Gutti" }
        val records = repo.exportRecords(repo.householdMemberIds(gutti.id))
        val opts = com.example.etikoppakavoterdirectory.export.ImageOptions(com.example.etikoppakavoterdirectory.export.ImageLayout.COMPACT)
        val compact = exporter.render(gutti.name, gutti.houseNo, records, opts, outDir, "img_compact")
        val detailed = exporter.render(gutti.name, gutti.houseNo, records, opts.copy(layout = com.example.etikoppakavoterdirectory.export.ImageLayout.DETAILED, epic = true), outDir, "img_detailed")
        assertEquals(1, compact.size); assertEquals(1, detailed.size)
        // 120 members (a synthetic stress case built from real records) must not create one huge bitmap.
        val many = repo.exportRecords(repo.allIds(11).take(120))
        val parts = exporter.render("Large test household", "8-139", many, opts.copy(layout = com.example.etikoppakavoterdirectory.export.ImageLayout.DETAILED), outDir, "img_large")
        assertTrue("split into parts: ${parts.size}", parts.size > 1)
        (compact + detailed + parts).forEach { f ->
            val b = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, b)
            assertEquals(1080, b.outWidth)
            assertTrue("height ${b.outHeight}", b.outHeight in 300..(com.example.etikoppakavoterdirectory.export.HouseholdImageExporter.MAX_HEIGHT + 400))
        }
    }
}
