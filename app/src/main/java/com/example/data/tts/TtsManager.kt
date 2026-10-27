package com.example.data.tts

import android.content.Context
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.usecase.SpeakDictionaryWordUseCase
import com.example.domain.usecase.StopSpeechUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class TtsManager(
    private val context: Context,
    val repository: TtsRepository = TtsRepositoryImpl(
        context = context,
        preferencesRepository = UserPreferencesRepository(context)
    )
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val speakUseCase = SpeakDictionaryWordUseCase(repository)
    val stopUseCase = StopSpeechUseCase(repository)

    val playbackState: StateFlow<TtsPlaybackState> = repository.playbackState

    val isPlaying: StateFlow<Boolean> = repository.playbackState
        .map { it.isPlaying }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val availableVoices: StateFlow<List<TtsVoiceInfo>> = repository.availableVoices
    val currentSpeechRate: StateFlow<Float> = repository.speechRate
    val currentPitch: StateFlow<Float> = repository.speechPitch
    val selectedEngineType: StateFlow<TtsEngineType> = repository.selectedEngineType
    val modelDownloadStates: StateFlow<Map<String, ModelDownloadState>> = repository.modelDownloadStates
    val cacheSizeBytes: StateFlow<Long> = repository.cacheSizeBytes

    init {
        scope.launch {
            repository.initialize()
        }
    }

    fun setSpeechRate(rate: Float) {
        repository.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        repository.setSpeechPitch(pitch)
    }

    fun setEngineType(engineType: TtsEngineType) {
        repository.setEngineType(engineType)
    }

    fun setVoice(voiceId: String) {
        repository.setSelectedVoiceId(voiceId)
    }

    fun speak(text: String, languageCode: String, rateMultiplier: Float = 1.0f) {
        speakUseCase(text, languageCode, rateMultiplier)
    }

    fun speak(text: String, languageCode: String, rateMultiplier: Float, voiceId: String?) {
        speakUseCase(text, languageCode, rateMultiplier, voiceId)
    }

    fun stop() {
        stopUseCase()
    }

    suspend fun downloadModel(modelId: String): Result<File> {
        return repository.downloadVoiceModel(modelId)
    }

    fun cancelDownload(modelId: String) {
        repository.cancelModelDownload(modelId)
    }

    fun deleteModel(modelId: String): Boolean {
        return repository.deleteVoiceModel(modelId)
    }

    fun clearCache() {
        repository.clearAudioCache()
    }

    fun shutdown() {
        repository.shutdown()
    }
}
