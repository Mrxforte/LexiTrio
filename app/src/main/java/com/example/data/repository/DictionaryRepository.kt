package com.example.data.repository

import android.util.LruCache
import com.example.data.cache.DetailCacheManager
import com.example.data.cloud.CloudApi
import com.example.data.dao.DictionaryDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.FullDictionaryDetail
import com.example.data.model.Language
import com.example.data.model.textIn
import com.example.data.model.SearchHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import com.example.domain.repository.DictionaryRepository as DictionaryRepositoryContract

class DictionaryRepository internal constructor(
    private val dictionaryDao: DictionaryDao,
    private val cloudApi: CloudApi? = null,
    // While the offline catalog is still downloading, the server answers too.
    private val isCatalogComplete: () -> Boolean = { true },
    private val detailCache: DetailCacheManager? = null
) : DictionaryRepositoryContract {

    constructor(dictionaryDao: DictionaryDao) : this(dictionaryDao, null)

    // Bounded LRU of recent result lists; keys embed SearchCacheVersion so
    // background writes invalidate everything at once.
    private val searchCache = LruCache<String, List<DictionaryEntryEntity>>(200)

    override fun getEntryCountFlow(): Flow<Int> = dictionaryDao.getEntryCountFlow()

    suspend fun getEntryCount(): Int = dictionaryDao.getEntryCount()

    override suspend fun searchDirected(
        query: String, source: Language, target: Language, limit: Int, offset: Int
    ): List<DictionaryEntryEntity> {
        val normalized = Language.normalizeText(query)
        val sourceCode = source.code.substringBefore('-')
        val targetCode = target.code.substringBefore('-')
        require(sourceCode != targetCode)
        val tokens = normalized.split(NON_WORD).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        suspend fun find(text: String): List<DictionaryEntryEntity> {
            val words = text.split(NON_WORD).filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            fun match(column: String) = words.mapIndexed { index, word ->
                "$column:$word${if (index == words.lastIndex) "*" else ""}"
            }.joinToString(" ")
            val meaningColumn = when (sourceCode) {
                "en" -> "primaryMeaningEn"
                "ru" -> "primaryMeaningRu"
                else -> "primaryMeaningUz"
            }
            return dictionaryDao.searchDirected(
                match("headwordNormalized"), match("alternativeSpellings"), match(meaningColumn), sourceCode, targetCode,
                text, text + PREFIX_UPPER_SUFFIX, limit, offset
            )
        }
        val results = coroutineScope {
            val remote = if (offset == 0 && shouldAskServer(normalized)) {
                async { searchRemote(query.trim(), sourceCode, null, limit) }
            } else null
            var local = find(normalized)
            if (local.isEmpty() && sourceCode == "uz") {
                val cyrillic = normalized.any { it in 'а'..'я' || it in "ўғқҳ" }
                val alternative = Language.normalizeText(Language.transliterateUzbek(normalized, !cyrillic))
                if (alternative != normalized) local = find(alternative)
            }
            val remoteHits = remote?.await().orEmpty().filter { entry ->
                val sourceWords = Language.normalizeText(entry.textIn(source)).split(NON_WORD)
                entry.textIn(target).isNotBlank() && tokens.all { token ->
                    sourceWords.any { it.startsWith(token) }
                }
            }
            mergeRanked(remoteHits, local, limit)
        }
        return results
    }

    override suspend fun search(
        query: String,
        sourceLanguage: Language?,
        partOfSpeech: String?,
        limit: Int,
        offset: Int
    ): List<DictionaryEntryEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val normalized = Language.normalizeText(trimmed)
        // The two Uzbek scripts share one headword column.
        val langCode = sourceLanguage?.code?.let { if (it == "uz-cyrl") "uz" else it }
        val cacheKey = "${SearchCacheVersion.current}|$normalized|$langCode|$partOfSpeech|$limit|$offset"

        searchCache.get(cacheKey)?.let { return it }

        // Uzbek can be typed in Latin or Cyrillic; fall back to the other script.
        val altQuery = if (langCode == "uz" || langCode == null) {
            val isCyrillic = normalized.any { it in 'а'..'я' || it == 'ў' || it == 'ғ' || it == 'қ' || it == 'ҳ' }
            val alt = Language.normalizeText(Language.transliterateUzbek(normalized, toCyrillic = !isCyrillic))
            if (alt != normalized) alt else null
        } else {
            null
        }

        var serverAnswered = true
        val results = coroutineScope {
            // The server only helps with the first page; later pages are local.
            val remote = if (offset == 0 && shouldAskServer(normalized)) {
                async { searchRemote(trimmed, langCode, partOfSpeech, limit) }
            } else {
                null
            }
            val local = searchLocal(normalized, altQuery, langCode, partOfSpeech, limit, offset)
            val remoteHits = remote?.await().orEmpty()
            serverAnswered = remote == null || remoteHits.isNotEmpty()
            mergeRanked(remoteHits, local, limit)
        }

        // A failed or timed-out server call must not pin local-only results.
        if (serverAnswered) searchCache.put(cacheKey, results)
        return results
    }

    private fun shouldAskServer(normalized: String): Boolean =
        cloudApi != null && normalized.length >= MIN_REMOTE_QUERY_LENGTH && !isCatalogComplete()

    private suspend fun searchLocal(
        normalized: String,
        altQuery: String?,
        lang: String?,
        pos: String?,
        limit: Int,
        offset: Int
    ): List<DictionaryEntryEntity> {
        val primary = searchVariant(normalized, lang, pos, limit, offset)
        if (primary.isNotEmpty() || altQuery == null) return primary
        return searchVariant(altQuery, lang, pos, limit, offset)
    }

    /**
     * One page of a single spelling: headwords that start with it, alphabetical
     * (index range scan, no table scan), then word-level matches in meanings.
     * Both lists are served as one sequence so `offset` pages through them.
     */
    private suspend fun searchVariant(
        query: String,
        lang: String?,
        pos: String?,
        limit: Int,
        offset: Int
    ): List<DictionaryEntryEntity> {
        val upper = query + PREFIX_UPPER_SUFFIX
        val prefix = dictionaryDao.searchHeadwordPrefix(query, upper, lang, pos, limit, offset)
        if (prefix.size >= limit) return prefix

        // A short page means the prefix list ended here; its size is then known
        // without a count, except when the page lies entirely past its end.
        val prefixTotal = if (prefix.isNotEmpty() || offset == 0) {
            offset + prefix.size
        } else {
            dictionaryDao.countHeadwordPrefix(query, upper, lang, pos)
        }
        val match = buildFtsMatch(query) ?: return prefix
        val textHits = dictionaryDao.searchFullText(
            match, query, upper, lang, pos,
            limit = limit - prefix.size,
            offset = maxOf(0, offset - prefixTotal)
        )
        return prefix + textHits
    }

    private suspend fun searchRemote(
        query: String,
        lang: String?,
        pos: String?,
        limit: Int
    ): List<DictionaryEntryEntity> {
        val api = cloudApi ?: return emptyList()
        return try {
            val response = withTimeoutOrNull(REMOTE_TIMEOUT_MS) {
                api.searchDictionary(
                    query = query,
                    sourceLanguage = lang,
                    partOfSpeech = pos,
                    pageSize = minOf(limit, MAX_REMOTE_PAGE_SIZE)
                )
            } ?: return emptyList()
            val entries = response.results.map { it.toEntity() }.filter { it.id != 0L }
            // Keep what the server found so the detail screen and the next
            // (offline) search can use it.
            if (entries.isNotEmpty()) dictionaryDao.insertEntriesIfAbsent(entries)
            entries
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeRanked(
        remote: List<DictionaryEntryEntity>,
        local: List<DictionaryEntryEntity>,
        limit: Int
    ): List<DictionaryEntryEntity> {
        if (remote.isEmpty()) return local
        // Local order stays intact (alphabetical prefix hits, then meaning hits)
        // so paging with `offset` never skips rows; the server only fills the
        // page with words that have not been downloaded yet.
        val merged = LinkedHashMap<Long, DictionaryEntryEntity>()
        local.forEach { merged.putIfAbsent(it.id, it) }
        remote.forEach { merged.putIfAbsent(it.id, it) }
        return merged.values.take(limit)
    }

    internal companion object {
        const val REMOTE_TIMEOUT_MS = 1500L
        const val MIN_REMOTE_QUERY_LENGTH = 2
        const val MAX_REMOTE_PAGE_SIZE = 100
        const val PREFIX_UPPER_SUFFIX = "\uFFFF"

        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        /**
         * FTS4 MATCH expression from free text: only letters and digits survive
         * (so no operator can be injected) and the last word is a prefix query.
         */
        fun buildFtsMatch(normalized: String): String? {
            val tokens = normalized.lowercase().split(NON_WORD).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return (tokens.dropLast(1) + (tokens.last() + "*")).joinToString(" ")
        }
    }

    override suspend fun getFullDetail(id: Long): FullDictionaryDetail? {
        // Local first; the access is recorded for the adaptive cache score.
        val detail = dictionaryDao.getFullDictionaryDetail(id)
        if (detail != null) detailCache?.recordAccess(id)
        return detail
    }

    override suspend fun refreshDetail(id: Long): FullDictionaryDetail? =
        if (detailCache?.refreshDetail(id) == true) dictionaryDao.getFullDictionaryDetail(id) else null

    override suspend fun getWordOfDay(source: Language, target: Language, dayKey: Int): DictionaryEntryEntity? {
        val sourceCode = source.code.substringBefore('-')
        val targetCode = target.code.substringBefore('-')
        require(sourceCode != targetCode)
        val minId = dictionaryDao.getMinEntryId() ?: return null
        val maxId = dictionaryDao.getMaxEntryId() ?: return null
        val candidate = if (maxId <= minId || maxId == Long.MAX_VALUE) minId
            else Random(dayKey).nextLong(minId, maxId + 1)
        return dictionaryDao.getFirstEntryForDirection(candidate, sourceCode, targetCode)
            ?: dictionaryDao.getFirstEntryForDirection(minId, sourceCode, targetCode)
    }

    fun getTopWords(): Flow<List<DictionaryEntryEntity>> = dictionaryDao.getTopWords(20)

    override fun getRecentSearches(): Flow<List<SearchHistoryEntity>> = dictionaryDao.getRecentSearches()

    override suspend fun recordSearch(query: String, language: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        // Typing "app" -> "appl" -> "apple" must not fill the history list.
        if (dictionaryDao.getLastSearchQuery().equals(trimmed, ignoreCase = true)) return
        dictionaryDao.insertSearchQuery(
            SearchHistoryEntity(
                query = trimmed,
                language = language,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSearchItem(id: Long) = dictionaryDao.deleteSearchItem(id)

    override suspend fun clearSearchHistory() = dictionaryDao.clearSearchHistory()
}
