package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.LexiTrioDatabase
import com.example.data.importer.DictionaryImportManager
import com.example.data.model.Language
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.LearningRepository
import com.example.data.repository.VocabularyRepository
import com.example.data.tts.TtsManager
import com.example.ui.navigation.Screen
import com.example.ui.screens.conversation.ConversationDetailScreen
import com.example.ui.screens.conversation.ConversationsScreen
import com.example.ui.screens.dictionary.DictionarySearchScreen
import com.example.ui.screens.dictionary.WordDetailScreen
import com.example.ui.screens.grammar.GrammarDetailScreen
import com.example.ui.screens.grammar.GrammarScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.progress.ProgressScreen
import com.example.ui.screens.quiz.QuizActiveScreen
import com.example.ui.screens.quiz.QuizResultScreen
import com.example.ui.screens.quiz.QuizSetupScreen
import com.example.ui.screens.settings.AppSettingsScreen
import com.example.ui.screens.settings.DataImportScreen
import com.example.ui.screens.settings.VoiceSettingsScreen
import com.example.ui.screens.topics.TopicDetailScreen
import com.example.ui.screens.topics.TopicsScreen
import com.example.ui.screens.vocabulary.FlashcardStudyScreen
import com.example.ui.screens.vocabulary.VocabularyScreen
import com.example.ui.theme.LexiTrioTheme

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = LexiTrioDatabase.getInstance(this)
        val dictionaryRepository = DictionaryRepository(database.dictionaryDao())
        val vocabularyRepository = VocabularyRepository(database.vocabularyDao())
        val learningRepository = LearningRepository(
            database.learningDao(),
            database.conversationGrammarDao(),
            database.quizDao()
        )
        val userPreferencesRepository = UserPreferencesRepository(this)
        val importManager = DictionaryImportManager(this, database.dictionaryDao())
        ttsManager = TtsManager(this)

        setContent {
            val preferences by userPreferencesRepository.preferences.collectAsState(initial = AppPreferences())

            LexiTrioTheme(
                darkTheme = preferences.isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
            ) {
                MainAppContent(
                    dictionaryRepository = dictionaryRepository,
                    vocabularyRepository = vocabularyRepository,
                    learningRepository = learningRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    importManager = importManager,
                    ttsManager = ttsManager,
                    preferences = preferences
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

@Composable
fun MainAppContent(
    dictionaryRepository: DictionaryRepository,
    vocabularyRepository: VocabularyRepository,
    learningRepository: LearningRepository,
    userPreferencesRepository: UserPreferencesRepository,
    importManager: DictionaryImportManager,
    ttsManager: TtsManager,
    preferences: AppPreferences
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(
        Triple(Screen.Home.route, "Asosiy", Icons.Default.Home),
        Triple(Screen.Dictionary.route, "Lug‘at", Icons.Default.Book),
        Triple(Screen.Vocabulary.route, "Yodlash", Icons.Default.Style),
        Triple(Screen.QuizSetup.route, "Testlar", Icons.Default.Quiz),
        Triple(Screen.ProgressStats.route, "O‘sish", Icons.Default.EmojiEvents)
    )

    val showBottomBar = bottomNavScreens.any { currentRoute?.startsWith(it.first.substringBefore("?")) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavScreens.forEach { (route, label, icon) ->
                            val baseRoute = route.substringBefore("?")
                            val isSelected = currentRoute?.startsWith(baseRoute) == true

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_item_$baseRoute")
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home
            composable(Screen.Home.route) {
                HomeScreen(
                    dictionaryRepository = dictionaryRepository,
                    vocabularyRepository = vocabularyRepository,
                    learningRepository = learningRepository,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateToSearch = { q, lang ->
                        navController.navigate(Screen.Dictionary.createRoute(q, lang))
                    },
                    onNavigateToWordDetail = { wordId ->
                        navController.navigate(Screen.WordDetail.createRoute(wordId))
                    },
                    onNavigateToTopics = { navController.navigate(Screen.Topics.route) },
                    onNavigateToTopicDetail = { topicId ->
                        navController.navigate(Screen.TopicDetail.createRoute(topicId))
                    },
                    onNavigateToConversations = { navController.navigate(Screen.Conversations.route) },
                    onNavigateToGrammar = { navController.navigate(Screen.Grammar.route) },
                    onNavigateToQuiz = { navController.navigate(Screen.QuizSetup.route) },
                    onNavigateToFlashcards = { mode ->
                        navController.navigate(Screen.FlashcardStudy.createRoute(mode))
                    },
                    onNavigateToVoiceSettings = { navController.navigate(Screen.VoiceSettings.route) },
                    onNavigateToDataImport = { navController.navigate(Screen.DataImport.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            // Dictionary Search
            composable(
                route = Screen.Dictionary.route,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("lang") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val q = backStackEntry.arguments?.getString("query") ?: ""
                val l = backStackEntry.arguments?.getString("lang") ?: ""
                DictionarySearchScreen(
                    initialQuery = q,
                    initialLang = l,
                    dictionaryRepository = dictionaryRepository,
                    vocabularyRepository = vocabularyRepository,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordDetail = { wordId ->
                        navController.navigate(Screen.WordDetail.createRoute(wordId))
                    }
                )
            }

            // Word Detail
            composable(
                route = Screen.WordDetail.route,
                arguments = listOf(navArgument("wordId") { type = NavType.LongType })
            ) { backStackEntry ->
                val wordId = backStackEntry.arguments?.getLong("wordId") ?: 1L
                WordDetailScreen(
                    wordId = wordId,
                    dictionaryRepository = dictionaryRepository,
                    vocabularyRepository = vocabularyRepository,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Vocabulary List
            composable(Screen.Vocabulary.route) {
                VocabularyScreen(
                    vocabularyRepository = vocabularyRepository,
                    ttsManager = ttsManager,
                    onNavigateToFlashcards = { mode ->
                        navController.navigate(Screen.FlashcardStudy.createRoute(mode))
                    },
                    onNavigateToWordDetail = { wordId ->
                        navController.navigate(Screen.WordDetail.createRoute(wordId))
                    }
                )
            }

            // Flashcards (SM-2)
            composable(
                route = Screen.FlashcardStudy.route,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "due"
                    }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "due"
                FlashcardStudyScreen(
                    mode = mode,
                    vocabularyRepository = vocabularyRepository,
                    learningRepository = learningRepository,
                    ttsManager = ttsManager,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Topics
            composable(Screen.Topics.route) {
                TopicsScreen(
                    learningRepository = learningRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTopicDetail = { topicId ->
                        navController.navigate(Screen.TopicDetail.createRoute(topicId))
                    }
                )
            }

            // Topic Detail
            composable(
                route = Screen.TopicDetail.route,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType })
            ) { backStackEntry ->
                val topicId = backStackEntry.arguments?.getLong("topicId") ?: 1L
                TopicDetailScreen(
                    topicId = topicId,
                    learningRepository = learningRepository,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordDetail = { wordId ->
                        navController.navigate(Screen.WordDetail.createRoute(wordId))
                    },
                    onNavigateToQuiz = { category ->
                        navController.navigate(Screen.QuizActive.createRoute(count = 10, category = category))
                    }
                )
            }

            // Conversations
            composable(Screen.Conversations.route) {
                ConversationsScreen(
                    learningRepository = learningRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToConversationDetail = { convId ->
                        navController.navigate(Screen.ConversationDetail.createRoute(convId))
                    }
                )
            }

            // Conversation Detail
            composable(
                route = Screen.ConversationDetail.route,
                arguments = listOf(navArgument("convId") { type = NavType.LongType })
            ) { backStackEntry ->
                val convId = backStackEntry.arguments?.getLong("convId") ?: 1L
                ConversationDetailScreen(
                    convId = convId,
                    learningRepository = learningRepository,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Grammar
            composable(Screen.Grammar.route) {
                GrammarScreen(
                    learningRepository = learningRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGrammarDetail = { lessonId ->
                        navController.navigate(Screen.GrammarDetail.createRoute(lessonId))
                    }
                )
            }

            // Grammar Detail
            composable(
                route = Screen.GrammarDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.LongType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getLong("lessonId") ?: 1L
                GrammarDetailScreen(
                    lessonId = lessonId,
                    learningRepository = learningRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Quiz Setup
            composable(Screen.QuizSetup.route) {
                QuizSetupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onStartQuiz = { count, category, type ->
                        navController.navigate(Screen.QuizActive.createRoute(count, category, type))
                    }
                )
            }

            // Quiz Active
            composable(
                route = Screen.QuizActive.route,
                arguments = listOf(
                    navArgument("count") { type = NavType.IntType; defaultValue = 10 },
                    navArgument("category") { type = NavType.StringType; defaultValue = "All" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val count = backStackEntry.arguments?.getInt("count") ?: 10
                val category = backStackEntry.arguments?.getString("category") ?: "All"
                val type = backStackEntry.arguments?.getString("type") ?: ""
                QuizActiveScreen(
                    count = count,
                    category = category,
                    typeString = type,
                    learningRepository = learningRepository,
                    ttsManager = ttsManager,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToResult = { correct, total, timeSec, xp ->
                        navController.navigate(Screen.QuizResult.createRoute(correct, total, timeSec, xp)) {
                            popUpTo(Screen.QuizSetup.route) { inclusive = false }
                        }
                    }
                )
            }

            // Quiz Result
            composable(
                route = Screen.QuizResult.route,
                arguments = listOf(
                    navArgument("correct") { type = NavType.IntType },
                    navArgument("total") { type = NavType.IntType },
                    navArgument("timeSec") { type = NavType.IntType },
                    navArgument("xp") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val correct = backStackEntry.arguments?.getInt("correct") ?: 0
                val total = backStackEntry.arguments?.getInt("total") ?: 10
                val timeSec = backStackEntry.arguments?.getInt("timeSec") ?: 30
                val xp = backStackEntry.arguments?.getInt("xp") ?: 0

                QuizResultScreen(
                    correct = correct,
                    total = total,
                    timeSec = timeSec,
                    xp = xp,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onRetryQuiz = {
                        navController.navigate(Screen.QuizSetup.route) {
                            popUpTo(Screen.QuizSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            // Progress Stats
            composable(Screen.ProgressStats.route) {
                ProgressScreen(
                    learningRepository = learningRepository,
                    vocabularyRepository = vocabularyRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Voice Settings
            composable(Screen.VoiceSettings.route) {
                VoiceSettingsScreen(
                    ttsManager = ttsManager,
                    userPreferencesRepository = userPreferencesRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Data Import
            composable(Screen.DataImport.route) {
                DataImportScreen(
                    importManager = importManager,
                    dictionaryRepository = dictionaryRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // App Settings
            composable(Screen.Settings.route) {
                AppSettingsScreen(
                    userPreferencesRepository = userPreferencesRepository,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVoiceSettings = { navController.navigate(Screen.VoiceSettings.route) },
                    onNavigateToDataImport = { navController.navigate(Screen.DataImport.route) }
                )
            }
        }
    }
}
