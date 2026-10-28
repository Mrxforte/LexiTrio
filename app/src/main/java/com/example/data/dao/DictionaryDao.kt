package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Query("""
        SELECT d.* FROM dictionary_entries d
        WHERE (
            ((d.language = :source OR (:source = 'uz' AND d.language = 'uz-cyrl'))
              AND (d.id IN (SELECT rowid FROM dictionary_fts WHERE dictionary_fts MATCH :headwordMatch)
                   OR d.id IN (SELECT rowid FROM dictionary_fts WHERE dictionary_fts MATCH :spellingsMatch)))
            OR d.id IN (SELECT rowid FROM dictionary_fts WHERE dictionary_fts MATCH :meaningMatch)
        )
        AND (d.language = :target OR (:target = 'uz' AND d.language = 'uz-cyrl')
             OR (:target = 'en' AND d.primaryMeaningEn != '')
             OR (:target = 'ru' AND d.primaryMeaningRu != '')
             OR (:target = 'uz' AND d.primaryMeaningUz != ''))
        ORDER BY CASE WHEN (d.language = :source OR (:source = 'uz' AND d.language = 'uz-cyrl'))
                           AND d.headwordNormalized >= :query AND d.headwordNormalized < :upperBound
                      THEN 0 ELSE 1 END,
                 d.headwordNormalized ASC, d.id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchDirected(
        headwordMatch: String, spellingsMatch: String, meaningMatch: String, source: String, target: String,
        query: String, upperBound: String, limit: Int, offset: Int
    ): List<DictionaryEntryEntity>

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
        WHERE (language = :lang OR (:lang = 'uz' AND language = 'uz-cyrl'))
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
        WHERE (:lang IS NULL 
               OR language = :lang 
               OR (:lang = 'uz' AND (primaryMeaningUz != '' OR language = 'uz-cyrl'))
               OR (:lang = 'ru' AND primaryMeaningRu != '')
               OR (:lang = 'en' AND primaryMeaningEn != ''))
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
          AND (:lang IS NULL OR t.targetLanguage = :lang OR (:lang = 'uz' AND t.targetLanguage = 'uz-cyrl') OR d.language = :lang)
        GROUP BY d.id
        LIMIT :limit
    """)
    suspend fun searchByTranslation(translationQuery: String, lang: String? = null, limit: Int = 50): List<DictionaryEntryEntity>

    @Query("""
        SELECT d.* FROM dictionary_entries d
        LEFT JOIN translations t ON d.id = t.entryId
        WHERE (
            (:lang IS NULL AND (
                d.headwordNormalized LIKE '%' || :queryPattern || '%'
                OR d.alternativeSpellings LIKE '%' || :queryPattern || '%'
                OR d.primaryMeaningEn LIKE '%' || :queryPattern || '%'
                OR d.primaryMeaningRu LIKE '%' || :queryPattern || '%'
                OR d.primaryMeaningUz LIKE '%' || :queryPattern || '%'
                OR t.translationText LIKE '%' || :queryPattern || '%'
                OR (:altQueryPattern != '' AND (
                    d.primaryMeaningUz LIKE '%' || :altQueryPattern || '%'
                    OR t.translationText LIKE '%' || :altQueryPattern || '%'
                ))
            ))
            OR (:lang = 'en' AND (
                (d.language = 'en' AND (d.headwordNormalized LIKE '%' || :queryPattern || '%' OR d.alternativeSpellings LIKE '%' || :queryPattern || '%'))
                OR d.primaryMeaningEn LIKE '%' || :queryPattern || '%'
            ))
            OR (:lang = 'ru' AND (
                (d.language = 'ru' AND (d.headwordNormalized LIKE '%' || :queryPattern || '%' OR d.alternativeSpellings LIKE '%' || :queryPattern || '%'))
                OR d.primaryMeaningRu LIKE '%' || :queryPattern || '%'
                OR (t.targetLanguage = 'ru' AND t.translationText LIKE '%' || :queryPattern || '%')
            ))
            OR ((:lang = 'uz' OR :lang = 'uz-cyrl') AND (
                (d.language IN ('uz', 'uz-cyrl') AND (d.headwordNormalized LIKE '%' || :queryPattern || '%' OR d.alternativeSpellings LIKE '%' || :queryPattern || '%'))
                OR d.primaryMeaningUz LIKE '%' || :queryPattern || '%'
                OR (:altQueryPattern != '' AND d.primaryMeaningUz LIKE '%' || :altQueryPattern || '%')
                OR (t.targetLanguage IN ('uz', 'uz-cyrl') AND (
                    t.translationText LIKE '%' || :queryPattern || '%'
                    OR (:altQueryPattern != '' AND t.translationText LIKE '%' || :altQueryPattern || '%')
                ))
            ))
        )
        AND (:pos IS NULL OR d.partOfSpeech = :pos)
        GROUP BY d.id
        ORDER BY 
            CASE 
                WHEN d.headwordNormalized = :exactQuery THEN 1
                WHEN d.primaryMeaningUz = :exactQuery OR d.primaryMeaningRu = :exactQuery OR d.primaryMeaningEn = :exactQuery THEN 2
                WHEN d.headwordNormalized LIKE :queryPattern || '%' THEN 3
                WHEN d.primaryMeaningUz LIKE :queryPattern || '%' OR d.primaryMeaningRu LIKE :queryPattern || '%' OR d.primaryMeaningEn LIKE :queryPattern || '%' OR (:altQueryPattern != '' AND d.primaryMeaningUz LIKE :altQueryPattern || '%') THEN 4
                ELSE 5
            END,
            d.frequencyRank DESC,
            d.headword ASC
        LIMIT :limit
    """)
    suspend fun searchComprehensive(
        queryPattern: String,
        exactQuery: String,
        altQueryPattern: String = "",
        lang: String? = null,
        pos: String? = null,
        limit: Int = 100
    ): List<DictionaryEntryEntity>

    /**
     * Headword exact/prefix matches via an index range scan (no `%x%` table
     * scan), in alphabetical order: `s` lists sa…, sb…, … page by page, and the
     * exact word sorts first because it is the shortest string in the range.
     * [upperBound] is the query followed by U+FFFF. The language filter is
     * strict here: searching in Uzbek lists Uzbek words first, not English
     * words that merely have an Uzbek translation (those come from the FTS arm).
     */
    @Query("""
        SELECT * FROM dictionary_entries
        WHERE headwordNormalized >= :query AND headwordNormalized < :upperBound
          AND (:lang IS NULL
               OR language = :lang
               OR (:lang = 'uz' AND language = 'uz-cyrl'))
          AND (:pos IS NULL OR partOfSpeech = :pos)
        ORDER BY headwordNormalized ASC, id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchHeadwordPrefix(
        query: String,
        upperBound: String,
        lang: String?,
        pos: String?,
        limit: Int,
        offset: Int
    ): List<DictionaryEntryEntity>

    @Query("""
        SELECT COUNT(*) FROM dictionary_entries
        WHERE headwordNormalized >= :query AND headwordNormalized < :upperBound
          AND (:lang IS NULL
               OR language = :lang
               OR (:lang = 'uz' AND language = 'uz-cyrl'))
          AND (:pos IS NULL OR partOfSpeech = :pos)
    """)
    suspend fun countHeadwordPrefix(
        query: String,
        upperBound: String,
        lang: String?,
        pos: String?
    ): Int

    /**
     * Word-level matches in headwords, meanings and spellings through FTS4.
     * Rows already listed by [searchHeadwordPrefix] for the same arguments are
     * left out so the two lists can be paged one after the other.
     */
    @Query("""
        SELECT d.* FROM dictionary_fts
        JOIN dictionary_entries AS d ON d.id = dictionary_fts.rowid
        WHERE dictionary_fts MATCH :match
          AND (:lang IS NULL
               OR d.language = :lang
               OR (:lang = 'uz' AND (d.language = 'uz-cyrl' OR d.primaryMeaningUz != ''))
               OR (:lang = 'ru' AND d.primaryMeaningRu != '')
               OR (:lang = 'en' AND d.primaryMeaningEn != ''))
          AND (:pos IS NULL OR d.partOfSpeech = :pos)
          AND NOT (d.headwordNormalized >= :query AND d.headwordNormalized < :upperBound
                   AND (:lang IS NULL
                        OR d.language = :lang
                        OR (:lang = 'uz' AND d.language = 'uz-cyrl')))
        ORDER BY d.frequencyRank DESC, d.headword ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchFullText(
        match: String,
        query: String,
        upperBound: String,
        lang: String?,
        pos: String?,
        limit: Int,
        offset: Int
    ): List<DictionaryEntryEntity>

    @Query("INSERT INTO dictionary_fts(dictionary_fts) VALUES('rebuild')")
    suspend fun rebuildFullTextIndex()

    @Transaction
    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    suspend fun getFullDictionaryDetail(id: Long): FullDictionaryDetail?

    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): DictionaryEntryEntity?

    @Query("SELECT MIN(id) FROM dictionary_entries")
    suspend fun getMinEntryId(): Long?

    @Query("SELECT MAX(id) FROM dictionary_entries")
    suspend fun getMaxEntryId(): Long?

    @Query("""
        SELECT * FROM dictionary_entries
        WHERE id >= :id
          AND TRIM(CASE
              WHEN language = :source OR (:source = 'uz' AND language = 'uz-cyrl') THEN headword
              WHEN :source = 'en' THEN primaryMeaningEn
              WHEN :source = 'ru' THEN primaryMeaningRu
              WHEN :source = 'uz' THEN primaryMeaningUz
              ELSE '' END) != ''
          AND TRIM(CASE
              WHEN language = :target OR (:target = 'uz' AND language = 'uz-cyrl') THEN headword
              WHEN :target = 'en' THEN primaryMeaningEn
              WHEN :target = 'ru' THEN primaryMeaningRu
              WHEN :target = 'uz' THEN primaryMeaningUz
              ELSE '' END) != ''
        ORDER BY id ASC LIMIT 1
    """)
    suspend fun getFirstEntryForDirection(id: Long, source: String, target: String): DictionaryEntryEntity?

    @Query("SELECT * FROM dictionary_entries ORDER BY frequencyRank DESC LIMIT :limit")
    fun getTopWords(limit: Int = 20): Flow<List<DictionaryEntryEntity>>

    // Upsert (not REPLACE): replacing a row deletes it without firing the FTS
    // sync triggers and would cascade-delete its meanings and translations.
    @Upsert
    suspend fun insertEntries(entries: List<DictionaryEntryEntity>): List<Long>

    @Upsert
    suspend fun insertEntry(entry: DictionaryEntryEntity): Long

    /** Caches search hits fetched from the server without touching richer local rows. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntriesIfAbsent(entries: List<DictionaryEntryEntity>): List<Long>

    // Lazily loaded content, removed per word when it is evicted or refreshed.
    @Query("DELETE FROM translations WHERE entryId IN (:entryIds)")
    suspend fun deleteTranslationsFor(entryIds: List<Long>)

    @Query("DELETE FROM example_sentences WHERE entryId IN (:entryIds)")
    suspend fun deleteExamplesFor(entryIds: List<Long>)

    @Query("DELETE FROM word_forms WHERE entryId IN (:entryIds)")
    suspend fun deleteFormsFor(entryIds: List<Long>)

    @Query("DELETE FROM collocations WHERE entryId IN (:entryIds)")
    suspend fun deleteCollocationsFor(entryIds: List<Long>)

    @Query("DELETE FROM synonyms_antonyms WHERE meaningId IN (SELECT id FROM word_meanings WHERE entryId IN (:entryIds))")
    suspend fun deleteSynonymsFor(entryIds: List<Long>)

    @Query("DELETE FROM word_meanings WHERE entryId IN (:entryIds)")
    suspend fun deleteMeaningsFor(entryIds: List<Long>)

    /**
     * Applies server-side deletions. A word the user saved is kept: it is user
     * data, and the server removing it must not silently empty their list.
     */
    @Query("DELETE FROM dictionary_entries WHERE id IN (:ids) AND id NOT IN (SELECT entryId FROM saved_words)")
    suspend fun deleteEntriesUnlessSaved(ids: List<Long>)

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

    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSearchQuery(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchItem(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
