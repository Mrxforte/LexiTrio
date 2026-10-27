package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LexiTrioDatabase
import com.example.data.database.PreloadCurriculumData
import com.example.data.repository.CurriculumRepositoryImpl
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
class CurriculumDatabaseTest {

    private lateinit var db: LexiTrioDatabase
    private lateinit var repository: CurriculumRepositoryImpl

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LexiTrioDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val currDao = db.curriculumDao()
        currDao.insertCourses(PreloadCurriculumData.getInitialCourses())
        currDao.insertLevels(PreloadCurriculumData.getInitialLevels())
        currDao.insertTopics(PreloadCurriculumData.getInitialTopics())
        PreloadCurriculumData.getInitialLessons().forEach { (lesson, tables) ->
            val lessonId = currDao.insertLessonContent(lesson)
            val tablesWithLessonId = tables.map { it.copy(lessonId = lessonId) }
            currDao.insertGrammarTables(tablesWithLessonId)
        }
        currDao.insertExercises(PreloadCurriculumData.getInitialExercises())
        currDao.insertTestQuestions(PreloadCurriculumData.getInitialTestQuestions())

        repository = CurriculumRepositoryImpl(currDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testDatabaseSeedingAndCounts(): Unit = runBlocking {
        val enTopicCount = repository.getTopicCount("en")
        val ruTopicCount = repository.getTopicCount("ru")

        assertEquals(133, enTopicCount)
        assertEquals(160, ruTopicCount)

        val enExCount = repository.getExerciseCount("en")
        val ruExCount = repository.getExerciseCount("ru")

        assertTrue("English exercise count should be at least 133 * 15", enExCount >= 133 * 15)
        assertTrue("Russian exercise count should be at least 160 * 15", ruExCount >= 160 * 15)
    }

    @Test
    fun testTopicAndLessonRetrieval(): Unit = runBlocking {
        val topic = repository.getTopicById(101L)
        assertNotNull("Topic 101 should exist", topic)
        assertEquals("EN_A1_001", topic?.topicCode)

        val lesson = repository.getLessonForTopic(101L)
        assertNotNull("Lesson for topic 101 should exist", lesson)
        assertEquals(101L, lesson?.topicId)

        val tables = repository.getTablesForLesson(lesson!!.id)
        assertTrue("Grammar tables should not be empty", tables.isNotEmpty())
    }

    @Test
    fun testConfigurableQuizGeneration(): Unit = runBlocking {
        val quiz10 = repository.generateQuiz("en", "A1", null, 10)
        assertEquals(10, quiz10.size)

        val quiz20 = repository.generateQuiz("en", "A1", null, 20)
        assertEquals(20, quiz20.size)

        val quiz30 = repository.generateQuiz("en", "A1", null, 30)
        assertEquals(30, quiz30.size)
    }
}
