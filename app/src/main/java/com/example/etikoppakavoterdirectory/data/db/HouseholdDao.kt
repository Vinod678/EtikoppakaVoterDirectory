package com.example.etikoppakavoterdirectory.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Households (automatic and user-made) and their members. Every list query is ward-scoped. */
@Dao
interface HouseholdDao {

    @Query(
        """
        SELECT h.id AS id, h.name AS name, h.house_no AS house_no,
               COUNT(v.voter_id) AS members,
               COALESCE(SUM(CASE WHEN u.visited = 1 THEN 1 ELSE 0 END), 0) AS visited,
               COALESCE(SUM(CASE WHEN u.favourite = 1 THEN 1 ELSE 0 END), 0) AS favourites,
               MIN(v.gp_serial_no) AS first_serial,
               h.ward_no AS ward_no, h.locked AS locked, h.needs_review AS needs_review, h.review_note AS review_note
        FROM households h
        LEFT JOIN household_members m ON m.household_id = h.id
        LEFT JOIN voters v ON v.voter_id = m.voter_id
        LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE h.ward_no = :ward
        GROUP BY h.id
        ORDER BY members DESC, first_serial IS NULL, first_serial, h.id
        """
    )
    fun observeSummaries(ward: Int): Flow<List<HouseholdSummary>>

    @Query("SELECT ward_no, COUNT(*) AS total FROM households GROUP BY ward_no ORDER BY ward_no")
    fun observeWardCounts(): Flow<List<WardCount>>

    @Query(
        """
        SELECT m.household_id AS household_id, v.name_en AS name_en, v.name_te AS name_te, v.house_no AS house_no,
               v.relative_name_en AS relative_name_en, v.relative_name_te AS relative_name_te, v.gp_serial_no AS gp_serial_no
        FROM household_members m JOIN voters v ON v.voter_id = m.voter_id
        WHERE v.ward_no = :ward
        """
    )
    fun observeMemberNames(ward: Int): Flow<List<HouseholdMemberName>>

    @Query("SELECT * FROM households WHERE id = :id")
    fun observeHousehold(id: Long): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun household(id: Long): HouseholdEntity?

    @Query("SELECT * FROM households WHERE id IN (:ids)")
    suspend fun householdsIn(ids: List<Long>): List<HouseholdEntity>

    @Query(
        """
        SELECT h.* FROM households h JOIN household_members m ON m.household_id = h.id
        WHERE m.voter_id = :voterId
        """
    )
    fun observeHouseholdOf(voterId: Int): Flow<HouseholdEntity?>

    @Query(
        """
        SELECT v.*,
               COALESCE(u.favourite, 0) AS favourite,
               COALESCE(u.visited, 0) AS visited,
               u.block AS block,
               CASE WHEN u.note IS NOT NULL AND u.note <> '' THEN 1 ELSE 0 END AS has_note,
               (SELECT COUNT(*) FROM voter_tags t WHERE t.voter_id = v.voter_id) AS tag_count
        FROM household_members m
        JOIN voters v ON v.voter_id = m.voter_id
        LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE m.household_id = :id
        ORDER BY v.gp_serial_no
        """
    )
    fun observeMembers(id: Long): Flow<List<VoterListItem>>

    /** Members in serial order. */
    @Query(
        """
        SELECT m.voter_id FROM household_members m JOIN voters v ON v.voter_id = m.voter_id
        WHERE m.household_id = :id ORDER BY v.gp_serial_no
        """
    )
    suspend fun memberIds(id: Long): List<Int>

    @Query("SELECT m.* FROM household_members m JOIN households h ON h.id = m.household_id WHERE h.ward_no = :ward")
    fun observeMembersOfWard(ward: Int): Flow<List<HouseholdMemberEntity>>

    /**
     * Coverage of [ward]: voters of the ward, and DISTINCT voters of the ward that belong to an
     * existing household of the same ward (a membership row pointing to no household never counts).
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM voters WHERE ward_no = :ward) AS total,
               (SELECT COUNT(DISTINCT m.voter_id) FROM household_members m
                    JOIN voters v ON v.voter_id = m.voter_id
                    JOIN households h ON h.id = m.household_id
                WHERE v.ward_no = :ward AND h.ward_no = :ward) AS assigned
        """
    )
    fun observeCoverage(ward: Int): Flow<HouseholdCoverage>

    /** Voters of [ward] that are in no (existing) household, in serial order. */
    @Query(
        """
        SELECT v.* FROM voters v
        WHERE v.ward_no = :ward AND NOT EXISTS (
            SELECT 1 FROM household_members m JOIN households h ON h.id = m.household_id
            WHERE m.voter_id = v.voter_id AND h.ward_no = :ward)
        ORDER BY v.gp_serial_no
        """
    )
    fun observeUnassigned(ward: Int): Flow<List<VoterEntity>>

