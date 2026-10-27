package com.example.ui.screens.curriculum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CurriculumExerciseEntity
import com.example.ui.components.InteractiveExerciseCard
import com.example.ui.components.LessonProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumExerciseScreen(
    topicTitle: String,
    exercises: List<CurriculumExerciseEntity>,
    onBackClicked: () -> Unit,
    onSpeakText: (String) -> Unit,
    onRecordMistake: (exerciseId: Long, questionType: String, userAnswer: String, correctAnswer: String) -> Unit,
    onCompleteSession: (score: Int, total: Int) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var correctAnswersCount by remember { mutableIntStateOf(0) }
    var isCurrentAnswerSubmitted by remember { mutableStateOf(false) }

    val currentExercise = exercises.getOrNull(currentIndex)
    val isFinished = currentIndex >= exercises.size || exercises.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercises: $topicTitle") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (isFinished) {
                // Exercise Session Complete Screen
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Success",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .padding(16.dp)
                            .height(72.dp)
                            .width(72.dp)
                    )

                    Text(
                        text = "Practice Complete!",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You scored $correctAnswersCount out of ${exercises.size} correctly.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            onCompleteSession(correctAnswersCount, exercises.size)
                            onBackClicked()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finish & Save Progress")
                    }
                }
            } else if (currentExercise != null) {
                // Active Exercise
                Column {
                    LessonProgressIndicator(
                        currentStep = currentIndex + 1,
                        totalSteps = exercises.size
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    InteractiveExerciseCard(
                        exercise = currentExercise,
                        onAnswerSubmitted = { userAnswer, isCorrect ->
                            isCurrentAnswerSubmitted = true
                            if (isCorrect) {
                                correctAnswersCount++
                            } else {
                                onRecordMistake(
                                    currentExercise.id,
                                    currentExercise.exerciseType,
                                    userAnswer,
                                    currentExercise.correctAnswer
                                )
                            }
                        },
                        onSpeakText = onSpeakText
                    )
                }

                if (isCurrentAnswerSubmitted) {
                    Button(
                        onClick = {
                            isCurrentAnswerSubmitted = false
                            currentIndex++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text("Next Question")
                    }
                }
            }
        }
    }
}
