package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.AppSettings
import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.data.WordCorrection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
data class AppBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val rules: List<Rule> = emptyList(),
    val wordCorrections: List<WordCorrection> = emptyList()
)

data class BackupImportResult(
    val restoredSettings: AppSettings,
    val newRules: List<Rule>,
    val newCorrections: List<WordCorrection>,
    val skippedRules: Int,
    val skippedCorrections: Int,
    val summary: String
)

object BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun createBackupJson(
        settings: AppSettings,
        rules: List<Rule>,
        corrections: List<WordCorrection>
    ): String {
        val backup = AppBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            settings = settings,
            rules = rules,
            wordCorrections = corrections
        )
        return json.encodeToString(AppBackup.serializer(), backup)
    }

    fun parseBackupJson(
        jsonString: String,
        existingRules: List<Rule>,
        existingCorrections: List<WordCorrection>
    ): BackupImportResult {
        val parsed = json.decodeFromString(AppBackup.serializer(), jsonString)

        val existingRuleTexts = existingRules.map { Normalizer.normalize(it.text.trim(), Normalizer.Form.NFC) }.toMutableSet()
        val existingBases = existingCorrections.map { Normalizer.normalize(it.base.trim(), Normalizer.Form.NFC) }.toMutableSet()

        val newRules = mutableListOf<Rule>()
        var skippedRules = 0
        for (r in parsed.rules) {
            val norm = Normalizer.normalize(r.text.trim(), Normalizer.Form.NFC)
            if (existingRuleTexts.contains(norm)) {
                skippedRules++
            } else {
                existingRuleTexts.add(norm)
                newRules.add(r.copy(id = 0))
            }
        }

        val newCorrections = mutableListOf<WordCorrection>()
        var skippedCorrections = 0
        for (wc in parsed.wordCorrections) {
            val norm = Normalizer.normalize(wc.base.trim(), Normalizer.Form.NFC)
            if (existingBases.contains(norm)) {
                skippedCorrections++
            } else {
                existingBases.add(norm)
                newCorrections.add(wc.copy(id = 0))
            }
        }

        val summary = "Restored settings, added ${newRules.size} rules (skipped $skippedRules), added ${newCorrections.size} word corrections (skipped $skippedCorrections)"
        return BackupImportResult(
            restoredSettings = parsed.settings,
            newRules = newRules,
            newCorrections = newCorrections,
            skippedRules = skippedRules,
            skippedCorrections = skippedCorrections,
            summary = summary
        )
    }
}
