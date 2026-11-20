package com.example.etikoppakavoterdirectory.ui

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.etikoppakavoterdirectory.VoterDirectoryApp
import com.example.etikoppakavoterdirectory.data.UserInputException
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.HouseholdEntity
import com.example.etikoppakavoterdirectory.export.HouseholdImageExporter
import com.example.etikoppakavoterdirectory.export.ImageOptions
import com.example.etikoppakavoterdirectory.export.PdfExporter
import com.example.etikoppakavoterdirectory.export.PdfOptions
import com.example.etikoppakavoterdirectory.export.PdfSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun CreationExtras.voterApp() = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VoterDirectoryApp

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/**
 * All user edits - single voter or many at once - in one place. Activity-scoped, so an edit that is
 * still running when a page is swiped away or closed is never cancelled.
 */
class EditViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {

    val repository: VoterRepository get() = repo

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.messages.error(friendlyError(e))
        }
    }

    // ---------- one voter ----------

    fun setFavourite(serial: Int, on: Boolean) = launchSafely { repo.setFavourite(serial, on) }
    fun setVisited(serial: Int, on: Boolean) = launchSafely { repo.setVisited(serial, on, recordTime = true) }
    fun clearVisitTime(serial: Int) = launchSafely { repo.clearVisitTime(serial) }

    /**
     * Same as [setVisited] (visit time recorded, unvisit clears it) and then hands back an undo
     * action that restores the exact previous state, including the original visit time.
     */
    fun setVisitedUndoable(serial: Int, on: Boolean, onChanged: (undo: () -> Unit) -> Unit) = launchSafely {
        val (wasVisited, wasAt) = repo.visitState(serial)
        if (wasVisited == on) return@launchSafely
        repo.setVisited(serial, on, recordTime = true)
        onChanged { launchSafely { repo.restoreVisit(serial, wasVisited, wasAt) } }
    }
    fun setBlock(serial: Int, block: String?) = launchSafely { repo.setBlock(serial, block) }
    fun setTag(serial: Int, tagId: Long, attached: Boolean) = launchSafely { repo.setTagAttached(serial, tagId, attached) }

    fun saveNote(serial: Int, note: String, onSaved: () -> Unit) = launchSafely {
        repo.setNote(serial, note)
        onSaved()
        app.messages.show(if (note.isEmpty()) "Note deleted" else "Note saved")
    }

    // ---------- many voters ----------

    fun bulkFavourite(serials: Collection<Int>, on: Boolean, done: () -> Unit = {}) = launchSafely {
        val n = repo.setFavourites(serials, on)
        done()
        app.messages.show(if (on) "${plural(n, "voter")} added to Favourites" else "${plural(n, "voter")} removed from Favourites")
    }

    fun bulkVisited(serials: Collection<Int>, on: Boolean, done: () -> Unit = {}) = launchSafely {
        val n = repo.setVisitedMany(serials, on)
        done()
        app.messages.show(if (on) "${plural(n, "voter")} marked visited" else "${plural(n, "voter")} marked not visited")
    }

    fun bulkBlock(serials: Collection<Int>, block: String?, done: () -> Unit = {}) = launchSafely {
        val n = repo.setBlocks(serials, block)
        done()
        app.messages.show(if (block == null) "${plural(n, "voter")} unassigned" else "${plural(n, "voter")} assigned to block $block")
    }

    fun bulkTag(serials: Collection<Int>, tagId: Long, tagName: String, attach: Boolean, done: () -> Unit = {}) = launchSafely {
        repo.setTagForMany(serials, tagId, attach)
        done()
        app.messages.show(
            if (attach) "Tag \"$tagName\" added to ${plural(serials.size, "voter")}"
            else "Tag \"$tagName\" removed from ${plural(serials.size, "voter")}"
        )
    }

    // ---------- households ----------

    fun createHousehold(name: String?, serials: Collection<Int>, onDone: (Long) -> Unit = {}, onError: ((String) -> Unit)? = null) =
        viewModelScope.launch {
            try {
                val id = repo.createHousehold(name, serials)
                onDone(id)
                app.messages.show("Household created with ${plural(serials.size, "member")}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = friendlyError(e)
                if (onError != null) onError(m) else app.messages.error(m)
            }
        }

    fun addToHousehold(id: Long, serials: Collection<Int>, done: () -> Unit = {}) = launchSafely {
        repo.addHouseholdMembers(id, serials)
        done()
        app.messages.show("${plural(serials.size, "voter")} added to the household")
    }

    fun removeFromHousehold(serials: Collection<Int>, done: () -> Unit = {}) = launchSafely {
        val n = repo.removeHouseholdMembers(serials)
        done()
        app.messages.show("${plural(n, "member")} removed from the household")
    }

    fun renameHousehold(id: Long, name: String, onDone: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            repo.renameHousehold(id, name)
            onDone()
            app.messages.show("Household renamed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(friendlyError(e))
        }
    }

    fun mergeHouseholds(target: Long, others: Collection<Long>, done: () -> Unit = {}) = launchSafely {
        repo.mergeHouseholds(target, others)
        done()
        app.messages.show("${plural(others.size, "household")} merged")
    }

    /** Moves voters from household [source] into the existing household [target] (see repository). */
    fun moveToHousehold(source: Long, serials: Collection<Int>, target: Long, done: (VoterRepository.MoveResult) -> Unit = {}) = launchSafely {
        val r = repo.moveHouseholdMembers(source, serials, target)
        done(r)
        app.messages.show(
            (if (r.moved == 1) "Voter moved successfully." else "${r.moved} voters moved successfully.") +
                if (r.sourceDeleted) " The empty household was removed." else ""
        )
    }

    fun splitHousehold(id: Long, serials: Collection<Int>, done: (Long) -> Unit = {}) = launchSafely {
        val newId = repo.splitHousehold(id, serials, null)
        done(newId)
        app.messages.show("${plural(serials.size, "member")} moved to a new household")
    }

    fun deleteHousehold(id: Long, done: () -> Unit = {}) = launchSafely {
        repo.deleteHousehold(id)
        done()
        app.messages.show("Household removed. Voter records are not changed.")
    }

    /** Confirms an automatically grouped household; it is then kept exactly as it is. */
    fun markHouseholdReviewed(id: Long) = launchSafely {
        repo.markHouseholdReviewed(id)
        app.messages.show("Household marked as reviewed")
    }

    /** Re-runs automatic grouping for [ward]. Manual corrections are kept. */
    fun regroupWard(ward: Int) = launchSafely {
        repo.regroupWardNow(ward)
        app.messages.show("Automatic households updated for Ward $ward. Your own changes were kept.")
    }

    fun addDefaultTags() = launchSafely {
        val n = repo.addDefaultTags()
        app.messages.show(if (n == 0) "All suggested tags already exist" else "${plural(n, "suggested tag")} added")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = voterApp(); EditViewModel(a.repository, a) }
        }
    }
}

