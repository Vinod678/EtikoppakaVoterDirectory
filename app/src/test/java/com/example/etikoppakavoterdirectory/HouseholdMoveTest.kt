package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.UserInputException
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Moving voters between existing households, including the last member (real Ward 11 dataset). */
@RunWith(RobolectricTestRunner::class)
class HouseholdMoveTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private var now = 10_000L
    private var a = 0L // Household A: 3697 only
    private var b = 0L // Household B: 3698, 3699

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun newRepo() = VoterRepository(db, ::readAsset, File(context.cacheDir, "mv-${System.nanoTime()}"), clock = { now++ })

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        repo = newRepo()
        runBlocking {
            repo.ensureImported()
            a = repo.createHousehold("Household A", listOf(3697.v))
            b = repo.createHousehold("Household B", listOf(3698.v, 3699.v))
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun householdIdOf(voter: Int) = db.householdDao().allMembers().filter { it.voterId == voter }.map { it.householdId }

    /** Everything a move must never change: voter rows, user data, tags. */
    private suspend fun voterState() = Triple(
        db.voterDao().votersOfWard(11),
        db.userDataDao().allUserData().sortedBy { it.voterId }.map { it.copy(updatedAt = 0) },
        db.userDataDao().allVoterTags().sortedBy { it.voterId },
    )

    @Test
    fun lastMember_movesToB_sourceDeleted_voterDataUntouched_stableAfterRegroupAndRestart() = runBlocking<Unit> {
        // The voter carries favourites, visit history, a note, a tag and a block - none may change.
        repo.setFavourite(3697.v, true)
        repo.setVisited(3697.v, true)
        repo.setNote(3697.v, "call in the evening")
        repo.setBlock(3697.v, "B")
        repo.setTagAttached(3697.v, repo.createTag("Move test"), true)
        val before = voterState()
        val householdsBefore = db.householdDao().allHouseholds().size

        val r = repo.moveHouseholdMembers(a, listOf(3697.v), b)

        assertEquals(VoterRepository.MoveResult(moved = 1, sourceDeleted = true), r)
        assertNull(db.householdDao().household(a))                                   // empty source deleted
        assertEquals(listOf(3697, 3698, 3699), repo.householdMemberSerials(b))       // B now has three
        assertEquals(listOf(b), householdIdOf(3697.v))                                // exactly once
        assertEquals(householdsBefore - 1, db.householdDao().allHouseholds().size)    // counts update
        assertEquals(3, repo.householdSummaries(11).first().first { it.id == b }.members)
        assertTrue(db.householdDao().allMembers().none { it.householdId == a })      // no orphaned rows
        assertEquals(before, voterState())                                            // voter data unchanged
        // Manual: the moved voter and the destination are locked, so grouping keeps them.
        assertTrue(db.householdDao().allLocks().any { it.voterId == 3697.v })
        assertTrue(db.householdDao().household(b)!!.locked)

        // Automatic grouping does not recreate household A or move the voter back.
        repo.regroupWardNow(11)
        assertNull(db.householdDao().household(a))
        assertEquals(listOf(3697, 3698, 3699), repo.householdMemberSerials(b))
        // "Restart": a new repository runs the start-up import on the same database.
        val restarted = newRepo()
        assertFalse(restarted.ensureImported().importedNow)
        assertNull(db.householdDao().household(a))
        assertEquals(listOf(b), householdIdOf(3697.v))
        assertEquals(before, voterState())
    }

    @Test
    fun oneOfSeveral_movesOnly_thatVoter_sourceKeepsTheRest() = runBlocking<Unit> {
        val c = repo.createHousehold("Household C", listOf(3700, 3701, 3702, 3703, 3704).v())
        val r = repo.moveHouseholdMembers(c, listOf(3702.v), b)
        assertEquals(VoterRepository.MoveResult(moved = 1, sourceDeleted = false), r)
        assertEquals(listOf(3700, 3701, 3703, 3704), repo.householdMemberSerials(c))
        assertEquals("Household C", db.householdDao().household(c)!!.name)
        assertEquals(listOf(3698, 3699, 3702), repo.householdMemberSerials(b))
        assertEquals(listOf(3697), repo.householdMemberSerials(a))                    // unrelated household untouched
    }

    @Test
    fun invalidRequests_changeNothing() = runBlocking<Unit> {
        val members = db.householdDao().allMembers().toSet()
        val households = db.householdDao().allHouseholds()
        // Destination missing / deleted.
        assertThrows(UserInputException::class.java) { runBlocking { repo.moveHouseholdMembers(a, listOf(3697.v), 999_999L) } }
        // Same household.
        assertThrows(UserInputException::class.java) { runBlocking { repo.moveHouseholdMembers(b, listOf(3698.v), b) } }
        // A voter who is not (or no longer) in the source, mixed with a valid one: nothing moves.
        assertThrows(UserInputException::class.java) { runBlocking { repo.moveHouseholdMembers(b, listOf(3698.v, 3697.v), a) } }
        assertEquals(members, db.householdDao().allMembers().toSet())
        assertEquals(households, db.householdDao().allHouseholds())
    }

    @Test
    fun repeatedRequest_secondOneFailsSafely() = runBlocking<Unit> {
        repo.moveHouseholdMembers(a, listOf(3697.v), b)
        val after = db.householdDao().allMembers().toSet()
        assertThrows(UserInputException::class.java) { runBlocking { repo.moveHouseholdMembers(a, listOf(3697.v), b) } }
        assertEquals(after, db.householdDao().allMembers().toSet())
        assertEquals(listOf(b), householdIdOf(3697.v))
    }

    @Test
    fun databaseFailureMidTransfer_rollsBackCompletely() = runBlocking<Unit> {
        val members = db.householdDao().allMembers().toSet()
        val households = db.householdDao().allHouseholds()
        // Simulated failure AFTER the voter was written to B: deleting the emptied source fails.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER fail_delete BEFORE DELETE ON households BEGIN SELECT RAISE(ABORT, 'simulated failure'); END"
        )
        assertThrows(Exception::class.java) { runBlocking { repo.moveHouseholdMembers(a, listOf(3697.v), b) } }
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
        assertEquals(members, db.householdDao().allMembers().toSet())   // voter still in A
        assertEquals(households, db.householdDao().allHouseholds())      // A still exists, nothing touched
        assertEquals(listOf(a), householdIdOf(3697.v))
    }

    @Test
    fun moveToNewHousehold_allMembersAllowed_emptySourceRemoved() = runBlocking<Unit> {
        val newId = repo.splitHousehold(a, listOf(3697.v), null)
        assertNull(db.householdDao().household(a))
        assertEquals(listOf(3697), repo.householdMemberSerials(newId))
        // Moving some (not all) members still keeps the source.
        val other = repo.splitHousehold(b, listOf(3699.v), null)
        assertEquals(listOf(3698), repo.householdMemberSerials(b))
        assertEquals(listOf(3699), repo.householdMemberSerials(other))
    }
}
