package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.data.db.HouseholdSummary
import com.example.etikoppakavoterdirectory.ui.households.HouseholdBrowseState
import com.example.etikoppakavoterdirectory.ui.households.HouseholdSort
import com.example.etikoppakavoterdirectory.ui.households.sortHouseholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Most / Least members first: order by live member count, ties by name then id; input untouched. */
class HouseholdSortTest {

    private fun h(id: Long, name: String, members: Int) =
        HouseholdSummary(id, name, null, members, 0, 0, (100 - id).toInt(), 11, locked = false)

    private val list = listOf(
        h(1, "Rayi", 5), h(2, "Gutti", 27), h(3, "Bandi", 2), h(4, "Achanta", 5),
        h(5, "Mogga", 1), h(6, "achanta", 5), h(7, "Zeta", 14), h(8, "Kola", 12), h(9, "Pedapati", 15),
    )

    @Test
    fun mostMembersFirst_descendingWithStableTies() {
        val sorted = sortHouseholds(list, HouseholdSort.MEMBERS_DESC)
        assertEquals(listOf(27, 15, 14, 12, 5, 5, 5, 2, 1), sorted.map { it.members })
        // Equal counts: by name (case-insensitive), then by id.
        assertEquals(listOf(4L, 6L, 1L), sorted.filter { it.members == 5 }.map { it.id })
    }

    @Test
    fun leastMembersFirst_ascendingWithStableTies() {
        val sorted = sortHouseholds(list, HouseholdSort.MEMBERS_ASC)
        assertEquals(listOf(1, 2, 5, 5, 5, 12, 14, 15, 27), sorted.map { it.members })
        assertEquals(listOf(4L, 6L, 1L), sorted.filter { it.members == 5 }.map { it.id })
    }

    @Test
    fun sortingIsDeterministic_andDoesNotChangeTheInput() {
        val copy = list.toList()
        repeat(3) {
            assertEquals(sortHouseholds(list.shuffled(), HouseholdSort.MEMBERS_ASC), sortHouseholds(list, HouseholdSort.MEMBERS_ASC))
            assertEquals(sortHouseholds(list.shuffled(), HouseholdSort.MEMBERS_DESC), sortHouseholds(list, HouseholdSort.MEMBERS_DESC))
        }
        assertEquals(copy, list)
    }

    @Test
    fun sortMenu_listsMostThenLeastFirst() {
        assertEquals(HouseholdSort.MEMBERS_DESC, HouseholdSort.entries[0])
        assertEquals(HouseholdSort.MEMBERS_ASC, HouseholdSort.entries[1])
        assertEquals("Least members first", HouseholdSort.MEMBERS_ASC.label)
    }

    @Test
    fun browseState_resetRestoresDefaults() {
        val s = HouseholdBrowseState()
        s.query = "Pedapati"; s.searching = true; s.sort = HouseholdSort.MEMBERS_ASC
        val list = s.listState
        s.reset()
        assertEquals("", s.query)
        assertTrue(!s.searching)
        assertEquals(HouseholdSort.MEMBERS_DESC, s.sort)
        assertTrue(s.listState !== list)
        assertEquals(0, s.listState.firstVisibleItemIndex)
    }
}