// ======================================================================================
// PDF export
// ======================================================================================

/** Which voters to export. [Preset] = a list prepared by the caller (selection, household, filter). */
sealed interface ExportScope {
    val label: String
    /** What the PDF header says about the export: like [label], without counts such as "96 households". */
    val pdfLabel: String get() = label
    data class Preset(override val label: String, val ids: List<Int>) : ExportScope
    /** Every voter of [ward]; null = every imported ward (explicit global, multi-ward export). */
    data class All(val ward: Int?) : ExportScope {
        override val label get() = if (ward == null) "All wards" else "All voters"
    }
    data class Favourites(val ward: Int) : ExportScope { override val label = "Favourites" }
    data class Visited(val ward: Int) : ExportScope { override val label = "Visited voters" }
    data class NotVisited(val ward: Int) : ExportScope { override val label = "Not visited voters" }
    /** Several blocks and / or households of one ward, exported as separate sections. */
    data class Selection(val ward: Int, val blocks: List<String>, val households: List<Long>) : ExportScope {
        override val label: String get() = listOfNotNull(
            blocks.takeIf { it.isNotEmpty() }?.let { b -> "Block " + sortBlocks(b).joinToString(", ") { if (it == Blocks.UNASSIGNED) "Unassigned" else it } },
            households.takeIf { it.isNotEmpty() }?.let { "${it.size} household${if (it.size == 1) "" else "s"}" },
        ).joinToString(" + ").ifEmpty { "Nothing selected" }
        override val pdfLabel: String get() = listOfNotNull(
            blocks.takeIf { it.isNotEmpty() }?.let { b -> "Block " + sortBlocks(b).joinToString(", ") { if (it == Blocks.UNASSIGNED) "Unassigned" else it } },
            households.takeIf { it.isNotEmpty() }?.let { "Households" },
        ).joinToString(" + ").ifEmpty { "Nothing selected" }
    }
}

