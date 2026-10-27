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
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.UserProgressEntity
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import com.example.ui.components.DictionarySearchBar
import com.example.ui.components.LanguageDirectionSelector
import com.example.ui.components.StreakProgressHeader
import com.example.ui.components.TopicCard
import com.example.ui.components.WordOfTheDayCard
import kotlinx.coroutines.launch

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
    onNavigateToVoiceSettings: () -> Unit,
    onNavigateToDataImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf<Language?>(preferences.searchSourceLanguage) }
    var targetLanguage by remember { mutableStateOf<Language?>(Language.UZBEK_LATIN) }

    val totalWordsCount by viewModel.entryCount.collectAsState()
    val userProgress by viewModel.progress.collectAsState()
    val dueCardsCount by viewModel.dueWordsCount.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val topics by viewModel.topics.collectAsState()

    var wordOfTheDay by remember { mutableStateOf<DictionaryEntryEntity?>(null) }

    LaunchedEffect(Unit) {
        val word = viewModel.getRandomWord()
        wordOfTheDay = word
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LexiTrio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "PRO",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToVoiceSettings,
                        modifier = Modifier.testTag("home_voice_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice & Pronunciation Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
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
            // 1. Search Bar & Ingestion Counter Banner
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DictionarySearchBar(
                        query = searchQuery,
                        onQueryChange = { q ->
                            searchQuery = q
                            if (q.isNotBlank()) {
                                onNavigateToSearch(q, sourceLanguage?.code ?: "")
                            }
                        },
                        placeholder = when (preferences.appLanguage) {
                            Language.ENGLISH -> "Search 100,000+ words or translations..."
                            Language.RUSSIAN -> "Поиск среди 100 000+ слов или переводов..."
                            else -> "100 000+ so‘z yoki tarjimalarni qidiring..."
                        }
                    )

                    LanguageDirectionSelector(
                        selectedSource = sourceLanguage,
                        selectedTarget = targetLanguage,
                        onSourceSelect = { sourceLanguage = it },
                        onTargetSelect = { targetLanguage = it },
                        onSwap = {
                            val temp = sourceLanguage
                            sourceLanguage = targetLanguage
                            targetLanguage = temp
                        }
                    )

                    // Data Ingestion & Real Corpus Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDataImport() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$totalWordsCount ta tasdiqlangan lug‘at birligi",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Import qilish →",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. Learning Streak & Spaced Repetition (SM-2) Header
            item {
                StreakProgressHeader(
                    progress = userProgress ?: UserProgressEntity(),
                    dueCardsCount = dueCardsCount,
                    onStartReview = { onNavigateToFlashcards("due") }
                )
            }

            // 3. Word of the Day
            item {
                wordOfTheDay?.let { word ->
                    WordOfTheDayCard(
                        entry = word,
                        onClick = { onNavigateToWordDetail(word.id) },
                        onAudioClick = {
                            ttsManager.speak(word.headword, word.language)
                        },
                        onSaveClick = {
                            coroutineScope.launch {
                                viewModel.saveWord(word, isFavorite = true)
                                snackbarHostState.showSnackbar("«${word.headword}» saqlanganlarga qo‘shildi")
                            }
                        }
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
                            onClick = { onNavigateToSearch("", sourceLanguage?.code ?: "") },
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
                                        onNavigateToSearch(item.query, item.language)
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

            // 6. Featured Topic Collections
            if (topics.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mavzuli lug‘at to‘plamlari",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onNavigateToTopics) {
                                Text("Barchasi (${topics.size}) →", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            topics.take(4).forEach { topic ->
                                TopicCard(
                                    topic = topic,
                                    lang = preferences.appLanguage,
                                    onClick = { onNavigateToTopicDetail(topic.id) }
                                )
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
