package com.example.domain.model

import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language

/**
 * Everything that decides which dictionary results are shown. Kept immutable so
 * the search flow can compare and debounce consecutive states cheaply.
 */
data class DictionarySearchFilters(
    val query: String = "",
    val language: Language? = null,
    val targetLanguage: Language? = null,
    val partOfSpeech: String? = null,
    val reverseTranslation: Boolean = false
) {
    val isActive: Boolean get() = query.isNotBlank()

    /** Language recorded in the search history for this filter combination. */
    val historyLanguage: String get() = language?.code ?: "all"
}

/** Immutable state rendered by the dictionary search screen. */
data class DictionarySearchUiState(
    val filters: DictionarySearchFilters = DictionarySearchFilters(),
    val isLoading: Boolean = false,
    val results: List<DictionaryEntryEntity> = emptyList(),
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    /** More pages are available after [results]; the list loads them on scroll. */
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false
) {
    val isEmptyResult: Boolean
        get() = hasSearched && !isLoading && errorMessage == null && results.isEmpty()
}
