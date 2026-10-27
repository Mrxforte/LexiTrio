package com.example.data.repository

import android.util.LruCache
import com.example.data.cloud.CloudApi
import com.example.data.dao.DictionaryDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.FullDictionaryDetail
import com.example.data.model.Language
import com.example.data.model.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random
import com.example.domain.repository.DictionaryRepository as DictionaryRepositoryContract

class DictionaryRepository internal constructor(
    private val dictionaryDao: DictionaryDao,
    private val cloudApi: CloudApi? = null
) : DictionaryRepositoryContract {

    constructor(dictionaryDao: DictionaryDao) : this(dictionaryDao, null)

    // In-memory LRU cache (bounded to 50 entries) for instantaneous autocomplete responses
    private val searchCache = LruCache<String, List<DictionaryEntryEntity>>(50)

    override fun getEntryCountFlow(): Flow<Int> = dictionaryDao.getEntryCountFlow()

    suspend fun getEntryCount(): Int = dictionaryDao.getEntryCount()

    override suspend fun search(
        query: String,
        sourceLanguage: Language?,
        partOfSpeech: String?,
        limit: Int
    ): List<DictionaryEntryEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val normalized = Language.normalizeText(trimmed)
        val langCode = sourceLanguage?.code
        val cacheKey = "$normalized|$langCode|$partOfSpeech|$limit"

        searchCache.get(cacheKey)?.let { cached ->
            return cached
        }

        // Generate transliterated alternative query for Uzbek (Latin <-> Cyrillic)
        val altQueryPattern = if (langCode == "uz" || langCode == "uz-cyrl" || langCode == null) {
            val isCyrillic = normalized.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ў' || it == 'ғ' || it == 'қ' || it == 'ҳ' }
            val transliterated = Language.transliterateUzbek(normalized, toCyrillic = !isCyrillic)
            val normalizedAlt = Language.normalizeText(transliterated)
            if (normalizedAlt != normalized) normalizedAlt else ""
        } else {
            ""
        }

        val localResults = dictionaryDao.searchComprehensive(
            queryPattern = normalized,
            exactQuery = normalized,
            altQueryPattern = altQueryPattern,
            lang = langCode,
            pos = partOfSpeech,
            limit = limit
        )

        val combinedResults = if (localResults.isNotEmpty()) {
            localResults
        } else {
            // Fallback to legacy translation search
            val reverseResults = dictionaryDao.searchByTranslation(trimmed, langCode, limit)
            if (reverseResults.isNotEmpty()) {
                if (partOfSpeech != null) {
                    reverseResults.filter { it.partOfSpeech.equals(partOfSpeech, ignoreCase = true) }
                } else {
                    reverseResults
                }
            } else {
                dictionaryDao.searchFiltered(normalized, langCode, partOfSpeech, limit)
            }
        }

        // Try remote search if local results are empty or sparse and cloudApi is supplied
        val finalResults = if (combinedResults.isEmpty() && cloudApi != null) {
            try {
                val remoteResponse = cloudApi.searchDictionary(
                    query = trimmed,
                    sourceLanguage = langCode,
                    partOfSpeech = partOfSpeech,
                    pageSize = limit
                )
                remoteResponse.results.map { it.toEntity() }
            } catch (_: Exception) {
                combinedResults
            }
        } else {
            combinedResults
        }

        searchCache.put(cacheKey, finalResults)
        return finalResults
    }

    override suspend fun getFullDetail(id: Long): FullDictionaryDetail? = dictionaryDao.getFullDictionaryDetail(id)

    /**
     * Picks a random word without sorting the whole table: a random id is used
     * as a cursor and the first row at or after it is returned.
     */
    override suspend fun getRandomWord(): DictionaryEntryEntity? {
        val minId = dictionaryDao.getMinEntryId() ?: return null
        val maxId = dictionaryDao.getMaxEntryId() ?: return null
        if (maxId <= minId) return dictionaryDao.getFirstEntryFrom(minId)
        val candidate = Random.nextLong(minId, maxId + 1)
        return dictionaryDao.getFirstEntryFrom(candidate) ?: dictionaryDao.getFirstEntryFrom(minId)
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
