package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.data.WordCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class RulesMdTest {

    @Test
    fun testExportAndImportRoundTrip() {
        val rules = listOf(
            Rule(text = "স should be written শ when pronounced 'sh'", enabled = true),
            Rule(text = "Disabled rule", enabled = false)
        )
        val corrections = listOf(
            WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true, note = "test note", enabled = true),
            WordCorrection(base = "কর", preferred = "কোর", wholeWord = false, note = "", enabled = true)
        )

        val exported = RulesMd.exportMarkdown(rules, corrections)
        assertTrue(exported.contains("# TTS rules"))
        assertTrue(exported.contains("## General rules"))
        assertTrue(exported.contains("## Word corrections"))
        assertTrue(exported.contains("স should be written শ"))
        assertTrue(!exported.contains("Disabled rule")) // only enabled exported

        val imported = RulesMd.importMarkdown(exported, emptyList(), emptyList())
        assertEquals(1, imported.newRules.size)
        assertEquals("স should be written শ when pronounced 'sh'", imported.newRules.first().text)

        assertEquals(2, imported.newCorrections.size)
        assertEquals("সঠিক", imported.newCorrections[0].base)
        assertEquals("শঠিক", imported.newCorrections[0].preferred)
        assertTrue(imported.newCorrections[0].wholeWord)
        assertEquals("test note", imported.newCorrections[0].note)

        assertEquals("কর", imported.newCorrections[1].base)
        assertEquals("কোর", imported.newCorrections[1].preferred)
        assertTrue(!imported.newCorrections[1].wholeWord)
    }

    @Test
    fun testDuplicateSkipping() {
        val existingRules = listOf(Rule(text = "স should be written শ", enabled = true))
        val existingCorrections = listOf(WordCorrection(base = "সঠিক", preferred = "শঠিক", wholeWord = true))

        val md = """
            # TTS rules
            ## General rules
            - স should be written শ
            - নতুন নিয়ম
            ## Word corrections
            | base | preferred | whole word | note |
            |---|---|---|---|
            | সঠিক | শঠিক | yes | |
            | নতুন | নোতুন | yes | |
        """.trimIndent()

        val result = RulesMd.importMarkdown(md, existingRules, existingCorrections)
        assertEquals(1, result.newRules.size)
        assertEquals("নতুন নিয়ম", result.newRules.first().text)

        assertEquals(1, result.newCorrections.size)
        assertEquals("নতুন", result.newCorrections.first().base)
        assertEquals(2, result.skippedCount) // 1 rule duplicate, 1 correction duplicate
    }
}
