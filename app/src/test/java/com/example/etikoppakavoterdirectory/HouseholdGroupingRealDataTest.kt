package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.data.dataset.VoterJsonParser
import com.example.etikoppakavoterdirectory.data.household.HouseholdGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The automatic household rules on the bundled ward data (plain JVM test; runs from the app module directory). */
class HouseholdGroupingRealDataTest {

    private class Ward(n: Int) {
        // Ward 11 is the original bundled dataset (voters_data.json).
        private val path = if (n == 11) "src/main/assets/voters_data.json" else "src/main/assets/wards/voters_ward_$n.json"
        val voters = VoterJsonParser.parse(File(path).readText(Charsets.UTF_8), "ward_$n").voters
        val groups = HouseholdGrouper.group(voters)
        private val idOf = voters.associate { it.gpSerialNo to it.voterId }
        fun groupOf(serial: Int) = groups.single { idOf.getValue(serial) in it.members }
        fun serials(g: HouseholdGrouper.Group) = g.members.map { id -> voters.single { it.voterId == id }.gpSerialNo }.sorted()
    }

    @Test
    fun givenNamePrintedFirst_isNotTakenAsTheFamilyName() {
        // Ward 9: "Chiranjeevi Bheemaraju", "Chiranjeevi Pendyala", "Chiranjeevi Eerla" are three different families,
        // although one card prints "Pedapalli Chiranjeevi" (surname first).
        val w = Ward(9)
        val titles = listOf(3029, 3178, 3206).map { w.groupOf(it).title }
        assertEquals(listOf("Bheemaraju", "Pendyala", "Eerla"), titles)
        assertNotEquals(w.groupOf(3029).key, w.groupOf(3178).key)
        assertNotEquals(w.groupOf(3178).key, w.groupOf(3206).key)
        // Two different "Bhavani"s stay apart as well.
        assertNotEquals(w.groupOf(3132).key, w.groupOf(3233).key)
    }

    @Test
    fun ward2Families_withCorrectedSurnames_areOneHouseholdEach() {
        val w = Ward(2)
        assertEquals("Maganti", w.groupOf(582).title)
        assertEquals(listOf(582, 585, 586), w.serials(w.groupOf(582)))
        assertEquals("Genji", w.groupOf(559).title)
        assertEquals(listOf(559, 560, 561), w.serials(w.groupOf(559)))
        assertEquals("Burre", w.groupOf(728).title) // "Burre Swamy": surname printed first
        assertEquals(listOf(680, 681, 728, 731, 732, 733, 734, 735, 736), w.serials(w.groupOf(728)))
        // "Naripireddi Srinu" and "Srinu Badireddi" are not one "Srinu" family.
        assertNotEquals(w.groupOf(625).key, w.groupOf(665).key)
        assertEquals(listOf(576, 578, 579, 581), w.serials(w.groupOf(576)))
        assertEquals("Veeramallu", w.groupOf(576).title)
        // A surname printed only once in the ward still names the household (never "Household - House x").
        assertEquals("Thala", w.groupOf(372).title)
        assertEquals("Katakam", w.groupOf(382).title)
        assertEquals("Nagireddi", w.groupOf(395).title) // surname first; father "Nagireddi Rama Krishna"
        assertEquals("Gude", w.groupOf(471).title)       // not the given name "Govindu"
        assertEquals("Kothwala", w.groupOf(467).title)
        assertEquals("Panchadarla", w.groupOf(721).title)
        assertTrue(w.groups.none { it.title.startsWith("Household") })
        // House 2-27: Lakshmi Buddireddi and her son Srinu (once printed "Badireddi").
        assertEquals("Buddireddi", w.groupOf(625).title)
        assertEquals(w.groupOf(624).key, w.groupOf(625).key)
    }

    @Test
    fun wifePrintedWithHerMaidenName_joinsHerHusbandsFamily_withoutMergingTheTwoFamilies() {
        val w = Ward(2)
        // House 1-131/1: "Pola Vijaya Durga Karri" (husband Nageswara Rao Kalla) and "Mohana NagaSri Doddi"
        // (husband Mohana Rao Kalla) are Kallas; the Karri family stays a household of its own.
        val kalla = w.groupOf(436)
        assertEquals("Kalla", kalla.title)
        assertEquals(listOf(434, 436, 437, 438, 439, 449, 450), w.serials(kalla))
        assertEquals("Karri", w.groupOf(482).title)
        assertEquals(listOf(435, 441, 444, 478, 479, 481, 482, 485, 570, 700), w.serials(w.groupOf(482)))
        assertEquals("Seeram", w.groupOf(421).title)
        assertEquals(w.groupOf(421).key, w.groupOf(422).key)
        // Ward 6: "Father: Pothu Raju" is a given name, so Paidayya Gonthumurthy stays a Gonthumurthy.
        val w6 = Ward(6)
        assertEquals("Gonthumurthy", w6.groupOf(2103).title)
        assertEquals(w6.groupOf(2101).key, w6.groupOf(2103).key)
        assertEquals("Medeti", w6.groupOf(2113).title)
    }

    @Test
    fun ward11LankaAndMedeti_spellingsConfirmedByTheUser() {
        val w = Ward(11)
        assertEquals("Lanka", w.groupOf(3786).title)
        assertEquals(w.groupOf(3785).key, w.groupOf(3786).key)
        assertEquals(w.groupOf(3786).key, w.groupOf(3787).key)
        assertEquals("Medeti", w.groupOf(4018).title)
        assertEquals(w.groupOf(4018).key, w.groupOf(4019).key)
        assertTrue(w.voters.none { "Lamka" in (it.nameEn ?: "") || "Madetee" in (it.nameEn ?: "") })
    }

    @Test
    fun kothwala_spellingConfirmedByTheUser() {
        val w = Ward(3)
        listOf(791, 845, 904, 912, 915, 940).forEach { assertEquals("Kothwala", w.groupOf(it).title) }
        assertEquals("Kothwala", Ward(4).groupOf(1117).title)
        assertEquals("Kothwala", Ward(5).groupOf(1523).title)
        listOf(3, 4, 5).map(::Ward).forEach { ward ->
            assertTrue(ward.voters.none { "Kotwal" in (it.nameEn ?: "") || "Kotwal" in (it.relativeNameEn ?: "") })
        }
    }
}
