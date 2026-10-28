package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.example.data.cache.DetailCacheManager
import com.example.data.cloud.AdaptivePrefetchScheduler
import com.example.data.cloud.CloudApiFactory
import com.example.data.cloud.DictionaryDownloadScheduler
import com.example.data.cloud.SyncScheduler
import com.example.data.cloud.SyncStateStore
import com.example.data.model.CurriculumCoverageReport
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.GrammarTableEntity
import com.example.data.model.Language
import com.example.data.model.LessonContentEntity
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CurriculumRepositoryImpl
import com.example.data.repository.DictionaryRepository
import com.example.data.repository.LearningRepository
import com.example.data.repository.VocabularyRepository
import com.example.data.tts.TtsManager
import com.example.ui.navigation.Screen
import com.example.ui.screens.curriculum.ContentDashboardScreen
import com.example.ui.screens.curriculum.CurriculumExerciseScreen
import com.example.ui.screens.curriculum.CurriculumScreen
import com.example.ui.screens.curriculum.LessonDetailScreen
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
import com.example.presentation.cloud.CloudSyncScreen
import com.example.presentation.AppViewModel
import com.example.ui.screens.settings.VoiceSettingsScreen
import com.example.ui.screens.topics.TopicDetailScreen
import com.example.ui.screens.topics.TopicsScreen
import com.example.ui.screens.vocabulary.FlashcardStudyScreen
import com.example.ui.screens.vocabulary.VocabularyScreen
import com.example.ui.screens.onboarding.ElingoSplashScreen
import com.example.ui.screens.onboarding.ElingoWelcomeScreen
import com.example.ui.screens.onboarding.ElingoAppLanguageScreen
import com.example.ui.screens.onboarding.ElingoTargetLanguageScreen
import com.example.ui.theme.LexiTrioTheme

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncScheduler.schedule(applicationContext)
        DictionaryDownloadScheduler.schedule(applicationContext)
        AdaptivePrefetchScheduler.schedule(applicationContext)

        val database = LexiTrioDatabase.getInstance(this)
        // Without a configured API (e.g. a release build missing its URL) the app stays fully offline.
        val cloudApi = runCatching { CloudApiFactory.create(applicationContext) }.getOrNull()
        val syncState = SyncStateStore(applicationContext)
        val dictionaryRepository = DictionaryRepository(
            database.dictionaryDao(),
            cloudApi,
            isCatalogComplete = { syncState.isDictionaryComplete },
            detailCache = DetailCacheManager(database, cloudApi)
        )
        val vocabularyRepository = VocabularyRepository(database.vocabularyDao())
        val learningRepository = LearningRepository(
            database.learningDao(),
            database.conversationGrammarDao(),
            database.quizDao()
        )
        val curriculumRepository = CurriculumRepositoryImpl(database.curriculumDao())
        val userPreferencesRepository = UserPreferencesRepository(this)
        ttsManager = TtsManager(this)
        val appViewModel = ViewModelProvider(
            this,
            AppViewModel.Factory(
                dictionaryRepository,
                vocabularyRepository,
                learningRepository,
                userPreferencesRepository,
                curriculumRepository
            )
        )[AppViewModel::class.java]

        setContent {
            val preferences by appViewModel.preferences.collectAsState()

            LexiTrioTheme(
                darkTheme = preferences.isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
            ) {
                MainAppContent(
                    viewModel = appViewModel,
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
    viewModel: AppViewModel,
    ttsManager: TtsManager,
    preferences: AppPreferences
) {
    var onboardingNative by rememberSaveable { mutableStateOf(preferences.searchTargetLanguage.code) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(
        Triple(Screen.Home.route, "Asosiy", Icons.Default.Home),
        Triple(Screen.Dictionary.route, "Lug‘at", Icons.Default.Book),
        Triple(Screen.Curriculum.route, "Dastur", Icons.Default.School),
        Triple(Screen.Vocabulary.route, "Yodlash", Icons.Default.Style),
        Triple(Screen.ProgressStats.route, "Profil", Icons.Default.Person)
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
        // Every screen owns its status-bar inset; the host only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Elingo Screen 1: Splash Screen
            composable(Screen.Splash.route) {
                ElingoSplashScreen(
                    onSplashFinished = {
                        val destination = if (preferences.isOnboardingCompleted) {
                            Screen.Home.route
                        } else {
                            Screen.OnboardingWelcome.route
                        }
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Elingo Screen 2: Welcome / Mascot Screen
            composable(Screen.OnboardingWelcome.route) {
                ElingoWelcomeScreen(
                    onGetStartedClick = {
                        navController.navigate(Screen.OnboardingAppLanguage.route)
                    },
                    onAlreadyHaveAccountClick = {
                        viewModel.setOnboardingCompleted(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                        }
                    }
                )
            }

            // Elingo Screen 3: App & Native Language Selection
            composable(Screen.OnboardingAppLanguage.route) {
                ElingoAppLanguageScreen(
                    onBackClick = { navController.popBackStack() },
                    onContinueClick = { appLangCode, nativeLangCode ->
                        onboardingNative = nativeLangCode
                        val selectedAppLang = when (appLangCode.lowercase()) {
                            "en" -> Language.ENGLISH
                            "ru" -> Language.RUSSIAN
                            "uz" -> Language.UZBEK_LATIN
                            else -> Language.ENGLISH
                        }
                        viewModel.setAppLanguage(selectedAppLang)
                        navController.navigate(Screen.OnboardingTargetLanguage.route)
                    }
                )
            }

            // Elingo Screen 4: Target Learning Language Selection
            composable(Screen.OnboardingTargetLanguage.route) {
                ElingoTargetLanguageScreen(
                    onBackClick = { navController.popBackStack() },
                    nativeLangCode = onboardingNative,
                    onContinueClick = { targetLangCode ->
                        val selectedTargetLang = when (targetLangCode.lowercase()) {
                            "en" -> Language.ENGLISH
                            "ru" -> Language.RUSSIAN
                            "uz" -> Language.UZBEK_LATIN
                            else -> Language.ENGLISH
                        }
                        val native = Language.fromCode(onboardingNative)
                        val target = if (native.code.substringBefore('-') == selectedTargetLang.code.substringBefore('-')) {
                            if (selectedTargetLang == Language.ENGLISH) Language.UZBEK_LATIN else Language.ENGLISH
                        } else native
                        viewModel.setDictionaryLanguages(selectedTargetLang, target)
                        viewModel.setOnboardingCompleted(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
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
                    onNavigateToCurriculum = { navController.navigate(Screen.Curriculum.route) },
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
                val rawQ = backStackEntry.arguments?.getString("query") ?: ""
                val rawL = backStackEntry.arguments?.getString("lang") ?: ""
                val q = if (rawQ == "{query}") "" else Uri.decode(rawQ)
                val l = if (rawL == "{lang}") "" else Uri.decode(rawL)
                DictionarySearchScreen(
                    initialQuery = q,
                    initialLang = l,
                    viewModel = viewModel,
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
                    viewModel = viewModel,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Vocabulary List
            composable(Screen.Vocabulary.route) {
                VocabularyScreen(
                    viewModel = viewModel,
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
                    viewModel = viewModel,
                    ttsManager = ttsManager,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Topics
            composable(Screen.Topics.route) {
                TopicsScreen(
                    viewModel = viewModel,
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
                    viewModel = viewModel,
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
                    viewModel = viewModel,
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
                    viewModel = viewModel,
                    ttsManager = ttsManager,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Grammar
            composable(Screen.Grammar.route) {
                GrammarScreen(
                    viewModel = viewModel,
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
                    viewModel = viewModel,
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
                    viewModel = viewModel,
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

            // Profile & Progress Stats
            composable(Screen.ProgressStats.route) {
                ProgressScreen(
                    viewModel = viewModel,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToVoiceSettings = { navController.navigate(Screen.VoiceSettings.route) },
                    onNavigateToCloudSync = { navController.navigate(Screen.CloudSync.route) }
                )
            }

            // Voice Settings
            composable(Screen.VoiceSettings.route) {
                VoiceSettingsScreen(
                    ttsManager = ttsManager,
                    viewModel = viewModel,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // App Settings
            composable(Screen.Settings.route) {
                AppSettingsScreen(
                    viewModel = viewModel,
                    preferences = preferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVoiceSettings = { navController.navigate(Screen.VoiceSettings.route) },
                    onNavigateToCloudSync = { navController.navigate(Screen.CloudSync.route) },
                    onNavigateToOnboarding = { navController.navigate(Screen.Splash.route) }
                )
            }

            composable(Screen.CloudSync.route) {
                CloudSyncScreen(onNavigateBack = { navController.popBackStack() })
            }

            // Enterprise Curriculum Screens
            composable(Screen.Curriculum.route) {
                var selectedLanguage by remember { mutableStateOf("en") }
                var selectedLevel by remember { mutableStateOf("All") }
                val topics by viewModel.getCurriculumTopics(selectedLanguage, selectedLevel).collectAsState(initial = emptyList())

                CurriculumScreen(
                    topics = topics,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { selectedLanguage = it },
                    selectedLevel = selectedLevel,
                    onLevelSelected = { selectedLevel = it },
                    onTopicClicked = { topicId ->
                        navController.navigate(Screen.CurriculumLessonDetail.createRoute(topicId))
                    },
                    onOpenDashboard = { navController.navigate(Screen.ContentDashboard.route) },
                    onOpenTestBank = { navController.navigate(Screen.QuizSetup.route) }
                )
            }

            composable(
                route = Screen.CurriculumLessonDetail.route,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType })
            ) { backStackEntry ->
                val topicId = backStackEntry.arguments?.getLong("topicId") ?: 1L
                var topic by remember { mutableStateOf<CurriculumTopicEntity?>(null) }
                var lesson by remember { mutableStateOf<LessonContentEntity?>(null) }
                var tables by remember { mutableStateOf<List<GrammarTableEntity>>(emptyList()) }

                LaunchedEffect(topicId) {
                    topic = viewModel.getCurriculumTopicById(topicId)
                    val l = viewModel.getCurriculumLessonForTopic(topicId)
                    lesson = l
                    if (l != null) {
                        tables = viewModel.getCurriculumTablesForLesson(l.id)
                    }
                }

                if (topic != null) {
                    LessonDetailScreen(
                        topic = topic!!,
                        lesson = lesson,
                        tables = tables,
                        onBackClicked = { navController.popBackStack() },
                        onStartExercises = {
                            navController.navigate(Screen.CurriculumExercises.createRoute(topicId))
                        },
                        onSpeakText = { text -> ttsManager.speak(text, topic?.language ?: "en") }
                    )
                }
            }

            composable(
                route = Screen.CurriculumExercises.route,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType })
            ) { backStackEntry ->
                val topicId = backStackEntry.arguments?.getLong("topicId") ?: 1L
                var topic by remember { mutableStateOf<CurriculumTopicEntity?>(null) }
                val exercises by viewModel.getCurriculumExercisesForTopic(topicId).collectAsState(initial = emptyList())
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(topicId) {
                    topic = viewModel.getCurriculumTopicById(topicId)
                }

                CurriculumExerciseScreen(
                    topicTitle = topic?.titleEn ?: "Practice",
                    exercises = exercises,
                    onBackClicked = { navController.popBackStack() },
                    onSpeakText = { text -> ttsManager.speak(text, topic?.language ?: "en") },
                    onRecordMistake = { exId, qType, userAns, correctAns ->
                        coroutineScope.launch {
                            viewModel.recordCurriculumMistake(exId, qType, userAns, correctAns)
                        }
                    },
                    onCompleteSession = { score, total ->
                        coroutineScope.launch {
                            viewModel.addProgress(xpGain = score * 10, isQuiz = true)
                        }
                    }
                )
            }

            composable(Screen.ContentDashboard.route) {
                var report by remember { mutableStateOf<CurriculumCoverageReport?>(null) }

                LaunchedEffect(Unit) {
                    report = viewModel.getCurriculumCoverageReport()
                }

                ContentDashboardScreen(
                    report = report,
                    onBackClicked = { navController.popBackStack() }
                )
            }
        }
    }
}
