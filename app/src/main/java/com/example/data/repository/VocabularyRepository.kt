package com.example.data.repository

import com.example.data.dao.VocabularyDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import com.example.data.model.VocabularyCollectionEntity
import kotlinx.coroutines.flow.Flow

class VocabularyRepository(private val vocabularyDao: VocabularyDao) {

    fun getAllSavedWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getAllSavedWords()

    fun getFavoriteWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getFavoriteWords()

    fun getWordsByStatus(status: LearningStatus): Flow<List<SavedWordEntity>> = vocabularyDao.getWordsByStatus(status)

    fun getDueWords(): Flow<List<SavedWordEntity>> = vocabularyDao.getDueWords(System.currentTimeMillis())

    fun getDueWordsCount(): Flow<Int> = vocabularyDao.getDueWordsCount(System.currentTimeMillis())

    fun getAllCollections(): Flow<List<VocabularyCollectionEntity>> = vocabularyDao.getAllCollections()

    suspend fun getSavedWordByEntryId(entryId: Long): SavedWordEntity? = vocabularyDao.getSavedWordByEntryId(entryId)

    suspend fun saveOrUpdateWord(
        entry: DictionaryEntryEntity,
        collectionId: Long = 1,
        notes: String = "",
        isFavorite: Boolean = false
    ): Long {
        val existing = vocabularyDao.getSavedWordByEntryId(entry.id)
        if (existing != null) {
            val updated = existing.copy(
                collectionId = collectionId,
                notes = if (notes.isNotBlank()) notes else existing.notes,
                isFavorite = isFavorite || existing.isFavorite
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

    suspend fun toggleFavorite(savedWord: SavedWordEntity) {
        vocabularyDao.setFavorite(savedWord.id, !savedWord.isFavorite)
    }

    suspend fun processReview(savedWord: SavedWordEntity, quality: ReviewQuality): SavedWordEntity {
        val updated = savedWord.calculateNextReview(quality)
        vocabularyDao.updateSavedWord(updated)
        return updated
    }

    suspend fun deleteSavedWord(id: Long) = vocabularyDao.deleteSavedWord(id)

    suspend fun createCollection(title: String, description: String, icon: String = "folder"): Long {
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
