package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.model.DictionaryDirection
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.WordMeaningEntity
import com.example.ui.components.WordEntryCard
import com.example.ui.components.WordOfTheDayCard
import com.example.ui.components.LanguageDirectionSelector
import com.example.ui.screens.dictionary.SenseCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionaryDirectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reverseCardShowsOnlySelectedPair() {
        val entry = DictionaryEntryEntity(
            id = 1, headword = "apple", headwordNormalized = "apple", language = "en",
            primaryMeaningUz = "olma", primaryMeaningRu = "яблоко"
        )
        compose.setContent {
            MaterialTheme {
                WordEntryCard(
                    entry = entry, onClick = {}, onAudioClick = {},
                    sourceLanguage = Language.UZBEK_LATIN, targetLanguage = Language.ENGLISH
                )
            }
        }
        compose.onNodeWithText("olma").assertIsDisplayed()
        compose.onNodeWithText("apple").assertIsDisplayed()
        compose.onNodeWithText("яблоко").assertDoesNotExist()
    }

    @Test
    fun wordOfDayUpdatesWithDirectionAndHidesThirdLanguage() {
        val entry = DictionaryEntryEntity(
            id = 1, headword = "apple", headwordNormalized = "apple", language = "en",
            phoneticIpa = "/apple/", primaryMeaningUz = "olma", primaryMeaningRu = "яблоко"
        )
        var pair by mutableStateOf(DictionaryDirection(Language.ENGLISH, Language.UZBEK_LATIN))
        compose.setContent {
            MaterialTheme {
                WordOfTheDayCard(entry = entry, direction = pair, onClick = {}, onAudioClick = {}, onSaveClick = {})
            }
        }
        compose.onNodeWithText("apple").assertIsDisplayed()
        compose.onNodeWithText("olma").assertIsDisplayed()
        compose.onNodeWithText("/apple/").assertIsDisplayed()
        compose.onNodeWithText("яблоко").assertDoesNotExist()
        compose.runOnIdle { pair = pair.swap() }
        compose.onNodeWithText("olma").assertIsDisplayed()
        compose.onNodeWithText("apple").assertIsDisplayed()
        compose.onNodeWithText("/apple/").assertDoesNotExist()
        compose.runOnIdle { pair = DictionaryDirection(Language.RUSSIAN, Language.UZBEK_LATIN) }
        compose.onNodeWithText("яблоко").assertIsDisplayed()
        compose.onNodeWithText("olma").assertIsDisplayed()
        compose.onNodeWithText("apple").assertDoesNotExist()
    }

    @Test
    fun selectorSwapsAndAllowsChangingToThirdLanguage() {
        compose.setContent {
            var pair by remember { mutableStateOf(DictionaryDirection(Language.ENGLISH, Language.UZBEK_LATIN)) }
            MaterialTheme {
                LanguageDirectionSelector(
                    selectedSource = pair.source, selectedTarget = pair.target,
                    onSourceSelect = { it?.let { language -> pair = pair.withSource(language) } },
                    onTargetSelect = { it?.let { language -> pair = pair.withTarget(language) } },
                    onSwap = { pair = pair.swap() }
                )
            }
        }
        compose.onNodeWithTag("swap_languages_button").performClick()
        compose.onNodeWithText("🇺🇿 UZ ▾").assertIsDisplayed()
        compose.onNodeWithText("🇬🇧 EN ▾").performClick()
        compose.onNodeWithText("🇷🇺 Ruscha").performClick()
        compose.onNodeWithText("🇷🇺 RU ▾").assertIsDisplayed()
        compose.onNodeWithText("🇬🇧 EN ▾").assertDoesNotExist()
    }

    @Test
    fun senseHidesExamplesAndDefinitionFromThirdLanguage() {
        compose.setContent {
            MaterialTheme {
                SenseCard(
                    sense = WordMeaningEntity(entryId = 1, definitionEn = "A fruit", definitionRu = "", definitionUz = ""),
                    translations = emptyList(),
                    examples = listOf(ExampleSentenceEntity(entryId = 1, sentenceEn = "An apple", sentenceRu = "яблоко", sentenceUz = "olma")),
                    visibleLanguages = setOf("ru", "uz"),
                    onAudioPlay = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText("A fruit").assertDoesNotExist()
        compose.onNodeWithText("“An apple”").assertDoesNotExist()
        compose.onNodeWithText("🇷🇺 яблоко").assertIsDisplayed()
        compose.onNodeWithText("🇺🇿 olma").assertIsDisplayed()
    }
}
