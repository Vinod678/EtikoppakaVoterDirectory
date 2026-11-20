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
 * The real bundled Ward 2 dataset (assets/wards/voters_ward_2.json) added to an app that already
 * holds Ward 11 with user data - exactly what happens when the update is installed.
 */
@RunWith(RobolectricTestRunner::class)
class Ward2ImportTest {

    private lateinit var context: Context
    private lateinit var db: VoterDatabase
    private var withWard2 = false
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
        db, ::readAsset, File(context.cacheDir, "w2-${System.nanoTime()}"), clock = { now++ },
        listAssets = { dir -> if (dir == "wards" && withWard2) listOf("voters_ward_2.json") else emptyList() },
    )

    @Test
    fun ward2_importsBesideWard11_keepsWard11UserData_isolatedAndIdempotent() = runBlocking<Unit> {
        // Installed app before the update: Ward 11 only, with the user's own data.
        val before = repo()
        before.ensureImported()
        before.setFavourite(3700.v, true)
        before.setBlockForRange(11, 3691, 3720, "A")
        before.setBlockLabel(11, "A", "Main Street")
        val ward11Households = db.householdDao().allHouseholds().map { it.id to it.name }.toSet()
        val ward11Members = db.householdDao().allMembers().toSet()

        // Update installed: Ward 2 appears in assets.
        withWard2 = true
        val repo = repo()
        val r = repo.ensureImported()
        assertTrue(r.importedNow)
        assertTrue(r.failed.isEmpty())
        assertEquals(369, db.voterDao().countInWard(2))
        assertEquals(369, db.voterDao().countInWard(11))
        assertEquals((370..738).map { VoterIds.of(2, it) }, db.voterDao().allIds(2))
        assertEquals(mapOf(2 to 369, 11 to 369), repo.wardVoterCounts().first().associate { it.wardNo to it.total })

        // Ward 11 untouched: user data, households, block labels.
        assertTrue(db.userDataDao().allUserData().first { it.voterId == 3700.v }.favourite)
        assertEquals(30, repo.directory(VoterFilter(ward = 11, block = "A")).first().size)
        assertEquals(mapOf("A" to "Main Street"), repo.blockLabels(11).first())
        assertTrue(repo.blockLabels(2).first().isEmpty())
        val all = db.householdDao().allHouseholds()
        assertEquals(ward11Households, all.filter { it.wardNo == 11 }.map { it.id to it.name }.toSet())
        assertTrue(db.householdDao().allMembers().toSet().containsAll(ward11Members))

        // Ward 2 households: every Ward 2 voter in exactly one, never mixed with Ward 11.
        val members = db.householdDao().allMembers()
        val ward2Ids = members.filter { VoterIds.wardOf(it.voterId) == 2 }
        assertEquals(369, ward2Ids.map { it.voterId }.toSet().size)
        assertEquals(369, ward2Ids.size)
        val hhWard = all.associate { it.id to it.wardNo }
        assertTrue(members.all { hhWard[it.householdId] == VoterIds.wardOf(it.voterId) })
        val yerramsetti = repo.householdOf(VoterIds.of(2, 550)).first()!!
        assertEquals("Yerramsetti", yerramsetti.name)

        // Ward 2 voter details, photos in their own folder, search.
        val v = repo.voter(VoterIds.of(2, 562)).first()!!
        assertEquals("Vinod Yedla", v.nameEn)
        assertEquals("ward_2/562.jpg", v.photoFilename)
        assertEquals("XOO2009637", repo.voter(VoterIds.of(2, 575)).first()!!.epicNo)
        val found = repo.directory(VoterFilter(ward = null, query = "Yerramsetti")).first()
        // Global search spans both wards (Ward 11 has one Yerramsetti too); ward search stays in its ward.
        assertTrue(found.count { it.voter.wardNo == 2 } == 18 && found.count { it.voter.wardNo == 11 } == 1)
        assertTrue(repo.directory(VoterFilter(ward = 2, query = "Yerramsetti")).first().all { it.voter.wardNo == 2 })
        assertTrue(repo.directory(VoterFilter(ward = 2, query = "గుర్రం")).first().isNotEmpty())

        // Restart: nothing re-imported or duplicated.
        assertFalse(repo().ensureImported().importedNow)
        assertEquals(738, db.voterDao().count())
    }
}
