package com.example.etikoppakavoterdirectory.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Read access to voters (combined with user markers) and dataset import. */
@Dao
interface VoterDao {

    /**
     * Directory query: free-text search combined with filters. All text parameters must already be
     * lower-cased and LIKE-escaped (escape character '\').
     *
     * @param ward null = all wards (global search), otherwise only that ward
     * @param hasQuery 0 = no text search
     * @param t1 .. t4 word patterns that must ALL match (any order, e.g. "challa saibabu" also finds
     *        "Saibabu Challa"; a single letter matches the start of a word, for initials). "%" = unused.
     * @param favouritesOnly 1 = only favourites
     * @param visited null = any, 1 = visited, 0 = not visited
     * @param block null = any, "A".."D", or "U" for unassigned
     * @param tagId null = any tag
     * @param reviewOnly 1 = only records that need review
     */
    @Query(
        """
        SELECT v.*,
               COALESCE(u.favourite, 0) AS favourite,
               COALESCE(u.visited, 0) AS visited,
               u.block AS block,
               CASE WHEN u.note IS NOT NULL AND u.note <> '' THEN 1 ELSE 0 END AS has_note,
               (SELECT COUNT(*) FROM voter_tags t WHERE t.voter_id = v.voter_id) AS tag_count
        FROM voters v
        LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE (:ward IS NULL OR v.ward_no = :ward)
          AND (:hasQuery = 0
               OR CAST(v.gp_serial_no AS TEXT) LIKE :serialPrefix || '%' ESCAPE '\'
               OR v.search_text LIKE '%' || :text || '%' ESCAPE '\'
               OR v.search_text LIKE '%' || :compactText || '%' ESCAPE '\'
               OR ((' ' || v.search_text) LIKE :t1 ESCAPE '\'
                   AND (' ' || v.search_text) LIKE :t2 ESCAPE '\'
                   AND (' ' || v.search_text) LIKE :t3 ESCAPE '\'
                   AND (' ' || v.search_text) LIKE :t4 ESCAPE '\'))
          AND (:favouritesOnly = 0 OR u.favourite = 1)
          AND (:visited IS NULL OR COALESCE(u.visited, 0) = :visited)
          AND (:block IS NULL OR (:block = 'U' AND u.block IS NULL) OR u.block = :block)
          AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM voter_tags vt WHERE vt.voter_id = v.voter_id AND vt.tag_id = :tagId))
          AND (:reviewOnly = 0 OR v.needs_review = 1)
        ORDER BY v.ward_no, v.gp_serial_no
        """
    )
    fun observeDirectory(
        ward: Int?,
        hasQuery: Int,
        serialPrefix: String,
        text: String,
        compactText: String,
        t1: String,
        t2: String,
        t3: String,
        t4: String,
        favouritesOnly: Int,
        visited: Int?,
        block: String?,
        tagId: Long?,
        reviewOnly: Int,
    ): Flow<List<VoterListItem>>

    @Query("SELECT * FROM voters WHERE voter_id = :id")
    fun observeVoter(id: Int): Flow<VoterEntity?>

    @Query("SELECT voter_id FROM voters WHERE (:ward IS NULL OR ward_no = :ward) ORDER BY ward_no, gp_serial_no")
    suspend fun allIds(ward: Int?): List<Int>

    @Query("SELECT * FROM voters WHERE ward_no = :ward ORDER BY gp_serial_no")
    suspend fun votersOfWard(ward: Int): List<VoterEntity>

    @Query("SELECT * FROM voters WHERE ward_no = :ward ORDER BY gp_serial_no")
    fun observeVotersOfWard(ward: Int): Flow<List<VoterEntity>>

    @Query("SELECT * FROM voters WHERE voter_id IN (:ids)")
    suspend fun votersIn(ids: List<Int>): List<VoterEntity>

