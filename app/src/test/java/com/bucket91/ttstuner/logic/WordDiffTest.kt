package com.bucket91.ttstuner.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDiffTest {

    @Test
    fun testTokenizeKeepsBanglaCombiningMarksTogether() {
        val text = "সঠিক উত্তর হলো চার।"
        val tokens = WordDiff.tokenize(text)
        assertEquals(listOf("সঠিক", "উত্তর", "হলো", "চার", "।"), tokens)
    }

    @Test
    fun testDiffReplaceSegment() {
        val text1 = "এটি সঠিক উত্তর"
        val text2 = "এটি শঠিক উত্তর"
        val diff = WordDiff.computeDiff(text1, text2)

        assertEquals(3, diff.size)
        assertTrue(diff[0] is DiffSegment.Equal)
        assertTrue(diff[1] is DiffSegment.Replace)
        assertTrue(diff[2] is DiffSegment.Equal)

        val replace = diff[1] as DiffSegment.Replace
        assertEquals("সঠিক", replace.oldText)
        assertEquals("শঠিক", replace.newText)
    }

    @Test
    fun testDiffInsertAndDeleteSegments() {
        val text1 = "আমি যাই"
        val text2 = "আমি দ্রুত বাজারে যাই"
        val diff = WordDiff.computeDiff(text1, text2)

        assertTrue(diff.any { it is DiffSegment.Insert })

        val text3 = "আমি দ্রুত বাজারে যাই"
        val text4 = "আমি যাই"
        val diffDel = WordDiff.computeDiff(text3, text4)

        assertTrue(diffDel.any { it is DiffSegment.Delete })
    }

    @Test
    fun testExtractCandidateWordPairs() {
        val preEdit = "নমস্কার এটি সঠিক উত্তর"
        val edited = "নমস্কার এটি শঠিক উত্তোর"

        val candidates = WordDiff.extractCandidateWordPairs(preEdit, edited)
        assertEquals(2, candidates.size)
        assertEquals("সঠিক", candidates[0].base)
        assertEquals("শঠিক", candidates[0].preferred)
        assertEquals("উত্তর", candidates[1].base)
        assertEquals("উত্তোর", candidates[1].preferred)
    }
}
