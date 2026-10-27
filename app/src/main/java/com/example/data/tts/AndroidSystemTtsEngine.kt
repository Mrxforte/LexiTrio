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

class AndroidSystemTtsEngine(
    private val context: Context
) : TtsEngine, TextToSpeech.OnInitListener {

    override val engineType: TtsEngineType = TtsEngineType.SYSTEM

    private var tts: TextToSpeech? = null
    private var _isAvailable = false
    override val isAvailable: Boolean get() = _isAvailable

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    private var activeOnStart: (() -> Unit)? = null
    private var activeOnDone: (() -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null

    override suspend fun initialize() {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _isAvailable = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                    activeOnStart?.invoke()
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                    activeOnDone?.invoke()
                    clearCallbacks()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                    activeOnError?.invoke("TTS engine synthesis error")
                    clearCallbacks()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isPlaying.value = false
                    activeOnError?.invoke("TTS engine synthesis error code: $errorCode")
                    clearCallbacks()
                }
            })
            loadAvailableVoices()
        } else {
            _isAvailable = false
        }
    }

    private fun clearCallbacks() {
        activeOnStart = null
        activeOnDone = null
        activeOnError = null
    }

    private fun loadAvailableVoices() {
        tts?.let { engine ->
            val list = mutableListOf<TtsVoiceInfo>()
            try {
                val voices: Set<Voice>? = engine.voices
                voices?.forEach { v ->
                    val isNet = v.isNetworkConnectionRequired
                    val qual = if (v.quality >= Voice.QUALITY_VERY_HIGH) "Very High"
                    else if (v.quality >= Voice.QUALITY_HIGH) "High" else "Standard"
                    val langCode = when {
                        v.locale.language == "ru" -> "ru"
                        v.locale.language == "uz" -> "uz"
                        else -> "en"
                    }
                    list.add(
                        TtsVoiceInfo(
                            id = v.name,
                            name = v.name.substringAfterLast("-", v.name),
                            locale = v.locale,
                            languageCode = langCode,
                            engineType = TtsEngineType.SYSTEM,
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

    override fun speak(
        text: String,
        languageCode: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        if (!_isAvailable || text.isBlank()) {
            onError?.invoke("System TTS engine is not initialized or text is empty")
            return
        }

        stop()

        activeOnStart = onStart
        activeOnDone = onDone
        activeOnError = onError

        tts?.let { engine ->
            val lang = Language.fromCode(languageCode)
            val locale = when (lang) {
                Language.ENGLISH -> Locale.US
                Language.RUSSIAN -> Locale("ru", "RU")
                Language.UZBEK_LATIN, Language.UZBEK_CYRILLIC -> {
                    val uzLocale = Locale("uz", "UZ")
                    val isUzSupported = engine.isLanguageAvailable(uzLocale) >= TextToSpeech.LANG_AVAILABLE
                    if (isUzSupported) uzLocale else Locale("tr", "TR")
                }
            }

            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }

            if (!voiceId.isNull_or_blank()) {
                try {
                    val match = engine.voices?.find { it.name == voiceId }
                    if (match != null) {
                        engine.voice = match
                    }
                } catch (_: Exception) {}
            }

            val clampedRate = rate.coerceIn(0.5f, 2.0f)
            val clampedPitch = pitch.coerceIn(0.5f, 2.0f)

            engine.setSpeechRate(clampedRate)
            engine.setPitch(clampedPitch)

            val utteranceId = "sys_utt_${System.currentTimeMillis()}"
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isPlaying.value = false
        clearCallbacks()
    }

    override fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
        _isAvailable = false
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}
