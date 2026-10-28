package com.example.data.model

data class DictionaryDirection(val source: Language, val target: Language) {
    fun swap() = DictionaryDirection(target, source)

    fun withSource(language: Language): DictionaryDirection =
        if (language.dictionaryCode == target.dictionaryCode) swap() else copy(source = language)

    fun withTarget(language: Language): DictionaryDirection =
        if (language.dictionaryCode == source.dictionaryCode) swap() else copy(target = language)

    companion object {
        fun from(source: Language, target: Language): DictionaryDirection {
            val normalizedSource = if (source == Language.UZBEK_CYRILLIC) Language.UZBEK_LATIN else source
            val normalizedTarget = if (target == Language.UZBEK_CYRILLIC) Language.UZBEK_LATIN else target
            return DictionaryDirection(
                normalizedSource,
                if (normalizedSource == normalizedTarget) {
                    if (normalizedSource == Language.ENGLISH) Language.UZBEK_LATIN else Language.ENGLISH
                } else normalizedTarget
            )
        }
    }
}

val Language.dictionaryCode: String get() = code.substringBefore('-')

fun DictionaryEntryEntity.textIn(language: Language): String {
    if (this.language.substringBefore('-') == language.dictionaryCode) return headword
    return when (language.dictionaryCode) {
        "en" -> primaryMeaningEn
        "ru" -> primaryMeaningRu
        else -> primaryMeaningUz
    }
}
