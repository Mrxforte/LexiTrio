package com.example.data.model

import java.util.Locale

enum class Language(
    val code: String,
    val displayNameEn: String,
    val displayNameRu: String,
    val displayNameUzLatn: String,
    val displayNameUzCyrl: String,
    val flagEmoji: String,
    val ttsLocale: Locale
) {
    ENGLISH(
        code = "en",
        displayNameEn = "English",
        displayNameRu = "Английский",
        displayNameUzLatn = "Inglizcha",
        displayNameUzCyrl = "Инглизча",
        flagEmoji = "🇬🇧",
        ttsLocale = Locale.US
    ),
    RUSSIAN(
        code = "ru",
        displayNameEn = "Russian",
        displayNameRu = "Русский",
        displayNameUzLatn = "Ruscha",
        displayNameUzCyrl = "Русча",
        flagEmoji = "🇷🇺",
        ttsLocale = Locale("ru", "RU")
    ),
    UZBEK_LATIN(
        code = "uz",
        displayNameEn = "Uzbek (Latin)",
        displayNameRu = "Узбекский (латиница)",
        displayNameUzLatn = "O‘zbekcha (Lotin)",
        displayNameUzCyrl = "Ўзбекча (Лотин)",
        flagEmoji = "🇺🇿",
        ttsLocale = Locale("uz", "UZ")
    ),
    UZBEK_CYRILLIC(
        code = "uz-cyrl",
        displayNameEn = "Uzbek (Cyrillic)",
        displayNameRu = "Узбекский (кириллица)",
        displayNameUzLatn = "O‘zbekcha (Kirill)",
        displayNameUzCyrl = "Ўзбекча (Кирилл)",
        flagEmoji = "🇺🇿",
        ttsLocale = Locale("uz", "UZ")
    );

    fun getDisplayName(appLang: Language): String = when (appLang) {
        ENGLISH -> displayNameEn
        RUSSIAN -> displayNameRu
        UZBEK_LATIN -> displayNameUzLatn
        UZBEK_CYRILLIC -> displayNameUzCyrl
    }

    companion object {
        fun fromCode(code: String): Language = entries.find { 
            it.code.equals(code, ignoreCase = true) || 
            (code.startsWith("uz", ignoreCase = true) && !code.contains("cyrl", ignoreCase = true) && it == UZBEK_LATIN)
        } ?: UZBEK_LATIN

        // Helper to convert between Uzbek Latin and Cyrillic transliteration
        fun transliterateUzbek(text: String, toCyrillic: Boolean): String {
            if (text.isBlank()) return text
            return if (toCyrillic) {
                var res = text
                // Digraphs and special characters first
                res = res.replace("sh", "ш").replace("Sh", "Ш").replace("SH", "Ш")
                res = res.replace("ch", "ч").replace("Ch", "Ч").replace("CH", "Ч")
                res = res.replace("yo", "ё").replace("Yo", "Ё").replace("YO", "Ё")
                res = res.replace("yu", "ю").replace("Yu", "Ю").replace("YU", "Ю")
                res = res.replace("ya", "я").replace("Ya", "Я").replace("YA", "Я")
                res = res.replace("o‘", "ў").replace("O‘", "Ў").replace("o'", "ў").replace("O'", "Ў")
                res = res.replace("oʻ", "ў").replace("Oʻ", "Ў").replace("o`", "ў").replace("O`", "Ў")
                res = res.replace("g‘", "ғ").replace("G‘", "Ғ").replace("g'", "ғ").replace("G'", "Ғ")
                res = res.replace("gʻ", "ғ").replace("Gʻ", "Ғ").replace("g`", "ғ").replace("G`", "Ғ")
                // Simple mappings
                val lat = "abdefghijklmnoprstuvxzqyeABDEFGHIJKLEMNOPRSTUVXZQYE"
                val cyr = "абдефгийклмнопрстувхзқйеАБДЕФГИЙКЛЕМНОПРСТУВХЗҚЙЕ"
                val sb = StringBuilder()
                for (ch in res) {
                    val idx = lat.indexOf(ch)
                    if (idx != -1) sb.append(cyr[idx]) else sb.append(ch)
                }
                sb.toString()
            } else {
                var res = text
                res = res.replace("ш", "sh").replace("Ш", "Sh")
                res = res.replace("ч", "ch").replace("Ч", "Ch")
                res = res.replace("ё", "yo").replace("Ё", "Yo")
                res = res.replace("ю", "yu").replace("Ю", "Yu")
                res = res.replace("я", "ya").replace("Я", "Ya")
                res = res.replace("ў", "oʻ").replace("Ў", "Oʻ")
                res = res.replace("ғ", "gʻ").replace("Ғ", "Gʻ")
                res = res.replace("қ", "q").replace("Қ", "Q")
                res = res.replace("ҳ", "h").replace("Ҳ", "H")
                val cyr = "абвгдезийклмнопрстуфхцэАБВГДЕЗИЙКЛМНОПРСТУФХЦЭ"
                val lat = "abvgdezijklmnoprstufxceABVGDEZIJKLMNOPRSTUFXCE"
                val sb = StringBuilder()
                for (ch in res) {
                    val idx = cyr.indexOf(ch)
                    if (idx != -1) sb.append(lat[idx]) else sb.append(ch)
                }
                sb.toString()
            }
        }

        fun normalizeText(input: String): String {
            return input.trim()
                .replace("’", "‘")
                .replace("`", "‘")
                .replace("'", "‘")
                .replace("ʻ", "‘")
                .lowercase()
        }
    }
}
