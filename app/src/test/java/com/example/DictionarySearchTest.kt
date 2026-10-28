package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.DictionaryDirection
import com.example.data.model.textIn
import com.example.data.model.TranslationEntity
import com.example.data.repository.DictionaryRepository
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionarySearchTest {

    private lateinit var db: LexiTrioDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DictionaryRepository(db.dictionaryDao())

        runBlocking {
            val dao = db.dictionaryDao()
            // 1. English headword entry for "apple"
            val id1 = dao.insertEntry(
                DictionaryEntryEntity(
                    id = 1L,
                    headword = "apple",
                    headwordNormalized = "apple",
                    language = "en",
                    primaryMeaningEn = "a round fruit with red or green skin",
                    primaryMeaningRu = "яблоко",
                    primaryMeaningUz = "olma"
                )
            )
            dao.insertTranslations(
                listOf(
                    TranslationEntity(id = 101L, entryId = id1, targetLanguage = "ru", translationText = "яблоко"),
                    TranslationEntity(id = 102L, entryId = id1, targetLanguage = "uz", translationText = "olma")
                )
            )

            // 2. English headword entry for "book"
            val id2 = dao.insertEntry(
                DictionaryEntryEntity(
                    id = 2L,
                    headword = "book",
                    headwordNormalized = "book",
                    language = "en",
                    primaryMeaningEn = "a written work published in printed form",
                    primaryMeaningRu = "книга",
                    primaryMeaningUz = "kitob"
                )
            )
            dao.insertTranslations(
                listOf(
                    TranslationEntity(id = 201L, entryId = id2, targetLanguage = "ru", translationText = "книга"),
                    TranslationEntity(id = 202L, entryId = id2, targetLanguage = "uz", translationText = "kitob")
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSearchUzbekWordReturnsEnglishEntryWithUzbekMeaning() = runBlocking {
        // Search "olma" with Uzbek language filter
        val resultsUz = repository.search("olma", Language.UZBEK_LATIN, null)
        assertTrue("Searching 'olma' in Uzbek should find entries", resultsUz.isNotEmpty())
        assertEquals("apple", resultsUz.first().headword)

        // Search "kitob" with Uzbek language filter
        val resultsKitob = repository.search("kitob", Language.UZBEK_LATIN, null)
        assertTrue("Searching 'kitob' in Uzbek should find entries", resultsKitob.isNotEmpty())
        assertEquals("book", resultsKitob.first().headword)
    }

    @Test
    fun testSearchRussianWordReturnsEnglishEntryWithRussianMeaning() = runBlocking {
        // Search "яблоко" with Russian language filter
        val resultsRu = repository.search("яблоко", Language.RUSSIAN, null)
        assertTrue("Searching 'яблоко' in Russian should find entries", resultsRu.isNotEmpty())
        assertEquals("apple", resultsRu.first().headword)

        // Search "книга" with Russian language filter
        val resultsKniga = repository.search("книга", Language.RUSSIAN, null)
        assertTrue("Searching 'книга' in Russian should find entries", resultsKniga.isNotEmpty())
        assertEquals("book", resultsKniga.first().headword)
    }

    @Test
    fun testSearchAllLanguagesWithoutFilter() = runBlocking {
        val resultsUz = repository.search("olma", null, null)
        assertTrue(resultsUz.isNotEmpty())
        assertEquals("apple", resultsUz.first().headword)

        val resultsRu = repository.search("яблоко", null, null)
        assertTrue(resultsRu.isNotEmpty())
        assertEquals("apple", resultsRu.first().headword)

        val resultsEn = repository.search("apple", null, null)
        assertTrue(resultsEn.isNotEmpty())
        assertEquals("apple", resultsEn.first().headword)
    }

    @Test
    fun testUzbekCyrillicSearch() = runBlocking {
        // Search "олма" (Cyrillic for olma)
        val resultsCyrillic = repository.search("олма", Language.UZBEK_CYRILLIC, null)
        assertTrue("Cyrillic Uzbek search for 'олма' should find 'apple'", resultsCyrillic.isNotEmpty())
        assertEquals("apple", resultsCyrillic.first().headword)
    }

    @Test
    fun directedSearchUsesOnlySourceAndRequiresTarget() = runBlocking {
        val enUz = repository.searchDirected("apple", Language.ENGLISH, Language.UZBEK_LATIN)
        assertEquals(listOf("apple"), enUz.map { it.headword })
        assertTrue(repository.searchDirected("apple", Language.RUSSIAN, Language.UZBEK_LATIN).isEmpty())
        val uzEn = repository.searchDirected("olma", Language.UZBEK_LATIN, Language.ENGLISH)
        assertEquals(listOf("apple"), uzEn.map { it.headword })
        assertEquals("olma", uzEn.first().textIn(Language.UZBEK_LATIN))
        assertEquals("apple", uzEn.first().textIn(Language.ENGLISH))
        assertEquals(listOf("apple"), repository.searchDirected("яблоко", Language.RUSSIAN, Language.UZBEK_LATIN).map { it.headword })
        assertTrue(repository.searchDirected("olma", Language.ENGLISH, Language.RUSSIAN).isEmpty())
        assertEquals(listOf("apple"), repository.searchDirected("олма", Language.UZBEK_LATIN, Language.ENGLISH).map { it.headword })
        assertEquals(listOf("apple"), repository.searchDirected("apple", Language.ENGLISH, Language.UZBEK_LATIN, limit = 1).map { it.headword })
        assertTrue(repository.searchDirected("apple", Language.ENGLISH, Language.UZBEK_LATIN, limit = 1, offset = 1).isEmpty())
        db.dictionaryDao().insertEntry(
            DictionaryEntryEntity(
                id = 3, headword = "pear", headwordNormalized = "pear", language = "en",
                primaryMeaningRu = "груша"
            )
        )
        assertTrue(repository.searchDirected("pear", Language.ENGLISH, Language.UZBEK_LATIN).isEmpty())
        assertEquals(listOf("pear"), repository.searchDirected("pear", Language.ENGLISH, Language.RUSSIAN).map { it.headword })
    }

    @Test
    fun directionSelectionAndSwapKeepTwoDifferentLanguages() {
        val direction = DictionaryDirection.from(Language.ENGLISH, Language.UZBEK_LATIN)
        assertEquals(DictionaryDirection(Language.UZBEK_LATIN, Language.ENGLISH), direction.swap())
        assertEquals(direction.swap(), direction.withSource(Language.UZBEK_LATIN))
        assertEquals(DictionaryDirection(Language.ENGLISH, Language.RUSSIAN), direction.withTarget(Language.RUSSIAN))
        assertEquals(DictionaryDirection(Language.UZBEK_LATIN, Language.ENGLISH),
            DictionaryDirection.from(Language.UZBEK_LATIN, Language.UZBEK_CYRILLIC))
    }

    @Test
    fun dictionaryDirectionPersistsTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UserPreferencesRepository(context)
        val original = preferences.preferences.first()
        try {
            preferences.setDictionaryLanguages(Language.RUSSIAN, Language.ENGLISH)
            val reloaded = UserPreferencesRepository(context).preferences.first()
            assertEquals(Language.RUSSIAN, reloaded.searchSourceLanguage)
            assertEquals(Language.ENGLISH, reloaded.searchTargetLanguage)
        } finally {
            val originalDirection = DictionaryDirection.from(original.searchSourceLanguage, original.searchTargetLanguage)
            preferences.setDictionaryLanguages(originalDirection.source, originalDirection.target)
        }
    }
}
