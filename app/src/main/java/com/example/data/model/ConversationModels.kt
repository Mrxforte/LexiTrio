package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titleEn: String,
    val titleRu: String,
    val titleUzLatn: String,
    val titleUzCyrl: String,
    val situationEn: String,
    val situationRu: String,
    val situationUzLatn: String,
    val category: String, // e.g., "Travel", "Dining", "Career", "Health", "Social"
    val difficulty: String = "A2",
    val iconName: String = "chat",
    val speakerOneName: String = "Speaker A",
    val speakerTwoName: String = "Speaker B"
) {
    fun getTitle(lang: Language): String = when (lang) {
        Language.ENGLISH -> titleEn
        Language.RUSSIAN -> titleRu
        Language.UZBEK_LATIN -> titleUzLatn
        Language.UZBEK_CYRILLIC -> titleUzCyrl
    }

    fun getSituation(lang: Language): String = when (lang) {
        Language.ENGLISH -> situationEn
        Language.RUSSIAN -> situationRu
        Language.UZBEK_LATIN -> situationUzLatn
        Language.UZBEK_CYRILLIC -> Language.transliterateUzbek(situationUzLatn, toCyrillic = true)
    }
}

@Entity(
    tableName = "conversation_turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ConversationTurnEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val turnIndex: Int,
    val speakerRole: String, // e.g. "Doctor", "Patient", "Customer", "Waiter"
    val isSpeakerOne: Boolean = true,
    val textEn: String,
    val textRu: String,
    val textUz: String,
    val culturalNote: String = ""
)
