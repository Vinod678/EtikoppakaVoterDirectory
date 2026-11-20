package com.example.etikoppakavoterdirectory.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.etikoppakavoterdirectory.VoterDirectoryApp
import com.example.etikoppakavoterdirectory.data.ImportResult
import com.example.etikoppakavoterdirectory.data.UserInputException
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.backup.BackupContent
import com.example.etikoppakavoterdirectory.data.backup.BackupException
import com.example.etikoppakavoterdirectory.data.dataset.DatasetException
import com.example.etikoppakavoterdirectory.data.db.BlockCount
import com.example.etikoppakavoterdirectory.data.db.DatasetInfoEntity
import com.example.etikoppakavoterdirectory.data.db.DirectoryStats
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.data.db.VoterListItem
import com.example.etikoppakavoterdirectory.data.db.VoterUserDataEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun CreationExtras.app() = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VoterDirectoryApp

/** Maps an exception to a short message without exposing personal data. */
internal fun friendlyError(e: Throwable): String = when (e) {
    is UserInputException, is BackupException, is DatasetException -> e.message ?: "Something went wrong."
    is android.database.sqlite.SQLiteException -> "The local database could not be updated. Please try again."
    else -> "Something went wrong. Please try again."
}

sealed interface ImportState {
    data object Loading : ImportState
    data class Ready(val result: ImportResult) : ImportState
    data class Failed(val message: String) : ImportState
}

// ======================================================================================
// Directory (list, search, filters, statistics). Activity-scoped and shared by the tabs.
// The active ward is part of the state: every list, count and filter belongs to that ward.
// ======================================================================================

