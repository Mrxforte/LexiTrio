package com.example.data.repository

import com.example.data.dao.VocabularyDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import com.example.data.model.VocabularyCollectionEntity
import kotlinx.coroutines.flow.Flow
import com.example.domain.repository.VocabularyRepository as VocabularyRepositoryContract

class VocabularyRepository(private val vocabularyDao: VocabularyDao) : VocabularyRepositoryContract {

    override fun getAllSavedWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getAllSavedWords()

    fun getFavoriteWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getFavoriteWords()

    override fun getWordsByStatus(status: LearningStatus): Flow<List<SavedWordEntity>> = vocabularyDao.getWordsByStatus(status)

    override fun getDueWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getDueWords(System.currentTimeMillis())

    override fun getDueWordsCount(): Flow<Int> = vocabularyDao.getDueWordsCount(System.currentTimeMillis())

    override fun getAllCollections(): Flow<List<VocabularyCollectionEntity>> = vocabularyDao.getAllCollections()

    suspend fun getSavedWordByEntryId(entryId: Long): SavedWordEntity? = vocabularyDao.getSavedWordByEntryId(entryId)

    override suspend fun saveOrUpdateWord(
        entry: DictionaryEntryEntity,
        collectionId: Long,
        notes: String,
        isFavorite: Boolean
    ): Long {
        val existing = vocabularyDao.getSavedWordByEntryId(entry.id)
        if (existing != null) {
            val updated = existing.copy(
                collectionId = collectionId,
                notes = if (notes.isNotBlank()) notes else existing.notes,
                isFavorite = isFavorite || existing.isFavorite,
                updatedAt = System.currentTimeMillis()
            )
            vocabularyDao.updateSavedWord(updated)
            return existing.id
        }

        val newWord = SavedWordEntity(
            entryId = entry.id,
            headword = entry.headword,
            language = entry.language,
            partOfSpeech = entry.partOfSpeech,
            phoneticIpa = entry.phoneticIpa,
            definition = entry.primaryMeaningEn.ifBlank { entry.primaryMeaningUz },
            translation = when (entry.language) {
                "en" -> "${entry.primaryMeaningRu} • ${entry.primaryMeaningUz}"
                "ru" -> "${entry.primaryMeaningEn} • ${entry.primaryMeaningUz}"
                else -> "${entry.primaryMeaningEn} • ${entry.primaryMeaningRu}"
            },
            collectionId = collectionId,
            notes = notes,
            isFavorite = isFavorite,
            status = LearningStatus.NEW,
            nextReviewTimestamp = System.currentTimeMillis()
        )
        return vocabularyDao.insertSavedWord(newWord)
    }

    override suspend fun toggleFavorite(savedWord: SavedWordEntity) {
        vocabularyDao.setFavorite(
            savedWord.id,
            !savedWord.isFavorite,
            System.currentTimeMillis()
        )
    }

    override suspend fun processReview(savedWord: SavedWordEntity, quality: ReviewQuality): SavedWordEntity {
        val updated = savedWord.calculateNextReview(quality)
        vocabularyDao.updateSavedWord(updated)
        return updated
    }

    suspend fun deleteSavedWord(id: Long) = vocabularyDao.deleteSavedWordAndRecord(id)

    override suspend fun createCollection(title: String, description: String, icon: String): Long {
        return vocabularyDao.insertCollection(
            VocabularyCollectionEntity(
                title = title,
                description = description,
                iconName = icon,
                isCustom = true
            )
        )
    }
}