/** Blocks in A, B, C, D order, "Unassigned" last. */
fun sortBlocks(blocks: Collection<String>): List<String> =
    blocks.distinct().sortedBy { if (it == Blocks.UNASSIGNED) Int.MAX_VALUE else Blocks.ALL.indexOf(it) }

/** One planned PDF section (voter ids only; records are loaded when the PDF is made). */
data class PlannedSection(
    val title: String?,
    val subtitle: String?,
    val ids: List<Int>,
    val startNewPage: Boolean,
    val keepTogether: Boolean,
    /** Members already printed in an earlier section, with the name of that section. */
    val alreadyListed: List<Pair<Int, String>> = emptyList(),
    /** A household (consecutive single-member households are printed side by side). */
    val household: Boolean = false,
)

data class ExportPlan(val sections: List<PlannedSection>) {
    /** Each voter once, in the order of first appearance. */
    val uniqueIds: List<Int> get() = sections.flatMap { it.ids }.distinct()
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Working(val message: String) : ExportStatus
    data class Ready(val file: File, val label: String, val records: Int, val pages: Int) : ExportStatus
}

class ExportViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {

    private val _status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val status: StateFlow<ExportStatus> = _status.asStateFlow()

    private val _count = MutableStateFlow<Int?>(null)
    /** Number of unique voters in the chosen scope (null while counting). */
    val count: StateFlow<Int?> = _count.asStateFlow()

    private var countJob: Job? = null
    private val dir get() = File(app.cacheDir, EXPORT_DIR)

    /**
     * Turns a scope into sections. For [ExportScope.Selection]: selected blocks first (A, B, C, D,
     * each on a new page), then selected households. A voter already printed in a block section is
     * not repeated in a later household section; the household heading lists them instead.
     */
    suspend fun plan(scope: ExportScope, options: PdfOptions = PdfOptions()): ExportPlan = when (scope) {
        is ExportScope.Preset -> single(scope.ids)
        is ExportScope.All -> single(repo.allIds(scope.ward))
        is ExportScope.Favourites -> single(ids(VoterFilter(ward = scope.ward, favouritesOnly = true)))
        is ExportScope.Visited -> single(ids(VoterFilter(ward = scope.ward, visited = true)))
        is ExportScope.NotVisited -> single(ids(VoterFilter(ward = scope.ward, visited = false)))
        is ExportScope.Selection -> selectionPlan(scope, options)
    }

    private fun single(ids: List<Int>) = ExportPlan(listOf(PlannedSection(null, null, ids, startNewPage = false, keepTogether = false)))

    private suspend fun ids(f: VoterFilter) = repo.directory(f).first().map { it.voter.voterId }

    private suspend fun selectionPlan(scope: ExportScope.Selection, options: PdfOptions): ExportPlan {
        val sections = ArrayList<PlannedSection>()
        val listedIn = LinkedHashMap<Int, String>()
        val labels = repo.blockLabelMap(scope.ward)
        sortBlocks(scope.blocks).forEach { b ->
            val ids = repo.idsInBlock(scope.ward, b)
            val name = if (b == Blocks.UNASSIGNED) "Unassigned" else "Block $b"
            val heading = labels[b]?.let { "$name — $it" } ?: name
            sections += PlannedSection("$heading — ${ids.size} Voter${if (ids.size == 1) "" else "s"}", null, ids, startNewPage = true, keepTogether = false)
            ids.forEach { listedIn.putIfAbsent(it, name) }
        }
        val households = repo.householdsIn(scope.households).filter { it.wardNo == scope.ward }
            .map { it to repo.householdMemberIds(it.id) }
            .filter { it.second.isNotEmpty() }
        val ordered = if (options.householdsLargestFirst) {
            households.sortedWith(compareByDescending<Pair<HouseholdEntity, List<Int>>> { it.second.size }.thenBy { it.first.name.lowercase() })
        } else {
            households.sortedBy { it.second.minOrNull() ?: Int.MAX_VALUE }
        }
        ordered.forEach { (h, members) ->
            val fresh = members.filter { it !in listedIn }
            val dup = members.filter { it in listedIn }.map { it to listedIn.getValue(it) }
            val subtitle = listOfNotNull(
                com.example.etikoppakavoterdirectory.ui.components.houseLabel(repo.houseNumbersOf(members)),
                "${members.size} member${if (members.size == 1) "" else "s"}",
                if (dup.isNotEmpty()) "${dup.size} already listed above" else null,
            ).joinToString(" · ")
            sections += PlannedSection(h.name, subtitle, fresh, startNewPage = options.householdNewPage, keepTogether = true, alreadyListed = dup, household = true)
            fresh.forEach { listedIn.putIfAbsent(it, h.name) }
        }
        return ExportPlan(sections)
    }

