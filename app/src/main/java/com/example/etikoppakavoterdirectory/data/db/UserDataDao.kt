package com.example.etikoppakavoterdirectory.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The user's editable data: favourites, visits, blocks, notes and tags. */
@Dao
interface UserDataDao {

    // ---------- per-voter markers ----------

    @Query("SELECT * FROM voter_user_data WHERE voter_id = :id")
    fun observeUserData(id: Int): Flow<VoterUserDataEntity?>

    @Query("SELECT * FROM voter_user_data WHERE voter_id = :id")
    suspend fun userData(id: Int): VoterUserDataEntity?

    @Query("INSERT OR IGNORE INTO voter_user_data (voter_id, updated_at) VALUES (:id, :now)")
    suspend fun ensureRow(id: Int, now: Long)

    @Query("UPDATE voter_user_data SET favourite = :favourite, updated_at = :now WHERE voter_id = :id")
    suspend fun updateFavourite(id: Int, favourite: Boolean, now: Long)

    @Query("UPDATE voter_user_data SET visited = :visited, visited_at = :visitedAt, updated_at = :now WHERE voter_id = :id")
    suspend fun updateVisited(id: Int, visited: Boolean, visitedAt: Long?, now: Long)

    @Query("UPDATE voter_user_data SET block = :block, updated_at = :now WHERE voter_id = :id")
    suspend fun updateBlock(id: Int, block: String?, now: Long)

    @Query("UPDATE voter_user_data SET note = :note, updated_at = :now WHERE voter_id = :id")
    suspend fun updateNote(id: Int, note: String?, now: Long)

    @Transaction
    suspend fun setFavourite(id: Int, favourite: Boolean, now: Long) {
        ensureRow(id, now); updateFavourite(id, favourite, now)
    }

    @Transaction
    suspend fun setVisited(id: Int, visited: Boolean, visitedAt: Long?, now: Long) {
        ensureRow(id, now); updateVisited(id, visited, if (visited) visitedAt else null, now)
    }

    @Transaction
    suspend fun setBlock(id: Int, block: String?, now: Long) {
        ensureRow(id, now); updateBlock(id, block, now)
    }

    @Transaction
    suspend fun setNote(id: Int, note: String?, now: Long) {
        ensureRow(id, now); updateNote(id, note, now)
    }

    // ---------- bulk block operations ----------

    /** Ward-scoped through the voter-id range of the ward ([VoterIds.range]). */
    @Query("UPDATE voter_user_data SET block = NULL, updated_at = :now WHERE block = :block AND voter_id BETWEEN :from AND :to")
    suspend fun clearBlock(block: String, from: Int, to: Int, now: Long): Int

    @Query(
        """
        INSERT OR IGNORE INTO voter_user_data (voter_id, updated_at)
        SELECT voter_id, :now FROM voters WHERE voter_id BETWEEN :from AND :to
        """
    )
    suspend fun ensureRowsInRange(from: Int, to: Int, now: Long)

    @Query("UPDATE voter_user_data SET block = :block, updated_at = :now WHERE voter_id BETWEEN :from AND :to")
    suspend fun updateBlockInRange(from: Int, to: Int, block: String?, now: Long): Int

    /** Assigns [block] (or unassigns when null) to every voter with a voter id in [from]..[to] (one ward). */
    @Transaction
    suspend fun setBlockForRange(from: Int, to: Int, block: String?, now: Long): Int {
        ensureRowsInRange(from, to, now)
        return updateBlockInRange(from, to, block, now)
    }

    // ---------- bulk updates for selected voters ----------

    @Query(
        """
        INSERT OR IGNORE INTO voter_user_data (voter_id, updated_at)
        SELECT voter_id, :now FROM voters WHERE voter_id IN (:ids)
        """
    )
    suspend fun ensureRows(ids: List<Int>, now: Long)

    @Query("UPDATE voter_user_data SET favourite = :favourite, updated_at = :now WHERE voter_id IN (:ids)")
    suspend fun updateFavourites(ids: List<Int>, favourite: Boolean, now: Long): Int

    /**
     * Visited on: keeps an existing visit time and records [now] only for voters not visited yet.
     * Visited off: clears the visit time. (SQLite evaluates SET expressions on the old row values.)
     */
    @Query(
        """
        UPDATE voter_user_data
        SET visited_at = CASE WHEN :visited = 0 THEN NULL WHEN visited = 1 THEN visited_at ELSE :now END,
            visited = :visited,
            updated_at = :now
        WHERE voter_id IN (:ids)
        """
    )
    suspend fun updateVisitedMany(ids: List<Int>, visited: Boolean, now: Long): Int

