package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.TranslationEntity
import com.example.data.repository.DictionaryRepository
import kotlinx.coroutines.runBlocking
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
}
