package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.WordCorrection
import java.text.Normalizer

data class WordMatch(
    val startIndex: Int,
    val endIndex: Int,
    val base: String,
    val preferred: String,
    val wholeWord: Boolean
)

data class PlaceholderReplacement(
    val index: Int,
    val placeholder: String, // "[[W1]]"
    val base: String,
    val preferred: String
)

data class Step1Result(
    val originalText: String,
    val textWithPlaceholders: String,
    val textWithReplacements: String,
    val replacements: List<PlaceholderReplacement>
)

object WordCorrector {

    private fun isBengaliChar(c: Char): Boolean {
        return c in '\u0980'..'\u09FF'
    }

    fun applyCorrections(input: String, corrections: List<WordCorrection>): Step1Result {
        val normalizedInput = Normalizer.normalize(input, Normalizer.Form.NFC)
        val enabledCorrections = corrections
            .filter { it.enabled && it.base.isNotEmpty() }
            .sortedByDescending { it.base.length }

        // Find matches respecting longest-first and non-overlapping
        val claimedSpans = mutableListOf<WordMatch>()

        for (corr in enabledCorrections) {
            val normalizedBase = Normalizer.normalize(corr.base, Normalizer.Form.NFC)
            if (normalizedBase.isEmpty()) continue

            var searchIndex = 0
            while (searchIndex < normalizedInput.length) {
                val foundIndex = normalizedInput.indexOf(normalizedBase, searchIndex)
                if (foundIndex < 0) break

                val endIndex = foundIndex + normalizedBase.length
                var isValid = true

                // Check whole word boundary if requested
                if (corr.wholeWord) {
                    if (foundIndex > 0) {
                        val prevChar = normalizedInput[foundIndex - 1]
                        if (isBengaliChar(prevChar)) {
                            isValid = false
                        }
                    }
                    if (isValid && endIndex < normalizedInput.length) {
                        val nextChar = normalizedInput[endIndex]
                        if (isBengaliChar(nextChar)) {
                            isValid = false
                        }
                    }
                }

                // Check overlap with already claimed spans
                if (isValid) {
                    val overlaps = claimedSpans.any { existing ->
                        foundIndex < existing.endIndex && endIndex > existing.startIndex
                    }
                    if (!overlaps) {
                        claimedSpans.add(
                            WordMatch(
                                startIndex = foundIndex,
                                endIndex = endIndex,
                                base = corr.base,
                                preferred = corr.preferred,
                                wholeWord = corr.wholeWord
                            )
                        )
                    }
                }

                searchIndex = foundIndex + 1
            }
        }

        // Sort matches by start index left-to-right
        claimedSpans.sortBy { it.startIndex }

        val placeholderBuilder = StringBuilder()
        val replacementBuilder = StringBuilder()
        val replacements = mutableListOf<PlaceholderReplacement>()

        var cursor = 0
        var counter = 1

        for (match in claimedSpans) {
            if (match.startIndex > cursor) {
                val nonMatchedSegment = normalizedInput.substring(cursor, match.startIndex)
                placeholderBuilder.append(nonMatchedSegment)
                replacementBuilder.append(nonMatchedSegment)
            }

            val placeholder = "[[W$counter]]"
            val replacementItem = PlaceholderReplacement(
                index = counter,
                placeholder = placeholder,
                base = match.base,
                preferred = match.preferred
            )
            replacements.add(replacementItem)

            placeholderBuilder.append(placeholder)
            replacementBuilder.append(match.preferred)

            cursor = match.endIndex
            counter++
        }

        if (cursor < normalizedInput.length) {
            val remaining = normalizedInput.substring(cursor)
            placeholderBuilder.append(remaining)
            replacementBuilder.append(remaining)
        }

        return Step1Result(
            originalText = normalizedInput,
            textWithPlaceholders = placeholderBuilder.toString(),
            textWithReplacements = replacementBuilder.toString(),
            replacements = replacements
        )
    }

    fun restorePlaceholders(text: String, replacements: List<PlaceholderReplacement>): String {
        var result = text
        for (item in replacements) {
            result = result.replace(item.placeholder, item.preferred)
        }
        return result
    }

    /**
     * Validates that every placeholder from the replacements list appears exactly once
     * and in the same order as in the original input.
     */
    fun validatePlaceholders(text: String, replacements: List<PlaceholderReplacement>): Boolean {
        var lastIndex = -1
        for (item in replacements) {
            val firstOccurrence = text.indexOf(item.placeholder)
            if (firstOccurrence == -1) return false
            if (firstOccurrence <= lastIndex) return false

            val secondOccurrence = text.indexOf(item.placeholder, firstOccurrence + item.placeholder.length)
            if (secondOccurrence != -1) return false

            lastIndex = firstOccurrence
        }

        // Check there are no phantom placeholders like [[W99]] that were not in replacements
        val placeholderRegex = Regex("\\[\\[W\\d+\\]\\]")
        val foundPlaceholders = placeholderRegex.findAll(text).map { it.value }.toList()
        if (foundPlaceholders.size != replacements.size) return false

        return true
    }
}
