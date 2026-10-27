package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.tts.TtsAudioCacheManager
import com.example.data.tts.TtsEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TtsAudioCacheManagerTest {

    @Test
    fun getCacheKey_generatesDeterministicHash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheManager = TtsAudioCacheManager(context)

        val key1 = cacheManager.getCacheKey("hello", "en", 1.0f, 1.0f, null, TtsEngineType.SYSTEM)
        val key2 = cacheManager.getCacheKey("hello", "en", 1.0f, 1.0f, null, TtsEngineType.SYSTEM)
        val key3 = cacheManager.getCacheKey("hello", "ru", 1.0f, 1.0f, null, TtsEngineType.SYSTEM)

        assertEquals("Identical parameters must produce identical cache keys", key1, key2)
        assertTrue("Different language parameter must produce different cache keys", key1 != key3)
    }

    @Test
    fun saveAndRetrieveCachedAudioFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheManager = TtsAudioCacheManager(context)

        val text = "dictionary"
        val lang = "en"
        val dummyAudio = ByteArray(1024) { 0x42 }

        val saved = cacheManager.saveToCache(text, lang, 1.0f, 1.0f, null, TtsEngineType.SYSTEM, dummyAudio)
        assertNotNull("Saved audio file must not be null", saved)

        val retrieved = cacheManager.getCachedFile(text, lang, 1.0f, 1.0f, null, TtsEngineType.SYSTEM)
        assertNotNull("Retrieved audio file must exist", retrieved)
        assertEquals(1024L, retrieved!!.length())
    }

    @Test
    fun clearCache_removesAllCachedFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheManager = TtsAudioCacheManager(context)

        cacheManager.saveToCache("test1", "en", 1.0f, 1.0f, null, TtsEngineType.SYSTEM, ByteArray(500))
        cacheManager.saveToCache("test2", "ru", 1.0f, 1.0f, null, TtsEngineType.SYSTEM, ByteArray(500))

        assertTrue("Cache size must be > 0", cacheManager.getCacheSizeBytes() > 0)

        val cleared = cacheManager.clearCache()
        assertTrue("clearCache must return true", cleared)
        assertEquals(0L, cacheManager.getCacheSizeBytes())
    }
}
