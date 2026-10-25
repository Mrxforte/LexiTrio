package com.example.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.QuizType
import com.example.presentation.AppViewModel
import com.example.data.tts.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizActiveScreen(
    count: Int,
    category: String,
    typeString: String,
    viewModel: AppViewModel,
    ttsManager: TtsManager,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (correct: Int, total: Int, timeSec: Int, xp: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var questions by remember { mutableStateOf<List<QuizQuestionEntity>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        val quizType = if (typeString.isNotBlank()) {
            try { QuizType.valueOf(typeString) } catch (_: Exception) { null }
        } else null

        val list = viewModel.getRandomQuiz(count, quizType, category)
        questions = list
        isLoading = false
        startTime = System.currentTimeMillis()
    }

    val currentQuestion = questions.getOrNull(currentIndex)
    val options = remember(currentQuestion) {
        currentQuestion?.getOptionsShuffled() ?: emptyList()
    }

    LaunchedEffect(currentQuestion) {
        if (currentQuestion != null && currentQuestion.type == QuizType.LISTENING_CHALLENGE) {
            delay(300)
            ttsManager.speak(currentQuestion.promptAudioText, currentQuestion.promptLanguage)
        }
    }

    fun handleNextQuestion() {
        if (currentIndex < questions.size - 1) {
            currentIndex++
            selectedOption = null
            isAnswerSubmitted = false
        } else {
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val xpEarned = correctCount * 15
            coroutineScope.launch {
                viewModel.saveQuizAttempt(
                    category = category,
                    total = questions.size,
                    correct = correctCount,
                    timeSec = elapsedSec,
                    xp = xpEarned
                )
                onNavigateToResult(correctCount, questions.size, elapsedSec, xpEarned)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Savol ${currentIndex + 1} / ${questions.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.titleMedium,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (questions.isEmpty() || currentQuestion == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Savollar topilmadi",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / questions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Question Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = currentQuestion.type.titleUz,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentQuestion.questionText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (currentQuestion.promptAudioText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            IconButton(
                                onClick = {
                                    ttsManager.speak(currentQuestion.promptAudioText, currentQuestion.promptLanguage)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("quiz_audio_prompt_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Pronounce question audio prompt",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Options List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        val isSelected = selectedOption == option
                        val isCorrect = option == currentQuestion.correctAnswer

                        val (containerColor, contentColor, borderColor) = when {
                            !isAnswerSubmitted -> {
                                if (isSelected) Triple(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    MaterialTheme.colorScheme.primary
                                )
                                else Triple(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.onSurface,
                                    MaterialTheme.colorScheme.outline
                                )
                            }
                            isCorrect -> Triple(
                                Color(0xFFD1FAE5),
                                Color(0xFF065F46),
                                Color(0xFF10B981)
                            )
                            isSelected -> Triple(
                                Color(0xFFFEE2E2),
                                Color(0xFF991B1B),
                                Color(0xFFEF4444)
                            )
                            else -> Triple(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.outline
                            )
                        }

                        Card(
                            onClick = {
                                if (!isAnswerSubmitted) {
                                    selectedOption = option
                                    isAnswerSubmitted = true
                                    if (option == currentQuestion.correctAnswer) {
                                        correctCount++
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            border = BorderStroke(1.dp, borderColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_option_${option.hashCode()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isAnswerSubmitted) {
                                    if (isCorrect) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Correct",
                                            tint = Color(0xFF059669)
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Incorrect",
                                            tint = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Explanation & Next Button
                AnimatedVisibility(visible = isAnswerSubmitted) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        if (currentQuestion.explanation.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💡 Izoh: ${currentQuestion.explanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Button(
                            onClick = { handleNextQuestion() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("quiz_next_button")
                        ) {
                            Text(
                                text = if (currentIndex < questions.size - 1) "Keyingi savol →" else "Natijani ko‘rish 🎉",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
