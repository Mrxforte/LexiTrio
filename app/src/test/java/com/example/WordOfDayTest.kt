package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.textIn
import com.example.data.repository.DictionaryRepository
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
class WordOfDayTest {
    private lateinit var context: Context
    private lateinit var db: LexiTrioDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java).allowMainThreadQueries().build()
        repository = DictionaryRepository(db.dictionaryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun wordOfDayRequiresBothLanguagesAndReversesSameEntry() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insertEntry(DictionaryEntryEntity(id = 1, headword = "book", headwordNormalized = "book", language = "en", primaryMeaningUz = "kitob"))
        dao.insertEntry(DictionaryEntryEntity(id = 2, headword = "apple", headwordNormalized = "apple", language = "en", primaryMeaningUz = "olma", primaryMeaningRu = "яблоко"))
        dao.insertEntry(DictionaryEntryEntity(id = 3, headword = "missing", headwordNormalized = "missing", language = "en", primaryMeaningUz = " "))

        for (day in 1..30) {
            val word = repository.getWordOfDay(Language.RUSSIAN, Language.UZBEK_LATIN, day)
            assertNotNull(word)
            assertEquals(2L, word!!.id)
            assertEquals("яблоко", word.textIn(Language.RUSSIAN))
            assertEquals("olma", word.textIn(Language.UZBEK_LATIN))
            assertEquals(word.id, repository.getWordOfDay(Language.UZBEK_LATIN, Language.RUSSIAN, day)!!.id)
            val enUz = repository.getWordOfDay(Language.ENGLISH, Language.UZBEK_LATIN, day)!!
            assertTrue(enUz.id == 1L || enUz.id == 2L)
            assertEquals(enUz.id, repository.getWordOfDay(Language.ENGLISH, Language.UZBEK_LATIN, day)!!.id)
        }
    }

    @Test
    fun wordOfDayIsAbsentWhenEitherLanguageIsMissing() = runBlocking {
        assertNull(repository.getWordOfDay(Language.ENGLISH, Language.UZBEK_LATIN, 1))
        db.dictionaryDao().insertEntry(
            DictionaryEntryEntity(id = 1, headword = "book", headwordNormalized = "book", language = "en", primaryMeaningUz = "kitob")
        )
        assertNull(repository.getWordOfDay(Language.RUSSIAN, Language.UZBEK_LATIN, 1))
        assertNotNull(repository.getWordOfDay(Language.ENGLISH, Language.UZBEK_LATIN, 1))
    }

    @Test
    fun wordOfDayRecognizesCyrillicUzbekHeadwords() = runBlocking {
        db.dictionaryDao().insertEntry(
            DictionaryEntryEntity(
                id = 1, headword = "олма", headwordNormalized = "олма", language = "uz-cyrl",
                primaryMeaningEn = "apple"
            )
        )
        val word = repository.getWordOfDay(Language.UZBEK_LATIN, Language.ENGLISH, 1)
        assertNotNull(word)
        assertEquals("олма", word!!.textIn(Language.UZBEK_LATIN))
        assertEquals("apple", word.textIn(Language.ENGLISH))
    }
}
