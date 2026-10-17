package com.bucket91.ttstuner.audio

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class EdgeTtsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : TtsClient {

    companion object {
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val CHROMIUM_FULL_VERSION = "130.0.2849.68"
        const val CHROMIUM_MAJOR_VERSION = "130"
        const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"

        @Volatile
        var storedClockSkewSeconds: Long = 0L

        fun computeTicksAndRounded(unixSeconds: Long): Pair<Long, Long> {
            val fileTimeSec = unixSeconds + 11644473600L
            val rounded = fileTimeSec - (fileTimeSec % 300L)
            val ticks = rounded * 10_000_000L
            return Pair(rounded, ticks)
        }

        fun computeSecMsGecToken(unixSeconds: Long): String {
            val (_, ticks) = computeTicksAndRounded(unixSeconds)
            val strToHash = "$ticks$TRUSTED_CLIENT_TOKEN"
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(strToHash.toByteArray(StandardCharsets.US_ASCII))
            return hashBytes.joinToString("") { "%02X".format(it) }
        }

        fun escapeSsml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun parseBinaryAudioFrame(bytes: ByteArray): ByteArray? {
            if (bytes.size < 2) return null
            val headerLen = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            if (bytes.size < 2 + headerLen) return null

            val headerText = String(bytes, 2, headerLen, StandardCharsets.UTF_8)
            if (!headerText.contains("Path:audio")) return null

            val audioDataLen = bytes.size - (2 + headerLen)
            if (audioDataLen <= 0) return null

            val audioBytes = ByteArray(audioDataLen)
            System.arraycopy(bytes, 2 + headerLen, audioBytes, 0, audioDataLen)
            return audioBytes
        }

        fun formatTimestamp(date: Date = Date()): String {
            val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(date)
        }
    }

    override suspend fun synthesize(
        text: String,
        voice: String,
        rate: String,
        outFile: File,
        log: (String) -> Unit
    ): Result<Long> {
        val result = executeSynthesisAttempt(text, voice, rate, outFile, log, isRetry = false)
        if (result.isSuccess) return result

        val failureEx = result.exceptionOrNull()
        if (failureEx is ClockSkewException) {
            log("[${formatLogTime()}] 403 Clock skew detected. Retrying once with corrected skew: ${storedClockSkewSeconds}s")
            return executeSynthesisAttempt(text, voice, rate, outFile, log, isRetry = true)
        }

        return result
    }

    private class ClockSkewException(val skewSeconds: Long, message: String) : Exception(message)

    private fun formatLogTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date())
    }

    private suspend fun executeSynthesisAttempt(
        text: String,
        voice: String,
        rate: String,
        outFile: File,
        log: (String) -> Unit,
        isRetry: Boolean
    ): Result<Long> = suspendCancellableCoroutine { continuation ->

        val nowUnixSec = (System.currentTimeMillis() / 1000L) + storedClockSkewSeconds
        val gecToken = computeSecMsGecToken(nowUnixSec)
        val connectionId = UUID.randomUUID().toString().replace("-", "")

        log("[${formatLogTime()}] Synthesis start: voice=$voice, rate=$rate, textLength=${text.length}, isRetry=$isRetry")
        log("[${formatLogTime()}] GEC token prefix=${gecToken.take(8)}, unixTimeUsed=$nowUnixSec, clockSkew=${storedClockSkewSeconds}s")

        val wssUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?" +
                "TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId&Sec-MS-GEC=$gecToken&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(wssUrl)
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .build()

        val totalBytesReceived = AtomicLong(0)
        val binaryMessageCount = AtomicLong(0)
        val completed = AtomicBoolean(false)
        val tempOutFile = File(outFile.parentFile, "${outFile.name}.tmp")

        outFile.parentFile?.mkdirs()
        val fileOutputStream = try {
            FileOutputStream(tempOutFile)
        } catch (e: Exception) {
            continuation.resume(Result.failure(Exception("Cannot create cache output file: ${e.message}")))
            return@suspendCancellableCoroutine
        }

        var webSocketRef: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("[${formatLogTime()}] WebSocket open (response code ${response.code})")

                // Message 1: speech.config
                val timestampStr = formatTimestamp()
                val configMsg = "X-Timestamp:$timestampStr\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(configMsg)
                log("[${formatLogTime()}] Config sent")

                // Message 2: ssml
                val requestId = UUID.randomUUID().toString().replace("-", "")
                val escapedText = escapeSsml(text)
                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestampStr}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                        "<voice name='$voice'><prosody pitch='+0Hz' rate='$rate' volume='+0%'>$escapedText</prosody></voice></speak>"
                webSocket.send(ssmlMsg)
                log("[${formatLogTime()}] SSML sent")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val pathLine = text.lines().firstOrNull { it.startsWith("Path:", ignoreCase = true) }
                val path = pathLine?.substringAfter(":")?.trim() ?: "unknown"
                log("[${formatLogTime()}] Text message received: Path:$path")

                if (path.equals("turn.end", ignoreCase = true)) {
                    log("[${formatLogTime()}] turn.end received. Audio synthesis completed.")
                    try {
                        fileOutputStream.flush()
                        fileOutputStream.close()
                    } catch (e: Exception) {
                        // ignore
                    }

                    if (tempOutFile.exists() && tempOutFile.length() > 0) {
                        tempOutFile.renameTo(outFile)
                    }

                    val finalBytes = totalBytesReceived.get()
                    log("[${formatLogTime()}] Finished: total bytes = $finalBytes")

                    webSocket.close(1000, "Completed")
                    if (completed.compareAndSet(false, true)) {
                        if (finalBytes > 0) {
                            continuation.resume(Result.success(finalBytes))
                        } else {
                            continuation.resume(Result.failure(Exception("Zero audio bytes received from edge-tts.")))
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val rawBytes = bytes.toByteArray()
                val audioData = parseBinaryAudioFrame(rawBytes)
                val headerLen = if (rawBytes.size >= 2) ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF) else 0
                val dataLen = audioData?.size ?: 0
                val count = binaryMessageCount.incrementAndGet()

                log("[${formatLogTime()}] Binary message #$count: headerLength=$headerLen, audioDataBytes=$dataLen")

                if (audioData != null && audioData.isNotEmpty()) {
                    try {
                        fileOutputStream.write(audioData)
                        totalBytesReceived.addAndGet(audioData.size.toLong())
                    } catch (e: Exception) {
                        log("[${formatLogTime()}] Error writing audio data to file: ${e.message}")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("[${formatLogTime()}] WebSocket closing: code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("[${formatLogTime()}] WebSocket closed: code=$code, reason=$reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                try {
                    fileOutputStream.close()
                } catch (e: Exception) {}

                val statusCode = response?.code ?: 0
                val responseBody = try { response?.body?.string().orEmpty() } catch (e: Exception) { "" }
                log("[${formatLogTime()}] WebSocket failure: HTTP $statusCode, exception=${t.javaClass.simpleName}: ${t.message}, body=$responseBody")

                if (statusCode == 403 && !isRetry) {
                    val dateHeader = response?.header("Date")
                    if (dateHeader != null) {
                        try {
                            val rfcFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                            val serverTime = rfcFormat.parse(dateHeader)?.time ?: System.currentTimeMillis()
                            val deviceTime = System.currentTimeMillis()
                            val skew = (serverTime - deviceTime) / 1000L
                            storedClockSkewSeconds = skew
                            log("[${formatLogTime()}] Parsed Date header '$dateHeader' -> calculated clock skew: ${skew}s")
                            if (completed.compareAndSet(false, true)) {
                                continuation.resume(Result.failure(ClockSkewException(skew, "HTTP 403 clock skew: $skew seconds")))
                            }
                            return
                        } catch (e: Exception) {
                            log("[${formatLogTime()}] Failed parsing server Date header: ${e.message}")
                        }
                    }
                }

                if (completed.compareAndSet(false, true)) {
                    val userFriendly = when (statusCode) {
                        403 -> "Edge-TTS service denied connection (HTTP 403). Clock skew or token invalid."
                        429 -> "Edge-TTS rate limited (HTTP 429). Please wait a moment."
                        else -> "Failed to connect to edge-tts voice service: ${t.localizedMessage ?: t.message ?: "Unknown error"}"
                    }
                    continuation.resume(Result.failure(Exception(userFriendly, t)))
                }
            }
        }

        webSocketRef = client.newWebSocket(request, listener)

        continuation.invokeOnCancellation {
            try {
                webSocketRef?.cancel()
                fileOutputStream.close()
            } catch (e: Exception) {}
        }
    }
}
