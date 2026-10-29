package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CacheMetadataEntity

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(metadata: CacheMetadataEntity): Long

    @Query("UPDATE cache_metadata SET lastAccessedAt = :now, accessCount = accessCount + 1 WHERE entryId = :entryId")
    suspend fun touch(entryId: Long, now: Long)

    @Query("UPDATE cache_metadata SET lastAccessedAt = :now, accessCount = accessCount + :countDelta WHERE entryId = :entryId")
    suspend fun touchBatch(entryId: Long, now: Long, countDelta: Int)

    @Query("SELECT * FROM cache_metadata WHERE entryId = :entryId")
    suspend fun get(entryId: Long): CacheMetadataEntity?

    @Query("SELECT COUNT(*) FROM cache_metadata")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM cache_metadata WHERE detailValidatedAt > 0")
    suspend fun countDetailed(): Int

    /** Seeds protected rows from the user's own data (saved words, then topic words). */
    @Query("""
        INSERT OR IGNORE INTO cache_metadata (entryId, priority, lastAccessedAt, accessCount, detailValidatedAt, serverVersion)
        SELECT entryId, 0, 0, 0, 0, NULL FROM saved_words
        WHERE entryId IN (SELECT id FROM dictionary_entries)
    """)
    suspend fun seedSavedWords()

    @Query("""
        INSERT OR IGNORE INTO cache_metadata (entryId, priority, lastAccessedAt, accessCount, detailValidatedAt, serverVersion)
        SELECT wordId, 1, 0, 0, 0, NULL FROM topic_word_cross_ref
        WHERE wordId IN (SELECT id FROM dictionary_entries)
    """)
    suspend fun seedTopicWords()

    @Query("UPDATE cache_metadata SET priority = 0 WHERE entryId IN (SELECT entryId FROM saved_words)")
    suspend fun promoteSavedWords()

    @Query("""
        UPDATE cache_metadata SET priority = 1
        WHERE priority > 1 AND entryId IN (SELECT wordId FROM topic_word_cross_ref)
    """)
    suspend fun promoteTopicWords()

    /** Words that were never accessed and are no longer protected fall back to the lazy class. */
    @Query("""
        UPDATE cache_metadata SET priority = 2
        WHERE priority < 2 AND entryId NOT IN (SELECT entryId FROM saved_words)
          AND entryId NOT IN (SELECT wordId FROM topic_word_cross_ref)
    """)
    suspend fun demoteUnprotected()

    /** Prefetch pool: protected and frequently used words first; scored further in Kotlin. */
    @Query("""
        SELECT * FROM cache_metadata
        WHERE detailValidatedAt < :validatedBefore
          AND entryId IN (SELECT id FROM dictionary_entries)
        ORDER BY priority ASC, accessCount DESC, lastAccessedAt DESC
        LIMIT :limit
    """)
    suspend fun getRefreshCandidates(validatedBefore: Long, limit: Int): List<CacheMetadataEntity>

    /** Eviction pool: loaded detail content of unprotected words, least recently used first. */
    @Query("""
        SELECT * FROM cache_metadata
        WHERE detailValidatedAt > 0 AND priority >= 2
          AND entryId NOT IN (SELECT entryId FROM saved_words)
          AND entryId NOT IN (SELECT wordId FROM topic_word_cross_ref)
        ORDER BY lastAccessedAt ASC
        LIMIT :limit
    """)
    suspend fun getEvictionCandidates(limit: Int): List<CacheMetadataEntity>

    @Query("UPDATE cache_metadata SET detailValidatedAt = :now, serverVersion = :version WHERE entryId IN (:ids)")
    suspend fun markDetailLoaded(ids: List<Long>, now: Long, version: String?)

    @Query("UPDATE cache_metadata SET detailValidatedAt = 0, serverVersion = NULL WHERE entryId IN (:ids)")
    suspend fun markDetailEvicted(ids: List<Long>)

    @Query("DELETE FROM cache_metadata WHERE entryId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
