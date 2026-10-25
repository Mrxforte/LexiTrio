package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

enum class QuizType(val titleEn: String, val titleRu: String, val titleUz: String) {
    WORD_TO_TRANSLATION("Word → Translation", "Слово → Перевод", "So‘z → Tarjima"),
    TRANSLATION_TO_WORD("Translation → Word", "Перевод → Слово", "Tarjima → So‘z"),
    CHOOSE_DEFINITION("Choose Definition", "Выбор определения", "Ta’rifni tanlang"),
    LISTENING_CHALLENGE("Listening Challenge", "Аудирование", "Tinglab topish"),
    FILL_IN_BLANK("Fill in the Blank", "Вставить пропущенное", "Bo‘sh joyni to‘ldirish"),
    SYNONYM_ANTONYM("Synonyms & Antonyms", "Синонимы и антонимы", "Sinonim va Antonimlar"),
    GRAMMAR_CHECK("Grammar Mastery", "Грамматический тест", "Grammatika testi")
}

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: QuizType = QuizType.WORD_TO_TRANSLATION,
    val questionText: String,
    val promptAudioText: String = "",
    val promptLanguage: String = "en", // "en", "ru", "uz"
    val correctAnswer: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val explanation: String = "",
    val category: String = "General",
    val difficulty: String = "B1"
) {
    fun getOptionsShuffled(): List<String> {
        return listOf(optionA, optionB, optionC, optionD).filter { it.isNotBlank() }.shuffled()
    }
}

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "''")
    val clientId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val timeSpentSec: Int,
    val xpEarned: Int
)
