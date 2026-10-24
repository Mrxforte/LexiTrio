package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.GrammarLessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationGrammarDao {

    // Conversations
    @Query("SELECT * FROM conversations ORDER BY id ASC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY turnIndex ASC")
    fun getConversationTurns(conversationId: Long): Flow<List<ConversationTurnEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversationTurns(turns: List<ConversationTurnEntity>)

    // Grammar Lessons
    @Query("SELECT * FROM grammar_lessons ORDER BY id ASC")
    fun getAllGrammarLessons(): Flow<List<GrammarLessonEntity>>

    @Query("SELECT * FROM grammar_lessons WHERE id = :id LIMIT 1")
    suspend fun getGrammarLessonById(id: Long): GrammarLessonEntity?

    @Query("SELECT DISTINCT category FROM grammar_lessons")
    fun getGrammarCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrammarLessons(lessons: List<GrammarLessonEntity>)
}
