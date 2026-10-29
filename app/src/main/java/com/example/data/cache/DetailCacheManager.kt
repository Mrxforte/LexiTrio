package com.example.data.cache

import androidx.room.withTransaction
import com.example.data.cloud.CloudApi
import com.example.data.cloud.DictionaryDetailsResponse
import com.example.data.database.LexiTrioDatabase
import com.example.data.model.CacheMetadataEntity
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Adaptive cache for lazily loaded word details with True 7-Day Sliding TTL.
 *
 * Word rows are the offline search index and are never evicted here. Detail
 * content is fetched on demand (or by [prefetch]), stored in Room together with
 * metadata, revalidated after a TTL, and evicted by score or inactivity when over budget.
 * Saved words and topic words are protected and never evicted.
 */
internal class DetailCacheManager(
    private val database: LexiTrioDatabase,
    private val api: CloudApi?,
    val policy: CachePolicy = CachePolicy(),
    val clock: () -> Long = System::currentTimeMillis
) {
    private val dictionaryDao get() = database.dictionaryDao()
    private val cacheDao get() = database.cacheDao()

    private val accessBuffer = ConcurrentHashMap<Long, AccessSpec>()

    data class AccessSpec(val lastAccessedAt: Long, val countDelta: Int)

    /** Records that the user opened a word; feeds the frequency/recency score. */
    suspend fun recordAccess(entryId: Long) {
        val now = clock()
        database.withTransaction {
            cacheDao.insertIfAbsent(CacheMetadataEntity(entryId = entryId, lastAccessedAt = now))
            cacheDao.touch(entryId, now)
        }
    }

    /** Evicts details that have not been meaningfully accessed for over 7 days (sliding TTL). */
    suspend fun evictExpiredDetails(): Int {
        val now = clock()
        val cutoff = now - policy.detailTtlMs
        val expiredCandidates = cacheDao.getEvictionCandidates(policy.candidatePool)
            .filter { it.lastAccessedAt > 0 && it.lastAccessedAt < cutoff }
            .map { it.entryId }

        if (expiredCandidates.isEmpty()) return 0

        expiredCandidates.chunked(EVICTION_CHUNK).forEach { chunk ->
            database.withTransaction {
                deleteLazyContent(chunk)
                cacheDao.markDetailEvicted(chunk)
            }
        }
        return expiredCandidates.size
    }

    /**
     * Revalidates one word's detail content if it is missing or older than the
     * TTL. Returns true when local data changed. Offline or failing servers
     * simply leave the local copy as it is.
     */
    suspend fun refreshDetail(entryId: Long): Boolean {
        val metadata = cacheDao.get(entryId)
        val now = clock()
        if (metadata != null && metadata.detailValidatedAt > 0 && now - metadata.detailValidatedAt < policy.detailTtlMs) {
            return false
        }
        return fetchAndStore(listOf(entryId))
    }

    /**
     * Downloads details for the highest-scored words that lack them, within a
     * bounded number of requests. Never walks the whole dictionary.
     * Returns the number of words stored.
     */
    suspend fun prefetch(): Int {
        refreshPriorities()
        val now = clock()
        val candidates = cacheDao.getRefreshCandidates(now - policy.detailTtlMs, policy.candidatePool)
            .sortedWith(
                compareBy<CacheMetadataEntity> { it.priority }
                    .thenByDescending { policy.score(it.accessCount, it.lastAccessedAt, now) }
            )
            .map { it.entryId }
        var stored = 0
        candidates.chunked(policy.prefetchBatchSize).take(policy.prefetchMaxRequestsPerRun).forEach { batch ->
            if (fetchAndStore(batch)) stored += batch.size
        }
        return stored
    }

    /**
     * Keeps detail content within [CachePolicy.maxDetailedEntries]. Lowest score
     * first; saved and topic words are skipped by the query itself.
     * Returns the number of words whose detail content was removed.
     */
    suspend fun evictIfNeeded(): Int {
        val excess = cacheDao.countDetailed() - policy.maxDetailedEntries
        if (excess <= 0) return 0
        val now = clock()
        val victims = cacheDao.getEvictionCandidates(maxOf(policy.candidatePool, excess))
            .sortedBy { policy.score(it.accessCount, it.lastAccessedAt, now) }
            .take(excess)
            .map { it.entryId }
        victims.chunked(EVICTION_CHUNK).forEach { chunk ->
            database.withTransaction {
                deleteLazyContent(chunk)
                cacheDao.markDetailEvicted(chunk)
            }
        }
        return victims.size
    }

    /** Brings protection classes in line with the user's current saved words and topics. */
    suspend fun refreshPriorities() {
        database.withTransaction {
            cacheDao.seedSavedWords()
            cacheDao.seedTopicWords()
            cacheDao.promoteSavedWords()
            cacheDao.promoteTopicWords()
            cacheDao.demoteUnprotected()
        }
    }

    /** Fetches [ids] in one request and stores the result atomically. */
    private suspend fun fetchAndStore(ids: List<Long>): Boolean {
        val api = api ?: return false
        val response = try {
            api.getDictionaryDetails(ids.joinToString(","))
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return false
        } catch (_: HttpException) {
            return false
        }
        store(response, ids)
        return true
    }

    private suspend fun store(response: DictionaryDetailsResponse, requested: List<Long>) {
        val found = response.entries.map { it.id }
        val now = clock()
        database.withTransaction {
            if (found.isNotEmpty()) {
                dictionaryDao.insertEntries(response.entries)
                deleteLazyContent(found)
                dictionaryDao.deleteMeaningsFor(found)
                dictionaryDao.insertMeanings(response.meanings)
                dictionaryDao.insertTranslations(response.translations)
                dictionaryDao.insertExamples(response.examples)
                dictionaryDao.insertSynonyms(response.synonyms)
                dictionaryDao.insertForms(response.forms)
                dictionaryDao.insertCollocations(response.collocations)
                found.forEach { cacheDao.insertIfAbsent(CacheMetadataEntity(entryId = it)) }
                cacheDao.markDetailLoaded(found, now, response.serverTime)
            }
            // Words the server no longer has: drop them (saved words are kept).
            val gone = response.missingIds.filter { it in requested }
            if (gone.isNotEmpty()) {
                dictionaryDao.deleteEntriesUnlessSaved(gone)
                cacheDao.delete(gone)
            }
        }
    }

    private suspend fun deleteLazyContent(ids: List<Long>) {
        dictionaryDao.deleteSynonymsFor(ids)
        dictionaryDao.deleteTranslationsFor(ids)
        dictionaryDao.deleteExamplesFor(ids)
        dictionaryDao.deleteFormsFor(ids)
        dictionaryDao.deleteCollocationsFor(ids)
    }

    private companion object {
        const val EVICTION_CHUNK = 400
        const val BUFFER_FLUSH_THRESHOLD = 20
    }
}
