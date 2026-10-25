package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.cloud.AuthResponse
import com.example.data.cloud.CloudApi
import com.example.data.cloud.CloudApiFactory
import com.example.data.cloud.CloudCollection
import com.example.data.cloud.CloudDeletion
import com.example.data.cloud.CloudProgress
import com.example.data.cloud.CloudQuizAttempt
import com.example.data.cloud.CloudSavedWord
import com.example.data.cloud.SessionStore
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.LearningStatus
import com.example.data.model.SavedWordEntity
import com.example.data.model.VocabularyCollectionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.SyncSummary
import com.example.domain.model.CloudSession
import com.example.domain.repository.CloudRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CloudRepositoryImpl internal constructor(
    context: Context,
    private val database: LexiTrioDatabase = LexiTrioDatabase.getInstance(context),
    private val api: CloudApi = CloudApiFactory.create()
) : CloudRepository {
    private val sessionStore = SessionStore(context.applicationContext)
    private val preferencesRepository = UserPreferencesRepository(context.applicationContext)
    private val vocabularyDao = database.vocabularyDao()
    private val quizDao = database.quizDao()

    override val session: Flow<CloudSession?> = sessionStore.session

    override suspend fun register(email: String, password: String) {
        saveSession(api.register(credentials(email, password)))
    }

    override suspend fun signIn(email: String, password: String) {
        saveSession(api.signIn(credentials(email, password)))
    }

    override suspend fun signOut() {
        val session = sessionStore.current() ?: return
        try {
            api.signOut(authorization(session.token))
        } finally {
            sessionStore.clear()
        }
    }

    override suspend fun synchronize(): SyncSummary {
        val session = sessionStore.current()
            ?: throw IllegalStateException("Avval email va parol bilan tizimga kiring.")

        val catalog = api.getCatalog()
        database.withTransaction {
            val dictionaryDao = database.dictionaryDao()
            dictionaryDao.insertEntries(catalog.dictionaryEntries)
            dictionaryDao.insertMeanings(catalog.wordMeanings)
            dictionaryDao.insertTranslations(catalog.translations)
            dictionaryDao.insertExamples(catalog.examples)
            dictionaryDao.insertSynonyms(catalog.synonyms)
            dictionaryDao.insertForms(catalog.wordForms)
            dictionaryDao.insertCollocations(catalog.collocations)

            val learningDao = database.learningDao()
            learningDao.insertTopics(catalog.topics)
            learningDao.insertTopicWordCrossRefs(catalog.topicWords)

            val conversationDao = database.conversationGrammarDao()
            conversationDao.insertConversations(catalog.conversations)
            conversationDao.insertConversationTurns(catalog.conversationTurns)
            conversationDao.insertGrammarLessons(catalog.grammarLessons)

            quizDao.insertQuestions(catalog.quizQuestions)
        }

        val collections = vocabularyDao.getAllCollectionsOnce()
        val collectionsById = collections.associateBy { it.id }
        val savedWords = vocabularyDao.getAllSavedWordsOnce()
        val attempts = quizDao.getAllAttemptsOnce()
        val progress = quizDao.getUserProgressOnce()
            ?: com.example.data.model.UserProgressEntity().also {
                quizDao.insertOrUpdateProgress(it)
            }
        val tombstones = vocabularyDao.getSyncTombstones()
        val request = com.example.data.cloud.SyncRequest(
            collections = collections.map { it.toCloud() },
            deletedCollectionIds = tombstones
                .filter { it.entityType == "collection" }
                .map { CloudDeletion(it.clientId, it.deletedAt) },
            savedWords = savedWords.map { word ->
                word.toCloud(collectionsById[word.collectionId]?.clientId)
            },
            deletedSavedWordIds = tombstones
                .filter { it.entityType == "saved_word" }
                .map { CloudDeletion(it.clientId, it.deletedAt) },
            quizAttempts = attempts.map {
                CloudQuizAttempt(
                    clientId = it.clientId,
                    timestamp = it.timestamp,
                    category = it.category,
                    totalQuestions = it.totalQuestions,
                    correctCount = it.correctCount,
                    timeSpentSec = it.timeSpentSec,
                    xpEarned = it.xpEarned
                )
            },
            progress = CloudProgress.from(progress),
            preferences = com.example.data.cloud.CloudPreferences.from(
                preferencesRepository.preferences.first()
            )
        )

        val snapshot = api.synchronize(authorization(session.token), request)
        mergeSnapshot(snapshot)
        preferencesRepository.replaceWith(snapshot.preferences.toAppPreferences())
        if (tombstones.isNotEmpty()) {
            vocabularyDao.deleteSyncTombstones(tombstones.map { it.id })
        }

        return SyncSummary(
            savedWords = snapshot.savedWords.count { !it.isDeleted },
            quizAttempts = snapshot.quizAttempts.size,
            catalogEntries = catalog.dictionaryEntries.size
        )
    }

    private suspend fun saveSession(response: AuthResponse) {
        sessionStore.save(CloudSession(email = response.email, token = response.token))
    }

    private suspend fun mergeSnapshot(snapshot: com.example.data.cloud.SyncSnapshot) {
        val localCollections = vocabularyDao.getAllCollectionsOnce().associateBy { it.clientId }
        snapshot.collections.forEach { remote ->
            val local = localCollections[remote.clientId]
            if (remote.isDeleted) {
                vocabularyDao.deleteCollectionByClientId(remote.clientId)
            } else {
                vocabularyDao.insertCollection(
                    VocabularyCollectionEntity(
                        id = local?.id ?: 0,
                        clientId = remote.clientId,
                        title = remote.title,
                        description = remote.description,
                        iconName = remote.iconName,
                        isCustom = remote.isCustom,
                        createdAt = remote.createdAt,
                        updatedAt = remote.updatedAt
                    )
                )
            }
        }

        val updatedCollections = vocabularyDao.getAllCollectionsOnce()
            .associateBy { it.clientId }
        val localWords = vocabularyDao.getAllSavedWordsOnce().associateBy { it.clientId }
        snapshot.savedWords.forEach { remote ->
            val local = localWords[remote.clientId]
            if (remote.isDeleted) {
                vocabularyDao.deleteSavedWordByClientId(remote.clientId)
            } else {
                val collectionId = remote.collectionClientId
                    ?.let(updatedCollections::get)
                    ?.id ?: local?.collectionId ?: 1L
                vocabularyDao.insertSavedWord(
                    SavedWordEntity(
                        id = local?.id ?: 0,
                        clientId = remote.clientId,
                        entryId = remote.entryId ?: local?.entryId ?: 0,
                        headword = remote.headword,
                        language = remote.language,
                        partOfSpeech = remote.partOfSpeech,
                        phoneticIpa = remote.phoneticIpa,
                        definition = remote.definition,
                        translation = remote.translation,
                        exampleSentence = remote.exampleSentence,
                        collectionId = collectionId,
                        savedAt = remote.savedAt,
                        isFavorite = remote.isFavorite,
                        status = LearningStatus.valueOf(remote.status),
                        notes = remote.notes,
                        repetitionNumber = remote.repetitionNumber,
                        intervalDays = remote.intervalDays,
                        easeFactor = remote.easeFactor,
                        nextReviewTimestamp = remote.nextReviewTimestamp,
                        lastReviewTimestamp = remote.lastReviewTimestamp,
                        lapsesCount = remote.lapsesCount,
                        updatedAt = remote.updatedAt
                    )
                )
            }
        }

        val localAttempts = quizDao.getAllAttemptsOnce().associateBy { it.clientId }
        quizDao.insertAttempts(
            snapshot.quizAttempts.map { remote ->
                val local = localAttempts[remote.clientId]
                com.example.data.model.QuizAttemptEntity(
                    id = local?.id ?: 0,
                    clientId = remote.clientId,
                    timestamp = remote.timestamp,
                    category = remote.category,
                    totalQuestions = remote.totalQuestions,
                    correctCount = remote.correctCount,
                    timeSpentSec = remote.timeSpentSec,
                    xpEarned = remote.xpEarned
                )
            }
        )
        quizDao.insertOrUpdateProgress(snapshot.progress.toEntity())
    }

    private fun credentials(email: String, password: String) =
        com.example.data.cloud.CredentialsRequest(email.trim(), password)

    private fun authorization(token: String) = "Token $token"

    private fun VocabularyCollectionEntity.toCloud() = CloudCollection(
        clientId = clientId,
        title = title,
        description = description,
        iconName = iconName,
        isCustom = isCustom,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SavedWordEntity.toCloud(collectionClientId: String?) = CloudSavedWord(
        clientId = clientId,
        entryId = entryId.takeIf { it > 0 },
        headword = headword,
        language = language,
        partOfSpeech = partOfSpeech,
        phoneticIpa = phoneticIpa,
        definition = definition,
        translation = translation,
        exampleSentence = exampleSentence,
        collectionClientId = collectionClientId,
        savedAt = savedAt,
        isFavorite = isFavorite,
        status = status.name,
        notes = notes,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        nextReviewTimestamp = nextReviewTimestamp,
        lastReviewTimestamp = lastReviewTimestamp,
        lapsesCount = lapsesCount,
        updatedAt = updatedAt
    )
}
