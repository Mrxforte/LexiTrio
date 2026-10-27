package com.example.data.database

import com.example.data.model.CurriculumCoverageReport
import com.example.data.model.LanguageCoverageMetrics
import com.example.data.model.LevelCoverageMetrics
import com.example.data.model.TopicValidationIssue

object CurriculumCoverageReportGenerator {

    fun generateReport(bundles: List<GrammarCurriculumRegistry.TopicBundle>): CurriculumCoverageReport {
        val allTopicCodes = bundles.map { it.topic.topicCode }.toSet()
        val allIssues = mutableListOf<TopicValidationIssue>()

        // Map topic status
        val statuses = bundles.map { bundle ->
            val (status, issues) = CurriculumValidator.validateTopic(
                bundle.topic,
                bundle.lesson,
                bundle.exercises,
                bundle.testQuestions,
                allTopicCodes
            )
            allIssues.addAll(issues)
            status
        }

        val levelsList = listOf("A1", "A2", "B1", "B2", "C1", "C2")

        // Build English Metrics
        val enStatuses = statuses.filter { it.language == "en" }
        val enMetricsByLevel = levelsList.associateWith { level ->
            val levelStatuses = enStatuses.filter { it.cefrLevel == level }
            LevelCoverageMetrics(
                level = level,
                totalRequiredTopics = levelStatuses.size,
                completeTopics = levelStatuses.count { it.isComplete },
                topicsWithLesson = levelStatuses.count { it.hasLessonContent },
                topicsWithExercises = levelStatuses.count { it.hasValidatedExercises },
                topicsWithQuizzes = levelStatuses.count { it.hasValidatedQuizzes },
                awaitingReviewCount = levelStatuses.count { it.reviewStatus == "awaiting_review" },
                missingPrerequisitesCount = levelStatuses.count { "Valid Prerequisite Links" in it.missingRequirements },
                validationFailuresCount = levelStatuses.count { !it.isComplete },
                totalExercises = levelStatuses.sumOf { it.exerciseCount },
                totalQuizzes = levelStatuses.sumOf { it.quizCount }
            )
        }

        val enCoverage = LanguageCoverageMetrics(
            language = "en",
            totalRequiredTopics = enStatuses.size,
            completeTopics = enStatuses.count { it.isComplete },
            metricsByLevel = enMetricsByLevel,
            totalExercises = enStatuses.sumOf { it.exerciseCount },
            totalQuizzes = enStatuses.sumOf { it.quizCount }
        )

        // Build Russian Metrics
        val ruStatuses = statuses.filter { it.language == "ru" }
        val ruMetricsByLevel = levelsList.associateWith { level ->
            val levelStatuses = ruStatuses.filter { it.cefrLevel == level }
            LevelCoverageMetrics(
                level = level,
                totalRequiredTopics = levelStatuses.size,
                completeTopics = levelStatuses.count { it.isComplete },
                topicsWithLesson = levelStatuses.count { it.hasLessonContent },
                topicsWithExercises = levelStatuses.count { it.hasValidatedExercises },
                topicsWithQuizzes = levelStatuses.count { it.hasValidatedQuizzes },
                awaitingReviewCount = levelStatuses.count { it.reviewStatus == "awaiting_review" },
                missingPrerequisitesCount = levelStatuses.count { "Valid Prerequisite Links" in it.missingRequirements },
                validationFailuresCount = levelStatuses.count { !it.isComplete },
                totalExercises = levelStatuses.sumOf { it.exerciseCount },
                totalQuizzes = levelStatuses.sumOf { it.quizCount }
            )
        }

        val ruCoverage = LanguageCoverageMetrics(
            language = "ru",
            totalRequiredTopics = ruStatuses.size,
            completeTopics = ruStatuses.count { it.isComplete },
            metricsByLevel = ruMetricsByLevel,
            totalExercises = ruStatuses.sumOf { it.exerciseCount },
            totalQuizzes = ruStatuses.sumOf { it.quizCount }
        )

        val totalTopics = bundles.size
        val completeTopics = enCoverage.completeTopics + ruCoverage.completeTopics
        val overallPct = if (totalTopics > 0) (completeTopics.toDouble() / totalTopics.toDouble()) * 100.0 else 0.0

        return CurriculumCoverageReport(
            english = enCoverage,
            russian = ruCoverage,
            totalTopics = totalTopics,
            completeTopics = completeTopics,
            overallCompletionPercentage = overallPct,
            validationIssues = allIssues
        )
    }
}
