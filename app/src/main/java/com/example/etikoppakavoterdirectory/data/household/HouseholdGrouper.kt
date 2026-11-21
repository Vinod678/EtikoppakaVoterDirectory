package com.example.etikoppakavoterdirectory.data.household

import com.example.etikoppakavoterdirectory.data.db.VoterEntity
import java.util.Locale

/**
 * Fully automatic household grouping for ONE ward.
 *
 * Input: the voters of a ward whose household assignment the user has not set by hand (manual
 * corrections are excluded by the caller and are never touched). Output: a deterministic list of
 * households - the same input always gives the same groups, keys and names, so results are stable
 * across app restarts.
 *
 * A household here is an extended family: everyone in the ward with the same established surname,
 * whatever their house numbers. Rules, in priority order:
 *  1. Family surname - recognised at the end or the start of the name ("Saibabu Challa",
 *     "Alugolu Varahalu"), with spelling normalisation (case, punctuation, spacing, doubled letters,
 *     th/t, ee/i, oo/u, w/v ...) and the Telugu surname as a second spelling. Same surname = same
 *     household. Common given names ("Rao", "Babu", "Lakshmi" ...) are never treated as surnames.
 *     A voter printed with another surname than the father / husband / mother at the same house (a
 *     wife printed with her maiden name) belongs to that relative's family.
 *  2. Initials ("C. Saibabu") join a family when a printed relationship or the house confirms it.
 *  3. Fallback for voters without an identifiable surname: a printed Father / Husband / Mother
 *     name that matches a voter at the same house (with age / gender checks).
 * A house number alone never links anyone; voters are never grouped across wards (the caller
 * passes one ward). The heading is the family's surname; a one-voter household without a surname is
 * named after the voter, and only several voters without a common surname get "Household - House x".
 */
object HouseholdGrouper {

    /** Bump when the rules change: wards are re-grouped once (manual corrections are kept). */
    const val VERSION = "g6"

    data class Group(
        /** Stable key: the smallest voter id of the group. */
        val key: String,
        /** Voter ids in GP serial order. */
        val members: List<Int>,
        /** Reliable family surname for the title (display spelling), or null for a neutral title. */
        val familyName: String?,
        val houseNo: String?,
        val needsReview: Boolean,
        val reviewNote: String?,
    ) {
        val title: String get() = title(familyName, houseNo)
    }

    fun title(familyName: String?, houseNo: String?): String = when {
        familyName != null -> familyName
        houseNo != null -> "Household — House $houseNo"
        else -> "Household"
    }

    private class P(val v: VoterEntity) {
        val id = v.voterId
        val words = tokens(v.nameEn)
        val initials = words.filter { it.length == 1 }
        val given = words.filter { it.length > 1 }
        val phon = given.map(::phonetic)
        val teSurname: String? = teluguTokens(v.nameTe).let { if (it.size >= 2) it.last() else null }
        val rel = tokens(v.relativeNameEn).filter { it.length > 1 }.map(::phonetic)
        val relKind = v.relationship?.trim()?.lowercase(Locale.ROOT)
        val house = v.houseNo?.trim()?.takeIf { it.isNotEmpty() }
        val houseBase = house?.substringBefore('/')?.trim()
    }

    /** Surname chosen per voter: end of name by default, start of name when that is the known surname. */
    private class Person(
        val p: P,
        val surname: String?,
        val surnameDisplay: String?,
        val firstNames: Set<String>,
        val teSurname: String? = p.teSurname,
    )

