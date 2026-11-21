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
 * Upgrade from the v3.3 installed state (Wards 2, 3, 4 + 11 with user data and manual households) to
 * the bundle with all 14 wards: Wards 1, 5-10, 12-14 import once, existing wards (voter rows, user data,
 * households) stay exactly the same, and restarts import nothing again.
 */
@RunWith(RobolectricTestRunner::class)
class FullRollImportTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private val old = setOf(2, 3, 4, 11)
    private var wardFiles = listOf("voters_ward_2.json", "voters_ward_3.json", "voters_ward_4.json")
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
        db, ::readAsset, File(context.cacheDir, "fr-${System.nanoTime()}"), clock = { now++ },
        listAssets = { dir -> if (dir == "wards") wardFiles else emptyList() },
    )

    private fun snapshot() = Triple(
        db.voterDao().let { d -> old.flatMap { w -> runBlocking { d.votersOfWard(w) } } },
        runBlocking { Triple(db.userDataDao().allUserData(), db.userDataDao().allVoterTags(), db.voterDao().datasetInfo(2)) },
        runBlocking { db.householdDao().allHouseholds().filter { it.wardNo in old } to db.householdDao().allMembers().filter { VoterIds.wardOf(it.voterId) in old } },
    )

    @Test
    fun allFourteenWards_importBesideExistingWards_preserveEverything_noDuplicatesOnRestart() = runBlocking<Unit> {
        val before = repo()
        before.ensureImported()
        before.setFavourite(3700.v, true)
        before.setVisited(VoterIds.of(3, 800), true)
        before.setBlockForRange(4, 1108, 1120, "B")
        before.setNote(VoterIds.of(2, 400), "call later")
        before.createHousehold("My family", listOf(VoterIds.of(4, 1200), VoterIds.of(4, 1201)))
        val snap = snapshot()
        assertEquals(1476, db.voterDao().count())

        // Update installed: every bundled ward dataset.
        wardFiles = context.assets.list("wards")!!.toList()
        assertEquals((1..14).filter { it != 11 }.map { "voters_ward_$it.json" }.toSet(), wardFiles.filter { it.endsWith(".json") }.toSet())
        val r = repo().ensureImported()
        assertTrue(r.importedNow)
        assertTrue(r.failed.isEmpty())
        val repo = repo()
        val expected = (1..14).associateWith { if (it == 14) 373 else 369 }
        assertEquals(expected, repo.wardVoterCounts().first().associate { it.wardNo to it.total })
        assertEquals(5170, db.voterDao().count())
        assertEquals((1..369).map { VoterIds.of(1, it) }, db.voterDao().allIds(1))
        assertEquals((4798..5170).map { VoterIds.of(14, it) }, db.voterDao().allIds(14))

        // Existing wards unchanged: voter rows, user data, households.
        assertEquals(snap, snapshot())

        // New wards: own photos, households inside the ward only, every voter in exactly one household.
        assertEquals("ward_1/1.jpg", repo.voter(VoterIds.of(1, 1)).first()!!.photoFilename)
        assertTrue(repo.voter(VoterIds.of(14, 5170)).first()!!.photoFilename!!.startsWith("ward_14/5170."))
        val hhWard = db.householdDao().allHouseholds().associate { it.id to it.wardNo }
        val members = db.householdDao().allMembers()
        assertTrue(members.all { hhWard[it.householdId] == VoterIds.wardOf(it.voterId) })
        for ((w, n) in expected) {
            val ids = members.filter { VoterIds.wardOf(it.voterId) == w }.map { it.voterId }
            assertEquals("ward $w", n, ids.size); assertEquals(n, ids.toSet().size)
        }

        // Search spans all wards; ward search stays in its ward; Telugu + serial search work in new wards.
        assertEquals((1..14).toSet(), repo.directory(VoterFilter(ward = null, query = "Lakshmi")).first().map { it.voter.wardNo }.toSet())
        assertTrue(repo.directory(VoterFilter(ward = 7, query = "Lakshmi")).first().all { it.voter.wardNo == 7 })
        assertTrue(repo.directory(VoterFilter(ward = 14, query = "5170")).first().any { it.voter.gpSerialNo == 5170 })
        assertTrue(repo.directory(VoterFilter(ward = 1, query = "దుర్గ")).first().any { it.voter.gpSerialNo == 1 })

        // Repeated restarts: nothing re-imported, regrouped or duplicated.
        val hh = db.householdDao().allHouseholds()
        repeat(2) { assertFalse(repo().ensureImported().importedNow) }
        assertEquals(5170, db.voterDao().count())
        assertEquals(hh, db.householdDao().allHouseholds())
        assertEquals(5170, db.householdDao().allMembers().size)
    }
}
