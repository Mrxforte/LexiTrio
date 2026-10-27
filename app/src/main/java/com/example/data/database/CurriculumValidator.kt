package com.example.data.database

import com.example.data.model.CurriculumExerciseEntity
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.LessonContentEntity
import com.example.data.model.TestBankQuestionEntity
import com.example.data.model.TopicCoverageStatus
import com.example.data.model.TopicValidationIssue

object CurriculumValidator {

    private fun parseJsonArray(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isBlank()) return emptyList()
        return trimmed.split("\",\"").map {
            it.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }

    private fun countJsonArrayLength(json: String): Int {
        return parseJsonArray(json).size
    }

    fun validateTopic(
        topic: CurriculumTopicEntity,
        lesson: LessonContentEntity?,
        exercises: List<CurriculumExerciseEntity>,
        quizzes: List<TestBankQuestionEntity>,
        allTopicCodes: Set<String>
    ): Pair<TopicCoverageStatus, List<TopicValidationIssue>> {
        val issues = mutableListOf<TopicValidationIssue>()
        val missingRequirements = mutableListOf<String>()

        // 1. Topic metadata checks
        if (topic.topicCode.isBlank()) {
            issues.add(TopicValidationIssue(topic.topicCode, "MISSING_TOPIC_CODE", "Topic code cannot be blank."))
            missingRequirements.add("Topic Code")
        }
        if (topic.language !in listOf("en", "ru")) {
            issues.add(TopicValidationIssue(topic.topicCode, "INVALID_LANGUAGE", "Unsupported language '${topic.language}'."))
            missingRequirements.add("Valid Language (en/ru)")
        }

        val objectives = parseJsonArray(topic.learningObjectivesJson)
        if (objectives.isEmpty()) {
            issues.add(TopicValidationIssue(topic.topicCode, "MISSING_OBJECTIVES", "No learning objectives defined."))
            missingRequirements.add("Learning Objectives")
        }

        val prereqs = parseJsonArray(topic.prerequisitesJson)
        prereqs.forEach { prereqCode ->
            if (prereqCode !in allTopicCodes) {
                issues.add(TopicValidationIssue(topic.topicCode, "BROKEN_PREREQUISITE", "Prerequisite topic '$prereqCode' does not exist in curriculum."))
                missingRequirements.add("Valid Prerequisite Links")
            }
        }

        // 2. Lesson content checks
        var hasLessonContent = false
        if (lesson == null) {
            issues.add(TopicValidationIssue(topic.topicCode, "MISSING_LESSON", "No lesson content found for topic."))
            missingRequirements.add("Lesson Content")
        } else {
            hasLessonContent = true
            if (lesson.simpleExplanationUz.isBlank()) {
                issues.add(TopicValidationIssue(topic.topicCode, "MISSING_UZBEK_EXPLANATION", "Simple Uzbek explanation is missing."))
                missingRequirements.add("Uzbek Explanation")
            }
            if (lesson.mainRuleEn.isBlank() && lesson.mainRuleRu.isBlank()) {
                issues.add(TopicValidationIssue(topic.topicCode, "MISSING_MAIN_RULE", "Main rule definition is missing."))
                missingRequirements.add("Main Rule")
            }

            // Check examples count (positives + negatives + detailed)
            val posCount = countJsonArrayLength(lesson.positiveExamplesJson)
            val negCount = countJsonArrayLength(lesson.negativeExamplesJson)
            val questCount = countJsonArrayLength(lesson.questionFormsJson)
            val detailedCount = countJsonArrayLength(lesson.detailedExamplesJson)
            val totalExamples = posCount + negCount + questCount + detailedCount

            if (totalExamples < 10) {
                issues.add(TopicValidationIssue(topic.topicCode, "INSUFFICIENT_EXAMPLES", "Expected at least 10 varied examples, found $totalExamples."))
                missingRequirements.add("Minimum 10 Examples")
            }

            val exceptionsCount = countJsonArrayLength(lesson.exceptionsJson)
            val mistakesCount = countJsonArrayLength(lesson.commonMistakesJson)
            if (exceptionsCount == 0 && mistakesCount == 0) {
                issues.add(TopicValidationIssue(topic.topicCode, "MISSING_MISTAKES_EXCEPTIONS", "No exceptions or common mistakes listed."))
                missingRequirements.add("Exceptions or Common Mistakes")
            }
        }

        // 3. Exercises check (Minimum 15 required)
        var hasValidatedExercises = false
        if (exercises.size < 15) {
            issues.add(TopicValidationIssue(topic.topicCode, "INSUFFICIENT_EXERCISES", "Expected at least 15 exercises, found ${exercises.size}."))
            missingRequirements.add("Minimum 15 Exercises")
        } else {
            val invalidExercises = exercises.filter { ex ->
                (ex.promptEn.isBlank() && ex.promptRu.isBlank()) || ex.correctAnswer.isBlank()
            }
            if (invalidExercises.isNotEmpty()) {
                issues.add(TopicValidationIssue(topic.topicCode, "INVALID_EXERCISES", "${invalidExercises.size} exercises have invalid prompts or missing answer keys."))
                missingRequirements.add("All Exercises Validated")
            } else {
                hasValidatedExercises = true
            }
        }

        // 4. Test bank questions check
        var hasValidatedQuizzes = false
        if (quizzes.isEmpty()) {
            issues.add(TopicValidationIssue(topic.topicCode, "MISSING_QUIZZES", "No test bank quiz questions available for topic."))
            missingRequirements.add("Test Bank Questions")
        } else {
            val invalidQuizzes = quizzes.filter { q -> q.correctAnswer.isBlank() }
            if (invalidQuizzes.isNotEmpty()) {
                issues.add(TopicValidationIssue(topic.topicCode, "INVALID_QUIZZES", "${invalidQuizzes.size} test questions have missing answer keys."))
                missingRequirements.add("All Quiz Questions Validated")
            } else {
                hasValidatedQuizzes = true
            }
        }

        val isComplete = missingRequirements.isEmpty()

        val status = TopicCoverageStatus(
            topicCode = topic.topicCode,
            language = topic.language,
            cefrLevel = topic.cefrLevel,
            title = topic.titleEn,
            hasLessonContent = hasLessonContent,
            hasValidatedExercises = hasValidatedExercises,
            hasValidatedQuizzes = hasValidatedQuizzes,
            exerciseCount = exercises.size,
            quizCount = quizzes.size,
            isComplete = isComplete,
            reviewStatus = topic.reviewStatus,
            missingRequirements = missingRequirements
        )

        return Pair(status, issues)
    }
}
