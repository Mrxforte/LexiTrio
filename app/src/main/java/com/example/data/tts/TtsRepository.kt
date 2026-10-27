package com.example.data.tts

import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface TtsRepository {
    val playbackState: StateFlow<TtsPlaybackState>
    val selectedEngineType: StateFlow<TtsEngineType>
    val speechRate: StateFlow<Float>
    val speechPitch: StateFlow<Float>
    val selectedVoiceId: StateFlow<String>
    val availableVoices: StateFlow<List<TtsVoiceInfo>>
    val modelDownloadStates: StateFlow<Map<String, ModelDownloadState>>
    val cacheSizeBytes: StateFlow<Long>

    suspend fun initialize()

    fun setEngineType(engineType: TtsEngineType)
    fun setSpeechRate(rate: Float)
    fun setSpeechPitch(pitch: Float)
    fun setSelectedVoiceId(voiceId: String)

    fun speak(
        text: String,
        languageCode: String,
        rateMultiplier: Float = 1.0f,
        voiceId: String? = null
    )

    fun stop()

    suspend fun downloadVoiceModel(modelId: String): Result<File>
    fun cancelModelDownload(modelId: String)
    fun deleteVoiceModel(modelId: String): Boolean
    fun clearAudioCache()

    fun shutdown()
}
