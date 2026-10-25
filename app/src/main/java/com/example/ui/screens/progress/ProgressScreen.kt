package com.example.ui.screens.progress

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AchievementItem
import com.example.data.model.Language
import com.example.data.model.LearningStatus
import com.example.data.model.UserProgressEntity
import com.example.data.preferences.AppPreferences
import com.example.presentation.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: AppViewModel,
    preferences: AppPreferences,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.progress.collectAsState()
    val allSavedWords by viewModel.savedWords.collectAsState()
    val masteredWords by viewModel.masteredWords.collectAsState()
    val dueWords by viewModel.dueWords.collectAsState()
    val recentQuizAttempts by viewModel.quizAttempts.collectAsState()

    val userProg = progress ?: UserProgressEntity()

    val achievements = remember(userProg, allSavedWords, masteredWords) {
        listOf(
            AchievementItem(
                id = "first_step",
                titleEn = "First Steps",
                titleRu = "Первые шаги",
                titleUzLatn = "Ilk qadamlar",
                descriptionEn = "Save your first 5 dictionary words",
                descriptionRu = "Сохраните свои первые 5 слов",
                descriptionUzLatn = "Dastlabki 5 ta so‘zni lug‘atga saqlang",
                iconName = "school",
                isUnlocked = allSavedWords.size >= 5,
                progress = (allSavedWords.size.toFloat() / 5f).coerceIn(0f, 1f)
            ),
            AchievementItem(
                id = "streak_master",
                titleEn = "Unstoppable Streak",
                titleRu = "Неудержимая серия",
                titleUzLatn = "To‘xtovsiz seriya",
                descriptionEn = "Maintain a 3-day continuous learning streak",
                descriptionRu = "Поддерживайте 3-дневную серию занятий",
                descriptionUzLatn = "Ketma-ket 3 kunlik o‘rganish seriyasiga erishing",
                iconName = "fire",
                isUnlocked = userProg.streakDays >= 3,
                progress = (userProg.streakDays.toFloat() / 3f).coerceIn(0f, 1f)
            ),
            AchievementItem(
                id = "quiz_champion",
                titleEn = "Quiz Champion",
                titleRu = "Чемпион викторин",
                titleUzLatn = "Viktorina chempioni",
                descriptionEn = "Successfully complete 5 vocabulary and grammar quizzes",
                descriptionRu = "Пройдите 5 тестов по словарному запасу и грамматике",
                descriptionUzLatn = "5 ta leksik va grammatik testni yakunlang",
                iconName = "trophy",
                isUnlocked = userProg.totalQuizzesCompleted >= 5,
                progress = (userProg.totalQuizzesCompleted.toFloat() / 5f).coerceIn(0f, 1f)
            ),
            AchievementItem(
                id = "polyglot_mind",
                titleEn = "Polyglot Master",
                titleRu = "Мастер-полиглот",
                titleUzLatn = "Poliglot bilimdon",
                descriptionEn = "Accumulate 200+ XP in trilingual mastery",
                descriptionRu = "Наберите 200+ XP в изучении трех языков",
                descriptionUzLatn = "200+ XP ball to‘plang",
                iconName = "star",
                isUnlocked = userProg.xp >= 200,
                progress = (userProg.xp.toFloat() / 200f).coerceIn(0f, 1f)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "O‘sish va statistika (Progress)",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Level & XP Hero Card
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
                            Column {
                                Text(
                                    text = "${userProg.level}-daraja",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = userProg.getLevelTitle(preferences.appLanguage),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFFFEF3C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MilitaryTech,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val levelProgress = ((userProg.xp % 100) / 100f).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${userProg.xp} XP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${((userProg.level) * 100)} XP gacha",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { levelProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Overview Metric Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "O‘rganilmoqda",
                        value = "${allSavedWords.size}",
                        icon = Icons.Default.School,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Yod olingan",
                        value = "${masteredWords.size}",
                        icon = Icons.Default.EmojiEvents,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Kunlik seriya",
                        value = "${userProg.streakDays} kun",
                        icon = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Viktorinalar",
                        value = "${userProg.totalQuizzesCompleted}",
                        icon = Icons.Default.AutoAwesome,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Achievements List
            item {
                Text(
                    text = "Yutuqlar (Achievements)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(achievements, key = { it.id }) { ach ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ach.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (ach.isUnlocked) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (ach.isUnlocked) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (preferences.appLanguage) {
                                    Language.ENGLISH -> ach.titleEn
                                    Language.RUSSIAN -> ach.titleRu
                                    else -> ach.titleUzLatn
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (preferences.appLanguage) {
                                    Language.ENGLISH -> ach.descriptionEn
                                    Language.RUSSIAN -> ach.descriptionRu
                                    else -> ach.descriptionUzLatn
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!ach.isUnlocked) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ach.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline
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
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
