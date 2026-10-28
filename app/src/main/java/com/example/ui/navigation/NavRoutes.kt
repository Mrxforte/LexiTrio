package com.example.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Dictionary : Screen("dictionary?query={query}&lang={lang}") {
        fun createRoute(query: String = "", lang: String = ""): String =
            "dictionary?query=${Uri.encode(query)}&lang=${Uri.encode(lang)}"
    }
    data object WordDetail : Screen("word_detail/{wordId}") {
        fun createRoute(wordId: Long): String = "word_detail/$wordId"
    }
    data object Vocabulary : Screen("vocabulary")
    data object FlashcardStudy : Screen("flashcard_study?mode={mode}") {
        fun createRoute(mode: String = "due"): String = "flashcard_study?mode=$mode"
    }
    data object Topics : Screen("topics")
    data object TopicDetail : Screen("topic_detail/{topicId}") {
        fun createRoute(topicId: Long): String = "topic_detail/$topicId"
    }
    data object Conversations : Screen("conversations")
    data object ConversationDetail : Screen("conversation_detail/{convId}") {
        fun createRoute(convId: Long): String = "conversation_detail/$convId"
    }
    data object Grammar : Screen("grammar")
    data object GrammarDetail : Screen("grammar_detail/{lessonId}") {
        fun createRoute(lessonId: Long): String = "grammar_detail/$lessonId"
    }
    data object QuizSetup : Screen("quiz_setup")
    data object QuizActive : Screen("quiz_active?count={count}&category={category}&type={type}") {
        fun createRoute(count: Int = 10, category: String = "All", type: String = ""): String =
            "quiz_active?count=$count&category=$category&type=$type"
    }
    data object QuizResult : Screen("quiz_result/{correct}/{total}/{timeSec}/{xp}") {
        fun createRoute(correct: Int, total: Int, timeSec: Int, xp: Int): String =
            "quiz_result/$correct/$total/$timeSec/$xp"
    }
    data object ProgressStats : Screen("progress_stats")
    data object VoiceSettings : Screen("voice_settings")
    data object Settings : Screen("settings")
    data object CloudSync : Screen("cloud_sync")

    // Enterprise Curriculum routes
    data object Curriculum : Screen("curriculum")
    data object CurriculumLessonDetail : Screen("curriculum_lesson/{topicId}") {
        fun createRoute(topicId: Long): String = "curriculum_lesson/$topicId"
    }
    data object CurriculumExercises : Screen("curriculum_exercises/{topicId}") {
        fun createRoute(topicId: Long): String = "curriculum_exercises/$topicId"
    }
    data object ContentDashboard : Screen("content_dashboard")

    // Elingo Splash & Onboarding Screens (Pixel Perfect from design)
    data object Splash : Screen("elingo_splash")
    data object OnboardingWelcome : Screen("elingo_welcome")
    data object OnboardingAppLanguage : Screen("elingo_app_language")
    data object OnboardingTargetLanguage : Screen("elingo_target_language")
}