    @Query(
        """
        SELECT vt.voter_id AS voter_id, t.name AS name
        FROM voter_tags vt JOIN tags t ON t.id = vt.tag_id
        WHERE vt.voter_id IN (:ids) ORDER BY t.name COLLATE NOCASE
        """
    )
    suspend fun tagNamesFor(ids: List<Int>): List<TagName>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN u.favourite = 1 THEN 1 ELSE 0 END), 0) AS favourites,
               COALESCE(SUM(CASE WHEN u.visited = 1 THEN 1 ELSE 0 END), 0) AS visited,
               COALESCE(SUM(CASE WHEN v.needs_review = 1 THEN 1 ELSE 0 END), 0) AS needs_review
        FROM voters v LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE (:ward IS NULL OR v.ward_no = :ward)
        """
    )
    fun observeStats(ward: Int?): Flow<DirectoryStats>

    @Query(
        """
        SELECT COALESCE(u.block, 'U') AS block,
               COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN u.visited = 1 THEN 1 ELSE 0 END), 0) AS visited
        FROM voters v LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE v.ward_no = :ward
        GROUP BY COALESCE(u.block, 'U')
        """
    )
    fun observeBlockCounts(ward: Int): Flow<List<BlockCount>>

    /** Voter ids of a ward in one block (or "U" = unassigned), in serial order. */
    @Query(
        """
        SELECT v.voter_id FROM voters v LEFT JOIN voter_user_data u ON u.voter_id = v.voter_id
        WHERE v.ward_no = :ward AND ((:block = 'U' AND u.block IS NULL) OR u.block = :block)
        ORDER BY v.gp_serial_no
        """
    )
    suspend fun idsInBlock(ward: Int, block: String): List<Int>

    @Query("SELECT ward_no, COUNT(*) AS total FROM voters GROUP BY ward_no ORDER BY ward_no")
    fun observeWardCounts(): Flow<List<WardCount>>

    @Query("SELECT MIN(gp_serial_no) FROM voters WHERE ward_no = :ward")
    suspend fun minSerial(ward: Int): Int?

    @Query("SELECT MAX(gp_serial_no) FROM voters WHERE ward_no = :ward")
    suspend fun maxSerial(ward: Int): Int?

    @Query("SELECT COUNT(*) FROM voters")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM voters WHERE ward_no = :ward")
    suspend fun countInWard(ward: Int): Int

    @Query("SELECT * FROM dataset_info WHERE ward_no = :ward")
    suspend fun datasetInfo(ward: Int): DatasetInfoEntity?

    @Query("SELECT * FROM dataset_info ORDER BY ward_no")
    suspend fun allDatasetInfo(): List<DatasetInfoEntity>

    @Query("SELECT * FROM dataset_info ORDER BY ward_no")
    fun observeDatasetInfo(): Flow<List<DatasetInfoEntity>>

    @Query("UPDATE dataset_info SET grouping_key = :key WHERE ward_no = :ward")
    suspend fun setGroupingKey(ward: Int, key: String?)

    @Upsert
    suspend fun upsertVoters(voters: List<VoterEntity>)

    @Upsert
    suspend fun upsertDatasetInfo(info: DatasetInfoEntity)

    @Query("DELETE FROM voters WHERE voter_id IN (:ids)")
    suspend fun deleteVoters(ids: List<Int>)

    /**
     * Inserts or updates the read-only voter records of one ward in one transaction. Ward-scoped:
     * re-importing one ward never removes voters of another ward. User data lives in other tables
     * and is never touched here, so favourites, visits, blocks, notes and tags survive.
     */
    @Transaction
    suspend fun importWard(ward: Int, voters: List<VoterEntity>, info: DatasetInfoEntity) {
        voters.chunked(500).forEach { upsertVoters(it) }
        val keep = voters.mapTo(HashSet()) { it.voterId }
        allIds(ward).filter { it !in keep }.chunked(500).forEach { deleteVoters(it) }
        upsertDatasetInfo(info)
    }
}
