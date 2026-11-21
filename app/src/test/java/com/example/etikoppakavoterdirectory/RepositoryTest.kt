package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.UserInputException
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.backup.BackupException
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Room DAO queries, import, persistence and restore, using the real bundled dataset. */
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private lateinit var safetyDir: File
    private var now = 1_000L

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun newRepo(database: VoterDatabase, read: (String) -> String = ::readAsset) =
        VoterRepository(database, read, safetyDir, clock = { now++ })

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        safetyDir = File(context.cacheDir, "safety-test-${System.nanoTime()}")
        repo = newRepo(db)
        runBlocking { repo.ensureImported() }
    }

    @After
    fun tearDown() {
        db.close()
        safetyDir.deleteRecursively()
    }

    private fun serials(filter: VoterFilter) = runBlocking { repo.directory(filter).first().map { it.voter.gpSerialNo } }

    // ---------------- import ----------------

    @Test
    fun import_loads369_andIsIdempotent() = runBlocking<Unit> {
        assertEquals(369, db.voterDao().count())
        val second = repo.ensureImported()
        assertFalse(second.importedNow)
        assertEquals(369, second.voterCount)
        assertEquals(369, db.voterDao().count())
        assertEquals((3691..4059).toList(), db.voterDao().allIds(11).map { VoterIds.serialOf(it) })
        assertEquals((3691..4059).toList().v(), db.voterDao().allIds(null))
    }

    @Test
    fun reimportOfNewDatasetVersion_keepsUserData_andDoesNotDuplicate() = runBlocking<Unit> {
        repo.setFavourite(3691.v, true)
        repo.setVisited(3692.v, true)
        repo.setBlock(3693.v, "B")
        repo.setNote(3694.v, "Call again")
        val tag = repo.createTag("Needs follow-up")
        repo.setTagAttached(3695.v, tag, true)

        // Simulate an updated bundled dataset (new version stamp, same voters).
        val newer = readAsset(VoterRepository.DATA_ASSET).replaceFirst("\"generated_at\": \"", "\"generated_at\": \"NEW-")
        val result = newRepo(db) { newer }.ensureImported()
        assertTrue(result.importedNow)
        assertEquals(369, db.voterDao().count())

        assertEquals(listOf(3691), serials(VoterFilter(favouritesOnly = true)))
        assertEquals(listOf(3692), serials(VoterFilter(visited = true)))
        assertEquals(listOf(3693), serials(VoterFilter(block = "B")))
        assertEquals("Call again", repo.userData(3694.v).first()!!.note)
        assertEquals(listOf(3695), serials(VoterFilter(tagId = tag)))
    }

    @Test
    fun voterValuesStoredVerbatim_missingStayNull() = runBlocking<Unit> {
        val v = repo.voter(3717.v).first()!!
        assertNull(v.relativeNameEn)
        assertNull(v.relativeNameTe)
        assertEquals("Not specified", v.relationship)
        assertTrue(v.needsReview)
        assertEquals("3717.jpg", v.photoFilename)
        assertEquals("3827.png", repo.voter(3827.v).first()!!.photoFilename)
    }

    // ---------------- search ----------------

    @Test
    fun search_bySerialPrefix() {
        assertEquals(listOf(3691), serials(VoterFilter(query = "3691")))
        // "405" is a serial prefix for 4050-4059 and also occurs inside two EPIC numbers (3698, 3889).
        assertEquals(listOf(3698, 3889) + (4050..4059).toList(), serials(VoterFilter(query = "405")))
    }

    @Test
    fun search_byEnglishName_caseInsensitive() {
        assertTrue(3691 in serials(VoterFilter(query = "VARAHALAMMA")))
    }

    @Test
    fun search_byTeluguName_andSpaceInsensitive() {
        assertTrue(3691 in serials(VoterFilter(query = "వరహలమ్మా")))
        // Stored as "సత్య వతి మేడిశెట్టి" (stray space) - still found without the space.
        assertTrue(3752 in serials(VoterFilter(query = "సత్యవతి")))
    }

    @Test
    fun search_byRelativeName_houseNo_andEpic() {
        assertTrue(3693 in serials(VoterFilter(query = "pentayya")))
        assertTrue(serials(VoterFilter(query = "8-139")).containsAll(listOf(3691, 3692, 3693, 3694, 3695)))
        assertEquals(listOf(3691), serials(VoterFilter(query = "xoo1357615")))
    }

    @Test
    fun search_specialCharacters_doNotMatchEverything() {
        assertTrue(serials(VoterFilter(query = "%")).isEmpty())
        assertTrue(serials(VoterFilter(query = "_")).isEmpty())
        assertTrue(serials(VoterFilter(query = "zzzz")).isEmpty())
    }

    // ---------------- filters ----------------

    @Test
    fun filters_combineWithEachOtherAndWithSearch() = runBlocking<Unit> {
        repo.setFavourite(3691.v, true)
        repo.setFavourite(3692.v, true)
        repo.setVisited(3691.v, true)
        repo.setBlock(3691.v, "A")
        repo.setBlock(3700.v, "A")

        assertEquals(listOf(3691, 3692), serials(VoterFilter(favouritesOnly = true)))
        assertEquals(listOf(3691), serials(VoterFilter(favouritesOnly = true, visited = true)))
        assertEquals(listOf(3692), serials(VoterFilter(favouritesOnly = true, visited = false)))
        assertEquals(368, serials(VoterFilter(visited = false)).size)
        assertEquals(listOf(3691, 3700), serials(VoterFilter(block = "A")))
        assertEquals(367, serials(VoterFilter(block = Blocks.UNASSIGNED)).size)
        assertEquals(listOf(3700), serials(VoterFilter(block = "A", visited = false)))
        assertEquals(listOf(3691), serials(VoterFilter(query = "varaha", block = "A")))
        assertEquals(140, serials(VoterFilter(reviewOnly = true)).size)
    }

    @Test
    fun blockCounts_andStats() = runBlocking<Unit> {
        repo.setBlock(3691.v, "C"); repo.setBlock(3692.v, "C"); repo.setBlock(3693.v, "D")
        repo.setVisited(3691.v, true)
        val counts = db.voterDao().observeBlockCounts(11).first().associateBy { it.block }
        assertEquals(2, counts["C"]!!.total)
        assertEquals(1, counts["C"]!!.visited)
        assertEquals(1, counts["D"]!!.total)
        assertEquals(366, counts[Blocks.UNASSIGNED]!!.total)
        val stats = repo.stats().first()
        assertEquals(369, stats.total)
        assertEquals(1, stats.visited)
        assertEquals(140, stats.needsReview)
    }

    @Test
    fun blockRangeAssignment_andClearBlock() = runBlocking<Unit> {
        repo.setFavourite(3695.v, true) // existing row in the range must keep its other data
        assertEquals(10, repo.setBlockForRange(3691, 3700, "B"))
        assertEquals((3691..3700).toList(), serials(VoterFilter(block = "B")))
        assertTrue(repo.userData(3695.v).first()!!.favourite)
        assertEquals(3, repo.setBlockForRange(3698, 3700, "D"))
        assertEquals(7, repo.clearBlock("B"))
        assertTrue(serials(VoterFilter(block = "B")).isEmpty())
        assertEquals(listOf(3698, 3699, 3700), serials(VoterFilter(block = "D")))
        assertThrows(UserInputException::class.java) { runBlocking { repo.setBlockForRange(3700, 3691, "A") } }
    }

    // ---------------- user edits ----------------

    @Test
    fun visited_withAndWithoutTime() = runBlocking<Unit> {
        repo.setVisited(3691.v, true, recordTime = true)
        assertNotNull(repo.userData(3691.v).first()!!.visitedAt)
        repo.clearVisitTime(3691.v)
        val ud = repo.userData(3691.v).first()!!
        assertTrue(ud.visited); assertNull(ud.visitedAt)
        repo.setVisited(3691.v, false)
        assertFalse(repo.userData(3691.v).first()!!.visited)
    }

    @Test
    fun blockUnassign_andInvalidBlock() = runBlocking<Unit> {
        repo.setBlock(3691.v, "A")
        repo.setBlock(3691.v, null)
        assertNull(repo.userData(3691.v).first()!!.block)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setBlock(3691.v, "E") } }
    }

    @Test
    fun tags_createRenameDelete_andDuplicateNames() = runBlocking<Unit> {
        val id = repo.createTag("  Needs   follow-up ")
        assertEquals("Needs follow-up", repo.tags().first().single().name)
        assertThrows(UserInputException::class.java) { runBlocking { repo.createTag("needs FOLLOW-UP") } }
        assertThrows(UserInputException::class.java) { runBlocking { repo.createTag("   ") } }
        repo.setTagAttached(3691.v, id, true)
        repo.setTagAttached(3691.v, id, true) // attaching twice is harmless
        assertEquals(1, repo.tags().first().single().usage)
        repo.renameTag(id, "Follow-up")
        assertEquals("Follow-up", repo.tags().first().single().name)
        repo.deleteTag(id)
        assertTrue(repo.tags().first().isEmpty())
        assertTrue(repo.tagIdsFor(3691.v).first().isEmpty())
    }

    // ---------------- persistence ----------------

    @Test
    fun userData_survivesClosingAndReopeningDatabase() = runBlocking<Unit> {
        val name = "persist-test-${System.nanoTime()}.db"
        val first = Room.databaseBuilder(context, VoterDatabase::class.java, name).allowMainThreadQueries().build()
        val repo1 = newRepo(first)
        repo1.ensureImported()
        repo1.setFavourite(4059.v, true)
        repo1.setNote(4059.v, "Persisted note")
        repo1.setBlock(4059.v, "D")
        first.close()

        val second = Room.databaseBuilder(context, VoterDatabase::class.java, name).allowMainThreadQueries().build()
        val repo2 = newRepo(second)
        assertFalse(repo2.ensureImported().importedNow) // nothing re-imported on "next launch"
        val ud = repo2.userData(4059.v).first()!!
        assertTrue(ud.favourite)
        assertEquals("Persisted note", ud.note)
        assertEquals("D", ud.block)
        assertEquals(369, second.voterDao().count())
        second.close()
        context.deleteDatabase(name)
    }

    // ---------------- backup / restore ----------------

    @Test
    fun backup_restoreIntoFreshInstall_andUndo() = runBlocking<Unit> {
        repo.setFavourite(3691.v, true)
        repo.setVisited(3692.v, true)
        repo.setBlock(3693.v, "C")
        repo.setNote(3694.v, "Private note")
        val tag = repo.createTag("Lives elsewhere")
        repo.setTagAttached(3695.v, tag, true)
        val file = repo.createBackup("a long password".toCharArray())

        // Fresh install with some different data of its own.
        val otherDb = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        val other = newRepo(otherDb)
        other.ensureImported()
        other.setFavourite(4000.v, true)

        assertThrows(BackupException::class.java) { runBlocking { other.readBackup(file, "wrong password!".toCharArray()) } }
        assertEquals(listOf(4000), other.directory(VoterFilter(favouritesOnly = true)).first().map { it.voter.gpSerialNo })

        val content = other.readBackup(file, "a long password".toCharArray())
        assertEquals(1, content.favourites)
        other.restore(content)
        assertEquals(listOf(3691), other.directory(VoterFilter(favouritesOnly = true)).first().map { it.voter.gpSerialNo })
        assertEquals("Private note", other.userData(3694.v).first()!!.note)
        assertEquals("C", other.userData(3693.v).first()!!.block)
        assertEquals(listOf(3695), other.directory(VoterFilter(tagId = tag)).first().map { it.voter.gpSerialNo })
        assertTrue(other.hasUndoSnapshot())

        other.undoLastRestore()
        assertEquals(listOf(4000), other.directory(VoterFilter(favouritesOnly = true)).first().map { it.voter.gpSerialNo })
        assertTrue(other.tags().first().isEmpty())
        assertFalse(other.hasUndoSnapshot())
        otherDb.close()
    }
}
