package com.example.data.repository

import com.example.data.dao.CurriculumDao
import com.example.data.database.CurriculumCoverageReportGenerator
import com.example.data.database.GrammarCurriculumRegistry
import com.example.data.model.CurriculumCourseEntity
import com.example.data.model.CurriculumCoverageReport
import com.example.data.model.CurriculumExerciseEntity
import com.example.data.model.CurriculumLevelEntity
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.GrammarTableEntity
import com.example.data.model.LessonContentEntity
import com.example.data.model.MistakeRecordEntity
import com.example.data.model.TestBankQuestionEntity
import com.example.data.model.UserTopicProgressEntity
import kotlinx.coroutines.flow.Flow

interface CurriculumRepository {
    fun getCourses(): Flow<List<CurriculumCourseEntity>>
    fun getLevelsForCourse(courseCode: String): Flow<List<CurriculumLevelEntity>>
    fun getTopicsByLanguage(language: String): Flow<List<CurriculumTopicEntity>>
    fun getTopicsByLevel(language: String, level: String): Flow<List<CurriculumTopicEntity>>
    suspend fun getTopicById(id: Long): CurriculumTopicEntity?
    suspend fun getLessonForTopic(topicId: Long): LessonContentEntity?
    suspend fun getTablesForLesson(lessonId: Long): List<GrammarTableEntity>
    fun getExercisesForTopic(topicId: Long): Flow<List<CurriculumExerciseEntity>>
    suspend fun getRandomExercises(language: String, level: String, count: Int): List<CurriculumExerciseEntity>
    suspend fun getTestQuestionsForLevel(language: String, level: String, count: Int): List<TestBankQuestionEntity>
    suspend fun getProgressForTopic(topicId: Long): UserTopicProgressEntity?
    suspend fun saveTopicProgress(progress: UserTopicProgressEntity)
    suspend fun recordMistake(exerciseId: Long, questionType: String, userAnswer: String, correctAnswer: String)
    suspend fun getTopicCount(language: String): Int
    suspend fun getExerciseCount(language: String): Int
    suspend fun getTestQuestionCount(language: String): Int
    suspend fun getCoverageReport(): CurriculumCoverageReport
    suspend fun generateQuiz(language: String, level: String, topicId: Long?, questionCount: Int): List<TestBankQuestionEntity>
}

class CurriculumRepositoryImpl(
    private val curriculumDao: CurriculumDao
) : CurriculumRepository {

    override fun getCourses(): Flow<List<CurriculumCourseEntity>> =
        curriculumDao.getAllCourses()

    override fun getLevelsForCourse(courseCode: String): Flow<List<CurriculumLevelEntity>> =
        curriculumDao.getLevelsForCourse(courseCode)

    override fun getTopicsByLanguage(language: String): Flow<List<CurriculumTopicEntity>> =
        curriculumDao.getTopicsByLanguage(language)

    override fun getTopicsByLevel(language: String, level: String): Flow<List<CurriculumTopicEntity>> =
        if (level == "All") {
            curriculumDao.getTopicsByLanguage(language)
        } else {
            curriculumDao.getTopicsByLevel(language, level)
        }

    override suspend fun getTopicById(id: Long): CurriculumTopicEntity? =
        curriculumDao.getTopicById(id)

    override suspend fun getLessonForTopic(topicId: Long): LessonContentEntity? =
        curriculumDao.getLessonForTopic(topicId)

    override suspend fun getTablesForLesson(lessonId: Long): List<GrammarTableEntity> =
        curriculumDao.getTablesForLesson(lessonId)

    override fun getExercisesForTopic(topicId: Long): Flow<List<CurriculumExerciseEntity>> =
        curriculumDao.getExercisesForTopic(topicId)

    override suspend fun getRandomExercises(language: String, level: String, count: Int): List<CurriculumExerciseEntity> =
        curriculumDao.getRandomExercises(language, level, count)

    override suspend fun getTestQuestionsForLevel(language: String, level: String, count: Int): List<TestBankQuestionEntity> =
        curriculumDao.getTestQuestionsForLevel(language, level, count)

    override suspend fun getProgressForTopic(topicId: Long): UserTopicProgressEntity? =
        curriculumDao.getProgressForTopic(topicId)

    override suspend fun saveTopicProgress(progress: UserTopicProgressEntity) =
        curriculumDao.insertOrUpdateProgress(progress)

    override suspend fun recordMistake(exerciseId: Long, questionType: String, userAnswer: String, correctAnswer: String) {
        val record = MistakeRecordEntity(
            exerciseId = exerciseId,
            questionType = questionType,
            userAnswer = userAnswer,
            correctAnswer = correctAnswer,
            timesFailed = 1,
            isMastered = false,
            updatedAtMs = System.currentTimeMillis()
        )
        curriculumDao.insertMistake(record)
    }

    override suspend fun getTopicCount(language: String): Int =
        curriculumDao.getTopicCount(language)

    override suspend fun getExerciseCount(language: String): Int =
        curriculumDao.getExerciseCount(language)

    override suspend fun getTestQuestionCount(language: String): Int =
        curriculumDao.getTestQuestionCount(language)

    override suspend fun getCoverageReport(): CurriculumCoverageReport {
        val bundles = GrammarCurriculumRegistry.getAllTopicBundles()
        return CurriculumCoverageReportGenerator.generateReport(bundles)
    }

    override suspend fun generateQuiz(
        language: String,
        level: String,
        topicId: Long?,
        questionCount: Int
    ): List<TestBankQuestionEntity> {
        val validCount = when {
            questionCount <= 10 -> 10
            questionCount <= 20 -> 20
            questionCount <= 30 -> 30
            questionCount <= 50 -> 50
            else -> 100
        }

        return if (topicId != null && topicId > 0) {
            val questions = curriculumDao.getTestQuestionsForTopic(topicId, validCount)
            if (questions.size < validCount) {
                val additional = curriculumDao.getTestQuestionsForLevel(language, level, validCount - questions.size)
                (questions + additional).take(validCount)
            } else {
                questions
            }
        } else {
            curriculumDao.getTestQuestionsForLevel(language, level, validCount)
        }
    }
}
