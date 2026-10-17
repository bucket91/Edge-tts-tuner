package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.data.WordCorrection

data class PipelineOutput(
    val input: String,
    val stage1WithPlaceholders: String,
    val stage1ReplacedText: String,
    val stage1Replacements: List<PlaceholderReplacement>,
    val stage2ResultText: String?,
    val stage2Notice: String?,
    val finalText: String,
    val diffSegments: List<DiffSegment>
)

object Pipeline {

    suspend fun run(
        input: String,
        corrections: List<WordCorrection>,
        rules: List<Rule>,
        apiKey: String,
        model: String,
        geminiClient: GeminiClient,
        onProgress: (String) -> Unit = {}
    ): PipelineOutput {
        // Step 1: Word corrections (deterministic, no AI)
        onProgress("1/4 Applying word corrections...")
        val step1 = WordCorrector.applyCorrections(input, corrections)

        // Step 2: Rules pass (Gemini)
        onProgress("2/4 Running pronunciation rules pass with Gemini...")
        val geminiResult = if (rules.none { it.enabled }) {
            GeminiResult.Skipped("Rules pass skipped: No enabled rules")
        } else if (apiKey.isBlank()) {
            GeminiResult.Skipped("Rules pass skipped: Gemini API key not set in Settings")
        } else {
            geminiClient.executeRespellingPass(
                textWithPlaceholders = step1.textWithPlaceholders,
                replacements = step1.replacements,
                rules = rules,
                apiKey = apiKey,
                model = model
            )
        }

        // Step 3: Restore
        onProgress("3/4 Restoring protected words...")
        val finalText: String
        val stage2Text: String?
        val stage2Notice: String?

        when (geminiResult) {
            is GeminiResult.Success -> {
                stage2Text = geminiResult.rewrittenText
                stage2Notice = null
                finalText = WordCorrector.restorePlaceholders(geminiResult.rewrittenText, step1.replacements)
            }
            is GeminiResult.Skipped -> {
                stage2Text = null
                stage2Notice = geminiResult.reason
                finalText = step1.textWithReplacements
            }
            is GeminiResult.Error -> {
                stage2Text = null
                stage2Notice = "${geminiResult.message} ${if (geminiResult.technicalDetail.isNotEmpty()) "(${geminiResult.technicalDetail})" else ""}"
                finalText = step1.textWithReplacements
            }
        }

        // Step 4: Compare (diff between original input and final text)
        onProgress("4/4 Computing word-level diff...")
        val diff = WordDiff.computeDiff(step1.originalText, finalText)

        onProgress("Done")
        return PipelineOutput(
            input = step1.originalText,
            stage1WithPlaceholders = step1.textWithPlaceholders,
            stage1ReplacedText = step1.textWithReplacements,
            stage1Replacements = step1.replacements,
            stage2ResultText = stage2Text,
            stage2Notice = stage2Notice,
            finalText = finalText,
            diffSegments = diff
        )
    }
}
