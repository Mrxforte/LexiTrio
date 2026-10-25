package com.example.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import com.example.data.model.QuizType
import com.example.domain.repository.DictionaryImportRepository
import com.example.domain.repository.DictionaryRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferenceRepository
import com.example.domain.repository.VocabularyRepository
import com.example.domain.usecase.GetQuizQuestionsUseCase
import com.example.domain.usecase.ProcessWordReviewUseCase
import com.example.domain.usecase.RecordQuizAttemptUseCase
import com.example.domain.usecase.SaveWordUseCase
import com.example.domain.usecase.SearchDictionaryUseCase
import com.example.domain.usecase.UpdateLearningProgressUseCase
import com.example.domain.model.ImportProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val dictionary: DictionaryRepository,
    private val vocabulary: VocabularyRepository,
    private val learning: LearningRepository,
    private val preferencesRepository: PreferenceRepository,
    private val dictionaryImport: DictionaryImportRepository
) : ViewModel() {
    private val searchDictionary = SearchDictionaryUseCase(dictionary)
    private val saveWordUseCase = SaveWordUseCase(vocabulary)
    private val processWordReview = ProcessWordReviewUseCase(vocabulary)
    private val getQuizQuestions = GetQuizQuestionsUseCase(learning)
    private val recordQuizAttempt = RecordQuizAttemptUseCase(learning)
    private val updateProgress = UpdateLearningProgressUseCase(learning)

    val entryCount = dictionary.getEntryCountFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )
    val recentSearches = dictionary.getRecentSearches().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val progress = learning.getUserProgress().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )
    val dueWordsCount = vocabulary.getDueWordsCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )
    val topics = learning.getAllTopics().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val savedWords = vocabulary.getAllSavedWords().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val dueWords = vocabulary.getDueWords().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val difficultWords = vocabulary.getWordsByStatus(LearningStatus.DIFFICULT).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val masteredWords = vocabulary.getWordsByStatus(LearningStatus.MASTERED).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val collections = vocabulary.getAllCollections().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val quizAttempts = learning.getRecentAttempts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val grammarLessons = learning.getAllGrammarLessons().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val conversations = learning.getAllConversations().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val preferences = preferencesRepository.preferences.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.example.data.preferences.AppPreferences()
    )
    val importProgress = dictionaryImport.progress.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportProgress()
    )

    suspend fun search(query: String, language: Language?, partOfSpeech: String?): List<DictionaryEntryEntity> =
        searchDictionary(query, language, partOfSpeech)

    fun recordSearch(query: String, language: String) =
        viewModelScope.launch { dictionary.recordSearch(query, language) }

    fun clearSearchHistory() = viewModelScope.launch { dictionary.clearSearchHistory() }
    suspend fun getRandomWord(): DictionaryEntryEntity? = dictionary.getRandomWord()
    suspend fun getFullDetail(id: Long) = dictionary.getFullDetail(id)
    suspend fun getTopicById(id: Long) = learning.getTopicById(id)
    fun getWordsForTopic(id: Long) = learning.getWordsForTopic(id)
    suspend fun getConversationById(id: Long) = learning.getConversationById(id)
    fun getConversationTurns(id: Long) = learning.getConversationTurns(id)
    suspend fun getGrammarLessonById(id: Long) = learning.getGrammarLessonById(id)
    suspend fun getRandomQuiz(count: Int, type: QuizType?, category: String?) =
        getQuizQuestions(count, type, category)

    fun getSavedWords() = vocabulary.getAllSavedWords()
    fun getWordsByStatus(status: LearningStatus) = vocabulary.getWordsByStatus(status)
    fun getDueWords() = vocabulary.getDueWords()
    fun getWordsCount() = dictionary.getEntryCountFlow()
    fun importFromUri(uri: Uri) =
        viewModelScope.launch { dictionaryImport.importFromUri(uri) }
    fun sampleJsonlTemplate() = dictionaryImport.getSampleJsonlTemplate()

    suspend fun saveWord(
        entry: DictionaryEntryEntity,
        collectionId: Long = 1,
        notes: String = "",
        isFavorite: Boolean = false
    ) = saveWordUseCase(entry, collectionId, notes, isFavorite)

    suspend fun toggleFavorite(word: SavedWordEntity) = vocabulary.toggleFavorite(word)
    suspend fun processReview(word: SavedWordEntity, quality: ReviewQuality) =
        processWordReview(word, quality)
    suspend fun createCollection(title: String, description: String) =
        vocabulary.createCollection(title, description)
    suspend fun addProgress(
        xpGain: Int,
        isQuiz: Boolean = false,
        wordsReviewedDelta: Int = 0
    ) = updateProgress(xpGain, isQuiz, wordsReviewedDelta)
    suspend fun saveQuizAttempt(
        category: String,
        total: Int,
        correct: Int,
        timeSec: Int,
        xp: Int
    ) = recordQuizAttempt(category, total, correct, timeSec, xp)

    fun setAppLanguage(language: Language) =
        viewModelScope.launch { preferencesRepository.setAppLanguage(language) }
    fun setSearchSourceLanguage(language: Language) =
        viewModelScope.launch { preferencesRepository.setSearchSourceLanguage(language) }
    fun setSpeechRate(rate: Float) =
        viewModelScope.launch { preferencesRepository.setSpeechRate(rate) }
    fun setSpeechPitch(pitch: Float) =
        viewModelScope.launch { preferencesRepository.setSpeechPitch(pitch) }
    fun setSelectedVoiceId(id: String) =
        viewModelScope.launch { preferencesRepository.setSelectedVoiceId(id) }
    fun setAutoPlayAudio(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setAutoPlayAudio(enabled) }
    fun setDailyGoalWords(goal: Int) =
        viewModelScope.launch { preferencesRepository.setDailyGoalWords(goal) }
    fun setDarkMode(enabled: Boolean?) =
        viewModelScope.launch { preferencesRepository.setDarkMode(enabled) }

    class Factory(
        private val dictionary: DictionaryRepository,
        private val vocabulary: VocabularyRepository,
        private val learning: LearningRepository,
        private val preferences: PreferenceRepository,
        private val importManager: DictionaryImportRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                "Unsupported ViewModel type: ${modelClass.name}"
            }
            return AppViewModel(
                dictionary,
                vocabulary,
                learning,
                preferences,
                importManager
            ) as T
        }
    }
}
