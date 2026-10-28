package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.DictionaryDirection
import com.example.data.model.UserProgressEntity
import com.example.data.model.textIn
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import com.example.ui.components.DictionarySearchBar
import com.example.ui.components.LanguageDirectionSelector
import com.example.ui.components.LearningProgressHeader
import com.example.ui.components.TopicCard
import com.example.ui.components.WordOfTheDayCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    ttsManager: TtsManager,
    preferences: AppPreferences,
    onNavigateToSearch: (query: String, lang: String) -> Unit,
    onNavigateToWordDetail: (wordId: Long) -> Unit,
    onNavigateToTopics: () -> Unit,
    onNavigateToTopicDetail: (topicId: Long) -> Unit,
    onNavigateToConversations: () -> Unit,
    onNavigateToGrammar: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToFlashcards: (mode: String) -> Unit,
    onNavigateToCurriculum: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    val direction = DictionaryDirection.from(preferences.searchSourceLanguage, preferences.searchTargetLanguage)

    val recentSearches by viewModel.recentSearches.collectAsState()

    var wordOfTheDay by remember(direction) { mutableStateOf<DictionaryEntryEntity?>(null) }
    var wordLoaded by remember(direction) { mutableStateOf(false) }

    LaunchedEffect(direction) {
        while (true) {
            wordLoaded = false
            val today = Calendar.getInstance()
            val dayKey = today.get(Calendar.YEAR) * 1000 + today.get(Calendar.DAY_OF_YEAR)
            wordOfTheDay = viewModel.getWordOfDay(direction.source, direction.target, dayKey)
            wordLoaded = true
            val tomorrow = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            delay((tomorrow.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1L))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Search Bar & Language Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DictionarySearchBar(
                        query = searchQuery,
                        onQueryChange = { q ->
                            searchQuery = q
                            if (q.isNotBlank()) {
                                onNavigateToSearch(q, direction.source.code)
                            }
                        },
                        placeholder = when (preferences.appLanguage) {
                            Language.ENGLISH -> "Search 100,000+ words or translations..."
                            Language.RUSSIAN -> "Поиск среди 100 000+ слов или переводов..."
                            else -> "100 000+ so‘z yoki tarjimalarni qidiring..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageDirectionSelector(
                        selectedSource = direction.source,
                        selectedTarget = direction.target,
                        onSourceSelect = { it?.let { language -> direction.withSource(language).let { pair -> viewModel.setDictionaryLanguages(pair.source, pair.target) } } },
                        onTargetSelect = { it?.let { language -> direction.withTarget(language).let { pair -> viewModel.setDictionaryLanguages(pair.source, pair.target) } } },
                        onSwap = {
                            viewModel.setDictionaryLanguages(direction.target, direction.source)
                        }
                    )
                }
            }

            // 3. Word of the Day
            item {
                wordOfTheDay?.let { word ->
                    WordOfTheDayCard(
                        entry = word,
                        direction = direction,
                        onClick = { onNavigateToWordDetail(word.id) },
                        onAudioClick = {
                            ttsManager.speak(word.textIn(direction.source), direction.source.code)
                        },
                        onSaveClick = {
                            coroutineScope.launch {
                                viewModel.saveWord(word, isFavorite = true)
                                snackbarHostState.showSnackbar("«${word.textIn(direction.source)}» saqlanganlarga qo‘shildi")
                            }
                        }
                    )
                }
                if (wordOfTheDay == null && wordLoaded) {
                    Text(
                        text = when (preferences.appLanguage) {
                            Language.ENGLISH -> "No word of the day is available for this language pair."
                            Language.RUSSIAN -> "Для этой языковой пары слово дня недоступно."
                            else -> "Bu til juftligi uchun kun so‘zi mavjud emas."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 4. Quick Action Modules Grid
            item {
                Column {
                    Text(
                        text = "O‘rganish va amaliyot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard(
                            title = "Lug‘at",
                            subtitle = "Keng qidiruv",
                            icon = Icons.Default.Book,
                            iconContainerColor = Color(0xFFE3F2FD),
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToSearch("", direction.source.code) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Flashcard",
                            subtitle = "SM-2 takrorlash",
                            icon = Icons.Default.Style,
                            iconContainerColor = Color(0xFFFEF3C7),
                            iconColor = Color(0xFFD97706),
                            onClick = { onNavigateToFlashcards("due") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard(
                            title = "Suhbatlar",
                            subtitle = "15+ real dialog",
                            icon = Icons.Default.Chat,
                            iconContainerColor = Color(0xFFD1FAE5),
                            iconColor = Color(0xFF059669),
                            onClick = onNavigateToConversations,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Grammatika",
                            subtitle = "Uch til qoidalari",
                            icon = Icons.Default.Psychology,
                            iconContainerColor = Color(0xFFF3E8FF),
                            iconColor = Color(0xFF7E22CE),
                            onClick = onNavigateToGrammar,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard(
                            title = "Kurrikulum",
                            subtitle = "EN A1-C2 & RU A1-B2",
                            icon = Icons.Default.School,
                            iconContainerColor = Color(0xFFE0F2FE),
                            iconColor = Color(0xFF0284C7),
                            onClick = onNavigateToCurriculum,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Mavzular",
                            subtitle = "30+ to‘plam",
                            icon = Icons.Default.Book,
                            iconContainerColor = Color(0xFFCFFAFE),
                            iconColor = Color(0xFF0891B2),
                            onClick = onNavigateToTopics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Recent Searches
            if (recentSearches.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Oxirgi qidiruvlar",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.clearSearchHistory()
                                }
                            ) {
                                Text("Tozalash", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(recentSearches.take(10)) { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.clickable {
                                        onNavigateToSearch(item.query, direction.source.code)
                                    }
                                ) {
                                    Text(
                                        text = item.query,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(78.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconContainerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
