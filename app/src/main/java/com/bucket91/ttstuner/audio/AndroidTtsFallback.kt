package com.bucket91.ttstuner.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidTtsFallback(private val context: Context) : TtsClient {

    private fun parseRateMultiplier(rateStr: String): Float {
        // e.g. "+4%" -> 1.04f, "-10%" -> 0.90f, "+0%" -> 1.0f
        return try {
            val clean = rateStr.trim().removeSuffix("%")
            val percent = clean.toFloat()
            1.0f + (percent / 100f)
        } catch (e: Exception) {
            1.0f
        }
    }

    override suspend fun synthesize(
        text: String,
        voice: String,
        rate: String,
        outFile: File,
        log: (String) -> Unit
    ): Result<Long> = suspendCancellableCoroutine { continuation ->
        log("Android TTS Fallback starting for text length ${text.length}")

        var ttsInstance: TextToSpeech? = null
        val utteranceId = UUID.randomUUID().toString()

        ttsInstance = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                log("Android TTS initialization failed: status=$status")
                continuation.resume(Result.failure(Exception("Failed to initialize Android TextToSpeech engine.")))
                return@TextToSpeech
            }

            val tts = ttsInstance ?: return@TextToSpeech
            var locale = Locale("bn", "BD")
            var availability = tts.isLanguageAvailable(locale)

            if (availability < TextToSpeech.LANG_AVAILABLE) {
                locale = Locale("bn", "IN")
                availability = tts.isLanguageAvailable(locale)
            }

            if (availability < TextToSpeech.LANG_AVAILABLE) {
                log("Bangla language not available in device TTS: availability code $availability")
                tts.shutdown()
                continuation.resume(Result.failure(Exception("No Bangla voice installed on this device")))
                return@TextToSpeech
            }

            tts.language = locale
            val speechRate = parseRateMultiplier(rate)
            tts.setSpeechRate(speechRate)
            log("Configured device TTS with locale $locale and rate multiplier $speechRate")

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    log("Device TTS synthesis started")
                }

                override fun onDone(id: String?) {
                    log("Device TTS synthesis completed")
                    val bytesWritten = outFile.length()
                    tts.shutdown()
                    continuation.resume(Result.success(bytesWritten))
                }

                override fun onError(id: String?) {
                    log("Device TTS synthesis error")
                    tts.shutdown()
                    continuation.resume(Result.failure(Exception("Android TextToSpeech synthesis failed.")))
                }

                override fun onError(id: String?, errorCode: Int) {
                    log("Device TTS synthesis error: code $errorCode")
                    tts.shutdown()
                    continuation.resume(Result.failure(Exception("Android TextToSpeech error code $errorCode.")))
                }
            })

            val params = Bundle()
            outFile.parentFile?.mkdirs()
            val result = tts.synthesizeToFile(text, params, outFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                log("synthesizeToFile returned failure code $result")
                tts.shutdown()
                continuation.resume(Result.failure(Exception("Failed to queue synthesizeToFile on device voice (code $result).")))
            }
        }

        continuation.invokeOnCancellation {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (e: Exception) {}
        }
    }
}