    @Query("UPDATE voter_user_data SET block = :block, updated_at = :now WHERE voter_id IN (:ids)")
    suspend fun updateBlocks(ids: List<Int>, block: String?, now: Long): Int

    @Transaction
    suspend fun setFavourites(ids: List<Int>, favourite: Boolean, now: Long): Int {
        ensureRows(ids, now); return updateFavourites(ids, favourite, now)
    }

    @Transaction
    suspend fun setVisitedMany(ids: List<Int>, visited: Boolean, now: Long): Int {
        ensureRows(ids, now); return updateVisitedMany(ids, visited, now)
    }

    @Transaction
    suspend fun setBlocks(ids: List<Int>, block: String?, now: Long): Int {
        ensureRows(ids, now); return updateBlocks(ids, block, now)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTags(links: List<VoterTagEntity>)

    @Query("DELETE FROM voter_tags WHERE tag_id = :tagId AND voter_id IN (:ids)")
    suspend fun detachTagFrom(ids: List<Int>, tagId: Long): Int

    @Query("SELECT COUNT(*) FROM voters WHERE voter_id IN (:ids)")
    suspend fun countExisting(ids: List<Int>): Int

    // ---------- tags ----------

    @Query("INSERT OR IGNORE INTO tags (name, created_at) VALUES (:name, :now)")
    suspend fun insertTagIfMissing(name: String, now: Long): Long

    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(vt.voter_id) AS usage
        FROM tags t LEFT JOIN voter_tags vt ON vt.tag_id = t.id AND vt.voter_id BETWEEN :from AND :to
        GROUP BY t.id ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeTagsWithCount(from: Int, to: Int): Flow<List<TagWithCount>>

    @Query("SELECT tag_id FROM voter_tags WHERE voter_id = :id")
    fun observeTagIdsFor(id: Int): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTag(link: VoterTagEntity)

    @Query("DELETE FROM voter_tags WHERE voter_id = :id AND tag_id = :tagId")
    suspend fun detachTag(id: Int, tagId: Long)

    // ---------- block labels (per ward) ----------

    @Query("SELECT * FROM block_labels WHERE ward_no = :ward")
    fun observeBlockLabels(ward: Int): Flow<List<BlockLabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBlockLabel(label: BlockLabelEntity)

    @Query("DELETE FROM block_labels WHERE ward_no = :ward AND block = :block")
    suspend fun deleteBlockLabel(ward: Int, block: String)

    @Query("SELECT * FROM block_labels ORDER BY ward_no, block")
    suspend fun allBlockLabels(): List<BlockLabelEntity>

    @Query("SELECT * FROM block_labels WHERE ward_no = :ward")
    suspend fun blockLabels(ward: Int): List<BlockLabelEntity>

    @Query("DELETE FROM block_labels")
    suspend fun clearBlockLabels()

    @Insert
    suspend fun insertBlockLabels(rows: List<BlockLabelEntity>)

    @Transaction
    suspend fun replaceBlockLabels(rows: List<BlockLabelEntity>) {
        clearBlockLabels()
        insertBlockLabels(rows)
    }

    // ---------- backup / restore ----------

    @Query("SELECT * FROM voter_user_data ORDER BY voter_id")
    suspend fun allUserData(): List<VoterUserDataEntity>

    @Query("SELECT * FROM voter_user_data WHERE voter_id IN (:ids)")
    suspend fun userDataIn(ids: List<Int>): List<VoterUserDataEntity>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM voter_tags ORDER BY voter_id, tag_id")
    suspend fun allVoterTags(): List<VoterTagEntity>

    @Query("DELETE FROM voter_user_data")
    suspend fun clearUserData()

    @Query("DELETE FROM voter_tags")
    suspend fun clearVoterTags()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Insert
    suspend fun insertUserData(rows: List<VoterUserDataEntity>)

    @Insert
    suspend fun insertTags(rows: List<TagEntity>)

    @Insert
    suspend fun insertVoterTags(rows: List<VoterTagEntity>)


    /** Replaces all user data atomically (used by restore). Either everything is replaced or nothing. */
    @Transaction
    suspend fun replaceAll(userData: List<VoterUserDataEntity>, tags: List<TagEntity>, links: List<VoterTagEntity>) {
        clearVoterTags()
        clearTags()
        clearUserData()
        userData.chunked(500).forEach { insertUserData(it) }
        insertTags(tags)
        links.chunked(500).forEach { insertVoterTags(it) }
    }
}
