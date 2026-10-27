package com.example

import com.example.data.database.CurriculumCoverageReportGenerator
import com.example.data.database.GrammarCurriculumRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumCompletenessTest {

    @Test
    fun testTaxonomyCountsAndCompleteness() {
        val bundles = GrammarCurriculumRegistry.getAllTopicBundles()
        val enBundles = bundles.filter { it.topic.language == "en" }
        val ruBundles = bundles.filter { it.topic.language == "ru" }

        // English topic counts per CEFR level
        assertEquals(133, enBundles.size)
        assertEquals(23, enBundles.count { it.topic.cefrLevel == "A1" })
        assertEquals(31, enBundles.count { it.topic.cefrLevel == "A2" })
        assertEquals(29, enBundles.count { it.topic.cefrLevel == "B1" })
        assertEquals(25, enBundles.count { it.topic.cefrLevel == "B2" })
        assertEquals(25, enBundles.count { it.topic.cefrLevel == "C1" })

        // Russian topic counts per CEFR level
        assertEquals(160, ruBundles.size)
        assertEquals(35, ruBundles.count { it.topic.cefrLevel == "A1" })
        assertEquals(35, ruBundles.count { it.topic.cefrLevel == "A2" })
        assertEquals(35, ruBundles.count { it.topic.cefrLevel == "B1" })
        assertEquals(30, ruBundles.count { it.topic.cefrLevel == "B2" })
        assertEquals(25, ruBundles.count { it.topic.cefrLevel == "C1" })

        // Total baseline taxonomy count
        assertEquals(293, bundles.size)
    }

    @Test
    fun testCoverageReportGeneration() {
        val bundles = GrammarCurriculumRegistry.getAllTopicBundles()
        val report = CurriculumCoverageReportGenerator.generateReport(bundles)

        assertEquals(293, report.totalTopics)
        assertEquals(293, report.completeTopics)
        assertEquals(100.0, report.overallCompletionPercentage, 0.01)

        assertEquals(133, report.english.totalRequiredTopics)
        assertEquals(133, report.english.completeTopics)
        assertEquals(100.0, report.english.completionPercentage, 0.01)

        assertEquals(160, report.russian.totalRequiredTopics)
        assertEquals(160, report.russian.completeTopics)
        assertEquals(100.0, report.russian.completionPercentage, 0.01)

        assertTrue("Validation issues should be empty for a complete curriculum", report.validationIssues.isEmpty())
    }

    @Test
    fun testTopicContentIntegrity() {
        val bundles = GrammarCurriculumRegistry.getAllTopicBundles()

        bundles.forEach { bundle ->
            val topic = bundle.topic
            val lesson = bundle.lesson
            val exercises = bundle.exercises
            val quizzes = bundle.testQuestions

            assertTrue("Topic code must be non-blank: ${topic.topicCode}", topic.topicCode.isNotBlank())
            assertTrue("Uzbek explanation must be present for ${topic.topicCode}", lesson.simpleExplanationUz.isNotBlank())
            assertTrue("Exercises must be at least 15 for ${topic.topicCode}", exercises.size >= 15)
            assertTrue("Quizzes must be non-empty for ${topic.topicCode}", quizzes.isNotEmpty())

            exercises.forEach { ex ->
                assertTrue("Exercise prompt must be non-blank in ${topic.topicCode}", ex.promptEn.isNotBlank() || ex.promptRu.isNotBlank())
                assertTrue("Exercise correct answer must be non-blank in ${topic.topicCode}", ex.correctAnswer.isNotBlank())
            }
        }
    }
}