    /** Loads the records of a plan and turns it into PDF sections ("already listed" members as references). */
    suspend fun pdfSections(plan: ExportPlan): List<PdfSection> {
        val records = repo.exportRecords(plan.uniqueIds).associateBy { it.voter.voterId }
        return plan.sections.map { s ->
            PdfSection(
                title = s.title,
                subtitle = s.subtitle,
                records = s.ids.mapNotNull { records[it] },
                startNewPage = s.startNewPage,
                keepTogether = s.keepTogether,
                household = s.household,
                references = s.alreadyListed.map { (id, where) ->
                    val v = records[id]?.voter
                    "${v?.gpSerialNo ?: ""} ${v?.nameEn ?: ""} ($where)".trim()
                },
            )
        }.filter { it.records.isNotEmpty() || it.references.isNotEmpty() }
    }

    fun updateCount(scope: ExportScope, options: PdfOptions = PdfOptions()) {
        countJob?.cancel()
        _count.value = null
        countJob = viewModelScope.launch {
            _count.value = try { plan(scope, options).uniqueIds.size } catch (e: CancellationException) { throw e } catch (e: Exception) { 0 }
        }
    }

    fun generate(scope: ExportScope, options: PdfOptions) {
        if (_status.value is ExportStatus.Working) return
        _status.value = ExportStatus.Working("Preparing…")
        viewModelScope.launch {
            try {
                val plan = plan(scope, options)
                val unique = plan.uniqueIds
                if (unique.isEmpty()) {
                    _status.value = ExportStatus.Idle
                    app.messages.error("There are no voters to export in \"${scope.label}\".")
                    return@launch
                }
                val sections = pdfSections(plan)
                val wards = sections.flatMap { it.records }.map { it.voter.wardNo }.toSet()
                val file = withContext(Dispatchers.IO) {
                    deleteExports()
                    dir.mkdirs()
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    val place = if (wards.size == 1) "ward${wards.first()}" else "allwards"
                    File(dir, "etikoppaka_${place}_${slug(scope.label)}_$stamp.pdf")
                }
                val pages = withContext(Dispatchers.Default) {
                    file.outputStream().buffered().use { out ->
                        PdfExporter(app).writeSections(sections, scope.pdfLabel, options, out) { done, total ->
                            _status.value = ExportStatus.Working("Creating page $done of $total…")
                        }
                    }
                }
                _status.value = ExportStatus.Ready(file, scope.label, unique.size, pages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = ExportStatus.Idle
                app.messages.error("The PDF could not be created. Please try again.")
            } catch (e: OutOfMemoryError) {
                _status.value = ExportStatus.Idle
                app.messages.error("Not enough memory to create this PDF. Try a smaller selection.")
            }
        }
    }

    /** Copies the finished PDF to a location the user picked (Storage Access Framework). */
    fun save(resolver: ContentResolver, uri: Uri) {
        val ready = _status.value as? ExportStatus.Ready ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { out -> ready.file.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException()
                }
                app.messages.show("PDF saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error("Could not save the PDF to the chosen location.")
            }
        }
    }

