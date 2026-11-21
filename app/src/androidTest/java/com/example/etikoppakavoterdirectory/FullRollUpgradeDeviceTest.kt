package com.example.etikoppakavoterdirectory

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.etikoppakavoterdirectory.data.db.VoterDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the app's own start-up import (the same call the app makes after unlocking) against the
 * REAL installed database, then checks all 14 wards. Safe for real data: it only does what the app
 * itself does on its first start after an update; the second call must import nothing.
 */
@RunWith(AndroidJUnit4::class)
class FullRollUpgradeDeviceTest {

    @Test
    fun upgradeImport_allFourteenWards_onceOnly() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<VoterDirectoryApp>()
        val repo = app.repository
        val r = repo.ensureImported()
        assertTrue(r.failed.isEmpty())
        val expected = (1..14).associateWith { if (it == 14) 373 else 369 }
        assertEquals(expected, repo.wardVoterCounts().first().associate { it.wardNo to it.total })
        val db = VoterDatabase.get(app)
        assertEquals(5170, db.voterDao().count())
        assertEquals(5170, db.householdDao().allMembers().size)
        assertFalse(repo.ensureImported().importedNow)
        assertEquals(5170, db.voterDao().count())
    }
}