/** Voter and household counts for one ward card on the home screen. */
data class WardSummary(val wardNo: Int, val voters: Int, val households: Int) {
    val imported get() = voters > 0
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DirectoryViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Loading)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** The ward currently open, or null on the home screen. */
    private val _ward = MutableStateFlow<Int?>(null)
    val ward: StateFlow<Int?> = _ward.asStateFlow()

    /** Filters are remembered per ward, so going to another ward and back keeps them. */
    private val savedFilters = HashMap<Int, VoterFilter>()
    private val _filter = MutableStateFlow(VoterFilter(ward = NO_WARD))
    val filter: StateFlow<VoterFilter> = _filter.asStateFlow()

    val items: StateFlow<List<VoterListItem>> = _filter
        .debounce(150)
        .flatMapLatest { f -> if (f.ward == null || f.ward < 1) flowOf(emptyList()) else repo.directory(f) }
        .catch { app.messages.error(friendlyError(it)); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> perWard(empty: T, f: (Int) -> Flow<T>): StateFlow<T> = _ward
        .flatMapLatest { w -> if (w == null) flowOf(empty) else f(w) }
        .catch { app.messages.error(friendlyError(it)); emit(empty) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, empty)

    val stats: StateFlow<DirectoryStats?> = perWard<DirectoryStats?>(null) { repo.stats(it) }
    val blockCounts: StateFlow<List<BlockCount>> = perWard(emptyList()) { repo.blockCounts(it) }
    val tags: StateFlow<List<TagWithCount>> = _ward
        .flatMapLatest { repo.tags(it) }
        .catch { app.messages.error(friendlyError(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val households: StateFlow<List<HouseholdSummary>> = perWard(emptyList()) { repo.householdSummaries(it) }
    /** Optional block labels of the open ward ("A" -> "Main Street"). */
    val blockLabels: StateFlow<Map<String, String>> = perWard(emptyMap()) { repo.blockLabels(it) }

    fun setBlockLabel(block: String, label: String, onDone: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        val w = _ward.value ?: return@launch
        try {
            repo.setBlockLabel(w, block, label)
            onDone()
            app.messages.show(if (label.isBlank()) "Label removed from Block $block" else "Block $block label saved")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(friendlyError(e))
        }
    }

    // ---------- home screen ----------

    val wards: StateFlow<List<WardSummary>> = combine(repo.wardVoterCounts(), repo.wardHouseholdCounts()) { v, h ->
        val vm = v.associate { it.wardNo to it.total }
        val hm = h.associate { it.wardNo to it.total }
        (1..VoterIds.MAX_WARD).map { WardSummary(it, vm[it] ?: 0, hm[it] ?: 0) }
    }
        .catch { app.messages.error(friendlyError(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _globalQuery = MutableStateFlow("")
    val globalQuery: StateFlow<String> = _globalQuery.asStateFlow()

    /** Quick search across every imported ward. Empty query = no results (null). */
    val globalResults: StateFlow<List<VoterListItem>?> = _globalQuery
        .debounce(200)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(null) else repo.directory(VoterFilter(ward = null, query = q)) }
        .catch { app.messages.error(friendlyError(it)); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setGlobalQuery(q: String) { _globalQuery.value = q }

    init {
        runImport()
    }

    fun runImport() {
        _importState.value = ImportState.Loading
        viewModelScope.launch {
            _importState.value = try {
                val r = repo.ensureImported()
                if (r.failed.isNotEmpty()) app.messages.error("${r.failed.size} ward data file(s) could not be read and were skipped.")
                ImportState.Ready(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportState.Failed(friendlyError(e))
            }
        }
    }

    /** Opens ward [w] (or the home screen for null). The filters of the ward being left are kept. */
    fun setWard(w: Int?) {
        val current = _filter.value
        if (current.ward != null && current.ward >= 1) savedFilters[current.ward] = current
        _ward.value = w
        _filter.value = if (w == null) VoterFilter(ward = NO_WARD) else savedFilters[w] ?: VoterFilter(ward = w)
    }

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun setFavouritesOnly(on: Boolean) = _filter.update { it.copy(favouritesOnly = on) }
    fun setVisitedFilter(v: Boolean?) = _filter.update { it.copy(visited = v) }
    fun setBlockFilter(b: String?) = _filter.update { it.copy(block = b) }
    fun setTagFilter(id: Long?) = _filter.update { it.copy(tagId = id) }
    fun clearFilters() = _filter.update { VoterFilter(ward = it.ward, query = it.query) }

    /** Called when a tag is deleted so no ward stays filtered on a missing tag. */
    fun onTagDeleted(id: Long) {
        _filter.update { if (it.tagId == id) it.copy(tagId = null) else it }
        savedFilters.replaceAll { _, f -> if (f.tagId == id) f.copy(tagId = null) else f }
    }

    fun toggleFavourite(id: Int, favourite: Boolean) = launchSafely { repo.setFavourite(id, favourite) }
    fun toggleVisited(id: Int, visited: Boolean) = launchSafely { repo.setVisited(id, visited, recordTime = true) }
    fun setBlock(id: Int, block: String?) = launchSafely { repo.setBlock(id, block) }

    fun clearBlock(block: String) = launchSafely {
        val w = _ward.value ?: return@launchSafely
        val n = repo.clearBlock(w, block)
        app.messages.show("Block $block cleared in Ward $w ($n voter${if (n == 1) "" else "s"} now unassigned)")
    }

    fun assignRange(from: Int, to: Int, block: String?) = launchSafely {
        val w = _ward.value ?: return@launchSafely
        val n = repo.setBlockForRange(w, from, to, block)
        app.messages.show(
            if (block == null) "$n voter${if (n == 1) "" else "s"} unassigned"
            else "$n voter${if (n == 1) "" else "s"} assigned to block $block"
        )
    }

    /** Lowest..highest GP serial of the open ward (for the block range form). */
    suspend fun serialRange(): IntRange? = _ward.value?.let { repo.serialRange(it) }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.messages.error(friendlyError(e))
        }
    }

    companion object {
        /** Filter ward used on the home screen: matches no ward. */
        const val NO_WARD = -1
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = app(); DirectoryViewModel(a.repository, a) }
        }
    }
}

// ======================================================================================
// Tags management
// ======================================================================================

class TagsViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {
    val tags: StateFlow<List<TagWithCount>> = repo.tags(null)
        .catch { app.messages.error(friendlyError(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Runs [action]; on success calls [onDone]. Problems go to [onError] when given (shown inside the
     * open dialog, where a snackbar would be hidden behind it), otherwise to a snackbar.
     */
    private fun perform(onDone: () -> Unit, success: String?, onError: ((String) -> Unit)?, action: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                action()
                onDone()
                success?.let(app.messages::show)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = friendlyError(e)
                if (onError != null) onError(msg) else app.messages.error(msg)
            }
        }

    fun create(name: String, onDone: () -> Unit, onError: (String) -> Unit) =
        perform(onDone, "Tag created", onError) { repo.createTag(name) }
    fun rename(id: Long, name: String, onDone: () -> Unit, onError: (String) -> Unit) =
        perform(onDone, "Tag renamed", onError) { repo.renameTag(id, name) }
    fun delete(id: Long, onDone: () -> Unit) = perform(onDone, "Tag deleted", null) { repo.deleteTag(id) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = app(); TagsViewModel(a.repository, a) }
        }
    }
}

// ======================================================================================
// Settings: dataset info, backup / restore
// ======================================================================================

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val label: String) : BackupUiState
    /** A valid backup was read; waiting for the user to confirm replacing current data. */
    data class ConfirmRestore(val content: BackupContent) : BackupUiState
}

class SettingsViewModel(private val repo: VoterRepository, private val app: VoterDirectoryApp) : ViewModel() {

    val datasetInfo: StateFlow<List<DatasetInfoEntity>> = repo.observeDatasetInfo()
        .catch { app.messages.error(friendlyError(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _backup = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    private val _canUndo = MutableStateFlow(repo.hasUndoSnapshot())
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** Password chosen for the export that is waiting for the document picker. Kept only in memory. */
    private var pendingExportPassword: CharArray? = null

    fun prepareExport(password: CharArray) {
        pendingExportPassword?.fill(' ')
        pendingExportPassword = password
    }

    fun cancelExport() {
        pendingExportPassword?.fill(' ')
        pendingExportPassword = null
    }

    fun writeBackup(resolver: ContentResolver, uri: Uri) {
        val password = pendingExportPassword ?: return
        pendingExportPassword = null
        _backup.value = BackupUiState.Working("Creating encrypted backup…")
        viewModelScope.launch {
            try {
                val bytes = repo.createBackup(password)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw BackupException("Could not write to the chosen file.")
                }
                app.messages.show("Backup saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error(if (e is BackupException) friendlyError(e) else "Could not save the backup file.")
            } finally {
                password.fill(' ')
                _backup.value = BackupUiState.Idle
            }
        }
    }

    fun readBackup(resolver: ContentResolver, uri: Uri, password: CharArray) {
        _backup.value = BackupUiState.Working("Checking backup…")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        val buffer = input.readNBytesCompat(BackupCodec.MAX_FILE_BYTES + 1)
                        if (buffer.size > BackupCodec.MAX_FILE_BYTES) throw BackupException("The file is too large to be a backup of this app.")
                        buffer
                    } ?: throw BackupException("Could not open the chosen file.")
                }
                _backup.value = BackupUiState.ConfirmRestore(repo.readBackup(bytes, password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _backup.value = BackupUiState.Idle
                app.messages.error(if (e is BackupException) friendlyError(e) else "Could not read the chosen file.")
            } finally {
                password.fill(' ')
            }
        }
    }

    fun confirmRestore() {
        val state = _backup.value as? BackupUiState.ConfirmRestore ?: return
        _backup.value = BackupUiState.Working("Restoring…")
        viewModelScope.launch {
            try {
                repo.restore(state.content)
                _canUndo.value = repo.hasUndoSnapshot()
                app.messages.show("Backup restored")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error(friendlyError(e))
            } finally {
                _backup.value = BackupUiState.Idle
            }
        }
    }

    fun dismissRestore() {
        _backup.value = BackupUiState.Idle
    }

    fun undoLastRestore() {
        _backup.value = BackupUiState.Working("Going back…")
        viewModelScope.launch {
            try {
                repo.undoLastRestore()
                app.messages.show("Previous data brought back")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.messages.error(friendlyError(e))
            } finally {
                _canUndo.value = repo.hasUndoSnapshot()
                _backup.value = BackupUiState.Idle
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { val a = app(); SettingsViewModel(a.repository, a) }
        }
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
