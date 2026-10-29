package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.database.PreloadCurriculumData
import com.example.data.database.PreloadDictionaryData
import com.example.data.database.PreloadLearningData
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.ReviewQuality
import com.example.data.model.UserProgressEntity
import com.example.data.repository.CurriculumRepositoryImpl
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.VocabularyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseRepositoryIntegrationTest {

    private lateinit var db: LexiTrioDatabase
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var curriculumRepository: CurriculumRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dictionaryRepository = DictionaryRepository(db.dictionaryDao())
        vocabularyRepository = VocabularyRepository(db.vocabularyDao())
        curriculumRepository = CurriculumRepositoryImpl(db.curriculumDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun preloadCorpus_populatesAllDatabaseTablesCorrectly() = runBlocking {
        val bundle = PreloadDictionaryData.getInitialData()
        db.dictionaryDao().insertEntries(bundle.entries)
        db.dictionaryDao().insertMeanings(bundle.meanings)

        val entryCount = dictionaryRepository.getEntryCount()
        assertTrue(entryCount > 0)
        assertEquals(bundle.entries.size, entryCount)

        val courses = PreloadCurriculumData.getInitialCourses()
        db.curriculumDao().insertCourses(courses)
        val loadedCourses = curriculumRepository.getCourses().first()
        assertEquals(courses.size, loadedCourses.size)
    }

    @Test
    fun dictionaryFtsSearch_returnsRankedResultsAcrossLanguages() = runBlocking {
        val entries = listOf(
            DictionaryEntryEntity(
                id = 10L,
                headword = "computer",
                headwordNormalized = "computer",
                language = "en",
                primaryMeaningUz = "kompyuter, hisoblash mashinasi"
            ),
            DictionaryEntryEntity(
                id = 20L,
                headword = "laptop",
                headwordNormalized = "laptop",
                language = "en",
                primaryMeaningUz = "noutbuk, ixcham kompyuter"
            )
        )
        db.dictionaryDao().insertEntries(entries)

        val searchResults = dictionaryRepository.search(
            query = "kompyuter",
            sourceLanguage = Language.ENGLISH,
            partOfSpeech = null,
            limit = 10,
            offset = 0
        )

        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.any { it.headword == "computer" || it.headword == "laptop" })
    }

    @Test
    fun vocabularySm2Review_updatesNextIntervalAndEasinessFactor() = runBlocking {
        val entry = DictionaryEntryEntity(
            id = 500L,
            headword = "freedom",
            headwordNormalized = "freedom",
            language = "en",
            primaryMeaningUz = "ozodlik"
        )
        db.dictionaryDao().insertEntry(entry)

        vocabularyRepository.saveOrUpdateWord(entry, collectionId = 1, notes = "", isFavorite = false)
        val savedWords = vocabularyRepository.getAllSavedWords().first()
        val initialWord = savedWords.first()

        assertEquals(0, initialWord.repetitionNumber)

        val updatedWord = vocabularyRepository.processReview(initialWord, ReviewQuality.GOOD)
        assertEquals(1, updatedWord.repetitionNumber)
        assertEquals(1, updatedWord.intervalDays)
        assertTrue(updatedWord.easeFactor >= 1.3f)
    }
}
