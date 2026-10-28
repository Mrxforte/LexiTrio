package com.example.ui.screens.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FullDictionaryDetail
import com.example.data.model.Language
import com.example.data.model.DictionaryDirection
import com.example.data.model.dictionaryCode
import com.example.data.model.textIn
import com.example.data.model.LearningStatus
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import com.example.ui.components.LanguageBadge
import com.example.ui.components.PartOfSpeechChip
import com.example.ui.components.PronunciationButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    wordId: Long,
    viewModel: AppViewModel,
    ttsManager: TtsManager,
    preferences: AppPreferences,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val direction = DictionaryDirection.from(preferences.searchSourceLanguage, preferences.searchTargetLanguage)
    val visibleLanguages = setOf(direction.source.dictionaryCode, direction.target.dictionaryCode)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var detail by remember { mutableStateOf<FullDictionaryDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var showSaveModal by remember { mutableStateOf(false) }
    var showReportModal by remember { mutableStateOf(false) }
    var userNotes by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("") }

    val savedWords by viewModel.savedWords.collectAsState()
    val currentSavedWord = remember(savedWords, wordId) {
        savedWords.find { it.entryId == wordId }
    }
    val isFavorite = currentSavedWord?.isFavorite == true

    LaunchedEffect(wordId) {
        isLoading = true
        detail = viewModel.getFullDetail(wordId)
        isLoading = false

        // Shown immediately from Room; fresher content replaces it when it arrives.
        viewModel.refreshFullDetail(wordId)?.let { detail = it }

        if (preferences.autoPlayAudio && detail != null) {
            val entry = detail!!.entry
            val spoken = entry.textIn(direction.source)
            if (spoken.isNotBlank()) ttsManager.speak(spoken, direction.source.code)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.entry?.textIn(direction.source)?.ifBlank { "So‘z tafsiloti" } ?: "So‘z tafsiloti",
                        fontWeight = FontWeight.SemiBold,
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
                actions = {
                    detail?.let { d ->
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.saveWord(
                                        entry = d.entry,
                                        isFavorite = !isFavorite
                                    )
                                    snackbarHostState.showSnackbar(
                                        if (!isFavorite) "Sevimlilarga qo‘shildi" else "Sevimlilardan o‘chirildi"
                                    )
                                }
                            },
                            modifier = Modifier.testTag("detail_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showSaveModal = true },
                            modifier = Modifier.testTag("detail_save_vocab_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Save to Vocabulary",
                                tint = if (currentSavedWord != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "So‘z topilmadi",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val d = detail!!
            val entry = d.entry

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header Section: Headword, Language, Audio Controls, CEFR
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = entry.textIn(direction.source).ifBlank { entry.headword },
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LanguageBadge(lang = direction.source.code, text = direction.source.dictionaryCode.uppercase())
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = entry.cefrLevel,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val targetText = entry.textIn(direction.target)
                            if (targetText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LanguageBadge(lang = direction.target.code, text = direction.target.dictionaryCode.uppercase())
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(targetText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    PronunciationButton(
                                        text = targetText, language = direction.target.code,
                                        onSpeak = { text, lang -> ttsManager.speak(text, lang) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PartOfSpeechChip(partOfSpeech = entry.partOfSpeech)
                                if (entry.language.substringBefore('-') == direction.source.dictionaryCode && entry.phoneticIpa.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = entry.phoneticIpa,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (entry.language.substringBefore('-') == direction.source.dictionaryCode && entry.alternativeSpellings.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Yozilishi: ${entry.alternativeSpellings}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Audio Playback Controls: Normal & Slow Speed + Stop
                            val playbackState by ttsManager.playbackState.collectAsState()
                            val spokenWord = entry.textIn(direction.source)
                            val isThisWordPlaying = playbackState.isPlaying && playbackState.currentText.equals(spokenWord, ignoreCase = true)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isThisWordPlaying && !playbackState.isSlowRate) {
                                            ttsManager.stop()
                                        } else {
                                            ttsManager.speak(spokenWord, direction.source.code, 1.0f)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isThisWordPlaying && !playbackState.isSlowRate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isThisWordPlaying && !playbackState.isSlowRate) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pronounce_normal_button")
                                ) {
                                    Icon(
                                        imageVector = if (isThisWordPlaying && !playbackState.isSlowRate) Icons.Default.Flag else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isThisWordPlaying && !playbackState.isSlowRate) "To‘xtatish" else "Talaffuz (1.0x)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isThisWordPlaying && playbackState.isSlowRate) {
                                            ttsManager.stop()
                                        } else {
                                            ttsManager.speak(spokenWord, direction.source.code, 0.65f)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pronounce_slow_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SlowMotionVideo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isThisWordPlaying && playbackState.isSlowRate) "To‘xtatish" else "Sekin (0.65x)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Definitions & Translations Section (Numbered Oxford-style senses)
                item {
                    Text(
                        text = "Ta’rif va tarjimalar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (d.meanings.isNotEmpty()) {
                    items(d.meanings, key = { it.id }) { meaning ->
                        SenseCard(
                            sense = meaning,
                            translations = d.translations.filter { (it.meaningId == meaning.id || it.meaningId == 0L) && it.targetLanguage.substringBefore('-') == direction.target.dictionaryCode },
                            examples = d.examples.filter { it.meaningId == meaning.id || it.meaningId == 0L },
                            visibleLanguages = visibleLanguages,
                            onAudioPlay = { text, lang -> ttsManager.speak(text, lang) }
                        )
                    }
                } else {
                    // Fallback to primary meanings
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if ("uz" in visibleLanguages && entry.primaryMeaningUz.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🇺🇿 ${entry.primaryMeaningUz}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PronunciationButton(
                                            text = entry.primaryMeaningUz,
                                            language = "uz",
                                            onSpeak = { text, lang -> ttsManager.speak(text, lang) },
                                            size = 32.dp
                                        )
                                    }
                                }
                                if ("ru" in visibleLanguages && entry.primaryMeaningRu.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🇷🇺 ${entry.primaryMeaningRu}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PronunciationButton(
                                            text = entry.primaryMeaningRu,
                                            language = "ru",
                                            onSpeak = { text, lang -> ttsManager.speak(text, lang) },
                                            size = 32.dp
                                        )
                                    }
                                }
                                if ("en" in visibleLanguages && entry.primaryMeaningEn.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🇬🇧 ${entry.primaryMeaningEn}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PronunciationButton(
                                            text = entry.primaryMeaningEn,
                                            language = "en",
                                            onSpeak = { text, lang -> ttsManager.speak(text, lang) },
                                            size = 32.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Word Forms & Inflections
                if (d.forms.isNotEmpty() && entry.language.substringBefore('-') in visibleLanguages) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Grammatik shakllar va tuslanish",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                d.forms.forEach { form ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = form.formType,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = form.formValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Collocations and Idioms
                if (d.collocations.isNotEmpty() && "en" in visibleLanguages) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Birikmalar va iboralar (Collocations)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                d.collocations.forEach { colloc ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = "• ${colloc.expression}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                colloc.translationUz.takeIf { "uz" in visibleLanguages && it.isNotBlank() }?.let { "🇺🇿 $it" },
                                                colloc.translationRu.takeIf { "ru" in visibleLanguages && it.isNotBlank() }?.let { "🇷🇺 $it" }
                                            ).joinToString("  |  "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Source Attribution & Report Entry
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manba: ${entry.source} • Tasdiqlangan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(onClick = { showReportModal = true }) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Xato haqida xabar berish",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Save to Vocabulary Learning
    if (showSaveModal && detail != null) {
        val entry = detail!!.entry
        AlertDialog(
            onDismissRequest = { showSaveModal = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Lug‘at boyligiga qo‘shish",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "«${entry.textIn(direction.source).ifBlank { entry.headword }}» so‘zi SM-2 takrorlash rejasiga kiritiladi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = userNotes,
                        onValueChange = { userNotes = it },
                        label = { Text("Shaxsiy eslatma yoki misol") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveWord(
                                entry = entry,
                                notes = userNotes
                            )
                            showSaveModal = false
                            snackbarHostState.showSnackbar("So‘z o‘rganish ro‘yxatiga qo‘shildi")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Saqlash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveModal = false }) {
                    Text("Bekor qilish", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Modal: Report Incorrect Entry
    if (showReportModal) {
        AlertDialog(
            onDismissRequest = { showReportModal = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Xato haqida xabar berish",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Lug‘at sifatini yaxshilashda yordamingiz uchun minnatdormiz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Qanday xatolik sezdingiz?") },
                        placeholder = { Text("Masalan: noto‘g‘ri tarjima, imlo xatosi...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportModal = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Xabaringiz qabul qilindi. Rahmat!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Yuborish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportModal = false }) {
                    Text("Yopish", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun SenseCard(
    sense: com.example.data.model.WordMeaningEntity,
    translations: List<com.example.data.model.TranslationEntity>,
    examples: List<com.example.data.model.ExampleSentenceEntity>,
    visibleLanguages: Set<String>,
    onAudioPlay: (text: String, lang: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = sense.senseNumber.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (sense.register != "general") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = sense.register,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // English definition
            if ("en" in visibleLanguages && sense.definitionEn.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sense.definitionEn,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PronunciationButton(
                        text = sense.definitionEn,
                        language = "en",
                        onSpeak = { text, lang -> onAudioPlay(text, lang) },
                        size = 32.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Direct Translations
            translations.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageBadge(lang = t.targetLanguage, text = t.targetLanguage.uppercase())
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t.translationText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (t.contextNote.isNotBlank()) {
                        Text(
                            text = " (${t.contextNote})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PronunciationButton(
                        text = t.translationText,
                        language = t.targetLanguage,
                        onSpeak = { text, lang -> onAudioPlay(text, lang) },
                        size = 28.dp,
                        iconSize = 14.dp
                    )
                }
            }

            // Usage Examples with Audio
            if (examples.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Misollar (Examples):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                examples.forEach { eg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if ("en" in visibleLanguages && eg.sentenceEn.isNotBlank()) Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "“${eg.sentenceEn}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                PronunciationButton(
                                    text = eg.sentenceEn,
                                    language = "en",
                                    onSpeak = { text, lang -> onAudioPlay(text, lang) },
                                    size = 32.dp
                                )
                            }
                            if ("uz" in visibleLanguages && eg.sentenceUz.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🇺🇿 ${eg.sentenceUz}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PronunciationButton(
                                        text = eg.sentenceUz,
                                        language = "uz",
                                        onSpeak = { text, lang -> onAudioPlay(text, lang) },
                                        size = 28.dp,
                                        iconSize = 14.dp
                                    )
                                }
                            }
                            if ("ru" in visibleLanguages && eg.sentenceRu.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🇷🇺 ${eg.sentenceRu}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PronunciationButton(
                                        text = eg.sentenceRu,
                                        language = "ru",
                                        onSpeak = { text, lang -> onAudioPlay(text, lang) },
                                        size = 28.dp,
                                        iconSize = 14.dp
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
