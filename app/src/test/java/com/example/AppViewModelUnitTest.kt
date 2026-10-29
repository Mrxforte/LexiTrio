package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CurriculumRepositoryImpl
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.LearningRepository
import com.example.data.repository.VocabularyRepository
import com.example.presentation.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppViewModelUnitTest {

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
    fun setSearchQuery_updatesSearchFiltersAndResults() = runTest(testDispatcher) {
        val entry = DictionaryEntryEntity(
            id = 100L,
            headword = "book",
            headwordNormalized = "book",
            language = "en",
            primaryMeaningUz = "kitob"
        )
        db.dictionaryDao().insertEntry(entry)

        viewModel.setSearchQuery("book")
        advanceUntilIdle()

        val searchState = viewModel.searchState.first { it.results.isNotEmpty() }
        assertEquals("book", searchState.filters.query)
        assertEquals(1, searchState.results.size)
        assertEquals("book", searchState.results.first().headword)
    }

    @Test
    fun saveWord_addsWordToVocabularyRepository() = runTest(testDispatcher) {
        val entry = DictionaryEntryEntity(
            id = 200L,
            headword = "school",
            headwordNormalized = "school",
            language = "en",
            primaryMeaningUz = "maktab"
        )

        viewModel.saveWord(entry, collectionId = 1, isFavorite = true)
        advanceUntilIdle()

        val saved = vocabularyRepository.getAllSavedWords().first()
        assertEquals(1, saved.size)
        assertEquals("school", saved.first().headword)
        assertTrue(saved.first().isFavorite)
    }

    @Test
    fun processReview_updatesWordLearningStatusAndDueCount() = runTest(testDispatcher) {
        val entry = DictionaryEntryEntity(
            id = 300L,
            headword = "teacher",
            headwordNormalized = "teacher",
            language = "en",
            primaryMeaningUz = "o'qituvchi"
        )
        viewModel.saveWord(entry)
        advanceUntilIdle()

        val savedWord = vocabularyRepository.getAllSavedWords().first().first()
        viewModel.processReview(savedWord, ReviewQuality.EASY)
        advanceUntilIdle()

        val updatedWords = vocabularyRepository.getAllSavedWords().first()
        assertEquals(LearningStatus.LEARNING, updatedWords.first().status)
    }

    @Test
    fun setDictionaryLanguages_updatesPreferences() = runTest(testDispatcher) {
        viewModel.setDictionaryLanguages(Language.RUSSIAN, Language.UZBEK_LATIN)
        advanceUntilIdle()

        val prefs = preferencesRepository.preferences.first { it.searchSourceLanguage == Language.RUSSIAN }
        assertEquals(Language.RUSSIAN, prefs.searchSourceLanguage)
        assertEquals(Language.UZBEK_LATIN, prefs.searchTargetLanguage)
    }
}
