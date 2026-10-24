package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titleEn: String,
    val titleRu: String,
    val titleUzLatn: String,
    val titleUzCyrl: String,
    val category: String, // e.g., "Daily Life", "Travel", "Business", "Tech", "Food", "Emergency"
    val iconName: String = "book",
    val descriptionEn: String = "",
    val descriptionRu: String = "",
    val descriptionUzLatn: String = "",
    val cefrLevel: String = "A2",
    val estimatedMinutes: Int = 10,
    val totalWords: Int = 15
) {
    fun getTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> titleEn
        Language.RUSSIAN -> titleRu
        Language.UZBEK_LATIN -> titleUzLatn
        Language.UZBEK_CYRILLIC -> titleUzCyrl
    }

    fun getDescription(lang: Language): String = when (lang) {
        Language.ENGLISH -> descriptionEn
        Language.RUSSIAN -> descriptionRu
        Language.UZBEK_LATIN -> descriptionUzLatn
        Language.UZBEK_CYRILLIC -> Language.transliterateUzbek(descriptionUzLatn, toCyrillic = true)
    }
}

@Entity(
    tableName = "topic_word_cross_ref",
    primaryKeys = ["topicId", "wordId"],
    indices = [Index("wordId")]
)
data class TopicWordCrossRef(
    val topicId: Long,
    val wordId: Long
)
