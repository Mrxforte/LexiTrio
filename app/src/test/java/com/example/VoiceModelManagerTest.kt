package com.example

import com.example.data.tts.VoiceModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelManagerTest {

    @Test
    fun availableModels_containsVerifiedEnglishAndRussianVoiceModels() {
        val models = VoiceModelManager.AVAILABLE_MODELS
        assertTrue("Models list must not be empty", models.isNotEmpty())

        val englishModel = models.find { it.languageCode == "en" }
        assertNotNull("English neural model must be present", englishModel)
        assertEquals("piper_en_amy_low", englishModel!!.id)
        assertTrue("English model size must be > 10MB", englishModel.modelSizeBytes > 10_000_000L)
        assertTrue("English model license notice must be present", englishModel.copyrightNotice.isNotBlank())

        val russianModel = models.find { it.languageCode == "ru" }
        assertNotNull("Russian neural model must be present", russianModel)
        assertEquals("piper_ru_denis_medium", russianModel!!.id)
        assertTrue("Russian model size must be > 20MB", russianModel.modelSizeBytes > 20_000_000L)
        assertTrue("Russian model license notice must be present", russianModel.copyrightNotice.isNotBlank())
    }

    @Test
    fun modelSizeMb_calculatesCorrectly() {
        val englishModel = VoiceModelManager.AVAILABLE_MODELS.first()
        val mb = englishModel.modelSizeMb
        assertTrue(mb > 15.0f && mb < 50.0f)
    }
}
