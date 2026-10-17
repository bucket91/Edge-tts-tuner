package com.bucket91.ttstuner

import android.app.Application
import com.bucket91.ttstuner.audio.AndroidTtsFallback
import com.bucket91.ttstuner.audio.AudioPlayer
import com.bucket91.ttstuner.audio.EdgeTtsClient
import com.bucket91.ttstuner.data.AppDatabase
import com.bucket91.ttstuner.data.SecretStore
import com.bucket91.ttstuner.data.SettingsStore
import com.bucket91.ttstuner.logic.GeminiClient

class TtsTunerApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val secretStore by lazy { SecretStore(this) }
    val audioPlayer by lazy { AudioPlayer() }
    val edgeTtsClient by lazy { EdgeTtsClient() }
    val fallbackTtsClient by lazy { AndroidTtsFallback(this) }
    val geminiClient by lazy { GeminiClient() }

    override fun onCreate() {
        super.onCreate()
    }
}
