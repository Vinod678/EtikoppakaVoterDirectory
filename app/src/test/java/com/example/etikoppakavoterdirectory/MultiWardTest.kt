package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import com.example.etikoppakavoterdirectory.export.PdfOptions
import com.example.etikoppakavoterdirectory.ui.ExportScope
import com.example.etikoppakavoterdirectory.ui.ExportViewModel
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
 * Ward isolation with a second ward whose GP serial numbers and house numbers are IDENTICAL to
 * Ward 11 (a copy of the dataset relabelled as Ward 12, used only in this test). Proves that
 * nothing assumes serial or house numbers are unique across wards.
 */
@RunWith(RobolectricTestRunner::class)
class MultiWardTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private var now = 1L

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val ward12Json by lazy {
        readAsset(VoterRepository.DATA_ASSET).replace("\"ward_no\": 11", "\"ward_no\": 12")
    }
    private fun w12(s: Int) = VoterIds.of(12, s)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        repo = VoterRepository(
            db,
            readAsset = { name -> if (name == "wards/voters_ward_12.json") ward12Json else readAsset(name) },
            safetyDir = File(context.cacheDir, "mw-${System.nanoTime()}"),
            clock = { now++ },
            listAssets = { dir -> if (dir == "wards") listOf("voters_ward_12.json", "README.txt") else emptyList() },
        )
        runBlocking { repo.ensureImported() }
    }

    @After
    fun tearDown() = db.close()

    private fun ids(f: VoterFilter) = runBlocking { repo.directory(f).first().map { it.voter.voterId } }

    @Test
    fun bothWardsImported_sameSerials_distinctVoters_photosInOwnFolder() = runBlocking<Unit> {
        assertEquals(738, db.voterDao().count())
        assertEquals(369, db.voterDao().countInWard(11))
        assertEquals(369, db.voterDao().countInWard(12))
        assertEquals("3691.jpg", repo.voter(3691.v).first()!!.photoFilename)
        assertEquals("ward_12/3691.jpg", repo.voter(w12(3691)).first()!!.photoFilename)
        val counts = repo.wardVoterCounts().first().associate { it.wardNo to it.total }
        assertEquals(mapOf(11 to 369, 12 to 369), counts)
        // Re-import is per ward and idempotent.
        assertFalse(repo.ensureImported().importedNow)
    }

    @Test
    fun favouritesVisitsNotesTags_areWardSpecific() = runBlocking<Unit> {
        repo.setFavourite(3691.v, true)
        repo.setVisited(w12(3692), true)
        repo.setNote(w12(3691), "ward 12 note")
        val tag = repo.createTag("Only in 12")
        repo.setTagAttached(w12(3700), tag, true)

        assertEquals(listOf(3691.v), ids(VoterFilter(ward = 11, favouritesOnly = true)))
        assertTrue(ids(VoterFilter(ward = 12, favouritesOnly = true)).isEmpty())
        assertEquals(listOf(w12(3692)), ids(VoterFilter(ward = 12, visited = true)))
        assertTrue(ids(VoterFilter(ward = 11, visited = true)).isEmpty())
        assertEquals(null, repo.userData(3691.v).first()!!.note)
        assertEquals("ward 12 note", repo.userData(w12(3691)).first()!!.note)
        assertEquals(0, repo.tags(11).first().single { it.id == tag }.usage)
        assertEquals(1, repo.tags(12).first().single { it.id == tag }.usage)
        assertEquals(1, repo.stats(11).first().favourites)
        assertEquals(0, repo.stats(12).first().favourites)
    }

    @Test
    fun blockRangeAndClear_onlyAffectTheirWard() = runBlocking<Unit> {
        assertEquals(10, repo.setBlockForRange(12, 3691, 3700, "B"))
        assertTrue(ids(VoterFilter(ward = 11, block = "B")).isEmpty())
        assertEquals((3691..3700).map(::w12), ids(VoterFilter(ward = 12, block = "B")))
        repo.setBlock(3691.v, "B")
        assertEquals(10, repo.clearBlock(12, "B"))
        assertEquals(listOf(3691.v), ids(VoterFilter(ward = 11, block = "B")))
        assertEquals(369, repo.blockCounts(12).first().single { it.block == Blocks.UNASSIGNED }.total)
    }

    @Test
    fun households_areGeneratedPerWard_andNeverMixWards() = runBlocking<Unit> {
        val h11 = repo.householdSummaries(11).first()
        val h12 = repo.householdSummaries(12).first()
        assertEquals(h11.size, h12.size) // identical data -> identical grouping, but separate households
        assertTrue(h11.map { it.id }.intersect(h12.map { it.id }.toSet()).isEmpty())
        db.householdDao().allHouseholds().forEach { h ->
            assertTrue(repo.householdMemberIds(h.id).all { VoterIds.wardOf(it) == h.wardNo })
        }
        // A manual edit in Ward 11 does not touch Ward 12.
        val g11 = repo.membershipsOf(listOf(3703.v)).values.single()
        repo.removeHouseholdMembers(listOf(3706.v))
        repo.regroupWardNow(12)
        repo.householdMemberSerials(g11).let { assertTrue(it.containsAll(listOf(3703, 3704, 3705)) && 3706 !in it) }
        val g12 = repo.membershipsOf(listOf(w12(3703))).values.single()
        assertTrue(repo.householdMemberIds(g12).containsAll((3703..3706).map(::w12)))
        // Households cannot take voters of another ward.
        assertTrue(runCatching { repo.addHouseholdMembers(g11, listOf(w12(3710))) }.isFailure)
        assertTrue(runCatching { repo.createHousehold(null, listOf(3691.v, w12(3691))) }.isFailure)
    }

    @Test
    fun globalSearch_findsAllWards_byNameSurnameFirstInitialsAndIds() = runBlocking<Unit> {
        val byName = repo.directory(VoterFilter(ward = null, query = "Saibabu Challa")).first()
        assertEquals(setOf(11, 12), byName.map { it.voter.wardNo }.toSet())
        // Surname first and Telugu
        assertEquals(setOf(3700.v, w12(3700)), repo.directory(VoterFilter(query = "challa saibabu")).first().filter { it.voter.gpSerialNo == 3700 }.map { it.voter.voterId }.toSet())
        assertTrue(repo.directory(VoterFilter(query = "సాయిబాబు")).first().map { it.voter.voterId }.containsAll(listOf(3700.v, w12(3700))))
        // Initial + given name
        assertTrue(repo.directory(VoterFilter(query = "c saibabu")).first().any { it.voter.gpSerialNo == 3700 })
        // Serial, house number, EPIC, relative name
        assertTrue(repo.directory(VoterFilter(query = "3691")).first().map { it.voter.voterId }.containsAll(listOf(3691.v, w12(3691))))
        assertEquals(10, repo.directory(VoterFilter(query = "8-139")).first().size)
        assertEquals(2, repo.directory(VoterFilter(query = "XOO1357615")).first().size)
        assertTrue(repo.directory(VoterFilter(query = "pentayya")).first().size >= 2)
        // Ward-scoped search stays in its ward.
        assertEquals(setOf(12), repo.directory(VoterFilter(ward = 12, query = "challa")).first().map { it.voter.wardNo }.toSet())
    }

    @Test
    fun exportPlan_blocksThenHouseholds_noDuplicates_uniqueCount() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<VoterDirectoryApp>()
        val vm = ExportViewModel(repo, app)
        repo.setBlockForRange(11, 3691, 3705, "A") // 15 voters
        repo.setBlockForRange(11, 3706, 3712, "B") // 7 voters
        val gutti = repo.membershipsOf(listOf(3703.v)).values.single()          // 3703-3706: 3 in A, 1 in B
        val other = repo.membershipsOf(listOf(3760.v)).values.single()          // not in a selected block
        val plan = vm.plan(ExportScope.Selection(11, listOf("B", "A"), listOf(gutti, other)), PdfOptions())

        assertEquals("Block A — 15 Voters", plan.sections[0].title)          // A before B
        assertEquals("Block B — 7 Voters", plan.sections[1].title)
        assertTrue(plan.sections[0].startNewPage && plan.sections[1].startNewPage)
        val gSection = plan.sections.first { s -> s.alreadyListed.isNotEmpty() }
        val inBlocks = (3691..3712).map { it.v }.toSet()
        assertTrue(gSection.ids.none { it in inBlocks })                         // block members are not printed again
        assertTrue(gSection.alreadyListed.map { it.first }.containsAll(listOf(3703.v, 3704.v, 3705.v, 3706.v)))
        assertEquals("Block B", gSection.alreadyListed.single { it.first == 3706.v }.second)
        val expected = inBlocks + repo.householdMemberIds(gutti) + repo.householdMemberIds(other)
        assertEquals(expected.size, plan.uniqueIds.size)
        assertEquals(plan.uniqueIds.size, plan.uniqueIds.toSet().size)

        // Global multi-ward export only when chosen explicitly.
        assertEquals(738, vm.plan(ExportScope.All(null)).uniqueIds.size)
        assertEquals(369, vm.plan(ExportScope.All(12)).uniqueIds.size)
    }

    @Test
    fun blockLabels_perWard_inPdfHeadings_andBackup() = runBlocking<Unit> {
        repo.setBlockLabel(11, "A", "  Main   Street ")
        repo.setBlockLabel(12, "A", "Temple Area")
        assertEquals(mapOf("A" to "Main Street"), repo.blockLabels(11).first())
        assertEquals(mapOf("A" to "Temple Area"), repo.blockLabels(12).first())
        repo.setBlockForRange(11, 3691, 3695, "A")
        val vm = ExportViewModel(repo, ApplicationProvider.getApplicationContext<VoterDirectoryApp>())
        assertEquals("Block A — Main Street — 5 Voters", vm.plan(ExportScope.Selection(11, listOf("A"), emptyList())).sections[0].title)

        val file = repo.createBackup("a long password".toCharArray())
        repo.setBlockLabel(11, "A", "")          // clear
        repo.setBlockLabel(11, "B", "Changed")
        assertEquals(mapOf("B" to "Changed"), repo.blockLabels(11).first())
        repo.restore(repo.readBackup(file, "a long password".toCharArray()))
        assertEquals(mapOf("A" to "Main Street"), repo.blockLabels(11).first())
        assertEquals(mapOf("A" to "Temple Area"), repo.blockLabels(12).first())
        assertEquals(5, repo.directory(VoterFilter(ward = 11, block = "A")).first().size) // assignments untouched
    }
}
