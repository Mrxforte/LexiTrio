package com.example.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsVoiceInfo(
    val id: String,
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean = false,
    val quality: String = "Normal",
    val latency: String = "Normal"
)

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    private val _currentSpeechRate = MutableStateFlow(1.0f)
    val currentSpeechRate: StateFlow<Float> = _currentSpeechRate.asStateFlow()

    private val _currentPitch = MutableStateFlow(1.0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                }
            })
            loadAvailableVoices()
        }
    }

    private fun loadAvailableVoices() {
        tts?.let { engine ->
            val list = mutableListOf<TtsVoiceInfo>()
            try {
                val voices: Set<Voice>? = engine.voices
                voices?.forEach { v ->
                    val isNet = v.isNetworkConnectionRequired
                    val qual = if (v.quality >= Voice.QUALITY_VERY_HIGH) "Very High" else if (v.quality >= Voice.QUALITY_HIGH) "High" else "Standard"
                    list.add(
                        TtsVoiceInfo(
                            id = v.name,
                            name = v.name.substringAfterLast("-", v.name),
                            locale = v.locale,
                            requiresNetwork = isNet,
                            quality = qual
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore voice retrieval exceptions on older engines
            }
            _availableVoices.value = list
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _currentSpeechRate.value = clamped
        tts?.setSpeechRate(clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _currentPitch.value = clamped
        tts?.setPitch(clamped)
    }

    fun setVoice(voiceId: String) {
        tts?.let { engine ->
            try {
                val match = engine.voices?.find { it.name == voiceId }
                if (match != null) {
                    engine.voice = match
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
    }

    fun speak(text: String, languageCode: String, rateMultiplier: Float = 1.0f) {
        if (!isInitialized || text.isBlank()) return
        tts?.let { engine ->
            stop()

            val lang = Language.fromCode(languageCode)
            val locale = when (lang) {
                Language.ENGLISH -> Locale.US
                Language.RUSSIAN -> Locale("ru", "RU")
                Language.UZBEK_LATIN, Language.UZBEK_CYRILLIC -> {
                    // Check if Uzbek is supported by installed TTS engine
                    val uzLocale = Locale("uz", "UZ")
                    val isUzSupported = engine.isLanguageAvailable(uzLocale) >= TextToSpeech.LANG_AVAILABLE
                    if (isUzSupported) uzLocale else Locale("tr", "TR") // Turkish shares strong phonetic similarity for Latin Uzbek
                }
            }

            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default
                engine.setLanguage(Locale.US)
            }

            val finalRate = (_currentSpeechRate.value * rateMultiplier).coerceIn(0.5f, 2.0f)
            engine.setSpeechRate(finalRate)
            engine.setPitch(_currentPitch.value)

            val utteranceId = "utterance_${System.currentTimeMillis()}"
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isPlaying.value = false
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}