    private fun contentUri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Android share sheet (WhatsApp and any other installed app). Nothing is sent unless the user picks an app. */
    fun shareIntent(context: Context): Intent? {
        val ready = _status.value as? ExportStatus.Ready ?: return null
        val uri = contentUri(context, ready.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(ready.file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share PDF")
    }

    /** Opens the PDF read-only in the phone's PDF viewer (temporary read permission only). */
    fun viewIntent(context: Context): Intent? {
        val ready = _status.value as? ExportStatus.Ready ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri(context, ready.file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun reset() {
        _status.value = ExportStatus.Idle
    }

    /** Leaving the export screen: forget the result and delete the temporary PDF. */
    fun discard() {
        if (_status.value is ExportStatus.Working) return
        _status.value = ExportStatus.Idle
        viewModelScope.launch(Dispatchers.IO) { deleteExports() }
    }

    private fun deleteExports() {
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    override fun onCleared() {
        // Temporary export files contain personal data: remove them when the export screen closes.
        deleteExports()
    }

    companion object {
        const val EXPORT_DIR = "exports"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = voterApp(); ExportViewModel(a.repository, a) }
        }
    }
}

internal fun slug(s: String) = s.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifEmpty { "voters" }

// ======================================================================================
// Household image export
// ======================================================================================

sealed interface ImageStatus {
    data object Idle : ImageStatus
    data class Working(val message: String) : ImageStatus
    data class Ready(val files: List<File>, val title: String) : ImageStatus
}

class ImageExportViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {

    private val _status = MutableStateFlow<ImageStatus>(ImageStatus.Idle)
    val status: StateFlow<ImageStatus> = _status.asStateFlow()
    private val dir get() = File(File(app.cacheDir, ExportViewModel.EXPORT_DIR), IMAGE_DIR)

    fun generate(householdId: Long, options: ImageOptions) {
        if (_status.value is ImageStatus.Working) return
        _status.value = ImageStatus.Working("Creating image…")
        viewModelScope.launch {
            try {
                val h = repo.householdsIn(listOf(householdId)).firstOrNull() ?: throw UserInputException("This household no longer exists.")
                val records = repo.exportRecords(repo.householdMemberIds(householdId))
                if (records.isEmpty()) throw UserInputException("This household has no members.")
                val files = withContext(Dispatchers.Default) {
                    deleteImages()
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    val houses = com.example.etikoppakavoterdirectory.ui.components.houseLabel(records.map { it.voter.houseNo })
                    HouseholdImageExporter(app).render(h.name, houses, records, options, dir, "household_ward${h.wardNo}_${slug(h.name)}_$stamp")
                }
                _status.value = ImageStatus.Ready(files, h.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _status.value = ImageStatus.Idle
                app.messages.error("Not enough memory to create this image.")
            } catch (e: Exception) {
                _status.value = ImageStatus.Idle
                app.messages.error(if (e is UserInputException) friendlyError(e) else "The image could not be created. Please try again.")
            }
        }
    }

    /** Saves one image to a document the user created with the system file picker. */
    fun saveOne(resolver: ContentResolver, uri: Uri, index: Int) {
        val ready = _status.value as? ImageStatus.Ready ?: return
        val file = ready.files.getOrNull(index) ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException()
                }
                app.messages.show("Image saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error("Could not save the image to the chosen location.")
            }
        }
    }

    /** Saves every part into a folder the user picked (Storage Access Framework). */
    fun saveAll(context: Context, treeUri: Uri) {
        val ready = _status.value as? ImageStatus.Ready ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                    ready.files.forEach { f ->
                        val doc = DocumentsContract.createDocument(resolver, parent, "image/png", f.name) ?: throw IllegalStateException()
                        resolver.openOutputStream(doc, "wt")?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException()
                    }
                }
                app.messages.show("${ready.files.size} images saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error("Could not save the images to the chosen folder.")
            }
        }
    }

    /** Share sheet for all parts (WhatsApp and other apps). Temporary read access only. */
    fun shareIntent(context: Context): Intent? {
        val ready = _status.value as? ImageStatus.Ready ?: return null
        val uris = ArrayList(ready.files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        send.type = "image/png"
        send.clipData = ClipData.newRawUri(ready.title, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share image")
    }

    fun reset() { _status.value = ImageStatus.Idle }

    fun discard() {
        if (_status.value is ImageStatus.Working) return
        _status.value = ImageStatus.Idle
        viewModelScope.launch(Dispatchers.IO) { deleteImages() }
    }

    private fun deleteImages() { dir.listFiles()?.forEach { it.delete() } }

    override fun onCleared() = deleteImages()

    companion object {
        const val IMAGE_DIR = "images"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = voterApp(); ImageExportViewModel(a.repository, a) }
        }
    }
}
