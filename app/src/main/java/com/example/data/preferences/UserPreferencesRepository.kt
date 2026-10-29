package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.Language
import com.example.data.tts.TtsEngineType
import com.example.domain.repository.PreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lexitrio_preferences")

data class AppPreferences(
    val appLanguage: Language = Language.UZBEK_LATIN, // Prompt requirement: Default interface language: Uzbek Latin
    val searchSourceLanguage: Language = Language.ENGLISH,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val selectedVoiceId: String = "",
    val ttsEngineType: TtsEngineType = TtsEngineType.AUTO,
    val autoPlayAudio: Boolean = false,
    val dailyGoalWords: Int = 10,
    val isDarkMode: Boolean? = null,
    val lastUpdatedTimestamp: Long = 0L,
    val isOnboardingCompleted: Boolean = false,
    val searchTargetLanguage: Language = Language.UZBEK_LATIN
)

class UserPreferencesRepository(private val context: Context) : PreferenceRepository {

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SEARCH_SOURCE_LANG = stringPreferencesKey("search_source_lang")
        val SEARCH_TARGET_LANG = stringPreferencesKey("search_target_lang")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val TTS_ENGINE_TYPE = stringPreferencesKey("tts_engine_type")
        val AUTO_PLAY_AUDIO = booleanPreferencesKey("auto_play_audio")
        val DAILY_GOAL_WORDS = intPreferencesKey("daily_goal_words")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val LAST_UPDATED_TIMESTAMP = longPreferencesKey("preferences_last_updated_timestamp")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    override val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.APP_LANGUAGE] ?: Language.UZBEK_LATIN.code
        val searchLangCode = prefs[Keys.SEARCH_SOURCE_LANG] ?: Language.ENGLISH.code
        val darkModeStr = prefs[Keys.DARK_MODE]
        val engineStr = prefs[Keys.TTS_ENGINE_TYPE]

        AppPreferences(
            appLanguage = Language.fromCode(langCode),
            searchSourceLanguage = Language.fromCode(searchLangCode),
            searchTargetLanguage = Language.fromCode(prefs[Keys.SEARCH_TARGET_LANG] ?: "uz"),
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 1.0f,
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: "",
            ttsEngineType = try {
                if (engineStr != null) TtsEngineType.valueOf(engineStr) else TtsEngineType.AUTO
            } catch (_: Exception) { TtsEngineType.AUTO },
            autoPlayAudio = prefs[Keys.AUTO_PLAY_AUDIO] ?: false,
            dailyGoalWords = prefs[Keys.DAILY_GOAL_WORDS] ?: 10,
            isDarkMode = when (darkModeStr) {
                "true" -> true
                "false" -> false
                else -> null
            },
            lastUpdatedTimestamp = prefs[Keys.LAST_UPDATED_TIMESTAMP] ?: 0L,
            isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    override suspend fun setAppLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.code
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setSearchSourceLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEARCH_SOURCE_LANG] = language.code
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setDictionaryLanguages(source: Language, target: Language) {
        require(source.code.substringBefore('-') != target.code.substringBefore('-'))
        context.dataStore.edit { prefs ->
            prefs[Keys.SEARCH_SOURCE_LANG] = source.code
            prefs[Keys.SEARCH_TARGET_LANG] = target.code
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_PITCH] = pitch
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setSelectedVoiceId(voiceId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setTtsEngineType(engineType: TtsEngineType) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TTS_ENGINE_TYPE] = engineType.name
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setAutoPlayAudio(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_AUDIO] = enabled
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setDailyGoalWords(goal: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL_WORDS] = goal
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setDarkMode(darkMode: Boolean?) {
        context.dataStore.edit { prefs ->
            if (darkMode == null) {
                prefs.remove(Keys.DARK_MODE)
            } else {
                prefs[Keys.DARK_MODE] = darkMode.toString()
            }
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun replaceWith(preferences: AppPreferences) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = preferences.appLanguage.code
            prefs[Keys.SEARCH_SOURCE_LANG] = preferences.searchSourceLanguage.code
            prefs[Keys.SPEECH_RATE] = preferences.speechRate
            prefs[Keys.SPEECH_PITCH] = preferences.speechPitch
            prefs[Keys.SELECTED_VOICE_ID] = preferences.selectedVoiceId
            prefs[Keys.AUTO_PLAY_AUDIO] = preferences.autoPlayAudio
            prefs[Keys.DAILY_GOAL_WORDS] = preferences.dailyGoalWords
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = preferences.lastUpdatedTimestamp
            prefs[Keys.ONBOARDING_COMPLETED] = preferences.isOnboardingCompleted
            if (preferences.isDarkMode == null) {
                prefs.remove(Keys.DARK_MODE)
            } else {
                prefs[Keys.DARK_MODE] = preferences.isDarkMode.toString()
            }
        }
    }
}
