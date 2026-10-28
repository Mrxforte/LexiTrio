package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.DictionaryDownloader
import com.example.data.cloud.DictionarySearchResponseDto
import com.example.data.cloud.DictionarySearchResultDto
import com.example.data.cloud.DictionarySyncPage
import com.example.data.cloud.SyncStateStore
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.WordMeaningEntity
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.SearchCacheVersion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DictionaryFtsSearchTest {

    private lateinit var context: Context
    private lateinit var db: LexiTrioDatabase

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

    private fun entry(
        id: Long,
        headword: String,
        language: String = "en",
        en: String = "",
        ru: String = "",
        uz: String = "",
        rank: Int = 0
    ) = DictionaryEntryEntity(
        id = id,
        headword = headword,
        headwordNormalized = Language.normalizeText(headword),
        language = language,
        frequencyRank = rank,
        primaryMeaningEn = en,
        primaryMeaningRu = ru,
        primaryMeaningUz = uz
    )

    @Test
    fun exactMatchRanksBeforePrefixMatches() = runBlocking {
        db.dictionaryDao().insertEntries(
            listOf(
                entry(1, "bookmark", rank = 900),
                entry(2, "booking", rank = 800),
                entry(3, "book", rank = 1),
                entry(4, "notebook", rank = 999)
            )
        )

        val results = DictionaryRepository(db.dictionaryDao()).search("book", null, null)

        assertEquals(listOf("book", "booking", "bookmark"), results.map { it.headword })
    }

    @Test
    fun prefixResultsAreAlphabeticalAndPagesContinueIntoMeaningMatches() = runBlocking {
        db.dictionaryDao().insertEntries(
            listOf(
                entry(1, "sc", rank = 1),
                entry(2, "sa", rank = 5),
                entry(3, "sab", rank = 9),
                entry(4, "sb", rank = 3),
                entry(5, "saa", rank = 7),
                entry(6, "tree", en = "sweet sap", rank = 100),
                entry(7, "root", en = "underground sprout", rank = 50),
                entry(8, "stone", rank = 2)
            )
        )
        val repository = DictionaryRepository(db.dictionaryDao())

        val everything = repository.search("s", null, null, limit = 100).map { it.headword }
        val paged = (0 until 10).flatMap { page ->
            repository.search("s", null, null, limit = 2, offset = page * 2).map { it.headword }
        }

        // Headwords starting with "s" in A-Z order, then entries matching by meaning (by frequency).
        assertEquals(listOf("sa", "saa", "sab", "sb", "sc", "stone", "tree", "root"), everything)
        assertEquals(everything, paged)
    }

    @Test
    fun pagingPastTheEndReturnsNothing() = runBlocking {
        db.dictionaryDao().insertEntries(listOf(entry(1, "sa"), entry(2, "sb")))

        assertTrue(DictionaryRepository(db.dictionaryDao()).search("s", null, null, limit = 5, offset = 50).isEmpty())
    }

    @Test
    fun multiWordQueryMatchesMeaningsThroughFullText() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1, "apple", en = "a round fruit with red or green skin"))

        val results = DictionaryRepository(db.dictionaryDao()).search("round fru", null, null)

        assertEquals(listOf("apple"), results.map { it.headword })
    }

    @Test
    fun russianAndUzbekHeadwordsAreFoundByPrefix() = runBlocking {
        db.dictionaryDao().insertEntries(
            listOf(
                entry(1, "книга", "ru", en = "book"),
                entry(2, "kitob", "uz", en = "book"),
                entry(3, "o‘rganmoq", "uz", en = "learn")
            )
        )
        val repository = DictionaryRepository(db.dictionaryDao())

        assertEquals("книга", repository.search("кни", Language.RUSSIAN, null).first().headword)
        assertEquals("kitob", repository.search("kit", Language.UZBEK_LATIN, null).first().headword)
        // Typed with an ASCII apostrophe, stored with the typographic one.
        assertEquals("o‘rganmoq", repository.search("o'rgan", Language.UZBEK_LATIN, null).first().headword)
    }

    @Test
    fun updatedEntriesAreReindexed() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntry(entry(1, "apple", en = "fruit"))
        val repository = DictionaryRepository(dao)
        assertEquals(1, repository.search("fruit", null, null).size)

        dao.insertEntry(entry(1, "apple", en = "tasty orchard produce"))
        SearchCacheVersion.bump()

        assertTrue(repository.search("fruit", null, null).isEmpty())
        assertEquals(1, repository.search("orchard", null, null).size)
    }

    @Test
    fun upsertKeepsChildRows() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntry(entry(1, "apple"))
        dao.insertMeanings(
            listOf(WordMeaningEntity(id = 10, entryId = 1, definitionEn = "fruit", definitionRu = "", definitionUz = ""))
        )

        dao.insertEntries(listOf(entry(1, "apple", en = "updated")))

        assertEquals(1, dao.getFullDictionaryDetail(1)!!.meanings.size)
    }

    @Test
    fun cachedServerHitsNeverOverwriteLocalRows() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntry(entry(1, "apple", en = "local"))

        dao.insertEntriesIfAbsent(listOf(entry(1, "apple", en = "remote"), entry(2, "pear")))

        assertEquals("local", dao.getEntryById(1)!!.primaryMeaningEn)
        assertEquals("pear", dao.getEntryById(2)!!.headword)
    }

    @Test
    fun languageFilterListsHeadwordsOfThatLanguageBeforeTranslationMatches() = runBlocking {
        db.dictionaryDao().insertEntries(
            listOf(
                entry(1, "see", en = "look", uz = "ko‘rmoq", rank = 1000),
                entry(2, "sabr", "uz", en = "patience", rank = 10),
                entry(3, "salom", "uz", en = "hello", rank = 5)
            )
        )

        val results = DictionaryRepository(db.dictionaryDao()).search("s", Language.UZBEK_LATIN, null)

        assertEquals(listOf("sabr", "salom", "see"), results.map { it.headword })
    }

    @Test
    fun serverIsAskedWhileARefreshPassIsRunning() {
        val state = SyncStateStore(context)
        state.saveLastSyncAt("2026-01-01T00:00:00Z")
        assertTrue(state.isDictionaryComplete)

        state.isPassRunning = true

        assertFalse(state.isDictionaryComplete)
    }

    @Test
    fun ftsMatchStripsOperatorsAndPrefixesLastWord() {
        assertEquals("round fru*", DictionaryRepository.buildFtsMatch("round fru"))
        // FTS operators are upper-case only, so lower-casing defuses "OR"/"NOT".
        assertEquals("rganmoq or*", DictionaryRepository.buildFtsMatch("\"rganmoq\" OR *"))
        assertNull(DictionaryRepository.buildFtsMatch("-- ()"))
    }

    @Test
    fun serverAnswersWhileTheCatalogIsStillDownloading() = runBlocking {
        val dao = db.dictionaryDao()
        val api = FakeApi(
            search = {
                DictionarySearchResponseDto(
                    results = listOf(
                        DictionarySearchResultDto(id = 500, headword = "perseverance", headwordNormalized = "perseverance")
                    )
                )
            }
        )
        val repository = DictionaryRepository(dao, api, isCatalogComplete = { false })

        val results = repository.search("persev", null, null)

        assertEquals(listOf("perseverance"), results.map { it.headword })
        assertEquals("perseverance", dao.getEntryById(500)?.headword)
    }

    @Test
    fun serverResultsFillTheTailAndNeverReorderLocalOnes() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntries(listOf(entry(1, "kitob", "uz"), entry(2, "kitobxon", "uz")))
        val api = FakeApi(
            search = {
                DictionarySearchResponseDto(
                    results = listOf(
                        DictionarySearchResultDto(id = 900, headword = "getting even", headwordNormalized = "getting even"),
                        DictionarySearchResultDto(id = 1, headword = "kitob", headwordNormalized = "kitob", language = "uz")
                    )
                )
            }
        )

        val results = DictionaryRepository(dao, api, isCatalogComplete = { false }).search("kitob", null, null)

        assertEquals(listOf("kitob", "kitobxon", "getting even"), results.map { it.headword })
    }

    @Test
    fun aSmallRefreshPassDoesNotSwitchSearchToTheServer() = runBlocking {
        val state = SyncStateStore(context).also { it.saveLastSyncAt("2026-01-01T00:00:00Z") }
        var runningDuringPass = true
        val api = FakeApi(sync = { _, _, _, _ ->
            runningDuringPass = state.isPassRunning
            DictionarySyncPage(entries = listOf(entry(1, "a1")), nextAfterId = 1, hasMore = false, serverTime = "t")
        })

        DictionaryDownloader(db, api, state).run()

        assertFalse(runningDuringPass)
        assertTrue(state.isDictionaryComplete)
    }

    @Test
    fun offlineFallsBackToLocalAndDoesNotCacheTheGap() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntry(entry(1, "perseverance"))
        var online = false
        val api = FakeApi(
            search = {
                if (!online) throw IOException("offline")
                DictionarySearchResponseDto(
                    results = listOf(DictionarySearchResultDto(id = 2, headword = "persevere", headwordNormalized = "persevere"))
                )
            }
        )
        val repository = DictionaryRepository(dao, api, isCatalogComplete = { false })

        assertEquals(listOf("perseverance"), repository.search("persev", null, null).map { it.headword })

        online = true
        assertEquals(
            setOf("perseverance", "persevere"),
            repository.search("persev", null, null).map { it.headword }.toSet()
        )
    }

    @Test
    fun completeCatalogNeverCallsTheServer() = runBlocking {
        db.dictionaryDao().insertEntry(entry(1, "apple"))
        val api = FakeApi(search = { error("server must not be asked") })

        val results = DictionaryRepository(db.dictionaryDao(), api, isCatalogComplete = { true })
            .search("app", null, null)

        assertEquals(listOf("apple"), results.map { it.headword })
    }

    @Test
    fun downloaderStoresPagesAndResumesFromItsCursor() = runBlocking {
        val state = SyncStateStore(context).also { it.saveLastSyncAt(null); it.saveDictionaryCursor(0) }
        val pages = mapOf(
            0L to DictionarySyncPage(
                entries = listOf(entry(1, "a1"), entry(2, "a2")),
                nextAfterId = 2, hasMore = true, serverTime = "2026-01-01T00:00:00Z"
            ),
            2L to DictionarySyncPage(
                entries = listOf(entry(3, "a3")),
                nextAfterId = 3, hasMore = false, serverTime = "2026-01-01T00:00:09Z"
            )
        )
        var failOnSecondPage = true
        val requested = mutableListOf<Long>()
        val api = FakeApi(sync = { afterId, _, _, lite ->
            requested += afterId
            assertEquals(true, lite)
            if (afterId == 2L && failOnSecondPage) throw IOException("connection lost")
            pages.getValue(afterId)
        })
        val downloader = DictionaryDownloader(db, api, state)

        runCatching { downloader.run() }
        assertEquals(2L, state.dictionaryCursor)
        assertFalse(state.isDictionaryComplete)
        assertEquals(2, db.dictionaryDao().getEntryCount())

        failOnSecondPage = false
        requested.clear()
        downloader.run()

        assertEquals(listOf(2L), requested)
        assertEquals(3, db.dictionaryDao().getEntryCount())
        assertTrue(state.isDictionaryComplete)
        // The watermark is when the pass began (first page), even across the resume.
        assertEquals("2026-01-01T00:00:00Z", state.lastSyncAt)
    }

    @Test
    fun downloaderPausesWithinItsBudgetAndKeepsTheIncrementalWatermark() = runBlocking {
        val state = SyncStateStore(context).also { it.saveLastSyncAt("2026-01-01T00:00:00Z") }
        val sinceSeen = mutableListOf<String?>()
        val api = FakeApi(sync = { afterId, _, since, _ ->
            sinceSeen += since
            if (afterId == 0L) {
                DictionarySyncPage(entries = listOf(entry(1, "a1")), nextAfterId = 1, hasMore = true, serverTime = "2026-02-02T00:00:00Z")
            } else {
                DictionarySyncPage(entries = listOf(entry(2, "a2")), nextAfterId = 2, hasMore = false, serverTime = "2026-02-02T00:05:00Z")
            }
        })

        assertFalse(DictionaryDownloader(db, api, state, budgetMs = 0).run())
        assertFalse(state.isDictionaryComplete)
        assertTrue(DictionaryDownloader(db, api, state).run())

        assertEquals(listOf<String?>("2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"), sinceSeen)
        assertEquals("2026-02-02T00:00:00Z", state.lastSyncAt)
        assertEquals(2, db.dictionaryDao().getEntryCount())
        assertTrue(state.isDictionaryComplete)
    }

    @Test
    fun migrationAddsAnIndexRoomAccepts() {
        val file = context.getDatabasePath("migration_test.db")
        file.parentFile?.mkdirs()
        file.delete()

        // 1. Create the current schema, then strip the index to mimic a v5 database.
        Room.databaseBuilder(context, LexiTrioDatabase::class.java, file.name).build().apply {
            openHelper.writableDatabase.run {
                execSQL("INSERT INTO dictionary_entries (id, headword, headwordNormalized, language, phoneticIpa, partOfSpeech, cefrLevel, frequencyRank, lemma, alternativeSpellings, transliteration, etymology, verified, source, primaryMeaningEn, primaryMeaningRu, primaryMeaningUz) VALUES (1, 'apple', 'apple', 'en', '', 'noun', 'B1', 0, '', '', '', '', 1, 's', 'fruit', '', '')")
                listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach {
                    execSQL("DROP TRIGGER room_fts_content_sync_dictionary_fts_$it")
                }
                execSQL("DROP TABLE dictionary_fts")
                version = 5
            }
            close()
        }

        // 2. Reopen with only the 5 -> 6 migration; Room validates the result.
        val migrated = Room.databaseBuilder(context, LexiTrioDatabase::class.java, file.name)
            .addMigrations(LexiTrioDatabase.MIGRATION_5_6, LexiTrioDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val results = runBlocking { DictionaryRepository(migrated.dictionaryDao()).search("fruit", null, null) }
            assertEquals(listOf("apple"), results.map { it.headword })

            // Triggers survived: later writes keep the index current.
            runBlocking { migrated.dictionaryDao().insertEntry(entry(2, "pear", en = "juicy")) }
            assertEquals(1, runBlocking { migrated.dictionaryDao().searchFullText("juicy*", "juicy", "juicy\uFFFF", null, null, 5, 0) }.size)
        } finally {
            migrated.close()
            file.delete()
        }
    }
}

private typealias FakeApi = FakeCloudApi
