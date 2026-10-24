package com.example

import com.example.data.database.PreloadDictionaryData
import com.example.data.model.Language
import com.example.data.model.LearningStatus
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun textNormalization_handlesVariedApostrophes() {
        val input1 = "O‘zbekiston"
        val input2 = "O'zbekiston"
        val input3 = "Oʻzbekiston"

        val norm1 = Language.normalizeText(input1)
        val norm2 = Language.normalizeText(input2)
        val norm3 = Language.normalizeText(input3)

        assertEquals("o‘zbekiston", norm1)
        assertEquals(norm1, norm2)
        assertEquals(norm2, norm3)
    }

    @Test
    fun transliterateUzbek_convertsLatinToCyrillic() {
        val latin = "shahar"
        val cyrillic = Language.transliterateUzbek(latin, toCyrillic = true)
        assertTrue(cyrillic.contains("ш"))
    }

    @Test
    fun sm2SpacedRepetition_advancesIntervalOnGoodRating() {
        val word = SavedWordEntity(
            headword = "resilience",
            repetitionNumber = 0,
            intervalDays = 0,
            easeFactor = 2.5f
        )

        // First review with GOOD quality
        val reviewed1 = word.calculateNextReview(ReviewQuality.GOOD)
        assertEquals(1, reviewed1.repetitionNumber)
        assertEquals(1, reviewed1.intervalDays)
        assertEquals(LearningStatus.LEARNING, reviewed1.status)

        // Second review with GOOD quality
        val reviewed2 = reviewed1.calculateNextReview(ReviewQuality.GOOD)
        assertEquals(2, reviewed2.repetitionNumber)
        assertEquals(6, reviewed2.intervalDays)

        // Failure review resets repetition
        val reviewedFail = reviewed2.calculateNextReview(ReviewQuality.AGAIN)
        assertEquals(0, reviewedFail.repetitionNumber)
        assertEquals(1, reviewedFail.intervalDays)
        assertEquals(LearningStatus.DIFFICULT, reviewedFail.status)
    }

    @Test
    fun preloadDictionary_hasValidCorpus() {
        val bundle = PreloadDictionaryData.getInitialData()
        assertTrue(bundle.entries.isNotEmpty())
        assertTrue(bundle.meanings.isNotEmpty())
        assertTrue(bundle.translations.isNotEmpty())
        assertTrue(bundle.examples.isNotEmpty())
    }
}
