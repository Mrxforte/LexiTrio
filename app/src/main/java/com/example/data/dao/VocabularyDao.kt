package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.LearningStatus
import com.example.data.model.SavedWordEntity
import com.example.data.model.SyncTombstoneEntity
import com.example.data.model.VocabularyCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    fun getAllSavedWords(): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    suspend fun getAllSavedWordsOnce(): List<SavedWordEntity>

    @Query("SELECT * FROM saved_words WHERE isFavorite = 1 ORDER BY savedAt DESC")
    fun getFavoriteWords(): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words WHERE status = :status ORDER BY savedAt DESC")
    fun getWordsByStatus(status: LearningStatus): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words WHERE nextReviewTimestamp <= :currentTimestamp ORDER BY nextReviewTimestamp ASC")
    fun getDueWords(currentTimestamp: Long): Flow<List<SavedWordEntity>>

    @Query("SELECT COUNT(*) FROM saved_words WHERE nextReviewTimestamp <= :currentTimestamp")
    fun getDueWordsCount(currentTimestamp: Long): Flow<Int>

    @Query("SELECT * FROM saved_words WHERE entryId = :entryId LIMIT 1")
    suspend fun getSavedWordByEntryId(entryId: Long): SavedWordEntity?

    @Query("SELECT * FROM saved_words WHERE id = :id LIMIT 1")
    suspend fun getSavedWordById(id: Long): SavedWordEntity?

    @Query("SELECT * FROM saved_words WHERE clientId = :clientId LIMIT 1")
    suspend fun getSavedWordByClientId(clientId: String): SavedWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedWord(word: SavedWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedWords(words: List<SavedWordEntity>)

    @Update
    suspend fun updateSavedWord(word: SavedWordEntity)

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteSavedWord(id: Long)

    @Query("DELETE FROM saved_words WHERE clientId = :clientId")
    suspend fun deleteSavedWordByClientId(clientId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones")
    suspend fun getSyncTombstones(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE id IN (:ids)")
    suspend fun deleteSyncTombstones(ids: List<Long>)

    @Transaction
    suspend fun deleteSavedWordAndRecord(id: Long) {
        val word = getSavedWordById(id) ?: return
        insertTombstone(
            SyncTombstoneEntity(entityType = "saved_word", clientId = word.clientId)
        )
        deleteSavedWord(id)
    }

    @Query("UPDATE saved_words SET isFavorite = :isFav, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean, updatedAt: Long)

    // Collections
    @Query("SELECT * FROM vocabulary_collections ORDER BY id ASC")
    fun getAllCollections(): Flow<List<VocabularyCollectionEntity>>

    @Query("SELECT * FROM vocabulary_collections ORDER BY id ASC")
    suspend fun getAllCollectionsOnce(): List<VocabularyCollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: VocabularyCollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<VocabularyCollectionEntity>)

    @Query("DELETE FROM vocabulary_collections WHERE clientId = :clientId")
    suspend fun deleteCollectionByClientId(clientId: String)

    @Query("DELETE FROM vocabulary_collections WHERE id = :id AND isCustom = 1")
    suspend fun deleteCollection(id: Long)

    @Transaction
    suspend fun deleteCollectionAndRecord(id: Long) {
        val collection = getAllCollectionsOnce().firstOrNull { it.id == id && it.isCustom } ?: return
        insertTombstone(
            SyncTombstoneEntity(entityType = "collection", clientId = collection.clientId)
        )
        deleteCollection(id)
    }
}
