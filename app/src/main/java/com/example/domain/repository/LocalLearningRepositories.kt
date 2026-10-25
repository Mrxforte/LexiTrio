package com.example.domain.repository

import com.example.data.model.*
import com.example.data.preferences.AppPreferences
import com.example.domain.model.ImportProgress
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    fun getEntryCountFlow(): Flow<Int>
    suspend fun search(query: String, sourceLanguage: Language?, partOfSpeech: String? = null, limit: Int = 60): List<DictionaryEntryEntity>
    suspend fun getFullDetail(id: Long): FullDictionaryDetail?
    suspend fun getRandomWord(): DictionaryEntryEntity?
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>
    suspend fun recordSearch(query: String, language: String)
    suspend fun clearSearchHistory()
}

interface VocabularyRepository {
    fun getAllSavedWords(): Flow<List<SavedWordEntity>>
    fun getWordsByStatus(status: LearningStatus): Flow<List<SavedWordEntity>>
    fun getDueWords(): Flow<List<SavedWordEntity>>
    fun getDueWordsCount(): Flow<Int>
    fun getAllCollections(): Flow<List<VocabularyCollectionEntity>>
    suspend fun saveOrUpdateWord(entry: DictionaryEntryEntity, collectionId: Long = 1, notes: String = "", isFavorite: Boolean = false): Long
    suspend fun toggleFavorite(savedWord: SavedWordEntity)
    suspend fun processReview(savedWord: SavedWordEntity, quality: ReviewQuality): SavedWordEntity
    suspend fun createCollection(title: String, description: String, icon: String = "folder"): Long
}

interface LearningRepository {
    fun getAllTopics(): Flow<List<TopicEntity>>
    suspend fun getTopicById(id: Long): TopicEntity?
    fun getWordsForTopic(topicId: Long): Flow<List<DictionaryEntryEntity>>
    fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationById(id: Long): ConversationEntity?
    fun getConversationTurns(convId: Long): Flow<List<ConversationTurnEntity>>
    fun getAllGrammarLessons(): Flow<List<GrammarLessonEntity>>
    suspend fun getGrammarLessonById(id: Long): GrammarLessonEntity?
    suspend fun getRandomQuiz(count: Int, type: QuizType? = null, category: String? = null): List<QuizQuestionEntity>
    fun getRecentAttempts(): Flow<List<QuizAttemptEntity>>
    fun getUserProgress(): Flow<UserProgressEntity?>
    suspend fun addXpAndProgress(xpGain: Int, isQuiz: Boolean = false, wordsReviewedDelta: Int = 0)
    suspend fun saveQuizAttempt(category: String, total: Int, correct: Int, timeSec: Int, xp: Int): Long
}

interface PreferenceRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setAppLanguage(language: Language)
    suspend fun setSearchSourceLanguage(language: Language)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setSpeechPitch(pitch: Float)
    suspend fun setSelectedVoiceId(voiceId: String)
    suspend fun setAutoPlayAudio(enabled: Boolean)
    suspend fun setDailyGoalWords(goal: Int)
    suspend fun setDarkMode(darkMode: Boolean?)
}

interface DictionaryImportRepository {
    val progress: Flow<ImportProgress>
    suspend fun importFromUri(uri: android.net.Uri): Result<Int>
    fun getSampleJsonlTemplate(): String
}
