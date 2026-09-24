package com.example.ime.popup

import helium314.keyboard.latin.SuggestedWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionDemoteTest {

    @Test
    fun testDemotedWordScoringPenalty() {
        val demotedWords = mutableSetOf("unwanted", "bananas")
        val typed = "unw"

        val candidates = listOf(
            Pair("unwanted", 200),
            Pair("unwind", 150),
            Pair("unwell", 140)
        )

        val penalized = candidates.map { (word, score) ->
            if (demotedWords.contains(word)) {
                val penalizedScore = if (word.equals(typed, ignoreCase = true)) {
                    score
                } else {
                    (score - 250).coerceAtLeast(1)
                }
                Pair(word, penalizedScore)
            } else {
                Pair(word, score)
            }
        }.sortedByDescending { it.second }

        // "unwanted" should be demoted from #1 to the lowest rank (score 1)
        assertEquals("unwind", penalized[0].first)
        assertEquals(150, penalized[0].second)
        assertEquals("unwell", penalized[1].first)
        assertEquals(140, penalized[1].second)
        assertEquals("unwanted", penalized[2].first)
        assertEquals(1, penalized[2].second)
    }

    @Test
    fun testDemotedWordExactMatchPreserved() {
        val demotedWords = mutableSetOf("unwanted")
        val typed = "unwanted" // User explicitly typed the full exact word

        val candidates = listOf(
            Pair("unwanted", 200),
            Pair("unwind", 150)
        )

        val penalized = candidates.map { (word, score) ->
            if (demotedWords.contains(word)) {
                val penalizedScore = if (word.equals(typed, ignoreCase = true)) {
                    score
                } else {
                    (score - 250).coerceAtLeast(1)
                }
                Pair(word, penalizedScore)
            } else {
                Pair(word, score)
            }
        }.sortedByDescending { it.second }

        // Exact match retains score
        assertEquals("unwanted", penalized[0].first)
        assertEquals(200, penalized[0].second)
    }

    @Test
    fun testCandidateRemovalAndBackfill() {
        val currentList = mutableListOf("the", "their", "there")
        val alternatives = listOf("their", "there", "they", "them", "then")
        val removedWord = "their"

        // 1. Remove the target word
        currentList.removeAll { it.equals(removedWord, ignoreCase = true) }
        assertEquals(listOf("the", "there"), currentList)

        // 2. Find next available alternative candidate to backfill up to 3 candidates
        val validAlts = alternatives.filter { !it.equals(removedWord, ignoreCase = true) }
        for (alt in validAlts) {
            if (!currentList.contains(alt)) {
                currentList.add(alt)
                if (currentList.size >= 3) break
            }
        }

        // Expected: "their" is removed, and "they" backfills slot 3
        assertEquals(3, currentList.size)
        assertEquals(listOf("the", "there", "they"), currentList)
        assertFalse(currentList.contains("their"))
    }

    @Test
    fun testAlternativesExclusion() {
        val sw = SuggestedWords.create(listOf("apple", "application", "apply", "applesauce"))
        val targetWord = "application"

        val alternatives = mutableListOf<String>()
        for (i in 0 until sw.size()) {
            val w = sw.getWord(i)
            if (w.isNotEmpty() && !w.equals(targetWord, ignoreCase = true) && !alternatives.contains(w)) {
                alternatives.add(w)
            }
        }

        assertEquals(listOf("apple", "apply", "applesauce"), alternatives)
        assertFalse(alternatives.contains(targetWord))
    }
}
