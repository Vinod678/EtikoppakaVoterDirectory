package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.HouseholdCoverage
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.ui.home.groupedCount
import com.example.etikoppakavoterdirectory.ui.households.coverageLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Households screen statistics: total / assigned / unassigned voters and the unassigned list (real Ward 11 data). */
@RunWith(RobolectricTestRunner::class)
class HouseholdCoverageTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private var now = 10_000L

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        repo = VoterRepository(db, ::readAsset, File(context.cacheDir, "cov-${System.nanoTime()}"), clock = { now++ })
        runBlocking { repo.ensureImported() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun coverage() = repo.householdCoverage(11).first()
    private suspend fun unassigned() = repo.unassignedVoters(11).first().map { it.gpSerialNo }

    /** Independent check straight from the membership table. */
    private suspend fun assignedFromTable() = db.householdDao().allMembers().map { it.voterId }.toSet().size

    @Test
    fun coverage_matchesMembership_throughRemoveAssignMoveAndDelete() = runBlocking<Unit> {
        // Automatic grouping: everyone in exactly one household.
        assertEquals(HouseholdCoverage(total = 369, assigned = 369), coverage())
        assertTrue(unassigned().isEmpty())

        // Remove two voters from their households -> unassigned, in serial order.
        repo.removeHouseholdMembers(listOf(3800.v, 3695.v))
        assertEquals(HouseholdCoverage(369, 367), coverage())
        assertEquals(2, coverage().unassigned)
        assertEquals(listOf(3695, 3800), unassigned())
        assertEquals(assignedFromTable(), coverage().assigned)

        // Assign one of them to an existing household -> leaves the unassigned list.
        val target = repo.householdSummaries(11).first().first().id
        repo.addHouseholdMembers(target, listOf(3695.v))
        assertEquals(HouseholdCoverage(369, 368), coverage())
        assertEquals(listOf(3800), unassigned())

        // Assign the other by creating a new household for it.
        val single = repo.createHousehold(null, listOf(3800.v))
        assertEquals(HouseholdCoverage(369, 369), coverage())
        assertTrue(unassigned().isEmpty())

        // Move the last member of that household elsewhere: household deleted, voter still counted once.
        val before = repo.householdSummaries(11).first().size
        repo.moveHouseholdMembers(single, listOf(3800.v), target)
        assertEquals(before - 1, repo.householdSummaries(11).first().size)
        assertEquals(HouseholdCoverage(369, 369), coverage())

        // Deleting a whole household makes all its members unassigned; nobody is counted twice.
        val members = repo.householdMemberSerials(target)
        repo.deleteHousehold(target)
        assertEquals(HouseholdCoverage(369, 369 - members.size), coverage())
        assertEquals(members.sorted(), unassigned())
        assertEquals(assignedFromTable(), coverage().assigned)
    }

    @Test
    fun coverage_isWardScoped_andTextFormats() = runBlocking<Unit> {
        assertEquals(HouseholdCoverage(0, 0), repo.householdCoverage(5).first()) // ward without data
        assertTrue(repo.unassignedVoters(5).first().isEmpty())
        assertEquals("111 Households · 365/369 Voters Assigned", coverageLine(111, HouseholdCoverage(369, 365)))
        assertEquals("1 Household", coverageLine(1, null))
        assertEquals("1,476", groupedCount(1476))
        assertEquals("369", groupedCount(369))
    }
}
