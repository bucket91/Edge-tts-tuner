package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.WordCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class PronunciationJsonTest {

    @Test
    fun testExportPatternForWholeWordAndSubstring() {
        val corr1 = WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true, note = "rule 1")
        val corr2 = WordCorrection(base = "কর", preferred = "কোর", wholeWord = false, note = "")

        val json = PronunciationJson.exportJson(listOf(corr1, corr2))

        // Check pattern format in serialized JSON (each backslash is doubled, so \\u0980)
        assertTrue(json.contains("(?<![\\\\u0980-\\\\u09FF])সঠিক(?![\\\\u0980-\\\\u09FF])"))
        assertTrue(json.contains("\"pattern\": \"কর\""))
        assertTrue(json.contains("\"format_version\": 1"))
    }

    @Test
    fun testImportRoundTripAndCustomRegexSkipping() {
        val corr1 = WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true)
        val corr2 = WordCorrection(base = "কর", preferred = "কোর", wholeWord = false)

        val exported = PronunciationJson.exportJson(listOf(corr1, corr2))
        val imported = PronunciationJson.importJson(exported, emptyList())

        assertEquals(2, imported.newCorrections.size)
        assertEquals("সঠিক", imported.newCorrections[0].base)
        assertEquals("শঠিক", imported.newCorrections[0].preferred)
        assertTrue(imported.newCorrections[0].wholeWord)

        assertEquals("কর", imported.newCorrections[1].base)
        assertEquals("কোর", imported.newCorrections[1].preferred)
        assertTrue(!imported.newCorrections[1].wholeWord)

        // Custom regex rule that does not match standard pattern shape should be skipped
        val jsonWithCustom = """
            {
              "format_version": 1,
              "rules": [
                {
                  "pattern": "\\b[0-9]+\\b",
                  "replacement": "সংখ্যা",
                  "note": "custom regex"
                }
              ]
            }
        """.trimIndent()

        val importedCustom = PronunciationJson.importJson(jsonWithCustom, emptyList())
        assertEquals(0, importedCustom.newCorrections.size)
        assertEquals(1, importedCustom.skippedCustomRegexCount)
    }

    @Test
    fun testDuplicateAndNfcEqualSkipping() {
        val existing = listOf(WordCorrection(base = "সঠিক", preferred = "শঠিক"))
        val decomposed = Normalizer.normalize("সঠিক", Normalizer.Form.NFD)
        val corr = WordCorrection(base = decomposed, preferred = "শঠিক", wholeWord = true)

        val json = PronunciationJson.exportJson(listOf(corr))
        val result = PronunciationJson.importJson(json, existing)

        assertEquals(0, result.newCorrections.size)
        assertEquals(1, result.skippedDuplicatesCount)
    }
}