    fun group(voters: List<VoterEntity>): List<Group> {
        if (voters.isEmpty()) return emptyList()
        val ps = voters.sortedWith(compareBy({ it.gpSerialNo }, { it.voterId })).map(::P)

        // Ward-wide surname frequency (last word of multi-word names) to recognise surname-first names.
        val lastFreq = HashMap<String, Int>()
        ps.forEach { p -> if (p.phon.size >= 2) p.phon.last().let { if (it !in NOT_SURNAMES) lastFreq.merge(it, 1, Int::plus) } }
        // How often each word starts a multi-word name: a word printed first far more often than last
        // is a given name ("Chiranjeevi"), even if one card prints it last ("Pedapalli Chiranjeevi").
        val firstFreq = HashMap<String, Int>()
        ps.forEach { p -> if (p.phon.size >= 2) firstFreq.merge(p.phon.first(), 1, Int::plus) }
        // A relative printed with one word only ("Father: Govindu") is a given name.
        val soloRelative = HashMap<String, Int>()
        ps.forEach { p -> if (p.rel.size == 1 && tokens(p.v.relativeNameEn).size == 1) soloRelative.merge(p.rel[0], 1, Int::plus) }

        val chosen = ps.map { p ->
            var idx = -1
            if (p.phon.size >= 2) {
                val last = p.phon.last(); val first = p.phon.first()
                // Frequencies of OTHER voters (this voter's own words are not counted).
                fun asLast(w: String) = (lastFreq[w] ?: 0) - (if (w == last) 1 else 0)
                fun asFirst(w: String) = (firstFreq[w] ?: 0) - (if (w == first) 1 else 0)
                fun given(w: String) = (asFirst(w) >= 2 && asFirst(w) > 2 * asLast(w)) ||
                    ((soloRelative[w] ?: 0) >= 1 && (soloRelative[w] ?: 0) >= asLast(w))
                val okLast = last !in NOT_SURNAMES; val okFirst = first !in NOT_SURNAMES
                idx = when {
                    // The family name is usually printed in the relative's name too ("Father: Apparao Eerla",
                    // "Nagireddi Bhargava" - father "Nagireddi Rama Krishna").
                    okLast && last in p.rel && first !in p.rel -> p.phon.lastIndex
                    okFirst && first in p.rel && last !in p.rel && asLast(first) >= asLast(last) && !given(first) -> 0
                    okLast && !given(last) && (asLast(last) >= asLast(first) || given(first) || !okFirst) -> p.phon.lastIndex
                    okFirst && !given(first) -> 0
                    okLast -> p.phon.lastIndex
                    okFirst -> 0
                    else -> -1
                }
            }
            val sn = if (idx >= 0) p.phon[idx] else null
            val display = if (idx >= 0) displayWord(p.given[idx]) else null
            val firsts = p.phon.filterIndexed { i, _ -> i != idx }.toSet()
            Person(p, sn, display, firsts)
        }

        // A voter whose father / husband / mother at the same house is printed with another family name
        // (a wife printed with her maiden name: "Pola Vijaya Durga Karri", husband "Nageswara Rao Kalla")
        // belongs to that relative's family; her own surname must not join the two families. Only a
        // surname that at least two voters of the ward carry counts ("Father: Pothu Raju" is a given name).
        val surnameCount = chosen.mapNotNull { it.surname }.groupingBy { it }.eachCount()
        val byHouse = chosen.filter { it.p.house != null }.groupBy { it.p.house }
        val people = chosen.map { a ->
            if (a.surname == null) return@map a
            val q = byHouse[a.p.house].orEmpty().firstOrNull { q ->
                q !== a && q.surname != null && q.surname != a.surname && (surnameCount[q.surname] ?: 0) >= 2 &&
                    q.surname in a.p.rel && relationLink(a, q) && !sameSurname(a, q)
            } ?: return@map a
            Person(a.p, q.surname, q.surnameDisplay, a.p.phon.filter { it != q.surname }.toSet(), q.teSurname)
        }

        val n = people.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb) }

        for (i in 0 until n) for (j in i + 1 until n) {
            val a = people[i]; val b = people[j]
            val sameHouse = a.p.house != null && a.p.house == b.p.house
            // 1. Surname first: the same established family name (incl. spelling variants) groups
            //    voters of the ward together, whatever their house numbers.
            val surname = sameSurname(a, b)
            if (surname) { union(i, j); continue }
            // A relationship counts only when the surnames do not contradict it.
            val related = (relationLink(a, b) && compatible(a, b, false)) ||
                (relationLink(b, a) && compatible(b, a, false)) ||
                (siblings(a, b) && compatible(a, b, false))
            // 2. Initials ("C. Saibabu") resolved through a relationship or the same house.
            // 3. Fallback for voters without a known surname: relationship + same house.
            if ((initialMatch(a, b) && (related || sameHouse)) || (related && sameHouse)) union(i, j)
        }

        val comps = (0 until n).groupBy(::find).values
        return comps.map { idx ->
            val members = idx.map { people[it] }
            // Heading: the surname of the group. Spelling variants matched as one surname
            // ("Gonnabattula" / "Gonna Battula") count as one name, written in the most common spelling.
            val raw = members.mapNotNull { it.surname }.groupingBy { it }.eachCount()
            val keyOf = HashMap<Person, String>()
            members.forEach { p ->
                if (p.surname == null) return@forEach
                keyOf[p] = members.filter { q -> q.surname != null && (q.surname == p.surname || sameSurname(p, q)) }
                    .map { it.surname!! }.distinct()
                    .sortedWith(compareByDescending<String> { raw[it] ?: 0 }.thenByDescending { it.length }.thenBy { it }).first()
            }
            val counts = keyOf.values.groupingBy { it }.eachCount()
            val top = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()
            val tie = top != null && counts.values.count { it == top.value } > 1
            // Heading: the family's surname, even when it is printed only once in the ward. A household of
            // one voter without a surname is named after that voter; only several voters without one
            // common surname get the neutral "Household - House x" title.
            val family = if (top != null && !tie) {
                members.filter { it.surname == top.key }.mapNotNull { it.surnameDisplay }
                    .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .first().key
            } else if (members.size == 1) {
                members[0].p.v.nameEn?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
            } else null

            val house = members.mapNotNull { it.p.house }.groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { naturalKey(it.key) }).firstOrNull()?.key
            val ids = members.map { it.p.id }
            Group(
                key = "auto:${ids.min()}",
                members = ids,
                familyName = family,
                houseNo = house,
                needsReview = false,
                reviewNote = null,
            )
        }.sortedBy { it.members.first().let { id -> people.indexOfFirst { p -> p.p.id == id } } }
    }

    // ------------------------------------------------------------------------------------------
    // Evidence
    // ------------------------------------------------------------------------------------------

    private fun sameSurname(a: Person, b: Person): Boolean {
        val sa = a.surname ?: return false
        val sb = b.surname ?: return false
        if (sa == sb) return true
        // Spelling variants in English, e.g. "Gonnabattula" / "Gonna Battula" ...
        val ca = compactSurname(a); val cb = compactSurname(b)
        if (ca.length >= 6 && (ca == sb || ca == cb)) return true
        if (cb.length >= 6 && cb == sa) return true
        // ... or the same Telugu surname with an English spelling that differs by one letter.
        val ta = a.teSurname; val tb = b.teSurname
        return ta != null && ta == tb && closeSpelling(sa, sb)
    }

    /** "Gonna Battula" and "Gonnabattula" both become "gonabatula". */
    private fun compactSurname(p: Person): String {
        val w = p.p.phon
        val s = p.surname.orEmpty()
        if (w.size < 3 || s != w.last()) return s
        return phonetic(w[w.size - 2] + w.last())
    }

    /** "C. Saibabu" and "Saibabu Challa": the initial is the first letter of the surname and a given name agrees. */
    private fun initialMatch(a: Person, b: Person): Boolean {
        fun one(x: Person, y: Person): Boolean {
            if (x.p.initials.isEmpty() || y.surname == null) return false
            if (x.p.initials.none { y.surname.startsWith(it) }) return false
            val given = x.p.phon.filter { it.length >= 4 }
            return given.isNotEmpty() && given.all { it in y.firstNames }
        }
        return one(a, b) || one(b, a)
    }

    /**
     * [a]'s relative may be [b] as far as family names go: the same surname, one of them has none,
     * or [a]'s relative is printed with [b]'s surname. (In this roll a wife carries her husband's
     * surname, so "Husband: Satyanarayana" written for a Pedapati is not a Yekula voter.)
     */
    private fun compatible(a: Person, b: Person, sameSurname: Boolean): Boolean =
        sameSurname || a.surname == null || b.surname == null || (b.surname in a.p.rel)

    /** [a] names [b] as father / husband / mother (with an age and gender sanity check). */
    private fun relationLink(a: Person, b: Person): Boolean {
        if (!nameMatches(a.p.rel, b.p.phon)) return false
        val ageA = a.p.v.age; val ageB = b.p.v.age
        val genderB = b.p.v.gender?.lowercase(Locale.ROOT)
        return when (a.p.relKind) {
            "father" -> genderB != "female" && (ageA == null || ageB == null || ageB >= ageA + 12)
            "mother" -> genderB != "male" && (ageA == null || ageB == null || ageB >= ageA + 12)
            "husband" -> genderB != "female" && (ageA == null || ageB == null || kotlin.math.abs(ageA - ageB) <= 30)
            else -> true
        }
    }

    /** Both list the same father or the same mother (children of one parent). Two wives listing the
     *  same husband name are NOT linked - that is far more often two different men with one name. */
    private fun siblings(a: Person, b: Person): Boolean {
        val parentKinds = setOf("father", "mother")
        if (a.p.relKind !in parentKinds || a.p.relKind != b.p.relKind) return false
        if (a.p.rel.isEmpty() || a.p.rel.sumOf { it.length } < MIN_LETTERS) return false
        return a.p.rel == b.p.rel || a.p.rel.joinToString("") == b.p.rel.joinToString("")
    }

    /** Every word of [relative] is in [name], or the relative equals a run of consecutive name words
     *  ("Venkataramana" = "Venkata Ramana"). Very short fragments (< 5 letters) never match. */
    internal fun nameMatches(relative: List<String>, name: List<String>): Boolean {
        if (relative.isEmpty() || name.isEmpty()) return false
        if (relative.sumOf { it.length } < MIN_LETTERS) return false
        if (name.containsAll(relative)) return true
        val r = relative.joinToString("")
        for (s in name.indices) {
            val sb = StringBuilder()
            for (e in s until name.size) {
                sb.append(name[e])
                if (sb.length > r.length) break
                if (sb.length == r.length && sb.toString() == r) return true
            }
        }
        // Relative printed with a surname the voter also has, in any word order.
        val rs = relative.toSet()
        return rs.size >= 2 && name.toSet().containsAll(rs)
    }

    // ------------------------------------------------------------------------------------------
    // Normalisation
    // ------------------------------------------------------------------------------------------

    /** Lower-case words; dots, commas and other punctuation separate words ("D.V." -> "d", "v"). */
    internal fun tokens(s: String?): List<String> {
        if (s.isNullOrBlank() || s.equals("none", true) || s.equals("null", true)) return emptyList()
        return s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }
    }

    internal fun teluguTokens(s: String?): List<String> =
        s.orEmpty().replace("‌", "").replace("‍", "").split(Regex("[\\s.]+")).filter { it.isNotEmpty() }

    /** Folds common English spelling variations of Telugu names. */
    internal fun phonetic(word: String): String {
        var w = word.lowercase(Locale.ROOT)
        listOf("th" to "t", "dh" to "d", "bh" to "b", "kh" to "k", "ph" to "p", "gh" to "g", "sh" to "s",
            "ee" to "i", "oo" to "u", "ou" to "u", "w" to "v", "z" to "j", "q" to "k", "x" to "ks", "ck" to "k",
        ).forEach { (a, b) -> w = w.replace(a, b) }
        val sb = StringBuilder()
        w.forEach { c -> if (sb.isEmpty() || sb.last() != c) sb.append(c) } // doubled letters: "challa" = "chala"
        return sb.toString()
    }

    private fun closeSpelling(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1 || minOf(a.length, b.length) < 4) return false
        // Levenshtein distance <= 1
        val prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diag = prev[0]; prev[0] = i
            for (j in 1..b.length) {
                val t = prev[j]
                prev[j] = minOf(prev[j] + 1, prev[j - 1] + 1, diag + if (a[i - 1] == b[j - 1]) 0 else 1)
                diag = t
            }
        }
        return prev[b.length] <= 1
    }

    private fun displayWord(w: String) = w.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** "8-139" < "8-150" < "8-150/1" < "9-2" < "10-1": compares the numeric parts as numbers. */
    fun naturalKey(house: String?): String =
        house.orEmpty().trim().lowercase(Locale.ROOT).replace(Regex("\\d+")) { it.value.padStart(8, '0') }

    /** Given-name words that end many names but are not family names. */
    private val NOT_SURNAMES = setOf(
        "rao", "ravu", "babu", "kumar", "kumari", "devi", "lakshmi", "laksmi", "laxmi", "murti", "murty", "murthy",
        "raju", "rani", "prasad", "prasada", "svami", "swamy", "amma", "naidu", "nayudu", "reddi", "reddy", "sai",
        "sri", "venkata", "satya", "naga", "surya", "rama", "ramana", "krishna", "krisna", "narayana", "devee", "siva", "sambasiva",
    ).map(::phonetic).toSet()

    private const val MIN_LETTERS = 5
}