    @Query("SELECT m.* FROM household_members m JOIN households h ON h.id = m.household_id WHERE h.ward_no = :ward")
    suspend fun membersOfWard(ward: Int): List<HouseholdMemberEntity>

    @Query("SELECT * FROM household_members WHERE voter_id IN (:ids)")
    suspend fun membershipsOf(ids: List<Int>): List<HouseholdMemberEntity>

    @Query("SELECT * FROM households WHERE ward_no = :ward")
    suspend fun householdsOfWard(ward: Int): List<HouseholdEntity>

    @Insert
    suspend fun insertHousehold(h: HouseholdEntity): Long

    @Query("UPDATE households SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Int

    @Query("UPDATE households SET updated_at = :now WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, now: Long)

    /** User edit: the household becomes the user's own and is never changed by automatic grouping. */
    @Query("UPDATE households SET locked = 1, needs_review = 0, review_note = NULL, updated_at = :now WHERE id IN (:ids)")
    suspend fun lockHouseholds(ids: List<Long>, now: Long)

    @Query("UPDATE households SET house_no = :houseNo WHERE id = :id")
    suspend fun setHouseNo(id: Long, houseNo: String?)

    @Query(
        """
        UPDATE households SET name = :name, house_no = :houseNo, needs_review = :needsReview,
               review_note = :reviewNote, updated_at = :now
        WHERE id = :id AND locked = 0
        """
    )
    suspend fun updateAuto(id: Long, name: String, houseNo: String?, needsReview: Boolean, reviewNote: String?, now: Long)

    /** REPLACE: a voter who already belongs to another household is moved. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMembers(rows: List<HouseholdMemberEntity>)

    @Query("DELETE FROM household_members WHERE voter_id IN (:ids)")
    suspend fun removeMembers(ids: List<Int>): Int

    @Query("DELETE FROM household_members WHERE household_id IN (:ids)")
    suspend fun removeMembersOf(ids: List<Long>)

    @Query("UPDATE household_members SET household_id = :target WHERE household_id IN (:sources)")
    suspend fun moveAllMembers(sources: List<Long>, target: Long)

    @Query("DELETE FROM households WHERE id = :id")
    suspend fun deleteHousehold(id: Long)

    @Query("DELETE FROM households WHERE id IN (:ids)")
    suspend fun deleteHouseholds(ids: List<Long>)

    /** A household without members has no meaning; removed after every membership change. */
    @Query("DELETE FROM households WHERE id NOT IN (SELECT DISTINCT household_id FROM household_members)")
    suspend fun deleteEmptyHouseholds(): Int

    // ---------- locks (manual corrections) ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocks(rows: List<HouseholdLockEntity>)

    @Query("SELECT voter_id FROM household_locks WHERE voter_id BETWEEN :from AND :to")
    suspend fun lockedIdsBetween(from: Int, to: Int): List<Int>

    // ---------- backup / restore ----------

    @Query("SELECT * FROM households ORDER BY id")
    suspend fun allHouseholds(): List<HouseholdEntity>

    @Query("SELECT * FROM household_members ORDER BY household_id, voter_id")
    suspend fun allMembers(): List<HouseholdMemberEntity>

    @Query("SELECT * FROM household_locks ORDER BY voter_id")
    suspend fun allLocks(): List<HouseholdLockEntity>

    @Query("DELETE FROM households")
    suspend fun clearHouseholds()

    @Query("DELETE FROM household_members")
    suspend fun clearMembers()

    @Query("DELETE FROM household_locks")
    suspend fun clearLocks()

    @Insert
    suspend fun insertHouseholds(rows: List<HouseholdEntity>)

    @Insert
    suspend fun insertMembers(rows: List<HouseholdMemberEntity>)

    @Transaction
    suspend fun replaceAll(households: List<HouseholdEntity>, members: List<HouseholdMemberEntity>, locks: List<HouseholdLockEntity>) {
        clearMembers()
        clearHouseholds()
        clearLocks()
        insertHouseholds(households)
        members.chunked(500).forEach { insertMembers(it) }
        locks.chunked(500).forEach { insertLocks(it) }
    }
}
