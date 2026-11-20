package com.example.etikoppakavoterdirectory.data.dataset

import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import com.example.etikoppakavoterdirectory.data.db.VoterIds
import org.json.JSONArray
import org.json.JSONObject

/** Parsed contents of one ward's dataset (assets/voters_data.json or assets/wards/voters_ward_N.json). */
class ParsedDataset(
    val wardNo: Int,
    val generatedAt: String,
    val declaredCount: Int,
    val voters: List<VoterEntity>,
)

class DatasetException(message: String) : Exception(message)

/**
 * Reads a ward dataset produced by tools/build_dataset.py.
 * JSON null stays Kotlin null; strings are kept exactly as written (no trimming or correction).
 * Every voter gets the stable id [VoterIds.of] (ward + GP serial).
 */
object VoterJsonParser {

    private val compactRegex = Regex("[\\s.\\u200C\\u200D]+")

    /**
     * @param photoFolder sub-folder of assets/voter_photos holding this ward's photos ("" for the
     *        original Ward 11 dataset, "ward_N" for wards added later), so photo names never collide.
     */
    fun parse(json: String, photoFolder: String = ""): ParsedDataset {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw DatasetException("Bundled dataset is not valid JSON")
        }
        val meta = root.getJSONObject("meta")
        val array = root.getJSONArray("voters")
        val voters = ArrayList<VoterEntity>(array.length())
        for (i in 0 until array.length()) {
            voters += parseVoter(array.getJSONObject(i), photoFolder)
        }
        val ward = if (meta.has("ward_no") && !meta.isNull("ward_no")) meta.getInt("ward_no") else voters.firstOrNull()?.wardNo
            ?: throw DatasetException("Dataset does not name its ward")
        val dataset = ParsedDataset(
            wardNo = ward,
            generatedAt = meta.getString("generated_at"),
            declaredCount = meta.getInt("record_count"),
            voters = voters,
        )
        validate(dataset)
        return dataset
    }

    /** Structural checks only - values themselves are never altered. */
    fun validate(dataset: ParsedDataset) {
        if (dataset.voters.size != dataset.declaredCount) {
            throw DatasetException("Dataset declares ${dataset.declaredCount} records but contains ${dataset.voters.size}")
        }
        if (dataset.wardNo !in 1..VoterIds.MAX_WARD) throw DatasetException("Dataset has an invalid ward number")
        if (dataset.voters.any { it.wardNo != dataset.wardNo }) throw DatasetException("Dataset mixes voters of different wards")
        val serials = dataset.voters.map { it.gpSerialNo }
        if (serials.toSet().size != serials.size) throw DatasetException("Dataset contains duplicate GP serial numbers")
        if (dataset.voters.any { it.photoFilename != null && !isSafeFileName(it.photoFilename) }) {
            throw DatasetException("Dataset contains an invalid photo file name")
        }
    }

    private fun isSafeFileName(name: String) = Regex("(ward_[0-9]{1,2}/)?[0-9A-Za-z_-]+\\.(jpg|jpeg|png)").matches(name)

    private fun parseVoter(o: JSONObject, photoFolder: String): VoterEntity {
        val nameEn = o.str("name_en")
        val nameTe = o.str("name_te")
        val relEn = o.str("relative_name_en")
        val relTe = o.str("relative_name_te")
        val houseNo = o.str("house_no")
        val epicNo = o.str("epic_no")
        val serial = o.getInt("gp_serial_no")
        val ward = o.getInt("ward_no")
        if (ward !in 1..VoterIds.MAX_WARD || serial !in 1 until VoterIds.FACTOR) {
            throw DatasetException("Dataset contains an invalid ward or serial number")
        }
        val photo = o.str("photo_filename")?.let { if (photoFolder.isEmpty()) it else "$photoFolder/$it" }
        return VoterEntity(
            voterId = VoterIds.of(ward, serial),
            gpSerialNo = serial,
            wardNo = ward,
            nameEn = nameEn,
            nameTe = nameTe,
            relationship = o.str("relationship"),
            relativeNameEn = relEn,
            relativeNameTe = relTe,
            age = o.int("age"),
            gender = o.str("gender"),
            houseNo = houseNo,
            assemblySerial = o.str("assembly_serial"),
            epicNo = epicNo,
            photoFilename = photo,
            photoWidthPx = o.int("photo_width_px"),
            photoHeightPx = o.int("photo_height_px"),
            pdfPage = o.int("pdf_page"),
            pdfRelationLabel = o.str("pdf_relation_label"),
            excelReviewStatus = o.str("excel_review_status"),
            needsReview = o.optBoolean("needs_review", false),
            reviewIssueCodes = o.optJSONArray("review_issue_codes").toStringList().joinToString(","),
            searchText = buildSearchText(listOf(nameEn, nameTe, relEn, relTe, houseNo, epicNo)),
        )
    }

    /** Lower-cased text joined with " | ", followed by a space/dot/zero-width-free copy. */
    fun buildSearchText(parts: List<String?>): String {
        val joined = parts.filterNotNull().joinToString(" | ").lowercase()
        return joined + " || " + compact(joined)
    }

    fun compact(text: String): String = text.replace(compactRegex, "")

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }
}
