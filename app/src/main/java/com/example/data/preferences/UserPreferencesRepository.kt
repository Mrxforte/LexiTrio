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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lexitrio_preferences")

data class AppPreferences(
    val appLanguage: Language = Language.UZBEK_LATIN, // Prompt requirement: Default interface language: Uzbek Latin
    val searchSourceLanguage: Language = Language.ENGLISH,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val selectedVoiceId: String = "",
    val autoPlayAudio: Boolean = false,
    val dailyGoalWords: Int = 10,
    val isDarkMode: Boolean? = null,
    val lastUpdatedTimestamp: Long = 0L
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SEARCH_SOURCE_LANG = stringPreferencesKey("search_source_lang")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val AUTO_PLAY_AUDIO = booleanPreferencesKey("auto_play_audio")
        val DAILY_GOAL_WORDS = intPreferencesKey("daily_goal_words")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val LAST_UPDATED_TIMESTAMP = longPreferencesKey("preferences_last_updated_timestamp")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.APP_LANGUAGE] ?: Language.UZBEK_LATIN.code
        val searchLangCode = prefs[Keys.SEARCH_SOURCE_LANG] ?: Language.ENGLISH.code
        val darkModeStr = prefs[Keys.DARK_MODE]

        AppPreferences(
            appLanguage = Language.fromCode(langCode),
            searchSourceLanguage = Language.fromCode(searchLangCode),
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 1.0f,
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: "",
            autoPlayAudio = prefs[Keys.AUTO_PLAY_AUDIO] ?: false,
            dailyGoalWords = prefs[Keys.DAILY_GOAL_WORDS] ?: 10,
            isDarkMode = when (darkModeStr) {
                "true" -> true
                "false" -> false
                else -> null
            },
            lastUpdatedTimestamp = prefs[Keys.LAST_UPDATED_TIMESTAMP] ?: 0L
        )
    }

    suspend fun setAppLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.code
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setSearchSourceLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEARCH_SOURCE_LANG] = language.code
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_PITCH] = pitch
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setSelectedVoiceId(voiceId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setAutoPlayAudio(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_AUDIO] = enabled
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setDailyGoalWords(goal: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL_WORDS] = goal
            prefs[Keys.LAST_UPDATED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setDarkMode(darkMode: Boolean?) {
        context.dataStore.edit { prefs ->
            if (darkMode == null) {
                prefs.remove(Keys.DARK_MODE)
            } else {
                prefs[Keys.DARK_MODE] = darkMode.toString()
            }
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
            if (preferences.isDarkMode == null) {
                prefs.remove(Keys.DARK_MODE)
            } else {
                prefs[Keys.DARK_MODE] = preferences.isDarkMode.toString()
            }
        }
    }
}
