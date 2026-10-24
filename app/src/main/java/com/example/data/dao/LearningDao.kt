package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.TopicEntity
import com.example.data.model.TopicWordCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {

    @Query("SELECT * FROM topics ORDER BY id ASC")
    fun getAllTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): TopicEntity?

    @Query("""
        SELECT d.* FROM dictionary_entries d
        INNER JOIN topic_word_cross_ref c ON d.id = c.wordId
        WHERE c.topicId = :topicId
        ORDER BY d.headword ASC
    """)
    fun getWordsForTopic(topicId: Long): Flow<List<DictionaryEntryEntity>>

    @Query("""
        SELECT d.* FROM dictionary_entries d
        INNER JOIN topic_word_cross_ref c ON d.id = c.wordId
        WHERE c.topicId = :topicId
        ORDER BY d.headword ASC
    """)
    suspend fun getWordsForTopicList(topicId: Long): List<DictionaryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicWordCrossRefs(refs: List<TopicWordCrossRef>)
}
