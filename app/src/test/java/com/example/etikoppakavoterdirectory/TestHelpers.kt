package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.data.VoterRepository
import com.example.etikoppakavoterdirectory.data.db.VoterIds

/** Ward 11 GP serial -> stable voter id (the bundled dataset is Ward 11). */
val Int.v: Int get() = VoterIds.of(11, this)
fun Iterable<Int>.v(): List<Int> = map { it.v }

suspend fun VoterRepository.householdMemberSerials(id: Long): List<Int> = householdMemberIds(id).map { VoterIds.serialOf(it) }
fun VoterRepository.householdSummaries() = householdSummaries(11)
fun VoterRepository.stats() = stats(11)
fun VoterRepository.tags() = tags(11)
suspend fun VoterRepository.clearBlock(block: String) = clearBlock(11, block)
suspend fun VoterRepository.setBlockForRange(from: Int, to: Int, block: String?) = setBlockForRange(11, from, to, block)
