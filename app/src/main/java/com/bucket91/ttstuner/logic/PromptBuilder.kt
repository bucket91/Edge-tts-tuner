package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.Rule

object PromptBuilder {

    fun buildSystemInstruction(rules: List<Rule>): String {
        val enabledRules = rules.filter { it.enabled && it.text.isNotBlank() }
        val rulesFormatted = if (enabledRules.isEmpty()) {
            "(No specific rules provided. Keep text as-is unless standard Bangla phonetic respelling applies.)"
        } else {
            enabledRules.mapIndexed { idx, rule ->
                "${idx + 1}. ${rule.text.trim()}"
            }.joinToString("\n")
        }

        return """
You are a respelling assistant for Bangla text-to-speech. You receive Bangla text. Rewrite it ONLY by changing the spelling of words so that a Bangla text-to-speech engine pronounces them the way the rules below say. 
Hard constraints:
- Do not add, remove, reorder, translate, summarize or paraphrase words. The output must have exactly the same number of words as the input.
- Do not change punctuation, digits, English words, line breaks, or any token that looks like [[W1]], [[W2]] and so on. Copy those tokens exactly.
- If no rule applies to a word, copy the word unchanged.
- Apply every rule whenever its condition is met.
Rules:
$rulesFormatted
Return JSON: {"text": "<the rewritten text>"}
        """.trimIndent()
    }
}
