package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LearningStatus
import com.example.data.model.SavedWordEntity
import com.example.data.model.VocabularyCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    fun getAllSavedWords(): Flow<List<SavedWordEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedWord(word: SavedWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedWords(words: List<SavedWordEntity>)

    @Update
    suspend fun updateSavedWord(word: SavedWordEntity)

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteSavedWord(id: Long)

    @Query("UPDATE saved_words SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    // Collections
    @Query("SELECT * FROM vocabulary_collections ORDER BY id ASC")
    fun getAllCollections(): Flow<List<VocabularyCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: VocabularyCollectionEntity): Long

    @Query("DELETE FROM vocabulary_collections WHERE id = :id AND isCustom = 1")
    suspend fun deleteCollection(id: Long)
}
