package com.example.ui.screens.dictionary

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Language
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import com.example.ui.components.DictionarySearchBar
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
    var searchQuery by rememberSaveable { mutableStateOf(initialQuery) }

    LaunchedEffect(initialQuery, initialLang) {
        if (initialQuery.isNotBlank() || initialLang.isNotBlank()) {
            searchQuery = initialQuery
            viewModel.applyInitialSearch(
                query = initialQuery,
                language = if (initialLang.isNotBlank()) Language.fromCode(initialLang) else null
            )
        }
    }
    LaunchedEffect(searchQuery) {
        viewModel.setSearchQuery(searchQuery)
    }

    val results = searchState.results
    val isLoading = searchState.isLoading
    val selectedLanguage = filters.language
    val selectedPartOfSpeech = filters.partOfSpeech
    val isReverseSearch = filters.reverseTranslation

    val onQueryChange: (String) -> Unit = { searchQuery = it }
    val onLanguageSelect: (Language) -> Unit = { viewModel.setSearchLanguage(it) }
    val onPartOfSpeechSelect: (String) -> Unit = { viewModel.setSearchPartOfSpeech(it) }
    val onReverseToggle: () -> Unit = { viewModel.setReverseTranslation(!isReverseSearch) }

    val savedWords by viewModel.savedWords.collectAsState()
    val favoriteIds = remember(savedWords) {
        savedWords.filter { it.isFavorite }.map { it.entryId }.toSet()
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    val chipBorder = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = false,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = MaterialTheme.colorScheme.primary
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lug‘at qidiruvi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input & Filters Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DictionarySearchBar(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    placeholder = if (isReverseSearch) "Tarjima bo‘yicha qidirish..." else "So‘zni kiriting (inglizcha, ruscha, o‘zbekcha)...",
                    onSearch = { viewModel.recordCurrentSearch() }
                )

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reverse search toggle
                    FilterChip(
                        selected = isReverseSearch,
                        onClick = onReverseToggle,
                        label = { Text("Tarjima bo‘yicha", style = MaterialTheme.typography.labelMedium) },
                        colors = chipColors,
                        border = chipBorder,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("filter_reverse_search")
                    )

                    // Language Filter
                    Language.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { onLanguageSelect(lang) },
                            label = { Text("${lang.flagEmoji} ${lang.displayNameUzLatn}", style = MaterialTheme.typography.labelMedium) },
                            colors = chipColors,
                            border = chipBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Part of Speech Filter
                    listOf("noun", "verb", "adjective", "adverb", "idiom").forEach { pos ->
                        FilterChip(
                            selected = selectedPartOfSpeech == pos,
                            onClick = { onPartOfSpeechSelect(pos) },
                            label = { Text(pos, style = MaterialTheme.typography.labelMedium) },
                            colors = chipColors,
                            border = chipBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

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
                            text = "Imloni tekshirib ko‘ring yoki boshqa tilda yozib ko‘ring. Shuningdek, «Tarjima bo‘yicha» filtrini yoqishingiz mumkin.",
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "${results.size} ta natija topildi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(results, key = { it.id }) { entry ->
                            val playbackState by ttsManager.playbackState.collectAsState()
                            val isEntryPlaying = playbackState.isPlaying && playbackState.currentText.equals(entry.headword, ignoreCase = true)
                            val activeText = if (playbackState.isPlaying) playbackState.currentText else null

                            WordEntryCard(
                                entry = entry,
                                searchQuery = searchQuery,
                                onClick = { onNavigateToWordDetail(entry.id) },
                                onAudioClick = { rate ->
                                    if (isEntryPlaying) {
                                        ttsManager.stop()
                                    } else {
                                        ttsManager.speak(entry.headword, entry.language, rateMultiplier = rate)
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
                    }
                }
            }
        }
    }
}
