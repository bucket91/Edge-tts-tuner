package com.bucket91.ttstuner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class AppSettings(
    val geminiModel: String = "gemini-flash-latest",
    val voice: String = "bn-BD-PradeepNeural",
    val rate: String = "+4%",
    val useFallbackTts: Boolean = false
)

class SettingsStore(private val context: Context) {

    private object PreferencesKeys {
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val VOICE = stringPreferencesKey("voice")
        val RATE = stringPreferencesKey("rate")
        val USE_FALLBACK_TTS = booleanPreferencesKey("use_fallback_tts")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            geminiModel = preferences[PreferencesKeys.GEMINI_MODEL] ?: "gemini-flash-latest",
            voice = preferences[PreferencesKeys.VOICE] ?: "bn-BD-PradeepNeural",
            rate = preferences[PreferencesKeys.RATE] ?: "+4%",
            useFallbackTts = preferences[PreferencesKeys.USE_FALLBACK_TTS] ?: false
        )
    }

    suspend fun getSettings(): AppSettings {
        return settingsFlow.first()
    }

    suspend fun updateGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_MODEL] = model.trim().ifEmpty { "gemini-flash-latest" }
        }
    }

    suspend fun updateVoice(voice: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VOICE] = voice.trim().ifEmpty { "bn-BD-PradeepNeural" }
        }
    }

    suspend fun updateRate(rate: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RATE] = rate.trim().ifEmpty { "+4%" }
        }
    }

    suspend fun updateUseFallbackTts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_FALLBACK_TTS] = enabled
        }
    }

    suspend fun restoreSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_MODEL] = settings.geminiModel
            preferences[PreferencesKeys.VOICE] = settings.voice
            preferences[PreferencesKeys.RATE] = settings.rate
            preferences[PreferencesKeys.USE_FALLBACK_TTS] = settings.useFallbackTts
        }
    }
}
