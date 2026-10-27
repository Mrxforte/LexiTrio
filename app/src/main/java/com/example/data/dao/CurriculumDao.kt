package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.CurriculumCourseEntity
import com.example.data.model.CurriculumExerciseEntity
import com.example.data.model.CurriculumLevelEntity
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.GrammarTableEntity
import com.example.data.model.LessonContentEntity
import com.example.data.model.MistakeRecordEntity
import com.example.data.model.TestBankQuestionEntity
import com.example.data.model.UserTopicProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurriculumDao {

    // Courses & Levels
    @Query("SELECT * FROM curriculum_courses ORDER BY code ASC")
    fun getAllCourses(): Flow<List<CurriculumCourseEntity>>

    @Query("SELECT * FROM curriculum_courses WHERE language = :language LIMIT 1")
    suspend fun getCourseByLanguage(language: String): CurriculumCourseEntity?

    @Query("SELECT * FROM curriculum_levels WHERE courseCode = :courseCode ORDER BY `order` ASC")
    fun getLevelsForCourse(courseCode: String): Flow<List<CurriculumLevelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CurriculumCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevels(levels: List<CurriculumLevelEntity>)

    // Topics
    @Query("SELECT * FROM curriculum_topics WHERE language = :language ORDER BY `order` ASC")
    fun getTopicsByLanguage(language: String): Flow<List<CurriculumTopicEntity>>

    @Query("SELECT * FROM curriculum_topics WHERE language = :language AND cefrLevel = :level ORDER BY `order` ASC")
    fun getTopicsByLevel(language: String, level: String): Flow<List<CurriculumTopicEntity>>

    @Query("SELECT * FROM curriculum_topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): CurriculumTopicEntity?

    @Query("SELECT COUNT(*) FROM curriculum_topics WHERE language = :language")
    suspend fun getTopicCount(language: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<CurriculumTopicEntity>)

    // Lessons & Grammar Tables
    @Query("SELECT * FROM lesson_contents WHERE topicId = :topicId LIMIT 1")
    suspend fun getLessonForTopic(topicId: Long): LessonContentEntity?

    @Query("SELECT * FROM grammar_tables WHERE lessonId = :lessonId ORDER BY id ASC")
    suspend fun getTablesForLesson(lessonId: Long): List<GrammarTableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessonContent(lesson: LessonContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrammarTables(tables: List<GrammarTableEntity>)

    // Exercises
    @Query("SELECT * FROM curriculum_exercises WHERE topicId = :topicId ORDER BY id ASC")
    fun getExercisesForTopic(topicId: Long): Flow<List<CurriculumExerciseEntity>>

    @Query("SELECT * FROM curriculum_exercises WHERE language = :language AND cefrLevel = :level ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomExercises(language: String, level: String, count: Int): List<CurriculumExerciseEntity>

    @Query("SELECT COUNT(*) FROM curriculum_exercises WHERE language = :language")
    suspend fun getExerciseCount(language: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<CurriculumExerciseEntity>)

    // Test Questions
    @Query("SELECT * FROM test_bank_questions WHERE language = :language AND cefrLevel = :level ORDER BY RANDOM() LIMIT :count")
    suspend fun getTestQuestionsForLevel(language: String, level: String, count: Int): List<TestBankQuestionEntity>

    @Query("SELECT * FROM test_bank_questions WHERE topicId = :topicId ORDER BY RANDOM() LIMIT :count")
    suspend fun getTestQuestionsForTopic(topicId: Long, count: Int): List<TestBankQuestionEntity>

    @Query("SELECT COUNT(*) FROM test_bank_questions WHERE language = :language")
    suspend fun getTestQuestionCount(language: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestQuestions(questions: List<TestBankQuestionEntity>)

    // Progress & Mistakes
    @Query("SELECT * FROM user_topic_progress WHERE topicId = :topicId LIMIT 1")
    suspend fun getProgressForTopic(topicId: Long): UserTopicProgressEntity?

    @Query("SELECT * FROM user_topic_progress")
    fun getAllTopicProgress(): Flow<List<UserTopicProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: UserTopicProgressEntity)

    @Query("SELECT * FROM mistake_records WHERE isMastered = 0 ORDER BY updatedAtMs DESC")
    fun getUnmasteredMistakes(): Flow<List<MistakeRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeRecordEntity)
}
