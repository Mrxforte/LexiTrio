package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "curriculum_courses")
data class CurriculumCourseEntity(
    @PrimaryKey
    val code: String, // e.g., "EN_CORE", "RU_CORE"
    val titleEn: String,
    val titleRu: String,
    val titleUz: String,
    val language: String, // "en" or "ru"
    val description: String
)

@Entity(
    tableName = "curriculum_levels",
    indices = [Index("courseCode")]
)
data class CurriculumLevelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseCode: String,
    val code: String, // A1, A2, B1, B2, C1, C2
    val order: Int,
    val title: String,
    val description: String
)

@Entity(
    tableName = "curriculum_topics",
    indices = [Index("language"), Index("cefrLevel"), Index("topicCode")]
)
data class CurriculumTopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicCode: String,
    val language: String, // "en" or "ru"
    val titleEn: String,
    val titleRu: String,
    val titleUz: String,
    val category: String, // Grammar, Vocabulary, Pronunciation, Conversation, etc.
    val cefrLevel: String = "A1",
    val estimatedMinutes: Int = 15,
    val order: Int = 1,
    val iconName: String = "book",
    val summaryEn: String = "",
    val summaryRu: String = "",
    val summaryUz: String = "",
    val prerequisitesJson: String = "[]",
    val learningObjectivesJson: String = "[]",
    val relatedTopicCodesJson: String = "[]",
    val masteryCriteriaScore: Int = 80,
    val reviewStatus: String = "published", // "published", "awaiting_review", "draft"
    val provenance: String = "curriculum_baseline",
    val validationStatus: String = "passed" // "passed", "failed", "pending"
) {
    fun getTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> titleEn
        Language.RUSSIAN -> titleRu.ifBlank { titleEn }
        Language.UZBEK_LATIN -> titleUz.ifBlank { titleEn }
        Language.UZBEK_CYRILLIC -> Language.transliterateUzbek(titleUz.ifBlank { titleEn }, toCyrillic = true)
    }
}

@Entity(
    tableName = "lesson_contents",
    foreignKeys = [
        ForeignKey(
            entity = CurriculumTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("topicId")]
)
data class LessonContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val simpleExplanationEn: String,
    val simpleExplanationRu: String = "",
    val simpleExplanationUz: String = "",
    val realLifeAnalogyEn: String = "",
    val realLifeAnalogyRu: String = "",
    val realLifeAnalogyUz: String = "",
    val mainRuleEn: String,
    val mainRuleRu: String = "",
    val mainRuleUz: String = "",
    val positiveExamplesJson: String = "[]",
    val negativeExamplesJson: String = "[]",
    val questionFormsJson: String = "[]",
    val exceptionsJson: String = "[]",
    val commonMistakesJson: String = "[]",
    val recapEn: String = "",
    val recapRu: String = "",
    val recapUz: String = "",
    val publicationStatus: String = "published",
    val detailedExamplesJson: String = "[]"
)

@Entity(
    tableName = "grammar_tables",
    indices = [Index("lessonId")]
)
data class GrammarTableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lessonId: Long,
    val titleEn: String,
    val titleRu: String = "",
    val titleUz: String = "",
    val tableType: String = "RULE", // RULE, WORD_FORM, VERB_CONJUGATION, TENSE_COMPARISON, SENTENCE_STRUCTURE
    val headersJson: String = "[]", // List<String>
    val rowsJson: String = "[]"     // List<List<String>>
)

