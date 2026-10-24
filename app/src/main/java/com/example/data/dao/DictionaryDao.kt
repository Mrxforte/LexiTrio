package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.CollocationEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.FullDictionaryDetail
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.SynonymAntonymEntity
import com.example.data.model.TranslationEntity
import com.example.data.model.WordFormEntity
import com.example.data.model.WordMeaningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    fun getEntryCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getEntryCount(): Int

    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE headwordNormalized LIKE :queryPattern || '%' 
           OR headwordNormalized LIKE '%' || :queryPattern || '%'
           OR alternativeSpellings LIKE '%' || :queryPattern || '%'
        ORDER BY 
           CASE WHEN headwordNormalized = :exactQuery THEN 1 
                WHEN headwordNormalized LIKE :queryPattern || '%' THEN 2 
                ELSE 3 END,
           frequencyRank DESC,
           headword ASC
        LIMIT :limit
    """)
    suspend fun searchEntries(queryPattern: String, exactQuery: String, limit: Int = 50): List<DictionaryEntryEntity>

    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE language = :lang
          AND (headwordNormalized LIKE :queryPattern || '%' 
               OR headwordNormalized LIKE '%' || :queryPattern || '%'
               OR alternativeSpellings LIKE '%' || :queryPattern || '%')
        ORDER BY 
           CASE WHEN headwordNormalized = :exactQuery THEN 1 
                WHEN headwordNormalized LIKE :queryPattern || '%' THEN 2 
                ELSE 3 END,
           frequencyRank DESC,
           headword ASC
        LIMIT :limit
    """)
    suspend fun searchEntriesByLanguage(queryPattern: String, exactQuery: String, lang: String, limit: Int = 50): List<DictionaryEntryEntity>

    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE (:lang IS NULL OR language = :lang)
          AND (:pos IS NULL OR partOfSpeech = :pos)
          AND (headwordNormalized LIKE '%' || :queryPattern || '%' 
               OR primaryMeaningEn LIKE '%' || :queryPattern || '%'
               OR primaryMeaningRu LIKE '%' || :queryPattern || '%'
               OR primaryMeaningUz LIKE '%' || :queryPattern || '%')
        ORDER BY frequencyRank DESC, headword ASC
        LIMIT :limit
    """)
    suspend fun searchFiltered(queryPattern: String, lang: String?, pos: String?, limit: Int = 50): List<DictionaryEntryEntity>

    @Query("""
        SELECT d.* FROM dictionary_entries d
        INNER JOIN translations t ON d.id = t.entryId
        WHERE t.translationText LIKE '%' || :translationQuery || '%'
        GROUP BY d.id
        LIMIT :limit
    """)
    suspend fun searchByTranslation(translationQuery: String, limit: Int = 50): List<DictionaryEntryEntity>

    @Transaction
    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    suspend fun getFullDictionaryDetail(id: Long): FullDictionaryDetail?

    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): DictionaryEntryEntity?

    @Query("SELECT * FROM dictionary_entries ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWord(): DictionaryEntryEntity?

    @Query("SELECT * FROM dictionary_entries ORDER BY frequencyRank DESC LIMIT :limit")
    fun getTopWords(limit: Int = 20): Flow<List<DictionaryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DictionaryEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DictionaryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeanings(meanings: List<WordMeaningEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<TranslationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamples(examples: List<ExampleSentenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(synonyms: List<SynonymAntonymEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForms(forms: List<WordFormEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollocations(collocations: List<CollocationEntity>)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchItem(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
