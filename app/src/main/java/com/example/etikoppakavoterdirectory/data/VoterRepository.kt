package com.example.etikoppakavoterdirectory.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.backup.BackupContent
import com.example.etikoppakavoterdirectory.data.backup.BackupException
import com.example.etikoppakavoterdirectory.data.dataset.VoterJsonParser
import com.example.etikoppakavoterdirectory.data.db.BlockCount
import com.example.etikoppakavoterdirectory.data.db.HouseholdCoverage
import com.example.etikoppakavoterdirectory.data.db.BlockLabelEntity
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.DatasetInfoEntity
import com.example.etikoppakavoterdirectory.data.db.DefaultTags
import com.example.etikoppakavoterdirectory.data.db.DirectoryStats
import com.example.etikoppakavoterdirectory.data.db.HouseholdEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdLockEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdMemberEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdMemberName
import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.data.db.TagEntity
import com.example.etikoppakavoterdirectory.data.db.TagWithCount
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import com.example.etikoppakavoterdirectory.data.db.VoterListItem
import com.example.etikoppakavoterdirectory.data.db.VoterTagEntity
import com.example.etikoppakavoterdirectory.data.db.VoterUserDataEntity
import com.example.etikoppakavoterdirectory.data.db.WardCount
import com.example.etikoppakavoterdirectory.data.household.HouseholdGrouper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Directory filters; all combine with each other and with the text query. */
data class VoterFilter(
    /** null = all wards (global search); otherwise the active ward. */
    val ward: Int? = null,
    val query: String = "",
    val favouritesOnly: Boolean = false,
    /** null = any, true = visited, false = not visited */
    val visited: Boolean? = null,
    /** null = any, "A".."D", or [Blocks.UNASSIGNED] */
    val block: String? = null,
    val tagId: Long? = null,
    val reviewOnly: Boolean = false,
) {
    val hasFilters get() = favouritesOnly || visited != null || block != null || tagId != null || reviewOnly
}

/** One voter prepared for export (original values plus the user's own markers). */
data class ExportRecord(
    val voter: VoterEntity,
    val favourite: Boolean,
    val visited: Boolean,
    val block: String?,
    val note: String?,
    val tags: List<String>,
)

data class ImportResult(
    val voterCount: Int,
    val importedNow: Boolean,
    val info: List<DatasetInfoEntity>,
    /** Bundled ward datasets that could not be read (file names only; never personal data). */
    val failed: List<String> = emptyList(),
)

class UserInputException(message: String) : Exception(message)