@Entity(
    tableName = "curriculum_exercises",
    indices = [Index("topicId"), Index("language"), Index("cefrLevel"), Index("exerciseType")]
)
data class CurriculumExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long = 0,
    val language: String, // "en" or "ru"
    val cefrLevel: String = "A1",
    val skillCategory: String = "Grammar",
    val difficulty: String = "Medium",
    val exerciseType: String = "MULTIPLE_CHOICE", // MULTIPLE_CHOICE, FILL_IN_BLANK, TYPE_MISSING_WORD, SENTENCE_ORDERING, WORD_MATCHING, ERROR_IDENTIFICATION, ERROR_CORRECTION, TRANSLATION, CONJUGATION, etc.
    val promptEn: String,
    val promptRu: String = "",
    val promptUz: String = "",
    val instructions: String = "",
    val audioText: String = "",
    val optionsJson: String = "[]",
    val correctAnswer: String,
    val acceptedVariantsJson: String = "[]",
    val explanationEn: String = "",
    val explanationRu: String = "",
    val explanationUz: String = "",
    val hintEn: String = "",
    val hintRu: String = "",
    val hintUz: String = "",
    val relatedRule: String = "",
    val tags: String = "",
    val validationStatus: String = "valid",
    val publicationStatus: String = "published"
)

@Entity(
    tableName = "test_bank_questions",
    indices = [Index("topicId"), Index("language"), Index("cefrLevel")]
)
data class TestBankQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long = 0,
    val language: String, // "en" or "ru"
    val cefrLevel: String = "A1",
    val questionType: String = "MULTIPLE_CHOICE",
    val promptEn: String,
    val promptRu: String = "",
    val promptUz: String = "",
    val optionsJson: String = "[]",
    val correctAnswer: String,
    val acceptedVariantsJson: String = "[]",
    val explanationEn: String = "",
    val explanationRu: String = "",
    val explanationUz: String = "",
    val difficulty: String = "Medium",
    val publicationStatus: String = "published"
)

@Entity(
    tableName = "user_topic_progress",
    indices = [Index("topicId")]
)
data class UserTopicProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val status: String = "NOT_STARTED", // NOT_STARTED, IN_PROGRESS, COMPLETED, MASTERED
    val completedExercises: Int = 0,
    val totalExercises: Int = 0,
    val quizScore: Int = 0,
    val timeSpentSec: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "mistake_records")
data class MistakeRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: Long,
    val questionType: String = "EXERCISE",
    val userAnswer: String,
    val correctAnswer: String,
    val timesFailed: Int = 1,
    val isMastered: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class TopicValidationIssue(
    val topicCode: String,
    val issueType: String,
    val description: String
)

data class TopicCoverageStatus(
    val topicCode: String,
    val language: String,
    val cefrLevel: String,
    val title: String,
    val hasLessonContent: Boolean,
    val hasValidatedExercises: Boolean,
    val hasValidatedQuizzes: Boolean,
    val exerciseCount: Int,
    val quizCount: Int,
    val isComplete: Boolean,
    val reviewStatus: String,
    val missingRequirements: List<String>
)

data class LevelCoverageMetrics(
    val level: String,
    val totalRequiredTopics: Int,
    val completeTopics: Int,
    val topicsWithLesson: Int,
    val topicsWithExercises: Int,
    val topicsWithQuizzes: Int,
    val awaitingReviewCount: Int,
    val missingPrerequisitesCount: Int,
    val validationFailuresCount: Int,
    val totalExercises: Int,
    val totalQuizzes: Int
) {
    val completionPercentage: Double
        get() = if (totalRequiredTopics > 0) (completeTopics.toDouble() / totalRequiredTopics) * 100.0 else 0.0
}

data class LanguageCoverageMetrics(
    val language: String,
    val totalRequiredTopics: Int,
    val completeTopics: Int,
    val metricsByLevel: Map<String, LevelCoverageMetrics>,
    val totalExercises: Int,
    val totalQuizzes: Int
) {
    val completionPercentage: Double
        get() = if (totalRequiredTopics > 0) (completeTopics.toDouble() / totalRequiredTopics) * 100.0 else 0.0
}

data class CurriculumCoverageReport(
    val english: LanguageCoverageMetrics,
    val russian: LanguageCoverageMetrics,
    val totalTopics: Int,
    val completeTopics: Int,
    val overallCompletionPercentage: Double,
    val validationIssues: List<TopicValidationIssue>
)

