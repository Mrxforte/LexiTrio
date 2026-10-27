package com.example

import com.example.data.model.Language
import com.example.data.tts.ModelDownloadState
import com.example.data.tts.TtsEngineType
import com.example.data.tts.TtsPlaybackState
import com.example.data.tts.TtsRepository
import com.example.data.tts.TtsVoiceInfo
import com.example.domain.usecase.SpeakDictionaryWordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TtsTextProcessorTest {

    private val dummyRepository = object : TtsRepository {
        override val playbackState = MutableStateFlow(TtsPlaybackState())
        override val selectedEngineType = MutableStateFlow(TtsEngineType.AUTO)
        override val speechRate = MutableStateFlow(1.0f)
        override val speechPitch = MutableStateFlow(1.0f)
        override val selectedVoiceId = MutableStateFlow("")
        override val availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
        override val modelDownloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
        override val cacheSizeBytes = MutableStateFlow(0L)

        override suspend fun initialize() {}
        override fun setEngineType(engineType: TtsEngineType) {}
        override fun setSpeechRate(rate: Float) {}
        override fun setSpeechPitch(pitch: Float) {}
        override fun setSelectedVoiceId(voiceId: String) {}
        override fun speak(text: String, languageCode: String, rateMultiplier: Float, voiceId: String?) {}
        override fun stop() {}
        override suspend fun downloadVoiceModel(modelId: String): Result<File> = Result.failure(Exception())
        override fun cancelModelDownload(modelId: String) {}
        override fun deleteVoiceModel(modelId: String): Boolean = true
        override fun clearAudioCache() {}
        override fun shutdown() {}
    }

    private val useCase = SpeakDictionaryWordUseCase(dummyRepository)

    @Test
    fun normalizeText_trimsWhitespaceAndMultipleSpaces() {
        val input = "   hello     world   "
        val normalized = useCase.normalizeText(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun normalizeText_expandsEnglishAbbreviations() {
        val input = "e.g. apple is an adj. word etc."
        val normalized = useCase.normalizeText(input)
        assertEquals("for example apple is an adjective word et cetera", normalized)
    }

    @Test
    fun normalizeText_expandsRussianAbbreviations() {
        val input = "слово т.е. существительное и т.д."
        val normalized = useCase.normalizeText(input)
        assertEquals("слово то есть существительное и так далее", normalized)
    }

    @Test
    fun normalizeText_handlesEmptyAndBlankString() {
        assertEquals("", useCase.normalizeText(""))
        assertEquals("", useCase.normalizeText("   "))
    }

    @Test
    fun normalizeText_truncatesOverlyLongText() {
        val longText = "a ".repeat(400)
        val normalized = useCase.normalizeText(longText)
        assertTrue(normalized.length <= 500)
    }
}
