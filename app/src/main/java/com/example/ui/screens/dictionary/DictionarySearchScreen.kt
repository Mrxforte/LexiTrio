package com.example.ui.screens.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Language
import com.example.data.model.DictionaryDirection
import com.example.data.model.textIn
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import com.example.ui.components.DictionarySearchBar
import com.example.ui.components.LanguageDirectionSelector
import com.example.ui.components.WordEntryCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySearchScreen(
    initialQuery: String = "",
    initialLang: String = "",
    viewModel: AppViewModel,
    ttsManager: TtsManager,
    preferences: AppPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToWordDetail: (wordId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val searchState by viewModel.searchState.collectAsState()
    val filters = searchState.filters

    // The text field owns the caret; the ViewModel owns the debounced search.
    var searchQuery by rememberSaveable { mutableStateOf(if (initialQuery == "{query}") "" else initialQuery) }

    val direction = DictionaryDirection.from(preferences.searchSourceLanguage, preferences.searchTargetLanguage)
    LaunchedEffect(direction) {
        viewModel.setSearchDirection(direction.source, direction.target)
    }
    LaunchedEffect(searchQuery) {
        viewModel.setSearchQuery(searchQuery)
    }

    val results = searchState.results
    val isLoading = searchState.isLoading && results.isEmpty()
    val onQueryChange: (String) -> Unit = { searchQuery = it }

    val savedWords by viewModel.savedWords.collectAsState()
    val favoriteIds = remember(savedWords) {
        savedWords.filter { it.isFavorite }.map { it.entryId }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Compact Header: Back Button + Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DictionarySearchBar(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    placeholder = "${direction.source.displayNameUzLatn} so‘zni kiriting...",
                    onSearch = { viewModel.recordCurrentSearch() },
                    modifier = Modifier.weight(1f)
                )
            }

            LanguageDirectionSelector(
                selectedSource = direction.source,
                selectedTarget = direction.target,
                onSourceSelect = { it?.let { language -> direction.withSource(language).let { pair -> viewModel.setDictionaryLanguages(pair.source, pair.target) } } },
                onTargetSelect = { it?.let { language -> direction.withTarget(language).let { pair -> viewModel.setDictionaryLanguages(pair.source, pair.target) } } },
                onSwap = { viewModel.setDictionaryLanguages(direction.target, direction.source) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Results List / Empty State / Loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (searchState.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = searchState.errorMessage ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.retrySearch() },
                            modifier = Modifier.testTag("search_retry_button")
                        ) {
                            Text("Qayta urinish")
                        }
                    }
                } else if (isLoading || (searchQuery.isNotBlank() && !searchState.hasSearched)) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("search_loading_indicator")
                    )
                } else if (results.isEmpty() && searchQuery.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "«$searchQuery» bo‘yicha natija topilmadi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Imloni tekshiring yoki qidiruv tilini o‘zgartiring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (results.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Qidirish uchun so‘z kiriting",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Ingliz, rus yoki o‘zbek tilidagi 100 000+ so‘zlar bazasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    // A new query starts at the top of the list again.
                    LaunchedEffect(filters.query, filters.language, filters.targetLanguage) {
                        listState.scrollToItem(0)
                    }
                    // Load the next page while a few cards are still below the screen.
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
                        }.collect { nearEnd -> if (nearEnd) viewModel.loadMoreSearchResults() }
                    }
                    val playbackState by ttsManager.playbackState.collectAsState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "${results.size}${if (searchState.canLoadMore) "+" else ""} ta natija topildi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(results, key = { it.id }) { entry ->
                            val sourceText = entry.textIn(direction.source)
                            val isEntryPlaying = playbackState.isPlaying && playbackState.currentText.equals(sourceText, ignoreCase = true)
                            val activeText = if (playbackState.isPlaying) playbackState.currentText else null

                            WordEntryCard(
                                entry = entry,
                                searchQuery = searchQuery,
                                sourceLanguage = direction.source,
                                targetLanguage = direction.target,
                                onClick = { onNavigateToWordDetail(entry.id) },
                                onAudioClick = { rate ->
                                    if (isEntryPlaying) {
                                        ttsManager.stop()
                                    } else {
                                        ttsManager.speak(sourceText, direction.source.code, rateMultiplier = rate)
                                    }
                                },
                                isPlaying = isEntryPlaying,
                                isFavorite = favoriteIds.contains(entry.id),
                                onFavoriteToggle = {
                                    coroutineScope.launch {
                                        viewModel.saveWord(
                                            entry = entry,
                                            isFavorite = !favoriteIds.contains(entry.id)
                                        )
                                    }
                                },
                                onSpeakText = { text, lang ->
                                    if (playbackState.isPlaying && playbackState.currentText == text) {
                                        ttsManager.stop()
                                    } else {
                                        ttsManager.speak(text, lang, 1.0f)
                                    }
                                },
                                activeSpeakingText = activeText
                            )
                        }

                        if (searchState.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
