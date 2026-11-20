package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.data.backup.BackupCodec
import com.example.etikoppakavoterdirectory.data.backup.BackupContent
import com.example.etikoppakavoterdirectory.data.backup.BackupException
import com.example.etikoppakavoterdirectory.data.db.TagEntity
import com.example.etikoppakavoterdirectory.data.db.VoterTagEntity
import com.example.etikoppakavoterdirectory.data.db.VoterUserDataEntity
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val serials = (3691..4059).map { VoterIds.of(11, it) }.toSet()
    private val password = "correct horse".toCharArray()
    private val content = BackupContent(
        createdAt = 1_700_000_000_000,
        datasetGeneratedAt = "2026-11-19T03:15:00",
        userData = listOf(
            VoterUserDataEntity(3691.v, favourite = true, visited = true, visitedAt = 1_700_000_000_500, block = "C", note = "నోట్ – note", updatedAt = 5),
            VoterUserDataEntity(4059.v, favourite = false, visited = false, block = null, note = null, updatedAt = 6),
        ),
        tags = listOf(TagEntity(1, "Needs follow-up", 1), TagEntity(2, "Lives elsewhere", 2)),
        voterTags = listOf(VoterTagEntity(3691.v, 1), VoterTagEntity(4059.v, 2)),
    )

    private fun fast(json: String) = BackupCodec.encrypt(json, password.copyOf(), iterations = 10_000)

    @Test
    fun roundTrip_preservesEverything() {
        val file = fast(BackupCodec.toJson(content))
        val restored = BackupCodec.fromJson(BackupCodec.decrypt(file, password.copyOf()), serials)
        assertEquals(content, restored)
    }

    @Test
    fun fileDoesNotContainPlainText() {
        val text = String(fast(BackupCodec.toJson(content)), Charsets.UTF_8)
        assertFalse(text.contains("Needs follow-up"))
        assertFalse(text.contains("gp_serial_no"))
    }

    @Test
    fun wrongPassword_isRejected() {
        val file = fast(BackupCodec.toJson(content))
        val e = assertThrows(BackupException::class.java) { BackupCodec.decrypt(file, "wrong password".toCharArray()) }
        assertTrue(e.message!!.contains("Wrong password"))
    }

    @Test
    fun tamperedFile_isRejected() {
        val env = JSONObject(String(fast(BackupCodec.toJson(content)), Charsets.UTF_8))
        val data = env.getString("data").toCharArray()
        data[10] = if (data[10] == 'A') 'B' else 'A'
        env.put("data", String(data))
        assertThrows(BackupException::class.java) {
            BackupCodec.decrypt(env.toString().toByteArray(), password.copyOf())
        }
    }

    @Test
    fun randomFile_isRejected() {
        assertThrows(BackupException::class.java) { BackupCodec.decrypt("hello".toByteArray(), password.copyOf()) }
        assertThrows(BackupException::class.java) { BackupCodec.decrypt("{\"format\":\"other\"}".toByteArray(), password.copyOf()) }
    }

    @Test
    fun shortPassword_notAllowedForExport() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encrypt("{}", "short".toCharArray()) }
    }

    @Test
    fun unknownVoter_isRejected() {
        val bad = content.copy(userData = listOf(VoterUserDataEntity(9999.v, favourite = true)))
        assertThrows(BackupException::class.java) { BackupCodec.fromJson(BackupCodec.toJson(bad), serials) }
    }

    @Test
    fun invalidBlock_isRejected() {
        val bad = content.copy(userData = listOf(VoterUserDataEntity(3691.v, block = "Z")))
        assertThrows(BackupException::class.java) { BackupCodec.fromJson(BackupCodec.toJson(bad), serials) }
    }

    @Test
    fun duplicateTagNames_andDanglingLinks_areRejected() {
        val dupNames = content.copy(tags = listOf(TagEntity(1, "Same"), TagEntity(2, "same")), voterTags = emptyList())
        assertThrows(BackupException::class.java) { BackupCodec.fromJson(BackupCodec.toJson(dupNames), serials) }
        val dangling = content.copy(voterTags = listOf(VoterTagEntity(3691.v, 99)))
        assertThrows(BackupException::class.java) { BackupCodec.fromJson(BackupCodec.toJson(dangling), serials) }
    }

    @Test
    fun duplicateVoterRows_areRejected() {
        val dup = content.copy(userData = listOf(VoterUserDataEntity(3691.v), VoterUserDataEntity(3691.v)))
        assertThrows(BackupException::class.java) { BackupCodec.fromJson(BackupCodec.toJson(dup), serials) }
    }
}
