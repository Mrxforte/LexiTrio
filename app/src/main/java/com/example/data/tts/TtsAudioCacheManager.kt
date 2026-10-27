package com.example.data.tts

import android.content.Context
import java.io.File
import java.security.MessageDigest

class TtsAudioCacheManager(
    private val context: Context,
    private val maxCacheSizeBytes: Long = 20 * 1024 * 1024L // 20 MB default limit
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "tts_audio_cache").apply { if (!exists()) mkdirs() }
    }

    fun getCacheKey(
        text: String,
        languageCode: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        engineType: TtsEngineType
    ): String {
        val raw = "$text|$languageCode|$rate|$pitch|${voiceId ?: ""}|${engineType.name}"
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getCachedFile(
        text: String,
        languageCode: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        engineType: TtsEngineType
    ): File? {
        val key = getCacheKey(text, languageCode, rate, pitch, voiceId, engineType)
        val file = File(cacheDir, "$key.wav")
        return if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else null
    }

    fun saveToCache(
        text: String,
        languageCode: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        engineType: TtsEngineType,
        audioBytes: ByteArray
    ): File? {
        return try {
            trimCacheIfNeeded(audioBytes.size.toLong())
            val key = getCacheKey(text, languageCode, rate, pitch, voiceId, engineType)
            val file = File(cacheDir, "$key.wav")
            file.writeBytes(audioBytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun trimCacheIfNeeded(incomingSizeBytes: Long) {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }

        if (totalSize + incomingSizeBytes <= maxCacheSizeBytes) return

        // Sort files by last modified ascending (LRU)
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            if (totalSize + incomingSizeBytes <= maxCacheSizeBytes) break
            val len = f.length()
            if (f.delete()) {
                totalSize -= len
            }
        }
    }

    fun getCacheSizeBytes(): Long {
        val files = cacheDir.listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    fun clearCache(): Boolean {
        val files = cacheDir.listFiles() ?: return true
        var allDeleted = true
        for (f in files) {
            if (!f.delete()) allDeleted = false
        }
        return allDeleted
    }
}
