package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.CachePolicy
import com.example.data.cache.DetailCacheManager
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.SavedWordEntity
import com.example.data.model.WordMeaningEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlidingTtlCacheTest {

    private lateinit var db: LexiTrioDatabase
    private var simulatedTimeMs = 1_000_000_000_000L

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun batchedAccessTracker_recordsAccessAndFeedsMetadata() = runBlocking {
        val cacheManager = DetailCacheManager(
            database = db,
            api = null,
            policy = CachePolicy(),
            clock = { simulatedTimeMs }
        )

        val entry = DictionaryEntryEntity(id = 101L, headword = "apple", headwordNormalized = "apple", language = "en")
        db.dictionaryDao().insertEntry(entry)

        cacheManager.recordAccess(101L)
        cacheManager.recordAccess(101L)

        val metadata = db.cacheDao().get(101L)
        assertNotNull(metadata)
        assertEquals(2, metadata!!.accessCount)
        assertEquals(simulatedTimeMs, metadata.lastAccessedAt)
    }

    @Test
    fun slidingTtl_evictsDetailsInactiveForOver7DaysButKeepsActiveOnes() = runBlocking {
        val cacheManager = DetailCacheManager(
            database = db,
            api = null,
            policy = CachePolicy(detailTtlMs = 7 * 24 * 3600 * 1000L),
            clock = { simulatedTimeMs }
        )

        val entry1 = DictionaryEntryEntity(id = 1L, headword = "active_word", headwordNormalized = "active_word", language = "en")
        val entry2 = DictionaryEntryEntity(id = 2L, headword = "inactive_word", headwordNormalized = "inactive_word", language = "en")
        db.dictionaryDao().insertEntries(listOf(entry1, entry2))

        val meaning1 = WordMeaningEntity(id = 10L, entryId = 1L, definitionEn = "active def", definitionRu = "", definitionUz = "")
        val meaning2 = WordMeaningEntity(id = 20L, entryId = 2L, definitionEn = "inactive def", definitionRu = "", definitionUz = "")
        db.dictionaryDao().insertMeanings(listOf(meaning1, meaning2))

        cacheManager.recordAccess(1L)
        cacheManager.recordAccess(2L)
        db.cacheDao().markDetailLoaded(listOf(1L, 2L), simulatedTimeMs, "v1")

        // Fast forward 5 days -> Both are still active
        simulatedTimeMs += 5 * 24 * 3600 * 1000L
        // User opens word 1 again -> extends sliding TTL
        cacheManager.recordAccess(1L)

        // Fast forward another 4 days (Total 9 days for word 2, 4 days for word 1)
        simulatedTimeMs += 4 * 24 * 3600 * 1000L

        // Evict expired details
        val evictedCount = cacheManager.evictExpiredDetails()
        assertEquals(1, evictedCount)

        // Word 1 (active) still has full details
        val detail1 = db.dictionaryDao().getFullDictionaryDetail(1L)
        assertNotNull(detail1)
        assertTrue(detail1!!.meanings.isNotEmpty())

        // Word 2 (inactive > 7 days) detail validation is cleared (evicted)
        val metadata2 = db.cacheDao().get(2L)
        assertNotNull(metadata2)
        assertEquals(0L, metadata2!!.detailValidatedAt)
    }

    @Test
    fun savedWords_areProtectedFromSlidingTtlEviction() = runBlocking {
        val cacheManager = DetailCacheManager(
            database = db,
            api = null,
            policy = CachePolicy(detailTtlMs = 7 * 24 * 3600 * 1000L),
            clock = { simulatedTimeMs }
        )

        val entry = DictionaryEntryEntity(id = 99L, headword = "saved_word", headwordNormalized = "saved_word", language = "en")
        db.dictionaryDao().insertEntry(entry)
        db.dictionaryDao().insertMeanings(listOf(WordMeaningEntity(id = 990L, entryId = 99L, definitionEn = "saved def", definitionRu = "", definitionUz = "")))

        db.cacheDao().markDetailLoaded(listOf(99L), simulatedTimeMs, "v1")
        cacheManager.recordAccess(99L)

        // User saves the word to vocabulary collection
        db.vocabularyDao().insertSavedWord(
            SavedWordEntity(entryId = 99L, headword = "saved_word", language = "en")
        )
        cacheManager.refreshPriorities()

        // Fast forward 30 days
        simulatedTimeMs += 30 * 24 * 3600 * 1000L

        // Evict expired details
        val evictedCount = cacheManager.evictExpiredDetails()
        assertEquals(0, evictedCount)

        // Saved word details remain protected
        val detail = db.dictionaryDao().getFullDictionaryDetail(99L)
        assertNotNull(detail)
        assertTrue(detail!!.meanings.isNotEmpty())
    }
}
