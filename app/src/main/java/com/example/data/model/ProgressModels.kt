package com.example.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val language: String = "en",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey
    val id: Int = 1,
    val xp: Int = 120,
    val streakDays: Int = 0,
    val lastStudyDateTimestamp: Long = System.currentTimeMillis(),
    val dailyGoalWords: Int = 10,
    val todayWordsReviewed: Int = 4,
    val wordsMasteredCount: Int = 28,
    val totalQuizzesCompleted: Int = 5,
    val totalMinutesListened: Int = 22,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    val level: Int
        get() = (xp / 100) + 1

    fun getLevelTitle(lang: Language): String {
        return when {
            level < 3 -> when (lang) {
                Language.ENGLISH -> "Beginner Linguist"
                Language.RUSSIAN -> "Начинающий лингвист"
                Language.UZBEK_LATIN -> "Boshlang‘ich tilshunos"
                Language.UZBEK_CYRILLIC -> "Бошланғич тилшунос"
            }
            level < 7 -> when (lang) {
                Language.ENGLISH -> "Avid Learner"
                Language.RUSSIAN -> "Увлеченный ученик"
                Language.UZBEK_LATIN -> "Faol o‘rganuvchi"
                Language.UZBEK_CYRILLIC -> "Фаол ўрганувчи"
            }
            level < 15 -> when (lang) {
                Language.ENGLISH -> "Polyglot Scholar"
                Language.RUSSIAN -> "Полиглот-эрудит"
                Language.UZBEK_LATIN -> "Poliglot bilimdon"
                Language.UZBEK_CYRILLIC -> "Полиглот билимдон"
            }
            else -> when (lang) {
                Language.ENGLISH -> "Lexicography Master"
                Language.RUSSIAN -> "Мастер лексикографии"
                Language.UZBEK_LATIN -> "Leksikografiya ustasi"
                Language.UZBEK_CYRILLIC -> "Лексикография устаси"
            }
        }
    }
}

data class AchievementItem(
    val id: String,
    val titleEn: String,
    val titleRu: String,
    val titleUzLatn: String,
    val descriptionEn: String,
    val descriptionRu: String,
    val descriptionUzLatn: String,
    val iconName: String,
    val isUnlocked: Boolean,
    val progress: Float = 1f
)
