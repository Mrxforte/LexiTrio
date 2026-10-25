package com.example.data.repository

import com.example.data.dao.ConversationGrammarDao
import com.example.data.dao.LearningDao
import com.example.data.dao.QuizDao
import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.GrammarLessonEntity
import com.example.data.model.QuizAttemptEntity
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.QuizType
import com.example.data.model.TopicEntity
import com.example.data.model.UserProgressEntity
import kotlinx.coroutines.flow.Flow

class LearningRepository(
    private val learningDao: LearningDao,
    private val conversationGrammarDao: ConversationGrammarDao,
    private val quizDao: QuizDao
) {

    // Topics
    fun getAllTopics(): Flow<List<TopicEntity>> = learningDao.getAllTopics()

    suspend fun getTopicById(id: Long): TopicEntity? = learningDao.getTopicById(id)

    fun getWordsForTopic(topicId: Long): Flow<List<DictionaryEntryEntity>> = learningDao.getWordsForTopic(topicId)

    // Conversations
    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationGrammarDao.getAllConversations()

    suspend fun getConversationById(id: Long): ConversationEntity? = conversationGrammarDao.getConversationById(id)

    fun getConversationTurns(convId: Long): Flow<List<ConversationTurnEntity>> =
        conversationGrammarDao.getConversationTurns(convId)

    // Grammar
    fun getAllGrammarLessons(): Flow<List<GrammarLessonEntity>> = conversationGrammarDao.getAllGrammarLessons()

    suspend fun getGrammarLessonById(id: Long): GrammarLessonEntity? = conversationGrammarDao.getGrammarLessonById(id)

    // Quiz
    suspend fun getRandomQuiz(count: Int, type: QuizType? = null, category: String? = null): List<QuizQuestionEntity> {
        return when {
            type != null -> quizDao.getQuestionsByType(type, count)
            category != null && category != "All" -> quizDao.getQuestionsByCategory(category, count)
            else -> quizDao.getRandomQuestions(count)
        }
    }

    fun getRecentAttempts(): Flow<List<QuizAttemptEntity>> = quizDao.getRecentAttempts()

    suspend fun saveQuizAttempt(
        category: String,
        total: Int,
        correct: Int,
        timeSec: Int,
        xp: Int
    ): Long {
        val attempt = QuizAttemptEntity(
            category = category,
            totalQuestions = total,
            correctCount = correct,
            timeSpentSec = timeSec,
            xpEarned = xp
        )
        val id = quizDao.insertAttempt(attempt)
        addXpAndProgress(xp, isQuiz = true)
        return id
    }

    // User Progress
    fun getUserProgress(): Flow<UserProgressEntity?> = quizDao.getUserProgress()

    suspend fun addXpAndProgress(xpGain: Int, isQuiz: Boolean = false, wordsReviewedDelta: Int = 0) {
        val current = quizDao.getUserProgressOnce() ?: UserProgressEntity()
        val updated = current.copy(
            xp = current.xp + xpGain,
            todayWordsReviewed = current.todayWordsReviewed + wordsReviewedDelta,
            totalQuizzesCompleted = if (isQuiz) current.totalQuizzesCompleted + 1 else current.totalQuizzesCompleted,
            updatedAt = System.currentTimeMillis()
        )
        quizDao.insertOrUpdateProgress(updated)
    }
}
