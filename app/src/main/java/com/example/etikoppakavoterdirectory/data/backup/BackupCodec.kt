package com.example.etikoppakavoterdirectory.data.backup

import com.example.etikoppakavoterdirectory.data.db.BlockLabelEntity
import com.example.etikoppakavoterdirectory.data.db.Blocks
import com.example.etikoppakavoterdirectory.data.db.HouseholdEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdLockEntity
import com.example.etikoppakavoterdirectory.data.db.HouseholdMemberEntity
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import com.example.etikoppakavoterdirectory.data.db.TagEntity
import com.example.etikoppakavoterdirectory.data.db.VoterTagEntity
import com.example.etikoppakavoterdirectory.data.db.VoterUserDataEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything the user has entered. The bundled voter dataset itself is never part of a backup. */
data class BackupContent(
    val createdAt: Long,
    val datasetGeneratedAt: String?,
    val userData: List<VoterUserDataEntity>,
    val tags: List<TagEntity>,
    val voterTags: List<VoterTagEntity>,
    /** null = the backup was made before households existed (app 1.x): current households are kept. */
    val households: List<HouseholdEntity>? = null,
    val householdMembers: List<HouseholdMemberEntity> = emptyList(),
    /** null = backup made before automatic grouping (app 2.x): all its households count as the user's own. */
    val householdLocks: List<HouseholdLockEntity>? = null,
    /** Content schema of the file (1 = app 1.x, 2 = app 2.x Ward 11 only, 3 = app 3.x all wards). */
    val schema: Int = CONTENT_SCHEMA_CURRENT,
    /** null = backup made before block labels existed (app 3.0 and older): current labels are kept. */
    val blockLabels: List<BlockLabelEntity>? = null,
) {
    val isLegacyWard11Only get() = schema < 3
    val favourites get() = userData.count { it.favourite }
    val visited get() = userData.count { it.visited }
    val blocksAssigned get() = userData.count { it.block != null }
    val notes get() = userData.count { !it.note.isNullOrEmpty() }
}

class BackupException(message: String) : Exception(message)

const val CONTENT_SCHEMA_CURRENT = 3

private fun label(id: Int) = "Ward ${VoterIds.wardOf(id)} serial ${VoterIds.serialOf(id)}"

/**
 * Password-protected backup file format.
 *
 * File = JSON envelope {format, version, kdf, iterations, salt, iv, data}. `data` is the backup JSON
 * encrypted with AES-256-GCM; the key is derived from the user's password with PBKDF2-HMAC-SHA256.
 * GCM authentication means a wrong password or any modification of the file is detected.
 */
object BackupCodec {
    const val FORMAT = "etikoppaka-ward11-user-backup"
    const val VERSION = 1
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    const val MAX_NOTE_LENGTH = 10_000
    const val MAX_TAG_NAME_LENGTH = 40
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_HOUSEHOLD_NAME_LENGTH = 60
    const val MAX_BLOCK_LABEL_LENGTH = 40
    /** Content schema written by this version. 1 = app 1.x (no households), 2 = app 2.x, 3 = app 3.x (all wards). */
    const val CONTENT_SCHEMA = CONTENT_SCHEMA_CURRENT
    private const val ITERATIONS = 120_000
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val GCM_TAG_BITS = 128

    // ---------- encryption ----------

