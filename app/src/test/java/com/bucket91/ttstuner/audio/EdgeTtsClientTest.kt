package com.bucket91.ttstuner.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class EdgeTtsClientTest {

    @Test
    fun testSecMsGecComputationWithFixedClock() {
        val fixedUnixSec = 1700000000L
        val (rounded, ticks) = EdgeTtsClient.computeTicksAndRounded(fixedUnixSec)

        // Verify rounding to multiple of 300
        assertEquals(0L, rounded % 300L)
        assertEquals(13344473400L, rounded)
        assertEquals(133444734000000000L, ticks)

        val token = EdgeTtsClient.computeSecMsGecToken(fixedUnixSec)
        assertEquals(64, token.length)
        assertTrue(token.all { it.isDigit() || it in 'A'..'F' })
    }

    @Test
    fun testSsmlEscaping() {
        val input = "Tom & Jerry <cat> \"hello\" 'world'"
        val escaped = EdgeTtsClient.escapeSsml(input)
        assertEquals("Tom &amp; Jerry &lt;cat&gt; &quot;hello&quot; &apos;world&apos;", escaped)
    }

    @Test
    fun testBinaryAudioFrameParsing() {
        val headerText = "X-RequestId:123\r\nPath:audio\r\n\r\n"
        val headerBytes = headerText.toByteArray(StandardCharsets.UTF_8)
        val headerLen = headerBytes.size

        val dummyAudioPayload = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00) // fake mp3 ID3 header

        val totalLength = 2 + headerLen + dummyAudioPayload.size
        val packet = ByteArray(totalLength)
        packet[0] = ((headerLen shr 8) and 0xFF).toByte()
        packet[1] = (headerLen and 0xFF).toByte()
        System.arraycopy(headerBytes, 0, packet, 2, headerLen)
        System.arraycopy(dummyAudioPayload, 0, packet, 2 + headerLen, dummyAudioPayload.size)

        val parsedAudio = EdgeTtsClient.parseBinaryAudioFrame(packet)
        assertNotNull(parsedAudio)
        assertArrayEquals(dummyAudioPayload, parsedAudio)
    }

    @Test
    fun testCacheNamingAndCap() {
        val voice = "bn-BD-PradeepNeural"
        val rate = "+4%"
        val text = "নমস্কার"
        val key = AudioCache.getCacheKey(voice, rate, text)
        assertTrue(key.isNotEmpty())
        assertEquals(32, key.length) // MD5 hex length

        val tempDir = File.createTempFile("tts_cache_test", "").apply {
            delete()
            mkdirs()
        }

        val ttsSubdir = File(tempDir, "tts").apply { mkdirs() }
        for (i in 1..55) {
            val f = File(ttsSubdir, "file_$i.mp3")
            f.writeText("data $i")
            f.setLastModified(System.currentTimeMillis() + i * 1000L)
        }

        assertEquals(55, ttsSubdir.listFiles()?.size)
        AudioCache.pruneCache(tempDir, 50)
        assertEquals(50, ttsSubdir.listFiles()?.size)

        // Oldest files should have been removed
        assertTrue(File(ttsSubdir, "file_55.mp3").exists())

        tempDir.deleteRecursively()
    }
}
