package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grammar_lessons")
data class GrammarLessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titleEn: String,
    val titleRu: String,
    val titleUzLatn: String,
    val titleUzCyrl: String,
    val category: String, // "Verbs & Tenses", "Nouns & Articles", "Russian Cases & Gender", "Uzbek Agglutinative Suffixes", "Adjectives & Adverbs", "Sentence Structure"
    val level: String = "B1", // A1, A2, B1, B2
    val summaryEn: String,
    val summaryRu: String,
    val summaryUzLatn: String,
    val ruleDetailsJson: String = "", // Detailed markdown / structured guide
    val examplesJson: String = "" // Illustrated trilingual examples
) {
    fun getTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> titleEn
        Language.RUSSIAN -> titleRu
        Language.UZBEK_LATIN -> titleUzLatn
        Language.UZBEK_CYRILLIC -> titleUzCyrl
    }

    fun getSummary(lang: Language): String = when (lang) {
        Language.ENGLISH -> summaryEn
        Language.RUSSIAN -> summaryRu
        Language.UZBEK_LATIN -> summaryUzLatn
        Language.UZBEK_CYRILLIC -> Language.transliterateUzbek(summaryUzLatn, toCyrillic = true)
    }
}

data class GrammarExampleItem(
    val english: String,
    val russian: String,
    val uzbek: String,
    val explanation: String
)
