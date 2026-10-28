package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import com.example.data.model.QuizType
import com.example.data.tts.TtsEngineType
import com.example.data.repository.CurriculumRepository
import com.example.domain.model.DictionarySearchFilters
import com.example.domain.model.DictionarySearchUiState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MILLIS = 200L
private const val SEARCH_ERROR_MESSAGE = "Qidiruvda xatolik yuz berdi. Qayta urinib ko'ring."

class AppViewModel(
    private val dictionary: DictionaryRepository,
    private val vocabulary: VocabularyRepository,
    private val learning: LearningRepository,
    private val preferencesRepository: PreferenceRepository,
    private val curriculum: CurriculumRepository? = null
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
    private val searchFilters = MutableStateFlow(DictionarySearchFilters())
    private val searchRefreshTicket = MutableStateFlow(0L)
    private val _searchState = MutableStateFlow(DictionarySearchUiState())

    /** Debounced, cancellable dictionary search state for the search screen. */
    val searchState: StateFlow<DictionarySearchUiState> = _searchState.asStateFlow()

    init {
        viewModelScope.launch {
            searchFilters.collect { filters ->
                _searchState.update {
                    if (it.filters == filters) it else it.copy(
                        filters = filters, results = emptyList(), isLoading = filters.isActive,
                        hasSearched = false, errorMessage = null, canLoadMore = false, isLoadingMore = false
                    )
                }
            }
        }
        observeSearch()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearch() {
        viewModelScope.launch {
            combine(searchFilters, searchRefreshTicket) { filters, ticket -> filters to ticket }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                // A newer query cancels the previous one instead of queueing it.
                .flatMapLatest { (filters, _) ->
                    searchFlow(filters).catch { error ->
                        emit(SearchOutcome(filters, hasSearched = true, errorMessage = error.message ?: SEARCH_ERROR_MESSAGE))
                    }
                }
                .collect { outcome ->
                    _searchState.update {
                        if (outcome.filters != searchFilters.value || it.filters != outcome.filters) return@update it
                        it.copy(
                            isLoading = outcome.isLoading,
                            results = if (outcome.isLoading) it.results else outcome.results,
                            hasSearched = outcome.hasSearched,
                            errorMessage = outcome.errorMessage,
                            canLoadMore = !outcome.isLoading && outcome.results.size >= SearchDictionaryUseCase.PAGE_SIZE,
                            isLoadingMore = false
                        )
                    }
                }
        }
    }

    /** Appends the next page of the current search; called when the list nears its end. */
    fun loadMoreSearchResults() {
        val state = _searchState.value
        if (!state.canLoadMore || state.isLoadingMore || state.isLoading) return
        val filters = state.filters
        val offset = state.results.size
        _searchState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            var pageError: String? = null
            val page = try {
                withContext(Dispatchers.IO) {
                    searchPage(filters, offset)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pageError = error.message ?: SEARCH_ERROR_MESSAGE
                null
            }
            _searchState.update { current ->
                // The user changed the query or filters meanwhile: drop this page.
                if (current.filters != filters || current.results.size != offset) {
                    return@update current.copy(isLoadingMore = false)
                }
                if (page == null) return@update current.copy(isLoadingMore = false, errorMessage = pageError)
                val known = current.results.mapTo(HashSet()) { it.id }
                current.copy(
                    results = current.results + page.filter { it.id !in known },
                    canLoadMore = page.size >= SearchDictionaryUseCase.PAGE_SIZE,
                    isLoadingMore = false
                )
            }
        }
    }

    private fun searchFlow(filters: DictionarySearchFilters): Flow<SearchOutcome> = flow {
        if (!filters.isActive) {
            emit(SearchOutcome(filters))
            return@flow
        }
        emit(SearchOutcome(filters, isLoading = true))
        val rows = searchPage(filters, 0)
        emit(SearchOutcome(filters, hasSearched = true, results = rows))
    }.flowOn(Dispatchers.IO)

    private suspend fun searchPage(filters: DictionarySearchFilters, offset: Int): List<DictionaryEntryEntity> {
        val source = filters.language
        val target = filters.targetLanguage
        return if (source != null && target != null) {
            dictionary.searchDirected(filters.query, source, target, SearchDictionaryUseCase.PAGE_SIZE, offset)
        } else {
            searchDictionary(filters.query, if (filters.reverseTranslation) null else source, filters.partOfSpeech, offset = offset)
        }
    }

    fun setSearchDirection(source: Language, target: Language) {
        searchFilters.update { it.copy(language = source, targetLanguage = target, partOfSpeech = null, reverseTranslation = false) }
    }

    private class SearchOutcome(
        val filters: DictionarySearchFilters,
        val isLoading: Boolean = false,
        val results: List<DictionaryEntryEntity> = emptyList(),
        val hasSearched: Boolean = false,
        val errorMessage: String? = null
    )

    fun setSearchQuery(query: String) {
        searchFilters.update { it.copy(query = query) }
    }

    fun setDictionaryLanguages(source: Language, target: Language) =
        viewModelScope.launch { preferencesRepository.setDictionaryLanguages(source, target) }

    /**
     * Applies navigation arguments once. Re-applying after a configuration
     * change would overwrite filters the user has since changed.
     */
    fun applyInitialSearch(query: String, language: Language?) {
        if (query.isBlank() && language == null) return
        searchFilters.update { it.copy(query = query, language = language ?: it.language) }
    }

    fun setSearchLanguage(language: Language?) {
        searchFilters.update { it.copy(language = if (it.language == language) null else language) }
    }

    fun setSearchPartOfSpeech(partOfSpeech: String?) {
        searchFilters.update {
            it.copy(partOfSpeech = if (it.partOfSpeech == partOfSpeech) null else partOfSpeech)
        }
    }

    fun setReverseTranslation(enabled: Boolean) {
        searchFilters.update { it.copy(reverseTranslation = enabled) }
    }

    /** Re-runs the current query, e.g. after a failed local database read. */
    fun retrySearch() {
        searchRefreshTicket.update { it + 1 }
    }

    /** Stores an explicit search (keyboard submit) in the history list. */
    fun recordCurrentSearch() {
        val filters = searchFilters.value
        if (!filters.isActive) return
        recordSearch(filters.query, filters.historyLanguage)
    }

    suspend fun search(query: String, language: Language?, partOfSpeech: String?): List<DictionaryEntryEntity> =
        searchDictionary(query, language, partOfSpeech)

    fun recordSearch(query: String, language: String) =
        viewModelScope.launch { dictionary.recordSearch(query, language) }
    fun clearSearchHistory() = viewModelScope.launch { dictionary.clearSearchHistory() }
    suspend fun getWordOfDay(source: Language, target: Language, dayKey: Int): DictionaryEntryEntity? =
        withContext(Dispatchers.IO) { dictionary.getWordOfDay(source, target, dayKey) }
    suspend fun getFullDetail(id: Long) = dictionary.getFullDetail(id)
    suspend fun refreshFullDetail(id: Long) = dictionary.refreshDetail(id)
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
    fun setTtsEngineType(type: TtsEngineType) =
        viewModelScope.launch { preferencesRepository.setTtsEngineType(type) }
    fun setAutoPlayAudio(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setAutoPlayAudio(enabled) }
    fun setDailyGoalWords(goal: Int) =
        viewModelScope.launch { preferencesRepository.setDailyGoalWords(goal) }
    fun setDarkMode(enabled: Boolean?) =
        viewModelScope.launch { preferencesRepository.setDarkMode(enabled) }
    fun setOnboardingCompleted(completed: Boolean) =
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(completed) }

    // Curriculum Methods
    fun getCurriculumTopics(language: String, level: String) =
        curriculum?.getTopicsByLevel(language, level) ?: flow { emit(emptyList()) }

    suspend fun getCurriculumTopicById(id: Long) =
        curriculum?.getTopicById(id)

    suspend fun getCurriculumLessonForTopic(topicId: Long) =
        curriculum?.getLessonForTopic(topicId)

    suspend fun getCurriculumTablesForLesson(lessonId: Long) =
        curriculum?.getTablesForLesson(lessonId) ?: emptyList()

    fun getCurriculumExercisesForTopic(topicId: Long) =
        curriculum?.getExercisesForTopic(topicId) ?: flow { emit(emptyList()) }

    suspend fun recordCurriculumMistake(exerciseId: Long, questionType: String, userAnswer: String, correctAnswer: String) {
        curriculum?.recordMistake(exerciseId, questionType, userAnswer, correctAnswer)
    }

    suspend fun getCurriculumTopicCount(language: String) =
        curriculum?.getTopicCount(language) ?: 0

    suspend fun getCurriculumExerciseCount(language: String) =
        curriculum?.getExerciseCount(language) ?: 0

    suspend fun getCurriculumTestQuestionCount(language: String) =
        curriculum?.getTestQuestionCount(language) ?: 0

    suspend fun getCurriculumCoverageReport() =
        curriculum?.getCoverageReport()

    suspend fun generateCurriculumQuiz(language: String, level: String, topicId: Long?, count: Int) =
        curriculum?.generateQuiz(language, level, topicId, count) ?: emptyList()

    class Factory(
        private val dictionary: DictionaryRepository,
        private val vocabulary: VocabularyRepository,
        private val learning: LearningRepository,
        private val preferences: PreferenceRepository,
        private val curriculum: CurriculumRepository? = null
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
                curriculum
            ) as T
        }
    }
}
