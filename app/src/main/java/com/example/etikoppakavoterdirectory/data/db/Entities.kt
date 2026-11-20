package com.example.etikoppakavoterdirectory.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stable voter identity across all wards.
 *
 * GP serial numbers and house numbers are NOT assumed to be unique across wards, so every voter is
 * identified by `voter_id = ward_no * 100000 + gp_serial_no`. The value is deterministic: the same
 * voter always gets the same id, on every import and every device, which keeps favourites, visits,
 * notes, tags and household assignments attached to the right person and the right ward.
 */
object VoterIds {
    const val FACTOR = 100_000
    const val MAX_WARD = 14
    /** The only ward that existed before database version 4 (app 2.x). */
    const val LEGACY_WARD = 11

    fun of(wardNo: Int, gpSerialNo: Int): Int {
        require(wardNo in 1..MAX_WARD) { "Invalid ward" }
        require(gpSerialNo in 1 until FACTOR) { "Invalid serial" }
        return wardNo * FACTOR + gpSerialNo
    }

    fun wardOf(voterId: Int) = voterId / FACTOR
    fun serialOf(voterId: Int) = voterId % FACTOR
    fun range(wardNo: Int) = (wardNo * FACTOR + 1) until ((wardNo + 1) * FACTOR)
}

/**
 * One voter from a bundled dataset. Read-only for the user.
 *
 * Nullable columns stay null when the source has no value - nothing is guessed, normalised or
 * filled in. Name / relationship columns come verbatim from the Excel file; age, gender, house
 * number, assembly serial and EPIC number come from the PDF voter cards.
 */
@Entity(
    tableName = "voters",
    indices = [
        Index(value = ["house_no"]),
        Index(value = ["ward_no", "gp_serial_no"], unique = true),
    ],
)
data class VoterEntity(
    @PrimaryKey @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "gp_serial_no") val gpSerialNo: Int,
    @ColumnInfo(name = "ward_no") val wardNo: Int,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "name_te") val nameTe: String?,
    @ColumnInfo(name = "relationship") val relationship: String?,
    @ColumnInfo(name = "relative_name_en") val relativeNameEn: String?,
    @ColumnInfo(name = "relative_name_te") val relativeNameTe: String?,
    @ColumnInfo(name = "age") val age: Int?,
    @ColumnInfo(name = "gender") val gender: String?,
    @ColumnInfo(name = "house_no") val houseNo: String?,
    @ColumnInfo(name = "assembly_serial") val assemblySerial: String?,
    @ColumnInfo(name = "epic_no") val epicNo: String?,
    /** File name inside assets/voter_photos (may contain one "ward_N/" folder for later wards). */
    @ColumnInfo(name = "photo_filename") val photoFilename: String?,
    @ColumnInfo(name = "photo_width_px") val photoWidthPx: Int?,
    @ColumnInfo(name = "photo_height_px") val photoHeightPx: Int?,
    @ColumnInfo(name = "pdf_page") val pdfPage: Int?,
    @ColumnInfo(name = "pdf_relation_label") val pdfRelationLabel: String?,
    @ColumnInfo(name = "excel_review_status") val excelReviewStatus: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
    /** Issue codes joined with ",". Empty string = no issues. */
    @ColumnInfo(name = "review_issue_codes") val reviewIssueCodes: String,
    /** Derived search helper only (lower-cased names, house no., EPIC + a space-free copy). */
    @ColumnInfo(name = "search_text") val searchText: String,
) {
    val issueCodes: List<String>
        get() = if (reviewIssueCodes.isBlank()) emptyList() else reviewIssueCodes.split(",")
}

/** Which bundled dataset version has been imported, one row per ward. */
@Entity(tableName = "dataset_info")
data class DatasetInfoEntity(
    @PrimaryKey @ColumnInfo(name = "ward_no") val wardNo: Int,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "record_count") val recordCount: Int,
    @ColumnInfo(name = "needs_review_count") val needsReviewCount: Int,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    /** Dataset + algorithm version the automatic households were last generated for (null = never). */
    @ColumnInfo(name = "grouping_key") val groupingKey: String? = null,
)

/**
 * The user's own, editable information about a voter. Kept separate from [VoterEntity] so that
 * re-importing a bundled dataset can never overwrite it. Keyed by [VoterIds] voter id.
 */
@Entity(tableName = "voter_user_data")
data class VoterUserDataEntity(
    @PrimaryKey @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "favourite", defaultValue = "0") val favourite: Boolean = false,
    @ColumnInfo(name = "visited", defaultValue = "0") val visited: Boolean = false,
    /** Epoch millis of the visit, or null when visited without a recorded time. */
    @ColumnInfo(name = "visited_at") val visitedAt: Long? = null,
    /** "A", "B", "C", "D" or null (= unassigned). */
    @ColumnInfo(name = "block") val block: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

/** A neutral, user-defined organisational tag (e.g. "Needs follow-up"). Tag names are shared by all wards. */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = 0,
)

@Entity(
    tableName = "voter_tags",
    primaryKeys = ["voter_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class VoterTagEntity(
    @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

object Blocks {
    const val UNASSIGNED = "U"
    val ALL = listOf("A", "B", "C", "D")
    fun isValid(block: String?) = block == null || block in ALL
}

/**
 * A household in one ward. Created automatically (see HouseholdGrouper) or by the user.
 *
 * [locked] = created or edited by the user. Locked households are never changed or removed by
 * automatic grouping. [needsReview] = the automatic match was not fully certain.
 */
@Entity(tableName = "households", indices = [Index(value = ["ward_no"])])
data class HouseholdEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "ward_no") val wardNo: Int,
    @ColumnInfo(name = "name") val name: String,
    /** House number shown on the card (most common among members; may be null). */
    @ColumnInfo(name = "house_no") val houseNo: String?,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** Stable key of an automatic household (null for user-created ones). */
    @ColumnInfo(name = "auto_key") val autoKey: String? = null,
    @ColumnInfo(name = "locked", defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
    /** Short, non-personal explanation of why an automatic household needs review. */
    @ColumnInfo(name = "review_note") val reviewNote: String? = null,
)

/** Membership: each voter belongs to at most one household (primary key = voter id). */
@Entity(
    tableName = "household_members",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["household_id"])],
)
data class HouseholdMemberEntity(
    @PrimaryKey @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "household_id") val householdId: Long,
)

/**
 * A voter whose household assignment was set by the user (including "in no household"). Automatic
 * grouping never moves, adds or removes a locked voter.
 */
@Entity(tableName = "household_locks")
data class HouseholdLockEntity(
    @PrimaryKey @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "locked_at", defaultValue = "0") val lockedAt: Long = 0,
)

/** Optional user label for a block of one ward, e.g. "Main Street" for Block A of Ward 11. */
@Entity(tableName = "block_labels", primaryKeys = ["ward_no", "block"])
data class BlockLabelEntity(
    @ColumnInfo(name = "ward_no") val wardNo: Int,
    /** "A".."D" */
    @ColumnInfo(name = "block") val block: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

/** Neutral organisational tags created once (on a new install or on upgrade to database version 3). */
object DefaultTags {
    val NAMES = listOf(
        "Out of village",
        "Out of state",
        "Abroad",
        "Need to call",
        "Follow-up needed",
        "Contacted",
        "Unable to contact",
        "Address verification",
        "Details to verify",
        "Reported deceased",
    )
}
