package com.example.etikoppakavoterdirectory

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.export.PdfExporter
import com.example.etikoppakavoterdirectory.export.PdfLayout
import com.example.etikoppakavoterdirectory.export.PdfOptions
import com.example.etikoppakavoterdirectory.ui.ExportScope
import com.example.etikoppakavoterdirectory.ui.ExportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sample PDFs of every export type for visual review (read-only use of the app's data). Writes
 * the PDFs, PNG renderings of selected pages and "pages.txt" (page count per file) to the app's
 * external files folder "pdf-samples", which can be pulled with adb.
 */
@RunWith(AndroidJUnit4::class)
class PdfSamplesTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VoterDirectoryApp
    private val repo = app.repository
    private val outDir = File(app.getExternalFilesDir(null), "pdf-samples").also { it.deleteRecursively(); it.mkdirs() }
    private val counts = StringBuilder()

    // The export reads data for Admin only (Viewer sessions cannot export).
    @org.junit.Before fun signIn() = signInAdminDirect(app)
    @org.junit.After fun signOut() = signOutDirect(app)

    private fun write(name: String, scope: ExportScope, options: PdfOptions, renderPages: List<Int> = listOf(0, 1)) = runBlocking {
        val vm = ExportViewModel(repo, app)
        val sections = vm.pdfSections(vm.plan(scope, options))
        val file = File(outDir, "$name.pdf")
        val pages = file.outputStream().buffered().use { PdfExporter(app).writeSections(sections, scope.pdfLabel, options, it) }
        counts.append("$name\t$pages\n")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                assertEquals(pages, r.pageCount)
                (renderPages + (r.pageCount - 1)).distinct().filter { it in 0 until r.pageCount }.forEach { i ->
                    r.openPage(i).use { p ->
                        val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        File(outDir, "${name}_p${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun samples() = runBlocking<Unit> {
        repo.ensureImported()
        val ward = 11
        write("voters_compact", ExportScope.All(ward), PdfOptions())
        write("voters_detailed", ExportScope.All(ward), PdfOptions(layout = PdfLayout.DETAILED, assemblySerial = true, epic = true))

        val blocks = repo.blockCounts(ward).first().filter { it.total > 0 }.map { it.block }
        val someBlocks = blocks.filter { it != Blocks.UNASSIGNED }.ifEmpty { listOf(Blocks.UNASSIGNED) }
        write("blocks", ExportScope.Selection(ward, someBlocks, emptyList()), PdfOptions())

        val tag = repo.tags(ward).first().filter { it.usage > 0 }.maxByOrNull { it.usage }
        if (tag != null) {
            val ids = repo.directory(VoterFilter(ward = ward, tagId = tag.id)).first().map { it.voter.voterId }
            write("tag", ExportScope.Preset("Tag: ${tag.name}", ids), PdfOptions(tags = true))
        }

        val households = repo.householdSummaries(ward).first().map { it.id }
        // Default order (largest first: single-member households come last, consecutively).
        write("households_largest_first", ExportScope.Selection(ward, emptyList(), households), PdfOptions(), listOf(0, 1, 2))
        // By first serial number: single- and multi-member households alternate.
        write("households_by_serial", ExportScope.Selection(ward, emptyList(), households), PdfOptions(householdsLargestFirst = false), listOf(0, 1, 2, 3))
        write("households_detailed", ExportScope.Selection(ward, emptyList(), households), PdfOptions(layout = PdfLayout.DETAILED, householdsLargestFirst = false), listOf(0, 1))

        // Longest English and Telugu names, everything shown: text must wrap, never be cut.
        val longNames = listOf(3781, 3717, 3874, 3875).map { com.example.etikoppakavoterdirectory.data.db.VoterIds.of(ward, it) }
        write("long_names", ExportScope.Preset("Selected voters", longNames), PdfOptions(assemblySerial = true, epic = true, tags = true, notes = true, visited = true, favourite = true))
        write("long_names_detailed", ExportScope.Preset("Selected voters", longNames), PdfOptions(layout = PdfLayout.DETAILED, assemblySerial = true, epic = true, tags = true, notes = true, visited = true, favourite = true))

        File(outDir, "pages.txt").writeText(counts.toString())
    }
}
