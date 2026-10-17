package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.data.WordCorrection
import java.text.Normalizer

data class RulesMdImportResult(
    val newRules: List<Rule>,
    val newCorrections: List<WordCorrection>,
    val addedCount: Int,
    val skippedCount: Int,
    val summary: String
)

object RulesMd {

    fun exportMarkdown(rules: List<Rule>, corrections: List<WordCorrection>): String {
        val enabledRules = rules.filter { it.enabled && it.text.isNotBlank() }
        val enabledCorrections = corrections.filter { it.enabled && it.base.isNotBlank() }

        val sb = StringBuilder()
        sb.appendLine("# TTS rules")
        sb.appendLine("## General rules")
        if (enabledRules.isEmpty()) {
            sb.appendLine()
        } else {
            for (rule in enabledRules) {
                sb.appendLine("- ${rule.text.trim()}")
            }
        }

        sb.appendLine("## Word corrections")
        sb.appendLine("| base | preferred | whole word | note |")
        sb.appendLine("|---|---|---|---|")
        for (wc in enabledCorrections) {
            val wholeWordStr = if (wc.wholeWord) "yes" else "no"
            val safeNote = wc.note.replace("|", "\\|").replace("\n", " ")
            val safeBase = wc.base.replace("|", "\\|").replace("\n", " ")
            val safePref = wc.preferred.replace("|", "\\|").replace("\n", " ")
            sb.appendLine("| $safeBase | $safePref | $wholeWordStr | $safeNote |")
        }

        return sb.toString()
    }

    fun importMarkdown(
        markdown: String,
        existingRules: List<Rule>,
        existingCorrections: List<WordCorrection>
    ): RulesMdImportResult {
        val existingRuleTexts = existingRules.map { Normalizer.normalize(it.text.trim(), Normalizer.Form.NFC) }.toMutableSet()
        val existingCorrectionBases = existingCorrections.map { Normalizer.normalize(it.base.trim(), Normalizer.Form.NFC) }.toMutableSet()

        val newRules = mutableListOf<Rule>()
        val newCorrections = mutableListOf<WordCorrection>()
        var skippedCount = 0

        val lines = markdown.lines()
        var currentSection = "" // "rules" or "words"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## General rules", ignoreCase = true)) {
                currentSection = "rules"
                continue
            } else if (trimmed.startsWith("## Word corrections", ignoreCase = true)) {
                currentSection = "words"
                continue
            } else if (trimmed.startsWith("#")) {
                currentSection = ""
                continue
            }

            if (trimmed.isBlank()) continue

            when (currentSection) {
                "rules" -> {
                    if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                        val ruleText = trimmed.substring(1).trim()
                        if (ruleText.isNotEmpty()) {
                            val norm = Normalizer.normalize(ruleText, Normalizer.Form.NFC)
                            if (existingRuleTexts.contains(norm)) {
                                skippedCount++
                            } else {
                                existingRuleTexts.add(norm)
                                newRules.add(Rule(text = ruleText, enabled = true))
                            }
                        }
                    }
                }
                "words" -> {
                    if (trimmed.startsWith("|")) {
                        val cols = trimmed.split("|")
                            .map { it.trim() }
                            .filterIndexed { index, _ -> index > 0 } // drop before first pipe
                        if (cols.size >= 3) {
                            val base = cols[0]
                            val preferred = cols[1]
                            val wholeWordStr = cols[2]
                            val note = if (cols.size >= 4) cols[3] else ""

                            // Ignore header rows
                            if (base.equals("base", ignoreCase = true) || base.startsWith("---")) {
                                continue
                            }

                            if (base.isNotEmpty()) {
                                val normBase = Normalizer.normalize(base, Normalizer.Form.NFC)
                                if (existingCorrectionBases.contains(normBase)) {
                                    skippedCount++
                                } else {
                                    existingCorrectionBases.add(normBase)
                                    val wholeWord = wholeWordStr.equals("yes", ignoreCase = true) ||
                                            wholeWordStr.equals("true", ignoreCase = true) ||
                                            wholeWordStr == "1"
                                    newCorrections.add(
                                        WordCorrection(
                                            base = base,
                                            preferred = preferred,
                                            wholeWord = wholeWord,
                                            note = note,
                                            enabled = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val totalAdded = newRules.size + newCorrections.size
        val summary = "Added $totalAdded, skipped $skippedCount"
        return RulesMdImportResult(
            newRules = newRules,
            newCorrections = newCorrections,
            addedCount = totalAdded,
            skippedCount = skippedCount,
            summary = summary
        )
    }
}