    fun encrypt(plainJson: String, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password too short" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(iterations))
        val data = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", b64.encodeToString(salt))
            .put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(data))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decrypt(file: ByteArray, password: CharArray): String {
        if (file.size > MAX_FILE_BYTES) throw BackupException("The file is too large to be a backup of this app.")
        val env = try {
            JSONObject(String(file, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("This is not a backup file from this app.")
        }
        if (env.optString("format") != FORMAT) throw BackupException("This is not a backup file from this app.")
        if (env.optInt("version", -1) != VERSION) throw BackupException("Unsupported backup version.")
        if (env.optString("kdf") != KDF) throw BackupException("Unsupported backup encryption.")
        val iterations = env.optInt("iterations", -1)
        if (iterations !in 10_000..2_000_000) throw BackupException("The backup file is damaged.")
        return try {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(env.getString("salt"))
            val iv = b64.decode(env.getString("iv"))
            val data = b64.decode(env.getString("data"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(iterations))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw BackupException("Wrong password, or the backup file has been changed or damaged.")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The backup file is damaged.")
        }
    }

    private fun aad(iterations: Int) = "$FORMAT|$VERSION|$KDF|$iterations".toByteArray(Charsets.UTF_8)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    // ---------- JSON ----------

    fun toJson(content: BackupContent): String {
        val users = JSONArray()
        content.userData.forEach { u ->
            users.put(
                JSONObject()
                    .put("voter_id", u.voterId)
                    .put("ward_no", VoterIds.wardOf(u.voterId))
                    .put("gp_serial_no", VoterIds.serialOf(u.voterId))
                    .put("favourite", u.favourite)
                    .put("visited", u.visited)
                    .put("visited_at", u.visitedAt ?: JSONObject.NULL)
                    .put("block", u.block ?: JSONObject.NULL)
                    .put("note", u.note ?: JSONObject.NULL)
                    .put("updated_at", u.updatedAt)
            )
        }
        val tags = JSONArray()
        content.tags.forEach { t -> tags.put(JSONObject().put("id", t.id).put("name", t.name).put("created_at", t.createdAt)) }
        val links = JSONArray()
        content.voterTags.forEach { l -> links.put(JSONObject().put("voter_id", l.voterId).put("ward_no", VoterIds.wardOf(l.voterId)).put("gp_serial_no", VoterIds.serialOf(l.voterId)).put("tag_id", l.tagId)) }
        val root = JSONObject()
            .put("schema", CONTENT_SCHEMA)
            .put("created_at", content.createdAt)
            .put("dataset_generated_at", content.datasetGeneratedAt ?: JSONObject.NULL)
            .put("user_data", users)
            .put("tags", tags)
            .put("voter_tags", links)
        content.households?.let { list ->
            val hh = JSONArray()
            val membersById = content.householdMembers.groupBy { it.householdId }
            list.forEach { h ->
                val members = JSONArray()
                membersById[h.id].orEmpty().sortedBy { it.voterId }.forEach { members.put(it.voterId) }
                hh.put(
                    JSONObject()
                        .put("id", h.id)
                        .put("ward_no", h.wardNo)
                        .put("name", h.name)
                        .put("auto_key", h.autoKey ?: JSONObject.NULL)
                        .put("locked", h.locked)
                        .put("needs_review", h.needsReview)
                        .put("review_note", h.reviewNote ?: JSONObject.NULL)
                        .put("house_no", h.houseNo ?: JSONObject.NULL)
                        .put("created_at", h.createdAt)
                        .put("updated_at", h.updatedAt)
                        .put("members", members)
                )
            }
            root.put("households", hh)
        }
        content.blockLabels?.let { labels ->
            val arr = JSONArray()
            labels.forEach { arr.put(JSONObject().put("ward_no", it.wardNo).put("block", it.block).put("label", it.label).put("updated_at", it.updatedAt)) }
            root.put("block_labels", arr)
        }
        content.householdLocks?.let { locks ->
            val arr = JSONArray()
            locks.sortedBy { it.voterId }.forEach { arr.put(JSONObject().put("voter_id", it.voterId).put("locked_at", it.lockedAt)) }
            root.put("household_locks", arr)
        }
        return root.toString()
    }

    /**
     * Parses and fully validates decrypted backup JSON. Every serial number must belong to the
     * installed dataset; blocks, tags and links must be consistent. Throws [BackupException] with a
     * user-readable reason on the first problem.
     */
    fun fromJson(json: String, validIds: Set<Int>): BackupContent {
        try {
            val root = JSONObject(json)
            val schema = root.optInt("schema", -1)
            if (schema !in 1..CONTENT_SCHEMA) throw BackupException("Unsupported backup content.")
            // Backups of app 1.x / 2.x hold Ward 11 GP serial numbers; app 3.x backups hold voter ids.
            fun idOf(o: JSONObject): Int =
                if (schema >= 3) o.getInt("voter_id") else VoterIds.of(VoterIds.LEGACY_WARD, o.getInt("gp_serial_no"))
            fun memberId(raw: Int): Int = if (schema >= 3) raw else VoterIds.of(VoterIds.LEGACY_WARD, raw)
            val userArr = root.getJSONArray("user_data")
            val userData = ArrayList<VoterUserDataEntity>(userArr.length())
            val seen = HashSet<Int>()
            for (i in 0 until userArr.length()) {
                val o = userArr.getJSONObject(i)
                val serial = idOf(o)
                if (serial !in validIds) throw BackupException("Backup refers to voter ${label(serial)}, who is not in this directory.")
                if (!seen.add(serial)) throw BackupException("Backup contains voter ${label(serial)} more than once.")
                val block = if (o.isNull("block")) null else o.getString("block")
                if (!Blocks.isValid(block)) throw BackupException("Backup contains an invalid block for voter ${label(serial)}.")
                val note = if (o.isNull("note")) null else o.getString("note")
                if (note != null && note.length > MAX_NOTE_LENGTH) throw BackupException("A note in the backup is too long.")
                val visited = o.getBoolean("visited")
                userData += VoterUserDataEntity(
                    voterId = serial,
                    favourite = o.getBoolean("favourite"),
                    visited = visited,
                    visitedAt = if (!visited || o.isNull("visited_at")) null else o.getLong("visited_at"),
                    block = block,
                    note = note,
                    updatedAt = o.optLong("updated_at", 0),
                )
            }
            val tagArr = root.getJSONArray("tags")
            val tags = ArrayList<TagEntity>(tagArr.length())
            val tagIds = HashSet<Long>()
            val tagNames = HashSet<String>()
            for (i in 0 until tagArr.length()) {
                val o = tagArr.getJSONObject(i)
                val id = o.getLong("id")
                val name = o.getString("name").trim()
                if (id <= 0 || !tagIds.add(id)) throw BackupException("Backup contains an invalid tag.")
                if (name.isEmpty() || name.length > MAX_TAG_NAME_LENGTH) throw BackupException("Backup contains an invalid tag name.")
                if (!tagNames.add(name.lowercase())) throw BackupException("Backup contains the tag \"$name\" twice.")
                tags += TagEntity(id = id, name = name, createdAt = o.optLong("created_at", 0))
            }
            val linkArr = root.getJSONArray("voter_tags")
            val links = ArrayList<VoterTagEntity>(linkArr.length())
            val linkKeys = HashSet<Pair<Int, Long>>()
            for (i in 0 until linkArr.length()) {
                val o = linkArr.getJSONObject(i)
                val serial = idOf(o)
                val tagId = o.getLong("tag_id")
                if (serial !in validIds || tagId !in tagIds) throw BackupException("Backup contains an invalid tag assignment.")
                if (linkKeys.add(serial to tagId)) links += VoterTagEntity(serial, tagId)
            }
            var households: List<HouseholdEntity>? = null
            val members = ArrayList<HouseholdMemberEntity>()
            if (root.has("households")) {
                val hhArr = root.getJSONArray("households")
                val list = ArrayList<HouseholdEntity>(hhArr.length())
                val ids = HashSet<Long>()
                val memberSeen = HashSet<Int>()
                for (i in 0 until hhArr.length()) {
                    val o = hhArr.getJSONObject(i)
                    val id = o.getLong("id")
                    val name = o.getString("name").trim()
                    if (id <= 0 || !ids.add(id)) throw BackupException("Backup contains an invalid household.")
                    if (name.isEmpty() || name.length > MAX_HOUSEHOLD_NAME_LENGTH) throw BackupException("Backup contains an invalid household name.")
                    val arr = o.getJSONArray("members")
                    if (arr.length() == 0) throw BackupException("Backup contains an empty household.")
                    val wards = HashSet<Int>()
                    for (j in 0 until arr.length()) {
                        val serial = memberId(arr.getInt(j))
                        if (serial !in validIds) throw BackupException("Backup refers to voter ${label(serial)}, who is not in this directory.")
                        if (!memberSeen.add(serial)) throw BackupException("Backup puts voter ${label(serial)} in more than one household.")
                        wards += VoterIds.wardOf(serial)
                        members += HouseholdMemberEntity(serial, id)
                    }
                    val ward = if (schema >= 3) o.getInt("ward_no") else VoterIds.LEGACY_WARD
                    if (wards.any { it != ward }) throw BackupException("Backup contains a household with voters of different wards.")
                    list += HouseholdEntity(
                        id = id,
                        wardNo = ward,
                        name = name,
                        houseNo = if (o.isNull("house_no")) null else o.getString("house_no"),
                        createdAt = o.optLong("created_at", 0),
                        updatedAt = o.optLong("updated_at", 0),
                        autoKey = if (schema < 3 || o.isNull("auto_key")) null else o.getString("auto_key"),
                        // Households in app 2.x backups were all created by the user.
                        locked = if (schema < 3) true else o.optBoolean("locked", true),
                        needsReview = schema >= 3 && o.optBoolean("needs_review", false),
                        reviewNote = if (schema < 3 || o.isNull("review_note")) null else o.getString("review_note"),
                    )
                }
                households = list
            }
            var locks: List<HouseholdLockEntity>? = null
            if (schema >= 3 && root.has("household_locks")) {
                val arr = root.getJSONArray("household_locks")
                val l = ArrayList<HouseholdLockEntity>(arr.length())
                val lockSeen = HashSet<Int>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getInt("voter_id")
                    if (id !in validIds) throw BackupException("Backup refers to voter ${label(id)}, who is not in this directory.")
                    if (lockSeen.add(id)) l += HouseholdLockEntity(id, o.optLong("locked_at", 0))
                }
                locks = l
            }
            var labels: List<BlockLabelEntity>? = null
            if (root.has("block_labels")) {
                val arr = root.getJSONArray("block_labels")
                val l = ArrayList<BlockLabelEntity>(arr.length())
                val keys = HashSet<Pair<Int, String>>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ward = o.getInt("ward_no"); val block = o.getString("block"); val label = o.getString("label").trim()
                    if (ward !in 1..VoterIds.MAX_WARD || block !in Blocks.ALL || label.isEmpty() || label.length > MAX_BLOCK_LABEL_LENGTH) {
                        throw BackupException("Backup contains an invalid block label.")
                    }
                    if (keys.add(ward to block)) l += BlockLabelEntity(ward, block, label, o.optLong("updated_at", 0))
                }
                labels = l
            }
            return BackupContent(
                blockLabels = labels,
                schema = schema,
                householdLocks = locks,
                households = households,
                householdMembers = members,
                createdAt = root.optLong("created_at", 0),
                datasetGeneratedAt = if (root.isNull("dataset_generated_at")) null else root.getString("dataset_generated_at"),
                userData = userData,
                tags = tags,
                voterTags = links,
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The backup content is damaged or incomplete.")
        }
    }
}
