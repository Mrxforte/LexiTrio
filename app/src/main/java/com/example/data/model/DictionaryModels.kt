package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Full-text index over the searchable columns of [DictionaryEntryEntity].
 * Room keeps it in sync through triggers on the content table, so writes must
 * go through UPDATE/INSERT (never REPLACE, which bypasses delete triggers).
 */
@Fts4(contentEntity = DictionaryEntryEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "dictionary_fts")
data class DictionaryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val headwordNormalized: String,
    val alternativeSpellings: String,
    val primaryMeaningEn: String,
    val primaryMeaningRu: String,
    val primaryMeaningUz: String
)

@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(value = ["headword"]),
        Index(value = ["headwordNormalized"]),
        Index(value = ["language"]),
        Index(value = ["partOfSpeech"]),
        Index(value = ["cefrLevel"]),
        Index(value = ["headwordNormalized", "language"])
    ]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val headword: String,
    val headwordNormalized: String,
    val language: String, // "en", "ru", "uz", "uz-cyrl"
    val phoneticIpa: String = "",
    val partOfSpeech: String = "noun", // noun, verb, adjective, adverb, etc.
    val cefrLevel: String = "B1", // A1, A2, B1, B2, C1, C2
    val frequencyRank: Int = 0,
    val lemma: String = "",
    val alternativeSpellings: String = "",
    val transliteration: String = "",
    val etymology: String = "",
    val verified: Boolean = true,
    val source: String = "LexiTrio Core Corpus",
    val primaryMeaningEn: String = "",
    val primaryMeaningRu: String = "",
    val primaryMeaningUz: String = ""
)

@Entity(
    tableName = "word_meanings",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId")]
)
data class WordMeaningEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val senseNumber: Int = 1,
    val definitionEn: String,
    val definitionRu: String,
    val definitionUz: String,
    val register: String = "general", // formal, informal, academic, slang, technical
    val grammarNotes: String = ""
)

@Entity(
    tableName = "translations",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("entryId"),
        Index("targetLanguage"),
        Index("translationText")
    ]
)
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val meaningId: Long = 0,
    val targetLanguage: String, // "en", "ru", "uz", "uz-cyrl"
    val translationText: String,
    val transliteration: String = "",
    val contextNote: String = ""
)

@Entity(
    tableName = "example_sentences",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId")]
)
data class ExampleSentenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val meaningId: Long = 0,
    val sentenceEn: String,
    val sentenceRu: String,
    val sentenceUz: String
)

@Entity(
    tableName = "synonyms_antonyms",
    indices = [Index("meaningId"), Index("word")]
)
data class SynonymAntonymEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meaningId: Long,
    val word: String,
    val isAntonym: Boolean = false // false = synonym, true = antonym
)

@Entity(
    tableName = "word_forms",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId")]
)
data class WordFormEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val formType: String, // e.g. "Past Simple", "Comparative", "Plural", "Rod/Gender"
    val formValue: String
)

@Entity(
    tableName = "collocations",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId")]
)
data class CollocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val expression: String,
    val translationRu: String,
    val translationUz: String
)

data class FullDictionaryDetail(
    @Embedded
    val entry: DictionaryEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val meanings: List<WordMeaningEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val translations: List<TranslationEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val examples: List<ExampleSentenceEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val forms: List<WordFormEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val collocations: List<CollocationEntity> = emptyList()
)
