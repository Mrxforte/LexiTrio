package com.example.data.tts

import android.content.Context
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TtsRepositoryImpl(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    val modelManager: VoiceModelManager = VoiceModelManager(context),
    val cacheManager: TtsAudioCacheManager = TtsAudioCacheManager(context)
) : TtsRepository {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val systemEngine: AndroidSystemTtsEngine = AndroidSystemTtsEngine(context)
    val neuralEngine: SherpaOnnxTtsEngine = SherpaOnnxTtsEngine(context, modelManager, systemEngine)

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    override val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _selectedEngineType = MutableStateFlow(TtsEngineType.AUTO)
    override val selectedEngineType: StateFlow<TtsEngineType> = _selectedEngineType.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    override val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(1.0f)
    override val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow("")
    override val selectedVoiceId: StateFlow<String> = _selectedVoiceId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    override val modelDownloadStates: StateFlow<Map<String, ModelDownloadState>>
        get() = modelManager.downloadStates

    private val _cacheSizeBytes = MutableStateFlow(0L)
    override val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val speakMutex = Mutex()
    private var speakJob: Job? = null

    override suspend fun initialize() {
        systemEngine.initialize()
        neuralEngine.initialize()
        updateVoicesList()
        updateCacheSize()

        // Observe preference changes
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _speechRate.value = prefs.speechRate
                _speechPitch.value = prefs.speechPitch
                _selectedVoiceId.value = prefs.selectedVoiceId
            }
        }
    }

    private fun updateVoicesList() {
        val sysVoices = systemEngine.availableVoices.value
        val neuralVoices = neuralEngine.availableVoices.value
        _availableVoices.value = neuralVoices + sysVoices
    }

    private fun updateCacheSize() {
        _cacheSizeBytes.value = cacheManager.getCacheSizeBytes()
    }

    override fun setEngineType(engineType: TtsEngineType) {
        _selectedEngineType.value = engineType
    }

    override fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        scope.launch { preferencesRepository.setSpeechRate(clamped) }
    }

    override fun setSpeechPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _speechPitch.value = clamped
        scope.launch { preferencesRepository.setSpeechPitch(clamped) }
    }

    override fun setSelectedVoiceId(voiceId: String) {
        _selectedVoiceId.value = voiceId
        scope.launch { preferencesRepository.setSelectedVoiceId(voiceId) }
    }

    override fun speak(
        text: String,
        languageCode: String,
        rateMultiplier: Float,
        voiceId: String?
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        // Cancel previous speech task immediately
        stop()

        speakJob = scope.launch {
            speakMutex.withLock {
                val effectiveRate = (_speechRate.value * rateMultiplier).coerceIn(0.5f, 2.0f)
                val effectivePitch = _speechPitch.value
                val effectiveVoiceId = voiceId ?: _selectedVoiceId.value

                val engineToUse = selectEngine(languageCode, effectiveVoiceId)
                val activeType = engineToUse.engineType

                _playbackState.value = TtsPlaybackState(
                    isPlaying = true,
                    currentText = cleanText,
                    currentLanguage = languageCode,
                    activeEngineType = activeType,
                    isSlowRate = rateMultiplier < 0.8f
                )

                engineToUse.speak(
                    text = cleanText,
                    languageCode = languageCode,
                    rate = effectiveRate,
                    pitch = effectivePitch,
                    voiceId = effectiveVoiceId,
                    onStart = {
                        _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    },
                    onDone = {
                        _playbackState.value = TtsPlaybackState(isPlaying = false)
                    },
                    onError = { _ ->
                        _playbackState.value = TtsPlaybackState(isPlaying = false)
                    }
                )
            }
        }
    }

    private fun selectEngine(languageCode: String, voiceId: String): TtsEngine {
        val preferred = _selectedEngineType.value
        return when (preferred) {
            TtsEngineType.SYSTEM -> systemEngine
            TtsEngineType.SHERPA_ONNX_NEURAL -> neuralEngine
            TtsEngineType.AUTO -> {
                // Prefers neural if a valid voice model exists for this language, else system
                val hasNeuralModel = VoiceModelManager.AVAILABLE_MODELS.any {
                    it.languageCode.equals(languageCode, ignoreCase = true) && modelManager.isModelInstalled(it.id)
                }
                if (hasNeuralModel) neuralEngine else systemEngine
            }
        }
    }

    override fun stop() {
        speakJob?.cancel()
        speakJob = null
        systemEngine.stop()
        neuralEngine.stop()
        _playbackState.value = TtsPlaybackState(isPlaying = false)
    }

    override suspend fun downloadVoiceModel(modelId: String): Result<File> {
        val result = modelManager.downloadModel(modelId)
        if (result.isSuccess) {
            neuralEngine.updateAvailableVoices()
            updateVoicesList()
        }
        return result
    }

    override fun cancelModelDownload(modelId: String) {
        modelManager.cancelDownload(modelId)
    }

    override fun deleteVoiceModel(modelId: String): Boolean {
        val deleted = modelManager.deleteModel(modelId)
        if (deleted) {
            neuralEngine.updateAvailableVoices()
            updateVoicesList()
        }
        return deleted
    }

    override fun clearAudioCache() {
        cacheManager.clearCache()
        updateCacheSize()
    }

    override fun shutdown() {
        stop()
        systemEngine.shutdown()
        neuralEngine.shutdown()
    }
}
