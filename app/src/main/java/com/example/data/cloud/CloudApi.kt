package com.example.data.cloud

import com.example.data.model.CollocationEntity
import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.GrammarLessonEntity
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.SavedWordEntity
import com.example.data.model.SynonymAntonymEntity
import com.example.data.model.TopicEntity
import com.example.data.model.TopicWordCrossRef
import com.example.data.model.TranslationEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.VocabularyCollectionEntity
import com.example.data.model.WordFormEntity
import com.example.data.model.WordMeaningEntity
import com.example.data.model.Language
import com.example.data.preferences.AppPreferences
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

internal interface CloudApi {
    @POST("auth/register/")
    suspend fun register(@Body request: CredentialsRequest): AuthResponse

    @POST("auth/login/")
    suspend fun signIn(@Body request: CredentialsRequest): AuthResponse

    @POST("auth/logout/")
    suspend fun signOut(@Header("Authorization") authorization: String): Response<Unit>

    @GET("catalog/")
    suspend fun getCatalog(): CatalogSnapshot

    @GET("dictionary/sync/")
    suspend fun syncDictionary(
        @Query("afterId") afterId: Long,
        @Query("limit") limit: Int,
        @Query("since") since: String? = null
    ): DictionarySyncPage

    @GET("dictionary/search/")
    suspend fun searchDictionary(
        @Query("q") query: String,
        @Query("source") sourceLanguage: String? = null,
        @Query("target") targetLanguage: String? = null,
        @Query("pos") partOfSpeech: String? = null,
        @Query("level") cefrLevel: String? = null,
        @Query("partial") matchPartial: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null
    ): DictionarySearchResponseDto

    @POST("sync/")
    suspend fun synchronize(
        @Header("Authorization") authorization: String,
        @Body request: SyncRequest
    ): SyncSnapshot
}

internal data class CredentialsRequest(val email: String, val password: String)

internal data class AuthResponse(val token: String, val email: String)

internal data class CatalogSnapshot(
    val version: String? = null,
    val serverTime: String? = null,
    val dictionary: DictionaryMeta = DictionaryMeta(),
    val topics: List<TopicEntity> = emptyList(),
    val topicWords: List<TopicWordCrossRef> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val conversationTurns: List<ConversationTurnEntity> = emptyList(),
    val grammarLessons: List<GrammarLessonEntity> = emptyList(),
    val quizQuestions: List<QuizQuestionEntity> = emptyList()
)

internal data class DictionaryMeta(
    val entries: Int = 0,
    val maxEntryId: Long = 0,
    val missingRussian: Int = 0,
    val missingUzbek: Int = 0
)

internal data class DictionarySyncPage(
    val entries: List<DictionaryEntryEntity> = emptyList(),
    val meanings: List<WordMeaningEntity> = emptyList(),
    val translations: List<TranslationEntity> = emptyList(),
    val examples: List<ExampleSentenceEntity> = emptyList(),
    val synonyms: List<SynonymAntonymEntity> = emptyList(),
    val forms: List<WordFormEntity> = emptyList(),
    val collocations: List<CollocationEntity> = emptyList(),
    val nextAfterId: Long = 0,
    val hasMore: Boolean = false,
    val serverTime: String? = null,
    val dictionary: DictionaryMeta? = null
)

internal data class SyncRequest(
    val collections: List<CloudCollection>,
    val deletedCollectionIds: List<CloudDeletion>,
    val savedWords: List<CloudSavedWord>,
    val deletedSavedWordIds: List<CloudDeletion>,
    val quizAttempts: List<CloudQuizAttempt>,
    val progress: CloudProgress,
    val preferences: CloudPreferences
)

internal data class CloudDeletion(
    val clientId: String,
    val deletedAt: Long
)

internal data class SyncSnapshot(
    val collections: List<CloudCollection>,
    val savedWords: List<CloudSavedWord>,
    val quizAttempts: List<CloudQuizAttempt>,
    val progress: CloudProgress,
    val preferences: CloudPreferences
)

