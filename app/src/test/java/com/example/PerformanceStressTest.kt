package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CurriculumRepositoryImpl
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.LearningRepository
import com.example.data.repository.VocabularyRepository
import com.example.presentation.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceStressTest {

    private lateinit var db: LexiTrioDatabase
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var learningRepository: LearningRepository
    private lateinit var curriculumRepository: CurriculumRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: AppViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dictionaryRepository = DictionaryRepository(db.dictionaryDao())
        vocabularyRepository = VocabularyRepository(db.vocabularyDao())
        learningRepository = LearningRepository(
            db.learningDao(),
            db.conversationGrammarDao(),
            db.quizDao()
        )
        curriculumRepository = CurriculumRepositoryImpl(db.curriculumDao())
        preferencesRepository = UserPreferencesRepository(context)

        viewModel = AppViewModel(
            dictionary = dictionaryRepository,
            vocabulary = vocabularyRepository,
            learning = learningRepository,
            preferencesRepository = preferencesRepository,
            curriculum = curriculumRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun databaseBatchTransactionStress_inserts1000EntriesInSingleTransactionFast() = runTest(testDispatcher) {
        val entries = (1..1000).map { index ->
            DictionaryEntryEntity(
                id = index.toLong(),
                headword = "word_$index",
                headwordNormalized = "word_$index",
                language = if (index % 3 == 0) "en" else if (index % 3 == 1) "ru" else "uz",
                primaryMeaningEn = "meaning_$index",
                primaryMeaningRu = "значение_$index",
                primaryMeaningUz = "ma'nosi_$index"
            )
        }

        val startTime = System.currentTimeMillis()
        db.withTransaction {
            db.dictionaryDao().insertEntries(entries)
        }
        val duration = System.currentTimeMillis() - startTime

        val count = dictionaryRepository.getEntryCount()
        assertEquals(1000, count)
        assertTrue("Batch transaction insertion took too long: ${duration}ms", duration < 2000)
    }

    @Test
    fun concurrentSearchStress_handlesMultipleParallelQueriesWithoutCrashing() = runTest(testDispatcher) {
        val entries = (1..100).map { index ->
            DictionaryEntryEntity(
                id = index.toLong(),
                headword = "testword_$index",
                headwordNormalized = "testword_$index",
                language = "en",
                primaryMeaningUz = "sinov_$index"
            )
        }
        db.dictionaryDao().insertEntries(entries)

        val queries = (1..50).map { "testword_$it" }
        val jobs = queries.map { query ->
            async(testDispatcher) {
                dictionaryRepository.search(
                    query = query,
                    sourceLanguage = Language.ENGLISH,
                    partOfSpeech = null,
                    limit = 10,
                    offset = 0
                )
            }
        }

        val results = jobs.awaitAll()
        assertEquals(50, results.size)
        assertTrue(results.all { it.isNotEmpty() })
    }

    @Test
    fun rapidSearchQueryUpdatesStress_cancelsPreviousDebouncedSearchesAndEmitsFinalResult() = runTest(testDispatcher) {
        val entries = listOf(
            DictionaryEntryEntity(id = 1L, headword = "apple", headwordNormalized = "apple", language = "en", primaryMeaningUz = "olma"),
            DictionaryEntryEntity(id = 2L, headword = "application", headwordNormalized = "application", language = "en", primaryMeaningUz = "ilova"),
            DictionaryEntryEntity(id = 3L, headword = "apply", headwordNormalized = "apply", language = "en", primaryMeaningUz = "qo'llash")
        )
        db.dictionaryDao().insertEntries(entries)

        // Rapidly type characters 'a' -> 'ap' -> 'app' -> 'appl' -> 'apple'
        val typedQueries = listOf("a", "ap", "app", "appl", "apple")
        typedQueries.forEach { query ->
            viewModel.setSearchQuery(query)
        }

        advanceUntilIdle()

        val searchState = viewModel.searchState.first { it.filters.query == "apple" && it.results.isNotEmpty() }
        assertEquals("apple", searchState.filters.query)
        assertEquals("apple", searchState.results.first().headword)
    }
}
