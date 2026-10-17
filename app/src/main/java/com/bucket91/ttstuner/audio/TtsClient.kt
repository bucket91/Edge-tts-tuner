package com.bucket91.ttstuner.audio

import java.io.File

interface TtsClient {
    suspend fun synthesize(
        text: String,
        voice: String,
        rate: String,
        outFile: File,
        log: (String) -> Unit
    ): Result<Long> // bytes written
}