class VoterRepository(
    private val db: VoterDatabase,
    /** Reads a bundled asset as UTF-8 text. */
    private val readAsset: (String) -> String,
    /** App-private directory used for the automatic pre-restore safety copy. */
    private val safetyDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Lists a bundled asset folder (for datasets of further wards in assets/wards/). */
    private val listAssets: (String) -> List<String> = { emptyList() },
) {
    private val voterDao = db.voterDao()
    private val userDao = db.userDataDao()
    private val householdDao = db.householdDao()
    private val importMutex = Mutex()

    // ---------- dataset import ----------

    private data class Source(val asset: String, val photoFolder: String)

    /** The original Ward 11 dataset plus any `assets/wards/voters_ward_N.json` (photos in voter_photos/ward_N/). */
    private fun sources(): List<Source> {
        val extra = try { listAssets(WARD_DIR) } catch (e: Exception) { emptyList() }
            .mapNotNull { name -> WARD_FILE.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { n -> n to name } }
            .filter { (n, _) -> n in 1..VoterIds.MAX_WARD }
            .sortedBy { it.first }
            .map { (n, name) -> Source("$WARD_DIR/$name", "ward_$n") }
        return listOf(Source(DATA_ASSET, "")) + extra
    }

    /**
     * Loads the bundled datasets into Room, one ward at a time. Runs on every launch but only
     * writes a ward when its dataset version (meta.generated_at) or record count differs from
     * what is stored. Voter rows are upserted by voter id (ward + serial), so records are never
     * duplicated and other wards are never touched. The user's own data (separate tables) is never
     * modified. Afterwards each ward whose dataset changed gets its automatic households
     * (re)generated once - manual household corrections are always kept.
     */
    suspend fun ensureImported(): ImportResult = importMutex.withLock {
        withContext(Dispatchers.IO) {
            var importedNow = false
            val failed = ArrayList<String>()
            val seenWards = HashSet<Int>()
            for (src in sources()) {
                val dataset = try {
                    VoterJsonParser.parse(readAsset(src.asset), src.photoFolder)
                } catch (e: Exception) {
                    if (src.asset == DATA_ASSET) throw e
                    failed += src.asset
                    continue
                }
                val ward = dataset.wardNo
                if (!seenWards.add(ward)) { failed += src.asset; continue }
                val current = voterDao.datasetInfo(ward)
                val upToDate = current != null &&
                    current.generatedAt == dataset.generatedAt &&
                    current.recordCount == dataset.voters.size &&
                    voterDao.countInWard(ward) == dataset.voters.size
                if (!upToDate) {
                    val info = DatasetInfoEntity(
                        wardNo = ward,
                        generatedAt = dataset.generatedAt,
                        recordCount = dataset.voters.size,
                        needsReviewCount = dataset.voters.count { it.needsReview },
                        importedAt = clock(),
                        groupingKey = current?.groupingKey,
                    )
                    voterDao.importWard(ward, dataset.voters, info)
                    importedNow = true
                }
            }
            // Automatic households: generated once per dataset version and grouping-rule version.
            for (info in voterDao.allDatasetInfo()) {
                val key = groupingKey(info)
                if (info.groupingKey != key) {
                    regroupWard(info.wardNo)
                    voterDao.setGroupingKey(info.wardNo, key)
                }
            }
            ImportResult(voterDao.count(), importedNow, voterDao.allDatasetInfo(), failed)
        }
    }

    private fun groupingKey(info: DatasetInfoEntity) = "${HouseholdGrouper.VERSION}|${info.generatedAt}|${info.recordCount}"

    fun observeDatasetInfo(): Flow<List<DatasetInfoEntity>> = voterDao.observeDatasetInfo()

    // ---------- reading ----------

    fun directory(filter: VoterFilter): Flow<List<VoterListItem>> {
        val q = filter.query.trim()
        val lower = q.lowercase()
        val compact = VoterJsonParser.compact(lower)
        val words = lower.split(Regex("[\\s.,;]+")).filter { it.isNotEmpty() }.take(4)
        fun pattern(i: Int): String {
            val w = words.getOrNull(i) ?: return "%"
            if (words.size < 2) return "%"
            val e = escapeLike(w)
            return if (w.length == 1) "% $e%" else "%$e%"
        }
        return voterDao.observeDirectory(
            ward = filter.ward,
            hasQuery = if (q.isEmpty()) 0 else 1,
            serialPrefix = escapeLike(q),
            text = escapeLike(lower),
            compactText = escapeLike(compact.ifEmpty { lower }),
            // With fewer than two words the word match is disabled (it would match everything).
            t1 = if (words.size < 2) NO_MATCH else pattern(0),
            t2 = pattern(1),
            t3 = pattern(2),
            t4 = pattern(3),
            favouritesOnly = if (filter.favouritesOnly) 1 else 0,
            visited = filter.visited?.let { if (it) 1 else 0 },
            block = filter.block,
            tagId = filter.tagId,
            reviewOnly = if (filter.reviewOnly) 1 else 0,
        )
    }

    fun stats(ward: Int?): Flow<DirectoryStats> = voterDao.observeStats(ward)
    fun blockCounts(ward: Int): Flow<List<BlockCount>> = voterDao.observeBlockCounts(ward)
    fun wardVoterCounts(): Flow<List<WardCount>> = voterDao.observeWardCounts()
    fun wardHouseholdCounts(): Flow<List<WardCount>> = householdDao.observeWardCounts()
    fun voter(id: Int): Flow<VoterEntity?> = voterDao.observeVoter(id)
    fun userData(id: Int): Flow<VoterUserDataEntity?> = userDao.observeUserData(id)
    fun tagIdsFor(id: Int): Flow<List<Long>> = userDao.observeTagIdsFor(id)

    /** Tags with their usage in [ward] (null = all wards). Tag names are shared by all wards. */
    fun tags(ward: Int?): Flow<List<TagWithCount>> =
        if (ward == null) userDao.observeTagsWithCount(0, Int.MAX_VALUE)
        else VoterIds.range(ward).let { userDao.observeTagsWithCount(it.first, it.last) }

    /** Lowest and highest GP serial number of a ward, or null when the ward has no data. */
    suspend fun serialRange(ward: Int): IntRange? {
        val lo = voterDao.minSerial(ward) ?: return null
        val hi = voterDao.maxSerial(ward) ?: return null
        return lo..hi
    }

    // ---------- user edits (by voter id) ----------

    suspend fun setFavourite(id: Int, favourite: Boolean) = userDao.setFavourite(id, favourite, clock())

    /** @param recordTime when true the current time is stored as the visit time. */
    suspend fun setVisited(id: Int, visited: Boolean, recordTime: Boolean = true) {
        val now = clock()
        userDao.setVisited(id, visited, if (visited && recordTime) now else null, now)
    }

    /** Current visit flag and time (for undo). No row yet = not visited. */
    suspend fun visitState(id: Int): Pair<Boolean, Long?> = userDao.userData(id)?.let { it.visited to it.visitedAt } ?: (false to null)

    /** Puts back an earlier visit state exactly (flag and original time) - used by "Undo". */
    suspend fun restoreVisit(id: Int, visited: Boolean, visitedAt: Long?) {
        userDao.setVisited(id, visited, visitedAt, clock())
    }

    suspend fun clearVisitTime(id: Int) {
        val current = userDao.userData(id) ?: return
        if (current.visited) userDao.setVisited(id, true, null, clock())
    }

    suspend fun setBlock(id: Int, block: String?) {
        require(Blocks.isValid(block)) { "Invalid block" }
        userDao.setBlock(id, block, clock())
    }

    /** Removes block [block] from every voter of [ward] that has it. @return number of voters changed. */
    suspend fun clearBlock(ward: Int, block: String): Int {
        require(block in Blocks.ALL) { "Invalid block" }
        val r = VoterIds.range(ward)
        return userDao.clearBlock(block, r.first, r.last, clock())
    }

    /**
     * Assigns [block] (null = unassign) to all voters of [ward] with GP serial numbers from [from]
     * to [to]. Only that ward is affected. @return number of voters changed.
     */
    suspend fun setBlockForRange(ward: Int, from: Int, to: Int, block: String?): Int {
        require(Blocks.isValid(block)) { "Invalid block" }
        if (from > to) throw UserInputException("The first serial number must not be larger than the last.")
        if (from < 1 || to >= VoterIds.FACTOR) throw UserInputException("Serial numbers are out of range.")
        return userDao.setBlockForRange(VoterIds.of(ward, from), VoterIds.of(ward, to), block, clock())
    }

    // ---------- block labels (optional, per ward) ----------

    /** block ("A".."D") -> label for [ward]. */
    fun blockLabels(ward: Int): Flow<Map<String, String>> =
        userDao.observeBlockLabels(ward).map { list -> list.associate { it.block to it.label } }

    suspend fun blockLabelMap(ward: Int): Map<String, String> = userDao.blockLabels(ward).associate { it.block to it.label }

    /** Sets, changes or (blank [label]) clears the label of a block. Block assignments are not touched. */
    suspend fun setBlockLabel(ward: Int, block: String, label: String?) {
        require(block in Blocks.ALL) { "Invalid block" }
        val clean = label?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (clean.length > BackupCodec.MAX_BLOCK_LABEL_LENGTH) {
            throw UserInputException("Label is too long (maximum ${BackupCodec.MAX_BLOCK_LABEL_LENGTH} characters).")
        }
        if (clean.isEmpty()) userDao.deleteBlockLabel(ward, block)
        else userDao.putBlockLabel(BlockLabelEntity(ward, block, clean, clock()))
    }

    suspend fun setNote(id: Int, note: String) {
        if (note.length > BackupCodec.MAX_NOTE_LENGTH) {
            throw UserInputException("Note is too long (maximum ${BackupCodec.MAX_NOTE_LENGTH} characters).")
        }
        userDao.setNote(id, note.ifEmpty { null }, clock())
    }

    // ---------- bulk updates (selected voters) ----------

    suspend fun setFavourites(ids: Collection<Int>, favourite: Boolean): Int =
        if (ids.isEmpty()) 0 else ids.distinct().chunked(500).sumOf { userDao.setFavourites(it, favourite, clock()) }

    /** Existing visit times are kept when marking visited; only voters not yet visited get "now". */
    suspend fun setVisitedMany(ids: Collection<Int>, visited: Boolean): Int =
        if (ids.isEmpty()) 0 else ids.distinct().chunked(500).sumOf { userDao.setVisitedMany(it, visited, clock()) }

    suspend fun setBlocks(ids: Collection<Int>, block: String?): Int {
        require(Blocks.isValid(block)) { "Invalid block" }
        return if (ids.isEmpty()) 0 else ids.distinct().chunked(500).sumOf { userDao.setBlocks(it, block, clock()) }
    }

    suspend fun setTagForMany(ids: Collection<Int>, tagId: Long, attached: Boolean) {
        if (ids.isEmpty()) return
        ids.distinct().chunked(500).forEach { chunk ->
            if (attached) userDao.attachTags(chunk.map { VoterTagEntity(it, tagId) }) else userDao.detachTagFrom(chunk, tagId)
        }
    }

    // ---------- tags ----------

    /** Adds any missing default neutral tags (never duplicates). @return number added. */
    suspend fun addDefaultTags(): Int {
        val now = clock()
        return db.withTransaction { DefaultTags.NAMES.count { userDao.insertTagIfMissing(it, now) > 0 } }
    }

    suspend fun createTag(name: String): Long {
        val clean = validateTagName(name)
        return try {
            userDao.insertTag(TagEntity(name = clean, createdAt = clock()))
        } catch (e: SQLiteConstraintException) {
            throw UserInputException("A tag named \"$clean\" already exists.")
        }
    }

    suspend fun renameTag(id: Long, name: String) {
        val clean = validateTagName(name)
        try {
            userDao.renameTag(id, clean)
        } catch (e: SQLiteConstraintException) {
            throw UserInputException("A tag named \"$clean\" already exists.")
        }
    }

    suspend fun deleteTag(id: Long) = userDao.deleteTag(id)

    suspend fun setTagAttached(voterId: Int, tagId: Long, attached: Boolean) {
        if (attached) userDao.attachTag(VoterTagEntity(voterId, tagId)) else userDao.detachTag(voterId, tagId)
    }

    private fun validateTagName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserInputException("Tag name cannot be empty.")
        if (clean.length > BackupCodec.MAX_TAG_NAME_LENGTH) {
            throw UserInputException("Tag name is too long (maximum ${BackupCodec.MAX_TAG_NAME_LENGTH} characters).")
        }
        return clean
    }

    // ---------- households (always within one ward) ----------

    fun householdSummaries(ward: Int): Flow<List<HouseholdSummary>> = householdDao.observeSummaries(ward)
    fun householdMemberNames(ward: Int): Flow<List<HouseholdMemberName>> = householdDao.observeMemberNames(ward)
    fun household(id: Long): Flow<HouseholdEntity?> = householdDao.observeHousehold(id)
    fun householdMembers(id: Long): Flow<List<VoterListItem>> = householdDao.observeMembers(id)
    fun householdOf(voterId: Int): Flow<HouseholdEntity?> = householdDao.observeHouseholdOf(voterId)
    suspend fun householdMemberIds(id: Long): List<Int> = householdDao.memberIds(id)
    fun householdMemberships(ward: Int): Flow<List<HouseholdMemberEntity>> = householdDao.observeMembersOfWard(ward)
    /** Total voters of the ward vs. distinct voters assigned to one of its households (live). */
    fun householdCoverage(ward: Int): Flow<HouseholdCoverage> = householdDao.observeCoverage(ward)
    /** Voters of the ward that are in no household (live), in serial order. */
    fun unassignedVoters(ward: Int): Flow<List<VoterEntity>> = householdDao.observeUnassigned(ward)
    fun wardVoters(ward: Int): Flow<List<VoterEntity>> = voterDao.observeVotersOfWard(ward)

    /** voter id -> household id for the given voters (only those already in a household). */
    suspend fun membershipsOf(ids: Collection<Int>): Map<Int, Long> =
        if (ids.isEmpty()) emptyMap()
        else ids.distinct().chunked(500).flatMap { householdDao.membershipsOf(it) }.associate { it.voterId to it.householdId }

    /**
     * Re-generates the automatic households of [ward]. Voters whose household the user has set
     * (locked) and households the user created or edited are never changed. Deterministic: the same
     * data always gives the same households; an automatic household keeps its id when its key
     * (smallest member) is unchanged.
     */
    suspend fun regroupWard(ward: Int) = withContext(Dispatchers.IO) {
        db.withTransaction { regroupInTransaction(ward) }
    }

    private suspend fun regroupInTransaction(ward: Int) {
            val voters = voterDao.votersOfWard(ward)
            val range = VoterIds.range(ward)
            val locked = householdDao.lockedIdsBetween(range.first, range.last).toHashSet()
            val households = householdDao.householdsOfWard(ward)
            val lockedHouseholds = households.filter { it.locked }.mapTo(HashSet()) { it.id }
            val members = householdDao.membersOfWard(ward)
            members.forEach { if (it.householdId in lockedHouseholds) locked += it.voterId }
            val free = voters.filter { it.voterId !in locked }
            val groups = HouseholdGrouper.group(free)

            val autoByKey = households.filter { !it.locked && it.autoKey != null }.associateBy { it.autoKey!! }
            free.map { it.voterId }.chunked(500).forEach { householdDao.removeMembers(it) }
            val now = clock()
            val reused = HashSet<Long>()
            for (g in groups) {
                val existing = autoByKey[g.key]
                val id = if (existing != null) {
                    householdDao.updateAuto(existing.id, g.title, g.houseNo, g.needsReview, g.reviewNote, now)
                    existing.id
                } else {
                    householdDao.insertHousehold(
                        HouseholdEntity(
                            wardNo = ward, name = g.title, houseNo = g.houseNo, createdAt = now, updatedAt = now,
                            autoKey = g.key, locked = false, needsReview = g.needsReview, reviewNote = g.reviewNote,
                        )
                    )
                }
                reused += id
                g.members.chunked(500).forEach { chunk -> householdDao.putMembers(chunk.map { HouseholdMemberEntity(it, id) }) }
            }
            households.filter { !it.locked && it.id !in reused }.map { it.id }.chunked(500).forEach { householdDao.deleteHouseholds(it) }
            householdDao.deleteEmptyHouseholds()
    }

    /** Re-runs automatic grouping on request (Households menu). Manual corrections are kept. */
    suspend fun regroupWardNow(ward: Int) {
        regroupWard(ward)
        voterDao.datasetInfo(ward)?.let { voterDao.setGroupingKey(ward, groupingKey(it)) }
    }

    /** Marks voters' household assignment as set by the user and their households as user-edited. */
    private suspend fun lock(voterIds: Collection<Int>, householdIds: Collection<Long>) {
        val now = clock()
        val all = HashSet(voterIds)
        householdIds.distinct().forEach { all += householdDao.memberIds(it) }
        all.chunked(500).forEach { chunk -> householdDao.insertLocks(chunk.map { HouseholdLockEntity(it, now) }) }
        householdIds.distinct().chunked(500).forEach { householdDao.lockHouseholds(it, now) }
    }

    private fun singleWard(voters: List<VoterEntity>): Int {
        val wards = voters.map { it.wardNo }.toSet()
        if (wards.size != 1) throw UserInputException("A household can only contain voters of one ward.")
        return wards.single()
    }

    private fun mostCommonHouse(voters: List<VoterEntity>): String? =
        voters.mapNotNull { it.houseNo?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * Creates a user household with [ids] as members (voters already in another household are moved
     * to the new one). A blank [name] gives "<Surname> Family" when the members share one, otherwise
     * "Household — House <no.>".
     */
    suspend fun createHousehold(name: String?, ids: Collection<Int>): Long {
        val members = ids.distinct()
        if (members.isEmpty()) throw UserInputException("Choose at least one voter for the household.")
        val clean = name?.takeIf { it.isNotBlank() }?.let(::validateHouseholdName)
        return db.withTransaction {
            val voters = members.chunked(500).flatMap { voterDao.votersIn(it) }
            if (voters.size != members.size) throw UserInputException("Some selected voters are not in this directory.")
            val ward = singleWard(voters)
            val houseNo = mostCommonHouse(voters)
            val now = clock()
            val auto = HouseholdGrouper.group(voters).singleOrNull()
            val finalName = clean ?: (auto?.familyName?.let { HouseholdGrouper.title(it, houseNo) } ?: HouseholdGrouper.title(null, houseNo))
            val id = householdDao.insertHousehold(
                HouseholdEntity(wardNo = ward, name = finalName, houseNo = houseNo, createdAt = now, updatedAt = now, locked = true)
            )
            members.chunked(500).forEach { c -> householdDao.putMembers(c.map { HouseholdMemberEntity(it, id) }) }
            householdDao.deleteEmptyHouseholds()
            // Only the new household and the moved voters are locked; the households they came from
            // stay automatic (they simply lose those members).
            lock(members, listOf(id))
            id
        }
    }

    suspend fun renameHousehold(id: Long, name: String) {
        val clean = validateHouseholdName(name)
        db.withTransaction {
            householdDao.rename(id, clean, clock())
            lock(emptyList(), listOf(id))
        }
    }

    /** "Mark as reviewed": the automatic household is confirmed as correct and becomes the user's. */
    suspend fun markHouseholdReviewed(id: Long) = db.withTransaction {
        if (householdDao.household(id) == null) throw UserInputException("This household no longer exists.")
        lock(emptyList(), listOf(id))
    }

    /** Adds voters to household [id]; voters in another household are moved here. */
    suspend fun addHouseholdMembers(id: Long, ids: Collection<Int>) {
        val members = ids.distinct()
        if (members.isEmpty()) return
        db.withTransaction {
            val h = householdDao.household(id) ?: throw UserInputException("This household no longer exists.")
            val voters = members.chunked(500).flatMap { voterDao.votersIn(it) }
            if (voters.size != members.size) throw UserInputException("Some selected voters are not in this directory.")
            if (voters.any { it.wardNo != h.wardNo }) throw UserInputException("Only voters of Ward ${h.wardNo} can join this household.")
            members.chunked(500).forEach { c -> householdDao.putMembers(c.map { HouseholdMemberEntity(it, id) }) }
            householdDao.touch(listOf(id), clock())
            householdDao.deleteEmptyHouseholds()
            lock(members, listOf(id))
            refreshHouseNo(id)
        }
    }

    /**
     * Removes voters from their household. They stay in no household (a manual correction that
     * automatic grouping keeps). The household they left counts as checked by the user. A household
     * left without members is deleted.
     */
    suspend fun removeHouseholdMembers(ids: Collection<Int>): Int = db.withTransaction {
        if (ids.isEmpty()) return@withTransaction 0
        val sources = membershipsOf(ids).values.toSet()
        val n = ids.distinct().chunked(500).sumOf { householdDao.removeMembers(it) }
        householdDao.deleteEmptyHouseholds()
        lock(ids, sources.filter { householdDao.household(it) != null })
        n
    }

    /** Moves every member of [others] into [targetId] and deletes the emptied households. */
    suspend fun mergeHouseholds(targetId: Long, others: Collection<Long>) {
        val sources = others.filter { it != targetId }.distinct()
        if (sources.isEmpty()) return
        db.withTransaction {
            val target = householdDao.household(targetId) ?: throw UserInputException("This household no longer exists.")
            if (householdDao.householdsIn(sources).any { it.wardNo != target.wardNo }) {
                throw UserInputException("Households of different wards cannot be merged.")
            }
            householdDao.moveAllMembers(sources, targetId)
            householdDao.touch(listOf(targetId), clock())
            householdDao.deleteEmptyHouseholds()
            lock(emptyList(), listOf(targetId))
            refreshHouseNo(targetId)
        }
    }

    /**
     * Moves [ids] out of household [id] into a new household. Moving every member is allowed: the
     * emptied household is then deleted (inside the same transaction as the move).
     */
    suspend fun splitHousehold(id: Long, ids: Collection<Int>, newName: String?): Long {
        val current = householdDao.memberIds(id).toSet()
        val moving = ids.distinct().filter { it in current }
        if (moving.isEmpty()) throw UserInputException("Choose the members to move to a new household.")
        return createHousehold(newName, moving)
    }

    /** Result of [moveHouseholdMembers]. */
    data class MoveResult(val moved: Int, val sourceDeleted: Boolean)

    /**
     * Moves voters [ids] from household [sourceId] into the existing household [targetId], in one
     * transaction (all or nothing):
     *  1. both households must exist, be different and belong to the same ward, and the voters must
     *     still be members of the source (a repeated request for an already-moved voter fails safely);
     *  2. the voters are moved (membership is one row per voter, so they can never be in two households);
     *  3. only after the move is confirmed, a source left without members is deleted; otherwise it is
     *     kept with its remaining members.
     * Like "Add members", the moved voters and the destination become manual (locked), so automatic
     * grouping never moves them back or recreates the deleted household. Voter records, favourites,
     * visits, notes, tags and blocks are separate tables and are not touched.
     */
    suspend fun moveHouseholdMembers(sourceId: Long, ids: Collection<Int>, targetId: Long): MoveResult {
        val requested = ids.distinct()
        if (requested.isEmpty()) throw UserInputException("Choose the members to move.")
        if (sourceId == targetId) throw UserInputException("The voter is already in this household.")
        return db.withTransaction {
            val source = householdDao.household(sourceId) ?: throw UserInputException("The original household no longer exists.")
            val target = householdDao.household(targetId)
                ?: throw UserInputException("The destination household no longer exists. Nothing was moved.")
            if (source.wardNo != target.wardNo) throw UserInputException("Voters can only move to a household of Ward ${source.wardNo}.")
            val inSource = householdDao.memberIds(sourceId).toSet()
            val moving = requested.filter { it in inSource }
            if (moving.size != requested.size) throw UserInputException("Some of these voters are no longer in this household. Nothing was moved.")
            val voters = moving.chunked(500).flatMap { voterDao.votersIn(it) }
            if (voters.size != moving.size || voters.any { it.wardNo != target.wardNo }) {
                throw UserInputException("Only voters of Ward ${target.wardNo} can join this household.")
            }
            moving.chunked(500).forEach { c -> householdDao.putMembers(c.map { HouseholdMemberEntity(it, targetId) }) }
            // Confirm the move before touching the source; any failure rolls everything back.
            val inTarget = householdDao.memberIds(targetId).toSet()
            check(inTarget.containsAll(moving)) { "Move could not be confirmed" }
            val now = clock()
            val sourceDeleted = householdDao.memberIds(sourceId).isEmpty()
            if (sourceDeleted) {
                householdDao.deleteHousehold(sourceId)
            } else {
                householdDao.touch(listOf(sourceId), now)
                refreshHouseNo(sourceId)
            }
            householdDao.touch(listOf(targetId), now)
            lock(moving, listOf(targetId))
            refreshHouseNo(targetId)
            MoveResult(moving.size, sourceDeleted)
        }
    }

    /** Removes the grouping; the members stay in no household (kept that way by automatic grouping). */
    suspend fun deleteHousehold(id: Long) = db.withTransaction {
        val members = householdDao.memberIds(id)
        householdDao.deleteHousehold(id)
        val now = clock()
        members.chunked(500).forEach { c -> householdDao.insertLocks(c.map { HouseholdLockEntity(it, now) }) }
    }

    private suspend fun refreshHouseNo(id: Long) {
        val voters = householdDao.memberIds(id).chunked(500).flatMap { voterDao.votersIn(it) }
        householdDao.setHouseNo(id, mostCommonHouse(voters))
    }

    private fun validateHouseholdName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserInputException("Household name cannot be empty.")
        if (clean.length > BackupCodec.MAX_HOUSEHOLD_NAME_LENGTH) {
            throw UserInputException("Household name is too long (maximum ${BackupCodec.MAX_HOUSEHOLD_NAME_LENGTH} characters).")
        }
        return clean
    }

    // ---------- export ----------

    /** All voter ids of [ward] (null = every imported ward), in ward and serial order. */
    suspend fun allIds(ward: Int?): List<Int> = voterDao.allIds(ward)

    suspend fun idsInBlock(ward: Int, block: String): List<Int> = voterDao.idsInBlock(ward, block)

    suspend fun houseNumbersOf(ids: Collection<Int>): List<String?> =
        ids.distinct().chunked(500).flatMap { voterDao.votersIn(it) }.map { it.houseNo }

    suspend fun householdsIn(ids: Collection<Long>): List<HouseholdEntity> =
        ids.distinct().chunked(500).flatMap { householdDao.householdsIn(it) }

    /** Voter records plus markers for [ids], in the given order. */
    suspend fun exportRecords(ids: List<Int>): List<ExportRecord> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        db.withTransaction {
            val distinct = ids.distinct()
            val voters = distinct.chunked(500).flatMap { voterDao.votersIn(it) }.associateBy { it.voterId }
            val user = distinct.chunked(500).flatMap { userDao.userDataIn(it) }.associateBy { it.voterId }
            val tags = distinct.chunked(500).flatMap { voterDao.tagNamesFor(it) }.groupBy({ it.voterId }, { it.name })
            ids.mapNotNull { s ->
                val v = voters[s] ?: return@mapNotNull null
                val u = user[s]
                ExportRecord(v, u?.favourite == true, u?.visited == true, u?.block, u?.note, tags[s].orEmpty())
            }
        }
    }

    // ---------- backup / restore ----------

    private suspend fun snapshot(): BackupContent = db.withTransaction {
        BackupContent(
            createdAt = clock(),
            datasetGeneratedAt = voterDao.allDatasetInfo().joinToString(";") { "${it.wardNo}=${it.generatedAt}" }.ifEmpty { null },
            userData = userDao.allUserData(),
            tags = userDao.allTags(),
            voterTags = userDao.allVoterTags(),
            households = householdDao.allHouseholds(),
            householdMembers = householdDao.allMembers(),
            householdLocks = householdDao.allLocks(),
            blockLabels = userDao.allBlockLabels(),
        )
    }

    /**
     * Replaces user data; households only when the backup contains them (app 2.x and later). A
     * backup from before automatic grouping (no locks) restores its households as the user's own.
     */
    private suspend fun replaceAll(content: BackupContent) = db.withTransaction {
        userDao.replaceAll(content.userData, content.tags, content.voterTags)
        content.blockLabels?.let { userDao.replaceBlockLabels(it) }
        content.households?.let { hh ->
            val locks = content.householdLocks
                ?: content.householdMembers.map { HouseholdLockEntity(it.voterId, clock()) }
            householdDao.replaceAll(hh, content.householdMembers, locks)
            // Voters without a protected assignment get automatic households again.
            voterDao.allDatasetInfo().forEach { regroupInTransaction(it.wardNo) }
        }
    }

    /** Builds the encrypted backup bytes for the document picker to write. */
    suspend fun createBackup(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        BackupCodec.encrypt(BackupCodec.toJson(snapshot()), password)
    }

    /** Decrypts and validates a backup without changing anything. */
    suspend fun readBackup(file: ByteArray, password: CharArray): BackupContent = withContext(Dispatchers.Default) {
        val json = BackupCodec.decrypt(file, password)
        BackupCodec.fromJson(json, voterDao.allIds(null).toSet())
    }

    /**
     * Replaces all user data with [content]. A copy of the current data is saved first in
     * app-private storage so the restore can be undone with [undoLastRestore].
     */
    suspend fun restore(content: BackupContent) = withContext(Dispatchers.IO) {
        val before = BackupCodec.toJson(snapshot())
        safetyDir.mkdirs()
        val tmp = File(safetyDir, "$SAFETY_FILE.tmp")
        tmp.writeText(before, Charsets.UTF_8)
        if (!tmp.renameTo(safetyFile())) {
            safetyFile().delete()
            if (!tmp.renameTo(safetyFile())) throw BackupException("Could not save a safety copy; nothing was restored.")
        }
        replaceAll(content)
    }

    fun hasUndoSnapshot(): Boolean = safetyFile().isFile

    suspend fun undoLastRestore() = withContext(Dispatchers.IO) {
        val file = safetyFile()
        if (!file.isFile) throw BackupException("There is no earlier state to go back to.")
        val content = BackupCodec.fromJson(file.readText(Charsets.UTF_8), voterDao.allIds(null).toSet())
        replaceAll(content)
        file.delete()
    }

    private fun safetyFile() = File(safetyDir, SAFETY_FILE)

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val DATA_ASSET = "voters_data.json"
        const val PHOTO_ASSET_DIR = "voter_photos"
        const val WARD_DIR = "wards"
        private val WARD_FILE = Regex("voters_ward_(\\d{1,2})\\.json")
        private const val SAFETY_FILE = "before_last_restore.json"
        /** A LIKE pattern no search text can match (search text never contains this control character). */
        private const val NO_MATCH = "\u0001"
    }
}
