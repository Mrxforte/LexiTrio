package com.example.domain.usecase

import com.example.data.model.Language
import com.example.data.tts.TtsRepository

class SpeakDictionaryWordUseCase(
    private val repository: TtsRepository
) {
    operator fun invoke(
        text: String,
        language: Language,
        rateMultiplier: Float = 1.0f,
        voiceId: String? = null
    ) {
        val normalizedText = normalizeText(text)
        if (normalizedText.isBlank()) return

        val langCode = language.code
        repository.speak(
            text = normalizedText,
            languageCode = langCode,
            rateMultiplier = rateMultiplier,
            voiceId = voiceId
        )
    }

    operator fun invoke(
        text: String,
        languageCode: String,
        rateMultiplier: Float = 1.0f,
        voiceId: String? = null
    ) {
        val normalizedText = normalizeText(text)
        if (normalizedText.isBlank()) return

        val lang = Language.fromCode(languageCode)
        invoke(normalizedText, lang, rateMultiplier, voiceId)
    }

    fun normalizeText(input: String): String {
        if (input.isBlank()) return ""

        var result = input.trim()

        // Replace common dictionary abbreviations with spoken equivalents for clear TTS
        result = result
            .replace(Regex("(?i)\\b(e\\.g\\.|eg\\.)"), "for example")
            .replace(Regex("(?i)\\b(i\\.e\\.|ie\\.)"), "that is")
            .replace(Regex("(?i)\\b(etc\\.)"), "et cetera")
            .replace(Regex("(?i)\\b(adj\\.)"), "adjective")
            .replace(Regex("(?i)\\b(adv\\.)"), "adverb")
            .replace(Regex("(?i)\\b(n\\.)"), "noun")
            .replace(Regex("(?i)\\b(v\\.)"), "verb")
            .replace(Regex("(?i)(?:^|(?<=\\s))т\\.е\\."), "то есть")
            .replace(Regex("(?i)(?:^|(?<=\\s))и т\\.д\\."), "и так далее")
            .replace(Regex("(?i)(?:^|(?<=\\s))и т\\.п\\."), "и тому подобное")
            .replace(Regex("\\s+"), " ")

        // Truncate overly long text (max 500 chars) for responsive dictionary speech
        if (result.length > 500) {
            result = result.substring(0, 500).substringBeforeLast(" ")
        }

        return result
    }
}