internal data class CloudCollection(
    val clientId: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isCustom: Boolean,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

internal data class CloudSavedWord(
    val clientId: String,
    val entryId: Long? = null,
    val headword: String,
    val language: String,
    val partOfSpeech: String,
    val phoneticIpa: String,
    val definition: String,
    val translation: String,
    val exampleSentence: String,
    val collectionClientId: String? = null,
    val savedAt: Long,
    val isFavorite: Boolean,
    val status: String,
    val notes: String,
    val repetitionNumber: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewTimestamp: Long,
    val lastReviewTimestamp: Long,
    val lapsesCount: Int,
    val isDeleted: Boolean = false,
    val updatedAt: Long
)

internal data class CloudQuizAttempt(
    val clientId: String,
    val timestamp: Long,
    val category: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val timeSpentSec: Int,
    val xpEarned: Int
)

internal data class CloudProgress(
    val xp: Int,
    val streakDays: Int,
    val lastStudyDateTimestamp: Long,
    val dailyGoalWords: Int,
    val todayWordsReviewed: Int,
    val wordsMasteredCount: Int,
    val totalQuizzesCompleted: Int,
    val totalMinutesListened: Int,
    val updatedAt: Long
) {
    fun toEntity() = UserProgressEntity(
        xp = xp,
        streakDays = streakDays,
        lastStudyDateTimestamp = lastStudyDateTimestamp,
        dailyGoalWords = dailyGoalWords,
        todayWordsReviewed = todayWordsReviewed,
        wordsMasteredCount = wordsMasteredCount,
        totalQuizzesCompleted = totalQuizzesCompleted,
        totalMinutesListened = totalMinutesListened,
        updatedAt = updatedAt
    )

    companion object {
        fun from(entity: UserProgressEntity) = CloudProgress(
            xp = entity.xp,
            streakDays = entity.streakDays,
            lastStudyDateTimestamp = entity.lastStudyDateTimestamp,
            dailyGoalWords = entity.dailyGoalWords,
            todayWordsReviewed = entity.todayWordsReviewed,
            wordsMasteredCount = entity.wordsMasteredCount,
            totalQuizzesCompleted = entity.totalQuizzesCompleted,
            totalMinutesListened = entity.totalMinutesListened,
            updatedAt = entity.updatedAt
        )
    }
}

internal data class CloudPreferences(
    val appLanguage: String,
    val searchSourceLanguage: String,
    val speechRate: Float,
    val speechPitch: Float,
    val selectedVoiceId: String,
    val autoPlayAudio: Boolean,
    val dailyGoalWords: Int,
    val darkMode: Boolean?,
    val updatedAt: Long
) {
    fun toAppPreferences() = AppPreferences(
        appLanguage = Language.fromCode(appLanguage),
        searchSourceLanguage = Language.fromCode(searchSourceLanguage),
        speechRate = speechRate,
        speechPitch = speechPitch,
        selectedVoiceId = selectedVoiceId,
        autoPlayAudio = autoPlayAudio,
        dailyGoalWords = dailyGoalWords,
        isDarkMode = darkMode,
        lastUpdatedTimestamp = updatedAt
    )

    companion object {
        fun from(preferences: AppPreferences) = CloudPreferences(
            appLanguage = preferences.appLanguage.code,
            searchSourceLanguage = preferences.searchSourceLanguage.code,
            speechRate = preferences.speechRate,
            speechPitch = preferences.speechPitch,
            selectedVoiceId = preferences.selectedVoiceId,
            autoPlayAudio = preferences.autoPlayAudio,
            dailyGoalWords = preferences.dailyGoalWords,
            darkMode = preferences.isDarkMode,
            updatedAt = preferences.lastUpdatedTimestamp
        )
    }
}

internal data class DictionarySearchResponseDto(
    val query: String = "",
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val partOfSpeech: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50,
    val hasMore: Boolean = false,
    val next: String? = null,
    val previous: String? = null,
    val backend: String = "fts5",
    val tookMs: Double = 0.0,
    val results: List<DictionarySearchResultDto> = emptyList()
)

internal data class DictionarySearchResultDto(
    val id: Long = 0,
    val headword: String = "",
    val headwordNormalized: String = "",
    val word: String = "",
    val language: String = "en",
    val sourceLanguage: String? = null,
    val partOfSpeech: String = "noun",
    val phoneticIpa: String? = null,
    val cefrLevel: String? = null,
    val frequencyRank: Int = 0,
    val article: String? = null,
    val pluralForm: String? = null,
    val primaryMeaningEn: String? = null,
    val primaryMeaningRu: String? = null,
    val primaryMeaningUz: String? = null,
    val translations: List<TranslationDto>? = null
) {
    fun toEntity(): DictionaryEntryEntity {
        val hWord = headword.ifBlank { word }
        val lang = language.ifBlank { sourceLanguage ?: "en" }
        return DictionaryEntryEntity(
            id = id,
            headword = hWord,
            headwordNormalized = headwordNormalized.ifBlank { Language.normalizeText(hWord) },
            language = lang,
            phoneticIpa = phoneticIpa ?: "",
            partOfSpeech = partOfSpeech.ifBlank { "noun" },
            cefrLevel = cefrLevel ?: "B1",
            frequencyRank = frequencyRank,
            primaryMeaningEn = primaryMeaningEn ?: "",
            primaryMeaningRu = primaryMeaningRu ?: "",
            primaryMeaningUz = primaryMeaningUz ?: ""
        )
    }
}

internal data class TranslationDto(
    val id: Long = 0,
    val targetLanguage: String = "",
    val text: String = "",
    val translationText: String? = null,
    val partOfSpeech: String? = null,
    val contextNote: String? = null
)
