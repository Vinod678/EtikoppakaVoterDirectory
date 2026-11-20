package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.UserInputException
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import com.example.etikoppakavoterdirectory.data.household.HouseholdGrouper
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

/** Households, bulk updates, suggestions and backup of households - on the real 369-voter dataset. */
@RunWith(RobolectricTestRunner::class)
class HouseholdTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private var now = 10_000L

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        repo = VoterRepository(db, ::readAsset, File(context.cacheDir, "hh-${System.nanoTime()}"), clock = { now++ })
        runBlocking { repo.ensureImported() }
    }

    @After
    fun tearDown() = db.close()

    private fun summary(id: Long) = runBlocking { repo.householdSummaries().first().first { it.id == id } }

    @Test
    fun createWithNonConsecutiveSerials_defaultName_andProgressCounts() = runBlocking<Unit> {
        val id = repo.createHousehold(null, listOf(3710.v, 3691.v, 3702.v, 3696.v)) // order and gaps do not matter
        assertEquals(listOf(3691, 3696, 3702, 3710), repo.householdMemberSerials(id))
        val h = repo.household(id).first()!!
        assertTrue(h.name.startsWith("Household — "))

        repo.setVisitedMany(listOf(3691.v, 3696.v, 3702.v), true)
        repo.setFavourites(listOf(3691.v), true)
        val s = summary(id)
        assertEquals(4, s.members); assertEquals(3, s.visited); assertEquals(1, s.favourites)
    }

    @Test
    fun bulkVisited_fourOfFive_leavesFifthUnvisited_andDoesNotTouchFavourites() = runBlocking<Unit> {
        val members = listOf(3703, 3704, 3705, 3706, 3707).v()
        val id = repo.createHousehold("Gutti family", members)
        repo.setFavourite(3707.v, true)
        repo.setVisited(3704.v, true) // existing visit time must be kept
        val t3704 = repo.userData(3704.v).first()!!.visitedAt
        repo.setVisitedMany(members.take(4), true)

        assertEquals(t3704, repo.userData(3704.v).first()!!.visitedAt)
        assertNotNull(repo.userData(3703.v).first()!!.visitedAt)
        assertFalse(repo.userData(3707.v).first()!!.visited)
        assertTrue(repo.userData(3707.v).first()!!.favourite)
        assertFalse(repo.userData(3703.v).first()!!.favourite)
        assertEquals(4, summary(id).visited); assertEquals(1, summary(id).favourites)

        repo.setFavourites(members, true)
        assertEquals(4, summary(id).visited) // favourites do not change visits
        repo.setVisitedMany(listOf(3703.v), false)
        assertNull(repo.userData(3703.v).first()!!.visitedAt)
        assertTrue(repo.userData(3703.v).first()!!.favourite)
    }

    @Test
    fun bulkBlocksAndTags_onlyAffectSelected() = runBlocking<Unit> {
        repo.setBlock(3800.v, "D")
        repo.setNote(3801.v, "keep me")
        assertEquals(2, repo.setBlocks(listOf(3801.v, 3802.v), "B"))
        assertEquals(listOf(3801, 3802), repo.directory(VoterFilter(block = "B")).first().map { it.voter.gpSerialNo })
        assertEquals("D", repo.userData(3800.v).first()!!.block)
        assertEquals("keep me", repo.userData(3801.v).first()!!.note)
        val tag = repo.createTag("Need to call later")
        repo.setTagForMany(listOf(3801.v, 3802.v, 3802.v), tag, true)
        assertEquals(listOf(3801, 3802), repo.directory(VoterFilter(tagId = tag)).first().map { it.voter.gpSerialNo })
        repo.setTagForMany(listOf(3802.v), tag, false)
        assertEquals(listOf(3801), repo.directory(VoterFilter(tagId = tag)).first().map { it.voter.gpSerialNo })
    }

    @Test
    fun addMoveMergeSplitRemove_andSingleMember() = runBlocking<Unit> {
        val a = repo.createHousehold("A", listOf(3691.v, 3692.v))
        val b = repo.createHousehold("B", listOf(3693.v))           // single-member household
        assertEquals(1, summary(b).members)

        repo.addHouseholdMembers(a, listOf(3693.v))                 // moved from B -> B becomes empty and is removed
        assertNull(repo.household(b).first())
        assertEquals(listOf(3691, 3692, 3693), repo.householdMemberSerials(a))

        val c = repo.createHousehold("C", listOf(3700.v, 3701.v))
        repo.mergeHouseholds(a, listOf(c))
        assertNull(repo.household(c).first())
        assertEquals(listOf(3691, 3692, 3693, 3700, 3701), repo.householdMemberSerials(a))

        val d = repo.splitHousehold(a, listOf(3700.v, 3701.v), null)
        assertEquals(listOf(3700, 3701), repo.householdMemberSerials(d))
        assertEquals(listOf(3691, 3692, 3693), repo.householdMemberSerials(a))
        // Moving all members is now allowed (see HouseholdMoveTest); voters who are not members still fail.
        assertThrows(UserInputException::class.java) { runBlocking { repo.splitHousehold(a, listOf(3800.v), null) } }

        repo.renameHousehold(a, "  Lanka   household ")
        assertEquals("Lanka household", repo.household(a).first()!!.name)
        assertThrows(UserInputException::class.java) { runBlocking { repo.renameHousehold(a, "  ") } }

        repo.removeHouseholdMembers(listOf(3700.v, 3701.v))
        assertNull(repo.household(d).first())
        repo.deleteHousehold(a)
        // Only automatic households remain; the voters the user removed stay without a household.
        assertTrue(repo.householdSummaries().first().none { it.locked })
        assertTrue(repo.membershipsOf(listOf(3691, 3692, 3693, 3700, 3701).v()).isEmpty())
        repo.regroupWardNow(11) // automatic grouping must not undo those manual corrections
        assertTrue(repo.membershipsOf(listOf(3691, 3692, 3693, 3700, 3701).v()).isEmpty())
        assertEquals(369, db.voterDao().count()) // voter records never touched
    }

    private suspend fun householdOf(serial: Int) = repo.membershipsOf(listOf(serial.v))[serial.v]
    private suspend fun membersOf(serial: Int) = repo.householdMemberSerials(householdOf(serial)!!)

    @Test
    fun automaticGrouping_everyVoterExactlyOnce_noSuggestionsNeeded() = runBlocking<Unit> {
        val members = db.householdDao().allMembers()
        assertEquals(369, members.size)
        assertEquals(369, members.map { it.voterId }.toSet().size) // no duplicate assignments
        val summaries = repo.householdSummaries().first()
        assertTrue(summaries.isNotEmpty())
        assertTrue(summaries.all { !it.locked }) // generated, not user-made
        assertTrue(summaries.any { it.members == 1 }) // one-member households are supported
        assertEquals(369, summaries.sumOf { it.members })
    }

    @Test
    fun surnameFirstGrouping_acrossHouseNumbers_surnameOnlyHeadings() = runBlocking<Unit> {
        // Mogga: Mahesh (9-28), Kartheek (9-28A), Satyanarayana and Ammaji (9-29) - different houses, one household.
        val mogga = membersOf(3824)
        assertTrue(mogga.containsAll(listOf(3824, 3827, 3829, 3830)))
        assertEquals("Mogga", repo.household(householdOf(3824)!!).first()!!.name)
        // Gutti and Challa: every member carries that surname; heading is the surname only.
        assertTrue(membersOf(3703).containsAll(listOf(3703, 3704, 3705, 3706)))
        assertEquals("Gutti", repo.household(householdOf(3703)!!).first()!!.name)
        assertEquals("Challa", repo.household(householdOf(3700)!!).first()!!.name)
        // Doddipatla at 8-144 and 8-147 (cousin families) are one household; never mixed with Gutti.
        assertTrue(membersOf(3708).containsAll(listOf(3707, 3708, 3709, 3722, 3723)))
        assertFalse(3708 in membersOf(3703))
        // Spelling variants of one surname ("Gonnabattula" / "Gonna Battula").
        assertTrue(membersOf(3697).containsAll(listOf(3697, 3698)))
        // Surname-first name ("Alugolu Varahalu") with "Nageswara Rao Alugolu".
        assertTrue(membersOf(3972).contains(3970))
        // No identifiable surname: fallback, neutral title (no guess, no "Family" suffix anywhere).
        assertTrue(repo.household(householdOf(3691)!!).first()!!.name.startsWith("Household — House"))
        assertTrue(repo.householdSummaries().first().none { it.name.endsWith(" Family") || it.needsReview })
    }

    @Test
    fun automaticGrouping_isDeterministic_andStableAcrossRuns() = runBlocking<Unit> {
        val before = db.householdDao().allMembers().map { it.voterId to it.householdId }.toSet()
        val names = db.householdDao().allHouseholds().map { it.id to it.name }.toSet()
        repo.regroupWardNow(11)
        assertEquals(before, db.householdDao().allMembers().map { it.voterId to it.householdId }.toSet())
        assertEquals(names, db.householdDao().allHouseholds().map { it.id to it.name }.toSet())
        // A second import of the same dataset ("next app start") does not regroup at all.
        assertFalse(repo.ensureImported().importedNow)
        assertEquals(before, db.householdDao().allMembers().map { it.voterId to it.householdId }.toSet())
    }

    @Test
    fun manualCorrections_areNeverOverwrittenByAutomaticGrouping() = runBlocking<Unit> {
        // Move 3705 out of the Gutti household into his own, rename the Gutti household, remove 3706.
        val gutti = householdOf(3703)!!
        val own = repo.createHousehold("Suryababu's own", listOf(3705.v))
        repo.renameHousehold(gutti, "Gutti (checked)")
        repo.removeHouseholdMembers(listOf(3706.v))

        // Re-run grouping and re-import a NEW dataset version: everything the user did stays.
        repo.regroupWardNow(11)
        val newer = readAsset(VoterRepository.DATA_ASSET).replaceFirst("\"generated_at\": \"", "\"generated_at\": \"NEW-")
        VoterRepository(db, { newer }, File(context.cacheDir, "x"), clock = { now++ }).ensureImported()

        assertEquals(listOf(3705), repo.householdMemberSerials(own))
        assertEquals("Gutti (checked)", repo.household(gutti).first()!!.name)
        val guttiNow = repo.householdMemberSerials(gutti)
        assertTrue(guttiNow.containsAll(listOf(3703, 3704)) && 3705 !in guttiNow && 3706 !in guttiNow)
        assertNull(householdOf(3706)) // removed by the user: stays without household
        assertTrue(repo.household(gutti).first()!!.locked)
        assertEquals(368, db.householdDao().allMembers().size)
    }

    @Test
    fun nameMatching_rules() {
        fun t(s: String) = HouseholdGrouper.tokens(s).map(HouseholdGrouper::phonetic)
        assertTrue(HouseholdGrouper.nameMatches(t("Subrahmanyam"), t("Subrahmanyam Gutti")))
        assertTrue(HouseholdGrouper.nameMatches(t("Buchchi Bangaraju"), t("Buchchi Bangaraju Chintalapati")))
        assertTrue(HouseholdGrouper.nameMatches(t("Venkataramana"), t("Venkata Ramana Jami")))
        assertFalse(HouseholdGrouper.nameMatches(t("Rao"), t("Rao Gutti")))           // too short to mean anything
        assertFalse(HouseholdGrouper.nameMatches(t("Sudarshan"), t("Venkata Sudarshana Raju")))
        assertFalse(HouseholdGrouper.nameMatches(t("Ramana"), t("Venkataramana Jami")))
        assertFalse(HouseholdGrouper.nameMatches(emptyList(), t("X Y")))
        assertEquals(HouseholdGrouper.phonetic("Challa"), HouseholdGrouper.phonetic("chala"))
        assertEquals(listOf("d", "v", "srinivasa", "rao", "upmaka"), HouseholdGrouper.tokens("D. V. Srinivasa Rao Upmaka"))
    }

    @Test
    fun householdSearch_andSorting() = runBlocking<Unit> {
        val hs = repo.householdSummaries().first()
        val names = repo.householdMemberNames(11).first()
        val index = com.example.etikoppakavoterdirectory.ui.households.householdSearchIndex(hs, names)
        fun find(q: String) = hs.filter { com.example.etikoppakavoterdirectory.ui.households.matchesHousehold(index.getValue(it.id), q) }.map { it.id }
        val gutti = householdOf(3703)!!
        assertTrue(gutti in find("gutti"))                 // family name
        assertTrue(gutti in find("Suryababu"))             // member name
        assertTrue(gutti in find("గుత్తి"))                  // Telugu
        assertTrue(gutti in find("8-144"))                 // house number
        assertTrue(gutti in find("  NAGANNA "))           // father of a member, case / spaces normalised
        assertTrue(gutti in find("3705"))                  // GP serial
        assertTrue(householdOf(3824)!! in find("mahesh mogga") && householdOf(3824)!! in find("Mogga Mahesh"))
        assertTrue(find("zzqq").isEmpty())
        assertTrue(householdOf(3700)!! in find("s challa")) // initial + family name
        val sorted = com.example.etikoppakavoterdirectory.ui.households.sortHouseholds(hs, com.example.etikoppakavoterdirectory.ui.households.HouseholdSort.MEMBERS_DESC)
        assertEquals(sorted.map { it.members }, sorted.map { it.members }.sortedDescending())
        val az = com.example.etikoppakavoterdirectory.ui.households.sortHouseholds(hs, com.example.etikoppakavoterdirectory.ui.households.HouseholdSort.NAME_ASC).map { it.name.lowercase() }
        assertEquals(az, az.sorted())
        val house = com.example.etikoppakavoterdirectory.ui.households.sortHouseholds(hs, com.example.etikoppakavoterdirectory.ui.households.HouseholdSort.HOUSE_ASC).mapNotNull { it.houseNo }
        assertEquals(house, house.sortedBy { HouseholdGrouper.naturalKey(it) })
        assertTrue(HouseholdGrouper.naturalKey("8-139") < HouseholdGrouper.naturalKey("8-150/1"))
        assertTrue(HouseholdGrouper.naturalKey("9-2") < HouseholdGrouper.naturalKey("10-1"))
    }

    @Test
    fun backup_includesHouseholds_andOldBackupKeepsCurrentHouseholds() = runBlocking<Unit> {
        val id = repo.createHousehold("Family one", listOf(3691.v, 3692.v))
        val file = repo.createBackup("a long password".toCharArray())

        val otherDb = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        val other = VoterRepository(otherDb, ::readAsset, File(context.cacheDir, "o-${System.nanoTime()}"), clock = { now++ })
        other.ensureImported()
        val content = other.readBackup(file, "a long password".toCharArray())
        assertTrue(content.households!!.single { it.id == id }.locked) // user household + automatic ones
        assertTrue(content.householdLocks!!.map { it.voterId }.containsAll(listOf(3691.v, 3692.v)))
        other.restore(content)
        assertEquals(listOf(3691, 3692), other.householdMemberSerials(id))
        assertEquals("Family one", other.household(id).first()!!.name)

        // A backup made by app 1.x (content schema 1, no households) keeps the current households.
        val v1Json = BackupCodec.toJson(content.copy(households = null, householdLocks = null)).replace("\"schema\":3", "\"schema\":1")
        val v1 = BackupCodec.fromJson(v1Json, (3691..4059).toList().v().toSet())
        assertNull(v1.households)
        other.restore(v1)
        assertEquals(listOf(3691, 3692), other.householdMemberSerials(id))
        // Undo goes back to the state before that restore, households included.
        other.undoLastRestore()
        assertEquals(listOf(3691, 3692), other.householdMemberSerials(id))
        otherDb.close()
    }
}
