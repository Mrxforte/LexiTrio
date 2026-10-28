package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.CachePolicy
import com.example.data.cache.DetailCacheManager
import com.example.data.cloud.DictionaryDetailsResponse
import com.example.data.cloud.DictionaryDownloader
import com.example.data.cloud.DictionarySyncPage
import com.example.data.cloud.SyncStateStore
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.SavedWordEntity
import com.example.data.model.TopicWordCrossRef
import com.example.data.model.TranslationEntity
import com.example.data.model.WordMeaningEntity
import com.example.data.repository.DictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveCacheTest {

    private lateinit var context: Context
    private lateinit var db: LexiTrioDatabase
    private var now = 1_000_000_000L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(id: Long, headword: String = "word$id") = DictionaryEntryEntity(
        id = id,
        headword = headword,
        headwordNormalized = Language.normalizeText(headword),
        language = "en"
    )

    private fun details(ids: List<Long>, translation: (Long) -> String = { "tr$it" }) = DictionaryDetailsResponse(
        entries = ids.map { entry(it) },
        meanings = ids.map { WordMeaningEntity(id = it * 10, entryId = it, definitionEn = "def$it", definitionRu = "", definitionUz = "") },
        translations = ids.map { TranslationEntity(id = it * 100, entryId = it, targetLanguage = "uz", translationText = translation(it)) },
        serverTime = "t$now"
    )

    private fun requestedIds(raw: String) = raw.split(",").map { it.toLong() }

    private fun manager(api: FakeCloudApi?, policy: CachePolicy = CachePolicy()) =
        DetailCacheManager(db, api, policy) { now }

    private suspend fun translationsOf(id: Long) =
        db.dictionaryDao().getFullDictionaryDetail(id)!!.translations.map { it.translationText }

    @Test
    fun detailIsLoadedOnceThenServedFromTheCacheUntilItsTtlExpires() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1))
        var calls = 0
        var text = "a"
        val api = FakeCloudApi(details = { ids -> calls++; details(requestedIds(ids)) { text } })
        val cache = manager(api)

        assertTrue(cache.refreshDetail(1))
        assertEquals(listOf("a"), translationsOf(1))

        assertFalse("fresh content is a cache hit", cache.refreshDetail(1))
        assertEquals(1, calls)

        now += CachePolicy().detailTtlMs + 1
        text = "b"
        assertTrue("expired content is revalidated", cache.refreshDetail(1))
        assertEquals(2, calls)
        assertEquals("rows removed on the server disappear locally", listOf("b"), translationsOf(1))
    }

    @Test
    fun offlineRefreshKeepsTheLocalCopy() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1))
        db.dictionaryDao().insertTranslations(
            listOf(TranslationEntity(id = 7, entryId = 1, targetLanguage = "uz", translationText = "kept"))
        )
        val cache = manager(FakeCloudApi(details = { throw IOException("offline") }))

        assertFalse(cache.refreshDetail(1))
        assertEquals(listOf("kept"), translationsOf(1))
        assertNull(db.cacheDao().get(1)?.takeIf { it.detailValidatedAt > 0 })
    }

    @Test
    fun wordsRemovedOnTheServerAreDroppedButSavedWordsSurvive() = runBlocking {
        db.dictionaryDao().insertEntries(listOf(entry(1), entry(2)))
        db.vocabularyDao().insertSavedWord(SavedWordEntity(entryId = 2, headword = "word2"))
        val cache = manager(FakeCloudApi(details = { DictionaryDetailsResponse(missingIds = listOf(1, 2)) }))

        cache.refreshDetail(1)
        cache.refreshDetail(2)

        assertNull(db.dictionaryDao().getEntryById(1))
        assertNotNull(db.dictionaryDao().getEntryById(2))
    }

    @Test
    fun accessesFeedTheCacheMetadata() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1))
        val cache = manager(null)

        repeat(3) { cache.recordAccess(1) }

        val metadata = db.cacheDao().get(1)!!
        assertEquals(3, metadata.accessCount)
        assertEquals(now, metadata.lastAccessedAt)
    }

    @Test
    fun scoreRewardsFrequencyAndRecency() {
        val policy = CachePolicy()
        val day = CachePolicy.DAY_MS
        assertTrue(policy.score(10, now, now) > policy.score(2, now, now))
        assertTrue(policy.score(3, now, now) > policy.score(3, now - 30 * day, now))
        assertEquals(0.0, policy.score(0, 0, now), 0.0)
    }

    @Test
    fun evictionRemovesLowestScoredDetailsAndNeverTouchesProtectedOrCoreContent() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntries((1L..6L).map { entry(it) })
        db.vocabularyDao().insertSavedWord(SavedWordEntity(entryId = 5, headword = "word5"))
        db.learningDao().insertTopicWordCrossRefs(listOf(TopicWordCrossRef(topicId = 1, wordId = 6)))
        val api = FakeCloudApi(details = { ids -> details(requestedIds(ids)) })
        val cache = manager(api, CachePolicy(maxDetailedEntries = 2))
        val accesses = mapOf(1L to 5, 2L to 1, 3L to 3, 4L to 0)
        accesses.forEach { (id, count) -> repeat(count) { cache.recordAccess(id) } }
        (1L..6L).forEach { cache.refreshDetail(it) }
        assertEquals(6, db.cacheDao().countDetailed())

        val evicted = cache.evictIfNeeded()

        assertEquals(4, evicted)
        // Kept: the saved word (5) and the topic word (6). Unprotected words are all
        // over budget here, so every one of them lost its lazy content.
        assertEquals(listOf("tr5"), translationsOf(5))
        assertEquals(listOf("tr6"), translationsOf(6))
        listOf(1L, 2L, 3L, 4L).forEach { id ->
            assertTrue("word $id keeps no lazy rows", translationsOf(id).isEmpty())
            assertNotNull("word $id stays searchable", dao.getEntryById(id))
            assertEquals("meanings are core content", 1, dao.getFullDictionaryDetail(id)!!.meanings.size)
            assertEquals(0L, db.cacheDao().get(id)!!.detailValidatedAt)
        }
        assertEquals(2, db.cacheDao().countDetailed())
    }

    @Test
    fun evictionStopsAtTheBudgetAndPrefersLowScores() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntries((1L..4L).map { entry(it) })
        val cache = manager(FakeCloudApi(details = { ids -> details(requestedIds(ids)) }), CachePolicy(maxDetailedEntries = 2))
        mapOf(1L to 5, 2L to 1, 3L to 3, 4L to 0).forEach { (id, count) -> repeat(count) { cache.recordAccess(id) } }
        (1L..4L).forEach { cache.refreshDetail(it) }

        assertEquals(2, cache.evictIfNeeded())

        assertEquals(listOf("tr1"), translationsOf(1))
        assertEquals(listOf("tr3"), translationsOf(3))
        assertTrue(translationsOf(2).isEmpty())
        assertTrue(translationsOf(4).isEmpty())
        assertEquals("nothing more to evict", 0, cache.evictIfNeeded())
        // An evicted word is simply a cache miss: it is fetched again on demand.
        assertTrue(cache.refreshDetail(2))
        assertEquals(listOf("tr2"), translationsOf(2))
    }

    @Test
    fun prefetchIsBoundedAndStartsWithProtectedWords() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntries((1L..20L).map { entry(it) })
        db.vocabularyDao().insertSavedWord(SavedWordEntity(entryId = 20, headword = "word20"))
        val batches = mutableListOf<List<Long>>()
        val api = FakeCloudApi(details = { ids -> requestedIds(ids).also { batches += it }.let { details(it) } })
        val cache = manager(api, CachePolicy(prefetchBatchSize = 5, prefetchMaxRequestsPerRun = 2))
        (1L..19L).forEach { id -> repeat(id.toInt()) { cache.recordAccess(id) } }

        val stored = cache.prefetch()

        assertEquals(10, stored)
        assertEquals(2, batches.size)
        assertTrue(batches.all { it.size <= 5 })
        assertTrue("the saved word comes first", 20L in batches.first())
        // Then the most used words.
        assertEquals(setOf(20L, 19L, 18L, 17L, 16L, 15L, 14L, 13L, 12L, 11L), batches.flatten().toSet())
        assertEquals(10, db.cacheDao().countDetailed())
    }

    @Test
    fun prefetchWhileOfflineStoresNothing() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1))
        val cache = manager(FakeCloudApi(details = { throw IOException("offline") }))
        cache.recordAccess(1)

        assertEquals(0, cache.prefetch())
        assertEquals(0, db.cacheDao().countDetailed())
    }

    @Test
    fun deltaSyncAppliesDeletionMarkersButKeepsSavedWords() = runBlocking {
        db.dictionaryDao().insertEntries(listOf(entry(1), entry(2), entry(3)))
        db.vocabularyDao().insertSavedWord(SavedWordEntity(entryId = 2, headword = "word2"))
        val state = SyncStateStore(context).also { it.saveLastSyncAt("2026-01-01T00:00:00Z") }
        val api = FakeCloudApi(sync = { _, _, since, _ ->
            assertEquals("2026-01-01T00:00:00Z", since)
            DictionarySyncPage(deletedIds = listOf(1, 2), hasMore = false, serverTime = "2026-02-01T00:00:00Z")
        })

        assertTrue(DictionaryDownloader(db, api, state).run())

        assertNull(db.dictionaryDao().getEntryById(1))
        assertNotNull(db.dictionaryDao().getEntryById(2))
        assertNotNull(db.dictionaryDao().getEntryById(3))
        assertEquals("2026-02-01T00:00:00Z", state.lastSyncAt)
    }

    @Test
    fun deletedWordsLeaveNoSearchIndexEntries() = runBlocking {
        db.dictionaryDao().insertEntries(listOf(entry(1, "ghost"), entry(2, "ghoul")))
        val state = SyncStateStore(context).also { it.saveLastSyncAt("2026-01-01T00:00:00Z") }
        val api = FakeCloudApi(sync = { _, _, _, _ -> DictionarySyncPage(deletedIds = listOf(1), serverTime = "t") })
        DictionaryDownloader(db, api, state).run()

        val results = DictionaryRepository(db.dictionaryDao()).search("gho", null, null)

        assertEquals(listOf("ghoul"), results.map { it.headword })
    }

    @Test
    fun migrationAddsCacheMetadata() {
        val file = context.getDatabasePath("cache_migration_test.db")
        file.parentFile?.mkdirs()
        file.delete()
        Room.databaseBuilder(context, LexiTrioDatabase::class.java, file.name).build().apply {
            openHelper.writableDatabase.run {
                execSQL("DROP TABLE cache_metadata")
                version = 6
            }
            close()
        }

        val migrated = Room.databaseBuilder(context, LexiTrioDatabase::class.java, file.name)
            .addMigrations(LexiTrioDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                migrated.dictionaryDao().insertEntry(entry(1))
                DetailCacheManager(migrated, null) { now }.recordAccess(1)
                assertEquals(1, migrated.cacheDao().get(1)!!.accessCount)
            }
        } finally {
            migrated.close()
            file.delete()
        }
    }

    @Test
    fun searchStaysFastOnALargeDictionary() = runBlocking {
        val dao = db.dictionaryDao()
        val total = 50_000
        val insertMs = elapsedMs {
            (0 until total).chunked(5_000).forEach { chunk ->
                dao.insertEntries(
                    chunk.map { i ->
                        val word = "w" + i.toString(36).padStart(4, '0')
                        DictionaryEntryEntity(
                            id = i + 1L, headword = word, headwordNormalized = word, language = if (i % 3 == 0) "ru" else "en",
                            frequencyRank = i % 1000, primaryMeaningEn = "meaning of $word", primaryMeaningUz = "ma'no $word"
                        )
                    }
                )
            }
        }
        val repository = DictionaryRepository(dao)

        val cold = elapsedMs { repository.search("w00", null, null) }
        val warm = elapsedMs { repository.search("w00", null, null) }
        val deepPage = elapsedMs { repository.search("w", null, null, limit = 50, offset = 20_000) }
        val meaning = elapsedMs { repository.search("meaning w01", null, null) }

        println("BENCH rows=$total insert=${insertMs}ms cold=${cold}ms warm=${warm}ms deepPage=${deepPage}ms meaningMatch=${meaning}ms")
        assertTrue("cold search took ${cold}ms", cold < 1_500)
        assertTrue("deep page took ${deepPage}ms", deepPage < 1_500)
        assertTrue("warm search must be a cache hit", warm <= cold)
        assertEquals(50, repository.search("w", null, null, limit = 50, offset = 20_000).size)
    }

    private inline fun elapsedMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
