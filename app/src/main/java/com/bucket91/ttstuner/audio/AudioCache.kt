package com.bucket91.ttstuner.audio

import java.io.File
import java.security.MessageDigest

object AudioCache {

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getCacheKey(voice: String, rate: String, text: String): String {
        return md5("$voice|$rate|$text")
    }

    fun getCacheFile(baseDir: File, voice: String, rate: String, text: String, isFallback: Boolean): File {
        val ttsDir = File(baseDir, "tts")
        ttsDir.mkdirs()
        val extension = if (isFallback) "wav" else "mp3"
        val filename = "${getCacheKey(voice, rate, text)}.$extension"
        return File(ttsDir, filename)
    }

    fun isCacheValid(file: File): Boolean {
        return file.exists() && file.length() > 1000L
    }

    fun pruneCache(baseDir: File, maxFiles: Int = 50) {
        val ttsDir = File(baseDir, "tts")
        if (!ttsDir.exists() || !ttsDir.isDirectory) return

        val files = ttsDir.listFiles() ?: return
        if (files.size > maxFiles) {
            // Sort by last modified ascending (oldest first)
            val sorted = files.sortedBy { it.lastModified() }
            val toDeleteCount = files.size - maxFiles
            for (i in 0 until toDeleteCount) {
                sorted[i].delete()
            }
        }
    }
}
