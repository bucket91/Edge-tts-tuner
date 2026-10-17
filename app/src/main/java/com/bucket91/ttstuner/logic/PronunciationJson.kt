package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.WordCorrection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
data class PronunciationRuleJson(
    val pattern: String,
    val replacement: String,
    val note: String = ""
)

@Serializable
data class PronunciationFileJson(
    val format_version: Int = 1,
    val rules: List<PronunciationRuleJson> = emptyList(),
    val keep_s: List<String> = emptyList()
)

data class PronunciationImportResult(
    val newCorrections: List<WordCorrection>,
    val addedCount: Int,
    val skippedDuplicatesCount: Int,
    val skippedCustomRegexCount: Int,
    val summary: String
)

object PronunciationJson {

    private val jsonSerializer = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val WHOLE_WORD_PREFIX = "(?<![\\u0980-\\u09FF])"
    private const val WHOLE_WORD_SUFFIX = "(?![\\u0980-\\u09FF])"

    private val PYTHON_METACHARS = setOf('\\', '.', '^', '$', '*', '+', '?', '{', '}', '[', ']', '|', '(', ')')

    fun escapeRegexPython(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch in PYTHON_METACHARS) {
                sb.append('\\')
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    fun unescapeRegexPython(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length && text[i + 1] in PYTHON_METACHARS) {
                sb.append(text[i + 1])
                i += 2
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    fun exportJson(corrections: List<WordCorrection>): String {
        val enabledCorrections = corrections
            .filter { it.enabled && it.base.isNotBlank() }
            .sortedByDescending { it.base.length }

        val rulesList = enabledCorrections.map { wc ->
            val escapedBase = escapeRegexPython(wc.base)
            val pattern = if (wc.wholeWord) {
                "$WHOLE_WORD_PREFIX$escapedBase$WHOLE_WORD_SUFFIX"
            } else {
                escapedBase
            }
            PronunciationRuleJson(
                pattern = pattern,
                replacement = wc.preferred,
                note = wc.note
            )
        }

        val fileObj = PronunciationFileJson(
            format_version = 1,
            rules = rulesList,
            keep_s = emptyList()
        )

        return jsonSerializer.encodeToString(PronunciationFileJson.serializer(), fileObj)
    }

    fun importJson(
        jsonString: String,
        existingCorrections: List<WordCorrection>
    ): PronunciationImportResult {
        val existingBases = existingCorrections.map { Normalizer.normalize(it.base.trim(), Normalizer.Form.NFC) }.toMutableSet()
        val parsed = try {
            jsonSerializer.decodeFromString(PronunciationFileJson.serializer(), jsonString)
        } catch (e: Exception) {
            return PronunciationImportResult(
                newCorrections = emptyList(),
                addedCount = 0,
                skippedDuplicatesCount = 0,
                skippedCustomRegexCount = 0,
                summary = "Failed to parse pronunciation.json: ${e.message}"
            )
        }

        val newCorrections = mutableListOf<WordCorrection>()
        var duplicatesCount = 0
        var customRegexCount = 0

        for (rule in parsed.rules) {
            val pattern = rule.pattern
            var wholeWord = false
            var base: String? = null

            if (pattern.startsWith(WHOLE_WORD_PREFIX) && pattern.endsWith(WHOLE_WORD_SUFFIX)) {
                val escapedBase = pattern.substring(
                    WHOLE_WORD_PREFIX.length,
                    pattern.length - WHOLE_WORD_SUFFIX.length
                )
                // Verify it's valid escaped base
                val unescaped = unescapeRegexPython(escapedBase)
                if (escapeRegexPython(unescaped) == escapedBase) {
                    wholeWord = true
                    base = unescaped
                }
            } else {
                // Check substring shape
                val unescaped = unescapeRegexPython(pattern)
                if (escapeRegexPython(unescaped) == pattern) {
                    wholeWord = false
                    base = unescaped
                }
            }

            if (base != null) {
                val normBase = Normalizer.normalize(base.trim(), Normalizer.Form.NFC)
                if (normBase.isEmpty()) continue

                if (existingBases.contains(normBase)) {
                    duplicatesCount++
                } else {
                    existingBases.add(normBase)
                    newCorrections.add(
                        WordCorrection(
                            base = base,
                            preferred = rule.replacement,
                            note = rule.note,
                            wholeWord = wholeWord,
                            enabled = true
                        )
                    )
                }
            } else {
                customRegexCount++
            }
        }

        val summary = "Added ${newCorrections.size}, skipped $duplicatesCount duplicates, skipped $customRegexCount (custom regex)"
        return PronunciationImportResult(
            newCorrections = newCorrections,
            addedCount = newCorrections.size,
            skippedDuplicatesCount = duplicatesCount,
            skippedCustomRegexCount = customRegexCount,
            summary = summary
        )
    }
}
