package com.example.etikoppakavoterdirectory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.etikoppakavoterdirectory.data.VoterFilter
import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.DefaultTags
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase.Companion.configure
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds real version-2 and version-3 databases from the exported Room schemas
 * (app/schemas/.../N.json), fills them with user data exactly as app 1.x / 2.x store it, then
 * opens them with the current app (version 4). Room validates the migrated schema against the
 * version-4 schema on open, so any mismatch fails the test.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun readAsset(n: String) = context.assets.open(n).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun createFromSchema(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = File("schemas/com.example.etikoppakavoterdirectory.data.db.VoterDatabase/$version.json")
        val db = JSONObject(schema.readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            val entities = db.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { idx ->
                    for (j in 0 until idx.length()) sql.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = db.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.version = version
            fill(sql)
        }
    }

    private fun open() = Room.databaseBuilder(context, VoterDatabase::class.java, name).configure().allowMainThreadQueries().build()

    @Test
    fun version2To4_keepsAllUserData_addsHouseholdTables_andDefaultTagsOnce() = runBlocking<Unit> {
        createFromSchema(2) { sql ->
            listOf(3691, 3692, 3693).forEach { s ->
                sql.execSQL(
                    "INSERT INTO voters (gp_serial_no, ward_no, name_en, needs_review, review_issue_codes, search_text) VALUES (?, 11, ?, 0, '', '')",
                    arrayOf<Any>(s, "Voter $s"),
                )
            }
            sql.execSQL("INSERT INTO voter_user_data (gp_serial_no, favourite, visited, visited_at, block, note, updated_at) VALUES (3691, 1, 1, 1700000000123, 'C', 'సాయంత్రం కలవాలి', 5)")
            sql.execSQL("INSERT INTO voter_user_data (gp_serial_no, favourite, visited, visited_at, block, note, updated_at) VALUES (3692, 0, 1, NULL, NULL, NULL, 6)")
            sql.execSQL("INSERT INTO tags (id, name, created_at) VALUES (7, 'contacted', 1)") // same as a default tag, other case
            sql.execSQL("INSERT INTO tags (id, name, created_at) VALUES (8, 'Lives elsewhere', 2)")
            sql.execSQL("INSERT INTO voter_tags (gp_serial_no, tag_id) VALUES (3691, 7), (3692, 8)")
        }
        val db = open()
        try {
            val u = db.userDataDao().allUserData().associateBy { it.voterId }
            assertEquals(2, u.size)
            val a = u.getValue(3691.v)
            assertTrue(a.favourite); assertTrue(a.visited)
            assertEquals(1700000000123L, a.visitedAt)
            assertEquals("C", a.block)
            assertEquals("సాయంత్రం కలవాలి", a.note)
            assertEquals(5L, a.updatedAt)
            val b = u.getValue(3692.v)
            assertFalse(b.favourite); assertTrue(b.visited); assertNull(b.visitedAt); assertNull(b.block)

            val tags = db.userDataDao().allTags()
            assertEquals("contacted", tags.first { it.id == 7L }.name)
            assertEquals("Lives elsewhere", tags.first { it.id == 8L }.name)
            assertEquals(2 + DefaultTags.NAMES.size - 1, tags.size)
            assertEquals(tags.size, tags.map { it.name.lowercase() }.toSet().size)
            assertEquals(setOf(3691.v to 7L, 3692.v to 8L), db.userDataDao().allVoterTags().map { it.voterId to it.tagId }.toSet())

            assertTrue(db.householdDao().allHouseholds().isEmpty())
            val repo = VoterRepository(db, { error("no import") }, File(context.cacheDir, "s"))
            val id = repo.createHousehold(null, listOf(3691.v, 3693.v))
            assertEquals(listOf(3691, 3693), repo.householdMemberSerials(id))
            assertEquals(1, repo.householdSummaries().first().single().favourites)
        } finally {
            db.close()
        }
        val again = open()
        try {
            assertEquals(2 + DefaultTags.NAMES.size - 1, again.userDataDao().allTags().size)
            assertEquals(1, again.householdDao().allHouseholds().size)
        } finally {
            again.close()
        }
    }

    /**
     * Version 3 (app 2.0, the installed version) -> 4 with the same kind of data the device holds:
     * the full Ward 11 dataset, blocks, notes, favourites, visits, tag links and one manual
     * household. Afterwards the real import runs (automatic grouping) and must keep all of it.
     */
    @Test
    fun version3To4_rekeysEverything_locksManualHouseholds_andKeepsThemThroughGrouping() = runBlocking<Unit> {
        val dataset = JSONObject(readAsset(VoterRepository.DATA_ASSET))
        val generatedAt = dataset.getJSONObject("meta").getString("generated_at")
        val voters = dataset.getJSONArray("voters")
        createFromSchema(3) { sql ->
            sql.beginTransaction()
            for (i in 0 until voters.length()) {
                val o = voters.getJSONObject(i)
                sql.execSQL(
                    "INSERT INTO voters (gp_serial_no, ward_no, name_en, name_te, relationship, relative_name_en, relative_name_te, age, gender, house_no, " +
                        "photo_filename, needs_review, review_issue_codes, search_text) VALUES (?, 11, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, '', 'x')",
                    arrayOf<Any?>(
                        o.getInt("gp_serial_no"), o.optString("name_en"), o.optString("name_te"), o.optString("relationship"),
                        if (o.isNull("relative_name_en")) null else o.getString("relative_name_en"),
                        if (o.isNull("relative_name_te")) null else o.getString("relative_name_te"),
                        if (o.isNull("age")) null else o.getInt("age"), o.optString("gender"),
                        if (o.isNull("house_no")) null else o.getString("house_no"), o.optString("photo_filename"),
                    ),
                )
            }
            sql.setTransactionSuccessful(); sql.endTransaction()
            sql.execSQL("INSERT INTO dataset_info (id, generated_at, record_count, needs_review_count, imported_at) VALUES (1, ?, 369, 143, 77)", arrayOf<Any>(generatedAt))
            (3691..3720).forEach { s -> sql.execSQL("INSERT INTO voter_user_data (gp_serial_no, block, updated_at) VALUES (?, 'A', 1)", arrayOf<Any>(s)) }
            sql.execSQL("UPDATE voter_user_data SET favourite = 1, visited = 1, visited_at = 123, note = 'note one' WHERE gp_serial_no = 3700")
            sql.execSQL("INSERT INTO voter_user_data (gp_serial_no, block, note, updated_at) VALUES (4059, 'D', 'last', 2)")
            sql.execSQL("INSERT INTO tags (id, name, created_at) VALUES (20, 'Need to call', 1)")
            sql.execSQL("INSERT INTO voter_tags (gp_serial_no, tag_id) VALUES (3700, 20), (4059, 20)")
            // The user's own household on the device: 3697 + 3698.
            sql.execSQL("INSERT INTO households (id, name, house_no, created_at, updated_at) VALUES (3, 'Household — 8-142', '8-142', 1, 1)")
            sql.execSQL("INSERT INTO household_members (gp_serial_no, household_id) VALUES (3697, 3), (3698, 3)")
        }

        val db = open()
        try {
            // Voters: same 369 rows, new stable ids.
            assertEquals(369, db.voterDao().count())
            assertEquals((3691..4059).toList().v(), db.voterDao().allIds(11))
            val info = db.voterDao().datasetInfo(11)!!
            assertEquals(generatedAt, info.generatedAt); assertEquals(77L, info.importedAt); assertNull(info.groupingKey)

            val u = db.userDataDao().allUserData().associateBy { it.voterId }
            assertEquals(31, u.size)
            assertEquals(30, u.values.count { it.block == "A" })
            u.getValue(3700.v).let { assertTrue(it.favourite); assertTrue(it.visited); assertEquals(123L, it.visitedAt); assertEquals("note one", it.note) }
            assertEquals("last", u.getValue(4059.v).note)
            assertEquals(setOf(3700.v, 4059.v), db.userDataDao().allVoterTags().map { it.voterId }.toSet())

            val h = db.householdDao().allHouseholds().single()
            assertEquals(3L, h.id); assertEquals(11, h.wardNo); assertTrue(h.locked); assertEquals("Household — 8-142", h.name)
            assertEquals(setOf(3697.v, 3698.v), db.householdDao().allMembers().map { it.voterId }.toSet())
            assertEquals(setOf(3697.v, 3698.v), db.householdDao().allLocks().map { it.voterId }.toSet())

            // First start of the new app: import (nothing to re-import) + automatic grouping.
            val repo = VoterRepository(db, ::readAsset, File(context.cacheDir, "m"))
            val r = repo.ensureImported()
            assertFalse(r.importedNow)
            assertEquals(369, db.householdDao().allMembers().size) // everyone now in exactly one household
            assertEquals(listOf(3697, 3698), repo.householdMemberSerials(3L))
            assertEquals("Household — 8-142", db.householdDao().household(3L)!!.name)
            assertEquals(30, repo.directory(VoterFilter(ward = 11, block = "A")).first().size)
            // v5 table exists and is empty; manual household survived regrouping, no duplicates.
            assertTrue(db.userDataDao().allBlockLabels().isEmpty())
            assertEquals(369, db.householdDao().allMembers().map { it.voterId }.toSet().size)
            assertTrue(VoterIds.wardOf(db.voterDao().allIds(null).first()) == 11)
        } finally {
            db.close()
        }
    }

    @Test
    fun freshInstall_getsDefaultTagsOnce() = runBlocking<Unit> {
        val db = open()
        try {
            assertEquals(DefaultTags.NAMES, db.userDataDao().allTags().map { it.name })
            val repo = VoterRepository(db, { error("no import") }, File(context.cacheDir, "s"))
            assertEquals(0, repo.addDefaultTags()) // idempotent
            assertEquals(DefaultTags.NAMES.size, db.userDataDao().allTags().size)
            repo.deleteTag(db.userDataDao().allTags().first { it.name == "Abroad" }.id)
            assertEquals(1, repo.addDefaultTags())
            assertEquals(DefaultTags.NAMES.size, db.userDataDao().allTags().size)
        } finally {
            db.close()
        }
    }
}
