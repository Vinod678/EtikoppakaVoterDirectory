package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Upgrade from the installed state (Wards 2 + 11 with user data) to the bundle with Wards 3 and 4:
 * new wards import once, existing wards (voters, user data, households) stay byte-for-byte the same.
 */
@RunWith(RobolectricTestRunner::class)
class AllWardsImportTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private var wardFiles = listOf("voters_ward_2.json")
    private var now = 1L

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun repo() = VoterRepository(
        db, ::readAsset, File(context.cacheDir, "aw-${System.nanoTime()}"), clock = { now++ },
        listAssets = { dir -> if (dir == "wards") wardFiles else emptyList() },
    )

    private fun snapshot() = Triple(
        db.voterDao().let { d -> listOf(2, 11).flatMap { w -> runBlocking { d.votersOfWard(w) } } },
        runBlocking { db.userDataDao().allUserData() to db.userDataDao().allVoterTags() },
        runBlocking { db.householdDao().allHouseholds().filter { it.wardNo in setOf(2, 11) } to db.householdDao().allMembers().filter { VoterIds.wardOf(it.voterId) in setOf(2, 11) } },
    )

    @Test
    fun wards3and4_importBesideExistingWards_preserveEverything_noDuplicatesOnRestart() = runBlocking<Unit> {
        // Installed state: Wards 2 + 11, with user data and a manual household in each.
        val before = repo()
        before.ensureImported()
        before.setFavourite(3700.v, true)
        before.setVisited(VoterIds.of(2, 562), true)
        before.setBlockForRange(2, 370, 380, "B")
        before.setNote(VoterIds.of(2, 400), "call later")
        before.createHousehold("My family", listOf(VoterIds.of(2, 371), VoterIds.of(2, 372)))
        val snap = snapshot()

        // Update installed: all four ward datasets bundled.
        wardFiles = context.assets.list("wards")!!.toList()
        assertTrue(wardFiles.containsAll(listOf("voters_ward_2.json", "voters_ward_3.json", "voters_ward_4.json")))
        val r = repo().ensureImported()
        assertTrue(r.importedNow)
        assertTrue(r.failed.isEmpty())
        val repo = repo()
        assertEquals(mapOf(2 to 369, 3 to 369, 4 to 369, 11 to 369), repo.wardVoterCounts().first().associate { it.wardNo to it.total })
        assertEquals(1476, db.voterDao().count())
        assertEquals((739..1107).map { VoterIds.of(3, it) }, db.voterDao().allIds(3))
        assertEquals((1108..1476).map { VoterIds.of(4, it) }, db.voterDao().allIds(4))

        // Existing wards unchanged: voter rows, user data, households.
        assertEquals(snap, snapshot())

        // New wards: own photos, households inside the ward only, everyone in exactly one.
        assertTrue(repo.voter(VoterIds.of(3, 739)).first()!!.photoFilename!!.startsWith("ward_3/739."))
        assertTrue(repo.voter(VoterIds.of(4, 1476)).first()!!.photoFilename!!.startsWith("ward_4/1476."))
        val hhWard = db.householdDao().allHouseholds().associate { it.id to it.wardNo }
        val members = db.householdDao().allMembers()
        assertTrue(members.all { hhWard[it.householdId] == VoterIds.wardOf(it.voterId) })
        for (w in listOf(3, 4)) {
            val ids = members.filter { VoterIds.wardOf(it.voterId) == w }.map { it.voterId }
            assertEquals(369, ids.size); assertEquals(369, ids.toSet().size)
        }

        // Search: global search spans all four wards; ward search stays in its ward.
        val lakshmi = repo.directory(VoterFilter(ward = null, query = "Lakshmi")).first().map { it.voter.wardNo }.toSet()
        assertEquals(setOf(2, 3, 4, 11), lakshmi)
        assertTrue(repo.directory(VoterFilter(ward = 3, query = "Lakshmi")).first().all { it.voter.wardNo == 3 })
        assertTrue(repo.directory(VoterFilter(ward = 4, query = "1300")).first().any { it.voter.gpSerialNo == 1300 })

        // Repeated restarts: nothing re-imported, regrouped or duplicated.
        val hh = db.householdDao().allHouseholds()
        repeat(2) { assertFalse(repo().ensureImported().importedNow) }
        assertEquals(1476, db.voterDao().count())
        assertEquals(hh, db.householdDao().allHouseholds())
        assertEquals(1476, db.householdDao().allMembers().size)
    }
}
