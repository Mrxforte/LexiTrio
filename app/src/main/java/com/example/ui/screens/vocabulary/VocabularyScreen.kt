package com.example.ui.screens.vocabulary

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LearningStatus
import com.example.data.model.SavedWordEntity
import com.example.data.repository.VocabularyRepository
import com.example.data.tts.TtsManager
import com.example.ui.components.PartOfSpeechChip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    vocabularyRepository: VocabularyRepository,
    ttsManager: TtsManager,
    onNavigateToFlashcards: (mode: String) -> Unit,
    onNavigateToWordDetail: (wordId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Barchasi", "Takrorlash", "Qiyin", "Yod olingan", "To‘plamlar")

    val allWords by vocabularyRepository.getAllSavedWords().collectAsState(initial = emptyList())
    val dueWords by vocabularyRepository.getDueWords().collectAsState(initial = emptyList())
    val difficultWords by vocabularyRepository.getWordsByStatus(LearningStatus.DIFFICULT).collectAsState(initial = emptyList())
    val masteredWords by vocabularyRepository.getWordsByStatus(LearningStatus.MASTERED).collectAsState(initial = emptyList())
    val collections by vocabularyRepository.getAllCollections().collectAsState(initial = emptyList())

    var showCreateCollectionModal by remember { mutableStateOf(false) }
    var newCollectionTitle by remember { mutableStateOf("") }
    var newCollectionDesc by remember { mutableStateOf("") }

    val currentWordsList = when (selectedTabIndex) {
        0 -> allWords
        1 -> dueWords
        2 -> difficultWords
        3 -> masteredWords
        else -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lug‘at boyligi (Vocabulary)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 4) {
                FloatingActionButton(
                    onClick = { showCreateCollectionModal = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_collection_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Yangi to‘plam")
                }
            } else if (currentWordsList.isNotEmpty()) {
                Button(
                    onClick = {
                        val mode = when (selectedTabIndex) {
                            1 -> "due"
                            2 -> "difficult"
                            else -> "all"
                        }
                        onNavigateToFlashcards(mode)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier = Modifier.testTag("start_flashcards_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Flashcard bilan o‘rganish (${currentWordsList.size})")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val count = when (index) {
                        0 -> allWords.size
                        1 -> dueWords.size
                        2 -> difficultWords.size
                        3 -> masteredWords.size
                        else -> collections.size
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = "$title ($count)",
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            if (selectedTabIndex == 4) {
                // Collections List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(collections, key = { it.id }) { coll ->
                        Card(
                            onClick = { onNavigateToFlashcards("collection_${coll.id}") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = coll.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (coll.description.isNotBlank()) {
                                        Text(
                                            text = coll.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (currentWordsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = when (selectedTabIndex) {
                                1 -> "Bugun takrorlash uchun so‘z yo‘q! 🎉"
                                2 -> "Hozircha qiyin deb belgilangan so‘zlar yo‘q."
                                3 -> "Yod olingan so‘zlar yo‘q. Mashqlarni boshlang!"
                                else -> "Lug‘atdan so‘zlarni saqlang."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SM-2 algoritmi so‘zlarni xotirangizda uzoq muddat saqlab qolish uchun eng maqbul vaqtda takrorlashni taklif qiladi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentWordsList, key = { it.id }) { word ->
                        SavedWordRow(
                            word = word,
                            onClick = {
                                if (word.entryId > 0) onNavigateToWordDetail(word.entryId)
                            },
                            onAudioClick = {
                                ttsManager.speak(word.headword, word.language)
                            },
                            onFavoriteToggle = {
                                coroutineScope.launch {
                                    vocabularyRepository.toggleFavorite(word)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateCollectionModal) {
        AlertDialog(
            onDismissRequest = { showCreateCollectionModal = false },
            title = { Text("Yangi lug‘at to‘plami yaratish") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCollectionTitle,
                        onValueChange = { newCollectionTitle = it },
                        label = { Text("To‘plam nomi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCollectionDesc,
                        onValueChange = { newCollectionDesc = it },
                        label = { Text("Tavsifi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCollectionTitle.isNotBlank()) {
                            coroutineScope.launch {
                                vocabularyRepository.createCollection(
                                    title = newCollectionTitle,
                                    description = newCollectionDesc
                                )
                                showCreateCollectionModal = false
                                newCollectionTitle = ""
                                newCollectionDesc = ""
                                snackbarHostState.showSnackbar("Yangi to‘plam yaratildi")
                            }
                        }
                    }
                ) {
                    Text("Yaratish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCollectionModal = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }
}

@Composable
fun SavedWordRow(
    word: SavedWordEntity,
    onClick: () -> Unit,
    onAudioClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.headword,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    PartOfSpeechChip(partOfSpeech = word.partOfSpeech)
                }

                if (word.phoneticIpa.isNotBlank()) {
                    Text(
                        text = word.phoneticIpa,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAudioClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Pronounce",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (word.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (word.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
