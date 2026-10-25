package com.example.data.repository

import com.example.data.dao.DictionaryDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.FullDictionaryDetail
import com.example.data.model.Language
import com.example.data.model.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import com.example.domain.repository.DictionaryRepository as DictionaryRepositoryContract

class DictionaryRepository(private val dictionaryDao: DictionaryDao) : DictionaryRepositoryContract {

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

        // 1. First check if exact/prefix match in entries
        val directResults = if (langCode == null) {
            dictionaryDao.searchEntries(normalized, normalized, limit)
        } else {
            dictionaryDao.searchEntriesByLanguage(normalized, normalized, langCode, limit)
        }

        if (directResults.isNotEmpty()) {
            return if (partOfSpeech != null) {
                directResults.filter { it.partOfSpeech.equals(partOfSpeech, ignoreCase = true) }
            } else {
                directResults
            }
        }

        // 2. Reverse search in translations if direct results are sparse
        val reverseResults = dictionaryDao.searchByTranslation(trimmed, limit)
        if (reverseResults.isNotEmpty()) {
            return reverseResults
        }

        // 3. Fallback to general filtered search
        return dictionaryDao.searchFiltered(normalized, langCode, partOfSpeech, limit)
    }

    override suspend fun getFullDetail(id: Long): FullDictionaryDetail? = dictionaryDao.getFullDictionaryDetail(id)

    override suspend fun getRandomWord(): DictionaryEntryEntity? = dictionaryDao.getRandomWord()

    fun getTopWords(): Flow<List<DictionaryEntryEntity>> = dictionaryDao.getTopWords(20)

    override fun getRecentSearches(): Flow<List<SearchHistoryEntity>> = dictionaryDao.getRecentSearches()

    override suspend fun recordSearch(query: String, language: String) {
        if (query.isNotBlank()) {
            dictionaryDao.insertSearchQuery(
                SearchHistoryEntity(
                    query = query.trim(),
                    language = language,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSearchItem(id: Long) = dictionaryDao.deleteSearchItem(id)

    override suspend fun clearSearchHistory() = dictionaryDao.clearSearchHistory()
}
