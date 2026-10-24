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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.preferences.AppPreferences
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.VocabularyRepository
import com.example.data.tts.TtsManager
import com.example.ui.components.DictionarySearchBar
import com.example.ui.components.WordEntryCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySearchScreen(
    initialQuery: String = "",
    initialLang: String = "",
    dictionaryRepository: DictionaryRepository,
    vocabularyRepository: VocabularyRepository,
    ttsManager: TtsManager,
    preferences: AppPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToWordDetail: (wordId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedLanguage by remember {
        mutableStateOf<Language?>(if (initialLang.isNotBlank()) Language.fromCode(initialLang) else null)
    }
    var selectedPartOfSpeech by remember { mutableStateOf<String?>(null) }
    var isReverseSearch by remember { mutableStateOf(false) }

    var results by remember { mutableStateOf<List<DictionaryEntryEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val savedWords by vocabularyRepository.getAllSavedWords().collectAsState(initial = emptyList())
    val favoriteIds = remember(savedWords) {
        savedWords.filter { it.isFavorite }.map { it.entryId }.toSet()
    }

    // Debounced Search
    LaunchedEffect(searchQuery, selectedLanguage, selectedPartOfSpeech, isReverseSearch) {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        searchJob = coroutineScope.launch {
            delay(250) // Debounce typing
            val list = if (isReverseSearch) {
                dictionaryRepository.search(searchQuery, null, selectedPartOfSpeech)
            } else {
                dictionaryRepository.search(searchQuery, selectedLanguage, selectedPartOfSpeech)
            }
            results = list
            isLoading = false

            if (searchQuery.isNotBlank()) {
                dictionaryRepository.recordSearch(searchQuery, selectedLanguage?.code ?: "all")
            }
        }
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
                    onQueryChange = { searchQuery = it },
                    placeholder = if (isReverseSearch) "Tarjima bo‘yicha qidirish..." else "So‘zni kiriting (inglizcha, ruscha, o‘zbekcha)..."
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
                        onClick = { isReverseSearch = !isReverseSearch },
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
                            onClick = {
                                selectedLanguage = if (selectedLanguage == lang) null else lang
                            },
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
                            onClick = {
                                selectedPartOfSpeech = if (selectedPartOfSpeech == pos) null else pos
                            },
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
                if (isLoading) {
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
                            WordEntryCard(
                                entry = entry,
                                onClick = { onNavigateToWordDetail(entry.id) },
                                onAudioClick = { rate ->
                                    ttsManager.speak(entry.headword, entry.language, rateMultiplier = rate)
                                },
                                isFavorite = favoriteIds.contains(entry.id),
                                onFavoriteToggle = {
                                    coroutineScope.launch {
                                        vocabularyRepository.saveOrUpdateWord(
                                            entry = entry,
                                            isFavorite = !favoriteIds.contains(entry.id)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
