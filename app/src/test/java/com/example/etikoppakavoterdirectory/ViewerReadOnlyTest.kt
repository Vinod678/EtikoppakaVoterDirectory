package com.example.etikoppakavoterdirectory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.ReadOnlyAccessException
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Viewer sessions are read-only in the data layer itself (not only by hidden buttons): every
 * data-changing, export and backup operation is refused and nothing changes; reading still works.
 */
@RunWith(RobolectricTestRunner::class)
class ViewerReadOnlyTest {

    private lateinit var db: VoterDatabase
    private lateinit var repo: VoterRepository
    private lateinit var safetyDir: File
    private var admin = true

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoterDatabase::class.java).allowMainThreadQueries().build()
        safetyDir = File(context.cacheDir, "safety-viewer-${System.nanoTime()}")
        repo = VoterRepository(
            db,
            { name -> context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() } },
            safetyDir,
            canEdit = { admin },
        )
        runBlocking { repo.ensureImported() } // start-up import is not a user change: always allowed
    }

    @After
    fun tearDown() {
        db.close()
        safetyDir.deleteRecursively()
    }

    private suspend fun refused(what: String, op: suspend () -> Unit) {
        try {
            op()
            fail("$what was allowed for a Viewer")
        } catch (e: ReadOnlyAccessException) {
            // expected
        }
    }

    @Test
    fun viewer_cannotChangeAnything_butCanRead() = runBlocking<Unit> {
        val voter = 3700.v
        val household = repo.householdSummaries().first().first()
        val membersBefore = repo.householdMemberIds(household.id)
        admin = false

        refused("favourite") { repo.setFavourite(voter, true) }
        refused("visited") { repo.setVisited(voter, true) }
        refused("visit time") { repo.clearVisitTime(voter) }
        refused("block") { repo.setBlock(voter, "A") }
        refused("block range") { repo.setBlockForRange(3691, 3700, "B") }
        refused("note") { repo.setNote(voter, "x") }
        refused("bulk favourite") { repo.setFavourites(listOf(voter), true) }
        refused("bulk visited") { repo.setVisitedMany(listOf(voter), true) }
        refused("new tag") { repo.createTag("Viewer tag") }
        refused("default tags") { repo.addDefaultTags() }
        refused("new household") { repo.createHousehold(null, listOf(voter)) }
        refused("remove from household") { repo.removeHouseholdMembers(membersBefore.take(1)) }
        refused("delete household") { repo.deleteHousehold(household.id) }
        refused("regroup") { repo.regroupWardNow(11) }
        refused("export") { repo.exportRecords(listOf(voter)) }
        refused("backup") { repo.createBackup("password123".toCharArray()) }

        // Nothing changed.
        val ud = repo.userData(voter).first()
        assertTrue(ud == null || (!ud.favourite && !ud.visited && ud.block == null && ud.note == null))
        assertEquals(membersBefore, repo.householdMemberIds(household.id))
        assertFalse(repo.tags().first().any { it.name == "Viewer tag" })

        // Reading still works.
        assertNotNull(repo.voter(voter).first())
        assertEquals(369, repo.directory(VoterFilter(ward = 11)).first().size)

        // Admin again: changes work.
        admin = true
        repo.setFavourite(voter, true)
        assertTrue(repo.userData(voter).first()!!.favourite)
    }
}
