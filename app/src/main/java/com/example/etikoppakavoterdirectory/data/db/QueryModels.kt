package com.example.etikoppakavoterdirectory.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A row in the directory list: the voter plus the user's own status markers. */
data class VoterListItem(
    @Embedded val voter: VoterEntity,
    @ColumnInfo(name = "favourite") val favourite: Boolean,
    @ColumnInfo(name = "visited") val visited: Boolean,
    @ColumnInfo(name = "block") val block: String?,
    @ColumnInfo(name = "has_note") val hasNote: Boolean,
    @ColumnInfo(name = "tag_count") val tagCount: Int,
)

data class BlockCount(
    @ColumnInfo(name = "block") val block: String,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "visited") val visited: Int,
)

data class TagWithCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "usage") val usage: Int,
)

/** One household card: counts come straight from the members' own markers. */
data class HouseholdSummary(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "house_no") val houseNo: String?,
    @ColumnInfo(name = "members") val members: Int,
    @ColumnInfo(name = "visited") val visited: Int,
    @ColumnInfo(name = "favourites") val favourites: Int,
    @ColumnInfo(name = "first_serial") val firstSerial: Int?,
    @ColumnInfo(name = "ward_no") val wardNo: Int = 0,
    @ColumnInfo(name = "locked") val locked: Boolean = true,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean = false,
    @ColumnInfo(name = "review_note") val reviewNote: String? = null,
)

/** Member names of a household, used for household search (family, member, initials, house). */
data class HouseholdMemberName(
    @ColumnInfo(name = "household_id") val householdId: Long,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "name_te") val nameTe: String?,
    @ColumnInfo(name = "house_no") val houseNo: String?,
    @ColumnInfo(name = "relative_name_en") val relativeNameEn: String? = null,
    @ColumnInfo(name = "relative_name_te") val relativeNameTe: String? = null,
    @ColumnInfo(name = "gp_serial_no") val gpSerialNo: Int = 0,
)

data class DirectoryStats(
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "favourites") val favourites: Int,
    @ColumnInfo(name = "visited") val visited: Int,
    @ColumnInfo(name = "needs_review") val needsReview: Int,
)

/** Voters and households per ward for the home screen. */
data class WardCount(
    @ColumnInfo(name = "ward_no") val wardNo: Int,
    @ColumnInfo(name = "total") val total: Int,
)

data class TagName(
    @ColumnInfo(name = "voter_id") val voterId: Int,
    @ColumnInfo(name = "name") val name: String,
)

/** Household coverage of one ward: all voters, and distinct voters in an existing household of that ward. */
data class HouseholdCoverage(
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "assigned") val assigned: Int,
) {
    val unassigned: Int get() = total - assigned
}
