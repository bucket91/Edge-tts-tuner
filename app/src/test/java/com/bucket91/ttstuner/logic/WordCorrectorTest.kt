package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.WordCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class WordCorrectorTest {

    @Test
    fun testWholeWordVsSubstringMatching() {
        val wholeWordCorrection = WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true)
        val substringCorrection = WordCorrection(base = "কর", preferred = "কোর", wholeWord = false)

        // 1. Whole word should match when surrounded by spaces or punctuation
        val input1 = "এটি সঠিক উত্তর।"
        val res1 = WordCorrector.applyCorrections(input1, listOf(wholeWordCorrection))
        assertEquals("[[W1]]", res1.replacements.first().placeholder)
        assertEquals("এটি [[W1]] উত্তর।", res1.textWithPlaceholders)
        assertEquals("এটি শঠিক উত্তর।", res1.textWithReplacements)

        // 2. Whole word should NOT match if inside another Bengali word
        // e.g. "অসঠিক" has Bengali letter 'অ' right before 'স'
        val input2 = "এটি অসঠিক উত্তর"
        val res2 = WordCorrector.applyCorrections(input2, listOf(wholeWordCorrection))
        assertEquals(0, res2.replacements.size)
        assertEquals("এটি অসঠিক উত্তর", res2.textWithPlaceholders)

        // 3. Substring should match anywhere even inside another word
        val input3 = "কাজটি করছে"
        val res3 = WordCorrector.applyCorrections(input3, listOf(substringCorrection))
        assertEquals(1, res3.replacements.size)
        assertEquals("কাজটি [[W1]]ছে", res3.textWithPlaceholders)
        assertEquals("কাজটি কোরছে", res3.textWithReplacements)
    }

    @Test
    fun testPunctuationDariIsBoundary() {
        // Bengali dari '।' is outside U+0980..U+09FF boundary block, so it serves as a boundary
        val correction = WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true)
        val input = "।সঠিক।"
        val res = WordCorrector.applyCorrections(input, listOf(correction))
        assertEquals(1, res.replacements.size)
        assertEquals("।[[W1]]।", res.textWithPlaceholders)
        assertEquals("।শঠিক।", res.textWithReplacements)
    }

    @Test
    fun testLongestFirstPrecedence() {
        // Shorter entry "কর" and longer entry "কাজ কর"
        val shortCorr = WordCorrection(base = "কর", preferred = "কোর", wholeWord = true)
        val longCorr = WordCorrection(base = "কাজ কর", preferred = "কাজ কোর", wholeWord = true)

        val input = "তুমি কাজ কর আজ"
        val res = WordCorrector.applyCorrections(input, listOf(shortCorr, longCorr))
        assertEquals(1, res.replacements.size)
        assertEquals("তুমি [[W1]] আজ", res.textWithPlaceholders)
        assertEquals("কাজ কর", res.replacements.first().base)
        assertEquals("কাজ কোর", res.replacements.first().preferred)
    }

    @Test
    fun testNfcNormalizationMatching() {
        val decomposedBase = Normalizer.normalize("সঠিক", Normalizer.Form.NFD)
        val composedInput = Normalizer.normalize("সঠিক উত্তর", Normalizer.Form.NFC)

        val corr = WordCorrection(base = decomposedBase, preferred = "শঠিক", wholeWord = true)
        val res = WordCorrector.applyCorrections(composedInput, listOf(corr))
        assertEquals(1, res.replacements.size)
        assertEquals("[[W1]] উত্তর", res.textWithPlaceholders)
    }

    @Test
    fun testPlaceholdersRestoredExactly() {
        val corr1 = WordCorrection(base = "সঠিক", preferred = "শঠিক")
        val corr2 = WordCorrection(base = "উত্তর", preferred = "উত্তোর")

        val input = "সঠিক উত্তর"
        val res = WordCorrector.applyCorrections(input, listOf(corr1, corr2))
        assertEquals(2, res.replacements.size)
        assertEquals("[[W1]] [[W2]]", res.textWithPlaceholders)

        val restored = WordCorrector.restorePlaceholders(res.textWithPlaceholders, res.replacements)
        assertEquals("শঠিক উত্তোর", restored)
    }

    @Test
    fun testPlaceholderValidation() {
        val corr1 = WordCorrection(base = "সঠিক", preferred = "শঠিক")
        val corr2 = WordCorrection(base = "উত্তর", preferred = "উত্তোর")
        val res = WordCorrector.applyCorrections("সঠিক উত্তর", listOf(corr1, corr2))

        // Valid: both present in same order
        assertTrue(WordCorrector.validatePlaceholders("[[W1]] [[W2]]", res.replacements))

        // Invalid: missing one
        assertFalse(WordCorrector.validatePlaceholders("[[W1]]", res.replacements))

        // Invalid: duplicated
        assertFalse(WordCorrector.validatePlaceholders("[[W1]] [[W1]] [[W2]]", res.replacements))

        // Invalid: swapped order
        assertFalse(WordCorrector.validatePlaceholders("[[W2]] [[W1]]", res.replacements))

        // Invalid: unexpected extra placeholder
        assertFalse(WordCorrector.validatePlaceholders("[[W1]] [[W2]] [[W3]]", res.replacements))
    }
}
