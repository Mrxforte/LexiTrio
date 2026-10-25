package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

enum class LearningStatus {
    NEW,
    LEARNING,
    DIFFICULT,
    MASTERED
}

enum class ReviewQuality(val grade: Int, val labelEn: String, val labelRu: String, val labelUz: String) {
    AGAIN(1, "Again", "Снова", "Qaytadan"),
    HARD(2, "Hard", "Трудно", "Qiyin"),
    GOOD(3, "Good", "Хорошо", "Yaxshi"),
    EASY(4, "Easy", "Легко", "Oson")
}

@Entity(
    tableName = "saved_words",
    indices = [
        Index("entryId"),
        Index("headword"),
        Index("collectionId"),
        Index("isFavorite"),
        Index("status"),
        Index("nextReviewTimestamp")
    ]
)
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "''")
    val clientId: String = UUID.randomUUID().toString(),
    val entryId: Long = 0,
    val headword: String,
    val language: String = "en",
    val partOfSpeech: String = "noun",
    val phoneticIpa: String = "",
    val definition: String = "",
    val translation: String = "",
    val exampleSentence: String = "",
    val collectionId: Long = 1, // 1 = Default "All Saved Words"
    val savedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val status: LearningStatus = LearningStatus.NEW,
    val notes: String = "",
    // Spaced repetition fields (SM-2 algorithm)
    val repetitionNumber: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val nextReviewTimestamp: Long = System.currentTimeMillis(),
    val lastReviewTimestamp: Long = 0L,
    val lapsesCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Calculates the next review schedule using the SuperMemo SM-2 algorithm.
     */
    fun calculateNextReview(quality: ReviewQuality): SavedWordEntity {
        val q = quality.grade
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60 * 60 * 1000

        var newRepetition = repetitionNumber
        var newInterval: Int
        var newEase = easeFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
        if (newEase < 1.3f) newEase = 1.3f

        var newLapses = lapsesCount
        val newStatus: LearningStatus

        if (q < 3) {
            // Failure (Again / Hard reset)
            newRepetition = 0
            newInterval = 1
            newLapses++
            newStatus = LearningStatus.DIFFICULT
        } else {
            // Success (Good / Easy)
            newRepetition++
            newInterval = when (newRepetition) {
                1 -> 1
                2 -> 6
                else -> (intervalDays * newEase).toInt().coerceAtLeast(1)
            }
            if (q == 4) {
                // Easy bonus
                newInterval = (newInterval * 1.3f).toInt()
            }
            newStatus = if (newRepetition >= 4) LearningStatus.MASTERED else LearningStatus.LEARNING
        }

        val nextTimestamp = now + (newInterval * oneDayMillis)
        return copy(
            repetitionNumber = newRepetition,
            intervalDays = newInterval,
            easeFactor = newEase,
            nextReviewTimestamp = nextTimestamp,
            lastReviewTimestamp = now,
            lapsesCount = newLapses,
            status = newStatus,
            updatedAt = now
        )
    }
}

@Entity(tableName = "vocabulary_collections")
data class VocabularyCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "''")
    val clientId: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val iconName: String = "folder",
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
